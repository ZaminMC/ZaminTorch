package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.player.PlayerSession;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-019 — the 0x26 Map Chunk Bulk law pinned at the wire (the reference's
 * per-player pending-chunk walk, ServerPlayerEntity.java lines 233-272: the
 * walk collects the ready columns — at most 10, line 238 — and ONE packet
 * carries them; exactly 1 keeps the single 0x21, 2+ ride the bulk, lines
 * 257-262):
 *
 * <ul>
 * <li>the join stream batches the pregenerated columns: every bulk carries
 *     2-10 columns, the skylight boolean is true, and the client can size
 *     every column's array from its own mask (the reference's read,
 *     WorldChunksS2CPacket lines 36-53 — headers first, then the arrays
 *     concatenated WITHOUT length prefixes);</li>
 * <li>the respawn re-stream of a fully-loaded view is byte-EXACT: 81 columns
 *     = 8 bulks of 10 + the single law for the remainder of one (the
 *     reference's own lone-column form), in the walk's own order, every
 *     column's bytes equal to a direct serialization taken in the SAME
 *     pre-handler drain window (nothing runs between the snapshot job and
 *     the re-stream job — and the flat world's inertness makes the bytes
 *     immutable anyway: its grass surface has no spread targets and no
 *     shadows);</li>
 * <li>the movement arm batches the newly-visible strip: one east-chunk
 *     shift makes 9 new columns, pre-loaded, arriving as ONE 9-column
 *     bulk.</li>
 * </ul>
 *
 * <p>Every wait is an engine-condition await or a bounded socket-timeout
 * read; the flat test world random-ticks nothing, so no block-change frame
 * can interleave with the captures. The captures read the join wave
 * DIRECTLY (the client's own readUntilPositionAndLook would discard every
 * chunk frame ahead of the anchor — the wave is the whole story here).</p>
 */
class ChunkBulkWireTest extends ProtocolTestBase {

    /** No keep-alives inside the capture windows (the harness's law). */
    @Override
    protected long keepAliveIntervalMs() {
        return 10_000_000;
    }

    /** One full-form column payload: sections * (blocks + blocklight + skylight) + biomes. */
    private static int fullFormArraySize(int mask) {
        return Integer.bitCount(mask) * 12_288 + 256;
    }

    /** One parsed bulk header. */
    private record BulkColumn(int x, int z, int mask) {
    }

    /** One parsed 0x26 frame (id already consumed). */
    private record ParsedBulk(boolean light, List<BulkColumn> headers, byte[][] arrays, int leftover) {
        int count() {
            return headers.size();
        }
    }

    /** One parsed 0x21 frame (id already consumed). */
    private record ParsedSingle(int x, int z, boolean groundUp, int mask, byte[] data, int leftover) {
    }

    private ParsedBulk parseBulk(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // the 0x26 id
        boolean light = buffer.readBoolean();
        int count = ByteBufOps.readVarInt(buffer);
        List<BulkColumn> headers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            headers.add(new BulkColumn(buffer.readInt(), buffer.readInt(), buffer.readShort() & 0xFFFF));
        }
        byte[][] arrays = new byte[count][];
        for (int i = 0; i < count; i++) {
            // The client's own size law: each column's array is sized by its
            // mask alone (the reference's findBufferSize — no length prefix
            // rides the bulk).
            arrays[i] = new byte[fullFormArraySize(headers.get(i).mask())];
            buffer.readBytes(arrays[i]);
        }
        return new ParsedBulk(light, headers, arrays, buffer.readableBytes());
    }

    private ParsedSingle parseSingle(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // the 0x21 id
        int x = buffer.readInt();
        int z = buffer.readInt();
        boolean groundUp = buffer.readBoolean();
        int mask = buffer.readShort() & 0xFFFF;
        int length = ByteBufOps.readVarInt(buffer);
        byte[] data = new byte[length];
        buffer.readBytes(data);
        return new ParsedSingle(x, z, groundUp, mask, data, buffer.readableBytes());
    }

    private int readPacketId(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        return ByteBufOps.readVarInt(buffer);
    }

    /**
     * Reads until a packet of one of the wanted ids arrives (bounded by the
     * wall deadline; a quiet socket times out and re-reads; every other
     * packet is skipped).
     */
    private byte[] readUntil(TestClient18 client, IntPredicate wanted, long timeoutMs)
            throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        client.setReadTimeout(2_000);
        while (System.currentTimeMillis() < deadline) {
            byte[] payload;
            try {
                payload = client.readPacketForDiagnostics();
            } catch (SocketTimeoutException quiet) {
                continue;
            }
            if (wanted.test(readPacketId(payload))) {
                return payload;
            }
        }
        throw new IOException("Timed out waiting for a wanted packet id");
    }

    /**
     * Joins one client up to Login Success — the PLAY wave itself stays
     * UNREAD so the test's own capture loop can parse every chunk frame
     * (readUntilPositionAndLook would discard the wave ahead of the anchor).
     */
    private TestClient18 join(String name) throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        awaitPlayers(name);
        return client;
    }

    /** The parsed Position And Look anchor (the movement drive's own start). */
    private record Anchor(double x, double y, double z) {
    }

    /**
     * Captures the join's whole chunk wave: every 0x26 bulk and every 0x21
     * single (their columns collected distinct), with the bulk law asserted
     * per frame (2-10 columns, skylight boolean set, exact-end parse, every
     * array sized by its own mask), stopping at the view's full column count
     * or the deadline. The wave's 0x08 Position And Look, when seen, lands in
     * {@code anchorOut} (the movement drive's start point).
     */
    private Set<Long> captureJoinWave(TestClient18 client, long expectedColumns,
                                      long timeoutMs, Anchor[] anchorOut)
            throws IOException {
        Set<Long> distinct = new HashSet<>();
        int bulksSeen = 0;
        int singlesSeen = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        client.setReadTimeout(2_000);
        // Both ends of the wave: the full column set AND the trailing 0x08
        // anchor (the join sequence sends the position packet after the
        // chunk wave — the loop reads until both landed).
        while ((distinct.size() < expectedColumns || anchorOut[0] == null)
                && System.currentTimeMillis() < deadline) {
            byte[] payload;
            try {
                payload = client.readPacketForDiagnostics();
            } catch (SocketTimeoutException quiet) {
                continue;
            }
            int id = readPacketId(payload);
            if (id == Protocol18.S2C_CHUNK_BULK) {
                ParsedBulk bulk = parseBulk(payload);
                assertTrue(bulk.light,
                        "the bulk's skylight boolean is set (the engine's both-dimensions form)");
                assertTrue(bulk.count() >= 2 && bulk.count() <= 10,
                        "every bulk carries 2-10 columns (saw " + bulk.count() + ")");
                assertEquals(0, bulk.leftover(),
                        "every bulk frame parses to its exact end — arrays sized by masks alone");
                for (int i = 0; i < bulk.count(); i++) {
                    assertTrue(distinct.add(packed(new ChunkPosition(
                                    bulk.headers().get(i).x(), bulk.headers().get(i).z()))),
                            "no column rides twice");
                }
                bulksSeen++;
            } else if (id == Protocol18.S2C_CHUNK_DATA) {
                ParsedSingle single = parseSingle(payload);
                assertTrue(single.groundUp(), "the join's single columns are ground-up");
                assertEquals(fullFormArraySize(single.mask()), single.data().length,
                        "the single's varint length matches its mask's size law");
                assertEquals(0, single.leftover(), "the single frame parses to its exact end");
                assertTrue(distinct.add(packed(new ChunkPosition(single.x(), single.z()))),
                        "no column rides twice");
                singlesSeen++;
            } else if (id == Protocol18.S2C_PLAYER_POSITION_AND_LOOK && anchorOut[0] == null) {
                ByteBuf buffer = Unpooled.wrappedBuffer(payload);
                ByteBufOps.readVarInt(buffer); // the 0x08 id
                anchorOut[0] = new Anchor(buffer.readDouble(), buffer.readDouble(),
                        buffer.readDouble());
            }
        }
        System.out.println("[bulk-capture] " + distinct.size() + " columns over "
                + bulksSeen + " bulks + " + singlesSeen + " singles");
        return distinct;
    }

    /** The walk order of the view: dx outer, dz inner, around the center. */
    private static List<ChunkPosition> walkOrder(ChunkPosition center, int radius) {
        List<ChunkPosition> order = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                order.add(new ChunkPosition(center.x() + dx, center.z() + dz));
            }
        }
        return order;
    }

    private long packed(ChunkPosition position) {
        return position.packed();
    }

    // ---- the tests ------------------------------------------------------------

    /**
     * The join stream: the pregenerated view arrives batched — every bulk
     * carries 2-10 columns with the skylight boolean set, every column's
     * array sizes exactly by its mask, and the whole view is 81 distinct
     * columns across the bulk and single frames combined (the flat pregen
     * makes every column ready, so at least one bulk is guaranteed).
     */
    @Test
    void theJoinStreamBatchesThePregeneratedColumns() throws Exception {
        int view = server.engineConfig().viewDistance();
        long expectedColumns = (2L * view + 1) * (2L * view + 1);
        TestClient18 client = join("BulkJoin");
        try {
            Set<Long> distinct = captureJoinWave(client, expectedColumns, 20_000, new Anchor[1]);
            assertEquals(expectedColumns, distinct.size(),
                    "the whole view arrived: " + expectedColumns + " distinct columns");
        } finally {
            client.close();
        }
        awaitEmptyServer();
    }

    /**
     * The respawn re-stream — the deterministic byte-exact pin: a fully
     * loaded view re-streams as exactly 8 ten-column bulks plus the single
     * law for the last column, in the walk's own order, and every column's
     * bytes equal a direct serialization snapshotted in the SAME pre-handler
     * drain window (the snapshot job and the re-stream job run back-to-back
     * with no tick work between — and the flat world cannot mutate a byte
     * anyway).
     */
    @Test
    void theRespawnRestreamIsEightBulksAndTheLoneSingle() throws Exception {
        int view = server.engineConfig().viewDistance();
        long expectedColumns = (2L * view + 1) * (2L * view + 1);
        TestClient18 client = join("BulkRespawn");
        try {
            PlayerSession session = server.playerRegistry().byName("BulkRespawn").orElseThrow();
            // One pass over the whole join wave: the anchor + every column.
            Anchor[] holder = new Anchor[1];
            Set<Long> distinct = captureJoinWave(client, expectedColumns, 20_000, holder);
            assertEquals(expectedColumns, distinct.size(),
                    "the join's own stream completed: every column loaded");
            assertNotNull(holder[0], "the join's Position And Look anchor arrived");
            ChunkPosition center = new ChunkPosition(
                    (int) Math.floor(holder[0].x() / 16.0), (int) Math.floor(holder[0].z() / 16.0));
            List<ChunkPosition> order = walkOrder(center, view);
            assertEquals((2 * view + 1) * (2 * view + 1), order.size(),
                    "the walk covers the full view square");

            // The bed-spawn pin: the respawn's center is the standing spot.
            net.zaminmc.torch.util.Position standing = session.position();
            server.ticker().submit(() -> session.setBedSpawn(standing));
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 2,
                    "the bed-spawn pin");

            // The snapshot + the re-stream, back-to-back in ONE drain window:
            // the snapshot serializes every column the walk will re-send, and
            // nothing runs between the two jobs (the drain executes them
            // consecutively, before any tick section).
            Map<Long, byte[]> snapshot = new HashMap<>();
            Map<Long, Integer> snapshotMasks = new HashMap<>();
            server.ticker().submit(() -> {
                for (ChunkPosition position : order) {
                    var chunk = server.world().peek(position);
                    assertNotNull(chunk, "every view column is loaded after the join");
                    snapshot.put(packed(position), ChunkSerializer18.serialize(chunk, true));
                    snapshotMasks.put(packed(position), ChunkSerializer18.sectionBitmask(chunk));
                }
            });
            // performRespawn's re-stream job lands AFTER the snapshot job in the
            // same FIFO drain — the bytes are the same by construction.
            server.damage(session, 1000.0f);
            awaitCondition(session::dead, "the death landed");
            server.performRespawn(session);

            // The capture: exactly 8 bulks of 10 + the 81st column's single.
            List<ParsedBulk> bulks = new ArrayList<>();
            ParsedSingle loneSingle = null;
            ChunkPosition lastColumn = order.get(order.size() - 1);
            long captureDeadline = System.currentTimeMillis() + 15_000;
            client.setReadTimeout(2_000);
            while ((bulks.size() < 8 || loneSingle == null)
                    && System.currentTimeMillis() < captureDeadline) {
                byte[] payload;
                try {
                    payload = client.readPacketForDiagnostics();
                } catch (SocketTimeoutException quiet) {
                    continue;
                }
                int id = readPacketId(payload);
                if (id == Protocol18.S2C_CHUNK_BULK) {
                    ParsedBulk bulk = parseBulk(payload);
                    assertEquals(0, bulk.leftover(), "every bulk frame parses to its exact end");
                    bulks.add(bulk);
                } else if (id == Protocol18.S2C_CHUNK_DATA) {
                    ParsedSingle single = parseSingle(payload);
                    if (single.x() == lastColumn.x() && single.z() == lastColumn.z()) {
                        loneSingle = single;
                    }
                }
            }
            assertEquals(8, bulks.size(),
                    "81 loaded columns re-stream as 8 ten-column bulks (saw " + bulks.size() + ")");
            assertNotNull(loneSingle, "the 81st column keeps the reference's lone-single law");
            assertTrue(loneSingle.groundUp(), "the lone single is the ground-up full form");

            // The walk order, column by column, bulk by bulk — and the bytes.
            int cursor = 0;
            for (int bulkIndex = 0; bulkIndex < 8; bulkIndex++) {
                ParsedBulk bulk = bulks.get(bulkIndex);
                assertEquals(10, bulk.count(), "bulk " + bulkIndex + " carries ten columns");
                assertTrue(bulk.light, "the bulk's skylight boolean is set");
                for (int i = 0; i < 10; i++) {
                    ChunkPosition expected = order.get(cursor++);
                    BulkColumn header = bulk.headers().get(i);
                    assertEquals(expected.x(), header.x(),
                            "bulk " + bulkIndex + " column " + i + ": the walk's x order");
                    assertEquals(expected.z(), header.z(),
                            "bulk " + bulkIndex + " column " + i + ": the walk's z order");
                    assertEquals(snapshotMasks.get(packed(expected)).intValue(), header.mask(),
                            "the column's section mask equals the direct serialization's");
                    assertTrue(java.util.Arrays.equals(
                                    snapshot.get(packed(expected)), bulk.arrays()[i]),
                            "column (" + header.x() + "," + header.z()
                                    + ") is byte-identical to the direct serialization");
                }
            }
            assertEquals(snapshotMasks.get(packed(lastColumn)).intValue(), loneSingle.mask(),
                    "the lone single's mask equals the direct serialization's");
            assertEquals(snapshot.get(packed(lastColumn)).length, loneSingle.data().length,
                    "the lone single's varint length matches");
            assertTrue(java.util.Arrays.equals(snapshot.get(packed(lastColumn)), loneSingle.data()),
                    "the lone single is byte-identical to the direct serialization");
            assertEquals(0, loneSingle.leftover(), "the lone single parses to its exact end");
        } finally {
            client.close();
        }
        awaitEmptyServer();
    }

    /**
     * The movement arm: one east-chunk shift's newly-visible columns arrive
     * batched — the updateCenter walk collects exactly what became newly
     * visible (the strip PLUS the hysteresis geometry's own corner re-adds,
     * the pre-existing euclidean-drop law: the two old-square corners outside
     * the (view+1)-squared band drop and re-enter the same walk) and the
     * emission follows the reference's law: 2+ columns bulk, a remainder of
     * exactly one the lone single.
     */
    @Test
    void theMovementArmBatchesTheNewlyVisibleColumns() throws Exception {
        int view = server.engineConfig().viewDistance();
        long expectedInitial = (2L * view + 1) * (2L * view + 1);
        TestClient18 client = join("BulkWalk");
        try {
            PlayerSession session = server.playerRegistry().byName("BulkWalk").orElseThrow();
            // One pass over the whole join wave: the anchor + every column.
            Anchor[] holder = new Anchor[1];
            Set<Long> distinct = captureJoinWave(client, expectedInitial, 20_000, holder);
            assertEquals(expectedInitial, distinct.size(), "the join's stream completed");
            assertNotNull(holder[0], "the join's Position And Look anchor arrived");
            Anchor anchor = holder[0];
            ChunkPosition center = new ChunkPosition(
                    (int) Math.floor(anchor.x() / 16.0), (int) Math.floor(anchor.z() / 16.0));
            ChunkPosition shifted = new ChunkPosition(center.x() + 1, center.z());

            // The engine's own newly-visible law, computed from the geometry:
             // the old square's survivors keep their seats (the euclidean
            // hysteresis band (view+1)-squared around the new center), every
            // other column of the new square is newly visible — in the walk's
            // own dx-then-dz order. On the default view this is the 9-column
            // strip plus the two dropped-and-re-added corners: eleven.
            List<ChunkPosition> expectedNewly = new ArrayList<>();
            for (int dx = -view; dx <= view; dx++) {
                for (int dz = -view; dz <= view; dz++) {
                    ChunkPosition column = new ChunkPosition(shifted.x() + dx, shifted.z() + dz);
                    boolean inOldSquare = Math.abs(column.x() - center.x()) <= view
                            && Math.abs(column.z() - center.z()) <= view;
                    long edx = column.x() - shifted.x();
                    long edz = column.z() - shifted.z();
                    boolean survivesHysteresis =
                            inOldSquare && (edx * edx + edz * edz) <= (long) (view + 1) * (view + 1);
                    if (!survivesHysteresis) {
                        expectedNewly.add(column);
                    }
                }
            }
            assertTrue(expectedNewly.size() >= 2,
                    "the shift makes at least a batch's worth newly visible (got "
                            + expectedNewly.size() + ")");
            // Pre-load the expected set so the walk's collection is fully
            // synchronous (the flat pregen covers only the initial square).
            server.ticker().submit(() -> {
                for (ChunkPosition column : expectedNewly) {
                    server.world().getOrGenerate(column);
                }
            });
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 2,
                    "the newly-visible set pre-loaded");

            // The honest walk: the movement guard's own envelope — at most 0.4
            // blocks per packet, 16 blocks east in 40 steps: exactly one
            // chunk-boundary crossing, one updateCenter, one emission wave.
            int steps = 40;
            for (int i = 1; i <= steps; i++) {
                client.sendPosition(anchor.x() + 16.0 * i / steps, anchor.y(), anchor.z(), true);
            }
            awaitCondition(() -> session.position().x() >= anchor.x() + 15.9,
                    "the walk's position landed east");

            // The capture: the whole emission wave, routed by frame — the
            // expected set in the walk's order, split by the reference's own
            // bulk/single law (2+ per bulk, at most 10 per packet, a remainder
            // of exactly one the lone single).
            List<ParsedBulk> bulks = new ArrayList<>();
            List<ParsedSingle> singles = new ArrayList<>();
            long captureDeadline = System.currentTimeMillis() + 15_000;
            int expectedFrames = (expectedNewly.size() / 10)
                    + (expectedNewly.size() % 10 >= 2 ? 1 : 0)
                    + (expectedNewly.size() % 10 == 1 ? 1 : 0);
            client.setReadTimeout(2_000);
            while (bulks.size() + singles.size() < expectedFrames
                    && System.currentTimeMillis() < captureDeadline) {
                byte[] payload;
                try {
                    payload = client.readPacketForDiagnostics();
                } catch (SocketTimeoutException quiet) {
                    continue;
                }
                int id = readPacketId(payload);
                if (id == Protocol18.S2C_CHUNK_BULK) {
                    ParsedBulk bulk = parseBulk(payload);
                    assertEquals(0, bulk.leftover(), "every bulk frame parses to its exact end");
                    bulks.add(bulk);
                } else if (id == Protocol18.S2C_CHUNK_DATA) {
                    ParsedSingle single = parseSingle(payload);
                    assertEquals(0, single.leftover(), "the single frame parses to its exact end");
                    singles.add(single);
                }
            }
            // The emission wave, column by column, frame by frame — the
            // walk's own order over the expected set.
            List<ChunkPosition> arrived = new ArrayList<>();
            for (ParsedBulk bulk : bulks) {
                assertTrue(bulk.light, "the skylight boolean is set");
                assertTrue(bulk.count() >= 2 && bulk.count() <= 10,
                        "every bulk carries 2-10 columns (saw " + bulk.count() + ")");
                for (int i = 0; i < bulk.count(); i++) {
                    arrived.add(new ChunkPosition(bulk.headers().get(i).x(),
                            bulk.headers().get(i).z()));
                }
            }
            for (ParsedSingle single : singles) {
                assertTrue(single.groundUp(), "the lone single is the ground-up full form");
                arrived.add(new ChunkPosition(single.x(), single.z()));
            }
            assertEquals(expectedNewly, arrived,
                    "the shift's emission wave is the expected set in the walk's order — "
                            + "bulks of (at most) ten, the remainder of one the lone single");
        } finally {
            client.close();
        }
        awaitEmptyServer();
    }
}
