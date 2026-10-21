package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.entity.Entity;
import net.zaminmc.torch.event.Event;

/**
 * A piston's moving carrier displaced an entity (§27.7's post-commit
 * observation of the VIII13 displacement arm — the complement of the
 * pre-commit {@link PistonMoveEvent}).
 *
 * <p>Fires once per pushed body inside the carrier's own tick pass
 * (the reference's {@code MovingBlockEntity.moveEntities} walk: the
 * interpolated collision box sweeping the flight path pushes every
 * intersecting body along the facing), AFTER the displacement has
 * happened — the pushed body's position already reflects the push when
 * the handler runs. This is deliberately NOT cancellable: the event
 * observes a committed result, and the engine models no revert of a
 * completed physics step (§27.7's observe-after-commit form; cancelling
 * a single body's step would desynchronize the swept pair the carrier
 * just moved). The PLAYERS never appear: the engine's players are
 * client-authoritative and the reference's own walk exempts them.</p>
 *
 * <p>Threading: handlers run on the world's simulation context (inside
 * the piston system's tick). Handlers must not block; mutations inside
 * the handler are legal only for state the handler's own context owns
 * (the engine's position-aware assertions govern every write).</p>
 */
public final class PistonPushEvent implements Event {

    private final String worldName;
    private final BlockPosition carrierPosition;
    private final Direction facing;
    private final boolean extending;
    private final BlockType carriedBlock;
    private final double amount;
    private final boolean flung;
    private final Entity pushed;

    public PistonPushEvent(String worldName, BlockPosition carrierPosition,
                           Direction facing, boolean extending,
                           BlockType carriedBlock, double amount, boolean flung,
                           Entity pushed) {
        this.worldName = worldName;
        this.carrierPosition = carrierPosition;
        this.facing = facing;
        this.extending = extending;
        this.carriedBlock = carriedBlock;
        this.amount = amount;
        this.flung = flung;
        this.pushed = pushed;
    }

    /** The world the piston moves in (the world's own name). */
    public String worldName() {
        return worldName;
    }

    /**
     * The moving carrier's own cell (the in-flight moving-piston block's
     * position — the box the displacement swept grows from this cell
     * along the facing).
     */
    public BlockPosition carrierPosition() {
        return carrierPosition;
    }

    /** The piston's facing (the head's direction — the push's axis). */
    public Direction facing() {
        return facing;
    }

    /**
     * @return true when the carrier extends (the push along the facing);
     *         false when it retracts (the completion's flat push).
     */
    public boolean extending() {
        return extending;
    }

    /**
     * The carried block's type (the state riding the carrier — a slime
     * block's carry names the fling).
     */
    public BlockType carriedBlock() {
        return carriedBlock;
    }

    /**
     * The displacement's signed magnitude ALONG the facing (the
     * extending steps' progress delta + the catch-up band, or the
     * completion's flat 0.25; negative against the facing never occurs —
     * the reference's amounts are facing-signed by this value alone).
     */
    public double amount() {
        return amount;
    }

    /**
     * @return true when an extending SLIME carrier FLUNG the body — the
     *         velocity was SET along the facing axis (no positional push
     *         rode the fling; the reference's lines 94-107).
     */
    public boolean flung() {
        return flung;
    }

    /**
     * The pushed body's public handle (§27.5: the engine object IS the
     * handle — a mob, a dropped item, or a vehicle; the players are
     * exempt). Position reads reflect the ALREADY-PUSHED body.
     */
    public Entity pushed() {
        return pushed;
    }

    @Override
    public String eventName() {
        return "PistonPush";
    }
}
