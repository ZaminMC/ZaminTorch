package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;

public class EnderDragonModel extends Model {
    private ModelPart head;
    private ModelPart neck;
    private ModelPart jaw;
    private ModelPart body;
    private ModelPart rearLeg;
    private ModelPart frontLeg;
    private ModelPart rearLegTip;
    private ModelPart frontLegTip;
    private ModelPart rearFoot;
    private ModelPart frontFoot;
    private ModelPart wing;
    private ModelPart wingTip;
    private float tickDelta;

    public EnderDragonModel(float reduction) {
        this.textureWidth = 256;
        this.textureHeight = 256;
        this.setTexturePos("body.body", 0, 0);
        this.setTexturePos("wing.skin", -56, 88);
        this.setTexturePos("wingtip.skin", -56, 144);
        this.setTexturePos("rearleg.main", 0, 0);
        this.setTexturePos("rearfoot.main", 112, 0);
        this.setTexturePos("rearlegtip.main", 196, 0);
        this.setTexturePos("head.upperhead", 112, 30);
        this.setTexturePos("wing.bone", 112, 88);
        this.setTexturePos("head.upperlip", 176, 44);
        this.setTexturePos("jaw.jaw", 176, 65);
        this.setTexturePos("frontleg.main", 112, 104);
        this.setTexturePos("wingtip.bone", 112, 136);
        this.setTexturePos("frontfoot.main", 144, 104);
        this.setTexturePos("neck.box", 192, 104);
        this.setTexturePos("frontlegtip.main", 226, 138);
        this.setTexturePos("body.scale", 220, 53);
        this.setTexturePos("head.scale", 0, 0);
        this.setTexturePos("neck.scale", 48, 0);
        this.setTexturePos("head.nostril", 112, 0);
        float f = -16.0F;
        this.head = new ModelPart(this, "head");
        this.head.addBox("upperlip", -6.0F, -1.0F, -8.0F + f, 12, 5, 16);
        this.head.addBox("upperhead", -8.0F, -8.0F, 6.0F + f, 16, 16, 16);
        this.head.flipped = true;
        this.head.addBox("scale", -5.0F, -12.0F, 12.0F + f, 2, 4, 6);
        this.head.addBox("nostril", -5.0F, -3.0F, -6.0F + f, 2, 2, 4);
        this.head.flipped = false;
        this.head.addBox("scale", 3.0F, -12.0F, 12.0F + f, 2, 4, 6);
        this.head.addBox("nostril", 3.0F, -3.0F, -6.0F + f, 2, 2, 4);
        this.jaw = new ModelPart(this, "jaw");
        this.jaw.setPos(0.0F, 4.0F, 8.0F + f);
        this.jaw.addBox("jaw", -6.0F, 0.0F, -16.0F, 12, 4, 16);
        this.head.addChild(this.jaw);
        this.neck = new ModelPart(this, "neck");
        this.neck.addBox("box", -5.0F, -5.0F, -5.0F, 10, 10, 10);
        this.neck.addBox("scale", -1.0F, -9.0F, -3.0F, 2, 4, 6);
        this.body = new ModelPart(this, "body");
        this.body.setPos(0.0F, 4.0F, 8.0F);
        this.body.addBox("body", -12.0F, 0.0F, -16.0F, 24, 24, 64);
        this.body.addBox("scale", -1.0F, -6.0F, -10.0F, 2, 6, 12);
        this.body.addBox("scale", -1.0F, -6.0F, 10.0F, 2, 6, 12);
        this.body.addBox("scale", -1.0F, -6.0F, 30.0F, 2, 6, 12);
        this.wing = new ModelPart(this, "wing");
        this.wing.setPos(-12.0F, 5.0F, 2.0F);
        this.wing.addBox("bone", -56.0F, -4.0F, -4.0F, 56, 8, 8);
        this.wing.addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56);
        this.wingTip = new ModelPart(this, "wingtip");
        this.wingTip.setPos(-56.0F, 0.0F, 0.0F);
        this.wingTip.addBox("bone", -56.0F, -2.0F, -2.0F, 56, 4, 4);
        this.wingTip.addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56);
        this.wing.addChild(this.wingTip);
        this.frontLeg = new ModelPart(this, "frontleg");
        this.frontLeg.setPos(-12.0F, 20.0F, 2.0F);
        this.frontLeg.addBox("main", -4.0F, -4.0F, -4.0F, 8, 24, 8);
        this.frontLegTip = new ModelPart(this, "frontlegtip");
        this.frontLegTip.setPos(0.0F, 20.0F, -1.0F);
        this.frontLegTip.addBox("main", -3.0F, -1.0F, -3.0F, 6, 24, 6);
        this.frontLeg.addChild(this.frontLegTip);
        this.frontFoot = new ModelPart(this, "frontfoot");
        this.frontFoot.setPos(0.0F, 23.0F, 0.0F);
        this.frontFoot.addBox("main", -4.0F, 0.0F, -12.0F, 8, 4, 16);
        this.frontLegTip.addChild(this.frontFoot);
        this.rearLeg = new ModelPart(this, "rearleg");
        this.rearLeg.setPos(-16.0F, 16.0F, 42.0F);
        this.rearLeg.addBox("main", -8.0F, -4.0F, -8.0F, 16, 32, 16);
        this.rearLegTip = new ModelPart(this, "rearlegtip");
        this.rearLegTip.setPos(0.0F, 32.0F, -4.0F);
        this.rearLegTip.addBox("main", -6.0F, -2.0F, 0.0F, 12, 32, 12);
        this.rearLeg.addChild(this.rearLegTip);
        this.rearFoot = new ModelPart(this, "rearfoot");
        this.rearFoot.setPos(0.0F, 31.0F, 4.0F);
        this.rearFoot.addBox("main", -9.0F, 0.0F, -20.0F, 18, 6, 24);
        this.rearLegTip.addChild(this.rearFoot);
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
        this.tickDelta = tickDelta;
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        GlStateManager.pushMatrix();
        EnderDragonEntity enderdragonentity = (EnderDragonEntity)entity;
        float f = enderdragonentity.lastWingPosition + (enderdragonentity.wingPosition - enderdragonentity.lastWingPosition) * this.tickDelta;
        this.jaw.rotationX = (float)(Math.sin(f * (float) Math.PI * 2.0F) + 1.0) * 0.2F;
        float f1 = (float)(Math.sin(f * (float) Math.PI * 2.0F - 1.0F) + 1.0);
        f1 = (f1 * f1 * 1.0F + f1 * 2.0F) * 0.05F;
        GlStateManager.translatef(0.0F, f1 - 2.0F, -3.0F);
        GlStateManager.rotatef(f1 * 2.0F, 1.0F, 0.0F, 0.0F);
        float f2 = -30.0F;
        float f4 = 0.0F;
        float f5 = 1.5F;
        double[] adouble = enderdragonentity.getSegmentProperties(6, this.tickDelta);
        float f6 = this.tickRotation(
            enderdragonentity.getSegmentProperties(5, this.tickDelta)[0] - enderdragonentity.getSegmentProperties(10, this.tickDelta)[0]
        );
        float f7 = this.tickRotation(enderdragonentity.getSegmentProperties(5, this.tickDelta)[0] + f6 / 2.0F);
        f2 += 2.0F;
        float f8 = f * (float) Math.PI * 2.0F;
        f2 = 20.0F;
        float f3 = -12.0F;

        for (int i = 0; i < 5; i++) {
            double[] adouble1 = enderdragonentity.getSegmentProperties(5 - i, this.tickDelta);
            float f9 = (float)Math.cos(i * 0.45F + f8) * 0.15F;
            this.neck.rotationY = this.tickRotation(adouble1[0] - adouble[0]) * (float) Math.PI / 180.0F * f5;
            this.neck.rotationX = f9 + (float)(adouble1[1] - adouble[1]) * (float) Math.PI / 180.0F * f5 * 5.0F;
            this.neck.rotationZ = -this.tickRotation(adouble1[0] - f7) * (float) Math.PI / 180.0F * f5;
            this.neck.y = f2;
            this.neck.z = f3;
            this.neck.x = f4;
            f2 = (float)(f2 + Math.sin(this.neck.rotationX) * 10.0);
            f3 = (float)(f3 - Math.cos(this.neck.rotationY) * Math.cos(this.neck.rotationX) * 10.0);
            f4 = (float)(f4 - Math.sin(this.neck.rotationY) * Math.cos(this.neck.rotationX) * 10.0);
            this.neck.render(scale);
        }

        this.head.y = f2;
        this.head.z = f3;
        this.head.x = f4;
        double[] adouble2 = enderdragonentity.getSegmentProperties(0, this.tickDelta);
        this.head.rotationY = this.tickRotation(adouble2[0] - adouble[0]) * (float) Math.PI / 180.0F * 1.0F;
        this.head.rotationZ = -this.tickRotation(adouble2[0] - f7) * (float) Math.PI / 180.0F * 1.0F;
        this.head.render(scale);
        GlStateManager.pushMatrix();
        GlStateManager.translatef(0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-f6 * f5 * 1.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.translatef(0.0F, -1.0F, 0.0F);
        this.body.rotationZ = 0.0F;
        this.body.render(scale);

        for (int j = 0; j < 2; j++) {
            GlStateManager.enableCull();
            float f11 = f * (float) Math.PI * 2.0F;
            this.wing.rotationX = 0.125F - (float)Math.cos(f11) * 0.2F;
            this.wing.rotationY = 0.25F;
            this.wing.rotationZ = (float)(Math.sin(f11) + 0.125) * 0.8F;
            this.wingTip.rotationZ = -((float)(Math.sin(f11 + 2.0F) + 0.5)) * 0.75F;
            this.rearLeg.rotationX = 1.0F + f1 * 0.1F;
            this.rearLegTip.rotationX = 0.5F + f1 * 0.1F;
            this.rearFoot.rotationX = 0.75F + f1 * 0.1F;
            this.frontLeg.rotationX = 1.3F + f1 * 0.1F;
            this.frontLegTip.rotationX = -0.5F - f1 * 0.1F;
            this.frontFoot.rotationX = 0.75F + f1 * 0.1F;
            this.wing.render(scale);
            this.frontLeg.render(scale);
            this.rearLeg.render(scale);
            GlStateManager.scalef(-1.0F, 1.0F, 1.0F);
            if (j == 0) {
                GlStateManager.cullFace(1028);
            }
        }

        GlStateManager.popMatrix();
        GlStateManager.cullFace(1029);
        GlStateManager.disableCull();
        float f10 = -((float)Math.sin(f * (float) Math.PI * 2.0F)) * 0.0F;
        f8 = f * (float) Math.PI * 2.0F;
        f2 = 10.0F;
        f3 = 60.0F;
        f4 = 0.0F;
        adouble = enderdragonentity.getSegmentProperties(11, this.tickDelta);

        for (int k = 0; k < 12; k++) {
            adouble2 = enderdragonentity.getSegmentProperties(12 + k, this.tickDelta);
            f10 = (float)(f10 + Math.sin(k * 0.45F + f8) * 0.05F);
            this.neck.rotationY = (this.tickRotation(adouble2[0] - adouble[0]) * f5 + 180.0F) * (float) Math.PI / 180.0F;
            this.neck.rotationX = f10 + (float)(adouble2[1] - adouble[1]) * (float) Math.PI / 180.0F * f5 * 5.0F;
            this.neck.rotationZ = this.tickRotation(adouble2[0] - f7) * (float) Math.PI / 180.0F * f5;
            this.neck.y = f2;
            this.neck.z = f3;
            this.neck.x = f4;
            f2 = (float)(f2 + Math.sin(this.neck.rotationX) * 10.0);
            f3 = (float)(f3 - Math.cos(this.neck.rotationY) * Math.cos(this.neck.rotationX) * 10.0);
            f4 = (float)(f4 - Math.sin(this.neck.rotationY) * Math.cos(this.neck.rotationX) * 10.0);
            this.neck.render(scale);
        }

        GlStateManager.popMatrix();
    }

    private float tickRotation(double rotation) {
        while (rotation >= 180.0) {
            rotation -= 360.0;
        }

        while (rotation < -180.0) {
            rotation += 360.0;
        }

        return (float)rotation;
    }
}
