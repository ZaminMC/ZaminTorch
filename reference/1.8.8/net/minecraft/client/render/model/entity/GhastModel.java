package net.minecraft.client.render.model.entity;

import java.util.Random;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class GhastModel extends Model {
    ModelPart body;
    ModelPart[] tentacles = new ModelPart[9];

    public GhastModel() {
        int i = -16;
        this.body = new ModelPart(this, 0, 0);
        this.body.addBox(-8.0F, -8.0F, -8.0F, 16, 16, 16);
        this.body.y += 24 + i;
        Random random = new Random(1660L);

        for (int j = 0; j < this.tentacles.length; j++) {
            this.tentacles[j] = new ModelPart(this, 0, 0);
            float f = ((j % 3 - j / 3 % 2 * 0.5F + 0.25F) / 2.0F * 2.0F - 1.0F) * 5.0F;
            float f1 = (j / 3 / 2.0F * 2.0F - 1.0F) * 5.0F;
            int k = random.nextInt(7) + 8;
            this.tentacles[j].addBox(-1.0F, 0.0F, -1.0F, 2, k, 2);
            this.tentacles[j].x = f;
            this.tentacles[j].z = f1;
            this.tentacles[j].y = 31 + i;
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        for (int i = 0; i < this.tentacles.length; i++) {
            this.tentacles[i].rotationX = 0.2F * MathHelper.sin(bob * 0.3F + i) + 0.4F;
        }
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        GlStateManager.pushMatrix();
        GlStateManager.translatef(0.0F, 0.6F, 0.0F);
        this.body.render(scale);

        for (ModelPart modelpart : this.tentacles) {
            modelpart.render(scale);
        }

        GlStateManager.popMatrix();
    }
}
