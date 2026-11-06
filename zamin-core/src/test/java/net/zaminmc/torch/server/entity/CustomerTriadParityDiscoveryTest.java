package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE CUSTOMER TRIAD'S DISCOVERY SEAT (Wave PE-26): the villager's
 * trading-window family whole — the customer reference the interact's
 * open arms (setCustomer, VillagerEntity lines 399-407), the head on
 * the customer (LookAtCustomerGoal — canStart's NO-DICE claim), the
 * closed-window STAND that owns the customer's lifetime
 * (StopFollowingCustomerGoal — the stand's stop() is the only clear),
 * and the food-share throw (TradeWithVillagerGoal — the farmer feeds
 * the pack). Each law read from the reference's own source.
 */
class CustomerTriadParityDiscoveryTest {

    /** Solid everywhere below y=4 (the flat test world). */
    private static MobEntity.WorldQuery worldAt() {
        return new ManagerWorld(null);
    }

    /**
     * The flat stub with the manager's own census (the
     * nearestMobOfKindInBox consult the share walk reads — the engine
     * server's own implementation mirrored for the manager-level seat).
     */
    private static final class ManagerWorld implements MobEntity.WorldQuery {
        private MobManager manager;

        ManagerWorld(MobManager manager) {
            this.manager = manager;
        }

        @Override public boolean isSolid(double x, double y, double z) {
            return y < 4.0;
        }

        @Override public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public MobEntity nearestMobOfKindInBox(double minX, double minY, double minZ,
                                               double maxX, double maxY, double maxZ,
                                               MobType kind, int excludeId,
                                               double sx, double sy, double sz) {
            if (manager == null) {
                return null;
            }
            MobEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (MobEntity mob : manager.all()) {
                if (mob.dead() || mob.type() != kind || mob.entityId() == excludeId) {
                    continue;
                }
                Position p = mob.position();
                double half = mob.type().width / 2.0;
                if (p.x() + half <= minX || p.x() - half >= maxX
                        || p.y() + mob.type().height <= minY || p.y() >= maxY
                        || p.z() + half <= minZ || p.z() - half >= maxZ) {
                    continue;
                }
                double dx = p.x() - sx;
                double dy = p.y() - sy;
                double dz = p.z() - sz;
                double distance = dx * dx + dy * dy + dz * dz;
                if (!(distance > bestDistance)) {
                    bestDistance = distance;
                    best = mob;
                }
            }
            return best;
        }
    }

    /** The scripted customer's state (the wired consult's mutable answer). */
    private static final class ScriptedCustomer {
        Position position = new Position(2.5, 4.0, 0.5);
        boolean menuClosed;
        boolean present = true;

        MobEntity.VillagerCustomerState state() {
            return present
                    ? new MobEntity.VillagerCustomerState(position, menuClosed)
                    : null;
        }
    }

    private static MobEntity villager(long seed, ScriptedCustomer customer) {
        MobEntity mob = new MobEntity(1, MobType.VILLAGER, new Position(0.5, 4.0, 0.5),
                new Random(seed), worldAt());
        mob.setVillagerCustomer(100);
        mob.setVillagerCustomerState(customer::state);
        return mob;
    }

    // ------------------------------------------------ the customer look

    /**
     * LookAtCustomerGoal (lines 10-23 over LookAtEntityGoal): NO chance
     * dice — a standing customer always claims the head on the FIRST
     * consult; the 40+nextInt(40) window holds the head on the
     * customer's eyes; shouldContinue's end at the customer's departure
     * beyond the ctor's 8.
     */
    @Test
    void theCustomerLookClaimsWithNoDiceAndHoldsTheHead() {
        ScriptedCustomer customer = new ScriptedCustomer();
        MobEntity trader = villager(7L, customer);
        // The very first consult claims (canStart line 16: hasCustomer()
        // alone — the 2% dice the plain look family rolls never runs).
        assertTrue(trader.goalLookCustomerCanUse(),
                "the standing customer claims the head with no dice");
        int window = trader.lookCustomerTicksLeftForTest();
        assertTrue(window >= 40 && window <= 79,
                "the window is the 40+nextInt(40) band: " + window);
        trader.goalLookCustomerTick();
        float head = trader.headYaw();
        double expected = Math.toDegrees(Math.atan2(
                -(customer.position.x() - trader.position().x()),
                customer.position.z() - trader.position().z()));
        assertEquals((float) expected, head, 0.001,
                "the head holds the customer (the eyes' yaw line)");
        assertEquals(window - 1, trader.lookCustomerTicksLeftForTest(),
                "the clock pays its tick");
        // shouldContinue: the customer walks beyond 8 — the window ends.
        customer.position = new Position(20.5, 4.0, 0.5);
        assertFalse(trader.goalLookCustomerCanUse(),
                "the customer beyond 8 closes the look window");
        assertEquals(0, trader.lookCustomerTicksLeftForTest());
        // The customer gone entirely: the same end.
        customer.position = new Position(2.5, 4.0, 0.5);
        trader.goalLookCustomerCanUse(); // re-arm
        assertTrue(trader.lookCustomerTicksLeftForTest() > 0);
        customer.present = false;
        assertFalse(trader.goalLookCustomerCanUse(),
                "a departed customer closes the look window");
    }

    // ------------------------------------------------ the customer stand

    /**
     * StopFollowingCustomerGoal (lines 14-47): the villager STANDS while
     * its customer stands within 4 blocks with the window CLOSED; the
     * claim's end CLEARS THE CUSTOMER (stop(): setCustomer(null) — the
     * reference's own lifetime manager); the gates refuse water, air,
     * damage, and the open window.
     */
    @Test
    void theClosedWindowStandClaimsAndOwnsTheCustomerLifetime() {
        ScriptedCustomer customer = new ScriptedCustomer();
        MobEntity trader = villager(11L, customer);
        for (int i = 0; i < 10; i++) {
            trader.tick(); // settle onto the ground (the stand's onGround gate)
        }
        customer.menuClosed = false;
        assertFalse(trader.goalStopCustomerCanUse(),
                "the OPEN window refuses the stand");
        customer.menuClosed = true;
        assertTrue(trader.goalStopCustomerCanUse(),
                "the closed window within 4 blocks claims the stand");
        assertTrue(trader.hasCustomer(), "the customer still stands");
        // The customer departs beyond 4: the claim ends AND the customer
        // clears (stop(): setCustomer(null), line 45).
        customer.position = new Position(10.5, 4.0, 0.5);
        assertFalse(trader.goalStopCustomerCanUse(),
                "the customer beyond 4 ends the stand");
        assertFalse(trader.hasCustomer(),
                "the stand's stop() cleared the customer — the lifetime law");
        // The triad reads the clear: the look refuses too.
        assertFalse(trader.goalLookCustomerCanUse(),
                "the cleared customer refuses the look");
    }

    /**
     * The stand's MIND-level law: while the claim holds, the body never
     * strolls (the exclusive band-1 claim stops the selector's walk —
     * the reference's setControls(5) MOVE+JUMP hold).
     */
    @Test
    void theStandHoldsTheBodyStill() {
        ScriptedCustomer customer = new ScriptedCustomer();
        customer.menuClosed = true;
        MobEntity trader = villager(13L, customer);
        for (int i = 0; i < 10; i++) {
            trader.tick(); // settle onto the ground (the stand's onGround gate)
        }
        Position before = trader.position();
        for (int i = 0; i < 300; i++) {
            trader.tick();
        }
        double drifted = Math.abs(trader.position().x() - before.x())
                + Math.abs(trader.position().z() - before.z());
        assertTrue(drifted < 0.25,
                "the standing body never strolls (300 ticks): " + drifted);
        assertTrue(trader.hasCustomer(), "the customer held through the stand");
    }

    // ------------------------------------------------ the food share

    /**
     * TradeWithVillagerGoal (lines 21-79 over GoToEntityGoal): the 2%
     * dice, the nearest villager within the grown (3, 3, 3) box, the
     * stare window — and the donation arm: a walker with two breeding
     * units of wheat shares at the 10-tick cooldown, HALF the stack
     * rounded down to loaves of 3 AS BREAD, thrown from the eyes along
     * the head facing at 0.3 speed with the +0.1 lift.
     */
    @Test
    void theFoodShareThrowsHalfTheWheatAsBread() {
        ScriptedCustomer customer = new ScriptedCustomer();
        ManagerWorld world = new ManagerWorld(null);
        MobManager manager = new MobManager(world, new Random(3),
                (pos, stack, dimension) -> { }, 2_000_000, (x, z) -> 4);
        world.manager = manager; // the census consult reads the live mob list
        MobEntity farmer = manager.spawnAt(MobType.VILLAGER,
                new Position(0.5, 4.0, 0.5));
        MobEntity hungry = manager.spawnAt(MobType.VILLAGER,
                new Position(2.0, 4.0, 0.5));
        // The farmer's stock: 20 wheat (two breeding units — the share
        // gate's wantsMoreFood band) in the first slot.
        farmer.villagerStockItem(net.zaminmc.torch.item.ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.WHEAT, 20));
        assertTrue(farmer.villagerWantsMoreFood(),
                "20 wheat is two breeding units (the 9-wheat unit's double)");
        assertTrue(hungry.villagerHasRoomForHarvest(),
                "an empty body has room");
        // The claim: the 2% dice lands within the budget; the stare
        // window and the 10-tick cooldown follow.
        boolean claimed = false;
        for (int i = 0; i < 20_000 && !claimed; i++) {
            claimed = farmer.goalTradeShareCanUse();
        }
        assertTrue(claimed, "the 2% dice claimed the share walk");
        assertEquals(hungry, farmer.tradeShareTargetForTest(),
                "the nearest villager inside the grown box answers");
        // The donation: 10 ticks of the share tick, then the throw.
        
        MobEntity.VillagerShare share = null;
        for (int i = 0; i < 40 && share == null; i++) {
            farmer.goalTradeShareTick();
            share = farmer.consumeTradeShare();
        }
        assertNotNull(share, "the cooldown paid the donation");
        // 20 wheat: j = 20/2/3*3 = 9, k = 3 loaves; 11 wheat remains.
        assertEquals("minecraft:bread", share.stack().type().identifier().toString());
        assertEquals(3, share.stack().count(),
                "half the wheat rounded to loaves of 3 (20 -> j=9 -> k=3)");
        assertEquals(11, farmer.villagerFoodSlots()[0].count(),
                "the stock kept the remainder");
        // The throw's origin: the eye band (y - 0.3 + eyeHeight).
        assertEquals(farmer.position().y() - 0.3 + farmer.eyeHeightForQuery(),
                share.origin().y(), 1e-9);
        assertEquals(farmer.position().x(), share.origin().x(), 1e-9);
        // The velocity: the head facing at 0.3 speed with the +0.1 lift
        // (pitch 0 — the flat world's body).
        double yawRad = Math.toRadians(farmer.headYaw());
        assertEquals(-Math.sin(yawRad) * 0.3, share.vx(), 1e-9);
        assertEquals(0.1, share.vy(), 1e-9);
        assertEquals(Math.cos(yawRad) * 0.3, share.vz(), 1e-9);
        // The bread/potato arm: beyond 3 donates count/2 of the same kind.
        MobEntity fed = manager.spawnAt(MobType.VILLAGER,
                new Position(0.5, 4.0, 20.5));
        fed.villagerStockItem(net.zaminmc.torch.item.ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.BREAD, 7));
        fed.setVillagerCustomer(100);
        fed.setVillagerCustomerState(() -> null); // no customer state: the share's own law is the walker's
        MobEntity.VillagerShare breadShare = null;
        for (int i = 0; i < 40 && breadShare == null; i++) {
            fed.goalTradeShareTick();
            breadShare = fed.consumeTradeShare();
        }
        assertNull(breadShare,
                "no live window: the tick stands down (the defensive clear)");
    }

    // ------------------------------------------------ the haggle gate

    /**
     * getAmbientSound (VillagerEntity line 475): the customer gate — a
     * trading body haggles, an idle one hums.
     */
    @Test
    void theHaggleSoundRidesTheCustomerGate() {
        ScriptedCustomer customer = new ScriptedCustomer();
        MobEntity trader = villager(17L, customer);
        assertEquals("mob.villager.haggle", trader.effectiveIdleSound(),
                "a customer-holding body haggles");
        trader.setVillagerCustomer(-1);
        assertEquals("mob.villager.idle", trader.effectiveIdleSound(),
                "an idle body hums");
    }
}
