package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The villager trade slice 1:1 (Slice 21g — VillagerEntity's own career
 * model over the engine): the lazy stock (the first open rolls the career
 * and its opening level — the reference's traderOffers == null gate), the
 * TRADE_SOURCES tables (the per-career, per-level generator rows), the
 * trade bookkeeping (the use charge, the 40-tick level-up arm, the
 * riches), and the level-up walk (the spent-out re-greens + the next
 * level's rows).
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
    void villagerSpawnsWithAProfessionAndTheLazyStock() {
        // initialize line 808: the profession nextInt(5); the career and
        // stock roll LAZILY at the first getOffers (traderOffers == null).
        MobEntity trader = villager(5);
        assertTrue(trader.isTrader(), "the villager trades");
        assertTrue(trader.profession() >= VillagerTrades.PROFESSION_FARMER
                        && trader.profession() <= VillagerTrades.PROFESSION_BUTCHER,
                "the profession stays in the 1.8 band 0-4");
        assertEquals(0, trader.villagerCareer(), "no career before the first open");
        assertEquals(0, trader.villagerCareerLevel(), "no level before the first open");
        // The first consult rolls: the career inside its profession's table,
        // level 1, and that level's rows standing as the stock.
        TradeOffer[] offers = trader.offers();
        assertTrue(trader.villagerCareer() >= 1
                        && trader.villagerCareer() <= VillagerTrades.careerCount(trader.profession()),
                "the career rolls inside the profession's table ("
                        + trader.villagerCareer() + " of "
                        + VillagerTrades.careerCount(trader.profession()) + ")");
        assertEquals(1, trader.villagerCareerLevel(), "the opening level");
        assertEquals(VillagerTrades.levelRows(trader.profession(),
                        trader.villagerCareer(), 1).length, offers.length,
                "the level-1 rows stand as the stock");
        for (int i = 0; i < offers.length; i++) {
            assertFalse(offers[i].buy1().isEmpty(), "every offer takes a buy");
            assertFalse(offers[i].result().isEmpty(), "every offer pays a result");
            assertEquals(VillagerTrades.STOCK_MAX_USES, trader.offerMaxUses(i),
                    "the fresh budget is the reference's 7");
            assertEquals(0, trader.offerUses(i), "fresh offers are unused");
        }
    }

    @Test
    void careerTablesCarryTheThematicStock() {
        // The reference's own tables: the farmer's careers open with
        // wheat/potato/carrot buys or the fisherman's string, the
        // librarian's with paper + the enchanted book, the priest's with
        // the rotten flesh, the smith's with the coal, the butcher's with
        // the porkchop (the level-1 rows of every career of every
        // profession).
        Random random = new Random(11L);
        for (int profession = 0; profession < 5; profession++) {
            for (int career = 1; career <= VillagerTrades.careerCount(profession); career++) {
                VillagerTrades.TradeSource[] rows = VillagerTrades.levelRows(
                        profession, career, 1);
                assertTrue(rows.length > 0,
                        "every career opens with rows (p" + profession + " c" + career + ")");
                for (VillagerTrades.TradeSource row : rows) {
                    TradeOffer offer = row.provide(random);
                    assertFalse(offer.buy1().isEmpty());
                    assertFalse(offer.result().isEmpty());
                }
            }
        }
        // The thematic anchors (the reference's own level-1 rows).
        boolean farmerWheat = false;
        for (VillagerTrades.TradeSource row : VillagerTrades.levelRows(0, 1, 1)) {
            TradeOffer offer = row.provide(new Random(12L));
            farmerWheat |= offer.buy1().type().equals(BuiltinItems.WHEAT);
        }
        assertTrue(farmerWheat, "the farmer career buys wheat");
        boolean librarianPaper = false;
        boolean librarianBook = false;
        for (VillagerTrades.TradeSource row : VillagerTrades.levelRows(1, 1, 1)) {
            TradeOffer offer = row.provide(new Random(13L));
            librarianPaper |= offer.buy1().type().equals(BuiltinItems.PAPER);
            librarianBook |= offer.result().type().equals(BuiltinItems.ENCHANTED_BOOK);
        }
        assertTrue(librarianPaper, "the librarian buys paper");
        assertTrue(librarianBook, "the librarian's enchanted book");
        boolean priestFlesh = false;
        for (VillagerTrades.TradeSource row : VillagerTrades.levelRows(2, 1, 1)) {
            TradeOffer offer = row.provide(new Random(14L));
            priestFlesh |= offer.buy1().type().equals(BuiltinItems.ROTTEN_FLESH);
        }
        assertTrue(priestFlesh, "the priest buys the rotten flesh");
    }

    @Test
    void theNegativeAmountFlipsTheTradeDirection() {
        // EmeraldsForVillagerTypeItem's own pair: the positive roll sells
        // one item for emeralds x the roll; the negative one SELLS the
        // item x -roll for a single emerald (the reference's bread row).
        VillagerTrades.TradeSource bread = null;
        for (VillagerTrades.TradeSource row : VillagerTrades.levelRows(0, 1, 1)) {
            TradeOffer probe = row.provide(new Random(15L));
            if (probe.buy1().type().equals(BuiltinItems.EMERALD)
                    && probe.result().type().equals(BuiltinItems.BREAD)) {
                bread = row; // the always-negative bread band (-4, -2]
                break;
            }
        }
        assertNotNull(bread, "the farmer's bread row");
        TradeOffer negative = bread.provide(new Random(16L) {
            @Override public int nextInt(int bound) {
                return bound == 3 ? 0 : super.nextInt(bound); // the (-4, -2] band's -4
            }
        });
        // The forced roll: RandomAmount(-4, -2).pick — min + nextInt(3):
        // the 0 forces -4, so one emerald buys 4 bread.
        assertEquals(BuiltinItems.EMERALD, negative.buy1().type(),
                "the negative roll buys with a single emerald");
        assertEquals(1, negative.buy1().count());
        assertEquals(BuiltinItems.BREAD, negative.result().type());
        assertEquals(4, negative.result().count(), "the -4 roll sells four bread");
    }

    @Test
    void theTradeChargesAndArmsTheLevelUp() {
        // trade lines 604-633: the use charges; the FIRST use arms the
        // 40-tick level-up countdown; the emerald payments absorb into the
        // riches; the XP band is 3 + nextInt(4) + 5 on the armed arm.
        MobEntity trader = villager(21L);
        TradeOffer[] offers = trader.offers();
        assertTrue(offers.length > 0);
        int xp = trader.villagerTrade(0);
        assertEquals(1, trader.offerUses(0), "the use charges");
        assertTrue(xp >= 8 && xp <= 11, "the first trade's armed XP band (saw " + xp + ")");
        assertTrue(trader.villagerWilling(), "the successful trade makes the body willing");
        // The emerald buys absorb into the riches.
        int richesBefore = trader.villagerRiches();
        for (int i = 0; i < offers.length; i++) {
            if (offers[i].buy1().type().equals(BuiltinItems.EMERALD)) {
                trader.villagerTrade(i);
                assertTrue(trader.villagerRiches() > richesBefore,
                        "the emerald payment absorbs into the riches");
                break;
            }
        }
    }

    @Test
    void theLevelUpWalkReGreensSpentOutOffersAndAppends() {
        // mobAiTick lines 377-405: the countdown burns while no customer
        // stands; at zero the armed level-up grows every SPENT-OUT offer's
        // budget by nextInt(6) + nextInt(6) + 2 and appends the career's
        // next level.
        MobEntity trader = villager(31L);
        TradeOffer[] offers = trader.offers();
        // Spend the first offer out.
        while (trader.offerAvailable(0)) {
            trader.chargeOfferUse(0);
        }
        int spentBudget = trader.offerMaxUses(0);
        int stockSize = trader.offers().length;
        int level = trader.villagerCareerLevel();
        // The trade's arm: any trade arms the countdown (the spent-out
        // row cannot trade — use a fresh one).
        int freshRow = -1;
        for (int i = 0; i < offers.length; i++) {
            if (trader.offerAvailable(i)) {
                freshRow = i;
                break;
            }
        }
        assertTrue(freshRow >= 0, "a fresh row stands");
        trader.villagerTrade(freshRow);
        // The 40-tick burn.
        for (int tick = 0; tick < 40; tick++) {
            trader.tickVillagerLevelUp();
        }
        assertTrue(trader.offerMaxUses(0) > spentBudget,
                "the spent-out row's budget grew (the re-green)");
        assertEquals(level + 1, trader.villagerCareerLevel(), "the career advanced");
        assertTrue(trader.offers().length > stockSize
                        || VillagerTrades.levelRows(trader.profession(),
                                trader.villagerCareer(), trader.villagerCareerLevel()).length == 0,
                "the next level's rows appended (or the table ran out)");
    }

    @Test
    void useBudgetGreysTheOfferOutAndChargesCount() {
        MobEntity trader = villager(9);
        TradeOffer offer = trader.offers()[0];
        assertTrue(trader.offerAvailable(0), "a fresh offer trades");
        for (int i = 0; i < trader.offerMaxUses(0); i++) {
            trader.chargeOfferUse(0);
        }
        assertEquals(trader.offerMaxUses(0), trader.offerUses(0),
                "the counter counts every trade");
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
                () -> new TradeOffer(ItemStack.of(BuiltinItems.WHEAT, 20),
                        ItemStack.of(BuiltinItems.EMERALD, 1), 0),
                "a non-positive budget is broken");
        // The single-buy form carries no second buy.
        TradeOffer single = new TradeOffer(ItemStack.of(BuiltinItems.WHEAT, 20),
                ItemStack.of(BuiltinItems.EMERALD, 1), 7);
        assertFalse(single.hasSecondBuy());
    }
}
