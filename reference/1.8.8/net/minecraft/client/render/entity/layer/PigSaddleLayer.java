package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.PigRenderer;
import net.minecraft.client.render.model.entity.PigModel;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.resource.Identifier;

public class PigSaddleLayer implements EntityRenderLayer<PigEntity> {
    private static final Identifier PIG_SADDLE_LOCATION = new Identifier("textures/entity/pig/pig_saddle.png");
    private final PigRenderer parent;
    private final PigModel model = new PigModel(0.5F);

    public PigSaddleLayer(PigRenderer parent) {
        this.parent = parent;
    }

    public void render(PigEntity pigEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (pigEntity.isSaddled()) {
            this.parent.bindTexture(PIG_SADDLE_LOCATION);
            this.model.copyPropertiesFrom(this.parent.getModel());
            this.model.render(pigEntity, f, g, i, j, k, l);
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
