package net.zaminmc.torch.server.leash;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;

/**
 * One leash knot (Slice 11c — the reference port of
 * entity/decoration/LeadKnotEntity): the fence-post entity the leads tie
 * to. The knot hangs at the fence's center (+0.5 all around, the
 * reference's own {@code setPosition(blockPos + 0.5)}), survives while its
 * fence stands (the reference's own 100-tick {@code canSurvive} poll),
 * and carries a global engine entity id so the wire can spawn it (Spawn
 * Object type 77) and the leashes can name it as their holder.
 *
 * <p>FL4-GAP2 Slice D — the dimension column: the knot carries its own
 * world's stamp (the reference's knot IS a per-WorldServer entity), so
 * the wire's observer gate and the family router read it.</p>
 */
public final class LeashKnot {

    private final int entityId;
    private final BlockPosition fence;
    /** The owning world's stamp (the wire's observer gate). */
    private final int dimension;
    /** The validation age (the reference's {@code ticksSinceValidation}). */
    private int validationTicks;

    LeashKnot(int entityId, BlockPosition fence, int dimension) {
        this.entityId = entityId;
        this.fence = fence;
        this.dimension = dimension;
    }

    /** The knot's engine entity id (the Spawn Object 77 wire id). */
    public int entityId() {
        return entityId;
    }

    /** The fence post the knot hangs on. */
    public BlockPosition fence() {
        return fence;
    }

    /** The owning world's stamp (the wire's observer gate reads it). */
    public int dimension() {
        return dimension;
    }

    /** The knot's entity position (the fence center + 0.5, the reference's own). */
    public Position position() {
        return new Position(fence.x() + 0.5, fence.y() + 0.5, fence.z() + 0.5);
    }

    /**
     * One tick of the knot's survival clock (DecorationEntity.tick lines
     * 113-125: the check runs every 100 ticks, not every tick — the
     * reference's own cadence; a broken fence leaves the knot hanging
     * for up to five seconds, the vanilla quirk).
     *
     * @return whether the knot survived this tick (false: the fence is
     * gone, the knot must fall).
     */
    boolean tickSurvival(net.zaminmc.torch.World world, Object expectedFence) {
        if (++validationTicks < 100) {
            return true;
        }
        validationTicks = 0;
        net.zaminmc.torch.block.BlockType at = world.getBlock(fence);
        return at != null && at.identifier().equals(expectedFence);
    }
}
