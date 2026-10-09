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
public final class FallingBlockEntity {

    /** Historical gravity, shared with items and mobs (physics slice unifies). */
    public static final double GRAVITY_PER_TICK = 0.04;
    /** Community entities.json: the FallingSand box is 0.98, half 0.49. */
    public static final double HALF_HEIGHT = 0.49;
    public static final double HALF_WIDTH = 0.49;

    /** Minimal ground query the entity needs from its world (tick-thread context only). */
    public interface Ground {
        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);

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
    private final net.zaminmc.torch.block.BlockType blockType;
    private Position position;
    private double velocityY;
    private boolean onGround;
    private int fallingTicks;

    public FallingBlockEntity(int entityId, net.zaminmc.torch.block.BlockType blockType, Position center) {
        this.entityId = entityId;
        this.blockType = Objects.requireNonNull(blockType, "blockType");
        this.position = Objects.requireNonNull(center, "center");
    }

    /** The test seam: a fall already {@code initialFallingTicks} deep. */
    FallingBlockEntity(int entityId, net.zaminmc.torch.block.BlockType blockType, Position center,
                       int initialFallingTicks) {
        this.entityId = entityId;
        this.blockType = Objects.requireNonNull(blockType, "blockType");
        this.position = Objects.requireNonNull(center, "center");
        this.fallingTicks = initialFallingTicks;
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

    public boolean onGround() {
        return onGround;
    }

    /** The vanilla {@code fallingTicks}: the tick count since the fall began. */
    public int fallingTicks() {
        return fallingTicks;
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
                || id.equals(net.zaminmc.torch.server.block.BuiltinBlocks.FIRE.identifier());
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
}
