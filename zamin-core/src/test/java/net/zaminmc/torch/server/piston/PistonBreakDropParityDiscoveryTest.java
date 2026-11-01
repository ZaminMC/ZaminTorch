package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FlowerPotBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-023's discovery seats — the piston toBreak DROP consult (the
 * reference's law, 1.8.8): the toBreak consumption
 * (PistonBaseBlock.move lines 303-309) calls
 * {@code block.dropItems(world, pos, state, 0)} — the standard NO-TOOL
 * drop walk (the same law the fluid spread's displaced block carries,
 * FlowingLiquidBlock.spreadTo lines 119-125) — and then
 * {@code world.removeBlock(pos)}, whose WorldChunk.setBlockState line 503
 * dispatches the old block's {@code onRemoved} (the flower pot's plant
 * item drops BESIDE the pot, FlowerPotBlock lines 149-156). Every drop
 * entry rolls; the family arms run (the leaves' sapling chance).
 *
 * <p>THE CONTRACT: these seats are RED on the #126 tree WITHOUT this
 * fix — the piston's {@code dropBlock} read the behavior table locally
 * and STOPPED at the first successful roll, so a piston-broken potted
 * plant dropped the pot ALONE (the plant's entry never rolled) and a
 * piston-broken mature crop dropped the wheat alone (the seeds never
 * rolled). On plain develop the seats are red for VT-016's reason
 * first (the pots and crops are PUSHED, never broken) — this finding
 * stacks on PR #126's table exactly as recorded.</p>
 */
class PistonBreakDropParityDiscoveryTest {

    /** The reference direction id for EAST (the tests push along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    @Test
    void thePistonBrokenPottedPlantDropsPotAndPlant() throws Exception {
        EngineServer server = boot();
        try {
            int x = 310, z = 310, y = 40;
            server.ticker().submit(() -> {
                // The piston facing east; the potted dandelion at the head's
                // cell (Material.DECORATION — the destroy arm), the floor row
                // under the pair, the lever on the piston's top.
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        FlowerPotBlocks.state(2)); // potted_dandelion
                for (int i = 1; i <= 3; i++) {
                    server.world().setBlock(new BlockPosition(x + i, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            await(() -> typeAt(server, x + 1, y, z).identifier().value()
                            .equals("potted_dandelion"),
                    "the potted dandelion placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));

            // Vanilla: the destroy arm breaks the pot with its drops — the
            // flower_pot AND the contained dandelion (dropItems + onRemoved).
            // The engine before this fix dropped the pot ALONE (the local
            // read's first-success break).
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston extended over the pot");
            await(() -> PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "the head landed where the pot stood");
            await(() -> dropsNear(server, x + 1, y, z).contains("flower_pot"),
                    "the broken pot dropped its flower_pot item");
            await(() -> dropsNear(server, x + 1, y, z).contains("dandelion"),
                    "VT-023: the broken pot dropped the contained dandelion "
                            + "too (onRemoved's plant item — every entry rolls)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePistonBrokenMatureWheatDropsWheatAndSeeds() throws Exception {
        EngineServer server = boot();
        try {
            int x = 330, z = 330, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                // The mature wheat crop at the head's cell (Material.PLANT —
                // the destroy arm), farmland under it, the lever.
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.WHEAT_STAGE7);
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z),
                        BuiltinBlocks.FARMLAND);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            await(() -> typeAt(server, x + 1, y, z).identifier().value()
                            .equals("wheat_stage7"),
                    "the mature crop placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));

            // Vanilla: the destroy arm breaks the crop with its drops — the
            // wheat AND the seeds (the row's two entries, each rolling).
            // The engine before this fix dropped the wheat ALONE.
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston extended over the crop");
            await(() -> dropsNear(server, x + 1, y, z).contains("wheat"),
                    "the broken crop dropped its wheat");
            await(() -> dropsNear(server, x + 1, y, z).contains("wheat_seeds"),
                    "VT-023: the broken crop dropped the seeds too (every "
                            + "entry rolls — the dig path's own walk)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /** @return the item ids dropped near a cell (the toBreak census). */
    private static Set<String> dropsNear(EngineServer server, int x, int y, int z) {
        AtomicReference<Set<String>> found = new AtomicReference<>(new HashSet<>());
        server.ticker().submit(() -> {
            Set<String> ids = new HashSet<>();
            for (net.zaminmc.torch.server.entity.ItemEntity item : server.itemEntities().all()) {
                Position at = item.position();
                if (Math.abs(at.x() - (x + 0.5)) <= 2.5 && Math.abs(at.y() - y) <= 2.0
                        && Math.abs(at.z() - (z + 0.5)) <= 2.0) {
                    ids.add(item.stack().type().identifier().value());
                }
            }
            found.set(ids);
        });
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && found.get().isEmpty()) {
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
                "pbd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
