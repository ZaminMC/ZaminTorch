package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.util.Map;
import java.util.Optional;

/**
 * Furnace data tables: smelting inputs and fuel burn values.
 *
 * <p>Provenance (hard rule: community data first): PrismarineJS/minecraft-data
 * — the project's canonical community dataset — carries no smelting or fuel
 * table for pc/1.8 (verified by a full-tree search of the repository, snapshot
 * 2026-10; it does carry the furnace <em>window</em> layout and property
 * contract). The values below therefore mirror the canonical Minecraft Wiki
 * tables for 1.8 (Smelting / Furnace fuel), encoded as the minimal subset the
 * current registry can reference — the set grows with the registry, never
 * speculatively, exactly like {@link net.zaminmc.torch.server.block.BlockBehaviorTable}.
 * Burn values are in ticks (20/s; coal's 1600 = 8 smelts, the historical
 * anchor every value is checked against).</p>
 *
 * <p>Smelting results keep the damage field: charcoal is coal with damage 1
 * (the 1.8 dataset encodes it as item 263 metadata 1), and any-metadata
 * ingredient rules make charcoal usable wherever coal is — the historical
 * semantics. Smelting takes 200 ticks (10 s) per item, the historical
 * {@code furnaceCookTime} cycle.</p>
 */
public final class FurnaceRecipes {

    /** Historical cook cycle length in ticks. */
    public static final int COOK_TICKS = 200;

    private FurnaceRecipes() {
    }

    /** Smelting result: output item identifier, unit count, damage (charcoal). */
    public record SmeltResult(Identifier output, int count, int damage) {
    }

    private static final Map<Identifier, SmeltResult> SMELTING = Map.of(
            Identifier.parse("minecraft:iron_ore"),
            new SmeltResult(Identifier.parse("minecraft:iron_ingot"), 1, 0),
            Identifier.parse("minecraft:cobblestone"),
            new SmeltResult(Identifier.parse("minecraft:stone"), 1, 0),
            Identifier.parse("minecraft:sand"),
            new SmeltResult(Identifier.parse("minecraft:glass"), 1, 0),
            Identifier.parse("minecraft:beef"),
            new SmeltResult(Identifier.parse("minecraft:cooked_beef"), 1, 0),
            Identifier.parse("minecraft:porkchop"),
            new SmeltResult(Identifier.parse("minecraft:cooked_porkchop"), 1, 0),
            Identifier.parse("minecraft:chicken"),
            new SmeltResult(Identifier.parse("minecraft:cooked_chicken"), 1, 0),
            Identifier.parse("minecraft:oak_log"),
            new SmeltResult(Identifier.parse("minecraft:coal"), 1, 1));
    // The oak_log entry is charcoal: output coal with damage 1, the 1.8 dataset
    // variant (items.json: coal metadata 1 = "Charcoal"). Charcoal burns like
    // coal because the fuel table keys by item type; the registry has exactly
    // one log, so the key covers every log variant the world can produce.

    private static final Map<Identifier, Integer> FUEL = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:coal"), 1600),
            Map.entry(Identifier.parse("minecraft:oak_planks"), 300),
            Map.entry(Identifier.parse("minecraft:oak_log"), 300),
            Map.entry(Identifier.parse("minecraft:stick"), 100),
            Map.entry(Identifier.parse("minecraft:crafting_table"), 300),
            Map.entry(Identifier.parse("minecraft:chest"), 300),
            Map.entry(Identifier.parse("minecraft:wooden_pickaxe"), 200),
            Map.entry(Identifier.parse("minecraft:wooden_axe"), 200),
            Map.entry(Identifier.parse("minecraft:wooden_shovel"), 200),
            Map.entry(Identifier.parse("minecraft:wooden_sword"), 200));

    /**
     * The smelting result for an input stack, or empty when the input is not
     * smeltable or its output is not a registered item (registry-gated data).
     */
    public static Optional<SmeltResult> resultOf(ItemType input) {
        SmeltResult result = SMELTING.get(input.identifier());
        if (result == null) {
            return Optional.empty();
        }
        if (BuiltinItems.lookup(result.output()).isEmpty()) {
            return Optional.empty(); // output not in the registry yet: no recipe
        }
        return Optional.of(result);
    }

    /** @return the burn duration of one fuel unit in ticks, or 0 when not fuel. */
    public static int burnTicksOf(ItemType type) {
        return FUEL.getOrDefault(type.identifier(), 0);
    }
}
