package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class SilverfishModel extends Model {
    private ModelPart[] body;
    private ModelPart[] scales;
    private float[] radii = new float[7];
    private static final int[][] SEGMENT_LOCATIONS = new int[][]{{3, 2, 2}, {4, 3, 2}, {6, 4, 3}, {3, 3, 3}, {2, 2, 3}, {2, 1, 2}, {1, 1, 2}};
    private static final int[][] SEGMENT_SIZES = new int[][]{{0, 0}, {0, 4}, {0, 9}, {0, 16}, {0, 22}, {11, 0}, {13, 4}};

    public SilverfishModel() {
        this.body = new ModelPart[7];
        float f = -3.5F;

        for (int i = 0; i < this.body.length; i++) {
            this.body[i] = new ModelPart(this, SEGMENT_SIZES[i][0], SEGMENT_SIZES[i][1]);
            this.body[i]
                .addBox(
                    SEGMENT_LOCATIONS[i][0] * -0.5F,
                    0.0F,
                    SEGMENT_LOCATIONS[i][2] * -0.5F,
                    SEGMENT_LOCATIONS[i][0],
                    SEGMENT_LOCATIONS[i][1],
                    SEGMENT_LOCATIONS[i][2]
                );
            this.body[i].setPos(0.0F, 24 - SEGMENT_LOCATIONS[i][1], f);
            this.radii[i] = f;
            if (i < this.body.length - 1) {
                f += (SEGMENT_LOCATIONS[i][2] + SEGMENT_LOCATIONS[i + 1][2]) * 0.5F;
            }
        }

        this.scales = new ModelPart[3];
        this.scales[0] = new ModelPart(this, 20, 0);
        this.scales[0].addBox(-5.0F, 0.0F, SEGMENT_LOCATIONS[2][2] * -0.5F, 10, 8, SEGMENT_LOCATIONS[2][2]);
        this.scales[0].setPos(0.0F, 16.0F, this.radii[2]);
        this.scales[1] = new ModelPart(this, 20, 11);
        this.scales[1].addBox(-3.0F, 0.0F, SEGMENT_LOCATIONS[4][2] * -0.5F, 6, 4, SEGMENT_LOCATIONS[4][2]);
        this.scales[1].setPos(0.0F, 20.0F, this.radii[4]);
        this.scales[2] = new ModelPart(this, 20, 18);
        this.scales[2].addBox(-3.0F, 0.0F, SEGMENT_LOCATIONS[4][2] * -0.5F, 6, 5, SEGMENT_LOCATIONS[1][2]);
        this.scales[2].setPos(0.0F, 19.0F, this.radii[1]);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);

        for (int i = 0; i < this.body.length; i++) {
            this.body[i].render(scale);
        }

        for (int j = 0; j < this.scales.length; j++) {
            this.scales[j].render(scale);
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        for (int i = 0; i < this.body.length; i++) {
            this.body[i].rotationY = MathHelper.cos(bob * 0.9F + i * 0.15F * (float) Math.PI) * (float) Math.PI * 0.05F * (1 + Math.abs(i - 2));
            this.body[i].x = MathHelper.sin(bob * 0.9F + i * 0.15F * (float) Math.PI) * (float) Math.PI * 0.2F * Math.abs(i - 2);
        }

        this.scales[0].rotationY = this.body[2].rotationY;
        this.scales[1].rotationY = this.body[4].rotationY;
        this.scales[1].x = this.body[4].x;
        this.scales[2].rotationY = this.body[1].rotationY;
        this.scales[2].x = this.body[1].x;
    }
}
