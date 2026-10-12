package net.minecraft.client.entity.particle;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.world.World;

public class BlockDustParticle extends BlockParticle {
    protected BlockDustParticle(World world, double d, double e, double f, double g, double h, double i, BlockState blockState) {
        super(world, d, e, f, g, h, i, blockState);
        this.velocityX = g;
        this.velocityY = h;
        this.velocityZ = i;
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            BlockState blockstate = Block.deserialize(parameters[0]);
            return blockstate.getBlock().getRenderType() == -1
                ? null
                : new BlockDustParticle(world, x, y, z, velocityX, velocityY, velocityZ, blockstate).updateColor();
        }
    }
}
