package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.entity.ZombieVillagerModel;

public class ZombieVillagerArmorLayer extends ArmorLayer {
    public ZombieVillagerArmorLayer(LivingEntityRenderer<?> livingEntityRenderer) {
        super(livingEntityRenderer);
    }

    @Override
    protected void hideAll() {
        this.innerModel = new ZombieVillagerModel(0.5F, 0.0F, true);
        this.outerModel = new ZombieVillagerModel(1.0F, 0.0F, true);
    }
}
