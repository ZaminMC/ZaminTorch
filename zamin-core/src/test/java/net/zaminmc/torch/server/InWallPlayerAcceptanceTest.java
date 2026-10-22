package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PLAYER'S IN-WALL ARM (the in-wall slice's player half —
 * LivingEntity.baseTick lines 194-197 over the player's own body): a real
 * boot, a player moved into a stone cell at the eye band, the wound at the
 * hurt-i-frame cadence, and the border arm's else-if demotion (a walled
 * body never takes the border wound in the same tick).
 */
class InWallPlayerAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "inwall" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("timed out waiting for " + description);
    }

    private PlayerSession join(String name, Position at) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session =
                ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " reaches PLAYING");
        return session;
    }

    @Test
    void theWalledPlayerWoundsAtTheIFrameCadence() throws Exception {
        // The flat world's surface: place the player standing IN a stone
        // wall — a column of stone around the body, the eye cells solid.
        // Position (8.5, 5, 8.5) standing ON the surface: the body occupies
        // y 5..6.8, the eye ~6.62. A stone at the eye cell (8, 6, 8) makes
        // the eye band view-blocking.
        PlayerSession walker = join("Walker", new Position(8.5, 5.0, 8.5));
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(8, 6, 8), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(8, 7, 8), BuiltinBlocks.STONE);
        });
        awaitCondition(() -> server.world()
                        .getBlock(new BlockPosition(8, 6, 8)) == BuiltinBlocks.STONE,
                "the wall stone landed");
        float before = walker.health();
        await(() -> walker.health() < before, "the wall's first wound landed");
        assertEquals(before - 1.0f, walker.health(), 1e-6f, "one 1.0 wound");
        // The i-frame: no second wound inside the window; the next one
        // after ~10 ticks.
        float first = walker.health();
        Thread.sleep(150); // ~3 ticks: inside the window
        assertEquals(first, walker.health(), 1e-6f, "no wound inside the i-frame window");
        await(() -> walker.health() < first, "the second wound lands past the window");
        assertEquals(first - 1.0f, walker.health(), 1e-6f, "the second 1.0 wound");
    }

    @Test
    void aClearEyedPlayerNeverWounds() throws Exception {
        PlayerSession walker = join("Clear", new Position(8.5, 5.0, 8.5));
        float before = walker.health();
        Thread.sleep(1_500); // ~30 ticks with no wall anywhere near the eye
        assertEquals(before, walker.health(), 1e-6f, "no wall, no wound");
        assertTrue(walker.state() == net.zaminmc.torch.entity.PlayerState.PLAYING);
    }

    private void awaitCondition(BooleanSupplier condition, String description)
            throws InterruptedException {
        await(condition, description);
    }
}
