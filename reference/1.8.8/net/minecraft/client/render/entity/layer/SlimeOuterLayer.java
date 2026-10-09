package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.SlimeRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.SlimeModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.SlimeEntity;

public class SlimeOuterLayer implements EntityRenderLayer<SlimeEntity> {
    private final SlimeRenderer parent;
    private final Model model = new SlimeModel(0);

    public SlimeOuterLayer(SlimeRenderer parent) {
        this.parent = parent;
    }

    public void render(SlimeEntity slimeEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (!slimeEntity.isInvisible()) {
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableNormalize();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            this.model.copyPropertiesFrom(this.parent.getModel());
            this.model.render(slimeEntity, f, g, i, j, k, l);
            GlStateManager.disableBlend();
            GlStateManager.disableNormalize();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return true;
    }
}
