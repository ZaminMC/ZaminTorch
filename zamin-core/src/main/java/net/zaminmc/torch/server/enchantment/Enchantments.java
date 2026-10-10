package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.item.Armor;
import net.zaminmc.torch.server.item.ToolClass;
import net.zaminmc.torch.server.item.ToolMaterial;
import net.zaminmc.torch.server.item.ToolSpec;
import net.zaminmc.torch.server.item.Tools;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The 1.8.8 enchantment registry (the historical {@code Enchantment} static
 * block): the 26 ids, their rarity weights ({@code getType()} — the weight
 * the offer picker rolls), their categories and their enchantability curves.
 * Every number below is transcribed from {@code reference/1.8.8/enchantment}
 * — the min/max XP requirement tables ride the per-family classes there
 * ({@code ProtectionEnchantment}, {@code DamageEnchantment}, and the singles).
 */
public final class Enchantments {

    // ------------------------------------------------ the categories

    /**
     * The historical {@code EnchantmentCategory}: which items an enchantment
     * may land on, mapped onto the engine's item model (tools via
     * {@link Tools#specOf}, armor via {@link Armor#specOf}, durability via
     * {@code maxDurability}).
     */
    public enum Category {
        ALL,
        ARMOR,
        ARMOR_FEET,
        ARMOR_LEGS,
        ARMOR_TORSO,
        ARMOR_HEAD,
        WEAPON,
        DIGGER,
        FISHING_ROD,
        BREAKABLE,
        BOW;

        /** The historical canEnchant (the reference's instanceof ladder). */
        public boolean canEnchant(ItemType item) {
            if (this == ALL) {
                return true;
            }
            if (this == BREAKABLE && item.maxDurability() > 0) {
                return true;
            }
            Optional<Armor.Spec> armor = Armor.specOf(item);
            if (armor.isPresent()) {
                Armor.Slot slot = armor.orElseThrow().slot();
                return switch (this) {
                    case ARMOR -> true;
                    case ARMOR_HEAD -> slot == Armor.Slot.HEAD;
                    case ARMOR_TORSO -> slot == Armor.Slot.CHEST;
                    case ARMOR_LEGS -> slot == Armor.Slot.LEGS;
                    case ARMOR_FEET -> slot == Armor.Slot.FEET;
                    default -> false;
                };
            }
            Optional<ToolSpec> tool = Tools.specOf(item);
            if (tool.isPresent()) {
                ToolClass toolClass = tool.orElseThrow().toolClass();
                return switch (this) {
                    case WEAPON -> toolClass == ToolClass.SWORD;
                    case DIGGER -> toolClass != ToolClass.SWORD;
                    default -> false;
                };
            }
            String id = item.identifier().toString();
            return switch (this) {
                case BOW -> id.equals("minecraft:bow");
                case FISHING_ROD -> id.equals("minecraft:fishing_rod");
                default -> false;
            };
        }
    }

    /** One registered enchantment (the reference's Enchantment fields). */
    public static final class Entry {
        public final int id;
        public final String key;
        /** The rarity weight ({@code getType()}) the WeightedPicker rolls. */
        public final int weight;
        public final Category category;
        public final int maxLevel;
        /** The family drives the min/max XP curves (the reference's tables). */
        public final Family family;
        /** The subtype inside the family (protection kind, damage target). */
        public final int subtype;

        Entry(int id, String key, int weight, Category category, int maxLevel,
              Family family, int subtype) {
            this.id = id;
            this.key = key;
            this.weight = weight;
            this.category = category;
            this.maxLevel = maxLevel;
            this.family = family;
            this.subtype = subtype;
        }

        /** The historical getMinXpRequirement (the per-family tables). */
        public int minXP(int level) {
            return family.minXP(subtype, level);
        }

        /** The historical getMaxXpRequirement. */
        public int maxXP(int level) {
            return family.maxXP(subtype, level);
        }

        /** The historical isCompatible (the family conflicts). */
        public boolean isCompatible(Entry other) {
            return family.isCompatible(this, other);
        }

        /** Whether the kind may carry the enchantment (canEnchant + the axe arm). */
        public boolean canEnchant(ItemStack item, boolean axeCounts) {
            if (item.isEmpty()) {
                return false;
            }
            if (axeCounts && family == Family.DAMAGE) {
                Optional<ToolSpec> tool = Tools.specOf(item.type());
                if (tool.isPresent() && tool.orElseThrow().toolClass() == ToolClass.AXE) {
                    return true; // the DamageEnchantment axe arm
                }
            }
            return category.canEnchant(item.type());
        }
    }

    // ------------------------------------------------ the XP curves

    /**
     * The per-family min/max XP requirement curves, transcribed from the
     * reference subclasses (each family's {@code getMinXpRequirement}).
     */
    public enum Family {
        /** The base Enchantment curve: min 1 + level*10, max min+5 (the singles' fallback). */
        BASE {
            @Override int minXP(int subtype, int level) {
                return 1 + level * 10;
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + 5;
            }
        },
        /** ProtectionEnchantment: MIN {1,10,5,5,3}, MOD {11,8,6,8,6}, MAX {20,12,10,12,15}. */
        PROTECTION {
            private static final int[] MIN = {1, 10, 5, 5, 3};
            private static final int[] MOD = {11, 8, 6, 8, 6};
            private static final int[] MAX = {20, 12, 10, 12, 15};
            @Override int minXP(int subtype, int level) {
                return MIN[subtype] + (level - 1) * MOD[subtype];
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + MAX[subtype];
            }
            @Override int maxLevel() {
                return 4;
            }
            @Override boolean isCompatible(Entry self, Entry other) {
                if (other.family == PROTECTION) {
                    // The reference rule: different types, and feather-falling
                    // (type 2) pairs with any other protection kind.
                    return other.subtype != self.subtype
                            && (self.subtype == 2 || other.subtype == 2);
                }
                return true;
            }
        },
        /** DamageEnchantment: MIN {1,5,5}, MOD {11,8,8}, MAX {20,20,20}; all mutually exclusive. */
        DAMAGE {
            private static final int[] MIN = {1, 5, 5};
            private static final int[] MOD = {11, 8, 8};
            private static final int[] MAX = {20, 20, 20};
            @Override int minXP(int subtype, int level) {
                return MIN[subtype] + (level - 1) * MOD[subtype];
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + MAX[subtype];
            }
            @Override int maxLevel() {
                return 5;
            }
            @Override boolean isCompatible(Entry self, Entry other) {
                return other.family != DAMAGE;
            }
        },
        /** Knockback: min 5 + 20*(l-1), max min+50, level 2. */
        KNOCKBACK {
            @Override int minXP(int subtype, int level) {
                return 5 + 20 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 2;
            }
        },
        /** Fire aspect: min 10 + 20*(l-1), max min+50, level 2. */
        FIRE_ASPECT {
            @Override int minXP(int subtype, int level) {
                return 10 + 20 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 2;
            }
        },
        /** Efficiency: min 1 + 10*(l-1), max min+50, level 5. */
        EFFICIENCY {
            @Override int minXP(int subtype, int level) {
                return 1 + 10 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return BASE.minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 5;
            }
        },
        /** Silk touch: flat 15..65, level 1; conflicts with fortune. */
        SILK_TOUCH {
            @Override int minXP(int subtype, int level) {
                return 15;
            }
            @Override int maxXP(int subtype, int level) {
                return BASE.minXP(subtype, level) + 50;
            }
            @Override boolean isCompatible(Entry self, Entry other) {
                // Fortune is id 35 (the reference compares SILK_TOUCH.id);
                // the numeric literal avoids the registry's forward reference.
                return other.id != 35;
            }
        },
        /** Unbreaking: min 5 + 8*(l-1), max min+50, level 3. */
        UNBREAKING {
            @Override int minXP(int subtype, int level) {
                return 5 + 8 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return BASE.minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 3;
            }
        },
        /** Fortune (lootBonusDigger): min 15 + 9*(l-1), max min+50, level 3. */
        FORTUNE {
            @Override int minXP(int subtype, int level) {
                return 15 + 9 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return BASE.minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 3;
            }
        },
        /** Power: min 1 + 10*(l-1), max min+15, level 5. */
        POWER {
            @Override int minXP(int subtype, int level) {
                return 1 + 10 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + 15;
            }
            @Override int maxLevel() {
                return 5;
            }
        },
        /** Punch: min 12 + 20*(l-1), max min+25, level 2. */
        PUNCH {
            @Override int minXP(int subtype, int level) {
                return 12 + 20 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + 25;
            }
            @Override int maxLevel() {
                return 2;
            }
        },
        /** Flame: flat 20..50, level 1. */
        FLAME {
            @Override int minXP(int subtype, int level) {
                return 20;
            }
            @Override int maxXP(int subtype, int level) {
                return 50;
            }
        },
        /** Infinity: flat 20..50, level 1. */
        INFINITY {
            @Override int minXP(int subtype, int level) {
                return 20;
            }
            @Override int maxXP(int subtype, int level) {
                return 50;
            }
        },
        /** Respiration: min 10*level, max min+30, level 3. */
        RESPIRATION {
            @Override int minXP(int subtype, int level) {
                return 10 * level;
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + 30;
            }
            @Override int maxLevel() {
                return 3;
            }
        },
        /** Aqua affinity: flat 1..41, level 1. */
        AQUA_AFFINITY {
            @Override int minXP(int subtype, int level) {
                return 1;
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + 40;
            }
        },
        /** Depth strider: min 10*level, max min+15, level 3. */
        DEPTH_STRIDER {
            @Override int minXP(int subtype, int level) {
                return 10 * level;
            }
            @Override int maxXP(int subtype, int level) {
                return minXP(subtype, level) + 15;
            }
            @Override int maxLevel() {
                return 3;
            }
        },
        /** Thorns: min 10 + 20*(l-1), max min+50, level 3. */
        THORNS {
            @Override int minXP(int subtype, int level) {
                return 10 + 20 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return BASE.minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 3;
            }
        },
        /** Looting (lootBonus): min 15 + 9*(l-1), max min+50, level 3. */
        LOOTING {
            @Override int minXP(int subtype, int level) {
                return 15 + 9 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return BASE.minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 3;
            }
        },
        /** Luck of the sea: min 15 + 9*(l-1), max min+50, level 3. */
        LUCK_OF_THE_SEA {
            @Override int minXP(int subtype, int level) {
                return 15 + 9 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return BASE.minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 3;
            }
        },
        /** Lure: min 15 + 9*(l-1), max min+50, level 3. */
        LURE {
            @Override int minXP(int subtype, int level) {
                return 15 + 9 * (level - 1);
            }
            @Override int maxXP(int subtype, int level) {
                return BASE.minXP(subtype, level) + 50;
            }
            @Override int maxLevel() {
                return 3;
            }
        };

        int minXP(int subtype, int level) {
            throw new UnsupportedOperationException("curve undefined for " + name());
        }

        int maxXP(int subtype, int level) {
            throw new UnsupportedOperationException("curve undefined for " + name());
        }

        int maxLevel() {
            return 1;
        }

        boolean isCompatible(Entry self, Entry other) {
            return self != other;
        }
    }

    // ------------------------------------------------ the registry

    // The maps live above the register calls: static init runs in
    // declaration order and the registrations populate them.
    private static final Map<Integer, Entry> BY_ID = new TreeMap<>();
    /** The registry order (the reference's ALL: id order, the offer scan's walk). */
    private static final java.util.List<Entry> ALL_LIST = new java.util.ArrayList<>();

    /** Protection kinds (the reference's type index): all, fire, fall, blast, projectile. */
    public static final int PROTECTION_ALL = 0;
    public static final int PROTECTION_FIRE = 1;
    public static final int PROTECTION_FALL = 2;
    public static final int PROTECTION_BLAST = 3;
    public static final int PROTECTION_PROJECTILE = 4;

    /** Damage targets: all, undead, arthropods. */
    public static final int TARGET_ALL = 0;
    public static final int TARGET_UNDEAD = 1;
    public static final int TARGET_ARTHROPODS = 2;

    public static final Entry PROTECTION = register(0, "protection", 10, Category.ARMOR, Family.PROTECTION, PROTECTION_ALL);
    public static final Entry FIRE_PROTECTION = register(1, "fire_protection", 5, Category.ARMOR, Family.PROTECTION, PROTECTION_FIRE);
    public static final Entry FEATHER_FALLING = register(2, "feather_falling", 5, Category.ARMOR_FEET, Family.PROTECTION, PROTECTION_FALL);
    public static final Entry BLAST_PROTECTION = register(3, "blast_protection", 2, Category.ARMOR, Family.PROTECTION, PROTECTION_BLAST);
    public static final Entry PROJECTILE_PROTECTION = register(4, "projectile_protection", 5, Category.ARMOR, Family.PROTECTION, PROTECTION_PROJECTILE);
    public static final Entry RESPIRATION = register(5, "respiration", 2, Category.ARMOR_HEAD, Family.RESPIRATION, 0);
    public static final Entry AQUA_AFFINITY = register(6, "aqua_affinity", 2, Category.ARMOR_HEAD, Family.AQUA_AFFINITY, 0);
    public static final Entry THORNS = register(7, "thorns", 1, Category.ARMOR_TORSO, Family.THORNS, 0);
    public static final Entry DEPTH_STRIDER = register(8, "depth_strider", 2, Category.ARMOR_FEET, Family.DEPTH_STRIDER, 0);
    public static final Entry SHARPNESS = register(16, "sharpness", 10, Category.WEAPON, Family.DAMAGE, TARGET_ALL);
    public static final Entry SMITE = register(17, "smite", 5, Category.WEAPON, Family.DAMAGE, TARGET_UNDEAD);
    public static final Entry BANE_OF_ARTHROPODS = register(18, "bane_of_arthropods", 5, Category.WEAPON, Family.DAMAGE, TARGET_ARTHROPODS);
    public static final Entry KNOCKBACK = register(19, "knockback", 5, Category.WEAPON, Family.KNOCKBACK, 0);
    public static final Entry FIRE_ASPECT = register(20, "fire_aspect", 2, Category.WEAPON, Family.FIRE_ASPECT, 0);
    public static final Entry LOOTING = register(21, "looting", 2, Category.WEAPON, Family.LOOTING, 0);
    public static final Entry EFFICIENCY = register(32, "efficiency", 10, Category.DIGGER, Family.EFFICIENCY, 0);
    public static final Entry SILK_TOUCH = register(33, "silk_touch", 1, Category.DIGGER, Family.SILK_TOUCH, 0);
    public static final Entry UNBREAKING = register(34, "unbreaking", 5, Category.BREAKABLE, Family.UNBREAKING, 0);
    public static final Entry FORTUNE = register(35, "fortune", 2, Category.DIGGER, Family.FORTUNE, 0);
    public static final Entry POWER = register(48, "power", 10, Category.BOW, Family.POWER, 0);
    public static final Entry PUNCH = register(49, "punch", 2, Category.BOW, Family.PUNCH, 0);
    public static final Entry FLAME = register(50, "flame", 2, Category.BOW, Family.FLAME, 0);
    public static final Entry INFINITY = register(51, "infinity", 1, Category.BOW, Family.INFINITY, 0);
    public static final Entry LUCK_OF_THE_SEA = register(61, "luck_of_the_sea", 2, Category.FISHING_ROD, Family.LUCK_OF_THE_SEA, 0);
    public static final Entry LURE = register(62, "lure", 2, Category.FISHING_ROD, Family.LURE, 0);

    private static Entry register(int id, String key, int weight, Category category,
                                  Family family, int subtype) {
        Entry entry = new Entry(id, key, weight, category, family.maxLevel(), family, subtype);
        Entry existing = BY_ID.put(id, entry);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate enchantment id: " + id);
        }
        ALL_LIST.add(entry);
        return entry;
    }

    /** @return the enchantment with the legacy id, or null (the byId rule). */
    public static Entry byId(int id) {
        return BY_ID.get(id);
    }

    /** @return every registered enchantment in id order (the reference's ALL). */
    public static java.util.List<Entry> all() {
        return java.util.List.copyOf(ALL_LIST);
    }

    /**
     * The historical {@code Item.getEnchantability} mapped onto the engine's
     * registry: tools and swords ride their material's tier, armor the armor
     * tier, book/bow/fishing rod 1, everything else 0 (the Item default —
     * the shears carry no override and are table-unenchantable, the
     * reference's silence is the value).
     */
    public static int enchantabilityOf(ItemStack item) {
        if (item.isEmpty()) {
            return 0;
        }
        // The shears: the engine registers them as an iron tool, but the
        // reference's ShearsItem extends Item with no getEnchantability
        // override — the Item default of 0 is the value (the reference's
        // silence is the value; the table cannot enchant shears in 1.8.8).
        if (item.type().identifier().toString().equals("minecraft:shears")) {
            return 0;
        }
        Optional<ToolSpec> tool = Tools.specOf(item.type());
        if (tool.isPresent()) {
            return switch (tool.orElseThrow().material()) {
                case WOOD -> 15;
                case STONE -> 5;
                case IRON -> 14;
                case DIAMOND -> 10;
                case GOLD -> 22;
            };
        }
        Optional<Armor.Spec> armor = Armor.specOf(item.type());
        if (armor.isPresent()) {
            return armorEnchantabilityOf(item.type().identifier().toString());
        }
        String id = item.type().identifier().toString();
        return switch (id) {
            case "minecraft:book", "minecraft:bow", "minecraft:fishing_rod" -> 1;
            default -> 0;
        };
    }

    /** The ArmorItem tier table: leather 15, chain 12, iron 9, gold 25, diamond 10. */
    private static int armorEnchantabilityOf(String id) {
        if (id.startsWith("minecraft:leather_")) {
            return 15;
        }
        if (id.startsWith("minecraft:chainmail_")) {
            return 12;
        }
        if (id.startsWith("minecraft:iron_")) {
            return 9;
        }
        if (id.startsWith("minecraft:golden_")) {
            return 25;
        }
        if (id.startsWith("minecraft:diamond_")) {
            return 10;
        }
        return 0;
    }

    private Enchantments() {
    }
}
