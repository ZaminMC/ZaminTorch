package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class HumanoidModel extends Model {
    public ModelPart head;
    public ModelPart hat;
    public ModelPart body;
    public ModelPart rightArm;
    public ModelPart leftArm;
    public ModelPart rightLeg;
    public ModelPart leftLeg;
    public int itemInLeftHand;
    public int itemInRightHand;
    public boolean sneaking;
    public boolean aimingBow;

    public HumanoidModel() {
        this(0.0F);
    }

    public HumanoidModel(float reduction) {
        this(reduction, 0.0F, 64, 32);
    }

    public HumanoidModel(float reduction, float pivotPoint, int textureWidth, int textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.head = new ModelPart(this, 0, 0);
        this.head.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, reduction);
        this.head.setPos(0.0F, 0.0F + pivotPoint, 0.0F);
        this.hat = new ModelPart(this, 32, 0);
        this.hat.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, reduction + 0.5F);
        this.hat.setPos(0.0F, 0.0F + pivotPoint, 0.0F);
        this.body = new ModelPart(this, 16, 16);
        this.body.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, reduction);
        this.body.setPos(0.0F, 0.0F + pivotPoint, 0.0F);
        this.rightArm = new ModelPart(this, 40, 16);
        this.rightArm.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, reduction);
        this.rightArm.setPos(-5.0F, 2.0F + pivotPoint, 0.0F);
        this.leftArm = new ModelPart(this, 40, 16);
        this.leftArm.flipped = true;
        this.leftArm.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, reduction);
        this.leftArm.setPos(5.0F, 2.0F + pivotPoint, 0.0F);
        this.rightLeg = new ModelPart(this, 0, 16);
        this.rightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, reduction);
        this.rightLeg.setPos(-1.9F, 12.0F + pivotPoint, 0.0F);
        this.leftLeg = new ModelPart(this, 0, 16);
        this.leftLeg.flipped = true;
        this.leftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, reduction);
        this.leftLeg.setPos(1.9F, 12.0F + pivotPoint, 0.0F);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        GlStateManager.pushMatrix();
        if (this.isBaby) {
            float f = 2.0F;
            GlStateManager.scalef(1.5F / f, 1.5F / f, 1.5F / f);
            GlStateManager.translatef(0.0F, 16.0F * scale, 0.0F);
            this.head.render(scale);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.scalef(1.0F / f, 1.0F / f, 1.0F / f);
            GlStateManager.translatef(0.0F, 24.0F * scale, 0.0F);
            this.body.render(scale);
            this.rightArm.render(scale);
            this.leftArm.render(scale);
            this.rightLeg.render(scale);
            this.leftLeg.render(scale);
            this.hat.render(scale);
        } else {
            if (entity.isSneaking()) {
                GlStateManager.translatef(0.0F, 0.2F, 0.0F);
            }

            this.head.render(scale);
            this.body.render(scale);
            this.rightArm.render(scale);
            this.leftArm.render(scale);
            this.rightLeg.render(scale);
            this.leftLeg.render(scale);
            this.hat.render(scale);
        }

        GlStateManager.popMatrix();
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
        this.rightArm.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F + (float) Math.PI) * 2.0F * walkAnimationSpeed * 0.5F;
        this.leftArm.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F) * 2.0F * walkAnimationSpeed * 0.5F;
        this.rightArm.rotationZ = 0.0F;
        this.leftArm.rotationZ = 0.0F;
        this.rightLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F) * 1.4F * walkAnimationSpeed;
        this.leftLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F + (float) Math.PI) * 1.4F * walkAnimationSpeed;
        this.rightLeg.rotationY = 0.0F;
        this.leftLeg.rotationY = 0.0F;
        if (this.riding) {
            this.rightArm.rotationX += (float) (-Math.PI / 5);
            this.leftArm.rotationX += (float) (-Math.PI / 5);
            this.rightLeg.rotationX = (float) (-Math.PI * 2.0 / 5.0);
            this.leftLeg.rotationX = (float) (-Math.PI * 2.0 / 5.0);
            this.rightLeg.rotationY = (float) (Math.PI / 10);
            this.leftLeg.rotationY = (float) (-Math.PI / 10);
        }

        if (this.itemInLeftHand != 0) {
            this.leftArm.rotationX = this.leftArm.rotationX * 0.5F - (float) (Math.PI / 10) * this.itemInLeftHand;
        }

        this.rightArm.rotationY = 0.0F;
        this.rightArm.rotationZ = 0.0F;
        switch (this.itemInRightHand) {
            case 0:
            case 2:
            default:
                break;
            case 1:
                this.rightArm.rotationX = this.rightArm.rotationX * 0.5F - (float) (Math.PI / 10) * this.itemInRightHand;
                break;
            case 3:
                this.rightArm.rotationX = this.rightArm.rotationX * 0.5F - (float) (Math.PI / 10) * this.itemInRightHand;
                this.rightArm.rotationY = (float) (-Math.PI / 6);
        }

        this.leftArm.rotationY = 0.0F;
        if (this.attackAnimationProgress > -9990.0F) {
            float f = this.attackAnimationProgress;
            this.body.rotationY = MathHelper.sin(MathHelper.sqrt(f) * (float) Math.PI * 2.0F) * 0.2F;
            this.rightArm.z = MathHelper.sin(this.body.rotationY) * 5.0F;
            this.rightArm.x = -MathHelper.cos(this.body.rotationY) * 5.0F;
            this.leftArm.z = -MathHelper.sin(this.body.rotationY) * 5.0F;
            this.leftArm.x = MathHelper.cos(this.body.rotationY) * 5.0F;
            this.rightArm.rotationY = this.rightArm.rotationY + this.body.rotationY;
            this.leftArm.rotationY = this.leftArm.rotationY + this.body.rotationY;
            this.leftArm.rotationX = this.leftArm.rotationX + this.body.rotationY;
            f = 1.0F - this.attackAnimationProgress;
            f *= f;
            f *= f;
            f = 1.0F - f;
            float f1 = MathHelper.sin(f * (float) Math.PI);
            float f2 = MathHelper.sin(this.attackAnimationProgress * (float) Math.PI) * -(this.head.rotationX - 0.7F) * 0.75F;
            this.rightArm.rotationX = (float)(this.rightArm.rotationX - (f1 * 1.2 + f2));
            this.rightArm.rotationY = this.rightArm.rotationY + this.body.rotationY * 2.0F;
            this.rightArm.rotationZ = this.rightArm.rotationZ + MathHelper.sin(this.attackAnimationProgress * (float) Math.PI) * -0.4F;
        }

        if (this.sneaking) {
            this.body.rotationX = 0.5F;
            this.rightArm.rotationX += 0.4F;
            this.leftArm.rotationX += 0.4F;
            this.rightLeg.z = 4.0F;
            this.leftLeg.z = 4.0F;
            this.rightLeg.y = 9.0F;
            this.leftLeg.y = 9.0F;
            this.head.y = 1.0F;
        } else {
            this.body.rotationX = 0.0F;
            this.rightLeg.z = 0.1F;
            this.leftLeg.z = 0.1F;
            this.rightLeg.y = 12.0F;
            this.leftLeg.y = 12.0F;
            this.head.y = 0.0F;
        }

        this.rightArm.rotationZ = this.rightArm.rotationZ + (MathHelper.cos(bob * 0.09F) * 0.05F + 0.05F);
        this.leftArm.rotationZ = this.leftArm.rotationZ - (MathHelper.cos(bob * 0.09F) * 0.05F + 0.05F);
        this.rightArm.rotationX = this.rightArm.rotationX + MathHelper.sin(bob * 0.067F) * 0.05F;
        this.leftArm.rotationX = this.leftArm.rotationX - MathHelper.sin(bob * 0.067F) * 0.05F;
        if (this.aimingBow) {
            float f3 = 0.0F;
            float f4 = 0.0F;
            this.rightArm.rotationZ = 0.0F;
            this.leftArm.rotationZ = 0.0F;
            this.rightArm.rotationY = -(0.1F - f3 * 0.6F) + this.head.rotationY;
            this.leftArm.rotationY = 0.1F - f3 * 0.6F + this.head.rotationY + 0.4F;
            this.rightArm.rotationX = (float) (-Math.PI / 2) + this.head.rotationX;
            this.leftArm.rotationX = (float) (-Math.PI / 2) + this.head.rotationX;
            this.rightArm.rotationX -= f3 * 1.2F - f4 * 0.4F;
            this.leftArm.rotationX -= f3 * 1.2F - f4 * 0.4F;
            this.rightArm.rotationZ = this.rightArm.rotationZ + (MathHelper.cos(bob * 0.09F) * 0.05F + 0.05F);
            this.leftArm.rotationZ = this.leftArm.rotationZ - (MathHelper.cos(bob * 0.09F) * 0.05F + 0.05F);
            this.rightArm.rotationX = this.rightArm.rotationX + MathHelper.sin(bob * 0.067F) * 0.05F;
            this.leftArm.rotationX = this.leftArm.rotationX - MathHelper.sin(bob * 0.067F) * 0.05F;
        }

        copyRotation(this.head, this.hat);
    }

    @Override
    public void copyPropertiesFrom(Model model) {
        super.copyPropertiesFrom(model);
        if (model instanceof HumanoidModel) {
            HumanoidModel humanoidmodel = (HumanoidModel)model;
            this.itemInLeftHand = humanoidmodel.itemInLeftHand;
            this.itemInRightHand = humanoidmodel.itemInRightHand;
            this.sneaking = humanoidmodel.sneaking;
            this.aimingBow = humanoidmodel.aimingBow;
        }
    }

    public void setVisible(boolean visible) {
        this.head.visible = visible;
        this.hat.visible = visible;
        this.body.visible = visible;
        this.rightArm.visible = visible;
        this.leftArm.visible = visible;
        this.rightLeg.visible = visible;
        this.leftLeg.visible = visible;
    }

    public void translateRightArm(float scale) {
        this.rightArm.transform(scale);
    }
}
