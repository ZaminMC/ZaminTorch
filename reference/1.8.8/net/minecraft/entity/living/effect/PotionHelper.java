package net.minecraft.entity.living.effect;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.client.util.IntegerBuffer;

public class PotionHelper {
    public static final String NONE = null;
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
    private static final Map<Integer, String> DURATION_RECIPES = Maps.newHashMap();
    private static final Map<Integer, String> AMPLIFIER_RECIPES = Maps.newHashMap();
    private static final Map<Integer, Integer> COLOR_CACHE = Maps.newHashMap();
    private static final String[] PREFIXES = new String[]{
        "potion.prefix.mundane",
        "potion.prefix.uninteresting",
        "potion.prefix.bland",
        "potion.prefix.clear",
        "potion.prefix.milky",
        "potion.prefix.diffuse",
        "potion.prefix.artless",
        "potion.prefix.thin",
        "potion.prefix.awkward",
        "potion.prefix.flat",
        "potion.prefix.bulky",
        "potion.prefix.bungling",
        "potion.prefix.buttered",
        "potion.prefix.smooth",
        "potion.prefix.suave",
        "potion.prefix.debonair",
        "potion.prefix.thick",
        "potion.prefix.elegant",
        "potion.prefix.fancy",
        "potion.prefix.charming",
        "potion.prefix.dashing",
        "potion.prefix.refined",
        "potion.prefix.cordial",
        "potion.prefix.sparkling",
        "potion.prefix.potent",
        "potion.prefix.foul",
        "potion.prefix.odorless",
        "potion.prefix.rank",
        "potion.prefix.harsh",
        "potion.prefix.acrid",
        "potion.prefix.gross",
        "potion.prefix.stinky"
    };

    public static boolean isFlagEnabled(int metadata, int flag) {
        return (metadata & 1 << flag) != 0;
    }

    private static int getFlag(int metadata, int flag) {
        return isFlagEnabled(metadata, flag) ? 1 : 0;
    }

    private static int getFlagInverse(int metadata, int flag) {
        return isFlagEnabled(metadata, flag) ? 0 : 1;
    }

    public static int getPrefixIndex(int metadata) {
        return buildPotionId(metadata, 5, 4, 3, 2, 1);
    }

    public static int getColor(Collection<StatusEffectInstance> effects) {
        int i = 3694022;
        if (effects != null && !effects.isEmpty()) {
            float f = 0.0F;
            float f1 = 0.0F;
            float f2 = 0.0F;
            float f3 = 0.0F;

            for (StatusEffectInstance statuseffectinstance : effects) {
                if (statuseffectinstance.hasParticles()) {
                    int j = StatusEffect.BY_ID[statuseffectinstance.getId()].getPotionColor();

                    for (int k = 0; k <= statuseffectinstance.getAmplifier(); k++) {
                        f += (j >> 16 & 0xFF) / 255.0F;
                        f1 += (j >> 8 & 0xFF) / 255.0F;
                        f2 += (j >> 0 & 0xFF) / 255.0F;
                        f3++;
                    }
                }
            }

            if (f3 == 0.0F) {
                return 0;
            }

            f = f / f3 * 255.0F;
            f1 = f1 / f3 * 255.0F;
            f2 = f2 / f3 * 255.0F;
            return (int)f << 16 | (int)f1 << 8 | (int)f2;
        } else {
            return i;
        }
    }

    public static boolean isAllAmbient(Collection<StatusEffectInstance> effects) {
        for (StatusEffectInstance statuseffectinstance : effects) {
            if (!statuseffectinstance.isAmbient()) {
                return false;
            }
        }

        return true;
    }

    public static int getColor(int metadata, boolean custom) {
        Integer integer = IntegerBuffer.get(metadata);
        if (!custom) {
            if (COLOR_CACHE.containsKey(integer)) {
                return COLOR_CACHE.get(integer);
            }

            int i = getColor(getStatusEffects(integer, false));
            COLOR_CACHE.put(integer, i);
            return i;
        } else {
            return getColor(getStatusEffects(integer, true));
        }
    }

    public static String getPrefix(int metadata) {
        int i = getPrefixIndex(metadata);
        return PREFIXES[i];
    }

    private static int evaluateMetadata(boolean invert, boolean multiply, boolean negate, int operator, int operand, int multiplier, int metadata) {
        int i = 0;
        if (invert) {
            i = getFlagInverse(metadata, operand);
        } else if (operator != -1) {
            if (operator == 0 && countSetFlags(metadata) == operand) {
                i = 1;
            } else if (operator == 1 && countSetFlags(metadata) > operand) {
                i = 1;
            } else if (operator == 2 && countSetFlags(metadata) < operand) {
                i = 1;
            }
        } else {
            i = getFlag(metadata, operand);
        }

        if (multiply) {
            i *= multiplier;
        }

        if (negate) {
            i *= -1;
        }

        return i;
    }

    private static int countSetFlags(int metadata) {
        int i;
        for (i = 0; metadata > 0; i++) {
            metadata &= metadata - 1;
        }

        return i;
    }

    /**
     * Parses the given potion using the given recipe between the given bounds, then returns
     * a number based on which conditions of the recipe are met.
     * <p>
     * The recipe string encodes how to parse the potion by reading specific "flags" on the
     * item metadata. A flag {@code n} represents the {@code n}th least significant bit in
     * the item metadata.
     * <p>
     * The recipe string reads like an expression: a series of sub-expressions and '|' (OR)
     * and '&' (AND) operators. It is evaluated from left to right, and the operators act
     * similarly to their boolean operator equivalents.
     * <br>- '|': evaluates to the preceding expression if that evaluates to more than
     * {@code 0}, otherwise evaluates to the succeeding expression
     * <br>- '&': evaluates to {@code 0} if either the preceding or succeeding expression
     * evaluates to {@code 0}, otherwise evaluates to the largest between the two, picking
     * the second in case they are equal
     * <p>
     * The sub-expressions are also evaluated from left to right, as a series of
     * statements, where each statement is an operator-number pair. Each statement is
     * evaluated independently, and the values of all statements added together give the
     * value of the entire sub-expression. Any operator without a number directly following
     * it is ignored.
     * <p>
     * There are three operators that interpret the following number as a flag:
     * <br>- '+': evaluates to {@code 1} if the flag is set in the metadata, otherwise
     * {@code 0} (can be implicit for the first statement of a sub-expression)
     * <br>- '-': evaluates to {@code -1} if the flag is set in the metadata, otherwise
     * {@code 0}
     * <br>- '!': evaluates to {@code 0} if the flag is set in the metadata, otherwise
     * {@code 1}
     * <p>
     * There are three comparison operators:
     * <br>- '=': evaluates to {@code 1} if the number of set flags in the metadata is
     * equal to the operand, otherwise {@code 0}
     * <br>- '>': evaluates to {@code 1} if the number of set flags in the metadata is
     * larger than the operand, otherwise {@code 0}
     * <br>- '<': evaluates to {@code 1} if the number of set flags in the metadata is
     * less than the operand, otherwise {@code 0}
     * <p>
     * Lastly, there is the '*' operator, which multiplies the preceding statement by the
     * following number.
     * <p>
     * As an example, let's parse the following recipe
     * <br> {@code "0 & +1 & -2 & !3 & 0-1!2*3 & =2 & >3 | <4"}
     * <br> on the following metadata {@code 0b0011} (truncated to four bits for
     * readability).
     * <p>
     * First we break the expression up into sub-expressions, and evaluate those.
     * <p>
     * {@code "0"} evaluates to {@code 1}, since the {@code 0} flag is set.
     * <p>
     * {@code "+1"} evaluates to {@code 1}, since the {@code 1} flag is set.
     * <p>
     * {@code "-2"} evaluates to {@code 0}, since the {@code 2} flag is not set.
     * <p>
     * {@code "!3"} evaluates to {@code 1}, since the {@code 3} flag is not set.
     * <p>
     * {@code "0-1!2*3"} breaks up into four statements:
     * <br> {@code "0"} evaluates to {@code 1}, since the {@code 0} flag is set.
     * <br> - {@code "-1"} evaluates to {@code -1}, since the {@code 1} flag is set.
     * <br> - {@code "!2"} evaluates to {@code 1}, since the {@code 2} flag is not set.
     * <br> - {@code "*3"} multiplies the previous statement by {@code 3}.
     * <br> - {@code "0-1!2*3"} then evaluates to {@code 1 - 1 + 1*3 = 3}.
     * <p>
     * {@code "=2"} evaluates to {@code 1}, since exactly two flags are set.
     * <p>
     * {@code ">3"} evaluates to {@code 0}, since not more than three flags are set.
     * <p>
     * {@code "<4"} evaluates to {@code 1}, since less than four flags are set.
     * <p>
     * Then parsing the '|' and '&' operators, we can insert brackets to make clear how
     * the expression is evaluated:
     * <br> {@code "0 & (+1 & (-2 & (!3 & (0-1!2*3 & (=2 & (>3 | <4))))))"}
     * <br> Working through the brackets from inside out, expression evaluates like so:
     * <p>
     * {@code ">3 | <4"} evaluates to {@code "<4"}, since {@code ">3"} evaluates to {@code 0}.
     * <p>
     * {@code "=2 & <4"} evaluates to {@code <4}, since {@code "=2"} and {@code "<4"} both
     * evaluate to {@code 1}.
     * <p>
     * {@code "0-1!2*3 & <4"} evaluates to {@code "0-1!2*3"}, since that evaluates to
     * {@code 3}, whereas {@code "<4"} evaluates to {@code 1}. Neither result is {@code 0},
     * and {@code 3 > 1}.
     * <p>
     * {@code "!3 & 0-1!2*3"} evaluates to {@code "0-1!2*3"}, since that evaluates to
     * {@code 3}, whereas {@code "!3"} evaluates to {@code 1}. Neither result is {@code 0},
     * and {@code 1 > 3}.
     * <p>
     * {@code "-2 & 0-1!2*3"} evaluates to {@code 0}, since {@code "-2"} evaluates to
     * {@code 0}.
     * <p> Since the remaining operations are all '&', the entire expression evaluates to
     * {@code 0}.
     */
    private static int parseRecipe(String recipe, int from, int to, int metadata) {
        if (from < recipe.length() && to >= 0 && from < to) {
            int i = recipe.indexOf(124, from);
            if (i >= 0 && i < to) {
                int l1 = parseRecipe(recipe, from, i - 1, metadata);
                if (l1 > 0) {
                    return l1;
                }

                int j2 = parseRecipe(recipe, i + 1, to, metadata);
                return j2 > 0 ? j2 : 0;
            } else {
                int j = recipe.indexOf(38, from);
                if (j >= 0 && j < to) {
                    int i2 = parseRecipe(recipe, from, j - 1, metadata);
                    if (i2 <= 0) {
                        return 0;
                    } else {
                        int k2 = parseRecipe(recipe, j + 1, to, metadata);
                        if (k2 <= 0) {
                            return 0;
                        } else {
                            return i2 > k2 ? i2 : k2;
                        }
                    }
                } else {
                    boolean flag = false;
                    boolean flag1 = false;
                    boolean flag2 = false;
                    boolean flag3 = false;
                    boolean flag4 = false;
                    int k = -1;
                    int l = 0;
                    int i1 = 0;
                    int j1 = 0;

                    for (int k1 = from; k1 < to; k1++) {
                        char c0 = recipe.charAt(k1);
                        if (c0 >= '0' && c0 <= '9') {
                            if (flag) {
                                i1 = c0 - '0';
                                flag1 = true;
                            } else {
                                l *= 10;
                                l += c0 - '0';
                                flag2 = true;
                            }
                        } else if (c0 == '*') {
                            flag = true;
                        } else if (c0 == '!') {
                            if (flag2) {
                                j1 += evaluateMetadata(flag3, flag1, flag4, k, l, i1, metadata);
                                flag3 = false;
                                flag4 = false;
                                flag = false;
                                flag1 = false;
                                flag2 = false;
                                i1 = 0;
                                l = 0;
                                k = -1;
                            }

                            flag3 = true;
                        } else if (c0 == '-') {
                            if (flag2) {
                                j1 += evaluateMetadata(flag3, flag1, flag4, k, l, i1, metadata);
                                flag3 = false;
                                flag4 = false;
                                flag = false;
                                flag1 = false;
                                flag2 = false;
                                i1 = 0;
                                l = 0;
                                k = -1;
                            }

                            flag4 = true;
                        } else if (c0 != '=' && c0 != '<' && c0 != '>') {
                            if (c0 == '+' && flag2) {
                                j1 += evaluateMetadata(flag3, flag1, flag4, k, l, i1, metadata);
                                flag3 = false;
                                flag4 = false;
                                flag = false;
                                flag1 = false;
                                flag2 = false;
                                i1 = 0;
                                l = 0;
                                k = -1;
                            }
                        } else {
                            if (flag2) {
                                j1 += evaluateMetadata(flag3, flag1, flag4, k, l, i1, metadata);
                                flag3 = false;
                                flag4 = false;
                                flag = false;
                                flag1 = false;
                                flag2 = false;
                                i1 = 0;
                                l = 0;
                                k = -1;
                            }

                            if (c0 == '=') {
                                k = 0;
                            } else if (c0 == '<') {
                                k = 2;
                            } else if (c0 == '>') {
                                k = 1;
                            }
                        }
                    }

                    if (flag2) {
                        j1 += evaluateMetadata(flag3, flag1, flag4, k, l, i1, metadata);
                    }

                    return j1;
                }
            }
        } else {
            return 0;
        }
    }

    public static List<StatusEffectInstance> getStatusEffects(int metadata, boolean custom) {
        List<StatusEffectInstance> list = null;

        for (StatusEffect statuseffect : StatusEffect.BY_ID) {
            if (statuseffect != null && (!statuseffect.isUsable() || custom)) {
                String s = DURATION_RECIPES.get(statuseffect.getId());
                if (s != null) {
                    int i = parseRecipe(s, 0, s.length(), metadata);
                    if (i > 0) {
                        int j = 0;
                        String s1 = AMPLIFIER_RECIPES.get(statuseffect.getId());
                        if (s1 != null) {
                            j = parseRecipe(s1, 0, s1.length(), metadata);
                            if (j < 0) {
                                j = 0;
                            }
                        }

                        if (statuseffect.isInstant()) {
                            i = 1;
                        } else {
                            i = 1200 * (i * 3 + (i - 1) * 2);
                            i >>= j;
                            i = (int)Math.round(i * statuseffect.getDurationMultiplier());
                            if ((metadata & 16384) != 0) {
                                i = (int)Math.round(i * 0.75 + 0.5);
                            }
                        }

                        if (list == null) {
                            list = Lists.newArrayList();
                        }

                        StatusEffectInstance statuseffectinstance = new StatusEffectInstance(statuseffect.getId(), i, j);
                        if ((metadata & 16384) != 0) {
                            statuseffectinstance.setSplash(true);
                        }

                        list.add(statuseffectinstance);
                    }
                }
            }
        }

        return list;
    }

    private static int modifyMetadata(int metadata, int flag, boolean disable, boolean toggle, boolean require) {
        if (require) {
            if (!isFlagEnabled(metadata, flag)) {
                return 0;
            }
        } else if (disable) {
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

    /**
     * Performs the next step in a brewing recipe by applying the given ingredient to the
     * given potion item metadata.
     * <p>
     * The ingredient string encodes how to modify the potion through operations on the item
     * metadata. This is mostly done through operations on specific "flags". A flag {@code n}
     * represents the {@code n}th least significant bit in the item metadata.
     * <p>
     * The ingredient string is evaluated from left to right, as a series of statements,
     * where each statement is an operator-flag pair. Numbers represent flags, while non-
     * digit characters represent operators. Any operator without a number directly following
     * it is ignored.
     * <p>
     * There are four operators ingredients can use:
     * <br>- '+': enables the flag
     * <br>- '-': disables the flag
     * <br>- '!': toggles the flag
     * <br>- '&': requires the flag to be enabled, clears the entire item metadata otherwise
     * <p>
     * While the '+', '-', and '!' operators only modify single flags, the '&' operator can
     * modify the whole item metadata. This means an '&' operation can override any other
     * operations preceding it.
     * <p>
     * As an example, let's apply the following ingredient
     * <br> {@code "+0-1!2&3+1-2!3"}
     * <br> on the following metadata {@code 0b0011} (truncated to four bits for
     * readability).
     * <p>
     * We break the expression into its component statements and apply them one by one.
     * <p>
     * {@code "+0"} enables the {@code 0} flag, metadata is now {@code 0b0011}.
     * <p>
     * {@code "-1"} disables the {@code 1} flag, metadata is now {@code 0b0001}.
     * <p>
     * {@code "!2"} toggles the {@code 2} flag, metadata is now {@code 0b0101}.
     * <p>
     * {@code "&3"} requires the {@code 3} flag, metadata is now {@code 0b0000}.
     * <p>
     * {@code "+1"} enables the {@code 1} flag, metadata is now {@code 0b0010}.
     * <p>
     * {@code "-2"} disables the {@code 2} flag, metadata is now {@code 0b0010}.
     * <p>
     * {@code "!3"} toggles the {@code 3} flag, metadata is now {@code 0b0110}.
     */
    public static int applyIngredient(int metadata, String ingredient) {
        int i = 0;
        int j = ingredient.length();
        boolean flag = false;
        boolean flag1 = false;
        boolean flag2 = false;
        boolean flag3 = false;
        int k = 0;

        for (int l = i; l < j; l++) {
            char c0 = ingredient.charAt(l);
            if (c0 >= '0' && c0 <= '9') {
                k *= 10;
                k += c0 - '0';
                flag = true;
            } else if (c0 == '!') {
                if (flag) {
                    metadata = modifyMetadata(metadata, k, flag2, flag1, flag3);
                    flag3 = false;
                    flag1 = false;
                    flag2 = false;
                    flag = false;
                    k = 0;
                }

                flag1 = true;
            } else if (c0 == '-') {
                if (flag) {
                    metadata = modifyMetadata(metadata, k, flag2, flag1, flag3);
                    flag3 = false;
                    flag1 = false;
                    flag2 = false;
                    flag = false;
                    k = 0;
                }

                flag2 = true;
            } else if (c0 == '+') {
                if (flag) {
                    metadata = modifyMetadata(metadata, k, flag2, flag1, flag3);
                    flag3 = false;
                    flag1 = false;
                    flag2 = false;
                    flag = false;
                    k = 0;
                }
            } else if (c0 == '&') {
                if (flag) {
                    metadata = modifyMetadata(metadata, k, flag2, flag1, flag3);
                    flag3 = false;
                    flag1 = false;
                    flag2 = false;
                    flag = false;
                    k = 0;
                }

                flag3 = true;
            }
        }

        if (flag) {
            metadata = modifyMetadata(metadata, k, flag2, flag1, flag3);
        }

        return metadata & 32767;
    }

    public static int buildPotionId(int metadata, int flag1, int flag2, int flag3, int flag4, int flag5) {
        return (isFlagEnabled(metadata, flag1) ? 16 : 0)
            | (isFlagEnabled(metadata, flag2) ? 8 : 0)
            | (isFlagEnabled(metadata, flag3) ? 4 : 0)
            | (isFlagEnabled(metadata, flag4) ? 2 : 0)
            | (isFlagEnabled(metadata, flag5) ? 1 : 0);
    }

    static {
        DURATION_RECIPES.put(StatusEffect.REGENERATION.getId(), "0 & !1 & !2 & !3 & 0+6");
        DURATION_RECIPES.put(StatusEffect.SPEED.getId(), "!0 & 1 & !2 & !3 & 1+6");
        DURATION_RECIPES.put(StatusEffect.FIRE_RESISTANCE.getId(), "0 & 1 & !2 & !3 & 0+6");
        DURATION_RECIPES.put(StatusEffect.INSTANT_HEALTH.getId(), "0 & !1 & 2 & !3");
        DURATION_RECIPES.put(StatusEffect.POISON.getId(), "!0 & !1 & 2 & !3 & 2+6");
        DURATION_RECIPES.put(StatusEffect.WEAKNESS.getId(), "!0 & !1 & !2 & 3 & 3+6");
        DURATION_RECIPES.put(StatusEffect.INSTANT_DAMAGE.getId(), "!0 & !1 & 2 & 3");
        DURATION_RECIPES.put(StatusEffect.SLOWNESS.getId(), "!0 & 1 & !2 & 3 & 3+6");
        DURATION_RECIPES.put(StatusEffect.STRENGTH.getId(), "0 & !1 & !2 & 3 & 3+6");
        DURATION_RECIPES.put(StatusEffect.NIGHTVISION.getId(), "!0 & 1 & 2 & !3 & 2+6");
        DURATION_RECIPES.put(StatusEffect.INVISIBILITY.getId(), "!0 & 1 & 2 & 3 & 2+6");
        DURATION_RECIPES.put(StatusEffect.WATER_BREATHING.getId(), "0 & !1 & 2 & 3 & 2+6");
        DURATION_RECIPES.put(StatusEffect.JUMP_BOOST.getId(), "0 & 1 & !2 & 3 & 3+6");
        AMPLIFIER_RECIPES.put(StatusEffect.SPEED.getId(), "5");
        AMPLIFIER_RECIPES.put(StatusEffect.HASTE.getId(), "5");
        AMPLIFIER_RECIPES.put(StatusEffect.STRENGTH.getId(), "5");
        AMPLIFIER_RECIPES.put(StatusEffect.REGENERATION.getId(), "5");
        AMPLIFIER_RECIPES.put(StatusEffect.INSTANT_DAMAGE.getId(), "5");
        AMPLIFIER_RECIPES.put(StatusEffect.INSTANT_HEALTH.getId(), "5");
        AMPLIFIER_RECIPES.put(StatusEffect.RESISTANCE.getId(), "5");
        AMPLIFIER_RECIPES.put(StatusEffect.POISON.getId(), "5");
        AMPLIFIER_RECIPES.put(StatusEffect.JUMP_BOOST.getId(), "5");
    }
}
