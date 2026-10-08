package net.zamin.engine.entity.projectile;

import net.zamin.api.Position;
import net.zamin.engine.fx.FxManager;
import net.zamin.engine.entity.MobEntity;
import net.zamin.engine.player.PlayerSession;

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
         * knock back (the historical snowball bruise).
         */
        void mobHit(MobEntity mob, float damage, double kbYaw);

        /** A player took a projectile hit: the PvP path (i-frames, velocity). */
        void playerHit(PlayerSession player, float damage, double kbYaw);

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
        ProjectileEntity projectile = new ProjectileEntity(
                nextEntityId++, kind, throwerId, origin,
                dx * speed, dy * speed, dz * speed);
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
            // without doubling the physics.
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
        if (solid.solid(point.x(), point.y(), point.z())) {
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
        float damage = damageOf(projectile);
        double kbYaw = Math.atan2(-projectile.velocityX(), projectile.velocityZ());
        combat.mobHit(mob, damage, kbYaw);
        if (projectile.kind() == ProjectileEntity.Kind.EGG && rollEggHatch()) {
            combat.chickenHatch(mob.position());
        }
    }

    private void hitPlayer(ProjectileEntity projectile, PlayerSession player) {
        float damage = damageOf(projectile);
        double kbYaw = Math.atan2(-projectile.velocityX(), projectile.velocityZ());
        combat.playerHit(player, damage, kbYaw);
        if (projectile.kind() == ProjectileEntity.Kind.EGG && rollEggHatch()) {
            combat.chickenHatch(player.position());
        }
    }

    /** The historical impact damage: arrows scale with speed, shards bruise 0. */
    private static float damageOf(ProjectileEntity projectile) {
        if (projectile.kind() != ProjectileEntity.Kind.ARROW) {
            return 0.0f;
        }
        double speed = projectile.launchSpeed();
        return Math.max(1.0f, (float) Math.ceil(speed * 2.0));
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
