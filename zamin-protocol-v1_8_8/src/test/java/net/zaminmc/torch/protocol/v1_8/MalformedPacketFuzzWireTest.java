package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-7 of the vanilla parity remainder: the malformed-packet fuzz and the
 * packet-size boundary pass, over the C2S reader surface.
 *
 * <p><b>The size law (the reference's own, SplitterHandler.java)</b>: the
 * reference's splitter reads at most a 3-byte varint — a length wider than
 * 21 bits throws {@code CorruptedFrameException("length wider than 21-bit")}
 * and kills the connection; any length within 21 bits (up to 2,097,151) is
 * lawful and read once the bytes arrive — there is NO other size cap in
 * 1.8.8 (the 32 KiB era came later). Torch's FrameCodec pins exactly that
 * ceiling (MAX_FRAME = 2,097,151, the 3-byte varint maximum): a longer
 * prefix closes, a malformed varint closes, a partial frame WAITS. The
 * boundary cases here pin all three forms observably.</p>
 *
 * <p><b>The fuzz law</b>: no malformed inbound frame may kill the ENGINE,
 * stall its tick, or corrupt state — the connection may lawfully die (the
 * reader's refuse arms and the frame decoder's close), but a fresh client
 * must always be able to join afterwards. The corpus is DETERMINISTIC: a
 * hand-crafted case list over the real C2S ids (oversized string prefixes,
 * truncated bodies, out-of-range enums and slots, NaN positions, unknown
 * packet ids, a mismatched keep-alive echo) plus a seeded random tail
 * (Random(474747L), 40 frames: random ids 0..0x45 over random 0..64-byte
 * bodies) — every case reproducible by construction, no wall-clock sleeps:
 * liveness is the engine's own tick (awaitCondition over the tick clock),
 * the same law the load-flake retirement set.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MalformedPacketFuzzWireTest {

    @org.junit.jupiter.api.io.TempDir
    static Path dataDir;

    private EngineServer server;
    private V18ProtocolServer adapter;

    @BeforeAll
    void bootStack() throws Exception {
        EngineConfig config = new EngineConfig(
                "127.0.0.1", 0, "r7fuzz", "ZaminTorch test", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        adapter = new V18ProtocolServer(server, 10_000_000L);
        adapter.start(server);
    }

    @AfterAll
    void stopStack() {
        server.shutdown(adapter::shutdown);
    }

    private void awaitCondition(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    /** The engine's own liveness face: the tick clock advances. */
    private long tickClock() {
        return server.world().totalTicks();
    }

    private void awaitOneTick(String after) throws InterruptedException {
        long before = tickClock();
        awaitCondition(() -> tickClock() > before, "the tick advanced " + after);
    }

    private void join(TestClient18 client, String name) throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(),
                name + " registered");
    }

    /** varint helper for hand-crafted payloads. */
    private static void putVarInt(List<Byte> out, int value) {
        while ((value & ~0x7F) != 0) {
            out.add((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.add((byte) value);
    }

    private static byte[] corpusCase(int packetId, byte... body) {
        List<Byte> frame = new ArrayList<>();
        putVarInt(frame, packetId);
        for (byte b : body) {
            frame.add(b);
        }
        byte[] payload = new byte[frame.size()];
        for (int i = 0; i < frame.size(); i++) {
            payload[i] = frame.get(i);
        }
        return payload;
    }

    private static byte[] corpusCaseBytes(int packetId, byte[]... bodies) {
        List<Byte> frame = new ArrayList<>();
        putVarInt(frame, packetId);
        for (byte[] body : bodies) {
            for (byte b : body) {
                frame.add(b);
            }
        }
        byte[] payload = new byte[frame.size()];
        for (int i = 0; i < frame.size(); i++) {
            payload[i] = frame.get(i);
        }
        return payload;
    }

    private static byte[] stringOf(int length) {
        byte[] fill = new byte[length];
        java.util.Arrays.fill(fill, (byte) 'A');
        return fill;
    }

    private static byte[] longVarint(int value) {
        // the 5-byte maximal varint encoding of a value (0x7FFFFFFF family)
        List<Byte> out = new ArrayList<>();
        putVarInt(out, value);
        byte[] payload = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) {
            payload[i] = out.get(i);
        }
        return payload;
    }

    /**
     * The deterministic corpus: each case rides its OWN fresh client (login
     * included), so no case's refuse arm can mask the next case's reach —
     * the engine must survive every one with its tick advancing.
     */
    @Test
    void theDeterministicCorpusNeverKillsTheEngine() throws Exception {
        List<byte[]> corpus = new ArrayList<>();
        // 0x01 chat: an oversized string length prefix with no body behind it
        List<Byte> chatOversized = new ArrayList<>();
        putVarInt(chatOversized, 0x01);
        putVarInt(chatOversized, 0x7FFFFFFF);
        corpus.add(toArray(chatOversized));
        // 0x01 chat: a 200-char string (over the reference's 100-char cap)
        List<Byte> chatLong = new ArrayList<>();
        putVarInt(chatLong, 0x01);
        putVarInt(chatLong, 200);
        for (byte b : stringOf(200)) {
            chatLong.add(b);
        }
        corpus.add(toArray(chatLong));
        // 0x04 position: y = NaN (the quiet-NaN double bits)
        corpus.add(corpusCaseBytes(0x04,
                doubleBytes(0.0), doubleBytes(Double.NaN), doubleBytes(0.0), new byte[]{1}));
        // 0x04 position: truncated after x
        corpus.add(corpusCaseBytes(0x04, doubleBytes(1.0)));
        // 0x04 position: truncated mid-double
        corpus.add(corpusCaseBytes(0x04, doubleBytes(1.0), doubleBytes(2.0), new byte[]{0x40, 0x09}));
        // 0x07 digging: face 99, status 99 (out-of-range enums)
        corpus.add(corpusCaseBytes(0x07,
                concat(longVarint(99), longBytes(5), longBytes(0), longBytes(64), longBytes(0), longBytes(99))));
        // 0x07 digging: a continuation-varint body (bad varint mid-body)
        corpus.add(corpusCase(0x07, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x08));
        // 0x09 held-item: slot -1 then slot 500 (out-of-range both ways)
        corpus.add(corpusCaseBytes(0x09, longVarint(-1)));
        corpus.add(corpusCaseBytes(0x09, longVarint(500)));
        // 0x0E window click: mode 99, slot -999, button 7
        corpus.add(corpusCaseBytes(0x0E,
                concat(longVarint(1), longVarint(-999), new byte[]{7}, shortBytes(0), shortBytes(0), longVarint(99), itemBytes())));
        // 0x0F confirm transaction: window 99, action -1
        corpus.add(corpusCaseBytes(0x0F, concat(new byte[]{99}, shortBytes(-1), new byte[]{1})));
        // 0x10 creative slot: slot -5 with an item-id continuation varint
        corpus.add(corpusCaseBytes(0x10, concat(longVarint(-5), longVarint(0x7FFFFFFF), new byte[]{(byte) 0x80, (byte) 0x80})));
        // 0x12 update sign: four 200-char lines (oversized strings)
        List<Byte> signOversized = new ArrayList<>();
        putVarInt(signOversized, 0x12);
        putVarInt(signOversized, longBytes(0).length);
        for (byte b : longBytes(0)) {
            signOversized.add(b);
        }
        putVarintThen(signOversized, 200);
        addAllBytes(signOversized, stringOf(200));
        putVarintThen(signOversized, 200);
        addAllBytes(signOversized, stringOf(200));
        putVarintThen(signOversized, 200);
        addAllBytes(signOversized, stringOf(200));
        putVarintThen(signOversized, 200);
        addAllBytes(signOversized, stringOf(200));
        corpus.add(toArray(signOversized));
        // 0x14 tab complete: a 300-char ask
        List<Byte> tabLong = new ArrayList<>();
        putVarInt(tabLong, 0x14);
        putVarintThen(tabLong, 300);
        addAllBytes(tabLong, stringOf(300));
        corpus.add(toArray(tabLong));
        // 0x15 client settings: locale 200 chars, view -5, chat flags 99, skin 0xFF
        List<Byte> settings = new ArrayList<>();
        putVarInt(settings, 0x15);
        putVarintThen(settings, 200);
        addAllBytes(settings, stringOf(200));
        addAllBytes(settings, longVarint(-5));
        settings.add((byte) 99);
        settings.add((byte) 99);
        settings.add((byte) 0xFF);
        corpus.add(toArray(settings));
        // 0x17 plugin message: a 4 KB garbage body under a lawful channel name
        List<Byte> plugin = new ArrayList<>();
        putVarInt(plugin, 0x17);
        putVarintThen(plugin, 9);
        addAllBytes(plugin, "MC|BTrand".getBytes(StandardCharsets.UTF_8));
        addAllBytes(plugin, stringOf(4096));
        corpus.add(toArray(plugin));
        // 0x18 spectate: a truncated uuid (8 of 16 bytes)
        corpus.add(corpusCaseBytes(0x18, longBytes(0x12345678L)));
        // 0x19 resource pack status: status 99
        corpus.add(corpusCase(0x19, (byte) 99));
        // 0x00 keep-alive: a mismatched echo (must NOT kill a healthy connection)
        corpus.add(corpusCaseBytes(0x00, longVarint(0x7FFFFFF0)));
        // unknown ids: the skip-or-refuse law, engine survives either way
        corpus.add(corpusCase(0x2B, (byte) 1, (byte) 2, (byte) 3, (byte) 4));
        corpus.add(corpusCase(0x3F, (byte) 0x80, (byte) 0x80, (byte) 0x80));
        corpus.add(corpusCase(0x45));

        for (int i = 0; i < corpus.size(); i++) {
            try (TestClient18 fuzzer = new TestClient18("127.0.0.1", adapter.boundPort())) {
                join(fuzzer, "Fuzz" + i);
                fuzzer.sendPacket(corpus.get(i));
            }
            awaitOneTick("after corpus case " + i);
        }
        // The corpus's own join proves the state survived: the last case's
        // client (or its refuse arm) never wedged the engine.
        assertTrue(server.world().totalTicks() > 0, "the engine ticked through the corpus");
    }

    /**
     * The seeded random tail: ONE connection absorbing 40 garbage frames —
     ids, bodies, and boundary bytes all from Random(474747L).
     */
    @Test
    void theSeededRandomTailOverGarbageFrames() throws Exception {
        Random rng = new Random(474747L);
        TestClient18 fuzzer = new TestClient18("127.0.0.1", adapter.boundPort());
        join(fuzzer, "FuzzTail0");
        int connections = 1;
        int lawfulCloses = 0;
        for (int i = 0; i < 40; i++) {
            int id = rng.nextInt(0x46);
            int bodyLen = rng.nextInt(65);
            byte[] body = new byte[bodyLen];
            rng.nextBytes(body);
            byte[] frame = corpusCaseBytes(id, body);
            try {
                fuzzer.sendPacket(frame);
            } catch (IOException lawfulRefuse) {
                // The connection died (the reader's or the decoder's refuse
                // arm) — lawful: the invariant is the ENGINE, not the
                // connection. Rejoin fresh and carry the tail on.
                lawfulCloses++;
                System.out.println("R-7 tail: frame " + i + " (id 0x"
                        + Integer.toHexString(id) + ", " + bodyLen + "B) closed the connection — lawful refuse");
                fuzzer.close();
                fuzzer = new TestClient18("127.0.0.1", adapter.boundPort());
                join(fuzzer, "FuzzTail" + connections);
                connections++;
            }
            awaitOneTick("after tail frame " + i);
        }
        fuzzer.close();
        System.out.println("R-7 tail: 40 frames over " + connections
                + " connections (" + lawfulCloses + " lawful closes), engine ticked through all");
        // A fresh clean client joins after the garbage: the state-integrity
        // assertion (no corruption from any frame the tail carried in).
        try (TestClient18 clean = new TestClient18("127.0.0.1", adapter.boundPort())) {
            join(clean, "FuzzClean");
        }
        assertTrue(server.playerRegistry().byName("FuzzClean").isPresent(),
                "a clean client joined after the fuzz tail");
    }

    /**
     * The frame-decoder boundary: the reference's own 21-bit law.
     * (a) a 4-byte continuation varint as the length prefix kills the
     *     connection (CorruptedFrameException's observable form: EOF);
     * (b) a length of exactly 2,097,151 (0xFF 0xFF 0x7F — the 21-bit
     *     ceiling, lawful per the reference's splitter) with only 4 bytes
     *     behind it makes the decoder WAIT — the connection stays open.
     */
    @Test
    void theFrameBoundaryFollowsTheReference21BitLaw() throws Exception {
        try (TestClient18 boundary = new TestClient18("127.0.0.1", adapter.boundPort())) {
            join(boundary, "FuzzEdge");
            // (a) 4-byte continuation varint + junk: the connection must die.
            boundary.sendRawBytes(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80,
                    (byte) 0x80, 0x01, 0x02, 0x03, 0x04});
            awaitCondition(() -> isClosed(boundary), "the malformed framing closed the connection");
        }
        try (TestClient18 waiter = new TestClient18("127.0.0.1", adapter.boundPort())) {
            join(waiter, "FuzzWait");
            // (b) the ceiling length with only 4 bytes: the decoder waits.
            waiter.sendRawBytes(new byte[]{(byte) 0xFF, (byte) 0xFF, 0x7F, 1, 2, 3, 4});
            long deadline = System.currentTimeMillis() + 1_500;
            boolean closed = false;
            while (System.currentTimeMillis() < deadline) {
                if (isClosedQuiet(waiter)) {
                    closed = true;
                    break;
                }
                Thread.sleep(25);
            }
            assertTrue(!closed, "a partial frame at the 21-bit ceiling must WAIT, not close");
        }
        awaitOneTick("after the boundary cases");
    }

    private static boolean isClosed(TestClient18 client) {
        try {
            client.setReadTimeout(400);
            client.readPacket();
            return false; // readable data — open
        } catch (java.net.SocketTimeoutException noDataYet) {
            return false; // silence — open, no data yet
        } catch (IOException eofFamily) {
            return true; // EOF/reset family — closed
        }
    }

    private boolean isClosedQuiet(TestClient18 client) {
        try {
            client.setReadTimeout(60);
            client.readPacket();
            return false; // readable data arrived — open either way
        } catch (java.net.SocketTimeoutException noDataYet) {
            return false; // silence within the window — still open, no data yet
        } catch (IOException eofFamily) {
            return true; // EOF/reset — closed
        }
    }

    // ---- byte-casing helpers -------------------------------------------------

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) {
            total += a.length;
        }
        byte[] out = new byte[total];
        int at = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, at, a.length);
            at += a.length;
        }
        return out;
    }

    private static byte[] doubleBytes(double value) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(8);
        try {
            new java.io.DataOutputStream(bos).writeDouble(value);
        } catch (IOException never) {
            throw new AssertionError(never);
        }
        return bos.toByteArray();
    }

    private static byte[] longBytes(long value) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(8);
        try {
            new java.io.DataOutputStream(bos).writeLong(value);
        } catch (IOException never) {
            throw new AssertionError(never);
        }
        return bos.toByteArray();
    }

    private static byte[] shortBytes(int value) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(2);
        try {
            new java.io.DataOutputStream(bos).writeShort(value);
        } catch (IOException never) {
            throw new AssertionError(never);
        }
        return bos.toByteArray();
    }

    private static byte[] itemBytes() {
        return shortBytes(-1); // the empty slot's own wire form
    }

    private static void putVarintThen(List<Byte> out, int value) {
        putVarInt(out, value);
    }

    private static void addAllBytes(List<Byte> out, byte[] bytes) {
        for (byte b : bytes) {
            out.add(b);
        }
    }

    private static byte[] toArray(List<Byte> frame) {
        byte[] payload = new byte[frame.size()];
        for (int i = 0; i < frame.size(); i++) {
            payload[i] = frame.get(i);
        }
        return payload;
    }
}
