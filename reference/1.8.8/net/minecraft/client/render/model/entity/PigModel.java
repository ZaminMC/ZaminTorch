package net.minecraft.client.render.model.entity;

public class PigModel extends QuadrupedModel {
    public PigModel() {
        this(0.0F);
    }

    public PigModel(float reduction) {
        super(6, reduction);
        this.head.setTextureCoords(16, 16).addBox(-2.0F, 0.0F, -9.0F, 4, 3, 1, reduction);
        this.babyHeadHeightOffset = 4.0F;
    }
}
