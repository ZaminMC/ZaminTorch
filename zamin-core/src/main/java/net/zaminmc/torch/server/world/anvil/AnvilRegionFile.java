package net.zaminmc.torch.server.world.anvil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * The read-only Anvil region file reader (the Anvil canvas' storage leg —
 * the reference's {@code world/chunk/storage/RegionFile.java} read side,
 * ported 1:1 with §31.6's isolation duty replacing the reference's silent
 * null answers). The format: an 8 KiB header — 1024 entries of 3 bytes
 * (a 24-bit big-endian {@code (sectorOffset << 8) | sectorCount}) then
 * 1024 entries of 4-byte timestamps — followed by 4 KiB sectors. A chunk
 * payload is {@code 4-byte length + 1-byte compression scheme + bytes}:
 * scheme 1 gzip, scheme 2 zlib (the reference's own accepted pair — the
 * scheme byte's raw form 3 is accepted here too because the import is
 * read-only and the payload is unambiguous; ledgered below). One entry
 * covers 32x32 chunks; the entry index is {@code x + z * 32} for the
 * chunk's position masked to the region.
 *
 * <p><b>The §31.6 discipline (the task's own rule):</b> the reference
 * answers {@code null} for every failure — a truncated file, a corrupt
 * header, an oversized length — and the caller cannot tell a missing
 * chunk from a broken one. This reader instead answers a typed
 * {@link Isolated} record carrying the file path, the chunk, and the
 * exact reason, so the import's ledger can report every isolated branch
 * loudly and never silently substitute a default. The UNOCCUPIED entry
 * (offset zero) is not a failure at all — it is the format's own "no
 * chunk here" answer, reported separately from corruption.</p>
 *
 * <p><b>The bounds walk (the reference's own checks, kept 1:1):</b> an
 * entry whose sectors run past the file is refused before any read; a
 * payload length that exceeds its sector budget or is non-positive is
 * refused; a truncated payload (fewer bytes than the length claims) is
 * refused. The reference's constructor-time padding-to-sector-multiple
 * and free-sector bookkeeping are WRITE-side machinery this read-only
 * port deliberately does not carry.</p>
 *
 * <p><b>The whole-file read:</b> {@link #open} reads the file once, whole,
 * and every entry slices that one array — a single consistent read that
 * sidesteps torn reads of a concurrently written source (§31.6's
 * snapshot-before-I/O spirit, read side). Region files are bounded by
 * their 255-sector-per-entry table in practice; the one-shot import
 * accepts the transient copy.</p>
 */
public final class AnvilRegionFile {

    /** The sector size (the format's own 4096-byte block). */
    public static final int SECTOR_BYTES = 4096;
    /** The region's chunk grid edge (32x32 entries). */
    public static final int REGION_CHUNKS = 32;
    /** The header's size (1024 location entries + 1024 timestamps). */
    public static final int HEADER_BYTES = 2 * REGION_CHUNKS * REGION_CHUNKS * 4;
    /** The gzip scheme (RegionFile.getChunkInputStream b0 == 1). */
    public static final int SCHEME_GZIP = 1;
    /** The zlib scheme (RegionFile.getChunkInputStream b0 == 2). */
    public static final int SCHEME_ZLIB = 2;
    /** The raw scheme (the format's defined-but-unwritten third form). */
    public static final int SCHEME_RAW = 3;

    private final Path file;
    private final byte[] whole;

    /**
     * One read outcome: either the decompressed payload bytes (an NBT
     * document ready for {@code BinaryNbtReader.read}), or the typed
     * isolation with the path + chunk + reason (§31.6's loud answer).
     */
    public sealed interface ChunkRead permits ChunkPayload, Isolated {
    }

    /** The occupied, intact, decompressed chunk payload. */
    public record ChunkPayload(byte[] nbtBytes) implements ChunkRead {
    }

    /**
     * The §31.6 isolation: the file, the chunk, and the reason. Never a
     * silent default — the caller counts, reports, and moves on.
     */
    public record Isolated(Path file, int chunkX, int chunkZ, String reason) implements ChunkRead {
    }

    /**
     * Opens and reads the whole region file. The header is validated
     * immediately; the payload walk slices the one array.
     *
     * @throws IOException the file-level failures (missing, too short for
     *                     even the header, unreadable) — these isolate the
     *                     WHOLE file before any entry is walked
     */
    public static AnvilRegionFile open(Path file) throws IOException {
        byte[] whole = Files.readAllBytes(file);
        if (whole.length < HEADER_BYTES) {
            throw new IOException("Region file is shorter than the 8 KiB header ("
                    + whole.length + " bytes): " + file);
        }
        return new AnvilRegionFile(file, whole);
    }

    private AnvilRegionFile(Path file, byte[] whole) {
        this.file = file;
        this.whole = whole;
    }

    /** The region file's path (the isolation reports' own subject). */
    public Path file() {
        return file;
    }

    /**
     * @return the entry's raw location int — bits 8..31 the sector offset,
     * bits 0..7 the sector count; zero means unoccupied (the format's
     * own empty answer, RegionFile.getChunkBlockInfo).
     */
    public int locationOf(int localX, int localZ) {
        int index = entryIndex(localX, localZ);
        // The header stores each location as 3 bytes big-endian followed by
        // the next entry — the int the reference's readInt answers.
        int b0 = whole[index * 3] & 0xFF;
        int b1 = whole[index * 3 + 1] & 0xFF;
        int b2 = whole[index * 3 + 2] & 0xFF;
        return b0 << 16 | b1 << 8 | b2;
    }

    /** @return the entry's raw timestamp (the header's second 4 KiB half). */
    public int timestampOf(int localX, int localZ) {
        int index = entryIndex(localX, localZ);
        int at = HEADER_BYTES / 2 + index * 4;
        return (whole[at] & 0xFF) << 24 | (whole[at + 1] & 0xFF) << 16
                | (whole[at + 2] & 0xFF) << 8 | (whole[at + 3] & 0xFF);
    }

    /** @return whether the entry is occupied (a non-zero location). */
    public boolean isOccupied(int localX, int localZ) {
        return locationOf(localX, localZ) != 0;
    }

    /**
     * Reads one chunk's payload — the reference's
     * {@code getChunkInputStream} walk (the bounds checks, the length
     * read, the scheme dispatch) with every failure answering an
     * {@link Isolated} instead of the reference's null.
     *
     * @param localX the chunk's position inside the region, 0..31
     * @param localZ the chunk's position inside the region, 0..31
     */
    public ChunkRead readChunk(int localX, int localZ) {
        if (localX < 0 || localX >= REGION_CHUNKS || localZ < 0 || localZ >= REGION_CHUNKS) {
            return new Isolated(file, localX, localZ, "entry outside the 32x32 region grid");
        }
        int location = locationOf(localX, localZ);
        if (location == 0) {
            return new Isolated(file, localX, localZ, "unoccupied entry (no chunk data)");
        }
        // The reference's own bounds arm: j + k > the file's sector count
        // refuses the read (blockEmptyFlags.size() == the sector count).
        int sectorOffset = location >> 8;
        int sectorCount = location & 0xFF;
        if (sectorCount == 0) {
            return new Isolated(file, localX, localZ,
                    "entry claims zero sectors at offset " + sectorOffset);
        }
        long sectorEnd = (long) sectorOffset + sectorCount;
        if (sectorEnd > (long) whole.length / SECTOR_BYTES) {
            return new Isolated(file, localX, localZ,
                    "entry's sectors (" + sectorOffset + "+" + sectorCount
                            + ") run past the file's end ("
                            + (whole.length / SECTOR_BYTES) + " sectors)");
        }
        return readPayloadAt(localX, localZ, sectorOffset, sectorCount);
    }

    /** The payload walk at the entry's sector: length, scheme, bytes. */
    private ChunkRead readPayloadAt(int localX, int localZ, int sectorOffset, int sectorCount) {
        int dataAt = sectorOffset * SECTOR_BYTES;
        int length = readInt(dataAt);
        if (length <= 0) {
            return new Isolated(file, localX, localZ, "non-positive payload length " + length);
        }
        if (length > sectorCount * SECTOR_BYTES) {
            // The reference's own arm: l > 4096 * k refuses the read.
            return new Isolated(file, localX, localZ,
                    "payload length " + length + " exceeds its " + sectorCount
                            + "-sector budget (" + sectorCount * SECTOR_BYTES + ")");
        }
        int payloadAt = dataAt + 4;
        if (payloadAt + length > whole.length) {
            return new Isolated(file, localX, localZ,
                    "payload truncated: length " + length + " claims "
                            + (payloadAt + length - whole.length) + " bytes past the file's end");
        }
        int scheme = whole[payloadAt] & 0xFF;
        int bytesAt = payloadAt + 1;
        int byteCount = length - 1;
        byte[] payload = Arrays.copyOfRange(whole, bytesAt, bytesAt + byteCount);
        // The reference's own dispatch (b0 == 1 gzip, b0 == 2 zlib) plus
        // the raw third form; every other byte isolates loudly.
        if (scheme != SCHEME_GZIP && scheme != SCHEME_ZLIB && scheme != SCHEME_RAW) {
            return new Isolated(file, localX, localZ,
                    "unknown compression scheme " + scheme + " (1=gzip, 2=zlib, 3=raw)");
        }
        try (InputStream in = scheme == SCHEME_GZIP
                ? new GZIPInputStream(new ByteArrayInputStream(payload))
                : scheme == SCHEME_ZLIB
                        ? new InflaterInputStream(new ByteArrayInputStream(payload))
                        : null;
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            if (in != null) {
                in.transferTo(out);
                return new ChunkPayload(out.toByteArray());
            }
            return new ChunkPayload(payload); // the raw scheme: bytes as stored
        } catch (IOException e) {
            return new Isolated(file, localX, localZ,
                    "corrupt compressed payload: " + e.getMessage());
        }
    }

    private int readInt(int at) {
        return (whole[at] & 0xFF) << 24 | (whole[at + 1] & 0xFF) << 16
                | (whole[at + 2] & 0xFF) << 8 | (whole[at + 3] & 0xFF);
    }

    private static int entryIndex(int localX, int localZ) {
        // RegionFile.getChunkBlockInfo: chunkBlockInfo[chunkX + chunkZ * 32].
        return localX + localZ * REGION_CHUNKS;
    }
}
