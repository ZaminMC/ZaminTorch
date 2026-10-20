package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.server.entity.MobType;

/**
 * The rider's own seat offset (the reference's getRideHeight family table
 * — Entity.java line 1360: "Return the height offset of this entity when
 * riding another entity"). The offset joins the mount's own seat law
 * (VehicleEntity.seatAnchor / the mob seat's height) to place the rider's
 * body each tick.
 *
 * <p>The table (the reference's own overrides): PlayerEntity line 937
 * (-0.35 — the player's body sinks into the seat), the adult zombie and
 * skeleton families (ZombieEntity line 584, SkeletonEntity line 318) at
 * -0.35 with babies flush (0.0), the silverfish at 0.2 (SilverfishEntity
 * line 39), every other family at the Entity default 0.0. The pigman
 * rides the zombie family's own row and the zombie villager the zombie's
 * (the reference's inheritance); the engine's WITHER is the boss family,
 * whose reference carries no override (the default 0.0).
 */
public final class RiderSeatLaw {
    /** PlayerEntity.getRideHeight line 937. */
    public static final double PLAYER_RIDE_HEIGHT = -0.35;

    private RiderSeatLaw() {
    }

    /**
     * The rider's own offset for a mob family (the reference's table).
     *
     * @param type the rider's family
     * @param baby whether the rider is still a baby (the reference's
     *             {@code isBaby()} consult — a baby sits flush)
     */
    public static double riderRideHeight(MobType type, boolean baby) {
        return switch (type) {
            // ZombieEntity line 584 / SkeletonEntity line 318: the adult
            // undead rider sits 0.35 lower; a baby sits flush (0.0).
            case ZOMBIE, ZOMBIE_PIGMAN, SKELETON -> baby ? 0.0 : -0.35;
            // SilverfishEntity line 39.
            case SILVERFISH -> 0.2;
            // Every other family: Entity's default 0.0 (line 1360).
            default -> 0.0;
        };
    }
}
