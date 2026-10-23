package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.sign.SignManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Open Sign Editor (0x36) over the real wire — the audit's ML-003 row: a
 * real 1.8.8 client's editor opens ONLY on this packet (the reference's
 * ServerPlayerEntity.openSignEditor lines 547-550 sends it on placement;
 * bot clients writing Update Sign 0x12 directly had masked its absence).
 * The placer's socket receives the exact reference wire form for the
 * placed cell; the observer's socket crosses the whole placement-and-edit
 * window without ever seeing one.
 */
class SignEditorOpenWireTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000; // the bounded reads must not race a keep-alive kick
    }

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            for (PlayerSession candidate : server.playerRegistry().all()) {
                if (candidate.name().equals(name)) {
                    return candidate;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("player never joined: " + name);
    }

    /**
     * The reference packet's own body form (OpenSignEditorS2CPacket.write
     * -> PacketByteBuf.writeBlockPos -> writeLong(BlockPos.toLong)): the
     * id varint then the packed position long — 9 bytes, nothing else.
     */
    private static byte[] expectedEditorFrame(BlockPosition position) {
        long packed = ((long) (position.x() & 0x3FFFFFF) << 38)
                | ((long) (position.y() & 0xFFF) << 26)
                | (position.z() & 0x3FFFFFF);
        return new byte[]{
                (byte) Protocol18.S2C_OPEN_SIGN_EDITOR,
                (byte) (packed >>> 56), (byte) (packed >>> 48),
                (byte) (packed >>> 40), (byte) (packed >>> 32),
                (byte) (packed >>> 24), (byte) (packed >>> 16),
                (byte) (packed >>> 8), (byte) packed};
    }

    @Test
    void thePlacerReceivesOpenSignEditorForThePlacedCell() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Placer");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            var session = awaitPlayer("Placer");

            // Ground truth: a floor block two columns east of the join
            // anchor (the SignIntegrationTest cell law — generated and dry
            // at spawn, clear of the placer's own bounding box).
            int gx = (int) Math.floor(session.position().x()) + 2;
            int gz = (int) Math.floor(session.position().z());
            int gy = (int) Math.floor(session.position().y()) - 1;
            var floor = new BlockPosition(gx, gy, gz);
            assertTrue(!server.world().getBlock(floor).equals(BuiltinBlocks.AIR),
                    "the floor cell exists");

            // Place: click the floor's top face with a sign (item 323).
            alice.sendChat("/give sign 1");
            alice.readWindowItems(5_000);
            alice.sendBlockPlacement(gx, gy, gz, 1, 323);
            var signCell = floor.offset(0, 1, 0);
            awaitCondition(
                    () -> SignManager.isSignType(server.world().getBlock(signCell)),
                    "the sign block commits");

            // The editor-open trigger: the placer's own socket receives
            // 0x36, and the frame is byte-identical to the reference
            // packet's write form — the id varint + the packed position
            // long for the placed cell, 9 bytes exactly, no tail.
            byte[] editor = alice.readPacketOfType(Protocol18.S2C_OPEN_SIGN_EDITOR, 10_000);
            assertArrayEquals(expectedEditorFrame(signCell), editor,
                    "the 0x36 frame is the reference packet's exact wire form");
            io.netty.buffer.ByteBuf editorBuffer =
                    io.netty.buffer.Unpooled.wrappedBuffer(editor);
            ByteBufOps.readVarInt(editorBuffer); // the id itself
            int[] decoded = ByteBufOps.decodePackedBlockPosition(editorBuffer.readLong());
            assertEquals(gx, decoded[0], "the editor names the sign's own column");
            assertEquals(gy + 1, decoded[1], "the editor names the placed cell's y");
            assertEquals(gz, decoded[2], "the editor names the sign's own z");
            assertEquals(9, editor.length, "no fields trail the position long");
        }
        awaitEmptyServer();
    }

    @Test
    void onlyThePlacerSeesTheEditorOpen() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Signer");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            awaitPlayer("Signer");
            alice.readWindowItems(5_000);

            bob.sendHandshake(47, 2);
            bob.sendLoginStart("Onlooker");
            bob.readLoginSuccess();
            bob.readUntilPositionAndLook();
            awaitPlayer("Onlooker");
            bob.readWindowItems(10_000);

            // The placement: Alice signs two columns east of her anchor —
            // the +4 column, clear of the standing test's +2 cell (this
            // class shares one world, so each test owns its own column)
            // and inside the survival placement reach.
            var aliceSession = server.playerRegistry().byName("Signer").orElseThrow();
            int gx = (int) Math.floor(aliceSession.position().x()) + 4;
            int gz = (int) Math.floor(aliceSession.position().z());
            int gy = (int) Math.floor(aliceSession.position().y()) - 1;
            alice.sendChat("/give sign 1");
            alice.readWindowSlotTable(10_000);
            alice.sendBlockPlacement(gx, gy, gz, 1, 323);
            var signCell = new BlockPosition(gx, gy + 1, gz);
            awaitCondition(
                    () -> SignManager.isSignType(server.world().getBlock(signCell)),
                    "the sign block commits");

            // The positive proof first: the placer's editor-open arrives.
            byte[] editor = alice.readPacketOfType(Protocol18.S2C_OPEN_SIGN_EDITOR, 10_000);
            assertArrayEquals(expectedEditorFrame(signCell), editor,
                    "the placer receives the reference wire form");

            // The editor closes: the four lines flow 0x12 as they already
            // do, and the write fans the 0x33 replay out to EVERY viewer —
            // Bob's stream is live and drained, so any 0x36 the placement
            // wrongly broadcast would have to show up ahead of the replay.
            String[] lines = {"hello", "from", "the", "placer"};
            alice.sendUpdateSign(gx, gy + 1, gz, lines);
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                byte[] payload = bob.readPacket();
                int id = bob.readPacketId(payload);
                if (id == Protocol18.S2C_OPEN_SIGN_EDITOR) {
                    fail("the observer's client must never receive 0x36");
                }
                if (id == Protocol18.S2C_UPDATE_SIGN) {
                    io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
                    ByteBufOps.readVarInt(buffer); // the id itself
                    int[] decoded = ByteBufOps.decodePackedBlockPosition(buffer.readLong());
                    if (decoded[0] == signCell.x() && decoded[1] == signCell.y()
                            && decoded[2] == signCell.z()) {
                        // The replay marker naming this sign: the whole
                        // placement window crossed Bob's socket and the
                        // editor-open never rode it.
                        assertArrayEquals(lines, new String[]{
                                ByteBufOps.readString(buffer, 128),
                                ByteBufOps.readString(buffer, 128),
                                ByteBufOps.readString(buffer, 128),
                                ByteBufOps.readString(buffer, 128)},
                                "the marker replay carries the placer's lines");
                        return;
                    }
                }
            }
            fail("the 0x33 replay marker never reached the observer");
        }
    }

    @Test
    void theEditorFramePinsTheReferenceBodyAcrossTheCoordinateBand() {
        // The pure codec law of the 0x36 body: the id varint + the packed
        // position long — the reference's own toLong arithmetic (x 26 bits
        // at 38, y 12 bits at 26, z 26 bits at 0) over the coordinate band
        // the sign placements can meet, negatives included.
        int[][] cells = {
                {2, 5, 2},
                {-13, 63, -7},
                {1, 255, -1},
                {0, 0, 0},
                {-1, 15, -1}};
        for (int[] cell : cells) {
            var position = new BlockPosition(cell[0], cell[1], cell[2]);
            io.netty.buffer.ByteBuf out = io.netty.buffer.Unpooled.buffer(16);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_SIGN_EDITOR);
            ByteBufOps.writePackedBlockPosition(out, cell[0], cell[1], cell[2]);
            byte[] frame = new byte[out.readableBytes()];
            out.readBytes(frame);
            assertArrayEquals(expectedEditorFrame(position), frame,
                    "the writer carries the reference packet's body at " + position);
        }
    }
}
