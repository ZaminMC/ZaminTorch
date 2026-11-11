package net.zaminmc.torch.server.world;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Factory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import com.github.luben.zstd.ZstdOutputStreamNoFinalizer;

/**
 * The world-delta store's compression envelope (WAVE RC — the KigPaper
 * region-compression pair 0214/0215 ported engine-native).
 *
 * <p>THE PATCH SHAPE: Paper 0214 ("Use alternative region compression") and
 * 0215 ("Add zstd region compression") give the region file format a
 * {@code RegionFile.CompressionAlgorithm} enum — {@code NONE, GZIP, ZLIB,
 * LZ4, ZSTD} — whose ordinal is written as the per-chunk compression-type
 * byte, selected by the {@code region-compression-algo} config row with
 * ZLIB (the format's own historical byte) as the patch's default. The write
 * path wraps the chunk buffer in the chosen codec's stream; the read path
 * dispatches on the byte each chunk carries — so a region file may hold
 * mixed-compression chunks and always reads back whatever any older or
 * newer server wrote. The codecs are the patch's own: JDK GZIP, JDK
 * {@code DeflaterOutputStream}/{@code InflaterInputStream} for ZLIB,
 * lz4-java's block streams ({@code 65536} block size, the factory's fast
 * compressor/decompressor — the pure-Java instance when JNI is unavailable,
 * the patch's own reason for choosing LZ4), and zstd-jni's
 * no-finalizer streams for ZSTD.</p>
 *
 * <p>THE ENGINE MAPPING: the ZWD store is a single snapshot file, not a
 * per-chunk region — so the envelope carries ONE compression-type byte at
 * the file head. The byte space is unambiguous: the historical raw ZWD
 * format's first byte is the magic {@code 'Z'} (0x5A), which can never
 * collide with the ordinals 0-4, so the reader discriminates raw from
 * enveloped on the first byte alone and EVERY historical save loads
 * unchanged. The write path compresses inside the publication walk (the
 * patch's own placement — the write stream), the single
 * {@code DeltaWorldStorage.writeSerialized} choke point, so no save path
    * can bypass the envelope.</p>
 *
 * <p>THE DEFAULT LAW (the never-a-default-change law, the KO family's own):
 * {@code NONE} — the engine's ZWD files stay byte-identical to the
 * historical format; the envelope is the operator's opt-in through
 * {@code -Dtorch.region-compression-algo=<NONE|GZIP|ZLIB|LZ4|ZSTD>}
 * (case-insensitive, the patch's own {@code toUpperCase(Locale.ROOT)} read).
 * An unknown name fails loudly at storage construction — the patch's own
 * {@code valueOf} shape — never silently falling back to a default.</p>
 */
public final class RegionCompression {

    private RegionCompression() {
    }

    /**
     * The compression algorithms the envelope knows (the patch's own
     * {@code RegionFile.CompressionAlgorithm} verbatim — the ordinals ARE
     * the file's compression-type bytes).
     */
    public enum Algorithm {
        NONE,
        GZIP,
        ZLIB,
        LZ4,
        ZSTD
    }

    /** The flag's own name (the patch's {@code region-compression-algo} row). */
    public static final String FLAG_PROPERTY = "torch.region-compression-algo";

    /** The historical raw ZWD magic's first byte — never a valid ordinal. */
    private static final int RAW_MAGIC_FIRST = 'Z';

    /** The patch's own shared factory (the pure-Java instance when JNI is absent). */
    private static final LZ4Factory LZ4 = LZ4Factory.fastestJavaInstance();

    /** The LZ4 block size the patch pins (65536 — {@code new LZ4BlockOutputStream(buffer, 65536, ...)}). */
    private static final int LZ4_BLOCK_SIZE = 65536;

    /**
     * Reads the operator's algorithm from the {@code torch.region-compression-algo}
     * system property — {@link Algorithm#NONE} when unset (the never-a-default-
     * change law: the engine's historical raw format), loud on an unknown name
     * (the patch's own {@code valueOf} shape, the case-insensitive read from
     * 0215).
     */
    public static Algorithm fromFlag() {
        String raw = System.getProperty(FLAG_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return Algorithm.NONE;
        }
        try {
            return Algorithm.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException(
                    "Unknown " + FLAG_PROPERTY + " '" + raw + "' (valid: NONE, GZIP, ZLIB, LZ4, ZSTD)",
                    unknown);
        }
    }

    /**
     * Wraps the raw ZWD bytes into the file bytes: {@link Algorithm#NONE}
     * returns the raw bytes UNCHANGED (the historical format, byte-identical
     * — no envelope byte is ever written); every armed algorithm answers
     * {@code [ordinal byte][compressed payload]}.
     *
     * <p>Pure: no filesystem, no shared state — the CPU cost rides the
     * publication walk's own thread (the patch's own placement: the
     * compression happens inside the write stream).</p>
     */
    public static byte[] wrap(Algorithm algorithm, byte[] raw) {
        if (algorithm == Algorithm.NONE) {
            return raw;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(raw.length / 2 + 16);
        buffer.write(algorithm.ordinal());
        try {
            OutputStream compressed = compressor(algorithm, buffer);
            compressed.write(raw);
            compressed.close(); // the codec's terminator (GZIP/zstd trailers, LZ4's block end mark)
        } catch (IOException impossible) {
            // A ByteArrayOutputStream has no physical failure mode; the
            // codecs' close() writes trailers, which cannot fail into memory.
            throw new IllegalStateException("ZWD compression failed for " + algorithm, impossible);
        }
        return buffer.toByteArray();
    }

    /**
     * Unwraps the file bytes into the ZWD payload stream: the first byte
     * discriminates raw ({@code 'Z'}) from an envelope (the ordinal), and
     * the envelope's own byte — NEVER the operator's flag — selects the
     * codec (the region reader's law: every file reads back whatever any
     * configuration wrote, mixed or historical). An unknown byte is an
     * {@link IOException} — the storage's quarantine walk's own input.
     */
    public static InputStream unwrap(byte[] fileBytes) throws IOException {
        if (fileBytes.length == 0) {
            throw new IOException("Empty world delta file");
        }
        int first = fileBytes[0] & 0xFF;
        if (first == RAW_MAGIC_FIRST) {
            // The historical raw format (or a NONE-written file — identical
            // by the default law): the bytes ARE the payload.
            return new ByteArrayInputStream(fileBytes);
        }
        if (first == Algorithm.NONE.ordinal()) {
            // An explicit NONE envelope (never written by this engine —
            // NONE answers raw whole; accepted for completeness, the region
            // reader's every-known-ordinal law).
            return new ByteArrayInputStream(fileBytes, 1, fileBytes.length - 1);
        }
        ByteArrayInputStream payload = new ByteArrayInputStream(fileBytes, 1, fileBytes.length - 1);
        if (first == Algorithm.GZIP.ordinal()) {
            return new GZIPInputStream(payload);
        }
        if (first == Algorithm.ZLIB.ordinal()) {
            return new InflaterInputStream(payload);
        }
        if (first == Algorithm.LZ4.ordinal()) {
            return new LZ4BlockInputStream(payload, LZ4.fastDecompressor());
        }
        if (first == Algorithm.ZSTD.ordinal()) {
            return new ZstdInputStreamNoFinalizer(payload);
        }
        throw new IOException("Unknown ZWD compression type byte: " + first
                + " (valid: 1 GZIP, 2 ZLIB, 3 LZ4, 4 ZSTD; 'Z' raw)");
    }

    /** The write-side codec for the armed algorithms — the patch's own streams. */
    private static OutputStream compressor(Algorithm algorithm, OutputStream target) throws IOException {
        switch (algorithm) {
            case GZIP:
                // "Normally not supported by the Notchian server" — the
                // patch's own comment; the engine's envelope accepts it.
                return new GZIPOutputStream(target);
            case ZLIB:
                return new DeflaterOutputStream(target);
            case LZ4:
                return new LZ4BlockOutputStream(target, LZ4_BLOCK_SIZE, LZ4.fastCompressor());
            case ZSTD:
                return new ZstdOutputStreamNoFinalizer(target);
            case NONE:
            default:
                throw new IllegalArgumentException("No codec for " + algorithm
                        + " (NONE answers the raw bytes whole)");
        }
    }
}
