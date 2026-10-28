package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The furnace fuel &amp; cycle parity audit's pins (the verifier's sixth
 * pass): every arm of the reference's own fuel law
 * (FurnaceBlockEntity.getFuelTime lines 262-300) over the registry, the
 * fuel consumption's recipe remainder (the tick's lines 182-188 — the lava
 * bucket's empty bucket, Item.java line 611's setRecipeRemainder), the
 * tick's two interruption laws (the guarded hard reset vs the unguarded
 * decay-by-two, lines 176-205), and the window's three slot gates
 * (FurnaceMenu lines 22-24: the plain input, the FurnaceFuelSlot, the
 * take-only FurnaceResultSlot).
 */
class FurnaceFuelParityTest {

    private static int burn(net.zaminmc.torch.item.ItemType type) {
        return FurnaceRecipes.burnTicksOf(type);
    }

    // ------------------------------------------------------------------
    // The burn arms over one table (getFuelTime lines 262-300)
    // ------------------------------------------------------------------

    @Test
    void theWoodenSlabArmPrecedesTheWoodClass() {
        // Lines 270-272: the slab answers 150 — HALF a plank's burn —
        // because the arm sits BEFORE the Material.WOOD class; both
        // registered ids of the one reference item (legacy 126).
        assertEquals(150, burn(BuiltinItems.WOODEN_SLAB));
        assertEquals(150, burn(BuiltinItems.OAK_SLAB));
    }

    @Test
    void theWoodMaterialClassBurnsThreeHundred() {
        // Lines 274-276: every registered block item whose BLOCK reads
        // Material.WOOD — the class, not a hand-picked four.
        assertEquals(300, burn(BuiltinItems.OAK_PLANKS));
        assertEquals(300, burn(BuiltinItems.PLANKS));
        assertEquals(300, burn(BuiltinItems.OAK_LOG));
        assertEquals(300, burn(BuiltinItems.LOG));
        assertEquals(300, burn(BuiltinItems.LOG2));
        assertEquals(300, burn(BuiltinItems.CRAFTING_TABLE));
        assertEquals(300, burn(BuiltinItems.CHEST));
        assertEquals(300, burn(BuiltinItems.BOOKSHELF));
        assertEquals(300, burn(BuiltinItems.NOTE_BLOCK));
        assertEquals(300, burn(BuiltinItems.JUKEBOX));
        assertEquals(300, burn(BuiltinItems.SIGN));
        assertEquals(300, burn(BuiltinItems.OAK_DOOR));
        assertEquals(300, burn(BuiltinItems.TRAPDOOR));
        assertEquals(300, burn(BuiltinItems.OAK_FENCE));
        assertEquals(300, burn(BuiltinItems.SPRUCE_FENCE));
        assertEquals(300, burn(BuiltinItems.BIRCH_FENCE));
        assertEquals(300, burn(BuiltinItems.JUNGLE_FENCE));
        assertEquals(300, burn(BuiltinItems.DARK_OAK_FENCE));
        assertEquals(300, burn(BuiltinItems.ACACIA_FENCE));
        assertEquals(300, burn(BuiltinItems.OAK_FENCE_GATE));
        assertEquals(300, burn(BuiltinItems.OAK_STAIRS));
        assertEquals(300, burn(BuiltinItems.SPRUCE_STAIRS));
        assertEquals(300, burn(BuiltinItems.BIRCH_STAIRS));
        assertEquals(300, burn(BuiltinItems.JUNGLE_STAIRS));
        assertEquals(300, burn(BuiltinItems.ACACIA_STAIRS));
        assertEquals(300, burn(BuiltinItems.DARK_OAK_STAIRS));
    }

    @Test
    void theLadderIsNotWood() {
        // LadderBlock line 20 reads Material.DECORATION — the one
        // wooden-looking block the class law excludes (the audit's trap:
        // a naive "everything wooden burns" read pays 300 here).
        assertEquals(0, burn(BuiltinItems.LADDER));
    }

    @Test
    void theItemArmsAnswerTheirOwnRows() {
        // The coal block (line 279): the 16-smelt block.
        assertEquals(16000, burn(BuiltinItems.COAL_BLOCK_ITEM));
        // The wooden tool / sword / hoe arms (lines 283-288): 200 each —
        // the hoe row the pre-audit table never carried.
        assertEquals(200, burn(BuiltinItems.WOODEN_PICKAXE));
        assertEquals(200, burn(BuiltinItems.WOODEN_AXE));
        assertEquals(200, burn(BuiltinItems.WOODEN_SHOVEL));
        assertEquals(200, burn(BuiltinItems.WOODEN_SWORD));
        assertEquals(200, burn(BuiltinItems.WOODEN_HOE));
        // The item arms (lines 289-298).
        assertEquals(100, burn(BuiltinItems.STICK));
        assertEquals(1600, burn(BuiltinItems.COAL), "the eight-smelt anchor");
        assertEquals(20000, burn(BuiltinItems.LAVA_BUCKET), "the ten-smelt bucket");
        assertEquals(100, burn(BuiltinItems.SAPLING));
        assertEquals(100, burn(BuiltinItems.OAK_SAPLING));
        assertEquals(2400, burn(BuiltinItems.BLAZE_ROD), "the twelve-smelt rod");
    }

    @Test
    void theNonFuelArmsAnswerZero() {
        // The else row (line 297-299): dirt, ingots, and every filled
        // bucket except the LAVA one — the water and milk buckets never
        // burn, and the EMPTY bucket is not fuel either (it only RIDES the
        // fuel slot, the FurnaceFuelSlot's own gate).
        assertEquals(0, burn(BuiltinItems.DIRT));
        assertEquals(0, burn(BuiltinItems.IRON_INGOT));
        assertEquals(0, burn(BuiltinItems.WATER_BUCKET));
        assertEquals(0, burn(BuiltinItems.MILK_BUCKET));
        assertEquals(0, burn(BuiltinItems.BUCKET));
    }

    @Test
    void theEmptyBucketWaitsInTheFuelSlotWithoutIgniting() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.BUCKET, 1));
        furnace.tick();
        assertFalse(furnace.burning(), "the empty bucket never ignites");
        assertEquals(1, furnace.fuel().count(), "the bucket keeps waiting");
    }

    // ------------------------------------------------------------------
    // The fuel consumption's remainder (the tick's lines 182-188)
    // ------------------------------------------------------------------

    @Test
    void theLavaBucketIgnitesAndLeavesItsEmptyBucket() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.LAVA_BUCKET, 1));
        furnace.tick();
        assertEquals(20000, furnace.burnTimeRemaining(), "the ten-smelt cycle");
        assertEquals(20000, furnace.burnTimeTotal());
        assertEquals(BuiltinItems.BUCKET, furnace.fuel().type(),
                "the lava bucket's recipe remainder (Item.java line 611)");
        assertEquals(1, furnace.fuel().count());
        assertTrue(furnace.burning());
        // The filled buckets stack to one (BucketItem's own max), so the
        // remainder consult is the single-unit path by construction — the
        // reference's mid-stack arm (size-- before the consult) belongs to
        // the stackable fuels, which carry no remainders.
    }

    @Test
    void aStackOfCoalBurnsOneUnitAtATimeWithoutARemainder() {
        // The mid-stack consumption arm (size-- before the consult): a
        // two-coal stack keeps its second unit through the first ignite,
        // and coal leaves nothing behind — the stackable fuels' own shape.
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.COAL, 2));
        furnace.tick();
        assertEquals(1600, furnace.burnTimeRemaining());
        assertEquals(BuiltinItems.COAL, furnace.fuel().type());
        assertEquals(1, furnace.fuel().count(), "no remainder mid-stack");
    }

    // ------------------------------------------------------------------
    // The tick's two interruption laws (lines 176-205)
    // ------------------------------------------------------------------

    @Test
    void burnOutDecaysTheCookProgressByTwoPerTick() {
        // One plank (300 ticks) cooking two ore: the first smelt lands at
        // tick 200, the second cooks to 100 by tick 300 (the ignition tick
        // spends no burn decrement — the plank's last point goes at tick
        // 301), and the progress then DECAYS by two per tick (line 204's
        // clamp), never the pre-audit hard reset.
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 2));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
        for (int tick = 0; tick < 299; tick++) {
            furnace.tick();
        }
        assertEquals(99, furnace.cookTime(), "the second cook window's progress");
        assertEquals(1, furnace.output().count(), "the first smelt landed");
        furnace.tick(); // tick 300: the plank's last burning tick
        assertEquals(100, furnace.cookTime());
        assertTrue(furnace.burning(), "the last burn point still stands");
        furnace.tick(); // tick 301: the burn is out — the decay begins
        assertEquals(98, furnace.cookTime(), "the decay: two points, not a reset");
        assertFalse(furnace.burning());
        for (int tick = 0; tick < 48; tick++) {
            furnace.tick(); // 98 -> 2
        }
        assertEquals(2, furnace.cookTime());
        furnace.tick(); // 2 -> 0, clamped
        assertEquals(0, furnace.cookTime());
        assertEquals(1, furnace.output().count(), "the decay never smelts");
        assertEquals(1, furnace.input().count(), "the waiting ore stays");
    }

    @Test
    void aFreshIgniteResumesTheDecayedWindow() {
        // The decay PRESERVES partial progress: fuel arriving mid-decay
        // ignites and the cook RESUMES from the decayed point (the
        // reference's own patience — a hard reset would have lost it).
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 2));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
        for (int tick = 0; tick < 301; tick++) {
            furnace.tick();
        }
        assertEquals(98, furnace.cookTime(), "the decayed window");
        assertFalse(furnace.burning());
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
        furnace.tick(); // the fresh ignite resumes the window
        assertEquals(99, furnace.cookTime(), "the resumed window, not a restart");
        assertTrue(furnace.burning());
    }

    @Test
    void theFuelGoneDecaysTheWindowWhileTheInputWaits() {
        // Outside the guard, fuel absent: the window decays even with a
        // smeltable input standing by (the pre-audit fold reset it).
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.restore(0, 0, 50);
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 1));
        furnace.tick();
        assertEquals(48, furnace.cookTime(), "the unguarded decay (no fuel)");
        assertFalse(furnace.burning());
    }

    @Test
    void theInputGoneDecaysTheWindowWhileTheFuelWaits() {
        // Outside the guard, input absent: same decay — the guard demands
        // BOTH a fuel item and an input (line 177).
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.restore(0, 0, 50);
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
        furnace.tick();
        assertEquals(48, furnace.cookTime(), "the unguarded decay (no input)");
        assertFalse(furnace.burning(), "no ignite without a smeltable input");
        assertEquals(1, furnace.fuel().count(), "the plank keeps waiting");
    }

    @Test
    void theGuardedBlockHardResetsWhenTheOutputBlocksTheSmelt() {
        // INSIDE the guard (fuel + input present) the blocked-output arm
        // pays the reference's hard reset, not the decay: canCook false
        // (the dirt in the output refuses the ingot) — the else of line
        // 200-202, not the decay of line 203-204.
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        furnace.restore(0, 0, 50);
        furnace.setSlot(FurnaceBlockEntity.SLOT_INPUT,
                ItemStack.of(BuiltinItems.IRON_ORE, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_FUEL,
                ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
        furnace.setSlot(FurnaceBlockEntity.SLOT_OUTPUT,
                ItemStack.of(BuiltinItems.DIRT, 1));
        furnace.tick();
        assertEquals(0, furnace.cookTime(), "the guarded hard reset");
        assertEquals(1, furnace.fuel().count(), "no ignition: canSmelt is false");
        assertFalse(furnace.burning());
    }

    // ------------------------------------------------------------------
    // The window's three slot gates (FurnaceMenu lines 22-24)
    // ------------------------------------------------------------------

    /** Picks a stack up from hotbar slot 0 onto the shared cursor. */
    private static void holdOnCursor(PlayerInventory inventory, ItemStack stack) {
        inventory.pickUp(stack); // lands in the first free hotbar/main slot
        inventory.clickSlot(0, 0); // left-click hotbar 0: the cursor takes it
    }

    @Test
    void theFuelSlotAcceptsFuelAndTheEmptyBucketOnly() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();

        // Dirt is neither: the gate refuses the placement, the cursor keeps
        // the stack, the slot stays empty.
        PlayerInventory dirtHand = new PlayerInventory();
        holdOnCursor(dirtHand, ItemStack.of(BuiltinItems.DIRT, 1));
        furnace.clickSlot(FurnaceBlockEntity.SLOT_FUEL, 0, dirtHand);
        assertTrue(furnace.fuel().isEmpty(), "dirt never enters the fuel slot");
        assertEquals(1, dirtHand.cursor().count(), "the cursor keeps the dirt");

        // The EMPTY bucket rides the slot (FurnaceFuelSlot.isBucket) —
        // and stacks to ONE there (FurnaceFuelSlot.getMaxStackSize): a
        // three-bucket cursor deposits a single bucket.
        PlayerInventory bucketHand = new PlayerInventory();
        holdOnCursor(bucketHand, ItemStack.of(BuiltinItems.BUCKET, 3));
        furnace.clickSlot(FurnaceBlockEntity.SLOT_FUEL, 0, bucketHand);
        assertEquals(BuiltinItems.BUCKET, furnace.fuel().type());
        assertEquals(1, furnace.fuel().count(), "the bucket caps at one in the slot");
        assertEquals(2, bucketHand.cursor().count(), "two buckets stay on the cursor");

        // Taking from the fuel slot is never gated: an empty cursor picks
        // the parked bucket up.
        bucketHand.clickSlot(0, 0); // park the two-bucket cursor back in the hotbar
        assertTrue(bucketHand.cursor().isEmpty());
        furnace.clickSlot(FurnaceBlockEntity.SLOT_FUEL, 0, bucketHand);
        assertTrue(furnace.fuel().isEmpty(), "the bucket left the slot");
        assertEquals(BuiltinItems.BUCKET, bucketHand.cursor().type());
        assertEquals(1, bucketHand.cursor().count());
    }

    @Test
    void theWaterBucketIsRefusedByTheFuelSlot() {
        // The gate's isBucket row answers for the EMPTY bucket only
        // (FurnaceFuelSlot line 24: item == Items.BUCKET): the water
        // bucket is neither fuel nor the empty bucket.
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        PlayerInventory inventory = new PlayerInventory();
        holdOnCursor(inventory, ItemStack.of(BuiltinItems.WATER_BUCKET, 1));
        furnace.clickSlot(FurnaceBlockEntity.SLOT_FUEL, 0, inventory);
        assertTrue(furnace.fuel().isEmpty(), "the water bucket never enters");
        assertEquals(1, inventory.cursor().count());
    }

    @Test
    void theOutputSlotTakesButNeverReceives() {
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        PlayerInventory inventory = new PlayerInventory();
        furnace.setSlot(FurnaceBlockEntity.SLOT_OUTPUT,
                ItemStack.of(BuiltinItems.IRON_INGOT, 5));

        // Placement is refused outright (FurnaceResultSlot lines 22-24:
        // isItemAllowed -> FALSE): the cursor keeps the dirt.
        holdOnCursor(inventory, ItemStack.of(BuiltinItems.DIRT, 1));
        furnace.clickSlot(FurnaceBlockEntity.SLOT_OUTPUT, 0, inventory);
        assertEquals(5, furnace.output().count(), "the dirt never lands");
        assertEquals(1, inventory.cursor().count());
        inventory.clickSlot(0, 0); // put the dirt back

        // Taking is never gated: an empty cursor picks the stack up.
        furnace.clickSlot(FurnaceBlockEntity.SLOT_OUTPUT, 0, inventory);
        assertTrue(furnace.output().isEmpty());
        assertEquals(5, inventory.cursor().count());

        // The not-allowed same-item cursor takes the REVERSE merge (the
        // slot into the cursor) — the window's own taking arm.
        furnace.setSlot(FurnaceBlockEntity.SLOT_OUTPUT,
                ItemStack.of(BuiltinItems.IRON_INGOT, 5));
        furnace.clickSlot(FurnaceBlockEntity.SLOT_OUTPUT, 0, inventory);
        assertTrue(furnace.output().isEmpty(), "the slot merged into the cursor");
        assertEquals(10, inventory.cursor().count(), "taking never gates");
    }

    @Test
    void theInputSlotStaysUnfiltered() {
        // The menu's first slot is a plain InventorySlot (FurnaceMenu line
        // 22): dirt parks in the input and never smelts, exactly the
        // reference's shape.
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        PlayerInventory inventory = new PlayerInventory();
        holdOnCursor(inventory, ItemStack.of(BuiltinItems.DIRT, 2));
        furnace.clickSlot(FurnaceBlockEntity.SLOT_INPUT, 0, inventory);
        assertEquals(BuiltinItems.DIRT, furnace.input().type());
        assertEquals(2, furnace.input().count());
        furnace.tick();
        assertEquals(2, furnace.input().count(), "dirt never smelts");
        assertFalse(furnace.burning());
    }

    // ------------------------------------------------------------------
    // The quick-move routing's own asymmetry (FurnaceMenu lines 100-103)
    // ------------------------------------------------------------------

    @Test
    void quickMoveNeverRoutesTheEmptyBucketIntoTheFuelSlot() {
        // The menu's transfer walk routes by isFuel alone (line 100) —
        // the empty bucket, admitted by the GUI gate, is NOT quick-moved
        // in: the stack comes back whole. The lava bucket IS fuel and
        // routes in.
        FurnaceBlockEntity furnace = new FurnaceBlockEntity();
        ItemStack buckets = ItemStack.of(BuiltinItems.BUCKET, 2);
        ItemStack returned = furnace.quickMoveIn(buckets);
        assertEquals(2, returned.count(), "the bucket stack comes back whole");
        assertTrue(furnace.fuel().isEmpty());

        ItemStack lava = ItemStack.of(BuiltinItems.LAVA_BUCKET, 1);
        assertTrue(furnace.quickMoveIn(lava).isEmpty(), "the lava bucket routes in");
        assertEquals(BuiltinItems.LAVA_BUCKET, furnace.fuel().type());
    }
}
