package net.zaminmc.torch.plugin;

/**
 * One registered plugin's handle (architecture §28.5): the identity,
 * the live state, the owned-resource ledger, and the managed task
 * surface. The handle stays valid across the whole lifecycle (through
 * disable and re-enable); its {@link #resources()} answers the CURRENT
 * scope (a fresh one after every re-enable — the reload never carries
 * the previous generation's resources over).
 *
 * <p>Threading: the state read is safe from any thread; the resource
 * admissions and task submissions follow their own documented
 * contracts (§28.5/§28.6).</p>
 */
public interface PluginHandle {

    /** The plugin's descriptor (immutable). */
    PluginDescriptor descriptor();

    /** The plugin's current lifecycle state (any thread). */
    PluginState state();

    /**
     * The plugin's CURRENT owned-resource ledger — the scope the enable
     * walk opened. A disabled plugin answers a closed scope; a
     * re-enabled plugin answers the fresh generation's scope.
     */
    ResourceScope resources();

    /** The plugin's managed task surface (§28.6 — the quota'd lanes). */
    PluginTasks tasks();
}
