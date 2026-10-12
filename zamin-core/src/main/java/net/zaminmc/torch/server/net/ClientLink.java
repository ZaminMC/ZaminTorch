package net.zaminmc.torch.server.net;

/**
 * The engine's handle to a client connection, implemented by the protocol adapter.
 *
 * <p>The engine only pushes engine-decided lifecycle actions through this link
 * (kick, game-mode and ability updates). It never sees packets, frames or
 * transport objects. Every method must be safe to call from any thread and
 * must never block.</p>
 */
public interface ClientLink {

    /** @return whether the underlying connection is still open. */
    boolean isActive();

    /**
     * @return the connection's remote IP (the /ban-ip gate's key), or an
     * empty string when the link has no address (engine-internal links).
     */
    default String remoteIp() {
        return "";
    }

    /**
     * Disconnects the client with the given engine-decided reason.
     * Must be safe to call multiple times; must never block.
     */
    void kick(String reason);

    /**
     * Announces a game mode change (Change Game State, reason 3). The value
     * is the protocol-47 mode id. Safe from any thread; default no-op for
     * engine-internal links (tests, headless harnesses).
     */
    default void updateGamemode(int gamemodeId) {
    }

    /**
     * Re-syncs the player's XP bar (Set Experience 0x1F) after an engine-side
     * XP mutation (/xp). Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void updateXp() {
    }

    /**
     * Syncs the player abilities window (Player Abilities 0x39): bit 0
     * invulnerable, bit 1 flying, bit 2 may-fly, bit 3 instant build.
     * Safe from any thread; default no-op for engine-internal links.
     */
    default void updateAbilities(int flags) {
    }

    /**
     * Announces the weather state (Change Game State 0x2B, reasons 1/2).
     * Safe from any thread; default no-op for engine-internal links.
     */
    default void updateWeather(boolean raining) {
    }

    /**
     * Re-sends the authoritative position (Position and Look 0x2E): the
     * movement guard's "moved wrongly" snap-back. Safe from any thread;
     * default no-op for engine-internal links.
     */
    default void resyncPosition() {
    }
}
