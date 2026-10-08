package net.zaminmc.torch.server.player;

import net.zaminmc.torch.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A crafting grid: window state beside the inventory (§429). Two shapes exist:
 * the player inventory's 2x2 grid (window 0) and the crafting table's 3x3 grid
 * (its container window). The grid holds the ingredients the player places
 * through window clicks; the result preview is computed by the crafting
 * service from a snapshot, and the consumption happens here
 * ({@link #consumeOne()}), one unit per non-empty cell per craft — the
 * historical cost model.
 *
 * <p>Ownership: mutated only by the simulation context, exactly like
 * {@link PlayerInventory}. The grid shares the inventory's cursor (one mouse);
 * its contents are transient window state — closing the window or leaving the
 * server returns everything to the inventory (a remainder is thrown into the
 * world, nothing is lost). The grid is therefore not persisted.</p>
 */
public final class CraftingGrid {

    /** Cell count of the player inventory's 2x2 grid (window 0). */
    public static final int PLAYER_SLOTS = 4;
    /** Cell count of the crafting table's 3x3 grid (container window). */
    public static final int TABLE_SLOTS = 9;

    private final int rows;
    private final int cols;
    private final ItemStack[] cells;

    /** The player inventory's 2x2 grid (window 0, row-major). */
    public CraftingGrid() {
        this(2, 2);
    }

    /** A grid of the given shape (crafting table: 3x3). */
    public CraftingGrid(int rows, int cols) {
        if (rows < 1 || rows > 3 || cols < 1 || cols > 3) {
            throw new IllegalArgumentException(
                    "Crafting grid shape must be 1..3 x 1..3: " + rows + "x" + cols);
        }
        this.rows = rows;
        this.cols = cols;
        this.cells = new ItemStack[rows * cols];
        Arrays.fill(cells, ItemStack.EMPTY);
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    /** @return the cell count ({@code rows * cols}). */
    public int size() {
        return cells.length;
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
        for (int i = 0; i < cells.length; i++) {
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
        for (int i = 0; i < cells.length; i++) {
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

    /** @return a defensive copy of the cells, index-aligned (row-major). */
    public ItemStack[] snapshotArray() {
        return cells.clone();
    }

    /** @return a defensive copy as a list, index-aligned (row-major). */
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

    private void rangeCheck(int index) {
        if (index < 0 || index >= cells.length) {
            throw new IllegalArgumentException("Crafting grid cell out of range: " + index);
        }
    }
}
