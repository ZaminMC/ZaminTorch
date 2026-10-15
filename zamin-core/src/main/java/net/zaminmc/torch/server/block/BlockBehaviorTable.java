package net.zaminmc.torch.server.block;

import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.item.ToolClass;
import net.zaminmc.torch.server.item.ToolMaterial;
import net.zaminmc.torch.server.item.ToolSpec;
import net.zaminmc.torch.server.item.Tools;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Survival behavior data for registered blocks, keyed by canonical identifier.
 *
 * <p>Data source (community, per the reuse rule): values were generated from
 * PrismarineJS/minecraft-data, {@code data/pc/1.8/blocks.json} + {@code items.json}
 * + {@code materials.json} (MIT license, https://github.com/PrismarineJS/minecraft-data,
 * snapshot 2026-10), cross-checked against historical 1.8.8 behavior. No Java
 * binding of that dataset exists; embedding a generated subset with attribution
 * is the established pattern for JVM engines (the same approach Glowstone/Nukkit
 * take with their tables). The table only contains blocks the engine registers;
 * it grows with the registry, never speculatively.</p>
 *
 * <p>Drops reference canonical item identifiers. Dataset names differing from
 * modern canonical names are translated here (dataset {@code planks} →
 * {@code minecraft:oak_planks}, dataset {@code log} → {@code minecraft:oak_log});
 * the 1.8 adapter separately translates legacy numeric ids at the wire only.</p>
 */
public final class BlockBehaviorTable {

    private BlockBehaviorTable() {
    }

    /**
     * The silk-touch gate (the reference's {@code hasSilkTouchDrops}:
     * {@code isCube() && !hasBlockEntity}, reference/1.8.8 block/Block
     * lines 784-787) encoded as the registered block set where the silk
     * arm diverges from the normal drops. Blocks whose normal drop IS
     * themselves (planks, logs, chests, furnaces, torches) are observably
     * identical under either arm and stay outside the set; crops and the
     * other non-cubes fall through to the normal path (the reference gate
     * excludes them); TE blocks (chest, furnace, the enchanting table)
     * take their own drop walk in vanilla and join them.
     */
    private static final Set<Identifier> SILK_TOUCHABLE = Set.of(
            Identifier.parse("minecraft:stone"),
            Identifier.parse("minecraft:grass_block"),
            Identifier.parse("minecraft:gravel"),
            Identifier.parse("minecraft:glass"),
            Identifier.parse("minecraft:oak_leaves"),
            Identifier.parse("minecraft:coal_ore"),
            Identifier.parse("minecraft:diamond_ore"),
            Identifier.parse("minecraft:farmland"),
            Identifier.parse("minecraft:farmland_wet"),
            Identifier.parse("minecraft:bookshelf"),
            // The mineral vault's silk arms (Slice 32 — the OreBlock silk
            // gate is the default hasSilkTouchDrops, so lapis/emerald ore
            // take themselves; PackedIceBlock rides the same default with
            // its bare-hand count 0).
            Identifier.parse("minecraft:lapis_ore"),
            Identifier.parse("minecraft:emerald_ore"),
            Identifier.parse("minecraft:redstone_ore"),
            Identifier.parse("minecraft:packed_ice"));

    /** @return whether the block's silk touch drops its own item (the reference gate). */
    public static boolean isSilkTouchable(Identifier block) {
        return SILK_TOUCHABLE.contains(block);
    }

    private static final Map<Identifier, BlockBehavior> BEHAVIORS = buildBehaviors();

    private static Map<Identifier, BlockBehavior> buildBehaviors() {
        Map<Identifier, BlockBehavior> behaviors = new java.util.HashMap<>(Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:air"),
                    new BlockBehavior(0.0, false, false, null, 0, List.of())),

            Map.entry(Identifier.parse("minecraft:stone"),
                    new BlockBehavior(1.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone"), 1)))),

            Map.entry(Identifier.parse("minecraft:grass_block"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            Map.entry(Identifier.parse("minecraft:dirt"),
                    new BlockBehavior(0.5, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            Map.entry(Identifier.parse("minecraft:cobblestone"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone"), 1)))),

            Map.entry(Identifier.parse("minecraft:oak_planks"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_planks"), 1)))),

            // Community data (pc/1.8 blocks.json): wool hardness 0.8, material
            // wool (shears accelerate), no tool gate, drops itself.
            Map.entry(Identifier.parse("minecraft:wool"),
                    new BlockBehavior(0.8, true, false, "wool", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wool"), 1)))),

            Map.entry(Identifier.parse("minecraft:oak_log"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_log"), 1)))),

            // Community data (pc/1.8 blocks.json): crafting_table hardness 2.5,
            // material wood, no harvest requirement, drops itself; torch is
            // instant-break (hardness 0) and drops itself.
            Map.entry(Identifier.parse("minecraft:crafting_table"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:crafting_table"), 1)))),

            Map.entry(Identifier.parse("minecraft:torch"),
                    new BlockBehavior(0.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:torch"), 1)))),

            // Community data (pc/1.8 blocks.json): furnace hardness 3.5, material
            // rock, pickaxe required (harvestTools: all pickaxe tiers), drops
            // itself; chest hardness 2.5, material wood, no tool requirement,
            // drops itself.
            Map.entry(Identifier.parse("minecraft:furnace"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),

            // Community data (pc/1.8 blocks.json): noteblock hardness 0.8
            // (the reference's own setStrength(0.8F) registration), material
            // wood, no harvest requirement, drops itself.
            Map.entry(Identifier.parse("minecraft:noteblock"),
                    new BlockBehavior(0.8, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:noteblock"), 1)))),

            // The jukebox (Slice 27b — the reference's line-1181
            // registration: setStrength(2.0F), setBlastResistance(10.0F)):
            // stone material, no harvest requirement, drops itself; the
            // HAS_RECORD variant mirrors it (the record ejects through the
            // break hook, not the drop table — the reference's own
            // onRemoved walk).
            Map.entry(Identifier.parse("minecraft:jukebox"),
                    new BlockBehavior(2.0, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:jukebox"), 1)))),
            Map.entry(Identifier.parse("minecraft:jukebox_record"),
                    new BlockBehavior(2.0, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:jukebox"), 1)))),

            Map.entry(Identifier.parse("minecraft:chest"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:chest"), 1)))),

            // The ender chest family (Slice 29a — Block.java lines 1281-1282:
            // setStrength(22.5F), setBlastResistance(1000.0F), STONE sounds):
            // obsidian-tier pickaxe harvest (the dataset's harvestTools:
            // diamond), the foreign-drop walk yields eight obsidian
            // (EnderChestBlock's own getDropItem + getBaseDropCount 8), and
            // silk takes the chest itself (hasSilkTouchDrops true). The
            // facing variants mirror the base row.
            Map.entry(Identifier.parse("minecraft:ender_chest_north"),
                    new BlockBehavior(22.5, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 8)))),
            Map.entry(Identifier.parse("minecraft:ender_chest_south"),
                    new BlockBehavior(22.5, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 8)))),
            Map.entry(Identifier.parse("minecraft:ender_chest_west"),
                    new BlockBehavior(22.5, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 8)))),
            Map.entry(Identifier.parse("minecraft:ender_chest_east"),
                    new BlockBehavior(22.5, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 8)))),

            // Obsidian itself (Slice 29a — Block.java line 1103's
            // setStrength(50.0F).setBlastResistance(2000.0F)): the ender
            // chest's drop yields it as an item, so the mining row lands with
            // the family (the dataset's harvestTools: diamond tier, drops
            // itself).
            Map.entry(Identifier.parse("minecraft:obsidian"),
                    new BlockBehavior(50.0, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 1)))),

            // The beacon (Slice 29b — BeaconBlock's own setStrength(3.0F),
            // the GLASS material): no tool gate, drops itself.
            Map.entry(Identifier.parse("minecraft:beacon"),
                    new BlockBehavior(3.0, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:beacon"), 1)))),

            // The beacon pyramid's minerals (Slice 29b — the dataset's own
            // rows: diamond block hardness 5, emerald block 5, both
            // pickaxe-gated iron tier (3), dropping themselves).
            Map.entry(Identifier.parse("minecraft:diamond_block"),
                    new BlockBehavior(5.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:diamond_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:emerald_block"),
                    new BlockBehavior(5.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:emerald_block"), 1)))),

            // The mineral vault family (Slice 32 — the reference's own
            // rows). Harvest gates are PickaxeItem.canMineBlock's: lapis
            // ore/block tier >= 1 (the engine's band 2 — stone pickaxe),
            // emerald ore >= 2 (iron, band 3); the plain stone-material
            // blocks take any pickaxe (band 1). Clay is the shovel family
            // (dirt band, ClayBlock's 4 clay balls); hay and slime need no
            // tool at all.
            Map.entry(Identifier.parse("minecraft:lapis_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 2,
                            // The count is the DropService's family arm:
                            // OreBlock.getBaseDropCount's 4 + nextInt(5)
                            // (the fixed 4 here only rides non-family
                            // callers that read the table raw).
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:lapis"), 4)))),
            Map.entry(Identifier.parse("minecraft:emerald_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:emerald"), 1)))),
            Map.entry(Identifier.parse("minecraft:lapis_block"),
                    new BlockBehavior(3.0, true, true, "rock", 2,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:lapis_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:redstone_block"),
                    new BlockBehavior(5.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:coal_block"),
                    new BlockBehavior(5.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:coal_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:quartz_block"),
                    new BlockBehavior(0.8, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:quartz_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:brick_block"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:brick_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:clay"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:clay_ball"), 4)))),
            Map.entry(Identifier.parse("minecraft:hay_block"),
                    new BlockBehavior(0.5, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:hay_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:slime_block"),
                    new BlockBehavior(0.0, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:slime_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:end_stone"),
                    new BlockBehavior(3.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:end_stone"), 1)))),
            // PackedIceBlock.getBaseDropCount 0: the bare pickaxe gets
            // nothing; silk takes the block itself (the silk set below).
            Map.entry(Identifier.parse("minecraft:packed_ice"),
                    new BlockBehavior(0.5, true, true, "rock", 1, List.of())),

            // The redstone power family (Slice 33): the lamp pair — one
            // item (the dark form — RedstoneLampBlock.getDropItem lines
            // 62-65 returns the DARK lamp for BOTH states), the glass
            // sounds' 0.3 hardness, no tool gate. The lit ore shares its
            // plain twin's row exactly (same dust drops, same gates — the
            // drop arm reads the family walk either way).
            Map.entry(Identifier.parse("minecraft:redstone_lamp"),
                    new BlockBehavior(0.3, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_lamp"), 1)))),
            Map.entry(Identifier.parse("minecraft:lit_redstone_lamp"),
                    new BlockBehavior(0.3, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_lamp"), 1)))),
            Map.entry(Identifier.parse("minecraft:lit_redstone_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone"), 4)))),

            // Community data (pc/1.8 blocks.json): sand hardness 0.5, material
            // dirt, no tool requirement, drops itself (metadata 0: normal sand;
            // red sand is variant 1, /give reachable). Glass hardness 0.3, no
            // material, drops nothing — the historical shatter (only silk touch
            // returned glass, which is a later slice).
            Map.entry(Identifier.parse("minecraft:sand"),
                    new BlockBehavior(0.5, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sand"), 1)))),

            Map.entry(Identifier.parse("minecraft:glass"),
                    new BlockBehavior(0.3, true, false, null, 0, List.of())),

            // The TNT block (Slice 9f, community blocks.json: hardness 0,
            // material tnt): drops the item when broken by hand.
            Map.entry(Identifier.parse("minecraft:tnt"),
                    new BlockBehavior(0.0, true, false, "tnt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:tnt"), 1)))),

            // Community data (pc/1.8 blocks.json): gravel 0.6, material dirt,
            // no tool requirement. Drops model the historical quantityDropped
            // override: 10% flint, otherwise gravel (first-hit-wins rolls).
            Map.entry(Identifier.parse("minecraft:gravel"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:flint"), 1, 0.1),
                                    new BlockBehavior.Drop(Identifier.parse("minecraft:gravel"), 1, 1.0)))),

            Map.entry(Identifier.parse("minecraft:coal_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:coal"), 1)))),

            Map.entry(Identifier.parse("minecraft:iron_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 2,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_ore"), 1)))),

            Map.entry(Identifier.parse("minecraft:diamond_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:diamond"), 1)))),

            // The full-world ladder (community blocks.json): gold and redstone
            // share iron's pickaxe gate; the oak trunk is wood (axe, drops
            // itself); the canopy shatters like glass (silk touches are later).
            Map.entry(Identifier.parse("minecraft:gold_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:gold_ore"), 1)))),
            Map.entry(Identifier.parse("minecraft:redstone_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            // The 1:1 repair (Slice 32 — RedstoneOreBlock
                            // lines 66-77): the drop is the DUST, base
                            // 4 + nextInt(2), fortune ADDS nextInt(f+1)
                            // — the DropService's family arm owns the
                            // walk; the fixed 4 here is the raw-table
                            // fallback only.
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone"), 4)))),
            Map.entry(Identifier.parse("minecraft:oak_leaves"),
                    new BlockBehavior(0.2, true, false, "leaves", 1,
                            List.of())),

            // Decoration flora (community blocks.json): all instant-break
            // (hardness 0), no tool, plants material. 1.8 drop model: the
            // flowers drop themselves; tall grass and the dead bush drop
            // nothing by hand (shears drops are a later slice; seeds/farming
            // do not exist yet).
            // The farming slice (community blocks.json): farmland hardness
            // 0.6, dirt material, drops dirt (the historical BlockFarmland).
            // The wheat crop (block 59) is instant-break; the mature stage
            // yields the wheat plus a seed (the historical 0-3 roll folded to
            // one, the Drop model's fixed-count shape), younger stages drop
            // only the seed.
            Map.entry(Identifier.parse("minecraft:farmland"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            Map.entry(Identifier.parse("minecraft:farmland_wet"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage0"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage1"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage2"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage3"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage4"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage5"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage6"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage7"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat"), 1),
                                    new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            // The village slice's crop families (community blocks.json:
            // carrots 141 / potatoes 142, instant-break plants like the
            // wheat; the mature stage yields the crop item — the reference's
            // 0-4 quantity roll folded to the Drop model's fixed one).
            Map.entry(Identifier.parse("minecraft:carrots_stage0"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage1"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage2"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage3"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage4"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage5"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage6"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage7"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 2)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage0"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage1"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage2"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage3"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage4"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage5"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage6"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage7"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 2)))),

            // The village's thin solids and stone pieces (community
            // blocks.json): the glass pane 102 shatters like glass (0.3,
            // no drops), the iron bars 101 hardness 5 drop themselves,
            // the double stone slab 43 hardness 2, the smooth sandstone
            // skin and the sandstone stairs pair the sandstone's own 0.8.
            Map.entry(Identifier.parse("minecraft:glass_pane"),
                    new BlockBehavior(0.3, true, false, null, 0, List.of())),
            Map.entry(Identifier.parse("minecraft:iron_bars"),
                    new BlockBehavior(5.0, true, false, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_bars"), 1)))),
            Map.entry(Identifier.parse("minecraft:double_stone_slab"),
                    new BlockBehavior(2.0, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:double_stone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:smooth_sandstone"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:smooth_sandstone"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_east"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_east"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_west"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_west"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_south"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_south"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_north"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_north"), 1)))),

            // The stronghold set (Slice 12c — community blocks.json 1.8
            // values): the stone-brick family 1.5 rock pickaxe-gated
            // (harvest 0) dropping itself — the mossy/cracked variants
            // drop the PLAIN brick (the reference's getStateFromMeta 0 —
            // Item.getItemFromBlock over the default state); the spawner
            // 5.0 rock pickaxe-gated dropping nothing (the reference's
            // null drop item); the smooth brick slab 2.0
            // rock like the stone slab; the iron door 5.0 rock
            // pickaxe-gated dropping itself; the end portal frames -1
            // undiggable dropless (the reference's unbreakable
            // portal-frame rule). The infested family (Slice 18c —
            // InfestedBlock's own rows): setStrength(0.0F) — the egg
            // breaks INSTANTLY by hand (the clay material, no tool gate),
            // drops NOTHING (getBaseDropCount 0) — the silk arm's host
            // drop walks the DropService's own infested arm, and the
            // silverfish burst rides the break path's spawn sink (never
            // a drop row).
            Map.entry(Identifier.parse("minecraft:stone_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:mossy_stone_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:cracked_stone_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:chiseled_stone_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:infested_stone"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_cobblestone"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_stone_bricks"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_mossy_stone_bricks"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_cracked_stone_bricks"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_chiseled_stone_bricks"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_brick_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:mob_spawner"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of())),
            Map.entry(Identifier.parse("minecraft:iron_door_west"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_door_north"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_door_east"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_door_south"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_door_upper"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_south"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_west"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_north"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_east"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_south"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_west"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_north"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_east"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),

            // The fortress set (Slice 12d — community blocks.json 1.8
            // values): the nether-brick family 2.0 rock pickaxe-gated
            // (harvest 0) dropping itself; the wart a plant (hardness 0,
            // the reference's drop rule: 1 below age 3, 2-4 at maturity —
            // the random band ledgers with the farming slice, the table
            // carries the floor).
            Map.entry(Identifier.parse("minecraft:nether_bricks"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_fence"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_fence"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_east"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage0"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_wart"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage1"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_wart"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage2"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_wart"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage3"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_wart"), 2)))),

            // The monument set (Slice 12e — community blocks.json 1.8
            // values): the prismarine trio 1.5 rock pickaxe-gated dropping
            // the prismarine item (the variant's damage nuance ledgers with
            // the item-damage slice), the sea lantern 0.3 dropping its
            // crystals (the 2-3 band floors at 2, the silk arm ledgers),
            // the sponge pair 0.6 dropping the dry sponge, the gold block
            // 3.0 rock pickaxe-gated dropping itself.
            Map.entry(Identifier.parse("minecraft:prismarine_rough"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:prismarine"), 1)))),
            Map.entry(Identifier.parse("minecraft:prismarine_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:prismarine"), 1)))),
            Map.entry(Identifier.parse("minecraft:prismarine_dark"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:prismarine"), 1)))),
            Map.entry(Identifier.parse("minecraft:sea_lantern"),
                    new BlockBehavior(0.3, true, false, "glass", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:prismarine_crystals"), 2)))),
            Map.entry(Identifier.parse("minecraft:sponge"),
                    new BlockBehavior(0.6, true, false, "sponge", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sponge"), 1)))),
            Map.entry(Identifier.parse("minecraft:wet_sponge"),
                    new BlockBehavior(0.6, true, false, "sponge", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sponge"), 1)))),
            Map.entry(Identifier.parse("minecraft:gold_block"),
                    new BlockBehavior(3.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:gold_block"), 1)))),

            // The temple set (Slice 12f — community blocks.json 1.8
            // values): the chiseled sandstone drops the plain sandstone
            // (the reference's Item.getItemFromBlock over the default
            // state), the clay pair drops itself, the mossy cobblestone
            // 2.0 rock pickaxe-gated dropping itself, the vine a plant.
            Map.entry(Identifier.parse("minecraft:chiseled_sandstone"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone"), 1)))),
            Map.entry(Identifier.parse("minecraft:stained_clay_orange"),
                    new BlockBehavior(1.25, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stained_clay_orange"), 1)))),
            Map.entry(Identifier.parse("minecraft:stained_clay_yellow"),
                    new BlockBehavior(1.25, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stained_clay_yellow"), 1)))),
            Map.entry(Identifier.parse("minecraft:mossy_cobblestone"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:mossy_cobblestone"), 1)))),
            Map.entry(Identifier.parse("minecraft:vine"),
                    new BlockBehavior(0.2, true, false, "plants", 0, List.of())),

            // The golem family's blocks (Slice 17d — community blocks.json
            // 1.8 values): the snow block drops the 4 snowballs
            // (SnowBlock.getDropItem + quantityDropped 4), the snow layer
            // drops nothing at all (SnowLayerBlock.getDropItem's null arm
            // without silk touch), the iron block is a stone-tier pickaxe
            // rock dropping itself, and the pumpkin pair are woody
            // self-droppers (the historical 1.0 axe family).
            Map.entry(Identifier.parse("minecraft:snow"),
                    new BlockBehavior(0.2, true, false, "snow", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:snowball"), 4)))),
            Map.entry(Identifier.parse("minecraft:snow_layer"),
                    new BlockBehavior(0.1, true, false, "snow", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:iron_block"),
                    new BlockBehavior(5.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:pumpkin"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:pumpkin"), 1)))),
            Map.entry(Identifier.parse("minecraft:lit_pumpkin"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:lit_pumpkin"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_west"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_north"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_east"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_head"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_head_west"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_head_north"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_head_east"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            // The standing sign (community blocks.json: hardness 1.0, wood
            // material, drops itself — the sign item, legacy 323).
            Map.entry(Identifier.parse("minecraft:sign"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),

            Map.entry(Identifier.parse("minecraft:sign_west"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),

            Map.entry(Identifier.parse("minecraft:sign_north"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),

            Map.entry(Identifier.parse("minecraft:sign_east"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),

            // The wall signs (block 68): the hanging model, same wood feel,
            // the same sign item drop (legacy 323).
            Map.entry(Identifier.parse("minecraft:wall_sign"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),
            Map.entry(Identifier.parse("minecraft:wall_sign_south"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),
            Map.entry(Identifier.parse("minecraft:wall_sign_west"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),
            Map.entry(Identifier.parse("minecraft:wall_sign_east"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),


            Map.entry(Identifier.parse("minecraft:oak_door"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_north"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_east"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_south"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_open"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_open_north"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_open_east"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_open_south"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_upper"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_upper_open"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            // The ladders (community blocks.json: hardness 0.4, drops itself)
            Map.entry(Identifier.parse("minecraft:ladder"),
                    new BlockBehavior(0.4, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:ladder"), 1)))),

            // The redstone family (community blocks.json: hardness 0, the
            // wire drops the dust, the torches drop the torch, the repeater
            // drops itself — every power/facing/delay variant shares the row).
            Map.entry(Identifier.parse("minecraft:redstone_wire"),
                    new BlockBehavior(0.0, false, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone"), 1)))),

            Map.entry(Identifier.parse("minecraft:ladder_south"),
                    new BlockBehavior(0.4, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:ladder"), 1)))),

            Map.entry(Identifier.parse("minecraft:ladder_west"),
                    new BlockBehavior(0.4, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:ladder"), 1)))),

            Map.entry(Identifier.parse("minecraft:ladder_east"),
                    new BlockBehavior(0.4, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:ladder"), 1)))),

            // The oak fence (hardness 2.0, wood, drops itself)
            Map.entry(Identifier.parse("minecraft:oak_fence"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_fence"), 1)))),

                        Map.entry(Identifier.parse("minecraft:tall_grass"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            // the historical seed roll (1/8) first, the
                            // flora slice's self-drop otherwise
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1, 0.125),
                                    new BlockBehavior.Drop(Identifier.parse("minecraft:tall_grass"), 1, 1.0)))),
            Map.entry(Identifier.parse("minecraft:dead_bush"),
                    new BlockBehavior(0.0, true, false, "plants", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:dandelion"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dandelion"), 1)))),
            Map.entry(Identifier.parse("minecraft:poppy"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:poppy"), 1)))),

            // Community data (pc/1.8 blocks.json): sandstone hardness 0.8,
            // material rock, pickaxe required for drops, drops itself.
            Map.entry(Identifier.parse("minecraft:sandstone"),
                    new BlockBehavior(0.8, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone"), 1)))),

            // The nether's materials (community blocks.json 1.8 values):
            // netherrack 0.4 rock pickaxe-gated (harvest level 0 — the wooden
            // pick suffices), drops itself; soul sand 0.5 sand-material
            // shovel-block, drops itself; quartz ore 3.0 rock pickaxe-gated
            // (harvest 0) dropping the quartz item; glowstone 0.3
            // glass-material instant-diggable dropping the reference
            // GlowstoneBlock's quantityDropped band (2 + nextInt(3)) through
            // the table's first-hit-wins chance model (1/3 each of 4, 3, 2
            // — the range roll restated in the table's vocabulary).
            Map.entry(Identifier.parse("minecraft:netherrack"),
                    new BlockBehavior(0.4, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:netherrack"), 1)))),
            Map.entry(Identifier.parse("minecraft:soul_sand"),
                    new BlockBehavior(0.5, true, false, "sand", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:soul_sand"), 1)))),
            Map.entry(Identifier.parse("minecraft:quartz_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:quartz"), 1)))),
            Map.entry(Identifier.parse("minecraft:glowstone"),
                    new BlockBehavior(0.3, true, false, "glass", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:glowstone_dust"), 4, 1.0 / 3.0),
                                    new BlockBehavior.Drop(Identifier.parse("minecraft:glowstone_dust"), 3, 1.0 / 3.0),
                                    new BlockBehavior.Drop(Identifier.parse("minecraft:glowstone_dust"), 2, 1.0 / 3.0)))),

            // The mushrooms (community blocks.json): instant-break plants
            // dropping themselves (the PlantFeature pair the nether grows on
            // its netherrack floors).
            Map.entry(Identifier.parse("minecraft:brown_mushroom"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:brown_mushroom"), 1)))),
            Map.entry(Identifier.parse("minecraft:red_mushroom"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:red_mushroom"), 1)))),

            // Community data (pc/1.8 blocks.json): furnace_lit mirrors the
            // furnace (3.5, rock, pickaxe 1, drops the furnace item); the
            // reed and the cactus are instant-break plants that drop
            // themselves (the cactus's slight inset is the collision slice's
            // concern).
            Map.entry(Identifier.parse("minecraft:furnace_lit"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),

            // The collision-shape slice (community blocks.json: slabs and
            // stairs hardness 2.0; the stone families gate drops on a
            // pickaxe like their parent cubes, the wooden families drop by
            // hand; every variant drops its own item form).
            Map.entry(Identifier.parse("minecraft:oak_slab"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_slab_top"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:stone_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:stone_slab_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_slab_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_east"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_west"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_south"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_north"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_east"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),

            // Community data (pc/1.8 blocks.json): rail hardness 0.7,
            // instant-ish, drops itself (any pick breaks it by hand speed).
            Map.entry(Identifier.parse("minecraft:rail"),
                    new BlockBehavior(0.7, true, false, "stone", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:rail"), 1)))),
            Map.entry(Identifier.parse("minecraft:rail_ew"),
                    new BlockBehavior(0.7, true, false, "stone", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:rail"), 1)))),
            Map.entry(Identifier.parse("minecraft:sugar_cane"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sugar_cane"), 1)))),
            Map.entry(Identifier.parse("minecraft:cactus"),
                    new BlockBehavior(0.4, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cactus"), 1)))),

            // Community data (pc/1.8 blocks.json): fire hardness 0 (instant
            // break), no material, drops nothing — the historical extinguish.
            Map.entry(Identifier.parse("minecraft:fire"),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),

            Map.entry(Identifier.parse("minecraft:bedrock"),
                    BlockBehavior.unbreakable())));

        // The redstone family's variants (Slice 9a): every power level of
        // the wire, both torch pairs and all the repeater states share the
        // family row — the drops are the item, the break is instant by hand.
        BlockBehavior wire = behaviors.get(Identifier.parse("minecraft:redstone_wire"));
        for (int power = 1; power <= 15; power++) {
            behaviors.put(Identifier.parse("minecraft:redstone_wire_" + power), wire);
        }
        BlockBehavior torch = new BlockBehavior(0.0, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_torch"), 1)));
        for (String prefix : new String[]{"redstone_torch", "unlit_redstone_torch"}) {
            behaviors.put(Identifier.parse("minecraft:" + prefix), torch);
            for (String wall : new String[]{"east", "west", "south", "north"}) {
                behaviors.put(Identifier.parse("minecraft:" + prefix + "_" + wall), torch);
            }
        }
        BlockBehavior repeater = new BlockBehavior(0.0, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:repeater"), 1)));
        for (String prefix : new String[]{"repeater", "powered_repeater"}) {
            for (String facing : new String[]{"south", "west", "north", "east"}) {
                for (int delay = 1; delay <= 4; delay++) {
                    behaviors.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_" + delay),
                            repeater);
                }
            }
        }
        // The comparator (Slice 9d, community blocks.json: hardness 0, the
        // break is instant by hand): every facing/mode/powered variant
        // drops the comparator item.
        BlockBehavior comparator = new BlockBehavior(0.0, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:comparator"), 1)));
        for (String prefix : new String[]{"comparator", "powered_comparator"}) {
            for (String facing : new String[]{"south", "west", "north", "east"}) {
                for (String mode : new String[]{"compare", "subtract"}) {
                    behaviors.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_" + mode),
                            comparator);
                }
            }
        }
        // The piston family (Slice 9e, community blocks.json: the base 0.5):
        // every base variant drops its item; the head drops nothing (the
        // reference's zero drop count — the base carries the item); the
        // moving carrier is unbreakable (MovingBlock's strength -1).
        String[] directions = {"down", "up", "north", "south", "west", "east"};
        BlockBehavior piston = new BlockBehavior(0.5, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:piston"), 1)));
        BlockBehavior stickyPiston = new BlockBehavior(0.5, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sticky_piston"), 1)));
        for (int facing = 0; facing < 6; facing++) {
            for (int extended = 0; extended <= 1; extended++) {
                String suffix = directions[facing] + (extended == 1 ? "_extended" : "");
                behaviors.put(Identifier.parse("minecraft:piston_" + suffix), piston);
                behaviors.put(Identifier.parse("minecraft:sticky_piston_" + suffix), stickyPiston);
            }
        }
        BlockBehavior pistonHead = new BlockBehavior(0.5, true, false, "rock", 0, List.of());
        for (int facing = 0; facing < 6; facing++) {
            behaviors.put(Identifier.parse("minecraft:piston_head_" + directions[facing]), pistonHead);
            behaviors.put(Identifier.parse("minecraft:piston_head_" + directions[facing] + "_sticky"),
                    pistonHead);
        }
        BlockBehavior movingPiston = new BlockBehavior(-1.0, false, false, "rock", 0, List.of());
        for (int facing = 0; facing < 6; facing++) {
            behaviors.put(Identifier.parse("minecraft:moving_piston_" + directions[facing]),
                    movingPiston);
            behaviors.put(Identifier.parse("minecraft:moving_piston_" + directions[facing] + "_sticky"),
                    movingPiston);
        }
        // The hopper (Slice 10b, community blocks.json: hardness 3.0): every
        // facing/enabled variant drops the hopper item.
        BlockBehavior hopper = new BlockBehavior(3.0, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:hopper"), 1)));
        for (int facing : new int[]{0, 2, 3, 4, 5}) { // no UP — the FACING predicate
            behaviors.put(Identifier.parse("minecraft:hopper_" + directions[facing]), hopper);
            behaviors.put(Identifier.parse("minecraft:hopper_" + directions[facing] + "_disabled"),
                    hopper);
        }
        // The daylight detector (Slice 27c — the reference's own
        // registration: setStrength(0.2F), WOOD sounds, Material.WOOD, a
        // 0.375-high non-cube): all thirty-two power states of both forms
        // drop the NORMAL detector item (getDropItem lines 88-90 — the
        // inverted form never drops itself).
        BlockBehavior daylightDetector = new BlockBehavior(0.2, true, false, "wood", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:daylight_detector"), 1)));
        for (int power = 0; power <= 15; power++) {
            behaviors.put(Identifier.parse("minecraft:daylight_detector_p" + power),
                    daylightDetector);
            behaviors.put(Identifier.parse("minecraft:daylight_detector_inverted_p" + power),
                    daylightDetector);
        }
        // The dispenser and the dropper (Slice 11a, Block.java line 1041's
        // strength 3.5): every facing/triggered variant drops its item.
        BlockBehavior dispenser = new BlockBehavior(3.5, true, false, "stone", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dispenser"), 1)));
        BlockBehavior dropper = new BlockBehavior(3.5, true, false, "stone", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dropper"), 1)));
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            behaviors.put(Identifier.parse("minecraft:dispenser_" + facing), dispenser);
            behaviors.put(Identifier.parse("minecraft:dispenser_" + facing + "_triggered"),
                    dispenser);
            behaviors.put(Identifier.parse("minecraft:dropper_" + facing), dropper);
            behaviors.put(Identifier.parse("minecraft:dropper_" + facing + "_triggered"),
                    dropper);
        }
        // The brewing stand (Slice 11e — Block.java line 1241's strength 0.5):
        // every bottle-bit variant drops the stand item (getDropItem lines
        // 120-123); instant-ish by hand, no tool gate.
        BlockBehavior brewingStand = new BlockBehavior(0.5, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:brewing_stand"), 1)));
        for (int bits = 0; bits < 8; bits++) {
            StringBuilder name = new StringBuilder("minecraft:brewing_stand");
            for (int slot = 0; slot < 3; slot++) {
                if ((bits & (1 << slot)) != 0) {
                    name.append("_bottle").append(slot);
                }
            }
            behaviors.put(Identifier.parse(name.toString()), brewingStand);
        }
        // The cauldron family (Block.java line 1242's strength 2.0): every
        // level state drops the cauldron item (getDropItem lines 178-180);
        // pickaxe-shaped by hand, no tool gate.
        BlockBehavior cauldron = new BlockBehavior(2.0, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cauldron"), 1)));
        behaviors.put(Identifier.parse("minecraft:cauldron"), cauldron);
        for (int level = 1; level <= 3; level++) {
            behaviors.put(Identifier.parse("minecraft:cauldron_level" + level), cauldron);
        }
        // The tripwire (Slice 10c, community blocks.json: hardness 0, instant
        // by hand): the wire drops string unless disarmed (the shears arm);
        // the hook drops the hook item.
        List<BlockBehavior.Drop> stringDrop =
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:string"), 1));
        List<BlockBehavior.Drop> hookDrop =
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:tripwire_hook"), 1));
        for (int bits = 0; bits < 16; bits++) {
            StringBuilder name = new StringBuilder("minecraft:tripwire");
            if ((bits & 1) != 0) {
                name.append("_powered");
            }
            if ((bits & 2) != 0) {
                name.append("_suspended");
            }
            if ((bits & 4) != 0) {
                name.append("_attached");
            }
            if ((bits & 8) != 0) {
                name.append("_disarmed");
            }
            // A disarmed wire drops nothing (the shears already paid the
            // string out — the reference's DISARMED arm).
            behaviors.put(Identifier.parse(name.toString()),
                    new BlockBehavior(0.0, false, false, null, 0,
                            (bits & 8) != 0 ? List.of() : stringDrop));
        }
        for (String facing : new String[]{"south", "west", "north", "east"}) {
            for (int attached = 0; attached <= 1; attached++) {
                for (int powered = 0; powered <= 1; powered++) {
                    behaviors.put(Identifier.parse("minecraft:tripwire_hook_" + facing
                            + (attached == 1 ? "_attached" : "")
                            + (powered == 1 ? "_powered" : "")),
                            new BlockBehavior(0.0, false, false, null, 0, hookDrop));
                }
            }
        }
        // The player-driven sources (Slice 9c): the lever, the buttons and
        // the plates — every facing/powered variant drops its item, the
        // break is instant by hand (community blocks.json: hardness 0.5 for
        // the lever/buttons, 0.5 for the plates).
        BlockBehavior lever = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:lever"), 1)));
        for (String key : new String[]{"down_x", "east", "west", "south", "north", "up_z", "up_x", "down_z"}) {
            behaviors.put(Identifier.parse("minecraft:lever_" + key), lever);
            behaviors.put(Identifier.parse("minecraft:lever_" + key + "_powered"), lever);
        }
        BlockBehavior stoneButton = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_button"), 1)));
        BlockBehavior woodButton = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wooden_button"), 1)));
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            behaviors.put(Identifier.parse("minecraft:stone_button_" + facing), stoneButton);
            behaviors.put(Identifier.parse("minecraft:stone_button_" + facing + "_powered"), stoneButton);
            behaviors.put(Identifier.parse("minecraft:wooden_button_" + facing), woodButton);
            behaviors.put(Identifier.parse("minecraft:wooden_button_" + facing + "_powered"), woodButton);
        }
        BlockBehavior stonePlate = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_pressure_plate"), 1)));
        BlockBehavior woodPlate = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wooden_pressure_plate"), 1)));
        behaviors.put(Identifier.parse("minecraft:stone_pressure_plate"), stonePlate);
        behaviors.put(Identifier.parse("minecraft:stone_pressure_plate_powered"), stonePlate);
        behaviors.put(Identifier.parse("minecraft:wooden_pressure_plate"), woodPlate);
        behaviors.put(Identifier.parse("minecraft:wooden_pressure_plate_powered"), woodPlate);

        // The dye-coloring world families (Slice 31 — the reference's Block
        // registrations: wool 35 `setStrength(0.8F)` CLOTH, stained glass 95
        // `setStrength(0.3F)` (getBaseDropCount 0 — the silk-only family),
        // stained clay 159 `setStrength(1.25F)` STONE, the stained pane 160
        // `setStrength(0.3F)` (PaneBlock hasDrops false), the carpet 171
        // `setStrength(0.1F)` CLOTH `setOpacity(0)`, hardened clay 172
        // `setStrength(1.25F)` STONE). Every member drops its family's own
        // item with the damage field carrying the color (the reference's
        // getDropItemMetadata); the glass pair drops nothing by hand (silk
        // returns the family item — the DropService's family arm).
        for (int color = 0; color < 16; color++) {
            // wool (the white keeps the plain minecraft:wool row above)
            if (color != 0) {
                behaviors.put(DyeFamilyBlocks.blockOf("wool", color),
                        new BlockBehavior(0.8, true, false, "wool", 0,
                                List.of(new BlockBehavior.Drop(DyeFamilyBlocks.WOOL_ITEM, 1, color))));
            }
            // stained glass: silk-only (no hand rows), the null material
            // matching the plain glass/pane rows (no tool class)
            behaviors.put(DyeFamilyBlocks.stainedGlassBlock(color),
                    new BlockBehavior(0.3, true, false, null, 0, List.of()));
            // stained glass pane: silk-only (PaneBlock hasDrops false)
            behaviors.put(DyeFamilyBlocks.stainedGlassPaneBlock(color),
                    new BlockBehavior(0.3, true, false, null, 0, List.of()));
            // carpet: the 1/16-tall cover, no tool class (the reference's
            // own Material.CARPET — shears accelerate WOOL, never CARPET)
            behaviors.put(DyeFamilyBlocks.carpetBlock(color),
                    new BlockBehavior(0.1, true, false, "carpet", 0,
                            List.of(new BlockBehavior.Drop(DyeFamilyBlocks.CARPET_ITEM, 1, color))));
            // stained clay: the stone pickaxe family (any tier drops)
            if (color != 1 && color != 4) {
                behaviors.put(DyeFamilyBlocks.stainedClayBlock(color),
                        new BlockBehavior(1.25, true, true, "rock", 0,
                                List.of(new BlockBehavior.Drop(
                                        DyeFamilyBlocks.STAINED_HARDENED_CLAY_ITEM, 1, color))));
            }
        }
        // the temple pair joins the family's rows (the loop skips their
        // colors — the constants predate the family)
        behaviors.put(Identifier.parse("minecraft:stained_clay_orange"),
                new BlockBehavior(1.25, true, true, "rock", 0,
                        List.of(new BlockBehavior.Drop(
                                DyeFamilyBlocks.STAINED_HARDENED_CLAY_ITEM, 1, 1))));
        behaviors.put(Identifier.parse("minecraft:stained_clay_yellow"),
                new BlockBehavior(1.25, true, true, "rock", 0,
                        List.of(new BlockBehavior.Drop(
                                DyeFamilyBlocks.STAINED_HARDENED_CLAY_ITEM, 1, 4))));
        // hardened clay itself (172 — the parent of the stained family)
        behaviors.put(Identifier.parse("minecraft:hardened_clay"),
                new BlockBehavior(1.25, true, true, "rock", 0,
                        List.of(new BlockBehavior.Drop(
                                Identifier.parse("minecraft:hardened_clay"), 1))));

        // The wood-variant foundation (Slice 35 — the reference's own
        // registrations: planks `setStrength(2.0F)` WOOD, logs 2.0 WOOD,
        // leaves 0.2 LEAVES, saplings 0.0 PLANT): the planks and logs drop
        // the family item with the variant damage (Item.java's BlockItem
        // walk — the oak keeps its shipped plain-item rows), the leaves
        // roll the reference's chance band (the shears arm and the
        // sapling/apple walks ride the DropService family branch), and the
        // saplings drop themselves.
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            Identifier planks = WoodVariantBlocks.planksBlock(variant);
            Identifier log = WoodVariantBlocks.logBlock(variant);
            Identifier leaves = WoodVariantBlocks.leavesBlock(variant);
            Identifier sapling = WoodVariantBlocks.saplingBlock(variant);
            if (variant != 0) {
                behaviors.put(planks, new BlockBehavior(2.0, true, false, "wood", 0,
                        List.of(new BlockBehavior.Drop(WoodVariantBlocks.PLANKS_ITEM, 1, variant))));
                behaviors.put(log, new BlockBehavior(2.0, true, false, "wood", 0,
                        List.of(new BlockBehavior.Drop(
                                variant < 4 ? WoodVariantBlocks.LOG_ITEM : WoodVariantBlocks.LOG2_ITEM,
                                1, WoodVariantBlocks.wireMetaOf("log", variant)))));
                behaviors.put(leaves, new BlockBehavior(0.2, true, false, "leaves", 0,
                        List.of())); // the shears/sapling/apple walks ride the DropService branch
            }
            behaviors.put(sapling, new BlockBehavior(0.0, true, false, "plant", 0,
                    List.of(new BlockBehavior.Drop(WoodVariantBlocks.SAPLING_ITEM, 1, variant))));
        }

        // The building-shape families (Slice 36 — the reference's own
        // registrations: every wood stair `setStrength(2.0F)` WOOD, the
        // brick stair 2.0 ROCK, the stone brick 1.5, the quartz 0.8, the
        // fences 2.0 WOOD, the wooden slabs 2.0 WOOD): every state drops
        // its family's own item (the stairs over the bare ids, the slabs
        // over the family item with the variant damage).
        for (String family : WoodShapeBlocks.STAIRS.keySet()) {
            boolean wood = !family.equals("brick") && !family.equals("stone_brick")
                    && !family.equals("quartz");
            double strength = switch (family) {
                case "brick" -> 2.0;
                case "stone_brick" -> 1.5;
                case "quartz" -> 0.8;
                default -> 2.0;
            };
            Identifier item = Identifier.parse("minecraft:" + family + "_stairs");
            BlockBehavior stairs = new BlockBehavior(strength, true, !wood,
                    wood ? "wood" : "rock", 0, List.of(new BlockBehavior.Drop(item, 1)));
            for (EngineBlockType state : WoodShapeBlocks.stairsStates(family)) {
                behaviors.put(state.identifier(), stairs);
            }
        }
        for (String wood : WoodShapeBlocks.FENCES.keySet()) {
            behaviors.put(Identifier.parse("minecraft:" + wood + "_fence"),
                    new BlockBehavior(2.0, true, false, "wood", 0, List.of(new BlockBehavior.Drop(
                            Identifier.parse("minecraft:" + wood + "_fence"), 1))));
        }
        for (int variant = 1; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            BlockBehavior slab = new BlockBehavior(2.0, true, false, "wood", 0,
                    List.of(new BlockBehavior.Drop(
                            Identifier.parse("minecraft:wooden_slab"), 1, variant)));
            behaviors.put(WoodShapeBlocks.slabStates(variant).get(0).identifier(), slab);
            behaviors.put(WoodShapeBlocks.slabStates(variant).get(1).identifier(), slab);
        }

        // The fence-gate families (Slice 37 — FenceGateBlock's own rows:
        // every gate `setStrength(2.0F).setBlastResistance(5.0F)` WOOD_SOUNDS
        // — the wood class, no tool gate): all sixteen states per family
        // drop the family item (the reference's single BlockItem).
        for (String family : FenceGateBlocks.GATES.keySet()) {
            BlockBehavior gate = new BlockBehavior(2.0, true, false, "wood", 0,
                    List.of(new BlockBehavior.Drop(FenceGateBlocks.gateItem(family), 1)));
            for (int facing = 0; facing < 4; facing++) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean powered : new boolean[] {false, true}) {
                        behaviors.put(FenceGateBlocks.state(family, facing, open, powered)
                                .identifier(), gate);
                    }
                }
            }
        }

        // The rail trio (Slice 38 — Block.java's own rows: every rail
        // `setStrength(0.7F)` METAL_SOUNDS over Material.DECORATION — no
        // tool gate, drops itself): all twelve states per family.
        for (String family : RailBlocks.RAILS.keySet()) {
            BlockBehavior rail = new BlockBehavior(0.7, true, false, "stone", 0,
                    List.of(new BlockBehavior.Drop(RailBlocks.railItem(family), 1)));
            for (int shape = 0; shape < 6; shape++) {
                for (boolean powered : new boolean[] {false, true}) {
                    behaviors.put(RailBlocks.state(family, shape, powered).identifier(), rail);
                }
            }
        }

        // The complete door families (Slice 34 — DoorBlock's own
        // registrations: every wood door `setStrength(3.0F)` WOOD_SOUNDS,
        // the iron door `setStrength(5.0F)` Material.IRON — the material's
        // own setRequiresTool gates the iron pair to pickaxe-class drops).
        // The reference's getDropItem returns the family item from EITHER
        // half (UPPER → null, but the orphaned lower's neighborChanged
        // dropItems covers the pair: the engine's broken-cell drop model
        // nets the same one item per door — the sibling cleanup removes
        // the survivor silently). The five new woods mirror oak's rows;
        // the iron's new open/hinge states mirror its shipped five.
        for (String family : WoodDoorFamily.FAMILIES) {
            boolean iron = family.equals("iron");
            BlockBehavior door = new BlockBehavior(iron ? 5.0 : 3.0,
                    true, iron, iron ? "rock" : "wood", 0,
                    List.of(new BlockBehavior.Drop(WoodDoorFamily.doorItem(family), 1)));
            for (int meta = 0; meta < 4; meta++) {
                behaviors.put(WoodDoorFamily.lower(family, meta, false).identifier(), door);
                behaviors.put(WoodDoorFamily.lower(family, meta, true).identifier(), door);
            }
            for (boolean right : new boolean[] {false, true}) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean powered : new boolean[] {false, true}) {
                        behaviors.put(WoodDoorFamily.upper(family, right, open, powered)
                                .identifier(), door);
                    }
                }
            }
        }
        return Map.copyOf(behaviors);
    }

    /** @return the behavior of a block type, or empty for unregistered identifiers. */
    public static java.util.Optional<BlockBehavior> of(Identifier blockIdentifier) {
        return java.util.Optional.ofNullable(BEHAVIORS.get(blockIdentifier));
    }

    /**
     * @return whether the given held item (empty hand = {@code null}) can harvest
     *         drops from the block. Blocks without a tool requirement are
     *         harvestable by hand; tool-gated blocks need a tool of the matching
     *         class whose material tier satisfies the block's harvest level
     *         (historical: breaking works regardless, it just yields nothing).
     */
    public static boolean canHarvest(BlockBehavior behavior, net.zaminmc.torch.item.ItemType held) {
        if (!behavior.requiresTool()) {
            return true;
        }
        return Tools.specOf(held)
                .map(spec -> spec.toolClass() == requiredToolClass(behavior)
                        && spec.material().harvestLevel() >= behavior.harvestLevel())
                .orElse(false);
    }

    /**
     * @return the mining speed multiplier the held item contributes to the
     *         block: the tool material's speed when its class matches the
     *         block's material, 1.0 otherwise (hand or mismatched tool).
     */
    public static double speedMultiplier(BlockBehavior behavior, net.zaminmc.torch.item.ItemType held) {
        return Tools.specOf(held)
                .filter(spec -> spec.toolClass() == requiredToolClass(behavior))
                .map(spec -> spec.material().speedMultiplier())
                .orElse(1.0);
    }

    /**
     * The dataset's material-to-tool-class mapping: pickaxes work "rock",
     * shovels work "dirt", axes work "wood", shears work leaves/wool/web.
     * Unmapped materials have no accelerating tool class.
     */
    private static ToolClass requiredToolClass(BlockBehavior behavior) {
        return switch (behavior.material() == null ? "" : behavior.material()) {
            case "rock" -> ToolClass.PICKAXE;
            case "dirt", "grass", "snow", "clay", "sand" -> ToolClass.SHOVEL;
            case "wood" -> ToolClass.AXE;
            case "leaves", "wool", "web", "plant" -> ToolClass.SHEARS;
            default -> null;
        };
    }
}
