package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The villager trade slice on the engine level: the career stock tables, the
 * use budget (the grey-out) and the charge bookkeeping. The execution flow
 * itself is the EngineServer's (validated against the player inventory over
 * the wire in the protocol module).
 */
class VillagerTradeTest {

    private static MobEntity villager(long seed) {
        return new MobEntity(1, MobType.VILLAGER,
                new Position(0.5, 4.0, 0.5), new Random(seed), stubWorld());
    }

    private static MobEntity.WorldQuery stubWorld() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    @Test
    void villagerSpawnsWithAProfessionAndThreeStockOffers() {
        MobEntity trader = villager(5);
        assertTrue(trader.isTrader(), "the villager trades");
        assertTrue(trader.profession() >= VillagerTrades.PROFESSION_FARMER
                        && trader.profession() <= VillagerTrades.PROFESSION_BUTCHER,
                "the profession stays in the 1.8 band 0-4");
        assertEquals(3, trader.offers().length, "each career stocks three offers");
        for (TradeOffer offer : trader.offers()) {
            assertFalse(offer.buy1().isEmpty(), "every offer takes a buy");
            assertFalse(offer.result().isEmpty(), "every offer pays a result");
            assertEquals(VillagerTrades.STOCK_MAX_USES, offer.maxUses(),
                    "the stock budget is the historical 7");
        }
    }

    @Test
    void careerTablesCarryTheThematicStock() {
        // The farmer trades wheat; the librarian trades paper; the butcher
        // trades meat (the vanilla career themes).
        assertTrue(VillagerTrades.offersFor(VillagerTrades.PROFESSION_FARMER).stream()
                        .anyMatch(o -> o.buy1().type().equals(BuiltinItems.WHEAT)),
                "the farmer buys wheat");
        assertTrue(VillagerTrades.offersFor(VillagerTrades.PROFESSION_LIBRARIAN).stream()
                        .anyMatch(o -> o.buy1().type().equals(BuiltinItems.PAPER)),
                "the librarian buys paper");
        assertTrue(VillagerTrades.offersFor(VillagerTrades.PROFESSION_BUTCHER).stream()
                        .anyMatch(o -> o.buy1().type().equals(BuiltinItems.BEEF)
                                || o.buy1().type().equals(BuiltinItems.PORKCHOP)),
                "the butcher buys meat");
        assertTrue(VillagerTrades.offersFor(VillagerTrades.PROFESSION_BLACKSMITH).stream()
                        .anyMatch(o -> o.buy1().type().equals(BuiltinItems.IRON_INGOT)),
                "the smith buys iron");
    }

    @Test
    void useBudgetGreysTheOfferOutAndChargesCount() {
        MobEntity trader = villager(9);
        TradeOffer offer = trader.offers()[0];
        assertTrue(trader.offerAvailable(0), "a fresh offer trades");
        for (int i = 0; i < offer.maxUses(); i++) {
            trader.chargeOfferUse(0);
        }
        assertEquals(offer.maxUses(), trader.offerUses(0), "the counter counts every trade");
        assertFalse(trader.offerAvailable(0), "the spent-out offer greys out");
        assertFalse(trader.offerAvailable(99), "a rogue index is simply unavailable");
        assertThrows(IllegalArgumentException.class, () -> trader.chargeOfferUse(99),
                "charging a rogue index throws");
    }

    @Test
    void offersRejectBrokenRecords() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeOffer(ItemStack.EMPTY, ItemStack.EMPTY,
                        ItemStack.of(BuiltinItems.EMERALD, 1), 7),
                "an offer without a buy is broken");
        assertThrows(IllegalArgumentException.class,
                () -> new TradeOffer(ItemStack.of(BuiltinItems.WHEAT, 20), ItemStack.EMPTY,
                        ItemStack.EMPTY, 7),
                "an offer without a result is broken");
        assertThrows(IllegalArgumentException.class,
                () -> new TradeOffer(ItemStack.of(BuiltinItems.WHEAT, 20), ItemStack.EMPTY,
                        ItemStack.of(BuiltinItems.EMERALD, 1), 0),
                "an offer without a budget is broken");
    }

    @Test
    void secondBuysRoundTripTheHasFlag() {
        TradeOffer single = new TradeOffer(ItemStack.of(BuiltinItems.WHEAT, 20),
                ItemStack.EMPTY, ItemStack.of(BuiltinItems.EMERALD, 1), 7);
        assertFalse(single.hasSecondBuy(), "no second buy when EMPTY");
        TradeOffer paired = new TradeOffer(ItemStack.of(BuiltinItems.WHEAT, 10),
                ItemStack.of(BuiltinItems.EMERALD, 1),
                ItemStack.of(BuiltinItems.BREAD, 6), 7);
        assertTrue(paired.hasSecondBuy(), "the second buy rides the flag");
    }
}
