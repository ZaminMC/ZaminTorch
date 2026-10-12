package net.zaminmc.torch.event;

/**
 * An event whose outcome a handler may prevent.
 *
 * <p>Cancellation semantics (architecture §27.7): cancelling an event
 * prevents the engine action it describes. The engine re-validates its own
 * state after dispatch (a handler may have mutated it), so cancellation is
 * advisory-unto-commit — the engine path decides what "not happening"
 * means for its system (for example a cancelled block break re-syncs the
 * cell so client prediction drops). Handlers running at
 * {@link EventPriority#MONITOR} must observe only: cancellation from
 * monitor handlers is a documented convention violation, not a
 * bus-enforced rejection — the event objects are plain values the bus
 * cannot police.</p>
 */
public interface Cancellable {

    /** @return whether the engine action this event describes was cancelled. */
    boolean cancelled();

    /** Sets the cancelled flag. MONITOR-priority handlers must not call this. */
    void setCancelled(boolean cancelled);

    /** Convenience for {@code setCancelled(true)}. */
    default void cancel() {
        setCancelled(true);
    }
}
