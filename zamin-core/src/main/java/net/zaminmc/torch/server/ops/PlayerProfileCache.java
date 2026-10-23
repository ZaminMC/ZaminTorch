package net.zaminmc.torch.server.ops;

import net.zaminmc.torch.server.player.PlayerDataStore;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The /op name resolution (ML-005 — the audit's B-3 row): the reference
 * resolves {@code /op} through the profile cache (dedicated OpCommand
 * lines 30-39: {@code getGameProfileCache().get(name)}, then
 * {@code addOp} + {@code commands.op.success}, or the
 * {@code commands.op.failed} line), so a joined-but-offline name still
 * ops — operators pre-op returning players. This is the engine's
 * equivalent, built ONLY over identities the engine already persists
 * (the lane's own boundary — no usercache.json, no Mojang session
 * walk):
 *
 * <ul>
 *   <li>the player save store's census — every {@code <uuid>.zpd}
 *       header's name, read at boot (the reference's usercache
 *       population role, derived from the engine's own per-identity
 *       files instead of a second cache file);</li>
 *   <li>ops.json's own entries (a surviving or hand-maintained operator
 *       row resolves even without a .zpd — the registry is the
 *       operator-maintained record);</li>
 *   <li>this run's joins ({@link #add} at the accept site — the
 *       reference's own login-time cache add), so a same-run
 *       join-then-quit name resolves without a restart.</li>
 * </ul>
 *
 * <p>Lookup is case-insensitive over the stored canonical case (the
 * reference's lowercase cache-key law, GameProfileCache lines 110 and
 * 124-125). No expiry: the engine's persisted identities carry none —
 * vanilla's month-long usercache expiry is the Mojang-lookup cache's
 * own policy, a file this engine deliberately does not keep.</p>
 *
 * <p>Thread-safe by construction: joins arrive on the joining channel's
 * event loop, {@code /op} on the tick lane — a concurrent map pair,
 * the same publication freedom the live registries carry. The census
 * itself runs once at boot, before the first join can consult it.</p>
 */
public final class PlayerProfileCache {

    /** One resolved identity: the stored uuid + the canonical-case name. */
    public record Profile(UUID uuid, String name) {
    }

    private final Map<UUID, String> byUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();

    private PlayerProfileCache() {
    }

    /**
     * The boot census: the player store's persisted names overlaid with
     * the operator registry's own rows (the op entries win — a name
     * both carry resolves through the registry's uuid, the row the
     * operator maintains).
     */
    public static PlayerProfileCache load(PlayerDataStore playerStore, OpStore opStore) {
        PlayerProfileCache cache = new PlayerProfileCache();
        if (playerStore != null) {
            playerStore.persistedNames().forEach(cache::add);
        }
        if (opStore != null) {
            for (OpStore.Entry entry : opStore.entries()) {
                cache.add(entry.uuid(), entry.name());
            }
        }
        return cache;
    }

    /**
     * Records a live identity (the join's own cache add — the
     * reference's GameProfileCache.add at the accept site). Idempotent;
     * a re-add under a changed name retires the old name key (the uuid
     * pins the identity).
     */
    public void add(UUID uuid, String name) {
        String previous = byUuid.put(uuid, name);
        if (previous != null && !previous.equals(name)) {
            byName.remove(previous.toLowerCase(Locale.ROOT), uuid);
        }
        byName.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    /**
     * Case-insensitive name resolution — the reference's cache-get law:
     * the stored canonical case answers, null when no persisted
     * identity carries the name ({@code /op} then answers the
     * reference's {@code commands.op.failed} line).
     */
    public Profile byName(String name) {
        UUID uuid = byName.get(name.toLowerCase(Locale.ROOT));
        if (uuid == null) {
            return null;
        }
        String canonical = byUuid.get(uuid);
        return canonical == null ? null : new Profile(uuid, canonical);
    }
}
