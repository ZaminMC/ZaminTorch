package net.zaminmc.torch.server.item;

import net.zaminmc.torch.util.Identifier;

import net.zaminmc.torch.item.ItemType;

import java.util.Map;
import java.util.Optional;

/**
 * Food values for edible items, embedded from community data.
 *
 * <p>Data source (per the reuse rule): PrismarineJS/minecraft-data,
 * {@code data/pc/1.8/foods.json} (MIT license,
 * https://github.com/PrismarineJS/minecraft-data, snapshot 2026-10). The
 * dataset's {@code foodPoints} is the hunger restored and its
 * {@code saturation} the saturation restored. The fold both speak is the
 * reference's own apply-time law — HungerManager.add lines 20-23:
 * {@code saturation += food x modifier x 2}, the item's registered
 * modifier (Item.java's FoodItem rows) folded by the hunger manager at
 * every bite — cooked beef's 8 food over 0.8 answers 12.8, the anchor.
 * The verifier's seventh pass re-pinned every row against that formula
 * (the raw chicken had ridden the dataset's own half-fold at 0.6; the
 * reference folds 2 x 0.3 x 2 = 1.2), added the six registered foods the
 * table never carried, and moved the fold for the fish family into
 * {@link FishItems} at the answer. The set grows with the registry, never
 * speculatively. Eating takes 32 ticks (1.6 s), the historical
 * {@code ItemFood} duration.</p>
 */
public final class Foods {

    /** Historical eating duration in ticks. */
    public static final int EAT_TICKS = 32;

    /** Restored hunger and saturation for one eaten unit. */
    public record Nutrition(int foodPoints, float saturation) {
    }

    /**
     * The FoodItem's own effect pair (Item.java's setStatusEffect family):
     * the effect id, the duration in SECONDS (the reference's
     * {@code duration * 20} at apply), the amplifier and the chance the
     * eat rolls ({@code effectChance}).
     */
    public record EatEffect(int effect, int durationSeconds, int amplifier, float chance) {
    }

    private static final Map<net.zaminmc.torch.util.Identifier, Nutrition> FOODS = Map.ofEntries(
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:beef"), new Nutrition(3, 1.8f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:cooked_beef"), new Nutrition(8, 12.8f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:porkchop"), new Nutrition(3, 1.8f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:cooked_porkchop"), new Nutrition(8, 12.8f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:chicken"),
                    new Nutrition(2, 1.2f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:cooked_chicken"), new Nutrition(6, 7.2f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:rotten_flesh"), new Nutrition(4, 0.8f)),
            // The spider eye's own nutrition (the reference's FoodItem(2, 0.8F,
            // false) — the item itself landed with Slice 11e's brewing
            // family; 2 food, 2 × 0.8 × 2 = 3.2 saturation).
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:spider_eye"), new Nutrition(2, 3.2f)),
            // The crop food (community foods.json: bread food 5, saturation 6.0)
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:bread"), new Nutrition(5, 6.0f)),
            // The breeding-slice foods (community foods.json: apple 4/2.4,
            // carrot 3/3.6, golden carrot 6/14.4, golden apple 4/9.6).
            // The golden apple's Regeneration II + Absorption effects need
            // the potion system (ledger: plain food for now).
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:apple"), new Nutrition(4, 2.4f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:carrot"), new Nutrition(3, 3.6f)),
            // The potato pair (Slice 12b — community foods.json: the raw
            // potato 1/0.6, the baked pair 5/6.0).
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:potato"), new Nutrition(1, 0.6f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:baked_potato"), new Nutrition(5, 6.0f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:golden_carrot"), new Nutrition(6, 14.4f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:golden_apple"), new Nutrition(4, 9.6f)),
            // The rabbit family (Slice 12l — the reference's Item.java lines
            // 715-717: FoodItem(3, 0.3F), FoodItem(5, 0.6F), FoodItem(10,
            // 0.6F); the dataset saturation fold: 3x0.3x2=1.8, 5x0.6x2=6.0,
            // 10x0.6x2=12.0).
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:rabbit"), new Nutrition(3, 1.8f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:cooked_rabbit"), new Nutrition(5, 6.0f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:rabbit_stew"), new Nutrition(10, 12.0f)),
            // The mooshroom's stew (Slice 17b — the reference's Item.java:
            // FoodItem(6, 0.6F); the dataset saturation fold 6*0.6*2=7.2).
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:mushroom_stew"), new Nutrition(6, 7.2f)),
            // The food-table completion audit (the verifier's seventh pass —
            // the registered foods the table never carried, each folded from
            // the reference's own Item.java registration):
            // the cookie (line 643: FoodItem(2, 0.1F) — 2x0.1x2 = 0.4);
            // the melon slice (line 360: FoodItem(2, 0.3F) — 1.2);
            // the poisonous potato (line 394: FoodItem(2, 0.3F) — 1.2; its
            // 60% Poison effect row sat in the EFFECTS table below while
            // the missing nutrition row made the item UNEATABLE, the effect
            // unreachable);
            // the pumpkin pie (line 400: FoodItem(8, 0.3F) — 4.8);
            // the mutton pair (lines 727-728: FoodItem(2, 0.3F) and
            // FoodItem(6, 0.8F) — 1.2 and 9.6; the SMELTING table carried
            // the pair since Slice 11i while the FOODS table never did, so
            // neither form answered eatable).
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:cookie"), new Nutrition(2, 0.4f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:melon"), new Nutrition(2, 1.2f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:poisonous_potato"), new Nutrition(2, 1.2f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:pumpkin_pie"), new Nutrition(8, 4.8f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:mutton"), new Nutrition(2, 1.2f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:cooked_mutton"), new Nutrition(6, 9.6f)));

    /**
     * The always-edible pair (Item.java line 604: the golden apple's own
     * {@code setAlwaysEdible()} — the registration the food family carries
     * once): FoodItem.startUsing line 59 consults
     * {@code player.canEat(this.alwaysEdible)}, so a survival body at FULL
     * hunger still begins the golden apple's eat (the effects pay; the
     * nutrition clamps). Every other food gates on needsFood alone.
     */
    private static final java.util.Set<net.zaminmc.torch.util.Identifier> ALWAYS_EDIBLE =
            java.util.Set.of(net.zaminmc.torch.util.Identifier.parse("minecraft:golden_apple"));

    /** @return whether the item eats at full hunger (the always-edible law). */
    public static boolean isAlwaysEdible(ItemType type) {
        return ALWAYS_EDIBLE.contains(type.identifier());
    }

    private Foods() {
    }

    /**
     * The damage-aware nutrition (Slice 11i — the FishItem's own
     * getHungerPoints/getSaturation overrides): the fish family answers
     * by the metadata variant, everything else by the plain table.
     *
     * @return the nutrition of an edible stack, or empty when not food.
     */
    public static Optional<Nutrition> nutritionOf(ItemType type, int damage) {
        if (FishItems.isFishFamily(type.identifier())) {
            return Optional.of(FishItems.nutritionOf(type.identifier(), damage));
        }
        return Optional.ofNullable(FOODS.get(type.identifier()));
    }

    /** @return the nutrition of an edible item, or empty when not food. */
    public static Optional<Nutrition> nutritionOf(ItemType type) {
        return Optional.ofNullable(FOODS.get(type.identifier()));
    }

    // The FoodItem registrations' own effect pairs (Item.java lines
    // 595-694, the setStatusEffect calls): the raw chicken's 30% Hunger,
    // the rotten flesh's 80%, the spider eye's certain Poison, the
    // poisonous potato's 60%, and the plain golden apple's certain
    // Regeneration II — the pair GoldenAppleItem's else-branch reaches
    // through its super call (the reference's own structure).
    private static final Map<net.zaminmc.torch.util.Identifier, EatEffect> EFFECTS = Map.ofEntries(
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:chicken"),
                    new EatEffect(17, 30, 0, 0.3f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:rotten_flesh"),
                    new EatEffect(17, 30, 0, 0.8f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:spider_eye"),
                    new EatEffect(19, 5, 0, 1.0f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:poisonous_potato"),
                    new EatEffect(19, 5, 0, 0.6f)),
            Map.entry(net.zaminmc.torch.util.Identifier.parse("minecraft:golden_apple"),
                    new EatEffect(10, 5, 1, 1.0f)));

    /** @return the FoodItem's own effect pair, or empty when the food carries none. */
    public static Optional<EatEffect> effectOf(ItemType type) {
        return Optional.ofNullable(EFFECTS.get(type.identifier()));
    }
}
