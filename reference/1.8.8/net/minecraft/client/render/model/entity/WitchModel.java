package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class WitchModel extends VillagerModel {
    public boolean itemInHand;
    private ModelPart mole = new ModelPart(this).setTextureSize(64, 128);
    private ModelPart hat;

    public WitchModel(float f) {
        super(f, 0.0F, 64, 128);
        this.mole.setPos(0.0F, -2.0F, 0.0F);
        this.mole.setTextureCoords(0, 0).addBox(0.0F, 3.0F, -6.75F, 1, 1, 1, -0.25F);
        this.nose.addChild(this.mole);
        this.hat = new ModelPart(this).setTextureSize(64, 128);
        this.hat.setPos(-5.0F, -10.03125F, -5.0F);
        this.hat.setTextureCoords(0, 64).addBox(0.0F, 0.0F, 0.0F, 10, 2, 10);
        this.head.addChild(this.hat);
        ModelPart modelpart = new ModelPart(this).setTextureSize(64, 128);
        modelpart.setPos(1.75F, -4.0F, 2.0F);
        modelpart.setTextureCoords(0, 76).addBox(0.0F, 0.0F, 0.0F, 7, 4, 7);
        modelpart.rotationX = -0.05235988F;
        modelpart.rotationZ = 0.02617994F;
        this.hat.addChild(modelpart);
        ModelPart modelpart1 = new ModelPart(this).setTextureSize(64, 128);
        modelpart1.setPos(1.75F, -4.0F, 2.0F);
        modelpart1.setTextureCoords(0, 87).addBox(0.0F, 0.0F, 0.0F, 4, 4, 4);
        modelpart1.rotationX = -0.10471976F;
        modelpart1.rotationZ = 0.05235988F;
        modelpart.addChild(modelpart1);
        ModelPart modelpart2 = new ModelPart(this).setTextureSize(64, 128);
        modelpart2.setPos(1.75F, -2.0F, 2.0F);
        modelpart2.setTextureCoords(0, 95).addBox(0.0F, 0.0F, 0.0F, 1, 2, 1, 0.25F);
        modelpart2.rotationX = (float) (-Math.PI / 15);
        modelpart2.rotationZ = 0.10471976F;
        modelpart1.addChild(modelpart2);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.nose.translateX = this.nose.translateY = this.nose.translateZ = 0.0F;
        float f = 0.01F * (entity.getNetworkId() % 10);
        this.nose.rotationX = MathHelper.sin(entity.ticks * f) * 4.5F * (float) Math.PI / 180.0F;
        this.nose.rotationY = 0.0F;
        this.nose.rotationZ = MathHelper.cos(entity.ticks * f) * 2.5F * (float) Math.PI / 180.0F;
        if (this.itemInHand) {
            this.nose.rotationX = -0.9F;
            this.nose.translateZ = -0.09375F;
            this.nose.translateY = 0.1875F;
        }
    }
}
