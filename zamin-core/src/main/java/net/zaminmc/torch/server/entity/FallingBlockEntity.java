package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;

import net.zaminmc.torch.util.Position;

import java.util.Objects;

/**
 * One falling block in the world — the §470 transition between block state and
 * entity state: a sand/gravel block whose support vanished continues its fall
 * as this entity and becomes a block again where it lands.
 *
 * <p>Physics ported from the vanilla 1.8.8 FallingBlockEntity (reference/
 * 1.8.8/net/minecraft/entity/FallingBlockEntity.java): gravity 0.04 applied
 * before the move and the 0.98 drag after it (the drag shapes the next tick's
 * displacement, not this one's), the 0.98 box, the vanilla position
 * convention (the position IS the box bottom — X/Z centered in the cell, Y
 * at the cell's bottom, exactly what the vanilla spawn packet sends), and
 * the vanilla lifetime rule — an entity still falling after 600 ticks, or
 * 100+ ticks with its cell outside y 1..256, drops itself as an item instead
 * of falling forever ({@code doEntityDrops}, on by default). Community
 * entities.json (pc/1.8, FallingSand id 70) confirms the 0.98 box.</p>
 */
public final class FallingBlockEntity implements net.zaminmc.torch.entity.FallingBlock {

    /** The public kind identifier (§27.3): the falling-block body. */
    private static final net.zaminmc.torch.util.Identifier TYPE_IDENTIFIER =
            net.zaminmc.torch.util.Identifier.parse("minecraft:falling_block");

    /** Historical gravity, shared with items and mobs (physics slice unifies). */
    public static final double GRAVITY_PER_TICK = 0.04;
    /** Community entities.json: the FallingSand box is 0.98, half 0.49. */
    public static final double HALF_HEIGHT = 0.49;
    public static final double HALF_WIDTH = 0.49;

    /** Minimal ground query the entity needs from its world (tick-thread context only). */
    public interface Ground extends net.zaminmc.torch.server.physics.HasCollisionWorld {
        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);

        /**
         * The dimension context switch (Wave FL4-F9 — the manager's
         * per-body flip before every tick walk, ItemEntity.Ground's own
         * shape): the engine's one shape ground re-points at the body's
         * own world; the stub grounds ignore it.
         */
        default void setActiveDimension(int dimension) {
        }

        /**
         * The shape-aware point test (the collision-shape slice); the
         * default folds to the boolean world for stub grounds.
         */
        default boolean isSolidAt(double x, double y, double z) {
            return isSolid(x, y, z);
        }

        /**
         * The top surface of the collision shape in the point's cell; the
         * default mirrors the boolean world's full-cube snap. Non-solid
         * cells answer negative infinity.
         */
        default double supportY(double x, double y, double z) {
            return isSolid(x, y, z) ? Math.floor(y) + 1.0 : Double.NEGATIVE_INFINITY;
        }
    }

    /** One physics step's outcome; the manager turns it into world state. */
    public enum Step {
        /** Still falling, position changed. */
        MOVED,
        /** Hit the ground: {@link #landingBlockY()} names the block to become. */
        LANDED,
        /**
         * The vanilla lifetime rule fired (600 ticks falling, or below y=1
         * after 100): the manager drops the block as an item, as vanilla's
         * {@code doEntityDrops} path does.
         */
        VOID
    }

    /** Position epsilon so a resting bottom queries the block *below* the surface. */
    private static final double GROUND_EPSILON = 1.0E-7;

    private final int entityId;
    /**
     * The body's OWN dimension (Wave FL4-F9 — the reference's {@code new
     * FallingSandBlock(world, ...)}): the physics ground flip, the landing
     * commit and the drop all range the body's OWN world's cells.
     */
    private final int dimension;
    private final net.zaminmc.torch.block.BlockType blockType;
    private Position position;
    private double velocityY;
    private boolean onGround;
    private int fallingTicks;
    /** The fall's source Y (Slice 53): the box bottom's starting height — the accumulated displacement is exactly {@code startY - restingY} (the fall only descends). */
    private final double startY;

    public FallingBlockEntity(int entityId, net.zaminmc.torch.block.BlockType blockType, Position center) {
        this(entityId, 0, blockType, center);
    }

    /**
     * The dimension-stamped spawn (Wave FL4-F9 — the reference's {@code
     * new FallingSandBlock(world, ...)} riding the world it was spawned
     * in): the body's physics ground, landing commit and drop all range
     * ITS OWN world's cells.
     */
    FallingBlockEntity(int entityId, int dimension,
                       net.zaminmc.torch.block.BlockType blockType, Position center) {
        this.entityId = entityId;
        this.dimension = dimension;
        this.blockType = Objects.requireNonNull(blockType, "blockType");
        this.position = Objects.requireNonNull(center, "center");
        this.startY = center.y();
    }

    /** The test seam: a fall already {@code initialFallingTicks} deep. */
    FallingBlockEntity(int entityId, net.zaminmc.torch.block.BlockType blockType, Position center,
                       int initialFallingTicks) {
        this(entityId, 0, blockType, center);
        this.fallingTicks = initialFallingTicks;
    }

    /** The body's own dimension (the world the fall started in). */
    public int dimension() {
        return dimension;
    }

    public int entityId() {
        return entityId;
    }

    public net.zaminmc.torch.block.BlockType blockType() {
        return blockType;
    }

    public Position position() {
        return position;
    }

    public double velocityY() {
        return velocityY;
    }

    /**
     * The piston push (the VIII13 additive families — the reference's
     * {@code Entity.move(amount * facing)} over MovingBlockEntity's
     * interpolated box, lines 80-115): the IMMEDIATE, per-axis clamped
     * position shift riding the family's OWN discipline — the swept-box
     * solver over the ground's collision world when it provides the
     * surface (the tick's own two-path shape), the plain per-axis point
     * consults otherwise (the leading-face law over the 0.98 box, the
     * descending axis clamped at the support's exact top). The landing
     * itself stays the tick's own business — a pushed body that ends on
     * a support settles through the manager's next pass exactly as a
     * falling one does. Outside the velocity integration (the push is
     * direct, the reference's own shape).
     */
    public void pushBy(Ground ground, double dx, double dy, double dz) {
        net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld =
                ground.collisionWorld();
        if (collisionWorld != null) {
            // The tick's own swept-box discipline: the 0.98 box's clip
            // against every collision box the shift touches.
            net.zaminmc.torch.server.physics.EntityMoveSolver.Body body =
                    new net.zaminmc.torch.server.physics.EntityMoveSolver.Body(
                            position.x(), position.y(), position.z(),
                            HALF_WIDTH * 2.0, HALF_HEIGHT * 2.0);
            body.onGround = onGround;
            net.zaminmc.torch.server.physics.EntityMoveSolver.Result result =
                    net.zaminmc.torch.server.physics.EntityMoveSolver.move(
                            collisionWorld, body, dx, dy, dz,
                            net.zaminmc.torch.server.physics.EntityMoveSolver.MoveLaw.plain());
            position = new Position(body.x, body.y, body.z);
            onGround = result.onGround();
            return;
        }
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
            if (dy < 0.0) {
                double newBottom = position.y() + dy;
                if (ground.isSolidAt(newX, newBottom - GROUND_EPSILON, newZ)) {
                    double surfaceY = ground.supportY(newX, newBottom - GROUND_EPSILON, newZ);
                    newY = surfaceY != Double.NEGATIVE_INFINITY ? surfaceY : position.y();
                } else {
                    newY = newBottom;
                }
            } else {
                double probeTop = position.y() + dy + HALF_HEIGHT * 2.0 - GROUND_EPSILON;
                if (!ground.isSolidAt(newX, probeTop, newZ)) {
                    newY = position.y() + dy;
                }
            }
        }
        position = new Position(newX, newY, newZ);
    }

    /**
     * The X-leading-face test (the item family's three-sample sweep):
     * the face at {@code leadX} over the box's z-width and y-height —
     * the bottom sits a hair above the resting surface so the support
     * the block slides on never blocks its own slide.
     */
    private static boolean faceBlockedX(Ground ground, double leadX,
                                        double bottomY, double centerZ) {
        double bottom = bottomY + GROUND_EPSILON;
        double top = bottomY + HALF_HEIGHT * 2.0 - GROUND_EPSILON;
        for (double y : new double[]{bottom, bottomY + HALF_HEIGHT, top}) {
            if (ground.isSolidAt(leadX, y, centerZ - HALF_WIDTH)
                    || ground.isSolidAt(leadX, y, centerZ)
                    || ground.isSolidAt(leadX, y, centerZ + HALF_WIDTH)) {
                return true;
            }
        }
        return false;
    }

    /** The Z-leading-face test: the face at {@code leadZ} over the x-width. */
    private static boolean faceBlockedZ(Ground ground, double centerX,
                                        double bottomY, double leadZ) {
        double bottom = bottomY + GROUND_EPSILON;
        double top = bottomY + HALF_HEIGHT * 2.0 - GROUND_EPSILON;
        for (double y : new double[]{bottom, bottomY + HALF_HEIGHT, top}) {
            if (ground.isSolidAt(centerX - HALF_WIDTH, y, leadZ)
                    || ground.isSolidAt(centerX, y, leadZ)
                    || ground.isSolidAt(centerX + HALF_WIDTH, y, leadZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The slime-block fling (MovingBlockEntity lines 94-107): the
     * velocity SET along the facing axis — the falling family's own
     * carrier is the VERTICAL one alone (the flight integrates velocityY
     * only, the family's established vertical-only model), so the
     * vertical axis's set component rides and the horizontal ones have
     * no carrier to carry them (the named shape — the reference's own
     * fling lands through the family's own integration law).
     */
    public void flingAxis(double axisX, double axisY, double axisZ) {
        if (axisY != 0.0) {
            velocityY = axisY;
        }
    }

    public boolean onGround() {
        return onGround;
    }

    /** The vanilla {@code fallingTicks}: the tick count since the fall began. */
    public int fallingTicks() {
        return fallingTicks;
    }

    /** The fall's source height (Slice 53): the box bottom's starting Y. */
    public double startY() {
        return startY;
    }

    /**
     * The accumulated fall distance at a resting bottom (Slice 53 — the
     * vanilla {@code fallDistance} the takeFallDamage walk receives): the
     * displacement from the source cell's bottom to the resting bottom.
     */
    public double fallDistance() {
        return Math.max(0.0, startY - position.y());
    }

    /**
     * The block column and level this entity rests on after a LANDED step:
     * the cell the box bottom sits in (the vanilla position convention — the
     * position is the box bottom, so the resting cell is simply its floor;
     * the resting bottom is the support's exact top, no drift).
     */
    public net.zaminmc.torch.block.BlockPosition landingPosition() {
        return new net.zaminmc.torch.block.BlockPosition(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y()),
                (int) Math.floor(position.z()));
    }

    /**
     * The bounds-safe block read for the fall paths: cells outside the y
     * 0..255 band answer air (the vanilla world answers air out of column),
     * never an index error — a sand at the band bottom falls and a landing
     * there cannot be held.
     */
    public static net.zaminmc.torch.block.BlockType blockOrAir(
            net.zaminmc.torch.server.world.EngineWorld world, BlockPosition position) {
        if (position.y() < 0 || position.y() >= 256) {
            return world.airType();
        }
        return world.getBlock(position);
    }

    /**
     * The vanilla {@code FallingBlock.canFallThrough}: a falling block passes
     * air, water, lava and fire and rests on everything else — the exact
     * trigger set (a sand stacked on tall grass does NOT fall, the historical
     * quirk) and the fall path's pass-through set.
     */
    public static boolean canFallThrough(net.zaminmc.torch.block.BlockType type) {
        net.zaminmc.torch.util.Identifier id = type.identifier();
        return id.toString().equals("minecraft:air")
                || net.zaminmc.torch.server.block.FluidBlocks.isWater(id)
                || net.zaminmc.torch.server.block.FluidBlocks.isLava(id)
                || net.zaminmc.torch.server.block.FireBlocks.isFire(id);
    }

    /**
     * The vanilla {@code canPlace} landing set: the replaceable materials —
     * air, water, lava, fire ({@code AirMaterial}/{@code LiquidMaterial} are
     * setReplaceable) plus the replaceable plants ({@code Material.
     * REPLACEABLE_PLANT}) the engine registers — so a settling sand replaces
     * the water or the flora it lands in instead of popping as an item.
     */
    public static boolean canBeReplacedOnLanding(net.zaminmc.torch.block.BlockType type) {
        return canFallThrough(type)
                || type.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.TALL_GRASS.identifier())
                || type.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.DEAD_BUSH.identifier())
                || type.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.DANDELION.identifier())
                || type.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.POPPY.identifier());
    }

    /**
     * Advances one tick of the vanilla physics model in the vanilla order —
     * the {@code fallingTicks} lifetime check, gravity, the move, then the
     * 0.98 drag (FallingBlockEntity.tick lines 85-89: the drag follows the
     * move, so it shapes the next tick's displacement, not this one's); the
     * position is the 0.98 box's bottom, exactly vanilla's entity convention.
     * The vanilla lifetime rule ends the fall as a drop: 600 ticks of
     * falling, or 100+ ticks with the cell outside y 1..256 ({@code
     * blockpos.getY() < 1 || blockpos1.getY() > 256} — the integer cell,
     * not the raw coordinate). After LANDED the position is the historical
     * resting bottom (the support's exact top).
     */
    public Step tick(Ground ground) {
        fallingTicks++;
        int cellY = (int) Math.floor(position.y());
        // The vanilla timeout branch (fallingTicks > 600, or > 100 with the
        // cell outside [1, 256]): the entity drops as an item rather than
        // falling forever.
        if (fallingTicks > 600 || (fallingTicks > 100 && (cellY < 1 || cellY > 256))) {
            return Step.VOID;
        }
        velocityY -= GRAVITY_PER_TICK;
        // THE SWEPT-BOX LANDING (the physics-solver slice): the 0.98
        // box's own Y clip against every collision box its swept shape
        // touches — the box's FOOTPRINT catches the slab's edge and the
        // funnel's rim the centre point misses — when the ground
        // provides the collision surface.
        net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld =
                ground.collisionWorld();
        if (collisionWorld != null) {
            net.zaminmc.torch.server.physics.EntityMoveSolver.Body body =
                    new net.zaminmc.torch.server.physics.EntityMoveSolver.Body(
                            position.x(), position.y(), position.z(),
                            HALF_WIDTH * 2.0, HALF_HEIGHT * 2.0);
            body.onGround = onGround;
            net.zaminmc.torch.server.physics.EntityMoveSolver.Result result =
                    net.zaminmc.torch.server.physics.EntityMoveSolver.move(
                            collisionWorld, body, 0.0, velocityY, 0.0,
                            net.zaminmc.torch.server.physics.EntityMoveSolver.MoveLaw.plain());
            position = new Position(body.x, body.y, body.z);
            onGround = result.onGround();
            if (onGround) {
                // The vanilla post-ground velocities (drag, then the -0.5
                // bounce): kept for state parity, unobservable — the
                // manager removes a landed entity this same tick.
                velocityY = velocityY * 0.98 * -0.5;
                return Step.LANDED;
            }
            velocityY *= 0.98;
            return Step.MOVED;
        }
        double newBottom = position.y() + velocityY;
        // The vanilla move: the box bottom collides with the support; the
        // epsilon keeps a resting bottom querying the block below the surface.
        double probeY = newBottom - GROUND_EPSILON;
        if (velocityY <= 0 && ground.isSolidAt(position.x(), probeY, position.z())) {
            double surfaceY = ground.supportY(position.x(), probeY, position.z());
            position = new Position(position.x(), surfaceY, position.z());
            // The vanilla post-ground velocities (drag, then the -0.5
            // bounce): kept for state parity, unobservable — the manager
            // removes a landed entity this same tick.
            velocityY = velocityY * 0.98 * -0.5;
            onGround = true;
            return Step.LANDED;
        }
        position = new Position(position.x(), newBottom, position.z());
        velocityY *= 0.98;
        return Step.MOVED;
    }

    // ------------------------------------------------------------------
    // The public API surface (§27.3 — the FallingBlock handle).
    // ------------------------------------------------------------------

    @Override
    public net.zaminmc.torch.util.Identifier typeIdentifier() {
        return TYPE_IDENTIFIER;
    }

    /**
     * The any-thread-consumable view (§27.6): captured on the owner
     * context inside {@code World.execute}. Health/maxHealth do not
     * apply to the falling body (1/1); the vertical-only flight carries
     * no horizontal velocity.
     */
    @Override
    public net.zaminmc.torch.entity.EntitySnapshot snapshot() {
        return new net.zaminmc.torch.entity.EntitySnapshot(
                entityId, TYPE_IDENTIFIER, position, 0.0f,
                0.0, velocityY, 0.0,
                1.0f, 1.0f, onGround, false);
    }

    /** The public block read (the engine's own {@link #blockType()}). */
    @Override
    public BlockType block() {
        return blockType;
    }
}
