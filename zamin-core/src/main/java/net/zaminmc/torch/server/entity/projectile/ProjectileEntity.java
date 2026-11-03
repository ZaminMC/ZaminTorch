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
    /**
     * True once the arrow struck a block and is waiting out its stick time.
     */
    private boolean inGround;
    /**
     * VT-015 — the tracked cell (the reference's {@code blockX/blockY/
     * blockZ}, ArrowEntity lines 29-31, stamped at the stick lines
     * 290-292): the cell whose block the arrow stuck IN, consulted every
     * stuck tick. {@code Integer.MIN_VALUE} marks a cell-less stuck body
     * (the import's restore without the tracked rows — the consult never
     * fires for it).
     */
    private int stickCellX = Integer.MIN_VALUE;
    private int stickCellY = Integer.MIN_VALUE;
    private int stickCellZ = Integer.MIN_VALUE;
    /**
     * VT-015 — the tracked block's identifier (the reference's
     * {@code block} + {@code blockData} pair, lines 32-33): the consult's
     * mismatch arm keys on it — a cell whose block no longer matches
     * un-sticks the arrow. {@code null} marks the cell-less form.
     */
    private String stickBlockId;
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

    /**
     * Marks the arrow as stuck (it stops moving and starts its removal
     * clock). VT-015 — the caller stamps the tracked cell and its block
     * identifier (the reference's stick arm, ArrowEntity lines 289-295:
     * {@code blockX/blockY/blockZ} + the {@code block/blockData} pair),
     * which the stick clock consults every tick.
     */
    public void landInGround(int cellX, int cellY, int cellZ, String blockId) {
        this.inGround = true;
        this.vx = 0;
        this.vy = 0;
        this.vz = 0;
        this.stickCellX = cellX;
        this.stickCellY = cellY;
        this.stickCellZ = cellZ;
        this.stickBlockId = blockId;
    }

    /**
     * VT-014 — the stick's full ceremony (the reference's stick arm,
     * ArrowEntity lines 288-304): the tracked cell + identifier stamped
     * (289-295) AND the velocity REPLACED by the face-relative vector
     * ({@code velocity := face − pos}, lines 296-298 — the remaining
     * step of the crossing tick). The replaced velocity is DEAD STATE on
     * a stuck body — the flight branch never integrates it, and its only
     * consumer in the whole reference is the un-stick arm's
     * {@code random * 0.2} per-axis damping (lines 179-186): the
     * reference's un-stuck arrow hops up to a fifth of its remaining
     * step ALONG THE FLIGHT DIRECTION instead of falling straight down.
     * The caller places the body at the rest first (the 0.05 backoff,
     * lines 300-302 + the flight tail's advance 328-330 —
     * {@code face − 0.05·unit(approach)}). Package-private: the
     * manager's stick arm is the only caller.
     */
    void landInGround(int cellX, int cellY, int cellZ, String blockId,
            double faceVectorX, double faceVectorY, double faceVectorZ) {
        this.inGround = true;
        this.vx = faceVectorX;
        this.vy = faceVectorY;
        this.vz = faceVectorZ;
        this.stickCellX = cellX;
        this.stickCellY = cellY;
        this.stickCellZ = cellZ;
        this.stickBlockId = blockId;
    }

    /** @return the tracked cell's X ({@code Integer.MIN_VALUE} when cell-less). */
    public int stickCellX() {
        return stickCellX;
    }

    /** @return the tracked cell's Y ({@code Integer.MIN_VALUE} when cell-less). */
    public int stickCellY() {
        return stickCellY;
    }

    /** @return the tracked cell's Z ({@code Integer.MIN_VALUE} when cell-less). */
    public int stickCellZ() {
        return stickCellZ;
    }

    /** @return the tracked block's identifier, or null (the cell-less form). */
    public String stickBlockId() {
        return stickBlockId;
    }

    /**
     * VT-015 — the tracked-cell mismatch arm (the reference's else arm,
     * ArrowEntity lines 179-186): {@code inGround} flips false, each
     * velocity component is re-armed with its own
     * {@code random.nextFloat() * 0.2} damped multiple (a fresh draw per
     * axis, over whatever the stick left as dead state), and both clocks
     * reset — the arrow falls out of the opened cell and flies again.
     * Package-private: the manager's per-tick consult is the only caller.
     */
    void unstickGrounded(java.util.Random random) {
        this.inGround = false;
        this.vx = this.vx * (random.nextFloat() * 0.2);
        this.vy = this.vy * (random.nextFloat() * 0.2);
        this.vz = this.vz * (random.nextFloat() * 0.2);
        this.ticksInGround = 0;
        this.age = 0; // the reference's inAirTicks reset
    }

    /**
     * The restored stuck form (the Anvil import's {@code inGround} +
     * {@code life} rows — the reference's readCustomNbt): the stick clock
     * resumes at the saved life. The saved velocity stays as loaded
     * (the reference's own shape — the stuck arm never integrates it, and
     * {@link #landInGround(int, int, int, String)}'s zeroing belongs to
     * the live hit alone).
     * Public like its live twin {@link #landInGround(int, int, int, String)}
     * (the import walk in another package is the caller).
     */
    public void restoreGrounded(int life) {
        this.inGround = true;
        this.ticksInGround = life;
    }

    /**
     * VT-015 — the restored stuck form WITH the tracked rows (the
     * reference's readCustomNbt lines 402-413: {@code xTile/yTile/zTile}
     * + {@code inTile} restore the consult — an imported stuck arrow
     * un-sticks when its saved block changes, exactly like a live one).
     */
    public void restoreGrounded(int life, int cellX, int cellY, int cellZ, String blockId) {
        this.inGround = true;
        this.ticksInGround = life;
        this.stickCellX = cellX;
        this.stickCellY = cellY;
        this.stickCellZ = cellZ;
        this.stickBlockId = blockId;
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
