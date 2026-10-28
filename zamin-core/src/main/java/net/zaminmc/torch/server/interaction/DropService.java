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
     * The wash-out drops (the fluid spread's {@code dropItems(world, pos,
     * state, 0)} — FlowingLiquidBlock.spreadTo's displaced-block arm, lines
     * 119-125): the block's own drop walk at fortune 0 with NO harvest
     * gate — the drop path carries no player or tool context (the tool
     * gate lives in the player's break path alone, the reference's own
     * split). A water stream washing a torch, a flower or a rail pops
     * the block's own item exactly like a no-tool break that ignores the
     * harvest rule.
     */
    public List<ItemStack> washedDrops(BlockType washed) {
        Objects.requireNonNull(washed, "washed");
        BlockBehavior behavior = BlockBehaviorTable.of(washed.identifier()).orElse(null);
        if (behavior == null || !behavior.diggable()) {
            return List.of();
        }
        return dropWalk(washed, behavior, null, false, 0);
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
        // The snow layer's own player arm (the mining-speed parity audit —
        // SnowLayerBlock.afterMinedByPlayer lines 102-106): the LAYERS + 1
        // snowballs belong to the PLAYER-DIG COMPLETION alone, over the
        // shovel verdict the gate above just answered — the generic walk the
        // row feeds (the wash-out, the piston break) carries the class's own
        // getBaseDropCount ZERO, so the engine keeps the row empty and the
        // player law here, exactly the reference's own split. The engine's
        // single-layer state answers 2.
        if (broken.identifier().value().equals("snow_layer")) {
            ItemType snowball = BuiltinItems.lookup(Identifier.parse("minecraft:snowball"))
                    .orElse(null);
            if (snowball == null) {
                throw new IllegalStateException("Snow layer's snowball is unregistered");
            }
            return List.of(ItemStack.of(snowball, 2));
        }
        return dropWalk(broken, behavior, heldTool, silkTouch, fortune);
    }

    /** The shared drop walk (every family arm — the harvest gate stays at the callers). */
    private List<ItemStack> dropWalk(BlockType broken, BlockBehavior behavior,
                                     ItemType heldTool, boolean silkTouch, int fortune) {
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
            // The decay band's members break exactly like their fresh form
            // (LeafDecayBlocks' derived states normalize to the base leaf
            // before the family consults — the jungle 1/40 band and the
            // oak apple consult the WOOD, never the decay bits).
            int variant = net.zaminmc.torch.server.block.WoodVariantBlocks
                    .variantOfLeaf(broken.identifier());
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
        // The cocoa's dye rolls (Slice 43 — CocoaBlock.dropItems lines
        // 137-145: 1 dye below age 2, 3 at it — the brown damage 12).
        if (net.zaminmc.torch.server.block.CocoaBlocks.isCocoa(broken)) {
            int age = net.zaminmc.torch.server.block.CocoaBlocks.ageOf(broken);
            ItemType dye = BuiltinItems.lookup(
                    Identifier.parse("minecraft:dye")).orElseThrow();
            return List.of(new ItemStack(dye, age >= 2 ? 3 : 1,
                    net.zaminmc.torch.server.block.CocoaBlocks.BROWN_DYE_DAMAGE));
        }
        // The tall-grass family (TallPlantBlock lines 63-93 + DeadBushBlock
        // lines 39-51 — the flora slices' shared shears law): the SHEARS arm
        // of afterMinedByPlayer drops the family's own item with the variant
        // in the damage field (the tallgrass item — the grass's TYPE 1, the
        // fern's TYPE 2 — or the dead bush's own item) and never rolls the
        // seeds; every other path runs getDropItem's walk alone — the
        // 1/8 wheat-seeds roll per getDropCount's iteration ({@code 1 +
        // nextInt(fortune * 2 + 1)}, one seed each), and the dead bush's
        // null arm yields nothing by hand. The pre-fix behavior table rows
        // self-dropped at chance 1.0, so every by-hand break yielded the
        // tallgrass item (the wire's "Shrub") and the fern row even named
        // the unregistered minecraft:fern item; this arm owns the law now,
        // mirroring the double-plant break's shears shape (variant damage
        // over the tallgrass item). The engine merges the per-iteration
        // single seeds into one stack (vanilla merges at pickup).
        String tallGrassVariant = net.zaminmc.torch.server.block.TallGrassFamily
                .variantOf(broken.identifier());
        if (tallGrassVariant != null) {
            boolean shears = heldTool != null
                    && Tools.specOf(heldTool)
                            .map(spec -> spec.toolClass() == ToolClass.SHEARS)
                            .orElse(false);
            if (shears) {
                Identifier self = net.zaminmc.torch.server.block.TallGrassFamily
                        .shearsItemOf(tallGrassVariant);
                ItemType itemType = BuiltinItems.lookup(self).orElse(null);
                if (itemType == null) {
                    throw new IllegalStateException(
                            "Tall-grass family shears item is unregistered: " + self);
                }
                return List.of(new ItemStack(itemType, 1,
                        net.zaminmc.torch.server.block.TallGrassFamily
                                .shearsDamageOf(tallGrassVariant)));
            }
            // DeadBushBlock.getDropItem lines 39-41: null — nothing by hand.
            if (tallGrassVariant.equals("dead_bush")) {
                return List.of();
            }
            // TallPlantBlock.getDropCount lines 68-70 + getDropItem lines
            // 63-65: the iteration walk, each iteration's 1/8 seed.
            int iterations = 1 + random.nextInt(fortune * 2 + 1);
            int seeds = 0;
            for (int i = 0; i < iterations; i++) {
                if (random.nextInt(8) == 0) {
                    seeds++;
                }
            }
            if (seeds == 0) {
                return List.of();
            }
            ItemType seedItem = BuiltinItems.lookup(
                    Identifier.parse("minecraft:wheat_seeds")).orElseThrow();
            return List.of(ItemStack.of(seedItem, seeds));
        }
        // The vine's shears law (the silk-arm parity audit — VineBlock
        // lines 303-318: getDropItem NULL and getBaseDropCount 0, so every
        // non-shears break yields nothing; afterMinedByPlayer's SHEARS
        // arm drops the vine itself). The pre-fix table row was empty and
        // no branch existed, so even a shears break yielded nothing.
        if (broken.identifier().value().equals("vine")) {
            boolean vineShears = heldTool != null
                    && Tools.specOf(heldTool)
                            .map(spec -> spec.toolClass() == ToolClass.SHEARS)
                            .orElse(false);
            if (vineShears) {
                ItemType vine = BuiltinItems.lookup(
                        Identifier.parse("minecraft:vine")).orElseThrow();
                return List.of(ItemStack.of(vine, 1));
            }
            return List.of();
        }
        // The double-plant lower's shears law (the silk-arm parity audit
        // — DoublePlantBlock.afterMinedByPlayer lines 141-149 +
        // onMinedByPlayer lines 181-191: SHEARS on the LOWER half of the
        // grass/fern pair answers TWO of the tallgrass item — the grass
        // TYPE damage 1, the fern TYPE 2 — REPLACING the by-hand walk;
        // the flowers' variants answer false from onMinedByPlayer, so
        // they keep the normal rows (themselves). The pre-fix walk had
        // no shears arm at all, so a shears break of the double grass
        // rolled the 1/8 seeds instead.
        if (net.zaminmc.torch.server.block.DoublePlantBlocks.isDoublePlant(broken)
                && !net.zaminmc.torch.server.block.DoublePlantBlocks.isUpper(broken.identifier())) {
            boolean pairShears = heldTool != null
                    && Tools.specOf(heldTool)
                            .map(spec -> spec.toolClass() == ToolClass.SHEARS)
                            .orElse(false);
            if (pairShears) {
                int variant = net.zaminmc.torch.server.block.DoublePlantBlocks
                        .variantOf(broken.identifier());
                if (variant == 2 || variant == 3) {
                    ItemType tallGrassItem = BuiltinItems.lookup(
                            Identifier.parse("minecraft:tall_grass")).orElseThrow();
                    return List.of(new ItemStack(tallGrassItem, 2, variant == 2 ? 1 : 2));
                }
            }
        }
        // The melon block's slice walk (Slice 41 — MelonBlock lines 17-31:
        // getDropCount's {@code min(9, 3 + nextInt(5) + nextInt(1 + fortune))}
        // of the melon slice). The silk-arm parity audit: the melon is a
        // full cube without a block entity, so the reference's default
        // hasSilkTouchDrops gate (Block.java lines 784-787) is TRUE — silk
        // takes the melon BLOCK itself (Item.byBlock's own 1:1); the
        // branch runs ahead of the generic silk arm, so the gate rides
        // here.
        if (broken.identifier().value().equals("melon_block")) {
            if (silkTouch) {
                ItemType whole = BuiltinItems.lookup(
                        Identifier.parse("minecraft:melon_block")).orElseThrow();
                return List.of(ItemStack.of(whole, 1));
            }
            int count = Math.min(9, 3 + random.nextInt(5) + random.nextInt(1 + fortune));
            ItemType slice = BuiltinItems.lookup(
                    Identifier.parse("minecraft:melon")).orElseThrow();
            return List.of(ItemStack.of(slice, count));
        }
        // The stems' seed roll (Slice 41 — StemBlock.dropItems lines 120-131:
        // three independent {@code nextInt(15) <= age} rolls of the seeds).
        String stemFamily = net.zaminmc.torch.server.block.MelonBlocks
                .stemFamilyOf(broken.identifier());
        if (stemFamily != null) {
            int age = net.zaminmc.torch.server.block.WorldSolidity
                    .cropStageOf(broken.identifier());
            int count = 0;
            for (int i = 0; i < 3; i++) {
                if (random.nextInt(15) <= age) {
                    count++;
                }
            }
            if (count == 0) {
                return List.of();
            }
            ItemType seeds = BuiltinItems.lookup(
                    net.zaminmc.torch.server.block.MelonBlocks.seedsOf(stemFamily))
                    .orElseThrow();
            return List.of(ItemStack.of(seeds, count));
        }
        // The silk-arm parity audit — the damage-carrying silk drops the
        // generic arm cannot express (its Item.byBlock lookup resolves the
        // block's OWN item, and these blocks ride a shared family item
        // with the variant in the damage field):
        if (silkTouch) {
            // The podzol (DirtBlock + Block.getSilkTouchDrop lines 791-797:
            // the silk stack keeps the state's metadata — getMetadataFromState
            // answers PODZOL 2, so the dirt item carries damage 2; the
            // by-hand row above already carries getDropItemMetadata's own
            // DIRT downgrade — lines 72-79).
            if (broken.identifier().value().equals("podzol")) {
                ItemType dirt = BuiltinItems.lookup(
                        Identifier.parse("minecraft:dirt")).orElseThrow();
                return List.of(new ItemStack(dirt, 1, 2));
            }
            // The stained glass pair (StainedGlassBlock line 55 +
            // PaneBlock line 144: hasSilkTouchDrops TRUE — the non-cube
            // override); the silk stack is the FAMILY item with the COLOR
            // in the damage field, the by-hand rows stay empty.
            if (net.zaminmc.torch.server.block.DyeFamilyBlocks.isStainedGlass(broken.identifier())) {
                ItemType pane = BuiltinItems.lookup(
                        net.zaminmc.torch.server.block.DyeFamilyBlocks.STAINED_GLASS_ITEM)
                        .orElseThrow();
                return List.of(new ItemStack(pane, 1,
                        net.zaminmc.torch.server.block.DyeFamilyBlocks.colorOf(broken.identifier())));
            }
            if (net.zaminmc.torch.server.block.DyeFamilyBlocks.isStainedGlassPane(broken.identifier())) {
                ItemType pane = BuiltinItems.lookup(
                        net.zaminmc.torch.server.block.DyeFamilyBlocks.STAINED_GLASS_PANE_ITEM)
                        .orElseThrow();
                return List.of(new ItemStack(pane, 1,
                        net.zaminmc.torch.server.block.DyeFamilyBlocks.colorOf(broken.identifier())));
            }
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
        // The glowstone band (GlowstoneBlock lines 15-29): getDropCount's
        // clamp(getBaseDropCount + nextInt(fortune + 1), 1, 4) over the
        // 2 + nextInt(3) base. The pre-fix table carried the band as THREE
        // independent 1/3 rows and the walk's add-all rolled EACH: ~30% of
        // breaks dropped NOTHING (all three failed) and ~3.7% dropped NINE
        // dust (all three hit) where vanilla always answers 1-4.
        if (broken.identifier().value().equals("glowstone")) {
            int count = Math.max(1, Math.min(4,
                    2 + random.nextInt(3) + random.nextInt(fortune + 1)));
            ItemType dust = BuiltinItems.lookup(
                    Identifier.parse("minecraft:glowstone_dust")).orElseThrow();
            return List.of(ItemStack.of(dust, count));
        }
        // The gravel XOR (GravelBlock.getDropItem): flint at
        // nextInt(10 - fortune * 3) == 0 (the fortune clamped at 3 — the
        // 10/7/4/1 ladder), ELSE the block itself — exactly ONE stack
        // either way, the count never multiplied. The pre-fix walk rolled
        // the flint chance then CONTINUED to the gravel row, so a
        // successful flint roll dropped BOTH stacks, and the generic
        // foreign-drop multiplier even multiplied the flint's count with
        // Fortune (vanilla widens only the chance).
        if (broken.identifier().value().equals("gravel")) {
            int clampedFortune = Math.min(fortune, 3);
            if (random.nextInt(Math.max(1, 10 - clampedFortune * 3)) == 0) {
                ItemType flint = BuiltinItems.lookup(
                        Identifier.parse("minecraft:flint")).orElseThrow();
                return List.of(ItemStack.of(flint, 1));
            }
            ItemType gravel = BuiltinItems.lookup(
                    Identifier.parse("minecraft:gravel")).orElseThrow();
            return List.of(ItemStack.of(gravel, 1));
        }
        // The sea lantern's own band (SeaLanternBlock lines 20-28):
        // clamp(getBaseDropCount + nextInt(fortune + 1), 1, 5) over the
        // 2 + nextInt(2) base — the pre-fix row folded the whole band to a
        // flat 2; silk took the lantern itself above (hasSilkTouchDrops
        // true joined the silk set with this slice).
        if (broken.identifier().value().equals("sea_lantern")) {
            int count = Math.max(1, Math.min(5,
                    2 + random.nextInt(2) + random.nextInt(fortune + 1)));
            ItemType crystals = BuiltinItems.lookup(
                    Identifier.parse("minecraft:prismarine_crystals")).orElseThrow();
            return List.of(ItemStack.of(crystals, count));
        }
        // The nether wart's mature band (NetherWartBlock.dropItems lines
        // 51-63): age 3 answers 2 + nextInt(3) plus the fortune bonus
        // nextInt(fortune + 1) — rolled ONLY when fortune > 0, the
        // reference's own gate; the younger stages stay the table's
        // exact-one rows. The pre-fix stage-3 row folded the band to a
        // flat 2.
        if (broken.identifier().value().equals("nether_wart_stage3")) {
            int count = 2 + random.nextInt(3);
            if (fortune > 0) {
                count += random.nextInt(fortune + 1);
            }
            ItemType wart = BuiltinItems.lookup(
                    Identifier.parse("minecraft:nether_wart")).orElseThrow();
            return List.of(ItemStack.of(wart, count));
        }
        // The crops' mature bands (WheatBlock.dropItems lines 91-105 with
        // the CarrotsBlock/PotatoesBlock overrides): the standard walk
        // answers EXACTLY ONE item at every age (the plant item at age 7 —
        // WheatBlock drops the standard walk's fortune to 0), then age 7
        // alone rolls the seed bonus — 3 + fortune iterations of
        // nextInt(15) <= 7, one seed each. The wheat's seeds are
        // wheat_seeds beside the wheat; the carrots' and potatoes' seed
        // IS the crop item, so the one and the bonus merge into a single
        // stack; the potatoes add the family's own 1/50 poisonous roll at
        // age 7 (PotatoesBlock lines 25-31). The pre-fix rows folded each
        // band to a flat count (wheat 1+1, carrot 2, potato 2 — no bonus
        // walk, no poison roll).
        String cropValue = broken.identifier().value();
        boolean matureWheat = cropValue.equals("wheat_stage7");
        boolean matureCarrot = cropValue.equals("carrots_stage7");
        boolean maturePotato = cropValue.equals("potatoes_stage7");
        if (matureWheat || matureCarrot || maturePotato) {
            ItemType plantItem = BuiltinItems.lookup(Identifier.parse(matureWheat
                    ? "minecraft:wheat" : matureCarrot
                    ? "minecraft:carrot" : "minecraft:potato")).orElseThrow();
            ItemType seedItem = matureWheat
                    ? BuiltinItems.lookup(
                            Identifier.parse("minecraft:wheat_seeds")).orElseThrow()
                    : plantItem;
            java.util.List<ItemStack> cropDrops = new ArrayList<>(2);
            cropDrops.add(ItemStack.of(plantItem, 1));
            int bonus = 0;
            for (int i = 0; i < 3 + fortune; i++) {
                if (random.nextInt(15) <= 7) {
                    bonus++;
                }
            }
            if (matureWheat) {
                if (bonus > 0) {
                    cropDrops.add(ItemStack.of(seedItem, bonus));
                }
            } else {
                // the carrot's and potato's seed IS the crop: the one and
                // the bonus merge into the single stack
                cropDrops.set(0, ItemStack.of(plantItem, 1 + bonus));
            }
            if (maturePotato && random.nextInt(50) == 0) {
                cropDrops.add(ItemStack.of(BuiltinItems.lookup(
                        Identifier.parse("minecraft:poisonous_potato")).orElseThrow(), 1));
            }
            return cropDrops;
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
            // The silk-arm parity audit — the LIT form's own quirk
            // (Item.byBlock(LIT_REDSTONE_ORE) resolves null: no item is
            // registered for the lit block in the reference, so the
            // silk stack is EMPTY and NOTHING drops — and the dig-start
            // light swap, the reference's own startMining arm, means the
            // LIT form is the one actually broken). The UNLIT form never
            // reaches this arm with silk: the silk set's own row takes
            // the ore itself first.
            if (silkTouch) {
                return List.of();
            }
            int count = 4 + random.nextInt(2) + random.nextInt(fortune + 1);
            ItemType dust = BuiltinItems.lookup(
                    Identifier.parse("minecraft:redstone")).orElseThrow();
            return List.of(ItemStack.of(dust, count));
        }
        List<ItemStack> drops = new ArrayList<>();
        for (BlockBehavior.Drop drop : behavior.drops()) {
            // Each row rolls its own chance independently (the reference's
            // per-quantityDropped walk over one drop entry at a time); the
            // gravel's flint XOR and the glowstone's band moved to their
            // own family arms above — the walk below never sees them.
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
            // The foreign-drop fortune multiplier (OreBlock.getDropCount
            // lines 45-56: {@code nextInt(fortune + 2) - 1} clamped at 0,
            // then {@code base * (i + 1)}) — the reference's OreBlock ALONE
            // owns this shape, so the walk applies it only to the plain
            // ore family with foreign rows (coal, diamond, emerald, quartz —
            // lapis and redstone carry their own arms above): every other
            // foreign row — the bookshelf's three books, clay's four balls,
            // the double slabs' pair, the crops' seeds — answers its row's
            // count FLAT. The pre-fix walk multiplied every foreign row.
            int count = drop.count();
            if (fortune > 0 && !drop.item().equals(broken.identifier())
                    && ORE_FAMILY_WITH_FOREIGN_DROPS.contains(broken.identifier())) {
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

    /**
     * The plain OreBlock family whose foreign rows take the reference's
     * fortune multiplier (OreBlock.getDropCount — iron and gold ore drop
     * THEMSELVES so the multiplier never fires there; lapis and redstone
     * override the shape with their own arms above).
     */
    private static final java.util.Set<Identifier> ORE_FAMILY_WITH_FOREIGN_DROPS =
            java.util.Set.of(Identifier.parse("minecraft:coal_ore"),
                    Identifier.parse("minecraft:diamond_ore"),
                    Identifier.parse("minecraft:emerald_ore"),
                    Identifier.parse("minecraft:quartz_ore"));
}
