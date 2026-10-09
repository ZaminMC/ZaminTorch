package net.zaminmc.torch.server.block;

import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.item.ToolClass;
import net.zaminmc.torch.server.item.ToolMaterial;
import net.zaminmc.torch.server.item.ToolSpec;
import net.zaminmc.torch.server.item.Tools;

import java.util.List;
import java.util.Map;

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

    private static final Map<Identifier, BlockBehavior> BEHAVIORS = Map.ofEntries(
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

            Map.entry(Identifier.parse("minecraft:bedrock"),
                    BlockBehavior.unbreakable()));

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
