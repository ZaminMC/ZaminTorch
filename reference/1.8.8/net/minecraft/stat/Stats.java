package net.minecraft.stat;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.crafting.CraftingManager;
import net.minecraft.crafting.SmeltingManager;
import net.minecraft.crafting.recipe.Recipe;
import net.minecraft.entity.Entities;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.text.TranslatableText;

public class Stats {
    protected static Map<String, Stat> BY_KEY = Maps.newHashMap();
    public static List<Stat> ALL = Lists.newArrayList();
    public static List<Stat> GENERAL = Lists.newArrayList();
    public static List<ItemStat> USED = Lists.newArrayList();
    public static List<ItemStat> MINED = Lists.newArrayList();
    public static Stat GAMES_LEFT = new DistanceStat("stat.leaveGame", new TranslatableText("stat.leaveGame")).setLocal().register();
    public static Stat MINUTES_PLAYED = new DistanceStat("stat.playOneMinute", new TranslatableText("stat.playOneMinute"), Stat.TIME_FORMATTER)
        .setLocal()
        .register();
    public static Stat TIME_SINCE_DEATH = new DistanceStat("stat.timeSinceDeath", new TranslatableText("stat.timeSinceDeath"), Stat.TIME_FORMATTER)
        .setLocal()
        .register();
    public static Stat CM_WALKED = new DistanceStat("stat.walkOneCm", new TranslatableText("stat.walkOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat CROUCH_ONE_CM = new DistanceStat("stat.crouchOneCm", new TranslatableText("stat.crouchOneCm"), Stat.DISTANCE_FORMATTER)
        .setLocal()
        .register();
    public static Stat SPRINT_ONE_CM = new DistanceStat("stat.sprintOneCm", new TranslatableText("stat.sprintOneCm"), Stat.DISTANCE_FORMATTER)
        .setLocal()
        .register();
    public static Stat CM_SWUM = new DistanceStat("stat.swimOneCm", new TranslatableText("stat.swimOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat CM_FALLEN = new DistanceStat("stat.fallOneCm", new TranslatableText("stat.fallOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat CM_CLIMB = new DistanceStat("stat.climbOneCm", new TranslatableText("stat.climbOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat CM_FLOWN = new DistanceStat("stat.flyOneCm", new TranslatableText("stat.flyOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat CM_DIVEN = new DistanceStat("stat.diveOneCm", new TranslatableText("stat.diveOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat CM_MINECART = new DistanceStat("stat.minecartOneCm", new TranslatableText("stat.minecartOneCm"), Stat.DISTANCE_FORMATTER)
        .setLocal()
        .register();
    public static Stat CM_SAILED = new DistanceStat("stat.boatOneCm", new TranslatableText("stat.boatOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat CM_PIG = new DistanceStat("stat.pigOneCm", new TranslatableText("stat.pigOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat CM_HORSE = new DistanceStat("stat.horseOneCm", new TranslatableText("stat.horseOneCm"), Stat.DISTANCE_FORMATTER).setLocal().register();
    public static Stat JUMPS = new DistanceStat("stat.jump", new TranslatableText("stat.jump")).setLocal().register();
    public static Stat DROPS = new DistanceStat("stat.drop", new TranslatableText("stat.drop")).setLocal().register();
    public static Stat DAMAGE_DEALT = new DistanceStat("stat.damageDealt", new TranslatableText("stat.damageDealt"), Stat.DIVIDE_BY_TEN_FORMATTER).register();
    public static Stat DAMAGE_TAKEN = new DistanceStat("stat.damageTaken", new TranslatableText("stat.damageTaken"), Stat.DIVIDE_BY_TEN_FORMATTER).register();
    public static Stat DEATHS = new DistanceStat("stat.deaths", new TranslatableText("stat.deaths")).register();
    public static Stat MOBS_KILLED = new DistanceStat("stat.mobKills", new TranslatableText("stat.mobKills")).register();
    public static Stat ANIMALS_BRED = new DistanceStat("stat.animalsBred", new TranslatableText("stat.animalsBred")).register();
    public static Stat PLAYERS_KILLED = new DistanceStat("stat.playerKills", new TranslatableText("stat.playerKills")).register();
    public static Stat FISH_CAUGHT = new DistanceStat("stat.fishCaught", new TranslatableText("stat.fishCaught")).register();
    public static Stat JUNK_FISHED = new DistanceStat("stat.junkFished", new TranslatableText("stat.junkFished")).register();
    public static Stat TREASURE_FISHED = new DistanceStat("stat.treasureFished", new TranslatableText("stat.treasureFished")).register();
    public static Stat TALKED_TO_VILLAGER = new DistanceStat("stat.talkedToVillager", new TranslatableText("stat.talkedToVillager")).register();
    public static Stat TRADED_WITH_VILLAGER = new DistanceStat("stat.tradedWithVillager", new TranslatableText("stat.tradedWithVillager")).register();
    public static Stat CAKE_SLICES_EATEN = new DistanceStat("stat.cakeSlicesEaten", new TranslatableText("stat.cakeSlicesEaten")).register();
    public static Stat CAULDRONS_FILLED = new DistanceStat("stat.cauldronFilled", new TranslatableText("stat.cauldronFilled")).register();
    public static Stat CAULDRONS_USED = new DistanceStat("stat.cauldronUsed", new TranslatableText("stat.cauldronUsed")).register();
    public static Stat ARMOR_CLEANED = new DistanceStat("stat.armorCleaned", new TranslatableText("stat.armorCleaned")).register();
    public static Stat BANNER_CLEANED = new DistanceStat("stat.bannerCleaned", new TranslatableText("stat.bannerCleaned")).register();
    public static Stat BREWING_STAND_INTERACTIONS = new DistanceStat("stat.brewingstandInteraction", new TranslatableText("stat.brewingstandInteraction"))
        .register();
    public static Stat BEACON_INTERACTIONS = new DistanceStat("stat.beaconInteraction", new TranslatableText("stat.beaconInteraction")).register();
    public static Stat DROPPERS_INSPECTED = new DistanceStat("stat.dropperInspected", new TranslatableText("stat.dropperInspected")).register();
    public static Stat HOPPERS_INSPECTED = new DistanceStat("stat.hopperInspected", new TranslatableText("stat.hopperInspected")).register();
    public static Stat DISPENSERS_INSPECTED = new DistanceStat("stat.dispenserInspected", new TranslatableText("stat.dispenserInspected")).register();
    public static Stat NOTE_BLOCKS_PLAYED = new DistanceStat("stat.noteblockPlayed", new TranslatableText("stat.noteblockPlayed")).register();
    public static Stat NOTE_BLOCKS_TUNED = new DistanceStat("stat.noteblockTuned", new TranslatableText("stat.noteblockTuned")).register();
    public static Stat FLOWERS_POTTED = new DistanceStat("stat.flowerPotted", new TranslatableText("stat.flowerPotted")).register();
    public static Stat TRAPPED_CHESTS_TRIGGERED = new DistanceStat("stat.trappedChestTriggered", new TranslatableText("stat.trappedChestTriggered")).register();
    public static Stat ENDER_CHESTS_OPENED = new DistanceStat("stat.enderchestOpened", new TranslatableText("stat.enderchestOpened")).register();
    public static Stat ITEMS_ENCHANTED = new DistanceStat("stat.itemEnchanted", new TranslatableText("stat.itemEnchanted")).register();
    public static Stat RECORDS_PLAYED = new DistanceStat("stat.recordPlayed", new TranslatableText("stat.recordPlayed")).register();
    public static Stat FURNACE_INTERACTIONS = new DistanceStat("stat.furnaceInteraction", new TranslatableText("stat.furnaceInteraction")).register();
    public static Stat CRAFTING_TABLE_INTERACTIONS = new DistanceStat("stat.craftingTableInteraction", new TranslatableText("stat.workbenchInteraction"))
        .register();
    public static Stat CHESTS_OPENED = new DistanceStat("stat.chestOpened", new TranslatableText("stat.chestOpened")).register();
    public static final Stat[] BLOCKS_MINED = new Stat[4096];
    public static final Stat[] ITEMS_CRAFTED = new Stat[32000];
    public static final Stat[] ITEMS_USED = new Stat[32000];
    public static final Stat[] ITEMS_BROKEN = new Stat[32000];

    public static void init() {
        initBlocksMinedStats();
        initItemsUsedStats();
        initItemsBrokenStats();
        initItemsCraftedStats();
        Achievements.init();
        Entities.init();
    }

    private static void initItemsCraftedStats() {
        Set<Item> set = Sets.newHashSet();

        for (Recipe recipe : CraftingManager.getInstance().getRecipes()) {
            if (recipe.getResult() != null) {
                set.add(recipe.getResult().getItem());
            }
        }

        for (ItemStack itemstack : SmeltingManager.getInstance().getRecipes().values()) {
            set.add(itemstack.getItem());
        }

        for (Item item : set) {
            if (item != null) {
                int i = Item.getId(item);
                String s = translationKey(item);
                if (s != null) {
                    ITEMS_CRAFTED[i] = new ItemStat("stat.craftItem.", s, new TranslatableText("stat.craftItem", new ItemStack(item).getDisplayName()), item)
                        .register();
                }
            }
        }

        mergeBlockStats(ITEMS_CRAFTED);
    }

    private static void initBlocksMinedStats() {
        for (Block block : Block.REGISTRY) {
            Item item = Item.byBlock(block);
            if (item != null) {
                int i = Block.getId(block);
                String s = translationKey(item);
                if (s != null && block.hasStats()) {
                    BLOCKS_MINED[i] = new ItemStat("stat.mineBlock.", s, new TranslatableText("stat.mineBlock", new ItemStack(block).getDisplayName()), item)
                        .register();
                    MINED.add((ItemStat)BLOCKS_MINED[i]);
                }
            }
        }

        mergeBlockStats(BLOCKS_MINED);
    }

    private static void initItemsUsedStats() {
        for (Item item : Item.REGISTRY) {
            if (item != null) {
                int i = Item.getId(item);
                String s = translationKey(item);
                if (s != null) {
                    ITEMS_USED[i] = new ItemStat("stat.useItem.", s, new TranslatableText("stat.useItem", new ItemStack(item).getDisplayName()), item)
                        .register();
                    if (!(item instanceof BlockItem)) {
                        USED.add((ItemStat)ITEMS_USED[i]);
                    }
                }
            }
        }

        mergeBlockStats(ITEMS_USED);
    }

    private static void initItemsBrokenStats() {
        for (Item item : Item.REGISTRY) {
            if (item != null) {
                int i = Item.getId(item);
                String s = translationKey(item);
                if (s != null && item.isDamageable()) {
                    ITEMS_BROKEN[i] = new ItemStat("stat.breakItem.", s, new TranslatableText("stat.breakItem", new ItemStack(item).getDisplayName()), item)
                        .register();
                }
            }
        }

        mergeBlockStats(ITEMS_BROKEN);
    }

    private static String translationKey(Item item) {
        Identifier identifier = Item.REGISTRY.getKey(item);
        return identifier != null ? identifier.toString().replace(':', '.') : null;
    }

    private static void mergeBlockStats(Stat[] stats) {
        mergeBlockStats(stats, Blocks.WATER, Blocks.FLOWING_WATER);
        mergeBlockStats(stats, Blocks.LAVA, Blocks.FLOWING_LAVA);
        mergeBlockStats(stats, Blocks.LIT_PUMPKIN, Blocks.PUMPKIN);
        mergeBlockStats(stats, Blocks.LIT_FURNACE, Blocks.FURNACE);
        mergeBlockStats(stats, Blocks.LIT_REDSTONE_ORE, Blocks.REDSTONE_ORE);
        mergeBlockStats(stats, Blocks.POWERED_REPEATER, Blocks.REPEATER);
        mergeBlockStats(stats, Blocks.POWERED_COMPARATOR, Blocks.COMPARATOR);
        mergeBlockStats(stats, Blocks.REDSTONE_TORCH, Blocks.UNLIT_REDSTONE_TORCH);
        mergeBlockStats(stats, Blocks.LIT_REDSTONE_LAMP, Blocks.REDSTONE_LAMP);
        mergeBlockStats(stats, Blocks.DOUBLE_STONE_SLAB, Blocks.STONE_SLAB);
        mergeBlockStats(stats, Blocks.DOUBLE_WOODEN_SLAB, Blocks.WOODEN_SLAB);
        mergeBlockStats(stats, Blocks.DOUBLE_RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB);
        mergeBlockStats(stats, Blocks.GRASS, Blocks.DIRT);
        mergeBlockStats(stats, Blocks.FARMLAND, Blocks.DIRT);
    }

    private static void mergeBlockStats(Stat[] stats, Block block1, Block block2) {
        int i = Block.getId(block1);
        int j = Block.getId(block2);
        if (stats[i] != null && stats[j] == null) {
            stats[j] = stats[i];
        } else {
            ALL.remove(stats[i]);
            MINED.remove(stats[i]);
            GENERAL.remove(stats[i]);
            stats[i] = stats[j];
        }
    }

    public static Stat createEntityKillStat(Entities.SpawnEggData spawnEggData) {
        String s = Entities.getKey(spawnEggData.id);
        return s == null
            ? null
            : new Stat("stat.killEntity." + s, new TranslatableText("stat.entityKill", new TranslatableText("entity." + s + ".name"))).register();
    }

    public static Stat createKilledByEntityStat(Entities.SpawnEggData spawnEggData) {
        String s = Entities.getKey(spawnEggData.id);
        return s == null
            ? null
            : new Stat("stat.entityKilledBy." + s, new TranslatableText("stat.entityKilledBy", new TranslatableText("entity." + s + ".name"))).register();
    }

    public static Stat byKey(String key) {
        return BY_KEY.get(key);
    }
}
