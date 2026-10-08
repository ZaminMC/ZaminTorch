package net.zaminmc.torch;

/**
 * Lifecycle states of the server. Transitions are engine-controlled and
 * one-directional; methods that require a specific state fail loudly when
 * invoked from an invalid state instead of silently misbehaving.
 */
public enum ServerState {
    NEW,
    INITIALIZING,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED;

    public boolean isRunning() {
        return this == RUNNING;
    }

    /** The server is alive (running or shutting down but not yet stopped). */
    public boolean isAlive() {
        return this == RUNNING || this == STOPPING;
    }
}
