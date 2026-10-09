package net.minecraft.world.gen.feature.decorator;

import net.minecraft.block.Blocks;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.EndPillarFeature;
import net.minecraft.world.gen.feature.Feature;

public class EndIslandDecorator extends FeatureDecorator {
    protected Feature pillar = new EndPillarFeature(Blocks.END_STONE);

    @Override
    protected void decorate(Biome biome) {
        this.placeVeins();
        if (this.random.nextInt(5) == 0) {
            int i = this.random.nextInt(16) + 8;
            int j = this.random.nextInt(16) + 8;
            this.pillar.place(this.world, this.random, this.world.getSurfaceHeight(this.pos.add(i, 0, j)));
        }

        if (this.pos.getX() == 0 && this.pos.getZ() == 0) {
            EnderDragonEntity enderdragonentity = new EnderDragonEntity(this.world);
            enderdragonentity.setPositionAndAngles(0.0, 128.0, 0.0, this.random.nextFloat() * 360.0F, 0.0F);
            this.world.addEntity(enderdragonentity);
        }
    }
}
