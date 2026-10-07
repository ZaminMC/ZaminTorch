package net.zamin.engine.interaction;

import net.zamin.api.BlockType;
import net.zamin.api.ItemStack;
import net.zamin.api.ItemType;
import net.zamin.engine.block.BlockBehavior;
import net.zamin.engine.block.BlockBehaviorTable;
import net.zamin.engine.item.BuiltinItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Drop calculation as a gameplay step, not a packet-handler concern (§434):
 *
 * <pre>block break -&gt; drop calculation -&gt; item entities / inventory</pre>
 *
 * <p>Pure and deterministic given (block, held tool): harvest gating and
 * yield come from the behavior table, so the same rule base drives mining,
 * future explosions and any other breaking mechanism.</p>
 */
public final class DropService {

    /** @return the stacks the broken block yields to the world (empty when none). */
    public List<ItemStack> dropsFor(BlockType broken, ItemType heldTool) {
        Objects.requireNonNull(broken, "broken");
        BlockBehavior behavior = BlockBehaviorTable.of(broken.identifier()).orElse(null);
        if (behavior == null || !behavior.diggable()) {
            return List.of();
        }
        if (!BlockBehaviorTable.canHarvest(behavior, heldTool)) {
            return List.of(); // historical: breakable but yields nothing by hand
        }
        List<ItemStack> drops = new ArrayList<>();
        for (BlockBehavior.Drop drop : behavior.drops()) {
            ItemType itemType = BuiltinItems.lookup(drop.item()).orElse(null);
            if (itemType == null) {
                // A behavior drop without a registered item type is a data bug;
                // skip it loudly rather than corrupt gameplay with a null stack.
                throw new IllegalStateException(
                        "Behavior table drop references unregistered item: " + drop.item());
            }
            drops.add(ItemStack.of(itemType, drop.count()));
        }
        return drops;
    }
}
