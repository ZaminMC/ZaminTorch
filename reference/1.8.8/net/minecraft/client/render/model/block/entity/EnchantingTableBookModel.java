package net.minecraft.client.render.model.block.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class EnchantingTableBookModel extends Model {
    public ModelPart leftCover = new ModelPart(this).setTextureCoords(0, 0).addBox(-6.0F, -5.0F, 0.0F, 6, 10, 0);
    public ModelPart rightCover = new ModelPart(this).setTextureCoords(16, 0).addBox(0.0F, -5.0F, 0.0F, 6, 10, 0);
    public ModelPart leftPage;
    public ModelPart rightPage;
    public ModelPart leftFlippîngPage;
    public ModelPart rightFlippingPage;
    public ModelPart spine = new ModelPart(this).setTextureCoords(12, 0).addBox(-1.0F, -5.0F, 0.0F, 2, 10, 0);

    public EnchantingTableBookModel() {
        this.leftPage = new ModelPart(this).setTextureCoords(0, 10).addBox(0.0F, -4.0F, -0.99F, 5, 8, 1);
        this.rightPage = new ModelPart(this).setTextureCoords(12, 10).addBox(0.0F, -4.0F, -0.01F, 5, 8, 1);
        this.leftFlippîngPage = new ModelPart(this).setTextureCoords(24, 10).addBox(0.0F, -4.0F, 0.0F, 5, 8, 0);
        this.rightFlippingPage = new ModelPart(this).setTextureCoords(24, 10).addBox(0.0F, -4.0F, 0.0F, 5, 8, 0);
        this.leftCover.setPos(0.0F, 0.0F, -1.0F);
        this.rightCover.setPos(0.0F, 0.0F, 1.0F);
        this.spine.rotationY = (float) (Math.PI / 2);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.leftCover.render(scale);
        this.rightCover.render(scale);
        this.spine.render(scale);
        this.leftPage.render(scale);
        this.rightPage.render(scale);
        this.leftFlippîngPage.render(scale);
        this.rightFlippingPage.render(scale);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        float f = (MathHelper.sin(walkAnimationProgress * 0.02F) * 0.1F + 1.25F) * yaw;
        this.leftCover.rotationY = (float) Math.PI + f;
        this.rightCover.rotationY = -f;
        this.leftPage.rotationY = f;
        this.rightPage.rotationY = -f;
        this.leftFlippîngPage.rotationY = f - f * 2.0F * walkAnimationSpeed;
        this.rightFlippingPage.rotationY = f - f * 2.0F * bob;
        this.leftPage.x = MathHelper.sin(f);
        this.rightPage.x = MathHelper.sin(f);
        this.leftFlippîngPage.x = MathHelper.sin(f);
        this.rightFlippingPage.x = MathHelper.sin(f);
    }
}
