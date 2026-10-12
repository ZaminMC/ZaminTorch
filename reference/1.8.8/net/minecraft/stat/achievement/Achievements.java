package net.minecraft.stat.achievement;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.AchievementProgress;

public class Achievements {
    public static int minColumn;
    public static int minRow;
    public static int maxColumn;
    public static int maxRow;
    public static List<AchievementStat> ALL = Lists.newArrayList();
    public static AchievementStat OPEN_INVENTORY = new AchievementStat("achievement.openInventory", "openInventory", 0, 0, Items.BOOK, null)
        .setLocal()
        .register();
    public static AchievementStat GET_LOG = new AchievementStat("achievement.mineWood", "mineWood", 2, 1, Blocks.LOG, OPEN_INVENTORY).register();
    public static AchievementStat CRAFT_CRAFTING_TABLE = new AchievementStat(
            "achievement.buildWorkBench", "buildWorkBench", 4, -1, Blocks.CRAFTING_TABLE, GET_LOG
        )
        .register();
    public static AchievementStat CRAFT_PICKAXE = new AchievementStat(
            "achievement.buildPickaxe", "buildPickaxe", 4, 2, Items.WOODEN_PICKAXE, CRAFT_CRAFTING_TABLE
        )
        .register();
    public static AchievementStat CRAFT_FURNACE = new AchievementStat("achievement.buildFurnace", "buildFurnace", 3, 4, Blocks.FURNACE, CRAFT_PICKAXE)
        .register();
    public static AchievementStat GET_IRON_INGOT = new AchievementStat("achievement.acquireIron", "acquireIron", 1, 4, Items.IRON_INGOT, CRAFT_FURNACE)
        .register();
    public static AchievementStat CRAFT_WOODEN_HOE = new AchievementStat("achievement.buildHoe", "buildHoe", 2, -3, Items.WOODEN_HOE, CRAFT_CRAFTING_TABLE)
        .register();
    public static AchievementStat CRAFT_BREAD = new AchievementStat("achievement.makeBread", "makeBread", -1, -3, Items.BREAD, CRAFT_WOODEN_HOE).register();
    public static AchievementStat CRAFT_CAKE = new AchievementStat("achievement.bakeCake", "bakeCake", 0, -5, Items.CAKE, CRAFT_WOODEN_HOE).register();
    /**
     * Achieved when a pickaxe better than wood is crafted
     */
    public static AchievementStat CRAFT_BETTER_PICKAXE = new AchievementStat(
            "achievement.buildBetterPickaxe", "buildBetterPickaxe", 6, 2, Items.STONE_PICKAXE, CRAFT_PICKAXE
        )
        .register();
    public static AchievementStat COOK_FISH = new AchievementStat("achievement.cookFish", "cookFish", 2, 6, Items.COOKED_FISH, CRAFT_FURNACE).register();
    /**
     * Achieved when the traveling 1k blocks away from their starting position in a minecart
     */
    public static AchievementStat TRAVEL_KILOMETER_BY_MINECART = new AchievementStat("achievement.onARail", "onARail", 2, 3, Blocks.RAIL, GET_IRON_INGOT)
        .setChallenge()
        .register();
    public static AchievementStat CRAFT_SWORD = new AchievementStat("achievement.buildSword", "buildSword", 6, -1, Items.WOODEN_SWORD, CRAFT_CRAFTING_TABLE)
        .register();
    /**
     * Achieved when a hostile mob is killed
     */
    public static AchievementStat KILL_ENEMY = new AchievementStat("achievement.killEnemy", "killEnemy", 8, -1, Items.BONE, CRAFT_SWORD).register();
    public static AchievementStat KILL_COW = new AchievementStat("achievement.killCow", "killCow", 7, -3, Items.LEATHER, CRAFT_SWORD).register();
    /**
     * Achieved when the pig the player is riding falls from 5 blocks or more
     */
    public static AchievementStat RIDE_PIG_OFF_CLIFF = new AchievementStat("achievement.flyPig", "flyPig", 9, -3, Items.SADDLE, KILL_COW)
        .setChallenge()
        .register();
    /**
     * Achieved when killing a skeleton from than 50 metres away
     */
    public static AchievementStat KILL_SKELETON_FROM_DISTANCE = new AchievementStat("achievement.snipeSkeleton", "snipeSkeleton", 7, 0, Items.BOW, KILL_ENEMY)
        .setChallenge()
        .register();
    public static AchievementStat GET_DIAMOND = new AchievementStat("achievement.diamonds", "diamonds", -1, 5, Blocks.DIAMOND_ORE, GET_IRON_INGOT).register();
    /**
     * Achieved when the player throwns a diamond that is picked up by an entity, including the player themselves
     */
    public static AchievementStat GIVE_DIAMOND = new AchievementStat("achievement.diamondsToYou", "diamondsToYou", -1, 2, Items.DIAMOND, GET_DIAMOND)
        .register();
    public static AchievementStat ENTER_THE_NETHER = new AchievementStat("achievement.portal", "portal", -1, 7, Blocks.OBSIDIAN, GET_DIAMOND).register();
    /**
     * Achieved when killing a ghast with a deflected fireball
     */
    public static AchievementStat KILL_GHAST_WITH_FIREBALL = new AchievementStat("achievement.ghast", "ghast", -4, 8, Items.GHAST_TEAR, ENTER_THE_NETHER)
        .setChallenge()
        .register();
    public static AchievementStat GET_BLAZE_ROD = new AchievementStat("achievement.blazeRod", "blazeRod", 0, 9, Items.BLAZE_ROD, ENTER_THE_NETHER).register();
    public static AchievementStat BREW_POTION = new AchievementStat("achievement.potion", "potion", 2, 8, Items.POTION, GET_BLAZE_ROD).register();
    public static AchievementStat ENTER_THE_END = new AchievementStat("achievement.theEnd", "theEnd", 3, 10, Items.ENDER_EYE, GET_BLAZE_ROD)
        .setChallenge()
        .register();
    public static AchievementStat LEAVE_THE_END = new AchievementStat("achievement.theEnd2", "theEnd2", 4, 13, Blocks.DRAGON_EGG, ENTER_THE_END)
        .setChallenge()
        .register();
    public static AchievementStat CRAFT_ENCHANTING_TABLE = new AchievementStat(
            "achievement.enchantments", "enchantments", -4, 4, Blocks.ENCHANTING_TABLE, GET_DIAMOND
        )
        .register();
    /**
     * Achieved when dealing 18 damage (9 hearts) in a single melee attack
     */
    public static AchievementStat DEAL_OVERKILL_DAMAGE = new AchievementStat(
            "achievement.overkill", "overkill", -4, 1, Items.DIAMOND_SWORD, CRAFT_ENCHANTING_TABLE
        )
        .setChallenge()
        .register();
    public static AchievementStat CRAFT_BOOKSHELF = new AchievementStat("achievement.bookcase", "bookcase", -3, 6, Blocks.BOOKSHELF, CRAFT_ENCHANTING_TABLE)
        .register();
    public static AchievementStat BREED_COW = new AchievementStat("achievement.breedCow", "breedCow", 7, -5, Items.WHEAT, KILL_COW).register();
    /**
     * Achieved when within a 100.9x100.9x203.5 area of a wither when it spawns
     */
    public static AchievementStat SUMMON_WITHER = new AchievementStat(
            "achievement.spawnWither", "spawnWither", 7, 12, new ItemStack(Items.SKULL, 1, 1), LEAVE_THE_END
        )
        .register();
    /**
     * Achieved when within a 100.9x100.9x203.5 area of a wither when it drops its nether star
     */
    public static AchievementStat KILL_WITHER = new AchievementStat("achievement.killWither", "killWither", 7, 10, Items.NETHER_STAR, SUMMON_WITHER).register();
    /**
     * Achieved when within a 20x20x14 area of a beacon when it becomes fully powered
     */
    public static AchievementStat ACTIVATE_MAX_BEACON = new AchievementStat("achievement.fullBeacon", "fullBeacon", 7, 8, Blocks.BEACON, KILL_WITHER)
        .setChallenge()
        .register();
    /**
     * Achieved when all overworld biomes are visited. The game only checks for the player entering a new biome every 100 ticks
     */
    public static AchievementStat ENTER_ALL_BIOMES = new AchievementStat(
            "achievement.exploreAllBiomes", "exploreAllBiomes", 4, 8, Items.DIAMOND_BOOTS, ENTER_THE_END
        )
        .setProgressType(AchievementProgress.class)
        .setChallenge()
        .register();
    public static AchievementStat EAT_ENCHANTED_GOLDEN_APPLE = new AchievementStat(
            "achievement.overpowered", "overpowered", 6, 4, new ItemStack(Items.GOLDEN_APPLE, 1, 1), CRAFT_BETTER_PICKAXE
        )
        .setChallenge()
        .register();

    public static void init() {
    }
}
