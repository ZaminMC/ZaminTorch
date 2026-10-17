package net.zaminmc.torch.server.text;

import net.zaminmc.torch.server.scoreboard.EngineScoreboard;
import net.zaminmc.torch.server.scoreboard.ScoreboardObjective;

/**
 * The 1.8.8 score component (ScoreText.java 1:1): the content consults the
 * live main-world scoreboard when the fixed value is empty —
 * {@code ScoreboardScore.get()} as a decimal string, and the empty string
 * when the objective or the score row does not exist.
 *
 * <p>The reference reads {@code MinecraftServer.getInstance().getWorld(0)
 * .getScoreboard()} behind a {@code hasGameDirectory()} gate; the engine's
 * board installs itself here at boot (the supplier stays null in tests, so
 * the component degrades to the reference's no-server form).</p>
 */
public class ScoreText extends BaseText {
    private final String owner;
    private final String objective;
    private String value = "";

    /** The live-board consult (installed by the engine at boot; null = no server). */
    private static volatile java.util.function.Supplier<EngineScoreboard> scoreboardSupplier;

    public static void installBoard(java.util.function.Supplier<EngineScoreboard> supplier) {
        scoreboardSupplier = supplier;
    }

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
        java.util.function.Supplier<EngineScoreboard> supplier = scoreboardSupplier;
        if (supplier != null && this.value.isEmpty()) {
            EngineScoreboard scoreboard = supplier.get();
            if (scoreboard != null) {
                ScoreboardObjective objective = scoreboard.getObjective(this.objective);
                if (objective != null && scoreboard.hasScore(this.owner, objective)) {
                    this.setValue(String.format("%d", scoreboard.getScore(this.owner, objective).get()));
                } else {
                    this.value = "";
                }
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
        if (!(object instanceof ScoreText scoretext)) {
            return false;
        }
        return this.owner.equals(scoretext.owner) && this.objective.equals(scoretext.objective) && super.equals(object);
    }

    @Override
    public String toString() {
        return "ScoreComponent{name='" + this.owner + "' objective='" + this.objective
                + "', siblings=" + this.siblings + ", style=" + this.getStyle() + '}';
    }
}
