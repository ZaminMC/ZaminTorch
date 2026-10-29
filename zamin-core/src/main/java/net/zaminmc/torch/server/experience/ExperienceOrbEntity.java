package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.FluidBlocks;

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
         * The block id at the body's OWN cell (the sixteenth pass —
         * the reference's lava-fizz consult {@code new BlockPos(this)},
         * the 1.8.8 position the box BOTTOM: {@code floor(x),
         * floor(y), floor(z)} — the OWN cell, never the -0.2 feet cell
         * the clip consult rides). ExperienceOrbEntity.tick lines 71-76
         * test the material against it (both lava blocks answer
         * {@code Material.LAVA}; the engine's
         * {@code FluidBlocks.Kind.LAVA} carries the same set — the
         * source, every flowing/falling/still form, every level). The
         * default answers null so stub grounds never fizz (null is
         * never lava).
         */
        default net.zaminmc.torch.util.Identifier blockIdAtCell(double centerX, double bottomY, double centerZ) {
            return null;
        }
    }

    /**
     * THE ATTRACTION CONSULT (the sixteenth pass — the reference's
     * {@code World.getNearestPlayer} lines 2399-2413 over
     * {@code EntityFilter.NOT_SPECTATOR} lines 27-31): the nearest
     * non-spectator player of the given dimension within 8 blocks of
     * the orb's own cell (squared distance < 64, the minimum over the
     * walk) — the ONLY filter the reference applies (a dead player
     * still attracts; the spectator never does). The manager supplies
     * the walk over its own players iterable.
     */
    public interface Attraction {

        /**
         * @return the nearest qualifying igniter (the sticky target's
         *         acquire), or null when no player is in range.
         */
        net.zaminmc.torch.server.player.PlayerSession nearestIgniter(
                int dimension, double centerX, double bottomY, double centerZ);
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
     * The body's own Random (the sixteenth pass — the reference's
     * {@code Entity.random}, Entity.java lines 99/158: {@code new Random()}
     * at construction). The lava fizz's three scatter draws and the pitch
     * draw come from it (ExperienceOrbEntity.tick lines 73-75: the
     * reference draws {@code this.random.nextFloat()}), exactly like the
     * reference's own per-entity field.
     */
    private final Random random = new Random();
    /**
     * The attraction's own clock (the reference's {@code renderTicks},
     * ExperienceOrbEntity line 12 — incremented at the tick's END, line
     * 123): the retarget cadence {@code lastTargetUpdateTick < renderTicks
     * - 20 + id % 100} rides it, an INDEPENDENT clock from the despawn
     * {@code age} (which the Anvil restore seeds — a fused counter would
     * drift on every restored orb).
     */
    private int renderTicks;
    /**
     * The sticky target (the reference's {@code igniter}, line 17): the
     * player the orb swims toward. Kept across ticks unless null, beyond
     * 64 squared distance, or turned spectator — a closer player never
     * steals the lock (lines 80-86).
     */
    private net.zaminmc.torch.server.player.PlayerSession igniter;
    /** The retarget cadence's watermark (the reference's line 18). */
    private int lastTargetUpdateTick;
    /** This tick's fizz pitch (NaN when the tick carried no pop). Take-once. */
    private float fizzPitch = Float.NaN;
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
     *
     * <p>The old signature delegates with the null-attraction world (no
     * players in the walk — no igniter, no pull), so the direct-seat
     * lanes keep their exact pre-wave behavior.</p>
     */
    public boolean tick(Ground ground) {
        return tick(ground, (dimension, centerX, bottomY, centerZ) -> null);
    }

    /**
     * The full tick (the sixteenth pass — the reference's
     * ExperienceOrbEntity.tick lines 61-128 whole): the pre-move gravity
     * (line 70), THE LAVA FIZZ (lines 71-76 — UNCONDITIONAL, no gate,
     * before the move), THE IGNITER ATTRACTION (lines 79-104 — the
     * staggered retarget, the spectator clear, the eye-aimed quadratic
     * pull), the move (line 106), the post-move drag (lines 107-121),
     * and the attraction clock's END increment (line 123).
     */
    public boolean tick(Ground ground, Attraction attraction) {
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
        // THE LAVA FIZZ (the sixteenth pass — the reference's lines
        // 71-76): UNCONDITIONAL — every tick, no crossing flag, no poll
        // (the item family's gates; the orb consults its own cell every
        // single tick). Placed after the gravity and before the move:
        // the popped velocity feeds the same tick's move, then the drag
        // bands damp it (airborne 0.98; a grounded body's -0.9 flip).
        maybeLavaFizz(ground);
        // THE IGNITER ATTRACTION (the sixteenth pass — the reference's
        // lines 79-104): the staggered retarget, the spectator clear,
        // the eye-aimed quadratic pull — before the move, so the pull's
        // velocity feeds the same tick's move (the reference's own
        // placement: a lava-popped orb's scatter is not overwritten by
        // the pull; the pull ADDS on top of it).
        applyIgniterPull(attraction);

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
        // The attraction's own clock (the reference's line 123):
        // renderTicks++ at the tick's END — the cadence comparison at
        // the NEXT tick's top reads the pre-increment value, exactly the
        // stagger arithmetic the retarget law rides.
        renderTicks++;
        return fixedPoint(position) != before;
    }

    /**
     * The tick's fizz pitch — the reference's {@code playSound(
     * "random.fizz", 0.4F, 2.0F + nextFloat() * 0.4F)} (line 75) — NaN
     * when the tick carried no pop. Take-once: the manager drains it and
     * publishes the Listener arm the wire adapter turns into the sound.
     */
    public float takeFizzPitch() {
        float pitch = fizzPitch;
        fizzPitch = Float.NaN;
        return pitch;
    }

    /**
     * THE ORB'S LAVA FIZZ (the sixteenth pass — the reference's
     * ExperienceOrbEntity.tick lines 71-76): UNCONDITIONAL — every tick,
     * no crossing flag, no poll (the ITEM family's gates; the orb never
     * waits). The consult is the body's OWN cell ({@code new BlockPos(this)}
     * — floor(x), floor(bottom), floor(z)) against the LAVA material (both
     * lava blocks answer it — the engine's {@code FluidBlocks.Kind.LAVA}
     * the same set); the response is the 0.2 vertical pop, the ±0.2
     * horizontal scatter (two nextFloat draws per axis) and the fizz pitch.
     * The pop feeds the same tick's move and drag bands (and the attraction
     * pull adds ON TOP of it — the reference's own order, the pull never
     * overwrites the scatter).
     */
    private void maybeLavaFizz(Ground ground) {
        net.zaminmc.torch.util.Identifier inCell = ground.blockIdAtCell(
                position.x(), position.y() - HALF_HEIGHT, position.z());
        if (inCell != null && FluidBlocks.kindOf(inCell) == FluidBlocks.Kind.LAVA) {
            velocityY = 0.2;
            velocityX = (random.nextFloat() - random.nextFloat()) * 0.2f;
            velocityZ = (random.nextFloat() - random.nextFloat()) * 0.2f;
            fizzPitch = 2.0f + random.nextFloat() * 0.4f;
        }
    }

    /**
     * THE IGNITER ATTRACTION (the sixteenth pass — the reference's
     * ExperienceOrbEntity.tick lines 79-104): the retarget cadence
     * {@code lastTargetUpdateTick < renderTicks - 20 + id % 100} (the
     * id stagger — an orb's first acquire waits its id's own offset),
     * the STICKY target (the current igniter is kept unless null or
     * beyond 64 squared distance — a closer player never steals the
     * lock; only the out-of-range exit re-acquires, the nearest then
     * wins), the spectator clear (EVERY tick, never only retarget
     * ticks — the player who turns spectator stops pulling the orb
     * immediately), and the pull itself: the eye-aimed offset over 8
     * ({@code igniter.y + getEyeHeight()} — PlayerEntity line 1648's
     * 1.62F standing, -0.08F sneaking, 0.2F sleeping), d4 the
     * normalized distance, {@code d5 = (1 - d4)^2} — the pull grows
     * QUADRATICALLY as the orb nears the player — and the velocity
     * add {@code d_i / d4 * d5 * 0.1} per axis. The engine's
     * same-dimension consult (the TF-10 pattern — the reference's own
     * world scoping: the other world's player entity is GONE from this
     * world's walk, never re-acquired) rides the acquire, the keep and
     * the pull.
     */
    private void applyIgniterPull(Attraction attraction) {
        double d0 = 8.0;
        if (lastTargetUpdateTick < renderTicks - 20 + entityId % 100) {
            if (igniter == null || igniterOutOfScope()) {
                igniter = attraction.nearestIgniter(dimension,
                        position.x(), position.y() - HALF_HEIGHT, position.z());
            }
            lastTargetUpdateTick = renderTicks;
        }
        // The spectator clear (lines 88-90) — EVERY tick, before the pull
        // arm; the same-dimension clear rides it (the other world's
        // player is gone from this world's walk — the TF-10 scoping).
        if (igniter != null && (igniter.gamemode() == net.zaminmc.torch.GameMode.SPECTATOR
                || igniter.dimension() != dimension)) {
            igniter = null;
        }
        if (igniter != null) {
            Position feet = igniter.position();
            double d1 = (feet.x() - position.x()) / d0;
            double eye = igniterEye();
            double d2 = (feet.y() + eye - (position.y() - HALF_HEIGHT)) / d0;
            double d3 = (feet.z() - position.z()) / d0;
            double d4 = Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3);
            double d5 = 1.0 - d4;
            if (d5 > 0.0) {
                d5 *= d5;
                velocityX += d1 / d4 * d5 * 0.1;
                velocityY += d2 / d4 * d5 * 0.1;
                velocityZ += d3 / d4 * d5 * 0.1;
            }
        }
    }

    /**
     * The sticky keep's own range consult (the reference's line 81:
     * {@code igniter.squaredDistanceTo(this) > d0 * d0} — the RECEIVER
     * the player, the argument the orb; the positions are the player's
     * feet against the orb's own bottom, the raw squared distance).
     */
    private boolean igniterOutOfScope() {
        if (igniter.dimension() != dimension) {
            return true; // the other world's player is gone from this walk
        }
        Position feet = igniter.position();
        double dx = position.x() - feet.x();
        double dy = (position.y() - HALF_HEIGHT) - feet.y();
        double dz = position.z() - feet.z();
        return dx * dx + dy * dy + dz * dz > 8.0 * 8.0;
    }

    /**
     * The pull's eye-height aim (the reference's PlayerEntity.getEyeHeight
     * line 1648): 1.62F standing; a sleeping body's 0.2F; a sneaking
     * body's -0.08F (the reference's own order — sleeping sets, sneaking
     * subtracts on top).
     */
    private double igniterEye() {
        double eye = 1.62;
        if (igniter.sleeping()) {
            eye = 0.2;
        }
        if (igniter.sneaking()) {
            eye -= 0.08;
        }
        return eye;
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
