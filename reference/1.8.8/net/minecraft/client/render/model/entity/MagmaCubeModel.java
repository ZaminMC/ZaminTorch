package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.MagmaCubeEntity;

public class MagmaCubeModel extends Model {
    ModelPart[] slices = new ModelPart[8];
    ModelPart body;

    public MagmaCubeModel() {
        for (int i = 0; i < this.slices.length; i++) {
            int j = 0;
            int k = i;
            if (i == 2) {
                j = 24;
                k = 10;
            } else if (i == 3) {
                j = 24;
                k = 19;
            }

            this.slices[i] = new ModelPart(this, j, k);
            this.slices[i].addBox(-4.0F, 16 + i, -4.0F, 8, 1, 8);
        }

        this.body = new ModelPart(this, 0, 16);
        this.body.addBox(-2.0F, 18.0F, -2.0F, 4, 4, 4);
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
        MagmaCubeEntity magmacubeentity = (MagmaCubeEntity)mob;
        float f = magmacubeentity.lastStretch + (magmacubeentity.stretch - magmacubeentity.lastStretch) * tickDelta;
        if (f < 0.0F) {
            f = 0.0F;
        }

        for (int i = 0; i < this.slices.length; i++) {
            this.slices[i].y = -(4 - i) * f * 1.7F;
        }
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.body.render(scale);

        for (int i = 0; i < this.slices.length; i++) {
            this.slices[i].render(scale);
        }
    }
}
