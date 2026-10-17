package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.util.Collection;
import java.util.List;
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

    private static final Map<Identifier, SmeltResult> SMELTING = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:iron_ore"),
            new SmeltResult(Identifier.parse("minecraft:iron_ingot"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:gold_ore"),
            new SmeltResult(Identifier.parse("minecraft:gold_ingot"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:cobblestone"),
            new SmeltResult(Identifier.parse("minecraft:stone"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:sand"),
            new SmeltResult(Identifier.parse("minecraft:glass"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:beef"),
            new SmeltResult(Identifier.parse("minecraft:cooked_beef"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:porkchop"),
            new SmeltResult(Identifier.parse("minecraft:cooked_porkchop"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:chicken"),
            new SmeltResult(Identifier.parse("minecraft:cooked_chicken"), 1, 0)),
            // The mutton pair (Slice 11i's data-gap fix — the item arrived
            // with the living-night slice's own registration comment claiming
            // the smelt, but the row never landed; the community table's own
            // raw -> cooked entry).
            Map.entry(Identifier.parse("minecraft:mutton"),
            new SmeltResult(Identifier.parse("minecraft:cooked_mutton"), 1, 0)),
            // The potato pair (Slice 12b — the village farm's second crop:
            // the raw potato bakes at 0.35 XP like the food band).
            Map.entry(Identifier.parse("minecraft:potato"),
            new SmeltResult(Identifier.parse("minecraft:baked_potato"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:oak_log"),
            new SmeltResult(Identifier.parse("minecraft:coal"), 1, 1)),
            // The vault's smelts (Slice 32 — SmeltingManager lines 25-64):
            // the clay pair (the ball -> the brick at 0.3, the block ->
            // hardened clay at 0.35), the missing ore smelts (coal 0.1,
            // redstone 0.7, lapis 0.2, emerald 1.0, quartz 0.2), and the
            // netherrack -> nether brick (0.1 — Item.java 405's own row).
            Map.entry(Identifier.parse("minecraft:clay_ball"),
            new SmeltResult(Identifier.parse("minecraft:brick"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:clay"),
            new SmeltResult(Identifier.parse("minecraft:hardened_clay"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:coal_ore"),
            new SmeltResult(Identifier.parse("minecraft:coal"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:redstone_ore"),
            new SmeltResult(Identifier.parse("minecraft:redstone"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:lapis_ore"),
            new SmeltResult(Identifier.parse("minecraft:lapis"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:diamond_ore"),
            new SmeltResult(Identifier.parse("minecraft:diamond"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:emerald_ore"),
            new SmeltResult(Identifier.parse("minecraft:emerald"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:quartz_ore"),
            new SmeltResult(Identifier.parse("minecraft:quartz"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:netherrack"),
            new SmeltResult(Identifier.parse("minecraft:nether_brick"), 1, 0)));
    // The oak_log entry is charcoal: output coal with damage 1, the 1.8 dataset
    // variant (items.json: coal metadata 1 = "Charcoal"). Charcoal burns like
    // coal because the fuel table keys by item type; the registry has exactly
    // one log, so the key covers every log variant the world can produce.

    private static final Map<Identifier, Integer> FUEL = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:coal"), 1600),
            // The coal block's burn (Slice 32 — FurnaceBlockEntity
            // getFuelTime line 279: 16000 ticks, the 16-smelt block).
            Map.entry(Identifier.parse("minecraft:coal_block"), 16000),
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
     * The experience points one taken output item pays. The cooked fish
     * (Slice 11i) joins the cooked-food band at 0.35.
     */
    private static final Map<Identifier, Double> TAKE_EXPERIENCE = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:iron_ingot"), 0.7),
            Map.entry(Identifier.parse("minecraft:gold_ingot"), 1.0),
            Map.entry(Identifier.parse("minecraft:stone"), 0.1),
            Map.entry(Identifier.parse("minecraft:glass"), 0.1),
            Map.entry(Identifier.parse("minecraft:cooked_beef"), 0.35),
            Map.entry(Identifier.parse("minecraft:cooked_porkchop"), 0.35),
            Map.entry(Identifier.parse("minecraft:cooked_chicken"), 0.35),
            Map.entry(Identifier.parse("minecraft:cooked_mutton"), 0.35),
            Map.entry(Identifier.parse("minecraft:baked_potato"), 0.35),
            Map.entry(Identifier.parse("minecraft:cooked_fish"), 0.35),
            Map.entry(Identifier.parse("minecraft:coal"), 0.15),
            // The vault's take XP (Slice 32 — SmeltingManager's own rows):
            // the brick 0.3, hardened clay 0.35, the ore smelts' bands.
            Map.entry(Identifier.parse("minecraft:brick"), 0.3),
            Map.entry(Identifier.parse("minecraft:hardened_clay"), 0.35),
            Map.entry(Identifier.parse("minecraft:lapis"), 0.2),
            Map.entry(Identifier.parse("minecraft:emerald"), 1.0),
            Map.entry(Identifier.parse("minecraft:redstone"), 0.7),
            Map.entry(Identifier.parse("minecraft:diamond"), 1.0),
            Map.entry(Identifier.parse("minecraft:quartz"), 0.2),
            Map.entry(Identifier.parse("minecraft:nether_brick"), 0.1));

    /** @return the experience points one taken output item pays. */
    public static double takeExperienceOf(ItemType output) {
        return TAKE_EXPERIENCE.getOrDefault(output.identifier(), 0.0);
    }

    /**
     * The smelting outputs themselves (Stats.initItemsCraftedStats' own
     * union member — the crafted-family stat keys walk them): every
     * output row the table carries, once each.
     */
    public static Collection<SmeltResult> smeltingResults() {
        return List.copyOf(SMELTING.values());
    }

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

    /**
     * The damage-aware smelting (Slice 11i — the fish family's own pair of
     * recipes, the reference's FurnaceRecipes.addSmelting rows): the raw
     * cod (metadata 0) smelts into the cooked cod, the raw salmon (metadata
     * 1) into the cooked salmon — the canBeCooked gate keeps the clownfish
     * and the pufferfish out of the furnace. Everything else keys by the
     * item alone (the plain table's own rule).
     */
    public static Optional<SmeltResult> resultOf(ItemType input, int damage) {
        if (input.identifier().equals(
                net.zaminmc.torch.server.item.FishItems.FISH)) {
            return switch (damage) {
                case 0 -> Optional.of(new SmeltResult(
                        net.zaminmc.torch.server.item.FishItems.COOKED_FISH, 1, 0));
                case 1 -> Optional.of(new SmeltResult(
                        net.zaminmc.torch.server.item.FishItems.COOKED_FISH, 1, 1));
                default -> Optional.empty(); // the non-cookable variants
            };
        }
        return resultOf(input);
    }

    /** @return the burn duration of one fuel unit in ticks, or 0 when not fuel. */
    public static int burnTicksOf(ItemType type) {
        return FUEL.getOrDefault(type.identifier(), 0);
    }
}
