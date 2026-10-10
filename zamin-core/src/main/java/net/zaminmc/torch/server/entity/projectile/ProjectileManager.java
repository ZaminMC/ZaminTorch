package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Owns all airborne projectiles of one world. Simulation-thread confined,
 * exactly like the item-entity and falling-block systems: launch, physics,
 * collision and removal all run on the world's owner, so no locks are needed
 * and ordering matches the tick (§447/§448 spirit).
 *
 * <p>Physics mirrors the historical thrown-entity loop: integrate, drag,
 * gravity, then collisions — sampled at two substeps per tick so a full-charge
 * arrow (about three blocks per tick) cannot tunnel a one-block wall. Damage
 * semantics live with the engine (the {@link CombatSink} callbacks); this
 * system resolves geometry only. Concepts credited to AtlasProjectiles, see
 * COMMUNITY_REFERENCES.md.</p>
 */
public final class ProjectileManager {

    private static final Logger LOGGER = Logger.getLogger(ProjectileManager.class.getName());

    /** Historical arrow constants: gravity per tick, ground stick time. */
    static final double ARROW_GRAVITY = 0.05;
    static final double THROWABLE_GRAVITY = 0.03;
    static final double DRAG = 0.99;
    /** A landed arrow despawns after a minute on the ground (historical 1200). */
    static final int ARROW_GROUND_TICKS = 1200;
    /** A shard in flight dies after ten seconds (the historical safety net). */
    static final int THROWABLE_AGE_TICKS = 200;
    /** Ticks a thrower ignores their own projectile (the launch-overlap rule). */
    static final int THROWER_IMMUNITY_TICKS = 5;
    /** The vanilla kill plane: a projectile below it stops being simulated. */
    static final double VOID_KILL_Y = -64.0;
    /** The egg's chick roll: one chance in eight (the historical omelet). */
    static final int EGG_HATCH_CHANCE = 8;

    /** The world solidity query (immutable snapshot reads; tick thread). */
    public interface SolidQuery {
        boolean solid(double x, double y, double z);

        /**
         * The shape-aware point test (the collision-shape slice): the
         * default folds to the boolean world so stub queries keep their
         * semantics.
         */
        default boolean solidAt(double x, double y, double z) {
            return solid(x, y, z);
        }
    }

    /** Resolves the entity a projectile is inside of, if any (tick thread). */
    public interface HitResolver {
        /** The mob whose bounding box contains the point, or null. */
        MobEntity mobAt(Position point);

        /** The player whose bounding box contains the point, or null. */
        PlayerSession playerAt(Position point);
    }

    /** Damage application callbacks (the engine owns the combat rules). */
    public interface CombatSink {
        /**
         * A mob took a projectile hit: the historical damage path with
         * knockback along {@code kbYaw} (radians). Zero-damage hits still
         * knock back (the historical snowball bruise). @return the landed
         * verdict (the Punch impulse rides it, the reference's takeDamage
         * boolean).
         */
        boolean mobHit(MobEntity mob, float damage, double kbYaw);

        /** A player took a projectile hit: the PvP path (i-frames, velocity). */
        void playerHit(PlayerSession player, float damage, double kbYaw);

        /**
         * The same hit with the projectile carried (the Punch impulse rides
         * the victim's velocity set) and the thrower's engine id resolved
         * (death chat names the shooter, the historical kill credit).
         * Default bridges to the legacy shape so existing sinks stay
         * source-compatible.
         */
        default void playerHit(ProjectileEntity projectile, PlayerSession player,
                               float damage, double kbYaw) {
            playerHit(player, damage, kbYaw);
        }

        /**
         * The burning arrow's ignite arm (the reference's setOnFireFor(5)
         * on the hit target, unconditional on the damage verdict). The mob
         * carries no equipment: the plain 100-tick burn.
         */
        default void mobIgniteFromProjectile(MobEntity mob) {
        }

        /** The player arm: the victim's Fire Protection shortens the clock. */
        default void playerIgniteFromProjectile(PlayerSession player) {
        }

        /** The egg's chick roll succeeded: spawn a chicken here. */
        void chickenHatch(Position position);
    }

    /** Events the protocol adapter translates into wire updates. */
    public interface Listener {
        /** A projectile launched: Spawn Object + velocity to every observer. */
        void onProjectileSpawned(ProjectileEntity projectile);

        /** A projectile moved this tick: Entity Teleport (the fast-path sync). */
        void onProjectileMoved(ProjectileEntity projectile);

        /** An arrow landed in a block (it stays until the stick time ends). */
        void onProjectileLanded(ProjectileEntity projectile);

        /** A projectile left the world (shattered, hit, expired or unloaded). */
        void onProjectileRemoved(ProjectileEntity projectile, String reason);
    }

    private final SolidQuery solid;
    private final HitResolver hits;
    private final CombatSink combat;
    private final FxManager fx;
    private final Random random;
    private final List<ProjectileEntity> projectiles = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int nextEntityId;

    public ProjectileManager(SolidQuery solid, HitResolver hits, CombatSink combat,
                             FxManager fx, Random random, int firstEntityId) {
        this.solid = Objects.requireNonNull(solid, "solid");
        this.hits = Objects.requireNonNull(hits, "hits");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.random = Objects.requireNonNull(random, "random");
        this.nextEntityId = firstEntityId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public List<ProjectileEntity> all() {
        return List.copyOf(projectiles);
    }

    public ProjectileEntity byId(int entityId) {
        for (ProjectileEntity projectile : projectiles) {
            if (projectile.entityId() == entityId) {
                return projectile;
            }
        }
        return null;
    }

    /**
     * Launches a projectile from a position along a look vector. Tick-thread
     * context (the engine routes every launch through the tick queue).
     */
    public ProjectileEntity launch(ProjectileEntity.Kind kind, int throwerId,
                                   Position origin, double yawDegrees, double pitchDegrees,
                                   double speed) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        // The reference dispense spread (ThrownEntity.dispense lines 86-99):
        // the unit look vector picks up per-axis gaussian noise (sigma
        // 0.0075 * scale, scale 1.0) before the speed multiplies — the
        // historical launch divergence.
        ProjectileEntity projectile = new ProjectileEntity(
                nextEntityId++, kind, throwerId, origin,
                (dx + random.nextGaussian() * 0.0075) * speed,
                (dy + random.nextGaussian() * 0.0075) * speed,
                (dz + random.nextGaussian() * 0.0075) * speed);
        projectiles.add(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileSpawned(projectile);
        }
        return projectile;
    }

    /** One physics pass over every projectile. Tick-thread context. */
    public void tick() {
        // Backward index walk: removals mid-tick (shatter, hit, expiry) shift
        // nothing the loop still needs, and a listener re-entry cannot CME.
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            ProjectileEntity projectile = projectiles.get(i);
            if (projectile.inGround()) {
                projectile.integrate(DRAG, ARROW_GRAVITY); // only the stick clock runs
                if (projectile.ticksInGround() >= ARROW_GROUND_TICKS) {
                    remove(projectile, "expired");
                }
                continue;
            }
            // One historical step per tick (drag 0.99, gravity 0.05/0.03),
            // then collisions sampled at the midpoint AND the endpoint — in
            // flight order (midpoint first): a full-draw arrow crosses ~3
            // blocks, and the midpoint halves the worst-case tunneling window
            // without doubling the physics. The burning clock steps with the
            // pass (the reference's onFireTimer decay).
            projectile.tickFire();
            Position before = projectile.position();
            projectile.integrate(DRAG, gravityOf(projectile.kind()));
            if (projectile.position().y() < VOID_KILL_Y) {
                remove(projectile, "fell out of world"); // the vanilla kill plane
                continue;
            }
            Position after = projectile.position();
            Position midpoint = new Position(
                    (before.x() + after.x()) / 2.0,
                    (before.y() + after.y()) / 2.0,
                    (before.z() + after.z()) / 2.0);
            if (resolveCollisions(projectile, midpoint)
                    || resolveCollisions(projectile, after)) {
                continue;
            }
            for (Listener listener : listeners) {
                listener.onProjectileMoved(projectile);
            }
            boolean expired = projectile.kind() == ProjectileEntity.Kind.ARROW
                    ? projectile.age() >= ARROW_GROUND_TICKS
                    : projectile.age() >= THROWABLE_AGE_TICKS;
            if (expired) {
                remove(projectile, "expired");
            }
        }
    }

    private static double gravityOf(ProjectileEntity.Kind kind) {
        return kind == ProjectileEntity.Kind.ARROW ? ARROW_GRAVITY : THROWABLE_GRAVITY;
    }

    /**
     * Collisions at one sample point: entity hits first (the faster
     * resolution), then blocks. Returns true when the projectile is resolved
     * (removed or landed).
     */
    private boolean resolveCollisions(ProjectileEntity projectile, Position point) {
        // --- entity hit -------------------------------------------------------
        MobEntity mob = hits.mobAt(point);
        if (mob != null && !mob.dead()
                && !(mob.entityId() == projectile.throwerId()
                     && projectile.age() <= THROWER_IMMUNITY_TICKS)) {
            hitMob(projectile, mob);
            remove(projectile, "hit");
            return true;
        }
        PlayerSession player = hits.playerAt(point);
        if (player != null && !player.dead()
                && !(player.engineEntityId() == projectile.throwerId()
                     && projectile.age() <= THROWER_IMMUNITY_TICKS)) {
            hitPlayer(projectile, player);
            remove(projectile, "hit");
            return true;
        }
        // --- block hit --------------------------------------------------------
        if (solid.solidAt(point.x(), point.y(), point.z())) {
            if (projectile.kind() == ProjectileEntity.Kind.ARROW) {
                projectile.landInGround();
                for (Listener listener : listeners) {
                    listener.onProjectileLanded(projectile);
                }
                return true;
            }
            shatter(projectile);
            remove(projectile, "shattered");
            return true;
        }
        return false;
    }

    private void hitMob(ProjectileEntity projectile, MobEntity mob) {
        // The reference ArrowEntity hit walk (lines 229-279): the burning
        // arrow sets the target on fire BEFORE the damage (unconditional on
        // the verdict — the reference's setOnFireFor(5) arm), the damage
        // takes the crit roll, and the Punch impulse rides the landed verdict.
        if (projectile.burning()) {
            combat.mobIgniteFromProjectile(mob);
        }
        float damage = damageOf(projectile);
        double kbYaw = Math.atan2(-projectile.velocityX(), projectile.velocityZ());
        boolean landed = combat.mobHit(mob, damage, kbYaw);
        if (landed) {
            int punch = projectile.punchLevel();
            if (punch > 0) {
                double horizontal = Math.sqrt(projectile.velocityX() * projectile.velocityX()
                        + projectile.velocityZ() * projectile.velocityZ());
                if (horizontal > 0.0) {
                    mob.addVelocity(
                            projectile.velocityX() * punch * 0.6 / horizontal,
                            0.1,
                            projectile.velocityZ() * punch * 0.6 / horizontal);
                }
            }
        }
        if (projectile.kind() == ProjectileEntity.Kind.EGG && rollEggHatch()) {
            combat.chickenHatch(mob.position());
        }
    }

    private void hitPlayer(ProjectileEntity projectile, PlayerSession player) {
        if (projectile.burning()) {
            combat.playerIgniteFromProjectile(player);
        }
        float damage = damageOf(projectile);
        double kbYaw = Math.atan2(-projectile.velocityX(), projectile.velocityZ());
        combat.playerHit(projectile, player, damage, kbYaw);
        if (projectile.kind() == ProjectileEntity.Kind.EGG && rollEggHatch()) {
            combat.chickenHatch(player.position());
        }
    }

    /**
     * The historical impact damage (the reference's ceil(currentSpeed *
     * damage)): the multiplier is 2.0 plus the Power bonus, the current
     * velocity magnitude (decayed by drag) rides — a spent arrow hits softer
     * — and the full-draw crit roll adds nextInt(l / 2 + 2).
     */
    private float damageOf(ProjectileEntity projectile) {
        if (projectile.kind() != ProjectileEntity.Kind.ARROW) {
            return 0.0f;
        }
        double speed = projectile.launchSpeed();
        double l = Math.ceil(speed * (2.0 + projectile.bonusDamage()));
        if (projectile.critical()) {
            l += random.nextInt((int) (l / 2) + 2);
        }
        return (float) l;
    }

    private boolean rollEggHatch() {
        return random.nextInt(EGG_HATCH_CHANCE) == 0;
    }

    /** The shard break feedback (the splat puff + the glassy click). */
    private void shatter(ProjectileEntity projectile) {
        fx.poof(projectile.position());
        fx.sound(projectile.position(), "random.glass", 0.5f,
                0.7f + random.nextFloat() * 0.3f);
    }

    private void remove(ProjectileEntity projectile, String reason) {
        projectiles.remove(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileRemoved(projectile, reason);
        }
    }
}
