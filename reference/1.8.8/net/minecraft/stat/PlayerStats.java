package net.minecraft.stat;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.stat.achievement.AchievementStat;

public class PlayerStats {
    protected final Map<Stat, StatCounter> counters = Maps.newConcurrentMap();

    public boolean hasAchievement(AchievementStat achievement) {
        return this.get((Stat)achievement) > 0;
    }

    public boolean hasParentAchievement(AchievementStat achievement) {
        return achievement.parent == null || this.hasAchievement(achievement.parent);
    }

    public int get(AchievementStat achievement) {
        if (this.hasAchievement(achievement)) {
            return 0;
        }

        int i = 0;

        for (AchievementStat achievementstat = achievement.parent; achievementstat != null && !this.hasAchievement(achievementstat); i++) {
            achievementstat = achievementstat.parent;
        }

        return i;
    }

    public void increment(PlayerEntity player, Stat stat, int amount) {
        if (!stat.isAchievement() || this.hasParentAchievement((AchievementStat)stat)) {
            this.set(player, stat, this.get(stat) + amount);
        }
    }

    public void set(PlayerEntity player, Stat stat, int value) {
        StatCounter statcounter = this.counters.get(stat);
        if (statcounter == null) {
            statcounter = new StatCounter();
            this.counters.put(stat, statcounter);
        }

        statcounter.setValue(value);
    }

    public int get(Stat stat) {
        StatCounter statcounter = this.counters.get(stat);
        return statcounter == null ? 0 : statcounter.getValue();
    }

    public <T extends StatProgress> T getProgress(Stat stat) {
        StatCounter statcounter = this.counters.get(stat);
        return statcounter != null ? statcounter.getProgress() : null;
    }

    public <T extends StatProgress> T setProgress(Stat stat, T progress) {
        StatCounter statcounter = this.counters.get(stat);
        if (statcounter == null) {
            statcounter = new StatCounter();
            this.counters.put(stat, statcounter);
        }

        statcounter.setProgress(progress);
        return progress;
    }
}
