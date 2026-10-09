package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.block.entity.EnderCrystalModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.EnderCrystalEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class EnderCrystalRenderer extends EntityRenderer<EnderCrystalEntity> {
    private static final Identifier ENDER_CRYSTAL_LOCATION = new Identifier("textures/entity/endercrystal/endercrystal.png");
    private Model model = new EnderCrystalModel(0.0F, true);

    public EnderCrystalRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
        this.shadowSize = 0.5F;
    }

    public void render(EnderCrystalEntity enderCrystalEntity, double d, double e, double f, float g, float h) {
        float fx = enderCrystalEntity.renderTicks + h;
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)d, (float)e, (float)f);
        this.bindTexture(ENDER_CRYSTAL_LOCATION);
        float f1 = MathHelper.sin(fx * 0.2F) / 2.0F + 0.5F;
        f1 = f1 * f1 + f1;
        this.model.render(enderCrystalEntity, 0.0F, fx * 3.0F, f1 * 0.2F, 0.0F, 0.0F, 0.0625F);
        GlStateManager.popMatrix();
        super.render(enderCrystalEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(EnderCrystalEntity enderCrystalEntity) {
        return ENDER_CRYSTAL_LOCATION;
    }
}
