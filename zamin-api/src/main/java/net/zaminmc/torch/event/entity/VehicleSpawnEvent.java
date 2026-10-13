package net.zaminmc.torch.event.entity;

import net.zaminmc.torch.entity.VehicleKind;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.util.Position;

/**
 * A vehicle is about to enter the world (§27.7's observe/cancel gate
 * over the mount family — the boat and minecart item placements and
 * the structure-seeded chest minecarts all ride the same chokepoint).
 *
 * <p>Fires on the world's simulation context BEFORE the body joins the
 * world. Cancelling prevents the spawn entirely: no entity id is
 * consumed, no body spawns, no wire frame leaves — and the placing
 * item is NOT consumed (the same withholding semantics the
 * {@link BlockPlaceEvent}-family gates carry).</p>
 */
public final class VehicleSpawnEvent implements Event, Cancellable {

    private final VehicleKind kind;
    private final Position position;
    private boolean cancelled;

    public VehicleSpawnEvent(VehicleKind kind, Position position) {
        this.kind = kind;
        this.position = position;
    }

    /** The vehicle kind (the public mount family's own constant). */
    public VehicleKind kind() {
        return kind;
    }

    /** The spawn position in world space. */
    public Position position() {
        return position;
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
        return "VehicleSpawn";
    }
}
