package net.zamin.engine.crafting;

import net.zamin.api.Identifier;
import net.zamin.api.ItemStack;

import java.util.Objects;

/**
 * One crafting ingredient rule: an item identity, optionally pinned to exact
 * metadata (the historical "bare id accepts any metadata" vs "(id, metadata)
 * demands the variant" distinction of the community dataset). The metadata
 * comparison rides the stack damage field, which for block items carries the
 * block metadata — the historical double duty.
 */
public record Ingredient(Identifier item, boolean exactMetadata, int metadata) {

    public Ingredient {
        Objects.requireNonNull(item, "item");
    }

    /** @return whether the stack satisfies this ingredient rule. */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!stack.type().identifier().equals(item)) {
            return false;
        }
        return !exactMetadata || stack.damage() == metadata;
    }
}
