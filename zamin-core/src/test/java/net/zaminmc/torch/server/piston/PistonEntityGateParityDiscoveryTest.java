package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-022's discovery seats — the piston's block-entity gate (the
 * reference's law, 1.8.8): {@code PistonBaseBlock.canMoveBlock}'s final
 * arm (lines 273-284) answers {@code !(block instanceof BlockEntity-
 * Provider)} — EVERY block-entity-backed block is immovable, whatever
 * its material: the chests (both flavors), the furnaces, the dispenser,
 * the dropper, the hopper, the brewing stand, the ender chest, the
 * signs, the banners, the skulls, the beacon, the enchanting table,
 * the jukebox, the note block, the spawner, the command block, the
 * daylight detector (each traced to its BlockWithBlockEntity class in
 * the reference) — the flower pot ALONE rides its Material.DECORATION
 * destroy arm past the gate (a piston DESTROYS pots).
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the
 * engine's {@code PistonSystem.isBlockEntityBacked} answers TRUE for
 * exactly five ids ({@code chest}, {@code furnace}, {@code furnace_lit},
 * {@code sign}, {@code sign_*}); every other entity-backed block RIDES
 * the push, and {@code move()}'s relocation writes the moving block
 * with NO container state (the MovingBlock record carries the BlockType
 * alone) — a pushed trapped chest, dispenser, dropper, hopper or
 * brewing stand LOSES ITS CONTENTS (the persistent-data-loss class);
 * a pushed skull or banner simply moves where vanilla refuses.</p>
 */
class PistonEntityGateParityDiscoveryTest {

    /** The reference direction id for EAST (the tests push along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    @Test
    void theTrappedChestRefusesThePushAndKeepsItsContents() throws Exception {
        EngineServer server = boot();
        try {
            int x = 210, z = 210, y = 40;
            BlockPosition chestAt = new BlockPosition(x + 1, y, z);
            server.ticker().submit(() -> {
                // The piston facing east; the trapped chest at the head's
                // cell (an overworld family member — Slice 49b joined it),
                // the floor row under the pair, the lever on the piston.
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(chestAt, BuiltinBlocks.TRAPPED_CHEST);
                for (int i = 1; i <= 3; i++) {
                    server.world().setBlock(new BlockPosition(x + i, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            await(() -> BuiltinBlocks.TRAPPED_CHEST.equals(typeAt(server, x + 1, y, z)),
                    "the trapped chest placed");
            server.ticker().submit(() -> server.chests().getOrCreate(chestAt)
                    .setSlot(0, ItemStack.of(BuiltinItems.EMERALD, 5)));
            await(() -> {
                var entity = server.chests().peek(chestAt);
                return entity != null && entity.snapshotSlots()[0].count() == 5;
            }, "the trapped chest holds its emeralds");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));

            // Vanilla (BlockWithBlockEntity → canMoveBlock's final arm):
            // the push REFUSES — the piston NEVER EXTENDS while the lever
            // stays on, the trapped chest STANDS at its cell with its
            // contents. The engine today pushes it (the extension fires,
            // the chest rides as a moving block, the relocation carries no
            // container state — the emeralds unreachable through the moved
            // chest's own cell: the contents are LOST).
            await(() -> RedstoneBlocks.leverPowered(typeAt(server, x, y + 1, z)),
                    "the lever flipped on");
            sleep(1500);
            assertFalse(PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "VT-022: the trapped chest refuses the push (the "
                            + "block-entity arm — the piston stays retracted)");
            assertTrue(BuiltinBlocks.TRAPPED_CHEST.equals(typeAt(server, x + 1, y, z)),
                    "VT-022: the trapped chest stands where it stood (the "
                            + "block-entity arm never moves it)");
            var entity = server.chests().peek(chestAt);
            assertTrue(entity != null && entity.snapshotSlots()[0].count() == 5,
                    "VT-022: the trapped chest keeps its emeralds (the "
                            + "contents survive the refused push)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSkullRefusesThePush() throws Exception {
        EngineServer server = boot();
        try {
            int x = 230, z = 230, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                // The skull at the head's cell (BlockContainer in the
                // reference), a floor cell under it.
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.SKULL);
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");
            await(() -> BuiltinBlocks.SKULL.equals(typeAt(server, x + 1, y, z)),
                    "the skull placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));

            // Vanilla (BlockContainer → the entity arm): the push REFUSES
            // outright. The engine today moves the skull a cell along.
            await(() -> RedstoneBlocks.leverPowered(typeAt(server, x, y + 1, z)),
                    "the lever flipped on");
            sleep(1500);
            assertFalse(PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "VT-022: the skull refuses the push (the block-entity "
                            + "arm — the piston stays retracted)");
            assertFalse(PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "VT-022: no piston head ever landed in the skull's cell");
            assertTrue(BuiltinBlocks.SKULL.equals(typeAt(server, x + 1, y, z)),
                    "VT-022: the skull stands where it stood");
        } finally {
            server.shutdown(() -> { });
        }
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
                "peg" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
