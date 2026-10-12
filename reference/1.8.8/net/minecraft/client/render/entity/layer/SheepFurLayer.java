package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.SheepRenderer;
import net.minecraft.client.render.model.entity.SheepFurModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.resource.Identifier;

public class SheepFurLayer implements EntityRenderLayer<SheepEntity> {
    private static final Identifier SHEEP_FUR_LOCATION = new Identifier("textures/entity/sheep/sheep_fur.png");
    private final SheepRenderer parent;
    private final SheepFurModel model = new SheepFurModel();

    public SheepFurLayer(SheepRenderer parent) {
        this.parent = parent;
    }

    public void render(SheepEntity sheepEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (!sheepEntity.isSheared() && !sheepEntity.isInvisible()) {
            this.parent.bindTexture(SHEEP_FUR_LOCATION);
            if (sheepEntity.hasCustomName() && "jeb_".equals(sheepEntity.getCustomName())) {
                int i1 = 25;
                int ix = sheepEntity.ticks / 25 + sheepEntity.getNetworkId();
                int jx = DyeColor.values().length;
                int kx = ix % jx;
                int lx = (ix + 1) % jx;
                float fx = (sheepEntity.ticks % 25 + h) / 25.0F;
                float[] afloat1 = SheepEntity.getColorRgb(DyeColor.byId(kx));
                float[] afloat2 = SheepEntity.getColorRgb(DyeColor.byId(lx));
                GlStateManager.color3f(
                    afloat1[0] * (1.0F - fx) + afloat2[0] * fx, afloat1[1] * (1.0F - fx) + afloat2[1] * fx, afloat1[2] * (1.0F - fx) + afloat2[2] * fx
                );
            } else {
                float[] afloat = SheepEntity.getColorRgb(sheepEntity.getColor());
                GlStateManager.color3f(afloat[0], afloat[1], afloat[2]);
            }

            this.model.copyPropertiesFrom(this.parent.getModel());
            this.model.prepare(sheepEntity, f, g, h);
            this.model.render(sheepEntity, f, g, i, j, k, l);
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return true;
    }
}
