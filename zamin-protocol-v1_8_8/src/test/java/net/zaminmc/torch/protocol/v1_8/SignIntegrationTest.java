package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.sign.SignManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signs end-to-end: the item places a standing sign block (facing its
 * placer), Update Sign (0x12) stores the four lines, Update Sign (0x33)
 * replays them to the writer, the chunk-send path re-sends them to a fresh
 * viewer, and the text survives the engine's save/load round trip.
 */
class SignIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000; // the replay sleeps must not race a keep-alive kick
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

    @Test
    void signsPlaceWriteAndReplay() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            var session = awaitPlayer("Alice");

            // Ground truth: a floor block two columns east of the join
            // anchor (the chunk is generated and dry at spawn; the offset
            // keeps the sign out of the placer's own bounding box, the
            // generic placement gate).
            int gx = (int) Math.floor(session.position().x()) + 2;
            int gz = (int) Math.floor(session.position().z());
            int gy = (int) Math.floor(session.position().y()) - 1;
            var floor = new BlockPosition(gx, gy, gz);
            assertTrue(engine_server_holds(floor), "the floor cell exists");

            // Place: click the floor's top face with a sign (item 323).
            // Survival placement validates the engine's own inventory, so
            // Alice holds a real sign first (the /give feedback inventory
            // sync is consumed here).
            alice.sendChat("/give sign 1");
            alice.readWindowItems(5_000);
            alice.sendBlockPlacement(gx, gy, gz, 1, 323);
            var signCell = floor.offset(0, 1, 0);
            awaitCondition(
                    () -> SignManager.isSignType(server.world().getBlock(signCell)),
                    "the sign block commits");
            var signType = server.world().getBlock(signCell);
            assertEquals(BuiltinBlocks.SIGN_NORTH, signType,
                    "the join look is yaw 0 (south): the sign fronts its placer");

            // Write: the editor closes and the four lines arrive.
            String[] lines = {"Zamin", "Torch", "", "rules"};
            alice.sendUpdateSign(gx, gy + 1, gz, lines);

            // The writer is a viewer of her own edit: the 0x33 replay arrives.
            Object[] replay = alice.readSignUpdate(5_000);
            assertEquals(gx, ((Integer) replay[0]).intValue());
            assertEquals(gy + 1, ((Integer) replay[1]).intValue());
            assertEquals(gz, ((Integer) replay[2]).intValue());
            assertArrayEquals(lines, (String[]) replay[3]);
            assertArrayEquals(lines, server.signs().peek(signCell),
                    "the engine stored the text");

            // A fresh viewer relogs: the chunk replay carries the text.
            try (TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
                bob.sendHandshake(47, 2);
                bob.sendLoginStart("Bob");
                bob.readLoginSuccess();
                // The join sequence sends chunks BEFORE the position anchor,
                // so the replay rides inside that early window: scan for the
                // 0x33 without waiting for the position packet first.
                Object[] bobSees = bob.readSignUpdate(10_000);
                assertArrayEquals(lines, (String[]) bobSees[3],
                        "the chunk replay carries the stored lines");
                bob.readUntilPositionAndLook();
                awaitPlayers("Alice", "Bob");
            }
        }
        awaitEmptyServer();
    }

    /** The floor cell must be generated before a sign can ride it. */
    private boolean engine_server_holds(BlockPosition position) {
        return !server.world().getBlock(position).identifier()
                .equals(BuiltinBlocks.AIR.identifier());
    }
}
