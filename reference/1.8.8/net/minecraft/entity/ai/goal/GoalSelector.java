package net.minecraft.entity.ai.goal;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.profiler.Profiler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GoalSelector {
    private static final Logger LOGGER = LogManager.getLogger();
    private List<GoalSelector.Entry> goals = Lists.newArrayList();
    private List<GoalSelector.Entry> activeGoals = Lists.newArrayList();
    private final Profiler profiler;
    private int ticks;
    private int constantThree = 3;

    public GoalSelector(Profiler profiler) {
        this.profiler = profiler;
    }

    public void addGoal(int priority, Goal goal) {
        this.goals.add(new GoalSelector.Entry(priority, goal));
    }

    public void removeGoal(Goal goal) {
        Iterator<GoalSelector.Entry> iterator = this.goals.iterator();

        while (iterator.hasNext()) {
            GoalSelector.Entry goalselector$entry = iterator.next();
            Goal goalx = goalselector$entry.goal;
            if (goalx == goal) {
                if (this.activeGoals.contains(goalselector$entry)) {
                    goalx.stop();
                    this.activeGoals.remove(goalselector$entry);
                }

                iterator.remove();
            }
        }
    }

    public void tick() {
        this.profiler.push("goalSetup");
        if (this.ticks++ % this.constantThree == 0) {
            for (GoalSelector.Entry goalselector$entry : this.goals) {
                boolean flag = this.activeGoals.contains(goalselector$entry);
                if (flag) {
                    if (this.canUse(goalselector$entry) && this.canContinue(goalselector$entry)) {
                        continue;
                    }

                    goalselector$entry.goal.stop();
                    this.activeGoals.remove(goalselector$entry);
                }

                if (this.canUse(goalselector$entry) && goalselector$entry.goal.canStart()) {
                    goalselector$entry.goal.start();
                    this.activeGoals.add(goalselector$entry);
                }
            }
        } else {
            Iterator<GoalSelector.Entry> iterator = this.activeGoals.iterator();

            while (iterator.hasNext()) {
                GoalSelector.Entry goalselector$entry1 = iterator.next();
                if (!this.canContinue(goalselector$entry1)) {
                    goalselector$entry1.goal.stop();
                    iterator.remove();
                }
            }
        }

        this.profiler.pop();
        this.profiler.push("goalTick");

        for (GoalSelector.Entry goalselector$entry2 : this.activeGoals) {
            goalselector$entry2.goal.tick();
        }

        this.profiler.pop();
    }

    private boolean canContinue(GoalSelector.Entry goalListEntry) {
        return goalListEntry.goal.shouldContinue();
    }

    private boolean canUse(GoalSelector.Entry goalListEntry) {
        for (GoalSelector.Entry goalselector$entry : this.goals) {
            if (goalselector$entry != goalListEntry) {
                if (goalListEntry.priority >= goalselector$entry.priority) {
                    if (!this.differentControlBits(goalListEntry, goalselector$entry) && this.activeGoals.contains(goalselector$entry)) {
                        return false;
                    }
                } else if (!goalselector$entry.goal.canStop() && this.activeGoals.contains(goalselector$entry)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean differentControlBits(GoalSelector.Entry goalListEntry1, GoalSelector.Entry goalListEntry2) {
        return (goalListEntry1.goal.getControls() & goalListEntry2.goal.getControls()) == 0;
    }

    class Entry {
        public Goal goal;
        public int priority;

        public Entry(int priority, Goal goal) {
            this.priority = priority;
            this.goal = goal;
        }
    }
}
