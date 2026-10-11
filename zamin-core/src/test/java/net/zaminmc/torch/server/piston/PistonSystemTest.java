package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The piston on the live engine (Slice 9e) — the reference port of
 * PistonBaseBlock + PistonMoveStructureResolver + MovingBlockEntity: the
 * lever-driven extend (the head's two-tick flight, the pushed chain landing
 * one cell along), the plain retract (the head departs, the block stays),
 * the sticky pull (the block returns), the twelve-block budget, the
 * break-on-push of the decoration family, and the quasi-connectivity walk
 * around the position above the piston.
 */
class PistonSystemTest {

    /** The reference direction id for EAST (the tests push along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    @Test
    void theLeverExtendsThePistonAndTheHeadPushesTheBlock() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                // The piston facing east; the stone it will push sits at the
                // head's cell; a floor lever rides the piston's top.
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            // The power on: the extend event fires, the moving head and the
            // pushed stone fly their two ticks, then land.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "the head landed at the cell in front of the piston");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            await(() -> typeAt(server, x + 1, y, z).identifier().value().startsWith("piston_head"),
                    "the stone left its old cell to the head");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePlainPistonRetractLeavesTheBlockBehind() throws Exception {
        EngineServer server = boot();
        try {
            int x = 20, z = 20, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston extended");
            // The power off: the retract event fires, the head departs, the
            // pushed stone stays where it landed.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> !PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped back to its retracted pair");
            await(() -> typeAt(server, x + 1, y, z).identifier().value().equals("air"),
                    "the head departed with the retract");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the plain piston left its pushed stone behind");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theStickyPistonPullsTheBlockBack() throws Exception {
        EngineServer server = boot();
        try {
            int x = 30, z = 30, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, true));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the sticky piston placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the sticky piston extended");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the stone pushed one cell along");
            // The power off: the sticky arm pulls the stone back adjacent.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> !PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the sticky piston retracted");
            await(() -> typeAt(server, x + 1, y, z).identifier().value().equals("stone"),
                    "the sticky arm pulled the stone back to the head's cell");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theTwelveBlockBudgetRefusesTheThirteenth() throws Exception {
        EngineServer server = boot();
        try {
            int x = 40, z = 40, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                // Thirteen blocks in the column: the resolver refuses.
                for (int i = 1; i <= 13; i++) {
                    server.world().setBlock(new BlockPosition(x + i, y, z), BuiltinBlocks.STONE);
                }
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            // Give the (wrongly armed) extend time to never happen: the
            // piston stays retracted, the column untouched.
            Thread.sleep(400);
            assertTrue(!PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "thirteen blocks refuse the extend");
            assertTrue(typeAt(server, x + 1, y, z).identifier().value().equals("stone"),
                    "the column head never moved");
            // The depower/re-power with twelve blocks extends: the far cell
            // clears, then a placement beside the piston rings its update
            // (the vanilla rule: a piston re-checks only when an adjacent
            // block changes).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x + 13, y, z),
                        server.world().airType());
                server.world().setBlock(new BlockPosition(x, y, z + 1), BuiltinBlocks.STONE);
            });
            await(() -> typeAt(server, x + 13, y, z).identifier().value().equals("air"),
                    "the thirteenth cell cleared");
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "twelve blocks fit the budget: the piston extends");
            await(() -> typeAt(server, x + 13, y, z).identifier().value().equals("stone"),
                    "the column shifted one cell along");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePushBreaksTheRedstoneTorchAndDropsIt() throws Exception {
        EngineServer server = boot();
        try {
            int x = 50, z = 50, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                // A standing torch in the push column (on its floor).
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.TORCH);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston extended over the broken torch");
            await(() -> PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "the head landed where the torch stood");
            // The torch dropped as an item at the break cell.
            await(() -> torchDropNear(server, x + 1, y, z), "the torch dropped as an item");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theQuasiPowerAroundThePositionAboveExtends() throws Exception {
        EngineServer server = boot();
        try {
            int x = 60, z = 60, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                // The block above the piston carries a lever on its east
                // face: the quasi region's power source, mounted clear of the
                // push row so it survives the extension. The push row clears
                // of terrain.
                server.world().setBlock(new BlockPosition(x, y + 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y + 1, z),
                        RedstoneBlocks.leverOf(1, false));
                for (int dx = 1; dx <= 5; dx++) {
                    server.world().setBlock(new BlockPosition(x + dx, y, z),
                            server.world().airType());
                }
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            await(() -> RedstoneBlocks.isLever(typeAt(server, x + 1, y + 1, z)),
                    "the quasi lever placed");
            Thread.sleep(300);
            assertTrue(!PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "an unpowered piston stays retracted");
            // Power the quasi region: the lever beside the block above. The
            // piston still needs an adjacent update to notice (the vanilla
            // quasi-connectivity rule — the lever is not the piston's
            // neighbor).
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 1, y + 1, z)));
            Thread.sleep(300);
            assertTrue(!PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the quasi power alone does not wake the piston");
            // The adjacent update: a placement beside the piston rings it.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y, z + 1), BuiltinBlocks.STONE));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the adjacent update plus the quasi walk extends the piston");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /** Whether a torch item drop sits near the cell. */
    private static boolean torchDropNear(EngineServer server, int x, int y, int z) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        server.ticker().submit(() -> {
            for (net.zaminmc.torch.server.entity.ItemEntity item : server.itemEntities().all()) {
                Position at = item.position();
                if (Math.abs(at.x() - (x + 0.5)) <= 2.0 && Math.abs(at.y() - y) <= 2.0
                        && Math.abs(at.z() - (z + 0.5)) <= 2.0
                        && item.stack().type().identifier().value().equals("torch")) {
                    found.set(true);
                }
            }
        });
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && !found.get()) {
            sleep(50);
        }
        return found.get();
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "pst" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
