package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.util.List;

/**
 * The villager stock tables (the vanilla 1.8 career shapes, simplified to
 * three fixed offers per profession — all items the engine already mints).
 * The trade flow ported from the Glowstone merchant model: offers are data,
 * the execution is the engine's own (validate buys, consume, pay out).
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

    /** The historical use budget of a stock offer. */
    public static final int STOCK_MAX_USES = 7;

    /** @return the villager's stock offers for the profession (fixed, testable). */
    public static List<TradeOffer> offersFor(int profession) {
        return switch (profession) {
            case PROFESSION_FARMER -> List.of(
                    new TradeOffer(stack(BuiltinItems.WHEAT, 20), ItemStack.EMPTY,
                            stack(BuiltinItems.EMERALD, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 1), ItemStack.EMPTY,
                            stack(BuiltinItems.BREAD, 6), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 2), ItemStack.EMPTY,
                            stack(BuiltinItems.WHEAT_SEEDS, 12), STOCK_MAX_USES));
            case PROFESSION_LIBRARIAN -> List.of(
                    new TradeOffer(stack(BuiltinItems.PAPER, 24), ItemStack.EMPTY,
                            stack(BuiltinItems.EMERALD, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 4), ItemStack.EMPTY,
                            stack(BuiltinItems.BOOK, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 1), ItemStack.EMPTY,
                            stack(BuiltinItems.GLASS, 4), STOCK_MAX_USES));
            case PROFESSION_PRIEST -> List.of(
                    new TradeOffer(stack(BuiltinItems.ROTTEN_FLESH, 18), ItemStack.EMPTY,
                            stack(BuiltinItems.EMERALD, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 3), ItemStack.EMPTY,
                            stack(BuiltinItems.GOLD_INGOT, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 4), ItemStack.EMPTY,
                            stack(BuiltinItems.DIAMOND, 1), 3));
            case PROFESSION_BLACKSMITH -> List.of(
                    new TradeOffer(stack(BuiltinItems.IRON_INGOT, 8), ItemStack.EMPTY,
                            stack(BuiltinItems.EMERALD, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 3), ItemStack.EMPTY,
                            stack(BuiltinItems.IRON_PICKAXE, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 5), ItemStack.EMPTY,
                            stack(BuiltinItems.IRON_HELMET, 1), STOCK_MAX_USES));
            case PROFESSION_BUTCHER -> List.of(
                    new TradeOffer(stack(BuiltinItems.BEEF, 15), ItemStack.EMPTY,
                            stack(BuiltinItems.EMERALD, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.PORKCHOP, 14), ItemStack.EMPTY,
                            stack(BuiltinItems.EMERALD, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 1), ItemStack.EMPTY,
                            stack(BuiltinItems.COOKED_BEEF, 7), STOCK_MAX_USES));
            default -> List.of(
                    new TradeOffer(stack(BuiltinItems.WHEAT, 20), ItemStack.EMPTY,
                            stack(BuiltinItems.EMERALD, 1), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 1), ItemStack.EMPTY,
                            stack(BuiltinItems.BREAD, 6), STOCK_MAX_USES),
                    new TradeOffer(stack(BuiltinItems.EMERALD, 2), ItemStack.EMPTY,
                            stack(BuiltinItems.WHEAT_SEEDS, 12), STOCK_MAX_USES));
        };
    }

    private static ItemStack stack(net.zaminmc.torch.item.ItemType type, int count) {
        return ItemStack.of(type, count);
    }
}
