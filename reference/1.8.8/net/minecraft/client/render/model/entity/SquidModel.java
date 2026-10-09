package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;

public class SquidModel extends Model {
    ModelPart body;
    ModelPart[] tentacles = new ModelPart[8];

    public SquidModel() {
        int i = -16;
        this.body = new ModelPart(this, 0, 0);
        this.body.addBox(-6.0F, -8.0F, -6.0F, 12, 16, 12);
        this.body.y += 24 + i;

        for (int j = 0; j < this.tentacles.length; j++) {
            this.tentacles[j] = new ModelPart(this, 48, 0);
            double d0 = j * Math.PI * 2.0 / this.tentacles.length;
            float f = (float)Math.cos(d0) * 5.0F;
            float f1 = (float)Math.sin(d0) * 5.0F;
            this.tentacles[j].addBox(-1.0F, 0.0F, -1.0F, 2, 18, 2);
            this.tentacles[j].x = f;
            this.tentacles[j].z = f1;
            this.tentacles[j].y = 31 + i;
            d0 = j * Math.PI * -2.0 / this.tentacles.length + (Math.PI / 2);
            this.tentacles[j].rotationY = (float)d0;
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        for (ModelPart modelpart : this.tentacles) {
            modelpart.rotationX = bob;
        }
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.body.render(scale);

        for (int i = 0; i < this.tentacles.length; i++) {
            this.tentacles[i].render(scale);
        }
    }
}
