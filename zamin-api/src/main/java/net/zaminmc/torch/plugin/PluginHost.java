package net.zaminmc.torch.plugin;

import java.util.Collection;
import java.util.Optional;

/**
 * The plugin host (architecture §28.5/§28.6's engine-side core): the
 * lifecycle state machine, the per-plugin resource scopes, and the
 * managed task quotas.
 *
 * <p><b>Programmatic now, discovered later:</b> the host's registration
 * is programmatic by design — the filesystem discovery and classloading
 * wave follows §27.1's order (the native API and engine mature first).
 * The lifecycle machine, the scopes, and the quotas this host owns are
 * the same contracts that wave will drive.</p>
 *
 * <p><b>Threading:</b> registration, disable, reload, and unload run on
 * the caller's thread (the engine's control context — typically boot or
 * a console command); the state reads and plugin-scope admissions are
 * safe from any thread. The disable walk refuses new admissions the
 * moment it begins (§28.5), synchronously sweeps the scope, and
 * invalidates late async results — it never waits inside a dependency
 * cycle (§28.2).</p>
 */
public interface PluginHost {

    /**
     * Registers and enables a plugin: {@code DISCOVERED → LOADED →
     * ENABLING → ENABLED}, running {@link PluginBody#onEnable} on the
     * caller's thread. A duplicate id is refused; a throwing
     * {@code onEnable} rolls the scope back and lands the plugin
     * DISABLED (the failure transition — never an undefined state).
     *
     * @return the plugin's handle
     * @throws IllegalStateException on a duplicate id
     */
    PluginHandle register(PluginDescriptor descriptor, PluginBody body);

    /**
     * Disables a plugin: {@code ENABLED → DISABLING → DISABLED}. From
     * DISABLING on, the plugin's new admissions are refused; the scope
     * closes synchronously after {@link PluginBody#onDisable} (or its
     * throw), cancelling every owned resource and invalidating late
     * async results. A disabled or unloaded plugin is a no-op.
     */
    void disable(PluginHandle plugin);

    /**
     * Reloads a plugin: the disable walk, a fresh scope, the enable
     * walk — one call, no duplicate resources (the fresh scope is
     * empty; the previous generation's admissions were all refused or
     * swept). A plugin not currently enabled is simply enabled.
     */
    void reload(PluginHandle plugin);

    /**
     * Unloads a disabled plugin: {@code DISABLED → UNLOADED} — the id
     * becomes free again. An enabled plugin is disabled first (the
     * documented composite). The handle's later reads answer UNLOADED.
     */
    void unload(PluginHandle plugin);

    /** @return every registered (not unloaded) plugin, any thread. */
    Collection<PluginHandle> plugins();

    /** @return the plugin registered under the id, if any. */
    Optional<PluginHandle> byId(String id);
}
