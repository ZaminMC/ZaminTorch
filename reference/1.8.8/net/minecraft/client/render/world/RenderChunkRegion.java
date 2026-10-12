package net.minecraft.client.render.world;

import java.util.Arrays;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.WorldRegion;
import net.minecraft.world.chunk.WorldChunk;

public class RenderChunkRegion extends WorldRegion {
    private static final BlockState AIR_STATE = Blocks.AIR.defaultState();
    private final BlockPos center;
    private int[] light;
    private BlockState[] blocks;

    public RenderChunkRegion(World world, BlockPos blockPos, BlockPos blockPos2, int i) {
        super(world, blockPos, blockPos2, i);
        this.center = blockPos.subtract(new Vec3i(i, i, i));
        int ix = 8000;
        this.light = new int[8000];
        Arrays.fill(this.light, -1);
        this.blocks = new BlockState[8000];
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        int i = (pos.getX() >> 4) - this.chunkX;
        int j = (pos.getZ() >> 4) - this.chunkZ;
        return this.chunks[i][j].getBlockEntity(pos, WorldChunk.BlockEntityCreationType.QUEUED);
    }

    @Override
    public int getLightColor(BlockPos pos, int blockLight) {
        int i = this.index(pos);
        int j = this.light[i];
        if (j == -1) {
            j = super.getLightColor(pos, blockLight);
            this.light[i] = j;
        }

        return j;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int i = this.index(pos);
        BlockState blockstate = this.blocks[i];
        if (blockstate == null) {
            blockstate = this.getBlockStateFromChunk(pos);
            this.blocks[i] = blockstate;
        }

        return blockstate;
    }

    private BlockState getBlockStateFromChunk(BlockPos pos) {
        if (pos.getY() >= 0 && pos.getY() < 256) {
            int i = (pos.getX() >> 4) - this.chunkX;
            int j = (pos.getZ() >> 4) - this.chunkZ;
            return this.chunks[i][j].getBlockState(pos);
        } else {
            return AIR_STATE;
        }
    }

    private int index(BlockPos pos) {
        int i = pos.getX() - this.center.getX();
        int j = pos.getY() - this.center.getY();
        int k = pos.getZ() - this.center.getZ();
        return i * 400 + k * 20 + j;
    }
}
