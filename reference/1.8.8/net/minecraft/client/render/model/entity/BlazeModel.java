package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class BlazeModel extends Model {
    private ModelPart[] rods = new ModelPart[12];
    private ModelPart head;

    public BlazeModel() {
        for (int i = 0; i < this.rods.length; i++) {
            this.rods[i] = new ModelPart(this, 0, 16);
            this.rods[i].addBox(0.0F, 0.0F, 0.0F, 2, 8, 2);
        }

        this.head = new ModelPart(this, 0, 0);
        this.head.addBox(-4.0F, -4.0F, -4.0F, 8, 8, 8);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.render(scale);

        for (int i = 0; i < this.rods.length; i++) {
            this.rods[i].render(scale);
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        float f = bob * (float) Math.PI * -0.1F;

        for (int i = 0; i < 4; i++) {
            this.rods[i].y = -2.0F + MathHelper.cos((i * 2 + bob) * 0.25F);
            this.rods[i].x = MathHelper.cos(f) * 9.0F;
            this.rods[i].z = MathHelper.sin(f) * 9.0F;
            f += (float) (Math.PI / 2);
        }

        f = (float) (Math.PI / 4) + bob * (float) Math.PI * 0.03F;

        for (int j = 4; j < 8; j++) {
            this.rods[j].y = 2.0F + MathHelper.cos((j * 2 + bob) * 0.25F);
            this.rods[j].x = MathHelper.cos(f) * 7.0F;
            this.rods[j].z = MathHelper.sin(f) * 7.0F;
            f += (float) (Math.PI / 2);
        }

        f = 0.47123894F + bob * (float) Math.PI * -0.05F;

        for (int k = 8; k < 12; k++) {
            this.rods[k].y = 11.0F + MathHelper.cos((k * 1.5F + bob) * 0.5F);
            this.rods[k].x = MathHelper.cos(f) * 5.0F;
            this.rods[k].z = MathHelper.sin(f) * 5.0F;
            f += (float) (Math.PI / 2);
        }

        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
    }
}
