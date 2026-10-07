package net.zamin.engine.block;

import net.zamin.api.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Survival behavior data for registered blocks, keyed by canonical identifier.
 *
 * <p>Data source (community, per the reuse rule): values were generated from
 * PrismarineJS/minecraft-data, {@code data/pc/1.8/blocks.json} + {@code items.json}
 * (MIT license, https://github.com/PrismarineJS/minecraft-data, snapshot 2026-10),
 * cross-checked against historical 1.8.8 behavior. No Java binding of that dataset
 * exists; embedding a generated subset with attribution is the established pattern
 * for JVM engines (the same approach Glowstone/Nukkit take with their tables).
 * The table only contains blocks the engine registers; it grows with the registry,
 * never speculatively.</p>
 *
 * <p>Drops reference canonical item identifiers. Dataset names differing from
 * modern canonical names are translated here (dataset {@code planks} →
 * {@code minecraft:oak_planks}, dataset {@code log} → {@code minecraft:oak_log});
 * the 1.8 adapter separately translates legacy numeric ids at the wire only.</p>
 */
public final class BlockBehaviorTable {

    private BlockBehaviorTable() {
    }

    private static final Map<Identifier, BlockBehavior> BEHAVIORS = Map.of(
            Identifier.parse("minecraft:air"),
            new BlockBehavior(0.0, false, false, null, List.of()),

            Identifier.parse("minecraft:stone"),
            new BlockBehavior(1.5, true, true, "rock",
                    List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone"), 1))),

            Identifier.parse("minecraft:grass_block"),
            new BlockBehavior(0.6, true, false, "dirt",
                    List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1))),

            Identifier.parse("minecraft:dirt"),
            new BlockBehavior(0.5, true, false, "dirt",
                    List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1))),

            Identifier.parse("minecraft:cobblestone"),
            new BlockBehavior(2.0, true, true, "rock",
                    List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone"), 1))),

            Identifier.parse("minecraft:oak_planks"),
            new BlockBehavior(2.0, true, false, "wood",
                    List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_planks"), 1))),

            Identifier.parse("minecraft:oak_log"),
            new BlockBehavior(2.0, true, false, "wood",
                    List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_log"), 1))),

            Identifier.parse("minecraft:bedrock"),
            BlockBehavior.unbreakable());

    /** @return the behavior of a block type, or empty for unregistered identifiers. */
    public static java.util.Optional<BlockBehavior> of(Identifier blockIdentifier) {
        return java.util.Optional.ofNullable(BEHAVIORS.get(blockIdentifier));
    }

    /**
     * @return whether the given held item (empty hand = {@code null}) can harvest
     *         drops from the block. Blocks without a tool requirement are
     *         harvestable by hand. No tools are implemented yet, so blocks that
     *         require one are never harvestable this slice (historical: breaking
     *         works, yields nothing).
     */
    public static boolean canHarvest(BlockBehavior behavior, net.zamin.api.ItemType held) {
        if (!behavior.requiresTool()) {
            return true;
        }
        // Tool items arrive with the tools slice; until then no hand can harvest
        // tool-gated blocks. The hook point is intentionally explicit.
        return held != null && ToolHarvestRules.canHarvest(behavior, held);
    }

    /**
     * Tool/harvest gating, separated so the tools slice replaces exactly one rule
     * point (harvest tool classes + speed multipliers from the same dataset).
     */
    private static final class ToolHarvestRules {
        static boolean canHarvest(BlockBehavior behavior, net.zamin.api.ItemType held) {
            return false; // no tool items exist yet
        }
    }
}
