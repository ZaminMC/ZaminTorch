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
    /** The fireball family's own drag (ProjectileEntity.getDrag line 198). */
    static final double FIREBALL_DRAG = 0.95;
    /** The charged blue skull's own drag (WitherSkullEntity.getDrag line 29). */
    static final double CHARGED_SKULL_DRAG = 0.73;
    /** The small fireball's hit damage (SmallFireballEntity.onHit line 32). */
    static final float FIREBALL_DAMAGE = 5.0f;
    /** A landed arrow despawns after a minute on the ground (historical 1200). */
    static final int ARROW_GROUND_TICKS = 1200;
    /** A shard in flight dies after ten seconds (the historical safety net). */
    static final int THROWABLE_AGE_TICKS = 200;
    /** Ticks a thrower ignores their own projectile (the launch-overlap rule). */
    static final int THROWER_IMMUNITY_TICKS = 5;
    /**
     * The fireball family's own shooter-immunity window (Slice 25b —
     * ProjectileEntity's collision scan line 119: {@code inAirTicks >= 25},
     * five times the thrown family's 5 — the window that makes the
     * deflected-fireball-kills-the-ghast trick work: the fireball is
     * usually past 25 air ticks by the time the punch turns it around).
     */
    static final int FIREBALL_THROWER_IMMUNITY_TICKS = 25;
    /** The vanilla kill plane: a projectile below it stops being simulated. */
    static final double VOID_KILL_Y = -64.0;
    /** The egg's chick roll: one chance in eight (the historical omelet). */
    static final int EGG_HATCH_CHANCE = 8;
    /** The bottle o' enchanting's own gravity (ExperienceBottleEntity
     * .getGravity line 24: 0.07F — heavier than the shard's 0.03 and the
     * potion's 0.05, the lobbed arc's own weight). */
    static final double XP_BOTTLE_GRAVITY = 0.07;

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

        /**
         * The vehicle whose box contains the point, or null (Slice 77 —
         * the reference's entity scan over the minecart family: the
         * arrow's onEntityHit lands on the cart's hull). Default null so
         * stub resolvers keep their semantics.
         */
        default net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicleAt(Position point) {
            return null;
        }
    }

    /**
     * The fireball's landing arm (SmallFireballEntity.onHit lines 34-49):
     * place the fire block at the first air cell the fireball crossed before
     * the solid one. Wired by the engine; runs on the tick thread.
     */
    public interface FireSink {
        /** Places fire at the cell when it holds air. */
        void tryPlaceFire(int x, int y, int z);
    }

    /**
     * The potion's splash arm (PotionEntity.onCollision lines 72-106 —
     * Slice 11b): the effect set applied to every living body around the
     * impact. Wired by the engine; runs on the tick thread.
     */
    public interface PotionSink {
        /**
         * The splash at the impact point ({@code directHit} null on a block
         * hit; {@code throwerId} the launching body's engine id, -1 for a
         * dispenser — the witch's own-splash immunity keys on the resolved
         * thrower).
         */
        void splash(double x, double y, double z, int damage, Object directHit, int throwerId);
    }

    /**
     * The ghast fireball's detonation arm (FireballEntity.onHit: the
     * explosion at the impact, the fire flag's mobGriefing default).
     * Wired by the engine; runs on the tick thread.
     */
    public interface ExplosionSink {
        /** The explosion at the point (the fireball's power). */
        void explode(Position at, float power);
    }

    /** The wither skull's explosion arm — the charged flag rides (the
     * blast's source model consults it). */
    @FunctionalInterface
    public interface SkullExplosionSink {
        void explode(Position at, float power, boolean charged);
    }

    /**
     * The ender pearl's landing arm (Slice 17c — EnderPearlEntity.
     * onCollision lines 45-71): the thrower's teleport verdict — the
     * engine resolves the player, the 5% endermite roll at the thrower's
     * pre-teleport feet, the position carry, and the 5.0 fall toll.
     */
    public interface PearlSink {
        /** The thrower teleports to the pearl's landing point. */
        void teleportThrower(int throwerId, Position at);
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
        boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId);

        /**
         * The same hit with the projectile carried (Slice 59 — the wither's
         * own gates read the source: the half-health shield is ARROW-only,
         * and another wither's skull never lands). Default bridges to the
         * legacy shape so existing sinks stay source-compatible.
         */
        default boolean mobHit(ProjectileEntity projectile, MobEntity mob,
                               float damage, double kbYaw) {
            return mobHit(mob, damage, kbYaw, projectile.throwerId());
        }

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

        /**
         * A vehicle took a projectile hit (Slice 77 — the reference's
         * entity scan over the minecart family): the damage with the
         * knockback impulse along the projectile's travel. @return the
         * landed verdict (the arrow stops either way).
         */
        default boolean vehicleHit(ProjectileEntity projectile,
                net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                float damage, double kbYaw) {
            return false;
        }

        /**
         * The wither skull's kill heal (Slice 59 — WitherSkullEntity.onHit
         * lines 58-64): the victim died to the 8.0 hit, the shooting
         * wither heals 5.0.
         */
        default void skullKillHeal(int shooterId) {
        }

        /**
         * The wither skull's curse on a living mob victim (onHit lines
         * 69-80): WITHER II for 10 s on NORMAL, 40 s on HARD, nothing
         * below — the sink resolves the difficulty band.
         */
        default void skullWitherCurse(MobEntity victim) {
        }

        /** The player arm of the skull's curse. */
        default void skullWitherCurse(PlayerSession victim) {
        }
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

        /**
         * A player collected a grounded arrow (ArrowEntity.onPlayerCollision
         * lines 427-440): the Collect Item pair + the pop sound ride the
         * fan; the manager already removed the body.
         */
        default void onProjectileCollected(ProjectileEntity projectile,
                net.zaminmc.torch.server.player.PlayerSession collector) {
        }

        /**
         * A player's punch deflected a fireball (Slice 25b — the velocity
         * and the shooter both flip; the wire re-publishes the velocity so
         * the client's own fireball model turns around with it).
         */
        default void onProjectileDeflected(ProjectileEntity projectile) {
        }
    }

    /**
     * The ender-crystal consult (Slice 62 — EnderCrystalEntity.takeDamage
     * lines 67-83: ANY damage source pops the crystal — the arrow's pop is
     * the canonical one). Wired by the engine; runs on the tick thread.
     */
    public interface CrystalSink {
        /**
         * The crystal whose 2x2x2 box holds the point takes its wound
         * (the removal + the 6.0F blast ride the system's own death walk).
         *
         * @return true when a crystal popped (the projectile dies with it).
         */
        boolean hitCrystal(double x, double y, double z);
    }

    /**
     * The dragon-part consult (Slice 63 — EnderDragonPart.takeDamage →
     * the dragon's own part walk): the projectile's crossing of a part
     * hull wounds the BODY through the part gates (the non-head /4+1, the
     * head-front reset, the player-or-explosive source).
     */
    public interface DragonSink {
        /**
         * The part whose hull holds the point takes the projectile's hit.
         *
         * @param damage   the projectile's computed damage (the speed-scaled
         *                 arrow, the per-kind flats)
         * @param explosive whether the source is explosive (the fireball
         *                 family — the damage gate's other arm)
         * @param shooterId the thrower's engine id (the player-source gate)
         * @return true when a part took the hit (the projectile dies).
         */
        boolean hitDragonPart(double x, double y, double z, float damage,
                              boolean explosive, int shooterId);
    }

    /**
     * The bottle o' enchanting's burst arm (Slice 65 —
     * ExperienceBottleEntity.onCollision lines 28-43): the 2002 splash
     * event at the impact plus the experience burst — {@code 3 + rand(5)
     * + rand(5)} points split through the orb family's own roundSize
     * table. No damage ever lands: the reference's onCollision calls no
     * takeDamage, so the burst IS the whole hit law.
     */
    public interface XpBottleSink {
        /** The splash event + the orb burst at the impact point. */
        void burst(double x, double y, double z);
    }

    private final SolidQuery solid;
    private final HitResolver hits;
    private final CombatSink combat;
    private final FxManager fx;
    private final Random random;
    private final List<ProjectileEntity> projectiles = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int nextEntityId;
    /** The fireball's landing arm; optional until the engine wires it. */
    private FireSink fireSink = (x, y, z) -> { };
    /** The potion's splash arm; optional until the engine wires it. */
    private PotionSink potionSink = (x, y, z, damage, directHit, throwerId) -> { };
    /** The ghast fireball's explosion arm; optional until the engine wires it. */
    private ExplosionSink explosionSink = (at, power) -> { };
    /** The wither skull's own arm (the charged flag rides the blast's
     * source model — the 0.8 cap family); optional until wired. */
    private SkullExplosionSink skullExplosionSink = (at, power, charged) -> { };
    /** The ender pearl's teleport arm (Slice 17c); optional until wired. */
    private PearlSink pearlSink = (throwerId, at) -> { };
    /** The ender crystal's pop arm (Slice 62); optional until wired. */
    private CrystalSink crystalSink = (x, y, z) -> false;
    /** The dragon part's wound arm (Slice 63); optional until wired. */
    private DragonSink dragonSink = (x, y, z, damage, explosive, shooterId) -> false;
    /** The bottle o' enchanting's burst arm (Slice 65); optional until wired. */
    private XpBottleSink xpBottleSink = (x, y, z) -> { };

    /** Wires the fireball's fire-placement arm. */
    public void setFireSink(FireSink sink) {
        this.fireSink = Objects.requireNonNull(sink, "sink");
    }

    /** Wires the potion's splash arm. */
    public void setPotionSink(PotionSink sink) {
        this.potionSink = Objects.requireNonNull(sink, "sink");
    }

    /** Wires the ghast fireball's explosion arm. */
    public void setExplosionSink(ExplosionSink sink) {
        this.explosionSink = Objects.requireNonNull(sink, "sink");
    }

    /** Wires the wither skull's explosion arm (the charged flag rides). */
    public void setSkullExplosionSink(SkullExplosionSink sink) {
        this.skullExplosionSink = Objects.requireNonNull(sink, "sink");
    }

    /** Wires the ender pearl's teleport arm (Slice 17c). */
    public void setPearlSink(PearlSink sink) {
        this.pearlSink = Objects.requireNonNull(sink, "sink");
    }

    /** Wires the ender crystal's pop arm (Slice 62). */
    public void setCrystalSink(CrystalSink sink) {
        this.crystalSink = Objects.requireNonNull(sink, "sink");
    }

    /** Wires the dragon part's wound arm (Slice 63). */
    public void setDragonSink(DragonSink sink) {
        this.dragonSink = Objects.requireNonNull(sink, "sink");
    }

    /** Wires the bottle o' enchanting's burst arm (Slice 65). */
    public void setXpBottleSink(XpBottleSink sink) {
        this.xpBottleSink = Objects.requireNonNull(sink, "sink");
    }

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

    /** The public §27.7 projectile-spawn gate (null until the server wires the bus). */
    private net.zaminmc.torch.server.event.EngineEventBus eventBus;

    /**
     * Wires the public projectile-spawn gate: every launch (the bow shot,
     * the snowball throw, the splash potion, the dispenser's projectile
     * arms, the blaze and ghast volleys) fires the cancellable pre-commit
     * event before the body joins the world. Called once by the server at
     * boot.
     */
    public void setEventBus(net.zaminmc.torch.server.event.EngineEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * The burning arrow's TNT arm (the reference's per-block entity
     * collision: Entity.checkBlockCollisions walks the arrow's box cells
     * every tick and TntBlock.onEntityCollision primes every TNT cell a
     * burning arrow overlaps — the block leaves the world and the primed
     * body spawns through the engine's own TNT arm). Wired once by the
     * server at boot; null (the unwired window) skips the pass.
     */
    public interface BurningTntArm {
        /** @return whether the cell is the TNT block. */
        boolean isTnt(net.zaminmc.torch.block.BlockPosition pos);

        /** Primes the cell (the block leaves, the primed body spawns). */
        void ignite(net.zaminmc.torch.block.BlockPosition pos);
    }

    private BurningTntArm burningTnt;

    /** Wires the burning arrow's TNT arm (the per-block entity collision). */
    public void setBurningTntArm(BurningTntArm arm) {
        this.burningTnt = arm;
    }

    /**
     * The launch gate: fires the §27.7 event (the public kind form),
     * answers whether the shot may enter the world. Runs on the launch's
     * own (tick) context; a cancelled shot consumes no entity id.
     */
    private boolean admitLaunch(ProjectileEntity.Kind kind, int throwerId, Position origin) {
        if (eventBus == null) {
            return true;
        }
        net.zaminmc.torch.event.entity.ProjectileSpawnEvent gate =
                new net.zaminmc.torch.event.entity.ProjectileSpawnEvent(
                        kind.apiKind, throwerId, origin);
        eventBus.fire(gate);
        return !gate.cancelled();
    }

    public List<ProjectileEntity> all() {
        return List.copyOf(projectiles);
    }

    // ------------------------------------------------------------------
    // The piston push (the VIII13 additive families — the reference's
    // MovingBlockEntity.moveEntities lines 80-115)
    // ------------------------------------------------------------------

    /** The pushed face's own epsilon (the item family's arm form). */
    private static final double PUSH_GROUND_EPSILON = 1.0E-7;

    /**
     * The per-kind push box half-size (the reference family's own
     * setSize rows): the arrow's 0.5 box (ArrowEntity line 46), the
     * fireball base's 1.0 (ProjectileEntity line 34), the small
     * fireball and the wither skull's 0.3125 (SmallFireballEntity line
     * 14, WitherSkullEntity line 19), the thrown family's 0.25
     * (ThrownEntity line 36 — the snowball, the egg, the pearl, the
     * potion, the bottle).
     */
    public static double pushHalfSize(ProjectileEntity.Kind kind) {
        return switch (kind) {
            case ARROW -> 0.25;
            case LARGE_FIREBALL -> 0.5;
            case SMALL_FIREBALL, WITHER_SKULL -> 0.15625;
            default -> 0.125;
        };
    }

    /**
     * The piston push over one projectile (the reference's
     * {@code Entity.move(amount * facing)}): the IMMEDIATE, per-axis
     * clamped position shift over the manager's own solidity consult —
     * the leading-face law with the kind's own box (the blocked axis
     * refusing its shift) — then the move publication through the
     * family's own listener face. The GROUNDED ARROW rides the same
     * law: the reference displaces every body in the box
     * (world.getEntities(null, box) — no family exempted) and the
     * arrow's own revalidation (ProjectileEntity.tick lines 84-92)
     * reads the STICK CELL, which the push never changes, so the
     * displaced arrow keeps its stick — the engine's grounded model
     * carries the same shape (the stick clock running on). The push
     * never triggers the flight's own hit resolution (the reference's
     * move is the clamped shift alone). Tick-thread context (the push
     * surface's walk runs inside the moving blocks' own tick).
     */
    public void pushProjectile(ProjectileEntity projectile, double dx, double dy, double dz) {
        double half = pushHalfSize(projectile.kind());
        double baseX = projectile.position().x();
        double baseY = projectile.position().y();
        double baseZ = projectile.position().z();
        double newX = baseX;
        double newY = baseY;
        double newZ = baseZ;
        if (dx != 0.0) {
            double leadX = baseX + dx + Math.signum(dx) * half;
            if (!faceBlockedX(leadX, baseY, baseZ, half)) {
                newX = baseX + dx;
            }
        }
        if (dz != 0.0) {
            double leadZ = baseZ + dz + Math.signum(dz) * half;
            if (!faceBlockedZ(newX, baseY, leadZ, half)) {
                newZ = baseZ + dz;
            }
        }
        if (dy != 0.0) {
            double probeY = dy > 0
                    ? baseY + dy + half - PUSH_GROUND_EPSILON
                    : baseY + dy - half + PUSH_GROUND_EPSILON;
            if (!solid.solidAt(newX, probeY, newZ)) {
                newY = baseY + dy;
            }
        }
        if (newX != baseX || newY != baseY || newZ != baseZ) {
            projectile.teleport(new Position(newX, newY, newZ));
            for (Listener listener : listeners) {
                listener.onProjectileMoved(projectile);
            }
        }
    }

    /** The X-leading-face test (the item family's three-sample sweep). */
    private boolean faceBlockedX(double leadX, double centerY, double centerZ, double half) {
        double bottom = centerY - half + PUSH_GROUND_EPSILON;
        double top = centerY + half - PUSH_GROUND_EPSILON;
        for (double y : new double[]{bottom, centerY, top}) {
            if (solid.solidAt(leadX, y, centerZ - half)
                    || solid.solidAt(leadX, y, centerZ)
                    || solid.solidAt(leadX, y, centerZ + half)) {
                return true;
            }
        }
        return false;
    }

    /** The Z-leading-face test: the face at {@code leadZ} over the x-width. */
    private boolean faceBlockedZ(double centerX, double centerY, double leadZ, double half) {
        double bottom = centerY - half + PUSH_GROUND_EPSILON;
        double top = centerY + half - PUSH_GROUND_EPSILON;
        for (double y : new double[]{bottom, centerY, top}) {
            if (solid.solidAt(centerX - half, y, leadZ)
                    || solid.solidAt(centerX, y, leadZ)
                    || solid.solidAt(centerX + half, y, leadZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The slime-block fling over one projectile (MovingBlockEntity
     * lines 94-107): the velocity SET along the facing axis — the
     * family's own integration carries the set components out.
     */
    public void flingProjectile(ProjectileEntity projectile,
                                double axisX, double axisY, double axisZ) {
        projectile.flingVelocity(axisX, axisY, axisZ);
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
        if (!admitLaunch(kind, throwerId, origin)) {
            return null; // the §27.7 gate swallowed the shot
        }
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

    /**
     * The restored form (the Anvil import's exact-position landing — the
     * reference's {@code Entities.create} + the base {@code readCustomNbt}
     * walk): the saved position and the saved Motion land whole, no
     * gaussian tilt, no launch force walk. The thrower stays unknown
     * (the reference's projectile NBT carries no shooter — the base rows
     * alone). A fireball lands with zero acceleration (the reference's
     * own load shape — writeNbt never writes the acceleration fields,
     * so a loaded fireball flies straight at its saved velocity);
     * a stuck arrow's rows land after through {@code restoreGrounded}.
     * The gate consult stays (the §27.7 ProjectileSpawnEvent — the
     * ItemEntityManager.spawnRestored pattern).
     *
     * @return the landed projectile, or null when the §27.7 gate
     *         swallowed the import (the row isolates loudly).
     */
    public ProjectileEntity spawnRestored(ProjectileEntity.Kind kind, Position at,
                                          double vx, double vy, double vz) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        if (!admitLaunch(kind, -1, at)) {
            return null; // the §27.7 gate withheld the import
        }
        ProjectileEntity projectile = new ProjectileEntity(
                nextEntityId++, kind, -1, at, vx, vy, vz);
        projectiles.add(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileSpawned(projectile);
        }
        return projectile;
    }

    /**
     * Launches a projectile from a position along a RAW direction vector
     * (the dispenser's projectile family — Bootstrap lines 66-84 +
     * ThrownEntity.dispense lines 86-99): the direction is normalized, tilted
     * by the per-axis gaussian (sigma 0.0075 * variation), then scaled by the
     * force. Tick-thread context.
     */
    public ProjectileEntity launchVector(ProjectileEntity.Kind kind, int throwerId,
                                         Position origin, double dx, double dy, double dz,
                                         double force, double variation) {
        if (!admitLaunch(kind, throwerId, origin)) {
            return null; // the §27.7 gate swallowed the shot
        }
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) {
            length = 1.0;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        dx += random.nextGaussian() * 0.0075 * variation;
        dy += random.nextGaussian() * 0.0075 * variation;
        dz += random.nextGaussian() * 0.0075 * variation;
        ProjectileEntity projectile = new ProjectileEntity(
                nextEntityId++, kind, throwerId, origin, dx * force, dy * force, dz * force);
        projectiles.add(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileSpawned(projectile);
        }
        return projectile;
    }

    /**
     * Launches a small fireball from a position along a raw direction vector
     * (the dispensed fire charge — Bootstrap's FIRE_CHARGE arm): the
     * direction picks up the gaussian tilt (sigma 0.05) before the ctor's
     * normalize-and-scale walk, and the body starts at rest, accelerating
     * toward ~1.9 blocks/tick. Tick-thread context.
     */
    public ProjectileEntity launchFireball(Position origin,
                                           double directionX, double directionY, double directionZ) {
        if (!admitLaunch(ProjectileEntity.Kind.SMALL_FIREBALL, -1, origin)) {
            return null; // the §27.7 gate swallowed the shot
        }
        ProjectileEntity projectile = new ProjectileEntity(nextEntityId++,
                ProjectileEntity.Kind.SMALL_FIREBALL, origin,
                directionX + random.nextGaussian() * 0.05,
                directionY + random.nextGaussian() * 0.05,
                directionZ + random.nextGaussian() * 0.05);
        projectiles.add(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileSpawned(projectile);
        }
        return projectile;
    }

    /**
     * Launches the ghast's charged fireball (FireballEntity's shooter
     * ctor): the raw direction toward the target's torso picks up the
     * 0.4-sigma gaussian per axis (the reference's own spread — the ghast
     * misses at range), then normalizes into the 0.1 acceleration. The
     * thrower's id rides the body for the hit credit. Tick-thread context.
     */
    public ProjectileEntity launchGhastFireball(int throwerId, Position origin,
                                                double directionX, double directionY,
                                                double directionZ) {
        if (!admitLaunch(ProjectileEntity.Kind.LARGE_FIREBALL, throwerId, origin)) {
            return null; // the §27.7 gate swallowed the shot
        }
        ProjectileEntity projectile = new ProjectileEntity(nextEntityId++,
                ProjectileEntity.Kind.LARGE_FIREBALL, throwerId, origin,
                0.0, 0.0, 0.0);
        projectile.setAccelerationFromDirection(
                directionX + random.nextGaussian() * 0.4,
                directionY + random.nextGaussian() * 0.4,
                directionZ + random.nextGaussian() * 0.4);
        projectiles.add(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileSpawned(projectile);
        }
        return projectile;
    }

    /**
     * Launches the blaze's volley fireball (Slice 18a —
     * BlazeEntity.AttackGoal lines 234-243 over SmallFireballEntity's
     * shooter ctor): the aim direction picks up the HORIZONTAL-only
     * gaussian spread ({@code sigma = sqrt(sqrt(d0)) * 0.5} — the x/z
     * pair, the vertical stays exact), then the ctor's own 0.4-sigma
     * per-axis tilt, then normalizes into the 0.1 acceleration. The
     * thrower's id rides the body for the hit credit. Tick-thread context.
     */
    public ProjectileEntity launchBlazeFireball(int throwerId, Position origin,
                                                 double directionX, double directionY,
                                                 double directionZ, double spread) {
        if (!admitLaunch(ProjectileEntity.Kind.SMALL_FIREBALL, throwerId, origin)) {
            return null; // the §27.7 gate swallowed the shot
        }
        ProjectileEntity projectile = new ProjectileEntity(nextEntityId++,
                ProjectileEntity.Kind.SMALL_FIREBALL, throwerId, origin,
                0.0, 0.0, 0.0);
        projectile.setAccelerationFromDirection(
                directionX + random.nextGaussian() * spread + random.nextGaussian() * 0.4,
                directionY + random.nextGaussian() * 0.4,
                directionZ + random.nextGaussian() * spread + random.nextGaussian() * 0.4);
        projectiles.add(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileSpawned(projectile);
        }
        return projectile;
    }

    /**
     * Launches the wither's skull (Slice 59 — shootSkullAt lines 383-400
     * over WitherSkullEntity's shooter ctor): the raw delta from the
     * head's own mount point to the target cell picks up the ctor's
     * 0.4-sigma gaussian per axis, then normalizes into the 0.1
     * acceleration. The charged flag rides the body (the blue skull's
     * own 0.73 drag). Tick-thread context.
     */
    public ProjectileEntity launchWitherSkull(int throwerId, Position origin,
                                              double directionX, double directionY,
                                              double directionZ, boolean charged) {
        if (!admitLaunch(ProjectileEntity.Kind.WITHER_SKULL, throwerId, origin)) {
            return null; // the §27.7 gate swallowed the shot
        }
        ProjectileEntity projectile = new ProjectileEntity(nextEntityId++,
                ProjectileEntity.Kind.WITHER_SKULL, throwerId, origin,
                0.0, 0.0, 0.0);
        projectile.setAccelerationFromDirection(
                directionX + random.nextGaussian() * 0.4,
                directionY + random.nextGaussian() * 0.4,
                directionZ + random.nextGaussian() * 0.4);
        projectile.setCharged(charged);
        projectiles.add(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileSpawned(projectile);
        }
        return projectile;
    }

    /** One physics pass over every projectile. Tick-thread context. */
    public void tick(Iterable<net.zaminmc.torch.server.player.PlayerSession> players) {
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
            // pass (the reference's onFireTimer decay). The fireball family
            // rides its own constants: drag 0.95, no gravity, the per-tick
            // acceleration (the reference's ProjectileEntity.tick).
            projectile.tickFire();
            double drag = dragOf(projectile);
            Position before = projectile.position();
            projectile.integrate(drag, gravityOf(projectile.kind()));
            if (projectile.position().y() < VOID_KILL_Y) {
                remove(projectile, "fell out of world"); // the vanilla kill plane
                continue;
            }
            Position after = projectile.position();
            Position midpoint = new Position(
                    (before.x() + after.x()) / 2.0,
                    (before.y() + after.y()) / 2.0,
                    (before.z() + after.z()) / 2.0);
            if (resolveCollisions(projectile, before, midpoint)
                    || resolveCollisions(projectile, before, after)) {
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
        burningTntPass();
        arrowPickupPass(players);
    }

    /** The player-entity collision box's growth (the item rule the orb
     *  pickup carries: grown 1.0 horizontal / 0.5 vertical). */
    static final double PICKUP_GROWTH_HORIZONTAL = 1.0;
    static final double PICKUP_GROWTH_VERTICAL = 0.5;
    /** The arrow's own half-size (setSize(0.5F, 0.5F), ArrowEntity line 45). */
    static final double ARROW_HALF_WIDTH = 0.25;
    static final double ARROW_HALF_HEIGHT = 0.25;

    /**
     * The grounded arrows' player pickup (ArrowEntity.onPlayerCollision
     * lines 427-440, 1:1): a STUCK arrow answers the walk — the flag law
     * ({@code pickup == 1 || pickup == 2 && creative}, line 430), the
     * pickup-1 inventory consult (line 431: a full inventory flips the
     * flag off — the arrow stays for the next walker), and the collect
     * (the pop sound + the Collect Item pair ride the listener fan; the
     * body leaves the world). The shake gate ({@code shake <= 0}, line
     * 428) is structural: the engine's arrow model carries no shake row
     * (the VII1 counted note — the point model derives it away). The
     * non-spectator walk continues past a failed consult (the reference's
     * collision walk calls every overlapping player) and stops at the
     * first collector.
     */
    private void arrowPickupPass(Iterable<net.zaminmc.torch.server.player.PlayerSession> players) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            ProjectileEntity projectile = projectiles.get(i);
            if (projectile.kind() != ProjectileEntity.Kind.ARROW
                    || !projectile.inGround()
                    || projectile.pickupStatus() == 0) {
                continue;
            }
            for (net.zaminmc.torch.server.player.PlayerSession player : players) {
                if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                        || player.dead()) {
                    continue;
                }
                if (!withinArrowPickupRange(player, projectile)) {
                    continue;
                }
                boolean flag = projectile.pickupStatus() == 1
                        || projectile.pickupStatus() == 2
                        && player.gamemode() == net.zaminmc.torch.GameMode.CREATIVE;
                if (!flag) {
                    continue; // the creative-only band's non-creative walker never collects
                }
                if (projectile.pickupStatus() == 1) {
                    // Line 431: the survival band must actually FIT — the
                    // engine's pickUp remainder (a count-1 stack either
                    // lands whole or stays whole).
                    net.zaminmc.torch.item.ItemStack remainder =
                            player.inventory().pickUp(net.zaminmc.torch.item.ItemStack.of(
                                    net.zaminmc.torch.server.item.BuiltinItems.ARROW, 1));
                    if (!remainder.isEmpty()) {
                        continue; // the full inventory: the arrow stays, the walk tries the next player
                    }
                }
                projectiles.remove(i);
                for (Listener listener : listeners) {
                    listener.onProjectileCollected(projectile, player);
                }
                break; // one collector per arrow
            }
        }
    }

    /** The player box grown 1.0/0.5 against the arrow's own 0.5 box (the
     *  orb pickup's exact shape over the arrow's half-size). */
    private boolean withinArrowPickupRange(
            net.zaminmc.torch.server.player.PlayerSession player, ProjectileEntity projectile) {
        Position playerPosition = player.position();
        Position arrowPosition = projectile.position();
        double dx = Math.abs(arrowPosition.x() - playerPosition.x());
        double dz = Math.abs(arrowPosition.z() - playerPosition.z());
        double maxHorizontal = ARROW_HALF_WIDTH + PICKUP_GROWTH_HORIZONTAL;
        if (dx > maxHorizontal || dz > maxHorizontal) {
            return false;
        }
        double arrowBottom = arrowPosition.y() - ARROW_HALF_HEIGHT;
        double arrowTop = arrowPosition.y() + ARROW_HALF_HEIGHT;
        double rangeBottom = playerPosition.y() - PICKUP_GROWTH_VERTICAL;
        double rangeTop = playerPosition.y() + 1.8 + PICKUP_GROWTH_VERTICAL;
        return arrowTop > rangeBottom && arrowBottom < rangeTop;
    }

    /**
     * The burning arrows' per-block TNT walk (the reference's
     * checkBlockCollisions, Entity lines 665-689, over the TntBlock's
     * onEntityCollision, lines 91-104): every burning arrow — in flight
     * or stuck in the ground — walks the cells its 0.5 box overlaps (the
     * faces shrunk by the reference's 0.001) and primes every TNT cell
     * found. The engine's point-model walks the 0.25 band around the
     * flight position (the box-center envelope).
     */
    private void burningTntPass() {
        if (burningTnt == null) {
            return;
        }
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            ProjectileEntity projectile = projectiles.get(i);
            if (projectile.kind() != ProjectileEntity.Kind.ARROW || !projectile.burning()) {
                continue;
            }
            Position at = projectile.position();
            int minX = (int) Math.floor(at.x() - 0.25 + 0.001);
            int maxX = (int) Math.floor(at.x() + 0.25 - 0.001);
            int minY = (int) Math.floor(at.y() - 0.25 + 0.001);
            int maxY = (int) Math.floor(at.y() + 0.25 - 0.001);
            int minZ = (int) Math.floor(at.z() - 0.25 + 0.001);
            int maxZ = (int) Math.floor(at.z() + 0.25 - 0.001);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        net.zaminmc.torch.block.BlockPosition cell =
                                new net.zaminmc.torch.block.BlockPosition(x, y, z);
                        if (burningTnt.isTnt(cell)) {
                            burningTnt.ignite(cell);
                        }
                    }
                }
            }
        }
    }

    /**
     * The per-kind drag step (the reference's getDrag family): the
     * ballistic kinds 0.99, the fireball family 0.95, and the wither's
     * charged blue skull 0.73 (WitherSkullEntity's own override).
     */
    static double dragOf(ProjectileEntity projectile) {
        if (projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL) {
            return projectile.charged() ? CHARGED_SKULL_DRAG : FIREBALL_DRAG;
        }
        return projectile.kind() == ProjectileEntity.Kind.SMALL_FIREBALL
                ? FIREBALL_DRAG : DRAG;
    }

    static double gravityOf(ProjectileEntity.Kind kind) {
        if (kind == ProjectileEntity.Kind.ARROW || kind == ProjectileEntity.Kind.POTION) {
            // The potion's own gravity (PotionEntity.getGravity line 41-43:
            // 0.05, the arrow's weight, not the shard's 0.03).
            return ARROW_GRAVITY;
        }
        if (kind == ProjectileEntity.Kind.EXPERIENCE_BOTTLE) {
            // The bottle's own weight (ExperienceBottleEntity.getGravity
            // line 24: 0.07F).
            return XP_BOTTLE_GRAVITY;
        }
        return kind == ProjectileEntity.Kind.SMALL_FIREBALL
                || kind == ProjectileEntity.Kind.WITHER_SKULL
                ? 0.0 : THROWABLE_GRAVITY;
    }

    /**
     * Collisions at one sample point: entity hits first (the faster
     * resolution), then blocks. Returns true when the projectile is resolved
     * (removed or landed). The {@code from} position is the pre-tick origin —
     * the fireball's fire placement walks back to the last air cell.
     */
    private boolean resolveCollisions(ProjectileEntity projectile, Position from, Position point) {
        // --- the bottle o' enchanting (Slice 65) ------------------------------
        // ExperienceBottleEntity.onCollision: the burst IS the whole hit law
        // — no damage, no knockback, no crystal pop, no dragon wound (the
        // reference's onCollision never calls takeDamage). The burst lands
        // on ANY collision: a body, a block. The thrower-immunity window
        // still applies (the reference's ThrownEntity collision scan).
        if (projectile.kind() == ProjectileEntity.Kind.EXPERIENCE_BOTTLE) {
            MobEntity burstMob = hits.mobAt(point);
            PlayerSession burstPlayer = hits.playerAt(point);
            boolean burstOnMob = burstMob != null && !burstMob.dead()
                    && !(burstMob.entityId() == projectile.throwerId()
                         && projectile.age() <= THROWER_IMMUNITY_TICKS);
            boolean burstOnPlayer = burstPlayer != null && !burstPlayer.dead()
                    && !(burstPlayer.engineEntityId() == projectile.throwerId()
                         && projectile.age() <= THROWER_IMMUNITY_TICKS);
            if (burstOnMob || burstOnPlayer || solid.solid(point.x(), point.y(), point.z())) {
                xpBottleSink.burst(point.x(), point.y(), point.z());
                remove(projectile, "hit");
                return true;
            }
            return false;
        }
        // --- entity hit -------------------------------------------------------
        MobEntity mob = hits.mobAt(point);
        if (mob != null && !mob.dead()
                && !(mob.entityId() == projectile.throwerId()
                     && projectile.age() <= throwerImmunityTicksOf(projectile))) {
            if (projectile.kind() == ProjectileEntity.Kind.POTION) {
                // The splash on the struck body (PotionEntity.onCollision's
                // result.entity arm — the direct hit answers proximity 1.0).
                potionSink.splash(point.x(), point.y(), point.z(),
                        projectile.potionDamage(), mob, projectile.throwerId());
            } else {
                hitMob(projectile, mob);
            }
            remove(projectile, "hit");
            return true;
        }
        PlayerSession player = hits.playerAt(point);
        if (player != null && !player.dead()
                && !(player.engineEntityId() == projectile.throwerId()
                     && projectile.age() <= throwerImmunityTicksOf(projectile))) {
            if (projectile.kind() == ProjectileEntity.Kind.ENDER_PEARL
                    && player.engineEntityId() == projectile.throwerId()) {
                // EnderPearlEntity.onCollision lines 33-35: the owner is a
                // pass-through — the pearl keeps flying (the reference's
                // own early return; the pearl-boost trick's rule).
            } else if (projectile.kind() == ProjectileEntity.Kind.POTION) {
                potionSink.splash(point.x(), point.y(), point.z(),
                        projectile.potionDamage(), player, projectile.throwerId());
            } else {
                hitPlayer(projectile, player);
            }
            if (projectile.kind() != ProjectileEntity.Kind.ENDER_PEARL
                    || player.engineEntityId() != projectile.throwerId()) {
                remove(projectile, "hit");
                return true;
            }
        }
        // --- vehicle hit (Slice 77) --------------------------------------------
        // The reference's entity scan includes the minecarts: the arrow's
        // onEntityHit lands on the cart's hull (the box containment). No
        // thrower immunity — the reference's scan exempts the THROWER
        // alone, never the vehicle it rides.
        net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle =
                hits.vehicleAt(point);
        if (vehicle != null && !vehicle.dead()) {
            hitVehicle(projectile, vehicle);
            remove(projectile, "hit");
            return true;
        }
        // --- crystal hit (Slice 62) -------------------------------------------
        // EnderCrystalEntity.takeDamage lines 67-83: any damage source pops
        // the crystal — the arrow's pop is the canonical one. The consult
        // rides BEFORE the block arm: the pillar-top crystal's feet cell
        // holds FIRE (non-solid), but the box spans two blocks of height
        // and the block arm must never shadow the body.
        if (crystalSink.hitCrystal(point.x(), point.y(), point.z())) {
            remove(projectile, "hit");
            return true;
        }
        // --- dragon part hit (Slice 63) ----------------------------------------
        // EnderDragonPart.takeDamage → the part walk: the projectile's
        // damage with the per-kind flats (the same numbers hitMob carries)
        // and the source gates (the fireball family is explosive, the
        // thrower's id the player gate).
        if (dragonSink.hitDragonPart(point.x(), point.y(), point.z(),
                projectileDamageForPart(projectile),
                projectile.kind() == ProjectileEntity.Kind.LARGE_FIREBALL
                        || projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL,
                projectile.throwerId())) {
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
            if (projectile.kind() == ProjectileEntity.Kind.ENDER_PEARL) {
                // EnderPearlEntity.onCollision (Slice 17c): the thrower
                // teleports to the landing point — the last crossed air
                // cell (the reference's own position carry, the fireball's
                // fire-placement stand-in shape). The 5% endermite roll,
                // the fall-distance reset and the 5.0 fall toll ride the
                // engine's sink.
                pearlSink.teleportThrower(projectile.throwerId(), from);
                remove(projectile, "hit");
                return true;
            }
            if (projectile.kind() == ProjectileEntity.Kind.SMALL_FIREBALL) {
                // The fireball's landing arm (SmallFireballEntity.onHit
                // lines 34-49): the fire lands at the air cell the fireball
                // crossed before the solid one — the engine's stand-in for the
                // reference's hit.face offset (the previous sample's cell).
                fireSink.tryPlaceFire((int) Math.floor(from.x()),
                        (int) Math.floor(from.y()), (int) Math.floor(from.z()));
                remove(projectile, "hit");
                return true;
            }
            if (projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL) {
                // WitherSkullEntity.onHit line 83: the skull detonates where
                // it lands — the power-1 explosion (no fire, the skull's own
                // onHit carries no flame arm) and the body leaves. The
                // charged flag rides the blast's SOURCE model (the blue
                // skull's 0.8 cap, WitherSkullEntity.getBlastResistance
                // lines 43-51): the skull's own arm, not the fireball's.
                skullExplosionSink.explode(point, 1.0f, projectile.charged());
                remove(projectile, "hit");
                return true;
            }
            if (projectile.kind() == ProjectileEntity.Kind.LARGE_FIREBALL) {
                // FireballEntity.onHit: the explosion where the fireball
                // lands (the fire flag's mobGriefing default — the fire
                // placement stands in for the blast's own).
                explosionSink.explode(point, 1.0f);
                fireSink.tryPlaceFire((int) Math.floor(point.x()),
                        (int) Math.floor(point.y()),
                        (int) Math.floor(point.z()));
                remove(projectile, "hit");
                return true;
            }
            if (projectile.kind() == ProjectileEntity.Kind.POTION) {
                // The block splash (onCollision's null-entity arm: the box
                // scan around the impact point alone).
                potionSink.splash(point.x(), point.y(), point.z(),
                        projectile.potionDamage(), null, projectile.throwerId());
                remove(projectile, "hit");
                return true;
            }
            shatter(projectile);
            remove(projectile, "shattered");
            return true;
        }
        return false;
    }

    /**
     * The vehicle hit (Slice 77 — the reference's entity scan over the
     * minecart family): a BURNING arrow detonates a TNT cart on the spot
     * (TntMinecartEntity.takeDamage lines 63-73 — the arm runs BEFORE the
     * damage, the ARROW's own speed squared feeding the power roll), and
     * every other hit lands the projectile's damage with the knockback
     * impulse along the travel.
     */
    private void hitVehicle(ProjectileEntity projectile,
            net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
        if (projectile.burning() && vehicle
                instanceof net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity tnt) {
            double arrowSpeedSquared = projectile.velocityX() * projectile.velocityX()
                    + projectile.velocityZ() * projectile.velocityZ();
            tnt.detonateByArrow(arrowSpeedSquared);
            return; // the cart is annihilated — no damage walk follows
        }
        float damage = damageOf(projectile);
        double kbYaw = Math.atan2(-projectile.velocityX(), projectile.velocityZ());
        combat.vehicleHit(projectile, vehicle, damage, kbYaw);
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
        if (projectile.kind() == ProjectileEntity.Kind.ENDER_PEARL) {
            // EnderPearlEntity.onCollision line 36: the struck body takes
            // the thrown family's 0.0 damage — the bruise without the wound.
            damage = 0.0f;
        }
        if (projectile.kind() == ProjectileEntity.Kind.LARGE_FIREBALL) {
            // FireballEntity.onHit lines 33-37: the struck body takes the
            // flat 6.0 fireball hit, then the explosion lands below (the
            // fireball's own detonation, at the impact).
            damage = 6.0f;
        }
        if (projectile.kind() == ProjectileEntity.Kind.SNOWBALL
                && mob.type() == net.zaminmc.torch.server.entity.MobType.BLAZE) {
            // SnowballEntity.onHit lines 26-29: the blaze pays the flat
            // three for every snowball — the fire family's own bruise.
            damage = 3.0f;
        }
        if (projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL) {
            // WitherSkullEntity.onHit line 58: the skull's own flat 8.0
            // hit (DamageSource.mob(shooter)).
            damage = 8.0f;
        }
        double kbYaw = Math.atan2(-projectile.velocityX(), projectile.velocityZ());
        boolean landed = combat.mobHit(projectile, mob, damage, kbYaw);
        if (projectile.kind() == ProjectileEntity.Kind.SMALL_FIREBALL && landed) {
            // SmallFireballEntity.onHit lines 32-38: the hit target takes the
            // five-second burn after the damage verdict (the fire-immune
            // exemption rides the combat layer's own fire-resistance walk).
            combat.mobIgniteFromProjectile(mob);
        }
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
        if (projectile.kind() == ProjectileEntity.Kind.LARGE_FIREBALL) {
            // FireballEntity.onHit: the detonation at the struck body.
            explosionSink.explode(mob.position(), 1.0f);
            fireSink.tryPlaceFire((int) Math.floor(projectile.position().x()),
                    (int) Math.floor(projectile.position().y()),
                    (int) Math.floor(projectile.position().z()));
        }
        if (projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL) {
            // WitherSkullEntity.onHit lines 58-84: a kill heals the
            // shooting wither 5.0; a living victim takes the curse
            // (WITHER II by the difficulty band); the detonation lands
            // at the struck body either way.
            if (landed && mob.dead()) {
                combat.skullKillHeal(projectile.throwerId());
            } else if (landed) {
                combat.skullWitherCurse(mob);
            }
            explosionSink.explode(mob.position(), 1.0f);
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
        if (projectile.kind() == ProjectileEntity.Kind.ENDER_PEARL) {
            // EnderPearlEntity.onCollision line 36: the struck player takes
            // the thrown family's 0.0 — the bruise without the wound (the
            // owner never reaches here; the resolution passes them through).
            damage = 0.0f;
        }
        if (projectile.kind() == ProjectileEntity.Kind.LARGE_FIREBALL) {
            // FireballEntity.onHit: the flat 6.0 fireball hit.
            damage = 6.0f;
        }
        if (projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL) {
            // WitherSkullEntity.onHit line 58: the flat 8.0 skull hit
            // (no shooter → DamageSource.MAGIC's 5.0 below stands in for
            // the engine's ownerless arm when the wither is gone).
            damage = projectile.throwerId() > 0 ? 8.0f : 5.0f;
        }
        double kbYaw = Math.atan2(-projectile.velocityX(), projectile.velocityZ());
        combat.playerHit(projectile, player, damage, kbYaw);
        if (projectile.kind() == ProjectileEntity.Kind.SMALL_FIREBALL) {
            // The five-second burn after the hit (the same onHit arm).
            combat.playerIgniteFromProjectile(player);
        }
        if (projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL) {
            // The curse and the detonation at the struck player (onHit
            // lines 69-84 — the same difficulty band as the mob arm).
            combat.skullWitherCurse(player);
            explosionSink.explode(player.position(), 1.0f);
        }
        if (projectile.kind() == ProjectileEntity.Kind.LARGE_FIREBALL) {
            // The detonation at the struck player (the fireball's own
            // explosion, at the impact).
            explosionSink.explode(player.position(), 1.0f);
        }
        if (projectile.kind() == ProjectileEntity.Kind.EGG && rollEggHatch()) {
            combat.chickenHatch(player.position());
        }
    }

    /**
     * The historical impact damage (the reference's ceil(currentSpeed *
     * damage)): the multiplier is 2.0 plus the Power bonus, the current
     * velocity magnitude (decayed by drag) rides — a spent arrow hits softer
     * — and the full-draw crit roll adds nextInt(l / 2 + 2). The fireball
     * carries its own flat five (SmallFireballEntity.onHit line 32).
     */
    private float damageOf(ProjectileEntity projectile) {
        if (projectile.kind() == ProjectileEntity.Kind.SMALL_FIREBALL) {
            return FIREBALL_DAMAGE;
        }
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

    /**
     * The projectile's damage against a dragon part (Slice 63 — the same
     * per-kind flats hitMob carries: the fireball's 6.0, the skull's 8.0,
     * the speed-scaled arrow, the thrown family's bruise).
     */
    private float projectileDamageForPart(ProjectileEntity projectile) {
        float damage = damageOf(projectile);
        if (projectile.kind() == ProjectileEntity.Kind.ENDER_PEARL) {
            damage = 0.0f; // the thrown family's bruise
        }
        if (projectile.kind() == ProjectileEntity.Kind.LARGE_FIREBALL) {
            damage = 6.0f; // FireballEntity.onHit lines 33-37
        }
        if (projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL) {
            damage = 8.0f; // WitherSkullEntity.onHit line 58
        }
        return damage;
    }

    /** The shard break feedback (the splat puff + the glassy click). */
    private void shatter(ProjectileEntity projectile) {
        fx.poof(projectile.position());
        fx.sound(projectile.position(), "random.glass", 0.5f,
                0.7f + random.nextFloat() * 0.3f);
    }

    /**
     * The fireball family's own immunity window (the reference's two
     * collision scans: ProjectileEntity line 119's {@code inAirTicks >= 25}
     * against ThrownEntity line 165's {@code ticksInAir >= 5}).
     */
    private static int throwerImmunityTicksOf(ProjectileEntity projectile) {
        return projectile.kind() == ProjectileEntity.Kind.LARGE_FIREBALL
                || projectile.kind() == ProjectileEntity.Kind.SMALL_FIREBALL
                || projectile.kind() == ProjectileEntity.Kind.WITHER_SKULL
                ? FIREBALL_THROWER_IMMUNITY_TICKS : THROWER_IMMUNITY_TICKS;
    }

    /**
     * The player's punch on a fireball (Slice 25b — ProjectileEntity
     * .takeDamage lines 248-273): the velocity becomes the attacker's
     * normalized look vector, the acceleration becomes look * 0.1, and the
     * attacker becomes the new shooter (the deflected blast credits the
     * puncher — the shooter-immunity window flips with the reassignment,
     * so a fireball past its 25 air ticks now ignores the PLAYER and can
     * strike the ghast that loosed it). The wire re-publishes the
     * velocity (the reference's velocityChanged flag → the tracker's
     * Entity Velocity packet). Only the fireball family carries the arm;
     * the ballistic kinds answer false exactly the reference's takeDamage
     * (arrows and shards have no deflect surface).
     *
     * @return whether the punch landed (the attack's own verdict — the
     *         caller charges the melee exhaustion on it)
     */
    public boolean deflect(ProjectileEntity projectile, int attackerId,
                           double lookX, double lookY, double lookZ) {
        Objects.requireNonNull(projectile, "projectile");
        if (projectile.kind() != ProjectileEntity.Kind.LARGE_FIREBALL
                && projectile.kind() != ProjectileEntity.Kind.SMALL_FIREBALL) {
            return false; // the thrown family and arrows take no deflection
        }
        projectile.deflectBy(attackerId, lookX, lookY, lookZ);
        for (Listener listener : listeners) {
            listener.onProjectileDeflected(projectile);
        }
        return true;
    }

    private void remove(ProjectileEntity projectile, String reason) {
        projectiles.remove(projectile);
        for (Listener listener : listeners) {
            listener.onProjectileRemoved(projectile, reason);
        }
    }
}
