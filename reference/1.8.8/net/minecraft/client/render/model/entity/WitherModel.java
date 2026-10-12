package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.util.math.MathHelper;

public class WitherModel extends Model {
    private ModelPart[] body;
    private ModelPart[] skulls;

    public WitherModel(float reduction) {
        this.textureWidth = 64;
        this.textureHeight = 64;
        this.body = new ModelPart[3];
        this.body[0] = new ModelPart(this, 0, 16);
        this.body[0].addBox(-10.0F, 3.9F, -0.5F, 20, 3, 3, reduction);
        this.body[1] = new ModelPart(this).setTextureSize(this.textureWidth, this.textureHeight);
        this.body[1].setPos(-2.0F, 6.9F, -0.5F);
        this.body[1].setTextureCoords(0, 22).addBox(0.0F, 0.0F, 0.0F, 3, 10, 3, reduction);
        this.body[1].setTextureCoords(24, 22).addBox(-4.0F, 1.5F, 0.5F, 11, 2, 2, reduction);
        this.body[1].setTextureCoords(24, 22).addBox(-4.0F, 4.0F, 0.5F, 11, 2, 2, reduction);
        this.body[1].setTextureCoords(24, 22).addBox(-4.0F, 6.5F, 0.5F, 11, 2, 2, reduction);
        this.body[2] = new ModelPart(this, 12, 22);
        this.body[2].addBox(0.0F, 0.0F, 0.0F, 3, 6, 3, reduction);
        this.skulls = new ModelPart[3];
        this.skulls[0] = new ModelPart(this, 0, 0);
        this.skulls[0].addBox(-4.0F, -4.0F, -4.0F, 8, 8, 8, reduction);
        this.skulls[1] = new ModelPart(this, 32, 0);
        this.skulls[1].addBox(-4.0F, -4.0F, -4.0F, 6, 6, 6, reduction);
        this.skulls[1].x = -8.0F;
        this.skulls[1].y = 4.0F;
        this.skulls[2] = new ModelPart(this, 32, 0);
        this.skulls[2].addBox(-4.0F, -4.0F, -4.0F, 6, 6, 6, reduction);
        this.skulls[2].x = 10.0F;
        this.skulls[2].y = 4.0F;
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);

        for (ModelPart modelpart : this.skulls) {
            modelpart.render(scale);
        }

        for (ModelPart modelpart1 : this.body) {
            modelpart1.render(scale);
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        float f = MathHelper.cos(bob * 0.1F);
        this.body[1].rotationX = (0.065F + 0.05F * f) * (float) Math.PI;
        this.body[2].setPos(-2.0F, 6.9F + MathHelper.cos(this.body[1].rotationX) * 10.0F, -0.5F + MathHelper.sin(this.body[1].rotationX) * 10.0F);
        this.body[2].rotationX = (0.265F + 0.1F * f) * (float) Math.PI;
        this.skulls[0].rotationY = yaw / (180.0F / (float)Math.PI);
        this.skulls[0].rotationX = pitch / (180.0F / (float)Math.PI);
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
        WitherEntity witherentity = (WitherEntity)mob;

        for (int i = 1; i < 3; i++) {
            this.skulls[i].rotationY = (witherentity.getHeadYaw(i - 1) - mob.bodyYaw) / (180.0F / (float)Math.PI);
            this.skulls[i].rotationX = witherentity.getHeadPitch(i - 1) / (180.0F / (float)Math.PI);
        }
    }
}
