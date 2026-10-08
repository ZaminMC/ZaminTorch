package net.zaminmc.torch.entity;

/**
 * Lifecycle states of a connected player. The engine rejects invalid transitions;
 * in particular a player can never be registered in two states at once.
 */
public enum PlayerState {
    CONNECTING,
    AUTHENTICATING,
    JOINING,
    PLAYING,
    DISCONNECTING,
    DISCONNECTED;

    public boolean isActive() {
        return this != DISCONNECTED;
    }
}
