package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;

public class ArmorStandModel extends ArmorStandArmorModel {
    public ModelPart rightBodyStick;
    public ModelPart leftBodyStick;
    public ModelPart shoulderStick;
    public ModelPart basePlate;

    public ArmorStandModel() {
        this(0.0F);
    }

    public ArmorStandModel(float f) {
        super(f, 64, 64);
        this.head = new ModelPart(this, 0, 0);
        this.head.addBox(-1.0F, -7.0F, -1.0F, 2, 7, 2, f);
        this.head.setPos(0.0F, 0.0F, 0.0F);
        this.body = new ModelPart(this, 0, 26);
        this.body.addBox(-6.0F, 0.0F, -1.5F, 12, 3, 3, f);
        this.body.setPos(0.0F, 0.0F, 0.0F);
        this.rightArm = new ModelPart(this, 24, 0);
        this.rightArm.addBox(-2.0F, -2.0F, -1.0F, 2, 12, 2, f);
        this.rightArm.setPos(-5.0F, 2.0F, 0.0F);
        this.leftArm = new ModelPart(this, 32, 16);
        this.leftArm.flipped = true;
        this.leftArm.addBox(0.0F, -2.0F, -1.0F, 2, 12, 2, f);
        this.leftArm.setPos(5.0F, 2.0F, 0.0F);
        this.rightLeg = new ModelPart(this, 8, 0);
        this.rightLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 11, 2, f);
        this.rightLeg.setPos(-1.9F, 12.0F, 0.0F);
        this.leftLeg = new ModelPart(this, 40, 16);
        this.leftLeg.flipped = true;
        this.leftLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 11, 2, f);
        this.leftLeg.setPos(1.9F, 12.0F, 0.0F);
        this.rightBodyStick = new ModelPart(this, 16, 0);
        this.rightBodyStick.addBox(-3.0F, 3.0F, -1.0F, 2, 7, 2, f);
        this.rightBodyStick.setPos(0.0F, 0.0F, 0.0F);
        this.rightBodyStick.visible = true;
        this.leftBodyStick = new ModelPart(this, 48, 16);
        this.leftBodyStick.addBox(1.0F, 3.0F, -1.0F, 2, 7, 2, f);
        this.leftBodyStick.setPos(0.0F, 0.0F, 0.0F);
        this.shoulderStick = new ModelPart(this, 0, 48);
        this.shoulderStick.addBox(-4.0F, 10.0F, -1.0F, 8, 2, 2, f);
        this.shoulderStick.setPos(0.0F, 0.0F, 0.0F);
        this.basePlate = new ModelPart(this, 0, 32);
        this.basePlate.addBox(-6.0F, 11.0F, -6.0F, 12, 1, 12, f);
        this.basePlate.setPos(0.0F, 12.0F, 0.0F);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        if (entity instanceof ArmorStandEntity) {
            ArmorStandEntity armorstandentity = (ArmorStandEntity)entity;
            this.leftArm.visible = armorstandentity.isShowArms();
            this.rightArm.visible = armorstandentity.isShowArms();
            this.basePlate.visible = !armorstandentity.isBasePlateVisible();
            this.leftLeg.setPos(1.9F, 12.0F, 0.0F);
            this.rightLeg.setPos(-1.9F, 12.0F, 0.0F);
            this.rightBodyStick.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getPitch();
            this.rightBodyStick.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getYaw();
            this.rightBodyStick.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getRoll();
            this.leftBodyStick.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getPitch();
            this.leftBodyStick.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getYaw();
            this.leftBodyStick.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getRoll();
            this.shoulderStick.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getPitch();
            this.shoulderStick.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getYaw();
            this.shoulderStick.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getRoll();
            float f = (armorstandentity.getLeftLegRotation().getPitch() + armorstandentity.getRightLegRotation().getPitch()) / 2.0F;
            float f1 = (armorstandentity.getLeftLegRotation().getYaw() + armorstandentity.getRightLegRotation().getYaw()) / 2.0F;
            float f2 = (armorstandentity.getLeftLegRotation().getRoll() + armorstandentity.getRightLegRotation().getRoll()) / 2.0F;
            this.basePlate.rotationX = 0.0F;
            this.basePlate.rotationY = (float) (Math.PI / 180.0) * -entity.yaw;
            this.basePlate.rotationZ = 0.0F;
        }
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        super.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
        GlStateManager.pushMatrix();
        if (this.isBaby) {
            float f = 2.0F;
            GlStateManager.scalef(1.0F / f, 1.0F / f, 1.0F / f);
            GlStateManager.translatef(0.0F, 24.0F * scale, 0.0F);
            this.rightBodyStick.render(scale);
            this.leftBodyStick.render(scale);
            this.shoulderStick.render(scale);
            this.basePlate.render(scale);
        } else {
            if (entity.isSneaking()) {
                GlStateManager.translatef(0.0F, 0.2F, 0.0F);
            }

            this.rightBodyStick.render(scale);
            this.leftBodyStick.render(scale);
            this.shoulderStick.render(scale);
            this.basePlate.render(scale);
        }

        GlStateManager.popMatrix();
    }

    @Override
    public void translateRightArm(float scale) {
        boolean flag = this.rightArm.visible;
        this.rightArm.visible = true;
        super.translateRightArm(scale);
        this.rightArm.visible = flag;
    }
}
