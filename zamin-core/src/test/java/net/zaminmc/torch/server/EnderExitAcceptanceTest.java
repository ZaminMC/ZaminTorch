package net.zaminmc.torch.server;

import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The End exit walk (Slice 64 — the exit fountain's End-to-overworld
 * redirect, ServerPlayerEntity.changeDimension lines 444-457 +
 * handleClientStatus lines 740-744): the exit portal cell in dimension 1
 * removes the body from the world for the GameEvent-4 credits (no body
 * simulation, no movement), and the client's respawn confirmation lands
 * the WHOLE body in the overworld at the bed/world spawn — the alive
 * respawn that keeps health, food, and XP (copyFrom's comesFromTheEnd
 * carry). The death respawn's own walk (resetBody + dimension reset)
 * must survive the new arm ordering.
 */
class EnderExitAcceptanceTest {

    private java.nio.file.Path dataDir;

    /** The recording link: the GameEvent pairs the credits walk sends. */
    private static final class RecordingLink implements ClientLink {
        final List<int[]> gameEvents = new ArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void playGameState(int reason, float value) {
            gameEvents.add(new int[]{reason, (int) value});
        }
    }

    @Test
    void theExitFountainPlaysTheCreditsAndTheAliveRespawnCarriesTheBody() throws Exception {
        dataDir = Files.createTempDirectory("ender-exit");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "exit", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            RecordingLink link = new RecordingLink();
            var accepted = server.joinRequest(link, "Vanquisher", UUID.randomUUID());
            PlayerSession vanquisher = ((EngineBridge.Accepted) accepted).session();
            await(() -> server.playerRegistry().byName("Vanquisher").isPresent(),
                    "registered");
            server.joinCompleted(vanquisher);
            await(() -> vanquisher.state() == PlayerState.PLAYING, "playing");

            // The dragon-slayer's carried body: wounded and hungry before
            // the exit (copyFrom's comesFromTheEnd=true keeps it all).
            vanquisher.setBody(13.0f, 16, 0.4f);
            vanquisher.setTotalXp(net.zaminmc.torch.server.experience.ExperienceMath
                    .totalXpForLevel(7));

            // The exit fountain's cell in the End: the body stands on the
            // end_portal block the dragon's death walk wrote (the feet
            // cell IS the portal cell — the collision read). The cell
            // carries the fountain's own SUPPORT below it (the reference's
            // bedrock structure): a bare portal cell leaves the body
            // falling through the whole credits window, and the island
            // landing's fall debt eats a health point under a loaded
            // runner (run 562's 13.0 -> 12.0) — the carried-body pin
            // reads the exit's own law, not the fall's.
            vanquisher.applyDimensionArrival(1, new Position(0.5, 64.0, 0.5), 90.0f, 0.0f);
            EngineWorld endWorld = server.worldFor(1);
            server.ticker().submit(() -> {
                var endPortal = server.blockRegistry().require(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:end_portal"));
                var bedrock = server.blockRegistry().require(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:bedrock"));
                endWorld.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(0, 0));
                endWorld.setBlock(new net.zaminmc.torch.block.BlockPosition(0, 64, 0), endPortal);
                endWorld.setBlock(new net.zaminmc.torch.block.BlockPosition(0, 63, 0), bedrock);
            });

            // The walk: standing on the exit cell arms the credits state and
            // sends the GameEvent-4 win screen (ServerPlayerEntity
            // .changeDimension lines 446-449).
            await(() -> vanquisher.leavingTheEnd(), "the credits armed");
            await(() -> link.gameEvents.stream().anyMatch(e -> e[0] == 4),
                    "the win screen sent");
            assertTrue(link.gameEvents.stream().anyMatch(e -> e[0] == 4 && e[1] == 0),
                    "GameEvent 4 with the 0.0F value = the credits");
            assertEquals(1, link.gameEvents.stream().filter(e -> e[0] == 4).count(),
                    "the removed body never re-triggers the walk");

            // The removed body answers no movement (handlePlayerMove line
            // 198's leavingTheEnd gate).
            Position fountain = vanquisher.position();
            server.movementProposal(vanquisher,
                    new Position(fountain.x() + 5.0, fountain.y(), fountain.z()),
                    new Rotation(0.0f, 0.0f), true);
            EngineAwaits.awaitTicks(server.world(), 6, "let the submit drain");
            assertEquals(fountain.x(), vanquisher.position().x(), 1e-9,
                    "the credits body ignores movement proposals");

            // The client's respawn confirmation (Client Status 0x16 action 0
            // — handleClientStatus's leavingTheEnd arm): the ALIVE respawn.
            server.performRespawn(vanquisher);
            await(() -> !vanquisher.leavingTheEnd(), "the credits answered");
            await(() -> vanquisher.dimension() == 0, "the overworld landing");
            assertEquals(0, vanquisher.dimension());
            assertEquals(server.worldFor(0).spawnPosition().x(),
                    vanquisher.position().x(), 1e-9, "the world-spawn landing");
            // The whole body carried (copyFrom comesFromTheEnd): the health,
            // the food, the saturation, and the XP bar all survive.
            assertEquals(13.0f, vanquisher.health(), 1e-6f);
            assertEquals(16, vanquisher.food());
            assertEquals(0.4f, vanquisher.saturation(), 1e-6f);
            assertEquals(7, vanquisher.experienceLevel());
            assertFalse(vanquisher.dead(), "the exit is not a death");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theDeathRespawnStillResetsAfterTheCreditsArm() throws Exception {
        dataDir = Files.createTempDirectory("ender-death");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "death", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            var accepted = server.joinRequest(new RecordingLink(), "Doomed", UUID.randomUUID());
            PlayerSession doomed = ((EngineBridge.Accepted) accepted).session();
            await(() -> server.playerRegistry().byName("Doomed").isPresent(), "registered");
            server.joinCompleted(doomed);
            await(() -> doomed.state() == PlayerState.PLAYING, "playing");

            // The death walk (the reference's own arm order — the
            // leavingTheEnd check comes FIRST, the death gate after).
            server.damage(doomed, 100.0f);
            await(() -> doomed.dead(), "the lethal damage killed");
            server.performRespawn(doomed);
            await(() -> !doomed.dead(), "the respawn reset the body");
            assertEquals(PlayerSession.MAX_HEALTH, doomed.health());
            assertEquals(PlayerSession.MAX_FOOD, doomed.food());
            assertEquals(0, doomed.experienceLevel(), "the death respawn keeps no XP");
            assertFalse(doomed.leavingTheEnd(), "the death path never arms the credits");
        } finally {
            server.shutdown(null);
        }
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
