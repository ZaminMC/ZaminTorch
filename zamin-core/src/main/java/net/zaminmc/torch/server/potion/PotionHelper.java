package net.zaminmc.torch.server.potion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The potion metadata engine (Slice 11b — the reference port of
 * entity/living/effect/PotionHelper.java, 1:1): the 1.8.8 damage-value
 * potion model where the item's metadata encodes a set of brewing flags
 * and the effect set derives from two recipe tables evaluated over those
 * flags.
 *
 * <p>The metadata layout: bits 0-3 the brewing flags, bit 5 the
 * amplifier-II (the AMPLIFIER_RECIPES' own "5"), bit 6 the extended
 * duration (REDSTONE's own -5+6-7), bit 13 the drinkable/splash carrier,
 * bit 14 the splash itself ({@code 16384}); the duration math
 * (getStatusEffects lines 414-422) walks the recipe's level i through
 * {@code 1200 * (i * 3 + (i - 1) * 2)} then the amplifier's right shift,
 * the effect's own duration multiplier, and the splash's 0.75 shave.</p>
 *
 * <p>{@link #applyIngredient} (the brewing stand's step) rides the same
 * flag model and stays here for the brewing slice.</p>
 */
public final class PotionHelper {

    private PotionHelper() {
    }

    // The ingredient recipes (PotionHelper lines 12-24) — the brewing
    // slice reads them; the port keeps the exact strings.
    public static final String SUGAR = "-0+1-2-3&4-4+13";
    public static final String GHAST_TEAR = "+0-1-2-3&4-4+13";
    public static final String SPIDER_EYE = "-0-1+2-3&4-4+13";
    public static final String FERMENTED_SPIDER_EYE = "-0+3-4+13";
    public static final String GLISTERING_MELON = "+0-1+2-3&4-4+13";
    public static final String BLAZE_POWDER = "+0-1-2+3&4-4+13";
    public static final String MAGMA_CREAM = "+0+1-2-3&4-4+13";
    public static final String REDSTONE = "-5+6-7";
    public static final String GLOWSTONE = "+5-6-7";
    public static final String GUNPOWDER = "+14&13-13";
    public static final String GOLDEN_CARROT = "-0+1+2-3+13&4-4";
    public static final String PUFFERFISH = "+0-1+2+3+13&4-4";
    public static final String JUMP_BOOST = "+0+1-2+3&4-4+13";

    /** The splash bit (PotionItem.isSplashPotion line 129). */
    public static final int SPLASH_BIT = 16384;

    private static final Map<Integer, String> DURATION_RECIPES = new HashMap<>();
    private static final Map<Integer, String> AMPLIFIER_RECIPES = new HashMap<>();

    static {
        DURATION_RECIPES.put(StatusEffects.REGENERATION, "0 & !1 & !2 & !3 & 0+6");
        DURATION_RECIPES.put(StatusEffects.SPEED, "!0 & 1 & !2 & !3 & 1+6");
        DURATION_RECIPES.put(StatusEffects.FIRE_RESISTANCE, "0 & 1 & !2 & !3 & 0+6");
        DURATION_RECIPES.put(StatusEffects.INSTANT_HEALTH, "0 & !1 & 2 & !3");
        DURATION_RECIPES.put(StatusEffects.POISON, "!0 & !1 & 2 & !3 & 2+6");
        DURATION_RECIPES.put(StatusEffects.WEAKNESS, "!0 & !1 & !2 & 3 & 3+6");
        DURATION_RECIPES.put(StatusEffects.INSTANT_DAMAGE, "!0 & !1 & 2 & 3");
        DURATION_RECIPES.put(StatusEffects.SLOWNESS, "!0 & 1 & !2 & 3 & 3+6");
        DURATION_RECIPES.put(StatusEffects.STRENGTH, "0 & !1 & !2 & 3 & 3+6");
        DURATION_RECIPES.put(StatusEffects.NIGHT_VISION, "!0 & 1 & 2 & !3 & 2+6");
        DURATION_RECIPES.put(StatusEffects.INVISIBILITY, "!0 & 1 & 2 & 3 & 2+6");
        DURATION_RECIPES.put(StatusEffects.WATER_BREATHING, "0 & !1 & 2 & 3 & 2+6");
        DURATION_RECIPES.put(StatusEffects.JUMP_BOOST, "0 & 1 & !2 & 3 & 3+6");
        AMPLIFIER_RECIPES.put(StatusEffects.SPEED, "5");
        AMPLIFIER_RECIPES.put(StatusEffects.HASTE, "5");
        AMPLIFIER_RECIPES.put(StatusEffects.STRENGTH, "5");
        AMPLIFIER_RECIPES.put(StatusEffects.REGENERATION, "5");
        AMPLIFIER_RECIPES.put(StatusEffects.INSTANT_DAMAGE, "5");
        AMPLIFIER_RECIPES.put(StatusEffects.INSTANT_HEALTH, "5");
        AMPLIFIER_RECIPES.put(StatusEffects.RESISTANCE, "5");
        AMPLIFIER_RECIPES.put(StatusEffects.POISON, "5");
        AMPLIFIER_RECIPES.put(StatusEffects.JUMP_BOOST, "5");
    }

    /** @return whether the flag is set in the metadata (isFlagEnabled). */
    public static boolean isFlagEnabled(int metadata, int flag) {
        return (metadata & 1 << flag) != 0;
    }

    /** The number of set bits (countSetFlags lines 170-177). */
    private static int countSetFlags(int metadata) {
        int count;
        for (count = 0; metadata > 0; count++) {
            metadata &= metadata - 1;
        }
        return count;
    }

    /** @return whether the metadata names a splash potion. */
    public static boolean isSplashPotion(int metadata) {
        return (metadata & SPLASH_BIT) != 0;
    }

    /**
     * The prefix index (buildPotionId lines 574-580 with the flag order
     * 5,4,3,2,1 — the reference's own getPrefixIndex).
     */
    public static int getPrefixIndex(int metadata) {
        return buildPotionId(metadata, 5, 4, 3, 2, 1);
    }

    private static int buildPotionId(int metadata, int flag1, int flag2, int flag3,
                                     int flag4, int flag5) {
        return (isFlagEnabled(metadata, flag1) ? 16 : 0)
                | (isFlagEnabled(metadata, flag2) ? 8 : 0)
                | (isFlagEnabled(metadata, flag3) ? 4 : 0)
                | (isFlagEnabled(metadata, flag4) ? 2 : 0)
                | (isFlagEnabled(metadata, flag5) ? 1 : 0);
    }

    /**
     * The potion color (getColor lines 79-111): the per-effect color
     * averaged over the amplifiers of every particle-carrying effect —
     * the splash tint. The empty set answers the water-bottle blue-grey
     * (3694022).
     */
    public static int getColor(List<StatusEffectInstance> effects) {
        int fallback = 3694022;
        if (effects == null || effects.isEmpty()) {
            return fallback;
        }
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        float count = 0.0F;
        for (StatusEffectInstance instance : effects) {
            if (instance.hasParticles()) {
                int color = StatusEffects.colorOf(instance.id());
                for (int amplifier = 0; amplifier <= instance.amplifier(); amplifier++) {
                    red += (color >> 16 & 0xFF) / 255.0F;
                    green += (color >> 8 & 0xFF) / 255.0F;
                    blue += (color >> 0 & 0xFF) / 255.0F;
                    count++;
                }
            }
        }
        if (count == 0.0F) {
            return 0;
        }
        red = red / count * 255.0F;
        green = green / count * 255.0F;
        blue = blue / count * 255.0F;
        return (int) red << 16 | (int) green << 8 | (int) blue;
    }

    /** The metadata-keyed color (getColor(metadata, false) — the cached arm). */
    public static int getColor(int metadata) {
        return getColor(getStatusEffects(metadata));
    }

    /**
     * The flag-expression evaluator (parseRecipe lines 272-394, the
     * reference's own documented grammar: '|' takes the left when positive
     * else the right, '&' zeroes on either zero else the max, the
     * sub-expression sums its statements).
     */
    static int parseRecipe(String recipe, int from, int to, int metadata) {
        if (from < recipe.length() && to >= 0 && from < to) {
            int or = recipe.indexOf('|', from);
            if (or >= 0 && or < to) {
                int left = parseRecipe(recipe, from, or - 1, metadata);
                if (left > 0) {
                    return left;
                }
                int right = parseRecipe(recipe, or + 1, to, metadata);
                return right > 0 ? right : 0;
            }
            int and = recipe.indexOf('&', from);
            if (and >= 0 && and < to) {
                int left = parseRecipe(recipe, from, and - 1, metadata);
                if (left <= 0) {
                    return 0;
                }
                int right = parseRecipe(recipe, and + 1, to, metadata);
                if (right <= 0) {
                    return 0;
                }
                return Math.max(left, right);
            }
            // The statement walk (the reference's flag/operator machine).
            boolean multiply = false;
            boolean multiplierRead = false;
            boolean negate = false;
            boolean invert = false;
            boolean operandRead = false;
            int comparison = -1;
            int operand = 0;
            int multiplier = 0;
            int total = 0;
            for (int at = from; at < to; at++) {
                char c = recipe.charAt(at);
                if (c >= '0' && c <= '9') {
                    if (multiply) {
                        multiplier = c - '0';
                        multiplierRead = true;
                    } else {
                        operand *= 10;
                        operand += c - '0';
                        operandRead = true;
                    }
                } else if (c == '*') {
                    multiply = true;
                } else if (c == '!') {
                    if (operandRead) {
                        total += evaluate(invert, multiplierRead, negate, comparison,
                                operand, multiplier, metadata);
                        invert = false;
                        negate = false;
                        multiply = false;
                        multiplierRead = false;
                        operandRead = false;
                        multiplier = 0;
                        operand = 0;
                        comparison = -1;
                    }
                    invert = true;
                } else if (c == '-') {
                    if (operandRead) {
                        total += evaluate(invert, multiplierRead, negate, comparison,
                                operand, multiplier, metadata);
                        invert = false;
                        negate = false;
                        multiply = false;
                        multiplierRead = false;
                        operandRead = false;
                        multiplier = 0;
                        operand = 0;
                        comparison = -1;
                    }
                    negate = true;
                } else if (c == '+' || c == '=' || c == '<' || c == '>') {
                    if (operandRead) {
                        total += evaluate(invert, multiplierRead, negate, comparison,
                                operand, multiplier, metadata);
                        invert = false;
                        negate = false;
                        multiply = false;
                        multiplierRead = false;
                        operandRead = false;
                        multiplier = 0;
                        operand = 0;
                        comparison = -1;
                    }
                    if (c == '=') {
                        comparison = 0;
                    } else if (c == '<') {
                        comparison = 2;
                    } else if (c == '>') {
                        comparison = 1;
                    }
                }
            }
            if (operandRead) {
                total += evaluate(invert, multiplierRead, negate, comparison,
                        operand, multiplier, metadata);
            }
            return total;
        }
        return 0;
    }

    /** The single statement (evaluateMetadata lines 143-168). */
    private static int evaluate(boolean invert, boolean multiply, boolean negate,
                                int comparison, int operand, int multiplier, int metadata) {
        int result;
        if (invert) {
            result = isFlagEnabled(metadata, operand) ? 0 : 1;
        } else if (comparison != -1) {
            if (comparison == 0 && countSetFlags(metadata) == operand) {
                result = 1;
            } else if (comparison == 1 && countSetFlags(metadata) > operand) {
                result = 1;
            } else if (comparison == 2 && countSetFlags(metadata) < operand) {
                result = 1;
            } else {
                result = 0;
            }
        } else {
            result = isFlagEnabled(metadata, operand) ? 1 : 0;
        }
        if (multiply) {
            result *= multiplier;
        }
        if (negate) {
            result *= -1;
        }
        return result;
    }

    /**
     * The metadata → effects walk (getStatusEffects lines 396-441): every
     * registered effect's duration recipe evaluates over the flags; a
     * positive level names the effect, the amplifier recipe (flag 5)
     * names the level, the duration takes the
     * {@code 1200 * (i * 3 + (i - 1) * 2) >> amplifier} math with the
     * effect's own multiplier, and the splash shaves a quarter. The
     * reference's non-usable filter (the {@code isUsable} gate keeping
     * haste/resistance/etc. out of the potions) folds to the same recipe
     * tables — only the tabulated ids carry recipes, exactly the
     * reference's own registered set.
     *
     * @return the effect list (empty when the metadata is a plain
     * bottle), or null never — the engine's empty list replaces the
     * reference's null.
     */
    public static List<StatusEffectInstance> getStatusEffects(int metadata) {
        List<StatusEffectInstance> list = new ArrayList<>();
        for (int id = 1; id < 32; id++) {
            if (!StatusEffects.exists(id)) {
                continue;
            }
            String durationRecipe = DURATION_RECIPES.get(id);
            if (durationRecipe == null) {
                continue;
            }
            int level = parseRecipe(durationRecipe, 0, durationRecipe.length(), metadata);
            if (level <= 0) {
                continue;
            }
            int amplifier = 0;
            String amplifierRecipe = AMPLIFIER_RECIPES.get(id);
            if (amplifierRecipe != null) {
                amplifier = parseRecipe(amplifierRecipe, 0, amplifierRecipe.length(), metadata);
                if (amplifier < 0) {
                    amplifier = 0;
                }
            }
            int duration;
            if (StatusEffects.isInstant(id)) {
                duration = 1;
            } else {
                duration = 1200 * (level * 3 + (level - 1) * 2);
                duration >>= amplifier;
                duration = (int) Math.round(duration * StatusEffects.durationMultiplierOf(id));
                if ((metadata & SPLASH_BIT) != 0) {
                    duration = (int) Math.round(duration * 0.75 + 0.5);
                }
            }
            StatusEffectInstance instance = new StatusEffectInstance(id, duration, amplifier);
            if ((metadata & SPLASH_BIT) != 0) {
                instance.setSplash(true);
            }
            list.add(instance);
        }
        return list;
    }

    /**
     * The brewing step (applyIngredient lines 507-572): the ingredient
     * string's operators walk the flags ('+' sets, '-' clears, '!' toggles,
     * '&' requires else zeroes the whole metadata).
     */
    public static int applyIngredient(int metadata, String ingredient) {
        int operand = 0;
        int length = ingredient.length();
        boolean operandRead = false;
        boolean clear = false;
        boolean toggle = false;
        boolean require = false;
        for (int at = 0; at < length; at++) {
            char c = ingredient.charAt(at);
            if (c >= '0' && c <= '9') {
                operand *= 10;
                operand += c - '0';
                operandRead = true;
            } else if (c == '!' || c == '-' || c == '+' || c == '&') {
                if (operandRead) {
                    metadata = modifyMetadata(metadata, operand, clear, toggle, require);
                    require = false;
                    toggle = false;
                    clear = false;
                    operandRead = false;
                    operand = 0;
                }
                if (c == '!') {
                    toggle = true;
                } else if (c == '-') {
                    clear = true;
                } else if (c == '&') {
                    require = true;
                }
            }
        }
        if (operandRead) {
            metadata = modifyMetadata(metadata, operand, clear, toggle, require);
        }
        return metadata & 32767;
    }

    /** The single flag modification (modifyMetadata lines 443-461). */
    private static int modifyMetadata(int metadata, int flag, boolean clear,
                                      boolean toggle, boolean require) {
        if (require) {
            if (!isFlagEnabled(metadata, flag)) {
                return 0;
            }
        } else if (clear) {
            metadata &= ~(1 << flag);
        } else if (toggle) {
            if ((metadata & 1 << flag) == 0) {
                metadata |= 1 << flag;
            } else {
                metadata &= ~(1 << flag);
            }
        } else {
            metadata |= 1 << flag;
        }
        return metadata;
    }
}
