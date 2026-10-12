package net.zaminmc.torch.event;

/**
 * A handler registered for one event type on an {@link EventBus}.
 *
 * <p>Runs synchronously on the engine context that fired the event.
 * Exceptions escaping a handler are caught and logged by the bus (the
 * dispatch continues; later handlers still run) — an exception never
 * aborts the engine action unless a handler cancelled first.</p>
 */
@FunctionalInterface
public interface EventHandler<T extends Event> {

    /** Receives one fired event. */
    void handle(T event);
}
