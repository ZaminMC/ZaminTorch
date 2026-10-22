package net.zaminmc.torch.stat;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * The achievement-table query surface (§27.3's progression family — the
 * VIII6 exposure): the reference 1.8.8's own 34-row {@code Achievements}
 * table — every row's stat key, short name, achievement-page column and
 * row, icon (the toast's item id + damage, the two damage-carrying rows
 * included), parent chain, challenge flag and local row — plus the
 * per-player unlocked read, reached through {@link Server#achievements()}.
 *
 * <p>The engine behind it is {@code stat/EngineAchievements} (the
 * Achievements + AchievementStat 1:1 port — the reference's own
 * declaration order, the order the give-all walk rides forward and the
 * take-all reversed) and {@code stat/PlayerStats} (the per-player
 * counters the unlocked read resolves). The parent chain is the
 * {@code /achievement take} walk's own dependency tree: an achievement's
 * gate is its parent (the engine refuses a row whose parent has not
 * been earned), and the mutation verbs (the give/take command walks)
 * stay engine-owned — this surface exposes the READS a plugin needs.</p>
 *
 * <p><b>Threading (§27.6):</b> the 34-row table is frozen before
 * gameplay begins — an immutable registration, never mutated at runtime —
 * so {@link #all()} and {@link #byKey(String)} are pure reads, trivially
 * thread-safe, safe from any thread. The per-player
 * {@link #unlocked(Player)} read resolves the LIVE stats store per call
 * (§27.5 — the handle keeps no second copy): consistent on the player's
 * own simulation context (the documented owner path is
 * {@code World#execute}); a foreign-thread call is best-effort — the
 * fixed-key consult cannot tear structurally, but may answer an instant
 * behind the tick lane (the same contract as the {@code Player} body
 * reads). The answer itself is an immutable per-call value — never a
 * live collection handout.</p>
 */
public interface Achievements {

    /** The empty answer (no table enumerated, no rows unlocked — an unbooted server's view). */
    Achievements EMPTY = new Achievements() {
        @Override public List<Achievement> all() {
            return List.of();
        }

        @Override public Optional<Achievement> byKey(String statKey) {
            return Optional.empty();
        }

        @Override public List<Achievement> unlocked(Player player) {
            return List.of();
        }
    };

    /**
     * @return the full 34-row table in the reference's own declaration
     *         order ({@code Achievements.ALL}): the order the give-all
     *         walk rides forward (every parent strictly before the
     *         children that gate on it) and the take-all reversed. The
     *         list is frozen — safe from any thread.
     */
    List<Achievement> all();

    /**
     * The key lookup (the reference's own {@code Stats} by-key view over
     * the achievement band).
     *
     * @param statKey the stat key (for example
     *                {@code achievement.mineWood})
     * @return the row for the key, or empty when the key does not
     *         belong to the achievement band
     */
    Optional<Achievement> byKey(String statKey);

    /**
     * The per-player unlocked set (the reference's
     * {@code hasAchievement} read walked over the table): every
     * achievement row whose counter the player holds past zero — the
     * 34 keys' unlocked subset, answered in the table's own declaration
     * order. Resolves the live stats store per call (§27.5 — no second
     * copy); the answer is an immutable per-call snapshot.
     *
     * @param player the player handle ({@link Server#players()}'s own)
     * @return the player's unlocked rows in declaration order (empty
     *         for a fresh player, and for a handle the engine does not
     *         own)
     */
    List<Achievement> unlocked(Player player);

    /**
     * One achievement row (the reference's {@code AchievementStat} own
     * surface): the stat key, the short name (the
     * {@code achievement.<name>} and {@code achievement.<name>.desc}
     * translation keys), the achievement-page column and row, the icon
     * (the reference's item id + damage — the toast's client-side tile),
     * the parent chain, the challenge flag, and the one local row
     * (OPEN_INVENTORY — awarded without the server broadcast band).
     *
     * @param key         the stat key ({@code achievement.<name>} — the
     *                    counters' own vocabulary)
     * @param name        the short name (the translation keys' stem)
     * @param column      the achievement-page column
     * @param row         the achievement-page row
     * @param icon        the icon's item id (the reference's own
     *                    ItemStack form)
     * @param iconDamage  the icon's damage (the two damage-carrying
     *                    rows: the wither skull's 1, the enchanted
     *                    golden apple's 1)
     * @param parent      the parent row (the gate an increment consults
     *                    — null only for OPEN_INVENTORY, the table's
     *                    one root)
     * @param challenge   the challenge flag (the special rows the
     *                    toast announces differently)
     * @param local       the local row (OPEN_INVENTORY's own — awarded
     *                    without the server broadcast)
     */
    record Achievement(String key, String name, int column, int row,
                       Identifier icon, int iconDamage, Achievement parent,
                       boolean challenge, boolean local) {

        /** @return the name translation key ({@code achievement.<name>} — Stat's name). */
        public String nameKey() {
            return "achievement." + name;
        }

        /** @return the description translation key ({@code achievement.<name>.desc}). */
        public String descriptionKey() {
            return "achievement." + name + ".desc";
        }
    }
}
