package net.zaminmc.torch.gamerule;

import java.util.SortedSet;

/**
 * The world's game-rule table (§27.3 — the 1.8.8 gamerule family): the
 * fifteen vanilla rules (plus any custom {@code ANY_VALUE} additions a
 * server persists) with the reference's string-value model.
 *
 * <p>Threading contract (§27.6): every read ({@link #get},
 * {@link #getBoolean}, {@link #getInt}, {@link #names},
 * {@link #contains}) is safe from any thread — each read observes one
 * fully-published table snapshot. Mutation ({@link #set}) is an
 * owner-context operation: call it on the simulation context (inside
 * {@link net.zaminmc.torch.World#execute}, or from the tick-lane
 * handlers); the off-lane call throws the loud ownership refusal.</p>
 */
public interface GameRules {

    /**
     * @return the rule's raw string value, or the empty string for a
     *         missing name (the reference's own miss arm).
     */
    String get(String rule);

    /**
     * @return the rule's boolean view ({@code Boolean.parseBoolean} on
     *         the raw string); false for a missing name.
     */
    boolean getBoolean(String rule);

    /**
     * @return the rule's int view (the parsed integer, or the boolean's
     *         1/0 fallback when the string is not a number); 0 for a
     *         missing name.
     */
    int getInt(String rule);

    /**
     * @return every known rule name in sorted order (the reference's
     *         TreeMap enumeration).
     */
    SortedSet<String> names();

    /** @return whether the rule exists. */
    boolean contains(String rule);

    /**
     * Sets a rule's value. A known rule keeps its type; an unknown name
     * is ADDED as a custom any-type rule (the reference's extension
     * arm). Owner context only — the {@code /gamerule} command is the
     * console route.
     *
     * @throws IllegalStateException if called outside the simulation context
     */
    void set(String rule, String value);
}
