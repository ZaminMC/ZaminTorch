package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.entity.EntitySnapshot;
import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.Objects;
import java.util.Random;

/**
 * One living mob in the world: identity, body, mind and the timers that shape
 * its behavior. The simulation owns it; the wire only observes.
 *
 * <p>AI is a small vanilla-style goal set, deterministic under seeded
 * random:</p>
 * <ul>
 *   <li><b>Passive kinds</b> wander (idle, then pick a direction and walk),
 *       glance at nearby players while idle (the historical LookAtPlayer),
 *       and panic when hurt. Sheep additionally carry a wool coat a player
 *       can shear off; it regrows on a timer.</li>
 *   <li><b>Zombies</b> chase the nearest player within their aggro range and
 *       attack in melee with a cooldown.</li>
 *   <li><b>Skeletons</b> hold a shooting band: they close when the target is
 *       far, back away when crowded, and loose an arrow when the line of
 *       sight is clear and the bow is off cooldown.</li>
 *   <li><b>Creepers</b> chase; at arm's length they stop and prime, and
 *       after the historical 30-tick fuse they detonate (the manager hears
 *       it and removes them). A target who retreats past the abort radius
 *       defuses the creeper.</li>
 *   <li><b>Spiders</b> hunt like zombies but only in the dark — in daylight
 *       they are neutral wanderers.</li>
 * </ul>
 *
 * <p>Walking mobs steer: a blocked walker picks a sideways detour heading
 * for a handful of ticks before re-choosing (the light-touch alternative to
 * full pathfinding — enough to slide along walls and around corners). Fluid
 * slows and buoys the body. Physics reuses the item-entity model (gravity,
 * drag, epsilon ground snap); horizontal motion is direct walk-integration,
 * not impulse physics, and stays marked for the physics slice.</p>
 */
public final class MobEntity implements LivingEntity {

    /** Historical gravity, shared with items (physics slice will unify). */
    public static final double GRAVITY_PER_TICK = 0.04;
    /** The 1.8 EntityLiving hurt animation window (red flash client-side). */
    public static final int HURT_FLASH_TICKS = 10;
    /** The 1.8 death animation: the body lies for 20 ticks, then loot + removal. */
    public static final int DEATH_TICKS = 20;
    /** Panic run length after being hurt (historical EntityAIPanic window). */
    public static final int PANIC_TICKS = 100;
    /** Idle look-around length before the wander roll re-fires. */
    public static final int IDLE_TICKS = 60;
    /** Wander leg length (walking one direction, then the roll re-fires). */
    public static final int WANDER_TICKS = 80;
    /** Hostile aggro radius in blocks (the historical follow range). */
    public static final double AGGRO_RANGE = 16.0;

    /**
     * The FOLLOW_RANGE attribute's per-kind override (VT-7a's census —
     * the reference's own rows: BlazeEntity line 44's 48,
     * EndermanEntity line 74's 64, WitherEntity line 484's 40,
     * ZombieEntity line 84's 35 with the pigman's inheritance,
     * GhastEntity line 81's 100); NaN when the kind carries none — each
     * consumer then answers its own default (the hunt band's
     * established 16 through {@link #followRange()}, the navigation's
     * search radius the attribute's own base 32 through
     * {@link #navigationRange()}).
     */
    private double followRangeOverride() {
        switch (type) {
            case BLAZE: return 48.0;
            case ENDERMAN: return 64.0;
            case WITHER: return 40.0;
            case ZOMBIE:
            case ZOMBIE_PIGMAN: return 35.0;
            case GHAST: return 100.0;
            default: return Double.NaN;
        }
    }

    private double followRange() {
        double override = followRangeOverride();
        return Double.isNaN(override) ? AGGRO_RANGE : override;
    }

    /**
     * The navigation's search radius — the reference's own FOLLOW_RANGE
     * attribute (the navigation's findPath range, MobNavigation's
     * construction): the per-kind override, the shared attribute
     * default 32 (EntityAttributes.FOLLOW_RANGE's base value). The
     * engine's 16-block AGGRO_RANGE is the HUNT SCAN's own band, never
     * the navigation's — the retired wiring (the flat 16 on every
     * body) starved every long walk: the village-return's candidates
     * reach 16 + 8 blocks out and the search found nothing.
     */
    private double navigationRange() {
        double override = followRangeOverride();
        return Double.isNaN(override) ? NAVIGATION_DEFAULT_RANGE : override;
    }

    /** The FOLLOW_RANGE attribute's shared base (the navigation's default). */
    private static final double NAVIGATION_DEFAULT_RANGE = 32.0;
    /** Zombie melee reach in blocks (horizontal, center to center-ish). */
    public static final double MELEE_ATTACK_RANGE = 1.6;
    /** Vertical spread the melee attack tolerates. */
    public static final double MELEE_ATTACK_VERTICAL_RANGE = 2.0;
    /** Historical easy-difficulty melee damage of the undead. */
    public static final float MELEE_ATTACK_DAMAGE = 2.0f;
    /** Melee cooldown in ticks (historical attack delay). */
    public static final int MELEE_ATTACK_COOLDOWN = 20;
    /** Skeleton shooting band: closer than this it backs away. */
    public static final double SKELETON_TOO_CLOSE = 6.0;
    /** Skeleton shooting band: farther than this it closes in. */
    public static final double SKELETON_TOO_FAR = 12.0;
    /** Skeleton maximum shot range (the historical bow reach). */
    public static final double SKELETON_SHOOT_RANGE = 15.0;
    /** Skeleton bow cooldown in ticks (the historical 60-tick attack delay). */
    public static final int SKELETON_ATTACK_COOLDOWN = 60;
    /** Skeleton arrow launch speed (blocks per tick). */
    public static final double SKELETON_ARROW_SPEED = 1.8;
    /** Creeper ignition distance: inside this it stops and primes. */
    public static final double CREEPER_PRIME_RANGE = 1.8;
    /** Creeper prime vertical tolerance. */
    public static final double CREEPER_PRIME_VERTICAL_RANGE = 2.0;
    /** Creeper abort radius: the target escaping this far defuses the fuse. */
    public static final double CREEPER_ABORT_RANGE = 3.5;
    /** The historical 30-tick fuse (EntityCreeper fuse, 1.5 seconds). */
    public static final int CREEPER_FUSE_TICKS = 30;
    /** Sheep wool regrow window (5-10 minutes, the historical grass-eat loop). */
    public static final int SHEEP_REGROW_MIN_TICKS = 6000;
    public static final int SHEEP_REGROW_SPREAD_TICKS = 6000;
    /** Sidestep detour length in ticks (the wall-slide steering window). */
    public static final int DETOUR_TICKS = 20;
    /** Panic speed multiplier over the kind's walk speed. */
    public static final double PANIC_SPEED_MULTIPLIER = 1.8;
    /** Position epsilon so ground queries see the block *below* the feet. */
    private static final double GROUND_EPSILON = 1.0E-7;

    /**
     * The walkers' step height (the physics-solver slice — the
     * reference's own {@code Entity.stepHeight} 0.5: the half slab and
     * the stair's base mount in ONE step, the full cube never).
     */
    private static final double WALK_STEP_HEIGHT = 0.5;

    /**
     * The horse's step height (EntityHorse's own 1.0 override — the
     * full block mounts in one step, the reference's own ridden climb).
     */
    private static final double HORSE_STEP_HEIGHT = 1.0;
    /**
     * The vanilla kill plane (1.8.8 living entities): below it the
     * out-of-world damage consumes the body. Between y=0 and here the body
     * simply falls through the void (no solid block exists below the world).
     */
    public static final double VOID_KILL_Y = -64.0;
    /** The historical DamageSource.outOfWorld rate: four damage (two hearts) per tick. */
    public static final float VOID_DAMAGE_PER_TICK = 4.0f;
    /** The ignite duration standing in fire arms (the historical 8 s). */
    public static final int FIRE_TICKS = 160;
    /** The historical burn rate: one damage per second while on fire. */
    public static final float FIRE_DAMAGE_PER_SECOND = 1.0f;
    /**
     * The lava burn's duration (Slice 93/D2 — Entity.takeLavaDamage line
     * 360's {@code setOnFireFor(15)}): fifteen seconds, re-armed every
     * tick the body stands in the lava (the ignite rule's own max-arm —
     * the reference re-arms unconditionally, the store never runs below
     * 300 while submerged).
     */
    public static final int LAVA_BURN_TICKS = 15 * 20;
    /**
     * The lava wound (takeLavaDamage line 359's flat {@code 4.0F} with
     * the LAVA source — isFire for the resistance consult): one wound
     * per hurt-i-frame window (the reference's invulnerableTicks/2 = 10).
     */
    public static final float LAVA_DAMAGE_PER_WOUND = 4.0f;
    /** The lava wound's cadence (the hurt-i-frame window, 10 ticks). */
    public static final int LAVA_WOUND_CADENCE_TICKS = 10;
    /**
     * The in-wall wound (the reference's takeDamage(IN_WALL, 1.0F) —
     * DamageSource.setBypassesArmor, "suffocated in a wall") at the
     * hurt-i-frame cadence (the same 10-tick window the lava arm rides).
     */
    public static final float IN_WALL_DAMAGE_PER_WOUND = 1.0f;
    /** The in-wall wound's cadence (the hurt-i-frame window, 10 ticks). */
    public static final int SUFFOCATION_CADENCE_TICKS = 10;

    /**
     * Minimal world query the mob needs (tick-thread context only). The
     * line and fluid queries default to open/empty so tests and simple
     * worlds only implement what they use.
     */
    public interface WorldQuery extends net.zaminmc.torch.server.physics.HasCollisionWorld {
        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);

        /**
         * The shape-aware point test (the collision-shape slice): whether
         * the point lies inside the collision shape of its cell — the
         * default folds to the boolean world so stub worlds keep their
         * semantics.
         */
        default boolean isSolidAt(double x, double y, double z) {
            return isSolid(x, y, z);
        }

        /**
         * The view-blocking read (the in-wall suffocation slice — the
         * reference's {@code Block.isViewBlocking()} over the cell:
         * {@code material.blocksMovement() && isCube()}); the stub worlds
         * fold to the boolean plane, the live query maps the full-cube
         * consult.
         */
        default boolean viewBlockingAt(double x, double y, double z) {
            return isSolid(x, y, z);
        }

        /**
         * The top surface of the collision shape in the point's cell (the
         * step/ground target); the default mirrors the boolean world's
         * full-cube snap. Non-solid cells answer negative infinity.
         */
        default double supportY(double x, double y, double z) {
            return isSolid(x, y, z) ? Math.floor(y) + 1.0 : Double.NEGATIVE_INFINITY;
        }

        /** @return the nearest playing player's position within {@code range} blocks, or null. */
        Position nearestPlayer(double x, double y, double z, double range);


        /**
         * @return whether nothing solid blocks the straight segment between
         * the two eye points (the shooter's line-of-sight test).
         */
        default boolean clearLine(Position from, Position to) {
            return true;
        }

        /** @return whether the block containing this point is a fluid. */
        default boolean inFluid(double x, double y, double z) {
            return false;
        }

        /**
         * The rain read (Slice 30d — the wolf's {@code isInWaterOrRain}
         * pair, Entity.isInWaterOrRain over World.isRainingAt): whether the
         * rain flag is up and the point sits above the precipitation
         * height at its column. The stub worlds answer dry.
         */
        default boolean isRainingAt(double x, double y, double z) {
            return false;
        }

        /**
         * The sky light level of the cell (0..15, the enderman's daylight
         * exposure read); the stub worlds answer full bright — the real
         * query maps the world's sky light.
         */
        default int skyLight(int x, int y, int z) {
            return 15;
        }

        /**
         * The sunny read (World.isSunny line 627: {@code ambientDarkness
         * < 4} — the calculateAmbientDarkness walk over the time of day
         * and the weather gradients, 0 at clear noon). The stub worlds
         * answer sunny (the bright-day default the escape goal's negative
         * pins override).
         */
        default boolean isSunny() {
            return true;
        }

        /**
         * The cell's sky access (World.hasSkyAccess line 413 over
         * WorldChunk line 652's heightmap read: {@code y >= heightMap[x |
         * z << 4]}): whether the open sky stands above the cell. The
         * stub worlds answer sky-open everywhere (the full-sky light
         * default's own shape).
         */
        default boolean hasSkyAccess(int x, int y, int z) {
            return true;
        }

        /**
         * The brightness at a cell (the reference's
         * {@code Entity.getBrightness} — the dimension's brightness table
         * over the raw light {@code max(block, sky - ambientDarkness)},
         * Explosion-adjacent World laws): the day-neutral kinds' own gate
         * reads it at the eye. The stub worlds answer full bright.
         */
        default float brightness(int x, int y, int z) {
            return 1.0f;
        }

        /**
         * The scoreboard team the member rides (the reference's key law —
         * players by NAME, {@code PlayerEntity.getScoreboardTeam} line
         * 1634; every other living body by its UUID STRING,
         * {@code LivingEntity.getScoreboardTeam} line 1635 over
         * {@code getTeamOfMember}). The stub worlds answer teamless.
         */
        default String teamNameOf(String member) {
            return null;
        }

        /**
         * The hunt scan's same-team refusal ({@code TrackTargetGoal
         * .isTarget} lines 102-106: the hunter's team non-null and the
         * candidate's the same team — refused): the nearest live mob of
         * the kind NOT on the given team; a null team folds to the plain
         * scan (a teamless body never refuses).
         */
        default MobEntity nearestMobOfKind(double x, double y, double z, double range,
                                           MobType kind, String excludeTeam) {
            return nearestMobOfKind(x, y, z, range, kind);
        }

        /**
         * The player hunt's own same-team refusal ({@code
         * MobEntityPlayerTargetGoal.shouldContinue} lines 90-94 over the
         * NAME key): the nearest live player not on the given team; a
         * null team folds to the plain scan.
         */
        default Position nearestPlayer(double x, double y, double z, double range,
                                       String excludeTeam) {
            return nearestPlayer(x, y, z, range);
        }

        /**
         * The iron golem's own hostile scan (the reference's golem-side
         * {@code ActiveTargetGoal} override, IronGolemEntity lines 247-283:
         * the creeper exclusion the override's own predicate carries, line
         * 256-258, over the shared same-team refusal): the nearest live
         * hostile mob, the given kind and team refused. A null kind or team
         * folds the matching gate off.
         */
        default MobEntity nearestHostileMob(double x, double y, double z, double range,
                                            MobType excludeKind, String excludeTeam) {
            return nearestHostileMob(x, y, z, range);
        }

        /** @return whether the block containing this point is lava. */
        default boolean inLava(double x, double y, double z) {
            return false;
        }

        /** @return whether the block containing this point is fire. */
        default boolean inFire(double x, double y, double z) {
            return false;
        }

        /**
         * The {@code mobGriefing} rule's view (Slice 24a — the reference
         * reads it at the mob's own world: the enderman's carry pair, the
         * rabbit's garden raid, the farmland trample, the creeper's
         * block-damaging blast). The stub worlds answer the default-true
         * stand-in (the rule's own default view); the engine's query
         * overrides with the live table read.
         */
        default boolean mobGriefing() {
            return true;
        }

        /**
         * The dimension context switch (Slice 25d — the reference's
         * Entity.world: every body reads its own world). The shared query
         * flips its active dimension before each body's walk; the stub
         * worlds keep the single-dimension default.
         */
        default void setActiveDimension(int dimension) {
        }

        /**
         * Whether the cell is iron bars or a bed (Slice 25a — the zombie
         * villager's cure accelerator, ZombieEntity
         * .getTicksUntilConversionDecrement lines 549-559: the 1%-roll scan
         * counts IRON_BARS and BED cells). The stub worlds answer false;
         * the engine's query maps the live block.
         */
        default boolean isIronBarsOrBed(int x, int y, int z) {
            return false;
        }

        /** @return whether the point's block (or the one below) is a cactus. */
        default boolean touchingCactus(double x, double y, double z) {
            return false;
        }

        /**
         * The path scanner's material for the cell (the vanilla
         * {@code getMaterial}/{@code canWalkThrough} pair the
         * WalkNodeEvaluator branches on). The stub worlds derive it from
         * the point solidity at the cell center; the real query maps the
         * actual block through {@code PathBlocks}.
         */
        default net.zaminmc.torch.server.entity.ai.pathing.CellMaterial pathMaterialAt(int x, int y, int z) {
            return isSolid(x + 0.5, y + 0.5, z + 0.5)
                    ? net.zaminmc.torch.server.entity.ai.pathing.CellMaterial.SOLID
                    : net.zaminmc.torch.server.entity.ai.pathing.CellMaterial.AIR;
        }

        /**
         * The nearest player's live view within {@code range} — the
         * tempt/beg family's read (TemptGoal's {@code getNearestPlayer(mob,
         * 10.0)} + {@code getItemInHand()}). The default answers null so
         * stub worlds keep their no-player semantics: a mind without the
         * view never tempts and never begs.
         */
        default PlayerView nearestPlayerView(double x, double y, double z, double range) {
            return null;
        }

        /**
         * The OWNER's live view (FollowOwnerGoal's {@code getOwner()}
         * consult — TameableEntity's own walk over the world's players,
         * the uuid arm first with the name row the legacy fallback). The
         * default answers null so stub worlds carry no owner: the pet
         * without its owner never follows. The engine's live query
         * resolves the same consult {@link MobEntity#isOwner} carries.
         */
        default PlayerView ownerPlayerView(String ownerName, String ownerUuid) {
            return null;
        }

        /**
         * The count of players with a chest window open at the cell
         * (OcelotSitOnBlockGoal.isValidTarget's {@code viewerCount < 1}
         * read over ChestBlockEntity's own field). The default answers 0
         * so stub worlds carry no viewers — an unwatched chest is a
         * valid perch.
         */
        default int containerViewersAt(int x, int y, int z) {
            return 0;
        }

        /**
         * The nearest live mob of the kind within {@code range} — the
         * mob-vs-mob target family's read (ActiveTargetGoal's scan and
         * FleeEntityGoal's scare range). The default answers null so stub
         * worlds carry no mob-vs-mob mind.
         */
        default MobEntity nearestMobOfKind(double x, double y, double z, double range, MobType kind) {
            return null;
        }

        /**
         * The nearest live HOSTILE mob within {@code range} — the golem
         * family's read (TrackIronGolemTargetGoal + the snow golem's
         * ActiveTargetGoal over Monster.VISIBLE_MONSTER_FILTER). The
         * default answers null so stub worlds carry no golem mind.
         */
        default MobEntity nearestHostileMob(double x, double y, double z, double range) {
            return null;
        }

        /**
         * Whether the position's biome runs hot (the reference's
         * temperature > 1.0 — the snow golem's melt arm). The default
         * answers false so stub worlds stay temperate.
         */
        default boolean biomeHotAt(double x, double y, double z) {
            return false;
        }

        /**
         * The block identifier of the cell (Slice 18c — the silverfish's
         * InfestStone roll reads the adjacent block's identity). The
         * default answers null so stub worlds never host the infestation;
         * the real query maps the world's own block read.
         */
        default net.zaminmc.torch.util.Identifier blockIdAt(int x, int y, int z) {
            return null;
        }

        // ----------------------------------------------------------
        // The wither's quarry lookups (Slice 59 — the three-head model's
        // World.getEntity family over the engine's shared id band)
        // ----------------------------------------------------------

        /**
         * The quarry's existence read (WitherEntity.mobAiTick line 244's
         * {@code this.world.getEntity(k)}): the id boxed when a live body
         * carries it, null when none — the callers treat null and dead
         * alike (the head clears either way).
         */
        default Integer entityById(int entityId) {
            return null;
        }

        /**
         * The quarry's live position by id (the heads' aim walk), or null
         * when no live body carries the id.
         */
        default Position entityPositionById(int entityId) {
            return null;
        }

        /**
         * The body's eye height by id (the aim point and the sight ray's
         * far end — {@code entity.getEyeHeight()}); 0.0 when unknown.
         */
        default double eyeHeightOf(int entityId) {
            return 0.0;
        }

        /**
         * Whether the body is a player under the invulnerable ability
         * (creative — PlayerEntity.abilities.invulnerable): the heads
         * never aim at one and the acquisition walk skips them
         * (mobAiTick lines 247-248 and 263-266).
         */
        default boolean entityInvulnerablePlayer(int entityId) {
            return false;
        }

        /**
         * Every playing player's engine id (EnderDragonEntity.chooseTarget
         * lines 380-388's own candidate walk — the spectator filter rides
         * the caller's own gates); empty when the consult is absent.
         */
        default int[] playerIds() {
            return new int[0];
        }

        /**
         * Whether an ender crystal stands at the position
         * (tickEndCrystalInteraction line 326's {@code connectedCrystal
         * .removed} read — the link's own liveness probe).
         */
        default boolean enderCrystalAliveAt(double x, double y, double z) {
            return false;
        }

        /**
         * The nearest ender crystal's position within the grown range
         * (tickEndCrystalInteraction lines 337-352's own scan — the
         * 32-grown box's nearest walk), or null when none/absent.
         */
        default double[] nearestEnderCrystal(double x, double y, double z, double range) {
            return null;
        }

        /**
         * The living non-undead candidates in the box grown by
         * {@code halfX/halfY/halfX} around the point (mobAiTick lines
         * 255-257's getEntitiesOfType over the UNDEAD_FILTER — creative
         * players stay in, the pick walk rejects them; the caller's own
         * id rides out through {@code excludeId}, the {@code != this}
         * check at line 262). Empty when the world carries none.
         */
        default int[] livingNonUndeadIdsIn(double x, double y, double z,
                                           double halfX, double halfY, int excludeId) {
            return new int[0];
        }

        /**
         * The main head's quarry read (line 280's
         * {@code getAttackTarget().getNetworkId()}): the engine id of the
         * living body the hunt stands on at the target position, 0 when
         * none resolves.
         */
        default int nearestVisibleEntityId(Position target) {
            return 0;
        }

        /** The difficulty read (NORMAL or HARD — {@code getDifficulty()}). */
        default boolean difficultyAtLeastNormal() {
            return false;
        }

        /**
         * The HARD read (VIII9 — BreakDoorGoal.tick line 53's own gate:
         * the door itself breaks on HARD alone) + ZombieEntity
         * .takeDamage line 200's reinforcement roll. The stub worlds
         * answer the plain band (nothing breaks, nothing calls).
         */
        default boolean difficultyHard() {
            return false;
        }

        /**
         * The eye-to-eye sight ray (Entity.canSee): whether nothing solid
         * blocks the segment between the two eye points. The default
         * bridges to the shooter's line so stub worlds keep their open
         * semantics.
         */
        default boolean hasLineOfSight(double x1, double y1, double z1,
                                       double x2, double y2, double z2) {
            return clearLine(new Position(x1, y1, z1), new Position(x2, y2, z2));
        }
    }

    /**
     * The player view the mind's awareness goals read (TemptGoal's
     * {@code player} reference): the live position and rotation every tick
     * (the scared-by-movement check compares them against the last seen
     * sample) plus the held item's identity (the tempt and the beg gates).
     */
    public interface PlayerView {
        /** The player's live position. */
        Position position();

        /** The player's live yaw (the scared rotation check). */
        float yaw();

        /** The player's live pitch (the scared rotation check). */
        float pitch();

        /** The held item's identifier, or null when the hand is empty. */
        String heldItem();

        /**
         * The helmet slot's identifier, or null when bare (Slice 19d —
         * the enderman's stare gate: a carved pumpkin blinds the stare
         * check, EndermanEntity.isLookedAtBy's armor[3] read). The
         * default answers null so stub worlds keep their no-armor
         * semantics.
         */
        default String helmetItem() {
            return null;
        }

        /**
         * Whether the player rides the spectator mode (FollowOwnerGoal's
         * canStart refusal — a spectating owner is not followed). The
         * default answers false so stub worlds keep their survival
         * semantics.
         */
        default boolean spectator() {
            return false;
        }
    }

    /** What the mob is doing (the vanilla-style goal set of this slice). */
    public enum Mode {
        IDLE,
        WANDER,
        PANIC,
        CHASE,
        /** Skeleton: standing in its shooting band, facing the target. */
        STRAFE,
        /** Creeper: primed, fuse burning down. */
        FUSE
    }

    private final int entityId;
    private final MobType type;
    private final Random random;
    private final WorldQuery world;

    private Position position;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private float yaw;
    private float headYaw;
    private float health;
    /** The absorption hearts (LivingEntity.absorption, line 126). */
    private float absorption;
    /** The breath store (Entity's syncedData 1 — 300 ticks of air). */
    private int air = 300;
    /** The fall accumulator (Entity.fallDistance, line 91). */
    private float fallDistance;

    /**
     * The step-sound walk's accumulated distance (Entity.distanceMoved,
     * line 90): every move adds the actual traveled delta scaled by 0.6;
     * the {@link #blocksWalkedOn} crossing fires the footstep. Not
     * persisted — the reference's own NBT carries neither row (a fresh
     * body starts at zero over the init {@code blocksWalkedOn = 1}).
     */
    private float distanceMoved;

    /**
     * The next footstep's threshold (Entity.blocksWalkedOn, line 92 —
     * the constructor's init 1): the crossing sets it to
     * {@code (int) distanceMoved + 1}, one step per block of
     * accumulated (0.6-scaled) travel.
     */
    private int blocksWalkedOn = 1;
    /** The portal re-entry cooldown (Entity.portalCooldown, line 128). */
    private int portalCooldown;
    /** The last wound's age stamp (LivingEntity.lastAttackedTime, line 118). */
    private int hurtByTimestamp;
    /** The body's own age in ticks (Entity.ticks — the stamp's clock). */
    private int age;
    private boolean onGround;
    /** The body's command-stat bindings (the reference's Entity field). */
    private final net.zaminmc.torch.server.command.EngineCommandResults commandResults =
            new net.zaminmc.torch.server.command.EngineCommandResults();

    private Mode mode = Mode.IDLE;
    private int modeTicks;
    // The path-follow state (the vanilla navigation port): the route lives
    // in the navigation (Path + the walking index), the feet walk the
    // heading it reports, the repath budget re-arms on both outcomes.
    private final net.zaminmc.torch.server.entity.ai.pathing.MobNavigation navigation;
    private int repathCooldown;
    private float pathYaw;
    private boolean pathYawValid;
    private int panicTicks;
    private int hurtFlash;
    private int attackCooldown;
    /**
     * The reference's {@code ambientSoundDelay} (MobEntity line 46): the
     * plain field, 0 at construction, climbing one per alive baseTick, reset
     * to {@code -getAmbientSoundInterval()} on every play — the VIII14
     * ambient-sound cadence's own clock (the named adaptation's idle timer
     * it replaces never matched the reference's law).
     */
    private int ambientSoundDelay;
    private boolean dead;
    private int deathTicks;
    /** The killer's Looting level (the reference reads it at death from the killer entity). */
    private int lootingLevel;
    /** The loot-pickup flag (MobEntity.n — the initialize roll, the villager's always-true). */
    private boolean canPickUpLoot;
    /**
     * The five equipment slots' drop chances (MobEntity
     * .inventoryDropChances, line 64 + the constructor's 0.085 fill): the
     * death walk's own gate — a picked-up slot carries the reference's
     * own 2.0 (an always-drop marker above the 1.0 ceiling).
     */
    private final float[] inventoryDropChances = {0.085f, 0.085f, 0.085f, 0.085f, 0.085f};

    // Steering: the wall-slide detour (a blocked walker slides along the wall
    // for a few ticks instead of grinding into it).
    private float detourYaw;
    private int detourTicks;

    // Sheep coat.
    private boolean sheared;
    private int regrowTimer;

    // Ranged skeleton: a shot armed by the mind, consumed by the manager.
    private boolean pendingRangedShot;
    private Position rangedTarget;

    // Primed creeper: the mind arms, the manager detonates and removes.
    private boolean pendingExplosion;

    // The creeper's own rows (CreeperEntity lines 26-28 + syncedData 17/18,
    // Slice 91 Wave C p2): the charged flag (powered — the lightning
    // strike's, doubling the blast), the permanent ignite (the flint/NBT
    // flag — the swell never cools), the swell's own duration (Fuse), and
    // the blast's own radius (ExplosionRadius).
    private boolean creeperCharged;
    /** The charged-creeper head-drop counter (CreeperEntity line 29). */
    private int mobHeadDropCount;
    /** The killing blast's charged-creeper source (the die walk's attribution). */
    private MobEntity chargedCreeperKiller;
    private boolean creeperIgnited;
    private int creeperFuseTime = CREEPER_FUSE_TICKS;
    private int creeperExplosionRadius = 3;

    // The fire clock: armed by fire contact (and the dawn sun for the
    // undead), deals the historical 1 damage per second while burning.
    private int fireTicks;
    private int fireDamageTimer;
    // The cactus's contact clock (the historical 1 damage per second).
    private int cactusDamageTimer;
    // The lava wound's cadence clock (Slice 93/D2 — the reference's
    // hurt-i-frame window: one 4.0 wound per 10 submerged ticks).
    private int lavaDamageTimer;
    private int suffocationTimer;

    // The goal layer (the community PathfinderGoal architecture).
    private final GoalSelector goals = MobGoals.standard();
    // The registration surface built on first use (the public goals() view).
    private net.zaminmc.torch.entity.ai.GoalRegistration goalRegistration;

    /**
     * The door-break read's live view (created once, on first use — the
     * same lazy shape {@link #goals()} carries; stateless over the body's
     * own fields, so one view serves every read, §27.5).
     */
    private net.zaminmc.torch.entity.ai.DoorBreak doorBreakView;
    // The owner-lane admission for mind mutations (wired by the manager at
    // spawn; null in bare harnesses — the caller then owns the context).
    private java.util.function.Consumer<Runnable> goalAdmission;
    private Position visibleTarget;
    private boolean targetHuntable;

    // ------------------------------------------------ mounts (the horse/pig slice)

    /** The horse flag bits (the 1.8 DataWatcher index-16 Int, ProtocolSupport/Glowstone-verified). */
    public static final int HORSE_FLAG_TAMED = 0x02;
    public static final int HORSE_FLAG_SADDLED = 0x04;
    public static final int HORSE_FLAG_EATING = 0x20;
    public static final int HORSE_FLAG_REARING = 0x40;
    public static final int HORSE_FLAG_MOUTH_OPEN = 0x80;

    /** Horse subtypes (the 1.8 DataWatcher index-19 Byte): horse, donkey, mule. */
    public static final int HORSE_SUBTYPE_HORSE = 0;
    public static final int HORSE_SUBTYPE_DONKEY = 1;
    public static final int HORSE_SUBTYPE_MULE = 2;

    /** Armor rows (the 1.8 DataWatcher index-22 Int): none, iron, gold, diamond. */
    public static final int HORSE_ARMOR_NONE = 0;
    public static final int HORSE_ARMOR_IRON = 1;
    public static final int HORSE_ARMOR_GOLD = 2;
    public static final int HORSE_ARMOR_DIAMOND = 3;

    /** Temper gained per mount attempt on an untamed horse (the historical +5). */
    public static final int TEMPER_PER_ATTEMPT = 5;
    /** Temper the common foods add per feeding (sugar/wheat/apple band). */
    public static final int TEMPER_PER_FEED = 3;
    /** Ticks a bucking horse keeps its rider before the throw (the rear-and-throw feel). */
    public static final int BUCK_THROW_TICKS = 20;
    /** Degrees per tick a mount turns at full sideways input (the 1.8 A/D steering). */
    public static final float RIDDEN_TURN_RATE = 3.5f;
    /** Controlled horse forward speed in blocks/tick (the canter). */
    public static final double HORSE_RIDE_SPEED = 0.115;
    /** Controlled pig forward speed in blocks/tick (slower than the horse). */
    public static final double PIG_RIDE_SPEED = 0.07;
    /** Jump impulse of a ridden horse (the ~2-block hop). */
    public static final double HORSE_JUMP_IMPULSE = 0.48;
    /** Jump impulse of a ridden pig (the small hop). */
    public static final double PIG_JUMP_IMPULSE = 0.30;
    /** Ticks between jumps of a ridden mount (the historical cooldown). */
    public static final int RIDE_JUMP_COOLDOWN = 10;
    /** Rider seat height above the mount's feet (the saddle top). */
    public static final double HORSE_SEAT_HEIGHT = 1.12;
    public static final double PIG_SEAT_HEIGHT = 0.55;

    /** The outcome of a mount attempt on an untamed horse (the vanilla temper flow). */
    public enum MountAttempt { BUCKED, TAMED, MOUNTED }

    private int horseSubtype = HORSE_SUBTYPE_HORSE;
    private int variant;
    private int horseFlags;
    private int armorType = HORSE_ARMOR_NONE;
    private int temper;
    private String ownerName = "";
    /**
     * The owner's uuid string (the reference's OwnerUUID row — the
     * syncedData 21/17 string IS the uuid at tame time; the engine's
     * name model rides beside it, the gates consult the uuid first).
     */
    private String ownerUuid = "";
    /**
     * The horse's breeding markers (HorseBaseEntity.bred/hasBred, lines
     * 85-86): the rows round-trip; the breeding-gate consults are the
     * engine's no-horse-breeding envelope.
     */
    private boolean horseBred;
    private boolean horseHasReproduced;
    private int riderId = -1;
    private float steerSideways;
    private float steerForward;
    private boolean steerJump;
    private boolean pigSaddled;
    private int jumpCooldown;
    private int buckTicks;
    private boolean pendingBuckThrow;
    private int eatingTicks;

    // The breeding state (the vanilla AnimalEntity age/love) and the donkey
    // chest: breedingAge < 0 grows up one tick at a time to 0 (the baby),
    // > 0 is the cooldown after breeding; love runs the historical 600
    // ticks; the chest rides horse flag 0x08 and holds the 15-slot grid.
    private int breedingAge;
    private int loveTicks;
    private int lovePlayerId = -1;
    private int breedTimer;
    private boolean chested;
    private net.zaminmc.torch.item.ItemStack[] chestSlots;

    // The leash (Slice 11c — the reference's own MobEntity fields): the
    // held flag + the holder, one of the two forms — a player's hand or
    // a fence's knot. The holder never survives its own removal (the
    // tick walk's holder-gone arm).
    private boolean leashed;
    private net.zaminmc.torch.server.player.PlayerSession leashHolderPlayer;
    private net.zaminmc.torch.server.leash.LeashKnot leashHolderKnot;

    // The despawn state (Slice 11d — the reference's own fields): the
    // persistent refuse every despawn (the leashed family), the far
    // window counts up while no player stands within 32 blocks.
    private boolean persistent;
    private int farFromPlayerTicks;

    // The zombie villager's conversion state (Slice 25a — ZombieEntity's
    // own fields): the villager skin flag (the synced slot 13's byte) and
    // the cure's countdown (ticksUntilConversion; -1 = not converting —
    // the synced slot 14's byte answers that). The completing tick's flag
    // hands the conversion to the manager's own walk.
    private boolean zombieVillager;
    private int ticksUntilConversion = -1;
    private boolean pendingZombieConversionComplete;

    /**
     * The body's dimension (Slice 25d — the reference's Entity.dimension:
     * 0 the overworld, -1 the nether). The shared world query flips to
     * the body's own world before every walk (setActiveDimension) — the
     * nether mobs' physics, scans, and spawns read the nether's blocks.
     */
    private int dimension;

    /** @return the dimension the body lives in (0 overworld, -1 nether). */
    public int dimension() {
        return dimension;
    }

    /** Sets the body's dimension (the spawn walks and the restore stamp it). */
    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    /** The despawn's persistence flag (the leashed family and the tamed tameables never leave). */
    public boolean persistent() {
        // The reference's checkDespawn gate: a CUSTOM-NAMED body never
        // despawns either (Slice 75 — the /summon NBT arm's own law).
        return persistent || leashed || customName != null
                || (isTameableKind() && wolfTamed());
    }

    /**
     * The reference's {@code canDespawn()} — whether the despawn rules
     * apply at all. The monsters answer true; the animals false
     * (AnimalEntity's own override — a penned cow stays penned); the wolf
     * answers true only UNTAMED and past 2400 ticks (its own override —
     * a lone wild wolf moves on after two minutes, a tamed one never
     * leaves its owner behind); the BOSS family answers false outright
     * (the wither's and the dragon's checkDespawn overrides replace the
     * whole walk — the reference's bosses never leave, whatever the
     * distance).
     */
    public boolean canDespawn() {
        // ZombieEntity.canDespawn (line 511): the converting body never
        // leaves — the cure's countdown outlives every despawn window.
        if (type == MobType.ZOMBIE && zombieConverting()) {
            return false;
        }
        // The boss family's never-despawn law (WitherEntity.checkDespawn
        // lines 462-464 + EnderDragonEntity.checkDespawn lines 587-589 —
        // the reference's overrides REPLACE the whole walk, so neither
        // the 128-block instant arm nor the 600-tick idle window ever
        // fires: a boss stays until it is killed, whatever the distance).
        if (type == MobType.WITHER || type == MobType.ENDER_DRAGON) {
            return false;
        }
        if (isTameableKind()) {
            return !wolfTamed() && wolfTicksExisted > 2400;
        }
        return type.hostile;
    }

    /** @return whether the kind carries TameableEntity's state family (the wolf and the ocelot). */
    private boolean isTameableKind() {
        return type == MobType.WOLF || type == MobType.OCELOT;
    }

    /** Marks the body persistent (named/bred mobs ride their own slices). */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * The RAW persistence mark (Slice 75 — the reference's
     * PersistenceRequired row): the store's own read, without the
     * leashed/tamed/named derivations (those derive live after the load —
     * a saved raw false never silently pins a body whose leash is not
     * itself persisted).
     */
    public boolean rawPersistent() {
        return persistent;
    }

    // The /summon NBT rows (Slice 75 — the reference's Entity NBT fields
    // the engine's mob model carries): the custom name pair (the wire's
    // synced slots 2/3), the NoAI pin, and the invulnerability flag.
    private String customName;
    private boolean customNameVisible;
    private boolean noAi;
    private boolean invulnerable;

    // The EntityNbt rows (Slice 89 — the reference's Entity base rows the
    // engine now carries): the body's own uuid (UUIDMost/UUIDLeast — a
    // random one at construction, the readNbt rows overwrite it), the
    // Silent flag, and the body pitch (the Rotation row's second element —
    // the engine's own no-consult envelope keeps it storage-only).
    private java.util.UUID uuid = java.util.UUID.randomUUID();
    private boolean silent;
    private float pitch;

    /** @return the custom name, or null when the body carries none. */
    public String customName() {
        return customName;
    }

    /** Sets the custom name (a null/empty argument clears it — readNbt's own shape). */
    public void setCustomName(String name) {
        this.customName = name == null || name.isEmpty() ? null : name;
    }

    /** @return whether the custom name renders without a hover (CustomNameVisible). */
    public boolean customNameVisible() {
        return customNameVisible;
    }

    /** Sets the name's always-render flag (CustomNameVisible). */
    public void setCustomNameVisible(boolean visible) {
        this.customNameVisible = visible;
    }

    /** @return whether the AI walk is pinned (NoAI — the body stands still). */
    public boolean noAi() {
        return noAi;
    }

    /** Pins or frees the AI walk (NoAI). */
    public void setNoAi(boolean noAi) {
        this.noAi = noAi;
    }

    /** @return whether the body refuses every wound (Invulnerable). */
    public boolean invulnerable() {
        return invulnerable;
    }

    /** Sets the invulnerability flag (Invulnerable). */
    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    /** @return the body's own uuid (the UUIDMost/UUIDLeast rows — Slice 89). */
    public java.util.UUID getUuid() {
        return uuid;
    }

    /** Sets the body's uuid (readNbt's own rows). */
    public void setUuid(java.util.UUID uuid) {
        this.uuid = uuid;
    }

    /** @return whether the body makes no sound (Silent — Slice 89). */
    public boolean isSilent() {
        return silent;
    }

    /** Sets the silence flag (Silent). */
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    /** @return the body pitch (the Rotation row's second element — Slice 89). */
    public float pitch() {
        return pitch;
    }

    /** Sets the body pitch (readNbt's Rotation row). */
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    /** @return the hurt animation's remaining ticks (the HurtTime row — Slice 89). */
    public int hurtTime() {
        return hurtFlash;
    }

    /** Sets the hurt animation ticks (the HurtTime row). */
    public void setHurtTime(int hurtTime) {
        this.hurtFlash = Math.max(0, hurtTime);
    }

    /** @return the death animation's elapsed ticks (the DeathTime row — Slice 89). */
    public int deathTime() {
        return deathTicks;
    }

    /** Sets the death animation ticks (the DeathTime row). */
    public void setDeathTime(int deathTime) {
        this.deathTicks = Math.max(0, deathTime);
    }

    /** Sets the whole motion (readNbt's Motion row — Slice 89). */
    public void setVelocity(double vx, double vy, double vz) {
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
    }

    /** Sets the on-ground flag (readNbt's OnGround row — Slice 89). */
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    /**
     * Sets the whole rotation (readNbt's Rotation row — Slice 89): the
     * yaw, the pitch, and the head yaw follows the yaw (the reference's
     * setHeadYaw(yaw)/setBodyYaw(yaw) at read time).
     */
    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.headYaw = yaw;
    }

    /** @return the raw movement-speed override, or NaN when unset (the Attributes Base row). */
    public double movementSpeedOverride() {
        return movementSpeedOverride;
    }

    /** @return the raw attack-damage override, or NaN when unset (the Attributes Base row). */
    public double attackDamageOverride() {
        return attackDamageOverride;
    }

    // ---- the public LivingEntity handle (§27.5: the engine object IS the
    // handle — every call resolves to live authoritative state) ----------

    /** The manager's removal hook, set at spawn (the despawn path). */
    private Runnable removalHook;

    /** Called once by the owning manager at spawn. */
    public void setRemovalHook(Runnable removalHook) {
        this.removalHook = removalHook;
    }

    /**
     * The owner-lane admission for mind mutations (§27.6's explicit command
     * protocol): the manager wires the world's {@code execute} at spawn, so
     * goal registrations land between ticks regardless of the calling
     * thread. Null in bare harnesses — the caller then owns the context.
     */
    public void setGoalAdmission(java.util.function.Consumer<Runnable> admission) {
        this.goalAdmission = admission;
    }

    /** The wired admission (null when bare). */
    java.util.function.Consumer<Runnable> goalAdmission() {
        return goalAdmission;
    }

    /** The mind's selector (package view for the registration surface). */
    GoalSelector goalSelector() {
        return goals;
    }

    @Override
    public net.zaminmc.torch.entity.ai.GoalRegistration goals() {
        net.zaminmc.torch.entity.ai.GoalRegistration surface = goalRegistration;
        if (surface == null) {
            surface = GoalRegistrationSurface.of(this);
            goalRegistration = surface;
        }
        return surface;
    }

    /**
     * The built-in door-break read (VIII9 — the BREAK_DOOR band's arming
     * + progress, the §27.10 exposure beside {@link #goals()}): the live
     * view over the body's own walk state — the {@code CanBreakDoors}
     * flag, the armed capture, the door cell, the 240-tick clock. Present
     * on the zombie family alone (the goal never arms on another kind —
     * {@link #setZombieCanBreakDoors}'s own gate); every other body
     * answers the api's empty default. Read-only: the walk itself stays
     * exactly as tested (the selector's own band-1 law).
     */
    @Override
    public java.util.Optional<net.zaminmc.torch.entity.ai.DoorBreak> doorBreak() {
        if (type != MobType.ZOMBIE) {
            return java.util.Optional.empty();
        }
        net.zaminmc.torch.entity.ai.DoorBreak view = doorBreakView;
        if (view == null) {
            view = new DoorBreakView();
            doorBreakView = view;
        }
        return java.util.Optional.of(view);
    }

    /**
     * The live door-break view (§27.5 — the engine object IS the handle:
     * every read resolves to the body's own state at the call, never a
     * copy; the per-call {@link net.zaminmc.torch.block.BlockPosition}
     * answer is the immutable cell value).
     */
    private final class DoorBreakView implements net.zaminmc.torch.entity.ai.DoorBreak {

        @Override public boolean canBreakDoors() {
            return zombieCanBreakDoors;
        }

        @Override public boolean breaking() {
            return zombieDoorBreakArmed;
        }

        @Override public net.zaminmc.torch.block.BlockPosition doorPosition() {
            return new net.zaminmc.torch.block.BlockPosition(
                    zombieBreakDoorX, zombieBreakDoorY, zombieBreakDoorZ);
        }

        @Override public int progress() {
            return zombieBreakDoorProgress;
        }
    }

    @Override
    public net.zaminmc.torch.util.Identifier typeIdentifier() {
        return type.identifier();
    }

    @Override
    public Rotation rotation() {
        return new Rotation(yaw, pitch);
    }

    @Override
    public EntitySnapshot snapshot() {
        return new EntitySnapshot(entityId, type.identifier(), position, yaw,
                velocityX, velocityY, velocityZ, health(), maxHealth(), onGround(), dead());
    }

    @Override
    public void despawn() {
        Runnable hook = removalHook;
        if (hook != null) {
            hook.run();
        }
    }

    /** The far-from-player window (checkDespawn's own counter). */
    public int farFromPlayerTicks() {
        return farFromPlayerTicks;
    }

    /**
     * One tick of the despawn bookkeeping (serverTickAi line 442 +
     * checkDespawn's own resets, 1:1): the window counts up EVERY tick
     * unconditionally, then the persistent reset (line 419) and the
     * near-player reset (lines 433-434, inside 32 blocks) zero it. No
     * nearest player at all: the window keeps counting (the reference's
     * own behavior — a mob alone for a thousand ticks rolls the idle
     * despawn the instant a distant player appears).
     */
    public void tickDespawnWindow(boolean persistent, Double nearestSquared) {
        this.farFromPlayerTicks++; // serverTickAi line 442: unconditional
        if (persistent) {
            this.farFromPlayerTicks = 0; // line 419: the persistent reset
        } else if (nearestSquared != null && nearestSquared < 1024.0) {
            this.farFromPlayerTicks = 0; // lines 433-434: within 32 blocks
        }
    }

    // The breed plumbing: the manager's mate scanner (the vanilla
    // getEntitiesOfType scan), the goal's confirmed mate, and the heart
    // flags the manager converts into observer effects.
    private BreedFinder breedFinder;
    private MobEntity pendingBreedMate;
    private boolean pendingLoveBurst;
    private boolean pendingAmbientHearts;
    private boolean grewUpThisTick;

    /**
     * The player-stat award seam (VIII6 — the body-side award sites whose
     * walks live inside the entity's own tick, the reference's
     * {@code playerentity.incrementStat} calls): given the earning player's
     * ENGINE id and the stat key, the engine resolves the session and
     * increments. Injected by the manager at spawn (the removal-hook
     * pattern); null keeps the sites no-ops (the manager-less test bodies).
     */
    private java.util.function.BiConsumer<Integer, String> playerStatAward;

    /** Wires the player-stat award seam (the manager's own spawn walk). */
    public void setPlayerStatAward(java.util.function.BiConsumer<Integer, String> award) {
        this.playerStatAward = award;
    }

    /** The mate scanner ({@code World.getEntitiesOfType} in the goal). */
    public interface BreedFinder {
        MobEntity findMate(MobEntity seeker);
    }

    // ------------------------------------------------ the villager's trade state

    /** The villager's profession (the DataWatcher index-16 Int, 0-4). */
    private int profession;
    /**
     * The villager's stock offers (the trade list the client renders) —
     * grown by the levelUp walk, one level's rows at a time.
     */
    private final java.util.List<TradeOffer> villagerStock = new java.util.ArrayList<>();
    /** The use counters, parallel to the stock (the grey-out budget). */
    private int[] offerUses = new int[0];
    /** The live use budgets, parallel to the stock (the level-up's own
     * extension — {@code increaseMaxUses} grows them past the fresh 7). */
    private int[] offerMaxUses = new int[0];
    /** The career (0 until the first open rolls it — VillagerEntity.career). */
    private int villagerCareer;
    /** The career's level (0 until the first open; grows on the level-ups). */
    private int villagerCareerLevel;
    /** The level-up countdown (mobAiTick's 40-tick walk, 0 idle). */
    private int villagerLevelUpCountdown;
    /** Whether the pending countdown ends in a level-up (the trade's flag). */
    private boolean villagerLevelUp;
    /** The mating willingness (the food arm's flag — Slice 21g's ledger). */
    private boolean villagerWilling;
    /** The villager's riches (the emerald payments absorbed, career XP). */
    private int villagerRiches;

    public MobEntity(int entityId, MobType type, Position position,
                     Random random, WorldQuery world) {
        this.entityId = entityId;
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.random = Objects.requireNonNull(random, "random");
        this.world = Objects.requireNonNull(world, "world");
        this.health = type.maxHealth;
        this.navigation = new net.zaminmc.torch.server.entity.ai.pathing.MobNavigation(
                new PathBody(), new PathWorldView(), (float) navigationRange());
        this.modeTicks = random.nextInt(IDLE_TICKS);
        // The herd look — the reference's own initialize dice
        // (HorseBaseEntity.initialize lines 1262-1280): 1 in 10 the donkey,
        // otherwise the horse coat = the color nextInt(7) | the marking
        // nextInt(5) << 8.
        if (type == MobType.HORSE) {
            this.horseSubtype = random.nextInt(10) == 0
                    ? HORSE_SUBTYPE_DONKEY : HORSE_SUBTYPE_HORSE;
            this.variant = random.nextInt(7) | random.nextInt(5) << 8;
        }
        // The villager's profession (VillagerEntity.initialize line 808:
        // {@code world.random.nextInt(5)}): the career and its stock roll
        // LAZILY at the first trade open (getOffers → levelUp — the
        // reference's own traderOffers == null gate).
        if (type == MobType.VILLAGER) {
            this.profession = random.nextInt(5);
        }
        // OcelotEntity's ctor line 42: the cat's own navigation arms the
        // swim paths ({@code setCanSwim(true)} — the jungle cat wades to
        // its owner; the wolf's own navigation keeps the default off, the
        // follow goal's stop() being the reference's own quirk that turns
        // it on after the first window).
        if (type == MobType.OCELOT) {
            this.navigation.setCanSwim(true);
        }
        // VIII9 — initAttributes line 87: the zombie's own
        // spawnReinforcements base ({@code nextDouble() * 0.1}) — the
        // attribute registers at the body's construction, before any
        // initialize roll touches the stream.
        if (type == MobType.ZOMBIE) {
            zombieReinforcementChance = random.nextDouble() * 0.1F;
        }
        // The dragon's seven parts (ctor lines 64-72): the wire id
        // convention rides the ORDER — the spawn's eight-id block holds
        // dragonId+1..+7 (the client re-aligns its locally-built parts by
        // the same offset on the Spawn Mob 63).
        if (type == MobType.ENDER_DRAGON) {
            buildDragonParts(entityId);
        }
    }

    /**
     * The villager's career override (Slice 12b — the reference's
     * VillagePieces spawnVillagers → setProfession): the structure-spawned
     * villagers carry their building's profession (the blacksmith house's
     * 3, the priest's 2, ...); the career state resets so the first open
     * rolls against the new profession's table.
     */
    public void setVillagerProfession(int profession) {
        if (type != MobType.VILLAGER) {
            return;
        }
        this.profession = Math.max(0, Math.min(4, profession));
        villagerStock.clear();
        offerUses = new int[0];
        offerMaxUses = new int[0];
        villagerCareer = 0;
        villagerCareerLevel = 0;
    }

    public int entityId() {
        return entityId;
    }

    public MobType type() {
        return type;
    }

    public Position position() {
        return position;
    }

    /** The body's command-stat bindings (the /stats surface). */
    public net.zaminmc.torch.server.command.EngineCommandResults commandResults() {
        return commandResults;
    }

    /**
     * The plain body teleport (the reference's Entity.teleport position
     * set — spreadplayers' landing): the position moves outright, no
     * landing checks (the enderman's own {@link #teleportTo} walk is the
     * checked form). The command layer runs this on the tick context.
     */
    public void teleport(Position to) {
        this.position = Objects.requireNonNull(to, "to");
    }

    public float yaw() {
        return yaw;
    }

    public float headYaw() {
        return headYaw;
    }

    public float health() {
        return health;
    }

    /** @return the kind's health ceiling ({@code getMaxHealth} — the tamed wolf's own 20). */
    public float maxHealth() {
        if (type == MobType.WOLF && wolfTamed()) {
            return 20.0f;
        }
        // VIII9 — the leader zombie's own MAX_HEALTH multiply (initialize
        // line 449, the op-2 band): the 40..100 ceiling over the plain 20.
        if (zombieLeaderMaxHealth > 0.0f) {
            return zombieLeaderMaxHealth;
        }
        // The horse's spawn-time MAX_HEALTH roll (HorseBaseEntity.initialize
        // line 1278 over getChildHealthBonus line 1386): the rolled
        // 15 + nextInt(8) + nextInt(9) ceiling over the kind's plain 22.
        if (horseSpawnMaxHealth > 0.0f) {
            return horseSpawnMaxHealth;
        }
        return type.maxHealth;
    }

    /** Restores health, clamped at the ceiling ({@code heal}). */
    public void heal(float amount) {
        if (amount <= 0 || dead) {
            return;
        }
        health = Math.min(maxHealth(), health + amount);
    }

    /**
     * The conversion's health carry ({@code setHealth(getHealth())} —
     * MooshroomEntity's shear-into-a-cow): the new body starts at the
     * old body's value, clamped into its own ceiling.
     */
    public void setHealthCarried(float value) {
        health = Math.max(0.0f, Math.min(maxHealth(), value));
    }

    /**
     * The absorption hearts (LivingEntity lines 1623-1633: the getter
     * over the field, the setter's own clamp — a negative read lands
     * zero, the hearts never dip below empty).
     */
    public float absorption() {
        return absorption;
    }

    public void setAbsorption(float value) {
        absorption = Math.max(0.0f, value);
    }

    /**
     * The breath store (Entity lines 1538-1543 — syncedData 1's model):
     * 300 full, drained underwater to -20 where the drown wound resets
     * it to 0 (LivingEntity.baseTick lines 210-233).
     */
    public int air() {
        return air;
    }

    public void setAir(int air) {
        this.air = air;
    }

    /** The fall accumulator (Entity.fallDistance — checkFallDamage's store). */
    public float fallDistance() {
        return fallDistance;
    }

    public void setFallDistance(float fallDistance) {
        this.fallDistance = fallDistance;
    }

    /** The portal re-entry cooldown (Entity.baseTick lines 311-313 decrements). */
    public int portalCooldown() {
        return portalCooldown;
    }

    public void setPortalCooldown(int portalCooldown) {
        this.portalCooldown = portalCooldown;
    }

    /** The last wound's age stamp (HurtByTimestamp's model — setAttacker's own clock write). */
    public int hurtByTimestamp() {
        return hurtByTimestamp;
    }

    public void setHurtByTimestamp(int hurtByTimestamp) {
        this.hurtByTimestamp = hurtByTimestamp;
    }

    /** The body's own age in ticks (Entity.ticks). */
    public int age() {
        return age;
    }

    public boolean onGround() {
        return onGround;
    }

    public Mode mode() {
        return mode;
    }

    public boolean dead() {
        return dead;
    }

    /** The killer's Looting level, read by the death loot walk (the reference's getLootingLevel at die()). */
    public int lootingLevel() {
        return lootingLevel;
    }

    /** Sets the killer's Looting level before the killing hurt lands. */
    public void setLootingLevel(int lootingLevel) {
        this.lootingLevel = Math.max(0, lootingLevel);
    }

    /**
     * The loot-pickup flag (MobEntity.n / setCanPickupLoot): armed at the
     * spawn roll (the skeleton's and the zombie's 0.55 * the local
     * difficulty multiplier), always true for the villager, read back
     * from the BYTE-gated NBT row.
     */
    public boolean canPickUpLoot() {
        return canPickUpLoot;
    }

    public void setCanPickUpLoot(boolean canPickUpLoot) {
        this.canPickUpLoot = canPickUpLoot;
    }

    /** One slot's drop chance (the death walk's gate, the pickup walk's 2.0 marker). */
    public float inventoryDropChance(int slot) {
        return inventoryDropChances[Math.max(0, Math.min(inventoryDropChances.length - 1, slot))];
    }

    public void setInventoryDropChance(int slot, float value) {
        inventoryDropChances[Math.max(0, Math.min(inventoryDropChances.length - 1, slot))] = value;
    }

    /** The five slots' chances (the NBT row's own read). */
    public float[] inventoryDropChances() {
        return inventoryDropChances.clone();
    }

    public void setInventoryDropChances(float[] values) {
        for (int i = 0; i < inventoryDropChances.length
                && values != null && i < values.length; i++) {
            inventoryDropChances[i] = values[i];
        }
    }

    /**
     * The last player attacker (the stats slice): a landed player wound
     * (melee or projectile) records the session for the death's kill
     * credit — the reference's {@code DamageSource.getEntity()} read at
     * die(). Cleared never; a mob killed later by the environment keeps
     * its last player wound's credit exactly like the reference's
     * last-damage-source model.
     */
    private transient net.zaminmc.torch.server.player.PlayerSession playerKiller;

    /** Records the player whose wound landed (the kill-credit carrier). */
    public void setPlayerKiller(net.zaminmc.torch.server.player.PlayerSession attacker) {
        this.playerKiller = attacker;
    }

    /** @return the last player whose wound landed, null for environment deaths. */
    public net.zaminmc.torch.server.player.PlayerSession playerKiller() {
        return playerKiller;
    }

    /** @return true while the death animation still plays (loot comes after). */
    public boolean dying() {
        if (type == MobType.ENDER_DRAGON) {
            // The dragon's death walk (Slice 63 — tickPostDeath): the
            // 200-tick rise-and-spin IS the animation; the standard
            // 20-tick walk never applies.
            return dragonTicksSinceDeath >= 0 && dragonTicksSinceDeath < 200;
        }
        return dead && deathTicks < DEATH_TICKS;
    }

    /** @return true once the death animation finished (removal is due). */
    public boolean deathAnimationFinished() {
        if (type == MobType.ENDER_DRAGON) {
            // The walk's own end (line 541's remove() at 200) — the
            // removal rides the portal event's own consume, never the
            // loop's standard walk (the dragon's XP + portal land through
            // the pending events; the standard dropLoot finds the empty
            // loot table).
            return false;
        }
        return dead && deathTicks >= DEATH_TICKS;
    }

    public boolean hurtFlashing() {
        return hurtFlash > 0;
    }

    public boolean sheared() {
        return sheared;
    }

    /** The creeper's charged flag (syncedData 17 — powered; the blast doubles). */
    public boolean creeperCharged() {
        return creeperCharged;
    }

    /**
     * The charged-creeper head-drop gate (CreeperEntity lines 29 + 223-227):
     * one head per creeper — {@code mobHeadDropCount < 1} (the doMobLoot
     * half of the reference's own gate rides the loot walk's early return).
     */
    public boolean shouldDropMobHead() {
        return mobHeadDropCount < 1;
    }

    /** The once-gate's own counter (CreeperEntity.addMobHeadDrop line 229). */
    public void addMobHeadDrop() {
        mobHeadDropCount++;
    }

    /**
     * The killing blast's charged-creeper source (the die walk's
     * attribution — ZombieEntity.die lines 591-597, SkeletonEntity.die
     * lines 165-172, CreeperEntity.die lines 146-152): set on the wound
     * that kills, read by the loot walk, never persisted.
     */
    public void killedByChargedCreeper(MobEntity creeper) {
        this.chargedCreeperKiller = creeper;
    }

    /** @return the charged creeper whose blast killed this body, or null. */
    public MobEntity chargedCreeperKiller() {
        return chargedCreeperKiller;
    }

    public void setCreeperCharged(boolean charged) {
        this.creeperCharged = charged;
    }

    /** The creeper's permanent ignite (syncedData 18 — the swell never cools). */
    public boolean creeperIgnited() {
        return creeperIgnited;
    }

    public void setCreeperIgnited(boolean ignited) {
        this.creeperIgnited = ignited;
    }

    /** The swell's own duration (the Fuse row, default 30). */
    public int creeperFuseTime() {
        return creeperFuseTime;
    }

    public void setCreeperFuseTime(int fuseTime) {
        this.creeperFuseTime = fuseTime;
    }

    /** The blast's own radius (the ExplosionRadius row, default 3). */
    public int creeperExplosionRadius() {
        return creeperExplosionRadius;
    }

    public void setCreeperExplosionRadius(int radius) {
        this.creeperExplosionRadius = radius;
    }

    public boolean fuseActive() {
        return mode == Mode.FUSE;
    }

    /** @return the armed shot's aim point (null when none is pending). */
    public Position rangedTarget() {
        return rangedTarget;
    }

    /** Clears the consumed shot's aim point (package-private: the manager calls). */
    void clearRangedTarget() {
        rangedTarget = null;
    }

    public double velocityX() {
        return velocityX;
    }

    public double velocityY() {
        return velocityY;
    }

    public double velocityZ() {
        return velocityZ;
    }

    /**
     * The reference's {@code addVelocity} walk on the raw motion — the melee
     * knockback-enchantment extra adds
     * {@code (-sin(yaw) * level * 0.5, 0.1, cos(yaw) * level * 0.5)} on top
     * of the base {@link #knockbackFrom} impulse, exactly once, after the
     * hit has landed (reference/1.8.8 PlayerEntity.attack lines 983-992).
     */
    public void addVelocity(double dx, double dy, double dz) {
        velocityX += dx;
        velocityY += dy;
        velocityZ += dz;
    }

    /** The test seam: a body already in motion (the knockback-residual cases). */
    void setVelocityForTest(double vx, double vy, double vz) {
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
    }

    /**
     * The vanilla applyKnockback (reference/1.8.8 LivingEntity lines
     * 778-793) along the attacker's away-direction: the current motion
     * halves on every axis first (chained hits decay exactly as
     * historical), then the 0.4 impulse rides the normalized direction and
     * the rise is capped at 0.4. The yaw-derived direction is the unit
     * away-vector, so the impulse divides by 1 (the vanilla normalizes the
     * attacker-to-victim delta; a zero-distance delta would jitter, which a
     * swing yaw never produces). No knockback-resistance roll: the engine
     * registers no resistance attribute (the roll at 0 never fires).
     */
    public void knockbackFrom(double attackerYaw) {
        double radians = Math.toRadians(attackerYaw);
        double awayX = -Math.sin(radians);
        double awayZ = Math.cos(radians);
        velocityX = velocityX / 2.0 + awayX * 0.4;
        velocityY = Math.min(velocityY / 2.0 + 0.4, 0.4);
        velocityZ = velocityZ / 2.0 + awayZ * 0.4;
        onGround = false; // a primed creeper keeps priming; range governs the abort
    }

    /** Applies damage; clamps at zero and starts the death timer at zero health. */
    public void hurt(float amount) {
        hurt(amount, false);
    }

    /**
     * The damage walk's armor consult arm (applyDamage line 911 —
     * {@code getDamageAfterArmor} runs FIRST, before the effects and the
     * absorption): the wound shrinks through the equipped pieces unless the
     * source bypasses armor (the reference's {@code setBypassesArmor} family
     * — ON_FIRE, DROWN, IN_WALL, STARVE, FALL, OUT_OF_WORLD, GENERIC, MAGIC,
     * WITHER; the melee, the arrows, the explosions, the lightning, the
     * anvil, the cactus, the lava and the standing-in-fire all pass through
     * the envelope).
     */
    public void hurt(float amount, boolean bypassesArmor) {
        if (amount <= 0 || dead) {
            return;
        }
        if (!bypassesArmor) {
            amount = damageAfterArmor(amount);
        }
        // The vanilla AnimalEntity.takeDamage arm: any hit clears the love
        // (the breeding window is fragile against mobs and players alike).
        if (isInLove()) {
            resetLove();
        }
        // The hurt flash rides takeDamage's own walk (damagedTimer = 10,
        // LivingEntity.takeDamage) — it fires even for a fully absorbed
        // hit; the absorption below gates the HEALTH term alone. The age
        // stamp rides the same walk (setAttacker's lastAttackedTime =
        // ticks — the reference's own HurtByTimestamp clock).
        hurtFlash = HURT_FLASH_TICKS;
        hurtByTimestamp = age;
        // applyDamage (LivingEntity lines 913-921): the absorption eats
        // first — a fully absorbed hit lands no health change (the
        // {@code if (damage != 0.0F)} guard skips the whole health walk);
        // the eaten part shaves the store; a REMAINING hit shaves the
        // store again by the landed damage — the reference's own
        // double-shave quirk (the same damage eats health AND hearts).
        float incoming = amount;
        amount = Math.max(amount - absorption, 0.0f);
        setAbsorption(absorption - (incoming - amount));
        if (amount == 0.0f) {
            return;
        }
        setAbsorption(absorption - amount);
        health = Math.max(0.0f, health - amount);
        if (health <= 0) {
            dead = true;
            return;
        }
        if (!type.hostile && mode != Mode.PANIC) {
            panicTicks = PANIC_TICKS;
            mode = Mode.PANIC;
            modeTicks = 0;
        }
    }

    /**
     * Restores a persisted body: health from the save (clamped into the
     * historical 1..max range — a corrupt or stale value degrades to a fresh
     * body, never an immortal or dead one) and the saved facing.
     */
    public void restore(float restoredHealth, float restoredYaw) {
        this.health = Math.max(1.0f, Math.min(type.maxHealth, restoredHealth));
        this.yaw = restoredYaw;
        this.headYaw = restoredYaw;
    }

    /**
     * Shears the coat off a wooly kind. Returns whether wool was actually
     * cut (an already-bald sheep or a bald kind yields nothing); the coat
     * regrows after the historical 5-10 minute window.
     */
    public boolean shear() {
        if (!type.traits.shearable() || sheared || dead) {
            return false;
        }
        sheared = true;
        regrowTimer = SHEEP_REGROW_MIN_TICKS + random.nextInt(SHEEP_REGROW_SPREAD_TICKS);
        return true;
    }

    /**
     * Advances one tick: timers, mind (goal selection), then body (physics).
     * Day-neutral kinds only hunt while {@code night} is true. Returns true
     * when the fixed-point position or rotation changed, so the publisher
     * decides to sync.
     */
    public boolean tick(boolean night) {
        long before = fixedPoint();
        float beforeYaw = yaw;
        float beforeHeadYaw = headYaw;
        // The body's own clock (Entity.ticks — the reference's age; the
        // HurtByTimestamp stamp reads it, the reference's own shape).
        age++;

        if (type == MobType.WOLF) {
            tickWolfClock();
            // mobTick's own clears: an angry wolf with no target stands down
            // (WolfEntity.mobTick lines 178-180) — the mob target family
            // (16b) keeps the posture through goalHasTarget.
            if (wolfAngry() && !goalHasTarget(night)) {
                setWolfAngry(false);
            }
        } else if (type == MobType.OCELOT) {
            tickWolfClock(); // the tameable family's shared clock
        } else if (type == MobType.ZOMBIE_PIGMAN) {
            tickPigmanAnger(); // the anger's decay + the delayed sound (16d)
        } else if (type == MobType.GUARDIAN) {
            tickGuardianClock(); // the elder's aura cadence (17a)
        } else if (type == MobType.ENDERMITE) {
            tickEndermiteClock(); // the lifetime despawn (17c)
        } else if (type == MobType.SNOW_GOLEM) {
            tickSnowGolemEnvironment(); // the melts (17d)
        } else if (type == MobType.SILVERFISH) {
            tickSilverfishClock(); // the wake-up + the infest rolls (18c)
        } else if (type == MobType.ZOMBIE && zombieConverting()) {
            tickZombieConversion(); // the cure's countdown (25a)
        }
        if (hurtFlash > 0) {
            hurtFlash--;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        // The fire clock: the historical 1 damage per second while burning.
        // Dead bodies stop burning (the death animation freezes the body).
        // The immune body (Entity.update lines 322-327 + takeFireDamage
        // lines 739-743): the flames burn FOUR times as fast and never
        // wound — the blaze and the wither skeleton stand in their own
        // element (Slice 18d's reference-backed fix).
        if (!dead && fireTicks > 0) {
            fireTicks--;
            if (fireImmune()) {
                fireTicks -= 3; // the reference's own 4x drain
                if (fireTicks < 0) {
                    fireTicks = 0;
                }
                fireDamageTimer = 0; // no wound ever arms against the immune
            } else if (++fireDamageTimer >= 20
                    // The FIRE_RESISTANCE consult (Slice 93/D2 — LivingEntity
                    // lines 645-647's own gate over the ON_FIRE source, an
                    // isFire family member like the lava): a resistant body
                    // burns visually but never wounds — the same gate the
                    // reference's takeDamage carries for the whole fire
                    // family.
                    && effectAmplifierPlusOne(
                            net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE) == 0) {
                fireDamageTimer = 0;
                // ON_FIRE bypasses armor (DamageSource line 16's own
                // setBypassesArmor) — the burn clock never consults pieces.
                hurt(FIRE_DAMAGE_PER_SECOND, true);
            }
        }
        // The breath clock (Slice 92 — LivingEntity.baseTick lines
        // 210-233, the reference's own shape): the EYE in water drains
        // the store one tick at a time; the water-breathers skip
        // (WaterMobEntity.canBreatheUnderwater — the squid and the
        // guardian family); the WATER_BREATHING effect shields (the
        // wired amplifier query); at -20 the reset to 0 and the 2.0
        // drown wound; out of the water the store refills to 300. The
        // walk runs for the alive body whatever else owns the tick —
        // the NoAI pin gates the mind and the movement alone.
        if (!dead) {
            boolean submerged = world.inFluid(
                    position.x(), position.y() + eyeHeight(), position.z());
            boolean breathes = type == MobType.SQUID || type == MobType.GUARDIAN;
            boolean shielded = effectAmplifierPlusOne(
                    net.zaminmc.torch.server.potion.StatusEffects.WATER_BREATHING) > 0;
            if (submerged && !breathes && !shielded) {
                // updateBreathUnderwater (lines 330-333): the mob's own
                // respiration term is equipment-borne (the helmet) — the
                // engine's no-mob-armor envelope carries none, the
                // decrement is straight.
                air--;
                if (air == -20) {
                    air = 0;
                    // DROWN bypasses armor (DamageSource line 19).
                    hurt(2.0f, true); // DROWN — the reference's own 2.0F
                }
            } else {
                air = 300;
            }
            // The portal re-entry cooldown (Entity.baseTick lines 311-313):
            // a positive store ticks down; the engine's mobs carry no
            // portal walk of their own (the world's portal envelope), the
            // row round-trips for the NBT surface.
            if (portalCooldown > 0) {
                portalCooldown--;
            }
        }
        if (sheared && regrowTimer > 0) {
            regrowTimer--;
            if (regrowTimer == 0) {
                sheared = false; // the coat returns; consumeRegrown() hears it
                regrownThisTick = true;
            }
        }
        // The player's hit window decays through the death animation too
        // (LivingEntity.update lines 254-255 — the walk runs before the
        // dead gate; dropRareItem reads the live remainder).
        tickPlayerHitTimer();

        if (dead) {
            if (type == MobType.ENDER_DRAGON && dragonTicksSinceDeath >= 0) {
                // The dragon's death walk (Slice 63 — tickPostDeath lines
                // 501-543): the standard dead gate freezes every body, but
                // the dragon's own walk owns this arm — the mind's death
                // branch (the rise, the spin, the XP batches, the portal)
                // runs through tickDragonMind.
                tickDragonMind(null, false);
                return fixedPoint() != before || beforeYaw != yaw
                        || beforeHeadYaw != headYaw;
            }
            deathTicks++;
            velocityX = 0;
            velocityZ = 0;
            velocityY = 0;
            return fixedPoint() != before || beforeYaw != yaw || beforeHeadYaw != headYaw;
        }

        // A ridden body parks its mind: the rider owns the reins (the vanilla
        // AI-suspension rule). The buck clock runs and the seat physics tick.
        if (hasRider()) {
            if (buckTicks > 0) {
                buckTicks--;
                if (buckTicks == 0) {
                    horseFlags &= ~HORSE_FLAG_REARING;
                    pendingBuckThrow = true;
                }
            }
            if (jumpCooldown > 0) {
                jumpCooldown--;
            }
            if (eatingTicks > 0 && --eatingTicks == 0) {
                horseFlags &= ~HORSE_FLAG_EATING;
            }
            tickRiddenBody();
            return fixedPoint() != before || beforeYaw != yaw || beforeHeadYaw != headYaw;
        }

        // The NoAI pin (Slice 75 — the reference's aiDisabled gate over
        // the ai section): a pinned body stands still — no goals, no
        // targeting, no posture flips. The physics, the burning, the
        // despawn window, and the wounds all continue (the vanilla shape:
        // the pin gates the MIND alone).
        if (!noAi) {
            tickMind(night);
        }
        tickBody();
        // The enderman's environmental arms (EndermanEntity.mobAiTick lines
        // 144-166): the water damage cadence and the daylight teleport.
        if (type == MobType.ENDERMAN) {
            tickEndermanEnvironment(night);
        }
        // The witch's defensive drink (WitchEntity.mobTick lines 96-131).
        if (type == MobType.WITCH) {
            tickWitchDrink(night);
        }
        // The undead family's daylight burn (ZombieEntity.mobTick lines
        // 160-181 carrying the zombie's baby gate, SkeletonEntity.mobTick
        // lines 115-136 without it — the pigman and the wither variant
        // ride their parents' walks, the flames visual under their fire
        // immunity). The walk runs AFTER the fire clock above, the
        // reference's own order (Entity's fire decrement walks before
        // mobTick's burn arm — a fresh re-arm keeps its whole 8 seconds).
        if (type == MobType.ZOMBIE || type == MobType.ZOMBIE_PIGMAN
                || type == MobType.SKELETON) {
            tickDaylightBurn();
        }
        return fixedPoint() != before || beforeYaw != yaw || beforeHeadYaw != headYaw;
    }

    /**
     * The enderman's environment walk: standing in fluid costs 1 health on
     * the hurt window's own ~10-tick cadence (the reference's
     * {@code takeDamage(DROWN, 1.0F)} riding the invulnerability frames),
     * and full daylight with sky exposure teleports the body away on the
     * reference's own rate — {@code nextFloat() * 30 < (brightness -
     * 0.4) * 2}.
     */
    private void tickEndermanEnvironment(boolean night) {
        if (world.inFluid(position.x(), position.y() + 0.2, position.z())) {
            if (--waterDamageTimer <= 0) {
                waterDamageTimer = HURT_WINDOW_TICKS;
                // DROWN bypasses armor — the enderman's water rage never
                // consults pieces.
                hurt(1.0f, true);
            }
        } else {
            waterDamageTimer = 1; // re-arms: the first wet tick hurts fast
        }
        // The anger decay (mobAiTick lines 152-154): a body angry without
        // a player's hand behind it calms on the 1-in-100 roll.
        tickEndermanCalm();
        if (!night && !dead) {
            float brightness = Math.min(1.0f, world.skyLight(
                    (int) Math.floor(position.x()),
                    (int) Math.min(255, Math.floor(position.y()) + 1),
                    (int) Math.floor(position.z())) / 15.0f);
            if (brightness > 0.5f
                    && random.nextFloat() * 30.0f < (brightness - 0.4f) * 2.0f) {
                // The daylight blink clears the whole provocation (lines
                // 159-162): the target, the anger, and the player's hand.
                setEndermanAngry(false);
                endermanAttackedByPlayer = false;
                teleportRandomly();
            }
        }
    }

    /** The enderman's water clock (re-armed per wet tick). */
    private int waterDamageTimer = 1;

    /** The enderman's far-target window (the ChasePlayerGoal's counter). */
    private int farFromTargetTicks;

    // ---- the enderman's provocation + carry (EndermanEntity, Slice 19d) ---

    /** The reference's HOLDABLE_BLOCKS (the static block lines 328-343). */
    private static final java.util.Set<String> ENDERMAN_HOLDABLE = java.util.Set.of(
            "grass", "dirt", "sand", "gravel",
            "yellow_flower", "red_flower",
            "brown_mushroom", "red_mushroom",
            "tnt", "cactus", "clay", "pumpkin", "melon_block", "mycelium");

    /** The carried block (null = air hands). */
    private net.zaminmc.torch.block.BlockType endermanCarried;
    /** The screaming flag (the synced slot 18's byte). */
    private boolean endermanAngry;
    /** Whether the anger came from a player's own hand (the decay gate). */
    private boolean endermanAttackedByPlayer;
    /** The stare's creepy countdown (LookAtPlayerGoal's 5-tick window). */
    private int endermanCreepyTicks;
    /** A pending stare scream (the manager drains it for the sound). */
    private boolean endermanStareScream;
    /** A pending pickup intent: the cell the enderman lifted a block from. */
    private int endermanPickupX;
    private int endermanPickupY;
    private int endermanPickupZ;
    private boolean endermanPickupPending;

    /** The farmland-trample intent (the fall path's world write — the
     *  manager's listener consumes it and writes the DIRT, the enderman
     *  carry's own pending shape). */
    private boolean tramplePending;
    private int trampleX;
    private int trampleY;
    private int trampleZ;
    /** A pending step-spark intent (the steps band's own consult — the
     *  -0.2 feet cell is an ORE form; the manager's listener consumes it
     *  and the engine performs the interact: the spark band and the
     *  unlit form's swap, the trample's own pending shape). */
    private boolean stepSparkPending;
    private int stepSparkX;
    private int stepSparkY;
    private int stepSparkZ;
    /** A pending place intent: the cell the carried block goes to. */
    private int endermanPlaceX;
    private int endermanPlaceY;
    private int endermanPlaceZ;
    private boolean endermanPlacePending;
    /** The wire's state-dirty flag (the carried block or the scream flipped). */
    private boolean endermanStateDirty;

    /** The carried block, or null for empty hands. */
    public net.zaminmc.torch.block.BlockType endermanCarried() {
        return endermanCarried;
    }

    /** The screaming flag (isAngry, the synced slot 18). */
    public boolean endermanAngry() {
        return endermanAngry;
    }

    /** Sets the screaming flag (the wire's slot-18 flip rides the drain). */
    public void setEndermanAngry(boolean angry) {
        if (endermanAngry != angry) {
            endermanAngry = angry;
            endermanStateDirty = true;
        }
    }

    /** The player-hand anger marker (the decay gate, takeDamage line 295). */
    public void setEndermanAttackedByPlayer(boolean attacked) {
        endermanAttackedByPlayer = attacked;
    }

    /** The manager's drain: the wire's state flip. */
    public boolean consumeEndermanStateDirty() {
        if (endermanStateDirty) {
            endermanStateDirty = false;
            return true;
        }
        return false;
    }

    /** The manager's drain: the pickup intent (the cell + the block). */
    public boolean consumeEndermanPickup() {
        if (endermanPickupPending) {
            endermanPickupPending = false;
            return true;
        }
        return false;
    }

    /** The manager's drain: the farmland-trample intent (the fall path's
     *  DIRT write — FarmlandBlock.onFallenOn's setBlockState). */
    public boolean consumeTrample() {
        if (tramplePending) {
            tramplePending = false;
            return true;
        }
        return false;
    }

    /** The manager's drain: the step-spark intent (the steps band's own
     *  consult — the engine's onSteppedOn listener performs the ore's
     *  whole interact at the staged cell). */
    public boolean consumeStepSpark() {
        if (stepSparkPending) {
            stepSparkPending = false;
            return true;
        }
        return false;
    }

    /** The step-spark intent's cell x. */
    public int stepSparkX() {
        return stepSparkX;
    }

    /** The step-spark intent's cell y. */
    public int stepSparkY() {
        return stepSparkY;
    }

    /** The step-spark intent's cell z. */
    public int stepSparkZ() {
        return stepSparkZ;
    }

    /** The trample intent's cell x. */
    public int trampleX() {
        return trampleX;
    }

    /** The trample intent's cell y. */
    public int trampleY() {
        return trampleY;
    }

    /** The trample intent's cell z. */
    public int trampleZ() {
        return trampleZ;
    }

    /** The pickup intent's cell x. */
    public int endermanPickupX() {
        return endermanPickupX;
    }

    /** The pickup intent's cell y. */
    public int endermanPickupY() {
        return endermanPickupY;
    }

    /** The pickup intent's cell z. */
    public int endermanPickupZ() {
        return endermanPickupZ;
    }

    /** The manager's drain: the place intent. */
    public boolean consumeEndermanPlace() {
        if (endermanPlacePending) {
            endermanPlacePending = false;
            return true;
        }
        return false;
    }

    /** The place intent's cell x. */
    public int endermanPlaceX() {
        return endermanPlaceX;
    }

    /** The place intent's cell y. */
    public int endermanPlaceY() {
        return endermanPlaceY;
    }

    /** The place intent's cell z. */
    public int endermanPlaceZ() {
        return endermanPlaceZ;
    }

    /** The manager's drain: the stare scream (the sound plays at the body). */
    public boolean consumeEndermanStareScream() {
        if (endermanStareScream) {
            endermanStareScream = false;
            return true;
        }
        return false;
    }

    /**
     * The manager-side pickup completion (the listener's write landed):
     * the carried block becomes the picked type and the wire's state
     * flips.
     */
    public void completeEndermanPickup(net.zaminmc.torch.block.BlockType picked) {
        endermanCarried = picked;
        endermanStateDirty = true;
    }

    /** The manager-side place completion (the listener's write landed). */
    public void completeEndermanPlace() {
        endermanCarried = null;
        endermanStateDirty = true;
    }

    /**
     * The enderman's provocation walk (LookAtPlayerGoal 1:1 adapted to the
     * engine's mind): the nearest player's stare — the look vector
     * crossing the body's mid-height band, the carved pumpkin blinding
     * the check, the line-of-sight gate — counts the 5-tick creepy window
     * down, then the scream, the anger, and the hunt. While the hunt runs
     * against a staring player inside 4 blocks the body blinks randomly
     * (LookAtPlayerGoal.tick lines 418-424).
     */
    private void tickEndermanProvocation(boolean hunting) {
        if (!endermanAngry) {
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), ENDERMAN_STARE_RANGE);
            if (view != null && isLookedAtBy(view)) {
                if (endermanCreepyTicks == 0) {
                    endermanCreepyTicks = 5; // start()'s own value
                }
                if (--endermanCreepyTicks <= 0) {
                    endermanCreepyTicks = 0;
                    endermanStareScream = true;
                    setEndermanAngry(true);
                    endermanAttackedByPlayer = true; // shouldContinue's own write
                }
            } else {
                endermanCreepyTicks = 0; // stop(): the window re-arms fresh
            }
            return;
        }
        // The hunted stare: a target that keeps looking from inside 4
        // blocks teleports the body away (the reference's own escape).
        if (hunting) {
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), ENDERMAN_STARE_RANGE);
            if (view != null && isLookedAtBy(view)
                    && position.distanceSquared(view.position()) < 16.0) {
                teleportRandomly();
            }
        }
    }

    /** The stare scan's range (the reference's FOLLOW_RANGE 64). */
    private static final double ENDERMAN_STARE_RANGE = 64.0;

    /**
     * The stare check (isLookedAtBy lines 106-118): the player's look
     * vector against the normalized body-to-eye direction, the 0.025 band
     * over the distance, the carved pumpkin blinding, the line of sight.
     */
    private boolean isLookedAtBy(PlayerView player) {
        String helmet = player.helmetItem();
        if (helmet != null && (helmet.equals("minecraft:pumpkin")
                || helmet.equals("pumpkin"))) {
            return false; // the carved pumpkin's veil
        }
        Position eye = player.position();
        double lookX = -Math.sin(Math.toRadians(player.yaw()))
                * Math.cos(Math.toRadians(player.pitch()));
        double lookY = -Math.sin(Math.toRadians(player.pitch()));
        double lookZ = Math.cos(Math.toRadians(player.yaw()))
                * Math.cos(Math.toRadians(player.pitch()));
        // The band's target: the body's middle (minY + height / 2).
        double dx = position.x() - eye.x();
        double dy = position.y() + type.height / 2.0 - (eye.y() + 1.62);
        double dz = position.z() - eye.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-4) {
            return true; // inside the body: every look crosses it
        }
        double magnitude = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double dot = (lookX * dx + lookY * dy + lookZ * dz) / magnitude;
        if (dot <= 1.0 - 0.025 / distance) {
            return false;
        }
        // canSee: the eye-to-mid segment crosses nothing solid.
        return world.clearLine(eye,
                new Position(position.x(), position.y() + type.height / 2.0, position.z()));
    }

    /**
     * The carry arms (PickUpBlockGoal + PlaceBlockGoal 1:1): the
     * 1-in-20 pickup roll over the ±2/±3/±2 box around the body (the
     * holdable set, the world write riding the manager's drain), and the
     * 1-in-2000 place roll over the ±1/±2/±1 box (air at the cell, a
     * solid full cube below).
     */
    private void tickEndermanCarry() {
        // mobGriefing (Slice 24a — PickUpBlockGoal.canStart line 446 +
        // PlaceBlockGoal.canStart line 476): the whole carry pair gates
        // on the rule — a no-grief world's endermen neither pick up nor
        // put down blocks (the carried body keeps what it holds).
        if (!world.mobGriefing()) {
            return;
        }
        if (endermanCarried == null) {
            if (random.nextInt(20) != 0) {
                return;
            }
            int x = (int) Math.floor(position.x() - 2.0 + random.nextDouble() * 4.0);
            int y = (int) Math.floor(position.y() + random.nextDouble() * 3.0);
            int z = (int) Math.floor(position.z() - 2.0 + random.nextDouble() * 4.0);
            net.zaminmc.torch.util.Identifier block = world.blockIdAt(x, y, z);
            if (block != null && ENDERMAN_HOLDABLE.contains(block.value())) {
                endermanPickupX = x;
                endermanPickupY = y;
                endermanPickupZ = z;
                endermanPickupPending = true;
            }
        } else {
            if (random.nextInt(2000) != 0) {
                return;
            }
            int x = (int) Math.floor(position.x() - 1.0 + random.nextDouble() * 2.0);
            int y = (int) Math.floor(position.y() + random.nextDouble() * 2.0);
            int z = (int) Math.floor(position.z() - 1.0 + random.nextDouble() * 2.0);
            net.zaminmc.torch.util.Identifier at = world.blockIdAt(x, y, z);
            net.zaminmc.torch.util.Identifier below = world.blockIdAt(x, y - 1, z);
            boolean airAt = at == null || at.value().equals("air");
            boolean solidBelow = below != null && !below.value().equals("air")
                    && world.isSolid(x + 0.5, y - 0.5, z + 0.5);
            if (airAt && solidBelow) {
                endermanPlaceX = x;
                endermanPlaceY = y;
                endermanPlaceZ = z;
                endermanPlacePending = true;
            }
        }
    }

    /** The anger decay (mobAiTick lines 152-154): the 1-in-100 calm-down. */
    private void tickEndermanCalm() {
        if (endermanAngry && !endermanAttackedByPlayer && random.nextInt(100) == 0) {
            setEndermanAngry(false);
        }
    }

    // ---- the witch's drink (WitchEntity.mobTick lines 96-131) -----------

    /** The drinking potion's metadata (0 = not drinking; the drink is not splash). */
    private int witchDrink;
    /** The drink timer (the useDuration: 32 ticks). */
    private int witchDrinkTicks;

    /** Whether the witch is mid-drink (the aggressive flag). */
    public boolean witchDrinking() {
        return witchDrink > 0;
    }

    /**
     * The witch's defensive drink roll (the reference's own ladder): the
     * 15% water breathing when submerged, the 15% fire resistance when
     * burning, the 5% instant health when hurt, the 25% speed when the
     * target is far — each gated on the witch not already carrying the
     * effect (the reference's own {@code hasStatusEffect} clauses). The
     * engine's walk runs inside the body tick; the MANAGER applies the
     * effect when the timer completes (the potion system is engine-side).
     */
    private void tickWitchDrink(boolean night) {
        if (witchDrink > 0) {
            // The reference's own post-decrement (mobTick line 90): the
            // timer starts at 32 and completes when the check reads 0 —
            // the aggressive window spans 33 ticks, the effects landing
            // on the thirty-third.
            if (witchDrinkTicks-- <= 0) {
                completedWitchDrink = witchDrink;
                witchDrink = 0;
            }
        } else {
            int choice = 0;
            if (random.nextFloat() < 0.15F && world.inFluid(position.x(), position.y() + 0.2, position.z())
                    && !witchHasEffect(net.zaminmc.torch.server.potion.StatusEffects.WATER_BREATHING)) {
                choice = WITCH_WATER_BREATHING;
            } else if (random.nextFloat() < 0.15F && burning()
                    && !witchHasEffect(net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE)) {
                choice = WITCH_FIRE_RESISTANCE;
            } else if (random.nextFloat() < 0.05F && health < type.maxHealth) {
                choice = WITCH_INSTANT_HEALTH;
            } else if (random.nextFloat() < 0.25F && visibleTarget != null
                    && !witchHasEffect(net.zaminmc.torch.server.potion.StatusEffects.SPEED)
                    && position.distanceSquared(visibleTarget) > 121.0) {
                choice = WITCH_SPEED;
            }
            if (choice > 0) {
                witchDrink = choice;
                witchDrinkTicks = WITCH_DRINK_DURATION_TICKS;
            }
        }
        // The idle spell swirl (mobTick line 135): the 0.075% roll every
        // tick, drinking or not — the wire rides the Entity Event byte 15.
        if (random.nextFloat() < 0.00075F) {
            pendingWitchAura = true;
        }
    }

    /** Set when the witch's aura roll fired; consumed by the manager. */
    private boolean pendingWitchAura;

    /** @return whether the spell-swirl roll fired this tick (the event 15). */
    public boolean consumeWitchAura() {
        boolean value = pendingWitchAura;
        pendingWitchAura = false;
        return value;
    }

    /**
     * The witch's effect query (the reference's {@code hasStatusEffect}
     * clauses in the drink ladder): wired by the manager at spawn so the
     * roll sees the potion system's live store. Null (or a null effect
     * store) means the witch carries nothing.
     */
    private java.util.function.IntPredicate witchEffectQuery;

    /** Wires the drink ladder's effect query (the manager at spawn). */
    public void setWitchEffectQuery(java.util.function.IntPredicate query) {
        this.witchEffectQuery = query;
    }

    /** @return whether the witch carries the effect (no store = absent). */
    private boolean witchHasEffect(int effectId) {
        java.util.function.IntPredicate query = witchEffectQuery;
        return query != null && query.test(effectId);
    }

    /**
     * The generic effect-amplifier query (Slice 92 — the body's own walks
     * that consult the live potion store): the breath clock's
     * WATER_BREATHING shield and the fall walk's JUMP_BOOST term. Wired
     * by the manager at spawn for EVERY body; null (or a null effect)
     * means the body carries nothing.
     */
    private java.util.function.IntUnaryOperator effectAmplifierQuery;

    /** Wires the effect-amplifier query (the manager at spawn). */
    public void setEffectAmplifierQuery(java.util.function.IntUnaryOperator query) {
        this.effectAmplifierQuery = query;
    }

    /**
     * @return the effect's {@code amplifier + 1} (the reference's own
     * term shape — a level-I effect answers 1), or 0 when absent.
     */
    private int effectAmplifierPlusOne(int effectId) {
        java.util.function.IntUnaryOperator query = effectAmplifierQuery;
        return query != null ? query.applyAsInt(effectId) : 0;
    }

    // ---- the slime (SlimeEntity 1:1 — Slice 12k) -----------------------

    /**
     * The slime's size (the synced-data slot 16): 1, 2, or 4 (the
     * initialize's {@code 1 << nextInt(3)} family). The body's dimensions
     * ({@code 0.51000005 * size}), the max health ({@code size * size}),
     * the movement speed ({@code 0.2 + 0.1 * size}), the XP drop and the
     * contact damage all ride it (setSize lines 51-59).
     */
    private int slimeSize = 1;

    // ------------------------------------------------ the wolf (Slice 15d)

    /** TameableEntity's index-16 bits: sitting 0x1, angry (the wolf's own) 0x2, tamed 0x4. */
    private static final int WOLF_FLAG_SITTING = 0x1;
    private static final int WOLF_FLAG_ANGRY = 0x2;
    private static final int WOLF_FLAG_TAMED = 0x4;
    /** The wolf's own ticks-lived counter ({@code this.ticks} — the 2400-tick despawn gate). */
    private int wolfTicksExisted;
    /** The wolf's state byte on the wire's index 16 (sitting | angry &lt;&lt; 1 | tamed &lt;&lt; 2). */
    private int wolfFlags;
    /** The collar color's dye metadata (the wire's index 20 — RED at spawn, the reference's own default). */
    private byte wolfCollarColor = 1; // DyeColor.RED.getId() = 1
    /** The begging head-tilt (the wire's index 19 — the bone-holding player's gaze). */
    private boolean wolfBegging;
    /**
     * The sheep's fleece color as a DyeColor id (the wire's index-16 low
     * nibble — SheepEntity's {@code getColor().getId()}). WHITE (0) at the
     * reference's own register default; the domain differs from the collar
     * (the dye item's damage) and the dye stack (both metadata): a fleece
     * id of {@code c} is dye damage {@code 15 - c} and wool damage
     * {@code c} itself (BlockColored's damage = the DyeColor id).
     */
    private int sheepFleeceColor;
    // The wolf's water-shake cycle (Slice 30d — WolfEntity.tick's closing
    // block lines 193-222 + mobTick's start gate lines 171-176): a body
    // that went wet (water or rain) and came to rest on the ground shakes
    // the water off over the 0.05-per-tick progress walk, playing the
    // cycle-start sound, broadcasting event 8, and throwing splash
    // particles past the 0.4 bend.
    private boolean wolfWet;
    private boolean wolfShaking;
    private float wolfShakeProgress;
    private float wolfLastShakeProgress;
    private boolean pendingWolfShakeEvent;
    private boolean pendingWolfShakeSound;
    private int pendingWolfShakeSplashes;

    // ---------------------------------------------- the mind's awareness (16a)

    /**
     * TemptGoal's live pair: whether the goal owns the body this tick (the
     * ocelot's tame gate reads it — {@code interactMob} only takes the fish
     * from a player the cat is currently tempted by) and the stop-penalty
     * delay ({@code 100} ticks, the reference's own re-arm).
     */
    private boolean temptActive;
    private int temptDelay;
    /**
     * TemptGoal's tracked sample: the player's last recorded position and
     * rotation. While the player stands within 6 blocks, any move beyond
     * 0.01 blocks or turn beyond 5 degrees scares the cat off the fish;
     * beyond 6 blocks the position keeps updating (the reference's own
     * asymmetric bookkeeping — the rotation sample updates every tick).
     */
    private double temptSampleX;
    private double temptSampleY;
    private double temptSampleZ;
    private float temptSampleYaw;
    private float temptSamplePitch;

    /**
     * The wolf's begging state (WolfBegGoal): the live window counts down
     * from {@code 40 + nextInt(40)} ticks while the attractive player stays
     * within the 8-block reach; the wire's index-19 flag rides it.
     */
    private int begTimer;

    /**
     * The ocelot's shared-flag postures (OcelotEntity.mobAiTick): the creep
     * (a tempt walk at the 0.6 goal speed sets sneaking) and the flee sprint
     * (the 1.33 close speed sets sprinting) — both ride the Entity flags
     * byte at index 0 (0x02 / 0x10).
     */
    private boolean postureSneaking;
    private boolean postureSprinting;

    /**
     * The goal-driven walk speed for this tick, as a multiple of the kind's
     * own base (the reference's goal {@code speed} parameter over the
     * movement attribute): the tempt's 0.6 on the ocelot's 0.3 base writes
     * 2.0; the flee's 0.8/1.33 write 2.67/4.43. NaN (the default) keeps
     * the mode-derived band (wander/panic/chase).
     */
    private double goalWalkSpeed = Double.NaN;

    // ------------------------------------- the mob-vs-mob targets (16b)

    /**
     * The single mob-vs-mob attack target (the reference's
     * {@code setAttackTarget} state — the targetSelector's whole family
     * writes it): the wolf's sheep/rabbit/skeleton hunts, the zombie's
     * villager hunt, and the revenge/owner arms all land here. The chase
     * mind runs against its position while it lives; the melee consume
     * damages it instead of the nearest player.
     */
    private MobEntity mobAttackTarget;
    /**
     * The skeleton's pending wolf scare (Slice 16b — computed before the
     * goal walk so the stroll yields through goalHasTarget): the nearest
     * live wolf within the 6-block flee range, or null.
     */
    private MobEntity pendingScareWolf;

    /**
     * The tamed body's attack damage (the reference's {@code setTamed}
     * recompute: 4.0 tamed over the 2.0 wild kind default — the per-instance
     * override the melee walk reads).
     */
    private float wolfAttackDamage;

    /** @return the wolf's raw index-16 state byte (the wire writer's view). */
    public int wolfFlagsRaw() {
        return wolfFlags;
    }

    /** @return whether the wolf is tamed (TameableEntity's bit). */
    public boolean wolfTamed() {
        return (wolfFlags & WOLF_FLAG_TAMED) != 0;
    }

    /** @return whether the wolf sits (the owner's empty-hand toggle). */
    public boolean wolfSitting() {
        return (wolfFlags & WOLF_FLAG_SITTING) != 0;
    }

    /** @return whether the wolf is angry (the untamed hostile posture). */
    public boolean wolfAngry() {
        return (wolfFlags & WOLF_FLAG_ANGRY) != 0;
    }

    /** Sets or clears the angry posture (WolfEntity.setAngry's own bit). */
    public void setWolfAngry(boolean angry) {
        if (angry) {
            wolfFlags |= WOLF_FLAG_ANGRY;
        } else {
            wolfFlags &= ~WOLF_FLAG_ANGRY;
        }
    }

    /** Toggles the sitting posture (the owner's empty-hand verb, SitGoal). */
    public void toggleWolfSitting() {
        wolfFlags ^= WOLF_FLAG_SITTING;
    }

    /** @return the collar color's dye metadata (index 20). */
    public int wolfCollarColor() {
        return wolfCollarColor & 0xFF;
    }

    /** Sets the collar color (the dye arm's write, index 20). */
    public void setWolfCollarColor(int dyeMetadata) {
        wolfCollarColor = (byte) (dyeMetadata & 0xF);
    }

    // ---- the per-type readNbt arms (Slice 89 p3 — the reference's
    // per-type readCustomNbt setters over the engine's own state) ---------

    /** The sheep's sheared mark (readNbt's Sheared row). */
    public void setSheared(boolean sheared) {
        if (type == MobType.SHEEP) {
            this.sheared = sheared;
        }
    }

    /** The saddle mark (readNbt's Saddle/Tame-family rows): the pig's own byte or the horse flag bit. */
    public void setSaddled(boolean saddled) {
        if (type == MobType.PIG) {
            this.pigSaddled = saddled;
        } else if (type == MobType.HORSE) {
            if (saddled) {
                horseFlags |= HORSE_FLAG_SADDLED;
            } else {
                horseFlags &= ~HORSE_FLAG_SADDLED;
            }
        }
    }

    /**
     * The tamed mark (readNbt's Tame row / the owner walk's own load gate):
     * the tameable family's shared wolf-flags bit (the wolf AND the ocelot)
     * or the horse flag bit.
     */
    public void setTamedFlag(boolean tamed) {
        if (type == MobType.WOLF || type == MobType.OCELOT) {
            if (tamed) {
                wolfFlags |= WOLF_FLAG_TAMED;
            } else {
                wolfFlags &= ~WOLF_FLAG_TAMED;
            }
        } else if (type == MobType.HORSE) {
            if (tamed) {
                horseFlags |= HORSE_FLAG_TAMED;
            } else {
                horseFlags &= ~HORSE_FLAG_TAMED;
            }
        }
    }

    /**
     * The tameable family's sitting posture (readNbt's Sitting row — the
     * wolf's and the ocelot's own bit, TameableEntity's shared index-16
     * state). The ocelot's arm rides the same bit (OcelotEntity's wire
     * block reads it at its own slot 16); the horse keeps its own flags.
     */
    public void setWolfSitting(boolean sitting) {
        if (type == MobType.WOLF || type == MobType.OCELOT) {
            if (sitting) {
                wolfFlags |= WOLF_FLAG_SITTING;
            } else {
                wolfFlags &= ~WOLF_FLAG_SITTING;
            }
        }
    }

    /** The horse's taming temper (readNbt's Temper row). */
    public void setHorseTemper(int temper) {
        if (type == MobType.HORSE) {
            this.temper = temper;
        }
    }

    /**
     * @return the fleece color's DyeColor id (the index-16 low nibble, the
     *         wool stack's own damage — SheepEntity.getColor().getId()).
     */
    public int sheepFleeceColor() {
        return sheepFleeceColor & 0xF;
    }

    /**
     * Sets the fleece color (the dye arm's write, the index-16 nibble).
     * Takes the DyeColor id domain (0 = white, 15 = black).
     */
    public void setSheepFleeceColor(int dyeColorId) {
        sheepFleeceColor = dyeColorId & 0xF;
    }

    // ---------------------------------------------- the wolf's water-shake cycle

    /** @return whether the body carries water (the shake never started yet). */
    public boolean wolfWet() {
        return wolfWet;
    }

    /** @return whether the shake cycle is running (the client's event-8 pose). */
    public boolean wolfShaking() {
        return wolfShaking;
    }

    /**
     * The shake state machine (WolfEntity.tick's closing block lines 193-222
     * + mobTick's start gate lines 171-176, 1:1, the reference's own
     * within-tick order — the gate rides the super walk, the processing
     * the tick's tail): a wet body resting on the ground with the
     * navigation done starts the shake with the event-8 broadcast; while
     * water-or-rain keeps touching, the flags reset and hold; the running
     * cycle walks {@code +0.05} per tick from the start sound at progress
     * zero, clears both flags at the 2.0 end, and arms the splash count
     * past the 0.4 bend — {@code sin((progress - 0.4) * PI) * 7} units.
     */
    public void tickWolfShake() {
        if (type != MobType.WOLF || dead) {
            return;
        }
        // mobTick's start gate (lines 171-176): wet, grounded, not yet
        // shaking, the navigation at rest — doEntityEvent(8).
        if (wolfWet && !wolfShaking && onGround && navigation.isDone()) {
            wolfShaking = true;
            wolfShakeProgress = 0.0f;
            wolfLastShakeProgress = 0.0f;
            pendingWolfShakeEvent = true;
        }
        boolean inWaterOrRain = world.inFluid(position.x(), position.y() + 0.2, position.z())
                || world.isRainingAt(position.x(), position.y() + 0.2, position.z());
        if (inWaterOrRain) {
            // tick lines 193-197: the wet re-arm — whatever shake was
            // running dies, the water stays.
            wolfWet = true;
            wolfShaking = false;
            wolfShakeProgress = 0.0f;
            wolfLastShakeProgress = 0.0f;
        } else if (wolfShaking) {
            // tick lines 198-222: the cycle walk.
            if (wolfShakeProgress == 0.0f) {
                pendingWolfShakeSound = true; // "mob.wolf.shake" at the start
            }
            wolfLastShakeProgress = wolfShakeProgress;
            wolfShakeProgress += 0.05f;
            if (wolfLastShakeProgress >= 2.0f) {
                wolfWet = false;
                wolfShaking = false;
                wolfShakeProgress = 0.0f;
                wolfLastShakeProgress = 0.0f;
            } else if (wolfShakeProgress > 0.4f) {
                pendingWolfShakeSplashes = (int) Math.floor(
                        Math.sin((wolfShakeProgress - 0.4f) * Math.PI) * 7.0f);
            }
        }
    }

    /** @return and clears the event-8 broadcast (the shake's start). */
    public boolean consumePendingWolfShakeEvent() {
        boolean value = pendingWolfShakeEvent;
        pendingWolfShakeEvent = false;
        return value;
    }

    /** @return and clears the cycle-start sound. */
    public boolean consumePendingWolfShakeSound() {
        boolean value = pendingWolfShakeSound;
        pendingWolfShakeSound = false;
        return value;
    }

    /** @return and clears this tick's splash count (zero when none due). */
    public int consumePendingWolfShakeSplashes() {
        int value = pendingWolfShakeSplashes;
        pendingWolfShakeSplashes = 0;
        return value;
    }

    /** @return the begging head-tilt (index 19). */
    public boolean wolfBegging() {
        return wolfBegging;
    }

    /** Sets the begging head-tilt (WolfBegGoal's write, index 19). */
    public void setWolfBegging(boolean begging) {
        wolfBegging = begging;
    }

    /**
     * The tame lands (interactMob lines 343-351): the flag, the owner, the
     * health flip to the tamed maximum ({@code setTamed}'s own attribute
     * recompute — 20 over the wild 8), the attack-damage recompute (4.0),
     * and the sitting start.
     */
    public void tameWolf(String owner, String ownerUuid) {
        wolfFlags |= WOLF_FLAG_TAMED;
        if (owner != null && !owner.isEmpty()) {
            ownerName = owner;
        }
        if (ownerUuid != null && !ownerUuid.isEmpty()) {
            this.ownerUuid = ownerUuid;
        }
        wolfAttackDamage = 4.0f;
        this.health = 20.0f; // setHealth(20) under setTamed's raised ceiling
        wolfFlags |= WOLF_FLAG_SITTING; // sitGoal.setEnabledWithOwner(true)
    }

    /** The wolf's own tick clock ({@code this.ticks} — the tameable family's
     * 2400-tick despawn gate; the ocelot shares it). */
    void tickWolfClock() {
        wolfTicksExisted++;
    }

    /**
     * The cat's coat variant (OcelotEntity's own synced slot 18: 0 the
     * wild ocelot skin, 1-3 the tamed cat coats — the tame's own
     * {@code 1 + nextInt(3)} roll).
     */
    private int catVariant;

    /** @return the cat's coat variant byte (0 wild, 1-3 the tamed coats). */
    public int catVariant() {
        return catVariant;
    }

    /**
     * The coat's own write (the CatType row's read arm — OcelotEntity.
     * readCustomNbt line 107 has NO contains gate: an absent row reads
     * its int 0 and the coat resets to wild, the reference's own shape).
     */
    public void setCatVariant(int variant) {
        this.catVariant = variant;
    }

    /**
     * The ocelot's tame (interactMob lines 163-172): the flag, the owner, the
     * coat roll ({@code 1 + nextInt(3)}), and the sitting start — NO health
     * flip (the cat keeps its 10 ceiling; the wolf's 20 is its own).
     */
    public void tameOcelot(String owner, String ownerUuid, int variant) {
        wolfFlags |= WOLF_FLAG_TAMED;
        if (owner != null && !owner.isEmpty()) {
            ownerName = owner;
        }
        if (ownerUuid != null && !ownerUuid.isEmpty()) {
            this.ownerUuid = ownerUuid;
        }
        catVariant = variant;
        wolfFlags |= WOLF_FLAG_SITTING; // sitGoal.setEnabledWithOwner(true)
    }

    /** @return the slime's size byte (1, 2, or 4; 1 for every other kind). */
    public int slimeSize() {
        return type.slimeFamily() ? slimeSize : 1;
    }

    /**
     * @return the body's attack damage (the kind's ATTACK_DAMAGE
     * attribute — the enderman's 7.0 — or the slime's own size,
     * getDamageAmount line 215).
     */
    public float attackDamage() {
        if (hasAttackDamageOverride()) {
            // The Attributes row's own base (Slice 78b — the reference's
            // readNbt lands the row's Base on the attribute: every
            // consult — the melee arms, the wire rows — reads the
            // override over the kind's registered base).
            return (float) attackDamageOverride;
        }
        if (type == MobType.RABBIT) {
            // tryDamage lines 255-262: the killer's 8.0 wound with the
            // mob.attack sound; the plain rabbit's 3.0 never arms (no
            // attack goal holds a target for it).
            return rabbitKiller() ? 8.0f : 3.0f;
        }
        if (type == MobType.WOLF && wolfTamed()) {
            return wolfAttackDamage; // setTamed's recompute: 4.0
        }
        if (type == MobType.SKELETON && skeletonWither) {
            // The nether initialize (lines 217-224): the wither variant's
            // own ATTACK_DAMAGE 4.0 over the archer's flat.
            return SKELETON_WITHER_DAMAGE;
        }
        if (type.slimeFamily()) {
            // getDamageAmount line 215: the size; MagmaCubeEntity's own
            // override adds 2 on top.
            return slimeSize + (type == MobType.MAGMA_CUBE ? 2 : 0);
        }
        if (type == MobType.GUARDIAN && guardianElder) {
            // setElder line 141: the elder's own ATTACK_DAMAGE 8.0 (the
            // laser's melee half reads the attribute live).
            return GUARDIAN_ELDER_DAMAGE;
        }
        if (type == MobType.IRON_GOLEM) {
            // tryDamage line 202: the heavy 7 + nextInt(15) roll — the
            // golem's own damage draw per swing (the manager's hurtByMob
            // site reads it through golemDamageRoll instead; this arm
            // answers the generic walks).
            return golemDamageRoll();
        }
        return type.attackDamage;
    }

    /**
     * Sets the slime's size and recomputes the body the reference's own
     * way (setSize lines 51-59): the dimensions, the max health
     * ({@code size * size}), and the full-heal — the split's children and
     * the spawn-time initialize both land through here.
     */
    public void setSlimeSize(int size) {
        if (!type.slimeFamily() || size < 1) {
            return;
        }
        this.slimeSize = size;
        this.health = size * size; // setHealth(getMaxHealth())
    }

    /** The spawn-time initialize (SlimeEntity.initialize lines 286-295):
     * {@code 1 << nextInt(3)} — the difficulty-dependent bump arm rides
     * the difficulty slice (the engine carries no local-difficulty
     * model; the fresh-world multiplier makes the bump rare anyway). */
    public void initializeSlimeSize() {
        setSlimeSize(1 << random.nextInt(3));
    }

    /** The slime's dynamic attack damage (getDamageAmount: the size; the
     * magma cube's own override adds 2). */
    public float slimeAttackDamage() {
        return slimeSize + (type == MobType.MAGMA_CUBE ? 2 : 0);
    }

    /**
     * The slime family's kind-dependent hurt/death sound (getHurtSound/
     * getDeathSound: {@code mob.slime.}/{@code mob.magmacube.} big over
     * size 1, small below).
     */
    public String slimeSoundName() {
        String prefix = type == MobType.MAGMA_CUBE ? "mob.magmacube." : "mob.slime.";
        return prefix + (slimeSize > 1 ? "big" : "small");
    }

    /** @return the body's hurt sound (the slime family's is size-dependent). */
    public String hurtSound() {
        if (type == MobType.GUARDIAN) {
            return guardianSoundKind() + "hit";
        }
        return type.slimeFamily() ? slimeSoundName() : type.hurtSound;
    }

    /** @return the body's death sound (the slime family's is size-dependent). */
    public String deathSound() {
        if (type == MobType.GUARDIAN) {
            return guardianSoundKind() + "death";
        }
        return type.slimeFamily() ? slimeSoundName() : type.deathSound;
    }

    /**
     * The guardian's sound family prefix (getHurtSound/getDeathSound lines
     * 210-226): the land pair when the body stands dry ({@code
     * mob.guardian.land.}), else the elder's own ({@code
     * mob.guardian.elder.}) or the plain water family.
     */
    private String guardianSoundKind() {
        if (!world.inFluid(position.x(), position.y() + 0.2, position.z())) {
            return "mob.guardian.land.";
        }
        return guardianElder ? "mob.guardian.elder." : "mob.guardian.";
    }

    /** The hop cadence counter (MovementControl's jumpCooldownTicks). */
    private int slimeHopCooldown = 1;
    /** The idle direction's re-roll counter (RandomDirectionGoal). */
    private int slimeDirectionCooldown = 1;
    /** The chase state driving the aggressive cadence (the AttackGoal). */
    private boolean slimeAggressive;
    /** The contact-damage cadence (the player's own hurt-resistance window). */
    private int slimeContactCooldown;
    /** The landing edge tracker (the wasOnGround flag). */
    private boolean wasSlimeGrounded = true;

    // ------------------------------------- the squid and the bat (16e)

    /** SquidEntity's SwimAroundGoal cadence: the 1-in-50 direction roll. */
    private static final int SQUID_DIRECTION_CHANCE = 50;
    /** The squid's tentacle-cycle speed band (1/(rand+1) * 0.2). */
    private float squidTentacleSpeed = 0.1f;
    /** The tentacle stroke's phase (the 2π walk). */
    private float squidTentacleMovement;
    /** The stroke's drive: 1.0 in the first half, decaying after. */
    private float squidSpeed;
    /** The swim direction target (the SwimAroundGoal's setTarget). */
    private float squidTargetX;
    private float squidTargetY;
    private float squidTargetZ;

    /**
     * The squid's mind (SwimAroundGoal): the 1-in-50 direction re-roll —
     * a random bearing's {@code cos * 0.2 / -0.1+rand*0.2 / sin * 0.2}
     * triple, or the stop target past the 100-tick despawn window.
     */
    private void tickSquidMind() {
        if (random.nextInt(SQUID_DIRECTION_CHANCE) == 0 || !hasSquidTarget()) {
            float bearing = random.nextFloat() * (float) Math.PI * 2.0f;
            squidTargetX = (float) Math.cos(bearing) * 0.2f;
            squidTargetY = -0.1f + random.nextFloat() * 0.2f;
            squidTargetZ = (float) Math.sin(bearing) * 0.2f;
        }
    }

    private boolean hasSquidTarget() {
        return squidTargetX != 0.0f || squidTargetY != 0.0f || squidTargetZ != 0.0f;
    }

    /**
     * The squid's body (SquidEntity.mobTick): the tentacle stroke phases
     * the drive — the first half of the 2π cycle swims at speed 1.0, the
     * second decays — and the velocity IS the direction target times the
     * drive (a direct set). Out of water the body sinks (velocityY -= 0.08
     * with the 0.98 drag, no horizontal).
     */
    private void tickSquidBody(boolean inFluid) {
        squidTentacleMovement += squidTentacleSpeed;
        if (squidTentacleMovement > Math.PI * 2) {
            squidTentacleMovement -= (float) (Math.PI * 2);
            if (random.nextInt(10) == 0) {
                squidTentacleSpeed = 1.0f / (random.nextFloat() + 1.0f) * 0.2f;
            }
        }
        if (inFluid) {
            if (squidTentacleMovement < Math.PI) {
                float phase = squidTentacleMovement / (float) Math.PI;
                if (phase > 0.75f) {
                    squidSpeed = 1.0f; // the power stroke
                }
            } else {
                squidSpeed *= 0.9f; // the recovery
            }
            velocityX = squidTargetX * squidSpeed;
            velocityY = squidTargetY * squidSpeed;
            velocityZ = squidTargetZ * squidSpeed;
        } else {
            velocityX = 0.0;
            velocityY = (velocityY - 0.08) * 0.98; // the stranded sink
            velocityZ = 0.0;
        }
    }

    /** The bat's roosting flag (BatEntity's synced slot 16, bit 0). */
    private boolean batRoosting = true;
    /** The bat's flight anchor (the re-rolled hangPos). */
    private double batHangX;
    private double batHangY;
    private double batHangZ;
    private boolean batHasHang;

    /** @return whether the bat hangs from a ceiling (the index-16 bit). */
    public boolean batRoosting() {
        return batRoosting;
    }

    /** Sets the roosting posture (the spawn starts hanging). */
    public void setBatRoosting(boolean roosting) {
        batRoosting = roosting;
    }

    /**
     * The spider family's wall flag (SpiderEntity's synced slot 16, bit 0 —
     * the tick law's own post-move horizontal clip). The climb arms read it
     * pre-move; the wire publishes it for the client's hanging pose.
     */
    private boolean spiderClimbingWall;

    /** @return whether the spider body touches a wall (the index-16 bit). */
    public boolean spiderClimbingWall() {
        return spiderClimbingWall;
    }

    /**
     * The bat's mind (BatEntity.mobAiTick): the roost holds while a solid
     * ceiling stays above and no player stands within 4 (the head glances
     * on the 1-in-200); the flight wanders toward the re-rolled hangPos
     * (±7/±6 cube, every 30 ticks or within 2 blocks), the velocity
     * lerping toward the signum band; the 1-in-100 re-roost on a solid
     * block above.
     */
    private void tickBatMind() {
        if (batRoosting) {
            boolean ceilingSolid = world.isSolidAt(position.x(), position.y() + type.height, position.z());
            if (!ceilingSolid) {
                batRoosting = false;
                pendingBatTakeoff = true; // doEvent(null, 1015, blockPos, 0)
            } else if (random.nextInt(200) == 0) {
                headYaw = random.nextInt(360);
            }
            Position player = world.nearestPlayer(position.x(), position.y(), position.z(), 4.0);
            if (player != null) {
                batRoosting = false;
                pendingBatTakeoff = true; // the player-wake arm's own event
            }
            return;
        }
        if (!batHasHang || random.nextInt(30) == 0
                || (batHangX - position.x()) * (batHangX - position.x())
                        + (batHangY - position.y()) * (batHangY - position.y())
                        + (batHangZ - position.z()) * (batHangZ - position.z()) < 4.0) {
            batHangX = (int) position.x() + random.nextInt(7) - random.nextInt(7);
            batHangY = (int) position.y() + random.nextInt(6) - 2;
            batHangZ = (int) position.z() + random.nextInt(7) - random.nextInt(7);
            batHasHang = true;
        }
        double d0 = batHangX + 0.5 - position.x();
        double d1 = batHangY + 0.1 - position.y();
        double d2 = batHangZ + 0.5 - position.z();
        velocityX += (Math.signum(d0) * 0.5 - velocityX) * 0.1;
        velocityY += (Math.signum(d1) * 0.7 - velocityY) * 0.1;
        velocityZ += (Math.signum(d2) * 0.5 - velocityZ) * 0.1;
        yaw += Math.toDegrees(Math.atan2(velocityZ, velocityX)) - 90.0 - yaw;
        if (random.nextInt(100) == 0
                && world.isSolidAt(position.x(), position.y() + type.height, position.z())) {
            batRoosting = true; // the re-roost
        }
    }

    /**
     * The bat's body (BatEntity.tick): the roost freezes the velocity and
     * snaps the body to the ceiling (floor(y) + 1 - height); the flight
     * decays the vertical (0.6) with no gravity.
     */
    private void tickBatBody() {
        if (batRoosting) {
            velocityX = 0;
            velocityY = 0;
            velocityZ = 0;
            position = new Position(position.x(),
                    Math.floor(position.y()) + 1.0 - type.height, position.z());
            return;
        }
        velocityY *= 0.6;
        position = new Position(position.x() + velocityX, position.y() + velocityY,
                position.z() + velocityZ);
        onGround = false;
    }

    // ------------------------------------- the guardian (17a)

    /** The laser's charge time (getAttackChargeTime: the elder's own 60). */
    private static final int GUARDIAN_ATTACK_CHARGE_TICKS = 80;
    /** The elder's own charge (getAttackChargeTime's elder arm). */
    private static final int ELDER_ATTACK_CHARGE_TICKS = 60;
    /** The charge cycle's start offset (GuardianAttackEntityGoal.start: -10). */
    private static final int GUARDIAN_CHARGE_START = -10;
    /** The laser hit's magic base (tick line 517: the flat 1.0F). */
    private static final float GUARDIAN_MAGIC_BASE = 1.0f;
    /** The hard-difficulty magic bump (tick lines 509-512: +2.0F — the engine's walk adds it). */
    private static final float GUARDIAN_MAGIC_HARD_BONUS = 2.0f;
    /** The elder's own magic bump (tick lines 514-516: +2.0F). */
    private static final float GUARDIAN_MAGIC_ELDER_BONUS = 2.0f;
    /** The still body's spike reflect (takeDamage lines 422-429: 2.0F on the melee family). */
    private static final float GUARDIAN_THORNS_DAMAGE = 2.0f;
    /** The elder's hull (setElder line 139: 1.9975F on both axes). */
    private static final double GUARDIAN_ELDER_SIZE = 1.9975;
    /** The elder's health ceiling (setElder line 142: MAX_HEALTH 80). */
    private static final float GUARDIAN_ELDER_HEALTH = 80.0f;
    /** The elder's melee (setElder line 141: ATTACK_DAMAGE 8.0). */
    private static final float GUARDIAN_ELDER_DAMAGE = 8.0f;
    /** The elder's speed (setElder line 140: MOVEMENT_SPEED 0.3). */
    private static final double GUARDIAN_ELDER_WALK = 0.3;
    /** The elder's mining-fatigue aura (mobAiTick: every 1200 ticks, the body's own cadence). */
    private static final int GUARDIAN_ELDER_AURA_INTERVAL = 1200;
    /** The aura's reach (squared: 2500 = 50 blocks, squaredDistanceTo < 2500). */
    private static final double GUARDIAN_ELDER_AURA_RADIUS_SQUARED = 2500.0;
    /** The aura's own duration (6000 ticks of Fatigue III). */
    private static final int GUARDIAN_ELDER_AURA_DURATION = 6000;
    /** The aura's amplifier (the 1.8.8 elder's own III). */
    private static final int GUARDIAN_ELDER_AURA_AMPLIFIER = 2;
    /** The aura's re-application floor (the 1200-duration arm: only weaker rows re-land). */
    private static final int GUARDIAN_ELDER_AURA_REFRESH_BELOW = 1200;
    /** The target validator's beyond-the-flop gate (squaredDistanceTo > 9). */
    private static final double GUARDIAN_TARGET_MIN_SQUARED = 9.0;
    /** The wander cadence (WanderAroundGoal's interval 80 — the re-roll). */
    private static final int GUARDIAN_WANDER_INTERVAL = 80;

    /**
     * The guardian's village-pin radius (GuardianEntity.mobAiTick line
     * 377: the self-cache at 16 — the body's monument anchor).
     */
    private static final int GUARDIAN_VILLAGE_PIN_RADIUS = 16;
    /** The swim's velocity scale (moveRelative: updateVelocity(…, 0.1F)). */
    private static final double GUARDIAN_SWIM_SCALE = 0.1;
    /** The swim's per-tick decay (velocity *= 0.9). */
    private static final double GUARDIAN_SWIM_DECAY = 0.9;
    /** The idle sink (velocityY -= 0.005 when neither steering nor hunting). */
    private static final double GUARDIAN_IDLE_SINK = 0.005;
    /** The land flop's rise (mobTick line 324: velocityY += 0.5). */
    private static final double GUARDIAN_FLOP_RISE = 0.5;
    /** The land flop's horizontal band ((rand*2-1) * 0.4 per axis). */
    private static final double GUARDIAN_FLOP_BAND = 0.4;

    /** The elder flag (setElder — the monument's own three carry it). */
    private boolean guardianElder;
    /** The swim steering flag (GuardianMovementControl's moving — the wire's slot-16 bit 0x02, the thorns gate). */
    private boolean guardianMoving;
    /** The charge cycle ({@code Integer.MIN_VALUE} = idle; -10 start, 0 = the beam on, the charge time = the hit). */
    private int guardianChargeTicks = Integer.MIN_VALUE;
    /** The beam's live target id (the wire's slot 17; 0 = none — the manager resolves the session id). */
    private int guardianBeamTargetId;
    /** Set when the beam starts (the charge crosses 0); the manager resolves the id + the Event 21. */
    private boolean pendingGuardianBeamStart;
    /** Set when the laser's charge completes; the manager lands the magic + melee pair on the target. */
    private boolean pendingGuardianLaserHit;
    /** Set when the elder's aura cycle fires; the manager walks the players. */
    private boolean pendingGuardianAura;
    /**
     * The live squid the hunt rides (VT-7c — GuardianTargetValidator line
     * 591's {@code instanceof SquidEntity} branch), or null when the hunt
     * rides a player (or nothing). Re-derived every mind tick; the manager
     * reads it after the tick for the beam id and the hit's walk.
     */
    private MobEntity guardianSquidHunt;
    /** The wander's steering target (the re-rolled swim direction). */
    private double guardianSteerX;
    private double guardianSteerY;
    private double guardianSteerZ;
    private boolean guardianHasSteer;

    /** @return whether the body is the elder variant (the wire's slot-16 bit 0x04). */
    public boolean guardianElder() {
        return guardianElder;
    }

    /** @return the swim steering flag (the wire's slot-16 bit 0x02, the thorns gate). */
    public boolean guardianMoving() {
        return guardianMoving;
    }

    /** @return the beam's live target id (the wire's slot 17; 0 = none). */
    public int guardianBeamTargetId() {
        return guardianBeamTargetId;
    }

    /**
     * @return whether the charge window carries a live beam (the charge
     * crossed 0 — the wire's slot 17 carries the target's id until the
     * hit or the hunt's break clears it).
     */
    public boolean guardianBeamActive() {
        return guardianChargeTicks >= 0 && guardianChargeTicks < guardianChargeTime();
    }

    /** The manager's beam write (the session id the charge window carries). */
    public void setGuardianBeamTargetId(int targetId) {
        guardianBeamTargetId = targetId;
    }

    /** @return the laser's charge time (the elder's own 60). */
    public int guardianChargeTime() {
        return guardianElder ? ELDER_ATTACK_CHARGE_TICKS : GUARDIAN_ATTACK_CHARGE_TICKS;
    }

    /**
     * The elder flip (setElder lines 132-146): the 1.9975 hull, the
     * speed 0.3, the damage 8.0, the 80-health ceiling (healed here —
     * the reference's own {@code spawnElderGuardian} heals to the new
     * max), and the persistent never-despawn rule.
     */
    public void setGuardianElder(boolean elder) {
        guardianElder = elder;
        if (elder) {
            persistent = true; // setPersistent (the elder never despawns)
            health = GUARDIAN_ELDER_HEALTH;
        }
    }

    /**
     * @return the guardian's thorns verdict — a still body spikes its
     * melee attacker ({@code !isMoving() && !source.getMagic() &&
     * source.getSource() instanceof LivingEntity && !source.isExplosive()}).
     */
    public boolean guardianSpikesMeleeAttacker() {
        return type == MobType.GUARDIAN && !guardianMoving;
    }

    /** @return the spike reflect's damage (the flat 2.0F). */
    public float guardianThornsDamage() {
        return GUARDIAN_THORNS_DAMAGE;
    }

    /** @return and clears the beam-start verdict (the Event 21 site). */
    public boolean consumeGuardianBeamStart() {
        boolean value = pendingGuardianBeamStart;
        pendingGuardianBeamStart = false;
        return value;
    }

    /** @return and clears the completed-charge verdict (the laser hit). */
    public boolean consumeGuardianLaserHit() {
        boolean value = pendingGuardianLaserHit;
        pendingGuardianLaserHit = false;
        return value;
    }

    /** @return and clears the elder's aura verdict (the 1200-tick cadence). */
    public boolean consumeGuardianAura() {
        boolean value = pendingGuardianAura;
        pendingGuardianAura = false;
        return value;
    }

    /**
     * The guardian's own tick clock ({@code this.ticks} — the elder's aura
     * cadence: {@code (ticks + getNetworkId()) % 1200 == 0}, the per-body
     * offset the reference staggers the monuments' three by). The walk
     * itself — which players stand within the 50-block reach — is the
     * manager's (the session registry lives there).
     */
    void tickGuardianClock() {
        guardianTicks++;
        if (guardianElder && (guardianTicks + entityId) % GUARDIAN_ELDER_AURA_INTERVAL == 0) {
            pendingGuardianAura = true;
        }
    }

    /** The guardian's own ticks-lived counter (the aura's cadence input). */
    private int guardianTicks;

    // ------------------------------------- the endermite (17c)

    /** The pearl's passenger despawns after its own lifetime (EndermiteEntity
     * mobTick lines 120-127: the non-persistent clock, the 2400-tick wall). */
    private static final int ENDERMITE_LIFETIME_TICKS = 2400;

    /** The endermite's own ticks-lived counter (the lifetime clock). */
    private int endermiteTicks;

    /** Set when the lifetime wall crossed; the manager removes the body. */
    private boolean pendingEndermiteExpire;

    /**
     * The endermite's lifetime clock (mobTick lines 120-127): the
     * non-persistent body counts up to the 2400-tick wall — the removal
     * itself is the manager's (the tick walk's own removal path, no death
     * animation, no loot).
     */
    void tickEndermiteClock() {
        if (!persistent) {
            endermiteTicks++;
            if (endermiteTicks >= ENDERMITE_LIFETIME_TICKS) {
                pendingEndermiteExpire = true;
            }
        }
    }

    /** @return and clears the lifetime wall's verdict. */
    public boolean consumeEndermiteExpire() {
        boolean value = pendingEndermiteExpire;
        pendingEndermiteExpire = false;
        return value;
    }

    // ------------------------------------- the golem pair (17d)

    /** The iron golem's hunt range (GoToEntityTargetGoal's 32.0F). */
    private static final double GOLEM_HUNT_RANGE = 32.0;
    /** The iron golem's melee reach (the 1.4-wide body's swing band). */
    private static final double GOLEM_MELEE_RANGE = 2.6;
    /** The iron golem's heavy hit (tryDamage line 202: 7 + nextInt(15)). */
    private static final int GOLEM_DAMAGE_BASE = 7;
    private static final int GOLEM_DAMAGE_SPREAD = 15;
    /** The launch the landed golem hit gives (velocityY += 0.4F). */
    private static final double GOLEM_LAUNCH_Y = 0.4;
    /** The snow golem's volley cadence (ProjectileAttackGoal's interval 20). */
    private static final int SNOW_GOLEM_SHOT_INTERVAL = 20;
    /** The snow golem's volley range (ProjectileAttackGoal's 10.0F). */
    private static final double SNOW_GOLEM_RANGE = 10.0;
    /** The melt's cost per contact tick (mobTick: takeDamage 1.0F). */
    private static final float GOLEM_MELT_DAMAGE = 1.0f;

    /** The golem's live mob target (the golem family's own hunt state). */
    private MobEntity golemTarget;
    /** The snow golem's volley clock. */
    private int snowGolemShotCooldown;
    /** Set when the iron golem's swing lands this tick; consumed by the manager. */
    private boolean pendingGolemSwing;
    /** Set when the snow golem's volley fires; the manager resolves the target. */
    private boolean pendingSnowGolemShot;

    /**
     * The iron golem's mind (the goal ladder 1-8 + the target selector's
     * TrackIronGolemTargetGoal): the nearest hostile mob within 32 owns
     * the walk and the melee — the body never hunts players (the
     * canAttack gate) — else the stroll cycle holds. The hostile scan
     * carries the golem's own target law (IronGolemEntity lines 54-57
     * over 247-283): the creeper excluded — the override's own predicate,
     * lines 256-258, and the canAttack line 137's {@code type !=
     * CreeperEntity.class} — and the same-team refusal every target goal
     * reads.
     */
    private void tickIronGolemMind(boolean night) {
        golemTarget = world.nearestHostileMob(position.x(), position.y(), position.z(),
                GOLEM_HUNT_RANGE, MobType.CREEPER, teamName());
        if (golemTarget != null && golemTarget.dead()) {
            golemTarget = null;
        }
        // VIII10 — the village-defense target (TrackIronGolemTargetGoal
        // over the manager's consult): a FORCED player target owns the
        // walk wherever it stands (the mob arm rode setMobAttackTarget
        // — the mob-vs-mob chase reads it through the standing walk
        // below); a gone body clears the arm.
        Position forced = forcedHuntPosition();
        Position chaseTarget;
        if (forced != null) {
            chaseTarget = forced;
        } else if (golemTarget != null) {
            chaseTarget = golemTarget.position();
        } else {
            return; // the stroll cycle (the goal layer's default)
        }
        double dx = chaseTarget.x() - position.x();
        double dy = chaseTarget.y() - position.y();
        double dz = chaseTarget.z() - position.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        yaw = angleTo(dx, dz);
        headYaw = yaw;
        if (horizontal > GOLEM_MELEE_RANGE) {
            mode = Mode.CHASE;
            tickPathFollowing(chaseTarget);
        } else {
            mode = Mode.IDLE;
            clearPath();
            // MeleeAttackGoal's cadence: the swing lands when the cooldown
                       // allows — the heavy hit + the launch ride the manager.
            if (attackCooldown == 0) {
                attackCooldown = GOLEM_MELEE_COOLDOWN;
                pendingMeleeAttack = true;
                pendingGolemSwing = true;
            }
        }
    }

    /** The golem's own melee cadence (MeleeAttackGoal's 20-tick cycle). */
    private static final int GOLEM_MELEE_COOLDOWN = 20;

    /**
     * The snow golem's mind (ProjectileAttackGoal 1.25/20/10 + the
     * target selector's Monster filter): the nearest hostile within 10
     * takes the 20-tick snowball volley — the walk holds the distance
     * (the goal's 1.25 chase closes only past the range). The scan keeps
     * the creeper IN (SnowGolemEntity line 33's plain Monster.FILTER —
     * the iron golem's exclusion is its own) and reads the shared
     * same-team refusal every target goal carries.
     */
    private void tickSnowGolemMind() {
        if (snowGolemShotCooldown > 0) {
            snowGolemShotCooldown--;
        }
        golemTarget = world.nearestHostileMob(position.x(), position.y(), position.z(),
                SNOW_GOLEM_RANGE, null, teamName());
        if (golemTarget == null || golemTarget.dead()) {
            golemTarget = null;
            return; // the plain stroll
        }
        double dx = golemTarget.position().x() - position.x();
        double dz = golemTarget.position().z() - position.z();
        yaw = angleTo(dx, dz);
        headYaw = yaw;
        if (snowGolemShotCooldown == 0) {
            snowGolemShotCooldown = SNOW_GOLEM_SHOT_INTERVAL;
            pendingSnowGolemShot = true;
        }
    }

    /**
     * The snow golem's environmental walk (mobTick lines 43-55): the water
     * contact melts a layer every tick ({@code DROWN} 1.0), the hot biome
     * the same ({@code ON_FIRE} — the engine's desert stand-in for the
     * reference's temperature > 1.0).
     */
    private void tickSnowGolemEnvironment() {
        if (world.inFluid(position.x(), position.y() + 0.2, position.z())) {
            // DROWN 1.0 — bypasses armor.
            hurt(GOLEM_MELT_DAMAGE, true); // isInWaterOrRain → DROWN 1.0F
        } else if (world.biomeHotAt(position.x(), position.y(), position.z())) {
            // ON_FIRE 1.0 — bypasses armor.
            hurt(GOLEM_MELT_DAMAGE, true); // temperature > 1.0 → ON_FIRE 1.0F
        }
    }

    /** @return the golem's live mob target (the hunt's single write). */
    public MobEntity golemTarget() {
        return golemTarget;
    }

    // --------------------------------------------------------------
    // The blaze (Slice 18a — BlazeEntity 1:1)
    // --------------------------------------------------------------

    /** AttackGoal's step: 0 idle, 1 charging, 2-4 the volleys, the reset. */
    private int blazeStep;
    /** AttackGoal's ticks: the countdown between the steps. */
    private int blazeTicks;
    /** mobAiTick's eyeOffset (re-rolled every 100 ticks around 0.5). */
    private float blazeEyeOffset = 0.5F;
    /** mobAiTick's fireActivation: the eye-offset re-roll clock. */
    private int blazeFireActivation;
    /** The fire-active flag (BlazeEntity's synced slot 16, bit 0). */
    private boolean blazeFireActive;
    /** Set when a fireball volley fires this tick; the manager consumes. */
    private boolean pendingBlazeFireball;
    /** Set when the fire-active flag flipped; the manager consumes. */
    private boolean blazeFireDirty;

    /**
     * The blaze's own mind (BlazeEntity's goal set — no SwimGoal, no
     * panic, no shared stroll: the AttackGoal at 4 over the wander family,
     * so the custom mind preempts the standard goal layer): the reference's
     * exact cycle (AttackGoal.tick lines 202-255) — inside 4 blocks the
     * 20-tick melee with the movement follow, inside 256 the step cycle
     * (the 60-tick charge with the fire-up, the three fireball volleys 6
     * ticks apart, the 100-tick cooldown), beyond the plain follow walk.
     * The mobAiTick arms run first (lines 109-128): the water drown, the
     * eye-offset re-roll, and the hover rise toward a higher target.
     */
    private void tickBlazeMind(Position target, boolean canHunt) {
        // mobAiTick lines 110-112: the water-or-rain contact drowns the
        // body (DROWN 1.0 per tick — the same shape the snow golem's melt
        // rides). The consult is the reference's own isInWaterOrRain —
        // the WATER flag alone plus the rain pair, NEVER the lava (the
        // blaze stands in its own element; the pre-Slice-93 engine
        // consulted the shared inFluid and a blaze in lava drowned
        // itself — Slice 93/D2's split of the shared fluid model).
        if ((world.inFluid(position.x(), position.y() + 0.2, position.z())
                && !world.inLava(position.x(), position.y() + 0.2, position.z()))
                || world.isRainingAt(position.x(), position.y(), position.z())
                || world.isRainingAt(position.x(), position.y() + type.height, position.z())) {
            // DROWN bypasses armor — the rain never consults pieces.
            hurt(1.0f, true);
        }

        // mobAiTick lines 115-119: the eye-offset re-roll every 100 ticks.
        if (--blazeFireActivation <= 0) {
            blazeFireActivation = 100;
            blazeEyeOffset = 0.5F + (float) random.nextGaussian() * 3.0F;
        }

        // mobAiTick lines 121-125: the hover rise — a target whose eye
        // band sits above the blaze's own (plus the drifting offset)
        // pulls the body up toward 0.3 (the player's eye 1.62 against the
        // blaze's own height-scaled band).
        if (target != null) {
            double targetEye = target.y() + 1.62;
            double ownEye = position.y() + eyeHeight() + blazeEyeOffset;
            if (targetEye > ownEye) {
                velocityY += (0.3 - velocityY) * 0.3;
            }
        }

        if (target == null || !canHunt) {
            // AttackGoal.stop() lines 197-199: the fire goes out; the
            // wander family owns the idle band.
            setBlazeFireActive(false);
            goalTickIdleWander(target);
            return;
        }

        double dx = target.x() - position.x();
        double dy = target.y() - position.y();
        double dz = target.z() - position.z();
        double squared = dx * dx + dy * dy + dz * dz;
        // The eyes stay on the target (setLookatValues 10.0F, 10.0F — the
        // look control runs outside the distance arms too).
        yaw = angleTo(dx, dz);
        headYaw = yaw;

        // AttackGoal.tick: the timer burns every tick first.
        blazeTicks--;

        if (squared < 4.0) {
            // lines 206-212: the close melee (d0 < 4 — the two-block
            // band) — the 20-tick cadence with the movement follow at the
            // reference's own speed.
            if (blazeTicks <= 0) {
                blazeTicks = 20;
                pendingMeleeAttack = true;
            }
            mode = Mode.CHASE;
            tickPathFollowing(target);
        } else if (squared < 256.0) {
            // lines 213-248: the step cycle (d0 < 256 — the sixteen-block
            // band). The blaze stands and volleys — only the look moves
            // (the movement control never fires inside this band).
            mode = Mode.IDLE;
            clearPath();
            if (blazeTicks <= 0) {
                blazeStep++;
                if (blazeStep == 1) {
                    blazeTicks = 60;
                    setBlazeFireActive(true);
                } else if (blazeStep <= 4) {
                    blazeTicks = 6;
                } else {
                    blazeTicks = 100;
                    blazeStep = 0;
                    setBlazeFireActive(false);
                }
                // lines 230-244: the volleys fire from step 2 on (the
                // reference's own `step > 1` gate — the reset wrote 0
                // first, so the 4th never fires).
                if (blazeStep > 1) {
                    pendingBlazeFireball = true;
                }
            }
        } else {
            // lines 249-251: beyond the volley band the navigation stops
            // and the movement control follows the target.
            mode = Mode.CHASE;
            tickPathFollowing(target);
        }
    }

    /** The synced fire-active flag's write (setFireActive lines 159-168). */
    private void setBlazeFireActive(boolean active) {
        if (blazeFireActive != active) {
            blazeFireActive = active;
            blazeFireDirty = true;
        }
    }

    /** @return the fire-active flag (the synced slot-16 bit). */
    public boolean blazeFireActive() {
        return blazeFireActive;
    }

    // --------------------------------------------------------------
    // The wither boss (Slice 59a — WitherEntity 1:1, the body)
    // --------------------------------------------------------------

    /**
     * The 220-tick blue window (onSummoned's setInvulnerabilityTimer):
     * heals 10 every 10 ticks toward the 300, then the 7.0F birth
     * explosion + the global 1013 spawn sound. The synced slot-20 Int.
     */
    private int witherInvulnTicks;
    /**
     * The boss's own monotonic tick counter (the reference's this.ticks —
     * modeTicks resets with the goal cycles, the heads' cooldown bands
     * must ride an unbroken clock).
     */
    private int witherTicks;
    /**
     * The main head's volley clock (ProjectileAttackGoal's
     * ticksUntilNextAttack — the wither's own registration: speed 1.0,
     * cooldown 40, range 20).
     */
    private int witherMainShotCooldown = -1;
    /** The three heads' quarry entity ids (the synced slots 17-19). */
    private final int[] witherHeadTargets = new int[3];
    /** The side heads' skull cooldowns (mobAiTick's skullCooldowns). */
    private final int[] witherSkullCooldowns = new int[2];
    /** The side heads' charged-skull volley counters (the 16-volley blue). */
    private final int[] witherChargedCooldowns = new int[2];
    /** The block-breaking sweep's cooldown (takeDamage's 20-tick arm). */
    private int witherBlockBreakCooldown;
    /** Set when the invuln window ends; the manager consumes (the explosion + 1013). */
    private boolean pendingWitherBirth;
    /** The skulls loosed this tick; the manager consumes (head index, target cell, charged). */
    private int pendingWitherSkullHead = -1;
    private double pendingWitherSkullX;
    private double pendingWitherSkullY;
    private double pendingWitherSkullZ;
    private boolean pendingWitherSkullCharged;
    /** Set when the head-target Ints or the invuln Int changed; the wire consumes. */
    private boolean witherMetadataDirty;
    /** Set when the sweep broke at least one block; the manager consumes (the 1012 sound). */
    private boolean pendingWitherBlockBreakSound;
    /** Set when a skull launch fired; the manager consumes (the 1014 sound). */
    private boolean pendingWitherShootSound;
    private boolean pendingBatTakeoff;

    /**
     * The boss's own mind (WitherEntity.mobTick + mobAiTick 1:1): the
     * flight shaping (the 0.6 dampened rise toward a target above, the
     * 0.5-capped horizontal drift toward a target beyond 3 blocks), the
     * 220-tick blue window (the 10-per-10 heal, the birth explosion at
     * the end), the three heads' independent quarries (the main head
     * reads the attack target, the side heads scan the 20x8x20 box for
     * the living non-undead), the skull volleys (every 10-20 ticks the
     * acquisition, every 40-60 the shot, the 16-volley blue barrage on
     * normal+), the 20-tick block-breaking sweep under fire, and the
     * flat 1.0 regen every 20 ticks.
     */
    private void tickWitherMind(Position target, boolean canHunt) {
        witherTicks++; // the reference's own this.ticks (a fresh monotonic
        // counter — modeTicks resets with the goals, the heads' cooldowns
        // must not)

        // mobTick lines 107-135: the flight shaping runs on the main head's
        // quarry — the dampened rise toward a target above (or within 5
        // below it while ABOVE half health — the reference's `!isAtHalfHealth`
        // gate: the weakened body hugs lower), the horizontal drift toward
        // a target beyond 3 blocks, the yaw following the motion.
        if (target != null) {
            boolean half = health() <= type().maxHealth / 2.0f;
            if (position.y() < target.y() || !half && position.y() < target.y() + 5.0) {
                if (velocityY < 0.0) {
                    velocityY = 0.0;
                }
                velocityY += (0.5 - velocityY) * 0.6;
            }
            double dx = target.x() - position.x();
            double dz = target.z() - position.z();
            double squared = dx * dx + dz * dz;
            if (squared > 9.0) {
                double dist = Math.sqrt(squared);
                velocityX += (dx / dist * 0.5 - velocityX) * 0.6;
                velocityZ += (dz / dist * 0.5 - velocityZ) * 0.6;
            }
        }
        velocityY *= 0.6; // mobTick line 108 — the hover dampening
        if (velocityX * velocityX + velocityZ * velocityZ > 0.05) {
            yaw = angleTo(velocityX, velocityZ);
        }

        // mobAiTick lines 214-225: the blue window — the 10-per-10 heal and
        // the birth explosion + the global 1013 at the end.
        if (witherInvulnTicks > 0) {
            if (--witherInvulnTicks <= 0) {
                pendingWitherBirth = true;
            }
            if (witherTicks % 10 == 0) {
                heal(10.0f);
            }
            witherMetadataDirty = true; // the slot-20 Int counts down
            return; // the blue window runs no heads, no sweep, no regen
        }

        // The main head reads the single target state (lines 279-283).
        setWitherHeadTarget(0, target != null && canHunt
                ? world.nearestVisibleEntityId(target) : 0);

        // The main head's own volley (ProjectileAttackGoal lines 76-93 —
        // the wither's registration: speed 1.0, cooldown 40, range 20):
        // the 40-tick clock arms on the first sight, fires the head-0
        // skull at the quarry (the 1-in-1000 blue rides the head-0 roll,
        // doRangedAttack → shootSkullAt(0, target)) when the 3D distance
        // to the quarry's feet sits inside 20 blocks and the sight holds,
        // and a failed attempt re-arms 40 (the reference's own -1 dance).
        int mainQuarry = witherHeadTargets[0];
        if (mainQuarry > 0) {
            if (witherMainShotCooldown < 0) {
                witherMainShotCooldown = 40; // the first-sight arm (line 92)
            }
            if (--witherMainShotCooldown <= 0) {
                Position quarry = world.entityPositionById(mainQuarry);
                if (quarry == null) {
                    witherMainShotCooldown = -1; // gone: re-arm on the next sight
                } else {
                    double mdx = quarry.x() - position.x();
                    double mdy = quarry.y() - position.y();
                    double mdz = quarry.z() - position.z();
                    boolean inRange = mdx * mdx + mdy * mdy + mdz * mdz <= 400.0;
                    if (inRange && !witherQuarryInvalid(mainQuarry)) {
                        looseWitherSkullAtQuarry(0, mainQuarry);
                        witherMainShotCooldown = 40;
                    } else {
                        witherMainShotCooldown = -1; // out of range or unseen
                    }
                }
            }
        } else {
            witherMainShotCooldown = -1; // stop()'s reset (line 60)
        }

        // mobAiTick lines 228-277: the side heads' own quarries.
        for (int i = 1; i < 3; i++) {
            if (witherTicks >= witherSkullCooldowns[i - 1]) {
                witherSkullCooldowns[i - 1] = witherTicks + 10 + random.nextInt(10);
                // The 16-volley blue barrage (lines 231-240) — normal and
                // hard only: a charged skull at a random cell in the
                // plus/minus 10 / plus/minus 5 / plus/minus 10 box.
                if (witherChargedCooldowns[i - 1]++ > 15
                        && world.difficultyAtLeastNormal()) {
                    double sx = position.x() + (random.nextDouble() * 20.0 - 10.0);
                    double sy = position.y() + (random.nextDouble() * 10.0 - 5.0);
                    double sz = position.z() + (random.nextDouble() * 20.0 - 10.0);
                    looseWitherSkull(i + 1, sx, sy, sz, true);
                    witherChargedCooldowns[i - 1] = 0;
                }
                int quarryId = witherHeadTargets[i];
                if (quarryId > 0) {
                    // The quarry's validity walk (lines 243-253): gone,
                    // dead, beyond 30 blocks, out of sight, or a creative
                    // player clears the head; a live quarry eats the
                    // 40-60-tick volley and resets the blue counter.
                    Integer quarry = world.entityById(quarryId);
                    if (quarry == null || witherQuarryInvalid(quarryId)
                            || world.entityInvulnerablePlayer(quarryId)) {
                        setWitherHeadTarget(i, 0);
                    } else {
                        looseWitherSkullAtQuarry(i + 1, quarryId);
                        witherSkullCooldowns[i - 1] = witherTicks + 40 + random.nextInt(20);
                        witherChargedCooldowns[i - 1] = 0;
                    }
                } else {
                    // The acquisition walk (lines 254-275): the grown
                    // 20x8x20 box's living non-undead candidates, ten
                    // random rolls — a dead, unseen, or creative pick
                    // leaves the list and the walk tries again; the first
                    // live sighting claims the head.
                    int[] candidates = world.livingNonUndeadIdsIn(
                            position.x(), position.y(), position.z(), 20.0, 8.0, entityId);
                    for (int roll = 0; roll < 10 && candidates.length > 0; roll++) {
                        int pick = candidates[random.nextInt(candidates.length)];
                        if (world.entityById(pick) == null
                                || witherQuarryInvalid(pick)
                                || world.entityInvulnerablePlayer(pick)) {
                            candidates = removeFromCandidates(candidates, pick);
                            continue;
                        }
                        setWitherHeadTarget(i, pick);
                        break;
                    }
                }
            }
        }

        // mobAiTick lines 285-312: the block-breaking sweep — the cooldown
        // armed by every wound, the 3x3x4 around the feet when it expires.
        if (witherBlockBreakCooldown > 0) {
            witherBlockBreakCooldown--;
            if (witherBlockBreakCooldown == 0) {
                pendingWitherSweep = true; // the manager walks the world blocks
            }
        }

        // mobAiTick lines 314-316: the flat 1.0 regen every 20 ticks.
        if (witherTicks % 20 == 0) {
            heal(1.0f);
        }
    }

    /** Set by the mind when the sweep's cooldown expires; the manager walks it. */
    private boolean pendingWitherSweep;

    /**
     * Looses one skull at a quarry's mid-height (shootSkullAt lines
     * 379-381): the head 0 shot rolls the 1-in-1000 blue.
     */
    private void looseWitherSkullAtQuarry(int head, int quarryId) {
        Position quarry = world.entityPositionById(quarryId);
        if (quarry == null) {
            return;
        }
        looseWitherSkull(head, quarry.x(), quarry.y() + world.eyeHeightOf(quarryId) * 0.5,
                quarry.z(), head == 0 && random.nextFloat() < 0.001F);
    }

    /**
     * Looses one skull from a head at a cell (shootSkullAt lines
     * 383-400): the head's own mount point (the center head at +3.0, the
     * side heads at the plus/minus 1.3 flank at +2.2), the delta aim,
     * and the launch riding the manager's own sink.
     */
    private void looseWitherSkull(int head, double tx, double ty, double tz,
                                  boolean charged) {
        pendingWitherSkullHead = head;
        pendingWitherSkullX = tx;
        pendingWitherSkullY = ty;
        pendingWitherSkullZ = tz;
        pendingWitherSkullCharged = charged;
        pendingWitherShootSound = true; // doEvent 1014 at the body
    }

    /**
     * The quarry-validity walk (mobAiTick lines 243-253): gone, beyond
     * 900 squared (30 blocks), or out of sight — the reference's canSee
     * runs eye to eye (this body's eye to the quarry's eye).
     */
    private boolean witherQuarryInvalid(int quarryId) {
        Position quarry = world.entityPositionById(quarryId);
        if (quarry == null) {
            return true;
        }
        double dx = quarry.x() - position.x();
        double dy = quarry.y() - position.y();
        double dz = quarry.z() - position.z();
        return dx * dx + dy * dy + dz * dz > 900.0
                || !world.hasLineOfSight(position.x(), position.y() + eyeHeight(), position.z(),
                        quarry.x(), quarry.y() + world.eyeHeightOf(quarryId), quarry.z());
    }

    /** The acquisition walk's rejected pick leaves the candidate list
     * (mobAiTick line 273's list.remove). */
    private static int[] removeFromCandidates(int[] candidates, int rejected) {
        int[] next = new int[candidates.length - 1];
        int at = 0;
        for (int candidate : candidates) {
            if (candidate != rejected) {
                next[at++] = candidate;
            }
        }
        return next;
    }

    /** The head-target write (setTrackedEntityId — the synced 17-19 Ints). */
    private void setWitherHeadTarget(int head, int entityId) {
        if (witherHeadTargets[head] != entityId) {
            witherHeadTargets[head] = entityId;
            witherMetadataDirty = true;
        }
    }

    /** onSummoned (lines 328-331): the 220-tick window, the third-health
     * start (setHealth — the spawned-full body DROPS to 100, the window's
     * 22 ten-heals carry it back to the 300 cap exactly at the end). */
    public void witherSummoned() {
        witherInvulnTicks = 220;
        health = type().maxHealth / 3.0f;
        witherMetadataDirty = true;
    }

    /** takeDamage lines 436-442: every wound arms the sweep's 20-tick clock. */
    public void armWitherSweep() {
        if (witherBlockBreakCooldown <= 0) {
            witherBlockBreakCooldown = 20;
        }
        for (int i = 0; i < witherChargedCooldowns.length; i++) {
            witherChargedCooldowns[i] += 3; // the wounded barrage speed-up
        }
    }

    /** @return the blue-window state (the reference's slot-20 Int > 0). */
    public boolean witherInvulnerable() {
        return witherInvulnTicks > 0;
    }

    /** @return the invuln countdown (the synced slot-20 Int). */
    public int witherInvulnTicks() {
        return witherInvulnTicks;
    }

    /** @return the head-target ids (the synced slots 17-19). */
    public int[] witherHeadTargets() {
        return witherHeadTargets.clone();
    }

    /** @return the birth event (the explosion + the global 1013); consumed once. */
    public boolean consumeWitherBirth() {
        boolean pending = pendingWitherBirth;
        pendingWitherBirth = false;
        return pending;
    }

    /** @return the loosed skull's head index, -1 when none this tick. */
    public int consumeWitherSkullHead() {
        int head = pendingWitherSkullHead;
        pendingWitherSkullHead = -1;
        return head;
    }

    public double pendingWitherSkullX() {
        return pendingWitherSkullX;
    }

    public double pendingWitherSkullY() {
        return pendingWitherSkullY;
    }

    public double pendingWitherSkullZ() {
        return pendingWitherSkullZ;
    }

    public boolean pendingWitherSkullCharged() {
        return pendingWitherSkullCharged;
    }

    /** @return the metadata-dirty flag (the wire's 17-20 Int resend). */
    public boolean consumeWitherMetadataDirty() {
        boolean dirty = witherMetadataDirty;
        witherMetadataDirty = false;
        return dirty;
    }

    /** @return the sweep-expired flag; the manager walks the 3x3x4 and consumes. */
    public boolean consumeWitherSweep() {
        boolean pending = pendingWitherSweep;
        pendingWitherSweep = false;
        return pending;
    }

    /** @return the 1014 launch-sound flag; consumed once. */
    public boolean consumeWitherShootSound() {
        boolean pending = pendingWitherShootSound;
        pendingWitherShootSound = false;
        return pending;
    }

    /** @return the 1012 break-sound flag; consumed once. */
    public boolean consumeWitherBlockBreakSound() {
        boolean pending = pendingWitherBlockBreakSound;
        pendingWitherBlockBreakSound = false;
        return pending;
    }

    /** @return the bat's 1015 takeoff-event flag; consumed once. */
    public boolean consumeBatTakeoff() {
        boolean pending = pendingBatTakeoff;
        pendingBatTakeoff = false;
        return pending;
    }

    /** The manager's sweep arm marks the sound when a block fell. */
    public void markWitherBlockBreakSound() {
        pendingWitherBlockBreakSound = true;
    }

    // --------------------------------------------------------------
    // The ender dragon (Slice 63 — EnderDragonEntity 1:1: the body,
    // the steering, the parts, the damage gates)
    // --------------------------------------------------------------

    /**
     * One server-side hitbox part (EnderDragonPart 1:1): the name, the
     * size, and the live placement the tick walk writes. Never a wire
     * entity of its own — the CLIENT builds its parts locally and
     * re-aligns their ids to the spawn packet's dragonId (the +1..+7
     * convention the spawn's eight-id block guarantees), so an attack
     * packet naming a part id resolves HERE.
     */
    public static final class DragonPart {
        /** The reference's ctor order (the wire id convention dragonId+k). */
        public final int index;
        public final String name;
        /** The tick-shrunk size (mobTick lines 242-251 overwrite the ctor's). */
        public final double width;
        public final double height;
        double x;
        double y;
        double z;

        DragonPart(int index, String name, double width, double height) {
            this.index = index;
            this.name = name;
            this.width = width;
            this.height = height;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        /** The box containment (the part's grown hull for the consults). */
        public boolean contains(double px, double py, double pz, double grow) {
            double half = width / 2.0;
            return px >= x - half - grow && px <= x + half + grow
                    && py >= y - grow && py <= y + height + grow
                    && pz >= z - half - grow && pz <= z + half + grow;
        }
    }

    /** The steering target (ctor line 77: targetY born 100). */
    private double dragonTargetX;
    private double dragonTargetY = 100.0;
    private double dragonTargetZ;
    /** The 64-slot (yaw, y) ring the body curve reads (lines 36-42). */
    private final double[][] dragonSegmentBuffer = new double[64][3];
    private int dragonLatestSegment = -1;
    private DragonPart[] dragonParts;
    private float dragonLastWingPosition;
    private float dragonWingPosition;
    private boolean dragonNeedsNewTarget = true;
    /** The reference's restrictMovement — an immune block met (the half-flap). */
    private boolean dragonRestrictMovement;
    /** The turn rate (the reference's rotationSpeed field — the dragon's own). */
    private float dragonRotationSpeed;
    /** The player quarry's engine id (the 50% arm), -1 for the point arm. */
    private int dragonTargetPlayerId = -1;
    /** The part-hit event (the manager routes the damage walk); consumed once. */
    private boolean dragonFlungPlayers;
    private boolean dragonDamagedPlayers;
    private boolean dragonSweptBlocks;
    /** The death walk's own clock (tickPostDeath's ticksSinceDeath). */
    private int dragonTicksSinceDeath = -1;
    /** The 1018 global event (the death walk's first tick). */
    private boolean pendingDragonDeathSound;
    /** An XP batch of {@code amount} at the body (the 1000s and the 2000). */
    private int pendingDragonXpBatch;
    /** The walk's 200th tick: the fountain + the removal. */
    private boolean pendingDragonPortal;
    /** The linked crystal's position (tickEndCrystalInteraction's own). */
    private double dragonCrystalX;
    private double dragonCrystalY;
    private double dragonCrystalZ;
    private boolean dragonHasCrystal;
    /** The disconnect wound (the linked crystal popped — the 10.0 blast hit). */
    private boolean pendingDragonCrystalWound;

    /**
     * The seven parts (ctor lines 64-72): the wire id convention rides the
     * ORDER — head, body, tailBase, tailMiddle, tailEnd, rightWing,
     * leftWing — each dragonId+1..+7.
     */
    private void buildDragonParts(int dragonEntityId) {
        dragonParts = new DragonPart[]{
                new DragonPart(dragonEntityId + 1, "head", 3.0, 3.0),
                new DragonPart(dragonEntityId + 2, "body", 5.0, 3.0),
                new DragonPart(dragonEntityId + 3, "tail", 2.0, 2.0),
                new DragonPart(dragonEntityId + 4, "tail", 2.0, 2.0),
                new DragonPart(dragonEntityId + 5, "tail", 2.0, 2.0),
                new DragonPart(dragonEntityId + 6, "wing", 4.0, 2.0),
                new DragonPart(dragonEntityId + 7, "wing", 4.0, 3.0)};
    }

    /** @return the parts (the attack routing + the projectile consults). */
    public DragonPart[] dragonParts() {
        return dragonParts;
    }

    /** @return the part with the wire entity id, or null (the attack arm). */
    public DragonPart dragonPartById(int partEntityId) {
        if (dragonParts == null) {
            return null;
        }
        for (DragonPart part : dragonParts) {
            if (part.index == partEntityId) {
                return part;
            }
        }
        return null;
    }

    /** @return the part whose grown hull holds the point (the arrow's walk). */
    public DragonPart dragonPartAt(double x, double y, double z) {
        if (dragonParts == null) {
            return null;
        }
        for (DragonPart part : dragonParts) {
            if (part.contains(x, y, z, 0.0)) {
                return part;
            }
        }
        return null;
    }

    /** The head part (the fling/damage and heal consults' own anchor). */
    public DragonPart dragonHead() {
        return dragonParts == null ? null : dragonParts[0];
    }

    /**
     * The part damage walk (takeDamage(EnderDragonPart, ...) lines 458-476):
     * the non-head parts quarter the amount plus one, the target resets to
     * the head-front with the jitter, the quarry drops, and only a PLAYER
     * attacker or an EXPLOSION wounds the body — everything else lands
     * nothing (the plain overload's own refusal, thorns excepted).
     *
     * @return the wound the body takes (0 when the source refuses).
     */
    public float dragonDamagePart(DragonPart part, float amount,
                                  boolean playerSource, boolean explosive) {
        if (part != dragonHead()) {
            amount = amount / 4.0f + 1.0f; // line 461
        }
        // Lines 464-469: the head-front reset with the jitter.
        float yawRadians = (float) Math.toRadians(yaw);
        dragonTargetX = position.x() + Math.sin(yawRadians) * 5.0
                + (random.nextFloat() - 0.5f) * 2.0;
        dragonTargetY = position.y() + random.nextFloat() * 3.0f + 1.0;
        dragonTargetZ = position.z() - Math.cos(yawRadians) * 5.0
                + (random.nextFloat() - 0.5f) * 2.0;
        dragonTargetPlayerId = -1; // this.target = null (line 470)
        if (playerSource || explosive) {
            return amount; // damageDragon (line 472)
        }
        return 0.0f;
    }

    /** The wing-flap read (the client's wings sound rides its own walk). */
    public float dragonWingPosition() {
        return dragonWingPosition;
    }

    /** @return the fling contact verdict; consumed once per tick. */
    public boolean consumeDragonFlungPlayers() {
        boolean pending = dragonFlungPlayers;
        dragonFlungPlayers = false;
        return pending;
    }

    /** @return the head-damage contact verdict; consumed once per tick. */
    public boolean consumeDragonDamagedPlayers() {
        boolean pending = dragonDamagedPlayers;
        dragonDamagedPlayers = false;
        return pending;
    }

    /** @return the block-sweep verdict (restrictMovement's own arm). */
    public boolean consumeDragonSweptBlocks() {
        boolean pending = dragonSweptBlocks;
        dragonSweptBlocks = false;
        return pending;
    }

    /** @return the steering quarry's engine id (-1 for the point arm). */
    public int dragonTargetPlayerId() {
        return dragonTargetPlayerId;
    }

    /**
     * The death walk's start (the hurt path's health-zero arm): the
     * standard death (loot, remove) never runs — the body rises and
     * spins for 200 ticks with the XP batches and the portal.
     */
    public void beginDragonDeath() {
        dragonTicksSinceDeath = 0;
    }

    /** @return whether the death walk is running. */
    public boolean dragonDying() {
        return dragonTicksSinceDeath >= 0;
    }

    /** @return the death walk's clock (-1 while alive). */
    public int dragonTicksSinceDeath() {
        return dragonTicksSinceDeath;
    }

    /** @return the 1018 event; consumed once. */
    public boolean consumeDragonDeathSound() {
        boolean pending = pendingDragonDeathSound;
        pendingDragonDeathSound = false;
        return pending;
    }

    /** @return the XP batch amount (0 when none); consumed once. */
    public int consumeDragonXpBatch() {
        int amount = pendingDragonXpBatch;
        pendingDragonXpBatch = 0;
        return amount;
    }

    /** @return the portal-and-remove verdict; consumed once. */
    public boolean consumeDragonPortal() {
        boolean pending = pendingDragonPortal;
        pendingDragonPortal = false;
        return pending;
    }

    /** @return the steering target (the point arm's reads). */
    public double dragonTargetX() {
        return dragonTargetX;
    }

    public double dragonTargetY() {
        return dragonTargetY;
    }

    public double dragonTargetZ() {
        return dragonTargetZ;
    }

    /**
     * getSegmentProperties (lines 92-109): the interpolated (yaw, y, extra)
     * at the segment offset — the body curve the head and the tails read.
     */
    private double[] dragonSegmentProperties(int segment, float tickDelta) {
        if (health <= 0.0f) {
            tickDelta = 0.0f;
        }
        tickDelta = 1.0f - tickDelta;
        int i = dragonLatestSegment - segment & 63;
        int j = dragonLatestSegment - segment - 1 & 63;
        double[] out = new double[3];
        double d0 = dragonSegmentBuffer[i][0];
        double d1 = wrapDegrees(dragonSegmentBuffer[j][0] - d0);
        out[0] = d0 + d1 * tickDelta;
        d0 = dragonSegmentBuffer[i][1];
        d1 = dragonSegmentBuffer[j][1] - d0;
        out[1] = d0 + d1 * tickDelta;
        out[2] = dragonSegmentBuffer[i][2]
                + (dragonSegmentBuffer[j][2] - dragonSegmentBuffer[i][2]) * tickDelta;
        return out;
    }

    /** MathHelper.wrapDegrees — the ±180 band. */
    private static double wrapDegrees(double value) {
        value = value % 360.0;
        if (value >= 180.0) {
            value -= 360.0;
        }
        if (value < -180.0) {
            value += 360.0;
        }
        return value;
    }

    /**
     * The sweep's write-back (destroyBlocks' immune-block verdict).
     */
    public void dragonSetRestrictMovement(boolean restricted) {
        this.dragonRestrictMovement = restricted;
    }

    /** @return the linked crystal's position (the heal beam's anchor). */
    public boolean dragonHasCrystal() {
        return dragonHasCrystal;
    }

    public double dragonCrystalX() {
        return dragonCrystalX;
    }

    public double dragonCrystalY() {
        return dragonCrystalY;
    }

    public double dragonCrystalZ() {
        return dragonCrystalZ;
    }

    /** @return the crystal-pop wound (the disconnect's 10.0); consumed once. */
    public boolean consumeDragonCrystalWound() {
        boolean pending = pendingDragonCrystalWound;
        pendingDragonCrystalWound = false;
        return pending;
    }

    /**
     * The crystal link (tickEndCrystalInteraction lines 324-353, 1:1):
     * the linked crystal's pop wounds the head with the 10.0 explosive
     * hit, the linked body heals 1 every 10 ticks under the cap, and the
     * 1-in-10 re-scan takes the nearest crystal in the grown-32 box.
     */
    private void tickDragonCrystalInteraction() {
        if (dragonHasCrystal) {
            if (!world.enderCrystalAliveAt(dragonCrystalX, dragonCrystalY, dragonCrystalZ)) {
                // Lines 326-331: the disconnect's wound — the head part,
                // the explosive source, the flat 10.0 (the reference's
                // own takeDamage(this.head, DamageSource.explosion, 10)).
                dragonDamagePart(dragonHead(), 10.0f, false, true);
                pendingDragonCrystalWound = true;
                dragonHasCrystal = false;
            } else if (modeTicks % 10 == 0 && health < type().maxHealth) {
                heal(1.0f); // line 332-334
            }
        }
        if (random.nextInt(10) == 0) {
            // Lines 337-352: the nearest crystal in the 32-grown box.
            double[] nearest = world.nearestEnderCrystal(position.x(), position.y(),
                    position.z(), 32.0);
            if (nearest != null) {
                dragonCrystalX = nearest[0];
                dragonCrystalY = nearest[1];
                dragonCrystalZ = nearest[2];
                dragonHasCrystal = true;
            } else {
                dragonHasCrystal = false;
            }
        }
    }

    /**
     * The dragon's own mind (mobTick lines 112-315, 1:1): the crystal
     * link, the wing flap, the steering walk (the quarry chase or the
     * point drift, the 100/22500 re-target band, the velocity shaping,
     * the rotation-speed yaw), the part placement (the segment-driven
     * head and tails, the wing flanks), the contact arms' events, and
     * the death walk's own arm.
     */
    private void tickDragonMind(Position target, boolean canHunt) {
        // The death walk (tickPostDeath lines 501-543): the rise and the
        // 20-degree spin, the XP batches, the 1018, and the portal at the
        // 200th tick — the body stays in the world the whole walk.
        if (dragonTicksSinceDeath >= 0) {
            dragonTicksSinceDeath++;
            if (dragonTicksSinceDeath == 1) {
                pendingDragonDeathSound = true; // doGlobalEvent(1018)
            }
            if (dragonTicksSinceDeath > 150 && dragonTicksSinceDeath % 5 == 0) {
                pendingDragonXpBatch = 1000; // the orb batches (lines 512-520)
            }
            if (dragonTicksSinceDeath == 200) {
                // The final batch ADDS to the tick's own 1000 (lines 529-
                // 537: the >150 arm fires at 200 too — 10x1000 + 2000 =
                // the 12000 total).
                pendingDragonXpBatch += 2000;
                pendingDragonPortal = true; // createPortal + remove
            }
            // Line 527-528: the rise and the spin.
            position = new Position(position.x(), position.y() + 0.1, position.z());
            yaw = (float) wrapDegrees(yaw + 20.0);
            return;
        }
        // Lines 121-135: the flap — 0.2 / (speed*10 + 1), scaled by
        // 2^velocityY, halved when the movement restricts.
        dragonLastWingPosition = dragonWingPosition;
        // The crystal link (line 128 — the first arm of the living walk).
        tickDragonCrystalInteraction();
        float flap = (float) (0.2 / (Math.sqrt(velocityX * velocityX
                + velocityZ * velocityZ) * 10.0 + 1.0));
        flap *= (float) Math.pow(2.0, velocityY);
        dragonWingPosition += dragonRestrictMovement ? flap * 0.5f : flap;

        yaw = (float) wrapDegrees(yaw);
        // The ring's first fill (lines 141-146).
        if (dragonLatestSegment < 0) {
            for (double[] slot : dragonSegmentBuffer) {
                slot[0] = yaw;
                slot[1] = position.y();
            }
        }
        if (++dragonLatestSegment == dragonSegmentBuffer.length) {
            dragonLatestSegment = 0;
        }
        dragonSegmentBuffer[dragonLatestSegment][0] = yaw;
        dragonSegmentBuffer[dragonLatestSegment][1] = position.y();

        // Lines 167-190: the steering target.
        double dx = dragonTargetX - position.x();
        double dy = dragonTargetY - position.y();
        double dz = dragonTargetZ - position.z();
        double squared = dx * dx + dy * dy + dz * dz;
        if (dragonTargetPlayerId > 0) {
            Position quarry = world.entityPositionById(dragonTargetPlayerId);
            if (quarry != null) {
                dragonTargetX = quarry.x();
                dragonTargetZ = quarry.z();
                double hdx = dragonTargetX - position.x();
                double hdz = dragonTargetZ - position.z();
                double hd = Math.sqrt(hdx * hdx + hdz * hdz);
                // Line 177: 0.4 + dist/80 - 1, capped at 10 — the quarry's
                // hover band above the feet.
                double hover = 0.4 + hd / 80.0 - 1.0;
                if (hover > 10.0) {
                    hover = 10.0;
                }
                dragonTargetY = quarry.y() + hover;
            }
        } else {
            // Lines 184-185: the point's own gaussian drift.
            dragonTargetX += random.nextGaussian() * 2.0;
            dragonTargetZ += random.nextGaussian() * 2.0;
        }
        if (dragonNeedsNewTarget || squared < 100.0 || squared > 22500.0) {
            // (the reference's collidedHorizontally/Vertically arms ride the
            // noClip body — a noClip entity never registers a collision, the
            // gates stay structurally false)
            chooseDragonTarget();
        }

        // Lines 192-238: the velocity shaping.
        dx = dragonTargetX - position.x();
        dy = dragonTargetY - position.y();
        dz = dragonTargetZ - position.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double clampedDy = Math.max(-0.6, Math.min(0.6, dy / horizontal));
        velocityY += clampedDy * 0.1;
        yaw = (float) wrapDegrees(yaw);
        double desired = 180.0 - Math.atan2(dx, dz) * 180.0 / Math.PI;
        double turn = wrapDegrees(desired - yaw);
        if (turn > 50.0) {
            turn = 50.0;
        }
        if (turn < -50.0) {
            turn = -50.0;
        }
        // The f5 alignment: the nose vector against the target vector.
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double tx = dx / len;
        double ty = dy / len;
        double tz = dz / len;
        double yawRad = Math.toRadians(yaw);
        double noseX = Math.sin(yawRad);
        double noseZ = -Math.cos(yawRad);
        double noseLen = Math.sqrt(noseX * noseX + velocityY * velocityY + noseZ * noseZ);
        double nx = noseX / noseLen;
        double ny = velocityY / noseLen;
        double nz = noseZ / noseLen;
        float alignment = (float) ((nx * tx + ny * ty + nz * tz) + 0.5) / 1.5f;
        if (alignment < 0.0f) {
            alignment = 0.0f;
        }
        dragonRotationSpeed *= 0.8f;
        double speedFactor = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ) + 1.0;
        if (speedFactor > 40.0) {
            speedFactor = 40.0;
        }
        dragonRotationSpeed = (float) (dragonRotationSpeed + turn * (0.7 / speedFactor / speedFactor));
        yaw = (float) (yaw + dragonRotationSpeed * 0.1);
        // updateVelocity(0, -1, 0.06 * (alignment*f7 + (1-f7))) — the
        // forward-strafe pair against the yaw.
        float f7 = (float) (2.0 / (speedFactor + 1.0));
        float accel = 0.06f * (alignment * f7 + (1.0f - f7));
        double forwardX = -Math.sin(Math.toRadians(yaw));
        double forwardZ = Math.cos(Math.toRadians(yaw));
        velocityX += forwardX * accel;
        velocityZ += forwardZ * accel;
        velocityY += -accel; // the strafe's -1 (the flight's own lift model)
        if (dragonRestrictMovement) {
            position = new Position(position.x() + velocityX * 0.8,
                    position.y() + velocityY * 0.8, position.z() + velocityZ * 0.8);
        } else {
            position = new Position(position.x() + velocityX,
                    position.y() + velocityY, position.z() + velocityZ);
        }
        // Lines 233-238: the post-move dampening.
        double vlen = Math.sqrt(velocityX * velocityX + velocityY * velocityY
                + velocityZ * velocityZ);
        if (vlen > 0.0) {
            float f9 = (float) (((velocityX / vlen) * nx + (velocityY / vlen) * ny
                    + (velocityZ / vlen) * nz) + 1.0) / 2.0f;
            f9 = 0.8f + 0.15f * f9;
            velocityX *= f9;
            velocityZ *= f9;
        }
        velocityY *= 0.91;

        // Line 241: the body yaw follows (the engine's single-yaw model —
        // the reference's bodyYaw field and the yaw ARE one here).

        // Lines 252-307: the part placement.
        double[] seg5 = dragonSegmentProperties(5, 1.0f);
        double[] seg10 = dragonSegmentProperties(10, 1.0f);
        // f12: the pitch between the two segments' y.
        float f12 = (float) (seg5[1] - seg10[1]) * 10.0f / 180.0f * (float) Math.PI;
        float pitchCos = (float) Math.cos(f12);
        float pitchSin = -(float) Math.sin(f12);
        float yawRad2 = (float) Math.toRadians(yaw);
        float sinYaw = (float) Math.sin(yawRad2);
        float cosYaw = (float) Math.cos(yawRad2);
        // The body (line 259): the center, half a block back.
        dragonParts[1].x = position.x() + sinYaw * 0.5;
        dragonParts[1].y = position.y();
        dragonParts[1].z = position.z() - cosYaw * 0.5;
        // The wings (lines 261-263): the ±4.5 flanks at +2.
        dragonParts[5].x = position.x() + cosYaw * 4.5;
        dragonParts[5].y = position.y() + 2.0;
        dragonParts[5].z = position.z() + sinYaw * 4.5;
        dragonParts[6].x = position.x() - cosYaw * 4.5;
        dragonParts[6].y = position.y() + 2.0;
        dragonParts[6].z = position.z() - sinYaw * 4.5;
        // The head (lines 270-278): the segment curve plus the 5.5 nose.
        double[] seg0 = dragonSegmentProperties(0, 1.0f);
        float headSin = (float) Math.sin(yawRad2 - dragonRotationSpeed * 0.01f);
        float headCos = (float) Math.cos(yawRad2 - dragonRotationSpeed * 0.01f);
        dragonParts[0].x = position.x() + headSin * 5.5 * pitchCos;
        dragonParts[0].y = position.y() + (seg0[1] - seg5[1]) + pitchSin * 5.5;
        dragonParts[0].z = position.z() - headCos * 5.5 * pitchCos;
        // The tails (lines 280-307): the three segment-offset segments.
        for (int j = 0; j < 3; j++) {
            DragonPart tail = dragonParts[2 + j];
            double[] seg = dragonSegmentProperties(12 + j * 2, 1.0f);
            float tailAngle = (float) (yawRad2
                    + wrapDegrees(seg[0] - seg5[0]) * Math.PI / 180.0);
            float tailSin = (float) Math.sin(tailAngle);
            float tailCos = (float) Math.cos(tailAngle);
            float back = 1.5f;
            float reach = (j + 1) * 2.0f;
            tail.x = position.x() - (sinYaw * back + tailSin * reach) * pitchCos;
            tail.y = position.y() + (seg[1] - seg5[1]) - (reach + back) * pitchSin + 1.5;
            tail.z = position.z() + (cosYaw * back + tailCos * reach) * pitchCos;
        }

        // The contact arms (lines 264-268, damagedTimer == 0 — the engine
        // carries no hurt cooldown here, the arms run every tick): the
        // fling against the wing hulls, the damage against the head hull.
        dragonFlungPlayers = true;
        dragonDamagedPlayers = true;
        // The block sweep (line 311): the head and body hulls.
        dragonSweptBlocks = true;
    }

    /**
     * chooseTarget (lines 379-408): the 50% quarry arm (a random
     * non-spectator player) or the point arm (y 70..120, x/z ±60 around
     * zero, at least 10 blocks out — the do-while's own loop).
     */
    private void chooseDragonTarget() {
        dragonNeedsNewTarget = false;
        // The engine's player walk (the world consult names the candidates —
        // the reference's spectator filter rides the consult's own PLAYING
        // gate; creative players stay fair game, the reference's own law).
        java.util.List<Integer> players = new java.util.ArrayList<>();
        for (int id : world.playerIds()) {
            Position at = world.entityPositionById(id);
            if (at != null && !world.entityInvulnerablePlayer(id)) {
                players.add(id);
            }
        }
        if (random.nextInt(2) == 0 && !players.isEmpty()) {
            dragonTargetPlayerId = players.get(random.nextInt(players.size()));
        } else {
            double sx;
            double sy;
            double sz;
            do {
                dragonTargetX = 0.0;
                dragonTargetY = 70.0 + random.nextFloat() * 50.0f;
                dragonTargetZ = 0.0;
                dragonTargetX += random.nextFloat() * 120.0f - 60.0f;
                dragonTargetZ += random.nextFloat() * 120.0f - 60.0f;
                sx = position.x() - dragonTargetX;
                sy = position.y() - dragonTargetY;
                sz = position.z() - dragonTargetZ;
            } while (sx * sx + sy * sy + sz * sz <= 100.0);
            dragonTargetPlayerId = -1;
        }
    }

    // --------------------------------------------------------------
    // The silverfish (Slice 18c — SilverfishEntity's egg-hosting life)
    // --------------------------------------------------------------

    /** WakeUpFriendsGoal's cooldown (armed by the hurt walk, 20 ticks). */
    private int silverfishWakeCooldown;
    /** Set when the wake-up cooldown expires; the manager consumes. */
    private boolean pendingSilverfishWake;
    /** Set when the infest roll fires; the manager consumes. */
    private boolean pendingSilverfishInfest;
    /** The infest target cell (InfestStoneGoal's dir offset). */
    private int silverfishInfestX;
    private int silverfishInfestY;
    private int silverfishInfestZ;

    /**
     * Arms the wake-up (WakeUpFriendsGoal.onDamaged lines 198-201): the
     * entity-sourced or magic hurt sets the 20-tick cooldown — the swarm
     * scan fires when it expires (the already-armed clock never resets).
     */
    public void armSilverfishWake() {
        if (silverfishWakeCooldown == 0) {
            silverfishWakeCooldown = 20;
        }
    }

    /**
     * The silverfish's clock (WakeUpFriendsGoal.tick lines 210-236 +
     * InfestStoneGoal.canStart lines 142-163): the wake-up countdown
     * ends in the scan verdict; the infest roll fires only with no
     * target in sight AND the navigation idle (the reference's own
     * isDone gate, line 147 — a walking silverfish never slips into
     * stone) — a 1-in-10 chance to merge into an adjacent infestable
     * block (the body disappears into the egg).
     */
    private void tickSilverfishClock() {
        if (silverfishWakeCooldown > 0) {
            silverfishWakeCooldown--;
            if (silverfishWakeCooldown <= 0) {
                pendingSilverfishWake = true;
            }
        }
        // InfestStoneGoal.canStart: the targetless, navigation-idle body
        // rolls the 1-in-10; the direction pick walks the six facings, the
        // adjacent cell read through the world's block identity. The idle
        // read folds the stroll's own window in (strollActive): the engine
        // model defers the stroll's path start to the walk's first tick
        // (the reference's start() calls moveTo at once), so the flag —
        // not the path alone — owns the body's walk state in that window.
        if (visibleTarget == null && !strollActive && navigation.isDone()
                && random.nextInt(10) == 0) {
            int facing = random.nextInt(6);
            int dx = facing == 0 ? 1 : facing == 1 ? -1 : 0;
            int dy = facing == 2 ? 1 : facing == 3 ? -1 : 0;
            int dz = facing == 4 ? 1 : facing == 5 ? -1 : 0;
            int bx = (int) Math.floor(position.x()) + dx;
            int by = (int) Math.floor(position.y() + 0.5) + dy;
            int bz = (int) Math.floor(position.z()) + dz;
            net.zaminmc.torch.util.Identifier block = world.blockIdAt(bx, by, bz);
            if (block != null
                    && net.zaminmc.torch.server.block.InfestedBlocks.canBeInfested(block)) {
                pendingSilverfishInfest = true;
                silverfishInfestX = bx;
                silverfishInfestY = by;
                silverfishInfestZ = bz;
            }
        }
    }

    /** @return and clears the wake-up scan verdict. */
    public boolean consumeSilverfishWake() {
        boolean value = pendingSilverfishWake;
        pendingSilverfishWake = false;
        return value;
    }

    /** @return and clears the infest verdict. */
    public boolean consumeSilverfishInfest() {
        boolean value = pendingSilverfishInfest;
        pendingSilverfishInfest = false;
        return value;
    }

    /** @return the infest target cell's x. */
    public int silverfishInfestX() {
        return silverfishInfestX;
    }

    /** @return the infest target cell's y. */
    public int silverfishInfestY() {
        return silverfishInfestY;
    }

    /** @return the infest target cell's z. */
    public int silverfishInfestZ() {
        return silverfishInfestZ;
    }

    // --------------------------------------------------------------
    // The wither skeleton (Slice 18d — SkeletonEntity.setType(1))
    // --------------------------------------------------------------

    /** The wither variant's flag (the nether initialize's 4-in-5 roll). */
    private boolean skeletonWither;

    /** The wither variant's melee (initialize line 221: ATTACK_DAMAGE 4.0). */
    private static final float SKELETON_WITHER_DAMAGE = 4.0f;

    /** The wither variant's hull (setType line 281: setSize(0.72F, 2.535F)). */
    private static final double SKELETON_WITHER_HEIGHT = 2.535;

    /** The wither variant's width (setType line 281's own 0.72F). */
    private static final double SKELETON_WITHER_WIDTH = 0.72;

    /**
     * The wither effect's duration on a landed melee hit (tryDamage line
     * 99: {@code new StatusEffectInstance(StatusEffect.WITHER.id, 200)} —
     * a flat 10 seconds, no amplifier, no difficulty gate).
     */
    public static final int SKELETON_WITHER_EFFECT_TICKS = 200;

    /** The mob's held item (setEquipment slot 0 — the bow, the stone sword). */
    private ItemStack heldEquipment;
    /**
     * The four armor slots (MobEntity's {@code equipment} array, line 60 —
     * the slot-1..4 band over the engine's slot-0 held model): [1] feet,
     * [2] legs, [3] chest, [4] head, the reference's own order. The spawn
     * dice (addRandomEquipment), the anvil import's Equipment rows, and the
     * /summon walk land here; the damage walk reads the points through the
     * {@link net.zaminmc.torch.server.item.Armor} model.
     */
    private final ItemStack[] armorEquipment = new ItemStack[4];

    /**
     * The {@code generic.movementSpeed} attribute override (Slice 78b —
     * the reference's Attributes NBT row over the kind's own base);
     * {@link Double#NaN} = no override (the kind's walkSpeed answers).
     */
    private double movementSpeedOverride = Double.NaN;

    /**
     * The {@code generic.attackDamage} attribute override (Slice 78b —
     * the same row family); NaN = no override.
     */
    private double attackDamageOverride = Double.NaN;

    /**
     * The movement-speed override's landing (EntityAttributes.readNbt's
     * own shape: the row's Base replaces the attribute's base — a
     * non-positive value is refused, the attribute keeps its base).
     */
    public void setMovementSpeedOverride(double speed) {
        if (speed > 0.0) {
            this.movementSpeedOverride = speed;
        }
    }

    /** @return whether the Attributes row overrode the movement speed. */
    public boolean hasMovementSpeedOverride() {
        return !Double.isNaN(movementSpeedOverride);
    }

    /**
     * The walk-speed consult (Slice 78b): every movement arm reads this
     * — the Attributes row's override over the kind's own base (the
     * reference's getEntityAttribute(MOVEMENT_SPEED).getAttributeValue()
     * consult shape).
     */
    public double walkSpeed() {
        if (hasMovementSpeedOverride()) {
            return movementSpeedOverride;
        }
        // The horse's spawn-time MOVEMENT_SPEED roll (HorseBaseEntity
        // .initialize lines 1280-1283): the horse's rolled band (line 1281
        // over getMovementSpeedBonus line 1390) or the donkey/mule flat
        // (line 1283) — the branch reads the LIVE subtype, so the rows that
        // overwrite the subtype after the spawn (the seeding's group type,
        // the /summon Type row, the breed's mule) answer coherently. The
        // roll answers on the reference's base scale (its registered horse
        // base is 0.225, initAttributes line 559; the roll's mean IS that
        // base), so the consult lands it on the engine's own base (the
        // MobType row's 0.045) by the same ratio — the pigman angry
        // multiplier's own precedent shape.
        if (type == MobType.HORSE && !Double.isNaN(horseSpeedRoll)) {
            double referenceScale = horseSubtype == HORSE_SUBTYPE_HORSE
                    ? horseSpeedRoll : HORSE_DONKEY_SPAWN_SPEED;
            return type.walkSpeed * (referenceScale / HORSE_REFERENCE_SPAWN_SPEED);
        }
        return type.walkSpeed;
    }

    /** The attack-damage override's landing (the same refuse-non-positive gate). */
    public void setAttackDamageOverride(float damage) {
        if (damage > 0.0f) {
            this.attackDamageOverride = damage;
        }
    }

    /** @return whether the Attributes row overrode the attack damage. */
    public boolean hasAttackDamageOverride() {
        return !Double.isNaN(attackDamageOverride);
    }

    /**
     * The player's recent-hit window (LivingEntity's own playerHitTimer,
     * lines 92/254/679: 100 ticks from the player's last wound — the
     * dropRareItem gate and the XP gate read it).
     */
    private int playerHitTimer;

    /**
     * The wither variant (setType lines 289-297): the taller 0.72x2.535
     * hull, the fire immunity, the 4.0 melee (the stone-sword arm) over
     * the archer's bow — the flag lands once at the spawn (the reference's
     * own initialize roll).
     */
    public void setSkeletonWither(boolean wither) {
        this.skeletonWither = wither;
    }

    /** @return the wither variant's flag. */
    public boolean skeletonWither() {
        return skeletonWither;
    }

    /**
     * @return the wither effect's duration on the variant's landed melee
     * hit (tryDamage line 99: the flat 200 ticks).
     */
    public int skeletonWitherEffectTicks() {
        return SKELETON_WITHER_EFFECT_TICKS;
    }

    /**
     * @return whether the body never ignites — the kind's own immunity
     * or the wither variant's (setType line 291:
     * {@code immuneToFire = type == 1}).
     */
    public boolean fireImmune() {
        return type.fireImmune() || (type == MobType.SKELETON && skeletonWither);
    }

    /**
     * The held equipment (setEquipment slot 0): the bow at the archer's
     * spawn (addRandomEquipment line 211), the stone sword at the wither
     * conversion (initialize line 220). The wire rides the Entity
     * Equipment packet; the drops ride the manager's equipment walk.
     *
     * @param held the held stack, or null for the empty hand.
     */
    public void setHeldEquipment(ItemStack held) {
        this.heldEquipment = held;
    }

    /** @return the held stack, or null for the empty hand. */
    public ItemStack heldEquipment() {
        return heldEquipment;
    }

    /**
     * The five-slot equipment read (MobEntity.getEquipment line 549:
     * {@code this.equipment[slot]}): [0] the held stack, [1] feet, [2] legs,
     * [3] chest, [4] head. A slot past the band reads the held form (the
     * reference's array index would throw — the engine's own clamped form,
     * the {@code inventoryDropChance} family's own convention).
     */
    public ItemStack equipment(int slot) {
        if (slot <= 0) {
            return heldEquipment;
        }
        if (slot > armorEquipment.length) {
            return heldEquipment;
        }
        return armorEquipment[slot - 1];
    }

    /**
     * The five-slot equipment write (MobEntity.setEquipment line 559): the
     * held stack at 0, the armor family at 1..4 — the slot the wire's
     * EQUIPMENT_SLOT_* band and the NBT Equipment rows share.
     */
    public void setEquipment(int slot, ItemStack stack) {
        if (slot <= 0 || slot > armorEquipment.length) {
            this.heldEquipment = stack;
            return;
        }
        this.armorEquipment[slot - 1] = stack;
    }

    // --- the public equipment family (§27.3's VIII4 exposure — the engine
    // object IS the handle, §27.5; the forgiving api forms over the engine's
    // own null-form reads) --------------------------------------------

    /**
     * The public write path's hook (the VIII4 exposure — the unleash
     * detacher's own family): the wired route carries the §27.6
     * owner-context assert, the one authoritative {@link #setEquipment},
     * and the manager's Entity Equipment re-publish. Null in bare
     * harnesses — {@link #setEquipmentAt} then falls back to the raw
     * authoritative write (the state still lands, the unleash
     * pattern's own bare form).
     */
    private java.util.function.BiConsumer<Integer, ItemStack> equipmentWritePath;

    /** Called by the owning manager at spawn (and by the retro-wiring setter). */
    public void setEquipmentWritePath(java.util.function.BiConsumer<Integer, ItemStack> path) {
        this.equipmentWritePath = path;
    }

    /** The public §27.3 count: the reference's own five (line 60's array). */
    @Override
    public int equipmentSlotCount() {
        return 1 + armorEquipment.length;
    }

    /**
     * The public §27.3 five-slot read: the canonical empty stack for a
     * bare slot (the engine's own null form never crosses the api —
     * the PlayerInventory armorAt convention) and for a slot outside
     * the band (the forgiving read, Vehicle.Cargo's own form — never
     * the engine's clamped held-echo).
     */
    @Override
    public ItemStack equipmentAt(int slot) {
        if (slot < 0 || slot > armorEquipment.length) {
            return ItemStack.EMPTY;
        }
        ItemStack at = equipment(slot);
        return at == null ? ItemStack.EMPTY : at;
    }

    /**
     * The public §27.3 band copy: the five stacks with the bare slots
     * as the canonical empty stack (the record's immutability keeps
     * the copy behavior-free — armorStacks' own note).
     */
    @Override
    public ItemStack[] equipmentStacks() {
        ItemStack[] copy = new ItemStack[1 + armorEquipment.length];
        for (int slot = 0; slot < copy.length; slot++) {
            ItemStack at = equipment(slot);
            copy[slot] = at == null ? ItemStack.EMPTY : at;
        }
        return copy;
    }

    /**
     * The public §27.3 write arm: the band guard first (an out-of-band
     * or null write is ignored — the forgiving convention; the engine's
     * own clamped form must never let a plugin's stray slot touch the
     * held stack), then the canonical-empty mapping (the engine's bare
     * slots store null — the form the spawn dice, the NBT rows and the
     * null-check walks all ride), then the ONE authoritative route:
     * the wired path (assert + write + the re-publish) or the bare
     * fallback write.
     */
    @Override
    public void setEquipmentAt(int slot, ItemStack stack) {
        if (slot < 0 || slot > armorEquipment.length || stack == null) {
            return;
        }
        ItemStack landing = stack.isEmpty() ? null : stack;
        java.util.function.BiConsumer<Integer, ItemStack> path = equipmentWritePath;
        if (path != null) {
            path.accept(slot, landing);
        } else {
            setEquipment(slot, landing);
        }
    }

    /**
     * The armor-points consult (LivingEntity.getArmorProtection lines
     * 852-863): the per-piece sum over ALL FIVE equipment slots — the
     * reference's own quirk that a helmet HELD in the hand contributes its
     * points too (the loop checks {@code instanceof ArmorItem}, never the
     * slot). Non-armor stacks (the pumpkin, the skull) contribute nothing.
     * The magma cube's own override (MagmaCubeEntity lines 34-37) replaces
     * the whole walk: {@code size * 3} — the hull's own protection, the
     * equipment never consulted. The wither's own override
     * (WitherEntity lines 338-340) answers a flat {@code 4} the same way.
     */
    public int armorProtectionPoints() {
        if (type == MobType.MAGMA_CUBE) {
            return slimeSize() * 3; // the reference's own override
        }
        // WitherEntity.getArmorProtection lines 338-340: the boss's own
        // hull answers a FLAT FOUR — the equipment walk never runs (a
        // bare wither carries the same four points a diamond chestplate's
        // wearer would sum from that piece, and no held helmet can raise
        // it).
        if (type == MobType.WITHER) {
            return 4;
        }
        int points = 0;
        for (int slot = 0; slot <= armorEquipment.length; slot++) {
            ItemStack stack = equipment(slot);
            if (stack != null && !stack.isEmpty()) {
                points += net.zaminmc.torch.server.item.Armor
                        .specOf(stack.type()).map(spec -> spec.armorPoints())
                        .orElse(0);
            }
        }
        return points;
    }

    /**
     * The armor envelope (LivingEntity.getDamageAfterArmor lines 868-877):
     * {@code damage * (25 - points) / 25.0F} — the 4%-per-point reduction,
     * the 20-point/80% cap implicit in the reference's own band (a full
     * diamond set sums 20, the deepest the vanilla pieces reach). The
     * reference's exact float arithmetic ({@code f = damage * i; f / 25.0F})
     * — no early return, the 0-point walk answers the incoming damage back.
     * The {@code damageArmor} wear call rides the PLAYER arm alone in the
     * reference (LivingEntity's own no-op body — a mob's armor never wears).
     */
    public float damageAfterArmor(float damage) {
        int i = 25 - armorProtectionPoints();
        float f = damage * i;
        return f / 25.0F;
    }

    /**
     * The player's hit window arms (LivingEntity.takeDamage lines
     * 678-680: {@code playerHitTimer = 100} when the attacker is a
     * player — the tamed wolf's own arm rides the pet slice).
     */
    public void markPlayerHit() {
        playerHitTimer = 100;
    }

    /**
     * @return whether a player wounded the body inside the last 100
     * ticks (LivingEntity.die's dropRareItem gate, line 766).
     */
    public boolean playerHitRecently() {
        return playerHitTimer > 0;
    }

    /** The window's own decay (LivingEntity.mobTick lines 254-255). */
    private void tickPlayerHitTimer() {
        if (playerHitTimer > 0) {
            playerHitTimer--;
        }
    }

    /** @return and clears the fireball volley verdict. */
    public boolean consumeBlazeFireball() {
        boolean value = pendingBlazeFireball;
        pendingBlazeFireball = false;
        return value;
    }

    /** @return and clears the fire-flag flip verdict. */
    public boolean consumeBlazeFireDirty() {
        boolean value = blazeFireDirty;
        blazeFireDirty = false;
        return value;
    }

    /**
     * The blaze's fireball aim (AttackGoal.tick lines 214-216 + 234-243):
     * the raw direction toward the target's MID band (the shape's minY +
     * height/2 minus the blaze's own mid), with the per-volley gaussian
     * spread sigma {@code sqrt(sqrt(d0)) * 0.5} on the HORIZONTAL pair
     * only — the vertical component stays exact.
     */
    public double blazeFireballSigma(double squared) {
        return Math.sqrt(Math.sqrt(squared)) * 0.5;
    }

    /**
     * @return the iron golem's heavy hit ({@code 7 + nextInt(15)} —
     * tryDamage line 202).
     */
    public float golemDamageRoll() {
        return GOLEM_DAMAGE_BASE + random.nextInt(GOLEM_DAMAGE_SPREAD);
    }

    /** @return the launch the landed golem hit gives (velocityY += 0.4F). */
    public double golemLaunchY() {
        return GOLEM_LAUNCH_Y;
    }

    /** @return and clears the golem's swing verdict (the Event-4 site). */
    public boolean consumeGolemSwing() {
        boolean value = pendingGolemSwing;
        pendingGolemSwing = false;
        return value;
    }

    /** @return and clears the snow golem's volley verdict. */
    public boolean consumeSnowGolemShot() {
        boolean value = pendingSnowGolemShot;
        pendingSnowGolemShot = false;
        return value;
    }

    /**
     * The guardian's own hunt read (VT-7c — the reference's ActiveTargetGoal
     * over LivingEntity with GuardianTargetValidator line 591): players AND
     * squids share one grown-box scan and the NEAREST body passing the
     * validator's {@code squaredDistance > 9} band owns the hunt. The
     * player arm keeps the shared nearest-player read (its follow-range
     * gate with the sneaking/invisibility folds the engine's scan already
     * carries); the squid arm rides the same follow-range box (the scan's
     * own {@code grown(d0, 4.0, d0)} shape reads the follow range).
     *
     * @param playerTarget the shared scan's nearest player (may be null)
     * @return the hunt's own target position — the nearer of the pair
     */
    private Position guardianHuntTarget(Position playerTarget) {
        guardianSquidHunt = null;
        MobEntity squid = world.nearestMobOfKind(position.x(), position.y(), position.z(),
                followRange(), MobType.SQUID);
        if (squid != null && !squid.dead()) {
            double sx = squid.position().x() - position.x();
            double sy = squid.position().y() - position.y();
            double sz = squid.position().z() - position.z();
            double squidSquared = sx * sx + sy * sy + sz * sz;
            if (squidSquared > GUARDIAN_TARGET_MIN_SQUARED) {
                // the validator's own band — both kinds share it
                if (playerTarget == null) {
                    guardianSquidHunt = squid;
                    return squid.position();
                }
                double px = playerTarget.x() - position.x();
                double py = playerTarget.y() - position.y();
                double pz = playerTarget.z() - position.z();
                if (squidSquared < px * px + py * py + pz * pz) {
                    guardianSquidHunt = squid; // the EntityDistanceComparator's nearest
                    return squid.position();
                }
            }
        }
        return playerTarget;
    }

    /** @return the live squid the hunt rides (the manager's beam id + hit
     * walk read), or null when the hunt rides a player. */
    public MobEntity guardianHuntSquid() {
        return guardianSquidHunt;
    }

    /**
     * The guardian's mind (GuardianAttackEntityGoal + the wander family):
     * the laser's charge cycle against the nearest player beyond the
     * flop band with a clear line (the target validator's {@code > 9}
     * squared), then the squid-style wander re-roll at the guardian's
     * own 80-tick cadence when the hunt is off.
     */
    private void tickGuardianMind(Position target, boolean canHunt) {
        boolean hunting = false;
        if (target != null && canHunt && world.clearLine(eyePosition(), target)) {
            double dx = target.x() - position.x();
            double dy = target.y() - position.y();
            double dz = target.z() - position.z();
            double squared = dx * dx + dy * dy + dz * dz;
            // GuardianAttackEntityGoal.shouldContinue lines 474-476: the
            // ELDER never drops a live charge for proximity (its own
            // {@code isElder() ||} exemption — only the plain guardian's
            // band break applies); a fresh hunt still needs the band.
            if (squared > GUARDIAN_TARGET_MIN_SQUARED
                    || (guardianElder && guardianChargeTicks != Integer.MIN_VALUE)) {
                hunting = true;
                // The look stays on the target every charge tick (the
                // reference's own setLookatValues(…, 90.0F, 90.0F)).
                yaw = angleTo(dx, dz);
                headYaw = yaw;
                if (guardianChargeTicks == Integer.MIN_VALUE) {
                    // start(): the -10 offset, the navigation stop, the look.
                    guardianChargeTicks = GUARDIAN_CHARGE_START;
                    clearPath();
                }
                // tick(): the charge climbs while the target stays seen.
                guardianChargeTicks++;
                if (guardianChargeTicks == 0) {
                    // setBeamTag(target id) + doEntityEvent(this, (byte)21).
                    pendingGuardianBeamStart = true;
                } else if (guardianChargeTicks >= guardianChargeTime()) {
                    // The hit: the magic base + the elder bump (the hard
                    // bump rides the engine's difficulty read), then the
                    // melee lands through the manager's pair.
                    pendingGuardianLaserHit = true;
                    guardianChargeTicks = Integer.MIN_VALUE; // stop()
                    guardianBeamTargetId = 0; // setBeamTag(0)
                }
            }
        }
        if (!hunting && guardianChargeTicks != Integer.MIN_VALUE) {
            // The hunt broke (no target, no line, inside the flop band):
            // stop() — the beam clears, the target resets.
            guardianChargeTicks = Integer.MIN_VALUE;
            guardianBeamTargetId = 0;
        }
        guardianMoving = !hunting && (guardianHasSteer
                && (guardianSteerX != 0.0 || guardianSteerY != 0.0 || guardianSteerZ != 0.0));
        if (hunting) {
            return; // the charge stands still (the navigation stop)
        }
        // The wander (WanderAroundGoal 1.0, the interval 80): the re-rolled
        // swim direction — the squid's own model at the guardian's cadence.
        // The water-favor arm (WaterPathNavigation's getPathfindingFavor:
        // the waypoints never leave the water): a dry cell re-rolls with
        // the climbing vertical.
        boolean dryCell = !world.inFluid(position.x(), position.y() + 0.2, position.z());
        // The village-pin return (GuardianEntity.mobAiTick line 377's
        // self-cache + WanderThroughVillageGoal 1.0): the pin lands at
        // the body's first mind tick (the reference sets it EVERY tick
        // while unset — the first tick's position), radius 16, and never
        // re-pins. A body outside the pin's radius steers back toward it
        // (the reference's half-plane-toward-pin candidate pick over the
        // water navigation; the engine's steer carries the swim home).
        if (!villageCacheSet()) {
            setVillageCache((int) Math.floor(position.x()),
                    (int) Math.floor(position.y()),
                    (int) Math.floor(position.z()), GUARDIAN_VILLAGE_PIN_RADIUS);
        }
        long guardianPinSquared = cellSquaredDistance(
                (long) Math.floor(position.x()), (long) Math.floor(position.y()),
                (long) Math.floor(position.z()),
                villageCenterX, villageCenterY, villageCenterZ);
        boolean outsidePin = villageCacheSet() && guardianPinSquared
                >= (long) GUARDIAN_VILLAGE_PIN_RADIUS * GUARDIAN_VILLAGE_PIN_RADIUS;
        if (outsidePin && !dryCell) {
            // The 3D homing direction (the reference's navigation walks
            // its waypoints in three axes — the pin's cell center): the
            // normalized vector at the swim scale, the body swimming
            // home through the column (a pin above lifts the body, a
            // pin below draws it down).
            double pinDx = villageCenterX + 0.5 - position.x();
            double pinDy = villageCenterY + 0.5 - position.y();
            double pinDz = villageCenterZ + 0.5 - position.z();
            double pinLength = Math.sqrt(
                    pinDx * pinDx + pinDy * pinDy + pinDz * pinDz);
            if (pinLength > 1e-6) {
                guardianSteerX = (pinDx / pinLength) * GUARDIAN_SWIM_SCALE;
                guardianSteerY = (pinDy / pinLength) * GUARDIAN_SWIM_SCALE;
                guardianSteerZ = (pinDz / pinLength) * GUARDIAN_SWIM_SCALE;
                guardianHasSteer = true;
                yaw = angleTo(guardianSteerX, guardianSteerZ);
                return;
            }
        }
        if (!guardianHasSteer || random.nextInt(GUARDIAN_WANDER_INTERVAL) == 0 || dryCell) {
            float bearing = random.nextFloat() * (float) Math.PI * 2.0f;
            guardianSteerX = Math.cos(bearing) * GUARDIAN_SWIM_SCALE;
            guardianSteerY = dryCell
                    ? GUARDIAN_SWIM_SCALE / 2.0 // the climb back to the column
                    : -0.05 + random.nextFloat() * 0.1;
            guardianSteerZ = Math.sin(bearing) * GUARDIAN_SWIM_SCALE;
            guardianHasSteer = true;
            yaw = angleTo(guardianSteerX, guardianSteerZ);
        }
    }

    /**
     * The guardian's body (GuardianMovementControl + moveRelative + mobTick's
     * flop): in water the sinusoidal swim — the steering target joins the
     * {@code sin((ticks + id) * 0.5) * 0.05} horizontal drift and the
     * {@code sin((ticks + id) * 0.75) * 0.25} vertical, the position
     * integrates the velocity, then the 0.9 decay and the idle sink; on
     * land the grounded body flops (the +0.5 rise, the ±0.4 horizontal, the
     * random yaw) between the gravity falls.
     */
    private void tickGuardianBody(boolean inFluid) {
        if (inFluid) {
            double steerX = guardianMoving ? guardianSteerX : 0.0;
            double steerY = guardianMoving ? guardianSteerY : 0.0;
            double steerZ = guardianMoving ? guardianSteerZ : 0.0;
            velocityX += steerX;
            velocityY += steerY;
            velocityZ += steerZ;
            // The bobbing arms (the movement control's own: lines 553-559 —
            // the lifetime clock + the body's own network id phase them).
            double drift = Math.sin((guardianTicks + entityId) * 0.5) * 0.05;
            double cosYaw = Math.cos(Math.toRadians(yaw));
            double sinYaw = Math.sin(Math.toRadians(yaw));
            velocityX += drift * cosYaw;
            velocityZ += drift * sinYaw;
            velocityY += Math.sin((guardianTicks + entityId) * 0.75) * 0.05
                    * (sinYaw + cosYaw) * 0.25;
            // THE SWEPT-BOX APPLICATION (the physics-solver slice): the
            // swimmer's own Entity.move — no step, the seabed rest the
            // solver's own downward clip — when the world provides the
            // collision surface.
            if (solverMove(velocityX, velocityY, velocityZ, 0.0) != null) {
                velocityX *= GUARDIAN_SWIM_DECAY;
                velocityY *= GUARDIAN_SWIM_DECAY;
                velocityZ *= GUARDIAN_SWIM_DECAY;
                if (!guardianMoving && guardianChargeTicks == Integer.MIN_VALUE) {
                    velocityY -= GUARDIAN_IDLE_SINK; // the idle drift down
                }
                return;
            }
            double newX = position.x() + velocityX;
            double newY = position.y() + velocityY;
            double newZ = position.z() + velocityZ;
            // The seabed rest (move()'s own collision): a sinking body in
            // water stands on the solid floor instead of passing through.
            if (velocityY <= 0 && world.isSolidAt(newX, newY - GROUND_EPSILON, newZ)) {
                newY = world.supportY(newX, newY - GROUND_EPSILON, newZ);
                velocityY = 0.0;
                onGround = true;
            } else {
                onGround = false;
            }
            position = new Position(newX, newY, newZ);
            velocityX *= GUARDIAN_SWIM_DECAY;
            velocityY *= GUARDIAN_SWIM_DECAY;
            velocityZ *= GUARDIAN_SWIM_DECAY;
            if (!guardianMoving && guardianChargeTicks == Integer.MIN_VALUE) {
                velocityY -= GUARDIAN_IDLE_SINK; // the idle drift down
            }
            return;
        }
        // The land body (mobTick lines 321-330 + the standard fall): the
        // grounded flop — the +0.5 rise, the ±0.4 horizontal pair, the
        // random yaw — between the gravity falls with the ground snap.
        if (onGround) {
            velocityY += GUARDIAN_FLOP_RISE;
            velocityX += (random.nextFloat() * 2.0F - 1.0F) * GUARDIAN_FLOP_BAND;
            velocityZ += (random.nextFloat() * 2.0F - 1.0F) * GUARDIAN_FLOP_BAND;
            yaw = random.nextFloat() * 360.0F;
            onGround = false;
        } else {
            velocityY = (velocityY - GRAVITY_PER_TICK) * 0.98;
        }
        // THE SWEPT-BOX APPLICATION (the physics-solver slice): the
        // flopping body's own Entity.move — the flop's rise clips at the
        // ceiling, the fall rests on the floor's shape — when the world
        // provides the collision surface.
        if (solverMove(velocityX, velocityY, velocityZ, 0.0) != null) {
            return;
        }
        double newX = position.x() + velocityX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + velocityZ;
        if (velocityY <= 0 && world.isSolidAt(newX, newY - GROUND_EPSILON, newZ)) {
            newY = world.supportY(newX, newY - GROUND_EPSILON, newZ);
            velocityY = 0.0;
            onGround = true;
        } else {
            onGround = false;
        }
        position = new Position(newX, newY, newZ);
    }

    /** The anger window's roll: {@code 400 + nextInt(400)} ticks. */
    private static final int PIGMAN_ANGER_BASE = 400;
    /** The angry sound's own delay: {@code nextInt(40)} ticks. */
    private static final int PIGMAN_ANGER_SOUND_BOUND = 40;
    /** The angry speed boost (the +0.05 attribute modifier on the 0.23
     * base, expressed as the chase multiplier). */
    private static final double PIGMAN_ANGRY_CHASE_MULTIPLIER = 1.1 * (0.28 / 0.23);
    /** The anger's group radius (RevengeGoal's call-others box: the
     * follow range 32 around the hurt body). */
    private static final double PIGMAN_ANGER_RADIUS_SQUARED = 32.0 * 32.0;

    /** The decaying anger window (0 = the calm neutral state). */
    private int pigmanAnger;
    /** The angry sound's countdown (0 = idle). */
    private int pigmanAngerSoundDelay;
    /** The anger's target player (the attacker's name — the anger keys on
     * the player, not the mob-target state). */
    private String pigmanAngerTarget = "";
    /** Set when the delayed angry sound fires; consumed by the manager. */
    private boolean pendingPigmanAngrySound;

    /** @return whether the pigman is angry (TargetPlayerGoal's gate). */
    public boolean isAngry() {
        return pigmanAnger > 0;
    }

    /**
     * The anger lands ({@code getAngryTo}): the 400+nextInt(400) window,
     * the delayed angry sound, and the attacker's identity. The group
     * propagation is the manager's own walk (the call-others family).
     */
    public void becomeAngryTo(String playerName) {
        pigmanAnger = PIGMAN_ANGER_BASE + random.nextInt(PIGMAN_ANGER_BASE);
        pigmanAngerSoundDelay = random.nextInt(PIGMAN_ANGER_SOUND_BOUND);
        if (playerName != null) {
            pigmanAngerTarget = playerName;
        }
    }

    /** @return the anger's target player name (the attacker), or "". */
    public String angerTargetName() {
        return pigmanAngerTarget;
    }

    /** @return and clears the delayed angry sound. */
    public boolean consumePigmanAngrySound() {
        boolean value = pendingPigmanAngrySound;
        pendingPigmanAngrySound = false;
        return value;
    }

    /** The anger's own per-tick walk (mobAiTick): the decay, the delayed
     * sound, and the angry speed boost through the goal-walk multiplier. */
    private void tickPigmanAnger() {
        if (pigmanAnger > 0) {
            pigmanAnger--;
            if (pigmanAngerSoundDelay > 0 && --pigmanAngerSoundDelay == 0) {
                pendingPigmanAngrySound = true;
            }
        }
    }

    /** ShootFireballGoal's charge cycle: 20 to the launch, -40 after. */
    private int ghastChargeTicks;
    /** The flight's current target (MoveAroundGoal's random ±16 pick). */
    private double ghastTargetX;
    private double ghastTargetY;
    private double ghastTargetZ;
    /** Whether the flight is steering (the movement control's moving flag). */
    private boolean ghastMoving;
    /** The movement control's burst cadence (the 2-7 tick clock). */
    private int ghastBurstCooldown;
    /** Set when the charge crossed 10 (the 1007 warning sound; the manager consumes). */
    private boolean pendingGhastChargeSound;
    /** The launch target (set at charge 20; the manager consumes and launches). */
    private Position pendingGhastFireball;

    /** @return the charging flag (GhastEntity's synced slot 16). */
    public boolean ghastCharging() {
        return ghastChargeTicks > 10;
    }

    /** @return and clears the 1007 charge warning. */
    public boolean consumeGhastChargeSound() {
        boolean value = pendingGhastChargeSound;
        pendingGhastChargeSound = false;
        return value;
    }

    /** @return and clears the fireball launch target. */
    public Position consumeGhastFireball() {
        Position value = pendingGhastFireball;
        pendingGhastFireball = null;
        return value;
    }

    /**
     * The ghast's own mind (GhastEntity's goal set): the LookAroundGoal
     * faces the drift or the target inside 64 blocks, and the
     * ShootFireballGoal runs the charge cycle — 20 ticks of charge (the
     * 1007 warning at 10), the launch, the -40 cooldown — only while the
     * target stands inside 64 blocks on a clear line.
     */
    private void tickGhastMind(Position target, boolean canHunt) {
        // LookAroundGoal (lines 153-180): the eyes face the drift, or the
        // target inside the 64-block grip.
        if (target != null) {
            double dx = target.x() - position.x();
            double dz = target.z() - position.z();
            if (dx * dx + (target.y() - position.y()) * (target.y() - position.y())
                    + dz * dz < 64.0 * 64.0) {
                yaw = angleTo(dx, dz);
                headYaw = yaw;
            }
        } else if (velocityX != 0.0 || velocityZ != 0.0) {
            yaw = (float) Math.toDegrees(Math.atan2(-velocityX, velocityZ));
            headYaw = yaw;
        }

        // ShootFireballGoal (lines 266-324).
        boolean inRange = target != null && canHunt;
        if (inRange) {
            double dx = target.x() - position.x();
            double dy = target.y() - position.y();
            double dz = target.z() - position.z();
            inRange = dx * dx + dy * dy + dz * dz < 64.0 * 64.0
                    && world.clearLine(eyePosition(), target);
        }
        if (inRange) {
            ghastChargeTicks++;
            if (ghastChargeTicks == 10) {
                pendingGhastChargeSound = true; // the 1007 warning
            }
            if (ghastChargeTicks == 20) {
                pendingGhastFireball = target; // the 1008 launch
                ghastChargeTicks = -40;
            }
        } else if (ghastChargeTicks > 0) {
            ghastChargeTicks--;
        }
    }

    /**
     * The ghast's flight body (MoveAroundGoal + the ghast's own
     * MovementControl over FlyingMobEntity): no gravity, the 0.91 drag, a
     * random ±16 target picked when the flight stalls, and the burst
     * cadence — every 2-7 ticks a 0.1 acceleration toward the target when
     * the line is clear (a blocked line stops the steering).
     */
    private void tickGhastBody() {
        if (!ghastMoving) {
            // MoveAroundGoal.start: the random ±16 cube around the body.
            ghastTargetX = position.x() + (random.nextFloat() * 2.0f - 1.0f) * 16.0;
            ghastTargetY = position.y() + (random.nextFloat() * 2.0f - 1.0f) * 16.0;
            ghastTargetZ = position.z() + (random.nextFloat() * 2.0f - 1.0f) * 16.0;
            ghastMoving = true;
            ghastBurstCooldown = 0;
        }
        double dx = ghastTargetX - position.x();
        double dy = ghastTargetY - position.y();
        double dz = ghastTargetZ - position.z();
        double squared = dx * dx + dy * dy + dz * dz;
        if (squared < 1.0 || squared > 3600.0) {
            ghastMoving = false; // the arrival (or the runaway): a new pick
        } else if (--ghastBurstCooldown <= 0) {
            ghastBurstCooldown = random.nextInt(5) + 2;
            double length = Math.sqrt(squared);
            if (world.clearLine(position(),
                    new Position(ghastTargetX, ghastTargetY, ghastTargetZ))) {
                velocityX += dx / length * 0.1;
                velocityY += dy / length * 0.1;
                velocityZ += dz / length * 0.1;
            } else {
                ghastMoving = false; // a blocked line stops the steering
            }
        }
        // FlyingMobEntity.moveRelative's drag arms (lines 19-49): the
        // fluid consult picks the band — WATER *0.8, LAVA *0.5, the air
        // 0.91 — over all three axes, with NO gravity arm in any (the
        // flying family's own model). The consult is the engine's own
        // y+0.2 point convention (the reference's box-grown read, the
        // standing named difference); the drag lands before the move like
        // the walker's F1 phase note.
        double ghastDrag;
        if (world.inFluid(position.x(), position.y() + 0.2, position.z())) {
            ghastDrag = world.inLava(position.x(), position.y() + 0.2, position.z()) ? 0.5 : 0.8;
        } else {
            ghastDrag = 0.91;
        }
        velocityX *= ghastDrag;
        velocityY *= ghastDrag;
        velocityZ *= ghastDrag;
        if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
            mode = Mode.IDLE;
            modeTicks = 0;
        }
    }

    /**
     * The flying body's position integration: the swept-box clip (the
     * physics-solver slice — the reference's own {@code Entity.move}
     * with no step and no ground: a flying body's drift stops exactly
     * at each wall's face, axis by axis) when the world provides the
     * collision surface; the per-axis cell stand-in (a solid blocking
     * cell stops that axis' drift) otherwise.
     */
    private void integrateFlight() {
        // THE SWEPT-BOX APPLICATION: the ghast's own Entity.move — the
        // 4-tall body clips against every box its swept shape touches.
        double preMoveX = position.x();
        double preMoveY = position.y();
        double preMoveZ = position.z();
        net.zaminmc.torch.server.physics.EntityMoveSolver.Result swept =
                solverMove(velocityX, velocityY, velocityZ, 0.0);
        if (swept != null) {
            if (swept.collidingHorizontally()) {
                ghastMoving = false;
            }
            // The step-sound walk (Entity.move's closing arm — a flying
            // body scraping a floor's cell steps on it; the air cell
            // gate refuses the open sky).
            stepSoundWalk(preMoveX, preMoveY, preMoveZ);
            return;
        }
        double midY = position.y() + 2.0; // the 4-tall body's center band
        double newX = position.x() + velocityX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + velocityZ;
        if (world.isSolidAt(newX, midY, position.z())) {
            newX = position.x();
            velocityX = 0.0;
            ghastMoving = false;
        }
        if (world.isSolidAt(position.x(), newY + 2.0, position.z())
                || world.isSolidAt(position.x(), newY, position.z())) {
            newY = position.y();
            velocityY = 0.0;
            ghastMoving = false;
        }
        if (world.isSolidAt(position.x(), midY, newZ)) {
            newZ = position.z();
            velocityZ = 0.0;
            ghastMoving = false;
        }
        position = new Position(newX, newY, newZ);
        onGround = false;
        stepSoundWalk(preMoveX, preMoveY, preMoveZ);
    }

    /**
     * The slime's hop (MovementControl.tick lines 377-410 + jump lines
     * 279-283): the movement input is ZERO between hops (the engine's
     * stand-in for the reference's speed model), and when the cadence
     * elapses on the ground the body launches — velocityY 0.42, the
     * horizontal at the reference's movement-speed attribute
     * ({@code 0.2 + 0.1 * size}) along the facing. The aggressive chase
     * divides the cadence by three (the AttackGoal's own rule); the
     * swim arm hops 80% of the ticks at the 1.2 speed
     * (SwimAroundGoal).
     */
    private void tickSlimeMovement(boolean inFluid) {
        if (inFluid) {
            if (random.nextFloat() < 0.8F) {
                launchSlimeHop(1.2);
            }
            return;
        }
        if (!onGround) {
            return; // airborne: the hop's momentum carries (the engine's own drag)
        }
        if (--slimeHopCooldown > 0) {
            return; // between hops the body is still (the zeroed input)
        }
        // getTicksUntilNextJump: the slime's 20+nextInt(10), the magma
        // cube's own 4x cadence override.
        slimeHopCooldown = (random.nextInt(20) + 10)
                * (type == MobType.MAGMA_CUBE ? 4 : 1);
        if (slimeAggressive) {
            slimeHopCooldown /= 3;
        }
        launchSlimeHop(1.0);
        pendingSlimeSound = new StagedSound(slimeSoundName(),
                0.4F * slimeSize,
                (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
    }

    /** Launches one hop (jump(): velocityY 0.42 — the magma cube's own
     * override rises with the size — plus the facing impulse). */
    private void launchSlimeHop(double speedMultiplier) {
        double speed = (0.2 + 0.1 * slimeSize) * speedMultiplier;
        double radians = Math.toRadians(yaw);
        velocityX += -Math.sin(radians) * speed;
        velocityZ += Math.cos(radians) * speed;
        velocityY = type == MobType.MAGMA_CUBE
                ? 0.42 + slimeSize * 0.1 // MagmaCubeEntity.jump
                : 0.42;
    }

    // ---- the rabbit's hop cycle (Slices 12l + 21d — RabbitEntity's own
    // movement model over the engine's input lane: the Action enum 1:1) ----

    /**
     * RabbitEntity.Action's own five rows (lines 389-423): the speed
     * parameter, the jump strength, the landing delay, and the animation
     * duration — the NONE idle sit, the HOP walk, the STEP jump-control
     * hop, the SPRINT panic/flee flight, the ATTACK killer's lunge.
     */
    private enum RabbitAction {
        NONE(0.0F, 0.0F, 30, 1),
        HOP(0.8F, 0.2F, 20, 10),
        STEP(1.0F, 0.45F, 14, 14),
        SPRINT(1.75F, 0.4F, 1, 8),
        ATTACK(2.0F, 0.7F, 7, 8);

        final float speed;
        final float jumpStrength;
        final int jumpDelay;
        final int jumpDuration;

        RabbitAction(float speed, float jumpStrength, int jumpDelay, int jumpDuration) {
            this.speed = speed;
            this.jumpStrength = jumpStrength;
            this.jumpDelay = jumpDelay;
            this.jumpDuration = jumpDuration;
        }
    }

    /** The live action (mobAiTick's own field; HOP the resting state). */
    private RabbitAction rabbitAction = RabbitAction.HOP;

    /** Ticks until the rabbit's next hop (jumpDelayTicks — the zeroed-input window). */
    private int rabbitJumpDelayTicks;

    /** The animation clock (jumpTicks) — the wire's doEntityEvent(1) fires at 0. */
    private int rabbitJumpTicks;

    /** The running animation length (jumpDuration — 0 between hops). */
    private int rabbitJumpDuration;

    /** The last tick's grounded state (the landing edge). */
    private boolean rabbitLastOnGround = true;

    /**
     * The raid-farm hunger clock (RabbitEntity.ticksBeforeHungry — the
     * MoreCarrotTicks NBT): 100 after a garden eat, decaying by
     * {@code nextInt(3)} per tick; zero means hungry.
     */
    private int rabbitTicksBeforeHungry;

    /** @return the raid-farm hunger clock (0 hungry — the MoreCarrotTicks NBT). */
    public int rabbitTicksBeforeHungry() {
        return rabbitTicksBeforeHungry;
    }

    /** @return the rabbit's skin variant (the synced slot 18's byte). */
    public int rabbitVariant() {
        return rabbitVariant;
    }

    /** @return whether this is the killer bunny (RabbitType 99). */
    public boolean rabbitKiller() {
        return rabbitVariant == 99;
    }

    /**
     * RabbitEntity.setVariant (lines 320-333): the skin byte lands on the
     * synced slot 18 — the 99 arm swaps the whole mind (the flee-wolf goal
     * leaves, the EvilAttack family and the player/wolf hunts join). The
     * reference's custom "Killer Bunny" name rides the name-tag family the
     * engine's mobs do not yet carry on the wire (ledgered).
     */
    public void setRabbitVariant(int variant) {
        rabbitVariant = variant;
    }

    /** The rabbit's skin variant (the synced slot 18: 0-5 natural, 99 the killer). */
    private int rabbitVariant;

    /** Queued at each hop start: the world's doEntityEvent(this, (byte)1). */
    private boolean pendingRabbitJumpStatus;

    /** @return and clears the queued jump event (the client's hop animation). */
    public boolean consumeRabbitJumpStatus() {
        boolean value = pendingRabbitJumpStatus;
        pendingRabbitJumpStatus = false;
        return value;
    }

    /**
     * The rabbit's movement tick (RabbitEntity.mobAiTick lines 120-172 over
     * the engine's input lane): the action band from the live movement
     * speed (SPRINT above 0.8 — the panic and the killer's chase; HOP
     * otherwise; ATTACK holds until the landing's startAction(false) flips
     * it), the delay burn, the hunger decay, the landing edge's delay
     * re-roll, the killer's lunge arm (the attack target within 16 squared
     * and the delay spent: face, move, ATTACK-hop), and the launch itself —
     * the facing impulse at {@code 1.5 x the action speed} over the walk
     * band, the action's own jump strength, the hop sound at the
     * 0.8-centered pitch band (startAction lines 89-101).
     */
    private void tickRabbitHop(boolean inFluid) {
        if (inFluid) {
            // The swim arm (the reference's canSwim navigation — the body
            // keeps paddling): the reference launches hops in fluid.
            if (random.nextFloat() < 0.8F) {
                launchRabbitHop(currentRabbitAction(), Double.NaN);
            }
            return;
        }
        // mobAiTick lines 121-125: the action band. The movementControl's
        // speed parameter — the engine's mode carries it (the panic and the
        // chase run the fast goals; everything else walks).
        if (rabbitJumpDelayTicks > 0) {
            rabbitJumpDelayTicks--;
        }
        // The hunger decay (line 131-136): only a fed clock decays.
        if (rabbitTicksBeforeHungry > 0) {
            rabbitTicksBeforeHungry -= random.nextInt(3);
            if (rabbitTicksBeforeHungry < 0) {
                rabbitTicksBeforeHungry = 0;
            }
        }
        if (!onGround) {
            rabbitLastOnGround = false;
            return; // airborne: the hop's momentum carries
        }
        if (!rabbitLastOnGround) {
            // The landing edge (lines 138-142): startAction(false) — the
            // ATTACK flips back to HOP — then the delay re-rolls from the
            // action the landing left standing.
            if (rabbitAction == RabbitAction.ATTACK) {
                rabbitAction = RabbitAction.HOP;
            }
            rabbitJumpDelayTicks = rabbitAction.jumpDelay;
            rabbitJumpTicks = 0;
            rabbitJumpDuration = 0;
        }
        rabbitLastOnGround = true;
        // The killer's lunge (lines 144-152): grounded, the delay spent, and
        // a live attack target within 16 squared — the look, the movement
        // update, and the ATTACK hop.
        if (rabbitKiller() && rabbitJumpDelayTicks == 0 && visibleTarget != null
                && targetHuntable) {
            double dx = visibleTarget.x() - position.x();
            double dy = visibleTarget.y() - position.y();
            double dz = visibleTarget.z() - position.z();
            if (dx * dx + dy * dy + dz * dz < 16.0) {
                yaw = angleTo(dx, dz);
                headYaw = yaw;
                startRabbitJump(RabbitAction.ATTACK, dy);
                return;
            }
        }
        if (rabbitJumpDelayTicks > 0) {
            return; // between hops the body is still (the zeroed input)
        }
        if (mode == Mode.IDLE) {
            return; // the idle rabbit sits (the NONE action)
        }
        startRabbitJump(currentRabbitAction(), Double.NaN);
    }

    /**
     * mobAiTick's action band (lines 121-125): the movementControl speed
     * above 0.8 answers SPRINT (the reference's 1.33 panic/flee and the
     * killer's 1.4 chase — the engine's PANIC/CHASE modes carry them),
     * everything else HOP. The ATTACK holds until the landing flips it.
     */
    private RabbitAction currentRabbitAction() {
        if (rabbitAction == RabbitAction.ATTACK) {
            return RabbitAction.ATTACK;
        }
        return mode == Mode.PANIC || mode == Mode.CHASE
                ? RabbitAction.SPRINT : RabbitAction.HOP;
    }

    /**
     * startJumping (lines 103-107) + startAction's jump arm (lines 89-101):
     * the navigation speed {@code 1.5 x the action speed}, the animation
     * clock arms, the hop sound at the 0.8-centered pitch band, and the
     * launch — the facing impulse over the walk band and the action's jump
     * strength (getJumpStrength's moving-up arm: {@code 0.5} when the
     * movement target sits above the body's own y + 0.5).
     */
    private void startRabbitJump(RabbitAction action, double targetDy) {
        rabbitAction = action;
        rabbitJumpDuration = action.jumpDuration;
        rabbitJumpTicks = 0;
        pendingRabbitJumpStatus = true; // mobTick's doEntityEvent(1)
        launchRabbitHop(action, targetDy);
        pendingSlimeSound = new StagedSound("mob.rabbit.hop",
                1.0F,
                ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
    }

    /**
     * One hop launch: the facing impulse at {@code 1.5 x the action speed}
     * over the walk band, the strength rise — getJumpStrength's own
     * override (line 72-74): a movement target above the body's
     * {@code y + 0.5} answers the flat 0.5 rise whatever the action.
     */
    private void launchRabbitHop(RabbitAction action, double targetDy) {
        double radians = Math.toRadians(yaw);
        double launchSpeed = 1.5 * action.speed;
        velocityX += -Math.sin(radians) * walkSpeed() * launchSpeed;
        velocityZ += Math.cos(radians) * walkSpeed() * launchSpeed;
        velocityY = !Double.isNaN(targetDy) && targetDy > 0.5
                ? 0.5 : action.jumpStrength;
    }

    // ---- the rabbit's garden raid (Slice 21d — RaidFarmGoal over
    // GoToBlockGoal, lines 521-597) ----

    /** RaidFarmGoal's cooldown between scans (GoToBlockGoal.canStart's own). */
    private int rabbitFarmCooldown;

    /** The garden's farmland cell (GoToBlockGoal.target). */
    private int rabbitFarmX;
    private int rabbitFarmY;
    private int rabbitFarmZ;

    /** Whether the scan found a garden this window (RaidFarmGoal.hasTarget). */
    private boolean rabbitFarmHasTarget;

    /** The approach clock (GoToBlockGoal.activeTicks — the give-up band). */
    private int rabbitFarmActiveTicks;

    /** The window's cap (start's own {@code nextInt(nextInt(1200)+1200)+1200}). */
    private int rabbitFarmMaxTicks;

    /** Whether the body stands at the garden (hasReachedTarget). */
    private boolean rabbitFarmReached;

    /** The hungry gate's snapshot (RaidFarmGoal.hungry — taken at the scan). */
    private boolean rabbitFarmHungry;

    /** The pending garden eat (the manager's intent): the carrots' cell. */
    private boolean pendingRabbitFarmEat;

    /** @return and clears the queued garden eat (the farmland cell above). */
    public boolean consumeRabbitFarmEat() {
        boolean value = pendingRabbitFarmEat;
        pendingRabbitFarmEat = false;
        return value;
    }

    /** The eaten garden's carrots cell (the pending intent's target.up()). */
    public int rabbitFarmEatX() {
        return rabbitFarmX;
    }

    public int rabbitFarmEatY() {
        return rabbitFarmY + 1;
    }

    public int rabbitFarmEatZ() {
        return rabbitFarmZ;
    }

    /**
     * RaidFarmGoal's eat landing (onEatFromGarden line 363-376): the
     * hunger clock refills to 100 — the manager's intent completes the
     * world write and the particle.
     */
    public void completeRabbitFarmEat() {
        rabbitTicksBeforeHungry = 100;
    }

    /**
     * RaidFarmGoal.canStart/shouldContinue over GoToBlockGoal (lines
     * 532-547 + 78-96 + 66-70): a live window continues while the approach
     * clock stays inside the band and the garden still stands; a closed
     * window waits out the cooldown ({@code 200 + nextInt(200)} between
     * scans, 10 after an eat), snapshots the hungry gate, then runs the
     * spiral hunt — the reference's own ring walk over the ±16 box at the
     * y-1/y+0/y-2 bands, a farmland cell with age-7 carrots above
     * answering the target. The mobGriefing gate rides the engine's
     * default-on stand-in (no gamerule surface yet — ledgered).
     */
    boolean goalRabbitFarmCanUse() {
        if (type != MobType.RABBIT) {
            return false;
        }
        // mobGriefing (Slice 24a — RaidFarmGoal.canStart line 534's first
        // gate): a no-grief world never opens the raid window; an ALIVE
        // window continues (the reference re-checks only at canStart, the
        // garden walk honors a window opened under the rule).
        if (!world.mobGriefing()) {
            return false;
        }
        if (rabbitFarmHasTarget) {
            // shouldContinue (GoToBlockGoal lines 66-70 over the wrapper's
            // own gate): the approach band and the live garden.
            if (rabbitFarmActiveTicks >= -rabbitFarmMaxTicks
                    && rabbitFarmActiveTicks <= 1200
                    && isRabbitGardenAt(rabbitFarmX, rabbitFarmY, rabbitFarmZ)) {
                return true;
            }
            rabbitFarmHasTarget = false; // stop(): the goal leaves quietly
            return false;
        }
        if (rabbitFarmCooldown > 0) {
            rabbitFarmCooldown--;
            return false;
        }
        rabbitFarmCooldown = 200 + random.nextInt(200);
        rabbitFarmHungry = rabbitTicksBeforeHungry == 0;
        if (!rabbitFarmHungry) {
            return false;
        }
        int cx = (int) Math.floor(position.x());
        int cy = (int) Math.floor(position.y());
        int cz = (int) Math.floor(position.z());
        int range = 16;
        // GoToBlockGoal.findNearestBlock's own spiral: k the y band
        // (0, 1, -1 → y-1, y+0, y-2), l the ring radius, i1/j1 the ring's
        // own cross walk.
        for (int k = 0; k <= 1; k = k > 0 ? -k : 1 - k) {
            for (int l = 0; l < range; l++) {
                for (int i1 = 0; i1 <= l; i1 = i1 > 0 ? -i1 : 1 - i1) {
                    for (int j1 = i1 < l && i1 > -l ? l : 0; j1 <= l; j1 = j1 > 0 ? -j1 : 1 - j1) {
                        int x = cx + i1;
                        int y = cy + k - 1;
                        int z = cz + j1;
                        if (isRabbitGardenAt(x, y, z)) {
                            rabbitFarmX = x;
                            rabbitFarmY = y;
                            rabbitFarmZ = z;
                            rabbitFarmHasTarget = true;
                            // GoToBlockGoal.start (lines 79-85) — the goal
                            // arms its window the moment the scan answers:
                            // activeTicks 0 and the maxTicks roll
                            // {@code nextInt(nextInt(1200) + 1200) + 1200}.
                            rabbitFarmActiveTicks = 0;
                            rabbitFarmMaxTicks = random.nextInt(random.nextInt(1200) + 1200) + 1200;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * RaidFarmGoal.isValidTarget (lines 583-596): a farmland cell with
     * age-7 carrots standing above it.
     */
    private boolean isRabbitGardenAt(int x, int y, int z) {
        var below = world.blockIdAt(x, y, z);
        if (below == null || !below.value().equals("farmland")) {
            return false;
        }
        var above = world.blockIdAt(x, y + 1, z);
        return above != null && above.value().equals("carrots_stage7");
    }

    /**
     * GoToBlockGoal.start + tick over RaidFarmGoal.tick (lines 545-580):
     * the walk to the farmland's up-center at the 0.7 goal speed with the
     * 40-tick re-path and the look control, the approach clock counting
     * the walk, and the eat at the standing cell (squared distance to
     * the up-center within 1.0) — the carrots vanish through the
     * manager's intent, the hunger refills, the cooldown re-arms at 10.
     */
    void goalRabbitFarmTick() {
        double tx = rabbitFarmX + 0.5;
        double ty = rabbitFarmY + 1.0;
        double tz = rabbitFarmZ + 0.5;
        double dx = tx - position.x();
        double dy = ty - position.y();
        double dz = tz - position.z();
        double squared = dx * dx + dy * dy + dz * dz;
        if (squared > 1.0) {
            // The approach (GoToBlockGoal.tick's walk arm): the clock
            // counts up, the re-path lands every 40 ticks.
            rabbitFarmReached = false;
            rabbitFarmActiveTicks++;
            headYaw = angleTo(dx, dz); // the look control's 10-degree grip
            mode = Mode.WANDER;
            modeTicks = 0;
            goalWalkSpeed = 0.7 / 0.3;
            if (rabbitFarmActiveTicks % 40 == 0) {
                tickPathFollowing(new Position(tx, ty, tz));
            }
            return;
        }
        // The standing cell (hasReachedTarget): the eat.
        rabbitFarmReached = true;
        rabbitFarmActiveTicks--;
        if (rabbitFarmHasTarget && isRabbitGardenAt(rabbitFarmX, rabbitFarmY, rabbitFarmZ)) {
            pendingRabbitFarmEat = true; // the manager drains the intent
        }
        rabbitFarmHasTarget = false;
        rabbitFarmCooldown = 10;
    }


    /**
     * One queued world sound (a jump's squish, a landing thud, a footstep
     * on stone — the staged-shape the manager's tick drains and
     * broadcasts through the mob-sound listener). Public for the
     * acceptance tests that read the staged pair.
     */
    public record StagedSound(String name, float volume, float pitch) {
    }

    /** The queued jump sound (consumed by the manager's tick). */
    private StagedSound pendingSlimeSound;

    /** The landing sound queued when a hop ends (the size > 2 rule). */
    private StagedSound pendingSlimeLandSound;

    /** The queued footstep (the step-sound walk's crossing). */
    private StagedSound pendingStepSound;

    /** The queued swim splash (the step-sound walk's crossing over water). */
    private StagedSound pendingSwimSound;

    /** @return and clears the queued jump sound (null when none). */
    public StagedSound consumeSlimeSound() {
        StagedSound sound = pendingSlimeSound;
        pendingSlimeSound = null;
        return sound;
    }

    /** @return and clears the queued landing sound (null when none). */
    public StagedSound consumeSlimeLandSound() {
        StagedSound sound = pendingSlimeLandSound;
        pendingSlimeLandSound = null;
        return sound;
    }

    /** @return and clears the queued footstep (null when none). */
    public StagedSound consumeStepSound() {
        StagedSound sound = pendingStepSound;
        pendingStepSound = null;
        return sound;
    }

    /** @return and clears the queued swim splash (null when none). */
    public StagedSound consumeSwimSound() {
        StagedSound sound = pendingSwimSound;
        pendingSwimSound = null;
        return sound;
    }

    /**
     * The slime's mind (the goal family over the engine's own body): the
     * aggressive chase faces the target (the AttackGoal's lookAt + the
     * direction), the idle re-rolls the direction every 40-99 ticks
     * (RandomDirectionGoal lines 428-436), and the big body's contact
     * damage rides the melee pipeline (onPlayerCollision +
     * damageTargetEntity lines 192-204: within {@code 0.6 * size}
     * blocks with a clear line, the damage the size, on the player's own
     * 10-tick hurt-resistance cadence).
     */
    private void tickSlimeMind(Position target, boolean canHunt) {
        slimeAggressive = canHunt && target != null;
        if (slimeAggressive) {
            yaw = angleTo(target.x() - position.x(), target.z() - position.z());
            headYaw = yaw;
        } else if (--slimeDirectionCooldown <= 0) {
            slimeDirectionCooldown = 40 + random.nextInt(60);
            yaw = random.nextInt(360);
            headYaw = yaw;
        }
        mode = slimeAggressive ? Mode.CHASE : Mode.WANDER;
        // The contact damage (damageTargetEntity): only the big body.
        if (slimeContactCooldown > 0) {
            slimeContactCooldown--;
            return;
        }
        double reach = 0.6 * slimeSize;
        if (slimeSize > 1 && target != null
                && position.distanceSquared(target) < reach * reach
                && world.clearLine(position, target)) {
            slimeContactCooldown = HURT_WINDOW_TICKS; // the i-frame cadence
            pendingMeleeAttack = true;
            pendingSlimeAttackSound = true;
        }
    }

    /** Set when the contact damage lands; consumed by the manager. */
    private boolean pendingSlimeAttackSound;

    /** @return and clears the contact-damage flag (the mob.attack sound). */
    public boolean consumeSlimeAttackSound() {
        boolean value = pendingSlimeAttackSound;
        pendingSlimeAttackSound = false;
        return value;
    }

    /** The completed drink's metadata (consumed by the manager's tick). */
    private int completedWitchDrink;

    /** @return the completed drink's metadata (0 = none this tick). */
    public int consumeWitchDrink() {
        int metadata = completedWitchDrink;
        completedWitchDrink = 0;
        return metadata;
    }

    /** The reference's drink choices (WitchEntity.mobTick lines 105-127). */
    static final int WITCH_WATER_BREATHING = 8237;
    static final int WITCH_FIRE_RESISTANCE = 16307;
    static final int WITCH_INSTANT_HEALTH = 16341;
    static final int WITCH_SPEED = 16274;
    /** The potion use duration (PotionItem.getUseDuration): 32 ticks. */
    static final int WITCH_DRINK_DURATION_TICKS = 32;


    /** The hurt window's own cadence (the reference's i-frame length). */
    private static final int HURT_WINDOW_TICKS = 10;

    /**
     * The enderman's random teleport (EndermanEntity.teleport lines
     * 167-174): the +-32-block cube around the body, landing through
     * {@link #teleportTo}.
     */
    public boolean teleportRandomly() {
        double x = position.x() + (random.nextDouble() - 0.5) * 64.0;
        double y = position.y() + (random.nextInt(64) - 32);
        double z = position.z() + (random.nextDouble() - 0.5) * 64.0;
        return teleportTo(x, y, z);
    }

    /**
     * The enderman's blink toward a target (EndermanEntity.teleportTo(Entity)
     * lines 176-184): 16 blocks along the toward-target direction with the
     * +-4-block jitter (the reference's own {@code (random-0.5)*8} x/z and
     * {@code nextInt(16)-8} y bands), landing through {@link #teleportTo}.
     */
    public boolean teleportToward(Position target) {
        double dx = position.x() - target.x();
        double dy = position.y() + type.height / 2.0 - (target.y() + 1.62);
        double dz = position.z() - target.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) {
            return teleportRandomly();
        }
        dx /= length;
        dy /= length;
        dz /= length;
        double reach = 16.0;
        double x = position.x() + (random.nextDouble() - 0.5) * 8.0 - dx * reach;
        double y = position.y() + (random.nextInt(16) - 8) - dy * reach;
        double z = position.z() + (random.nextDouble() - 0.5) * 8.0 - dz * reach;
        return teleportTo(x, y, z);
    }

    /**
     * The enderman's landing walk (EndermanEntity.teleportTo lines 186-238):
     * drop down to the first solid ground, require the body's own clearance
     * (the height's cells clear of solids and fluids), and commit the
     * position only when the landing is clean — otherwise the body stays
     * exactly where it was.
     */
    public boolean teleportTo(double x, double y, double z) {
        int feetY = (int) Math.floor(y);
        // The drop: fall down until the cell below is solid (the reference's
        // blocksMovement walk).
        while (feetY > 0 && !world.isSolid(x, feetY - 0.5, z)) {
            feetY--;
        }
        if (feetY <= 0) {
            return false;
        }
        // The clearance: the body's own cells (the 2.9-tall enderman needs
        // two clear cells above the ground plus most of the third).
        int bodyCells = (int) Math.ceil(type.height);
        for (int cell = 0; cell < bodyCells; cell++) {
            if (world.isSolid(x, feetY + cell + 0.5, z)
                    || world.inFluid(x, feetY + cell + 0.5, z)) {
                return false;
            }
        }
        position = new Position(x, feetY, z);
        velocityX = 0;
        velocityY = 0;
        velocityZ = 0;
        return true;
    }

    /** Day-locked convenience for tests and callers without a clock. */
    public boolean tick() {
        return tick(false);
    }

    /**
     * The reference's ambient-sound play bundle — the three consults
     * {@code playAmbientSound} evaluates, in the reference's own order
     * (MobEntity lines 143-149: the name first, then the volume/pitch pair
     * ONLY when a name exists).
     */
    public record AmbientSound(String name, float volume, float pitch) {
    }

    /**
     * The ambient-sound cadence, 1:1 (the VIII14 row — the reference's
     * {@code MobEntity.baseTick} lines 151-159): a live body rolls
     * {@code random.nextInt(1000) < ambientSoundDelay++} every tick — the
     * delay climbs from its 0 field default, so the first play's hazard
     * grows with age; a hit resets the clock to
     * {@code -getAmbientSoundInterval()} (the negative reset guarantees the
     * per-family silence window) and plays. Call exactly once per tick
     * after {@link #tick()} (the reference's own baseTick position).
     *
     * @return the play bundle when the roll hit and the kind has an idle
     *         voice (the reference's {@code getAmbientSound() != null} gate —
     *         the engine's empty-string idle is the same silence), else null
     */
    public AmbientSound ambientSoundDue() {
        if (dead || random.nextInt(1000) >= ambientSoundDelay++) {
            return null;
        }
        ambientSoundDelay = -ambientSoundInterval();
        String name = effectiveIdleSound();
        if (name.isEmpty()) {
            return null; // playAmbientSound: a null voice never plays
        }
        return new AmbientSound(name, ambientSoundVolume(), ambientSoundPitch());
    }

    /**
     * The per-family silence window — the reference's
     * {@code getAmbientSoundInterval} overrides: the base 80 (MobEntity
     * lines 138-140), the animal band 120 (AnimalEntity lines 138-140 — the
     * tameable wolf/ocelot children with it), the water mob 120
     * (WaterMobEntity), the golem 120 (GolemEntity), the guardian's own 160
     * (GuardianEntity line 197), the horse family's 400 (HorseBaseEntity
     * lines 570-572).
     */
    public int ambientSoundInterval() {
        return switch (type) {
            case PIG, COW, CHICKEN, SHEEP, RABBIT, WOLF, OCELOT, MOOSHROOM,
                    SQUID, IRON_GOLEM, SNOW_GOLEM -> 120;
            case GUARDIAN -> 160;
            case HORSE -> 400;
            default -> 80;
        };
    }

    /**
     * The play volume — the reference's {@code getSoundVolume} overrides:
     * the base 1.0 (LivingEntity lines 1056-1058), the ghast's 10.0 (line
     * 120), the dragon's 5.0 (line 617), the horse's 0.8 (line 572), the
     * cow's 0.4 (line 65 — the mooshroom inherits it), the ocelot/wolf 0.4
     * (OcelotEntity line 134, WolfEntity line 159), the squid's 0.4 (line
     * 64), the bat's 0.1 (line 29), the slime's {@code 0.4 * size} (line
     * 262 — the size-1 body 0.4).
     */
    public float ambientSoundVolume() {
        return switch (type) {
            case GHAST -> 10.0f;
            case ENDER_DRAGON -> 5.0f;
            case HORSE -> 0.8f;
            case COW, MOOSHROOM, OCELOT, WOLF, SQUID -> 0.4f;
            case BAT -> 0.1f;
            case SLIME, MAGMA_CUBE -> 0.4f * slimeSize();
            default -> 1.0f;
        };
    }

    /**
     * The play pitch — the reference's {@code getSoundPitch} (LivingEntity
     * lines 1060-1064): the two-draw jitter {@code (r - r) * 0.2} around
     * 1.5 for a baby, around 1.0 for a grown body.
     */
    public float ambientSoundPitch() {
        float jitter = random.nextFloat() - random.nextFloat();
        return isBaby() ? jitter * 0.2f + 1.5f : jitter * 0.2f + 1.0f;
    }

    /**
     * The villager's trade-arm clock reset — the reference's
     * {@code VillagerEntity.trade} line 600 writes the clock before its
     * "mob.villager.yes" (unconditional), and {@code updateOffer} lines
     * 626-635 resets under its own 20-tick gate.
     */
    public void resetAmbientSoundDelay() {
        ambientSoundDelay = -ambientSoundInterval();
    }

    /**
     * The villager's {@code updateOffer} gate (VillagerEntity line 627):
     * the clock must have climbed past {@code -interval + 20} before the
     * next offer sound may fire (the anti-spam window).
     */
    public boolean ambientOfferSoundGateOpen() {
        return ambientSoundDelay > -ambientSoundInterval() + 20;
    }

    /** @return the current cadence clock (the tests' own read). */
    int ambientSoundDelay() {
        return ambientSoundDelay;
    }

    /**
     * @return the body's idle sound. The kind's own dynamic families
     * (Slice 30c): the wolf's own ladder (WolfEntity.getAmbientSound
     * lines 138-146 — angry growls, a third of the rest whines under a
     * tamed 10-health body or pants otherwise, the rest barks) and the
     * ocelot's tamed family (OcelotEntity.getAmbientSound lines 111-121 —
     * the in-love purr, else a quarter purreow over the meow; the
     * untamed body stays silent). The guardian's own family reads the
     * water state live — the land pair, the elder's own, or the plain
     * water prefix.
     */
    public String effectiveIdleSound() {
        if (type == MobType.GUARDIAN) {
            return guardianSoundKind() + "idle";
        }
        if (type == MobType.ENDERMAN) {
            // EndermanEntity.getAmbientSound lines 241-244: the screaming
            // body's own voice — the stare's angry flag swaps the idle hum
            // for the scream.
            return endermanAngry() ? "mob.endermen.scream" : "mob.endermen.idle";
        }
        if (type == MobType.WOLF) {
            if (wolfAngry()) {
                return "mob.wolf.growl";
            }
            if (random.nextInt(3) == 0) {
                return wolfTamed() && health() < 10.0f
                        ? "mob.wolf.whine" : "mob.wolf.panting";
            }
            return "mob.wolf.bark";
        }
        if (type == MobType.OCELOT) {
            if (!wolfTamed()) {
                return ""; // the untamed body stays silent
            }
            if (isInLove()) {
                return "mob.cat.purr";
            }
            return random.nextInt(4) == 0 ? "mob.cat.purreow" : "mob.cat.meow";
        }
        return type.idleSound;
    }

    /** @return and clears whether a skeleton shot is armed this tick. */
    public boolean consumePendingRangedShot() {
        boolean value = pendingRangedShot;
        pendingRangedShot = false;
        return value;
    }

    /** @return and clears whether a primed creeper should detonate this tick. */
    public boolean consumePendingExplosion() {
        boolean value = pendingExplosion;
        pendingExplosion = false;
        return value;
    }

    /** @return and clears whether a sheep's coat regrew this tick. */
    public boolean consumeRegrown() {
        boolean value = regrownThisTick;
        regrownThisTick = false;
        return value;
    }

    // ------------------------------------------------ mount API (horse/pig)

    /** @return whether this kind accepts a rider (the horse and the saddled pig). */
    public boolean isMountable() {
        return type == MobType.HORSE || type == MobType.PIG;
    }

    /** @return the tamed bit (pigs mount without taming, the vanilla rule). */
    public boolean tamed() {
        return type == MobType.PIG || (horseFlags & HORSE_FLAG_TAMED) != 0;
    }

    /** @return whether the saddle is on (the control gate for both kinds). */
    public boolean saddled() {
        return type == MobType.PIG ? pigSaddled : (horseFlags & HORSE_FLAG_SADDLED) != 0;
    }

    /** @return the raw index-16 horse flag word (the wire writer's view). */
    public int horseFlagsRaw() {
        return horseFlags;
    }

    /** @return whether the rear flag is up (the bucking animation). */
    public boolean rearing() {
        return (horseFlags & HORSE_FLAG_REARING) != 0;
    }

    /** @return the eating flag (the feeding graze flash). */
    public boolean eating() {
        return (horseFlags & HORSE_FLAG_EATING) != 0;
    }

    /** @return the coat variant (color | marking &lt;&lt; 8 — the index-20 int). */
    public int variant() {
        return variant;
    }

    /** @return the armor row (0 none — the index-22 int). */
    public int armorType() {
        return armorType;
    }

    /** @return the taming temper (0-100; the taming odds ride it). */
    public int temper() {
        return temper;
    }

    /** @return the tamer's name (the index-21 owner string, "" until tamed). */
    public String ownerName() {
        return ownerName;
    }

    /**
     * @return the tamer's uuid string (the reference's OwnerUUID row —
     * "" until tamed; the reference's own owner slot carries THIS).
     */
    public String ownerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid == null ? "" : ownerUuid;
    }

    /**
     * The legacy owner NAME row's landing (the pre-1.8 {@code Owner}
     * string — the reference's UserConverter arm feeds the same slot):
     * the name-keyed gates keep working across the old row.
     */
    public void setCustomOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    /**
     * The owner consult (the reference's TameableEntity.isOwner /
     * getOwner: {@code UUID.fromString(owner) == entity.getUuid()}): the
     * uuid arm first — the row's own string — with the engine's name arm
     * as the legacy fallback (the pre-uuid model, the name-keyed gates).
     */
    public boolean isOwner(net.zaminmc.torch.server.player.PlayerSession player) {
        if (player == null) {
            return false;
        }
        if (!ownerUuid.isEmpty()) {
            return ownerUuid.equals(player.uuid().toString());
        }
        return !ownerName.isEmpty() && ownerName.equals(player.name());
    }

    /** The horse's Bred row (the bred marker — the breeding-gate envelope). */
    public boolean horseBred() {
        return horseBred;
    }

    public void setHorseBred(boolean horseBred) {
        this.horseBred = horseBred;
    }

    /** The horse's HasReproduced row (the hasBred marker). */
    public boolean horseHasReproduced() {
        return horseHasReproduced;
    }

    public void setHorseHasReproduced(boolean horseHasReproduced) {
        this.horseHasReproduced = horseHasReproduced;
    }

    /** @return whether the pig carries a saddle (the pig's own index-16 byte). */
    public boolean pigSaddled() {
        return pigSaddled;
    }

    /** @return the rider's engine id, or -1 when the seat is open. */
    public int riderId() {
        return riderId;
    }

    /** @return whether a rider holds the seat. */
    public boolean hasRider() {
        return riderId >= 0;
    }

    /** @return and clears whether the body must throw its rider this tick (buck flow). */
    public boolean consumeBuckThrow() {
        boolean value = pendingBuckThrow;
        pendingBuckThrow = false;
        return value;
    }

    /**
     * A mount attempt on the untamed horse rolls the vanilla temper flow:
     * temper rises, and the odds ride it. A pig or a tamed horse mounts
     * directly. The result tells the manager whether to seat, tame-and-seat,
     * or seat-and-buck.
     */
    public MountAttempt attemptMount(String ownerName, String ownerUuid) {
        if (type == MobType.PIG || tamed()) {
            return MountAttempt.MOUNTED;
        }
        temper = Math.min(100, temper + TEMPER_PER_ATTEMPT);
        if (random.nextInt(100) < temper) {
            // bondWithPlayer (lines 1020-1024): the taming rider's own
            // uuid rides the owner slot.
            tame(ownerName, ownerUuid);
            return MountAttempt.TAMED;
        }
        // The buck: seated, rearing, thrown shortly (the vanilla feel).
        buckTicks = BUCK_THROW_TICKS + random.nextInt(20);
        horseFlags |= HORSE_FLAG_REARING;
        return MountAttempt.BUCKED;
    }

    /** Flips the tame bit and records the tamer (the hearts moment). */
    public void tame(String owner, String ownerUuid) {
        horseFlags |= HORSE_FLAG_TAMED;
        if (owner != null && !owner.isEmpty()) {
            ownerName = owner;
        }
        if (ownerUuid != null && !ownerUuid.isEmpty()) {
            this.ownerUuid = ownerUuid;
        }
    }

    /** Feeding raises the temper and flashes the eating flag (the graze). */
    public void feedTemper(int amount) {
        temper = Math.min(100, temper + amount);
        eatingTicks = 20;
        horseFlags |= HORSE_FLAG_EATING;
    }

    /** The horse's eating graze without a temper change ({@code setEating}). */
    public void flashEating() {
        eatingTicks = 20;
        horseFlags |= HORSE_FLAG_EATING;
    }

    // ------------------------------------------------ breeding + donkey chest

    /** The vanilla love window ({@code AnimalEntity.lovePlayer}: 600 ticks). */
    public static final int LOVE_DURATION_TICKS = 600;
    /** The parent cooldown after a successful breed ({@code setBreedingAge(6000)}). */
    public static final int ADULT_BREED_COOLDOWN_TICKS = 6000;
    /** The child's growing-up debt ({@code setBreedingAge(-24000)}: 20 minutes). */
    public static final int CHILDHOOD_TICKS = -24000;
    /** The ticks a pair stands within 3 blocks before the breed lands (AnimalBreedGoal). */
    public static final int BREED_PROXIMITY_TICKS = 60;
    /** The vanilla findMate scan radius (the grown-8 box). */
    public static final double BREED_MATE_SCAN_RADIUS = 8.0;
    /** The breed distance gate (squaredDistanceTo &lt; 9 — within 3 blocks). */
    public static final double BREED_DISTANCE_SQUARED = 9.0;
    /** The donkey chest's slot count: the vanilla getInventorySize's 17 is
     * saddle (0) + armor (1) + the 15-slot 3x5 chest grid (2-16, HorseMenu). */
    public static final int CHEST_SLOT_COUNT = 15;
    /** The chested horse flag ({@code setHorseFlag(8, hasChest)}). */
    public static final int HORSE_FLAG_CHESTED = 0x08;

    public int breedingAge() {
        return breedingAge;
    }

    public void setBreedingAge(int age) {
        this.breedingAge = age;
    }

    /** Whether the body is still a baby (a negative breeding age). */
    public boolean isBaby() {
        return breedingAge < 0;
    }

    /** The vanilla age tick: negative grows one tick toward 0, positive drains.
     * Crossing 0 raises the grew-up flag (the wire's age byte flips). */
    public void tickBreedingAge() {
        if (breedingAge < 0) {
            breedingAge++;
            if (breedingAge >= 0) {
                breedingAge = 0;
                grewUpThisTick = true;
            }
        } else if (breedingAge > 0) {
            breedingAge--;
        }
    }

    /** @return and clears whether the body became an adult this tick. */
    public boolean consumeGrewUp() {
        boolean value = grewUpThisTick;
        grewUpThisTick = false;
        return value;
    }

    /**
     * Feeding a baby shrinks its childhood ({@code PassiveEntity.growUp}):
     * the vanilla unit is 20 ticks (the wheat arm's j=20 grows 400 ticks),
     * so the age moves by {@code units * 20} and clamps at 0.
     */
    public void growUp(int units) {
        if (breedingAge < 0) {
            breedingAge = Math.min(0, breedingAge + units * 20);
        }
    }

    /** Enters love for the historical 600 ticks ({@code lovePlayer}). */
    public void enterLove(int playerId) {
        this.loveTicks = LOVE_DURATION_TICKS;
        this.lovePlayerId = playerId;
        this.pendingLoveBurst = true; // the doEntityEvent(18) heart burst
    }

    public boolean isInLove() {
        return loveTicks > 0;
    }

    /** @return the remaining love window (the tick-exact state). */
    public int loveTicksLeft() {
        return loveTicks;
    }

    public int lovePlayerId() {
        return lovePlayerId;
    }

    public void resetLove() {
        this.loveTicks = 0;
        this.lovePlayerId = -1;
        this.breedTimer = 0;
    }

    // --- the leash (Slice 11c — MobEntity's own leash arms) ---------------

    /** The player holding this mob's lead, or null (the knot form / free). */
    public net.zaminmc.torch.server.player.PlayerSession leashHolderPlayer() {
        return leashed ? leashHolderPlayer : null;
    }

    /** The knot holding this mob's lead, or null (the player form / free). */
    public net.zaminmc.torch.server.leash.LeashKnot leashHolderKnot() {
        return leashed ? leashHolderKnot : null;
    }

    /** @return whether the mob carries a live lead (isLeashed). */
    public boolean isLeashed() {
        return leashed;
    }

    /** The player's hold (attachLeash): one lead, one holder. */
    public void attachLeashToPlayer(net.zaminmc.torch.server.player.PlayerSession holder) {
        this.leashed = true;
        this.leashHolderPlayer = holder;
        this.leashHolderKnot = null;
    }

    /** The knot's hold (attachLeash): one lead, one holder. */
    public void attachLeashToKnot(net.zaminmc.torch.server.leash.LeashKnot holder) {
        this.leashed = true;
        this.leashHolderKnot = holder;
        this.leashHolderPlayer = null;
    }

    /** The state clear (detachLeash's first arm — the drop stays the system's). */
    public void clearLeash() {
        this.leashed = false;
        this.leashHolderPlayer = null;
        this.leashHolderKnot = null;
    }

    // The readNbt leash stash (Slice 89 — the reference's own leashNbt
    // field): readCustomNbt stores the Leash compound unresolved; the
    // leash tick resolves it later (the knot by position, the holder by
    // uuid) exactly like the reference's updateLeashStatus.
    private java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> leashNbt;

    /** Stashes the unresolved Leash compound (readNbt's own shape). */
    public void stashLeashNbt(java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> rows) {
        this.leashNbt = rows;
    }

    /** Takes the stashed Leash compound (null when none) — the resolver consumes it once. */
    public java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> takeLeashNbt() {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> taken = leashNbt;
        leashNbt = null;
        return taken;
    }

    /**
     * The readNbt leash rows (Slice 89 — the reference's readCustomNbt
     * lines 317-320): the Leashed flag sets outright (a true with no
     * holder and no stash drops at the next tick's holder-gone arm,
     * exactly the reference's shape); a true with a Leash COMPOUND stashes
     * it unresolved.
     */
    public void applyLeashRows(boolean leashed,
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> nbt) {
        this.leashed = leashed;
        if (!leashed) {
            this.leashHolderPlayer = null;
            this.leashHolderKnot = null;
            this.leashNbt = null;
            return;
        }
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> leash =
                net.zaminmc.torch.server.nbt.NbtOps.getCompound(nbt, "Leash");
        if (!leash.isEmpty()) {
            stashLeashNbt(leash);
        }
    }

    // --- the public §27.3 leash family (the LivingEntity handle's reads
    // over the state above — the engine object IS the handle, §27.5) ------

    @Override
    public boolean leashed() {
        return leashed;
    }

    @Override
    public net.zaminmc.torch.entity.LeashHolderKind leashHolderKind() {
        if (!leashed) {
            return net.zaminmc.torch.entity.LeashHolderKind.NONE;
        }
        return leashHolderPlayer != null
                ? net.zaminmc.torch.entity.LeashHolderKind.PLAYER
                : net.zaminmc.torch.entity.LeashHolderKind.KNOT;
    }

    @Override
    public java.util.Optional<java.util.UUID> leashHolderPlayerUuid() {
        net.zaminmc.torch.server.player.PlayerSession holder = leashHolderPlayer();
        return holder == null ? java.util.Optional.empty()
                : java.util.Optional.of(holder.uuid());
    }

    @Override
    public java.util.Optional<net.zaminmc.torch.block.BlockPosition> leashKnotPosition() {
        net.zaminmc.torch.server.leash.LeashKnot knot = leashHolderKnot();
        return knot == null ? java.util.Optional.empty()
                : java.util.Optional.of(knot.fence());
    }

    /** The leash system's detach hook, set at spawn (the public unleash path). */
    private java.util.function.Consumer<Boolean> leashDetacher;

    /** Called by the owning manager at spawn (and by the retro-wiring setter). */
    public void setLeashDetacher(java.util.function.Consumer<Boolean> detacher) {
        this.leashDetacher = detacher;
    }

    @Override
    public void unleash(boolean dropLead) {
        java.util.function.Consumer<Boolean> hook = leashDetacher;
        if (hook != null) {
            hook.accept(dropLead); // the system's walk: the clear + the wire + the drop
        } else {
            // The bare-wiring fallback (a manager without the leash
            // system attached): the state still clears — the reference's
            // own first arm.
            clearLeash();
        }
    }

    /**
     * The leash's navigation arm (PathFinderMobEntity line 96: the
     * {@code getNavigation().moveTo(entity, 1.0)} follow) — the walking
     * target the mob's own path walk consumes.
     */
    public void navigateToward(double x, double y, double z) {
        navigation.moveTo(x, y, z, walkSpeed());
    }

    /**
     * The love clock drain ({@code AnimalEntity.mobTick}): the decrement
     * first, then the every-10-ticks ambient heart particle (the vanilla
     * {@code inLoveTimer % 10 == 0} check runs on the post-decrement value).
     */
    public void tickLove() {
        if (loveTicks > 0) {
            loveTicks--;
            if (loveTicks % 10 == 0) {
                pendingAmbientHearts = true;
            }
            if (loveTicks <= 0) {
                resetLove();
            }
        }
    }

    /** @return and clears whether the love burst (event 18) is due. */
    public boolean consumePendingLoveBurst() {
        boolean value = pendingLoveBurst;
        pendingLoveBurst = false;
        return value;
    }

    /** @return and clears whether the ambient heart particle is due. */
    public boolean consumePendingAmbientHearts() {
        boolean value = pendingAmbientHearts;
        pendingAmbientHearts = false;
        return value;
    }

    /** The breed goal's landing: the mate confirmed at proximity. */
    public void requestBreedWith(MobEntity mate) {
        this.pendingBreedMate = mate;
    }

    /** @return and clears the confirmed mate (the manager executes the breed). */
    public MobEntity consumePendingBreed() {
        MobEntity mate = pendingBreedMate;
        pendingBreedMate = null;
        return mate;
    }

    /** Wires the manager's mate scanner (called once at spawn). */
    public void setBreedFinder(BreedFinder finder) {
        this.breedFinder = finder;
    }

    /** The goal's scan: the nearest legal partner (null when none/finderless). */
    MobEntity findMate() {
        return breedFinder == null ? null : breedFinder.findMate(this);
    }

    /** The mate-proximity clock ({@code AnimalBreedGoal.breedTimer}). */
    public int advanceBreedTimer() {
        return ++breedTimer;
    }

    public void resetBreedTimer() {
        breedTimer = 0;
    }

    /**
     * The vanilla canBreedWith (AnimalEntity + the HorseBaseEntity override):
     * a different body of the same kind, both in love, both adults. The
     * horse family adds {@code canBreed()}'s gates — tamed, unmounted, at
     * full health — and the mule never qualifies (its love never starts,
     * the belt here is the vanilla {@code noLove} rule made explicit).
     */
    public boolean canBreedWith(MobEntity other) {
        if (other == null || other == this || other.type() != this.type()) {
            return false;
        }
        // VIII10 — the villager's own pair (VillagerMatingGoal.canStart
        // line 44 over VillagerEntity's willing model): the FOOD flag
        // stands where the love clock does for every animal; the
        // village's own gate rode the goal's canUse (the mating consult).
        if (type == MobType.VILLAGER) {
            return villagerWilling && other.villagerWilling
                    && breedingAge == 0 && other.breedingAge == 0;
        }
        if (!isInLove() || !other.isInLove()) {
            return false;
        }
        if (breedingAge != 0 || other.breedingAge != 0) {
            return false;
        }
        // WolfEntity.canBreedWith (and OcelotEntity's own — the same shape):
        // both bodies tamed, neither sitting.
        if (isTameableKind()) {
            if (!wolfTamed() || !other.wolfTamed()
                    || wolfSitting() || other.wolfSitting()) {
                return false;
            }
        }
        if (type == MobType.HORSE) {
            if (isMule() || other.isMule()) {
                return false;
            }
            if (!tamed() || !other.tamed()
                    || hasRider() || other.hasRider()) {
                return false;
            }
            if (health < type.maxHealth || other.health < other.type.maxHealth) {
                return false;
            }
        }
        return true;
    }

    public boolean isMule() {
        return type == MobType.HORSE && horseSubtype == HORSE_SUBTYPE_MULE;
    }

    public int horseSubtype() {
        return horseSubtype;
    }

    public void setHorseSubtype(int subtype) {
        this.horseSubtype = subtype;
    }

    // ---------------------------------------- horse spawn attributes (the initialize rolls)
    // (HorseBaseEntity.initialize lines 1278-1284, through getChildHealthBonus
    // line 1386 + getMovementSpeedBonus line 1390)

    /**
     * The reference's registered horse MOVEMENT_SPEED base (initAttributes
     * line 559): the roll's own scale — the rolled band's mean IS this
     * base, so the consult lands it on the engine's own base by the ratio.
     */
    private static final double HORSE_REFERENCE_SPAWN_SPEED = 0.225;

    /**
     * The donkey/mule's spawn speed (initialize line 1283: the flat 0.175
     * on the same 0.225 scale) — the {@code else} arm of initialize's
     * {@code if (i == 0)} speed branch.
     */
    private static final double HORSE_DONKEY_SPAWN_SPEED = 0.175;

    /**
     * The horse's spawn-time MAX_HEALTH ceiling (initialize line 1278 over
     * getChildHealthBonus line 1386: {@code 15 + nextInt(8) + nextInt(9)});
     * 0 = no roll landed (the kind's plain row).
     */
    private float horseSpawnMaxHealth;

    /**
     * The horse's spawn-time speed roll (initialize line 1281 over
     * getMovementSpeedBonus line 1390: {@code (0.45 + 3x nextDouble()*0.3)
     * * 0.25}), on the reference's base scale; NaN = no roll landed.
     */
    private double horseSpeedRoll = Double.NaN;

    /**
     * Lands the rolled MAX_HEALTH ceiling (the spawn-site arm,
     * MobManager.spawnAt's horse roll). The reference sets the
     * attribute's BASE and never touches the current health (the
     * LivingEntity ctor line 136's setHealth(getMaxHealth()) is the last
     * write — a freshly spawned body carries the ctor's full-of-registered
     * -base value above or below the rolled ceiling until the first
     * damage/heal clamps it); the engine keeps the same law.
     */
    public void applyHorseSpawnHealth(float ceiling) {
        if (type != MobType.HORSE) {
            return;
        }
        this.horseSpawnMaxHealth = ceiling;
    }

    /**
     * Lands the rolled speed (the spawn-site arm) on the reference's
     * base scale — {@link #walkSpeed()} consults it against the LIVE
     * subtype (the horse's rolled band or the donkey/mule flat).
     */
    public void rollHorseSpawnSpeed(java.util.Random roll) {
        if (type != MobType.HORSE) {
            return;
        }
        this.horseSpeedRoll = (0.45 + roll.nextDouble() * 0.3
                + roll.nextDouble() * 0.3 + roll.nextDouble() * 0.3) * 0.25;
    }

    /** Sets the coat directly (the group's shared color | the re-rolled marking). */
    public void setHorseVariant(int variant) {
        this.variant = variant;
    }

    /** Sets the body yaw (the spawn walks' setPositionAndAngles). */
    public void setBodyYaw(float yaw) {
        this.yaw = yaw;
    }

    /** @return the body's live yaw (the conversion's carry read). */
    public float bodyYawLive() {
        return yaw;
    }

    /** Whether a chest rides the donkey (flag 0x08, the vanilla rule). */
    public boolean chested() {
        return chested;
    }

    /** Equips or removes the chest ({@code setHasChest} — the flag + the slots). */
    public void setChested(boolean value) {
        this.chested = value;
        if (value) {
            horseFlags |= HORSE_FLAG_CHESTED;
            if (chestSlots == null) {
                chestSlots = new net.zaminmc.torch.item.ItemStack[CHEST_SLOT_COUNT];
                java.util.Arrays.fill(chestSlots, net.zaminmc.torch.item.ItemStack.EMPTY);
            }
        } else {
            horseFlags &= ~HORSE_FLAG_CHESTED;
            chestSlots = null;
        }
    }

    /**
     * The chest grid for the window click engine ({@link WindowClicks} plays
     * its historical cursor semantics straight onto it). Null when no chest.
     */
    public net.zaminmc.torch.item.ItemStack[] chestGrid() {
        return chested ? chestSlots : null;
    }

    /** @return the chest stack in the slot (or empty — the vanilla null). */
    public net.zaminmc.torch.item.ItemStack chestSlot(int index) {
        if (chestSlots == null || index < 0 || index >= CHEST_SLOT_COUNT) {
            return net.zaminmc.torch.item.ItemStack.EMPTY;
        }
        net.zaminmc.torch.item.ItemStack stack = chestSlots[index];
        return stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    /** Puts a stack in the chest slot. */
    public void setChestSlot(int index, net.zaminmc.torch.item.ItemStack stack) {
        if (chestSlots == null) {
            chestSlots = new net.zaminmc.torch.item.ItemStack[CHEST_SLOT_COUNT];
            java.util.Arrays.fill(chestSlots, net.zaminmc.torch.item.ItemStack.EMPTY);
        }
        chestSlots[index] = stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    /**
     * Spills the mount's worn gear and chest contents (the vanilla
     * dropInventoryAndChest on death): saddle, armor, chest, the stacks.
     */
    public java.util.List<net.zaminmc.torch.item.ItemStack> spillMountGear() {
        java.util.List<net.zaminmc.torch.item.ItemStack> dropped = new java.util.ArrayList<>();
        if (type == MobType.PIG) {
            if (pigSaddled) {
                dropped.add(net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.SADDLE, 1));
                pigSaddled = false;
            }
            return dropped;
        }
        if (saddled()) {
            dropped.add(net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.SADDLE, 1));
            horseFlags &= ~HORSE_FLAG_SADDLED;
        }
        if (armorType != HORSE_ARMOR_NONE) {
            dropped.add(switch (armorType) {
                case HORSE_ARMOR_DIAMOND -> net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_HORSE_ARMOR, 1);
                case HORSE_ARMOR_GOLD -> net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.GOLDEN_HORSE_ARMOR, 1);
                default -> net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.IRON_HORSE_ARMOR, 1);
            });
            armorType = HORSE_ARMOR_NONE;
        }
        if (chested) {
            dropped.add(net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.CHEST, 1));
            if (chestSlots != null) {
                for (net.zaminmc.torch.item.ItemStack stack : chestSlots) {
                    if (stack != null && stack.type() != null) {
                        dropped.add(stack);
                    }
                }
            }
            setChested(false);
        }
        return dropped;
    }

    /** Equips the saddle (horse flags or the pig's own byte). */
    public void applySaddle() {
        if (type == MobType.PIG) {
            pigSaddled = true;
        } else {
            horseFlags |= HORSE_FLAG_SADDLED;
        }
    }

    /** Equips a horse armor row (the index-22 int, tamed horses only). */
    public void applyArmor(int type) {
        this.armorType = type;
    }

    /** Seats a rider (the engine id; inputs zeroed — the stale-rein guard). */
    public void setRider(int engineId) {
        this.riderId = engineId;
        steerSideways = 0;
        steerForward = 0;
        steerJump = false;
    }

    /** Opens the seat (the dismount; the mount wanders on). */
    public void clearRider() {
        this.riderId = -1;
        steerSideways = 0;
        steerForward = 0;
        steerJump = false;
    }

    /** The rider's rein input this tick (sideways turns, forward drives, jump hops). */
    public void steer(float sideways, float forward, boolean jump) {
        this.steerSideways = sideways;
        this.steerForward = forward;
        this.steerJump = jump;
    }

    /** @return the seat height above the mount's feet (the rider's body anchor). */
    public double seatHeight() {
        return type == MobType.PIG ? PIG_SEAT_HEIGHT : HORSE_SEAT_HEIGHT;
    }

    // ------------------------------------------------ villager API (the trade slice)

    /** @return the villager's profession (the index-16 Int, 0-4). */
    public int profession() {
        return profession;
    }

    // ------------------------------------- zombie-villager API (the conversion slice)

    /** @return whether the zombie wears the villager skin (the synced slot 13's byte). */
    public boolean zombieVillager() {
        return zombieVillager;
    }

    /**
     * setType (lines 154-156): the villager skin flag — the synced slot
     * 13's byte ({@code isVillager() ? 1 : 0}); the infection arm and the
     * 5% natural-spawn Data roll are the only setters.
     */
    public void setZombieVillager(boolean villager) {
        this.zombieVillager = villager;
    }

    /** @return whether the cure's countdown runs (isConverting — the synced slot 14's byte). */
    public boolean zombieConverting() {
        return ticksUntilConversion > -1;
    }

    /** @return the conversion countdown's remaining ticks (-1 when not converting). */
    public int zombieConversionTicks() {
        return ticksUntilConversion;
    }

    /**
     * setConversionTime (line 482): arms the cure's countdown (a negative
     * time cancels — readNbt's own {@code -1} row, Slice 89). The
     * Weakness removal, the Strength pair, and the entity-event 16 all
     * ride the manager's own cure walk (the reference fires them around
     * this very call).
     */
    public void setZombieConversionTime(int time) {
        this.ticksUntilConversion = Math.max(-1, time);
    }

    /**
     * tick's conversion walk (lines 236-243): the countdown advances every
     * tick by {@link #zombieConversionDecrement()}; the completing tick
     * hands the conversion to the manager (the spawn/remove pair is the
     * manager's own — convertToVillager).
     */
    private void tickZombieConversion() {
        ticksUntilConversion -= zombieConversionDecrement();
        if (ticksUntilConversion <= 0) {
            ticksUntilConversion = 0;
            pendingZombieConversionComplete = true;
        }
    }

    /**
     * getTicksUntilConversionDecrement (lines 540-563): normally one; a
     * 1%-per-tick roll scans the 8-cube around the body for iron bars and
     * beds — every found cell (up to 14 counted) adds a 30% chance of one
     * more decrement, the famous iron-bars cure accelerator.
     */
    private int zombieConversionDecrement() {
        int decrement = 1;
        if (random.nextFloat() < 0.01F) {
            int found = 0;
            int baseX = (int) position.x();
            int baseY = (int) position.y();
            int baseZ = (int) position.z();
            for (int x = baseX - 4; x < baseX + 4 && found < 14; x++) {
                for (int y = baseY - 4; y < baseY + 4 && found < 14; y++) {
                    for (int z = baseZ - 4; z < baseZ + 4 && found < 14; z++) {
                        if (world.isIronBarsOrBed(x, y, z)) {
                            if (random.nextFloat() < 0.3F) {
                                decrement++;
                            }
                            found++;
                        }
                    }
                }
            }
        }
        return decrement;
    }

    /** @return whether the conversion completed this tick (the manager consumes). */
    boolean consumeZombieConversionComplete() {
        if (pendingZombieConversionComplete) {
            pendingZombieConversionComplete = false;
            return true;
        }
        return false;
    }

    // ------------------------------------- zombie attributes (VIII9 — the door-break goal + the reinforcement chance)

    /**
     * canBreakDoors (lines 54/108-121): the goal-arming flag initialize
     * rolls ({@code nextFloat() < f * 0.1F}) and the leader bonus forces
     * on; the engine's BREAK_DOOR goal reads it as its own gate (the
     * reference's setCanBreakDoors adds/removes the goal from the
     * selector — the catalog's kind-gated canUse carries the same law).
     */
    private boolean zombieCanBreakDoors;

    /**
     * BreakDoorGoal's breakProgress (line 9): the 240-tick hardness walk
     * (12 seconds at the door, the block-damage stages at
     * {@code progress / 240 * 10}).
     */
    private int zombieBreakDoorProgress;
    /**
     * BreakDoorGoal's previousBreakProgress (line 10): the last stage the
     * wire saw ({@code updateBlockMiningProgress} fires on change alone),
     * -1 before the first publication.
     */
    private int zombieBreakDoorLastStage = -1;
    /** AbstractDoorInteractGoal's doorPosition — the UPPER door half's cell. */
    private int zombieBreakDoorX;
    private int zombieBreakDoorY;
    private int zombieBreakDoorZ;
    /** Whether the walk owns a door cell this tick (the canStart capture). */
    private boolean zombieDoorBreakArmed;

    /**
     * The pending Block Break Animation (0x28) stage: -2 nothing, -1 the
     * clear (the goal's stop / the completion), 0-9 the walk's own stage.
     * The manager drains it into the raw-id progress fan.
     */
    private int pendingZombieDoorStage = -2;
    /** The pending 1-in-20 knock sound (the reference's doEvent 1010). */
    private boolean pendingZombieDoorKnock;
    /**
     * The pending completion (BreakDoorGoal.tick line 53: progress 240 on
     * HARD): the door cell's removal + the 1012/2001 pair land through
     * the manager's listener bridge.
     */
    private boolean pendingZombieDoorBroken;

    /** @return whether the body's door-break goal is armed (canBreakDoors). */
    public boolean zombieCanBreakDoors() {
        return zombieCanBreakDoors;
    }

    /**
     * setCanBreakDoors (lines 112-121): the flag alone in the engine — the
     * reference's goal-selector add/remove rides the catalog's own
     * kind-gated canUse read of the flag (MobGoals.BREAK_DOOR). The flag
     * never arms on a non-zombie body.
     */
    public void setZombieCanBreakDoors(boolean canBreakDoors) {
        this.zombieCanBreakDoors = canBreakDoors && type == MobType.ZOMBIE;
    }

    /**
     * AbstractDoorInteractGoal's getDoorBlock (lines 77-80) +
     * BreakDoorGoal's own gates (canStart line 18): whether the cell
     * carries a CLOSED WOODEN door half. The iron family never arms the
     * goal (isDoor answers the wood families alone); the OPEN bit clears
     * the goal (the reference's {@code !DoorBlock.isOpen(world, doorPos)}
     * — the engine's meta band carries the same bit 4 on both halves).
     */
    private boolean zombieBreakableDoorAt(int x, int y, int z) {
        var id = world.blockIdAt(x, y, z);
        if (id == null) {
            return false;
        }
        int meta = net.zaminmc.torch.server.block.WoodDoorFamily.metaOf(id);
        if (meta < 0 || (meta & 4) != 0) {
            return false; // not a door half, or the OPEN bit
        }
        // getDoorBlock's own material gate (line 79): the iron family
        // never arms the walk.
        String family = net.zaminmc.torch.server.block.WoodDoorFamily.familyOf(id);
        return family != null
                && !net.zaminmc.torch.server.block.WoodDoorFamily.isIron(family);
    }

    /**
     * AbstractDoorInteractGoal.canStart (lines 28-53) AT THE CLIP SITE —
     * the named adaptation: the reference's canStart reads
     * {@code collidingHorizontally} + the live path's node walk inside the
     * goal selector; the engine's swept-clip detour would clear the path
     * before the selector ever runs, so the clip site plays the
     * reference's reads at the moment they are true — the pressed-against
     * capture, the path nodes [0, currentIndex+2) with the
     * {@code (node.x, node.y + 1, node.z)} door cell inside the squared
     * 2.25 XZ band, the fallback cell above the body — and arms the walk.
     * The mobGriefing gate is BreakDoorGoal.canStart's own (line 18).
     *
     * @return whether the door-break owns the body this tick (the detour
     *         stands down; the path stays live).
     */
    boolean armZombieDoorBreak() {
        if (type != MobType.ZOMBIE || !zombieCanBreakDoors || !world.mobGriefing()) {
            return false;
        }
        var path = navigation.getPath();
        if (path != null && !path.isDone()) {
            int nodes = Math.min(path.getCurrentIndex() + 2, path.length());
            for (int i = 0; i < nodes; i++) {
                var node = path.getNode(i);
                int doorX = node.x;
                int doorY = node.y + 1; // the reference's doorPosition: node.y + 1
                int doorZ = node.z;
                double dx = doorX - position.x();
                double dz = doorZ - position.z();
                if (dx * dx + dz * dz <= 2.25
                        && zombieBreakableDoorAt(doorX, doorY, doorZ)) {
                    zombieBreakDoorX = doorX;
                    zombieBreakDoorY = doorY;
                    zombieBreakDoorZ = doorZ;
                    zombieDoorBreakArmed = true;
                    return true;
                }
            }
        }
        // The reference's fallback (lines 47-49): the cell above the
        // body's own feet — a body standing inside a door cell.
        int floorX = (int) Math.floor(position.x());
        int floorY = (int) Math.floor(position.y());
        int floorZ = (int) Math.floor(position.z());
        if (zombieBreakableDoorAt(floorX, floorY + 1, floorZ)) {
            zombieBreakDoorX = floorX;
            zombieBreakDoorY = floorY + 1;
            zombieBreakDoorZ = floorZ;
            zombieDoorBreakArmed = true;
            return true;
        }
        return false;
    }

    /**
     * BREAK_DOOR's canUse: the sticky walk (shouldContinue lines 28-31 —
     * the clock inside 240, the door still a closed wooden half, the body
     * within the squared-4 band of the cell) over the armed capture. A
     * live walk that fails any gate ENDS here — the stop() shape (line
     * 34-37): the {@code updateBlockMiningProgress(-1)} clear.
     */
    boolean goalZombieBreakDoorCanUse() {
        if (!zombieDoorBreakArmed) {
            return false;
        }
        double dx = zombieBreakDoorX - position.x();
        double dy = zombieBreakDoorY - position.y();
        double dz = zombieBreakDoorZ - position.z();
        boolean alive = zombieBreakDoorProgress <= 240
                && zombieBreakableDoorAt(zombieBreakDoorX, zombieBreakDoorY, zombieBreakDoorZ)
                && (dx * dx + dy * dy + dz * dz) < 4.0;
        if (!alive && zombieBreakDoorProgress > 0) {
            // stop(): the walk ends — the wire's stage clears.
            zombieBreakDoorProgress = 0;
            zombieDoorBreakArmed = false;
            zombieBreakDoorLastStage = -1;
            pendingZombieDoorStage = -1;
        }
        return alive;
    }

    /**
     * BREAK_DOOR's tick (lines 40-57 1:1): the 1-in-20 knock (doEvent
     * 1010 at the door cell), the progress clock, the stage publication
     * on change ({@code progress / 240 * 10} as the 0-9 band the client
     * cracks at), and the completion — progress 240 with the world on
     * HARD alone (line 53) removes the door cell and answers the 1012
     * (the door's own break sound) + 2001 (the block-dust burst) pair.
     */
    void goalZombieBreakDoorTick() {
        if (random.nextInt(20) == 0) {
            pendingZombieDoorKnock = true;
        }
        zombieBreakDoorProgress++;
        int stage = (int) (zombieBreakDoorProgress / 240.0F * 10.0F);
        if (stage != zombieBreakDoorLastStage) {
            pendingZombieDoorStage = stage;
            zombieBreakDoorLastStage = stage;
        }
        if (zombieBreakDoorProgress == 240 && world.difficultyHard()) {
            // The completion (lines 53-57): the removal + the 1012/2001
            // pair land through the manager's listener bridge (the world
            // write is the engine's, never the body's own). The stage-10
            // publication above stands as the wire's last word — the
            // reference's own order (the break sends no extra clear).
            pendingZombieDoorBroken = true;
            zombieDoorBreakArmed = false;
        }
    }

    /** @return and clears the pending 0x28 stage (-1 the clear; -2 none). */
    int consumeZombieDoorStage() {
        int stage = pendingZombieDoorStage;
        pendingZombieDoorStage = -2;
        return stage;
    }

    /** @return and clears the pending 1-in-20 knock sound. */
    boolean consumeZombieDoorKnock() {
        boolean knock = pendingZombieDoorKnock;
        pendingZombieDoorKnock = false;
        return knock;
    }

    /** @return and clears the pending door completion (the 240-on-HARD tick). */
    boolean consumeZombieDoorBroken() {
        boolean broken = pendingZombieDoorBroken;
        pendingZombieDoorBroken = false;
        if (broken) {
            zombieBreakDoorProgress = 0;
            zombieBreakDoorLastStage = -1;
        }
        return broken;
    }

    /** @return the door cell the walk owns (the completion's write site). */
    int zombieBreakDoorX() {
        return zombieBreakDoorX;
    }

    int zombieBreakDoorY() {
        return zombieBreakDoorY;
    }

    int zombieBreakDoorZ() {
        return zombieBreakDoorZ;
    }

    /**
     * The zombie's spawnReinforcements attribute (lines 48-49/87): the
     * base is the body's own {@code nextDouble() * 0.1} roll at the
     * attribute registration, the caller/callee charges each subtract
     * 0.05 (takeDamage lines 220-222), and the leader bonus adds
     * {@code nextDouble() * 0.25 + 0.5} (initialize line 448) — the
     * additive op-0 band over the 0..1 range.
     */
    private double zombieReinforcementChance;

    /** @return the reinforcement chance attribute's live value (clamped 0..1). */
    public double zombieReinforcementChance() {
        return Math.max(0.0, Math.min(zombieReinforcementChance, 1.0));
    }

    /**
     * addModifier over the reinforcement attribute (the caller/callee
     * charges + the leader bonus — all op-0 adds).
     */
    public void addZombieReinforcementModifier(double amount) {
        if (type == MobType.ZOMBIE) {
            zombieReinforcementChance += amount;
        }
    }

    /** initAttributes' own base roll (line 87): {@code nextDouble() * 0.1}. */
    void rollZombieReinforcementBase(java.util.Random random) {
        if (type == MobType.ZOMBIE) {
            zombieReinforcementChance = random.nextDouble() * 0.1F;
        }
    }

    /**
     * The leader's extra-health row (initialize line 449 — MAX_HEALTH's
     * own op-2 multiply {@code 20 * (1 + nextDouble()*3 + 1)}, the
     * 40..100 band the famous leader zombies carry) as the per-body
     * override; the health fills to the new ceiling exactly as the
     * attribute walk leaves the spawned body.
     */
    public void applyZombieLeaderHealth(double multiplierTerm) {
        if (type != MobType.ZOMBIE) {
            return;
        }
        double max = type.maxHealth * (1.0 + multiplierTerm);
        zombieLeaderMaxHealth = (float) max;
        health = (float) max;
    }

    /** The leader's own health ceiling (0 = the kind's plain row). */
    private float zombieLeaderMaxHealth;

    /**
     * A forced player hunt target (the reinforcement callee's own
     * setAttackTarget — takeDamage line 218: the callee chases the
     * body the caller was fighting, wherever it stands, past the plain
     * nearest-player band the unprompted hunt reads). The id rides the
     * engine's shared wire space; the hunt resolves the position through
     * the wither's quarry consult.
     */
    private int forcedHuntPlayerId = -1;

    /** Sets the forced player hunt (the reinforcement callee's target). */
    public void setForcedHuntPlayer(int engineId) {
        this.forcedHuntPlayerId = engineId;
    }

    /**
     * @return the forced hunt's live position, or null when none is set
     *         (or the body is gone) — the hunt's target read.
     */
    Position forcedHuntPosition() {
        if (forcedHuntPlayerId < 0) {
            return null;
        }
        return world.entityPositionById(forcedHuntPlayerId);
    }


    /** @return the villager's career (0 until the first open rolls it). */
    public int villagerCareer() {
        return villagerCareer;
    }

    /** @return the career's level (0 until the first open; grows per level-up). */
    public int villagerCareerLevel() {
        return villagerCareerLevel;
    }

    /**
     * getOffers (lines 633-639): the first consult rolls the career and its
     * opening level — the lazy stock the reference's {@code traderOffers ==
     * null} gate carries.
     */
    public TradeOffer[] offers() {
        if (villagerStock.isEmpty() && type == MobType.VILLAGER) {
            villagerLevelUp();
        }
        return villagerStock.toArray(new TradeOffer[0]);
    }

    /**
     * levelUp (lines 650-673): the first walk rolls the career
     * ({@code nextInt(careers) + 1}) at level 1; every later walk advances
     * the level and appends that level's rolled rows to the standing stock.
     */
    void villagerLevelUp() {
        if (villagerCareer != 0 && villagerCareerLevel != 0) {
            villagerCareerLevel++;
        } else {
            villagerCareer = random.nextInt(VillagerTrades.careerCount(profession)) + 1;
            villagerCareerLevel = 1;
        }
        for (VillagerTrades.TradeSource source : VillagerTrades.levelRows(
                profession, villagerCareer, villagerCareerLevel)) {
            TradeOffer offer = source.provide(random);
            villagerStock.add(offer);
            offerUses = java.util.Arrays.copyOf(offerUses, offerUses.length + 1);
            offerMaxUses = java.util.Arrays.copyOf(offerMaxUses, offerMaxUses.length + 1);
            offerMaxUses[offerMaxUses.length - 1] = offer.maxUses();
        }
    }

    /** @return the use counter of the offer at the index. */
    public int offerUses(int index) {
        if (index < 0 || index >= offerUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        return offerUses[index];
    }

    /** @return the live use budget of the offer (the level-up's extension included). */
    public int offerMaxUses(int index) {
        if (index < 0 || index >= offerMaxUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        return offerMaxUses[index];
    }

    /** @return whether the offer still trades (the use budget not spent out). */
    public boolean offerAvailable(int index) {
        if (index < 0 || index >= offerUses.length) {
            return false;
        }
        return offerUses[index] < offerMaxUses[index];
    }

    /**
     * The trade's own bookkeeping (VillagerEntity.trade lines 604-633): the
     * use charges, and on the first use or the 1-in-5 roll the 40-tick
     * level-up countdown arms with the flag; the emerald payments absorb
     * into the riches.
     *
     * @return the XP the trade pays out ({@code 3 + nextInt(4)}, the
     * level-up arm's +5 included) — the caller spawns the orb.
     */
    public int villagerTrade(int index) {
        return villagerTrade(index, null);
    }

    /**
     * The trade with the customer's NAME (VIII10 — trade lines 607-611:
     * the level-up arm records the last successful customer for the
     * village's +1 reputation when the level-up walk lands).
     */
    public int villagerTrade(int index, String customerName) {
        if (index < 0 || index >= offerUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        offerUses[index]++; // offer.use()
        int xp = 3 + random.nextInt(4);
        TradeOffer offer = villagerStock.get(index);
        if (offerUses[index] == 1 || random.nextInt(5) == 0) {
            villagerLevelUpCountdown = 40;
            villagerLevelUp = true;
            villagerWilling = true;
            // trade lines 607-611: the level-up remembers the customer.
            villagerLastTradeCustomer = customerName != null ? customerName : "";
            xp += 5;
        }
        if (offer.buy1().type() == net.zaminmc.torch.server.item.BuiltinItems.EMERALD) {
            villagerRiches += offer.buy1().count();
        }
        return xp;
    }

    /** The reference's lastSuccessfulTradeCustomer (the level-up's +1 read). */
    private String villagerLastTradeCustomer;

    /** @return the last successful trade's customer ("" when none named). */
    public String villagerLastTradeCustomer() {
        return villagerLastTradeCustomer != null ? villagerLastTradeCustomer : "";
    }

    /** Charges one use off the offer (the execution's bookkeeping). */
    public void chargeOfferUse(int index) {
        if (index < 0 || index >= offerUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        offerUses[index]++;
    }

    /** @return the villager's riches (the absorbed emerald payments). */
    public int villagerRiches() {
        return villagerRiches;
    }

    /**
     * The trade state's restore (Slice 25c — readCustomNbt lines 445-448:
     * Career/CareerLevel/Riches land exactly as saved; the career's stock
     * re-rolls lazily on the first open, the reference's own shape).
     * Package-private: the manager's restore walk is the only caller.
     */
    void restoreVillagerTradeState(int career, int careerLevel, int riches) {
        this.villagerCareer = career;
        this.villagerCareerLevel = careerLevel;
        this.villagerRiches = Math.max(0, riches);
    }

    /** @return whether the villager is willing to mate (the food arm's flag). */
    public boolean villagerWilling() {
        return villagerWilling;
    }

    // ------------------------------------- the villager's mating gates (VIII10)

    /**
     * The villager's own food inventory (VillagerEntity line 82 — the
     * 8-slot SimpleInventory the pickup walk fills): the willing model's
     * whole stock.
     */
    private final net.zaminmc.torch.item.ItemStack[] villagerFood =
            new net.zaminmc.torch.item.ItemStack[8];

    /**
     * The mating gate consult (the manager wires it — the nearest
     * village's canMate + the pop/doors band, VillagerMatingGoal
     * .checkVillageValidity lines 83-90). Null until wired: a stub world
     * carries no village, the gate never opens.
     */
    private java.util.function.BooleanSupplier villagerMatingGate;

    /** Sets the mating gate consult (the manager's own wiring). */
    public void setVillagerMatingGate(java.util.function.BooleanSupplier gate) {
        this.villagerMatingGate = gate;
    }

    /**
     * isFood (VillagerEntity lines 834-836): the five-item family the
     * villager stocks — bread, potatoes, carrots, wheat, wheat seeds.
     */
    public static boolean villagerFoodItem(net.zaminmc.torch.item.ItemType item) {
        String value = item.identifier().value();
        return value.equals("bread") || value.equals("potato")
                || value.equals("carrot") || value.equals("wheat")
                || value.equals("wheat_seeds");
    }

    /**
     * hasFoodPoints (lines 851-870 1:1): whether any slot carries a
     * breeding unit's multiple — 3 bread, 12 potatoes or carrots, the
     * farmer's own 9 wheat — times the minimum.
     */
    private boolean villagerHasFoodPoints(int min) {
        boolean farmer = profession == 0;
        for (net.zaminmc.torch.item.ItemStack stack : villagerFood) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String value = stack.type().identifier().value();
            if (value.equals("bread") && stack.count() >= 3 * min
                    || value.equals("potato") && stack.count() >= 12 * min
                    || value.equals("carrot") && stack.count() >= 12 * min) {
                return true;
            }
            if (farmer && value.equals("wheat") && stack.count() >= 9 * min) {
                return true;
            }
        }
        return false;
    }

    /** hasExcessFood (lines 838-840): the 1-unit band. */
    public boolean villagerHasExcessFood() {
        return villagerHasFoodPoints(1);
    }

    /**
     * The pickup's villager arm (updateInventory lines 821-832): the food
     * family lands in the food slots — a matching stack grows first
     * (SimpleInventory's addItem merge), an empty slot takes the stack
     * whole, and a full inventory leaves the item standing.
     *
     * @return the remainder stack (null when the whole stack landed).
     */
    public net.zaminmc.torch.item.ItemStack villagerAcceptFood(
            net.zaminmc.torch.item.ItemStack stack) {
        if (!villagerFoodItem(stack.type())) {
            return stack;
        }
        // The merge pass: a same-kind stack with room.
        for (int i = 0; i < villagerFood.length; i++) {
            net.zaminmc.torch.item.ItemStack slot = villagerFood[i];
            if (slot != null && !slot.isEmpty()
                    && slot.type().equals(stack.type())
                    && slot.count() < 64) {
                int room = 64 - slot.count();
                int move = Math.min(room, stack.count());
                villagerFood[i] = slot.withCount(slot.count() + move);
                int left = stack.count() - move;
                return left > 0 ? stack.withCount(left) : null;
            }
        }
        // The empty-slot pass.
        for (int i = 0; i < villagerFood.length; i++) {
            if (villagerFood[i] == null || villagerFood[i].isEmpty()) {
                villagerFood[i] = stack;
                return null;
            }
        }
        return stack; // no room: the item stays
    }

    /**
     * consumeAvailableFood (VillagerEntity lines 566-591 1:1): the mating
     * attempt's own walk — a not-yet-willing body with excess food eats
     * the breeding unit (3 bread or 12 potatoes/carrots), the willing
     * arms, and the heart burst (doEntityEvent 18) fires. The
     * {@code updateFirst} false form is the shouldContinue read: the
     * plain willing answer, no consume.
     *
     * @return the willing flag.
     */
    public boolean villagerConsumeAvailableFood(boolean updateFirst) {
        if (!villagerWilling && updateFirst && villagerHasExcessFood()) {
            for (int i = 0; i < villagerFood.length; i++) {
                net.zaminmc.torch.item.ItemStack stack = villagerFood[i];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String value = stack.type().identifier().value();
                int eat = 0;
                if (value.equals("bread") && stack.count() >= 3) {
                    eat = 3;
                } else if ((value.equals("potato") || value.equals("carrot"))
                        && stack.count() >= 12) {
                    eat = 12;
                }
                if (eat > 0) {
                    int left = stack.count() - eat;
                    villagerFood[i] = left > 0 ? stack.withCount(left) : null;
                    pendingLoveBurst = true; // doEntityEvent(18) — the heart burst
                    villagerWilling = true;
                    break;
                }
            }
        }
        return villagerWilling;
    }

    /** setWilling (line 593): the breed's own clear + the trade's arm. */
    public void setVillagerWilling(boolean willing) {
        this.villagerWilling = willing;
    }

    /**
     * The villager's food slots (the persistence read + the tests' own
     * inspect).
     */
    public net.zaminmc.torch.item.ItemStack[] villagerFoodSlots() {
        return villagerFood;
    }

    /**
     * The BREED goal's villager arm (VillagerMatingGoal.canStart lines
     * 23-48 1:1): the 1-in-500 cadence, the adult gate, the village
     * consult (the manager's mating gate — the nearest village within
     * range 0 + the canMate/pop-doors validity), the food consume (the
     * willing arms HERE, exactly the reference's own walk), and the
     * mate scan (the nearest willing villager inside the grown-8 box).
     */
    boolean goalVillagerMatingCanUse() {
        if (type != MobType.VILLAGER || breedingAge() != 0) {
            return false;
        }
        if (random.nextInt(500) != 0) {
            return false;
        }
        java.util.function.BooleanSupplier gate = villagerMatingGate;
        if (gate == null || !gate.getAsBoolean()) {
            return false; // no village, or the census gate refuses
        }
        if (!villagerConsumeAvailableFood(true)) {
            return false;
        }
        return findMate() != null;
    }

    /**
     * The mating walk's own cadence (mobAiTick lines 353-368): the
     * 70 + nextInt(50) window — at zero the villager marks its cell for
     * the door census (the manager's village scan consults answer).
     *
     * @return whether the scan tick arrived.
     */
    boolean tickVillagerScanCooldown(java.util.Random random) {
        if (--villagerScanCooldown <= 0) {
            villagerScanCooldown = 70 + random.nextInt(50);
            return true;
        }
        return false;
    }

    private int villagerScanCooldown;

    /**
     * The cached village seat (PathFinderMobEntity.pos + villageRadius,
     * lines 44-53): the villager's scan arm writes the center/radius of
     * its own village; the radius -1 sentinel marks "no cache". The
     * village-return walk (WanderThroughVillageGoal) reads it — the
     * cache-set-AND-outside pair is the goal's own gate. The guardian's
     * self-pin (GuardianEntity.mobAiTick line 377) rides the same seat.
     */
    private int villageCenterX;
    private int villageCenterY;
    private int villageCenterZ;
    private float villageRadius = -1.0f;

    /** setVillagePosAndRadius (line 50): the scan arm's write. */
    void setVillageCache(int x, int y, int z, float radius) {
        this.villageCenterX = x;
        this.villageCenterY = y;
        this.villageCenterZ = z;
        this.villageRadius = radius;
    }

    /** resetVillageRadius (line 63): the scan's no-village arm. */
    void resetVillageCache() {
        this.villageRadius = -1.0f;
    }

    /** @return whether the cache is set (the reference's inVillage()). */
    boolean villageCacheSet() {
        return this.villageRadius != -1.0f;
    }

    /** @return whether the village-return walk owns the stroll band (the test read). */
    boolean villageReturnWalking() {
        return villageReturnActive;
    }

    /** The candidate pick, exposed for the walk's own pin test. */
    Position pickVillageReturnTargetForTest() {
        return pickVillageReturnTarget((long) Math.floor(position.x()),
                (long) Math.floor(position.y()), (long) Math.floor(position.z()));
    }

    /**
     * The village-return walk's live target (WanderThroughVillageGoal's
     * targetX/Y/Z fields) — the candidate pick's winner, held for the
     * walk's whole life (the reference's start() navigates to it once;
     * shouldContinue runs the path to its end).
     */
    private Position villageReturnTarget;

    /** Whether the village-return walk owns the stroll band this tick. */
    private boolean villageReturnActive;

    // ----------------------------- the stroll family's favor pick (Slice TF-6)

    /**
     * The stroll's picked target (WanderAroundGoal's targetX/Y/Z fields,
     * set once at canStart's TargetFinder pass, held for the walk's
     * whole life — the reference's start() navigates to it once and
     * shouldContinue runs the path to its end).
     */
    private Position strollTarget;

    /**
     * Whether the stroll's own navigation walk owns the WANDER band this
     * tick — the flag that separates the stroll's path walk (this lane)
     * from the historical heading walk the other minds' WANDER writes
     * (the skeleton's retreat, the slime's re-assertions) still carry.
     */
    private boolean strollActive;

    /** The stroll's horizontal candidate band (TargetFinder's 10). */
    private static final int STROLL_RANGE = 10;

    /** The stroll's vertical candidate band (TargetFinder's 7). */
    private static final int STROLL_VERTICAL = 7;

    /** The stroll family's own interval (WanderAroundGoal line 23's 120). */
    private static final int STROLL_INTERVAL = 120;

    /** The silverfish's own interval (InfestStoneGoal line 137's 10). */
    private static final int SILVERFISH_STROLL_INTERVAL = 10;

    // ------------------------------------- the tameable family (Slice TF-1)

    /**
     * Whether the owner-follow window owns the band this tick
     * (FollowOwnerGoal's live state — the window between start() and
     * stop(), held so shouldContinue's own law runs while it lives).
     */
    private boolean followOwnerActive;

    /** The follow's re-path clock ({@code updateTimer}, 10 between paths). */
    private int followOwnerTimer;

    /**
     * Whether the perch walk owns the band this tick
     * (OcelotSitOnBlockGoal's live window over GoToBlockGoal's own
     * activeTicks/maxActiveTicks pair).
     */
    private boolean sitOnBlockHasTarget;

    /** The perch scan's cooldown ({@code GoToBlockGoal.cooldown}). */
    private int sitOnBlockCooldown;

    /** The perch cell ({@code GoToBlockGoal.target}). */
    private int sitOnBlockX;
    private int sitOnBlockY;
    private int sitOnBlockZ;

    /** The perch walk's approach clock ({@code activeTicks}, down at the perch). */
    private int sitOnBlockActiveTicks;

    /** The perch window's own bound ({@code maxActiveTicks}). */
    private int sitOnBlockMaxTicks;

    /** Whether the body stands at the perch ({@code reachedTarget}). */
    private boolean sitOnBlockReached;

    /**
     * The pounce's airborne window (PounceAtTargetGoal.shouldContinue's
     * own {@code !onGround} — true from the leap until the body lands).
     */
    private boolean pounceAirborne;

    /** The BlockPos squared distance (the reference's cell-to-cell reads). */
    private static long cellSquaredDistance(long ax, long ay, long az,
                                            long bx, long by, long bz) {
        long dx = ax - bx;
        long dy = ay - by;
        long dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * setConvertedZombie (VillagerEntity lines 784-786 + the mobAiTick read
     * lines 363-366): the cured-villager marker — the next village-scan
     * cadence pays the WHOLE village +5 reputation once (the reference's
     * updateAllReputations(5) over the villager's own village).
     */
    private boolean villagerConvertedZombie;

    /**
     * setConvertedZombie (line 784): the conversion walk's own marker —
     * the manager arms it when the cured villager lands.
     */
    public void setVillagerConvertedZombie() {
        this.villagerConvertedZombie = type == MobType.VILLAGER;
    }

    /** @return and clears the converted-zombie marker (the cadence arm reads). */
    public boolean consumeVillagerConvertedZombie() {
        boolean value = villagerConvertedZombie;
        villagerConvertedZombie = false;
        return value;
    }

    // ------------------------------------- the golem's village consult (VIII10)

    /**
     * The golem's village-defense consult (TrackIronGolemTargetGoal 1:1
     * over the manager's wiring): the village's attacker census first
     * (the creeper excluded), the 1-in-20 bad-reputation player fallback
     * — the answers land as the engine's shared ids.
     */
    private java.util.function.IntSupplier golemDefensePlayerQuery;

    /** The manager's own wiring for the defense consult's player arm. */
    public void setGolemDefensePlayerQuery(java.util.function.IntSupplier query) {
        this.golemDefensePlayerQuery = query;
    }

    /**
     * The defense consult's player arm (the 1-in-20 fallback when no
     * attacker stands): the nearest bad-reputation player's engine id,
     * -1 when none.
     */
    int golemVillageDefensePlayer() {
        java.util.function.IntSupplier query = golemDefensePlayerQuery;
        return query != null ? query.getAsInt() : -1;
    }

    /**
     * setPlayerCreated (IronGolemEntity lines 229-236): the pattern-built
     * golem never turns on its builder's village (-5 reputation arm
     * refuses) and never despawns (the engine's persistence pair rides
     * the same set).
     */
    private boolean golemPlayerCreated;

    /** @return whether the golem was pattern-built by a player (PlayerCreated). */
    public boolean golemPlayerCreated() {
        return golemPlayerCreated;
    }

    /**
     * setPlayerCreated (lines 229-236): the pattern's own arm — the flag
     * + the persistence (a built golem never despawns).
     */
    public void setGolemPlayerCreated(boolean playerCreated) {
        this.golemPlayerCreated = playerCreated && type == MobType.IRON_GOLEM;
        if (this.golemPlayerCreated) {
            persistent = true;
        }
    }

    /**
     * mobAiTick's level-up walk (VillagerEntity lines 377-405): with no
     * customer standing, the countdown burns down; at zero the armed
     * level-up lands — every SPENT-OUT offer's budget grows by
     * {@code nextInt(6) + nextInt(6) + 2} (the re-greens), the career's
     * next level appends — then the regeneration effect window (the
     * engine's mob-side effects ledger with the potion-on-mob family).
     * Called from the manager's tick (the villager branch).
     *
     * @return VIII10 — whether the armed LEVEL-UP landed this call (the
     *         event-14 happy burst + the village's +1 reputation arm ride
     *         the manager's site).
     */
    public boolean tickVillagerLevelUp() {
        if (villagerLevelUpCountdown <= 0) {
            return false;
        }
        villagerLevelUpCountdown--;
        if (villagerLevelUpCountdown > 0) {
            return false;
        }
        boolean landed = false;
        if (villagerLevelUp) {
            for (int i = 0; i < offerUses.length; i++) {
                if (offerUses[i] >= offerMaxUses[i]) {
                    offerMaxUses[i] += random.nextInt(6) + random.nextInt(6) + 2;
                }
            }
            villagerLevelUp();
            villagerLevelUp = false;
            landed = true;
            // The event-14 happy burst + the village's +1 reputation ride
            // the manager's site (VIII10 — the ledgered note retired).
        }
        // The 200-tick Regeneration window rides the mob-side effect
        // family (the witch's own model) — ledgered.
        return landed;
    }

    /** @return whether this kind trades (the villager). */
    public boolean isTrader() {
        return type == MobType.VILLAGER;
    }

    /**
     * The ridden body: the seat physics (turn, drive, jump) without the AI
     * mind. Mirrors tickBody's ground/step rules so mounts walk slabs and
     * stop at walls like every other body; the fluid rules carry over too.
     */
    private void tickRiddenBody() {
        float turn = steerSideways * RIDDEN_TURN_RATE;
        if (turn != 0.0f) {
            yaw += turn;
            headYaw = yaw;
        }

        boolean inFluid = world.inFluid(position.x(), position.y() + 0.2, position.z());
        boolean inLava = world.inLava(position.x(), position.y() + 0.2, position.z());
        if (inFluid && !inLava && fireTicks > 0) {
            extinguish();
        }

        // Drive: the manager feeds zero forward for an uncontrolled seat
        // (an unsaddled horse or a pig without the carrot on a stick).
        double walk = Math.abs(steerForward) > 0.01
                ? Math.signum(steerForward) * rideSpeed() : 0.0;
        if (inFluid) {
            walk *= 0.5; // swimming drag
        }
        if (steerJump && onGround && jumpCooldown == 0) {
            velocityY = type == MobType.PIG ? PIG_JUMP_IMPULSE : HORSE_JUMP_IMPULSE;
            jumpCooldown = RIDE_JUMP_COOLDOWN;
            onGround = false;
        }
        if (inLava) {
            // The mount rides the same travel lava arm (Slice 93/F1 — the
            // ridden body's own drag triple + the weak gravity).
            velocityY = velocityY * 0.5 - 0.02;
        } else if (inFluid) {
            // And the same travel WATER arm (the water arm's own model —
            // the walker's slice at the body tick, the clamp retired here
            // with it): {@code v = v * 0.8 - 0.02}, the geometric
            // transient and the -0.10 terminal sink, the reference's own
            // form under the engine's before-move order.
            velocityY = velocityY * 0.8 - 0.02;
        } else if (onGround) {
            velocityY = 0.0;
        } else {
            velocityY = (velocityY - GRAVITY_PER_TICK) * 0.98;
        }

        double radians = Math.toRadians(yaw);
        double moveX = velocityX - Math.sin(radians) * walk;
        double moveZ = velocityZ + Math.cos(radians) * walk;
        double damping = inLava ? 0.5 : inFluid ? 0.8 : (onGround ? 0.6 : 0.98);
        velocityX *= damping;
        velocityZ *= damping;

        // THE SWEPT-BOX APPLICATION (the physics-solver slice): the
        // ridden body's own Entity.move — the horse's step height 1.0
        // (the full-block mount), the pig's 0.5 — when the world
        // provides the collision surface. The standing drift (the
        // reference's gravity-AFTER-move cycle: a grounded body enters
        // with -0.0784, never the zeroed vertical).
        double mountDrift = onGround && velocityY == 0.0
                ? (0.0 - GRAVITY_PER_TICK) * 0.98 : velocityY;
        net.zaminmc.torch.server.physics.EntityMoveSolver.Result swept =
                solverMove(moveX, mountDrift, moveZ,
                        type == MobType.HORSE ? HORSE_STEP_HEIGHT : WALK_STEP_HEIGHT);
        if (swept != null) {
            return;
        }

        double newX = position.x() + moveX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + moveZ;

        // Ground snap / step: the tickBody rules without the AI detour —
        // a step up when the shape allows, a stop at the fence/wall.
        if (world.isSolidAt(newX, position.y(), newZ)
                && !world.isSolidAt(newX, position.y() + 1.0, newZ)
                && onGround && Math.abs(velocityY) < 0.01) {
            newY = world.supportY(newX, position.y() + GROUND_EPSILON, newZ);
            if (newY - position.y() > 1.0 + GROUND_EPSILON
                    || world.isSolidAt(newX, newY, newZ)) {
                newX = position.x();
                newZ = position.z();
                newY = position.y();
            }
        } else if (world.isSolidAt(newX, position.y(), newZ)) {
            newX = position.x();
            newZ = position.z();
            newY = position.y();
        }

        if (velocityY <= 0 && world.isSolidAt(newX, newY - GROUND_EPSILON, newZ)) {
            newY = world.supportY(newX, newY - GROUND_EPSILON, newZ);
            velocityY = 0.0;
            onGround = true;
        } else {
            onGround = false;
        }
        position = new Position(newX, newY, newZ);
    }

    /** @return the ridden forward speed (0 when the mount answers no reins). */
    private double rideSpeed() {
        if (type == MobType.PIG) {
            return PIG_RIDE_SPEED;
        }
        return saddled() ? HORSE_RIDE_SPEED : 0.0;
    }

    // ------------------------------------------------ fire (the burning slice)

    /**
     * The undead family's daylight burn, 1:1 (ZombieEntity.mobTick lines
     * 160-181 over the baby gate, SkeletonEntity.mobTick lines 115-136
     * without it — the pigman rides its zombie parent's walk and the
     * wither variant the skeleton's, the flames visual under their own
     * fire immunity: the burn clock's immune arm drains four ticks per
     * tick and never wounds). THE GATES, the reference's own order (the
     * random stream's shape rides it): the sunny world first
     * ({@code World.isSunny} — ambientDarkness under 4, the rain
     * gradients' shaves included, the roofed nether never sunny), then
     * the eye cell's brightness past 0.5 ({@code getBrightness(1.0F)}
     * — the y + eyeHeight read through the dimension's brightness
     * table), then the roll ({@code nextFloat() * 30 < (f - 0.4) * 2}
     * — a 1-in-25 re-arm per tick at full light, the coarser bands at
     * the lower lights), then the sky column above the feet cell
     * ({@code new BlockPos(x, Math.round(y), z)} — the rounded feet,
     * not the floored eye). THE HELMET ARM ({@code getEquipment(4)}):
     * any worn head blocks the burn outright; a damageable one wears
     * {@code +nextInt(2)} per gated tick and shatters at its max — the
     * slot clears and the break stages the reference's
     * {@code onBrokenItem} pair (the "random.break" sound at 0.8F with
     * its {@code 0.8 + nextFloat() * 0.4} pitch and the ITEM_CRACK
     * crumbs the manager's drain fans). A bare head ignites the clock
     * ({@code setOnFireFor(8)} — the 160-tick re-arm).
     */
    private void tickDaylightBurn() {
        if (!world.isSunny()) {
            return;
        }
        // The zombie family's own baby gate (ZombieEntity line 160's
        // !isBaby() — the skeleton's arm carries no gate; a skeleton is
        // never a baby in the reference either, the gate is moot there).
        if (type != MobType.SKELETON && isBaby()) {
            return;
        }
        float f = world.brightness(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y() + eyeHeight()),
                (int) Math.floor(position.z()));
        if (f <= 0.5f) {
            return;
        }
        if (random.nextFloat() * 30.0f >= (f - 0.4f) * 2.0f) {
            return;
        }
        if (!world.hasSkyAccess((int) Math.floor(position.x()),
                (int) Math.round(position.y()),
                (int) Math.floor(position.z()))) {
            return;
        }
        ItemStack helmet = equipment(4);
        if (helmet != null) {
            if (helmet.type().maxDurability() > 0 && !helmet.unbreakable()) {
                int wear = helmet.damage() + random.nextInt(2);
                if (wear >= helmet.type().maxDurability()) {
                    equipmentBreakStaged = new EquipmentBreak(
                            helmet, 0.8f + random.nextFloat() * 0.4f);
                    setEquipmentAt(4, ItemStack.EMPTY); // the slot clears
                } else {
                    setEquipmentAt(4, helmet.withDamage(wear));
                }
            }
            return; // the worn head blocks the burn, damageable or not
        }
        ignite(FIRE_TICKS); // setOnFireFor(8) — the reference's own clock
    }

    /** A worn piece the daylight wear exhausted (the drain's staged pair). */
    public record EquipmentBreak(ItemStack stack, float pitch) {
    }

    /** The staged equipment break (onBrokenItem's sound + crumbs pair). */
    private EquipmentBreak equipmentBreakStaged;

    /**
     * Takes the tick's staged equipment break, if the wear exhausted a
     * worn piece ({@code LivingEntity.onBrokenItem} — the manager drains
     * it into the sound face and the item-shatter crumbs).
     *
     * @return the broken stack with its sound pitch, or null when
     *         nothing broke this tick
     */
    public EquipmentBreak consumeEquipmentBreak() {
        EquipmentBreak staged = equipmentBreakStaged;
        equipmentBreakStaged = null;
        return staged;
    }

    /** @return ticks of burning left (0 = not on fire). */
    public int fireTicks() {
        return fireTicks;
    }

    /** @return true while the body is on fire (the living-flags bit 0x01). */
    public boolean burning() {
        return fireTicks > 0;
    }

    /** Sets the whole burn clock (readNbt's Fire row — Slice 89). */
    public void setFireTicks(int fireTicks) {
        this.fireTicks = Math.max(0, fireTicks);
    }

    /** Arms the fire clock to at least {@code ticks} (the ignite rule). */
    public void ignite(int ticks) {
        if (ticks > fireTicks) {
            fireTicks = ticks;
        }
    }

    /** Douses the body (water contact, test seams). */
    public void extinguish() {
        fireTicks = 0;
        fireDamageTimer = 0;
    }

    /** Set when a sheep's coat regrew; consumed by the manager. */
    private boolean regrownThisTick;

    private void tickMind(boolean night) {
        modeTicks++;

        // The awareness goals' per-tick defaults clear before the goal walk:
        // the ocelot's postures only stand when the owning goal sets them
        // again this tick (the reference's mobAiTick reads the movement
        // control every tick — a body not walking a goal speed stands
        // normal), and the goal walk speed falls back to the mode band.
        postureSneaking = false;
        postureSprinting = false;
        goalWalkSpeed = Double.NaN;
        // The AvoidSunlightGoal's stop() arm (TF-7f — the reference's
        // flag clears when the goal's canStart stops holding): the flag
        // stands exactly while the sunny claim re-arms it this pass —
        // every path a goal adopts below the claim gets the trim, none
        // after the sun sets.
        navigation.setAvoidSunLight(false);

        // The mob-vs-mob target state (Slice 16b) updates BEFORE the goal
        // walk — the stroll's yielding gate (goalHasTarget) reads the live
        // target, so the scan and the dead-target clear must land first.
        MobEntity mobTarget = tickMobTargeting();
        pendingScareWolf = type == MobType.SKELETON
                ? world.nearestMobOfKind(position.x(), position.y(), position.z(),
                        SKELETON_WOLF_FLEE_RANGE, MobType.WOLF)
                : null;

        Position target = world.nearestPlayer(position.x(), position.y(), position.z(),
                type.hostile ? followRange() : 8.0);
        // VIII9 — the forced player hunt (the reinforcement callee's own
        // setAttackTarget, takeDamage line 218): a body with a forced
        // target chases it wherever it stands, past the nearest-player
        // band the unprompted hunt reads; a gone body clears the arm.
        if (forcedHuntPlayerId >= 0) {
            Position forced = forcedHuntPosition();
            if (forced != null) {
                target = forced;
            } else {
                forcedHuntPlayerId = -1; // the target left the world
            }
        }
        boolean canHunt = (type.hostile || (type == MobType.RABBIT && rabbitKiller()))
                // The day-neutral kinds' own gate (the spider law — the
                // brightness at the eye, not the clock: SpiderEntity
                // .TargetGoal.canStart lines 222-226 + the attack goal's
                // 1-in-100 daylight drop, lines 200-208; a cave-dwelling
                // spider hunts at noon, a torch-lit one sleeps at night).
                && (!type.traits.neutralByDay() || dayNeutralHuntGate())
                && (type != MobType.ZOMBIE_PIGMAN || isAngry()) // the neutral gate (16d)
                // The enderman's own provocation gate (19d — the reference's
                // LookAtPlayerGoal/takeDamage model): the neutral body hunts
                // only the stare or the wound provoked.
                && (type != MobType.ENDERMAN || endermanAngry);
        this.visibleTarget = target;
        this.targetHuntable = canHunt;

        // The enderman's provocation + carry (19d): the stare window and
        // the block arms run whatever the hunt state (the reference's own
        // goal family runs below the hunt's).
        if (type == MobType.ENDERMAN) {
            tickEndermanProvocation(canHunt && target != null);
            tickEndermanCarry();
        }

        // The angry pigman's own speed boost (the +0.05 modifier on the
        // 0.23 base — mobAiTick's ATTACKING_SPEED_BOOST).
        if (type == MobType.ZOMBIE_PIGMAN && isAngry() && target != null) {
            goalWalkSpeed = PIGMAN_ANGRY_CHASE_MULTIPLIER;
        }

        // The custom minds (16c/16e — the ghast, the squid, the bat):
        // their reference goal sets carry NO shared goals (no float, no
        // panic, no stroll — only their own SwimAround/MoveAround/roost
        // families), so they preempt the goal layer entirely.
        if (type == MobType.GHAST) {
            tickGhastMind(target, canHunt);
            return;
        }
        if (type == MobType.SQUID) {
            tickSquidMind();
            return;
        }
        if (type == MobType.BAT) {
            tickBatMind();
            return;
        }
        if (type == MobType.GUARDIAN) {
            // VT-7c — the validator's squid branch: the reference's
            // ActiveTargetGoal scans players AND squids in one box and
            // the NEAREST passing body owns the hunt (the shared
            // nearest-player read alone missed the squid arm).
            tickGuardianMind(guardianHuntTarget(target), canHunt);
            return;
        }
        // The golem pair's own minds (17d — the reverse hunt): the iron
        // golem walks down the nearest hostile, the snow golem volleys it.
        if (type == MobType.IRON_GOLEM) {
            tickIronGolemMind(night);
            return;
        }
        if (type == MobType.SNOW_GOLEM) {
            tickSnowGolemMind();
            return;
        }
        // The blaze's own mind (18a — the AttackGoal family): the volley
        // cycle, the melee close-in, and the mobAiTick arms (the drown,
        // the eye-offset clock, the hover rise).
        if (type == MobType.BLAZE) {
            tickBlazeMind(target, canHunt);
            return;
        }
        // The wither boss's own mind (Slice 59 — WitherEntity.mobTick +
        // mobAiTick): the flight shaping, the blue window, the three
        // heads' quarries, the skull volleys, the sweep, the flat regen —
        // the reference's goal set carries only Swim/ProjectileAttack/
        // Wander/Look pairs whose observable law the mind folds in.
        if (type == MobType.WITHER) {
            tickWitherMind(target, canHunt);
            return;
        }
        // The ender dragon's own mind (Slice 63 — EnderDragonEntity
        // .mobTick 1:1): the wing flap, the steering walk (the quarry
        // chase or the point drift, the 100/22500 re-target band), the
        // velocity shaping, the rotation-speed yaw, the part placement,
        // and the contact arms — the reference carries no goal set at
        // all (no goals registered, the steering IS the AI).
        if (type == MobType.ENDER_DRAGON) {
            tickDragonMind(target, canHunt);
            return;
        }

        // The ignited creeper's own swell (CreeperEntity.tick lines
        // 103-122: the flag forces the fuse direction 1 every tick BEFORE
        // the goals run — an ignited body detonates at the Fuse row's
        // duration with nobody in sight, and the swell outranks every
        // goal the stroll layer would claim).
        if (type.traits.explodes() && creeperIgnited) {
            mode = Mode.FUSE;
            if (modeTicks >= creeperFuseTime) {
                pendingExplosion = true;
            }
            return; // priming creepers stand still (the historical swell)
        }

        // The goal layer (the community PathfinderGoal architecture): float
        // and panic claim the body first, the stroll cycle only when no hunt
        // is on. What remains below is the combat mind.
        if (goals.tick(this, night)) {
            return;
        }

        // The skeleton's flee-from-wolves (goal priority 3 over the attack
        // family's 4): a wolf within 6 blocks owns the movement whatever
        // else the skeleton was doing (Slice 16b).
        if (tickSkeletonWolfFlee()) {
            return;
        }

        // The mob-vs-mob target family (Slice 16b — the targetSelector's
        // single target): the hunts (the wolf's sheep/rabbits/skeletons,
        // the zombie's villagers, the spider's and the skeleton's golem
        // feuds) and the revenge/owner arms. The target outranks the player
        // hunt — setAttackTarget is the single write. (The state itself
        // updated before the goal walk above; the scan ran there so the
        // stroll's yielding gate could see it.)
        if (mobTarget != null) {
            // The ranged-on-mob arm (the reference's ProjectileAttackGoal
            // attacks ANY attackTarget — its canStart reads the target's
            // KIND not at all, ProjectileAttackGoal lines 40-48; the
            // skeleton's own melee goal is Player-only, lines 43-44): a mob
            // target on a bow carrier rides the shooting band, the arrows
            // landing through the engine's projectile-on-mob collision
            // family with the shooter's identity on the wound.
            if (type.traits.ranged() && !skeletonWither) {
                double dx = mobTarget.position().x() - position.x();
                double dy = mobTarget.position().y() - position.y();
                double dz = mobTarget.position().z() - position.z();
                tickSkeletonMind(mobTarget.position(),
                        Math.sqrt(dx * dx + dz * dz), dy);
            } else if (tickPounceAtTarget(mobTarget.position())) {
                // The tameable family's own leap (PounceAtTargetGoal over
                // the melee's band): the airborne window holds the walk —
                // the chase resumes at the landing.
                return;
            } else {
                tickMobTargetChase(mobTarget);
            }
            return;
        }

        // The slime family's own goal family (Slice 12k): the
        // direction/chase walk and the contact damage replace the standard
        // mind — the hop movement lands in the body tick below.
        if (type.slimeFamily()) {
            tickSlimeMind(target, canHunt);
            return;
        }

        // The hunt's same-team refusal (TrackTargetGoal.isTarget lines
        // 102-106 over the player's NAME key — MobEntityPlayerTargetGoal
        // lines 90-94): a teamed body never hunts its own kin; the glance
        // and the awareness reads keep the teamless scan
        // (LookAtEntityGoal carries no team law).
        Position huntTarget = target;
        String huntTeam = teamName();
        if (canHunt && huntTarget != null && huntTeam != null) {
            huntTarget = world.nearestPlayer(position.x(), position.y(), position.z(),
                    type.hostile ? followRange() : 8.0, huntTeam);
        }

        if (canHunt && huntTarget != null) {
            double dx = huntTarget.x() - position.x();
            double dy = huntTarget.y() - position.y();
            double dz = huntTarget.z() - position.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            // The enderman's far-target blink (the reference's
            // ChasePlayerGoal line 425): a target beyond 16 blocks for
            // 30 ticks blinks the body 16 blocks closer.
            if (type == MobType.ENDERMAN) {
                if (dx * dx + dy * dy + dz * dz > 256.0) {
                    if (++farFromTargetTicks >= 30) {
                        farFromTargetTicks = 0;
                        teleportToward(huntTarget);
                    }
                } else {
                    farFromTargetTicks = 0;
                }
            }

            if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
                // keep mode; the hunt continues
            } else {
                mode = Mode.CHASE;
                modeTicks = 0;
            }

            if (type.traits.explodes()) {
                tickCreeperMind(huntTarget, horizontal, dy);
                return;
            }
            if (type.traits.ranged() && !skeletonWither) {
                tickSkeletonMind(huntTarget, horizontal, dy);
                return;
            }
            // The wither variant's melee (the nether initialize lines
            // 218-220: the meleeAttackGoal replaces the archer's ranged
            // goal under the stone sword).

            // The melee hunter (zombie, night spider).
            mode = Mode.CHASE;
            yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            headYaw = yaw;
            if (tickPounceAtTarget(huntTarget)) {
                // The tameable family's own leap holds the chase walk for
                // the flight (the reference's selector stops the melee).
                return;
            }
            tickPathFollowing(huntTarget);
            // The killer bunny's own reach (EvilAttackGoal.getReach lines
            // 438-441: {@code 4.0 + target.width} against the SQUARED
            // distance — the plain rabbit's 3.0 swing never arms); the
            // strike's mob.attack sound rides the tryDamage override (lines
            // 255-258) — the pending flag the manager plays at the swing.
            boolean killerStrike = type == MobType.RABBIT && rabbitKiller();
            boolean inReach = killerStrike
                    ? dx * dx + dy * dy + dz * dz <= 4.0 + 0.6
                    : horizontal <= MELEE_ATTACK_RANGE && Math.abs(dy) <= MELEE_ATTACK_VERTICAL_RANGE;
            if (inReach && attackCooldown == 0) {
                attackCooldown = MELEE_ATTACK_COOLDOWN;
                // The manager hears the swing through the pending flag; the
                // attack lands through consumePendingMeleeAttack below.
                pendingMeleeAttack = true;
                if (killerStrike) {
                    pendingSlimeAttackSound = true; // mob.attack at the strike
                }
            }
            return;
        }

        if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
            mode = Mode.IDLE; // target lost (or daylight saved the spider)
            modeTicks = 0;
            clearPath();
        }
        // No hunt: the stroll goal owns the idle band this tick. (The
        // villager's village-return walk runs in the goal layer above —
        // the MobGoals catalog's own entry; the blaze's variant is
        // unreachable, the guardian's rides its swim pin.)
        goalTickIdleWander(target);
    }

    /**
     * The path-follow tick (the vanilla navigation port): the navigation
     * advances its walking index over the vanilla band (width²·offset, the
     * same-level prefix) with the direct-walk shortcut and the 100-tick
     * stuck check; the feet walk the heading to its current waypoint while
     * the eyes stay on the target. The route recomputes on the 20-30 tick
     * budget (both outcomes re-arm it); a failed search drops the route so
     * the wall-slide detour steering owns the gap between attempts.
     */
    private void tickPathFollowing(Position target) {
        if (repathCooldown > 0) {
            repathCooldown--;
        }
        navigation.tick();
        if (repathCooldown == 0) {
            repath(target);
        }
        if (navigation.hasTarget) {
            pathYaw = angleTo(navigation.currentTargetX - position.x(),
                    navigation.currentTargetZ - position.z());
            pathYawValid = true;
        } else {
            pathYawValid = false;
        }
    }

    /**
     * Recomputes the route (both outcomes re-arm the budget). The vanilla
     * flow: find, then moveAlong — a null route (nothing beyond the start)
     * drops the path and the detour fallback owns the block; a route with
     * the same node sequence keeps its walking index (the vanilla reset
     * rule).
     */
    private void repath(Position target) {
        repathCooldown = 20 + random.nextInt(10);
        navigation.moveTo(target.x(), target.y(), target.z(), walkSpeed());
    }

    private void clearPath() {
        navigation.stop();
        pathYawValid = false;
    }

    /** @return the path heading override the feet walk (or NaN when none). */
    boolean goalHasPathHeading() {
        return pathYawValid;
    }

    float goalPathYaw() {
        return pathYaw;
    }

    // ------------------------------------------------ goal surface (package-private)

    /** The nearest player this tick (the stroll goal's look-at anchor). */
    Position nearestVisiblePlayer() {
        return visibleTarget;
    }

    boolean goalInFluid() {
        return world.inFluid(position.x(), position.y() + 0.2, position.z());
    }

    void goalSwimUp() {
        // THE SWIM INPUT (SwimGoal.tick, goal/SwimGoal.java lines 17-25:
        // {@code if (random.nextFloat() < 0.8F) getJumpControl().setActive()}
        // — the 0.8 roll, then the jump block's water consult,
        // LivingEntity lines 1446-1453 over jumpInWater 1126-1128:
        // {@code velocityY += 0.04F}): the reference's own cadence — the
        // input lands on ~80% of ticks (the goal's per-tick roll), the
        // flat add with NO cap. The water arm's drag is the governor (the
        // composite's steady rise: v = 0.8(v + 0.04) - 0.02 → the +0.06
        // fixed point; the retired form was the unconditional
        // +0.05/cap-0.08 approximation, folded away with the clamp it
        // fed).
        if (random.nextFloat() >= 0.8F) {
            return; // this tick's roll refused — no swim input (the 20%)
        }
        velocityY += 0.04;
        onGround = false;
    }

    boolean goalPanicking() {
        return panicTicks > 0;
    }

    /**
     * The breed drive's steering (the vanilla goal tick): the eyes on the
     * mate, the feet on the path to it — the same chase pairing, without
     * the attack arm.
     */
    void goalTickBreed(MobEntity mate) {
        double dx = mate.position().x() - position.x();
        double dz = mate.position().z() - position.z();
        yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        headYaw = yaw;
        tickPathFollowing(mate.position());
    }

    void goalTickPanic() {
        panicTicks--;
        if (mode != Mode.PANIC) {
            mode = Mode.PANIC;
            modeTicks = 0;
        }
        if (modeTicks == 1) {
            yaw = randomDirection();
            headYaw = yaw;
        }
    }

    // ------------------------------------- the awareness goals (Slice 16a)

    /** TemptGoal's range: {@code getNearestPlayer(mob, 10.0)}. */
    private static final double TEMPT_RANGE = 10.0;
    /** The scared window: the player's move tolerance inside 6 blocks. */
    private static final double TEMPT_SCARED_DISTANCE_SQUARED = 0.010000000000000002;
    /** The scared rotation tolerance, degrees. */
    private static final double TEMPT_SCARED_ROTATION = 5.0;
    /** TemptGoal's stop() re-arm: the cat sulks 100 ticks before trying again. */
    private static final int TEMPT_DELAY = 100;
    /** The tempt's approach stop: within 2.5 blocks the feet hold. */
    private static final double TEMPT_STOP_DISTANCE_SQUARED = 6.25;
    /** The goal speed over the ocelot's 0.3 base: the creep at 0.6. */
    private static final double TEMPT_WALK_MULTIPLIER = 0.6 / 0.3;
    /** FleeEntityGoal's reach: players within 16 blocks scare the wild cat. */
    private static final double FLEE_PLAYER_RANGE = 16.0;
    /** The flee's close band: the scary player within 7 blocks sprints. */
    private static final double FLEE_CLOSE_SQUARED = 49.0;
    /** The flee's far speed over the base (0.8 on the 0.3 ocelot). */
    private static final double FLEE_FAR_MULTIPLIER = 0.8 / 0.3;
    /** The flee's close speed over the base (1.33 on the 0.3 — the sprint). */
    private static final double FLEE_CLOSE_MULTIPLIER = 1.33 / 0.3;
    /** WolfBegGoal's reach: the bone-holder's gaze window. */
    private static final double BEG_RANGE = 8.0;
    /** WolfBegGoal's timer: {@code 40 + nextInt(40)} ticks of begging. */
    private static final int BEG_TIMER_BASE = 40;

    /** @return whether the tempt goal is live (the ocelot's tame gate reads it). */
    public boolean temptActive() {
        return temptActive;
    }

    /**
     * TemptGoal's canStart/shouldContinue pair (the ocelot's fish-follow):
     * a fresh start finds the nearest player within 10 blocks holding the
     * raw fish — the delay gate first (100 ticks after a stop). A live goal
     * runs the scared check every tick: the player within 6 blocks may not
     * move beyond 0.01 blocks or turn beyond 5 degrees; the sample updates
     * beyond that band, and the rotation sample always does. A death on
     * any road applies the stop(): the delay re-arms and the feet release.
     */
    boolean goalTemptCanUse() {
        if (type == MobType.RABBIT) {
            // The rabbit's own TemptGoal family (lines 59-61: the carrot,
            // the golden carrot, the dandelion — each its own goal, all at
            // speed 1.0 and NOT scared by movement): the shared goal carries
            // the item set; the no-scared arm means shouldContinue is only
            // the canStart re-check.
            if (temptActive) {
                PlayerView view = world.nearestPlayerView(
                        position.x(), position.y(), position.z(), TEMPT_RANGE);
                if (view == null || !rabbitTemptItem(view.heldItem())) {
                    goalTemptStop();
                    return false;
                }
                return true;
            }
            if (temptDelay > 0) {
                temptDelay--;
                return false;
            }
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), TEMPT_RANGE);
            if (view == null) {
                return false;
            }
            if (!rabbitTemptItem(view.heldItem())) {
                return false;
            }
            temptActive = true;
            return true;
        }
        if (type != MobType.OCELOT) {
            return false;
        }
        if (temptActive) {
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), TEMPT_RANGE);
            if (view == null) {
                goalTemptStop();
                return false;
            }
            // shouldContinue's scared check (TemptGoal lines 52-72).
            double dx = view.position().x() - position.x();
            double dy = view.position().y() - position.y();
            double dz = view.position().z() - position.z();
            if (dx * dx + dy * dy + dz * dz < 36.0) {
                double px = view.position().x() - temptSampleX;
                double py = view.position().y() - temptSampleY;
                double pz = view.position().z() - temptSampleZ;
                if (px * px + py * py + pz * pz > TEMPT_SCARED_DISTANCE_SQUARED) {
                    goalTemptStop();
                    return false;
                }
                if (Math.abs(view.yaw() - temptSampleYaw) > TEMPT_SCARED_ROTATION
                        || Math.abs(view.pitch() - temptSamplePitch) > TEMPT_SCARED_ROTATION) {
                    goalTemptStop();
                    return false;
                }
            } else {
                temptSampleX = view.position().x();
                temptSampleY = view.position().y();
                temptSampleZ = view.position().z();
            }
            temptSampleYaw = view.yaw();
            temptSamplePitch = view.pitch();
            // The canStart re-check: the player still holds the fish.
            if (!"minecraft:fish".equals(view.heldItem())) {
                goalTemptStop();
                return false;
            }
            return true;
        }
        // canStart (lines 36-49): the delay gate, then the nearest player's
        // held item — the nearest player only, not the nearest fish-holder.
        if (temptDelay > 0) {
            temptDelay--;
            return false;
        }
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), TEMPT_RANGE);
        if (view == null) {
            return false;
        }
        if (!"minecraft:fish".equals(view.heldItem())) {
            return false;
        }
        // start(): the sample lands, the flag flips.
        temptActive = true;
        temptSampleX = view.position().x();
        temptSampleY = view.position().y();
        temptSampleZ = view.position().z();
        temptSampleYaw = view.yaw();
        temptSamplePitch = view.pitch();
        return true;
    }

    /** TemptGoal's stop(): the delay re-arms and the feet release. */
    private void goalTemptStop() {
        temptActive = false;
        temptDelay = TEMPT_DELAY;
        clearPath();
    }

    /** RabbitEntity.isTempting (lines 298-300): the three-item family. */
    private static boolean rabbitTemptItem(String heldItem) {
        return "minecraft:carrot".equals(heldItem)
                || "minecraft:golden_carrot".equals(heldItem)
                || "minecraft:dandelion".equals(heldItem);
    }

    /**
     * TemptGoal's tick (lines 95-102): the eyes stay on the player; the
     * feet walk the path at the creep speed unless the 2.5-block hold
     * band closes. The walk rides the goal-speed multiplier (2.0 — the
     * 0.6 over the 0.3 base), and the ocelot's mobAiTick posture turns
     * that walk into the shared-flags sneak.
     */
    void goalTemptTick() {
        if (type == MobType.RABBIT) {
            // The rabbit's own tick: the 1.0 goal speed over the band, no
            // posture (the walk stands normal), the same 2.5-block hold.
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), TEMPT_RANGE);
            if (view == null) {
                return;
            }
            double dx = view.position().x() - position.x();
            double dy = view.position().y() - position.y();
            double dz = view.position().z() - position.z();
            headYaw = angleTo(dx, dz);
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared < TEMPT_STOP_DISTANCE_SQUARED) {
                clearPath();
                goalWalkSpeed = 0.0;
                return;
            }
            goalWalkSpeed = 1.0 / 0.3; // the 1.0 goal parameter over the base
            mode = Mode.WANDER;
            modeTicks = 0;
            tickPathFollowing(view.position());
            return;
        }
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), TEMPT_RANGE);
        if (view == null) {
            return;
        }
        double dx = view.position().x() - position.x();
        double dy = view.position().y() - position.y();
        double dz = view.position().z() - position.z();
        headYaw = angleTo(dx, dz); // the look control's 30-degree grip
        double squared = dx * dx + dy * dy + dz * dz;
        if (squared < TEMPT_STOP_DISTANCE_SQUARED) {
            clearPath(); // the hold: the cat sits at the fish-holder's feet
            goalWalkSpeed = 0.0;
            postureSneaking = false; // the stopped walk stands normal (mobAiTick)
            return;
        }
        goalWalkSpeed = TEMPT_WALK_MULTIPLIER;
        postureSneaking = true; // mobAiTick: the 0.6 walk is the creep
        mode = Mode.WANDER;
        modeTicks = 0;
        tickPathFollowing(view.position());
    }

    /**
     * FleeEntityGoal's canStart (the untamed ocelot, players only): the
     * 16-block scare range — any live player inside it. The tamed cat
     * never flees (onTamedChanged removes the goal).
     */
    boolean goalFleePlayerCanUse() {
        return type == MobType.OCELOT && !wolfTamed() && world.nearestPlayerView(
                position.x(), position.y(), position.z(), FLEE_PLAYER_RANGE) != null;
    }

    /**
     * The rabbit's own flee (line 66: {@code FleeEntityGoal<>(this,
     * WolfEntity.class, 16.0F, 1.33, 1.33)}): any live wolf within 16
     * blocks sends the body running — BOTH speed bands 1.33 (the
     * reference's own pair). The killer bunny's setVariant(99) removes
     * this goal (the reference's removeGoal at line 322).
     */
    boolean goalFleeWolfCanUse() {
        return type == MobType.RABBIT && !rabbitKiller() && world.nearestMobOfKind(
                position.x(), position.y(), position.z(), FLEE_PLAYER_RANGE,
                MobType.WOLF) != null;
    }

    /** The rabbit's flee tick: directly away from the wolf at 1.33 both bands. */
    void goalFleeWolfTick() {
        MobEntity wolf = world.nearestMobOfKind(
                position.x(), position.y(), position.z(), FLEE_PLAYER_RANGE,
                MobType.WOLF);
        if (wolf == null) {
            return;
        }
        double dx = position.x() - wolf.position().x();
        double dz = position.z() - wolf.position().z();
        goalWalkSpeed = 1.33 / 0.3; // both bands share the 1.33 pair
        mode = Mode.PANIC;
        modeTicks = 0;
        yaw = angleTo(dx, dz); // directly away from the wolf
        headYaw = yaw;
    }

    /**
     * FleeEntityGoal's tick: away from the scary player — the far walk at
     * 0.8 over the base, the 7-block close band sprinting at 1.33 with the
     * shared-flags sprint bit (the ocelot's mobAiTick own postures).
     */
    void goalFleePlayerTick() {
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), FLEE_PLAYER_RANGE);
        if (view == null) {
            return;
        }
        double dx = position.x() - view.position().x();
        double dy = position.y() - view.position().y();
        double dz = position.z() - view.position().z();
        boolean close = dx * dx + dy * dy + dz * dz < FLEE_CLOSE_SQUARED;
        goalWalkSpeed = close ? FLEE_CLOSE_MULTIPLIER : FLEE_FAR_MULTIPLIER;
        if (close) {
            postureSprinting = true; // the 1.33 sprint bit
        }
        mode = Mode.PANIC; // the fleeing body: the panic walk, no grazing
        modeTicks = 0;
        yaw = angleTo(dx, dz); // directly away from the player
        headYaw = yaw;
    }

    /**
     * WolfBegGoal's isAttractive (lines 65-71): the untamed wolf begs at a
     * bone-holder; any taming state begs at its own breeding food (the meat
     * family — isBreedingItem's table).
     */
    private boolean begAttractive(String heldItem) {
        if (heldItem == null) {
            return false;
        }
        if (!wolfTamed() && heldItem.equals("minecraft:bone")) {
            return true;
        }
        return switch (heldItem) {
            case "minecraft:porkchop", "minecraft:cooked_porkchop", "minecraft:beef",
                    "minecraft:cooked_beef", "minecraft:chicken", "minecraft:cooked_chicken",
                    "minecraft:rabbit", "minecraft:cooked_rabbit" -> true;
            default -> false;
        };
    }

    /**
     * WolfBegGoal's canStart + shouldContinue pair: the nearest player
     * within 8 blocks holding an attractive item. A fresh start rolls the
     * timer ({@code 40 + nextInt(40)}) and raises the wire's begging flag;
     * the live window only continues while the timer, the reach, and the
     * attraction hold. The stop clears the flag (the manager's flip
     * detection publishes it).
     */
    boolean goalBegCanUse() {
        if (type != MobType.WOLF) {
            return false;
        }
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), BEG_RANGE);
        if (view == null || !begAttractive(view.heldItem())) {
            if (wolfBegging) {
                setWolfBegging(false); // stop(): the head comes up
            }
            return false;
        }
        double dx = view.position().x() - position.x();
        double dy = view.position().y() - position.y();
        double dz = view.position().z() - position.z();
        if (dx * dx + dy * dy + dz * dz > BEG_RANGE * BEG_RANGE) {
            if (wolfBegging) {
                setWolfBegging(false);
            }
            return false;
        }
        if (!wolfBegging) {
            setWolfBegging(true); // start(): the head tilts
            begTimer = BEG_TIMER_BASE + random.nextInt(BEG_TIMER_BASE);
        }
        return true;
    }

    /**
     * WolfBegGoal's tick: the head stays on the player's eyes while the
     * timer burns; the timer out (or the player leaving) is the stop.
     */
    void goalBegTick() {
        if (--begTimer <= 0) {
            setWolfBegging(false);
            return;
        }
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), BEG_RANGE);
        if (view != null) {
            headYaw = angleTo(view.position().x() - position.x(),
                    view.position().z() - position.z());
        }
    }

    /** @return the ocelot's living-flag posture bits: sneak 0x02 | sprint 0x10. */
    public int postureFlags() {
        return (postureSneaking ? 0x02 : 0) | (postureSprinting ? 0x10 : 0);
    }

    /** @return the tempt creep's sneak posture (the shared flags bit). */
    public boolean postureSneaking() {
        return postureSneaking;
    }

    /** @return the flee's sprint posture (the shared flags bit). */
    public boolean postureSprinting() {
        return postureSprinting;
    }

    // ------------------------------------- the mob-vs-mob targets (Slice 16b)

    /** ActiveTargetGoal's follow range: the generic attribute base 32. */
    private static final double MOB_TARGET_FOLLOW_RANGE = 32.0;
    /** ActiveTargetGoal's canStart cadence gate: a 1-in-10 scan. */
    private static final int HUNT_SCAN_CHANCE = 10;
    /** FleeEntityGoal's wolf scare for the skeleton: 6 blocks at 1.0/1.2. */
    private static final double SKELETON_WOLF_FLEE_RANGE = 6.0;
    /** The skeleton's flee speeds over its 0.25 base: the 1.0 far walk. */
    private static final double SKELETON_WOLF_FLEE_FAR = 1.0 / 0.25;
    /** The close sprint: the reference's 1.2. */
    private static final double SKELETON_WOLF_FLEE_CLOSE = 1.2 / 0.25;
    /** The melee reach against a mob target (the same band as the player). */
    private static final double MOB_MELEE_ATTACK_RANGE = 2.0;

    /** @return the live mob-vs-mob attack target, or null. */
    public MobEntity mobAttackTarget() {
        return mobAttackTarget;
    }

    /**
     * @return the body's own team name ({@code LivingEntity.getScoreboardTeam}
     * line 1635's UUID-STRING key over the engine's name-keyed teams), or
     * null when the body rides no team.
     */
    public String teamName() {
        return world.teamNameOf(uuid.toString());
    }

    /**
     * The same-team refusal ({@code TrackTargetGoal.isTarget} lines 102-106
     * over {@code AbstractTeam.isAlliedTo}'s same-team equality, lines 8-10):
     * a teamless body never refuses.
     */
    boolean sameTeamAs(MobEntity other) {
        String own = teamName();
        return own != null && own.equals(other.teamName());
    }

    /**
     * The day-neutral kinds' darkness gate (the reference's spider law —
     * {@code SpiderEntity.TargetGoal.canStart} lines 222-226 over the attack
     * goal's own continue, {@code SpiderAttackGoal.shouldContinue} lines
     * 200-208): a NEW hunt starts only in darkness (the brightness at the
     * eye under 0.5 — the raw light 12 quirk the brightness table's own
     * {@code table[12] == 0.5} carries), and a hunt already live in the
     * light keeps running until the 1-in-100 drop roll lands. The answer
     * caches per tick — the scan (tickMobTargeting, the tick's first walk)
     * resets it and the hunt gate reads the same roll, exactly the
     * reference's one-goal-per-tick cadence.
     */
    private Boolean dayNeutralDarkTick;

    private boolean dayNeutralHuntGate() {
        if (dayNeutralDarkTick == null) {
            if (brightnessAtEye() < 0.5f) {
                dayNeutralDarkTick = true; // dark: the hunt is armed
            } else {
                // Bright: only a live hunt continues, and only until the roll.
                dayNeutralDarkTick = (mode == Mode.CHASE || mode == Mode.STRAFE)
                        && random.nextInt(100) != 0;
            }
        }
        return dayNeutralDarkTick;
    }

    /**
     * The brightness at the eye ({@code Entity.getBrightness(1.0F)} — the
     * eye-height cell's own read, Entity.java lines 889-892).
     */
    private float brightnessAtEye() {
        return world.brightness(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y() + eyeHeight()),
                (int) Math.floor(position.z()));
    }

    /**
     * The single-target write ({@code setAttackTarget}): the wolf's angry
     * posture follows it — a wild wolf goes angry on any target, and the
     * target's death stands it down (WolfEntity.setAttackTarget lines
     * 88-94).
     */
    public void setMobAttackTarget(MobEntity target) {
        mobAttackTarget = target;
        if (type == MobType.WOLF) {
            if (target == null) {
                setWolfAngry(false);
            } else if (!wolfTamed()) {
                setWolfAngry(true);
            }
        }
    }

    /**
     * The hunt scan (ActiveTargetGoal.canStart over the target family):
     * the 1-in-10 cadence gate, then the nearest legal prey within the
     * follow range — the wolf's sheep/rabbits (the UNTAMED arm only, the
     * reference's UntamedActiveTargetGoal) and the skeleton feud (any
     * taming state), the zombie's villagers, the spider's golems (the
     * day-neutral gate — SpiderEntity.TargetGoal line 44, the same
     * brightness law the player hunt reads), and the skeleton's own
     * always-on golem feud (targetSelector 3). A live target is kept until
     * it dies (the goal's own shouldContinue — no re-targeting mid-hunt).
     * Every scan refuses same-team candidates ({@code TrackTargetGoal
     * .isTarget} lines 102-106 — a teamed body never hunts its own kin).
     *
     * @return the mob the chase mind runs against this tick, or null.
     */
    MobEntity tickMobTargeting() {
        dayNeutralDarkTick = null; // the per-tick reset — this scan runs first
        if (mobAttackTarget != null && mobAttackTarget.dead()) {
            setMobAttackTarget(null); // shouldContinue: the target died
        }
        boolean dayNeutralDark = !type.traits.neutralByDay() || dayNeutralHuntGate();
        if (mobAttackTarget == null && (type == MobType.WOLF || type == MobType.ZOMBIE
                || type == MobType.SPIDER || type == MobType.SKELETON
                || type == MobType.OCELOT
                || (type == MobType.RABBIT && rabbitKiller()))
                && random.nextInt(HUNT_SCAN_CHANCE) == 0) {
            MobEntity found = null;
            double best = MOB_TARGET_FOLLOW_RANGE * MOB_TARGET_FOLLOW_RANGE;
            String team = teamName();
            if (type == MobType.WOLF) {
                // targetSelector 5 (ActiveTargetGoal SkeletonEntity): any
                // taming state; 4 (UntamedActiveTargetGoal AnimalEntity):
                // sheep or rabbit, the untamed arm only.
                MobEntity skeleton = world.nearestMobOfKind(
                        position.x(), position.y(), position.z(),
                        MOB_TARGET_FOLLOW_RANGE, MobType.SKELETON, team);
                if (skeleton != null) {
                    found = skeleton;
                } else if (!wolfTamed()) {
                    MobEntity sheep = world.nearestMobOfKind(
                            position.x(), position.y(), position.z(),
                            MOB_TARGET_FOLLOW_RANGE, MobType.SHEEP, team);
                    MobEntity rabbit = world.nearestMobOfKind(
                            position.x(), position.y(), position.z(),
                            MOB_TARGET_FOLLOW_RANGE, MobType.RABBIT, team);
                    if (sheep != null && rabbit != null) {
                        found = distanceSquaredTo(sheep) <= distanceSquaredTo(rabbit) ? sheep : rabbit;
                    } else {
                        found = sheep != null ? sheep : rabbit;
                    }
                }
            } else if (type == MobType.RABBIT) {
                // The killer bunny's own target family (setVariant lines
                // 325-326: ActiveTargetGoal over the wolf — the player half
                // rides the engine's player hunt above): any wolf within the
                // follow range.
                found = world.nearestMobOfKind(position.x(), position.y(), position.z(),
                        MOB_TARGET_FOLLOW_RANGE, MobType.WOLF, team);
            } else if (type == MobType.OCELOT) {
                // The untamed cat's own hunt (OcelotEntity line 53,
                // targetSelector 1: UntamedActiveTargetGoal over the
                // chicken): the 1-in-10 cadence, the follow range, the
                // same-team refusal — the tamed cat never hunts.
                if (!wolfTamed()) {
                    found = world.nearestMobOfKind(position.x(), position.y(), position.z(),
                            MOB_TARGET_FOLLOW_RANGE, MobType.CHICKEN, team);
                }
            } else if (type == MobType.SPIDER) {
                // targetSelector 3 (SpiderEntity.TargetGoal over the iron
                // golem — line 44): the day-neutral gates' own arm, the
                // brightness gate the player hunt reads plus the goal's
                // checkVisibility (the canSee line the ActiveTargetGoal's
                // canTarget consult carries).
                if (dayNeutralDark) {
                    found = world.nearestMobOfKind(position.x(), position.y(), position.z(),
                            MOB_TARGET_FOLLOW_RANGE, MobType.IRON_GOLEM, team);
                    if (found != null && !world.clearLine(eyePosition(), found.position())) {
                        found = null; // canTarget's visibility consult
                    }
                }
            } else if (type == MobType.SKELETON) {
                // targetSelector 3 (ActiveTargetGoal over the iron golem —
                // line 57, always-on): the bow carrier's own feud with the
                // defender, the same visibility consult.
                found = world.nearestMobOfKind(position.x(), position.y(), position.z(),
                        MOB_TARGET_FOLLOW_RANGE, MobType.IRON_GOLEM, team);
                if (found != null && !world.clearLine(eyePosition(), found.position())) {
                    found = null; // canTarget's visibility consult
                }
            } else { // the zombie: targetSelector 2's villager hunt
                found = world.nearestMobOfKind(position.x(), position.y(), position.z(),
                        MOB_TARGET_FOLLOW_RANGE, MobType.VILLAGER, team);
            }
            if (found != null && !found.dead()) {
                setMobAttackTarget(found);
            }
        }
        return mobAttackTarget;
    }

    /** @return the squared distance to the other mob's position. */
    private double distanceSquaredTo(MobEntity other) {
        double dx = other.position().x() - position.x();
        double dy = other.position().y() - position.y();
        double dz = other.position().z() - position.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The chase mind's mob-target arm (MeleeAttackGoal's tick against the
     * single target): the eyes and the feet on the prey, the melee arm
     * inside the same reach band the player hunt uses.
     */
    private void tickMobTargetChase(MobEntity target) {
        double dx = target.position().x() - position.x();
        double dy = target.position().y() - position.y();
        double dz = target.position().z() - position.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        mode = Mode.CHASE;
        modeTicks = 0;
        yaw = angleTo(dx, dz);
        headYaw = yaw;
        tickPathFollowing(target.position());
        if (horizontal <= MOB_MELEE_ATTACK_RANGE && Math.abs(dy) <= MELEE_ATTACK_VERTICAL_RANGE
                && attackCooldown == 0) {
            attackCooldown = MELEE_ATTACK_COOLDOWN;
            pendingMeleeAttack = true;
        }
    }

    /**
     * The skeleton's flee-from-wolves (FleeEntityGoal(WolfEntity.class,
     * 6.0F, 1.0, 1.2) at goal priority 3 — above the ranged attack's own
     * 4): a wolf within 6 blocks owns the skeleton's movement whatever
     * else it was doing. The scare itself is computed before the goal walk
     * (the stroll yields through goalHasTarget's read of the pending
     * scare).
     */
    private boolean tickSkeletonWolfFlee() {
        MobEntity wolf = pendingScareWolf;
        if (type != MobType.SKELETON || wolf == null) {
            return false;
        }
        double dx = position.x() - wolf.position().x();
        double dy = position.y() - wolf.position().y();
        double dz = position.z() - wolf.position().z();
        boolean close = dx * dx + dy * dy + dz * dz < FLEE_CLOSE_SQUARED;
        goalWalkSpeed = close ? SKELETON_WOLF_FLEE_CLOSE : SKELETON_WOLF_FLEE_FAR;
        mode = Mode.PANIC;
        modeTicks = 0;
        yaw = angleTo(dx, dz); // directly away from the wolf
        headYaw = yaw;
        clearPath();
        return true;
    }

    /** Whether a hunt target is live this tick (the stroll goal yields):
     *  the player hunt, a live mob-vs-mob target (16b — the single
     *  attackTarget state), or the skeleton's pending wolf-flee (a wolf
     *  never skeleton-flees, so the wolf's angry-clear never sees the
     *  flee arm). */
    boolean goalHasTarget(boolean night) {
        return (targetHuntable && visibleTarget != null) || mobAttackTarget != null
                || pendingScareWolf != null;
    }

    // ------------------- the skeleton's sunlight escape (Slice TF-7b)

    /** The escape's live window (shouldContinue's own law). */
    private boolean escapeSunlightActive;

    /** The shaded cell the escape walks to (canStart's one pick). */
    private Position escapeSunlightTarget;

    /**
     * The escape's gate (EscapeSunlightGoal.canStart lines 28-52 over
     * shouldContinue line 48): the LIVE window answers true whatever the
     * gates — the reference's shouldContinue is the navigation's own
     * {@code !isDone()} alone, the walk runs to its path's end even
     * after the sun sets or the fire dies. A fresh start needs the
     * sunny world AND the burning body AND the open sky above the feet
     * AND a shaded cell the favor family rejects.
     */
    boolean goalEscapeSunlightCanUse() {
        if (type != MobType.SKELETON || skeletonWither) {
            return false; // the skeleton's own registration; the wither
                          // variant lives in the sunless nether
        }
        if (escapeSunlightActive) {
            return true; // shouldContinue: the walk owns the window
        }
        if (!world.isSunny() || !burning()) {
            return false;
        }
        int feetX = (int) Math.floor(position.x());
        int feetY = (int) Math.floor(position.y()); // getShape().minY: the feet
        int feetZ = (int) Math.floor(position.z());
        if (!world.hasSkyAccess(feetX, feetY, feetZ)) {
            return false;
        }
        Position shaded = pickShadedLocation(feetX, feetY, feetZ);
        if (shaded == null) {
            return false;
        }
        escapeSunlightTarget = shaded;
        return true;
    }

    /**
     * The shaded-cell pick (getShadedLocationPos lines 60-76): ten
     * random candidates in the ±10 horizontal / -3..+2 vertical band —
     * the first cell WITHOUT sky access whose pathfinding favor reads
     * negative wins (the monster family's dark preference — a lit
     * cavern's bright cells never qualify).
     */
    private Position pickShadedLocation(int x, int y, int z) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int cx = x + random.nextInt(20) - 10;
            int cy = y + random.nextInt(6) - 3;
            int cz = z + random.nextInt(20) - 10;
            if (!world.hasSkyAccess(cx, cy, cz)
                    && getPathfindingFavor(cx, cy, cz) < 0.0f) {
                return new Position(cx, cy, cz);
            }
        }
        return null;
    }

    /**
     * The escape's walk (start line 54 + shouldContinue line 48): the
     * navigation owns the body at the ctor's 1.0 speed until the path's
     * own end — then the window closes and the gates re-arm on the next
     * selector pass (a still-burning body picks a new shade and walks
     * again).
     */
    void goalEscapeSunlightTick() {
        escapeSunlightActive = true; // start(): the pick landed
        goalWalkSpeed = 1.0; // the ctor's speed parameter
        mode = Mode.WANDER;
        tickPathFollowing(escapeSunlightTarget);
        if (navigation.isDone()) {
            escapeSunlightActive = false; // shouldContinue's end
        }
    }

    /** @return whether the escape window is live (the test pin's read). */
    boolean escapeSunlightLive() {
        return escapeSunlightActive;
    }

    /**
     * The sun-avoidance claim (TF-7f — AvoidSunlightGoal 1:1,
     * reference/1.8.8 net/minecraft/entity/ai/goal/AvoidSunlightGoal):
     * canStart (line 19) is the sunny world ALONE — no burn, no shade
     * hunt, a pure flag goal. The registration is the skeleton's own
     * (SkeletonEntity line 49 — the wither variant never joins, the
     * nether has no sun), and the goal rides the float's additive band:
     * like the reference's flag-free mutex set it runs beside WHATEVER
     * exclusive goal owns the body — the panic, the escape, the stroll
     * — so every path the mind adopts under the sun gets the trim.
     */
    boolean goalAvoidSunlightCanUse() {
        return type == MobType.SKELETON && !skeletonWither && world.isSunny();
    }

    /** start() (line 23): the navigation's avoid-sunlight flag arms. */
    void goalAvoidSunlightTick() {
        navigation.setAvoidSunLight(true);
    }

    /** @return whether the avoid-sunlight trim stands (the test pin's read). */
    boolean avoidingSunlight() {
        return navigation.isAvoidingSunLight();
    }

    /**
     * The village-return walk (WanderThroughVillageGoal 1:1 — the
     * villager's own goal 5 at speed 0.6, VillagerEntity line 315): a
     * body whose cached village exists and whose cell sits OUTSIDE the
     * cached radius walks back toward the village. The gate is the
     * reference's own inverted pair — {@code isInVillage()}
     * (isValidGoalTarget at the mob's own cell) answers TRUE when the
     * cache is UNSET (the -1 short-circuit), so the goal never starts
     * for a village-less villager; it runs only when the cache is set
     * AND the body is beyond the radius. The walk runs until the
     * navigation completes (shouldContinue's own law) — even back
     * inside the radius, the armed path walks on.
     *
     * <p>The target pick is TargetFinder.getTargetAwayFromPosition(16,
     * 7, villageCenter) — the naming is the decompiler's: the offset
     * points from the body TOWARD the village center, and the filter
     * keeps only candidates in that half-plane (the walk homes). Ten
     * random candidates in the ±16/±7 band; the village nudge (the
     * cache-set arm) pulls each surviving candidate further toward the
     * center; the in-band valid gate (within (radius + 16)² of the
     * center, the candidate must fall inside the radius); the FIRST
     * passer wins — the villager's own getPathfindingFavor is the flat
     * 0.0 (PathFinderMobEntity line 29), so no candidate ever outscores
     * the first. No passer: the goal waits (canStart's null).</p>
     *
     * <p>The blaze's own goal 5 (BlazeEntity line 31) is ruled
     * NOT-REACHABLE beside this seat: nothing ever writes a blaze's
     * cache (the nether carries no village collection; the leash path
     * never applies) — an unleashed blaze answers isInVillage() true
     * forever, exactly the engine's shared stroll. The guardian's
     * variant rides its own pin (the swim seat's self-cache).</p>
     *
     * @return whether the goal claims the body this tick (canStart or
     *         the live walk's shouldContinue).
     */
    boolean goalVillageReturnCanUse() {
        if (type != MobType.VILLAGER) {
            return false; // the goal is the villager's own registration
        }
        if (villageReturnActive) {
            // shouldContinue (the goal's own law): the armed path walks
            // to its end — the gate itself is not re-consulted mid-walk
            // (a re-entry keeps the walk until the path completes).
            if (navigation.isDone()) {
                villageReturnActive = false;
                return false;
            }
            return true;
        }
        if (villageRadius <= 0.0f) {
            return false; // no cache: the -1 short-circuit answers inside
        }
        // canStart: inside the cached radius refuses the goal.
        long mobCellX = (long) Math.floor(position.x());
        long mobCellY = (long) Math.floor(position.y());
        long mobCellZ = (long) Math.floor(position.z());
        long radius = (long) this.villageRadius;
        if (cellSquaredDistance(mobCellX, mobCellY, mobCellZ,
                villageCenterX, villageCenterY, villageCenterZ)
                < radius * radius) {
            return false;
        }
        Position target = pickVillageReturnTarget(mobCellX, mobCellY, mobCellZ);
        if (target == null) {
            return false; // no candidate passed: the goal waits
        }
        villageReturnTarget = target;
        villageReturnActive = true;
        mode = Mode.WANDER;
        modeTicks = 0;
        return true;
    }

    /** start()/tick(): the navigation owns the walk at the goal's 0.6 speed. */
    void goalVillageReturnTick() {
        goalWalkSpeed = VILLAGE_RETURN_SPEED;
        mode = Mode.WANDER;
        tickPathFollowing(villageReturnTarget);
        if (navigation.isDone()) {
            villageReturnActive = false; // the walk's life ends; the stroll resumes
        }
    }

    // ------------------------------------- the tameable family (Slice TF-1)

    /** FollowOwnerGoal's min distance: the start band's own edge (10 blocks). */
    private static final double FOLLOW_OWNER_MIN_DISTANCE = 10.0;

    /** The follow's goal speed over the base band ({@code 1.0} both kinds). */
    private static final double FOLLOW_OWNER_SPEED = 1.0 / 0.3;

    /**
     * The teleport's distance gate ({@code !(squaredDistanceTo < 144.0)} —
     * the 12-block edge the failed path must clear before the ring scan).
     */
    private static final double FOLLOW_OWNER_TELEPORT_SQUARED = 144.0;

    /**
     * The pure follow-start probe (the breed goal's yield gate — the
     * reference's own priority order: FollowOwnerGoal 5 over the wolf's
     * AnimalBreedGoal 6 and the ocelot's 9, so a pet whose owner stands
     * beyond the start band never breeds while the follow could claim).
     * Reads only: the tamed kind, the live non-spectator owner, the
     * standing posture, and the 10-block band.
     */
    boolean followOwnerStartable() {
        if (!followOwnerKind() || !wolfTamed() || wolfSitting()) {
            return false;
        }
        PlayerView owner = world.ownerPlayerView(ownerName, ownerUuid);
        if (owner == null || owner.spectator()) {
            return false;
        }
        return squaredDistanceToView(owner) >= FOLLOW_OWNER_MIN_DISTANCE
                * FOLLOW_OWNER_MIN_DISTANCE;
    }

    /** @return whether the kind carries the owner-follow (wolf + ocelot). */
    private boolean followOwnerKind() {
        return type == MobType.WOLF || type == MobType.OCELOT;
    }

    /** @return the squared 3D distance to the view's position. */
    private double squaredDistanceToView(PlayerView view) {
        double dx = view.position().x() - position.x();
        double dy = view.position().y() - position.y();
        double dz = view.position().z() - position.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The follow's stop() (FollowOwnerGoal lines 75-79): the path drops
     * and the swim flag returns — the reference's own unconditional
     * {@code setCanSwim(true)} (the value start() parked is dead in the
     * 1.8.8 source; the write is the law, so the wolf's navigation gains
     * swim paths after its first follow window — the reference's own
     * quirk, carried as-is).
     */
    private void goalFollowOwnerStop() {
        followOwnerActive = false;
        clearPath();
        navigation.setCanSwim(true);
    }

    /**
     * FollowOwnerGoal's canStart/shouldContinue pair (lines 39-65): a
     * live window continues while the path runs, the owner sits beyond
     * the kind's own max band (the wolf's 2, the ocelot's 5), and the
     * body stands; a fresh start needs the owner live, not spectating,
     * the body standing, and the 10-block band cleared. The hunt yields
     * the reference's own way — the wolf's melee band (4) steals from
     * the running follow (5), so a fighting pet stops following until
     * the fight ends.
     */
    boolean goalFollowOwnerCanUse(boolean night) {
        if (!followOwnerKind() || !wolfTamed()) {
            return false;
        }
        PlayerView owner = world.ownerPlayerView(ownerName, ownerUuid);
        if (owner == null) {
            if (followOwnerActive) {
                goalFollowOwnerStop(); // the owner left the world
            }
            return false;
        }
        boolean huntLive = goalHasTarget(night);
        if (followOwnerActive) {
            // The steal: a live hunt owns the body over the running window
            // (the reference's 4-over-5 selector law).
            if (huntLive) {
                goalFollowOwnerStop();
                return false;
            }
            // shouldContinue: the path, the max band, the posture.
            if (wolfSitting() || navigation.isDone()
                    || squaredDistanceToView(owner) <= followOwnerMaxDistanceSquared()) {
                goalFollowOwnerStop();
                return false;
            }
            return true;
        }
        // canStart: the hunt band, the spectator refusal, the sitting
        // refusal, the start band.
        if (huntLive || owner.spectator() || wolfSitting()
                || squaredDistanceToView(owner) < FOLLOW_OWNER_MIN_DISTANCE
                * FOLLOW_OWNER_MIN_DISTANCE) {
            return false;
        }
        // start(): the re-path clock arms, the swim paths drop for the
        // window's life (the pet walks AROUND the water; the failed walk
        // is the teleport's own gate).
        followOwnerActive = true;
        followOwnerTimer = 0;
        navigation.setCanSwim(false);
        return true;
    }

    /**
     * The follow's own max band ({@code maxDistance}² — shouldContinue's
     * edge): the wolf's 2.0F pair (WolfEntity line 61), the ocelot's 5.0F
     * (OcelotEntity line 46).
     */
    private double followOwnerMaxDistanceSquared() {
        return type == MobType.OCELOT ? 5.0 * 5.0 : 2.0 * 2.0;
    }

    /**
     * FollowOwnerGoal's tick (lines 87-117): the eyes on the owner every
     * tick, the re-path on the 10-tick clock, and — when the path fails
     * and the owner stands 12+ blocks away — the teleport's 5x5 RING scan
     * (the center 3x3 refused, the solid-top floor required, two clear
     * cells for the body), landing at the ring cell's center through the
     * plain position write ({@code setPositionAndAngles}, the yaw kept).
     */
    void goalFollowOwnerTick() {
        PlayerView owner = world.ownerPlayerView(ownerName, ownerUuid);
        if (owner == null) {
            return; // the stop lands on the next canUse pass
        }
        // The look control runs first, whatever the posture (the reference's
        // own order — a sitting body still watches its owner walk away).
        headYaw = angleTo(owner.position().x() - position.x(),
                owner.position().z() - position.z());
        if (wolfSitting()) {
            return;
        }
        mode = Mode.WANDER;
        modeTicks = 0;
        goalWalkSpeed = FOLLOW_OWNER_SPEED;
        if (--followOwnerTimer <= 0) {
            followOwnerTimer = 10;
            // The reference's Entity overload (PathNavigation.moveTo lines
            // 87-90 over findPath's own gate, lines 73-75): an airborne
            // body can not update its path — findPath answers null and
            // the moveTo fails WITHOUT a search, so a re-path that lands
            // mid-hop feeds the teleport check exactly like the real
            // vanilla quirk (the pet that blinks to a sprinting owner).
            boolean pathFailed = !onGround || !navigation.moveTo(
                    owner.position().x(), owner.position().y(), owner.position().z(),
                    walkSpeed() * FOLLOW_OWNER_SPEED);
            if (pathFailed && !leashed()
                    && squaredDistanceToView(owner) >= FOLLOW_OWNER_TELEPORT_SQUARED) {
                int baseX = (int) Math.floor(owner.position().x()) - 2;
                int baseZ = (int) Math.floor(owner.position().z()) - 2;
                int baseY = (int) Math.floor(owner.position().y());
                for (int l = 0; l <= 4; l++) {
                    for (int i1 = 0; i1 <= 4; i1++) {
                        if ((l < 1 || i1 < 1 || l > 3 || i1 > 3) // the ring, never the center
                                && world.isSolid(baseX + l + 0.5, baseY - 0.5, baseZ + i1 + 0.5)
                                && !world.isSolid(baseX + l + 0.5, baseY + 0.5, baseZ + i1 + 0.5)
                                && !world.isSolid(baseX + l + 0.5, baseY + 1.5, baseZ + i1 + 0.5)) {
                            // setPositionAndAngles: the plain write — the
                            // feet land at the cell center, the body's own
                            // yaw/pitch kept, the path dropped.
                            position = new Position(baseX + l + 0.5, baseY, baseZ + i1 + 0.5);
                            clearPath();
                            return;
                        }
                    }
                }
            }
        }
        tickNavigationHeading();
    }

    /**
     * The per-tick navigation advance without the shared repath — the
     * goals that own their own re-path cadence (the follow's 10, the
     * perch's 40) call this between their moveTo lands: the walk
     * advances and the path heading publishes, exactly
     * {@link #tickPathFollowing}'s own tail.
     */
    private void tickNavigationHeading() {
        navigation.tick();
        if (navigation.hasTarget) {
            pathYaw = angleTo(navigation.currentTargetX - position.x(),
                    navigation.currentTargetZ - position.z());
            pathYawValid = true;
        } else {
            pathYawValid = false;
        }
    }

    /** The perch scan's range (GoToBlockGoal's own 8). */
    private static final int SIT_ON_BLOCK_RANGE = 8;

    /** The perch walk's speed over the base band ({@code 0.8}). */
    private static final double SIT_ON_BLOCK_SPEED = 0.8 / 0.3;

    /**
     * OcelotSitOnBlockGoal.isValidTarget (lines 54-78) over the cell the
     * scan offers — the PERCH cell itself (the chest/furnace/bed's own
     * cell): strict air above it, then the block's own family — a chest
     * nobody watches, a lit furnace, or the bed's FOOT half. The engine's
     * flattened identifiers carry the facings as suffixes (chest_north,
     * furnace_lit_east), so the families match on their prefixes; the
     * bed's head half (bed_head and its facings) refuses.
     */
    private boolean isSitPerchAt(int x, int y, int z) {
        var above = world.blockIdAt(x, y + 1, z);
        if (above == null || !above.value().equals("air")) {
            return false; // isValidTarget's first gate: isAir(pos.up())
        }
        var block = world.blockIdAt(x, y, z);
        if (block == null) {
            return false;
        }
        String value = block.value();
        if (value.equals("chest") || value.startsWith("chest_")) {
            return world.containerViewersAt(x, y, z) < 1;
        }
        if (value.equals("furnace_lit") || value.startsWith("furnace_lit_")) {
            return true;
        }
        return (value.equals("bed") || value.startsWith("bed_"))
                && !value.startsWith("bed_head");
    }

    /**
     * OcelotSitOnBlockGoal.canStart/shouldContinue over GoToBlockGoal's
     * own pair (lines 22-33 + 36-44): the tamed standing cat gates the
     * whole goal; a live window continues while the approach clock stays
     * inside the band and the perch still stands; a closed window waits
     * out the {@code 200 + nextInt(200)} cadence, then runs the reference's
     * own ring scan — the ±8 spiral at the y-1/y+0/y-2 bands, the first
     * valid perch answering the target.
     */
    boolean goalSitOnBlockCanUse() {
        if (type != MobType.OCELOT || !wolfTamed() || wolfSitting()) {
            return false;
        }
        if (sitOnBlockHasTarget) {
            // shouldContinue: the window's own clock band and the live perch.
            if (sitOnBlockActiveTicks >= -sitOnBlockMaxTicks
                    && sitOnBlockActiveTicks <= 1200
                    && isSitPerchAt(sitOnBlockX, sitOnBlockY, sitOnBlockZ)) {
                return true;
            }
            // stop(): the perch's own stand-down — the window ends, the
            // body stands (the reference's setSitting(false)).
            sitOnBlockHasTarget = false;
            setWolfSitting(false);
            return false;
        }
        if (sitOnBlockCooldown > 0) {
            sitOnBlockCooldown--;
            return false;
        }
        sitOnBlockCooldown = 200 + random.nextInt(200);
        int cx = (int) Math.floor(position.x());
        int cy = (int) Math.floor(position.y());
        int cz = (int) Math.floor(position.z());
        // GoToBlockGoal.findNearestBlock's own spiral (the rabbit's garden
        // scan carries the same walk): k the y band (0, 1, -1 → y-1, y+0,
        // y-2), l the ring radius, i1/j1 the ring's own cross walk.
        for (int k = 0; k <= 1; k = k > 0 ? -k : 1 - k) {
            for (int l = 0; l < SIT_ON_BLOCK_RANGE; l++) {
                for (int i1 = 0; i1 <= l; i1 = i1 > 0 ? -i1 : 1 - i1) {
                    for (int j1 = i1 < l && i1 > -l ? l : 0; j1 <= l; j1 = j1 > 0 ? -j1 : 1 - j1) {
                        int x = cx + i1;
                        int y = cy + k - 1;
                        int z = cz + j1;
                        if (isSitPerchAt(x, y, z)) {
                            sitOnBlockX = x;
                            sitOnBlockY = y;
                            sitOnBlockZ = z;
                            sitOnBlockHasTarget = true;
                            // GoToBlockGoal.start (lines 41-45): the first
                            // walk arms at the perch's up-center, the clock
                            // starts, and the window's own bound rolls
                            // {@code nextInt(nextInt(1200) + 1200) + 1200}.
                            navigation.moveTo(x + 0.5, y + 1.0, z + 0.5,
                                    walkSpeed() * SIT_ON_BLOCK_SPEED);
                            sitOnBlockActiveTicks = 0;
                            sitOnBlockMaxTicks = random.nextInt(random.nextInt(1200) + 1200) + 1200;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * OcelotSitOnBlockGoal.tick over GoToBlockGoal.tick (lines 52-63):
     * the walk to the perch's up-center with the 40-tick re-path and the
     * approach clock counting, then the perch itself — the standing cell
     * (the squared distance to the up-center within 1.0) sits the body
     * once and counts the window down. The reference's own overlay: the
     * walk keeps the body standing (setSitting(false) every walking
     * tick), the perch sits it (setSitting(true) at the arrival) — the
     * wire's state-16 bit rides the manager's flip detection.
     */
    void goalSitOnBlockTick() {
        double tx = sitOnBlockX + 0.5;
        double ty = sitOnBlockY + 1.0;
        double tz = sitOnBlockZ + 0.5;
        double dx = tx - position.x();
        double dy = ty - position.y();
        double dz = tz - position.z();
        if (dx * dx + dy * dy + dz * dz > 1.0) {
            // The approach (GoToBlockGoal.tick's walk arm).
            sitOnBlockReached = false;
            sitOnBlockActiveTicks++;
            setWolfSitting(false); // the reference's own walking write
            headYaw = angleTo(dx, dz);
            mode = Mode.WANDER;
            modeTicks = 0;
            goalWalkSpeed = SIT_ON_BLOCK_SPEED;
            if (sitOnBlockActiveTicks % 40 == 0) {
                navigation.moveTo(tx, ty, tz, walkSpeed() * SIT_ON_BLOCK_SPEED);
            }
            tickNavigationHeading();
            return;
        }
        // The perch (hasReachedTarget): the window counts down, the body
        // sits — the reference's own one-shot write.
        sitOnBlockReached = true;
        sitOnBlockActiveTicks--;
        if (!wolfSitting()) {
            setWolfSitting(true);
        }
    }

    /**
     * The follow goal's own test read: the navigation's swim flag (the
     * window's law — off while following, the reference's unconditional
     * true at the stop).
     */
    boolean navigationCanSwim() {
        return navigation.canSwim();
    }

    /**
     * PounceAtTargetGoal 1:1 (lines 19-42) on the chase arms — the wolf's
     * own 0.4F leap (WolfEntity line 59, goal 3 over the melee's 4) and
     * the ocelot's 0.3F (OcelotEntity line 48, goal 7 over the attack's
     * 8): a grounded body whose target sits 2-4 blocks out rolls the
     * 1-in-5, then leaps — the horizontal charge at the target's unit
     * bearing ({@code d/f * 0.4 + 20% of the current motion}) with the
     * kind's own vertical impulse. The airborne window holds the MOVE
     * control (the reference's selector stops the melee goal for the
     * flight — the chase walk resumes at the landing), and a body in
     * fluid never leaps (the swim goal owns the JUMP control at its
     * higher band).
     *
     * @return true when the pounce owns the movement this tick.
     */
    boolean tickPounceAtTarget(Position target) {
        if (type != MobType.WOLF && type != MobType.OCELOT) {
            return false;
        }
        if (pounceAirborne) {
            if (onGround) {
                pounceAirborne = false; // shouldContinue's own end: the landing
                return false;
            }
            return true; // the flight holds the walk; the body carries the leap
        }
        double dx = target.x() - position.x();
        double dy = target.y() - position.y();
        double dz = target.z() - position.z();
        double squared = dx * dx + dy * dy + dz * dz;
        if (squared < 4.0 || squared > 16.0 || !onGround || goalInFluid()) {
            return false;
        }
        if (random.nextInt(5) != 0) {
            return false; // the 1-in-5 roll
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) {
            return false; // a stacked-vertical target has no leap bearing (the NaN guard)
        }
        double speed = type == MobType.WOLF ? 0.4F : 0.3F;
        velocityX += dx / horizontal * 0.5 * 0.8F + velocityX * 0.2F;
        velocityZ += dz / horizontal * 0.5 * 0.8F + velocityZ * 0.2F;
        velocityY = speed;
        onGround = false;
        pounceAirborne = true;
        return true;
    }

    /**
     * The candidate pick (TargetFinder.getTarget over the village
     * offset, lines 26-88): ten random candidates in the ±16/±7 band,
     * the half-plane filter toward the village center, the ±8 nudge the
     * cache-set arm applies, the in-band valid gate — the first passer
     * wins (the villager's flat favor).
     */
    private Position pickVillageReturnTarget(long mobCellX, long mobCellY, long mobCellZ) {
        // getTargetAwayFromPosition's startOffset: the village center
        // minus the body — the half-plane's own axis.
        double offsetX = villageCenterX - mobCellX;
        double offsetZ = villageCenterZ - mobCellZ;
        // flag1 (the in-band valid gate, lines 34-40): within
        // (radius + 16)² of the center (the + 4.0 term included), every
        // candidate must fall INSIDE the village radius.
        boolean inBand = cellSquaredDistance(mobCellX, mobCellY, mobCellZ,
                villageCenterX, villageCenterY, villageCenterZ) + 4L
                < (long) (villageRadius + VILLAGE_RETURN_RANGE)
                  * (long) (villageRadius + VILLAGE_RETURN_RANGE);
        for (int attempt = 0; attempt < 10; attempt++) {
            int l = random.nextInt(2 * VILLAGE_RETURN_RANGE + 1) - VILLAGE_RETURN_RANGE;
            int k1 = random.nextInt(2 * VILLAGE_RETURN_VERTICAL + 1) - VILLAGE_RETURN_VERTICAL;
            int i1 = random.nextInt(2 * VILLAGE_RETURN_RANGE + 1) - VILLAGE_RETURN_RANGE;
            // The half-plane filter (line 52): a candidate against the
            // center's direction is refused.
            if (l * offsetX + i1 * offsetZ < 0.0) {
                continue;
            }
            // The village nudge (lines 55-67, the cache-set arm): pull
            // the candidate FURTHER toward the center by up to 8.
            if (mobCellX > villageCenterX) {
                l -= random.nextInt(VILLAGE_RETURN_RANGE / 2);
            } else {
                l += random.nextInt(VILLAGE_RETURN_RANGE / 2);
            }
            if (mobCellZ > villageCenterZ) {
                i1 -= random.nextInt(VILLAGE_RETURN_RANGE / 2);
            } else {
                i1 += random.nextInt(VILLAGE_RETURN_RANGE / 2);
            }
            int candidateX = (int) mobCellX + l;
            int candidateY = (int) mobCellY + k1;
            int candidateZ = (int) mobCellZ + i1;
            if (inBand) {
                long r = (long) villageRadius;
                if (cellSquaredDistance(candidateX, candidateY, candidateZ,
                        villageCenterX, villageCenterY, villageCenterZ) >= r * r) {
                    continue; // isValidGoalTarget refuses the outside cell
                }
            }
            // The villager's flat 0.0 favor: the first passer wins.
            return new Position(candidateX, candidateY, candidateZ);
        }
        return null;
    }

    // -------------------------- the stroll family's favor pick (Slice TF-6)

    /**
     * The pathfinder's favor (the reference's {@code getPathfindingFavor}
     * family, the ML-100 favor family's landing): how much a body likes a
     * cell as a stroll destination — the score TargetFinder's best-of-10
     * walk compares.
     *
     * <ul>
     * <li>the base (PathFinderMobEntity lines 29-31): the flat 0.0 — the
     * villager, the golem pair, the squid, the bat (their own wander
     * models never consult it anyway).</li>
     * <li>the monster family (MonsterEntity lines 105-109): {@code 0.5 -
     * brightness} — monsters stroll toward the DARK (the brightness
     * table's own curve puts raw light 12 at 0.5, so any cell dimmer
     * than that scores positive).</li>
     * <li>the animal family (AnimalEntity lines 72-75): the grass floor
     * scores 10.0, every other cell {@code brightness - 0.5} — animals
     * strongly prefer strolling onto grass, and beyond it the light.</li>
     * <li>the giant (GiantEntity lines 27-29): the family's famous
     * INVERTED quirk — {@code brightness - 0.5}, the monster law flipped:
     * the giant strolls toward the LIGHT.</li>
     * <li>the silverfish (SilverfishEntity lines 105-108): the stone
     * floor scores 10.0 (the infest family's own pull), else the
     * monster law.</li>
     * <li>the guardian (GuardianEntity lines 238-243): the water cell
     * scores {@code 10.0 + brightness - 0.5}, else the monster law (the
     * land arm the engine's own swim wander never reads — carried whole
     * for the dispatch's own law).</li>
     * </ul>
     */
    float getPathfindingFavor(int x, int y, int z) {
        if (type == MobType.GIANT) {
            // GiantEntity lines 27-29: the inverted law.
            return world.brightness(x, y, z) - 0.5f;
        }
        if (type == MobType.SILVERFISH) {
            if (isBlockBelow(x, y, z, "minecraft:stone")) {
                return 10.0f; // SilverfishEntity line 107's stone floor
            }
            return 0.5f - world.brightness(x, y, z);
        }
        if (type == MobType.GUARDIAN) {
            if (world.inFluid(x + 0.5, y + 0.5, z + 0.5)) {
                return 10.0f + world.brightness(x, y, z) - 0.5f;
            }
            return 0.5f - world.brightness(x, y, z);
        }
        if (type.hostile) {
            // MonsterEntity lines 105-109: the dark preference.
            return 0.5f - world.brightness(x, y, z);
        }
        if (isAnimalFamily(type)) {
            if (isBlockBelow(x, y, z, "minecraft:grass_block")) {
                return 10.0f; // AnimalEntity line 74's grass floor
            }
            return world.brightness(x, y, z) - 0.5f;
        }
        return 0.0f; // PathFinderMobEntity lines 29-31: the base flat
    }

    /** @return whether the cell below the candidate is the given block. */
    private boolean isBlockBelow(int x, int y, int z, String identifier) {
        net.zaminmc.torch.util.Identifier id = world.blockIdAt(x, y - 1, z);
        return id != null && identifier.equals(id.toString());
    }

    /** The AnimalEntity family (the reference's own subclass set). */
    private static boolean isAnimalFamily(MobType kind) {
        return switch (kind) {
            case PIG, COW, CHICKEN, SHEEP, RABBIT, WOLF, OCELOT, HORSE, MOOSHROOM -> true;
            default -> false;
        };
    }

    /**
     * The stroll's candidate pick (TargetFinder.getTarget lines 26-88 over
     * the plain stroll's {@code startOffset == null} — no half-plane
     * filter): ten random candidates in the ±10/±7 band, the HIGHEST
     * favor wins (the flat {@code f = -99999.0F} seed, the strict
     * {@code >} compare — an earlier candidate wins ties).
     *
     * <p>The village arms (lines 43-45 + 55-67): a body with a live
     * village cache (the villager's own; the reference's leash path never
     * applies here) gets its candidates NUDGED toward the village center
     * — {@code horizontalRange > 1} is the 10-band's own gate — and a
     * body inside the {@code radius + range} band (the {@code +4.0} term
     * included) only accepts candidates INSIDE the cached radius
     * (isValidGoalTarget, PathFinderMobEntity line 46).</p>
     *
     * @return whether a candidate won (the stroll's canStart null gate).
     */
    private boolean pickStrollTarget() {
        int mobCellX = (int) Math.floor(position.x());
        int mobCellY = (int) Math.floor(position.y());
        int mobCellZ = (int) Math.floor(position.z());
        boolean inVillage = villageRadius > 0.0f; // inVillage(): the cache is set
        boolean inBand = false;
        if (inVillage) {
            // flag1 (lines 43-45): the body's own distance to the center
            // (+4.0) against the radius + the horizontal range.
            double d0 = cellSquaredDistance(mobCellX, mobCellY, mobCellZ,
                    villageCenterX, villageCenterY, villageCenterZ) + 4.0;
            double d1 = villageRadius + STROLL_RANGE;
            inBand = d0 < d1 * d1;
        }
        float best = -99999.0f;
        boolean found = false;
        int bestX = 0;
        int bestY = 0;
        int bestZ = 0;
        for (int attempt = 0; attempt < 10; attempt++) {
            int l = random.nextInt(2 * STROLL_RANGE + 1) - STROLL_RANGE;
            int k1 = random.nextInt(2 * STROLL_VERTICAL + 1) - STROLL_VERTICAL;
            int i1 = random.nextInt(2 * STROLL_RANGE + 1) - STROLL_RANGE;
            // The village nudge (lines 55-67): the cache-set body's
            // candidates pull toward the village center by up to half
            // the horizontal range.
            if (inVillage && STROLL_RANGE > 1) {
                if (mobCellX > villageCenterX) {
                    l -= random.nextInt(STROLL_RANGE / 2);
                } else {
                    l += random.nextInt(STROLL_RANGE / 2);
                }
                if (mobCellZ > villageCenterZ) {
                    i1 -= random.nextInt(STROLL_RANGE / 2);
                } else {
                    i1 += random.nextInt(STROLL_RANGE / 2);
                }
            }
            int candidateX = mobCellX + l;
            int candidateY = mobCellY + k1;
            int candidateZ = mobCellZ + i1;
            // isValidGoalTarget (line 46): the in-band body only accepts
            // candidates inside the cached radius (the -1 short-circuit
            // folded into the inVillage gate above).
            if (!inBand || cellSquaredDistance(candidateX, candidateY, candidateZ,
                    villageCenterX, villageCenterY, villageCenterZ)
                    < (long) villageRadius * (long) villageRadius) {
                float favor = getPathfindingFavor(candidateX, candidateY, candidateZ);
                if (favor > best) {
                    best = favor;
                    bestX = candidateX;
                    bestY = candidateY;
                    bestZ = candidateZ;
                    found = true;
                }
            }
        }
        if (!found) {
            return false; // canStart's null: the goal waits for the next roll
        }
        strollTarget = new Position(bestX, bestY, bestZ);
        return true;
    }

    /** The stroll's own interval (WanderAroundGoal's ctor parameter): 120
     * the family's own default; the silverfish's InfestStoneGoal carries
     * 10 (the infest family's own cadence — it re-picks its den twelve
     * times as often).
     */
    private int strollInterval() {
        return type == MobType.SILVERFISH ? SILVERFISH_STROLL_INTERVAL : STROLL_INTERVAL;
    }

    // The lane's own test surface (package-private, the family's
    // established pattern — the acceptance pins read the pick directly).

    /** @return whether the stroll's navigation walk owns the band (test). */
    boolean isStrollActive() {
        return strollActive;
    }

    /** Runs the TargetFinder pick once (the acceptance pin's direct call). */
    boolean pickStrollTargetForTest() {
        return pickStrollTarget();
    }

    /** @return the stroll's picked target (the acceptance pin's read). */
    Position strollTargetForTest() {
        return strollTarget;
    }

    /**
     * The stroll's speed parameter (the ctor registrations' own numbers,
     * the multiplier over the movement attribute the engine's
     * {@code walkSpeed()} carries): the villager and the iron golem
     * 0.6, the rabbit 0.6, the horse 0.7, the creeper/spider pair and
     * the ocelot 0.8, every other strolling kind 1.0.
     */
    private double strollSpeedMultiplier() {
        return switch (type) {
            case VILLAGER, IRON_GOLEM, RABBIT -> 0.6;
            case HORSE -> 0.7;
            case CREEPER, SPIDER, CAVE_SPIDER, OCELOT -> 0.8;
            default -> 1.0;
        };
    }

    /** The goal's speed parameter (VillagerEntity line 315: 0.6). */
    private static final double VILLAGE_RETURN_SPEED = 0.6;

    /** The goal's horizontal candidate band (16). */
    private static final int VILLAGE_RETURN_RANGE = 16;

    /** The goal's vertical candidate band (7). */
    private static final int VILLAGE_RETURN_VERTICAL = 7;

    void goalTickIdleWander(Position target) {
        if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
            mode = Mode.IDLE; // target lost (or daylight saved the spider)
            modeTicks = 0;
            strollActive = false; // the hunt's walk replaced the stroll's
        }
        switch (mode) {
            case IDLE -> {
                // The historical LookAtPlayer: an idle mob glances at a
                // nearby player now and then — unless the beg owns the look
                // (WolfBegGoal claims the LOOK control for its whole window).
                if (!wolfBegging && target != null && modeTicks % 30 == 0 && random.nextInt(3) == 0) {
                    headYaw = angleTo(target.x() - position.x(), target.z() - position.z());
                }
                // The reference stroll's start gate (WanderAroundGoal
                // .canStart lines 19-42, Slice TF-6): the goal itself must
                // be LIVE first (the §27.3 removal's behavioral consult —
                // a removed stroll never starts, whatever the tail call
                // that reaches here when the selector did not claim),
                // then a body 100+ ticks without a player nearby never
                // strolls (the despawn clock's own pre-despawn band — it
                // is heading out), then the interval roll (120 the
                // family's own; the silverfish's InfestStoneGoal carries
                // 10), then TargetFinder's pick — ten candidates, the
                // best favor wins; no passer, no walk.
                if (goals.hasBuiltIn("stroll")
                        && farFromPlayerTicks() < 100
                        && random.nextInt(strollInterval()) == 0
                        && pickStrollTarget()) {
                    strollActive = true;
                    goalWalkSpeed = strollSpeedMultiplier();
                    mode = Mode.WANDER;
                    modeTicks = 0;
                }
            }
            case WANDER -> {
                if (strollActive) {
                    // shouldContinue (line 66): the navigation owns the
                    // walk's life — the path runs to its end (or dies at
                    // a wall), then the body stands back down.
                    goalWalkSpeed = strollSpeedMultiplier();
                    tickPathFollowing(strollTarget);
                    if (navigation.isDone()) {
                        strollActive = false;
                        mode = Mode.IDLE;
                        modeTicks = 0;
                    }
                    return;
                }
                if (modeTicks >= WANDER_TICKS + random.nextInt(WANDER_TICKS)) {
                    mode = Mode.IDLE;
                    modeTicks = 0;
                }
            }
            default -> {
                mode = Mode.IDLE;
                modeTicks = 0;
                strollActive = false; // another mind owned the body; the stroll re-picks
            }
        }
    }

    /** The creeper goal: close, stop at arm's length, prime, detonate. */
    private void tickCreeperMind(Position target, double horizontal, double dy) {
        yaw = angleTo(target.x() - position.x(), target.z() - position.z());
        headYaw = yaw;
        if (mode == Mode.FUSE) {
            // CreeperEntity.tick lines 105-107: the ignited flag forces the
            // fuse direction 1 every tick — an ignited swell NEVER cools,
            // target or no target (the goal-primed swell cools on abort).
            if (!creeperIgnited
                    && (horizontal > CREEPER_ABORT_RANGE || Math.abs(dy) > CREEPER_ABORT_RANGE)) {
                mode = Mode.IDLE; // the target slipped away: the fuse dies out
                modeTicks = 0;
                clearPath();
                return;
            }
            if (modeTicks >= creeperFuseTime) {
                pendingExplosion = true;
            }
            return; // priming creepers stand still (the historical swell)
        }
        if (horizontal <= CREEPER_PRIME_RANGE && Math.abs(dy) <= CREEPER_PRIME_VERTICAL_RANGE) {
            mode = Mode.FUSE;
            modeTicks = 0;
            clearPath();
            return;
        }
        tickPathFollowing(target); // the feet route around walls, the eyes stay on
    }

    /** The skeleton goal: hold the shooting band, shoot on a clear line. */
    private void tickSkeletonMind(Position target, double horizontal, double dy) {
        yaw = angleTo(target.x() - position.x(), target.z() - position.z());
        headYaw = yaw;
        if (horizontal > SKELETON_TOO_FAR) {
            mode = Mode.CHASE; // close the gap
            tickPathFollowing(target);
        } else if (horizontal < SKELETON_TOO_CLOSE) {
            mode = Mode.WANDER; // re-rolled below as a retreat heading
            yaw = angleTo(position.x() - target.x(), position.z() - target.z());
            headYaw = yaw; // the body backs off, the eyes stay on the target
            clearPath();
        } else {
            mode = Mode.STRAFE; // in the band: hold and shoot
            clearPath();
        }
        if (attackCooldown == 0
                && horizontal <= SKELETON_SHOOT_RANGE
                && world.clearLine(eyePosition(), target)) {
            attackCooldown = SKELETON_ATTACK_COOLDOWN + random.nextInt(20);
            pendingRangedShot = true;
            rangedTarget = target;
        }
    }

    /** @return and clears whether the melee hunter should land a hit this tick. */
    public boolean consumePendingMeleeAttack() {
        boolean value = pendingMeleeAttack;
        pendingMeleeAttack = false;
        return value;
    }

    /** Set when a hunter's cooldown elapsed inside reach; consumed by the manager. */
    private boolean pendingMeleeAttack;

    /** @return the eye point of the body (the ranged origin). */
    public Position eyePosition() {
        return new Position(position.x(), position.y() + eyeHeight(), position.z());
    }

    /**
     * @return the body's height (the kind's own, or the slime family's
     * size-scaled {@code 0.51000005 * size}).
     */
    public double bodyHeight() {
        if (type.slimeFamily()) {
            return 0.51000005 * slimeSize;
        }
        if (type == MobType.GUARDIAN && guardianElder) {
            // setElder line 139: the elder's own 1.9975 hull.
            return GUARDIAN_ELDER_SIZE;
        }
        if (type == MobType.SKELETON && skeletonWither) {
            // setType line 296: the wither variant's taller hull.
            return SKELETON_WITHER_HEIGHT;
        }
        return type.height;
    }

    /**
     * @return the body's width (the kind's own, or the slime family's
     * size-scaled {@code 0.51000005 * size}).
     */
    public double bodyWidth() {
        if (type.slimeFamily()) {
            return 0.51000005 * slimeSize;
        }
        if (type == MobType.GUARDIAN && guardianElder) {
            // setElder line 139: the elder's own 1.9975 hull.
            return GUARDIAN_ELDER_SIZE;
        }
        if (type == MobType.SKELETON && skeletonWither) {
            // setType line 296: the wither variant's wider hull.
            return SKELETON_WITHER_WIDTH;
        }
        return type.width;
    }

    /**
     * @return the eye height (the generic 0.85 share of the body, or the
     * slime's own {@code 0.625 * height} — getEyeHeight line 208).
     */
    private double eyeHeight() {
        if (type.slimeFamily()) {
            return 0.625 * bodyHeight();
        }
        if (type == MobType.GIANT) {
            // GiantEntity.getEyeHeight line 18: the flat 10.440001F — not
            // the generic 0.85 share (9.18) the default would give.
            return 10.440001;
        }
        if (type == MobType.GUARDIAN) {
            // GuardianEntity.getEyeHeight line 234: the mid-body eye —
            // height * 0.5 (the elder's own hull scales it).
            return bodyHeight() * 0.5;
        }
        if (type == MobType.ENDERMITE) {
            // EndermiteEntity.getEyeHeight line 57: the flat 0.1F.
            return 0.1;
        }
        if (type == MobType.SNOW_GOLEM) {
            // SnowGolemEntity.getEyeHeight line 97: the flat 1.7F.
            return 1.7;
        }
        if (type == MobType.SKELETON) {
            // getEyeHeight line 314: the archer's own 1.74; the wither
            // variant keeps the generic share of its OWN taller hull
            // (2.535 * 0.85 — super.getEyeHeight reads the live size).
            return skeletonWither ? bodyHeight() * 0.85 : 1.74;
        }
        if (type == MobType.CAVE_SPIDER) {
            // CaveSpiderEntity.getEyeHeight line 53: the flat 0.45F — not
            // the generic 0.85 share (0.425) the default would give.
            return 0.45;
        }
        if (type == MobType.SILVERFISH) {
            // SilverfishEntity.getEyeHeight line 45: the flat 0.1F.
            return 0.1;
        }
        return type.height * 0.85;
    }

    /**
     * The eye height as the world query exposes it (Slice 59 — the
     * wither's heads aim at {@code quarry.y + eyeHeight * 0.5} and the
     * sight ray runs eye to eye; the engine's query resolves the quarry's
     * own eye model through this surface).
     */
    public double eyeHeightForQuery() {
        return eyeHeight();
    }

    /**
     * The noClip family (Entity.noClip — the dragon's own body, the flight
     * model's Slice-63 arm): a noClip body never registers a collision and
     * {@link #isInWall()} answers false for it exactly like the reference's
     * first line.
     */
    public boolean noClipBody() {
        return type == MobType.ENDER_DRAGON;
    }

    /**
     * The in-wall consult, 1:1 (Entity.isInWall lines 1267-1287): the
     * eight-point survey around the eye — the y pair at eye {@literal +/-}
     * 0.05, the x/z pairs at {@literal +/-} 0.4 * width — each DISTINCT cell
     * read once (the mutable-position dedup), the cell view-blocking
     * (isCube over the movement-blocking material). A noClip body answers
     * false outright.
     */
    public boolean isInWall() {
        if (noClipBody()) {
            return false;
        }
        double eye = eyeHeight();
        double w = bodyWidth();
        int lastJ = Integer.MIN_VALUE;
        int lastK = Integer.MIN_VALUE;
        int lastL = Integer.MIN_VALUE;
        for (int i = 0; i < 8; i++) {
            // MathHelper.floor(y + ((i >> 0) % 2 - 0.5F) * 0.1F + eyeHeight)
            int j = (int) Math.floor(position.y() + ((i & 1) - 0.5) * 0.1 + eye);
            // MathHelper.floor(x + ((i >> 1) % 2 - 0.5F) * width * 0.8F)
            int k = (int) Math.floor(position.x() + (((i >> 1) & 1) - 0.5) * w * 0.8);
            // MathHelper.floor(z + ((i >> 2) % 2 - 0.5F) * width * 0.8F)
            int l = (int) Math.floor(position.z() + (((i >> 2) & 1) - 0.5) * w * 0.8);
            if (lastK != k || lastL != l || lastJ != j) {
                lastJ = j;
                lastK = k;
                lastL = l;
                if (world.viewBlockingAt(k + 0.5, j + 0.5, l + 0.5)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The pinned body's own decay (Slice 92 — the reference's
     * non-controlled arm, LivingEntity lines 1415-1419: {@code velocity
     * *= 0.98} per component while the mind and the movement stay gated).
     */
    private void decayVelocity() {
        velocityX *= 0.98;
        velocityY *= 0.98;
        velocityZ *= 0.98;
    }

    /**
     * The swept-box movement application (the physics-solver slice):
     * the reference's own {@code Entity.move} — the axis-by-axis clip
     * against every collision box the swept shape touches, with the
     * step-height retry — replacing the point-query walk's stop-at-
     * destination stand-in for the bodies whose world provides the
     * collision surface. The caller keeps its own velocity model (the
     * damping and the walk are already folded into the passed motion);
     * the solver's zeroing signals land back on the velocities, the
     * onGround flag flips on the downward clip alone, and the
     * horizontal-collision signal answers for the detour arms.
     *
     * @return the move's outcome, or null when the world provides no
     * collision surface (the caller takes its legacy walk)
     */
    /**
     * The climb-out assist's consult (the reference's {@code canMove(velocityX,
     * velocityY + 0.6F - y + d1, velocityZ)} over Entity.canMove lines
     * 381-384 and doesNotCollide lines 386-387): whether the body's own box,
     * offset to the exit ledge — the pre-move y + 0.6 band, carried along the
     * current velocity terms — collides with NO block (the engine's collision
     * gather over the offset box) and holds NO liquid (the span cells'
     * fluid reads). The body hops out only onto a dry, free ledge.
     */
    private boolean canOccupyClimbOut(double preMoveY, double appliedX, double appliedZ) {
        double dy = velocityY + 0.6 - position.y() + preMoveY;
        double w = bodyWidth() / 2.0;
        double h = bodyHeight();
        net.zaminmc.torch.server.physics.Aabb box =
                new net.zaminmc.torch.server.physics.Aabb(
                        position.x() - w + appliedX, position.y() + dy, position.z() - w + appliedZ,
                        position.x() + w + appliedX, position.y() + h + dy, position.z() + w + appliedZ);
        // doesNotCollide's block side — the engine's own gather (an empty
        // result means the offset box clips nothing).
        net.zaminmc.torch.server.physics.EntityCollisionWorld surface = world.collisionWorld();
        if (surface == null) {
            return false; // no collision surface: the stub worlds' walks
        }
        if (!net.zaminmc.torch.server.physics.EntityCollisions
                .gatherBlockBoxes(surface, box, true).isEmpty()) {
            return false;
        }
        // doesNotCollide's liquid side (containsLiquid): any fluid cell in
        // the offset box's span blocks the exit.
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.inFluid(x + 0.5, y + 0.5, z + 0.5)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private net.zaminmc.torch.server.physics.EntityMoveSolver.Result solverMove(
            double moveX, double moveY, double moveZ, double stepHeight) {
        net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld =
                world.collisionWorld();
        if (collisionWorld == null) {
            return null;
        }
        net.zaminmc.torch.server.physics.EntityMoveSolver.Body body =
                new net.zaminmc.torch.server.physics.EntityMoveSolver.Body(
                        position.x(), position.y(), position.z(),
                        type.width, type.height);
        body.onGround = onGround;
        net.zaminmc.torch.server.physics.EntityMoveSolver.Result result =
                net.zaminmc.torch.server.physics.EntityMoveSolver.move(collisionWorld,
                        body, moveX, moveY, moveZ,
                        new net.zaminmc.torch.server.physics.EntityMoveSolver.MoveLaw(
                                stepHeight, false, false, false));
        position = new Position(body.x, body.y, body.z);
        onGround = result.onGround();
        if (result.zeroVelocityX()) {
            velocityX = 0.0;
        }
        if (result.zeroVelocityZ()) {
            velocityZ = 0.0;
        }
        if (result.zeroVelocityY()) {
            velocityY = 0.0;
        }
        return result;
    }

    /**
     * The piston push (the VIII13 slice — the reference's
     * {@code Entity.move(amount * facing)} over the moving carrier's
     * interpolated box): the IMMEDIATE, collision-clamped position shift
     * with the body's own step height (the horse's 1.0, the walkers'
     * 0.5), outside the tick's velocity integration (the NoAI freeze
     * never eats a push — the reference's move is direct). Returns
     * whether the world provided the collision surface (the caller's
     * stand-down window).
     */
    public boolean pushBy(double dx, double dy, double dz) {
        // The step walk's pre-move anchor (the reference's Entity.move
        // runs the step arm over every applied move — the piston shove
        // and the explosion impulse step exactly like a walk).
        double preMoveX = position.x();
        double preMoveY = position.y();
        double preMoveZ = position.z();
        boolean pushed = solverMove(dx, dy, dz,
                type == MobType.HORSE ? HORSE_STEP_HEIGHT : WALK_STEP_HEIGHT) != null;
        stepSoundWalk(preMoveX, preMoveY, preMoveZ);
        return pushed;
    }

    /**
     * The step-sound walk (Entity.move lines 594-624, 1:1): the reference's
     * own footstep cadence, run after every applied move. The moved delta
     * accumulates into {@link #distanceMoved} at the 0.6 scale — the
     * vertical term counting only over a ladder ({@code block1 != LADDER}
     * zeroes d13 elsewhere) — and the {@link #blocksWalkedOn} integer
     * crossing fires over a non-air feet cell: the swim arm first (the
     * velocity-scaled {@code game.neutral.swim} — a body wading the
     * riverbed splashes at the footstep cadence), then the footstep
     * itself ({@code playStepSound} lines 691-698: the snow-layer-above
     * override reads the SNOW family, a liquid feet cell refuses — the
     * water arm already splashed — and every other block plays its
     * family's stepping name at {@code volume * 0.15}).
     *
     * <p>The {@code makesSteps()} gate 1:1: the reference's seven
     * overrides are the TNT, the falling block, the minecart, the boat
     * (not mob bodies here — structurally excluded), the guardian, the
     * endermite, and the silverfish. The passenger exclusion
     * ({@code vehicle == null}) has no engine surface — the engine's
     * mobs never ride (a jockey's mount carries the steps). The
     * sneaking-player skip ({@code !flag}) is player-only in the
     * reference — a mob never skips.</p>
     *
     * <p>The band's own consult — {@code onSteppedOn} (Entity.move lines
     * 602-604) — IS 1.8.8-live: THREE reference classes override it
     * (this step path's earlier no-op claim was STALE, re-pinned by the
     * fall-path family's fifteenth pass). {@code SlimeBlock.onSteppedOn}
     * (lines 42-50) carries the horizontal damping; {@code
     * RedstoneOreBlock.onSteppedOn} (lines 40-43) carries the walk-on
     * lighting — staged here as the step-spark intent (the engine's
     * listener performs the whole interact: the spark band and the unlit
     * form's swap); {@code StairsBlock.onSteppedOn} (lines 468-469)
     * delegates to the base block — a 1.8.8 no-op in practice (no
     * vanilla stair base is a slime or an ore), so the stairs carry no
     * arm. The consult fires per move while onGround, BEFORE the
     * distance-walked band — standing still on the ore lights it on
     * the first tick (the reference's own order).</p>
     */
    private void stepSoundWalk(double preX, double preY, double preZ) {
        if (!makesSteps()) {
            return;
        }
        // The feet cell (Entity.move line 568-573): floor(y - 0.2), and
        // when that cell is air the fence/wall/gate consult takes the
        // cell BELOW as the stepped-on block (a body standing in the
        // 1.5-tall fence's band reads the fence as its ground).
        int feetX = (int) Math.floor(position.x());
        int feetY = (int) Math.floor(position.y() - 0.2);
        int feetZ = (int) Math.floor(position.z());
        net.zaminmc.torch.util.Identifier ground = world.blockIdAt(feetX, feetY, feetZ);
        if (isAir(ground)) {
            net.zaminmc.torch.util.Identifier below =
                    world.blockIdAt(feetX, feetY - 1, feetZ);
            if (isFenceLike(below)) {
                ground = below;
                feetY--;
            }
        }
        // The band's own consult (Entity.move lines 602-604): the
        // onSteppedOn dispatch — per move while onGround, BEFORE the
        // distance-walked band below. The redstone ore's walk-on lighting
        // (RedstoneOreBlock.onSteppedOn lines 40-43 over interact
        // lines 51-56) is staged as the step-spark intent; the engine's
        // listener performs the whole interact at the staged cell (the
        // spark band and the unlit form's swap — the trample's own
        // pending shape over this pass's own consult).
        if (onGround && ground != null) {
            String groundName = ground.value();
            if (groundName.equals("redstone_ore")
                    || groundName.equals("lit_redstone_ore")) {
                stepSparkX = feetX;
                stepSparkY = feetY;
                stepSparkZ = feetZ;
                stepSparkPending = true;
            }
        }
        double d12 = position.x() - preX;
        double d13 = onLadderBlock(ground) ? position.y() - preY : 0.0;
        double d14 = position.z() - preZ;
        distanceMoved = (float) (distanceMoved
                + Math.sqrt(d12 * d12 + d13 * d13 + d14 * d14) * 0.6);
        if (distanceMoved > blocksWalkedOn && !isAir(ground)) {
            blocksWalkedOn = (int) distanceMoved + 1;
            // The swim arm (lines 611-620): the velocity-scaled splash at
            // the crossing — sqrt(vx²*0.2 + vy² + vz²*0.2) * 0.35 clamped
            // to 1.0, the pitch walk +-(rand-rand)*0.4 over 1.0. The walk
            // FALLS THROUGH to the footstep (the reference's own shape:
            // a body wading a riverbed over a solid floor splashes AND
            // steps — playStepSound's liquid gate reads the FEET CELL,
            // not the body's water flag).
            if (bodyInWater()) {
                float f = (float) Math.sqrt(velocityX * velocityX * 0.2
                        + velocityY * velocityY
                        + velocityZ * velocityZ * 0.2) * 0.35f;
                if (f > 1.0f) {
                    f = 1.0f;
                }
                pendingSwimSound = new StagedSound("game.neutral.swim", f,
                        1.0f + (random.nextFloat() - random.nextFloat()) * 0.4f);
            }
            // playStepSound (lines 691-698): the snow-layer override reads
            // the SNOW family, the liquid cell refuses (a body swimming
            // over open water never steps — the splash above already
            // answered), everything else plays its family at
            // volume * 0.15.
            if (isSnowLayer(world.blockIdAt(feetX, feetY + 1, feetZ))) {
                pendingStepSound = new StagedSound(
                        net.zaminmc.torch.server.fx.BlockSoundMap.SoundFamily.SNOW.stepping(),
                        net.zaminmc.torch.server.fx.BlockSoundMap.SoundFamily.SNOW.volume() * 0.15f,
                        net.zaminmc.torch.server.fx.BlockSoundMap.SoundFamily.SNOW.pitch());
            } else if (net.zaminmc.torch.server.block.FluidBlocks.kindOf(ground) == null) {
                net.zaminmc.torch.server.fx.BlockSoundMap.SoundFamily family =
                        net.zaminmc.torch.server.fx.BlockSoundMap.familyOf(ground)
                                .orElse(net.zaminmc.torch.server.fx.BlockSoundMap.SoundFamily.STONE);
                pendingStepSound = new StagedSound(family.stepping(),
                        family.volume() * 0.15f, family.pitch());
            }
        }
    }

    /**
     * The reference's {@code makesSteps()} (Entity line 715): true for
     * every body except the seven overrides — the mob kinds among them
     * are the guardian, the endermite, and the silverfish
     * (GuardianEntity 229, EndermiteEntity 51, SilverfishEntity 57; the
     * TNT, the falling block, the minecart, and the boat are not mob
     * bodies on this engine).
     */
    private boolean makesSteps() {
        return type != MobType.GUARDIAN && type != MobType.ENDERMITE
                && type != MobType.SILVERFISH;
    }

    /** Whether the identifier is the empty cell (the reference's Material.AIR). */
    private static boolean isAir(net.zaminmc.torch.util.Identifier id) {
        return id == null || id.value().equals("air");
    }

    /** Whether the identifier is a snow layer (Blocks.SNOW_LAYER). */
    private static boolean isSnowLayer(net.zaminmc.torch.util.Identifier id) {
        return id != null && id.value().equals("snow_layer");
    }

    /** Whether the identifier is the ladder (any facing form — Blocks.LADDER). */
    private static boolean onLadderBlock(net.zaminmc.torch.util.Identifier id) {
        return id != null && (id.value().equals("ladder")
                || id.value().startsWith("ladder_"));
    }

    /**
     * The fence/wall/gate consult (Entity.move lines 573-579): the
     * reference's {@code instanceof FenceBlock || WallBlock ||
     * FenceGateBlock} over the engine's flattened identifiers — the six
     * wood fences and the nether brick fence, the wall pair, and every
     * fence-gate state.
     */
    private static boolean isFenceLike(net.zaminmc.torch.util.Identifier id) {
        if (id == null) {
            return false;
        }
        String value = id.value();
        return value.equals("oak_fence") || value.equals("spruce_fence")
                || value.equals("birch_fence") || value.equals("jungle_fence")
                || value.equals("acacia_fence") || value.equals("dark_oak_fence")
                || value.equals("nether_brick_fence")
                || value.equals("cobblestone_wall") || value.equals("mossy_cobblestone_wall")
                || value.contains("_fence_gate");
    }

    /**
     * The body's own water read (Entity.isInWater over the box): the
     * engine's fluid consult at the body's mid band, water alone — the
     * lava cell refuses the swim arm exactly like the reference (the
     * lava's own loop hiss rides the fizz family elsewhere).
     */
    private boolean bodyInWater() {
        return world.inFluid(position.x(), position.y() + 0.2, position.z())
                && !world.inLava(position.x(), position.y() + 0.2, position.z());
    }

    private void tickBody() {
        // The void (1.8.8 EntityLivingBase): past the kill plane the
        // out-of-world damage lands every tick until death; the body keeps
        // falling because no block below the world is solid.
        if (position.y() < VOID_KILL_Y) {
            hurt(VOID_DAMAGE_PER_TICK);
        }
        // THE EVERY-BODY CONTACT BLOCK (hoisted above the flight families'
        // early returns — the reference carries NO family skip: Entity.tick
        // lines 337-341's lava arm, Entity.move's fire contact + the water
        // douse, LivingEntity.baseTick lines 195-197's suffocation, and the
        // block-collision cactus walk run for every body whatever owns its
        // movement — the walker, the rider, and the flight families alike.
        // The pre-hoist engine's early returns skipped them for the ghast,
        // the dragon, the bat, the squid and the guardian — the family
        // skip the 93/D2 ledger named as the flight families' lava arm.
        // The fire-immune kinds (the ghast, the dragon) ride takeLavaDamage's
        // own exemption; the dragon's noClip body never suffocates.)
        boolean inLava = !dead
                && world.inLava(position.x(), position.y() + 0.2, position.z());
        boolean inFluid = world.inFluid(position.x(), position.y() + 0.2, position.z());
        // Fire contact arms the burn (the historical setFire on
        // collision) — the fire-immune kinds (immuneToFire) never ignite,
        // the wither variant joining them (setType line 291).
        if (!dead && !fireImmune()
                && world.inFire(position.x(), position.y(), position.z())) {
            ignite(FIRE_TICKS);
        }
        // The lava walk (Slice 93/D2 — Entity.tick lines 337-341, the
        // isInLava arm): takeLavaDamage's pair — the fifteen-second arm
        // (setOnFireFor's own max-arm) and the flat 4.0 LAVA-source wound
        // at the hurt-i-frame cadence (the reference's invulnerableTicks/2
        // = 10 window, the same shape the cactus contact rides) — the
        // fire-immune kinds exempt (takeLavaDamage's own gate). The wound
        // itself additionally gated by FIRE_RESISTANCE (LivingEntity lines
        // 645-647's isFire-source consult — a resistant body burns
        // visually but never wounds, exactly the reference's own takeDamage
        // gate). The consult is the body's own mid-band (the y + 0.2
        // single-point convention the vehicle lava walk established). The
        // store's own halving rides the fall walk below (Entity.tick's own
        // split: the halving in the tick, the water zero in the move).
        if (inLava && !fireImmune()) {
            ignite(LAVA_BURN_TICKS);
            if (++lavaDamageTimer >= LAVA_WOUND_CADENCE_TICKS
                    && effectAmplifierPlusOne(
                            net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE) == 0) {
                lavaDamageTimer = 0;
                hurt(LAVA_DAMAGE_PER_WOUND);
            }
        }
        // The in-wall suffocation (LivingEntity.baseTick lines 195-197:
        // isInWall() -> takeDamage(IN_WALL, 1.0) — the bypasses-armor source
        // at the hurt-i-frame cadence, the reference's invulnerableTicks/2
        // = 10 window; the engine's own timer convention the lava arm rides,
        // the first wound one window deep). The dead body and the noClip
        // families exempt; the wall consult is the eight-point eye survey.
        if (!dead && !noClipBody() && isInWall()) {
            if (++suffocationTimer >= SUFFOCATION_CADENCE_TICKS) {
                suffocationTimer = 0;
                hurt(IN_WALL_DAMAGE_PER_WOUND);
            }
        } else {
            suffocationTimer = 0;
        }
        // Cactus contact pricks at the hurt-i-frame rhythm (10 ticks).
        if (!dead && world.touchingCactus(position.x(), position.y(), position.z())
                && ++cactusDamageTimer % 10 == 0) {
            hurt(1.0f);
        }
        // The douse is the WATER consult alone (checkWaterCollisions'
        // own family — Slice 93/D2's split of the shared fluid model: a
        // body in lava keeps its burn, the reference's own shape; the
        // pre-split engine extinguished it because the fluid test covered
        // both kinds).
        if (inFluid && !inLava && fireTicks > 0) {
            extinguish(); // the water douses the burn (the historical rule)
        }
        // The ghast's flight (Slice 16c — FlyingMobEntity): the drift owns
        // the whole vertical + horizontal integration; no gravity, no
        // ground snap, no walk.
        if (type == MobType.GHAST && !dead) {
            // The NoAI pin (Slice 92 — the reference's isLocallyControlled
            // gate covers the flight families too: the drift rides
            // moveRelative, a pinned ghast freezes midair with the *0.98
            // decay, the same arm the walker takes).
            if (noAi) {
                decayVelocity();
                return;
            }
            tickGhastBody();
            integrateFlight();
            return;
        }
        // The dragon's flight (Slice 63 — EnderDragonEntity's noClip body):
        // the mind's steering walk owns the whole integration (the
        // velocity shaping + the move); no gravity, no ground snap, no
        // walk. The contact block above still consults — the fire and
        // lava arms exempt through the dragon's own immunity, the
        // suffocation through its noClip body (the cactus consult alone
        // can reach it, the same block-collision walk every body rides).
        if (type == MobType.ENDER_DRAGON && !dead) {
            onGround = false;
            return;
        }
        // The bat's flight (Slice 16e — BatEntity.tick): the roost snap or
        // the hangPos wander; no gravity, no walk.
        if (type == MobType.BAT && !dead) {
            if (noAi) { // the pin (Slice 92 — the reference gates the bat's own move)
                decayVelocity();
                return;
            }
            tickBatBody();
            return;
        }
        // The squid's swim (Slice 16e — SquidEntity.mobTick): the tentacle
        // stroke owns the velocity; the position integrates it directly
        // (no gravity, no walk).
        if (type == MobType.SQUID && !dead) {
            if (noAi) { // the pin (Slice 92 — the stroke rides moveRelative)
                decayVelocity();
                return;
            }
            // The swim gates on WATER alone (SquidEntity's own isInWater
            // override — the WATER material consult, never the shared
            // fluid): a squid in LAVA is not swimming, it is the stranded
            // fall plus the lava contact above (the reference's own shape
            // — the stroke arms sit inside the inWater branch, the else
            // arm the fall).
            boolean squidInWater = !world.inLava(position.x(), position.y() + 0.2, position.z())
                    && world.inFluid(position.x(), position.y() + 0.2, position.z());
            tickSquidBody(squidInWater);
            position = new Position(position.x() + velocityX, position.y() + velocityY,
                    position.z() + velocityZ);
            onGround = false;
            return;
        }
        // The guardian's swim (Slice 17a — GuardianEntity.moveRelative +
        // the movement control): the sinusoidal water body or the land
        // flop owns the whole integration (no walk, no ground snap).
        if (type == MobType.GUARDIAN && !dead) {
            if (noAi) { // the pin (Slice 92 — the swim rides moveRelative)
                decayVelocity();
                return;
            }
            // The swim gates on WATER alone (GuardianEntity.moveRelative's
            // own isInWater consult): a guardian in LAVA falls to the land
            // body — the reference's super.moveRelative fallback — while
            // the contact block above wounds and burns it.
            boolean guardianInWater = !world.inLava(position.x(), position.y() + 0.2, position.z())
                    && world.inFluid(position.x(), position.y() + 0.2, position.z());
            tickGuardianBody(guardianInWater);
            return;
        }
        // (The fire contact, the lava walk, the suffocation, the cactus
        // prick and the water douse now ride THE EVERY-BODY CONTACT BLOCK
        // hoisted above the flight families' early returns — every body
        // takes them, exactly the reference's no-family-skip shape; the
        // inLava/inFluid consults it computed feed the movement arms below.)
        // The blaze's fall dampening (BlazeEntity.mobTick lines 79-82): an
        // airborne falling body drifts at 60% per tick before the gravity
        // arm — the hover's descent stays slow (takeFallDamage's empty
        // override means the landing never hurts; the engine's mob body
        // carries no fall damage to suppress).
        if (type == MobType.BLAZE && !onGround && velocityY < 0.0) {
            velocityY *= 0.6;
        }
        // Vertical: gravity + ground snap (the item-entity model). A body in
        // fluid sinks slowly and bobs (the historical fluid buoyancy) — the
        // inFluid consult rides the contact block above.
        // The NoAI pin (the reference's isLocallyControlled gate over
        // moveRelative, LivingEntity lines 1134-1145 + the non-controlled
        // else arm at 1415): a pinned body integrates NO motion — no
        // gravity, no walk, no ground snap (the vanilla midair freeze);
        // the velocity decays (the *0.98 family). The contact arms above
        // (void, fire, lava, cactus, the water douse) keep running — for
        // the walker AND for the flight families (their branch pins sit
        // below the hoisted contact block, so a pinned bat in lava still
        // wounds and burns — the pin gates the MIND and the MOVEMENT
        // alone). The wounds/despawn windows continue. The special flight
        // families (ghast, dragon, bat, squid, guardian) carry their own
        // pin at their own branches (Slice 92 — the reference gates them
        // through moveRelative exactly like the walker; the dragon's
        // steering rides the already-pinned mind, its body does nothing
        // on its own).
        if (noAi) {
            decayVelocity();
            return;
        }
        if (inLava) {
            // LivingEntity.travel's lava arm (lines 1202-1214): the
            // harder drag + the weak gravity on the VERTICAL —
            // {@code v = v * 0.5 - 0.02} (the steady sink -0.04; the water
            // arm's own terminal is now the deeper -0.10 — the water arm
            // slice below carries its own form); the TRANSIENT stays the
            // lava's: a plunging body decelerates geometrically over ~5
            // ticks — Slice 93/F1's split of the shared
            // fluid branch; the engine applies the drag before its move, a
            // one-tick phase against the reference's move-then-drag
            // order, ledgered). The HORIZONTAL drag rides the damping
            // line below (the same *0.5, applied once — not both).
            velocityY = velocityY * 0.5 - 0.02;
        } else if (inFluid) {
            // THE WATER ARM'S OWN TRAVEL MODEL (LivingEntity.travel's
            // water arm, lines 1238-1245 — the clamp retired): the
            // reference's form is {@code v = v * 0.8 - 0.02} — the drag
            // (0.8, the water's own horizontal family) then the weak
            // water gravity (-0.02, NOT the land arm's heavier term), a
            // GEOMETRIC transient: a plunging body keeps ~80% of its
            // speed each submerged tick and settles on the terminal sink
            // -0.10 over ~5 ticks — never the retired clamp's instant
            // -0.04 floor. The Depth Strider consult the reference folds
            // into this arm's f1/f2 (0.54600006 at level 3) is
            // structurally zero on this engine's bodies (the enchantment
            // row's own boundary: no boots on the bare vanilla mob/mount
            // set), so the base form is the whole arm here. The engine
            // applies the drag before its move like the F1 lava note (a
            // one-tick phase against the reference's move-then-drag
            // order, ledgered with that slice). The HORIZONTAL drag rides
            // the damping line below (the same *0.8) and the wading walk
            // (the *0.5 walk cut) — the engine-shaped equivalents of the
            // reference's 0.02-input water-arm walk (the steady states
            // agree: 0.02/(1-0.8) = 0.1 blocks/tick both models).
            velocityY = velocityY * 0.8 - 0.02;
        } else if (onGround && velocityY <= 0.0) {
            // A grounded body at rest carries no vertical speed — but a
            // POSITIVE write (the blaze's hover, an explosion's launch)
            // lifts it off the ground (the reference's own move
            // semantics: the ground only re-engages on a fall).
            velocityY = 0.0;
        } else {
            velocityY = (velocityY - GRAVITY_PER_TICK) * 0.98;
        }

        // Horizontal: knockback velocity decays; walking integrates directly.
        double walk = 0.0;
        if (mode == Mode.WANDER) {
            walk = walkSpeed();
        } else if (mode == Mode.PANIC) {
            walk = walkSpeed() * PANIC_SPEED_MULTIPLIER;
        } else if (mode == Mode.CHASE) {
            walk = walkSpeed() * (type.traits.ranged() ? 1.0 : 1.1);
        }
        if (witchDrink > 0) {
            walk = 0.0; // the drinking speed penalty (WitchEntity's -0.25 on the 0.25 base → 0)
        }
        if (!Double.isNaN(goalWalkSpeed)) {
            // The awareness goals' own speed (TemptGoal/FleeEntityGoal's
            // parameter over the movement attribute) overrides the band.
            walk = walkSpeed() * goalWalkSpeed;
            if (witchDrink > 0) {
                // The reference's drinking penalty is an ATTRIBUTE
                // modifier (WitchEntity's -0.25 on the 0.25 base) — it
                // zeroes EVERY walk shape, the stroll's goal speed
                // included, never re-armed by a goal band.
                walk = 0.0;
            }
        }
        if (type == MobType.RABBIT) {
            // The rabbit's hop cycle owns the movement (the zeroed-input
            // model — the HOP action's cadence, the reference's own
            // MovementControl rule: grounded and not jumping means no walk).
            tickRabbitHop(inFluid);
            walk = 0.0;
        }
        if (type.slimeFamily()) {
            // The landing (SlimeEntity.tick lines 101-123): the first
            // grounded tick after a hop queues the landing sound — the
            // size > 2 rule for the slime, ALWAYS for the magma cube
            // (MagmaCubeEntity.makesLandSound) — the particles ride the
            // particle slice.
            if (onGround && !wasSlimeGrounded) {
                if (slimeSize > 2 || type == MobType.MAGMA_CUBE) {
                    pendingSlimeLandSound = new StagedSound(slimeSoundName(),
                            0.4F * slimeSize,
                            (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
                }
                wasSlimeGrounded = true;
            } else if (!onGround) {
                wasSlimeGrounded = false;
            }
            tickSlimeMovement(inFluid);
            walk = 0.0; // the hop cycle owns the movement (the input model)
        }
        if (inFluid && walk > 0) {
            walk *= 0.5; // wading drag
        }
        double moveX = velocityX;
        double moveZ = velocityZ;
        if (walk > 0) {
            // A detour overrides the heading while it lasts (the wall slide);
            // the A* path heading owns the walk when no detour is live.
            float heading = detourTicks > 0 ? detourYaw
                    : (pathYawValid ? pathYaw : yaw);
            double radians = Math.toRadians(heading);
            moveX += -Math.sin(radians) * walk;
            moveZ += Math.cos(radians) * walk;
            if (detourTicks > 0) {
                detourTicks--;
            }
        }

        // The damping's lava arm (Slice 93/F1 — the reference's *0.5
        // horizontal drag vs the water's 0.8: a body crawling through
        // lava loses half its horizontal motion every tick).
        double damping = inLava ? 0.5 : inFluid ? 0.8 : (onGround ? 0.6 : 0.98);
        velocityX *= damping;
        velocityZ *= damping;

        // THE SWEPT-BOX APPLICATION (the physics-solver slice): the
        // reference's own Entity.move — the axis-by-axis clip against
        // every collision box the body's swept shape touches, the
        // step-height retry over the half slab, the exact face stop at
        // the wall — when the world provides the collision surface.
        // The fall law rides the ACTUAL moved delta exactly like the
        // legacy walk's own shape (Slice 92's checkFallDamage).
        boolean wasOnGround = onGround;
        // THE STANDING DRIFT (the slice's own 1:1 repair): the
        // reference's LivingEntity.travel applies the gravity AFTER the
        // move, so a standing body ENTERS move() carrying the previous
        // tick's {@code (0 - 0.08) * 0.98 = -0.0784} — the cycle the
        // vertical clip's own zeroing re-arms every tick. The engine's
        // gravity-BEFORE-move order had zeroed the grounded vertical
        // (the {@code velocityY = 0.0} arm above), and a zero dy never
        // turns the ground on ({@code d4 < 0.0} fails) — the drift
        // restores the reference's cycle for the solver path.
        double standingDrift = wasOnGround && velocityY == 0.0
                ? (0.0 - GRAVITY_PER_TICK) * 0.98 : velocityY;
        // The climb-out assist's pre-move anchor (LivingEntity.travel's
        // d1/d0: the y captured before the move — the exit ledge sits
        // 0.6 above IT, not above the post-move body). The x/z pair ride
        // beside it for the step-sound walk's own delta.
        double preMoveY = position.y();
        double preMoveX = position.x();
        double preMoveZ = position.z();
        // THE SPIDER FAMILY'S CLIMB CLAMP (LivingEntity.travel lines
        // 1165-1175 over SpiderEntity.isClimbing's wall override): a body
        // whose last move clipped a wall clamps its horizontal input to
        // ±0.15, zeroes the fall distance, and never sinks faster than
        // -0.15 — the pre-move arms the reference applies before its own
        // move. The wall flag itself is the spider's tick law
        // (SpiderEntity.tick line 67: setClimbingWall(collidingHorizontally),
        // server-side) — updated from the move below.
        double climbX = moveX;
        double climbZ = moveZ;
        double climbVy = standingDrift;
        if (spiderClimbingWall) {
            climbX = Math.max(-0.15, Math.min(0.15, climbX));
            climbZ = Math.max(-0.15, Math.min(0.15, climbZ));
            fallDistance = 0.0f;
            if (climbVy < -0.15) {
                climbVy = -0.15;
            }
        }
        net.zaminmc.torch.server.physics.EntityMoveSolver.Result swept =
                solverMove(climbX, climbVy, climbZ, WALK_STEP_HEIGHT);
        if (swept != null) {
            // The climb's push-up (LivingEntity.travel line 1184: a
            // climbing body still pushing into the wall rises at 0.2 —
            // the flag read is the PREVIOUS tick's own, the reference's
            // tick order: SpiderEntity.tick sets it AFTER travel).
            if (spiderClimbingWall && swept.collidingHorizontally()) {
                velocityY = 0.2f;
            }
            // The spider's own tick law (SpiderEntity.tick line 67): the
            // wall flag is the post-move horizontal clip — the wire's
            // byte-16 bit 0, the climb arms' read next tick.
            if (type == MobType.SPIDER || type == MobType.CAVE_SPIDER) {
                spiderClimbingWall = swept.collidingHorizontally();
            }
            if (swept.collidingHorizontally() && walk > 0 && detourTicks == 0
                    && swept.onGround()) {
                // VIII9 — the zombie's door-break intercept
                // (AbstractDoorInteractGoal.canStart at the clip site: the
                // engine's detour would clear the path before the goal
                // selector ever sees the door; the named adaptation).
                // A door-armed zombie claims the door INSTEAD of the wall
                // slide — the path stays live, the BREAK_DOOR goal takes
                // the body from here.
                if (!armZombieDoorBreak()) {
                    // Blocked mid-walk: slide along the wall (a 45-90° turn
                    // for a handful of ticks) before the mind re-chooses —
                    // the same detour the legacy walk arms, keyed on the
                    // solver's own horizontal-clip signal.
                    clearPath();
                    repathCooldown = 0;
                    float side = random.nextBoolean() ? 45.0f : -45.0f;
                    detourYaw = yaw + side + (random.nextFloat() * 45.0f - 22.5f);
                    detourTicks = DETOUR_TICKS;
                }
            }
            if (inLava) {
                fallDistance *= 0.5f;
            }
            if (inFluid && !inLava) {
                fallDistance = 0.0f;
            }
            if (onGround) {
                if (!wasOnGround && fallDistance > 0.0f) {
                    // The fall path's block consult (Entity.move lines
                    // 569-581 + checkFallDamage 719-733): the landed cell
                    // at floor(y - 0.2) with the air-cell fence fallback —
                    // the farmland's TRAMPLE rides it (FarmlandBlock
                    // .onFallenOn lines 57-71): the roll
                    // nextFloat() < fallDistance - 0.5, the mobGriefing
                    // gate (a non-player body refused grief tramples
                    // NOTHING AND SKIPS THE DAMAGE — the early return
                    // before super), the DIRT write. The world write rides
                    // the pending-intent pattern (the enderman carry's own
                    // shape — the manager's listener owns the world).
                    int landX = (int) Math.floor(position.x());
                    int landY = (int) Math.floor(position.y() - 0.2);
                    int landZ = (int) Math.floor(position.z());
                    net.zaminmc.torch.util.Identifier landed =
                            world.blockIdAt(landX, landY, landZ);
                    boolean fellToAir = isAir(landed);
                    if (fellToAir) {
                        net.zaminmc.torch.util.Identifier under =
                                world.blockIdAt(landX, landY - 1, landZ);
                        if (isFenceLike(under)) {
                            landed = under;
                            landY--;
                        }
                    }
                    String landedName = landed == null ? "" : landed.value();
                    boolean farmland = landedName.equals("farmland")
                            || landedName.equals("farmland_wet");
                    boolean damage = true;
                    if (farmland && random.nextFloat() < fallDistance - 0.5f) {
                        if (world.mobGriefing()) {
                            trampleX = landX;
                            trampleY = landY;
                            trampleZ = landZ;
                            tramplePending = true;
                        } else {
                            // The griefing-off early return: no trample AND
                            // no fall damage (lines 64-66 — the mob lands
                            // soft when its grief is refused).
                            damage = false;
                        }
                    }
                    if (damage) {
                        takeFallDamage(fallDistance, 1.0f);
                    }
                    fallDistance = 0.0f;
                }
            } else {
                if (swept.dy() < 0.0) {
                    fallDistance = (float) (fallDistance - swept.dy());
                }
            }
            // THE CLIMB-OUT ASSIST (LivingEntity.travel lines 1210/1238 —
            // the water and lava arms' SHARED pool-exit law, the step
            // model's own slice): a body in fluid pushing against a wall
            // (the move's collidingHorizontally) that CAN occupy the ledge
            // 0.6 above its pre-move position hops out — velocityY = 0.3
            // (the reference's canMove consult over doesNotCollide: no
            // block collision in the offset box, no liquid in its span).
            // The engine writes it AFTER its move like the reference (the
            // next tick's travel carries the 0.3 up; the engine's
            // drag-before-move order phases it one tick, the F1 ledger's
            // own note).
            if (swept.collidingHorizontally() && (inLava || inFluid)
                    && canOccupyClimbOut(preMoveY, swept.dx(), swept.dz())) {
                velocityY = 0.3f;
            }
            // The step-sound walk (Entity.move's own closing arm — the
            // swept path's move has landed the body; the walk reads the
            // post-move feet cell over the pre-move anchor).
            stepSoundWalk(preMoveX, preMoveY, preMoveZ);
            return;
        }

        double newX = position.x() + moveX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + moveZ;

        // Ground snap / step handling: walking mobs do not scale walls; a
        // collision shape in the path stops horizontal motion — or the body
        // steers around it (a half- or full-block step up first, then the
        // sideways detour). The step lands on the blocking cell's shape
        // surface (the slab half or the full cube), never above it: the
        // 1.5-tall fence refuses a step like a wall.
        if (world.isSolidAt(newX, position.y(), newZ)
                && !world.isSolidAt(newX, position.y() + 1.0, newZ)
                && onGround && Math.abs(velocityY) < 0.01) {
            newY = world.supportY(newX, position.y() + GROUND_EPSILON, newZ);
            if (newY - position.y() > 1.0 + GROUND_EPSILON // the fence rule
                    || world.isSolidAt(newX, newY, newZ)) {
                newX = position.x();
                newZ = position.z();
                newY = position.y();
            }
        } else if (world.isSolidAt(newX, position.y(), newZ)) {
            newX = position.x();
            newZ = position.z();
            newY = position.y();
            if (walk > 0 && detourTicks == 0 && onGround) {
                // VIII9 — the zombie's door-break intercept at the legacy
                // walk's own clip (the same named adaptation as the solver
                // site above).
                if (!armZombieDoorBreak()) {
                    // Blocked mid-walk: slide along the wall (a 45-90° turn for
                    // a handful of ticks) before the mind re-chooses. A hunting
                    // body drops its stale route so the next mind tick repaths
                    // around the obstacle (the A*-light trigger).
                    clearPath();
                    repathCooldown = 0;
                    float side = random.nextBoolean() ? 45.0f : -45.0f;
                    detourYaw = yaw + side + (random.nextFloat() * 45.0f - 22.5f);
                    detourTicks = DETOUR_TICKS;
                }
            }
        }

        // The ground snap over the legacy walk (wasOnGround captured
        // before the application above — the solver's null return left
        // it untouched).
        if (velocityY <= 0 && world.isSolidAt(newX, newY - GROUND_EPSILON, newZ)) {
            newY = world.supportY(newX, newY - GROUND_EPSILON, newZ);
            velocityY = 0.0;
            onGround = true;
        } else {
            onGround = false;
        }

        // checkFallDamage (Slice 92 — Entity lines 719-731, the reference's
        // own shape over the ACTUAL moved delta): the water zeroes the
        // store (checkWaterCollisions line 771) while the LAVA halves it
        // (Entity.tick's own arm, Slice 93/D2 — the shared fluid model's
        // split: {@code fallDistance *= 0.5F} every tick submerged, the
        // halving running BEFORE the tick's accumulate exactly like the
        // reference's tick-then-move order; a body touching both kinds
        // halves then zeroes — the water's zero wins, the reference's own
        // consult order); a still-falling body accumulates {@code -dy};
        // the landing spends the store through takeFallDamage and zeroes
        // it. The store's own NBT row (FallDistance) round-trips the
        // mid-air value.
        if (inLava) {
            fallDistance *= 0.5f;
        }
        if (inFluid && !inLava) {
            fallDistance = 0.0f;
        }
        if (onGround) {
            if (!wasOnGround && fallDistance > 0.0f) {
                takeFallDamage(fallDistance, 1.0f);
                fallDistance = 0.0f;
            }
        } else {
            double dy = newY - position.y();
            if (dy < 0.0) {
                fallDistance = (float) (fallDistance - dy);
            }
        }

        position = new Position(newX, newY, newZ);
        // The step-sound walk (Entity.move's own closing arm — the
        // legacy path's move has landed the body).
        stepSoundWalk(preMoveX, preMoveY, preMoveZ);
    }

    /**
     * The fall wound (Slice 92 — LivingEntity.takeFallDamage lines
     * 823-837): the jump-boost term ({@code amplifier + 1} — a level-II
     * jump spares two blocks), the {@code ceil((distance - 3 - boost) *
     * multiplier)} wound, the land sound's own slice. The empty overrides
     * — the kinds that never take the landing (GolemEntity 12-14 the
     * iron/snow pair, FlyingMobEntity 14-16 the ghast, MagmaCubeEntity
     * 107-109, BatEntity 150-152, BlazeEntity 131-133, ChickenEntity
     * 84-86, OcelotEntity 95-97, WitherEntity 472-474, HorseBaseEntity
     * 355-362 the sound alone — no super call) — stand first. The
     * creeper's own arm (CreeperEntity lines 56-62) rides after the
     * wound: the fall primes the fuse at {@code distance * 1.5}, capped
     * at {@code fuseTime - 5}.
     */
    public void takeFallDamage(float distance, float damageMultiplier) {
        // The pig's rider fall (VIII6 — PigEntity.takeFallDamage lines
        // 162-168: the super walk runs whole, then a fall past five blocks
        // with a rider aboard earns the flyPig row — the rider's own stat
        // store, the manager-injected award seam resolving the engine id).
        if (type == MobType.PIG && distance > 5.0f && riderId >= 0 && playerStatAward != null) {
            playerStatAward.accept(riderId, "achievement.flyPig");
        }
        if (type == MobType.IRON_GOLEM || type == MobType.SNOW_GOLEM
                || type == MobType.GHAST || type == MobType.MAGMA_CUBE
                || type == MobType.BAT || type == MobType.BLAZE
                || type == MobType.CHICKEN || type == MobType.OCELOT
                || type == MobType.WITHER || type == MobType.HORSE) {
            return; // the empty overrides
        }
        float boost = effectAmplifierPlusOne(
                net.zaminmc.torch.server.potion.StatusEffects.JUMP_BOOST);
        int wound = (int) Math.ceil((distance - 3.0f - boost) * damageMultiplier);
        if (wound > 0) {
            hurt(wound);
            if (type == MobType.CREEPER && mode == Mode.FUSE) {
                // The creeper's fuse arm (CreeperEntity lines 56-62): the
                // fall primes the LIVE swell — the counter jumps by
                // {@code distance * 1.5}, capped at {@code fuseTime - 5}
                // (just under the trigger). The reference's not-swelling
                // preload (a decaying counter outside the swell) has no
                // home in the engine's swell model (the abort resets the
                // counter) — the swell-model envelope, ledgered.
                int fuse = modeTicks + (int) (distance * 1.5f);
                int cap = creeperFuseTime - 5;
                if (fuse > cap) {
                    fuse = cap;
                }
                if (fuse < 0) {
                    fuse = 0;
                }
                modeTicks = fuse;
            }
        }
    }

    private float randomDirection() {
        return random.nextFloat() * 360.0f;
    }

    /** The 1.8 yaw of a facing vector (yaw grows clockwise; -Z is south 0). */
    private static float angleTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private long fixedPoint() {
        return (Math.round(position.x() * 32.0) << 42)
                | (Math.round(position.y() * 32.0) << 21)
                | Math.round(position.z() * 32.0);
    }

    // ------------------------------------------------ navigation adapters

    /** The navigation's view of this body (the vanilla MobView surface). */
    private final class PathBody implements
            net.zaminmc.torch.server.entity.ai.pathing.MobView {
        @Override
        public double x() {
            return position.x();
        }

        @Override
        public double y() {
            return position.y();
        }

        @Override
        public double z() {
            return position.z();
        }

        @Override
        public double width() {
            return bodyWidth();
        }

        @Override
        public double height() {
            return bodyHeight();
        }

        @Override
        public boolean onGround() {
            return onGround;
        }

        @Override
        public boolean inWater() {
            return world.inFluid(position.x(), position.y() + 0.2, position.z());
        }

        @Override
        public boolean inLava() {
            return world.inLava(position.x(), position.y() + 0.2, position.z());
        }
    }

    /** The navigation's view of the world (the cell-material query). */
    private final class PathWorldView implements
            net.zaminmc.torch.server.entity.ai.pathing.PathWorld {
        @Override
        public net.zaminmc.torch.server.entity.ai.pathing.CellMaterial materialAt(int x, int y, int z) {
            return world.pathMaterialAt(x, y, z);
        }

        @Override
        public boolean hasSkyAccess(int x, int y, int z) {
            // The sun-avoidance trim's world read (TF-7f — the same
            // heightmap consult the escape goal's shaded pick reads).
            return world.hasSkyAccess(x, y, z);
        }
    }
}
