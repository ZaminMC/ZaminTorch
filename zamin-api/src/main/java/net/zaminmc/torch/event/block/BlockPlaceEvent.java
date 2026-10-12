package net.zaminmc.torch.event.block;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;

/**
 * A block is about to be placed by a player.
 *
 * <p>Fires on the world's simulation context after the engine validated
 * the placement (reach, target air, no body intersection) and before the
 * world mutation and the survival inventory consumption. Cancelling
 * prevents the placement and the item consumption; the engine re-syncs
 * the cell so the placing client drops its prediction.</p>
 */
public final class BlockPlaceEvent implements Event, Cancellable {

    private final Player player;
    private final World world;
    private final BlockPosition position;
    private final BlockType block;
    private boolean cancelled;

    public BlockPlaceEvent(Player player, World world, BlockPosition position, BlockType block) {
        this.player = player;
        this.world = world;
        this.position = position;
        this.block = block;
    }

    /** The player performing the placement (never null). */
    public Player player() {
        return player;
    }

    /** The world receiving the block. */
    public World world() {
        return world;
    }

    /** The cell the block will occupy. */
    public BlockPosition position() {
        return position;
    }

    /** The block type about to be placed. */
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
        return "BlockPlace";
    }
}
