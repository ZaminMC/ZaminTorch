package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-019 — the block-change flush law pinned at the wire (the reference's
 * ChunkMap.ChunkHolder.sendChanges dispatch, ChunkMap.java lines 358-401,
 * driven once per tick from the engine's chunkMap section):
 *
 * <ul>
 * <li>ONE pending cell in a chunk → the single 0x23 with the reference's
 *     exact body (lines 360-365);</li>
 * <li>2-63 pending cells → ONE 0x22 Multi Block Change: i32 chunkX, i32
 *     chunkZ, varint count, then per cell the u16 packed position
 *     (localX << 12 | localZ << 8 | y, line 337) and the varint packed
 *     state (BlocksUpdateS2CPacket.write lines 40-49);</li>
 * <li>a full 64-cell ring → the partial 0x21 with ground-up false and the
 *     DIRTY-section mask (lines 369-372 — the overflow cells ride inside
 *     the rebuilt sections, the reference's own relief valve);</li>
 * <li>re-queued cells dedupe to their slot with the LATEST committed state
 *     (the reference re-reads the chunk at flush — the last commit of the
 *     tick is the same state);</li>
 * <li>per-chunk buffers never merge: two chunks flush two packets, in
 *     first-dirty order (the reference's dirty list order).</li>
 * </ul>
 *
 * <p><b>The determinism law of this suite</b>: the committed cells sit in
 * the flat test world's underground dirt band (y=1-2, BELOW the grass
 * surface at y=3), where nothing random-ticks — an underground cell shadows
 * no grass, so no decay/spread commit can ever interleave with the flush
 * under test, and the serialized sections cannot drift between the flush
 * and the byte compare. Every drive is the engine's own (a ticker submit's
 * setBlock commits on the tick lane; the flush rides the tick's chunkMap
 * section, which precedes the relight transport — the first 0x21 after a
 * full-ring drive IS the flush's dirty-mask partial), every wait is an
 * engine-condition await, and every negative window is a bounded
 * socket-timeout read (a quiet-wire assertion, never a hope-sleep).</p>
 */
class MultiBlockChangeWireTest extends ProtocolTestBase {

    /** No keep-alives inside the capture windows (the harness's law). */
    @Override
    protected long keepAliveIntervalMs() {
        return 10_000_000;
    }

    // ---- wire capture helpers ------------------------------------------------

    /** One parsed 0x23 Block Change body (id already consumed). */
    private record SingleChange(int x, int y, int z, int packedState, int leftover) {
    }

    /** One parsed 0x22 Multi Block Change body (id already consumed). */
    private record MultiChange(int chunkX, int chunkZ, int count, short[] cells, int[] states, int leftover) {
    }

    /**
     * Reads until a packet of one of the wanted ids arrives (bounded by the
     * wall deadline; a quiet socket times out and re-reads; every other
     * packet is skipped — time updates and the relight re-sends ride the
     * same wire).
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

    private int readPacketId(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        return ByteBufOps.readVarInt(buffer);
    }

    private SingleChange parseSingle(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // the 0x23 id
        int[] pos = ByteBufOps.readPackedBlockPosition(buffer);
        int state = ByteBufOps.readVarInt(buffer);
        return new SingleChange(pos[0], pos[1], pos[2], state, buffer.readableBytes());
    }

    private MultiChange parseMulti(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // the 0x22 id
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        int count = ByteBufOps.readVarInt(buffer);
        short[] cells = new short[count];
        int[] states = new int[count];
        for (int i = 0; i < count; i++) {
            cells[i] = buffer.readShort();
            states[i] = ByteBufOps.readVarInt(buffer);
        }
        return new MultiChange(chunkX, chunkZ, count, cells, states, buffer.readableBytes());
    }

    /**
     * The bounded quiet-wire window: after a flush answered, NO further
     * 0x22/0x23 may ride the wire for the window's span (well past a dozen
     * chunkMap sections — anything owed would have fired). The relight
     * re-send's 0x21 and the time sync's 0x03 are lawful here; the block
     * change forms are not.
     */
    private void assertWireQuietForBlockChanges(TestClient18 client, long windowMs)
            throws IOException {
        client.setReadTimeout((int) windowMs);
        long end = System.currentTimeMillis() + windowMs;
        while (System.currentTimeMillis() < end) {
            byte[] payload;
            try {
                payload = client.readPacketForDiagnostics();
            } catch (SocketTimeoutException expectedQuiet) {
                return; // the window closed with the wire quiet — the assertion holds
            }
            int id = readPacketId(payload);
            assertTrue(id != Protocol18.S2C_BLOCK_CHANGE && id != Protocol18.S2C_MULTI_BLOCK_CHANGE,
                    "no further block-change frame rides the wire (saw id 0x"
                            + Integer.toHexString(id) + ")");
        }
    }

    // ---- the drives ----------------------------------------------------------

    /** Joins one client and returns it (the house join sequence). */
    private TestClient18 join(String name) throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        awaitPlayers(name);
        return client;
    }

    /** The session's own anchor chunk — always in the client's tracked set. */
    private ChunkPosition anchorChunk(String name) {
        PlayerSession session = server.playerRegistry().byName(name).orElseThrow();
        return session.position().toBlockPosition().chunkPosition();
    }

    private boolean holds(BlockPosition cell, net.zaminmc.torch.block.BlockType type) {
        return server.world().getBlock(cell).identifier().equals(type.identifier());
    }

    /**
     * An inert underground cell in the given chunk's y=1/y=2 band (below the
     * flat world's grass surface at y=3): underground cells shadow nothing,
     * so no grass decay/spread commit can ever interleave. Indices 0-31 sit
     * at y=1 (x fastest, then z 0-1), 32+ at y=2 (z 0-2) — all distinct,
     * all in section 0.
     *
     * <p><b>The suite's cell allocation</b> — the PER_CLASS world persists
     * every test's writes, and a commit that rewrites the standing block
     * (stone over stone) is a NO-OP that never queues: every test owns a
     * disjoint index range (the full-ring drive takes 5-69, the small
     * drives take 70+), so no drive can ever eat another's cell.</p>
     */
    private static final int FULL_RING_FIRST_CELL = 5;
    private static final int FULL_RING_CELL_COUNT = 65;
    private static BlockPosition cellIn(ChunkPosition chunk, int index) {
        if (index < 32) {
            return new BlockPosition(chunk.x() * 16 + (index % 16), 1,
                    chunk.z() * 16 + (index / 16));
        }
        int rest = index - 32;
        return new BlockPosition(chunk.x() * 16 + (rest % 16), 2,
                chunk.z() * 16 + (rest / 16));
    }

    /** The u16 wire packing of a cell (the reference's line 337). */
    private static short packedCell(BlockPosition cell) {
        return (short) (cell.localX() << 12 | cell.localZ() << 8 | cell.y());
    }

    // ---- the tests ------------------------------------------------------------

    /** The single arm: one committed cell flushes ONE exact 0x23. */
    @Test
    void onePendingCellFlushesTheExactSingleBlockChange() throws Exception {
        TestClient18 client = join("SingleFlush");
        try {
            ChunkPosition chunk = anchorChunk("SingleFlush");
            BlockPosition cell = cellIn(chunk, 70);
            int expectedState = ChunkSerializer18.packedStateId(BuiltinBlocks.STONE);
            server.ticker().submit(() -> server.world().setBlock(cell, BuiltinBlocks.STONE));
            awaitCondition(() -> holds(cell, BuiltinBlocks.STONE), "the cell committed");
            byte[] payload = readUntil(client,
                    id -> id == Protocol18.S2C_BLOCK_CHANGE || id == Protocol18.S2C_MULTI_BLOCK_CHANGE,
                    5_000);
            assertEquals(Protocol18.S2C_BLOCK_CHANGE, readPacketId(payload),
                    "one pending cell keeps the reference's single 0x23");
            SingleChange change = parseSingle(payload);
            assertEquals(cell.x(), change.x(), "the cell's x");
            assertEquals(cell.y(), change.y(), "the cell's y");
            assertEquals(cell.z(), change.z(), "the cell's z");
            assertEquals(expectedState, change.packedState(), "the packed state (id << 4 | meta)");
            assertEquals(0, change.leftover(), "the 0x23 body parses to the exact frame end");
            assertWireQuietForBlockChanges(client, 700);
        } finally {
            client.close();
        }
        awaitEmptyServer();
    }

    /** The batch arm: two cells in one chunk flush ONE exact 0x22, arrival order. */
    @Test
    void twoPendingCellsFlushOneMultiBlockChange() throws Exception {
        TestClient18 client = join("BatchFlush");
        try {
            ChunkPosition chunk = anchorChunk("BatchFlush");
            BlockPosition first = cellIn(chunk, 71);
            BlockPosition second = cellIn(chunk, 72);
            int stoneState = ChunkSerializer18.packedStateId(BuiltinBlocks.STONE);
            int cobbleState = ChunkSerializer18.packedStateId(BuiltinBlocks.COBBLESTONE);
            server.ticker().submit(() -> {
                server.world().setBlock(first, BuiltinBlocks.STONE);
                server.world().setBlock(second, BuiltinBlocks.COBBLESTONE);
            });
            awaitCondition(() -> holds(second, BuiltinBlocks.COBBLESTONE), "both cells committed");
            byte[] payload = readUntil(client,
                    id -> id == Protocol18.S2C_BLOCK_CHANGE || id == Protocol18.S2C_MULTI_BLOCK_CHANGE,
                    5_000);
            assertEquals(Protocol18.S2C_MULTI_BLOCK_CHANGE, readPacketId(payload),
                    "two pending cells batch into the reference's 0x22");
            MultiChange batch = parseMulti(payload);
            assertEquals(chunk.x(), batch.chunkX, "the chunk's x");
            assertEquals(chunk.z(), batch.chunkZ, "the chunk's z");
            assertEquals(2, batch.count, "both cells in one packet");
            assertEquals(packedCell(first), batch.cells[0],
                    "the first cell's packed position (x << 12 | z << 8 | y)");
            assertEquals(packedCell(second), batch.cells[1],
                    "the second cell's packed position, arrival order kept");
            assertEquals(stoneState, batch.states[0], "the first cell's packed state");
            assertEquals(cobbleState, batch.states[1], "the second cell's packed state");
            assertEquals(0, batch.leftover, "the 0x22 body parses to the exact frame end");
            assertWireQuietForBlockChanges(client, 700);
        } finally {
            client.close();
        }
        awaitEmptyServer();
    }

    /** The dedupe law: a re-committed cell keeps ONE slot with the LATEST state. */
    @Test
    void aReCommittedCellDedupesToTheLatestState() throws Exception {
        TestClient18 client = join("DedupeFlush");
        try {
            ChunkPosition chunk = anchorChunk("DedupeFlush");
            BlockPosition cell = cellIn(chunk, 73);
            int dirtState = ChunkSerializer18.packedStateId(BuiltinBlocks.DIRT);
            server.ticker().submit(() -> {
                server.world().setBlock(cell, BuiltinBlocks.STONE);
                server.world().setBlock(cell, BuiltinBlocks.DIRT);
            });
            awaitCondition(() -> holds(cell, BuiltinBlocks.DIRT), "the cell re-committed to dirt");
            byte[] payload = readUntil(client,
                    id -> id == Protocol18.S2C_BLOCK_CHANGE || id == Protocol18.S2C_MULTI_BLOCK_CHANGE,
                    5_000);
            // One pending cell after the dedupe: the SINGLE arm carries the latest state.
            assertEquals(Protocol18.S2C_BLOCK_CHANGE, readPacketId(payload),
                    "a re-committed cell is ONE pending cell — the single 0x23");
            SingleChange change = parseSingle(payload);
            assertEquals(cell.x(), change.x(), "the cell's x");
            assertEquals(cell.y(), change.y(), "the cell's y");
            assertEquals(cell.z(), change.z(), "the cell's z");
            assertEquals(dirtState, change.packedState(),
                    "the latest committed state rides the flush (the reference's chunk re-read)");
            assertWireQuietForBlockChanges(client, 700);
        } finally {
            client.close();
        }
        awaitEmptyServer();
    }

    /**
     * The full-ring arm: 65 commits in one chunk in one tick fill the 64-cell
     * ring and overflow — the flush escalates to the partial 0x21 over the
     * dirty sections (the overflow cell's section rides the mask, the 65th
     * cell itself rides inside the rebuilt section), and the payload is the
     * exact partial serialization of the chunk's dirty sections.
     */
    @Test
    void theFullRingFlushesThePartialChunkOverTheDirtySections() throws Exception {
        TestClient18 client = join("FullRingFlush");
        try {
            ChunkPosition chunk = anchorChunk("FullRingFlush");
            List<BlockPosition> cells = new ArrayList<>();
            for (int i = 0; i < FULL_RING_CELL_COUNT; i++) {
                cells.add(cellIn(chunk, FULL_RING_FIRST_CELL + i));
            }
            BlockPosition overflow = cells.get(64);
            server.ticker().submit(() -> {
                for (BlockPosition cell : cells) {
                    server.world().setBlock(cell, BuiltinBlocks.STONE);
                }
            });
            awaitCondition(() -> holds(overflow, BuiltinBlocks.STONE), "all 65 cells committed");
            // The full ring flushes as the partial 0x21 — never the 0x22, never 65
            // singles. The flush's chunkMap section precedes the relight
            // transport, so the FIRST 0x21 after the drive is the flush's own.
            byte[] payload = readUntil(client,
                    id -> id == Protocol18.S2C_CHUNK_DATA
                            || id == Protocol18.S2C_BLOCK_CHANGE
                            || id == Protocol18.S2C_MULTI_BLOCK_CHANGE,
                    5_000);
            assertEquals(Protocol18.S2C_CHUNK_DATA, readPacketId(payload),
                    "a full 64-cell ring escalates to the partial 0x21 (the reference's relief valve)");
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(buffer); // the 0x21 id
            assertEquals(chunk.x(), buffer.readInt(), "the chunk's x");
            assertEquals(chunk.z(), buffer.readInt(), "the chunk's z");
            assertEquals(false, buffer.readBoolean(), "ground-up is FALSE — the partial form");
            int wireMask = buffer.readShort() & 0xFFFF;
            int dataLength = ByteBufOps.readVarInt(buffer);
            byte[] wireData = new byte[dataLength];
            buffer.readBytes(wireData);
            assertEquals(0, buffer.readableBytes(), "the partial body parses to the exact frame end");

            // The dirty section: the one band all 65 cells touched (y 1-2 →
            // section 0). The mask is the dirty band intersected with the
            // chunk's own sections — and the payload is that section's exact
            // partial serialization (the underground band cannot drift: it
            // random-ticks nothing).
            var worldChunk = server.world().peek(chunk);
            int expectedMask = ChunkSerializer18.effectiveSectionMask(worldChunk, 0b01);
            assertEquals(expectedMask, wireMask,
                    "the wire mask is the dirty section intersected with the chunk's own");
            byte[] expectedData = ChunkSerializer18.serialize(worldChunk, false, expectedMask);
            assertTrue(java.util.Arrays.equals(expectedData, wireData),
                    "the payload is the exact partial serialization of the dirty sections");
            assertEquals(1 << (overflow.y() >> 4), wireMask & (1 << (overflow.y() >> 4)),
                    "the overflow cell's section rides the dirty mask");
            assertWireQuietForBlockChanges(client, 700);
        } finally {
            client.close();
        }
        awaitEmptyServer();
    }

    /** The per-chunk split: two chunks flush two separate batches, first-dirty first. */
    @Test
    void perChunkBuffersFlushSeparatePacketsInFirstDirtyOrder() throws Exception {
        TestClient18 client = join("SplitFlush");
        try {
            ChunkPosition firstChunk = anchorChunk("SplitFlush");
            ChunkPosition secondChunk = new ChunkPosition(firstChunk.x() + 1, firstChunk.z());
            // TWO cells per chunk — one cell would take the reference's single
            // arm (ChunkMap.java lines 360-365); the split law shows itself at
            // two batches of two.
            BlockPosition firstA = cellIn(firstChunk, 74);
            BlockPosition firstB = cellIn(firstChunk, 75);
            BlockPosition secondA = cellIn(secondChunk, 74);
            BlockPosition secondB = cellIn(secondChunk, 75);
            server.ticker().submit(() -> {
                server.world().setBlock(firstA, BuiltinBlocks.STONE);
                server.world().setBlock(firstB, BuiltinBlocks.STONE);
                server.world().setBlock(secondA, BuiltinBlocks.STONE);
                server.world().setBlock(secondB, BuiltinBlocks.STONE);
            });
            awaitCondition(() -> holds(secondB, BuiltinBlocks.STONE),
                    "both chunks' cells committed");
            byte[] firstPayload = readUntil(client,
                    id -> id == Protocol18.S2C_MULTI_BLOCK_CHANGE, 5_000);
            MultiChange firstBatch = parseMulti(firstPayload);
            assertEquals(firstChunk.x(), firstBatch.chunkX, "the first-dirty chunk leads");
            assertEquals(firstChunk.z(), firstBatch.chunkZ, "the first-dirty chunk's z");
            assertEquals(2, firstBatch.count, "its own two cells");
            assertEquals(packedCell(firstA), firstBatch.cells[0], "the first cell's packed position");
            assertEquals(packedCell(firstB), firstBatch.cells[1], "the second cell's packed position");
            assertEquals(0, firstBatch.leftover, "the first batch parses to its exact end");
            byte[] secondPayload = readUntil(client,
                    id -> id == Protocol18.S2C_MULTI_BLOCK_CHANGE, 5_000);
            MultiChange secondBatch = parseMulti(secondPayload);
            assertEquals(secondChunk.x(), secondBatch.chunkX, "the second chunk follows");
            assertEquals(secondChunk.z(), secondBatch.chunkZ, "the second chunk's z");
            assertEquals(2, secondBatch.count, "its own two cells");
            assertEquals(packedCell(secondA), secondBatch.cells[0], "the third cell's packed position");
            assertEquals(packedCell(secondB), secondBatch.cells[1], "the fourth cell's packed position");
            assertEquals(0, secondBatch.leftover, "the second batch parses to its exact end");
            assertWireQuietForBlockChanges(client, 700);
        } finally {
            client.close();
        }
        awaitEmptyServer();
    }
}
