package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.entity.ai.Goal;
import net.zaminmc.torch.entity.ai.GoalRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The public registration surface (§27.3's mob-AI family) over one body's
 * {@link GoalSelector}: register, remove, restore, and observe the mind.
 *
 * <p><b>The admission protocol (§27.6):</b> every mutation verb is admitted
 * onto the entity's owner lane — the world's {@code execute} admission the
 * manager wires at spawn — so a registration from any thread lands between
 * ticks, never inside the selector's walk. A mutation requested from
 * INSIDE a goal body re-queues the same way (the admission lane re-enters
 * at its next window); nothing mutates the live set mid-iteration. The
 * verbs return nothing — the mutation has not run yet when they return —
 * and {@code goals()} is the confirmation channel. A body with no
 * admission wired (unit harnesses constructing the manager bare) mutates
 * directly on the calling thread — the caller then owns the context, the
 * same contract the entity mutation family carries.</p>
 *
 * <p><b>The bridge shapes:</b> the engine's {@link MobGoal} and the public
 * {@link Goal} differ only in the body's type (the engine object IS the
 * public handle, §27.5 — a cast, never a copy), so two thin adapters carry
 * each direction: {@link PluginGoalAdapter} wraps a registered public goal
 * for the selector, and {@link BuiltinGoalView} dresses an engine built-in
 * for the public snapshot. Custom goals keep their identity through the
 * round trip — {@code goals()} hands the registered instance itself back.</p>
 */
final class GoalRegistrationSurface implements GoalRegistration {

    private final MobEntity mob;

    private GoalRegistrationSurface(MobEntity mob) {
        this.mob = Objects.requireNonNull(mob, "mob");
    }

    /** The body's registration surface (created once, on first use). */
    static GoalRegistration of(MobEntity mob) {
        return new GoalRegistrationSurface(mob);
    }

    @Override
    public void register(Goal goal) {
        Objects.requireNonNull(goal, "goal");
        admit(() -> mob.goalSelector().register(new PluginGoalAdapter(goal)));
    }

    @Override
    public List<Goal> goals() {
        // The snapshot is a point-in-time copy (List.copyOf under us) —
        // safe to dress and hand out from any thread.
        List<Goal> dressed = new ArrayList<>();
        for (MobGoal entry : mob.goalSelector().snapshot()) {
            if (entry instanceof PluginGoalAdapter adapter) {
                dressed.add(adapter.target()); // the plugin's own instance, by identity
            } else {
                dressed.add(new BuiltinGoalView(entry));
            }
        }
        return List.copyOf(dressed);
    }

    @Override
    public void remove(Goal goal) {
        Objects.requireNonNull(goal, "goal");
        admit(() -> {
            GoalSelector selector = mob.goalSelector();
            // The custom case first: the registered adapter whose target is
            // this instance.
            for (MobGoal entry : selector.snapshot()) {
                if (entry instanceof PluginGoalAdapter adapter && adapter.target() == goal) {
                    selector.removeEngine(entry);
                    return;
                }
            }
            // The built-in view case: the view's engine target, by identity.
            if (goal instanceof BuiltinGoalView view) {
                for (MobGoal entry : selector.snapshot()) {
                    if (view.target() == entry) {
                        selector.removeEngine(entry);
                        return;
                    }
                }
            }
        });
    }

    @Override
    public void removeBuiltIn(String id) {
        Objects.requireNonNull(id, "id");
        admit(() -> mob.goalSelector().removeBuiltIn(id));
    }

    @Override
    public void restoreBuiltIn(String id) {
        Objects.requireNonNull(id, "id");
        admit(() -> mob.goalSelector().restoreBuiltIn(id));
    }

    /**
     * Runs the mutation on the owner lane when the admission is wired;
     * otherwise runs it inline (the bare-harness contract: the caller
     * owns the context).
     */
    private void admit(Runnable mutation) {
        Consumer<Runnable> admission = mob.goalAdmission();
        if (admission != null) {
            admission.accept(mutation);
        } else {
            mutation.run();
        }
    }

    /** A registered public goal, dressed for the engine's selector. */
    static final class PluginGoalAdapter implements MobGoal {

        private final Goal target;

        PluginGoalAdapter(Goal target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        Goal target() {
            return target;
        }

        @Override public int priority() {
            return target.priority();
        }

        @Override public boolean exclusive() {
            return target.exclusive();
        }

        @Override public boolean canUse(MobEntity body, boolean night) {
            return target.canUse(body, night); // the engine object IS the handle (§27.5)
        }

        @Override public void tick(MobEntity body, boolean night) {
            target.tick(body, night);
        }
    }

    /** An engine built-in, dressed for the public snapshot. */
    static final class BuiltinGoalView implements Goal {

        private final MobGoal target;

        BuiltinGoalView(MobGoal target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        MobGoal target() {
            return target;
        }

        @Override public int priority() {
            return target.priority();
        }

        @Override public boolean exclusive() {
            return target.exclusive();
        }

        @Override public boolean canUse(LivingEntity body, boolean night) {
            return target.canUse(asEngine(body), night);
        }

        @Override public void tick(LivingEntity body, boolean night) {
            target.tick(asEngine(body), night);
        }

        private static MobEntity asEngine(LivingEntity body) {
            if (body instanceof MobEntity engine) {
                return engine;
            }
            throw new IllegalStateException(
                    "A built-in goal view only drives engine mobs (got " + body + ")");
        }
    }
}
