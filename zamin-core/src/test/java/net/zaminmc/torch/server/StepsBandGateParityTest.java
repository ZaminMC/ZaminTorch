package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The squid's own move (the fifteenth pass's re-adjudication tail —
 * the seat the ore-glow family's rejection uncovered):
 *
 * <p>The reference's {@code SquidEntity.moveRelative} override (lines
 * 154-157) is the FULL COLLIDING {@code this.move(vX, vY, vZ)} — not a
 * raw position integration. The pre-fix engine read it as integration
 * (Slice 16e): the stranded squid's sink velocity wrote the position
 * directly with no collision resolution, so every squid PHASED THROUGH
 * BLOCKS — a squid above any seabed sank out of the world (the void
 * plane finished it), a swimming squid crossed aquarium glass. The
 * stroke now rides the swept solver every walker uses: the stranded
 * squid GROUNDS on the surface it falls onto.</p>
 *
 * <p>The steps band's own gate (the band rides the applied move):
 * {@code SquidEntity.makesSteps()} (lines 73-75) is FALSE — the
 * grounded squid never lights redstone ore, whatever its physics walk
 * does. The engine's gate lacked the squid (and the bat, BatEntity
 * 144-146 — currently unreachable: the bat's own flight integration
 * still bypasses the walk, the follow-up row's own scope) and the
 * walker control keeps the seat honest: the default TRUE lights the
 * ore through the same landing walk.</p>
 */
class StepsBandGateParityTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The mob-kind exclusions (the full server — the pending intent)
    // ------------------------------------------------------------------

    @Test
    void theStrandedSquidGroundsAndNeverLightsTheOre() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> {
                world.setBlock(new BlockPosition(0, 4, 0),
                        BuiltinBlocks.REDSTONE_ORE);
                world.setBlock(new BlockPosition(8, 4, 0),
                        BuiltinBlocks.REDSTONE_ORE);
            });
            await(() -> world.getBlock(new BlockPosition(0, 4, 0))
                            .equals(BuiltinBlocks.REDSTONE_ORE)
                    && world.getBlock(new BlockPosition(8, 4, 0))
                            .equals(BuiltinBlocks.REDSTONE_ORE),
                    "the two ores seeded");

            // The stranded sink rides the FULL COLLIDING move (the
            // moveRelative override's own body): the squid falls onto the
            // ore and GROUNDS on its top face — the pre-fix raw
            // integration phased it through the world (this very await
            // timed out over the pre-fix tree, the squid under the
            // world).
            var squid = server.mobs().spawnAt(MobType.SQUID,
                    new Position(0.5, 12.0, 0.5));
            assertNotNull(squid, "the squid spawned");
            await(() -> squid.position().y() < 6.5 && squid.onGround(),
                    "the squid grounded on the ore");
            Thread.sleep(600);
            assertTrue(squid.onGround(), "the squid still grounded (no phasing)");
            assertTrue(squid.position().y() < 6.0,
                    "the squid rests on the surface (never under it)");
            assertEquals(BuiltinBlocks.REDSTONE_ORE,
                    world.getBlock(new BlockPosition(0, 4, 0)),
                    "the squid never lights the ore (makesSteps FALSE)");

            // The control (the default TRUE): a zombie over the second
            // ore lights it through the same landing walk — the seat's
            // own guard against a vacuous negative.
            var zombie = server.mobs().spawnAt(MobType.ZOMBIE,
                    new Position(8.5, 12.0, 0.5));
            assertNotNull(zombie, "the zombie spawned");
            await(() -> world.getBlock(new BlockPosition(8, 4, 0))
                            .equals(BuiltinBlocks.LIT_REDSTONE_ORE),
                    "the zombie's landing lit the control ore");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "stepsgate",
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timeout: " + description);
            }
            Thread.sleep(25);
        }
    }
}
