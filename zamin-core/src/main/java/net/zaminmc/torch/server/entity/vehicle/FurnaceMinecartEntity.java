package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;

import java.util.Random;

/**
 * The furnace minecart (Slice 66 — the reference port of
 * vehicle/FurnaceMinecartEntity.java): the coal-fed pusher cart.
 *
 * <p><b>The fuel (interact lines 106-117, 1:1):</b> a right-click with
 * coal adds 3600 ticks of fuel (the consume is the caller's) and the
 * push direction becomes the player's offset from the cart — the cart
 * rolls AWAY from the player. The fuel burns one per tick
 * (tick lines 44-47); an empty tank zeroes the push vector.</p>
 *
 * <p><b>The push (moveOnRail + applySlowdown lines 74-105, 1:1):</b> the
 * push vector normalizes; a push opposing the current motion zeroes it
 * (the anti-reverse gate); the cart accelerates along the push up to the
 * furnace's own 0.2 max speed (getMaxSpeed line 61 — every other cart
 * caps at 0.4). Unfueled, the cart coasts with the family's own drag.</p>
 *
 * <p><b>The lit byte (registerSyncedData slot 16 + tick line 52):</b> the
 * fueled flag rides the wire metadata (the client swaps the furnace
 * texture). The cart-vs-cart push (the reference's applyCollisions — a
 * fueled furnace cart shoves every cart it touches) rides the
 * cart-collision model's own slice, ledgered.</p>
 */
public class FurnaceMinecartEntity extends MinecartEntity
        implements net.zaminmc.torch.entity.VariantState.Furnace {

    /** The coal feed (interact line 102: fuel += 3600). */
    public static final int COAL_FUEL_TICKS = 3600;
    /** The furnace's own max speed (getMaxSpeed line 61: 0.2). */
    public static final double FURNACE_MAX_SPEED = 0.2;

    private int fuel;
    private double pushX;
    private double pushZ;

    public FurnaceMinecartEntity(int entityId, Position position, Random random) {
        super(entityId, Kind.FURNACE_MINECART, position, random);
    }

    /** The wire's minecart type index: 2 = furnace. */
    @Override
    public int minecartTypeIndex() {
        return 2;
    }

    /**
     * The furnace's own on-rail move clamp (getMaxSpeed lines 59-61:
     * 0.2 — every other cart moves at 0.4): the fueled pusher rolls at
     * a tenth of the plain family's 0.4-saturated state.
     */
    @Override
    protected double getMaxSpeedOnRail() {
        return FURNACE_MAX_SPEED;
    }

    /**
     * The furnace's own drag (applySlowdown lines 91-110, 1:1): a live
     * push damps the carried velocity to 0.8 and hands it the push
     * vector's whole unit (the coal-fed ram); no push (the empty tank
     * or the anti-reverse zero) coasts at 0.98 — and then the BASE
     * rider-gated triple rides on top ({@code super.applySlowdown()},
     * the reference's own call). The push re-normalizes here (the
     * reference's own double walk — moveOnRail's scale arm is inert
     * under this re-normalize, kept verbatim).
     */
    @Override
    protected void applySlowdown() {
        double squared = pushX * pushX + pushZ * pushZ;
        if (squared > 1.0E-4) {
            double length = Math.sqrt(squared);
            pushX /= length;
            pushZ /= length;
            setVelocity(velocityX() * 0.8 + pushX, 0.0, velocityZ() * 0.8 + pushZ);
        } else {
            setVelocity(velocityX() * 0.98, 0.0, velocityZ() * 0.98);
        }
        super.applySlowdown();
    }

    /** @return the remaining fuel ticks. */
    public int fuel() {
        return fuel;
    }

    /** @return whether the tank holds fuel (the lit byte's own state). */
    public boolean lit() {
        return fuel > 0;
    }

    /** The push vector (the rolling direction). */
    public double pushX() {
        return pushX;
    }

    /** The push vector (the rolling direction). */
    public double pushZ() {
        return pushZ;
    }

    /**
     * The public variant view (§27.3 — the furnace cart's own state):
     * the engine object IS the handle (§27.5). Simulation context.
     */
    @Override
    public java.util.Optional<net.zaminmc.torch.entity.VariantState> variantState() {
        return java.util.Optional.of(this);
    }

    // ----------------------------------------------------------
    // The VariantState.Furnace view (§27.3 — the API's own reads).
    // The lit/pushX/pushZ signatures already carry the view's reads;
    // only the fuel's name bridges.
    // ----------------------------------------------------------

    @Override public int fuelTicks() {
        return fuel();
    }

    /**
     * The fuel feed + the push aim (interact lines 106-117): a coal in
     * hand adds 3600 ticks ({@code fed} the caller's consume verdict),
     * and the push vector becomes the player's offset from the cart —
     * the cart rolls away from wherever the player stands.
     */
    public void feedAndAim(double playerX, double playerZ, boolean fed) {
        if (fed) {
            this.fuel += COAL_FUEL_TICKS;
        }
        this.pushX = position().x() - playerX;
        this.pushZ = position().z() - playerZ;
    }

    /**
     * The kind's tick (tick lines 44-52 + the push walks, 1:1): the fuel
     * burns, the empty tank zeroes the push, and the fueled push carries
     * the cart along the rail (the anti-reverse gate, the 0.2 cap).
     */
    @Override
    protected void tickKind(net.zaminmc.torch.server.entity.vehicle.VehicleEntity.WorldQuery world) {
        if (fuel > 0) {
            fuel--;
        }
        if (fuel <= 0) {
            pushX = 0.0;
            pushZ = 0.0;
        }
        double pushSquared = pushX * pushX + pushZ * pushZ;
        if (pushSquared > 1.0E-4 && fuel > 0) {
            // The push walk (moveOnRail lines 74-90): normalize, and a
            // push opposing the current motion zeroes it (the furnace
            // never brakes its own roll).
            double length = Math.sqrt(pushSquared);
            pushX /= length;
            pushZ /= length;
            if (pushX * velocityX() + pushZ * velocityZ() < 0.0) {
                pushX = 0.0;
                pushZ = 0.0;
            } else {
                double scaled = Math.min(length, FURNACE_MAX_SPEED) / FURNACE_MAX_SPEED;
                pushX *= scaled;
                pushZ *= scaled;
            }
        }
        super.tickKind(world);
        // The reference's own walk order keeps the push arm INSIDE
        // applySlowdown (the override above) — the fueled ram and the
        // 0.98 coast both ride the on-rail drag call site alone; the
        // off-rail furnace never pushes (moveOffRail owns the gravity
        // walk, the reference's own shape).
    }
}
