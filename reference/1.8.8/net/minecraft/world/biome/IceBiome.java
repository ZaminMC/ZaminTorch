package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.feature.IcePatchFeature;
import net.minecraft.world.gen.feature.IceSpikeFeature;
import net.minecraft.world.gen.feature.SpruceTreeFeature;

public class IceBiome extends Biome {
    private boolean spikes;
    private IceSpikeFeature iceSpike = new IceSpikeFeature();
    private IcePatchFeature icePatch = new IcePatchFeature(4);

    public IceBiome(int id, boolean spikes) {
        super(id);
        this.spikes = spikes;
        if (spikes) {
            this.surfaceBlock = Blocks.SNOW.defaultState();
        }

        this.passiveEntries.clear();
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        if (this.spikes) {
            for (int i = 0; i < 3; i++) {
                int j = random.nextInt(16) + 8;
                int k = random.nextInt(16) + 8;
                this.iceSpike.place(world, random, world.getHeight(pos.add(j, 0, k)));
            }

            for (int l = 0; l < 2; l++) {
                int i1 = random.nextInt(16) + 8;
                int j1 = random.nextInt(16) + 8;
                this.icePatch.place(world, random, world.getHeight(pos.add(i1, 0, j1)));
            }
        }

        super.decorate(world, random, pos);
    }

    @Override
    public AbstractTreeFeature pickTree(Random random) {
        return new SpruceTreeFeature(false);
    }

    @Override
    protected Biome mutate(int id) {
        Biome biome = new IceBiome(id, true)
            .setColor(13828095, true)
            .setName(this.name + " Spikes")
            .setSnowy()
            .setTemperatureAndDownfall(0.0F, 0.5F)
            .setHeight(new Biome.Height(this.baseHeight + 0.1F, this.heightVariation + 0.1F));
        biome.baseHeight = this.baseHeight + 0.3F;
        biome.heightVariation = this.heightVariation + 0.4F;
        return biome;
    }
}
