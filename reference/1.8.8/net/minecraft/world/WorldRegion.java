package net.minecraft.world;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.WorldGeneratorType;

public class WorldRegion implements WorldView {
    protected int chunkX;
    protected int chunkZ;
    protected WorldChunk[][] chunks;
    protected boolean empty;
    protected World world;

    public WorldRegion(World world, BlockPos minPos, BlockPos maxPos, int offset) {
        this.world = world;
        this.chunkX = minPos.getX() - offset >> 4;
        this.chunkZ = minPos.getZ() - offset >> 4;
        int i = maxPos.getX() + offset >> 4;
        int j = maxPos.getZ() + offset >> 4;
        this.chunks = new WorldChunk[i - this.chunkX + 1][j - this.chunkZ + 1];
        this.empty = true;

        for (int k = this.chunkX; k <= i; k++) {
            for (int l = this.chunkZ; l <= j; l++) {
                this.chunks[k - this.chunkX][l - this.chunkZ] = world.getChunkAt(k, l);
            }
        }

        for (int i1 = minPos.getX() >> 4; i1 <= maxPos.getX() >> 4; i1++) {
            for (int j1 = minPos.getZ() >> 4; j1 <= maxPos.getZ() >> 4; j1++) {
                WorldChunk worldchunk = this.chunks[i1 - this.chunkX][j1 - this.chunkZ];
                if (worldchunk != null && !worldchunk.isEmpty(minPos.getY(), maxPos.getY())) {
                    this.empty = false;
                }
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return this.empty;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        int i = (pos.getX() >> 4) - this.chunkX;
        int j = (pos.getZ() >> 4) - this.chunkZ;
        return this.chunks[i][j].getBlockEntity(pos, WorldChunk.BlockEntityCreationType.IMMEDIATE);
    }

    @Override
    public int getLightColor(BlockPos pos, int blockLight) {
        int i = this.getBrightness(LightType.SKY, pos);
        int j = this.getBrightness(LightType.BLOCK, pos);
        if (j < blockLight) {
            j = blockLight;
        }

        return i << 20 | j << 4;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (pos.getY() >= 0 && pos.getY() < 256) {
            int i = (pos.getX() >> 4) - this.chunkX;
            int j = (pos.getZ() >> 4) - this.chunkZ;
            if (i >= 0 && i < this.chunks.length && j >= 0 && j < this.chunks[i].length) {
                WorldChunk worldchunk = this.chunks[i][j];
                if (worldchunk != null) {
                    return worldchunk.getBlockState(pos);
                }
            }
        }

        return Blocks.AIR.defaultState();
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return this.world.getBiome(pos);
    }

    private int getBrightness(LightType type, BlockPos pos) {
        if (type == LightType.SKY && this.world.dimension.hasNoSky()) {
            return 0;
        }

        if (pos.getY() >= 0 && pos.getY() < 256) {
            if (this.getBlockState(pos).getBlock().usesNeighborLight()) {
                int l = 0;

                for (Direction direction : Direction.values()) {
                    int k = this.getLight(type, pos.offset(direction));
                    if (k > l) {
                        l = k;
                    }

                    if (l >= 15) {
                        return l;
                    }
                }

                return l;
            } else {
                int i = (pos.getX() >> 4) - this.chunkX;
                int j = (pos.getZ() >> 4) - this.chunkZ;
                return this.chunks[i][j].getLight(type, pos);
            }
        } else {
            return type.defaultValue;
        }
    }

    @Override
    public boolean isAir(BlockPos pos) {
        return this.getBlockState(pos).getBlock().getMaterial() == Material.AIR;
    }

    public int getLight(LightType type, BlockPos pos) {
        if (pos.getY() >= 0 && pos.getY() < 256) {
            int i = (pos.getX() >> 4) - this.chunkX;
            int j = (pos.getZ() >> 4) - this.chunkZ;
            return this.chunks[i][j].getLight(type, pos);
        } else {
            return type.defaultValue;
        }
    }

    @Override
    public int getDirectSignal(BlockPos pos, Direction dir) {
        BlockState blockstate = this.getBlockState(pos);
        return blockstate.getBlock().getDirectSignal(this, pos, blockstate, dir);
    }

    @Override
    public WorldGeneratorType getGeneratorType() {
        return this.world.getGeneratorType();
    }
}
