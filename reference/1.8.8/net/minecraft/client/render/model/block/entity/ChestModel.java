package net.minecraft.client.render.model.block.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;

public class ChestModel extends Model {
    public ModelPart lid = new ModelPart(this, 0, 0).setTextureSize(64, 64);
    public ModelPart base;
    public ModelPart lock;

    public ChestModel() {
        this.lid.addBox(0.0F, -5.0F, -14.0F, 14, 5, 14, 0.0F);
        this.lid.x = 1.0F;
        this.lid.y = 7.0F;
        this.lid.z = 15.0F;
        this.lock = new ModelPart(this, 0, 0).setTextureSize(64, 64);
        this.lock.addBox(-1.0F, -2.0F, -15.0F, 2, 4, 1, 0.0F);
        this.lock.x = 8.0F;
        this.lock.y = 7.0F;
        this.lock.z = 15.0F;
        this.base = new ModelPart(this, 0, 19).setTextureSize(64, 64);
        this.base.addBox(0.0F, 0.0F, 0.0F, 14, 10, 14, 0.0F);
        this.base.x = 1.0F;
        this.base.y = 6.0F;
        this.base.z = 1.0F;
    }

    public void renderParts() {
        this.lock.rotationX = this.lid.rotationX;
        this.lid.render(0.0625F);
        this.lock.render(0.0625F);
        this.base.render(0.0625F);
    }
}
