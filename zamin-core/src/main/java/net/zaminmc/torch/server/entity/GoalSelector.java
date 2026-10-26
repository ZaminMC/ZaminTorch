package net.zaminmc.torch.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The prioritized goal selector (the historical PathfinderGoalSelector
 * shape): every tick it walks the goals by priority, lets additive goals
 * contribute, and stops at the first exclusive goal that claims the body.
 *
 * <p>Since the §27.3 registration surface the selector is also the mind's
 * REGISTRY: custom goals join at their own priority band, built-ins leave
 * and return by their stable ids, and the set is observable as a snapshot.
 * All mutation runs on the owner lane (the admission protocol of
 * {@link net.zaminmc.torch.entity.ai.GoalRegistration}); the selector
 * itself never mutates mid-iteration, so the tick walk below stays a
 * plain ordered scan.</p>
 */
public final class GoalSelector {

    /** The built-in family in its historic order (float, panic, breed, stroll). */
    private final List<MobGoal> builtIns;
    /** The built-ins currently removed by id (restorable). */
    private final List<MobGoal> removedBuiltIns = new ArrayList<>();
    /** Everything live — built-ins and customs, kept sorted by priority. */
    private final List<MobGoal> active;

    public GoalSelector(List<MobGoal> goals) {
        // The declared order IS the priority order; callers pass goals sorted.
        this.builtIns = List.copyOf(goals);
        this.active = new ArrayList<>(goals);
    }

    /**
     * Runs one selector pass.
     *
     * @return true when an exclusive goal consumed the tick (the combat mind
     *         is skipped); false when the body stays available.
     */
    public boolean tick(MobEntity mob, boolean night) {
        // The walk iterates the LIVE list; every mutation path runs on the
        // owner lane between ticks (never inside this loop — the admission
        // protocol re-queues anything a goal body tries to mutate mid-walk).
        for (MobGoal goal : active) {
            if (!goal.canUse(mob, night)) {
                continue;
            }
            goal.tick(mob, night);
            if (goal.exclusive()) {
                return true;
            }
        }
        return false;
    }

    // ---- the registry (owner lane only — the admission protocol) -----------

    /** Adds the goal at its own priority band; a duplicate instance is a no-op. */
    public void register(MobGoal goal) {
        Objects.requireNonNull(goal, "goal");
        if (active.contains(goal)) {
            return;
        }
        insertByPriority(goal);
    }

    /** Removes by engine-object identity; true when something left. A
     *  built-in parks in the restorable set on its way out, so the stable-id
     *  restore verb reaches every removal path. */
    public boolean removeEngine(MobGoal goal) {
        if (active.remove(goal)) {
            if (builtIns.contains(goal) && !removedBuiltIns.contains(goal)) {
                removedBuiltIns.add(goal);
            }
            return true;
        }
        return false;
    }

    /**
     * Removes the built-in with the stable id — it parks in the restorable
     * set, so {@link #restoreBuiltIn} can bring it home at its historic band.
     */
    public boolean removeBuiltIn(String id) {
        Objects.requireNonNull(id, "id");
        for (MobGoal builtIn : builtIns) {
            if (builtIn.goalId().equals(id)) {
                return removeEngine(builtIn);
            }
        }
        return false; // no built-in carries that id
    }

    /** Restores a removed built-in at its historic priority band. */
    public boolean restoreBuiltIn(String id) {
        Objects.requireNonNull(id, "id");
        for (MobGoal parked : removedBuiltIns) {
            if (parked.goalId().equals(id)) {
                removedBuiltIns.remove(parked);
                insertByPriority(parked);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the built-in with the stable id is LIVE (the behavioral
     * consult — a removed goal must not only leave the snapshot, its walk
     * must stop starting). The mind's stroll band consults this every
     * idle tick: the tail call that runs the stroll when the selector did
     * not claim would otherwise keep a REMOVED stroll walking forever.
     */
    public boolean hasBuiltIn(String id) {
        for (MobGoal goal : active) {
            if (goal.goalId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The live set in priority order (a snapshot copy — safe anywhere). */
    public List<MobGoal> snapshot() {
        return List.copyOf(active);
    }

    /**
     * Inserts at the goal's priority band. Stable within the band: every
     * standing goal with the same number keeps its place, so a custom joins
     * after the built-ins and any earlier-registered custom at that band
     * (the reference's set-of-goals semantics: same-priority goals run in
     * the order they joined).
     */
    private void insertByPriority(MobGoal goal) {
        int at = active.size();
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).priority() > goal.priority()) {
                at = i;
                break;
            }
        }
        active.add(at, goal);
    }
}
