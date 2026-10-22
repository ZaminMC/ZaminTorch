package net.zaminmc.torch.server.stat;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The public achievement-table view (§27.3's family — the VIII6
 * exposure): the engine's own {@link EngineAchievements} table and the
 * per-player {@link PlayerStats} counters dressed as the
 * {@code net.zaminmc.torch.stat.Achievements} query surface.
 *
 * <p><b>Threading (§27.6):</b> the 34-row table is frozen before
 * gameplay begins (the engine's own {@code List.of} constants), so the
 * mapped api rows are built ONCE at class init and every table read is
 * a pure handout — trivially thread-safe, any thread. The per-player
 * {@code unlocked} read resolves the LIVE {@link PlayerStats} per call
 * (§27.5 — this view holds no player state of its own): the fixed-key
 * consult over the 34 known keys cannot tear structurally against the
 * tick lane's writes, so a call on the player's own simulation context
 * is consistent, and a foreign-thread call is best-effort — some
 * instant between ticks, the same contract as the {@code Player} body
 * reads. The answer lands as an immutable per-call list (never a live
 * collection handout).</p>
 *
 * <p><b>The handle law (§27.5):</b> the engine session IS the api
 * {@code Player} handle; the per-player read resolves it to the
 * session's own authoritative {@link PlayerStats} (the same object the
 * engine's increment walks write). A handle the engine does not own
 * answers the empty set (the defined behavior for foreign implements).</p>
 */
public final class AchievementViews implements net.zaminmc.torch.stat.Achievements {

    /** The stateless view (the table is static; the player resolves per call). */
    public static final AchievementViews INSTANCE = new AchievementViews();

    private AchievementViews() {
    }

    /**
     * The engine rows dressed as api rows, mapped once at class init —
     * the table is an immutable registration (frozen before gameplay
     * begins), so the mapped copies are values of frozen datums, never
     * a second copy of live state. The forward pass relies on the
     * table's own law: every parent is declared strictly before the
     * children that gate on it (the give-all walk's order).
     */
    private static final Map<EngineAchievements.Achievement,
            net.zaminmc.torch.stat.Achievements.Achievement> API_ROWS = buildApiRows();

    private static final List<net.zaminmc.torch.stat.Achievements.Achievement> API_ALL =
            EngineAchievements.ALL.stream().map(API_ROWS::get).toList();

    private static Map<EngineAchievements.Achievement,
            net.zaminmc.torch.stat.Achievements.Achievement> buildApiRows() {
        Map<EngineAchievements.Achievement,
                net.zaminmc.torch.stat.Achievements.Achievement> rows =
                new IdentityHashMap<>();
        for (EngineAchievements.Achievement row : EngineAchievements.ALL) {
            rows.put(row, new net.zaminmc.torch.stat.Achievements.Achievement(
                    row.key(), row.name(), row.column(), row.row(),
                    row.icon(), row.iconDamage(),
                    row.parent() == null ? null : rows.get(row.parent()),
                    row.challenge(), row.local()));
        }
        return rows;
    }

    @Override
    public List<net.zaminmc.torch.stat.Achievements.Achievement> all() {
        return API_ALL;
    }

    @Override
    public Optional<net.zaminmc.torch.stat.Achievements.Achievement> byKey(String statKey) {
        EngineAchievements.Achievement row = EngineAchievements.byKey(statKey);
        return row == null ? Optional.empty() : Optional.of(API_ROWS.get(row));
    }

    @Override
    public List<net.zaminmc.torch.stat.Achievements.Achievement> unlocked(Player player) {
        // §27.5: the api handle resolves to the session's own
        // authoritative stats store — the same object the engine's
        // increment walks write; a foreign implement answers empty.
        if (!(player instanceof PlayerSession session)) {
            return List.of();
        }
        PlayerStats stats = session.stats();
        java.util.ArrayList<net.zaminmc.torch.stat.Achievements.Achievement> unlocked =
                new java.util.ArrayList<>();
        // The fixed-key consult over the 34 known keys: no structural
        // walk of the counters map, each read one get — the consult
        // cannot tear against the tick lane's writes (best-effort
        // off-lane, consistent on the owner's context).
        for (EngineAchievements.Achievement row : EngineAchievements.ALL) {
            if (stats.get(row.key()) > 0) {
                unlocked.add(API_ROWS.get(row));
            }
        }
        return List.copyOf(unlocked);
    }
}
