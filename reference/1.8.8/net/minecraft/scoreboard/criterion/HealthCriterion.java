package net.minecraft.scoreboard.criterion;

import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

public class HealthCriterion extends GenericCriterion {
    public HealthCriterion(String string) {
        super(string);
    }

    @Override
    public int countScore(List<PlayerEntity> owners) {
        float f = 0.0F;

        for (PlayerEntity playerentity : owners) {
            f += playerentity.getHealth() + playerentity.getAbsorption();
        }

        if (owners.size() > 0) {
            f /= owners.size();
        }

        return MathHelper.ceil(f);
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ScoreboardCriterion.RenderType getRenderType() {
        return ScoreboardCriterion.RenderType.HEARTS;
    }
}
