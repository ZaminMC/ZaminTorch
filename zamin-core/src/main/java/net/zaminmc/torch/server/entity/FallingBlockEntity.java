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
 * <p>Community entities.json (pc/1.8, FallingSand id 70) gives the historical
 * 0.98 box; physics reuses the shared simplified model (gravity 0.04, drag,
 * epsilon-correct ground snap). A falling block has no timers: it ends only by
 * landing (block or drop), leaving the world bottom, or removal by policy.</p>
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
        /** Fell out of the world: removed without a drop. */
        VOID
    }

    /** Position epsilon so a resting bottom queries the block *below* the surface. */
    private static final double GROUND_EPSILON = 1.0E-7;

    private final int entityId;
    private final net.zaminmc.torch.block.BlockType blockType;
    private Position position;
    private double velocityY;
    private boolean onGround;

    public FallingBlockEntity(int entityId, net.zaminmc.torch.block.BlockType blockType, Position center) {
        this.entityId = entityId;
        this.blockType = Objects.requireNonNull(blockType, "blockType");
        this.position = Objects.requireNonNull(center, "center");
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

    /**
     * The block column and level this entity rests on after a LANDED step:
     * the first air level above the solid surface its bottom touched.
     */
    public net.zaminmc.torch.block.BlockPosition landingPosition() {
        int bottomY = (int) Math.floor(position.y() - HALF_HEIGHT - GROUND_EPSILON);
        return new net.zaminmc.torch.block.BlockPosition(
                (int) Math.floor(position.x()), bottomY + 1, (int) Math.floor(position.z()));
    }

    /**
     * Advances one tick of the shared physics model. Returns the step outcome;
     * after LANDED the position is already the historical resting center.
     */
    public Step tick(Ground ground) {
        if (position.y() + HALF_HEIGHT < 0.0) {
            return Step.VOID; // out of the world: no drop, silently gone
        }
        velocityY = (velocityY - GRAVITY_PER_TICK) * 0.98;
        double newY = position.y() + velocityY;
        double probeY = newY - HALF_HEIGHT - GROUND_EPSILON;
        if (velocityY <= 0 && ground.isSolidAt(position.x(), probeY, position.z())) {
            double surfaceY = ground.supportY(position.x(), probeY, position.z());
            position = new Position(position.x(), surfaceY + HALF_HEIGHT, position.z());
            velocityY = 0.0;
            onGround = true;
            return Step.LANDED;
        }
        position = new Position(position.x(), newY, position.z());
        return Step.MOVED;
    }
}
