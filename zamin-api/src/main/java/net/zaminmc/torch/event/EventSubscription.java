package net.zaminmc.torch.event;

/**
 * The registration handle returned by {@link EventBus#on}.
 *
 * <p>Unsubscribing is idempotent and safe from any thread, including from
 * inside a handler during dispatch. The subscription's handlers stop
 * receiving events at the next dispatch after {@link #unsubscribe()}
 * returns; in-flight dispatches complete their current pass.</p>
 */
public interface EventSubscription {

    /** Removes the handler from its bus. Idempotent. */
    void unsubscribe();

    /** @return whether the subscription is still active. */
    boolean active();
}
