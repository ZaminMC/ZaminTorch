package net.zamin.engine.player;

import net.zamin.api.Player;
import net.zamin.api.PlayerState;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active players. Engine-managed: registration and removal happen
 * on the simulation context through the lifecycle paths, so the "registered"
 * set can never disagree with session states ({@code ghost state} prevention).
 *
 * <p>Reads are safe from any thread; the map is a concurrent structure and the
 * returned views are snapshots.</p>
 */
public final class PlayerRegistry {

    private final Map<UUID, PlayerSession> byUuid = new ConcurrentHashMap<>();
    private final Map<String, PlayerSession> byName = new ConcurrentHashMap<>();

    public boolean isNameTaken(String name) {
        return byName.containsKey(name);
    }

    /** Registers a session in CONNECTING state. Engine-internal (EngineServer only). */
    public void register(PlayerSession session) {
        if (session.state() != PlayerState.CONNECTING) {
            throw new IllegalStateException("Only CONNECTING sessions can be registered");
        }
        if (byUuid.putIfAbsent(session.uuid(), session) != null) {
            throw new IllegalStateException("Duplicate player uuid: " + session.uuid());
        }
        PlayerSession raced = byName.putIfAbsent(session.name(), session);
        if (raced != null) {
            byUuid.remove(session.uuid(), session);
            throw new IllegalStateException("Duplicate player name: " + session.name());
        }
    }

    /** Removes a session. Idempotent. Engine-internal (EngineServer only). */
    public void unregister(PlayerSession session) {
        byUuid.remove(session.uuid(), session);
        byName.remove(session.name(), session);
    }

    public Optional<PlayerSession> byUuid(UUID uuid) {
        return Optional.ofNullable(byUuid.get(uuid));
    }

    public Optional<PlayerSession> byName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public int size() {
        return byUuid.size();
    }

    /** Snapshot of currently registered players. */
    public Collection<PlayerSession> all() {
        return byUuid.values().stream().toList();
    }

    /** Public API view. */
    public Collection<Player> publicView() {
        return all().stream().map(Player.class::cast).toList();
    }
}
