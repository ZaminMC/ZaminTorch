package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;

public class PlayerModel extends HumanoidModel {
    public ModelPart leftSleeve;
    public ModelPart rightSleeve;
    public ModelPart leftPants;
    public ModelPart rightPants;
    public ModelPart jacket;
    private ModelPart cape;
    private ModelPart ears;
    private boolean thinArms;

    public PlayerModel(float reduction, boolean thinArms) {
        super(reduction, 0.0F, 64, 64);
        this.thinArms = thinArms;
        this.ears = new ModelPart(this, 24, 0);
        this.ears.addBox(-3.0F, -6.0F, -1.0F, 6, 6, 1, reduction);
        this.cape = new ModelPart(this, 0, 0);
        this.cape.setTextureSize(64, 32);
        this.cape.addBox(-5.0F, 0.0F, -1.0F, 10, 16, 1, reduction);
        if (thinArms) {
            this.leftArm = new ModelPart(this, 32, 48);
            this.leftArm.addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, reduction);
            this.leftArm.setPos(5.0F, 2.5F, 0.0F);
            this.rightArm = new ModelPart(this, 40, 16);
            this.rightArm.addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, reduction);
            this.rightArm.setPos(-5.0F, 2.5F, 0.0F);
            this.leftSleeve = new ModelPart(this, 48, 48);
            this.leftSleeve.addBox(-1.0F, -2.0F, -2.0F, 3, 12, 4, reduction + 0.25F);
            this.leftSleeve.setPos(5.0F, 2.5F, 0.0F);
            this.rightSleeve = new ModelPart(this, 40, 32);
            this.rightSleeve.addBox(-2.0F, -2.0F, -2.0F, 3, 12, 4, reduction + 0.25F);
            this.rightSleeve.setPos(-5.0F, 2.5F, 10.0F);
        } else {
            this.leftArm = new ModelPart(this, 32, 48);
            this.leftArm.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, reduction);
            this.leftArm.setPos(5.0F, 2.0F, 0.0F);
            this.leftSleeve = new ModelPart(this, 48, 48);
            this.leftSleeve.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, reduction + 0.25F);
            this.leftSleeve.setPos(5.0F, 2.0F, 0.0F);
            this.rightSleeve = new ModelPart(this, 40, 32);
            this.rightSleeve.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, reduction + 0.25F);
            this.rightSleeve.setPos(-5.0F, 2.0F, 10.0F);
        }

        this.leftLeg = new ModelPart(this, 16, 48);
        this.leftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, reduction);
        this.leftLeg.setPos(1.9F, 12.0F, 0.0F);
        this.leftPants = new ModelPart(this, 0, 48);
        this.leftPants.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, reduction + 0.25F);
        this.leftPants.setPos(1.9F, 12.0F, 0.0F);
        this.rightPants = new ModelPart(this, 0, 32);
        this.rightPants.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, reduction + 0.25F);
        this.rightPants.setPos(-1.9F, 12.0F, 0.0F);
        this.jacket = new ModelPart(this, 16, 32);
        this.jacket.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, reduction + 0.25F);
        this.jacket.setPos(0.0F, 0.0F, 0.0F);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        super.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
        GlStateManager.pushMatrix();
        if (this.isBaby) {
            float f = 2.0F;
            GlStateManager.scalef(1.0F / f, 1.0F / f, 1.0F / f);
            GlStateManager.translatef(0.0F, 24.0F * scale, 0.0F);
            this.leftPants.render(scale);
            this.rightPants.render(scale);
            this.leftSleeve.render(scale);
            this.rightSleeve.render(scale);
            this.jacket.render(scale);
        } else {
            if (entity.isSneaking()) {
                GlStateManager.translatef(0.0F, 0.2F, 0.0F);
            }

            this.leftPants.render(scale);
            this.rightPants.render(scale);
            this.leftSleeve.render(scale);
            this.rightSleeve.render(scale);
            this.jacket.render(scale);
        }

        GlStateManager.popMatrix();
    }

    public void renderEars(float tickDelta) {
        copyRotation(this.head, this.ears);
        this.ears.x = 0.0F;
        this.ears.y = 0.0F;
        this.ears.render(tickDelta);
    }

    public void renderCape(float tickDelta) {
        this.cape.render(tickDelta);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        copyRotation(this.leftLeg, this.leftPants);
        copyRotation(this.rightLeg, this.rightPants);
        copyRotation(this.leftArm, this.leftSleeve);
        copyRotation(this.rightArm, this.rightSleeve);
        copyRotation(this.body, this.jacket);
        if (entity.isSneaking()) {
            this.cape.y = 2.0F;
        } else {
            this.cape.y = 0.0F;
        }
    }

    public void renderRightArm() {
        this.rightArm.render(0.0625F);
        this.rightSleeve.render(0.0625F);
    }

    public void renderLeftArm() {
        this.leftArm.render(0.0625F);
        this.leftSleeve.render(0.0625F);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        this.leftSleeve.visible = visible;
        this.rightSleeve.visible = visible;
        this.leftPants.visible = visible;
        this.rightPants.visible = visible;
        this.jacket.visible = visible;
        this.cape.visible = visible;
        this.ears.visible = visible;
    }

    @Override
    public void translateRightArm(float scale) {
        if (this.thinArms) {
            this.rightArm.x++;
            this.rightArm.transform(scale);
            this.rightArm.x--;
        } else {
            this.rightArm.transform(scale);
        }
    }
}
