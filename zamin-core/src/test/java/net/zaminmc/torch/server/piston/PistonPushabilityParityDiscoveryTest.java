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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-016's discovery seats — the piston pushability table's membership
 * (the reference's law, 1.8.8): the push behavior is the MATERIAL's own
 * piston setter (Material.java lines 10-43 — ANVIL and PORTAL and PISTON
 * and BARRIER {@code setBlocksPistonMove()}, the plants, the liquids, the
 * leaves, the fire, the decoration, the snow layer, the cactus, the
 * pumpkin, the egg, the cake and the cobweb
 * {@code setDestroyOnPistonMove()}, everything else PUSH) PLUS the
 * BLOCK-level overrides ({@code Block.getPistonMoveBehavior()} — BedBlock
 * returns 1, DoorBlock returns 1, AbstractPressurePlateBlock returns 1,
 * IceBlock returns 0, AbstractRailBlock returns 0).
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the
 * engine's {@code PistonSystem.pistonMoveBehavior} table omitted the bed
 * family, the door family, the weighted plates, the cocoa and the cobweb
 * (vanilla DESTROY: they BREAK with their drops), omitted the anvil
 * family (vanilla BLOCK: the piston REFUSES to extend), destroyed the
 * grass block (vanilla Material.GRASS is plain PUSH: the block MOVES),
 * and — the wider census the whole-registry pin
 * ({@code PistonPushabilityCensusTest}) holds — omitted the crop stages,
 * the saplings' real {@code <wood>_sapling} ids, the six woods' leaves,
 * the fluid state vocabulary (flowing_/falling_/still_), the vine's face
 * states, the double plants, the ladder, the tripwire family, the small
 * mushrooms, the nether wart, the lily, the stems, the melon, the
 * pumpkins' facing states, the cake bites, the potted forms and the
 * dragon egg (its old "egg" row keyed the ITEM id).</p>
 */
class PistonPushabilityParityDiscoveryTest {

    /** The reference direction id for EAST (the tests push along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    @Test
    void theBedBreaksUnderThePistonWithItsDrop() throws Exception {
        EngineServer server = boot();
        try {
            int x = 110, z = 110, y = 40;
            server.ticker().submit(() -> {
                // The piston facing east; the bed pair spans the push column
                // (the foot at the head's cell, the head one further — the
                // foot's facing is EAST so the head sits to its east), the
                // floor row under the pair, the lever on the piston's top.
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.BED_FOOT_EAST);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        BuiltinBlocks.BED_HEAD_EAST);
                for (int i = 1; i <= 3; i++) {
                    server.world().setBlock(new BlockPosition(x + i, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));

            // Vanilla (BedBlock.getPistonMoveBehavior() == 1, DESTROY): the
            // resolver's walk stops AT the destroy block (the reference's
            // own resolve() shape — the start cell goes to toDestroy and the
            // walk returns), so the FOOT breaks with its bed item and NEVER
            // rides the push; the HEAD's own clearing rides its
            // neighborChanged arm (BedBlock lines 156-165 — PR #114's lane,
            // the pair-integrity correction this seat composes with). The
            // engine today PUSHES the pair (the foot and the head both ride
            // — the far cell gains a bed state, no drop — VT-009's floating
            // survivor compounded).
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston extended over the bed");
            await(() -> PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "the head landed where the bed foot stood");
            await(() -> !isBedFamily(typeAt(server, x + 3, y, z)),
                    "VT-016: the bed family never rides the push (vanilla "
                    + "destroys it — BedBlock.getPistonMoveBehavior() == 1)");
            await(() -> bedDropNear(server, x + 1, y, z),
                    "VT-016: the broken bed foot dropped its bed item");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theAnvilRefusesThePush() throws Exception {
        EngineServer server = boot();
        try {
            int x = 130, z = 130, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                // The anvil at the head's cell, a floor cell under it (the
                // anvil stands on support through the observation).
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.ANVIL);
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));

            // Vanilla (Material.ANVIL.setBlocksPistonMove(), BLOCK): the
            // canMove gate refuses the anvil outright — the piston NEVER
            // EXTENDS while the lever stays on. The engine today pushes the
            // anvil (the extension fires, the anvil lands a cell along).
            await(() -> RedstoneBlocks.leverPowered(typeAt(server, x, y + 1, z)),
                    "the lever flipped on");
            sleep(1500);
            assertFalse(PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "VT-016: the anvil refuses the push (Material.ANVIL "
                    + "setBlocksPistonMove — the piston stays retracted)");
            assertFalse(PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "VT-016: no piston head ever landed in the anvil's cell");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theGrassBlockMovesUnderThePiston() throws Exception {
        EngineServer server = boot();
        try {
            int x = 150, z = 150, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.GRASS_BLOCK);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));

            // Vanilla (Material.GRASS — a plain material, no piston setter:
            // PUSH): the grass block MOVES with the push, landing one cell
            // along with its state whole. The engine today answers its
            // startsWith("grass") DESTROY row for the grass BLOCK — the
            // piston breaks it (dirt drops) instead of moving it.
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston extended");
            await(() -> typeAt(server, x + 2, y, z).identifier().value()
                            .equals("grass_block"),
                    "VT-016: the grass block rode the push one cell along "
                    + "(Material.GRASS is PUSH — the classic mover)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /** @return whether the cell holds any bed-family state. */
    private static boolean isBedFamily(BlockType type) {
        String value = type.identifier().value();
        return value.equals("bed") || value.startsWith("bed_");
    }

    private static boolean bedDropNear(EngineServer server, int x, int y, int z) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        server.ticker().submit(() -> {
            for (net.zaminmc.torch.server.entity.ItemEntity item : server.itemEntities().all()) {
                Position at = item.position();
                if (Math.abs(at.x() - (x + 1.0)) <= 2.5 && Math.abs(at.y() - y) <= 2.0
                        && Math.abs(at.z() - (z + 0.5)) <= 2.0
                        && item.stack().type().identifier().value().equals("bed")) {
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
                "ppd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
