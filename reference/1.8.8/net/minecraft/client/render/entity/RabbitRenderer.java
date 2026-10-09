package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.mob.passive.animal.RabbitEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;

public class RabbitRenderer extends MobRenderer<RabbitEntity> {
    private static final Identifier BROWN_LOCATION = new Identifier("textures/entity/rabbit/brown.png");
    private static final Identifier WHITE_LOCATION = new Identifier("textures/entity/rabbit/white.png");
    private static final Identifier BLACK_LOCATION = new Identifier("textures/entity/rabbit/black.png");
    private static final Identifier GOLD_LOCATION = new Identifier("textures/entity/rabbit/gold.png");
    private static final Identifier SALT_LOCATION = new Identifier("textures/entity/rabbit/salt.png");
    private static final Identifier WHITE_SPLOTCHED_LOCATION = new Identifier("textures/entity/rabbit/white_splotched.png");
    private static final Identifier TOAST_LOCATION = new Identifier("textures/entity/rabbit/toast.png");
    private static final Identifier CAERBANNOG_LOCATION = new Identifier("textures/entity/rabbit/caerbannog.png");

    public RabbitRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
    }

    protected Identifier getTextureLocation(RabbitEntity rabbitEntity) {
        String s = Formatting.strip(rabbitEntity.getName());
        if (s != null && s.equals("Toast")) {
            return TOAST_LOCATION;
        }

        switch (rabbitEntity.getSkin()) {
            case 0:
            default:
                return BROWN_LOCATION;
            case 1:
                return WHITE_LOCATION;
            case 2:
                return BLACK_LOCATION;
            case 3:
                return WHITE_SPLOTCHED_LOCATION;
            case 4:
                return GOLD_LOCATION;
            case 5:
                return SALT_LOCATION;
            case 99:
                return CAERBANNOG_LOCATION;
        }
    }
}
