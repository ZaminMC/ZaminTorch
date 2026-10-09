package net.minecraft.client.render.entity;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.entity.HorseModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.LayeredTexture;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.resource.Identifier;

public class HorseRenderer extends MobRenderer<HorseBaseEntity> {
    private static final Map<String, Identifier> ARMORED_TEXTURE_LOCATIONS = Maps.newHashMap();
    private static final Identifier WHITE_HORSE_LOCATION = new Identifier("textures/entity/horse/horse_white.png");
    private static final Identifier MULE_LOCATION = new Identifier("textures/entity/horse/mule.png");
    private static final Identifier DONKEY_LOCATION = new Identifier("textures/entity/horse/donkey.png");
    private static final Identifier ZOMBIE_HORSE_LOCATION = new Identifier("textures/entity/horse/horse_zombie.png");
    private static final Identifier SKELETON_HORSE_LOCATION = new Identifier("textures/entity/horse/horse_skeleton.png");

    public HorseRenderer(EntityRenderDispatcher dispatcher, HorseModel model, float shadowSize) {
        super(dispatcher, model, shadowSize);
    }

    protected void applyScale(HorseBaseEntity horseBaseEntity, float f) {
        float fx = 1.0F;
        int i = horseBaseEntity.getType();
        if (i == 1) {
            fx *= 0.87F;
        } else if (i == 2) {
            fx *= 0.92F;
        }

        GlStateManager.scalef(fx, fx, fx);
        super.applyScale(horseBaseEntity, f);
    }

    protected Identifier getTextureLocation(HorseBaseEntity horseBaseEntity) {
        if (!horseBaseEntity.hasArmor()) {
            switch (horseBaseEntity.getType()) {
                case 0:
                default:
                    return WHITE_HORSE_LOCATION;
                case 1:
                    return DONKEY_LOCATION;
                case 2:
                    return MULE_LOCATION;
                case 3:
                    return ZOMBIE_HORSE_LOCATION;
                case 4:
                    return SKELETON_HORSE_LOCATION;
            }
        } else {
            return this.getArmoredTextureLocation(horseBaseEntity);
        }
    }

    private Identifier getArmoredTextureLocation(HorseBaseEntity horse) {
        String s = horse.getBaseTexturePath();
        if (!horse.hasArmoredTexture()) {
            return null;
        }

        Identifier identifier = ARMORED_TEXTURE_LOCATIONS.get(s);
        if (identifier == null) {
            identifier = new Identifier(s);
            Minecraft.getInstance().getTextureManager().register(identifier, new LayeredTexture(horse.getArmoredTexturePaths()));
            ARMORED_TEXTURE_LOCATIONS.put(s, identifier);
        }

        return identifier;
    }
}
