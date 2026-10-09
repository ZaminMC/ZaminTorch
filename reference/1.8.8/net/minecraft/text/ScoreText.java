package net.minecraft.text;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.server.MinecraftServer;

public class ScoreText extends BaseText {
    private final String owner;
    private final String objective;
    private String value = "";

    public ScoreText(String owner, String objective) {
        this.owner = owner;
        this.objective = objective;
    }

    public String getOwner() {
        return this.owner;
    }

    public String getObjective() {
        return this.objective;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String getContent() {
        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        if (minecraftserver != null && minecraftserver.hasGameDirectory() && StringUtils.isStringEmpty(this.value)) {
            Scoreboard scoreboard = minecraftserver.getWorld(0).getScoreboard();
            ScoreboardObjective scoreboardobjective = scoreboard.getObjective(this.objective);
            if (scoreboard.hasScore(this.owner, scoreboardobjective)) {
                ScoreboardScore scoreboardscore = scoreboard.getScore(this.owner, scoreboardobjective);
                this.setValue(String.format("%d", scoreboardscore.get()));
            } else {
                this.value = "";
            }
        }

        return this.value;
    }

    public ScoreText copy() {
        ScoreText scoretext = new ScoreText(this.owner, this.objective);
        scoretext.setValue(this.value);
        scoretext.setStyle(this.getStyle().deepCopy());

        for (Text text : this.getSiblings()) {
            scoretext.append(text.copy());
        }

        return scoretext;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ScoreText)) {
            return false;
        }

        ScoreText scoretext = (ScoreText)object;
        return this.owner.equals(scoretext.owner) && this.objective.equals(scoretext.objective) && super.equals(object);
    }

    @Override
    public String toString() {
        return "ScoreComponent{name='"
            + this.owner
            + '\''
            + "objective='"
            + this.objective
            + '\''
            + ", siblings="
            + this.siblings
            + ", style="
            + this.getStyle()
            + '}';
    }
}
