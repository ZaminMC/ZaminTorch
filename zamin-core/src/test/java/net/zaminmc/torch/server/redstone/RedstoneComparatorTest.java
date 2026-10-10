package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
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
 * The comparator on the live engine (Slice 9d) — the reference port of
 * ComparatorBlock + ComparatorBlockEntity + the InventoryMenu.getAnalogSignal
 * container math: the chest fullness ladder (the exact reference arithmetic),
 * the analog emission into the wire at the output side, the through-solid
 * read, the compare gate against the side signal, the subtract mode, and
 * the container-content wake (the reference's markDirty ->
 * updateNeighborComparators fan-out).
 */
class RedstoneComparatorTest {

    @TempDir
    Path dataDir;

    @Test
    void theChestFullnessReadsTheExactReferenceLadder() {
        // InventoryMenu.getAnalogSignal (lines 541-558): the per-slot
        // fraction over min(inventoryMax, itemMax), averaged over the whole
        // 27-slot size, floor(f * 14) + 1 when any slot holds anything.
        assertEquals(0, RedstoneSystem.containerFullness(slots(), 64), "an empty chest reads 0");
        assertEquals(1, RedstoneSystem.containerFullness(slots(stack(1)), 64),
                "a single item still reads 1 (the any-non-empty +1)");
        assertEquals(1, RedstoneSystem.containerFullness(slots(stack(64)), 64),
                "one full stack: 1/27 of the chest, floor(14/27)=0, +1");
        assertEquals(2, RedstoneSystem.containerFullness(slots(stack(64), stack(64)), 64),
                "two full stacks: floor(28/27)=1, +1");
        assertEquals(7, RedstoneSystem.containerFullness(slots(stack(64), stack(64), stack(64),
                stack(64), stack(64), stack(64), stack(64), stack(64), stack(64), stack(64),
                stack(64), stack(64), stack(64)), 64),
                "thirteen full stacks: floor(14*13/27)=6, +1");
        assertEquals(1, RedstoneSystem.containerFullness(slots(halfStack()), 64),
                "a half stack: floor(14*0.5/27)=0, +1");
        assertEquals(15, RedstoneSystem.containerFullness(slots(
                stack(64), stack(64), stack(64), stack(64), stack(64), stack(64), stack(64),
                stack(64), stack(64), stack(64), stack(64), stack(64), stack(64), stack(64),
                stack(64), stack(64), stack(64), stack(64), stack(64), stack(64), stack(64),
                stack(64), stack(64), stack(64), stack(64), stack(64), stack(64)), 64),
                "a completely full chest reads 15");
        // The min() arm: a full stack of signs (max 16) fills its slot
        // fraction completely (16/min(64,16) = 1.0), so 16 slots of signs
        // read floor(14 * 16/27) + 1 = 9.
        assertEquals(9, RedstoneSystem.containerFullness(signs(16), 64),
                "sixteen full sign stacks in a 64-inventory: the item's own max stacks the fraction");
    }

    @Test
    void theComparatorReadsTheChestAndEmitsTheAnalogValue() throws Exception {
        EngineServer server = boot();
        try {
            // A chest at (10,40,10), the comparator at (11,40,10) facing
            // WEST (its input side), the wire chain at the output side
            // (12,40,10)+ reading the analog emission.
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                // The comparator's bed + the wires' beds.
                for (int dx = 1; dx <= 3; dx++) {
                    server.world().setBlock(new BlockPosition(x + dx, y - 1, z), BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
                server.world().setBlock(new BlockPosition(x + 2, y, z), RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x + 3, y, z), RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x + 1, y, z)), "the comparator placed");
            await(() -> wirePower(server, x + 2, y, z) == 0 && wirePower(server, x + 3, y, z) == 0,
                    "the empty chest keeps the comparator silent");

            // Thirteen full stacks: the reference's fullness 7.
            fill(server, x, y, z, 13, 64);
            await(() -> wirePower(server, x + 2, y, z) == 7,
                    "the wire at the output reads the analog value 7 (the chest is 13/27 full)");
            await(() -> wirePower(server, x + 3, y, z) == 6,
                    "the wire chain decays from the emission: 7 - 1 = 6");
            await(() -> RedstoneBlocks.comparatorPowered(typeAt(server, x + 1, y, z)),
                    "the comparator flipped to its powered pair");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theChestContentChangeWakesTheComparator() throws Exception {
        EngineServer server = boot();
        try {
            int x = 20, z = 20, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
                server.world().setBlock(new BlockPosition(x + 2, y, z), RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x + 1, y, z)), "the comparator placed");
            // The wake's rising arm: the fill re-arms the 2-tick reaction.
            fill(server, x, y, z, 1, 64);
            await(() -> wirePower(server, x + 2, y, z) == 1,
                    "one stack in the chest: the fullness 1 wakes the comparator");
            // The wake's falling arm: emptying the chest drops the output.
            server.ticker().submit(() -> server.chestManager().getOrCreate(new BlockPosition(x, y, z))
                    .setSlot(0, ItemStack.EMPTY));
            await(() -> wirePower(server, x + 2, y, z) == 0,
                    "the emptied chest reads 0 again");
            await(() -> !RedstoneBlocks.comparatorPowered(typeAt(server, x + 1, y, z)),
                    "the comparator released its powered pair");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSubtractModeTakesTheSideSignalOffTheInput() throws Exception {
        EngineServer server = boot();
        try {
            // The input: a chest 13/27 full (the fullness 7). The side: a
            // wire chain fed by a floor lever, arriving at the comparator's
            // north side with power 4. The output wire reads the arithmetic.
            int x = 30, z = 30, y = 40;
            int leverZ = z - 13; // the chain: lever at z-13, wires z-12..z-1
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                // The beds for the comparator, its output wire and the chain.
                for (int dz = -13; dz <= 2; dz++) {
                    server.world().setBlock(new BlockPosition(x + 1, y - 1, z + dz), BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
                server.world().setBlock(new BlockPosition(x + 2, y, z), RedstoneBlocks.wireOfPower(0));
                // The lever feeding the chain (a floor lever, up_z).
                server.world().setBlock(new BlockPosition(x + 1, y, leverZ),
                        RedstoneBlocks.leverOf(5, false));
                for (int dz = -12; dz <= -1; dz++) {
                    server.world().setBlock(new BlockPosition(x + 1, y, z + dz),
                            RedstoneBlocks.wireOfPower(0));
                }
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x + 1, y, z)), "the comparator placed");
            // Power the lever: the chain fills — the side wire (the one
            // beside the comparator, at z-1) settles at power 4 (the 12th
            // step from the lever: 15 - 11 = 4).
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 1, y, leverZ)));
            await(() -> wirePower(server, x + 1, y, z - 1) == 4,
                    "the side wire settles at power 4");
            // The input arrives: 13 full stacks (the fullness 7).
            fill(server, x, y, z, 13, 64);
            // COMPARE mode: the input 7 >= the side 4, so the output is on
            // with the input's own value.
            await(() -> wirePower(server, x + 2, y, z) == 7,
                    "compare mode passes the input through (7 >= the side 4)");
            // The mode cycle: SUBTRACT takes the side off the input.
            server.ticker().submit(() -> server.redstone().useComparator(
                    new BlockPosition(x + 1, y, z)));
            await(() -> RedstoneBlocks.comparatorSubtract(typeAt(server, x + 1, y, z)),
                    "the use cycled the comparator into subtract mode");
            await(() -> wirePower(server, x + 2, y, z) == 3,
                    "subtract mode: 7 - 4 = 3 at the output");
            // The compare gate's dark arm: a side exceeding the input kills
            // the output entirely (shouldBePowered false).
            server.ticker().submit(() -> server.redstone().useComparator(
                    new BlockPosition(x + 1, y, z)));
            await(() -> !RedstoneBlocks.comparatorSubtract(typeAt(server, x + 1, y, z)),
                    "the use cycled back to compare mode");
            await(() -> wirePower(server, x + 2, y, z) == 7,
                    "compare mode restores the pass-through");
            // Empty the chest: input 0 < side 4 in compare mode -> off.
            server.ticker().submit(() -> {
                for (int slot = 0; slot < 13; slot++) {
                    server.chestManager().getOrCreate(new BlockPosition(x, y, z))
                            .setSlot(slot, ItemStack.EMPTY);
                }
            });
            await(() -> wirePower(server, x + 2, y, z) == 0,
                    "the emptied input (0 < the side 4) drops the output");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theThroughSolidReadReachesTheChestOneBlockBack() throws Exception {
        EngineServer server = boot();
        try {
            // The reference's through-solid arm (ComparatorBlock lines
            // 114-118): a SOLID block at the input position with the read
            // below 15 looks one further back — the chest behind the stone
            // answers with its fullness.
            int x = 40, z = 40, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 3, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
                server.world().setBlock(new BlockPosition(x + 3, y, z), RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x + 2, y, z)), "the comparator placed");
            fill(server, x, y, z, 13, 64);
            await(() -> wirePower(server, x + 3, y, z) == 7,
                    "the chest behind the stone still reads through (the fullness 7)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /** Fills the first {@code slots} slots of the chest with full stacks. */
    private static void fill(EngineServer server, int x, int y, int z, int slots, int count) {
        server.ticker().submit(() -> {
            var chest = server.chestManager().getOrCreate(new BlockPosition(x, y, z));
            for (int slot = 0; slot < slots; slot++) {
                chest.setSlot(slot, ItemStack.of(BuiltinItems.STONE, count));
            }
        });
    }

    private static ItemStack stack(int count) {
        return ItemStack.of(BuiltinItems.STONE, count);
    }

    private static ItemStack halfStack() {
        return ItemStack.of(BuiltinItems.STONE, 32);
    }

    private static ItemStack[] slots(ItemStack... stacks) {
        ItemStack[] all = new ItemStack[27];
        java.util.Arrays.fill(all, ItemStack.EMPTY);
        System.arraycopy(stacks, 0, all, 0, stacks.length);
        return all;
    }

    private static ItemStack[] signs(int count) {
        ItemStack[] all = new ItemStack[27];
        java.util.Arrays.fill(all, ItemStack.EMPTY);
        for (int i = 0; i < count; i++) {
            all[i] = ItemStack.of(BuiltinItems.SIGN, 16);
        }
        return all;
    }

    private static int wirePower(EngineServer server, int x, int y, int z) {
        BlockType type = typeAt(server, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
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
                "rct" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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

    @SuppressWarnings("unused")
    private static void unused(BooleanSupplier supplier) {
        assertTrue(supplier.getAsBoolean());
    }
}
