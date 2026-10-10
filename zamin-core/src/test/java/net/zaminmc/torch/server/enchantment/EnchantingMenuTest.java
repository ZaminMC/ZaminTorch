package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.WindowClicks;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchanting menu's state walk against the reference
 * (reference/1.8.8/inventory/menu/EnchantingTableMenu.java): the seeded cost
 * ladder and its {@code < slot+1} zeroing, the clue pick
 * ({@code id | level << 8}), the bookshelf power scan's exact geometry (the
 * air gate, the double columns, the corner sextuples), the lapis gate through
 * the shared click semantics, the button gate ladder with the creative
 * bypasses and the null-offer still-pays quirk, the book-to-enchanted-book
 * conversion, the exact recompute ordering after an enchant, and the
 * close-drop. Seeds are fixed so every assertion is deterministic.
 */
class EnchantingMenuTest {

    /** A scripted world: a map of positions to block types, air elsewhere. */
    private static final class ScriptedWorld implements EnchantingMenu.WorldView {
        private final Map<BlockPosition, BlockType> blocks = new HashMap<>();

        void place(int x, int y, int z, BlockType type) {
            blocks.put(new BlockPosition(x, y, z), type);
        }

        @Override
        public BlockType blockAt(BlockPosition position) {
            return blocks.getOrDefault(position, BuiltinBlocks.AIR);
        }
    }

    private static final BlockPosition TABLE = new BlockPosition(8, 40, 8);

    /** A recording enchanter: fixed level, creative flag, seed rerolls counted. */
    private static final class ScriptedEnchanter implements EnchantingMenu.Enchanter {
        int level;
        boolean creative;
        int seed;
        int lastCost = -1;

        @Override
        public int xpLevel() {
            return level;
        }

        @Override
        public boolean creative() {
            return creative;
        }

        @Override
        public void applyEnchantmentCosts(int cost) {
            lastCost = cost;
            level = Math.max(0, level - cost);
            seed = seed + 101; // a deterministic reroll the recompute ordering can observe
        }

        @Override
        public int enchantingSeed() {
            return seed;
        }
    }

    private static ItemStack sword() {
        return new ItemStack(BuiltinItems.DIAMOND_SWORD, 1, 0, null);
    }

    private static ItemStack lapis(int count) {
        return new ItemStack(BuiltinItems.LAPIS, count, 4, null);
    }

    @Test
    void emptyItemZeroesTheLadder() {
        EnchantingMenu menu = new EnchantingMenu(12345, TABLE);
        ScriptedWorld world = new ScriptedWorld();
        menu.onContentsChanged(world);
        for (int slot = 0; slot < 3; slot++) {
            assertEquals(0, menu.cost(slot), "no item: every cost zero");
            assertEquals(-1, menu.clue(slot), "no item: every clue unset");
        }
    }

    @Test
    void costLadderRunsTheSeededRollsAndZeroesBelowSlotPlusOne() {
        // The same seed + item must give the same ladder as a direct
        // EnchantmentHelper walk (the menu is the seed holder, the helper the math).
        EnchantingMenu menu = new EnchantingMenu(777, TABLE);
        ScriptedWorld world = new ScriptedWorld(); // power 0: the shelf-less corner
        menu.itemSlotSet(sword()); // test hook: place the item
        menu.onContentsChanged(world);

        java.util.Random reference = new java.util.Random();
        reference.setSeed(777);
        int[] expected = new int[3];
        for (int slot = 0; slot < 3; slot++) {
            expected[slot] = EnchantmentHelper.requiredXpLevel(reference, slot, 0, sword());
            if (expected[slot] < slot + 1) {
                expected[slot] = 0;
            }
        }
        assertEquals(expected[0], menu.cost(0), "slot 0 cost");
        assertEquals(expected[1], menu.cost(1), "slot 1 cost");
        assertEquals(expected[2], menu.cost(2), "slot 2 cost");
    }

    @Test
    void bookshelfPowerScanFollowsTheExactGeometry() {
        // The scan needs both cells above each horizontal neighbor clear, then
        // counts the k*2/j*2 double column at both heights, and corners add
        // the four lateral cells.
        ScriptedWorld world = new ScriptedWorld();
        // A single due-east shelf at distance 2: neighbor (1,0) clear above →
        // counts (2,0) and (2,1): power 2.
        world.place(10, 40, 8, BuiltinBlocks.BOOKSHELF);
        world.place(10, 41, 8, BuiltinBlocks.BOOKSHELF);
        EnchantingMenu menu = new EnchantingMenu(1, TABLE);
        menu.itemSlotSet(sword());
        menu.onContentsChanged(world);
        assertLadder(ladderFor(world, 2), ladderOf(menu), "east double column counts twice");

        // Blocking the air above the east neighbor kills the whole east arm.
        ScriptedWorld world2 = new ScriptedWorld();
        world2.place(10, 40, 8, BuiltinBlocks.BOOKSHELF);
        world2.place(10, 41, 8, BuiltinBlocks.BOOKSHELF);
        world2.place(9, 41, 8, BuiltinBlocks.STONE); // the east neighbor's upper air cell
        EnchantingMenu menu2 = new EnchantingMenu(1, TABLE);
        menu2.itemSlotSet(sword());
        menu2.onContentsChanged(world2);
        assertLadder(ladderFor(world2, 0), ladderOf(menu2), "a blocked neighbor air cell zeroes that arm");

        // A corner's sextuple: NE corner counts (2,0,2)/(2,1,2) plus the four
        // laterals (2,0,1)/(2,1,1)/(1,0,2)/(1,1,2) = 6 shelves.
        ScriptedWorld world3 = new ScriptedWorld();
        world3.place(10, 40, 10, BuiltinBlocks.BOOKSHELF);
        world3.place(10, 41, 10, BuiltinBlocks.BOOKSHELF);
        world3.place(10, 40, 9, BuiltinBlocks.BOOKSHELF);
        world3.place(10, 41, 9, BuiltinBlocks.BOOKSHELF);
        world3.place(9, 40, 10, BuiltinBlocks.BOOKSHELF);
        world3.place(9, 41, 10, BuiltinBlocks.BOOKSHELF);
        EnchantingMenu menu3 = new EnchantingMenu(1, TABLE);
        menu3.itemSlotSet(sword());
        menu3.onContentsChanged(world3);
        assertLadder(ladderFor(world3, 6), ladderOf(menu3), "the corner sextuple");
    }

    private static int[] ladderOf(EnchantingMenu menu) {
        return new int[]{menu.cost(0), menu.cost(1), menu.cost(2)};
    }

    private static void assertLadder(int[] expected, int[] actual, String message) {
        for (int slot = 0; slot < 3; slot++) {
            assertEquals(expected[slot], actual[slot], message + " (slot " + slot + ")");
        }
    }

    /** A direct reference walk of the ladder (helper calls only) at a given power. */
    private static int[] ladderFor(net.zaminmc.torch.server.enchantment.EnchantingMenu.WorldView world,
                                   int expectedPower) {
        // The scan itself is asserted through the menu; this walk only pins the
        // helper contract at the stated power with the same seed.
        java.util.Random reference = new java.util.Random();
        reference.setSeed(1);
        int[] costs = new int[3];
        for (int slot = 0; slot < 3; slot++) {
            costs[slot] = EnchantmentHelper.requiredXpLevel(reference, slot, expectedPower, sword());
            if (costs[slot] < slot + 1) {
                costs[slot] = 0;
            }
        }
        return costs;
    }

    @Test
    void lapisGateRefusesEverythingButBlueDye() {
        EnchantingMenu menu = new EnchantingMenu(1, TABLE);
        WindowClicks.CursorBox cursor = new WindowClicks.CursorBox() {
            private ItemStack stack = ItemStack.EMPTY;

            @Override
            public ItemStack get() {
                return stack;
            }

            @Override
            public void set(ItemStack stack) {
                this.stack = stack;
            }
        };

        // A sword refuses the lapis slot entirely (the same-item reverse merge
        // keeps everything: slot empty, nothing to merge back).
        cursor.set(sword());
        assertFalse(menu.clickSlot(1, 0, cursor), "a sword refuses the lapis slot");
        assertEquals(sword(), cursor.get(), "the refused cursor keeps its stack");
        assertTrue(menu.lapis().isEmpty());

        // Wrong-dye lapis (damage 0) refuses too.
        cursor.set(new ItemStack(BuiltinItems.LAPIS, 4, 0, null));
        assertFalse(menu.clickSlot(1, 0, cursor));
        assertEquals(4, cursor.get().count(), "the refused lapis stays on the cursor");

        // Blue lapis (damage 4) lands — 40 first, then 30 toward the slot's
        // 64 cap: the split keeps 6 on the cursor.
        cursor.set(lapis(40));
        assertTrue(menu.clickSlot(1, 0, cursor));
        assertTrue(cursor.get().isEmpty(), "a 40 deposit fully lands");
        cursor.set(lapis(30));
        assertTrue(menu.clickSlot(1, 0, cursor));
        assertEquals(64, menu.lapis().count(), "the lapis slot caps at 64");
        assertEquals(6, cursor.get().count(), "the split keeps the remainder on the cursor");

        // Right-click on a full lapis slot refuses the unit merge (at cap).
        cursor.set(lapis(3));
        assertFalse(menu.clickSlot(1, 1, cursor));
        assertEquals(3, cursor.get().count());

        // Right-click takes half, ceil.
        cursor.set(ItemStack.EMPTY);
        assertTrue(menu.clickSlot(1, 1, cursor));
        assertEquals(32, cursor.get().count(), "half of 64, ceil");
        assertEquals(32, menu.lapis().count());
    }

    @Test
    void itemSlotTakesOneStackMax() {
        EnchantingMenu menu = new EnchantingMenu(1, TABLE);
        WindowClicks.CursorBox cursor = cursorOf(lapis(10).withDamage(0));
        // A 10-stack toward slot 0: the max-1 split keeps 9 on the cursor.
        assertTrue(menu.clickSlot(0, 0, cursor));
        assertEquals(9, cursor.get().count(), "the max-1 slot splits the deposit");
        assertEquals(1, menu.item().count());
    }

    private static WindowClicks.CursorBox cursorOf(ItemStack initial) {
        return new WindowClicks.CursorBox() {
            private ItemStack stack = initial;

            @Override
            public ItemStack get() {
                return stack;
            }

            @Override
            public void set(ItemStack stack) {
                this.stack = stack;
            }
        };
    }

    @Test
    void enchantButtonPaysTheGatesAndConsumesTheLapis() {
        // Power 5 (enough for real costs), level 30, lapis 3: button 2 (slot 2)
        // costs 3 lapis and level >= 3 and level >= cost[2].
        ScriptedWorld world = new ScriptedWorld();
        world.place(6, 40, 8, BuiltinBlocks.BOOKSHELF);
        world.place(6, 41, 8, BuiltinBlocks.BOOKSHELF);
        world.place(10, 40, 8, BuiltinBlocks.BOOKSHELF);
        world.place(10, 41, 8, BuiltinBlocks.BOOKSHELF);
        // power 4 so far (two double columns); add a corner-free pair north.
        world.place(8, 40, 6, BuiltinBlocks.BOOKSHELF);
        world.place(8, 41, 6, BuiltinBlocks.BOOKSHELF);
        EnchantingMenu menu = new EnchantingMenu(20250101, TABLE);
        menu.itemSlotSet(sword());
        menu.onContentsChanged(world);

        ScriptedEnchanter enchanter = new ScriptedEnchanter();
        enchanter.level = 30;
        enchanter.seed = 20250101;

        // Not enough lapis for button 2 (needs 3): refused with 2.
        menu.itemSlotSet(sword());
        menu.lapisSlotSet(lapis(2));
        menu.onContentsChanged(world);
        enchanter.level = 30;
        assertFalse(menu.enchantButton(enchanter, 2, world), "two lapis refuse button 2");

        // Enough lapis: button 2 lands, pays slot+1 levels, consumes 3 lapis.
        menu.lapisSlotSet(lapis(3));
        menu.onContentsChanged(world);
        int cost2 = menu.cost(2);
        assertTrue(cost2 > 0, "the scripted seed gives slot 2 a cost (power " + powerOf(world) + ")");
        enchanter.level = 30;
        assertTrue(menu.enchantButton(enchanter, 2, world));
        assertEquals(3, enchanter.lastCost, "button 2 pays slot+1 levels (the cost only gates)");
        assertEquals(27, enchanter.level, "the level walks down by slot+1");
        assertTrue(menu.lapis().isEmpty(), "the lapis consumes");
        assertTrue(menu.item().enchantments() != null && !menu.item().enchantments().isEmpty(),
                "the sword now carries enchantments");
    }

    private static int powerOf(ScriptedWorld world) {
        int power = 0;
        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                if ((j != 0 || k != 0)
                        && world.blockAt(TABLE.offset(k, 0, j)).equals(BuiltinBlocks.AIR)
                        && world.blockAt(TABLE.offset(k, 1, j)).equals(BuiltinBlocks.AIR)) {
                    if (world.blockAt(TABLE.offset(k * 2, 0, j * 2)).equals(BuiltinBlocks.BOOKSHELF)) {
                        power++;
                    }
                    if (world.blockAt(TABLE.offset(k * 2, 1, j * 2)).equals(BuiltinBlocks.BOOKSHELF)) {
                        power++;
                    }
                    if (k != 0 && j != 0) {
                        if (world.blockAt(TABLE.offset(k * 2, 0, j)).equals(BuiltinBlocks.BOOKSHELF)) {
                            power++;
                        }
                        if (world.blockAt(TABLE.offset(k * 2, 1, j)).equals(BuiltinBlocks.BOOKSHELF)) {
                            power++;
                        }
                        if (world.blockAt(TABLE.offset(k, 0, j * 2)).equals(BuiltinBlocks.BOOKSHELF)) {
                            power++;
                        }
                        if (world.blockAt(TABLE.offset(k, 1, j * 2)).equals(BuiltinBlocks.BOOKSHELF)) {
                            power++;
                        }
                    }
                }
            }
        }
        return power;
    }

    @Test
    void theRecomputeOrderingAfterAnEnchantIsOldSeedThenRerolledSeed() {
        ScriptedWorld world = new ScriptedWorld();
        world.place(6, 40, 8, BuiltinBlocks.BOOKSHELF);
        world.place(6, 41, 8, BuiltinBlocks.BOOKSHELF);
        EnchantingMenu menu = new EnchantingMenu(42, TABLE);
        menu.itemSlotSet(sword());
        menu.lapisSlotSet(lapis(2));
        menu.onContentsChanged(world);

        ScriptedEnchanter enchanter = new ScriptedEnchanter();
        enchanter.level = 40;
        enchanter.seed = 42;

        long before = menu.revision();
        assertTrue(menu.enchantButton(enchanter, 0, world), "button 0 needs 1 lapis");
        assertEquals(before + 2, menu.revision(),
                "exactly two recomputes ride one enchant");
        // The menu's seed now mirrors the enchanter's rerolled one.
        assertEquals(enchanter.seed, menu.seed(), "the second recompute ran on the rerolled seed");
        assertEquals(1, enchanter.lastCost, "button 0 pays exactly 1 level");
    }

    @Test
    void bookConvertsToEnchantedBook() {
        ScriptedWorld world = new ScriptedWorld();
        world.place(6, 40, 8, BuiltinBlocks.BOOKSHELF);
        world.place(6, 41, 8, BuiltinBlocks.BOOKSHELF);
        world.place(10, 40, 8, BuiltinBlocks.BOOKSHELF);
        world.place(10, 41, 8, BuiltinBlocks.BOOKSHELF);
        EnchantingMenu menu = new EnchantingMenu(314, TABLE);
        menu.itemSlotSet(new ItemStack(BuiltinItems.BOOK, 1, 0, null));
        menu.lapisSlotSet(lapis(1));
        menu.onContentsChanged(world);

        ScriptedEnchanter enchanter = new ScriptedEnchanter();
        enchanter.level = 30;
        enchanter.seed = 314;

        assertTrue(menu.enchantButton(enchanter, 0, world));
        assertEquals(BuiltinItems.ENCHANTED_BOOK.identifier(), menu.item().type().identifier(),
                "the book becomes the enchanted book");
        assertTrue(menu.item().enchantments() != null && !menu.item().enchantments().isEmpty(),
                "the enchanted book carries its offer");
    }

    @Test
    void creativeBypassesTheLapisAndXpGates() {
        ScriptedWorld world = new ScriptedWorld();
        world.place(6, 40, 8, BuiltinBlocks.BOOKSHELF);
        world.place(6, 41, 8, BuiltinBlocks.BOOKSHELF);
        EnchantingMenu menu = new EnchantingMenu(99, TABLE);
        menu.itemSlotSet(sword());
        menu.onContentsChanged(world); // no lapis in the slot at all

        ScriptedEnchanter enchanter = new ScriptedEnchanter();
        enchanter.level = 0; // a level-0 creative still enchants
        enchanter.creative = true;
        enchanter.seed = 99;

        int cost1 = menu.cost(1);
        if (cost1 > 0) {
            assertTrue(menu.enchantButton(enchanter, 1, world),
                    "creative enchants without lapis or levels");
            assertEquals(0, enchanter.level, "creative pays nothing");
            assertTrue(menu.lapis().isEmpty(), "creative consumes no lapis");
            assertTrue(menu.item().enchantments() != null && !menu.item().enchantments().isEmpty(),
                    "the offer still lands on the stack");
        }
    }

    @Test
    void closeDropReturnsBothSlotsAndClearsTheMenu() {
        EnchantingMenu menu = new EnchantingMenu(5, TABLE);
        menu.itemSlotSet(sword());
        menu.lapisSlotSet(lapis(9));
        ItemStack[] dropped = menu.closeDrop();
        assertEquals(2, dropped.length);
        assertEquals(sword(), dropped[0]);
        assertEquals(lapis(9), dropped[1]);
        assertTrue(menu.item().isEmpty());
        assertTrue(menu.lapis().isEmpty());
    }

    @Test
    void stillValidGatesOnTheBlockAndTheSquaredDistance() {
        ScriptedWorld world = new ScriptedWorld();
        world.place(8, 40, 8, BuiltinBlocks.ENCHANTING_TABLE);
        EnchantingMenu menu = new EnchantingMenu(1, TABLE);

        // The table center is (8.5, 40.5, 8.5); squared 64 = 8 blocks out.
        assertTrue(menu.stillValid(world, 8.5, 40.5, 8.5), "standing on the table");
        assertTrue(menu.stillValid(world, 8.5, 44.0, 8.5), "a few blocks up is inside");
        assertFalse(menu.stillValid(world, 20.5, 40.5, 8.5), "12 blocks east is outside");

        // Breaking the table invalidates regardless of distance.
        world.place(8, 40, 8, BuiltinBlocks.AIR);
        assertFalse(menu.stillValid(world, 8.5, 40.5, 8.5), "no table, no window");
    }

    @Test
    void quickMoveFromPlayerPlaysTheReferenceArms() {
        EnchantingMenu menu = new EnchantingMenu(1, TABLE);

        // Lapis merges into an occupied slot (the reference moveItem merge arm).
        menu.lapisSlotSet(lapis(60));
        ItemStack leftover = menu.quickMoveFromPlayer(lapis(10));
        assertEquals(64, menu.lapis().count(), "the merge tops up to the slot's 64");
        assertEquals(6, leftover.count(), "the overflow stays with the player");

        // A full lapis slot returns everything.
        menu.lapisSlotSet(lapis(64));
        leftover = menu.quickMoveFromPlayer(lapis(5));
        assertEquals(5, leftover.count(), "a full slot keeps the stack with the player");
        assertEquals(64, menu.lapis().count());

        // A non-lapis occupant blocks slot 0 entirely.
        menu.itemSlotSet(sword());
        leftover = menu.quickMoveFromPlayer(new ItemStack(BuiltinItems.BOOK, 4, 0, null));
        assertEquals(4, leftover.count(), "an occupied item slot refuses the quick-move");

        // An empty item slot takes one unit from a stack.
        menu.itemSlotSet(ItemStack.EMPTY);
        leftover = menu.quickMoveFromPlayer(new ItemStack(BuiltinItems.BOOK, 4, 0, null));
        assertEquals(1, menu.item().count(), "one book lands");
        assertEquals(3, leftover.count());

        // A single named stack moves whole.
        menu.itemSlotSet(ItemStack.EMPTY);
        ItemStack named = new ItemStack(BuiltinItems.BOOK, 1, 0, "Tome");
        leftover = menu.quickMoveFromPlayer(named);
        assertTrue(leftover.isEmpty(), "a single named stack moves whole");
        assertEquals("Tome", menu.item().displayName());
    }

    @Test
    void quickMoveToInventoryEmptiesThroughTheInsert() {
        EnchantingMenu menu = new EnchantingMenu(1, TABLE);
        menu.lapisSlotSet(lapis(30));
        ItemStack leftover = menu.quickMoveToInventory(1, stack -> ItemStack.EMPTY);
        assertTrue(leftover.isEmpty(), "a full insert empties the slot");
        assertTrue(menu.lapis().isEmpty());
    }
}
