package net.zaminmc.torch.event.block;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;

/**
 * A block is about to be broken by a player — creative or the completed
 * survival dig.
 *
 * <p>Fires on the world's simulation context before the world mutation:
 * the block is still in place, the drops have not rolled, the tool has
 * not worn. Cancelling prevents the break entirely (no drops, no tool
 * wear, no XP); the engine re-syncs the cell so the breaking client drops
 * its prediction.</p>
 */
public final class BlockBreakEvent implements Event, Cancellable {

    private final Player player;
    private final World world;
    private final BlockPosition position;
    private final BlockType block;
    private boolean cancelled;

    public BlockBreakEvent(Player player, World world, BlockPosition position, BlockType block) {
        this.player = player;
        this.world = world;
        this.position = position;
        this.block = block;
    }

    /** The player performing the break (never null). */
    public Player player() {
        return player;
    }

    /** The world containing the block. */
    public World world() {
        return world;
    }

    /** The block's position. */
    public BlockPosition position() {
        return position;
    }

    /** The block type about to be removed. */
    public BlockType block() {
        return block;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String eventName() {
        return "BlockBreak";
    }
}
