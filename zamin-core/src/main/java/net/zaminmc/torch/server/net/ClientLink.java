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

    /**
     * One scoreboard objective's wire payload (ScoreboardObjectiveS2CPacket's
     * own fields): the name, the display name, and the render type key.
     */
    record ScoreboardObjectiveData(String name, String displayName, String renderType) {
    }

    /**
     * One team's wire payload (TeamS2CPacket's own fields): the names, the
     * decoration, the friendly flags byte, the name-tag visibility key, and
     * the chat color id (-1 = RESET).
     */
    record ScoreboardTeamData(String name, String displayName, String prefix, String suffix,
                              int friendlyFlags, String nameTagVisibility, int colorId) {
    }

    /**
     * Scoreboard Objective (0x3B): string name, byte action (0 add, 1
     * remove, 2 update), then for add/update the display name string and the
     * render type string. Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void scoreboardObjective(ScoreboardObjectiveData data, int action) {
    }

    /**
     * Update Score (0x3C): string owner, varint mode (0 CHANGE, 1 REMOVE),
     * string objective, and for CHANGE the varint value. Safe from any
     * thread; default no-op for engine-internal links.
     */
    default void scoreboardScore(String owner, int mode, String objective, int value) {
    }

    /**
     * Display Scoreboard (0x3D): byte slot (0-18), string objective name
     * ("" = cleared). Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void scoreboardDisplay(int slot, String objectiveName) {
    }

    /**
     * Teams (0x3E): string name, byte action (0 create, 1 remove, 2 update,
     * 3 join, 4 leave), for create/update the full team payload, and for
     * create/join/leave the varint-counted member strings. Safe from any
     * thread; default no-op for engine-internal links.
     */
    default void scoreboardTeam(ScoreboardTeamData team, int action, java.util.List<String> members) {
    }

    /**
     * World Border (0x44) SET_SIZE: the new target size (double). Safe from
     * any thread; default no-op for engine-internal links.
     */
    default void worldBorderSize(double sizeLerpTarget) {
    }

    /**
     * World Border (0x44) LERP_SIZE: the current size, the target size, and
     * the remaining lerp time in ms (varlong). Safe from any thread;
     * default no-op for engine-internal links.
     */
    default void worldBorderLerp(double lerpSize, double sizeLerpTarget, long lerpTimeMs) {
    }

    /**
     * World Border (0x44) SET_CENTER: the new center (two doubles). Safe
     * from any thread; default no-op for engine-internal links.
     */
    default void worldBorderCenter(double centerX, double centerZ) {
    }

    /**
     * World Border (0x44) SET_WARNING_TIME: the warning seconds (varint).
     * Safe from any thread; default no-op for engine-internal links.
     */
    default void worldBorderWarningTime(int seconds) {
    }

    /**
     * World Border (0x44) SET_WARNING_BLOCKS: the warning distance in blocks
     * (varint). Safe from any thread; default no-op for engine-internal
     * links.
     */
    default void worldBorderWarningBlocks(int blocks) {
    }

    /**
     * World Border (0x44) INITIALIZE (the join sync, PlayerManager
     * .sendWorldInfo line 679): the full border state. Safe from any
     * thread; default no-op for engine-internal links.
     */
    default void worldBorderInit(double centerX, double centerZ, double lerpSize,
                                 double sizeLerpTarget, long lerpTimeMs, int maxSize,
                                 int warningBlocks, int warningTime) {
    }

    /**
     * Title (0x45) TITLE (the reference's Type ordinal 0): the title's
     * serialized component (the JSON text string — PacketByteBuf.writeText's
     * own form). Safe from any thread; default no-op for engine-internal
     * links.
     */
    default void titleTitle(String jsonText) {
    }

    /**
     * Title (0x45) SUBTITLE (the reference's Type ordinal 1): the subtitle's
     * serialized component. Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void titleSubtitle(String jsonText) {
    }

    /**
     * Title (0x45) TIMES (the reference's Type ordinal 2): the three i32s —
     * fadeIn, stay, fadeOut. Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void titleTimes(int fadeIn, int duration, int fadeOut) {
    }

    /**
     * Title (0x45) CLEAR (the reference's Type ordinal 3): the client hides
     * the current title. Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void titleClear() {
    }

    /**
     * Title (0x45) RESET (the reference's Type ordinal 4): clear + reset the
     * times to the client's defaults. Safe from any thread; default no-op
     * for engine-internal links.
     */
    default void titleReset() {
    }

    /**
     * Player List Header/Footer (0x47): the header's then the footer's
     * serialized component. The vanilla 1.8.8 server never sends this packet
     * (the reference tree wires only the client handler) — the verb exists
     * for the native API surface. Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void tabListHeaderFooter(String headerJson, String footerJson) {
    }

    /**
     * A raw-JSON chat line (Chat 0x02): the serialized component at the
     * given position (1 = system) — the reference's sendMessage(Component).
     * The plain-string system lines keep their own path; this verb carries
     * the component's full shape (hover/click events intact). Safe from any
     * thread; default no-op for engine-internal links.
     */
    default void sendChatComponent(String json, int position) {
    }

    /**
     * The anti-desync block resync (ServerPlayNetworkHandler.handlePlayerUse
     * lines 494-497): the authoritative state of one cell pushed to THIS
     * client after a use — the reference sends the pair (the clicked cell +
     * the face-offset cell) after EVERY block-face use, accepted, refused,
     * or too-high, so the client's predicted ghost from a refused or
     * short-circuited use reverts to the truth. Safe from any thread;
     * default no-op for engine-internal links.
     */
    default void blockResync(net.zaminmc.torch.block.BlockPosition position,
                             net.zaminmc.torch.block.BlockType type) {
    }

    /**
     * Spawn Position (0x05): the world spawn re-anchor (the reference's
     * PlayerManager.sendPacket on /setworldspawn; the join burst carries
     * the same packet). Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void updateSpawnPosition(int x, int y, int z) {
    }

    /**
     * Named Sound Effect (0x29) sent directly to THIS client (the reference's
     * PlaySoundCommand: a per-player packet, not a world broadcast). The
     * name is the 1.8.8 sound key ("mob.zombie.say"); unknown names are
     * ignored by the client. Safe from any thread; default no-op for
     * engine-internal links.
     */
    default void playSound(String name, double x, double y, double z, float volume, float pitch) {
    }

    /**
     * World Particles (0x2A) for THIS client (the reference's
     * ServerWorld.addParticle → sendToAround: /particle broadcasts to every
     * player within the radius; the verb carries the already-decided
     * payload). The params carry the crack families' extra varints. Safe
     * from any thread; default no-op for engine-internal links.
     */
    default void worldParticles(int particleId, boolean longDistance, double x, double y, double z,
                                float offsetX, float offsetY, float offsetZ,
                                float particleData, int count, int[] params) {
    }

    /**
     * One map icon row (the map item family — the reference's
     * MapDecoration): the type byte, the -128..127 canvas coordinates,
     * and the sixteen-step rotation byte.
     */
    record MapIcon(byte type, byte x, byte y, byte rotation) {
    }

    /**
     * Map Data (0x34 — the reference's MapDataS2CPacket, the wire form the
     * holder's update consult answers): the map id (the stack's damage
     * field, never an entity id), the scale byte, the icon rows, and the
     * dirty rectangle (width 0 = the decorations-only resend — no color
     * payload follows on the wire). The gate chunk is the framed-maps'
     * visibility gate (the packed chunk key the adapter checks against its
     * observer's loaded window — the reference's tracking-player set);
     * {@link Long#MIN_VALUE} means no gate (the held-map path).
     */
    default void sendMapData(int mapId, byte scale, java.util.List<MapIcon> icons,
                             int minX, int minY, int width, int height, byte[] colors,
                             long gateChunk) {
    }

    /**
     * Server Difficulty (0x41 — the reference's DifficultyS2CPacket, the
     * single difficulty id byte 0..3): the runtime difficulty's change-time
     * fan-out after {@code /difficulty} (the reference's own send set: the
     * join walk's packet, PlayerManager.onLogin line 128, and the change
     * re-sync), so tracking clients drop the join-time byte before their
     * next respawn. Safe from any thread; default no-op for engine-internal
     * links.
     */
    default void updateDifficulty(int difficultyId) {
    }

    /**
     * Resource Pack Send (0x48 — the reference's ResourcePackS2CPacket: the
     * url string then the hash string, the ctor's own 40-character hash
     * law): the join walk's own send (PlayerManager.onLogin lines 148-150 —
     * only ever called when the configured url is non-empty, the reference's
     * own gate). Safe from any thread; default no-op for engine-internal
     * links.
     */
    default void sendResourcePack(String url, String hash) {
    }
}
