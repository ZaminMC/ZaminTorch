package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class EndermiteModel extends Model {
    private static final int[][] PART_SIZES = new int[][]{{4, 3, 2}, {6, 4, 5}, {3, 3, 1}, {1, 2, 1}};
    private static final int[][] PART_TEXTURE_COORDS = new int[][]{{0, 0}, {0, 5}, {0, 14}, {0, 18}};
    private static final int PART_COUNT = PART_SIZES.length;
    private final ModelPart[] parts = new ModelPart[PART_COUNT];

    public EndermiteModel() {
        float f = -3.5F;

        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i] = new ModelPart(this, PART_TEXTURE_COORDS[i][0], PART_TEXTURE_COORDS[i][1]);
            this.parts[i].addBox(PART_SIZES[i][0] * -0.5F, 0.0F, PART_SIZES[i][2] * -0.5F, PART_SIZES[i][0], PART_SIZES[i][1], PART_SIZES[i][2]);
            this.parts[i].setPos(0.0F, 24 - PART_SIZES[i][1], f);
            if (i < this.parts.length - 1) {
                f += (PART_SIZES[i][2] + PART_SIZES[i + 1][2]) * 0.5F;
            }
        }
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);

        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].render(scale);
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].rotationY = MathHelper.cos(bob * 0.9F + i * 0.15F * (float) Math.PI) * (float) Math.PI * 0.01F * (1 + Math.abs(i - 2));
            this.parts[i].x = MathHelper.sin(bob * 0.9F + i * 0.15F * (float) Math.PI) * (float) Math.PI * 0.1F * Math.abs(i - 2);
        }
    }
}
