package net.zaminmc.torch.plugin;

import java.util.List;

/**
 * The plugin lifecycle states (architecture §28.5's own machine).
 *
 * <p>The happy walk: {@code DISCOVERED → LOADED → ENABLING → ENABLED →
 * DISABLING → DISABLED → UNLOADED}. The failure transitions are part of
 * the contract: a plugin whose {@code onEnable} throws lands
 * {@link #DISABLED} (never ENABLED — partial registrations are rolled
 * back through its {@link ResourceScope}); a plugin whose
 * {@code onDisable} throws still lands {@link #DISABLED} (the scope's
 * cleanup is total and exception-isolated).</p>
 */
public enum PluginState {

    /** Registered with the host; nothing loaded yet. */
    DISCOVERED,

    /** The descriptor validated; the plugin's identity stands. */
    LOADED,

    /**
     * The enable walk runs. New resource admissions still legal; a
     * failure here rolls the scope back and lands DISABLED.
     */
    ENABLING,

    /** The plugin's callbacks may run; its resources are live. */
    ENABLED,

    /**
     * The disable walk runs. New admissions are REFUSED from this state
     * on (§28.5: disable prevents new work the moment it begins, not
     * when it ends) — a running callback is not proof of authority.
     */
    DISABLING,

    /**
     * The disable walk ended (success or failure): the scope closed,
     * every owned resource cancelled or deregistered, late async
     * results invalidated (they never reach the plugin's callbacks).
     */
    DISABLED,

    /** Removed from the host entirely (the id is free again). */
    UNLOADED
}
