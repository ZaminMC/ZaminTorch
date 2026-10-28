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
            new SmeltResult(Identifier.parse("minecraft:nether_brick"), 1, 0)),
            // The smelting-completion audit (SmeltingManager lines 25-64 —
            // the reference's own rows the table never carried): the raw
            // rabbit smelts like the food band (cooked_rabbit, 0.35 take)
            // and the cactus smelts to the GREEN dye (damage 2, 0.2 take).
            Map.entry(Identifier.parse("minecraft:rabbit"),
            new SmeltResult(Identifier.parse("minecraft:cooked_rabbit"), 1, 0)),
            Map.entry(Identifier.parse("minecraft:cactus"),
            new SmeltResult(Identifier.parse("minecraft:dye"), 1, 2)));
    // The oak_log entry is charcoal: output coal with damage 1, the 1.8 dataset
    // variant (items.json: coal metadata 1 = "Charcoal"). Charcoal burns like
    // coal because the fuel table keys by item type; the registry has exactly
    // one log, so the key covers every log variant the world can produce.

    // The fuel family (the furnace fuel & cycle parity audit — the reference's
    // own arms, FurnaceBlockEntity.getFuelTime lines 262-300, carried in the
    // reference's own ORDER): the pre-audit table pinned four wood rows, the
    // coal pair, the stick and the four wooden tools — and never carried the
    // wooden-slab arm, the Material.WOOD class beyond its four pins, the
    // wooden hoe, the lava bucket, the sapling pair, or the blaze rod. The
    // arms below name the reference's law they restore; the set grows with
    // the registry, never speculatively (this file's own provenance rule).
    private static final Map<Identifier, Integer> FUEL = Map.ofEntries(
            // The WOODEN_SLAB arm FIRST (lines 270-272): 150 ticks — checked
            // BEFORE the wood-material class, the reference's own ordering
            // law (half a plank's burn; a naive class-only read pays 300).
            // Both registered ids of the one reference item (Block.java line
            // 1275's legacy 126, SingleWoodenSlabBlock).
            Map.entry(Identifier.parse("minecraft:wooden_slab"), 150),
            Map.entry(Identifier.parse("minecraft:oak_slab"), 150),
            // The Material.WOOD class (lines 274-276): 300 ticks for every
            // registered block item whose BLOCK reads wood — the reference
            // answers the CLASS, so the rows below are the registry's wood
            // members, not a hand-picked four: the planks/log/log2 variant
            // trio, the workbench, the chest, the bookshelf (BookshelfBlock
            // line 12's own Material.WOOD), the note block (NoteBlock line
            // 21), the jukebox (JukeboxBlock line 24), the sign (SignBlock
            // line 19), the oak door (Block.java line 1130's explicit
            // Material.WOOD), the trapdoor (line 1198), the oak fence and the
            // five variant fences (FenceBlock(Material.WOOD) — Block.java
            // lines 1185 / 1419-1455), the fence gate (FenceGateBlock line
            // 22), and the six wood stairs (StairsBlock line 37 — the
            // modeled planks state's own material). The LADDER is NOT a
            // member: LadderBlock line 20 reads Material.DECORATION — the
            // one wooden-looking block the class law excludes.
            Map.entry(Identifier.parse("minecraft:oak_planks"), 300),
            Map.entry(Identifier.parse("minecraft:planks"), 300),
            Map.entry(Identifier.parse("minecraft:oak_log"), 300),
            Map.entry(Identifier.parse("minecraft:log"), 300),
            Map.entry(Identifier.parse("minecraft:log2"), 300),
            Map.entry(Identifier.parse("minecraft:crafting_table"), 300),
            Map.entry(Identifier.parse("minecraft:chest"), 300),
            Map.entry(Identifier.parse("minecraft:bookshelf"), 300),
            Map.entry(Identifier.parse("minecraft:noteblock"), 300),
            Map.entry(Identifier.parse("minecraft:jukebox"), 300),
            Map.entry(Identifier.parse("minecraft:sign"), 300),
            Map.entry(Identifier.parse("minecraft:oak_door"), 300),
            Map.entry(Identifier.parse("minecraft:trapdoor"), 300),
            Map.entry(Identifier.parse("minecraft:oak_fence"), 300),
            Map.entry(Identifier.parse("minecraft:spruce_fence"), 300),
            Map.entry(Identifier.parse("minecraft:birch_fence"), 300),
            Map.entry(Identifier.parse("minecraft:jungle_fence"), 300),
            Map.entry(Identifier.parse("minecraft:dark_oak_fence"), 300),
            Map.entry(Identifier.parse("minecraft:acacia_fence"), 300),
            Map.entry(Identifier.parse("minecraft:oak_fence_gate"), 300),
            Map.entry(Identifier.parse("minecraft:oak_stairs"), 300),
            Map.entry(Identifier.parse("minecraft:spruce_stairs"), 300),
            Map.entry(Identifier.parse("minecraft:birch_stairs"), 300),
            Map.entry(Identifier.parse("minecraft:jungle_stairs"), 300),
            Map.entry(Identifier.parse("minecraft:acacia_stairs"), 300),
            Map.entry(Identifier.parse("minecraft:dark_oak_stairs"), 300),
            // The coal block (Slice 32 — FurnaceBlockEntity getFuelTime
            // line 279: 16000 ticks, the 16-smelt block).
            Map.entry(Identifier.parse("minecraft:coal_block"), 16000),
            // The wooden TOOL / SWORD / HOE arms (lines 283-288): 200 each —
            // the reference reads the tier per ITEM CLASS (ToolItem, then
            // SwordItem, then HoeItem), and the pre-audit table's four rows
            // never carried the hoe.
            Map.entry(Identifier.parse("minecraft:wooden_pickaxe"), 200),
            Map.entry(Identifier.parse("minecraft:wooden_axe"), 200),
            Map.entry(Identifier.parse("minecraft:wooden_shovel"), 200),
            Map.entry(Identifier.parse("minecraft:wooden_sword"), 200),
            Map.entry(Identifier.parse("minecraft:wooden_hoe"), 200),
            // The item arms (lines 289-298): the stick 100, the coal 1600
            // (charcoal rides the type key — the one registry item covers
            // the damage-1 variant, the historical charcoal law), the lava
            // bucket 20000 (the ten-smelt bucket the pre-audit table never
            // carried), the sapling 100 (both registered ids of legacy 6),
            // and the blaze rod 2400 (the twelve-smelt rod).
            Map.entry(Identifier.parse("minecraft:stick"), 100),
            Map.entry(Identifier.parse("minecraft:coal"), 1600),
            Map.entry(Identifier.parse("minecraft:lava_bucket"), 20000),
            Map.entry(Identifier.parse("minecraft:sapling"), 100),
            Map.entry(Identifier.parse("minecraft:oak_sapling"), 100),
            Map.entry(Identifier.parse("minecraft:blaze_rod"), 2400));

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
            // The smelting-completion audit — the raw rabbit's cooked form
            // joins the food band (SmeltingManager line 33: 0.35).
            Map.entry(Identifier.parse("minecraft:cooked_rabbit"), 0.35),
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
     * The damage-split take rows (the smelting-completion audit — the
     * reference keys the RESULT STACK, item AND metadata, so two takes of
     * the same item id can pay different bands):
     * <ul>
     * <li>the coal pair (SmeltingManager lines 60 + 47-48): the ORE take
     *     (plain coal, damage 0) pays 0.1, the LOG take (charcoal, damage
     *     1) pays 0.15 — the pre-fix identifier table answered 0.15 for
     *     both;</li>
     * <li>the cactus take (line 50): the GREEN dye — damage 2 — pays 0.2
     *     (no other dye take pays: nothing else smelts to a dye);</li>
     * <li>the stone-brick take (line 41): the CRACKED brick — damage 2 —
     *     pays 0.1;</li>
     * <li>the sponge take (line 63): the DRIED sponge — damage 0 — pays
     *     0.15 (only the dried form is a smelting result).</li>
     * </ul>
     *
     * @return the damage-keyed take, or a negative when the identifier
     *         table answers instead.
     */
    private static double damageKeyedTakeOf(Identifier output, int damage) {
        String id = output.value();
        if (id.equals("coal")) {
            return damage == 1 ? 0.15 : 0.1;
        }
        if (id.equals("dye")) {
            return damage == 2 ? 0.2 : 0.0;
        }
        if (id.equals("stone_bricks")) {
            return damage == 2 ? 0.1 : 0.0;
        }
        if (id.equals("sponge")) {
            return damage == 0 ? 0.15 : 0.0;
        }
        return -1.0;
    }

    /**
     * The experience points one taken output STACK pays (the reference's
     * own keying — {@code SmeltingManager.getXp} consults the result stack's
     * item AND metadata).
     *
     * @param output the taken output's item type
     * @param damage the taken stack's damage field (the variant domains)
     */
    public static double takeExperienceOf(ItemType output, int damage) {
        double damageKeyed = damageKeyedTakeOf(output.identifier(), damage);
        if (damageKeyed >= 0.0) {
            return damageKeyed;
        }
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
        // The stone-brick take (the smelting-completion audit —
        // SmeltingManager line 41: the DEFAULT brick alone smelts into the
        // CRACKED form, damage 2 — the mossy/chiseled pair never enters
        // the furnace).
        if (input.identifier().equals(
                Identifier.parse("minecraft:stone_bricks"))) {
            return damage == 0
                    ? Optional.of(new SmeltResult(
                            Identifier.parse("minecraft:stone_bricks"), 1, 2))
                    : Optional.empty();
        }
        // The sponge take (SmeltingManager line 63: the WET sponge —
        // damage 1 — dries to the plain form; the dry sponge never
        // enters the furnace).
        if (input.identifier().equals(Identifier.parse("minecraft:sponge"))) {
            return damage == 1
                    ? Optional.of(new SmeltResult(
                            Identifier.parse("minecraft:sponge"), 1, 0))
                    : Optional.empty();
        }
        return resultOf(input);
    }

    /** @return the burn duration of one fuel unit in ticks, or 0 when not fuel. */
    public static int burnTicksOf(ItemType type) {
        return FUEL.getOrDefault(type.identifier(), 0);
    }

    /**
     * The fuel consumption's leftover (the reference tick's own consult,
     * FurnaceBlockEntity lines 184-187: when the igniting stack's LAST unit
     * burns away, the item's recipe remainder replaces the whole stack —
     * {@code Item.getRecipeRemainder}, whose rows live at the item
     * registrations: Item.java line 611 pins the lava bucket's
     * {@code setRecipeRemainder(bucket)}). Every other fuel leaves nothing
     * (the reference's null row) — smelting consumes its input clean, and
     * only the fuel stack ever rides this arm.
     *
     * @return the leftover stack's item type, or null when the fuel leaves
     *         nothing behind.
     */
    public static ItemType fuelRemainderOf(ItemType fuel) {
        if (fuel.identifier().value().equals("lava_bucket")) {
            return BuiltinItems.lookup(Identifier.parse("minecraft:bucket")).orElse(null);
        }
        return null;
    }
}
