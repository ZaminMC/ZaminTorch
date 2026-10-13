package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;

import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Light behavior of registered blocks: how much light a block emits and how
 * much it filters (attenuates) passing light (§475 "lighting as world state",
 * §476 "first implement correctness").
 *
 * <p>Data source (community, per the reuse rule): values were generated from
 * PrismarineJS/minecraft-data, {@code data/pc/1.8/blocks.json} fields
 * {@code emitLight} and {@code filterLight} (MIT license,
 * https://github.com/PrismarineJS/minecraft-data, snapshot 2026-10), the same
 * dataset the survival behavior table embeds. Registered blocks only; the
 * table grows with the registry, never speculatively.</p>
 *
 * <p>Notable values from the dataset: torch {@code emitLight 14}; glass and
 * chest are fully transparent to light ({@code filterLight 0}) — light passes
 * through them unattenuated-by-opacity; every full opaque cube filters 15
 * (stone, grass, dirt, sand, gravel, ores, bedrock). The furnace is registered
 * with emission 0: the dataset's 13 belongs to the LIT furnace variant
 * (historical lit-furnace emission), which arrives with a lit block state of
 * its own — an unlit furnace glowing would be wrong.</p>
 *
 * <p>Semantics of the two numbers in the engine's model: {@code emission} is
 * the block-light level a cell holding this block radiates from itself;
 * {@code filter} is the attenuation a light ray entering the cell pays —
 * {@code max(1, filter)} per cell step for every propagation, so a filter-15
 * (opaque) cell never passes light and a filter-0 cell costs the standard 1.
 * A cell holding an opaque block stores no light, exactly like the historical
 * model.</p>
 */
public final class BlockLightTable {

    /** One block's light behavior: emitted level and filter in 0..15. */
    public record LightData(int emission, int filter) {
        public LightData {
            if (emission < 0 || emission > 15 || filter < 0 || filter > 15) {
                throw new IllegalArgumentException("Light values must be 0..15: emit="
                        + emission + " filter=" + filter);
            }
        }
    }

    private static final Map<Identifier, LightData> LIGHT = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:air"), new LightData(0, 0)),

            // Full opaque cubes: filter 15, no emission (dataset filterLight 15).
            Map.entry(Identifier.parse("minecraft:stone"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:grass_block"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:dirt"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:bedrock"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:coal_ore"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:iron_ore"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:diamond_ore"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:gold_ore"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:redstone_ore"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_log"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_leaves"), new LightData(1, 1)),

            // The fluids: water attenuates two per cell (dataset filterLight 2)
            // and emits nothing; lava emits the historical 15 (its glow).
            Map.entry(Identifier.parse("minecraft:water"), new LightData(0, 3)),
            Map.entry(Identifier.parse("minecraft:falling_water"), new LightData(0, 2)),
            Map.entry(Identifier.parse("minecraft:lava"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:falling_lava"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:sand"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:gravel"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_planks"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:wool"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:crafting_table"), new LightData(0, 15)),
            // The enchanting corner (the reference carries no light overrides:
            // the bookshelf is a full opaque cube, the table a 0.75-high
            // non-cube light passes through; neither emits).
            Map.entry(Identifier.parse("minecraft:bookshelf"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:enchanting_table"), new LightData(0, 0)),

            // The engine's light sources: torch emits 14, fire 15, the lit
            // furnace 13 (dataset emitLight values; the lit furnace's 13 is
            // the historical glow the unlit variant rightly lacks).
            Map.entry(Identifier.parse("minecraft:torch"), new LightData(14, 0)),
            Map.entry(Identifier.parse("minecraft:fire"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:furnace_lit"), new LightData(13, 15)),

            // The nether's materials (community blocks.json 1.8 values):
            // glowstone emits the historical 15; netherrack/soul sand/quartz
            // ore are full opaque cubes (filter 15); the mushrooms shade
            // nothing and emit nothing.
            Map.entry(Identifier.parse("minecraft:glowstone"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:netherrack"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:soul_sand"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:quartz_ore"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:brown_mushroom"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:red_mushroom"), new LightData(0, 0)),

            // Transparent to light, no emission (dataset filterLight 0): light
            // crosses glass, a chest's empty bounding shape and an unlit furnace
            // paying only the standard per-cell step.
            Map.entry(Identifier.parse("minecraft:glass"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:chest"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:furnace"), new LightData(0, 0)),

            // The brewing stand (Slice 11e): the reference's setLight(0.125F)
            // = filter 1 — the engine's max(1, filter) step absorbs it into
            // the standard per-cell cost, so the stand registers like the
            // chest (light passes paying the ordinary step).
            Map.entry(Identifier.parse("minecraft:brewing_stand"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:brewing_stand_bottle0"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:brewing_stand_bottle1"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:brewing_stand_bottle2"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:brewing_stand_bottle0_bottle1"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:brewing_stand_bottle0_bottle2"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:brewing_stand_bottle1_bottle2"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:brewing_stand_bottle0_bottle1_bottle2"), new LightData(0, 0)),

            // The flora passes light fully (dataset filterLight 0: the plants
            // shade nothing); sandstone is a full opaque cube (filter 15).
            Map.entry(Identifier.parse("minecraft:tall_grass"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sign"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sign_west"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sign_north"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sign_east"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wall_sign"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wall_sign_south"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wall_sign_west"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wall_sign_east"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:ladder"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:ladder_south"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:ladder_west"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:ladder_east"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:oak_door"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_north"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_east"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_open"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_open_north"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_open_east"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_open_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_upper"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_door_upper_open"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_fence"), new LightData(0, 15)),
            // The collision-shape slice: slabs and stairs are opaque partial
            // cubes — filter 15 like their parent materials (the light
            // engine's cell model: one value per cell, the half-block nuance
            // stays out of scope).
            Map.entry(Identifier.parse("minecraft:oak_slab"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_slab_top"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:stone_slab"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:stone_slab_top"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab_top"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:sandstone_slab"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:sandstone_slab_top"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_stairs_east"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_stairs_west"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_stairs_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:oak_stairs_north"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_east"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north"), new LightData(0, 15)),
            // The rails: walk-through track, light passes (the flora rule).
            Map.entry(Identifier.parse("minecraft:rail"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:rail_ew"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:bed"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:bed_west"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:bed_north"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:bed_east"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:bed_head"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:bed_head_west"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:bed_head_north"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:bed_head_east"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:farmland"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:farmland_wet"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:wheat_stage0"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wheat_stage1"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wheat_stage2"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wheat_stage3"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wheat_stage4"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wheat_stage5"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wheat_stage6"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:wheat_stage7"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:dead_bush"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:dandelion"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:poppy"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sandstone"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cactus"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sugar_cane"), new LightData(0, 0)),
            // The village slice (community blocks.json): the new crops
            // pass light like the wheat, the stone pieces filter 15 like
            // their families, the thin panes pass (the engine's
            // per-cell model keeps the glass's own 0-opacity rule).
            Map.entry(Identifier.parse("minecraft:carrots_stage0"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:carrots_stage1"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:carrots_stage2"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:carrots_stage3"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:carrots_stage4"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:carrots_stage5"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:carrots_stage6"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:carrots_stage7"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:potatoes_stage0"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:potatoes_stage1"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:potatoes_stage2"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:potatoes_stage3"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:potatoes_stage4"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:potatoes_stage5"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:potatoes_stage6"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:potatoes_stage7"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:glass_pane"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:iron_bars"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:double_stone_slab"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:smooth_sandstone"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_east"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_west"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_north"), new LightData(0, 15)),

            // The stronghold set (Slice 12c): the stone-brick family are
            // full opaque cubes (dataset filterLight 15), the infested
            // egg identical (the disguise), the spawner a cutout cage
            // (filter 0), the iron door pair opaque like the oak family,
            // the portal frames opaque (their cutout shape still filters
            // the dataset's 15), and the smooth brick slab's bottom-half
            // carries the dataset's full filter (the slab collision
            // refinement lives with the shape queries).
            Map.entry(Identifier.parse("minecraft:stone_bricks"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:mossy_stone_bricks"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cracked_stone_bricks"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:chiseled_stone_bricks"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:infested_stone_bricks"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:mob_spawner"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:iron_door_west"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:iron_door_north"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:iron_door_east"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:iron_door_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:iron_door_upper"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_west"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_north"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_east"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_west"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_north"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_east"), new LightData(0, 15)),

            // The fortress set (Slice 12d): the nether-brick family are
            // full opaque cubes (dataset filterLight 15), the fence's
            // thin shape passes light, and the wart is a plant (filter 0,
            // no emission).
            Map.entry(Identifier.parse("minecraft:nether_bricks"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:nether_brick_fence"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_east"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage0"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage1"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage2"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage3"), new LightData(0, 0)),

            // The monument set (Slice 12e): the prismarine trio are full
            // opaque cubes (dataset filterLight 15), the sea lantern the
            // glowing light source (emit 15, filter 0), the sponge pair
            // opaque, the gold block opaque.
            Map.entry(Identifier.parse("minecraft:prismarine_rough"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:prismarine_bricks"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:prismarine_dark"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:sea_lantern"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:sponge"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:wet_sponge"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:gold_block"), new LightData(0, 15)),
            // The jack o'lantern's famous 15 (Slice 17d — the reference's
            // own emission), the snow family and the plain pumpkin opaque.
            Map.entry(Identifier.parse("minecraft:lit_pumpkin"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:snow"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:snow_layer"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:iron_block"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:pumpkin"), new LightData(0, 15)),

            // The temple set (Slice 12f): the chiseled sandstone + the clay
            // pair + the mossy cobblestone are full opaque cubes; the vine's
            // thin shape passes light.
            Map.entry(Identifier.parse("minecraft:chiseled_sandstone"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:stained_clay_orange"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:stained_clay_yellow"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:mossy_cobblestone"), new LightData(0, 15)),
            // The six rows the total-function read of Slice 14b exposed:
            // cobblestone/obsidian/tnt are plain opaques; the web filters
            // almost nothing (opacity 1); the portal PAIR emits the famous
            // 11 (BlockNetherPortal's own lightLevel).
            Map.entry(Identifier.parse("minecraft:cobblestone"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:obsidian"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:tnt"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:cobweb"), new LightData(0, 1)),
            Map.entry(Identifier.parse("minecraft:nether_portal"), new LightData(11, 0)),
            Map.entry(Identifier.parse("minecraft:nether_portal_z"), new LightData(11, 0)),
            // The flowing fluid levels (Slice 14b's total-function read
            // exposed the family gap): the water levels pay the source's
            // opacity 3, the lava levels carry the source's emission 15
            // (the reference registers both forms with the same light
            // pair — setOpacity(3) / setLight(1.0F)).
            Map.entry(Identifier.parse("minecraft:flowing_water_1"), new LightData(0, 3)),
            Map.entry(Identifier.parse("minecraft:flowing_water_2"), new LightData(0, 3)),
            Map.entry(Identifier.parse("minecraft:flowing_water_3"), new LightData(0, 3)),
            Map.entry(Identifier.parse("minecraft:flowing_water_4"), new LightData(0, 3)),
            Map.entry(Identifier.parse("minecraft:flowing_water_5"), new LightData(0, 3)),
            Map.entry(Identifier.parse("minecraft:flowing_water_6"), new LightData(0, 3)),
            Map.entry(Identifier.parse("minecraft:flowing_water_7"), new LightData(0, 3)),
            Map.entry(Identifier.parse("minecraft:flowing_lava_1"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:flowing_lava_2"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:flowing_lava_3"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:flowing_lava_4"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:flowing_lava_5"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:flowing_lava_6"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:flowing_lava_7"), new LightData(15, 0)),
            // The comparator's whole directional/mode family (the
            // powered pair included): a non-cube that never emits
            // (the reference's comparator carries no setLight).
            Map.entry(Identifier.parse("minecraft:comparator_east_compare"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:comparator_east_subtract"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:comparator_south_compare"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:comparator_south_subtract"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:comparator_west_compare"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:comparator_west_subtract"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:comparator_north_compare"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:comparator_north_subtract"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:powered_comparator_east_compare"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:powered_comparator_east_subtract"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:powered_comparator_south_compare"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:powered_comparator_south_subtract"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:powered_comparator_west_compare"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:powered_comparator_west_subtract"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:powered_comparator_north_compare"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:powered_comparator_north_subtract"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:vine"), new LightData(0, 0)));

    /**
     * The redstone family's light rows (Slice 9a) and the mechanical
     * families' (the pistons 9e, the hopper 10b, the tripwire 10c, the
     * dispenser/dropper 11a — the late-registered blocks whose rows the
     * builder constructs in loops): the lit torches emit level 7 (the
     * dataset's {@code emitLight 7} for minecraft:redstone_torch — dimmer
     * than the plain torch's 14), the unlit pair, the wire at every power
     * and the repeaters emit nothing, and nothing in the redstone family
     * filters light (the thin shapes pass it); the opaque machines
     * (dispenser, dropper, piston bases and heads) filter 15; the thin
     * machines (hopper, moving carrier, tripwire) pass light.
     */
    private static final Map<Identifier, LightData> REDSTONE_LIGHT = buildRedstoneLight();

    private static Map<Identifier, LightData> buildRedstoneLight() {
        Map<Identifier, LightData> entries = new java.util.HashMap<>();
        LightData lit = new LightData(7, 0);
        for (String wall : new String[]{"", "_east", "_west", "_south", "_north"}) {
            entries.put(Identifier.parse("minecraft:redstone_torch" + wall), lit);
            entries.put(Identifier.parse("minecraft:unlit_redstone_torch" + wall), new LightData(0, 0));
        }
        for (int power = 0; power <= 15; power++) {
            entries.put(Identifier.parse("minecraft:redstone_wire"
                    + (power == 0 ? "" : "_" + power)), new LightData(0, 0));
        }
        for (String prefix : new String[]{"repeater", "powered_repeater"}) {
            for (String facing : new String[]{"south", "west", "north", "east"}) {
                for (int delay = 1; delay <= 4; delay++) {
                    entries.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_" + delay),
                            new LightData(0, 0));
                }
            }
        }
        // The player-driven sources (Slice 9c): nothing in the family emits
        // or filters light (the thin shapes pass it).
        for (String key : new String[]{"down_x", "east", "west", "south", "north", "up_z", "up_x", "down_z"}) {
            entries.put(Identifier.parse("minecraft:lever_" + key), new LightData(0, 0));
            entries.put(Identifier.parse("minecraft:lever_" + key + "_powered"), new LightData(0, 0));
        }
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            entries.put(Identifier.parse("minecraft:stone_button_" + facing), new LightData(0, 0));
            entries.put(Identifier.parse("minecraft:stone_button_" + facing + "_powered"), new LightData(0, 0));
            entries.put(Identifier.parse("minecraft:wooden_button_" + facing), new LightData(0, 0));
            entries.put(Identifier.parse("minecraft:wooden_button_" + facing + "_powered"), new LightData(0, 0));
        }
        entries.put(Identifier.parse("minecraft:stone_pressure_plate"), new LightData(0, 0));
        entries.put(Identifier.parse("minecraft:stone_pressure_plate_powered"), new LightData(0, 0));
        entries.put(Identifier.parse("minecraft:wooden_pressure_plate"), new LightData(0, 0));
        entries.put(Identifier.parse("minecraft:wooden_pressure_plate_powered"), new LightData(0, 0));

        // The mechanical families' rows (Slices 9e/10b/10c/11a — the gap the
        // §54 warning swallow hid): the dispenser, the dropper and the
        // piston bases/heads are full opaque cubes (filter 15); the hopper
        // (the funnel shape), the moving carrier and the tripwire wire/hook
        // pass light untouched.
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            entries.put(Identifier.parse("minecraft:dispenser_" + facing), new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:dispenser_" + facing + "_triggered"),
                    new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:dropper_" + facing), new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:dropper_" + facing + "_triggered"),
                    new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:piston_" + facing), new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:piston_" + facing + "_extended"),
                    new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:sticky_piston_" + facing), new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:sticky_piston_" + facing + "_extended"),
                    new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:piston_head_" + facing), new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:piston_head_" + facing + "_sticky"),
                    new LightData(0, 15));
            entries.put(Identifier.parse("minecraft:moving_piston_" + facing), new LightData(0, 0));
            entries.put(Identifier.parse("minecraft:moving_piston_" + facing + "_sticky"),
                    new LightData(0, 0));
        }
        for (String facing : new String[]{"down", "north", "south", "west", "east"}) {
            entries.put(Identifier.parse("minecraft:hopper_" + facing), new LightData(0, 0));
            entries.put(Identifier.parse("minecraft:hopper_" + facing + "_disabled"),
                    new LightData(0, 0));
        }
        for (int bits = 0; bits < 16; bits++) {
            StringBuilder wire = new StringBuilder("minecraft:tripwire");
            if ((bits & 1) != 0) {
                wire.append("_powered");
            }
            if ((bits & 2) != 0) {
                wire.append("_suspended");
            }
            if ((bits & 4) != 0) {
                wire.append("_attached");
            }
            if ((bits & 8) != 0) {
                wire.append("_disarmed");
            }
            entries.put(Identifier.parse(wire.toString()), new LightData(0, 0));
        }
        for (String facing : new String[]{"south", "west", "north", "east"}) {
            for (int attached = 0; attached <= 1; attached++) {
                for (int powered = 0; powered <= 1; powered++) {
                    entries.put(Identifier.parse("minecraft:tripwire_hook_" + facing
                            + (attached == 1 ? "_attached" : "")
                            + (powered == 1 ? "_powered" : "")), new LightData(0, 0));
                }
            }
        }
        return Map.copyOf(entries);
    }

    private static final Map<Identifier, LightData> ALL_LIGHT = mergeLight();

    private static Map<Identifier, LightData> mergeLight() {
        Map<Identifier, LightData> merged = new java.util.HashMap<>(LIGHT);
        merged.putAll(REDSTONE_LIGHT);
        return Map.copyOf(merged);
    }

    private BlockLightTable() {
    }

    /** @return the light behavior of a registered block (every registered block is present). */
    public static Optional<LightData> lookup(Identifier block) {
        return Optional.ofNullable(ALL_LIGHT.get(block));
    }

    /** @return the emitted level of a registered block, failing loudly on gaps (§147). */
    public static int emission(net.zaminmc.torch.block.BlockType type) {
        return require(type).emission();
    }

    /** @return the filter of a registered block, failing loudly on gaps (§147). */
    public static int filter(net.zaminmc.torch.block.BlockType type) {
        return require(type).filter();
    }

    private static LightData require(net.zaminmc.torch.block.BlockType type) {
        LightData data = ALL_LIGHT.get(type.identifier());
        if (data == null) {
            throw new IllegalStateException(
                    "Block type has no light table entry: " + type.identifier());
        }
        return data;
    }

    /** @return all table entries (diagnostics; registry coverage checks). */
    public static List<Map.Entry<Identifier, LightData>> entries() {
        return List.copyOf(ALL_LIGHT.entrySet());
    }
}
