package net.zaminmc.torch.plugin;

/**
 * The plugin's own lifecycle callbacks (architecture §28.5). Runs on
 * the host's registration thread (the registration call itself); a
 * throwing callback drives the documented failure transition, never an
 * undefined state.
 *
 * <p>A functional interface: the common plugin is an enable lambda
 * (register resources through {@link PluginHandle#resources()}); the
 * ordered-cleanup {@link #onDisable} arm is the opt-in.</p>
 */
@FunctionalInterface
public interface PluginBody {

    /**
     * The enable walk's body. Register resources through
     * {@link PluginHandle#resources()} — everything admitted here is
     * rolled back through the scope on failure or disable. Throwing
     * here lands the plugin DISABLED (the partial registrations
     * cleaned).
     */
    void onEnable(PluginHandle handle);

    /**
     * The disable walk's body. The scope is closed AFTER this returns
     * (or throws): cleanup resources that need ordering here, and let
     * the scope sweep the rest. Throwing here still lands the plugin
     * DISABLED — the scope's cleanup is total.
     */
    default void onDisable(PluginHandle handle) {
    }
}
