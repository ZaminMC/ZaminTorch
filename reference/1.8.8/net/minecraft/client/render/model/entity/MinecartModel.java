package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;

public class MinecartModel extends Model {
    public ModelPart[] parts = new ModelPart[7];

    public MinecartModel() {
        this.parts[0] = new ModelPart(this, 0, 10);
        this.parts[1] = new ModelPart(this, 0, 0);
        this.parts[2] = new ModelPart(this, 0, 0);
        this.parts[3] = new ModelPart(this, 0, 0);
        this.parts[4] = new ModelPart(this, 0, 0);
        this.parts[5] = new ModelPart(this, 44, 10);
        int i = 20;
        int j = 8;
        int k = 16;
        int l = 4;
        this.parts[0].addBox(-i / 2, -k / 2, -1.0F, i, k, 2, 0.0F);
        this.parts[0].setPos(0.0F, l, 0.0F);
        this.parts[5].addBox(-i / 2 + 1, -k / 2 + 1, -1.0F, i - 2, k - 2, 1, 0.0F);
        this.parts[5].setPos(0.0F, l, 0.0F);
        this.parts[1].addBox(-i / 2 + 2, -j - 1, -1.0F, i - 4, j, 2, 0.0F);
        this.parts[1].setPos(-i / 2 + 1, l, 0.0F);
        this.parts[2].addBox(-i / 2 + 2, -j - 1, -1.0F, i - 4, j, 2, 0.0F);
        this.parts[2].setPos(i / 2 - 1, l, 0.0F);
        this.parts[3].addBox(-i / 2 + 2, -j - 1, -1.0F, i - 4, j, 2, 0.0F);
        this.parts[3].setPos(0.0F, l, -k / 2 + 1);
        this.parts[4].addBox(-i / 2 + 2, -j - 1, -1.0F, i - 4, j, 2, 0.0F);
        this.parts[4].setPos(0.0F, l, k / 2 - 1);
        this.parts[0].rotationX = (float) (Math.PI / 2);
        this.parts[1].rotationY = (float) (Math.PI * 3.0 / 2.0);
        this.parts[2].rotationY = (float) (Math.PI / 2);
        this.parts[3].rotationY = (float) Math.PI;
        this.parts[5].rotationX = (float) (-Math.PI / 2);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.parts[5].y = 4.0F - bob;

        for (int i = 0; i < 6; i++) {
            this.parts[i].render(scale);
        }
    }
}
