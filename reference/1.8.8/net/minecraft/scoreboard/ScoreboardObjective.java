package net.minecraft.scoreboard;

import net.minecraft.scoreboard.criterion.ScoreboardCriterion;

public class ScoreboardObjective {
    private final Scoreboard scoreboard;
    private final String name;
    private final ScoreboardCriterion criterion;
    private ScoreboardCriterion.RenderType renderType;
    private String displayName;

    public ScoreboardObjective(Scoreboard scoreboard, String name, ScoreboardCriterion criterion) {
        this.scoreboard = scoreboard;
        this.name = name;
        this.criterion = criterion;
        this.displayName = name;
        this.renderType = criterion.getRenderType();
    }

    public Scoreboard getScoreboard() {
        return this.scoreboard;
    }

    public String getName() {
        return this.name;
    }

    public ScoreboardCriterion getCriterion() {
        return this.criterion;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.scoreboard.onObjectiveUpdated(this);
    }

    public ScoreboardCriterion.RenderType getRenderType() {
        return this.renderType;
    }

    public void setRenderType(ScoreboardCriterion.RenderType type) {
        this.renderType = type;
        this.scoreboard.onObjectiveUpdated(this);
    }
}
