package net.zaminmc.torch.server.crafting;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Identifier;

/**
 * The crafting remainders (Slice 46 — the reference's
 * {@code Item.getRecipeRemainder()} over
 * {@code Container.getRemainingItems}): a consumed ingredient that leaves
 * its container behind in the grid cell instead of vanishing.
 *
 * <p>The 1.8.8 set is the single milk bucket ({@code Item.java} line 619's
 * {@code setRecipeRemainder} on the bucket) — every craft that spends milk
 * (the cake's three) returns the empty buckets to the grid.</p>
 */
public final class CraftingRemainders {

    private CraftingRemainders() {
    }

    /**
     * @return the container item left in the cell for this consumed
     *         ingredient, or null when the ingredient simply vanishes.
     */
    public static ItemStack remainderOf(ItemStack consumed) {
        if (consumed != null && !consumed.isEmpty()
                && consumed.type().identifier()
                        .equals(Identifier.parse("minecraft:milk_bucket"))) {
            return new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.BUCKET, 1, 0);
        }
        return null;
    }
}
