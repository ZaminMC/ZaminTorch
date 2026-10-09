package net.minecraft.client.entity.particle;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockParticle extends Particle {
    private BlockState state;
    private BlockPos pos;

    protected BlockParticle(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, BlockState state) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.state = state;
        this.setTexture(Minecraft.getInstance().getBlockRenderDispatcher().getModelShaper().getParticleIcon(state));
        this.gravity = state.getBlock().gravity;
        this.red = this.green = this.blue = 0.6F;
        this.size /= 2.0F;
    }

    public BlockParticle init(BlockPos pos) {
        this.pos = pos;
        if (this.state.getBlock() == Blocks.GRASS) {
            return this;
        }

        int i = this.state.getBlock().getColor(this.world, pos);
        this.red *= (i >> 16 & 0xFF) / 255.0F;
        this.green *= (i >> 8 & 0xFF) / 255.0F;
        this.blue *= (i & 0xFF) / 255.0F;
        return this;
    }

    public BlockParticle updateColor() {
        this.pos = new BlockPos(this.x, this.y, this.z);
        Block block = this.state.getBlock();
        if (block == Blocks.GRASS) {
            return this;
        }

        int i = block.getColor(this.state);
        this.red *= (i >> 16 & 0xFF) / 255.0F;
        this.green *= (i >> 8 & 0xFF) / 255.0F;
        this.blue *= (i & 0xFF) / 255.0F;
        return this;
    }

    @Override
    public int getAtlasType() {
        return 1;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.spriteRow + this.offsetU / 4.0F) / 16.0F;
        float f1 = f + 0.015609375F;
        float f2 = (this.spriteColumn + this.offsetV / 4.0F) / 16.0F;
        float f3 = f2 + 0.015609375F;
        float f4 = 0.1F * this.size;
        if (this.sprite != null) {
            f = this.sprite.getU(this.offsetU / 4.0F * 16.0F);
            f1 = this.sprite.getU((this.offsetU + 1.0F) / 4.0F * 16.0F);
            f2 = this.sprite.getV(this.offsetV / 4.0F * 16.0F);
            f3 = this.sprite.getV((this.offsetV + 1.0F) / 4.0F * 16.0F);
        }

        float f5 = (float)(this.lastX + (this.x - this.lastX) * tickDelta - lerpCameraX);
        float f6 = (float)(this.lastY + (this.y - this.lastY) * tickDelta - lerpCameraY);
        float f7 = (float)(this.lastZ + (this.z - this.lastZ) * tickDelta - lerpCameraZ);
        int i = this.getLightLevel(tickDelta);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        bufferBuilder.vertex(f5 - dx * f4 - forwards * f4, f6 - dy * f4, f7 - dz * f4 - sideways * f4)
            .texture(f, f3)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 - dx * f4 + forwards * f4, f6 + dy * f4, f7 - dz * f4 + sideways * f4)
            .texture(f, f2)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 + dx * f4 + forwards * f4, f6 + dy * f4, f7 + dz * f4 + sideways * f4)
            .texture(f1, f2)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 + dx * f4 - forwards * f4, f6 - dy * f4, f7 + dz * f4 - sideways * f4)
            .texture(f1, f3)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
    }

    @Override
    public int getLightLevel(float tickDelta) {
        int i = super.getLightLevel(tickDelta);
        int j = 0;
        if (this.world.isChunkLoaded(this.pos)) {
            j = this.world.getLightColor(this.pos, 0);
        }

        return i == 0 ? j : i;
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new BlockParticle(world, x, y, z, velocityX, velocityY, velocityZ, Block.deserialize(parameters[0])).updateColor();
        }
    }
}
