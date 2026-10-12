package net.zaminmc.torch.event;

/**
 * The base of every public engine event.
 *
 * <p>Events are the plugin-facing observation and cancellation surface
 * (architecture §27.7). They fire on the engine's authoritative execution
 * context for the state they describe; handlers run synchronously in that
 * same context, so a handler may read the live state it observes without a
 * snapshot. Handlers must not block (no I/O waits, no cross-owner
 * synchronous calls): an event handler stalling its owner stalls the tick.
 * Event objects are transient — do not retain them after dispatch.</p>
 */
public interface Event {

    /** A short machine-readable name, stable across releases (for logging). */
    default String eventName() {
        return getClass().getSimpleName();
    }
}
