package net.zaminmc.torch.server.gamerule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * The game-rule table (Slice 24a — the reference port of
 * reference/1.8.8 world/GameRules.java): the fifteen 1.8.8 rules with
 * their defaults and types, the string-value model with the parsed
 * boolean/int views, the unknown-rule extension arm ({@code set} on a
 * missing name ADDS it as {@link Type#ANY_VALUE} — the reference's own
 * behavior), and the sorted-name enumeration.
 *
 * <p>Threading contract (§27.6/§28.3): one shared instance per server —
 * the reference's own shape, where every dimension's derived
 * {@code ReadOnlyWorldData.getGameRules()} delegates to the overworld's
 * table. Mutations ({@link #set}) run ONLY on the authoritative
 * simulation context (the {@code /gamerule} command's dispatch already
 * rides the tick lane; the public API route asserts the owner context
 * before delegating). Reads ({@link #get}, {@link #getBoolean},
 * {@link #getInt}, {@link #names}, {@link #contains}) are safe from any
 * thread through an immutable published snapshot — each read sees one
 * fully-parsed table state (last-write-wins per read; no torn values).
 * This is the deliberate snapshot contract, not a claim that concurrent
 * mutation is legal: there is exactly one writer lane.</p>
 */
public final class GameRules implements net.zaminmc.torch.gamerule.GameRules {

    /** The rule's value-type classification (the reference's Type enum). */
    public enum Type {
        ANY_VALUE,
        BOOLEAN_VALUE,
        NUMERICAL_VALUE;
    }

    /**
     * One rule's immutable value (the reference's Value class, made
     * immutable so the published snapshot is safely shareable): the raw
     * string plus the parsed boolean/int views — the reference's own
     * parse rules ({@code Boolean.parseBoolean}, the int parse with the
     * boolean fallback 1/0, the silent NumberFormatException swallows).
     */
    static final class Value {
        final String value;
        final boolean booleanValue;
        final int intValue;
        final Type type;

        Value(String value, Type type) {
            this.value = value;
            this.type = type;
            this.booleanValue = Boolean.parseBoolean(value);
            int parsed = this.booleanValue ? 1 : 0;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // the reference's own silent swallow
            }
            this.intValue = parsed;
        }
    }

    /** The authoritative table — touched only on the simulation context. */
    private final TreeMap<String, Value> values = new TreeMap<>();

    /**
     * The published read snapshot (immutable, swapped whole on every
     * mutation): the any-thread reads walk this, never the live table.
     */
    private final AtomicReference<Map<String, Value>> published =
            new AtomicReference<>(Map.copyOf(values));

    /**
     * The ownership gate for the public mutation route (the engine's
     * domain check; null in the pre-attach window and in unit tests
     * where the constructing thread is the authority).
     */
    private volatile BooleanSupplier ownershipCheck;

    public GameRules() {
        this.add("doFireTick", "true", Type.BOOLEAN_VALUE);
        this.add("mobGriefing", "true", Type.BOOLEAN_VALUE);
        this.add("keepInventory", "false", Type.BOOLEAN_VALUE);
        this.add("doMobSpawning", "true", Type.BOOLEAN_VALUE);
        this.add("doMobLoot", "true", Type.BOOLEAN_VALUE);
        this.add("doTileDrops", "true", Type.BOOLEAN_VALUE);
        this.add("doEntityDrops", "true", Type.BOOLEAN_VALUE);
        this.add("commandBlockOutput", "true", Type.BOOLEAN_VALUE);
        this.add("naturalRegeneration", "true", Type.BOOLEAN_VALUE);
        this.add("doDaylightCycle", "true", Type.BOOLEAN_VALUE);
        this.add("logAdminCommands", "true", Type.BOOLEAN_VALUE);
        this.add("showDeathMessages", "true", Type.BOOLEAN_VALUE);
        this.add("randomTickSpeed", "3", Type.NUMERICAL_VALUE);
        this.add("sendCommandFeedback", "true", Type.BOOLEAN_VALUE);
        this.add("reducedDebugInfo", "false", Type.BOOLEAN_VALUE);
    }

    /** The engine-internal registration arm (the reference's add). */
    private void add(String name, String defaultValue, Type type) {
        values.put(name, new Value(defaultValue, type));
        publish();
    }

    /**
     * Attaches the public mutation route's ownership gate (the engine's
     * simulation domain). Called once when the domain attaches.
     */
    public void setOwnershipCheck(BooleanSupplier check) {
        this.ownershipCheck = check;
    }

    /** Swaps the read snapshot (called only from the mutation arms). */
    private void publish() {
        published.set(Map.copyOf(values));
    }

    /**
     * Sets a rule's value (the reference's set): a known rule takes the
     * new value; an unknown name is ADDED as an {@code ANY_VALUE} rule.
     * Engine-internal mutations call this from the simulation context;
     * the public route is {@link #set(String, String)}.
     */
    void setInternal(String name, String value) {
        Value existing = values.get(name);
        if (existing != null) {
            values.put(name, new Value(value, existing.type));
        } else {
            values.put(name, new Value(value, Type.ANY_VALUE));
        }
        publish();
    }

    /**
     * The public mutation route (§27.6): asserts the caller runs on the
     * authoritative simulation context before the write lands. The
     * {@code /gamerule} command and the engine's own walks already ride
     * that context; a plugin mutating off-lane gets the loud
     * diagnosable refusal, not a silent data race.
     */
    @Override
    public void set(String name, String value) {
        BooleanSupplier check = ownershipCheck;
        if (check != null && !check.getAsBoolean()) {
            throw new IllegalStateException(
                    "Game rules are owned by the simulation context; set('" + name
                            + "') was called from " + Thread.currentThread().getName());
        }
        setInternal(name, value);
    }

    @Override
    public String get(String name) {
        Value rule = published.get().get(name);
        return rule != null ? rule.value : "";
    }

    @Override
    public boolean getBoolean(String name) {
        Value rule = published.get().get(name);
        return rule != null && rule.booleanValue;
    }

    @Override
    public int getInt(String name) {
        Value rule = published.get().get(name);
        return rule != null ? rule.intValue : 0;
    }

    @Override
    public java.util.SortedSet<String> names() {
        return java.util.Collections.unmodifiableSortedSet(
                new java.util.TreeSet<>(published.get().keySet()));
    }

    @Override
    public boolean contains(String name) {
        return published.get().containsKey(name);
    }

    /**
     * The rule's type (the reference's isType: a missing rule answers
     * false for every type; {@code ANY_VALUE} asked as the type matches
     * everything known).
     */
    public boolean isType(String name, Type type) {
        Value rule = published.get().get(name);
        return rule != null && (rule.type == type || type == Type.ANY_VALUE);
    }

    /** The rule's own type, or null for a missing name. */
    public Type typeOf(String name) {
        Value rule = published.get().get(name);
        return rule != null ? rule.type : null;
    }

    /** The persistence snapshot: the (name, value) pairs in sorted order. */
    public List<Map.Entry<String, String>> entries() {
        List<Map.Entry<String, String>> entries = new ArrayList<>(values.size());
        for (Map.Entry<String, Value> entry : values.entrySet()) {
            entries.add(Map.entry(entry.getKey(), entry.getValue().value));
        }
        return entries;
    }

    /** Restores persisted pairs (the reference's readNbt: set each key). */
    public void restore(Set<Map.Entry<String, String>> persisted) {
        for (Map.Entry<String, String> entry : persisted) {
            setInternal(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public String toString() {
        return "GameRules" + values;
    }
}
