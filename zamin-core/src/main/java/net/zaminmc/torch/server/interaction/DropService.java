package net.zaminmc.torch.server.interaction;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BlockBehavior;
import net.zaminmc.torch.server.block.BlockBehaviorTable;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.item.ToolClass;
import net.zaminmc.torch.server.item.Tools;
import net.zaminmc.torch.util.Identifier;

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
        // The infested family (Slice 18c — InfestedBlock.getSilkTouchDrop
        // lines 59-76 + getBaseDropCount 0): the egg never drops by hand
        // (the silverfish burst is the break path's spawn, not a drop
        // row); under silk the HOST's item — the stone brick variants
        // riding the item's damage field (mossy 1, cracked 2, chiseled
        // 3).
        if (net.zaminmc.torch.server.block.InfestedBlocks.isInfested(broken.identifier())) {
            if (!silkTouch) {
                return List.of();
            }
            Identifier hostItem = net.zaminmc.torch.server.block.InfestedBlocks.silkDropItem(
                    broken.identifier());
            ItemType itemType = BuiltinItems.lookup(hostItem).orElse(null);
            if (itemType == null) {
                throw new IllegalStateException(
                        "Infested egg's silk item is unregistered: " + hostItem);
            }
            return List.of(new ItemStack(itemType, 1,
                    net.zaminmc.torch.server.block.InfestedBlocks.silkDropDamage(
                            broken.identifier())));
        }
        // The ender chest family (Slice 29a — EnderChestBlock lines 48-60:
        // getDropItem OBSIDIAN, getBaseDropCount 8, hasSilkTouchDrops
        // true): silk takes the chest itself (the item shares legacy 130),
        // otherwise eight obsidian flat (the default quantityDropped walk
        // applies no fortune bonus — the ender chest does not override
        // quantityDroppedWithBonus).
        if (net.zaminmc.torch.server.chest.EnderChestBlocks.isFamily(broken)) {
            if (silkTouch) {
                ItemType self = BuiltinItems.lookup(
                        Identifier.parse("minecraft:ender_chest")).orElse(null);
                if (self == null) {
                    throw new IllegalStateException(
                            "Ender chest item is unregistered: minecraft:ender_chest");
                }
                return List.of(ItemStack.of(self, 1));
            }
            ItemType obsidian = BuiltinItems.lookup(
                    Identifier.parse("minecraft:obsidian")).orElse(null);
            if (obsidian == null) {
                throw new IllegalStateException("Obsidian item is unregistered");
            }
            return List.of(new ItemStack(obsidian, 8));
        }
        // The dye-coloring families' silk arms (Slice 31 — StainedGlassBlock
        // + StainedGlassPaneBlock's `hasSilkTouchDrops` true over
        // getBaseDropCount 0 / PaneBlock's hasDrops false): silk returns the
        // family's own item with the damage field carrying the color (the
        // reference's getDropItemMetadata), the bare hand gets nothing at
        // all. The wool/carpet/stained-clay families drop normally with
        // damage (their rows) — silk is observably identical to them.
        if (net.zaminmc.torch.server.block.DyeFamilyBlocks.isStainedGlass(broken.identifier())
                || net.zaminmc.torch.server.block.DyeFamilyBlocks.isStainedGlassPane(broken.identifier())) {
            if (!silkTouch) {
                return List.of();
            }
            Identifier familyItem = net.zaminmc.torch.server.block.DyeFamilyBlocks
                    .dropItemOf(broken.identifier()).orElseThrow();
            ItemType itemType = BuiltinItems.lookup(familyItem).orElse(null);
            if (itemType == null) {
                throw new IllegalStateException(
                        "Dye family item is unregistered: " + familyItem);
            }
            int color = net.zaminmc.torch.server.block.DyeFamilyBlocks.colorOf(broken.identifier());
            return List.of(new ItemStack(itemType, 1, color));
        }
        // The leaves family (Slice 35 — AbstractLeavesBlock.dropItems lines
        // 178-203): shears or silk takes the leaf itself (the family item
        // with the variant damage — the reference's getSilkTouchDrop); the
        // bare hand rolls TWO independent walks — the sapling at the wood's
        // own chance band (1/20, the jungle leaf's 1/40 override, the
        // fortune relief -2<<level clamped at 10), then the OAK-only apple
        // at 1/200 (dropAppleWithChance with its -10<<level relief clamped
        // at 40).
        if (net.zaminmc.torch.server.block.WoodVariantBlocks.isLeaves(broken.identifier())) {
            int variant = net.zaminmc.torch.server.block.WoodVariantBlocks
                    .variantOf(broken.identifier());
            boolean shears = heldTool != null
                    && Tools.specOf(heldTool)
                            .map(spec -> spec.toolClass() == ToolClass.SHEARS)
                            .orElse(false);
            if (silkTouch || shears) {
                Identifier familyItem = variant < 4
                        ? net.zaminmc.torch.server.block.WoodVariantBlocks.LEAVES_ITEM
                        : net.zaminmc.torch.server.block.WoodVariantBlocks.LEAVES2_ITEM;
                ItemType itemType = BuiltinItems.lookup(familyItem).orElse(null);
                if (itemType == null) {
                    throw new IllegalStateException(
                            "Leaves family item is unregistered: " + familyItem);
                }
                return List.of(new ItemStack(itemType, 1,
                        net.zaminmc.torch.server.block.WoodVariantBlocks
                                .wireMetaOf("leaves", variant)));
            }
            java.util.List<ItemStack> leafDrops = new ArrayList<>(2);
            int saplingChance = net.zaminmc.torch.server.block.WoodVariantBlocks
                    .saplingChanceOf(broken.identifier(), fortune);
            if (random.nextInt(saplingChance) == 0) {
                ItemType sapling = BuiltinItems.lookup(
                        net.zaminmc.torch.server.block.WoodVariantBlocks.SAPLING_ITEM)
                        .orElseThrow();
                leafDrops.add(new ItemStack(sapling, 1, variant));
            }
            int appleChance = net.zaminmc.torch.server.block.WoodVariantBlocks
                    .appleChanceOf(broken.identifier(), fortune);
            if (appleChance > 0 && random.nextInt(appleChance) == 0) {
                ItemType apple = BuiltinItems.lookup(
                        Identifier.parse("minecraft:apple")).orElseThrow();
                leafDrops.add(ItemStack.of(apple, 1));
            }
            return leafDrops;
        }
        // The huge-mushroom family (Slice 40 — MushroomBlock.dropItems
        // lines 27-50: silk takes the block itself (the full-cube default's
        // own silk arm), the bare hand walks getBaseDropCount's
        // max(0, nextInt(10) - 7) — 0 to 2 of the PLANT item
        // (getDropItem's Item.byBlock(this.plant))).
        if (net.zaminmc.torch.server.block.MushroomBlocks.isMushroomBlock(broken)) {
            String family = net.zaminmc.torch.server.block.MushroomBlocks.familyOf(broken);
            if (silkTouch) {
                ItemType self = BuiltinItems.lookup(
                        net.zaminmc.torch.server.block.MushroomBlocks.blockItem(family))
                        .orElseThrow();
                return List.of(ItemStack.of(self, 1));
            }
            int count = net.zaminmc.torch.server.block.MushroomBlocks.baseDropCount(random);
            if (count == 0) {
                return List.of();
            }
            ItemType plant = BuiltinItems.lookup(
                    net.zaminmc.torch.server.block.MushroomBlocks.plantOf(family)).orElseThrow();
            return List.of(ItemStack.of(plant, count));
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
        // The lapis ore's variable base (Slice 32 — OreBlock.getBaseDropCount
        // line 42: {@code 4 + nextInt(5)}) with the OreBlock fortune
        // multiplier (getDropCount lines 45-56: {@code nextInt(fortune + 2)
        // - 1} clamped at 0, then {@code base * (i + 1)}) — the drop is
        // foreign (the lapis dye), so the multiplier applies.
        if (broken.identifier().value().equals("lapis_ore")) {
            int base = 4 + random.nextInt(5);
            if (fortune > 0) {
                int i = random.nextInt(fortune + 2) - 1;
                if (i < 0) {
                    i = 0;
                }
                base *= (i + 1);
            }
            ItemType lapis = BuiltinItems.lookup(
                    Identifier.parse("minecraft:lapis")).orElseThrow();
            return List.of(ItemStack.of(lapis, base));
        }
        // The redstone ore's own walk (Slice 32 — RedstoneOreBlock lines
        // 71-77): the drop is the dust, base {@code 4 + nextInt(2)}, and
        // fortune ADDS {@code nextInt(fortune + 1)} — its own override of
        // the OreBlock multiplier shape, never a multiple.
        if (broken.identifier().value().equals("redstone_ore")
                || broken.identifier().value().equals("lit_redstone_ore")) {
            int count = 4 + random.nextInt(2) + random.nextInt(fortune + 1);
            ItemType dust = BuiltinItems.lookup(
                    Identifier.parse("minecraft:redstone")).orElseThrow();
            return List.of(ItemStack.of(dust, count));
        }
        List<ItemStack> drops = new ArrayList<>();
        for (BlockBehavior.Drop drop : behavior.drops()) {
            // First successful roll wins (the historical quantityDropped model):
            // gravel rolls the flint chance, otherwise the next entry takes
            // the slot.
            // The namespace never rides value() (the Identifier record's
            // own split) — the original "minecraft:gravel" comparison
            // never matched, so Fortune never widened the flint chance
            // (a latent Slice 32 find; the repair restores the reference's
            // GravelBlock.getDropItem walk).
            boolean gravelFlint = broken.identifier().value().equals("gravel")
                    && drop.item().value().equals("flint");
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
            drops.add(drop.damage() == 0
                    ? ItemStack.of(itemType, count)
                    : new ItemStack(itemType, count, drop.damage()));
        }
        return drops;
    }
}
