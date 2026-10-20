package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The piston's entity displacement (the VIII13 slice — the reference's
 * MovingBlockEntity.moveEntities, lines 80-115): the interpolated
 * collision box over the moving carrier's flight, the push of the bodies
 * standing in it (the mobs, the items, the vehicles — the three solver
 * families), the amounts (the extending steps' delta + 0.0625 catch-up,
 * the completion's flat 0.25), the wall clip (a pushed body stops at the
 * block — the crush setup), and the slime-block fling (the velocity set
 * along the facing axis).
 */
class PistonEntityPushTest {

    /** The reference direction id for EAST (the tests push along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    @Test
    void theExtendingPistonPushesTheMobAlongTheFlightPath() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.MobEntity> mob =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                // The piston facing east with the stone it pushes; the mob
                // stands in the stone's landing cell (the flight path).
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                mob.set(server.mobs().spawnAt(MobType.PIG,
                        new Position(x + 2.5, y, z + 0.5)));
            });
            await(() -> mob.get() != null, "the mob spawned");
            double before = mob.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            // The mob rode the flight: the extending steps' amounts (0.5625
            // at the half-step + 0.5625 at the arrival + the completion's
            // flat 0.25 over the landing cell) — the total displacement at
            // least the completion's own 0.25, clamped only by a wall (none
            // here — the flat world's open air).
            assertTrue(mob.get().position().x() > before + 0.24,
                    "the mob rode the piston's flight east (before " + before
                            + ", after " + mob.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePushClampsAtTheWall() throws Exception {
        EngineServer server = boot();
        try {
            int x = 30, z = 30, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.MobEntity> mob =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                // The wall one cell past the landing: the pushed mob clamps
                // against it (the solver's clip — the crush setup).
                server.world().setBlock(new BlockPosition(x + 3, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 3, y + 1, z), BuiltinBlocks.STONE);
                mob.set(server.mobs().spawnAt(MobType.PIG,
                        new Position(x + 2.5, y, z + 0.5)));
            });
            await(() -> mob.get() != null, "the mob spawned");
            double before = mob.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            // The wall at x+3: the mob's box (0.9 wide) clamps at
            // (x+3) - 0.45 — the push never moves it INTO the wall.
            assertTrue(mob.get().position().x() <= x + 3 - 0.44,
                    "the mob clamped at the wall (x " + mob.get().position().x() + ")");
            assertTrue(mob.get().position().x() > before,
                    "the mob still rode the free part of the flight");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDroppedItemRidesThePush() throws Exception {
        EngineServer server = boot();
        try {
            int x = 50, z = 50, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> item =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                item.set(server.itemEntities().spawnDropAtBlock(
                        new Position(x + 2, y, z),
                        net.zaminmc.torch.item.ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 1),
                        0));
            });
            await(() -> item.get() != null, "the item spawned");
            double before = item.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            assertTrue(item.get().position().x() > before + 0.24,
                    "the item rode the piston's flight east (before " + before
                            + ", after " + item.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSlimeBlockFlingsTheBody() throws Exception {
        EngineServer server = boot();
        try {
            int x = 70, z = 70, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.MobEntity> mob =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                // The slime block the piston pushes (the engine's slime pad).
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.SLIME_BLOCK);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                mob.set(server.mobs().spawnAt(MobType.PIG,
                        new Position(x + 2.5, y, z + 0.5)));
            });
            await(() -> mob.get() != null, "the mob spawned");
            double before = mob.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            // The fling (MovingBlockEntity lines 94-107): the velocity SET
            // along the facing axis to the facing offset — the east fling's
            // +1.0 per tick decaying over the flight. A plain push moves a
            // body at most 0.8125; the fling flies it SEVERAL blocks east
            // (the velocity's own integration, the friction decay).
            await(() -> mob.get().position().x() > before + 1.5,
                    "the slime block flung the mob east");
            assertTrue(mob.get().position().x() > before + 1.5,
                    "the slime block flung the mob east (before " + before
                            + ", after " + mob.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The harness
    // ------------------------------------------------------------------

    private BlockType typeAt(EngineServer server, int x, int y, int z) {
        return server.world().getBlock(new BlockPosition(x, y, z));
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ppt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
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
