package net.minecraft.scoreboard;

import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;

public class ScoreboardScore {
    public static final Comparator<ScoreboardScore> COMPARATOR = new Comparator<ScoreboardScore>() {
        public int compare(ScoreboardScore scoreboardScore, ScoreboardScore scoreboardScore2) {
            if (scoreboardScore.get() > scoreboardScore2.get()) {
                return 1;
            } else {
                return scoreboardScore.get() < scoreboardScore2.get() ? -1 : scoreboardScore2.getOwner().compareToIgnoreCase(scoreboardScore.getOwner());
            }
        }
    };
    private final Scoreboard scoreboard;
    private final ScoreboardObjective objective;
    private final String owner;
    private int score;
    private boolean locked;
    private boolean forceUpdate;

    public ScoreboardScore(Scoreboard scoreboard, ScoreboardObjective objective, String owner) {
        this.scoreboard = scoreboard;
        this.objective = objective;
        this.owner = owner;
        this.forceUpdate = true;
    }

    public void increase(int amount) {
        if (this.objective.getCriterion().isReadOnly()) {
            throw new IllegalStateException("Cannot modify read-only score");
        }

        this.set(this.get() + amount);
    }

    public void decrease(int amount) {
        if (this.objective.getCriterion().isReadOnly()) {
            throw new IllegalStateException("Cannot modify read-only score");
        }

        this.set(this.get() - amount);
    }

    public void increment() {
        if (this.objective.getCriterion().isReadOnly()) {
            throw new IllegalStateException("Cannot modify read-only score");
        }

        this.increase(1);
    }

    public int get() {
        return this.score;
    }

    public void set(int score) {
        int i = this.score;
        this.score = score;
        if (i != score || this.forceUpdate) {
            this.forceUpdate = false;
            this.getScoreboard().onScoreUpdated(this);
        }
    }

    public ScoreboardObjective getObjective() {
        return this.objective;
    }

    public String getOwner() {
        return this.owner;
    }

    public Scoreboard getScoreboard() {
        return this.scoreboard;
    }

    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public void setToTotalOf(List<PlayerEntity> owners) {
        this.set(this.objective.getCriterion().countScore(owners));
    }
}
