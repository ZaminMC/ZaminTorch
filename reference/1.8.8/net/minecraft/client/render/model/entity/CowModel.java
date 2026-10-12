package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.ModelPart;

public class CowModel extends QuadrupedModel {
    public CowModel() {
        super(12, 0.0F);
        this.head = new ModelPart(this, 0, 0);
        this.head.addBox(-4.0F, -4.0F, -6.0F, 8, 8, 6, 0.0F);
        this.head.setPos(0.0F, 4.0F, -8.0F);
        this.head.setTextureCoords(22, 0).addBox(-5.0F, -5.0F, -4.0F, 1, 3, 1, 0.0F);
        this.head.setTextureCoords(22, 0).addBox(4.0F, -5.0F, -4.0F, 1, 3, 1, 0.0F);
        this.body = new ModelPart(this, 18, 4);
        this.body.addBox(-6.0F, -10.0F, -7.0F, 12, 18, 10, 0.0F);
        this.body.setPos(0.0F, 5.0F, 2.0F);
        this.body.setTextureCoords(52, 0).addBox(-2.0F, 2.0F, -8.0F, 4, 6, 1);
        this.backRightLeg.x--;
        this.backLeftLeg.x++;
        this.backRightLeg.z += 0.0F;
        this.backLeftLeg.z += 0.0F;
        this.frontRightLeg.x--;
        this.frontLeftLeg.x++;
        this.frontRightLeg.z--;
        this.frontLeftLeg.z--;
        this.babyHeadOffset += 2.0F;
    }
}
