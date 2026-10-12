package net.zaminmc.torch.entity.ai;

import java.util.List;

/**
 * The registration surface of one mob's mind (§27.3's mob-AI family +
 * §27.7's "registering additional behavior"): a plugin-facing handle that
 * adds, removes, restores, and observes the goal set of the entity it was
 * obtained from.
 *
 * <p><b>Threading (§27.6 — the explicit command protocol):</b>
 * {@link #register}, {@link #remove}, {@link #removeBuiltIn}, and
 * {@link #restoreBuiltIn} are safe from any thread: each is admitted onto
 * the entity's simulation owner lane and lands between ticks, never
 * mid-iteration of the mind (a mutation requested from inside a goal body
 * lands at the next admission window). The verbs carry no synchronous
 * verdict — the mutation has not run yet when they return — and
 * {@link #goals()} is the confirmation channel: the snapshot reflects a
 * mutation once it has landed. {@link #goals()} itself is a point-in-time
 * snapshot and may be consumed anywhere.</p>
 *
 * <p><b>Identity:</b> a registered goal is addressed by its own instance
 * ({@link #goals} returns the registered instances back by identity, and
 * {@link #remove} matches on identity). The built-in family is addressed
 * by its stable ids — {@code "float"}, {@code "panic"}, {@code "breed"},
 * {@code "stroll"} — because its objects are engine-internal (§27.5: the
 * view objects {@code goals()} hands out for built-ins are per-call
 * wrappers, not stable identities).</p>
 */
public interface GoalRegistration {

    /**
     * Adds the goal to the mind at its own {@link Goal#priority() priority}
     * (stable insertion order among equal priorities, after any built-in
     * at the same band). Registering the same instance twice is a no-op.
     * Admitted onto the owner lane — see the class contract for the
     * landing/confirmation semantics.
     */
    void register(Goal goal);

    /**
     * The live goal set in priority order — the registered custom goals by
     * their own identity, the built-in family as per-call view objects
     * (each view also probes its built-in's willingness through
     * {@link Goal#canUse}, §27.3's "querying intermediate decisions").
     * Point-in-time snapshot; safe to consume anywhere.
     */
    List<Goal> goals();

    /**
     * Removes the goal from the mind. Matches the registered instance by
     * identity, or a view previously handed out by {@link #goals()} for a
     * built-in. Admitted onto the owner lane; confirm through
     * {@link #goals()}.
     */
    void remove(Goal goal);

    /**
     * Removes the built-in goal with the stable id ({@code "float"},
     * {@code "panic"}, {@code "breed"}, {@code "stroll"}) — the compose
     * and replace verb: a plugin that replaces the vanilla stroll with
     * its own pathfinding removes it by id and registers the custom goal.
     * Admitted onto the owner lane; confirm through {@link #goals()}.
     */
    void removeBuiltIn(String id);

    /**
     * Restores a removed built-in goal at its historic priority band —
     * the way back from a replacement. Admitted onto the owner lane;
     * confirm through {@link #goals()}.
     */
    void restoreBuiltIn(String id);
}
