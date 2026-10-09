package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.WolfRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.resource.Identifier;

public class WolfCollarLayer implements EntityRenderLayer<WolfEntity> {
    private static final Identifier WOLF_COLLAR_LOCATION = new Identifier("textures/entity/wolf/wolf_collar.png");
    private final WolfRenderer parent;

    public WolfCollarLayer(WolfRenderer parent) {
        this.parent = parent;
    }

    public void render(WolfEntity wolfEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (wolfEntity.isTamed() && !wolfEntity.isInvisible()) {
            this.parent.bindTexture(WOLF_COLLAR_LOCATION);
            DyeColor dyecolor = DyeColor.byId(wolfEntity.getCollarColor().getId());
            float[] afloat = SheepEntity.getColorRgb(dyecolor);
            GlStateManager.color3f(afloat[0], afloat[1], afloat[2]);
            this.parent.getModel().render(wolfEntity, f, g, i, j, k, l);
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return true;
    }
}
