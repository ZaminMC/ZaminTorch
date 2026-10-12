package net.zaminmc.torch.event;

/**
 * The public engine event bus (architecture §27.7).
 *
 * <p>Registration is safe from any thread. Dispatch happens on the
 * authoritative engine context that produced the event; handlers therefore
 * run on the owner of the state they observe and may read it live. The bus
 * itself adds no synchronization around handler bodies — the firing owner
 * serializes them — so handlers need no extra locking to touch engine
 * state, and equally must not block that owner.</p>
 *
 * <p>Reentrancy: a handler may fire further events (including of the same
 * type); dispatches nest like method calls and unwind in order. A handler
 * may register or unsubscribe subscriptions; changes apply from the next
 * dispatch snapshot (an in-flight pass over one event type completes with
 * its captured handler list).</p>
 */
public interface EventBus {

    /**
     * Registers a handler for an event type at a priority.
     *
     * @param type    the event class to receive
     * @param priority the dispatch order slot
     * @param handler the body to run
     * @return the active subscription
     */
    <T extends Event> EventSubscription on(Class<T> type, EventPriority priority,
                                           EventHandler<? super T> handler);

    /** Registers at {@link EventPriority#DEFAULT}. */
    default <T extends Event> EventSubscription on(Class<T> type,
                                                   EventHandler<? super T> handler) {
        return on(type, EventPriority.DEFAULT, handler);
    }

    /**
     * Dispatches an event to all its registered handlers, in priority then
     * registration order. Runs on the caller's (engine) context.
     *
     * @return the same event instance, post-dispatch (for
     *         {@code fire(event).cancelled()} reads)
     */
    <T extends Event> T fire(T event);
}
