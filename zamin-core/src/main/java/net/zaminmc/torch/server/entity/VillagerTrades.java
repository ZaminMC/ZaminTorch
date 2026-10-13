package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.enchantment.Enchantments;
import net.zaminmc.torch.server.enchantment.EnchantmentHelper;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.util.List;
import java.util.Random;

/**
 * The villager stock tables (Slice 21g — VillagerEntity 1:1): the
 * TRADE_SOURCES data exactly as the reference declares it, indexed
 * {@code [profession][career - 1][careerLevel - 1]} into the per-level
 * generator rows. Each generator rolls its own amounts at the levelUp walk
 * ({@code TradeSource.provide}) — the offer's price band, not a fixed row.
 *
 * <p>The five generator shapes are the reference's own inner classes:
 * the emerald-for-items buy, the items-for-emeralds sell (with the
 * random-enchantment polish), the type-flip sell (the negative amount
 * buying with a single emerald), the item-plus-emerald compound buy, and
 * the enchanted-book roll.</p>
 */
public final class VillagerTrades {

    private VillagerTrades() {
    }

    /** The 1.8 profession ids (the DataWatcher index-16 Int values). */
    public static final int PROFESSION_FARMER = 0;
    public static final int PROFESSION_LIBRARIAN = 1;
    public static final int PROFESSION_PRIEST = 2;
    public static final int PROFESSION_BLACKSMITH = 3;
    public static final int PROFESSION_BUTCHER = 4;

    /** The historical use budget of a fresh stock offer (TradeOffer's default 7). */
    public static final int STOCK_MAX_USES = 7;

    // ---------------------------------------------------------- the generators

    /** The reference's RandomAmount: a [min, max] band (min when inverted). */
    record RandomAmount(int min, int max) {
        int pick(Random random) {
            return min >= max ? min : min + random.nextInt(max - min + 1);
        }
    }

    /** One trade-source row: rolls a fresh offer at the levelUp walk. */
    interface TradeSource {
        TradeOffer provide(Random random);
    }

    /** EmeraldForItems: the item x the rolled amount buys one emerald. */
    static TradeSource emeraldForItems(net.zaminmc.torch.item.ItemType item,
                                       RandomAmount cost) {
        return random -> {
            int amount = cost == null ? 1 : cost.pick(random);
            return new TradeOffer(ItemStack.of(item, amount),
                    ItemStack.of(BuiltinItems.EMERALD, 1), STOCK_MAX_USES);
        };
    }

    /**
     * ItemsForEmeralds: the emeralds x the roll buy one of the item, the
     * item polished by the reference's random enchantment
     * ({@code addRandomEnchantment(random, stack, 5 + nextInt(15))}).
     */
    static TradeSource itemsForEmeralds(net.zaminmc.torch.item.ItemType item,
                                        RandomAmount emeralds) {
        return random -> {
            int amount = emeralds == null ? 1 : emeralds.pick(random);
            ItemStack sold = EnchantmentHelper.addRandomEnchantment(random,
                    ItemStack.of(item, 1), 5 + random.nextInt(15));
            return new TradeOffer(ItemStack.of(BuiltinItems.EMERALD, amount),
                    sold, STOCK_MAX_USES);
        };
    }

    /**
     * EmeraldsForVillagerTypeItem: the sign flip — a positive roll sells
     * one of the item for emeralds x the roll; a negative one SELLS the
     * item x -roll for a single emerald (the reference's own pair).
     */
    static TradeSource emeraldsForVillagerTypeItem(
            net.zaminmc.torch.item.ItemType item, int damage, RandomAmount cost) {
        return random -> {
            int roll = cost == null ? 1 : cost.pick(random);
            if (roll < 0) {
                return new TradeOffer(ItemStack.of(BuiltinItems.EMERALD, 1),
                        new ItemStack(item, -roll, damage, null), STOCK_MAX_USES);
            }
            return new TradeOffer(new ItemStack(BuiltinItems.EMERALD, roll, damage, null),
                    ItemStack.of(item, 1), STOCK_MAX_USES);
        };
    }

    /**
     * ItemsForItemsAndEmeralds: the compound buy — the item x the cost
     * roll PLUS one emerald buys the result x the amount roll (the
     * fisherman's raw/cooked pair, the fletcher's gravel-to-flint).
     */
    static TradeSource itemsForItemsAndEmeralds(
            net.zaminmc.torch.item.ItemType item, RandomAmount cost,
            net.zaminmc.torch.item.ItemType result, RandomAmount amount) {
        return random -> {
            int buy = cost == null ? 1 : cost.pick(random);
            int pay = amount == null ? 1 : amount.pick(random);
            return new TradeOffer(ItemStack.of(item, buy),
                    ItemStack.of(BuiltinItems.EMERALD, 1),
                    ItemStack.of(result, pay), STOCK_MAX_USES);
        };
    }

    /**
     * EnchantedItemForEmeralds: the librarian's book — one book plus
     * {@code 2 + nextInt(5 + level * 10) + 3 * level} emeralds (capped 64)
     * buys the enchanted book carrying one random enchantment at a random
     * level of its own range.
     */
    static TradeSource enchantedItemForEmeralds() {
        return random -> {
            List<Enchantments.Entry> all = Enchantments.all();
            Enchantments.Entry enchantment = all.get(random.nextInt(all.size()));
            int level = 1 + random.nextInt(enchantment.maxLevel);
            ItemStack book = ItemStack.of(BuiltinItems.ENCHANTED_BOOK, 1)
                    .withEnchantment(enchantment.id, level);
            int price = 2 + random.nextInt(5 + level * 10) + 3 * level;
            if (price > 64) {
                price = 64;
            }
            return new TradeOffer(ItemStack.of(BuiltinItems.BOOK, 1),
                    ItemStack.of(BuiltinItems.EMERALD, price),
                    book, STOCK_MAX_USES);
        };
    }

    // ------------------------------------------------------------- the tables

    private static final RandomAmount AMOUNT(int min, int max) {
        return new RandomAmount(min, max);
    }

    /**
     * TRADE_SOURCES (the reference's own literal, lines 74-300): the five
     * professions, each a career array, each career a level array, each
     * level the generator rows the levelUp walk appends.
     */
    private static final TradeSource[][][][] TRADE_SOURCES = {
            // 0 — the farmer: four careers (farmer, fisherman, shepherd, fletcher)
            {
                    { // farmer
                            {
                                    emeraldForItems(BuiltinItems.WHEAT, AMOUNT(18, 22)),
                                    emeraldForItems(BuiltinItems.POTATO, AMOUNT(15, 19)),
                                    emeraldForItems(BuiltinItems.CARROT, AMOUNT(15, 19)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.BREAD, 0, AMOUNT(-4, -2))
                            },
                            {
                                    emeraldForItems(BuiltinItems.PUMPKIN, AMOUNT(8, 13)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.PUMPKIN_PIE, 0, AMOUNT(-3, -2))
                            },
                            {
                                    emeraldForItems(BuiltinItems.MELON, AMOUNT(7, 12)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.APPLE, 0, AMOUNT(-5, -7))
                            },
                            {
                                    emeraldsForVillagerTypeItem(BuiltinItems.COOKIE, 0, AMOUNT(-6, -10)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.CAKE, 0, AMOUNT(1, 1))
                            }
                    },
                    { // fisherman
                            {
                                    emeraldForItems(BuiltinItems.STRING, AMOUNT(15, 20)),
                                    emeraldForItems(BuiltinItems.COAL, AMOUNT(16, 24)),
                                    itemsForItemsAndEmeralds(BuiltinItems.FISH, AMOUNT(6, 6),
                                            BuiltinItems.COOKED_FISH, AMOUNT(6, 6))
                            },
                            {
                                    itemsForEmeralds(BuiltinItems.FISHING_ROD, AMOUNT(7, 8))
                            }
                    },
                    { // shepherd
                            {
                                    emeraldForItems(BuiltinItems.WOOL, AMOUNT(16, 22)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.SHEARS, 0, AMOUNT(3, 4))
                            },
                            {
                                    // The wool-color wall: the reference's sixteen
                                    // metadata rows, one offer each (the engine's
                                    // wool rides the plain item).
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 0, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 1, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 2, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 3, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 4, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 5, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 6, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 7, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 8, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 9, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 10, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 11, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 12, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 13, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 14, AMOUNT(1, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.WOOL, 15, AMOUNT(1, 2))
                            }
                    },
                    { // fletcher
                            {
                                    emeraldForItems(BuiltinItems.STRING, AMOUNT(15, 20)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.ARROW, 0, AMOUNT(-12, -8))
                            },
                            {
                                    emeraldsForVillagerTypeItem(BuiltinItems.BOW, 0, AMOUNT(2, 3)),
                                    itemsForItemsAndEmeralds(BuiltinItems.GRAVEL, AMOUNT(10, 10),
                                            BuiltinItems.FLINT, AMOUNT(6, 10))
                            }
                    }
            },
            // 1 — the librarian: one career, six levels
            {
                    {
                            {
                                    emeraldForItems(BuiltinItems.PAPER, AMOUNT(24, 36)),
                                    enchantedItemForEmeralds()
                            },
                            {
                                    emeraldForItems(BuiltinItems.BOOK, AMOUNT(8, 10)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.COMPASS, 0, AMOUNT(10, 12)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.BOOKSHELF, 0, AMOUNT(3, 4))
                            },
                            {
                                    emeraldForItems(BuiltinItems.WRITTEN_BOOK, AMOUNT(2, 2)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.CLOCK, 0, AMOUNT(10, 12)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.GLASS, 0, AMOUNT(-5, -3))
                            },
                            {enchantedItemForEmeralds()},
                            {enchantedItemForEmeralds()},
                            {emeraldsForVillagerTypeItem(BuiltinItems.NAME_TAG, 0, AMOUNT(20, 22))}
                    }
            },
            // 2 — the cleric: one career, four levels
            {
                    {
                            {
                                    emeraldForItems(BuiltinItems.ROTTEN_FLESH, AMOUNT(36, 40)),
                                    emeraldForItems(BuiltinItems.GOLD_INGOT, AMOUNT(8, 10))
                            },
                            {
                                    emeraldsForVillagerTypeItem(BuiltinItems.REDSTONE, 0, AMOUNT(-4, -1)),
                                    // The reference's blue-dye row (the dye 351's
                                    // metadata 4 — the engine's lapis item).
                                    emeraldsForVillagerTypeItem(BuiltinItems.LAPIS, 4, AMOUNT(-2, -1))
                            },
                            {
                                    emeraldsForVillagerTypeItem(BuiltinItems.ENDER_EYE, 0, AMOUNT(7, 11)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.GLOWSTONE, 0, AMOUNT(-3, -1))
                            },
                            {emeraldsForVillagerTypeItem(BuiltinItems.EXPERIENCE_BOTTLE, 0, AMOUNT(3, 11))}
                    }
            },
            // 3 — the blacksmith: three careers (armor, weapon, tool)
            {
                    {
                            {
                                    emeraldForItems(BuiltinItems.COAL, AMOUNT(16, 24)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.IRON_HELMET, 0, AMOUNT(4, 6))
                            },
                            {
                                    emeraldForItems(BuiltinItems.IRON_INGOT, AMOUNT(7, 9)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.IRON_CHESTPLATE, 0, AMOUNT(10, 14))
                            },
                            {
                                    emeraldForItems(BuiltinItems.DIAMOND, AMOUNT(3, 4)),
                                    itemsForEmeralds(BuiltinItems.DIAMOND_CHESTPLATE, AMOUNT(16, 19))
                            },
                            {
                                    emeraldsForVillagerTypeItem(BuiltinItems.CHAINMAIL_BOOTS, 0, AMOUNT(5, 7)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.CHAINMAIL_LEGGINGS, 0, AMOUNT(9, 11)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.CHAINMAIL_HELMET, 0, AMOUNT(5, 7)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.CHAINMAIL_CHESTPLATE, 0, AMOUNT(11, 15))
                            }
                    },
                    {
                            {
                                    emeraldForItems(BuiltinItems.COAL, AMOUNT(16, 24)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.IRON_AXE, 0, AMOUNT(6, 8))
                            },
                            {
                                    emeraldForItems(BuiltinItems.IRON_INGOT, AMOUNT(7, 9)),
                                    itemsForEmeralds(BuiltinItems.IRON_SWORD, AMOUNT(9, 10))
                            },
                            {
                                    emeraldForItems(BuiltinItems.DIAMOND, AMOUNT(3, 4)),
                                    itemsForEmeralds(BuiltinItems.DIAMOND_SWORD, AMOUNT(12, 15)),
                                    itemsForEmeralds(BuiltinItems.DIAMOND_AXE, AMOUNT(9, 12))
                            }
                    },
                    {
                            {
                                    emeraldForItems(BuiltinItems.COAL, AMOUNT(16, 24)),
                                    itemsForEmeralds(BuiltinItems.IRON_SHOVEL, AMOUNT(5, 7))
                            },
                            {
                                    emeraldForItems(BuiltinItems.IRON_INGOT, AMOUNT(7, 9)),
                                    itemsForEmeralds(BuiltinItems.IRON_PICKAXE, AMOUNT(9, 11))
                            },
                            {
                                    emeraldForItems(BuiltinItems.DIAMOND, AMOUNT(3, 4)),
                                    itemsForEmeralds(BuiltinItems.DIAMOND_PICKAXE, AMOUNT(12, 15))
                            }
                    }
            },
            // 4 — the butcher: two careers (butcher, leather)
            {
                    {
                            {
                                    emeraldForItems(BuiltinItems.PORKCHOP, AMOUNT(14, 18)),
                                    emeraldForItems(BuiltinItems.RAW_CHICKEN, AMOUNT(14, 18))
                            },
                            {
                                    emeraldForItems(BuiltinItems.COAL, AMOUNT(16, 24)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.COOKED_PORKCHOP, 0, AMOUNT(-7, -5)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.COOKED_CHICKEN, 0, AMOUNT(-8, -6))
                            }
                    },
                    {
                            {
                                    emeraldForItems(BuiltinItems.LEATHER, AMOUNT(9, 12)),
                                    emeraldsForVillagerTypeItem(BuiltinItems.LEATHER_LEGGINGS, 0, AMOUNT(2, 4))
                            },
                            {itemsForEmeralds(BuiltinItems.LEATHER_CHESTPLATE, AMOUNT(7, 12))},
                            {emeraldsForVillagerTypeItem(BuiltinItems.SADDLE, 0, AMOUNT(8, 10))}
                    }
            }
    };

    /** @return the profession's career table (the outer array). */
    static TradeSource[][][] tableFor(int profession) {
        return TRADE_SOURCES[Math.max(0, Math.min(TRADE_SOURCES.length - 1, profession))];
    }

    /**
     * @return the career count of the profession (the levelUp roll's own
     * bound: {@code nextInt(careers) + 1}).
     */
    static int careerCount(int profession) {
        return tableFor(profession).length;
    }

    /**
     * @return the rolled rows of the career's level ({@code level - 1}
     * indexed), or an empty row when the level runs past the table.
     */
    static TradeSource[] levelRows(int profession, int career, int careerLevel) {
        TradeSource[][] careerTable = tableFor(profession)[career - 1];
        int level = careerLevel - 1;
        if (level < 0 || level >= careerTable.length) {
            return new TradeSource[0];
        }
        return careerTable[level];
    }
}
