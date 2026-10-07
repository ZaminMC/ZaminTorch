package net.zamin.engine.player;

import net.zamin.api.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The player's 2x2 crafting grid: window state beside the inventory (§429).
 * The grid holds the ingredients the player places through window clicks; the
 * result preview is computed by the crafting service from a snapshot, and the
 * consumption happens here ({@link #consumeOne()}), one unit per non-empty
 * cell per craft — the historical cost model.
 *
 * <p>Ownership: mutated only by the simulation context, exactly like
 * {@link PlayerInventory}. The grid shares the inventory's cursor (one mouse);
 * its contents are transient window state — closing the window or leaving the
 * server returns everything to the inventory (a remainder is thrown into the
 * world, nothing is lost). The grid is therefore not persisted.</p>
 */
public final class CraftingGrid {

    public static final int SLOTS = 4; // 2x2, row-major

    private final ItemStack[] cells = new ItemStack[SLOTS];

    public CraftingGrid() {
        Arrays.fill(cells, ItemStack.EMPTY);
    }

    /** @return the stack in one grid cell (never null; empty when unused). */
    public ItemStack cell(int index) {
        rangeCheck(index);
        return cells[index];
    }

    /**
     * Semantic left/right click on one grid cell (window click mode 0),
     * sharing the inventory's cursor through the historical click semantics.
     */
    public void clickCell(int index, int button, PlayerInventory inventory) {
        rangeCheck(index);
        WindowClicks.click(cells, index, button, inventory.cursorBox());
    }

    /**
     * Semantic shift-click (window click mode 1) out of the grid: the whole
     * cell stack moves into the inventory (historical fill order); a remainder
     * when the inventory is full stays in the cell.
     */
    public void quickMoveTo(int index, PlayerInventory inventory) {
        rangeCheck(index);
        ItemStack moving = cells[index];
        if (moving.isEmpty()) {
            return;
        }
        cells[index] = ItemStack.EMPTY;
        ItemStack remaining = inventory.pickUp(moving);
        if (!remaining.isEmpty()) {
            cells[index] = remaining; // target full: the stack stays
        }
    }

    /**
     * Semantic drop-click (window click mode 4) on a grid cell: removes one
     * unit or the whole stack. @return the stack that left the grid (possibly
     * empty); the caller throws it into the world.
     */
    public ItemStack dropFromCell(int index, boolean entireStack) {
        rangeCheck(index);
        ItemStack current = cells[index];
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack dropped = entireStack ? current : current.split(1);
        cells[index] = current.withCount(current.count() - dropped.count());
        return dropped;
    }

    /**
     * The historical crafting cost: one unit leaves every non-empty cell.
     * Called by the engine only after the result take has succeeded, so the
     * transition is atomic from the player's point of view (§431).
     */
    public void consumeOne() {
        for (int i = 0; i < SLOTS; i++) {
            if (!cells[i].isEmpty()) {
                cells[i] = cells[i].withCount(cells[i].count() - 1);
            }
        }
    }

    /**
     * Returns every cell's contents to the inventory (window close, leave):
     * fills matching then empty slots, historical order. @return the stacks
     * that did not fit — the caller throws each into the world so nothing is
     * lost (the historical close behavior drops overflow at the player).
     */
    public List<ItemStack> returnAllTo(PlayerInventory inventory) {
        List<ItemStack> overflow = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack carried = cells[i];
            cells[i] = ItemStack.EMPTY;
            if (carried.isEmpty()) {
                continue;
            }
            ItemStack back = inventory.pickUp(carried);
            if (!back.isEmpty()) {
                overflow.add(back);
            }
        }
        return overflow;
    }

    /** @return a defensive copy of the cells, index-aligned (row-major 2x2). */
    public ItemStack[] snapshotArray() {
        return cells.clone();
    }

    /** @return a defensive copy as a list, index-aligned (row-major 2x2). */
    public List<ItemStack> snapshot() {
        return new ArrayList<>(List.of(cells));
    }

    /** @return whether every cell is empty. */
    public boolean isEmpty() {
        for (ItemStack cell : cells) {
            if (!cell.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void rangeCheck(int index) {
        if (index < 0 || index >= SLOTS) {
            throw new IllegalArgumentException("Crafting grid cell out of range: " + index);
        }
    }
}
