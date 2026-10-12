package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.util.math.MathHelper;

public class HorseModel extends Model {
    private ModelPart head;
    private ModelPart topJaw;
    private ModelPart bottomJaw;
    private ModelPart donkeyLeftEar;
    private ModelPart donkeyRightEar;
    private ModelPart muleEarLeft;
    private ModelPart muleEarRight;
    private ModelPart neck;
    /**
     *  <a href="https://en.wikipedia.org/wiki/Halter">Halters</a>  are the ropes around the horse's face.
     */
    private ModelPart halster;
    private ModelPart mane;
    private ModelPart mainBody;
    /**
     * A <a href="https://en.wikipedia.org/wiki/Tail_(horse)">dock</a> is the area where the tail connects to the body of a horse.
     */
    private ModelPart dock;
    private ModelPart tail;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Tail_(horse)">skirt</a> are the long hairs extending down at the tail.
     */
    private ModelPart skirt;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Equine_anatomy#External_anatomy">thigh</a> is the upper part of a horse's back leg.
     */
    private ModelPart leftThigh;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Equine_anatomy#External_anatomy">cannon</a> is the lower part of a horse's leg.
     */
    private ModelPart backLeftCannon;
    private ModelPart backLeftHoof;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Equine_anatomy#External_anatomy">thigh</a> is the upper part of a horse's back leg.
     */
    private ModelPart rightThigh;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Equine_anatomy#External_anatomy">cannon</a> is the lower part of a horse's leg.
     */
    private ModelPart backRightCannon;
    private ModelPart backRightHoof;
    private ModelPart leftForearm;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Equine_anatomy#External_anatomy">cannon</a> is the lower part of a horse's leg.
     */
    private ModelPart frontLeftCannon;
    private ModelPart frontLeftHoof;
    private ModelPart rightForearm;
    private ModelPart frontRightCannon;
    private ModelPart frontRightHoof;
    private ModelPart leftDonkeyChest;
    private ModelPart rightDonkeyChest;
    private ModelPart saddleSeat;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Saddle">gullet</a> is the front part of a horse's saddle.
     */
    private ModelPart gullet;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Saddle">cantle</a> is the back part of a horse's saddle.
     */
    private ModelPart cantle;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Saddle#Parts">fender</a> is the leather strap connecting the saddle to the stirrup..
     */
    private ModelPart leftFender;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Saddle#Parts">stirrup</a> is the metal part in which a horse's rider puts their foot.
     */
    private ModelPart leftStirrup;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Saddle#Parts">fender</a> is the leather strap connecting the saddle to the stirrup..
     */
    private ModelPart rightFender;
    /**
     * The <a href="https://en.wikipedia.org/wiki/Saddle#Parts">stirrup</a> is the metal part in which a horse's rider puts their foot.
     */
    private ModelPart rightStirrup;
    /**
     * The <a href=https://en.wikipedia.org/wiki/Snaffle_bit>snaffle bit</a> are the metal rings to chich one connects the reigns.
     */
    private ModelPart leftSnaffleBit;
    /**
     * The <a href=https://en.wikipedia.org/wiki/Snaffle_bit>snaffle bit</a> are the metal rings to chich one connects the reigns
     */
    private ModelPart rightSnaffleBit;
    private ModelPart leftReign;
    private ModelPart rightReign;

    public HorseModel() {
        this.textureWidth = 128;
        this.textureHeight = 128;
        this.mainBody = new ModelPart(this, 0, 34);
        this.mainBody.addBox(-5.0F, -8.0F, -19.0F, 10, 10, 24);
        this.mainBody.setPos(0.0F, 11.0F, 9.0F);
        this.dock = new ModelPart(this, 44, 0);
        this.dock.addBox(-1.0F, -1.0F, 0.0F, 2, 2, 3);
        this.dock.setPos(0.0F, 3.0F, 14.0F);
        this.setModelRotation(this.dock, -1.134464F, 0.0F, 0.0F);
        this.tail = new ModelPart(this, 38, 7);
        this.tail.addBox(-1.5F, -2.0F, 3.0F, 3, 4, 7);
        this.tail.setPos(0.0F, 3.0F, 14.0F);
        this.setModelRotation(this.tail, -1.134464F, 0.0F, 0.0F);
        this.skirt = new ModelPart(this, 24, 3);
        this.skirt.addBox(-1.5F, -4.5F, 9.0F, 3, 4, 7);
        this.skirt.setPos(0.0F, 3.0F, 14.0F);
        this.setModelRotation(this.skirt, -1.40215F, 0.0F, 0.0F);
        this.leftThigh = new ModelPart(this, 78, 29);
        this.leftThigh.addBox(-2.5F, -2.0F, -2.5F, 4, 9, 5);
        this.leftThigh.setPos(4.0F, 9.0F, 11.0F);
        this.backLeftCannon = new ModelPart(this, 78, 43);
        this.backLeftCannon.addBox(-2.0F, 0.0F, -1.5F, 3, 5, 3);
        this.backLeftCannon.setPos(4.0F, 16.0F, 11.0F);
        this.backLeftHoof = new ModelPart(this, 78, 51);
        this.backLeftHoof.addBox(-2.5F, 5.1F, -2.0F, 4, 3, 4);
        this.backLeftHoof.setPos(4.0F, 16.0F, 11.0F);
        this.rightThigh = new ModelPart(this, 96, 29);
        this.rightThigh.addBox(-1.5F, -2.0F, -2.5F, 4, 9, 5);
        this.rightThigh.setPos(-4.0F, 9.0F, 11.0F);
        this.backRightCannon = new ModelPart(this, 96, 43);
        this.backRightCannon.addBox(-1.0F, 0.0F, -1.5F, 3, 5, 3);
        this.backRightCannon.setPos(-4.0F, 16.0F, 11.0F);
        this.backRightHoof = new ModelPart(this, 96, 51);
        this.backRightHoof.addBox(-1.5F, 5.1F, -2.0F, 4, 3, 4);
        this.backRightHoof.setPos(-4.0F, 16.0F, 11.0F);
        this.leftForearm = new ModelPart(this, 44, 29);
        this.leftForearm.addBox(-1.9F, -1.0F, -2.1F, 3, 8, 4);
        this.leftForearm.setPos(4.0F, 9.0F, -8.0F);
        this.frontLeftCannon = new ModelPart(this, 44, 41);
        this.frontLeftCannon.addBox(-1.9F, 0.0F, -1.6F, 3, 5, 3);
        this.frontLeftCannon.setPos(4.0F, 16.0F, -8.0F);
        this.frontLeftHoof = new ModelPart(this, 44, 51);
        this.frontLeftHoof.addBox(-2.4F, 5.1F, -2.1F, 4, 3, 4);
        this.frontLeftHoof.setPos(4.0F, 16.0F, -8.0F);
        this.rightForearm = new ModelPart(this, 60, 29);
        this.rightForearm.addBox(-1.1F, -1.0F, -2.1F, 3, 8, 4);
        this.rightForearm.setPos(-4.0F, 9.0F, -8.0F);
        this.frontRightCannon = new ModelPart(this, 60, 41);
        this.frontRightCannon.addBox(-1.1F, 0.0F, -1.6F, 3, 5, 3);
        this.frontRightCannon.setPos(-4.0F, 16.0F, -8.0F);
        this.frontRightHoof = new ModelPart(this, 60, 51);
        this.frontRightHoof.addBox(-1.6F, 5.1F, -2.1F, 4, 3, 4);
        this.frontRightHoof.setPos(-4.0F, 16.0F, -8.0F);
        this.head = new ModelPart(this, 0, 0);
        this.head.addBox(-2.5F, -10.0F, -1.5F, 5, 5, 7);
        this.head.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.head, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.topJaw = new ModelPart(this, 24, 18);
        this.topJaw.addBox(-2.0F, -10.0F, -7.0F, 4, 3, 6);
        this.topJaw.setPos(0.0F, 3.95F, -10.0F);
        this.setModelRotation(this.topJaw, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.bottomJaw = new ModelPart(this, 24, 27);
        this.bottomJaw.addBox(-2.0F, -7.0F, -6.5F, 4, 2, 5);
        this.bottomJaw.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.bottomJaw, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.head.addChild(this.topJaw);
        this.head.addChild(this.bottomJaw);
        this.donkeyLeftEar = new ModelPart(this, 0, 0);
        this.donkeyLeftEar.addBox(0.45F, -12.0F, 4.0F, 2, 3, 1);
        this.donkeyLeftEar.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.donkeyLeftEar, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.donkeyRightEar = new ModelPart(this, 0, 0);
        this.donkeyRightEar.addBox(-2.45F, -12.0F, 4.0F, 2, 3, 1);
        this.donkeyRightEar.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.donkeyRightEar, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.muleEarLeft = new ModelPart(this, 0, 12);
        this.muleEarLeft.addBox(-2.0F, -16.0F, 4.0F, 2, 7, 1);
        this.muleEarLeft.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.muleEarLeft, (float) (Math.PI / 6), 0.0F, (float) (Math.PI / 12));
        this.muleEarRight = new ModelPart(this, 0, 12);
        this.muleEarRight.addBox(0.0F, -16.0F, 4.0F, 2, 7, 1);
        this.muleEarRight.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.muleEarRight, (float) (Math.PI / 6), 0.0F, (float) (-Math.PI / 12));
        this.neck = new ModelPart(this, 0, 12);
        this.neck.addBox(-2.05F, -9.8F, -2.0F, 4, 14, 8);
        this.neck.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.neck, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.leftDonkeyChest = new ModelPart(this, 0, 34);
        this.leftDonkeyChest.addBox(-3.0F, 0.0F, 0.0F, 8, 8, 3);
        this.leftDonkeyChest.setPos(-7.5F, 3.0F, 10.0F);
        this.setModelRotation(this.leftDonkeyChest, 0.0F, (float) (Math.PI / 2), 0.0F);
        this.rightDonkeyChest = new ModelPart(this, 0, 47);
        this.rightDonkeyChest.addBox(-3.0F, 0.0F, 0.0F, 8, 8, 3);
        this.rightDonkeyChest.setPos(4.5F, 3.0F, 10.0F);
        this.setModelRotation(this.rightDonkeyChest, 0.0F, (float) (Math.PI / 2), 0.0F);
        this.saddleSeat = new ModelPart(this, 80, 0);
        this.saddleSeat.addBox(-5.0F, 0.0F, -3.0F, 10, 1, 8);
        this.saddleSeat.setPos(0.0F, 2.0F, 2.0F);
        this.gullet = new ModelPart(this, 106, 9);
        this.gullet.addBox(-1.5F, -1.0F, -3.0F, 3, 1, 2);
        this.gullet.setPos(0.0F, 2.0F, 2.0F);
        this.cantle = new ModelPart(this, 80, 9);
        this.cantle.addBox(-4.0F, -1.0F, 3.0F, 8, 1, 2);
        this.cantle.setPos(0.0F, 2.0F, 2.0F);
        this.leftStirrup = new ModelPart(this, 74, 0);
        this.leftStirrup.addBox(-0.5F, 6.0F, -1.0F, 1, 2, 2);
        this.leftStirrup.setPos(5.0F, 3.0F, 2.0F);
        this.leftFender = new ModelPart(this, 70, 0);
        this.leftFender.addBox(-0.5F, 0.0F, -0.5F, 1, 6, 1);
        this.leftFender.setPos(5.0F, 3.0F, 2.0F);
        this.rightStirrup = new ModelPart(this, 74, 4);
        this.rightStirrup.addBox(-0.5F, 6.0F, -1.0F, 1, 2, 2);
        this.rightStirrup.setPos(-5.0F, 3.0F, 2.0F);
        this.rightFender = new ModelPart(this, 80, 0);
        this.rightFender.addBox(-0.5F, 0.0F, -0.5F, 1, 6, 1);
        this.rightFender.setPos(-5.0F, 3.0F, 2.0F);
        this.leftSnaffleBit = new ModelPart(this, 74, 13);
        this.leftSnaffleBit.addBox(1.5F, -8.0F, -4.0F, 1, 2, 2);
        this.leftSnaffleBit.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.leftSnaffleBit, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.rightSnaffleBit = new ModelPart(this, 74, 13);
        this.rightSnaffleBit.addBox(-2.5F, -8.0F, -4.0F, 1, 2, 2);
        this.rightSnaffleBit.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.rightSnaffleBit, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.leftReign = new ModelPart(this, 44, 10);
        this.leftReign.addBox(2.6F, -6.0F, -6.0F, 0, 3, 16);
        this.leftReign.setPos(0.0F, 4.0F, -10.0F);
        this.rightReign = new ModelPart(this, 44, 5);
        this.rightReign.addBox(-2.6F, -6.0F, -6.0F, 0, 3, 16);
        this.rightReign.setPos(0.0F, 4.0F, -10.0F);
        this.mane = new ModelPart(this, 58, 0);
        this.mane.addBox(-1.0F, -11.5F, 5.0F, 2, 16, 4);
        this.mane.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.mane, (float) (Math.PI / 6), 0.0F, 0.0F);
        this.halster = new ModelPart(this, 80, 12);
        this.halster.addBox(-2.5F, -10.1F, -7.0F, 5, 5, 12, 0.2F);
        this.halster.setPos(0.0F, 4.0F, -10.0F);
        this.setModelRotation(this.halster, (float) (Math.PI / 6), 0.0F, 0.0F);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        HorseBaseEntity horsebaseentity = (HorseBaseEntity)entity;
        int i = horsebaseentity.getType();
        float f = horsebaseentity.getGrassAnimationProgress(0.0F);
        boolean flag = horsebaseentity.isOldEnoughForBreeding();
        boolean flag1 = flag && horsebaseentity.isSaddled();
        boolean flag2 = flag && horsebaseentity.hasChest();
        boolean flag3 = i == 1 || i == 2;
        float f1 = horsebaseentity.getSize();
        boolean flag4 = horsebaseentity.rider != null;
        if (flag1) {
            this.halster.render(scale);
            this.saddleSeat.render(scale);
            this.gullet.render(scale);
            this.cantle.render(scale);
            this.leftFender.render(scale);
            this.leftStirrup.render(scale);
            this.rightFender.render(scale);
            this.rightStirrup.render(scale);
            this.leftSnaffleBit.render(scale);
            this.rightSnaffleBit.render(scale);
            if (flag4) {
                this.leftReign.render(scale);
                this.rightReign.render(scale);
            }
        }

        if (!flag) {
            GlStateManager.pushMatrix();
            GlStateManager.scalef(f1, 0.5F + f1 * 0.5F, f1);
            GlStateManager.translatef(0.0F, 0.95F * (1.0F - f1), 0.0F);
        }

        this.leftThigh.render(scale);
        this.backLeftCannon.render(scale);
        this.backLeftHoof.render(scale);
        this.rightThigh.render(scale);
        this.backRightCannon.render(scale);
        this.backRightHoof.render(scale);
        this.leftForearm.render(scale);
        this.frontLeftCannon.render(scale);
        this.frontLeftHoof.render(scale);
        this.rightForearm.render(scale);
        this.frontRightCannon.render(scale);
        this.frontRightHoof.render(scale);
        if (!flag) {
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.scalef(f1, f1, f1);
            GlStateManager.translatef(0.0F, 1.35F * (1.0F - f1), 0.0F);
        }

        this.mainBody.render(scale);
        this.dock.render(scale);
        this.tail.render(scale);
        this.skirt.render(scale);
        this.neck.render(scale);
        this.mane.render(scale);
        if (!flag) {
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            float f2 = 0.5F + f1 * f1 * 0.5F;
            GlStateManager.scalef(f2, f2, f2);
            if (f <= 0.0F) {
                GlStateManager.translatef(0.0F, 1.35F * (1.0F - f1), 0.0F);
            } else {
                GlStateManager.translatef(0.0F, 0.9F * (1.0F - f1) * f + 1.35F * (1.0F - f1) * (1.0F - f), 0.15F * (1.0F - f1) * f);
            }
        }

        if (flag3) {
            this.muleEarLeft.render(scale);
            this.muleEarRight.render(scale);
        } else {
            this.donkeyLeftEar.render(scale);
            this.donkeyRightEar.render(scale);
        }

        this.head.render(scale);
        if (!flag) {
            GlStateManager.popMatrix();
        }

        if (flag2) {
            this.leftDonkeyChest.render(scale);
            this.rightDonkeyChest.render(scale);
        }
    }

    private void setModelRotation(ModelPart model, float rotationX, float rotationY, float rotationZ) {
        model.rotationX = rotationX;
        model.rotationY = rotationY;
        model.rotationZ = rotationZ;
    }

    private float tickRotation(float prevBodyYaw, float bodyYaw, float tickdelta) {
        float f = bodyYaw - prevBodyYaw;

        while (f < -180.0F) {
            f += 360.0F;
        }

        while (f >= 180.0F) {
            f -= 360.0F;
        }

        return prevBodyYaw + tickdelta * f;
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
        super.prepare(mob, walkAnimationProgress, walkAnimationSpeed, tickDelta);
        float f = this.tickRotation(mob.lastBodyYaw, mob.bodyYaw, tickDelta);
        float f1 = this.tickRotation(mob.lastHeadYaw, mob.headYaw, tickDelta);
        float f2 = mob.lastPitch + (mob.pitch - mob.lastPitch) * tickDelta;
        float f3 = f1 - f;
        float f4 = f2 / (180.0F / (float)Math.PI);
        if (f3 > 20.0F) {
            f3 = 20.0F;
        }

        if (f3 < -20.0F) {
            f3 = -20.0F;
        }

        if (walkAnimationSpeed > 0.2F) {
            f4 += MathHelper.cos(walkAnimationProgress * 0.4F) * 0.15F * walkAnimationSpeed;
        }

        HorseBaseEntity horsebaseentity = (HorseBaseEntity)mob;
        float f5 = horsebaseentity.getGrassAnimationProgress(tickDelta);
        float f6 = horsebaseentity.getAngryAnimationProgress(tickDelta);
        float f7 = 1.0F - f6;
        float f8 = horsebaseentity.getEatingAnimationProgress(tickDelta);
        boolean flag = horsebaseentity.type != 0;
        boolean flag1 = horsebaseentity.isSaddled();
        boolean flag2 = horsebaseentity.rider != null;
        float f9 = mob.ticks + tickDelta;
        float f10 = MathHelper.cos(walkAnimationProgress * 0.6662F + (float) Math.PI);
        float f11 = f10 * 0.8F * walkAnimationSpeed;
        this.head.y = 4.0F;
        this.head.z = -10.0F;
        this.dock.y = 3.0F;
        this.tail.z = 14.0F;
        this.rightDonkeyChest.y = 3.0F;
        this.rightDonkeyChest.z = 10.0F;
        this.mainBody.rotationX = 0.0F;
        this.head.rotationX = (float) (Math.PI / 6) + f4;
        this.head.rotationY = f3 / (180.0F / (float)Math.PI);
        this.head.rotationX = f6 * ((float) (Math.PI / 12) + f4) + f5 * 2.18166F + (1.0F - Math.max(f6, f5)) * this.head.rotationX;
        this.head.rotationY = f6 * f3 / (180.0F / (float)Math.PI) + (1.0F - Math.max(f6, f5)) * this.head.rotationY;
        this.head.y = f6 * -6.0F + f5 * 11.0F + (1.0F - Math.max(f6, f5)) * this.head.y;
        this.head.z = f6 * -1.0F + f5 * -10.0F + (1.0F - Math.max(f6, f5)) * this.head.z;
        this.dock.y = f6 * 9.0F + f7 * this.dock.y;
        this.tail.z = f6 * 18.0F + f7 * this.tail.z;
        this.rightDonkeyChest.y = f6 * 5.5F + f7 * this.rightDonkeyChest.y;
        this.rightDonkeyChest.z = f6 * 15.0F + f7 * this.rightDonkeyChest.z;
        this.mainBody.rotationX = f6 * -45.0F / (180.0F / (float)Math.PI) + f7 * this.mainBody.rotationX;
        this.donkeyLeftEar.y = this.head.y;
        this.donkeyRightEar.y = this.head.y;
        this.muleEarLeft.y = this.head.y;
        this.muleEarRight.y = this.head.y;
        this.neck.y = this.head.y;
        this.topJaw.y = 0.02F;
        this.bottomJaw.y = 0.0F;
        this.mane.y = this.head.y;
        this.donkeyLeftEar.z = this.head.z;
        this.donkeyRightEar.z = this.head.z;
        this.muleEarLeft.z = this.head.z;
        this.muleEarRight.z = this.head.z;
        this.neck.z = this.head.z;
        this.topJaw.z = 0.02F - f8 * 1.0F;
        this.bottomJaw.z = 0.0F + f8 * 1.0F;
        this.mane.z = this.head.z;
        this.donkeyLeftEar.rotationX = this.head.rotationX;
        this.donkeyRightEar.rotationX = this.head.rotationX;
        this.muleEarLeft.rotationX = this.head.rotationX;
        this.muleEarRight.rotationX = this.head.rotationX;
        this.neck.rotationX = this.head.rotationX;
        this.topJaw.rotationX = 0.0F - 0.09424778F * f8;
        this.bottomJaw.rotationX = 0.0F + (float) (Math.PI / 20) * f8;
        this.mane.rotationX = this.head.rotationX;
        this.donkeyLeftEar.rotationY = this.head.rotationY;
        this.donkeyRightEar.rotationY = this.head.rotationY;
        this.muleEarLeft.rotationY = this.head.rotationY;
        this.muleEarRight.rotationY = this.head.rotationY;
        this.neck.rotationY = this.head.rotationY;
        this.topJaw.rotationY = 0.0F;
        this.bottomJaw.rotationY = 0.0F;
        this.mane.rotationY = this.head.rotationY;
        this.leftDonkeyChest.rotationX = f11 / 5.0F;
        this.rightDonkeyChest.rotationX = -f11 / 5.0F;
        float f12 = (float) (Math.PI / 2);
        float f13 = (float) (Math.PI * 3.0 / 2.0);
        float f14 = (float) (-Math.PI / 3);
        float f15 = (float) (Math.PI / 12) * f6;
        float f16 = MathHelper.cos(f9 * 0.6F + (float) Math.PI);
        this.leftForearm.y = -2.0F * f6 + 9.0F * f7;
        this.leftForearm.z = -2.0F * f6 + -8.0F * f7;
        this.rightForearm.y = this.leftForearm.y;
        this.rightForearm.z = this.leftForearm.z;
        this.backLeftCannon.y = this.leftThigh.y + MathHelper.sin((float) (Math.PI / 2) + f15 + f7 * -f10 * 0.5F * walkAnimationSpeed) * 7.0F;
        this.backLeftCannon.z = this.leftThigh.z + MathHelper.cos((float) (Math.PI * 3.0 / 2.0) + f15 + f7 * -f10 * 0.5F * walkAnimationSpeed) * 7.0F;
        this.backRightCannon.y = this.rightThigh.y + MathHelper.sin((float) (Math.PI / 2) + f15 + f7 * f10 * 0.5F * walkAnimationSpeed) * 7.0F;
        this.backRightCannon.z = this.rightThigh.z + MathHelper.cos((float) (Math.PI * 3.0 / 2.0) + f15 + f7 * f10 * 0.5F * walkAnimationSpeed) * 7.0F;
        float f17 = ((float) (-Math.PI / 3) + f16) * f6 + f11 * f7;
        float f18 = ((float) (-Math.PI / 3) + -f16) * f6 + -f11 * f7;
        this.frontLeftCannon.y = this.leftForearm.y + MathHelper.sin((float) (Math.PI / 2) + f17) * 7.0F;
        this.frontLeftCannon.z = this.leftForearm.z + MathHelper.cos((float) (Math.PI * 3.0 / 2.0) + f17) * 7.0F;
        this.frontRightCannon.y = this.rightForearm.y + MathHelper.sin((float) (Math.PI / 2) + f18) * 7.0F;
        this.frontRightCannon.z = this.rightForearm.z + MathHelper.cos((float) (Math.PI * 3.0 / 2.0) + f18) * 7.0F;
        this.leftThigh.rotationX = f15 + -f10 * 0.5F * walkAnimationSpeed * f7;
        this.backLeftCannon.rotationX = -0.08726646F * f6 + (-f10 * 0.5F * walkAnimationSpeed - Math.max(0.0F, f10 * 0.5F * walkAnimationSpeed)) * f7;
        this.backLeftHoof.rotationX = this.backLeftCannon.rotationX;
        this.rightThigh.rotationX = f15 + f10 * 0.5F * walkAnimationSpeed * f7;
        this.backRightCannon.rotationX = -0.08726646F * f6 + (f10 * 0.5F * walkAnimationSpeed - Math.max(0.0F, -f10 * 0.5F * walkAnimationSpeed)) * f7;
        this.backRightHoof.rotationX = this.backRightCannon.rotationX;
        this.leftForearm.rotationX = f17;
        this.frontLeftCannon.rotationX = (this.leftForearm.rotationX + (float) Math.PI * Math.max(0.0F, 0.2F + f16 * 0.2F)) * f6
            + (f11 + Math.max(0.0F, f10 * 0.5F * walkAnimationSpeed)) * f7;
        this.frontLeftHoof.rotationX = this.frontLeftCannon.rotationX;
        this.rightForearm.rotationX = f18;
        this.frontRightCannon.rotationX = (this.rightForearm.rotationX + (float) Math.PI * Math.max(0.0F, 0.2F - f16 * 0.2F)) * f6
            + (-f11 + Math.max(0.0F, -f10 * 0.5F * walkAnimationSpeed)) * f7;
        this.frontRightHoof.rotationX = this.frontRightCannon.rotationX;
        this.backLeftHoof.y = this.backLeftCannon.y;
        this.backLeftHoof.z = this.backLeftCannon.z;
        this.backRightHoof.y = this.backRightCannon.y;
        this.backRightHoof.z = this.backRightCannon.z;
        this.frontLeftHoof.y = this.frontLeftCannon.y;
        this.frontLeftHoof.z = this.frontLeftCannon.z;
        this.frontRightHoof.y = this.frontRightCannon.y;
        this.frontRightHoof.z = this.frontRightCannon.z;
        if (flag1) {
            this.saddleSeat.y = f6 * 0.5F + f7 * 2.0F;
            this.saddleSeat.z = f6 * 11.0F + f7 * 2.0F;
            this.gullet.y = this.saddleSeat.y;
            this.cantle.y = this.saddleSeat.y;
            this.leftFender.y = this.saddleSeat.y;
            this.rightFender.y = this.saddleSeat.y;
            this.leftStirrup.y = this.saddleSeat.y;
            this.rightStirrup.y = this.saddleSeat.y;
            this.leftDonkeyChest.y = this.rightDonkeyChest.y;
            this.gullet.z = this.saddleSeat.z;
            this.cantle.z = this.saddleSeat.z;
            this.leftFender.z = this.saddleSeat.z;
            this.rightFender.z = this.saddleSeat.z;
            this.leftStirrup.z = this.saddleSeat.z;
            this.rightStirrup.z = this.saddleSeat.z;
            this.leftDonkeyChest.z = this.rightDonkeyChest.z;
            this.saddleSeat.rotationX = this.mainBody.rotationX;
            this.gullet.rotationX = this.mainBody.rotationX;
            this.cantle.rotationX = this.mainBody.rotationX;
            this.leftReign.y = this.head.y;
            this.rightReign.y = this.head.y;
            this.halster.y = this.head.y;
            this.leftSnaffleBit.y = this.head.y;
            this.rightSnaffleBit.y = this.head.y;
            this.leftReign.z = this.head.z;
            this.rightReign.z = this.head.z;
            this.halster.z = this.head.z;
            this.leftSnaffleBit.z = this.head.z;
            this.rightSnaffleBit.z = this.head.z;
            this.leftReign.rotationX = f4;
            this.rightReign.rotationX = f4;
            this.halster.rotationX = this.head.rotationX;
            this.leftSnaffleBit.rotationX = this.head.rotationX;
            this.rightSnaffleBit.rotationX = this.head.rotationX;
            this.halster.rotationY = this.head.rotationY;
            this.leftSnaffleBit.rotationY = this.head.rotationY;
            this.leftReign.rotationY = this.head.rotationY;
            this.rightSnaffleBit.rotationY = this.head.rotationY;
            this.rightReign.rotationY = this.head.rotationY;
            if (flag2) {
                this.leftFender.rotationX = (float) (-Math.PI / 3);
                this.leftStirrup.rotationX = (float) (-Math.PI / 3);
                this.rightFender.rotationX = (float) (-Math.PI / 3);
                this.rightStirrup.rotationX = (float) (-Math.PI / 3);
                this.leftFender.rotationZ = 0.0F;
                this.leftStirrup.rotationZ = 0.0F;
                this.rightFender.rotationZ = 0.0F;
                this.rightStirrup.rotationZ = 0.0F;
            } else {
                this.leftFender.rotationX = f11 / 3.0F;
                this.leftStirrup.rotationX = f11 / 3.0F;
                this.rightFender.rotationX = f11 / 3.0F;
                this.rightStirrup.rotationX = f11 / 3.0F;
                this.leftFender.rotationZ = f11 / 5.0F;
                this.leftStirrup.rotationZ = f11 / 5.0F;
                this.rightFender.rotationZ = -f11 / 5.0F;
                this.rightStirrup.rotationZ = -f11 / 5.0F;
            }
        }

        f12 = -1.3089F + walkAnimationSpeed * 1.5F;
        if (f12 > 0.0F) {
            f12 = 0.0F;
        }

        if (flag) {
            this.dock.rotationY = MathHelper.cos(f9 * 0.7F);
            f12 = 0.0F;
        } else {
            this.dock.rotationY = 0.0F;
        }

        this.tail.rotationY = this.dock.rotationY;
        this.skirt.rotationY = this.dock.rotationY;
        this.tail.y = this.dock.y;
        this.skirt.y = this.dock.y;
        this.tail.z = this.dock.z;
        this.skirt.z = this.dock.z;
        this.dock.rotationX = f12;
        this.tail.rotationX = f12;
        this.skirt.rotationX = -0.2618F + f12;
    }
}
