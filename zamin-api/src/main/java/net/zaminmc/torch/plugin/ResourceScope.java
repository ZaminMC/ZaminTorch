package net.zaminmc.torch.plugin;

import net.zaminmc.torch.concurrent.AsyncCompute;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.scheduler.Task;

/**
 * One plugin's owned-resource ledger (architecture §28.5: "every
 * plugin-owned resource must be registered with a lifecycle owner").
 *
 * <p>Every registration the plugin makes — event subscriptions,
 * scheduled and repeating tasks, packet listeners, hook registrations,
 * async submissions — is owned through this scope. Closing the scope
 * (the disable walk's own act) cancels or deregisters every owned
 * resource: idempotently, totally, and with each resource's close verb
 * exception-isolated (one throwing cleanup never shields the rest).</p>
 *
 * <p><b>Late results:</b> closing the scope also invalidates the
 * plugin's not-yet-delivered async completions — a computation that
 * finishes after the close never reaches the plugin's callbacks
 * (§28.5: "do not permit a late task to mutate state after its plugin
 * has been disabled").</p>
 *
 * <p><b>Threading:</b> {@code own} is safe from any thread (the
 * admissions gate closes atomically with the close); {@code close} runs
 * on the host's disable walk.</p>
 */
public interface ResourceScope extends AutoCloseable {

    /**
     * Owns one resource through its close verb. The verb must be
     * idempotent (the engine's own handles are: unsubscribe, cancel,
     * tryCancel, registry removal).
     *
     * @param closeVerb the cleanup run at the scope's close
     * @return whether the resource was admitted (false once the scope
     *         has closed — the §28.5 admissions gate)
     */
    boolean own(Runnable closeVerb);

    /** Owns an event subscription (unsubscribed at close). */
    default boolean own(EventSubscription subscription) {
        return own(subscription::unsubscribe);
    }

    /** Owns a scheduled task (cancelled at close). */
    default boolean own(Task task) {
        return own(task::cancel);
    }

    /** Owns a computation (try-cancelled at close; late callbacks dropped). */
    default boolean own(AsyncCompute.Computation<?> computation) {
        return own(computation::tryCancel);
    }

    /** @return whether the scope has closed (admissions refused). */
    boolean closed();

    /** @return the resources currently owned (diagnostics, any thread). */
    int ownedCount();

    /**
     * The scope's own generation — bumped at every open; a plugin
     * re-enabled gets a fresh scope with a fresh generation (late work
     * from the previous generation is refused or dropped by it).
     */
    long generation();

    /** Closes the scope (the disable walk's own act). Idempotent. */
    @Override
    void close();
}
