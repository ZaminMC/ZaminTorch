package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave RD — THE MINECART'S RIDDEN DRAG (the re-pin's named row: "the
 * reference's ridden-body velocity triple the seat now carries") and
 * the roll laws of moveOnRail's own tail, each read from the reference's
 * source 1:1:
 *
 * <ul>
 * <li>THE DRAG TRIPLE (applySlowdown lines 497-506): a body holding the
 * seat carries {@code 0.997 / 0.0 / 0.997} — the cart barely slows; an
 * empty cart grinds at {@code 0.96 / 0.0 / 0.96} — the abandoned cart
 * stops dead within the from-rest floor's reach. The pre-fix engine
 * carried NO drag at all for the plain family (the stale javadoc's
 * "historical 0.997" claim was never in the code): an abandoned cart
 * rolled forever at its launch speed.</li>
 * <li>THE RIDER'S MOVE SCALE (moveOnRail lines 438-441): a ridden cart
 * advances at 0.75 of its velocity state each tick — the seat's own
 * law, the pre-fix engine's move un-scaled.</li>
 * <li>THE MOVE CLAMP (getMaxSpeed lines 313-315 + the clamp at lines
 * 443-445): the position's per-tick advance caps at 0.4 (the furnace's
 * own 0.2) — NEVER the carried state, so a booster-saturated state
 * rides above the clamp it moves at. The pre-fix engine capped the
 * STATE at a hand-rolled 0.3 at the walk's head.</li>
 * <li>THE WALL LAUNCH (moveOnRail lines 485-494): the powered golden
 * rail's from-rest consult reads the neighbor solids — the cart rolls
 * AWAY from the block behind it at ±0.02, and a wall-less rest NEVER
 * launches. The pre-fix engine launched every resting cart at 0.06 in
 * the look's direction, wall or none.</li>
 * <li>THE HOPPER'S FULLNESS DRAG (InventoryMinecartEntity.applySlowdown
 * lines 167-173 — the hopper cart EXTENDS the inventory family): the
 * five slots' cargo the same walk the chest cart's 27 run, an empty
 * cart 0.995, a full one 0.98. The pre-fix engine gave the hopper cart
 * NO drag at all (the plain family's 1.0).</li>
 * </ul>
 *
 * <p>The drag's own PLACEMENT rides the reference's call site (line
 * 454, AFTER the move): the per-tick move runs at the un-damped state
 * and the drag lands on the carry alone — the observable the wire and
 * the family pushes read.</p>
 */
class MinecartRiddenDragParityDiscoveryTest {

    /** A wall cell that never matches (the wall-less consult). */
    private static final int NO_WALL = Integer.MIN_VALUE;

    /**
     * The endless east-west rail at y=4, z=0 — the powered-rail walk
     * parameterized ({@code railPower}: 1 the powered golden boost, -1
     * the unpowered brake, 0 plain), the wall cell the launch consult
     * reads (the stub's own solid at the rail's own Y).
     */
    private static VehicleEntity.WorldQuery railWorld(int railPower, int wallCellX) {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return (int) Math.floor(x) == wallCellX
                        && (int) Math.floor(y) == 4
                        && (int) Math.floor(z) == 0;
            }

            @Override public int railAxisAt(double x, double y, double z) {
                if (y < 3.9 || y > 5.0) {
                    return -1;
                }
                return (int) Math.floor(z) == 0 ? 1 : -1;
            }

            @Override public int railPowerAt(double x, double y, double z) {
                return railPower;
            }
        };
    }

    // ------------------------------------------------------------------
    // Seat 1 — the drag triple gates on the seat (0.997 vs 0.96)
    // ------------------------------------------------------------------

    /**
     * THE RIDER-GATED TRIPLE: two carts launched at the same 0.3 on the
     * same plain track — the empty one decays 0.96 per tick (line 503),
     * the seated one 0.997 (line 498). RED on the pre-fix engine: both
     * stayed at 0.3 (the plain family carried no drag at all).
     */
    @Test
    void theDragTripleGatesOnTheSeat() {
        VehicleEntity.WorldQuery plain = railWorld(0, NO_WALL);

        MinecartEntity empty = new MinecartEntity(9801,
                new Position(5.5, 4.06, 0.5), new Random(11L));
        empty.setVelocity(0.3, 0.0, 0.0);
        for (int i = 0; i < 30; i++) {
            empty.tick(plain);
        }
        assertEquals(0.3 * Math.pow(0.96, 30), Math.abs(empty.velocityX()), 1e-9,
                "the abandoned cart decays 0.96 per tick (applySlowdown line 503)");

        MinecartEntity ridden = new MinecartEntity(9802,
                new Position(5.5, 4.06, 0.5), new Random(12L));
        assertTrue(ridden.mount(4242), "the seat takes the rider");
        ridden.setVelocity(0.3, 0.0, 0.0);
        for (int i = 0; i < 30; i++) {
            ridden.tick(plain);
        }
        assertEquals(0.3 * Math.pow(0.997, 30), Math.abs(ridden.velocityX()), 1e-9,
                "the seated rider carries the 0.997 drag (applySlowdown line 498)");
    }

    /**
     * THE ABANDONED CART'S DEAD STOP: the 0.96 decay walks the state
     * under the from-rest floor (0.01) and the walk zeroes it — the
     * position freezes mid-track. RED on the pre-fix engine: the cart
     * rolled on forever at 0.3.
     */
    @Test
    void theAbandonedCartStopsDead() {
        VehicleEntity.WorldQuery plain = railWorld(0, NO_WALL);
        MinecartEntity cart = new MinecartEntity(9803,
                new Position(5.5, 4.06, 0.5), new Random(13L));
        cart.setVelocity(0.3, 0.0, 0.0);
        for (int i = 0; i < 120; i++) {
            cart.tick(plain);
        }
        assertEquals(0.0, cart.velocityX(), 1e-9,
                "the 0.96 decay walked the state under the from-rest floor");
        double frozenAt = cart.position().x();
        cart.tick(plain);
        cart.tick(plain);
        assertEquals(frozenAt, cart.position().x(), 1e-9,
                "the dead-stopped cart never moves again");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the rider's 0.75 move scale (lines 438-441)
    // ------------------------------------------------------------------

    /**
     * THE RIDER'S MOVE SCALE: a ridden cart advances at 0.75 of its
     * state each tick — 0.3 × 0.75 = 0.225 — while the carried state
     * itself stays un-scaled (the drag alone damps it: 0.3 × 0.997).
     * The un-ridden control moves the whole state. RED on the pre-fix
     * engine: the ridden move ran un-scaled (0.3).
     */
    @Test
    void theRiderScalesTheMove() {
        VehicleEntity.WorldQuery plain = railWorld(0, NO_WALL);

        MinecartEntity empty = new MinecartEntity(9804,
                new Position(5.5, 4.06, 0.5), new Random(14L));
        empty.setVelocity(0.3, 0.0, 0.0);
        double emptyBefore = empty.position().x();
        empty.tick(plain);
        assertEquals(0.3, empty.position().x() - emptyBefore, 1e-9,
                "the un-ridden move carries the whole state");
        assertEquals(0.3 * 0.96, empty.velocityX(), 1e-9,
                "the un-ridden carry drags 0.96");

        MinecartEntity ridden = new MinecartEntity(9805,
                new Position(5.5, 4.06, 0.5), new Random(15L));
        ridden.mount(4243);
        ridden.setVelocity(0.3, 0.0, 0.0);
        double riddenBefore = ridden.position().x();
        ridden.tick(plain);
        assertEquals(0.3 * 0.75, ridden.position().x() - riddenBefore, 1e-9,
                "the ridden move scales at 0.75 (moveOnRail lines 438-441)");
        assertEquals(0.3 * 0.997, ridden.velocityX(), 1e-9,
                "the state itself never scales — the drag alone damps it");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the move clamp at getMaxSpeed (the state above it)
    // ------------------------------------------------------------------

    /**
     * THE MOVE CLAMP: a cart whose state (0.96) sits above getMaxSpeed's
     * 0.4 moves at exactly 0.4 — and the CARRY keeps the state above
     * the clamp (dragged 0.96, never clamped). RED on the pre-fix
     * engine: the walk's head capped the state at the hand-rolled 0.3,
     * so both the move and the carry read 0.3.
     */
    @Test
    void theMoveClampsWhileTheStateRidesAbove() {
        VehicleEntity.WorldQuery plain = railWorld(0, NO_WALL);
        MinecartEntity cart = new MinecartEntity(9806,
                new Position(5.5, 4.06, 0.5), new Random(16L));
        cart.setVelocity(0.96, 0.0, 0.0);
        double before = cart.position().x();
        cart.tick(plain);
        assertEquals(0.4, cart.position().x() - before, 1e-9,
                "the move clamps at getMaxSpeed's 0.4 (lines 443-445)");
        assertEquals(0.96 * 0.96, cart.velocityX(), 1e-9,
                " the carried state rides above the clamp, dragged 0.96 alone");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the powered rail's from-rest wall consult (lines 485-494)
    // ------------------------------------------------------------------

    /**
     * THE WALL LAUNCH: a resting cart on a POWERED golden rail reads
     * the neighbor solids — the west wall launches it EAST at +0.02,
     * the east wall WEST at -0.02, and a wall-less rest NEVER launches
     * (the vanilla powered rail holds a resting cart until a block
     * stands behind it). RED on the pre-fix engine: every resting cart
     * launched at 0.06 in the look's direction, wall or none.
     */
    @Test
    void thePoweredRailsFromRestLaunchConsultsTheWall() {
        // The west wall: the cart at cell 5, the wall at cell 4.
        MinecartEntity westBacked = new MinecartEntity(9807,
                new Position(5.5, 4.06, 0.5), new Random(17L));
        westBacked.tick(railWorld(1, 4));
        assertEquals(0.02, westBacked.velocityX(), 1e-9,
                "the west wall launches east at +0.02 (lines 485-486)");
        assertEquals(5.5, westBacked.position().x(), 1e-9,
                "the from-rest move is zero — the launch arms the NEXT tick");

        // The east wall: the wall at cell 6.
        MinecartEntity eastBacked = new MinecartEntity(9808,
                new Position(5.5, 4.06, 0.5), new Random(18L));
        eastBacked.tick(railWorld(1, 6));
        assertEquals(-0.02, eastBacked.velocityX(), 1e-9,
                "the east wall launches west at -0.02 (lines 487-488)");

        // The wall-less rest: no launch at all.
        MinecartEntity openRest = new MinecartEntity(9809,
                new Position(5.5, 4.06, 0.5), new Random(19L));
        openRest.tick(railWorld(1, NO_WALL));
        assertEquals(0.0, openRest.velocityX(), 1e-9,
                "a wall-less rest never launches (the vanilla powered rail)");
        assertEquals(5.5, openRest.position().x(), 1e-9,
                "the wall-less cart stays at rest");
    }

    /**
     * THE BOOST'S OWN SHAPE (lines 478-484): a ROLLING cart over the
     * powered golden rail gains 0.06 along its rolling direction, AFTER
     * the drag — 0.3 → (0.3 × 0.96) + 0.06 = 0.348. RED on the pre-fix
     * engine: the boost landed before a drag that never was, capped at
     * 0.4 on the state (0.3 + 0.06 = 0.36 — a different number).
     */
    @Test
    void theRollingBoostLandsAfterTheDrag() {
        MinecartEntity cart = new MinecartEntity(9810,
                new Position(5.5, 4.06, 0.5), new Random(20L));
        cart.setVelocity(0.3, 0.0, 0.0);
        cart.tick(railWorld(1, NO_WALL));
        assertEquals(0.3 * 0.96 + 0.06, cart.velocityX(), 1e-9,
                "the boost adds 0.06 to the DRAGGED carry (the reference's"
                        + " own tail placement)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the hopper cart's fullness drag (the inventory family)
    // ------------------------------------------------------------------

    /** The inert transfer host: the drag seat never transfers. */
    private static final class NoTransfers implements HopperMinecartEntity.TransferHost {
        @Override public boolean transferItems(HopperMinecartEntity cart) {
            return false;
        }
    }

    /**
     * THE HOPPER'S FULLNESS DRAG: the hopper cart extends the inventory
     * family (InventoryMinecartEntity.applySlowdown lines 167-173) — an
     * empty cart coasts at 0.995 (the 15-signal arm), a full one at
     * 0.98. RED on the pre-fix engine: the hopper inherited the plain
     * family's no-drag 1.0 — both carts rolled on at 0.3.
     */
    @Test
    void theHopperCartCarriesTheInventoryFullnessDrag() {
        VehicleEntity.WorldQuery plain = railWorld(0, NO_WALL);

        HopperMinecartEntity empty = new HopperMinecartEntity(9811,
                new Position(5.5, 4.06, 0.5), new Random(21L), new NoTransfers());
        empty.setVelocity(0.3, 0.0, 0.0);
        for (int i = 0; i < 20; i++) {
            empty.tick(plain);
        }
        assertEquals(0.3 * Math.pow(0.995, 20), Math.abs(empty.velocityX()), 1e-9,
                "the empty hopper cart coasts at 0.995 (the 15-signal arm)");

        HopperMinecartEntity full = new HopperMinecartEntity(9812,
                new Position(5.5, 4.06, 0.5), new Random(22L), new NoTransfers());
        for (int slot = 0; slot < 5; slot++) {
            full.hopper().setItem(slot,
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.COAL, 64));
        }
        full.setVelocity(0.3, 0.0, 0.0);
        for (int i = 0; i < 20; i++) {
            full.tick(plain);
        }
        assertEquals(0.3 * Math.pow(0.98, 20), Math.abs(full.velocityX()), 1e-9,
                "the full hopper cart drags at 0.98 (the cargo's own weight)");
    }

    // ------------------------------------------------------------------
    // Seat 6 — the rider's W-kick (moveOnRail lines 387-399)
    // ------------------------------------------------------------------

    /**
     * THE W-KICK: a mounted body holding the forward input, from rest
     * alone (the squared state under 0.01 — the second tick's 0.0997
     * still inside the band), kicks 0.1 along the RIDER'S OWN LOOK —
     * the kick-tick move 0.075 (the 0.75 scale), the carry 0.0997, the
     * second kick 0.1997 — and the ROLLING cart gains NOTHING (the
     * third tick coasts). The RIDERLESS cart never kicks at all (the
     * consult reads the rider's own forward speed). RED on the pre-fix
     * engine: the continuous +0.02 drive fed any steered cart forever,
     * rider or none — the riderless control ROLLED.
     */
    @Test
    void theRidersWKicksFromRestAlone() {
        VehicleEntity.WorldQuery plain = railWorld(0, NO_WALL);

        MinecartEntity cart = new MinecartEntity(9813,
                new Position(5.5, 4.06, 0.5), new Random(23L));
        assertTrue(cart.mount(4244), "the seat takes the rider");
        cart.steer(new VehicleEntity.SteerInput(0.0f, 0.98f, false, false, -90.0f));
        cart.tick(plain);
        assertEquals(0.075, cart.position().x() - 5.5, 1e-9,
                "the kick-tick move: 0.1 along the rider's look × 0.75");
        assertEquals(0.1 * 0.997, cart.velocityX(), 1e-9,
                "the kick's carry damped 0.997");

        cart.steer(new VehicleEntity.SteerInput(0.0f, 0.98f, false, false, -90.0f));
        cart.tick(plain);
        assertEquals(0.1997 * 0.997, cart.velocityX(), 1e-9,
                "the second kick (the squared band 0.01 still open at 0.0997)");

        cart.steer(new VehicleEntity.SteerInput(0.0f, 0.98f, false, false, -90.0f));
        cart.tick(plain);
        assertEquals(0.1997 * 0.997 * 0.997, cart.velocityX(), 1e-9,
                "the rolling cart gains NOTHING from the input (the coast)");

        // The riderless control: the input never drives an empty cart
        // (the consult reads the rider's own forward speed — the
        // instanceof LivingEntity gate).
        MinecartEntity riderless = new MinecartEntity(9814,
                new Position(5.5, 4.06, 0.5), new Random(24L));
        riderless.steer(new VehicleEntity.SteerInput(0.0f, 0.98f, false, false, -90.0f));
        riderless.tick(plain);
        assertEquals(0.0, riderless.velocityX(), 1e-9,
                "a riderless cart never kicks (no rider, no drive)");
        assertEquals(5.5, riderless.position().x(), 1e-9,
                "the riderless cart stays at rest");
    }

    /**
     * THE KICK'S LOOK IS THE RIDER'S OWN (the reference reads
     * {@code this.rider.yaw}, never the cart's): a rider looking WEST
     * kicks the resting cart west — the 5-arg SteerInput's riderYaw
     * carrying the player's rotation from the wire. RED on the pre-fix
     * engine: the from-rest direction was the CART'S facing (east).
     */
    @Test
    void theKickReadsTheRidersOwnLook() {
        VehicleEntity.WorldQuery plain = railWorld(0, NO_WALL);
        MinecartEntity cart = new MinecartEntity(9815,
                new Position(5.5, 4.06, 0.5), new Random(25L));
        cart.setRotation(new Rotation(-90.0f, 0.0f)); // the CART faces east
        assertTrue(cart.mount(4245), "the seat takes the rider");
        cart.steer(new VehicleEntity.SteerInput(0.0f, 0.98f, false, false, 90.0f)); // the RIDER looks west
        cart.tick(plain);
        assertEquals(-0.075, cart.position().x() - 5.5, 1e-9,
                "the kick runs along the RIDER's west look, never the cart's east facing");
        assertEquals(-0.1 * 0.997, cart.velocityX(), 1e-9,
                "the carry runs west (the rider's own look)");
    }

    // ------------------------------------------------------------------
    // Seat 7 — the kick's brake cancel (line 395's flag1 = false)
    // ------------------------------------------------------------------

    /**
     * THE BRAKE CANCEL: a kicked cart on an UNPOWERED golden rail never
     * takes the halving that tick (line 395's {@code flag1 = false}) —
     * the kick-tick carry 0.0997, never the halved 0.05 × 0.997. RED
     * on the pre-fix engine: the continuous steer's +0.02 fell under
     * the brake's 0.03 full-stop band — the cart never moved at all.
     */
    @Test
    void theKickCancelsTheBrakeFlag() {
        VehicleEntity.WorldQuery brakeRail = railWorld(-1, NO_WALL);
        MinecartEntity cart = new MinecartEntity(9816,
                new Position(5.5, 4.06, 0.5), new Random(26L));
        assertTrue(cart.mount(4246), "the seat takes the rider");
        cart.steer(new VehicleEntity.SteerInput(0.0f, 0.98f, false, false, -90.0f));
        cart.tick(brakeRail);
        assertEquals(0.075, cart.position().x() - 5.5, 1e-9,
                "the kick-tick move never halved (the brake stood down)");
        assertEquals(0.1 * 0.997, cart.velocityX(), 1e-9,
                "the carry whole — the halving never ran (flag1 = false)");

        // The control: an UNKICKED rolling cart still takes the brake
        // (the cancel arms only on the kick's own tick).
        cart.setVelocity(0.2, 0.0, 0.0);
        cart.tick(brakeRail);
        assertEquals(0.2 * 0.5 * 0.997, cart.velocityX(), 1e-9,
                "the un-kicked roll still halves on the brake rail");
    }
}
