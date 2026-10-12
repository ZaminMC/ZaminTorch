package net.minecraft.scoreboard.criterion;

import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;

public class GenericCriterion implements ScoreboardCriterion {
    private final String name;

    public GenericCriterion(String name) {
        this.name = name;
        ScoreboardCriterion.BY_NAME.put(name, this);
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
