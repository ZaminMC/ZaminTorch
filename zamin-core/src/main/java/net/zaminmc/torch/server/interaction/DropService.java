package net.zaminmc.torch.server.interaction;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BlockBehavior;
import net.zaminmc.torch.server.block.BlockBehaviorTable;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Drop calculation as a gameplay step, not a packet-handler concern (§434):
 *
 * <pre>block break -&gt; drop calculation -&gt; item entities / inventory</pre>
 *
 * <p>Pure given (block, held tool, random source): harvest gating, yield and
 * the first-hit-wins chance rolls come from the behavior table, so the same
 * rule base drives mining, future explosions and any other breaking
 * mechanism.</p>
 */
public final class DropService {

    private final Random random;

    public DropService(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

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
            // First successful roll wins (the historical quantityDropped model):
            // gravel rolls 10% flint, otherwise the next entry takes the slot.
            if (random.nextDouble() > drop.chance()) {
                continue;
            }
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
