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
            Map.entry(Identifier.parse("minecraft:water"), new LightData(0, 2)),
            Map.entry(Identifier.parse("minecraft:falling_water"), new LightData(0, 2)),
            Map.entry(Identifier.parse("minecraft:lava"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:falling_lava"), new LightData(15, 0)),
            Map.entry(Identifier.parse("minecraft:sand"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:gravel"), new LightData(0, 15)),
            Map.entry(Identifier.parse("minecraft:crafting_table"), new LightData(0, 15)),

            // The one engine light source: torch emits 14, filters nothing
            // (dataset emitLight 14, filterLight 0).
            Map.entry(Identifier.parse("minecraft:torch"), new LightData(14, 0)),

            // Transparent to light, no emission (dataset filterLight 0): light
            // crosses glass, a chest's empty bounding shape and an unlit furnace
            // paying only the standard per-cell step.
            Map.entry(Identifier.parse("minecraft:glass"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:chest"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:furnace"), new LightData(0, 0)),

            // The flora passes light fully (dataset filterLight 0: the plants
            // shade nothing); sandstone is a full opaque cube (filter 15).
            Map.entry(Identifier.parse("minecraft:tall_grass"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sign"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sign_west"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sign_north"), new LightData(0, 0)),
            Map.entry(Identifier.parse("minecraft:sign_east"), new LightData(0, 0)),
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
            Map.entry(Identifier.parse("minecraft:sandstone"), new LightData(0, 15)));

    private BlockLightTable() {
    }

    /** @return the light behavior of a registered block (every registered block is present). */
    public static Optional<LightData> lookup(Identifier block) {
        return Optional.ofNullable(LIGHT.get(block));
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
        LightData data = LIGHT.get(type.identifier());
        if (data == null) {
            throw new IllegalStateException(
                    "Block type has no light table entry: " + type.identifier());
        }
        return data;
    }

    /** @return all table entries (diagnostics; registry coverage checks). */
    public static List<Map.Entry<Identifier, LightData>> entries() {
        return List.copyOf(LIGHT.entrySet());
    }
}
