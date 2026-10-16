package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;

import java.util.Random;

/**
 * The TNT minecart (Slice 66 — the reference port of
 * vehicle/TntMinecartEntity.java): the 80-tick fuse the activator rail
 * primes, the detonation whose power rides the cart's own speed, and the
 * impact-detonation law.
 *
 * <p><b>The fuse (prime + tick lines 39-60, 141-153, 1:1):</b> a powered
 * activator rail primes ({@code fuseTicks = 80}, the entity event 10 +
 * the game.tnt.primed sound); the clock burns down one per tick and at
 * zero the cart detonates. A horizontal collision at speed squared
 * {@code >= 0.01} detonates immediately (the reference's
 * {@code collidingHorizontally} arm — the engine's track-end halt is the
 * matching surface; the wall-hit nuance rides the collision model's own
 * slice, ledgered).</p>
 *
 * <p><b>The detonation (explode lines 121-130, 1:1):</b> {@code 4.0 +
 * rand * 1.5 * min(sqrt(vx^2 + vz^2), 5.0)} with fire — a cart at rest
 * bursts at ~4, a cart at full speed at ~9.7. The death arms
 * (dropItems lines 101-112): a fire or explosive death, or a death while
 * moving fast, detonates too; a plain break drops the TNT block item
 * (under doEntityDrops). A burning arrow's hit detonates on the spot
 * (takeDamage lines 63-73 — the arrow-hit arm rides the vehicle hurt
 * path's own slice, ledgered).</p>
 */
public class TntMinecartEntity extends MinecartEntity {

    /** The prime's fuse (prime line 151: 80 ticks). */
    public static final int FUSE_TICKS = 80;
    /** The detonation's base power (explode line 126: 4.0). */
    public static final float BLAST_BASE = 4.0f;
    /** The detonation's speed-scaled bonus band (explode line 126:
     * rand * 1.5 * d0 with d0 the clamped speed). */
    public static final float BLAST_SPEED_SCALE = 1.5f;
    /** The speed clamp (explode line 123: min(sqrt(d), 5.0)). */
    public static final double BLAST_SPEED_CLAMP = 5.0;
    /** The impact-detonation's threshold (tick line 55: 0.01). */
    public static final double IMPACT_SPEED_SQUARED = 0.01;

    /** The explosion sink (the engine's blast walk with fire). */
    public interface BlastSink {
        /** The detonation at the point with the rolled power. */
        void detonate(double x, double y, double z, float power);
    }

    private int fuseTicks = -1;
    private BlastSink blastSink = (x, y, z, power) -> { };
    /**
     * The detonation's own death mark (explode line 129's {@code remove()}):
     * the annihilated body never drops — the reference's remove path runs
     * no dropItems. A plain break (the sword) keeps the ordinary cart
     * drops (the cart item + the TNT block).
     */
    private boolean annihilated;

    public TntMinecartEntity(int entityId, Position position, Random random) {
        super(entityId, Kind.TNT_MINECART, position, random);
    }

    /** Wires the detonation sink (the engine's explosion service). */
    public void setBlastSink(BlastSink sink) {
        this.blastSink = java.util.Objects.requireNonNull(sink, "sink");
    }

    /** The wire's minecart type index: 3 = TNT. */
    @Override
    public int minecartTypeIndex() {
        return 3;
    }

    /** @return the remaining fuse, -1 when unprimed (getFuseTicks). */
    public int fuseTicks() {
        return fuseTicks;
    }

    /** @return whether the cart is primed (isPrimed: fuse > -1). */
    public boolean primed() {
        return fuseTicks > -1;
    }

    /**
     * The prime (prime lines 141-153): the 80-tick fuse arms. The entity
     * event 10 + the primed sound ride the listener wire (the adapter's
     * own arm); the engine's caller fans them.
     */
    public void prime() {
        this.fuseTicks = FUSE_TICKS;
    }

    /** The fuse's detonation roll (explode lines 121-130, 1:1). */
    private void explode() {
        double d0 = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
        if (d0 > BLAST_SPEED_CLAMP) {
            d0 = BLAST_SPEED_CLAMP;
        }
        float power = (float) (BLAST_BASE + random().nextDouble() * BLAST_SPEED_SCALE * d0);
        blastSink.detonate(position().x(), position().y(), position().z(), power);
    }

    /** The detonation + the death (the explode's own remove — no drops). */
    public void detonateAndDie() {
        explode();
        this.annihilated = true;
        this.hurt(health()); // the break walk drops nothing (the cart is gone)
    }

    /** @return whether the detonation killed this body (the drop skip). */
    public boolean annihilated() {
        return annihilated;
    }

    /**
     * The kind's tick (tick lines 39-60, 1:1): the fuse burns, the
     * detonation at zero, and the impact arm (the track-end halt at
     * speed squared >= 0.01 — the reference's collidingHorizontally
     * surface in the engine's rail walk).
     */
    @Override
    protected void tickKind(net.zaminmc.torch.server.entity.vehicle.VehicleEntity.WorldQuery world) {
        boolean wasOnRail = railAxisAt(world, position()) != -1;
        double speedSquaredBefore = velocityX() * velocityX() + velocityZ() * velocityZ();
        super.tickKind(world);
        if (fuseTicks > 0) {
            fuseTicks--;
        } else if (fuseTicks == 0) {
            detonateAndDie();
            return;
        }
        // The impact arm: the reference's collidingHorizontally (a wall
        // hit at speed) — the engine's track-end halt with the pre-halt
        // speed is the matching surface (the cart stopped dead this tick).
        if (wasOnRail && speedSquaredBefore >= IMPACT_SPEED_SQUARED
                && velocityX() == 0.0 && velocityZ() == 0.0) {
            detonateAndDie();
        }
    }

    /**
     * The activator rail's powered arm (onActivatorRail lines 138-141):
     * a powered activator rail primes an unprimed cart. The engine's
     * rail walk exposes the powered state; the caller (the manager's
     * tick) consults it before the tick.
     */
    public void onActivatorRail(boolean powered) {
        if (powered && fuseTicks < 0) {
            prime();
        }
    }
}
