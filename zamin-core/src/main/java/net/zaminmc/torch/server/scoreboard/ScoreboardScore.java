package net.zaminmc.torch.server.scoreboard;

import java.util.Comparator;

/**
 * One score cell (ScoreboardScore 1:1): an owner's value under one
 * objective. The reference's own laws: the read-only gate on
 * increase/decrease, the forceUpdate flag (a freshly created cell always
 * publishes once even at value 0), and the comparator (value ascending,
 * ties by reverse owner name — the packet emission order).
 */
public final class ScoreboardScore {

    /**
     * The reference's own COMPARATOR (ScoreboardScore lines 10-18): value
     * ASCENDING, ties broken by the REVERSE owner-name comparison
     * (b.compareToIgnoreCase(a)) — the order the join-sync's score packets
     * emit in (the client renders its own sort).
     */
    public static final Comparator<ScoreboardScore> SCORE_ORDER = (a, b) -> {
        if (a.get() > b.get()) {
            return 1;
        }
        if (a.get() < b.get()) {
            return -1;
        }
        return b.owner().compareToIgnoreCase(a.owner());
    };

    private final EngineScoreboard scoreboard;
    private final ScoreboardObjective objective;
    private final String owner;
    private int score;
    private boolean locked;
    private boolean forceUpdate;

    ScoreboardScore(EngineScoreboard scoreboard, ScoreboardObjective objective, String owner) {
        this.scoreboard = scoreboard;
        this.objective = objective;
        this.owner = owner;
        this.forceUpdate = true;
    }

    public void increase(int amount) {
        if (objective.criterion().isReadOnly()) {
            throw new IllegalStateException("Cannot modify read-only score");
        }
        set(get() + amount);
    }

    public void decrease(int amount) {
        if (objective.criterion().isReadOnly()) {
            throw new IllegalStateException("Cannot modify read-only score");
        }
        set(get() - amount);
    }

    public void increment() {
        if (objective.criterion().isReadOnly()) {
            throw new IllegalStateException("Cannot modify read-only score");
        }
        increase(1);
    }

    public int get() {
        return score;
    }

    public void set(int score) {
        int previous = this.score;
        this.score = score;
        if (previous != score || forceUpdate) {
            forceUpdate = false;
            scoreboard.onScoreUpdated(this);
        }
    }

    public ScoreboardObjective objective() {
        return objective;
    }

    public String owner() {
        return owner;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
