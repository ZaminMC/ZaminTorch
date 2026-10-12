package net.zaminmc.torch.dispenser;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.World;

/**
 * The dispense a {@link DispenseBehavior} answers with: the world, the
 * dispensing block, its facing, and the picked stack.
 *
 * <p><b>Threading (§27.6):</b> the context is handed out on the
 * simulation owner's context, inside the dispense itself — the world and
 * the block state are live authoritative views and their mutation
 * methods carry their own ownership contracts (the dispense lane IS the
 * owner lane, so a behavior may place blocks directly, exactly the way
 * the built-in fire and fluid arms do).</p>
 */
public interface DispenseContext {

    /** The world the dispenser stands in (live authoritative view). */
    World world();

    /** The dispensing block's position. */
    BlockPosition origin();

    /** The dispenser's facing (the historical 0-5 id order). */
    Direction facing();

    /** The stack the reservoir picked (read-only view of the slot). */
    ItemStack stack();

    /** The cell the dispenser faces — the classic dispense target. */
    default BlockPosition facingCell() {
        Direction facing = facing();
        return new BlockPosition(
                origin().x() + facing.offsetX(),
                origin().y() + facing.offsetY(),
                origin().z() + facing.offsetZ());
    }
}
