package net.zaminmc.torch.server.net;

import java.util.UUID;

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

    /**
     * Plays an arbitrary Change Game State reason with the float value
     * (the elder-guardian ghost curse overlay — reason 10). Safe from any
     * thread; default no-op for engine-internal links.
     */
    default void playGameState(int reason, float value) {
    }

    /**
     * Spawns the lightning bolt (Spawn Global Entity 0x2C, type 1 — the
     * fixed-point 1/32 coordinates). Safe from any thread; default no-op
     * for engine-internal links.
     */
    default void spawnLightning(int entityId, double x, double y, double z) {
    }

    /**
     * Sends the client's statistics (Client Statistics 0x37 — the stat-key
     * string / varint value pairs, the dirty set the request consumes).
     * Safe from any thread; default no-op for engine-internal links.
     */
    default void sendStatistics(java.util.Map<String, Integer> stats) {
    }

    /**
     * One tab-list entry: the tab-list family's wire payload (the reference's
     * PlayerInfoS2CPacket.Entry — the offline profile's uuid + name, the game
     * mode id, and the smoothed ping). No display name: the reference's
     * getPlayerListName() answers null, and offline profiles carry no
     * texture properties.
     */
    record TabListEntry(UUID uuid, String name, int gamemodeId, int ping) {
    }

    /**
     * Player Info 0x38 action ADD_PLAYER (the reference's ctor walk: uuid,
     * name, zero properties, game mode varint, ping varint, no display
     * name). One packet with the given entries. Safe from any thread;
     * default no-op for engine-internal links.
     */
    default void tabListAdd(java.util.List<TabListEntry> entries) {
    }

    /**
     * Player Info 0x38 action REMOVE_PLAYER: uuid per entry, one packet.
     * Safe from any thread; default no-op for engine-internal links.
     */
    default void tabListRemove(java.util.List<UUID> ids) {
    }

    /**
     * Player Info 0x38 action UPDATE_GAME_MODE (2 = creative ...): the uuid
     * + the new mode id. Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void tabListUpdateGamemode(UUID id, int gamemodeId) {
    }

    /**
     * Player Info 0x38 action UPDATE_PING: one packet carrying every entry's
     * uuid + smoothed ping (the reference's single batched broadcast).
     * Safe from any thread; default no-op for engine-internal links.
     */
    default void tabListUpdatePing(java.util.List<TabListEntry> entries) {
    }
}
