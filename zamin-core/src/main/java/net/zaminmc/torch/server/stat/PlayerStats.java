package net.zaminmc.torch.server.stat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One player's statistics (the stats slice — PlayerStats 1:1): the counter
 * map keyed by the {@link EngineStats} vocabulary, the dirty set the wire
 * answer consumes, and the snapshot the persistence walk carries.
 *
 * <p>Tick-lane owned: the increments ride the owning owner's lane (the
 * same context every gameplay mutation carries), and the packet/save
 * snapshots are taken through the same lane — the map itself never
 * crosses owners (§6.4, the one authoritative writer rule).</p>
 */
public final class PlayerStats {

    private final Map<String, Integer> counters = new HashMap<>();
    private final Map<String, Integer> dirty = new LinkedHashMap<>();
    private final Map<String, java.util.Set<String>> progress = new HashMap<>();
    private int ticksSinceDeath;
    private Listener listener;

    /**
     * The stat-change observation (the engine's seam for the reference's
     * ServerPlayerStats.set + ServerPlayerEntity.incrementStat halves): the
     * server attaches one per session — it carries the scoreboard ride (every
     * stat's criterion objectives follow the value) and the achievement
     * transition broadcast + the 0x37 flush (the toast path).
     */
    public interface Listener {

        /** Called after every write (the old value for the transition checks). */
        void onStatSet(String stat, int oldValue, int newValue);
    }

    /** Attaches the change observation (at most one; the session's own). */
    public void attachListener(Listener listener) {
        this.listener = listener;
    }

    /** PlayerStats.increment 1:1 — the achievement parent gate rides. */
    public void increment(String stat, int amount) {
        if (stat == null || amount == 0) {
            return;
        }
        // PlayerStats.increment's own condition: an achievement only counts
        // when its parent chain is satisfied — the /achievement give walk
        // hands the parents out first so the row itself passes.
        if (EngineAchievements.isAchievementKey(stat) && !hasParentAchievement(stat)) {
            return;
        }
        set(stat, get(stat) + amount);
    }

    /** PlayerStats.set 1:1 — writes the value and marks the stat dirty. */
    public void set(String stat, int value) {
        int oldValue = get(stat);
        counters.put(stat, value);
        dirty.put(stat, value);
        if (listener != null) {
            listener.onStatSet(stat, oldValue, value);
        }
    }

    /** PlayerStats.hasAchievement 1:1 — the counter is past zero. */
    public boolean hasAchievement(String stat) {
        return EngineAchievements.isAchievementKey(stat) && get(stat) > 0;
    }

    /** PlayerStats.hasParentAchievement 1:1 — no parent, or the parent earned. */
    public boolean hasParentAchievement(String stat) {
        EngineAchievements.Achievement achievement = EngineAchievements.byKey(stat);
        return achievement == null
                || achievement.parent() == null
                || hasAchievement(achievement.parent().key());
    }

    /** PlayerStats.get 1:1 — the counter's value, 0 when never written. */
    public int get(String stat) {
        Integer value = counters.get(stat);
        return value == null ? 0 : value;
    }

    /**
     * ServerPlayerStats.takeDirty 1:1 — the dirty snapshot for the wire
     * answer, clearing the set (the client accumulates the updates into
     * its own running copy, so only the changed counters ever travel).
     */
    public Map<String, Integer> takeDirty() {
        Map<String, Integer> taken = new LinkedHashMap<>(dirty);
        dirty.clear();
        return taken;
    }

    /**
     * The death clock (Stats.TIME_SINCE_DEATH): every tick past a death
     * accumulates until the next one clears it — the value counts ticks
     * exactly like the play clock (the client's formatter divides by 20).
     */
    public void tickTimeSinceDeath() {
        ticksSinceDeath++;
        increment(EngineStats.TIME_SINCE_DEATH, 1);
    }

    /** Marks a death: the since-death clock resets. */
    public void noteDeath() {
        ticksSinceDeath = 0;
        set(EngineStats.TIME_SINCE_DEATH, 0);
    }

    /**
     * The full snapshot (the persistence walk and the initial wire answer
     * carry every counter at once).
     */
    public Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(counters);
    }

    /** Restores a snapshot (the persistence load — no dirty marking). */
    public void load(Map<String, Integer> values) {
        counters.putAll(values);
    }

    /**
     * ServerPlayerStats.markAllDirty 1:1 — every counter joins the dirty
     * set (the login walk: the whole table answers the client's first
     * statistics request).
     */
    public void markAllDirty() {
        dirty.putAll(counters);
    }

    /**
     * ServerPlayerStats.sendAchievements 1:1 — the login sync: the had
     * achievements only (value 1), each removed from the dirty set as it
     * travels — the general band stays dirty for the client's first
     * statistics request.
     */
    public Map<String, Integer> takeAchievements() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (EngineAchievements.Achievement achievement : EngineAchievements.ALL) {
            String key = achievement.key();
            if (get(key) > 0) {
                out.put(key, get(key));
                dirty.remove(key);
            }
        }
        return out;
    }

    // ------------------------------------------------ the progress model (VIII6)

    /**
     * PlayerStats.getProgress 1:1 (the reference's StatCounter carriage —
     * the all-biomes row's own set): the progress strings of a stat, null
     * when never written. The returned set is the LIVE store — callers add
     * through {@link #progressOf} exactly like the reference's
     * {@code achievementprogress.add(s)} over the shared counter.
     */
    public java.util.Set<String> getProgress(String stat) {
        return progress.get(stat);
    }

    /**
     * PlayerStats.setProgress 1:1 — creates the progress row when absent
     * (the reference's null-then-construct arm) and answers it.
     */
    public java.util.Set<String> progressOf(String stat) {
        return progress.computeIfAbsent(stat, key -> java.util.Collections
                .synchronizedSet(new java.util.LinkedHashSet<>()));
    }

    /**
     * The persistence load (ServerPlayerStats.deserialize's progress arm):
     * the rows land without dirty marking, exactly like the counters.
     */
    public void loadProgress(Map<String, java.util.Set<String>> rows) {
        for (Map.Entry<String, java.util.Set<String>> row : rows.entrySet()) {
            progress.put(row.getKey(), java.util.Collections.synchronizedSet(
                    new java.util.LinkedHashSet<>(row.getValue())));
        }
    }

    /** The persistence snapshot (serialize's progress arm — every row). */
    public Map<String, java.util.Set<String>> progressSnapshot() {
        Map<String, java.util.Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.Set<String>> row : progress.entrySet()) {
            synchronized (row.getValue()) {
                out.put(row.getKey(), new java.util.LinkedHashSet<>(row.getValue()));
            }
        }
        return out;
    }
}
