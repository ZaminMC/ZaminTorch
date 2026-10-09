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
 * {@code saturation} the saturation restored (it already folds in the
 * historical foodPoints x saturationModifier x 2 computation — cooked beef's
 * 8 food yields 12.8 saturation, the dataset anchor). The set grows with the
 * registry, never speculatively. Eating takes 32 ticks (1.6 s), the
 * historical {@code ItemFood} duration.</p>
 */
public final class Foods {

    /** Historical eating duration in ticks. */
    public static final int EAT_TICKS = 32;

    /** Restored hunger and saturation for one eaten unit. */
    public record Nutrition(int foodPoints, float saturation) {
    }

    private static final Map<net.zaminmc.torch.util.Identifier, Nutrition> FOODS = Map.of(
            net.zaminmc.torch.util.Identifier.parse("minecraft:beef"), new Nutrition(3, 1.8f),
            net.zaminmc.torch.util.Identifier.parse("minecraft:cooked_beef"), new Nutrition(8, 12.8f),
            net.zaminmc.torch.util.Identifier.parse("minecraft:porkchop"), new Nutrition(3, 1.8f),
            net.zaminmc.torch.util.Identifier.parse("minecraft:cooked_porkchop"), new Nutrition(8, 12.8f),
            net.zaminmc.torch.util.Identifier.parse("minecraft:chicken"), new Nutrition(2, 0.6f),
            net.zaminmc.torch.util.Identifier.parse("minecraft:cooked_chicken"), new Nutrition(6, 7.2f),
            net.zaminmc.torch.util.Identifier.parse("minecraft:rotten_flesh"), new Nutrition(4, 0.8f),
            // The crop food (community foods.json: bread food 5, saturation 6.0)
            net.zaminmc.torch.util.Identifier.parse("minecraft:bread"), new Nutrition(5, 6.0f));

    private Foods() {
    }

    /** @return the nutrition of an edible item, or empty when not food. */
    public static Optional<Nutrition> nutritionOf(ItemType type) {
        return Optional.ofNullable(FOODS.get(type.identifier()));
    }
}
