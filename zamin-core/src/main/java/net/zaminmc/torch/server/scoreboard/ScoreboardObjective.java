package net.zaminmc.torch.server.scoreboard;

/**
 * One objective (ScoreboardObjective 1:1): a named score column over a
 * criterion. The display name starts as the name; both display name and
 * render type changes fire the update broadcast (action 2) through the
 * owning scoreboard.
 */
public final class ScoreboardObjective {

    private final EngineScoreboard scoreboard;
    private final String name;
    private final ScoreboardCriterion criterion;
    private ScoreboardCriterion.RenderType renderType;
    private String displayName;

    ScoreboardObjective(EngineScoreboard scoreboard, String name,
                        ScoreboardCriterion criterion) {
        this.scoreboard = scoreboard;
        this.name = name;
        this.criterion = criterion;
        this.displayName = name;
        this.renderType = criterion.renderType();
    }

    public EngineScoreboard scoreboard() {
        return scoreboard;
    }

    public String name() {
        return name;
    }

    public ScoreboardCriterion criterion() {
        return criterion;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        scoreboard.onObjectiveUpdated(this);
    }

    public ScoreboardCriterion.RenderType renderType() {
        return renderType;
    }

    public void setRenderType(ScoreboardCriterion.RenderType type) {
        this.renderType = type;
        scoreboard.onObjectiveUpdated(this);
    }
}
