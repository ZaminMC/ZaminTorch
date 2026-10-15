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
    private int ticksSinceDeath;

    /** PlayerStats.increment 1:1 — adds {@code amount} to the stat's counter. */
    public void increment(String stat, int amount) {
        if (stat == null || amount == 0) {
            return;
        }
        set(stat, get(stat) + amount);
    }

    /** PlayerStats.set 1:1 — writes the value and marks the stat dirty. */
    public void set(String stat, int value) {
        counters.put(stat, value);
        dirty.put(stat, value);
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
}
