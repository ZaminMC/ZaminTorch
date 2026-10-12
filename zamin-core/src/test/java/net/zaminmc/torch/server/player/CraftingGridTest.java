package net.zaminmc.torch.server.player;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 2x2 crafting grid as window state: shared cursor clicks, the
 * historical consumption rule, quick-move/drop gestures, return-on-close, and
 * the result-slot cursor take. The engine's craft routing (result take ->
 * consume) is exercised with the builtin service.
 */
class CraftingGridTest {

    private static ItemStack planks(int count) {
        return ItemStack.of(BuiltinItems.OAK_PLANKS, count);
    }

    @Test
    void clicksShareTheInventoryCursor() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(planks(10));
        CraftingGrid grid = new CraftingGrid();

        inventory.clickSlot(0, 0); // pick the planks onto the cursor
        assertEquals(10, inventory.cursor().count());

        grid.clickCell(0, 0, inventory); // place the whole stack into cell 0
        assertTrue(inventory.cursor().isEmpty());
        assertEquals(10, grid.cell(0).count());

        grid.clickCell(0, 1, inventory); // right click: half comes back (5)
        assertEquals(5, inventory.cursor().count());
        assertEquals(5, grid.cell(0).count());

        grid.clickCell(1, 0, inventory); // place the 5 into cell 1
        assertEquals(5, grid.cell(1).count());
        assertTrue(inventory.cursor().isEmpty());
    }

    @Test
    void consumeOneCostsEveryNonEmptyCellOneUnit() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(planks(8));
        inventory.clickSlot(0, 0);
        CraftingGrid grid = new CraftingGrid();
        grid.clickCell(0, 0, inventory); // 8 planks into cell 0
        assertEquals(8, grid.cell(0).count());

        grid.consumeOne();
        assertEquals(7, grid.cell(0).count());
        grid.consumeOne();
        assertEquals(6, grid.cell(0).count());
    }

    @Test
    void consumeOneEmptiesSingleUnitCells() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(planks(2));
        inventory.clickSlot(0, 0);
        CraftingGrid grid = new CraftingGrid();
        grid.clickCell(0, 1, inventory); // right click: 1 plank into r0c0
        grid.clickCell(2, 1, inventory); // right click: 1 plank into r1c0
        assertEquals(1, grid.cell(0).count());
        assertEquals(1, grid.cell(2).count());

        grid.consumeOne();
        assertTrue(grid.cell(0).isEmpty());
        assertTrue(grid.cell(2).isEmpty());
    }

    @Test
    void quickMoveSendsTheCellIntoTheInventoryAndKeepsRemainder() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 64));
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 64));
        inventory.pickUp(ItemStack.of(BuiltinItems.DIRT, 64)); // hotbar 0-2

        CraftingGrid grid = new CraftingGrid();
        inventory.pickUp(planks(64)); // hotbar 3
        inventory.pickUp(planks(36)); // hotbar 4 (the 64-stack is full: no top-up)
        inventory.clickSlot(3, 0);    // 64 planks onto the cursor
        grid.clickCell(0, 0, inventory);
        inventory.clickSlot(4, 0);    // 36 planks onto the cursor
        grid.clickCell(1, 0, inventory);
        assertEquals(64, grid.cell(0).count());
        assertEquals(36, grid.cell(1).count());

        grid.quickMoveTo(0, inventory);
        assertTrue(grid.cell(0).isEmpty());
        assertEquals(64, inventory.snapshot().get(3).count(),
                "the planks merged back into the matching hotbar stack");

        // A completely full inventory with no capacity anywhere: a quick-moved
        // stack stays in the grid. Cobblestone fills every slot to 64; the
        // dirt stacks (full) leave no matching capacity either.
        for (int i = 0; i < PlayerInventory.TOTAL_SLOTS; i++) {
            inventory.setSlot(i, ItemStack.of(BuiltinItems.COBBLESTONE, 64));
        }
        inventory.setSlot(0, ItemStack.of(BuiltinItems.DIRT, 64));
        inventory.setSlot(1, ItemStack.of(BuiltinItems.DIRT, 64));
        inventory.setSlot(2, ItemStack.of(BuiltinItems.DIRT, 64));
        CraftingGrid blocker = new CraftingGrid();
        PlayerInventory donor = new PlayerInventory();
        donor.pickUp(planks(36));
        donor.clickSlot(0, 0);
        blocker.clickCell(0, 0, donor);
        blocker.quickMoveTo(0, inventory);
        assertEquals(36, blocker.cell(0).count(),
                "a full inventory leaves the stack in the grid");
    }

    @Test
    void dropFromCellHandsUnitsToTheCaller() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(planks(5));
        inventory.clickSlot(0, 0);
        CraftingGrid grid = new CraftingGrid();
        grid.clickCell(0, 0, inventory);

        assertEquals(1, grid.dropFromCell(0, false).count());
        assertEquals(4, grid.cell(0).count());
        assertEquals(4, grid.dropFromCell(0, true).count());
        assertTrue(grid.cell(0).isEmpty());
        assertTrue(grid.dropFromCell(0, true).isEmpty());
    }

    @Test
    void returnAllToEmptiesTheGridIntoTheInventoryWithOverflowListed() {
        PlayerInventory inventory = new PlayerInventory();
        CraftingGrid grid = new CraftingGrid();
        inventory.pickUp(planks(6));
        inventory.clickSlot(0, 0);
        grid.clickCell(0, 0, inventory); // 6 planks in cell 0

        List<ItemStack> overflow = grid.returnAllTo(inventory);
        assertTrue(overflow.isEmpty());
        assertTrue(grid.isEmpty());
        assertEquals(6, inventory.snapshot().get(0).count());

        // Overflow: a completely full inventory cannot take the cell back —
        // the cell carries cobblestone and every cobblestone stack sits at 64.
        for (int i = 0; i < PlayerInventory.TOTAL_SLOTS; i++) {
            inventory.setSlot(i, ItemStack.of(BuiltinItems.COBBLESTONE, 64));
        }
        CraftingGrid full = new CraftingGrid();
        PlayerInventory donor = new PlayerInventory();
        donor.pickUp(ItemStack.of(BuiltinItems.COBBLESTONE, 3));
        donor.clickSlot(0, 0);
        full.clickCell(2, 0, donor);

        List<ItemStack> overflowed = full.returnAllTo(inventory);
        assertEquals(1, overflowed.size());
        assertEquals(3, overflowed.get(0).count());
        assertTrue(full.isEmpty());
    }

    @Test
    void resultTakeFollowsTheHistoricalCursorRules() {
        PlayerInventory inventory = new PlayerInventory();
        // empty cursor takes the result
        assertTrue(inventory.takeResultToCursor(planks(4)));
        assertEquals(4, inventory.cursor().count());

        // a matching cursor absorbs the next result
        assertTrue(inventory.takeResultToCursor(planks(4)));
        assertEquals(8, inventory.cursor().count());

        // a different stack is refused
        assertFalse(inventory.takeResultToCursor(ItemStack.of(BuiltinItems.STICK, 1)));
        assertEquals(8, inventory.cursor().count());

        // an overflowing stack is refused
        assertFalse(inventory.takeResultToCursor(planks(64)));
        assertEquals(8, inventory.cursor().count());

        // taking "nothing" (no recipe) is an accepted no-op
        assertTrue(inventory.takeResultToCursor(ItemStack.EMPTY));
    }

    @Test
    void diagonalPlanksMatchNothingButTheColumnCraftsSticks() {
        CraftingService service = CraftingService.builtin();

        // diagonal: bounding box 2x2 with empty off-diagonal cells -> nothing
        CraftingGrid diagonal = new CraftingGrid();
        PlayerInventory a = new PlayerInventory();
        a.pickUp(planks(2));
        a.clickSlot(0, 0);
        diagonal.clickCell(0, 1, a); // 1 plank r0c0
        diagonal.clickCell(3, 1, a); // 1 plank r1c1
        assertTrue(service.resultOf(diagonal.snapshotArray()).isEmpty());

        // the left column is the sticks shape: preview -> take -> consume
        CraftingGrid column = new CraftingGrid();
        PlayerInventory b = new PlayerInventory();
        b.pickUp(planks(2));
        b.clickSlot(0, 0);
        column.clickCell(0, 1, b); // 1 plank r0c0
        column.clickCell(2, 1, b); // 1 plank r1c0
        var result = service.resultOf(column.snapshotArray());
        assertTrue(result.isPresent());
        assertEquals(BuiltinItems.STICK, result.orElseThrow().type());
        assertEquals(4, result.orElseThrow().count());

        PlayerInventory crafter = new PlayerInventory();
        assertTrue(crafter.takeResultToCursor(result.orElseThrow()));
        column.consumeOne();
        assertTrue(column.isEmpty(), "one unit left each cell");
        assertEquals(4, crafter.cursor().count());
    }

    // ---- 3x3 crafting-table grid --------------------------------------------

    @Test
    void tableGridHoldsNineCellsRowMajor() {
        CraftingGrid grid = new CraftingGrid(3, 3);
        assertEquals(9, grid.size());
        assertEquals(3, grid.rows());
        assertEquals(3, grid.cols());

        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(planks(3));
        inventory.clickSlot(0, 0);
        grid.clickCell(2, 0, inventory); // whole stack into cell 2 (r0c2)
        assertEquals(3, grid.cell(2).count());
        assertTrue(grid.cell(0).isEmpty());
        assertTrue(grid.cell(8).isEmpty());
    }

    @Test
    void tableGridClicksPlayTheSameCursorRules() {
        CraftingGrid grid = new CraftingGrid(3, 3);
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(planks(4));
        inventory.clickSlot(0, 0); // 4 planks on the cursor

        grid.clickCell(0, 1, inventory); // right click: 1 plank into r0c0
        grid.clickCell(4, 1, inventory); // 1 into the center (r1c1)
        assertEquals(1, grid.cell(0).count());
        assertEquals(1, grid.cell(4).count());
        assertEquals(2, inventory.cursor().count());

        // park the cursor stack (empty cursor = pickup clicks)
        inventory.clickSlot(0, 0);
        assertTrue(inventory.cursor().isEmpty());
        grid.clickCell(0, 0, inventory); // left click: the cell's stack comes back
        assertTrue(grid.cell(0).isEmpty());
        assertEquals(1, inventory.cursor().count());
    }

    @Test
    void tableGridConsumesAndReturnsAllNineCells() {
        CraftingGrid grid = new CraftingGrid(3, 3);
        PlayerInventory inventory = new PlayerInventory();
        inventory.pickUp(planks(9));
        inventory.clickSlot(0, 0); // 9 planks on the cursor

        for (int cell = 0; cell < 9; cell++) {
            grid.clickCell(cell, 1, inventory); // one plank per cell
        }
        assertTrue(inventory.cursor().isEmpty(), "all 9 planks placed");

        // consume: every non-empty cell loses one unit
        grid.consumeOne();
        for (int cell = 0; cell < 9; cell++) {
            assertTrue(grid.cell(cell).isEmpty(), "cell " + cell + " consumed");
        }
    }

    @Test
    void tableGridShapeIsBoundedToThree() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CraftingGrid(4, 3));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CraftingGrid(0, 3));
    }
}
