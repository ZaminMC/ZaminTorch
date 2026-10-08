package net.zaminmc.torch.server.entity;

/**
 * One goal of the prioritized mob mind (the historical PathfinderGoal
 * architecture, adopted in shape from the community independent-server
 * implementations — BlueDragonMC/Server and the Minestom goal frameworks,
 * credited in COMMUNITY_REFERENCES.md).
 *
 * <p>Goals are stateless decisions over the simulation context: they read the
 * mob's body and world query and either drive it for a tick or yield. Lower
 * priority numbers win; non-exclusive goals (the float goal) run additively
 * beside the chosen exclusive one.</p>
 */
public interface MobGoal {

    /** The priority band: lower runs first, one exclusive goal per tick. */
    int priority();

    /** Exclusive goals stop the selector at this goal; additive ones do not. */
    boolean exclusive();

    /** @return whether this goal wants the body this tick. */
    boolean canUse(MobEntity mob, boolean night);

    /** Drives the body for one tick (the simulation context only). */
    void tick(MobEntity mob, boolean night);
}
