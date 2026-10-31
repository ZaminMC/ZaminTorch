package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F-EX3's wire seat — the blast over the REDSTONE CLOCK (the loudest of
 * the 107 fallback families): the reference's own registrations leave the
 * repeater, the comparator, the lever and the tripwire at blast-resistance
 * READ 0/0.5 (Block.java lines 1195-1196, 1319-1321, 1135, 1285) — a
 * creeper's blast shreds a clock where it stands. The pre-fix engine
 * answered the flattened STATE ids (repeater_east_2, comparator_south_
 * subtract, lever_up_x, tripwire_...) through blastResistance's fallback
 * as MAX_VALUE — the clock SURVIVED a point-blank blast.
 */
class BlastResistanceFallbackParityTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "fex3" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    @Test
    void theBlastShredsTheRedstoneClock() throws Exception {
        EngineServer server = boot();
        try {
            // The clock's parts on an OBSIDIAN shelf (READ 1200 — the
            // creeper's rays cannot afford it, so the parts' SUPPORT
            // survives the blast and only the rays themselves can remove
            // them — the discrimination the bare plain cannot give: there
            // the crater under the parts pops them whatever their
            // resistance). A repeater, a comparator and a ground-standing
            // lever (the up_x axis form) — the flattened STATE forms the
            // pre-fix fallback caught.
            BlockPosition repeater = new BlockPosition(15, 5, 10);
            BlockPosition comparator = new BlockPosition(16, 5, 10);
            BlockPosition lever = new BlockPosition(17, 5, 10);
            server.ticker().submit(() -> {
                for (int x = 14; x <= 18; x++) {
                    for (int z = 9; z <= 12; z++) {
                        server.world().setBlock(new BlockPosition(x, 4, z),
                                BuiltinBlocks.OBSIDIAN);
                    }
                }
                server.world().setBlock(repeater,
                        RedstoneBlocks.repeaterOf(3, 2, false));
                server.world().setBlock(comparator,
                        RedstoneBlocks.comparatorOf(3, false, false));
                server.world().setBlock(lever,
                        RedstoneBlocks.leverOf(6, true));
            });
            // The settle await: every placement holds (a support pop would
            // fail the seat loudly here, never passing it falsely).
            await(server, () -> server.world().getBlock(repeater)
                            .identifier().value().equals("repeater_east_2")
                            && server.world().getBlock(comparator)
                            .identifier().value().equals("comparator_east_compare")
                            && server.world().getBlock(lever)
                            .identifier().value().equals("lever_up_x_powered"),
                    "the clock's parts placed and standing");

            // The creeper's own blast two cells south of the parts (power
            // 3 — its crater eats the plain, the obsidian shelf stands).
            var creeper = server.mobs().spawnAt(MobType.CREEPER,
                    new Position(16.5, 5, 12.5));
            assertNotNull(creeper, "the creeper spawned");
            server.ticker().submit(() -> {
                creeper.setCreeperIgnited(true);
                creeper.setCreeperFuseTime(5);
            });

            // The blast fired: the creeper left the registry (the blast
            // replaces the body — no death animation, no loot).
            int creeperId = creeper.entityId();
            await(server, () -> server.mobs().all().stream()
                            .noneMatch(m -> m.entityId() == creeperId),
                    "the creeper detonated");

            // Vanilla's law: READ 0 (the repeater, the comparator) and
            // READ 0.5 (the lever) — the rays pay ~nothing, every part of
            // the clock is torn off its shelf. The pre-fix engine's
            // MAX_VALUE fallback left all three standing untouched on the
            // same shelf (the support-pop road closed by the obsidian).
            await(server, () -> server.world().getBlock(repeater)
                            .identifier().value().equals("air"),
                    "the repeater shredded (READ 0, never blast-proof)");
            await(server, () -> server.world().getBlock(comparator)
                            .identifier().value().equals("air"),
                    "the comparator shredded (READ 0, never blast-proof)");
            await(server, () -> server.world().getBlock(lever)
                            .identifier().value().equals("air"),
                    "the lever shredded (READ 0.5, never blast-proof)");
            // The shelf itself stands (the control's own control — the
            // blast's geometry held, the parts died by their resistance
            // alone).
            await(server, () -> server.world().getBlock(new BlockPosition(15, 4, 10))
                            .identifier().value().equals("obsidian"),
                    "the obsidian shelf survives (READ 1200)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    private static void await(EngineServer server, BooleanSupplier condition,
                              String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            java.util.concurrent.atomic.AtomicReference<Boolean> ran =
                    new java.util.concurrent.atomic.AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
        }
        assertEquals(true, false, "Timed out waiting for " + description);
    }
}
