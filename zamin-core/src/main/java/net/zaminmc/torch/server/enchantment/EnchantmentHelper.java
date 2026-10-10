package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.entity.damage.DamageKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * The enchantment offer math (the historical {@code EnchantmentHelper}
 * selection half): the three table-slot level ladders, the enchantability
 * roll that bends the power, the weighted first pick and the compatibility
 * cull loop that grows multi-enchantment offers. Every constant and branch
 * order is transcribed from {@code reference/1.8.8/enchantment/
 * EnchantmentHelper.java} ({@code getRequiredXpLevel},
 * {@code getEnchantmentEntries}, {@code getAvailableEnchantmentEntries},
 * {@code addRandomEnchantment}) and {@code util/WeightedPicker.java}.
 */
public final class EnchantmentHelper {

    /** One offered enchantment (the historical EnchantmentEntry + its weight). */
    public record Offer(Enchantments.Entry enchantment, int level) {
        /** The WeightedPicker weight (the enchantment's rarity weight). */
        public int weight() {
            return enchantment.weight;
        }
    }

    /**
     * The table's three slot ladders ({@code getRequiredXpLevel}): the
     * enchantability-gated roll with the slot curve — slot 0 the cheap
     * third, slot 1 the two-thirds-plus-one, slot 2 the max(j, 2x bookshelf
     * cap). The 15-bookshelf cap rides in.
     */
    public static int requiredXpLevel(Random random, int slot, int bookshelfPower, ItemStack item) {
        int enchantability = Enchantments.enchantabilityOf(item);
        if (enchantability <= 0) {
            return 0;
        }
        int max = bookshelfPower;
        if (max > 15) {
            max = 15;
        }
        int j = random.nextInt(8) + 1 + (max >> 1) + random.nextInt(max + 1);
        if (slot == 0) {
            return Math.max(j / 3, 1);
        }
        if (slot == 1) {
            return j * 2 / 3 + 1;
        }
        return Math.max(j, max * 2);
    }

    /**
     * The full offer build ({@code getEnchantmentEntries}): the
     * enchantability roll bends the level budget, the weighted pick chooses
     * the first enchantment, and the halving loop culls incompatible
     * remnants and rolls again while {@code nextInt(50) <= budget}.
     *
     * @return the chosen offers, or null when nothing applies (the vanilla
     *         null: no list, no enchantment).
     */
    public static List<Offer> buildOffers(Random random, ItemStack item, int xpLevel) {
        int enchantability = Enchantments.enchantabilityOf(item);
        if (enchantability <= 0) {
            return null;
        }

        // The reference's i walk: halve, then two triangular rolls over the
        // quarter band, one-based.
        int i = enchantability / 2;
        i = 1 + random.nextInt((i >> 1) + 1) + random.nextInt((i >> 1) + 1);
        int j = i + xpLevel;
        // The +/-15% bend: two floats summed minus one, scaled.
        float f = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        int k = (int) (j * (1.0F + f) + 0.5F);
        if (k < 1) {
            k = 1;
        }

        List<Offer> list = null;
        Map<Integer, Offer> map = availableOffers(k, item);
        if (map != null && !map.isEmpty()) {
            Offer first = pick(random, map.values());
            if (first != null) {
                list = new ArrayList<>();
                list.add(first);
                final List<Offer> chosen = list;

                // The reference's loop: `for (int l = k; nextInt(50) <= l; l >>= 1)`.
                for (int l = k; random.nextInt(50) <= l; l >>= 1) {
                    // The cull: every id incompatible with the picks so far
                    // leaves the pool (the whole set goes — the iteration
                    // order cannot change the removed set).
                    map.keySet().removeIf(id -> {
                        Enchantments.Entry candidate = Enchantments.byId(id);
                        for (Offer offer : chosen) {
                            if (!offer.enchantment().isCompatible(candidate)) {
                                return true;
                            }
                        }
                        return false;
                    });

                    if (!map.isEmpty()) {
                        list.add(pick(random, map.values()));
                    }
                }
            }
        }

        return list;
    }

    /**
     * The level-gated offer pool ({@code getAvailableEnchantmentEntries}):
     * every enchantment the item can carry, at every level whose XP window
     * covers {@code xp}. Books take everything (the {@code flag} arm).
     * Keyed by id in id order — the engine's deterministic stand-in for the
     * reference's HashMap bucket walk (documented in the ledger: the pool
     * membership is exact, the second-pick roll mapping may differ from a
     * seed-matched vanilla run because the reference's values() order is
     * Java-8 HashMap bucket order).
     */
    public static Map<Integer, Offer> availableOffers(int xp, ItemStack item) {
        if (item.isEmpty()) {
            return null;
        }
        boolean book = item.type().identifier().toString().equals("minecraft:book");
        Map<Integer, Offer> map = null;
        for (Enchantments.Entry enchantment : Enchantments.all()) {
            if (book || enchantment.canEnchant(item, false)) {
                for (int level = 1; level <= enchantment.maxLevel; level++) {
                    if (xp >= enchantment.minXP(level) && xp <= enchantment.maxXP(level)) {
                        if (map == null) {
                            map = new TreeMap<>();
                        }
                        map.put(enchantment.id, new Offer(enchantment, level));
                    }
                }
            }
        }
        return map;
    }

    /**
     * The anvil/book random enchant ({@code addRandomEnchantment}): the
     * offers build, every one lands on the item (the book's enchantment
     * conversion lives with the caller — the engine has no enchanted-book
     * item type yet).
     *
     * @return the enchanted stack (the same value when nothing applied).
     */
    public static ItemStack addRandomEnchantment(Random random, ItemStack item, int xpLevel) {
        List<Offer> offers = buildOffers(random, item, xpLevel);
        if (offers == null) {
            return item;
        }
        ItemStack enchanted = item;
        for (Offer offer : offers) {
            enchanted = enchanted.withEnchantment(offer.enchantment().id, offer.level());
        }
        return enchanted;
    }

    /** The WeightedPicker walk ({@code pick}: roll the total, subtract per entry). */
    private static Offer pick(Random random, Iterable<Offer> offers) {
        int total = 0;
        for (Offer offer : offers) {
            total += offer.weight();
        }
        if (total <= 0) {
            throw new IllegalArgumentException("Offer pool weight must be positive: " + total);
        }
        int roll = random.nextInt(total);
        for (Offer offer : offers) {
            roll -= offer.weight();
            if (roll < 0) {
                return offer;
            }
        }
        return null; // unreachable: the roll is inside the total
    }

    /** @return the summed extra damage of the weapon's damage enchantments. */
    public static float modifyDamage(ItemStack weapon, int targetMobType) {
        float damage = 0.0F;
        if (weapon.enchantments() == null) {
            return 0.0F;
        }
        for (Map.Entry<Integer, Integer> ench : weapon.enchantments().entrySet()) {
            Enchantments.Entry enchantment = Enchantments.byId(ench.getKey());
            if (enchantment != null && enchantment.family == Enchantments.Family.DAMAGE) {
                damage += extraDamage(enchantment.subtype, ench.getValue(), targetMobType);
            }
        }
        return damage;
    }

    /** The DamageEnchantment.getExtraDamage table. */
    public static float extraDamage(int target, int level, int targetMobType) {
        if (target == Enchantments.TARGET_ALL) {
            return level * 1.25F;
        }
        if (target == Enchantments.TARGET_UNDEAD && targetMobType == 1) {
            return level * 2.5F;
        }
        if (target == Enchantments.TARGET_ARTHROPODS && targetMobType == 2) {
            return level * 2.5F;
        }
        return 0.0F;
    }

    // ------------------------------------------------------------- effect hooks
    //
    // The consumption half of the historical EnchantmentHelper (the level
    // readers) plus the ProtectionEnchantment arithmetic. Every constant and
    // branch order below is transcribed from reference/1.8.8:
    // EnchantmentHelper.getLevel / getHighestEnchantmentLevel /
    // modifyProtection / getKnockbackLevel / getFireAspectLevel /
    // getEfficiencyLevel, ProtectionEnchantment.getExtraProtection /
    // modifyOnFireTimer / modifyExplosionDamage.

    /** The historical {@code getLevel}: the stack's level of one enchantment id (0 when absent). */
    public static int level(ItemStack item, int id) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        Map<Integer, Integer> enchantments = item.enchantments();
        if (enchantments == null) {
            return 0;
        }
        return enchantments.getOrDefault(id, 0);
    }

    /** The historical {@code getHighestEnchantmentLevel} over the equipment array. */
    public static int highestLevel(int id, ItemStack[] items) {
        if (items == null) {
            return 0;
        }
        int highest = 0;
        for (ItemStack item : items) {
            int candidate = level(item, id);
            if (candidate > highest) {
                highest = candidate;
            }
        }
        return highest;
    }

    /** The historical {@code getKnockbackLevel}: the held item's Knockback. */
    public static int knockbackLevel(ItemStack held) {
        return level(held, Enchantments.KNOCKBACK.id);
    }

    /** The historical {@code getFireAspectLevel}: the held item's Fire Aspect. */
    public static int fireAspectLevel(ItemStack held) {
        return level(held, Enchantments.FIRE_ASPECT.id);
    }

    /** The historical {@code getEfficiencyLevel}: the held item's Efficiency. */
    public static int efficiencyLevel(ItemStack held) {
        return level(held, Enchantments.EFFICIENCY.id);
    }

    /**
     * The per-piece protection contribution ({@code ProtectionEnchantment.
     * getExtraProtection}): the (6 + level^2)/3 curve scaled per kind —
     * all 0.75, fire 1.25, fall 2.5, blast and projectile 1.5 — floored
     * per branch, zero off-kind and zero in the void.
     */
    public static int extraProtection(int subtype, int level, DamageKind kind) {
        if (kind.isOutOfWorld()) {
            return 0;
        }
        float f = (6 + level * level) / 3.0F;
        if (subtype == Enchantments.PROTECTION_ALL) {
            return floor(f * 0.75F);
        }
        if (subtype == Enchantments.PROTECTION_FIRE && kind.isFire()) {
            return floor(f * 1.25F);
        }
        if (subtype == Enchantments.PROTECTION_FALL && kind.isFall()) {
            return floor(f * 2.5F);
        }
        if (subtype == Enchantments.PROTECTION_BLAST && kind.isExplosive()) {
            return floor(f * 1.5F);
        }
        if (subtype == Enchantments.PROTECTION_PROJECTILE && kind.isProjectile()) {
            return floor(f * 1.5F);
        }
        return 0;
    }

    /**
     * The historical {@code modifyProtection}: the armor pieces' protection
     * sum clamped to 0..25, then the vanilla half-to-full roll —
     * {@code (p + 1 >> 1) + nextInt((p >> 1) + 1)} — the legacy random
     * discount that makes the envelope land anywhere from half the
     * protection up to all of it.
     */
    public static int modifyProtection(ItemStack[] armor, DamageKind kind, Random random) {
        int protection = 0;
        if (armor != null) {
            for (ItemStack piece : armor) {
                if (piece == null || piece.isEmpty() || piece.enchantments() == null) {
                    continue;
                }
                for (Map.Entry<Integer, Integer> ench : piece.enchantments().entrySet()) {
                    Enchantments.Entry enchantment = Enchantments.byId(ench.getKey());
                    if (enchantment != null && enchantment.family == Enchantments.Family.PROTECTION) {
                        protection += extraProtection(enchantment.subtype, ench.getValue(), kind);
                    }
                }
            }
        }
        if (protection > 25) {
            protection = 25;
        } else if (protection < 0) {
            protection = 0;
        }
        return (protection + 1 >> 1) + random.nextInt((protection >> 1) + 1);
    }

    /**
     * The historical {@code ProtectionEnchantment.modifyOnFireTimer}: the
     * highest Fire Protection on the equipment shortens the burn —
     * {@code ticks -= floor(ticks * (level * 0.15))} per the reference walk.
     * The input is ticks (the reference converts seconds × 20 before the call).
     */
    public static int modifyOnFireTimer(ItemStack[] armor, int ticks) {
        int level = highestLevel(Enchantments.FIRE_PROTECTION.id, armor);
        if (level > 0) {
            ticks -= floor(ticks * (level * 0.15F));
        }
        return ticks;
    }

    /**
     * The historical {@code ProtectionEnchantment.modifyExplosionDamage}: the
     * highest Blast Protection on the equipment shaves
     * {@code floor(damage * (level * 0.15))} off the raw blast number before
     * the ordinary damage pipeline sees it.
     */
    public static double modifyExplosionDamage(ItemStack[] armor, double damage) {
        int level = highestLevel(Enchantments.BLAST_PROTECTION.id, armor);
        if (level > 0) {
            damage -= floor(damage * (level * 0.15F));
        }
        return damage;
    }

    /** The reference MathHelper.floor (the int cast of the double floor). */
    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private EnchantmentHelper() {
    }
}
