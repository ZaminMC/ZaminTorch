package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

import java.util.Objects;

/**
 * One airborne projectile: an arrow, a thrown snowball or an egg.
 *
 * <p>State is simulation-owned (tick thread only). Physics mirrors the
 * historical thrown-entity loop: integrate motion, apply drag, apply gravity,
 * then resolve block and entity collisions. Arrows that land stick; the
 * shard-family projectiles (snowball, egg) shatter on any impact.</p>
 */
public final class ProjectileEntity implements net.zaminmc.torch.entity.Projectile {

    /**
     * VT-006 — the base family's portal re-entry cooldown (the
     * reference's {@code getPortalCooldown()} = 300, Entity.java lines
     * 1457-1459).
     */
    private static final int PROJECTILE_PORTAL_COOLDOWN_TICKS = 300;

    /** The projectiles the engine launches. */
    public enum Kind {
        /** The bow's arrow: sticks where it lands, hurts what it hits. */
        ARROW(60, net.zaminmc.torch.entity.ProjectileKind.ARROW),
        /** The snowball: no damage, knockback only, shatters on impact. */
        SNOWBALL(61, net.zaminmc.torch.entity.ProjectileKind.SNOWBALL),
        /** The egg: like the snowball, but may hatch a chick. */
        EGG(62, net.zaminmc.torch.entity.ProjectileKind.EGG),
        /** The dispensed fire charge (Slice 11a): accelerates along its
         * direction, places fire where it lands, burns what it hits. */
        SMALL_FIREBALL(63, net.zaminmc.torch.entity.ProjectileKind.SMALL_FIREBALL),
        /** The ghast's charged fireball (Slice 16c — FireballEntity):
         * the same accelerating flight, the 6.0 direct hit plus the
         * power-1 explosion where it lands. */
        LARGE_FIREBALL(64, net.zaminmc.torch.entity.ProjectileKind.LARGE_FIREBALL),
        /** The thrown potion (Slice 11b): splashes its effect set where it
         * lands, carrying the potion metadata as the payload. */
        POTION(68, net.zaminmc.torch.entity.ProjectileKind.SPLASH_POTION),
        /** The ender pearl (Slice 17c — EnderPearlEntity): the thrower
         * teleports to the landing point with the 5.0 fall toll, and a
         * pearl-spawned endermite may ride (the 5% roll at the thrower's
         * pre-teleport feet). */
        ENDER_PEARL(65, net.zaminmc.torch.entity.ProjectileKind.ENDER_PEARL),
        /** The wither's skull (Slice 59 — WitherSkullEntity, the object
         * type 66): the accelerating flight with the charged blue's own
         * 0.73 drag, the 8.0 hit with the wither curse, and the 1.0F
         * detonation where it lands. */
        WITHER_SKULL(66, net.zaminmc.torch.entity.ProjectileKind.WITHER_SKULL),
        /** The bottle o' enchanting (Slice 65 — ExperienceBottleEntity,
         * the object type 75): the lobbed flight (the -20-degree start
         * pitch offset, the 0.7 power, the 0.07 gravity) that bursts into
         * experience orbs on any impact — no damage, ever (the reference's
         * onCollision only splashes the 2002 event and the orbs). */
        EXPERIENCE_BOTTLE(75, net.zaminmc.torch.entity.ProjectileKind.EXPERIENCE_BOTTLE);

        /** Protocol 47's Spawn Object entity type id. */
        public final int objectType;

        /** The public API's kind (§27.3 — the stable surface the engine
         * kind maps onto). */
        public final net.zaminmc.torch.entity.ProjectileKind apiKind;

        Kind(int objectType, net.zaminmc.torch.entity.ProjectileKind apiKind) {
            this.objectType = objectType;
            this.apiKind = apiKind;
        }
    }

    /** The engine-global entity id (its own id band). */
    private final int entityId;
    private final Kind kind;
    /**
     * The thrower's engine-global id (player session id or mob id), or -1.
     * Not final: the fireball family's deflection re-credits the puncher
     * (ProjectileEntity.takeDamage line 266 — the attacker becomes the
     * new shooter).
     */
    private int throwerId;
    private Position position;
    private double vx;
    private double vy;
    private double vz;
    /** Ticks alive airborne; age caps runaway projectiles. */
    private int age;
    /** Ticks a landed arrow has been stuck (its removal clock). */
    private int ticksInGround;
    /** True once the arrow struck a block and is waiting out its stick time. */
    private boolean inGround;
    /** The full-draw flag: the hit damage takes the reference's crit roll. */
    private boolean critical;
    /** The Punch level carried from the release (0 = none). */
    private int punchLevel;
    /** The Power bonus riding the damage multiplier (the j * 0.5 + 0.5 arm). */
    private double bonusDamage;
    /** The burning clock (Flame releases ignite 100 s — the whole flight). */
    private int fireTicks;
    /**
     * The potion projectile's payload (Slice 11b — the reference's
     * ItemStack): the potion metadata carrying the effect set. -1 marks
     * every non-potion kind.
     */
    private int potionDamage = -1;
    /**
     * The wither skull's charged flag (WitherSkullEntity's synced slot-10
     * byte, Slice 59): the blue skull drag-steps at 0.73 instead of 0.95
     * (getDrag's own override).
     */
    private boolean charged;
    /**
     * The fireball family's per-tick acceleration (the reference
     * ProjectileEntity fields, the 6-double ctor's normalized*0.1 vector).
     * Zero for the ballistic kinds (arrows, shards); a fireball ignores
     * gravity and drag-steps at 0.95 instead.
     */
    private double accelerationX;
    private double accelerationY;
    private double accelerationZ;
    /**
     * The arrow's pickup band (ArrowEntity's own field, line 35): 0 = no
     * pickup (the mob shooters' ctor never sets it — a skeleton's arrow
     * stays on the ground), 1 = collectable (the PLAYER-shooter ctors'
     * own set, lines 61/85, and Bootstrap's dispenser arm, line 70 — the
     * survival walk tries the inventory), 2 = creative-only (the
     * onPlayerCollision flag walk's own band, line 430 — no vanilla
     * launch site sets it; the anvil import restores it from the saved
     * row). Non-arrow kinds never read it.
     */
    private int pickup;
    /**
     * The body's dimension (Wave TF-9 — the reference's Entity.world, the
     * mob query's own Slice-25d shape generalized over the projectile
     * family): 0 the overworld, -1 the nether, 1 the end. The shared
     * solid query flips to the body's own world before every collision
     * walk — a nether arrow sticks in the nether's blocks and wounds
     * only the nether's bodies.
     */
    private int dimension;

    /** @return the dimension the body flies in (0 overworld, -1 nether, 1 the end). */
    public int dimension() {
        return dimension;
    }

    /** Sets the body's dimension (the launch walks stamp it). */
    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    /**
     * VT-006 — the portal stand clock (the reference's
     * {@code Entity.portalTime}, Entity.java line 127): the base family's
     * threshold is {@code getMaxNetherPortalTime() = 0} (lines 354-355),
     * so the clock never accumulates — the row rides the body for the
     * decay arm's own law (lines 302-308) exactly like the item, orb,
     * and mob families' own rows.
     */
    private int portalTime;
    /**
     * VT-006 — the portal re-entry cooldown (the reference's
     * {@code Entity.portalCooldown}, Entity.java line 128): armed at the
     * crossing, re-armed by the collision consult while it lives
     * (onPortalCollision lines 1429-1430) — the arrival standing in the
     * destination frame never re-fires.
     */
    private int portalCooldown;
    /** VT-006 — the last portal cell the collision derived ({@code lastPortalPos}). */
    private net.zaminmc.torch.block.BlockPosition lastPortalPosition;
    /** VT-006 — the entry's offset along the frame's width ({@code lastPortalOffset.x}). */
    private double portalOffsetAlongWidth;
    /** VT-006 — the entry's offset up the frame's height ({@code lastPortalOffset.y}). */
    private double portalOffsetUp;
    /** VT-006 — the entry's facing ({@code lastPortalFacing}'s horizontal id). */
    private int portalFacingHorizontalId;

    /**
     * VT-006 — the entity-level portal clock's advance (the reference's
     * Entity.baseTick portal arm, lines 282-313, riding ArrowEntity.tick
     * line 150's super chain — the same walk the item, orb, and mob
     * families carry): the cooldown tail every ticked body pays (lines
     * 311-312), the collision consult's re-arm (onPortalCollision lines
     * 1429-1430), the crossing arm's base threshold 0 (lines 286-297 —
     * armed on the very first touching tick, the cooldown armed for the
     * arrival), and the decay arm (lines 302-308). A projectile never
     * rides (no vehicle, no rider rows), so the arm's own
     * {@code this.vehicle == null} gate (line 286) is structural.
     *
     * @return true on the crossing tick (the manager's walk consumes the
     *         body's remaining walk)
     */
    public boolean advanceEntityPortalClock(boolean touching) {
        // The tail decrement (lines 311-312): every ticked body pays it.
        if (portalCooldown > 0) {
            portalCooldown--;
        }
        if (touching) {
            if (portalCooldown > 0) {
                // The collision consult's re-arm (onPortalCollision lines
                // 1429-1430): a colliding body whose cooldown is live
                // resets it to the full store.
                portalCooldown = PROJECTILE_PORTAL_COOLDOWN_TICKS;
                return false;
            }
            // The crossing arm (lines 286-297): the base threshold 0 —
            // the post-increment consult is armed on the very first
            // touching tick, and the cooldown arms for the arrival.
            portalTime = 0;
            portalCooldown = PROJECTILE_PORTAL_COOLDOWN_TICKS;
            return true;
        }
        // The decay arm (lines 302-308).
        if (portalTime > 0) {
            portalTime = Math.max(0, portalTime - 4);
        }
        return false;
    }

    /**
     * VT-006 — the reference's {@code onPortalCollision} memory arm
     * (lines 1428-1451), the same walk the item, orb, and mob families
     * carry: a NEW portal cell derives the body's offset inside its
     * frame (the inverse-lerp of the body's position across the frame's
     * width and height — the raw reference ratios, unclamped) and the
     * entered facing; the SAME cell re-collides without re-deriving.
     */
    public void notePortalEntry(net.zaminmc.torch.block.BlockPosition portalCell,
                                net.zaminmc.torch.server.world.PortalFrameBuilder.PortalShapeMatch match,
                                double x, double y, double z) {
        if (portalCell.equals(lastPortalPosition)) {
            return; // the reference's !pos.equals(lastPortalPos) guard
        }
        lastPortalPosition = portalCell;
        boolean axisX = match.forwardAxis()
                == net.zaminmc.torch.server.world.PortalFrameBuilder.Axis.X;
        double origin = axisX ? match.topLeftFront().z() : match.topLeftFront().x();
        double bodyCoord = axisX ? z : x;
        if (match.forwardAxis() == net.zaminmc.torch.server.world.PortalFrameBuilder.Axis.Z
                && match.forwardPositive()) {
            bodyCoord -= 1.0;
        }
        // The reference's inverseLerp: (a - b) / (c - b), raw — no clamp.
        portalOffsetAlongWidth = Math.abs(
                (bodyCoord - origin) / ((origin - match.width()) - origin));
        portalOffsetUp = ((y - 1.0) - match.topLeftFront().y())
                / ((match.topLeftFront().y() - match.height()) - match.topLeftFront().y());
        portalFacingHorizontalId = match.forwardHorizontalId();
    }

    /** @return the memory snapshot the PortalForcer's placement math consumes. */
    public net.zaminmc.torch.server.world.PortalForcer.Memory portalMemory() {
        net.zaminmc.torch.server.world.PortalForcer.Memory memory =
                new net.zaminmc.torch.server.world.PortalForcer.Memory();
        memory.offsetAlongWidth = portalOffsetAlongWidth;
        memory.offsetUp = portalOffsetUp;
        memory.lastFacingHorizontalId = portalFacingHorizontalId;
        memory.known = lastPortalPosition != null;
        return memory;
    }

    /**
     * VT-006 — the crossing's arrival (the reference's
     * setPositionAndAngles + the world swap over the recreated body's
     * copyNbtFrom, Entity.changeDimension lines 1708-1722: the kind, the
     * thrower, the stick clock — everything the NBT holds carries whole;
     * the engine keeps the body itself): the dimension stamp, the
     * forcer's landing, and the rotated motion. The projectile family
     * carries no yaw row (the engine's own model — the flight derives
     * its own pitch/yaw), so the arrival's rotated yaw is consumed by
     * the forcer's placement math, never stamped.
     */
    public void applyDimensionArrival(int dimension, Position arrival,
                                       double velocityX, double velocityZ) {
        this.dimension = dimension;
        this.position = Objects.requireNonNull(arrival, "arrival");
        this.vx = velocityX;
        this.vz = velocityZ;
    }

    public ProjectileEntity(int entityId, Kind kind, int throwerId,
                            Position position, double vx, double vy, double vz) {
        this.entityId = entityId;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.throwerId = throwerId;
        this.position = Objects.requireNonNull(position, "position");
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }

    /**
     * The fireball launch (the reference's 6-double ctor): starts at rest
     * and accelerates along the normalized direction scaled to 0.1 per
     * tick (ProjectileEntity lines 50-58).
     */
    public ProjectileEntity(int entityId, Kind kind, Position position,
                            double directionX, double directionY, double directionZ) {
        this(entityId, kind, -1, position, 0.0, 0.0, 0.0);
        setAccelerationFromDirection(directionX, directionY, directionZ);
    }

    /**
     * The fireball family's acceleration from a raw direction (the
     * reference's ctor normalize-and-scale walk, ProjectileEntity lines
     * 50-58): normalized, then scaled to the 0.1 per-tick push. The
     * shooter's own jitter lands on the components BEFORE this.
     */
    void setAccelerationFromDirection(double directionX, double directionY, double directionZ) {
        double length = Math.sqrt(directionX * directionX + directionY * directionY
                + directionZ * directionZ);
        if (length < 1.0E-4) {
            length = 1.0;
        }
        this.accelerationX = directionX / length * 0.1;
        this.accelerationY = directionY / length * 0.1;
        this.accelerationZ = directionZ / length * 0.1;
    }

    public int entityId() {
        return entityId;
    }

    public Kind kind() {
        return kind;
    }

    /** The thrower's engine-global id, or -1 when unknown. */
    public int throwerId() {
        return throwerId;
    }

    public Position position() {
        return position;
    }

    /**
     * The seated rider's plain position set (the riding-follow surface —
     * the reference's updateRiderPositon writes the rider's position
     * whatever family it is; a seated projectile stops being a body in
     * flight, its saved velocity staying as loaded exactly like the
     * reference's rideTick zeroes the motion while the tick rides).
     */
    public void teleport(Position to) {
        this.position = Objects.requireNonNull(to, "to");
    }

    public double velocityX() {
        return vx;
    }

    public double velocityY() {
        return vy;
    }

    public double velocityZ() {
        return vz;
    }

    /** |v| at launch (the bow's damage scales with it); recomputed on demand. */
    public double launchSpeed() {
        return Math.sqrt(vx * vx + vy * vy + vz * vz);
    }

    public int age() {
        return age;
    }

    public int ticksInGround() {
        return ticksInGround;
    }

    public boolean inGround() {
        return inGround;
    }

    /** @return the arrow's pickup band (0 none, 1 collectable, 2 creative-only). */
    public int pickupStatus() {
        return pickup;
    }

    /** Sets the pickup band (the launch arms' own set — the player bow's 1,
     *  the dispenser's 1, the import's saved row). */
    public void setPickupStatus(int pickup) {
        this.pickup = pickup;
    }

    /** Marks the arrow as stuck (it stops moving and starts its removal clock). */
    public void landInGround() {
        this.inGround = true;
        this.vx = 0;
        this.vy = 0;
        this.vz = 0;
    }

    /**
     * The restored stuck form (the Anvil import's {@code inGround} +
     * {@code life} rows — the reference's readCustomNbt): the stick clock
     * resumes at the saved life. The saved velocity stays as loaded
     * (the reference's own shape — the stuck arm never integrates it, and
     * {@link #landInGround()}'s zeroing belongs to the live hit alone).
     * Public like its live twin {@link #landInGround()} (the import walk
     * in another package is the caller).
     */
    public void restoreGrounded(int life) {
        this.inGround = true;
        this.ticksInGround = life;
    }

    /** @return whether the full-draw flag rides (the hit's crit roll). */
    public boolean critical() {
        return critical;
    }

    /** Sets the full-draw crit flag (the release walk's setCritical arm). */
    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public int punchLevel() {
        return punchLevel;
    }

    public void setPunchLevel(int punchLevel) {
        this.punchLevel = punchLevel;
    }

    public double bonusDamage() {
        return bonusDamage;
    }

    /** Adds the Power bonus to the damage multiplier (the setDamage arm). */
    public void addBonusDamage(double bonus) {
        this.bonusDamage += bonus;
    }

    /** @return whether the arrow is burning (the Flame release, the visual flag). */
    public boolean burning() {
        return fireTicks > 0;
    }

    /** The potion payload (the metadata carrying the effect set), or -1. */
    public int potionDamage() {
        return potionDamage;
    }

    /** Attaches the potion payload (the POTION kind's flight data). */
    public void setPotionDamage(int damage) {
        this.potionDamage = damage;
    }

    /** Ignites the arrow for the given tick count (the reference's setOnFireFor). */
    public void setOnFireFor(int ticks) {
        this.fireTicks = ticks;
    }

    /** One burning-clock step (the manager's tick calls it per pass). */
    public void tickFire() {
        if (fireTicks > 0) {
            fireTicks--;
        }
    }

    /** The fireball family's per-tick acceleration components (the deflection tests read them). */
    public double accelerationX() {
        return accelerationX;
    }

    public double accelerationY() {
        return accelerationY;
    }

    public double accelerationZ() {
        return accelerationZ;
    }

    /** The wither skull's charged flag (WitherSkullEntity.isCharged). */
    public boolean charged() {
        return charged;
    }

    /** WitherSkullEntity.setCharged: the blue skull's own drag and curse. */
    public void setCharged(boolean charged) {
        this.charged = charged;
    }

    /**
     * The player's punch on a fireball (Slice 25b — ProjectileEntity
     * .takeDamage lines 248-273): the velocity becomes the attacker's
     * normalized look vector, the acceleration becomes look * 0.1 (the
     * reference's own {@code velocity * 0.1}), and the attacker becomes
     * the new shooter — the deflected fireball's blast credits the
     * puncher. Package-private: the manager's deflect verb is the caller.
     */
    void deflectBy(int attackerId, double lookX, double lookY, double lookZ) {
        this.throwerId = attackerId;
        this.vx = lookX;
        this.vy = lookY;
        this.vz = lookZ;
        this.accelerationX = lookX * 0.1;
        this.accelerationY = lookY * 0.1;
        this.accelerationZ = lookZ * 0.1;
    }

    /**
     * The landed punch's knockback push (Slice TF-7c — PlayerEntity
     * .attack lines 983-987: {@code target.addVelocity(-sin(yaw) * i *
     * 0.5, 0.1, cos(yaw) * i * 0.5)} over the Knockback-enchanted or
     * sprinting punch): ADDS to whatever the deflection set — the
     * reference applies it after takeDamage's look-vector write. The
     * y term is the flat 0.1. Package-private: the manager's deflect
     * verb is the caller.
     */
    void punchKnockbackVelocity(double pushX, double pushY, double pushZ) {
        this.vx += pushX;
        this.vy += pushY;
        this.vz += pushZ;
    }

    /**
     * The slime-block fling's velocity set (the VIII13 additive
     * families — MovingBlockEntity lines 94-107): the velocity SET
     * along the facing axis, the other components kept. A GROUNDED
     * arrow carries the set components as dead state exactly as the
     * reference does (the stick branch returns before any velocity
     * use; the reference's own damped 0.2 factor applies to them only
     * if the stick cell's block ever changes). Package-private: the
     * manager's fling arm is the only caller.
     */
    void flingVelocity(double axisX, double axisY, double axisZ) {
        this.vx = axisX != 0.0 ? axisX : vx;
        this.vy = axisY != 0.0 ? axisY : vy;
        this.vz = axisZ != 0.0 ? axisZ : vz;
    }

    /**
     * The blast's scatter add (the reference's Explosion.damageEntities
     * lines 135-137 — {@code velocityX/Y/Z += dir * d11}, the add riding
     * outside the takeDamage verdict). Package-private: the manager's
     * addVelocity verb is the only caller. A grounded arrow keeps the add
     * as dead state exactly like the reference's own stick shape (the
     * stick branch returns before any velocity use).
     */
    void addVelocity(double dx, double dy, double dz) {
        this.vx += dx;
        this.vy += dy;
        this.vz += dz;
    }

    /**
     * One physics step. Returns the position integrated through (for block
     * collision sampling the manager segments the path between).
     */
    public Position integrate(double drag, double gravity) {
        if (!inGround) {
            // The fireball family's own tick arm (the reference
            // ProjectileEntity.tick lines 186-191): the velocity picks up the
            // acceleration BEFORE the drag term — a fireball starts at rest
            // and speeds toward v_inf = a*drag/(1-drag). The ballistic kinds
            // carry zero acceleration and keep the plain gravity term.
            vx = (vx + accelerationX) * drag;
            vy = (vy + accelerationY) * drag;
            vz = (vz + accelerationZ) * drag;
            if (accelerationX == 0.0 && accelerationY == 0.0 && accelerationZ == 0.0) {
                vy -= gravity;
            }
            position = new Position(position.x() + vx, position.y() + vy, position.z() + vz);
            age++;
        } else {
            ticksInGround++;
        }
        return position;
    }

    // ------------------------------------------------------------------
    // The public API surface (§27.3 — the Projectile handle). The engine
    // class IS the handle (§27.5's zero-allocation shape).
    // ------------------------------------------------------------------

    /** The public kind (the engine kind's stable API mapping). */
    @Override
    public net.zaminmc.torch.entity.ProjectileKind projectileKind() {
        return kind.apiKind;
    }

    @Override
    public int throwerEntityId() {
        return throwerId;
    }

    @Override
    public Identifier typeIdentifier() {
        return kind.apiKind.identifier();
    }

    /**
     * The any-thread-consumable view (§27.6): captured on the owner
     * context inside {@code World.execute}. Health/maxHealth do not
     * apply to the projectile body (1/1); {@code dead} answers false —
     * the removal contract is the population view's disappearance.
     */
    @Override
    public net.zaminmc.torch.entity.EntitySnapshot snapshot() {
        return new net.zaminmc.torch.entity.EntitySnapshot(
                entityId, kind.apiKind.identifier(), position, 0.0f,
                vx, vy, vz,
                1.0f, 1.0f, inGround, false);
    }
}
