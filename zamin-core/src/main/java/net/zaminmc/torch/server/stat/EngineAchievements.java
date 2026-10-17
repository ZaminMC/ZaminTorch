package net.zaminmc.torch.server.stat;

import net.zaminmc.torch.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The achievement table (Achievements + AchievementStat 1:1): the 34
 * 1.8.8 achievements with their keys, columns, rows, icons, parent
 * chains, challenge flags and the one local row, exactly the
 * reference's own declaration order — the order the give-all walk
 * (parents strictly before the children that gate on them) and the
 * take-all's reverse both ride.
 *
 * <p>The parent chain is the {@code /achievement take} walk's own
 * dependency tree: an achievement's gate is its parent (the engine's
 * {@link PlayerStats#increment} refuses an achievement whose parent
 * has not been earned — PlayerStats.increment's own condition), and
 * taking one clears every achievement earned on top of it (the
 * reference's list2 walk: the rows whose parent chain passes through
 * the taken one).</p>
 *
 * <p>The icons are the reference's own ItemStack forms (the toast's
 * client-side tile), including the two damage-carrying rows — the
 * wither skeleton skull ({@code minecraft:skull} meta 1) and the
 * enchanted golden apple ({@code minecraft:golden_apple} meta 1).</p>
 */
public final class EngineAchievements {

    private EngineAchievements() {
    }

    /**
     * One achievement (AchievementStat's own surface): the stat key,
     * the short name (the {@code achievement.<name>} and
     * {@code achievement.<name>.desc} translation keys), the
     * achievement-page column and row, the icon (the reference's item
     * id + damage), the parent chain, the challenge flag, and the one
     * local row (OPEN_INVENTORY — awarded without the server broadcast
     * band; Stat.setLocal).
     */
    public record Achievement(String key, String name, int column, int row,
                               Identifier icon, int iconDamage, Achievement parent,
                               boolean challenge, boolean local) {

        /** The description translation key ({@code achievement.<name>.desc}). */
        public String descriptionKey() {
            return "achievement." + name + ".desc";
        }

        /** The name translation key ({@code achievement.<name>} — Stat's name). */
        public String nameKey() {
            return "achievement." + name;
        }
    }

    private static Identifier id(String value) {
        return Identifier.parse("minecraft:" + value);
    }

    // --- the 34 rows, the reference's declaration order -----------------------

    public static final Achievement OPEN_INVENTORY = new Achievement(
            "achievement.openInventory", "openInventory", 0, 0, id("book"), 0, null, false, true);
    public static final Achievement GET_LOG = new Achievement(
            "achievement.mineWood", "mineWood", 2, 1, id("log"), 0, OPEN_INVENTORY, false, false);
    public static final Achievement CRAFT_CRAFTING_TABLE = new Achievement(
            "achievement.buildWorkBench", "buildWorkBench", 4, -1, id("crafting_table"), 0, GET_LOG, false, false);
    public static final Achievement CRAFT_PICKAXE = new Achievement(
            "achievement.buildPickaxe", "buildPickaxe", 4, 2, id("wooden_pickaxe"), 0, CRAFT_CRAFTING_TABLE, false, false);
    public static final Achievement CRAFT_FURNACE = new Achievement(
            "achievement.buildFurnace", "buildFurnace", 3, 4, id("furnace"), 0, CRAFT_PICKAXE, false, false);
    public static final Achievement GET_IRON_INGOT = new Achievement(
            "achievement.acquireIron", "acquireIron", 1, 4, id("iron_ingot"), 0, CRAFT_FURNACE, false, false);
    public static final Achievement CRAFT_WOODEN_HOE = new Achievement(
            "achievement.buildHoe", "buildHoe", 2, -3, id("wooden_hoe"), 0, CRAFT_CRAFTING_TABLE, false, false);
    public static final Achievement CRAFT_BREAD = new Achievement(
            "achievement.makeBread", "makeBread", -1, -3, id("bread"), 0, CRAFT_WOODEN_HOE, false, false);
    public static final Achievement CRAFT_CAKE = new Achievement(
            "achievement.bakeCake", "bakeCake", 0, -5, id("cake"), 0, CRAFT_WOODEN_HOE, false, false);
    /** The pickaxe-better-than-wood row (the reference's own comment). */
    public static final Achievement CRAFT_BETTER_PICKAXE = new Achievement(
            "achievement.buildBetterPickaxe", "buildBetterPickaxe", 6, 2, id("stone_pickaxe"), 0, CRAFT_PICKAXE, false, false);
    public static final Achievement COOK_FISH = new Achievement(
            "achievement.cookFish", "cookFish", 2, 6, id("cooked_fish"), 0, CRAFT_FURNACE, false, false);
    /** The 1km-by-minecart row (the reference's own comment). */
    public static final Achievement TRAVEL_KILOMETER_BY_MINECART = new Achievement(
            "achievement.onARail", "onARail", 2, 3, id("rail"), 0, GET_IRON_INGOT, true, false);
    public static final Achievement CRAFT_SWORD = new Achievement(
            "achievement.buildSword", "buildSword", 6, -1, id("wooden_sword"), 0, CRAFT_CRAFTING_TABLE, false, false);
    /** The first-hostile-kill row (the reference's own comment). */
    public static final Achievement KILL_ENEMY = new Achievement(
            "achievement.killEnemy", "killEnemy", 8, -1, id("bone"), 0, CRAFT_SWORD, false, false);
    public static final Achievement KILL_COW = new Achievement(
            "achievement.killCow", "killCow", 7, -3, id("leather"), 0, CRAFT_SWORD, false, false);
    /** The ridden-pig-falls row (the reference's own comment). */
    public static final Achievement RIDE_PIG_OFF_CLIFF = new Achievement(
            "achievement.flyPig", "flyPig", 9, -3, id("saddle"), 0, KILL_COW, true, false);
    /** The snipe-the-skeleton row (the reference's own comment). */
    public static final Achievement KILL_SKELETON_FROM_DISTANCE = new Achievement(
            "achievement.snipeSkeleton", "snipeSkeleton", 7, 0, id("bow"), 0, KILL_ENEMY, true, false);
    public static final Achievement GET_DIAMOND = new Achievement(
            "achievement.diamonds", "diamonds", -1, 5, id("diamond_ore"), 0, GET_IRON_INGOT, false, false);
    /** The thrown-diamond row (the reference's own comment). */
    public static final Achievement GIVE_DIAMOND = new Achievement(
            "achievement.diamondsToYou", "diamondsToYou", -1, 2, id("diamond"), 0, GET_DIAMOND, false, false);
    public static final Achievement ENTER_THE_NETHER = new Achievement(
            "achievement.portal", "portal", -1, 7, id("obsidian"), 0, GET_DIAMOND, false, false);
    /** The deflected-fireball row (the reference's own comment). */
    public static final Achievement KILL_GHAST_WITH_FIREBALL = new Achievement(
            "achievement.ghast", "ghast", -4, 8, id("ghast_tear"), 0, ENTER_THE_NETHER, true, false);
    public static final Achievement GET_BLAZE_ROD = new Achievement(
            "achievement.blazeRod", "blazeRod", 0, 9, id("blaze_rod"), 0, ENTER_THE_NETHER, false, false);
    public static final Achievement BREW_POTION = new Achievement(
            "achievement.potion", "potion", 2, 8, id("potion"), 0, GET_BLAZE_ROD, false, false);
    public static final Achievement ENTER_THE_END = new Achievement(
            "achievement.theEnd", "theEnd", 3, 10, id("ender_eye"), 0, GET_BLAZE_ROD, true, false);
    public static final Achievement LEAVE_THE_END = new Achievement(
            "achievement.theEnd2", "theEnd2", 4, 13, id("dragon_egg"), 0, ENTER_THE_END, true, false);
    public static final Achievement CRAFT_ENCHANTING_TABLE = new Achievement(
            "achievement.enchantments", "enchantments", -4, 4, id("enchanting_table"), 0, GET_DIAMOND, false, false);
    /** The 18-damage-melee row (the reference's own comment). */
    public static final Achievement DEAL_OVERKILL_DAMAGE = new Achievement(
            "achievement.overkill", "overkill", -4, 1, id("diamond_sword"), 0, CRAFT_ENCHANTING_TABLE, true, false);
    public static final Achievement CRAFT_BOOKSHELF = new Achievement(
            "achievement.bookcase", "bookcase", -3, 6, id("bookshelf"), 0, CRAFT_ENCHANTING_TABLE, false, false);
    public static final Achievement BREED_COW = new Achievement(
            "achievement.breedCow", "breedCow", 7, -5, id("wheat"), 0, KILL_COW, false, false);
    /** The wither-spawn row (the reference's own comment). */
    public static final Achievement SUMMON_WITHER = new Achievement(
            "achievement.spawnWither", "spawnWither", 7, 12, id("skull"), 1, LEAVE_THE_END, false, false);
    /** The nether-star drop row (the reference's own comment). */
    public static final Achievement KILL_WITHER = new Achievement(
            "achievement.killWither", "killWither", 7, 10, id("nether_star"), 0, SUMMON_WITHER, false, false);
    /** The fully-powered-beacon row (the reference's own comment). */
    public static final Achievement ACTIVATE_MAX_BEACON = new Achievement(
            "achievement.fullBeacon", "fullBeacon", 7, 8, id("beacon"), 0, KILL_WITHER, true, false);
    /** The all-biomes row (the reference's own comment) — the progress-carrying one. */
    public static final Achievement ENTER_ALL_BIOMES = new Achievement(
            "achievement.exploreAllBiomes", "exploreAllBiomes", 4, 8, id("diamond_boots"), 0, ENTER_THE_END, true, false);
    /** The enchanted-golden-apple row (the reference's own comment). */
    public static final Achievement EAT_ENCHANTED_GOLDEN_APPLE = new Achievement(
            "achievement.overpowered", "overpowered", 6, 4, id("golden_apple"), 1, CRAFT_BETTER_PICKAXE, true, false);

    /**
     * The full table in the reference's declaration order (Achievements.ALL):
     * the give-all walk rides it forward (every parent strictly before the
     * children that gate on it), the take-all reversed.
     */
    public static final List<Achievement> ALL = List.of(
            OPEN_INVENTORY,
            GET_LOG,
            CRAFT_CRAFTING_TABLE,
            CRAFT_PICKAXE,
            CRAFT_FURNACE,
            GET_IRON_INGOT,
            CRAFT_WOODEN_HOE,
            CRAFT_BREAD,
            CRAFT_CAKE,
            CRAFT_BETTER_PICKAXE,
            COOK_FISH,
            TRAVEL_KILOMETER_BY_MINECART,
            CRAFT_SWORD,
            KILL_ENEMY,
            KILL_COW,
            RIDE_PIG_OFF_CLIFF,
            KILL_SKELETON_FROM_DISTANCE,
            GET_DIAMOND,
            GIVE_DIAMOND,
            ENTER_THE_NETHER,
            KILL_GHAST_WITH_FIREBALL,
            GET_BLAZE_ROD,
            BREW_POTION,
            ENTER_THE_END,
            LEAVE_THE_END,
            CRAFT_ENCHANTING_TABLE,
            DEAL_OVERKILL_DAMAGE,
            CRAFT_BOOKSHELF,
            BREED_COW,
            SUMMON_WITHER,
            KILL_WITHER,
            ACTIVATE_MAX_BEACON,
            ENTER_ALL_BIOMES,
            EAT_ENCHANTED_GOLDEN_APPLE);

    /** The key lookup (the reference's Stats.BY_KEY view over the achievement band). */
    private static final Map<String, Achievement> BY_KEY = buildByKey();

    private static Map<String, Achievement> buildByKey() {
        Map<String, Achievement> map = new LinkedHashMap<>();
        for (Achievement achievement : ALL) {
            map.put(achievement.key(), achievement);
        }
        return Collections.unmodifiableMap(map);
    }

    /** @return the achievement for a stat key, null when the key is not an achievement. */
    public static Achievement byKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    /** @return whether the stat key belongs to the achievement band. */
    public static boolean isAchievementKey(String key) {
        return key != null && BY_KEY.containsKey(key);
    }

    /**
     * The achievement-page bounds (the reference's min/maxColumn/Row,
     * tracked in the ctor as each row registers): the toast's own page
     * geometry on the client.
     */
    public static int minColumn() {
        int min = Integer.MAX_VALUE;
        for (Achievement achievement : ALL) {
            min = Math.min(min, achievement.column());
        }
        return min;
    }

    public static int minRow() {
        int min = Integer.MAX_VALUE;
        for (Achievement achievement : ALL) {
            min = Math.min(min, achievement.row());
        }
        return min;
    }

    public static int maxColumn() {
        int max = Integer.MIN_VALUE;
        for (Achievement achievement : ALL) {
            max = Math.max(max, achievement.column());
        }
        return max;
    }

    public static int maxRow() {
        int max = Integer.MIN_VALUE;
        for (Achievement achievement : ALL) {
            max = Math.max(max, achievement.row());
        }
        return max;
    }

    /**
     * The parent chain from the row up to the root (the reference's
     * take-walk `while (achievementstat3 != null)` walk): the taken
     * row's own ancestors, nearest first.
     */
    public static List<Achievement> ancestorsOf(Achievement achievement) {
        List<Achievement> chain = new java.util.ArrayList<>();
        for (Achievement parent = achievement.parent(); parent != null; parent = parent.parent()) {
            chain.add(parent);
        }
        return chain;
    }

    /** @return every achievement whose parent chain passes through the given row. */
    public static Collection<Achievement> dependentsOf(Achievement taken) {
        java.util.ArrayList<Achievement> dependents = new java.util.ArrayList<>();
        for (Achievement candidate : ALL) {
            if (candidate == taken) {
                continue;
            }
            for (Achievement parent = candidate.parent(); parent != null; parent = parent.parent()) {
                if (parent == taken) {
                    dependents.add(candidate);
                    break;
                }
            }
        }
        return dependents;
    }
}
