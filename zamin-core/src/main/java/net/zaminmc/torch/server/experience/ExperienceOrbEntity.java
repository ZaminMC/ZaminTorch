package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.util.Position;

import java.util.Objects;
import java.util.Random;

/**
 * One experience orb in the world (the historical EntityXPOrb): a small
 * physics body that carries a bundle of XP points and dies into the first
 * player's level that touches it.
 *
 * <p>Physics reuses the item-entity model (gravity, drag, epsilon ground
 * snap) — the orb is a smaller box of the same kind. The entity is engine
 * state owned by the simulation context; the id is engine-global from the
 * experience band so every observer agrees on it.</p>
 */
public final class ExperienceOrbEntity implements net.zaminmc.torch.entity.ExperienceOrb {

    /** The public kind identifier (§27.3): the experience-orb body. */
    private static final net.zaminmc.torch.util.Identifier TYPE_IDENTIFIER =
            net.zaminmc.torch.util.Identifier.parse("minecraft:xp_orb");

    /** Shared with items (the physics slice will unify the small-body model). */
    /** The reference's own orb gravity (Wave IO-F — ExperienceOrbEntity line 70: velocityY -= 0.03F, NOT the item family's 0.04). */
    public static final double GRAVITY_PER_TICK = 0.03;
    public static final double HALF_HEIGHT = 0.125;
    public static final double HALF_WIDTH = 0.125;
    /** Historical orb lifespan: 5 minutes, then it fades (the item rule). */
    public static final int DESPAWN_TICKS = 6_000;
    /** Position epsilon so a pushed bottom queries the block *below* the surface (the item arm's own form). */
    private static final double PUSH_GROUND_EPSILON = 1.0E-7;
    /** Fresh orbs wait a beat before pickup (the item-drop rhythm). */
    public static final int PICKUP_DELAY_TICKS = 10;
    /** The vanilla kill plane: an orb below it is gone without payout. */
    public static final double VOID_KILL_Y = -64.0;

    /** Minimal ground query the orb needs (tick-thread context only). */
    public interface Ground {
        /**
         * The dimension context switch (Wave TF-10 — the reference's
         * Entity.world, the item family's own TF-9 shape): the manager
         * flips the shared ground to the body's own dimension before
         * every tick walk. The default is a no-op so stub grounds keep
         * their overworld-only semantics.
         */
        default void setActiveDimension(int dimension) {
        }

        /**
         * VT-006 — the portal collision consult (the reference's gated
         * {@code PortalBlock.onEntityCollision} over the per-tick box
         * scan): the frame match at the body's cell in the ACTIVE
         * dimension, null when the cell is not a portal cell. The default
         * null keeps the stub grounds' pre-VT-006 no-crossing semantics
         * (an orb never rides, so the collision's vehicle/rider gate
         * passes vacuously for the family).
         */
        default net.zaminmc.torch.server.world.PortalFrameBuilder.PortalShapeMatch portalFrameAt(
                int x, int y, int z) {
            return null;
        }

        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);

        /**
         * The block id at the body's -0.2 feet cell (Wave IO-F — the
         * reference's Entity.move line 572 consult cell
         * {@code floor(x), floor(y - 0.2), floor(z)} with y the box
         * bottom; the engine's bottom = {@code position.y - HALF_HEIGHT}).
         * The vertical-clip consult (line 599's {@code beforeCollision})
         * reads it — the slime block's own override flips a falling
         * body's velocityY at full speed while the default zeroes it.
         * The default answers null so stub grounds keep the pre-wave
         * zeroing semantics (null is never the pad).
         */
        default net.zaminmc.torch.util.Identifier blockIdAt(double centerX, double bottomY, double centerZ) {
            return null;
        }

        /**
         * The block slipperiness at the support cell (Wave IO-F — the
         * reference's ExperienceOrbEntity.tick lines 107-114 over
         * Block.slipperiness: the drag band reads
         * {@code floor(x), floor(minY) - 1, floor(z)} — the cell BELOW
         * the feet cell, the engine's bottom =
         * {@code position.y - HALF_HEIGHT}). The default answers the
         * common case 0.6 (Block.java line 117) so stub grounds keep the
         * pre-wave drag.
         */
        default double slipperinessBelow(double centerX, double bottomY, double centerZ) {
            return 0.6;
        }

        /**
         * The lava consult at the body's feet cell (Wave LF — the
         * reference's ExperienceOrbEntity.tick line 71 over
         * {@code new BlockPos(this)} / {@code getMaterial() == LAVA}):
         * the cell CONTAINING the body position, the engine's feet =
         * {@code position.y - HALF_HEIGHT} (the reference's Entity.y IS
         * the feet). The orb's fizz is UNCONDITIONAL — every tick, no
         * gate — so the default false keeps the stub walks dry.
         */
        default boolean lavaAtCell(double centerX, double feetY, double centerZ) {
            return false;
        }
    }

    /**
     * The entity's sound emission seam (Wave LF — the same shape the
     * item family carries: the reference's {@code Entity.playSound} over
     * {@code World.playSound(x, y, z, ...)}); the manager wires it over
     * the FX broadcast, the entity decides when.
     */
    public interface Sounds {

        /** Emits a named sound at a position in the dimension. */
        void sound(Position at, String name, float volume, float pitch, int dimension);
    }

    /**
     * The attraction's player consult (Wave LF — the reference's
     * {@code world.getNearestPlayer(this, 8.0)} and the held
     * {@code igniter} field's own live reads): the manager resolves
     * both over the tick walk's players. The view carries exactly the
     * facts the attraction reads — the id (the sticky hold), the
     * FEET position (the retarget's own squaredDistanceTo compare,
     * entity position to entity position), the EYE height
     * ({@code y + getEyeHeight()} — 1.62 standing, 1.54 sneaking, 0.2
     * lying, the reference PlayerEntity lines 1648-1655 — the pull's
     * own aim point), and the spectator flag.
     */
    public interface Players {

        /**
         * The nearest non-spectator player within {@code range} of the
         * point in the dimension (the reference's NOT_SPECTATOR filter,
         * strictly-inside range), or null.
         */
        Target nearest(double x, double y, double z, int dimension, double range);

        /** The live same-dimension player with the id, or null. */
        Target byId(int entityId, int dimension);

        /** The attraction's own player view. */
        record Target(int entityId, double x, double feetY, double eyeY, double z,
                      boolean spectator) {
        }
    }

    private final int entityId;
    private final int amount;
    private Position position;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private int age;
    private int pickupDelay;
    private boolean onGround;
    /**
     * The body's own health (the reference's ExperienceOrbEntity line 15:
     * {@code private int health = 5}). The BLAST wound decrements it
     * (takeDamage lines 141-151 over the explosion's own source) and the
     * body dies at zero — removed whole, no payout. Not yet persisted
     * (the reference's {@code Health} tag rides the orb store's own
     * excepted row — the family's documented tail).
     */
    private int health = 5;
    /**
     * The reference's own {@code renderTicks} counter (the clock-frame
     * repair — ExperienceOrbEntity line 12's public field, incremented at
     * the tick's TAIL, line 123, INDEPENDENT from the despawn {@code age}
     * incremented beside it): the retarget window
     * {@code lastTargetUpdateTick < renderTicks - 20 + networkId % 100}
     * (line 80) rides THIS counter, and the NBT round trip writes the Age
     * tag alone — a RELOADED orb restarts renderTicks at 0 while its age
     * keeps the stored value, so the two clocks diverge exactly where a
     * fused {@code age - 1} frame would misplace the first-acquire window
     * (an orb with {@code id % 100 <= 20} waits out its staggered window
     * after load; the age frame would acquire on the first tick).
     */
    private int renderTicks;
    /**
     * The attraction's held igniter id (Wave LF — the reference's
     * {@code igniter} PlayerEntity field, id form): 0 holds nobody.
     * The throttle keeps the hold STICKY — a player inside the 8-block
     * radius keeps the pull until they leave it, the nearest search
     * runs only on the retarget cadence.
     */
    private int igniterId;
    /**
     * The retarget cadence stamp (Wave LF — the reference's
     * {@code lastTargetUpdateTick}, renderTicks frame).
     */
    private int lastTargetUpdateTick;
    /**
     * The body's dimension (Wave TF-10 — the reference's Entity.world, the
     * TF-9 shape over the orb family): 0 the overworld, -1 the nether. The
     * shared ground flips to the body's own world before every tick walk;
     * only the body's own dimension's players collect it.
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

    // ------------------------------------------------------------------
    // VT-006 — the entity-level portal arm over the orb family (the
    // reference's Entity.baseTick portal arm, lines 282-313, reached
    // through ExperienceOrbEntity.tick line 61's super.tick() chain).
    // The per-family duplication is the reference's own shape (the mobs'
    // copy is MobEntity's, PR #98; MinecartEntity lines 203-227 carries
    // another); the shared holder is the named follow-up.
    // ------------------------------------------------------------------

    /** The base entity's portal cooldown (Entity.getPortalCooldown lines 1457-1459). */
    private static final int ENTITY_PORTAL_COOLDOWN_TICKS = 300;

    /** The re-entry cooldown (Entity.baseTick lines 311-313 decrements). */
    private int portalCooldown;

    /** The stand clock (the base threshold 0 — Entity lines 354-355). */
    private int portalTime;

    /** The last portal cell the memory derived at (onPortalCollision's !equals guard). */
    private net.zaminmc.torch.block.BlockPosition lastPortalPosition;

    /** The memory's offset along the frame's width (the raw inverse-lerp, unclamped). */
    private double portalOffsetAlongWidth;

    /** The memory's offset up the frame's height (the raw inverse-lerp, unclamped). */
    private double portalOffsetUp;

    /** The memory's entered facing (the frame's forward horizontal id). */
    private int portalFacingHorizontalId;

    /** @return the portal re-entry cooldown. */
    public int portalCooldown() {
        return portalCooldown;
    }

    /** Sets the portal re-entry cooldown. */
    public void setPortalCooldown(int portalCooldown) {
        this.portalCooldown = portalCooldown;
    }

    /**
     * VT-006 — the entity-level portal clock, the orb family's copy of
     * the base law (Entity.baseTick lines 282-313): the stand threshold
     * is {@code getMaxNetherPortalTime() = 0} (lines 354-355), so a
     * colliding body with an expired cooldown crosses on the FIRST armed
     * tick; the cooldown arms at the base 300; a body out of the cells
     * decays its stand clock by 4 per tick, floored at zero.
     *
     * @param touching the manager's per-tick collision consult
     * @return true on the crossing tick (the dimension walk consumes the
     *         body's remaining walk)
     */
    public boolean advanceEntityPortalClock(boolean touching) {
        // The tail decrement (lines 311-313): every ticked body pays it.
        if (portalCooldown > 0) {
            portalCooldown--;
        }
        if (touching) {
            if (portalCooldown > 0) {
                // The collision consult's re-arm (onPortalCollision lines
                // 1429-1430): the arrival standing in the destination
                // frame never re-fires.
                portalCooldown = ENTITY_PORTAL_COOLDOWN_TICKS;
                return false;
            }
            // The crossing arm (lines 286-297): the base threshold 0 —
            // armed on the very first touching tick.
            portalTime = 0;
            portalCooldown = ENTITY_PORTAL_COOLDOWN_TICKS;
            return true;
        }
        // The decay arm (lines 302-308) — vacuous at the base threshold.
        if (portalTime > 0) {
            portalTime = Math.max(0, portalTime - 4);
        }
        return false;
    }

    /**
     * VT-006 — the reference's {@code onPortalCollision} memory arm
     * (lines 1428-1451): a NEW portal cell derives the body's offset
     * inside its frame (the raw inverse-lerp ratios, unclamped) and the
     * entered facing; the SAME cell re-collides without re-deriving.
     *
     * @param portalCell the body's portal cell (the consult's cell)
     * @param match      the frame's shape match at that cell
     * @param x          the body's x
     * @param y          the body's y
     * @param z          the body's z
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
     * VT-006 — the crossing's arrival over the orb family (the
     * reference's setPositionAndAngles + the world swap over the
     * recreated body, Entity.changeDimension lines 1708-1722: the amount
     * carries whole): the dimension stamp, the forcer's landing, and the
     * rotated motion. The orb body carries no yaw of its own.
     */
    public void applyDimensionArrival(int dimension, Position arrival,
                                      double velocityX, double velocityZ) {
        this.dimension = dimension;
        teleport(arrival);
        setVelocity(velocityX, velocityY, velocityZ);
    }

    public ExperienceOrbEntity(int entityId, Position position, int amount, int pickupDelayTicks) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Orbs carry a positive amount: " + amount);
        }
        this.entityId = entityId;
        this.amount = amount;
        this.position = Objects.requireNonNull(position, "position");
        this.pickupDelay = pickupDelayTicks;
    }

    public int entityId() {
        return entityId;
    }

    /** The body's current health (the blast wound's own ledger). */
    public int health() {
        return health;
    }

    /**
     * The blast wound's decrement (the manager's explosion arm is the
     * only caller). Package-private over the manager's verb.
     */
    void takeExplosionDamage(float amount) {
        this.health = (int) (this.health - amount);
    }

    public int amount() {
        return amount;
    }

    /** The despawn clock (readCustomNbt's own {@code Age} row resumes it). */
    public int age() {
        return age;
    }

    public Position position() {
        return position;
    }

    /**
     * The seated rider's plain position set (the riding-follow surface —
     * the reference's updateRiderPositon writes the rider's position
     * whatever family it is; the orb's own drift keeps running, the
     * seat re-anchoring the body each tick after it).
     */
    public void teleport(Position to) {
        this.position = Objects.requireNonNull(to, "to");
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

    public boolean onGround() {
        return onGround;
    }

    public boolean pickupAllowed() {
        return pickupDelay <= 0;
    }

    public boolean expired() {
        return age >= DESPAWN_TICKS;
    }

    /** @return true when the orb fell out of the world (silent removal due). */
    public boolean inVoid() {
        return position.y() < VOID_KILL_Y;
    }

    public void setVelocity(double vx, double vy, double vz) {
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
    }

    /**
     * The piston push (the VIII13 additive families — the reference's
     * {@code Entity.move(amount * facing)} over MovingBlockEntity's
     * interpolated box, lines 80-115): the IMMEDIATE, per-axis clamped
     * position shift — the same leading-face law the item family's own
     * arm carries (the orb's physics reuses the item model whole, this
     * class's own unify note; the box's leading face against the world's
     * solid cells, the blocked axis refusing its shift), the vertical
     * clamped by the box's own top/bottom faces. Outside the velocity
     * integration (the push is direct, the reference's own shape).
     */
    public void pushBy(Ground ground, double dx, double dy, double dz) {
        double newX = position.x();
        double newY = position.y();
        double newZ = position.z();
        if (dx != 0.0) {
            double leadX = position.x() + dx + Math.signum(dx) * HALF_WIDTH;
            if (!faceBlockedX(ground, leadX, position.y(), position.z())) {
                newX = position.x() + dx;
            }
        }
        if (dz != 0.0) {
            double leadZ = position.z() + dz + Math.signum(dz) * HALF_WIDTH;
            if (!faceBlockedZ(ground, newX, position.y(), leadZ)) {
                newZ = position.z() + dz;
            }
        }
        if (dy != 0.0) {
            double probeY = dy > 0
                    ? position.y() + dy + HALF_HEIGHT - PUSH_GROUND_EPSILON
                    : position.y() + dy - HALF_HEIGHT + PUSH_GROUND_EPSILON;
            if (!ground.isSolid(newX, probeY, newZ)) {
                newY = position.y() + dy;
            }
        }
        position = new Position(newX, newY, newZ);
    }

    /**
     * The X-leading-face test (the item family's own three-sample sweep):
     * the face at {@code leadX} over the box's z-width and y-height — the
     * bottom sits a hair above the resting surface so the ground the orb
     * slides on never blocks its own slide.
     */
    private static boolean faceBlockedX(Ground ground, double leadX,
                                        double centerY, double centerZ) {
        double bottom = centerY - HALF_HEIGHT + PUSH_GROUND_EPSILON;
        double top = centerY + HALF_HEIGHT - PUSH_GROUND_EPSILON;
        for (double y : new double[]{bottom, centerY, top}) {
            if (ground.isSolid(leadX, y, centerZ - HALF_WIDTH)
                    || ground.isSolid(leadX, y, centerZ)
                    || ground.isSolid(leadX, y, centerZ + HALF_WIDTH)) {
                return true;
            }
        }
        return false;
    }

    /** The Z-leading-face test: the face at {@code leadZ} over the x-width. */
    private static boolean faceBlockedZ(Ground ground, double centerX,
                                        double centerY, double leadZ) {
        double bottom = centerY - HALF_HEIGHT + PUSH_GROUND_EPSILON;
        double top = centerY + HALF_HEIGHT - PUSH_GROUND_EPSILON;
        for (double y : new double[]{bottom, centerY, top}) {
            if (ground.isSolid(centerX - HALF_WIDTH, y, leadZ)
                    || ground.isSolid(centerX, y, leadZ)
                    || ground.isSolid(centerX + HALF_WIDTH, y, leadZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The restored age (the Anvil import's exact-value landing — the
     * reference's readCustomNbt {@code Age} short): the saved orb's
     * despawn clock resumes where the save left it. Package-private: the
     * manager's spawnRestored arm is the only caller.
     */
    void setAge(int age) {
        this.age = age;
    }

    /**
     * Advances one tick of the orb's physics — the reference's own
     * ExperienceOrbEntity.tick model (Wave IO-F): the pre-move gravity
     * (0.03, the orb's own constant), the point-test move with its
     * vertical-clip consult, the post-move drag with the slipperiness
     * band, and the grounded vertical's -0.9 flip (line 121 — the orb's
     * own landing law). Returns true when the rounded fixed-point
     * position changed (the publisher's sync hint).
     */
    public boolean tick(Ground ground) {
        return tick(ground, null, null, null);
    }

    /**
     * Advances one tick with the full envelope (Wave LF — the
     * unconditional lava fizz + the igniter attraction): the null
     * consults keep the pre-wave physics-only semantics (the seats the
     * landing family drives).
     */
    public boolean tick(Ground ground, Random random, Sounds sounds, Players players) {
        long before = fixedPoint(position);
        age++;
        if (pickupDelay > 0) {
            pickupDelay--;
        }
        // THE PRE-MOVE GRAVITY (Wave IO-F — the reference's line 70,
        // {@code velocityY -= 0.03F}): gravity lands BEFORE the move,
        // UNCONDITIONALLY — the reference applies the *0.98 vertical drag
        // POST-move (line 117); the pre-fix engine had fused the pair into
        // (v - g) * 0.98 and gated it on !onGround.
        velocityY -= GRAVITY_PER_TICK;

        // THE LAVA FIZZ (Wave LF — the reference's lines 69-74): UNLIKE
        // the item family there is NO gate — every tick the feet cell's
        // lava pops the body straight up (velocityY = 0.2, the
        // horizontals re-rolled at (rand - rand) * 0.2, the fizz sound
        // at 0.4 / 2.0 + rand * 0.4) BEFORE the move, so the pop feeds
        // THIS tick's rise (the orb in lava climbs 0.2/tick, dragging
        // to 0.196 after the move).
        if (random != null && sounds != null
                && ground.lavaAtCell(position.x(), position.y() - HALF_HEIGHT, position.z())) {
            velocityY = 0.2;
            velocityX = (random.nextFloat() - random.nextFloat()) * 0.2;
            velocityZ = (random.nextFloat() - random.nextFloat()) * 0.2;
            // The reference's playSound at the entity: x/z the box center,
            // y the box BOTTOM (the engine's feet frame).
            sounds.sound(new Position(position.x(), position.y() - HALF_HEIGHT, position.z()),
                    "random.fizz", 0.4f,
                    2.0f + random.nextFloat() * 0.4f, dimension);
        }

        applyIgniterAttraction(players);

        double newY = position.y() + velocityY;
        double newX = position.x() + velocityX;
        double newZ = position.z() + velocityZ;

        double groundEpsilon = 1.0E-7;
        double clipConsultY = Double.NaN;
        if (velocityY <= 0 && ground.isSolid(newX, newY - HALF_HEIGHT - groundEpsilon, newZ)) {
            newY = Math.floor(newY - HALF_HEIGHT - groundEpsilon) + 1.0 + HALF_HEIGHT;
            onGround = true;
            clipConsultY = newY; // the landing clip (the vertical consult below)
        } else if (velocityY > 0 && ground.isSolid(newX, newY + HALF_HEIGHT + groundEpsilon, newZ)) {
            newY = position.y();
            onGround = false;
            clipConsultY = newY; // bumped a ceiling (the same consult below)
        } else {
            onGround = false;
        }

        position = new Position(newX, newY, newZ);
        if (!Double.isNaN(clipConsultY)) {
            // THE VERTICAL-CLIP CONSULT (the reference's Entity.move line
            // 599 over Block.beforeCollision line 882): the clip consults
            // the -0.2 feet cell — the slime block's override (SlimeBlock
            // lines 33-39) flips a falling body's velocityY at full speed
            // and keeps a rising one (the velocityY >= 0 no-op quirk), the
            // default zeroes it. Orbs never sneak. Riding the POST-move
            // position exactly like the reference's own placement.
            beforeCollisionAt(ground);
        }
        applyPostMoveDrag(ground);
        // The reference's renderTicks++ (ExperienceOrbEntity line 123, the
        // tick's TAIL — after the drag bands and after the retarget gate's
        // read): the counter the NEXT tick's retarget window reads, its own
        // clock independent of the age (the clock-frame repair).
        renderTicks++;
        return fixedPoint(position) != before;
    }

    /**
     * The vertical-clip consult (the reference's beforeCollision at the
     * clip): the -0.2 feet cell's block id decides the vertical response.
     */
    private void beforeCollisionAt(Ground ground) {
        net.zaminmc.torch.util.Identifier underfoot = ground.blockIdAt(
                position.x(), position.y() - HALF_HEIGHT, position.z());
        if (underfoot != null && underfoot.value().equals("slime_block")) {
            if (velocityY < 0.0) {
                velocityY = -velocityY;
            }
            // velocityY >= 0: the no-op quirk — a rising body clipped over
            // the pad keeps its rise.
        } else {
            velocityY = 0.0;
        }
    }

    /**
     * The post-move drag (the reference's ExperienceOrbEntity.tick lines
     * 107-121): the horizontal band reads the SUPPORT cell's slipperiness
     * on the ground (all * 0.98 — the ice family's 0.98 and the pad's 0.8
     * against the common case's 0.6), the air band is the flat 0.98, the
     * vertical drags at 0.98 — and a grounded body's vertical flips at
     * -0.9 (line 121: the orb's own landing law — over the pad it keeps
     * most of the beforeCollision bounce, over the common blocks it acts
     * on the zero the default consult left).
     */
    private void applyPostMoveDrag(Ground ground) {
        double f = 0.98;
        if (onGround) {
            f = ground.slipperinessBelow(position.x(), position.y() - HALF_HEIGHT, position.z()) * 0.98;
        }
        velocityX *= f;
        velocityY *= 0.98;
        velocityZ *= f;
        if (onGround) {
            velocityY *= -0.9;
        }
    }

    /**
     * THE IGNITER ATTRACTION (Wave LF — the reference's
     * ExperienceOrbEntity.tick lines 76-100): the 8-block pull toward
     * the nearest player, the retarget THROTTLED — every ~20 ticks
     * (staggered by {@code networkId % 100} so a burst of orbs does not
     * retarget the same tick; the window rides the body's OWN
     * {@code renderTicks} counter — the clock-frame repair, the
     * reference's line-123 tail-incremented field never persisted, so a
     * reloaded orb waits out its staggered window exactly like a fresh
     * one whatever its restored age) the hold re-arms only when empty
     * or the held player left the 8-block radius; the held igniter's
     * live state is read EVERY tick (a spectator release, the pull
     * itself). The pull: {@code d = (target - orb) / 8}, {@code d4 =
     * |d|}, {@code d5 = (1 - d4)²} — the velocity gains
     * {@code d/d4 * d5 * 0.1}, nothing beyond the radius (d4 >= 1
     * leaves d5 <= 0). The orb's own position reads the FEET frame
     * (position.y - HALF_HEIGHT — the reference's Entity.y), the
     * player's the EYE (y + 1.62 standing).
     */
    private void applyIgniterAttraction(Players players) {
        if (players == null) {
            return; // the pre-wave physics-only walk
        }
        // The reference's retarget window (line 80) over the body's OWN
        // renderTicks counter — incremented at the tick's TAIL (line 123),
        // never persisted (the NBT writes the Age tag alone), so a reloaded
        // orb restarts the window fresh while its age keeps the stored
        // value. The clock-frame repair: a fused age frame would misplace
        // the first-acquire window wherever the age was seeded.
        if (lastTargetUpdateTick < renderTicks - 20 + entityId % 100) {
            Players.Target held = igniterId == 0 ? null : players.byId(igniterId, dimension);
            double feetY = position.y() - HALF_HEIGHT;
            boolean lost = held == null
                    || squaredDistance(held.x(), held.feetY(), held.z()) > 8.0 * 8.0;
            if (lost) {
                Players.Target nearest = players.nearest(
                        position.x(), feetY, position.z(), dimension, 8.0);
                igniterId = nearest == null ? 0 : nearest.entityId();
            }
            lastTargetUpdateTick = renderTicks;
        }

        Players.Target igniter = igniterId == 0 ? null : players.byId(igniterId, dimension);
        if (igniter != null && igniter.spectator()) {
            igniterId = 0;
            igniter = null;
        }
        if (igniter == null) {
            return;
        }
        double d1 = (igniter.x() - position.x()) / 8.0;
        double d2 = (igniter.eyeY() - (position.y() - HALF_HEIGHT)) / 8.0;
        double d3 = (igniter.z() - position.z()) / 8.0;
        double d4 = Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3);
        double d5 = 1.0 - d4;
        if (d5 > 0.0) {
            d5 *= d5;
            velocityX += d1 / d4 * d5 * 0.1;
            velocityY += d2 / d4 * d5 * 0.1;
            velocityZ += d3 / d4 * d5 * 0.1;
        }
    }

    /** The squared distance to a world point from the orb's position. */
    private double squaredDistance(double x, double y, double z) {
        double dx = x - position.x();
        double dy = y - (position.y() - HALF_HEIGHT);
        double dz = z - position.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static long fixedPoint(Position position) {
        return (Math.round(position.x() * 32.0) << 42)
                | (Math.round(position.y() * 32.0) << 21)
                | Math.round(position.z() * 32.0);
    }

    // ------------------------------------------------------------------
    // The public API surface (§27.3 — the ExperienceOrb handle).
    // ------------------------------------------------------------------

    @Override
    public net.zaminmc.torch.util.Identifier typeIdentifier() {
        return TYPE_IDENTIFIER;
    }

    /**
     * The any-thread-consumable view (§27.6): captured on the owner
     * context inside {@code World.execute}. Health/maxHealth do not
     * apply to the orb body (1/1); {@code dead} answers the lifetime's
     * expiry state.
     */
    @Override
    public net.zaminmc.torch.entity.EntitySnapshot snapshot() {
        return new net.zaminmc.torch.entity.EntitySnapshot(
                entityId, TYPE_IDENTIFIER, position, 0.0f,
                velocityX, velocityY, velocityZ,
                1.0f, 1.0f, onGround, expired());
    }
}
