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
        return dropsFor(broken, heldTool, false, 0);
    }

    /**
     * The drop walk with the loot enchantments riding (the reference's
     * {@code Block.afterMinedByPlayer}, reference/1.8.8 block/Block lines
     * 772-781): the silk arm drops the block's own item when the block is
     * silk-touchable ({@code hasSilkTouchDrops} — the cube-without-TE gate
     * encoded as {@link BlockBehaviorTable#isSilkTouchable}); otherwise the
     * normal rows roll with the fortune walk — the foreign-drop multiplier
     * (the {@code OreBlock.getDropCount} shape: {@code nextInt(fortune + 2)
     * - 1} clamped at 0, then {@code base * (i + 1)}) applies only when the
     * row's item differs from the block itself (the vanilla quirk: iron and
     * gold ore, dropping themselves, gain nothing from Fortune), and
     * gravel's flint chance widens as {@code nextInt(10 - fortune * 3) == 0}
     * (the {@code GravelBlock.getDropItem} shape — Fortune III gravel is
     * always flint). The vanilla per-iteration single stacks merge at
     * pickup; the engine emits the multiplied count in one stack.
     *
     * @param silkTouch whether the held tool carries Silk Touch
     * @param fortune    the held tool's Fortune level (0 = none)
     */
    public List<ItemStack> dropsFor(BlockType broken, ItemType heldTool,
                                    boolean silkTouch, int fortune) {
        Objects.requireNonNull(broken, "broken");
        BlockBehavior behavior = BlockBehaviorTable.of(broken.identifier()).orElse(null);
        if (behavior == null || !behavior.diggable()) {
            return List.of();
        }
        if (!BlockBehaviorTable.canHarvest(behavior, heldTool)) {
            return List.of(); // historical: breakable but yields nothing by hand
        }
        // The silk arm: the block's own item (the reference's
        // getSilkTouchDrop — Item.byBlock with the state metadata), one
        // stack, no chance roll.
        if (silkTouch && BlockBehaviorTable.isSilkTouchable(broken.identifier())) {
            ItemType self = BuiltinItems.lookup(broken.identifier()).orElse(null);
            if (self == null) {
                // A silk-touchable block without a registered item: fall
                // through to the normal rows (the vanilla gate's data is
                // trusted; this is a registration bug surfaced loudly).
                throw new IllegalStateException(
                        "Silk-touchable block has no registered item: " + broken.identifier());
            }
            return List.of(ItemStack.of(self, 1));
        }
        List<ItemStack> drops = new ArrayList<>();
        for (BlockBehavior.Drop drop : behavior.drops()) {
            // First successful roll wins (the historical quantityDropped model):
            // gravel rolls the flint chance, otherwise the next entry takes
            // the slot.
            boolean gravelFlint = broken.identifier().value().equals("minecraft:gravel")
                    && drop.item().value().equals("minecraft:flint");
            if (gravelFlint) {
                // The gravel flint walk (GravelBlock.getDropItem): the flint
                // chance widens with Fortune — nextInt(10 - fortune * 3) == 0
                // (10% base, 1/7 at I, 1/4 at II, always at III — the vanilla
                // quirk). The gravel fallback row below then wins the slot.
                if (random.nextInt(Math.max(1, 10 - fortune * 3)) != 0) {
                    continue;
                }
            } else if (random.nextDouble() > drop.chance()) {
                continue;
            }
            ItemType itemType = BuiltinItems.lookup(drop.item()).orElse(null);
            if (itemType == null) {
                // A behavior drop without a registered item type is a data bug;
                // skip it loudly rather than corrupt gameplay with a null stack.
                throw new IllegalStateException(
                        "Behavior table drop references unregistered item: " + drop.item());
            }
            // The foreign-drop fortune multiplier (OreBlock.getDropCount):
            // applies only when the row's item differs from the block —
            // the self-dropping blocks (iron ore, gold ore) gain nothing.
            int count = drop.count();
            if (fortune > 0 && !drop.item().equals(broken.identifier())) {
                int i = random.nextInt(fortune + 2) - 1;
                if (i < 0) {
                    i = 0;
                }
                count *= (i + 1);
            }
            drops.add(ItemStack.of(itemType, count));
        }
        return drops;
    }
}
