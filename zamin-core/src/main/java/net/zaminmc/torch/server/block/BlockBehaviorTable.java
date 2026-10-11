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
            Identifier.parse("minecraft:bookshelf"));

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

            Map.entry(Identifier.parse("minecraft:chest"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:chest"), 1)))),

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
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_ore"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_leaves"),
                    new BlockBehavior(0.2, true, true, "leaves", 1,
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
