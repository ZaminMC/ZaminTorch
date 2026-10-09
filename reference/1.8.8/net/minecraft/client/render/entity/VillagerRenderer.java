package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.client.render.model.entity.VillagerModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.resource.Identifier;

public class VillagerRenderer extends MobRenderer<VillagerEntity> {
    private static final Identifier VILLAGER_LOCATION = new Identifier("textures/entity/villager/villager.png");
    private static final Identifier FARMER_LOCATION = new Identifier("textures/entity/villager/farmer.png");
    private static final Identifier LIBRARIAN_LOCATION = new Identifier("textures/entity/villager/librarian.png");
    private static final Identifier PRIEST_LOCATION = new Identifier("textures/entity/villager/priest.png");
    private static final Identifier BLACKSMITH_LOCATION = new Identifier("textures/entity/villager/smith.png");
    private static final Identifier BUTCHER_LOCATION = new Identifier("textures/entity/villager/butcher.png");

    public VillagerRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new VillagerModel(0.0F), 0.5F);
        this.addLayer(new WornSkullLayer(this.getModel().head));
    }

    public VillagerModel getModel() {
        return (VillagerModel)super.getModel();
    }

    protected Identifier getTextureLocation(VillagerEntity villagerEntity) {
        switch (villagerEntity.getProfession()) {
            case 0:
                return FARMER_LOCATION;
            case 1:
                return LIBRARIAN_LOCATION;
            case 2:
                return PRIEST_LOCATION;
            case 3:
                return BLACKSMITH_LOCATION;
            case 4:
                return BUTCHER_LOCATION;
            default:
                return VILLAGER_LOCATION;
        }
    }

    protected void applyScale(VillagerEntity villagerEntity, float f) {
        float fx = 0.9375F;
        if (villagerEntity.getBreedingAge() < 0) {
            fx = (float)(fx * 0.5);
            this.shadowSize = 0.25F;
        } else {
            this.shadowSize = 0.5F;
        }

        GlStateManager.scalef(fx, fx, fx);
    }
}
