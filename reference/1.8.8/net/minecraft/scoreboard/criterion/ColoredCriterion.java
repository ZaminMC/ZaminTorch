package net.minecraft.scoreboard.criterion;

import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.Formatting;

public class ColoredCriterion implements ScoreboardCriterion {
    private final String name;

    public ColoredCriterion(String name, Formatting color) {
        this.name = name + color.getName();
        ScoreboardCriterion.BY_NAME.put(this.name, this);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int countScore(List<PlayerEntity> owners) {
        return 0;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ScoreboardCriterion.RenderType getRenderType() {
        return ScoreboardCriterion.RenderType.INTEGER;
    }
}
