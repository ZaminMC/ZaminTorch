package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class SpiderModel extends Model {
    public ModelPart head;
    public ModelPart neck;
    public ModelPart body;
    public ModelPart backRightLeg;
    public ModelPart backLeftLeg;
    public ModelPart backMiddleRightLeg;
    public ModelPart backMiddleLeftLeg;
    public ModelPart frontMiddleRightLeg;
    public ModelPart frontMiddleLeftLeg;
    public ModelPart frontRightLeg;
    public ModelPart frontLeftLeg;

    public SpiderModel() {
        float f = 0.0F;
        int i = 15;
        this.head = new ModelPart(this, 32, 4);
        this.head.addBox(-4.0F, -4.0F, -8.0F, 8, 8, 8, f);
        this.head.setPos(0.0F, i, -3.0F);
        this.neck = new ModelPart(this, 0, 0);
        this.neck.addBox(-3.0F, -3.0F, -3.0F, 6, 6, 6, f);
        this.neck.setPos(0.0F, i, 0.0F);
        this.body = new ModelPart(this, 0, 12);
        this.body.addBox(-5.0F, -4.0F, -6.0F, 10, 8, 12, f);
        this.body.setPos(0.0F, i, 9.0F);
        this.backRightLeg = new ModelPart(this, 18, 0);
        this.backRightLeg.addBox(-15.0F, -1.0F, -1.0F, 16, 2, 2, f);
        this.backRightLeg.setPos(-4.0F, i, 2.0F);
        this.backLeftLeg = new ModelPart(this, 18, 0);
        this.backLeftLeg.addBox(-1.0F, -1.0F, -1.0F, 16, 2, 2, f);
        this.backLeftLeg.setPos(4.0F, i, 2.0F);
        this.backMiddleRightLeg = new ModelPart(this, 18, 0);
        this.backMiddleRightLeg.addBox(-15.0F, -1.0F, -1.0F, 16, 2, 2, f);
        this.backMiddleRightLeg.setPos(-4.0F, i, 1.0F);
        this.backMiddleLeftLeg = new ModelPart(this, 18, 0);
        this.backMiddleLeftLeg.addBox(-1.0F, -1.0F, -1.0F, 16, 2, 2, f);
        this.backMiddleLeftLeg.setPos(4.0F, i, 1.0F);
        this.frontMiddleRightLeg = new ModelPart(this, 18, 0);
        this.frontMiddleRightLeg.addBox(-15.0F, -1.0F, -1.0F, 16, 2, 2, f);
        this.frontMiddleRightLeg.setPos(-4.0F, i, 0.0F);
        this.frontMiddleLeftLeg = new ModelPart(this, 18, 0);
        this.frontMiddleLeftLeg.addBox(-1.0F, -1.0F, -1.0F, 16, 2, 2, f);
        this.frontMiddleLeftLeg.setPos(4.0F, i, 0.0F);
        this.frontRightLeg = new ModelPart(this, 18, 0);
        this.frontRightLeg.addBox(-15.0F, -1.0F, -1.0F, 16, 2, 2, f);
        this.frontRightLeg.setPos(-4.0F, i, -1.0F);
        this.frontLeftLeg = new ModelPart(this, 18, 0);
        this.frontLeftLeg.addBox(-1.0F, -1.0F, -1.0F, 16, 2, 2, f);
        this.frontLeftLeg.setPos(4.0F, i, -1.0F);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.render(scale);
        this.neck.render(scale);
        this.body.render(scale);
        this.backRightLeg.render(scale);
        this.backLeftLeg.render(scale);
        this.backMiddleRightLeg.render(scale);
        this.backMiddleLeftLeg.render(scale);
        this.frontMiddleRightLeg.render(scale);
        this.frontMiddleLeftLeg.render(scale);
        this.frontRightLeg.render(scale);
        this.frontLeftLeg.render(scale);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
        float f = (float) (Math.PI / 4);
        this.backRightLeg.rotationZ = -f;
        this.backLeftLeg.rotationZ = f;
        this.backMiddleRightLeg.rotationZ = -f * 0.74F;
        this.backMiddleLeftLeg.rotationZ = f * 0.74F;
        this.frontMiddleRightLeg.rotationZ = -f * 0.74F;
        this.frontMiddleLeftLeg.rotationZ = f * 0.74F;
        this.frontRightLeg.rotationZ = -f;
        this.frontLeftLeg.rotationZ = f;
        float f1 = -0.0F;
        float f2 = (float) (Math.PI / 8);
        this.backRightLeg.rotationY = f2 * 2.0F + f1;
        this.backLeftLeg.rotationY = -f2 * 2.0F - f1;
        this.backMiddleRightLeg.rotationY = f2 * 1.0F + f1;
        this.backMiddleLeftLeg.rotationY = -f2 * 1.0F - f1;
        this.frontMiddleRightLeg.rotationY = -f2 * 1.0F + f1;
        this.frontMiddleLeftLeg.rotationY = f2 * 1.0F - f1;
        this.frontRightLeg.rotationY = -f2 * 2.0F + f1;
        this.frontLeftLeg.rotationY = f2 * 2.0F - f1;
        float f3 = -(MathHelper.cos(walkAnimationProgress * 0.6662F * 2.0F + 0.0F) * 0.4F) * walkAnimationSpeed;
        float f4 = -(MathHelper.cos(walkAnimationProgress * 0.6662F * 2.0F + (float) Math.PI) * 0.4F) * walkAnimationSpeed;
        float f5 = -(MathHelper.cos(walkAnimationProgress * 0.6662F * 2.0F + (float) (Math.PI / 2)) * 0.4F) * walkAnimationSpeed;
        float f6 = -(MathHelper.cos(walkAnimationProgress * 0.6662F * 2.0F + (float) (Math.PI * 3.0 / 2.0)) * 0.4F) * walkAnimationSpeed;
        float f7 = Math.abs(MathHelper.sin(walkAnimationProgress * 0.6662F + 0.0F) * 0.4F) * walkAnimationSpeed;
        float f8 = Math.abs(MathHelper.sin(walkAnimationProgress * 0.6662F + (float) Math.PI) * 0.4F) * walkAnimationSpeed;
        float f9 = Math.abs(MathHelper.sin(walkAnimationProgress * 0.6662F + (float) (Math.PI / 2)) * 0.4F) * walkAnimationSpeed;
        float f10 = Math.abs(MathHelper.sin(walkAnimationProgress * 0.6662F + (float) (Math.PI * 3.0 / 2.0)) * 0.4F) * walkAnimationSpeed;
        this.backRightLeg.rotationY += f3;
        this.backLeftLeg.rotationY += -f3;
        this.backMiddleRightLeg.rotationY += f4;
        this.backMiddleLeftLeg.rotationY += -f4;
        this.frontMiddleRightLeg.rotationY += f5;
        this.frontMiddleLeftLeg.rotationY += -f5;
        this.frontRightLeg.rotationY += f6;
        this.frontLeftLeg.rotationY += -f6;
        this.backRightLeg.rotationZ += f7;
        this.backLeftLeg.rotationZ += -f7;
        this.backMiddleRightLeg.rotationZ += f8;
        this.backMiddleLeftLeg.rotationZ += -f8;
        this.frontMiddleRightLeg.rotationZ += f9;
        this.frontMiddleLeftLeg.rotationZ += -f9;
        this.frontRightLeg.rotationZ += f10;
        this.frontLeftLeg.rotationZ += -f10;
    }
}
