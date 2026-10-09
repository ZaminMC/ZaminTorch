package net.minecraft.client.render.model.block.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;

public class SignModel extends Model {
    public ModelPart board = new ModelPart(this, 0, 0);
    public ModelPart pole;

    public SignModel() {
        this.board.addBox(-12.0F, -14.0F, -1.0F, 24, 12, 2, 0.0F);
        this.pole = new ModelPart(this, 0, 14);
        this.pole.addBox(-1.0F, -2.0F, -1.0F, 2, 14, 2, 0.0F);
    }

    public void render() {
        this.board.render(0.0625F);
        this.pole.render(0.0625F);
    }
}
