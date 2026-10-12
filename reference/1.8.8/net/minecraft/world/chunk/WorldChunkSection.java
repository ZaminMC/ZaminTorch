package net.minecraft.world.chunk;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;

public class WorldChunkSection {
    private int offsetY;
    private int nonAirBlockCount;
    private int randomTickingBlockCount;
    private char[] blockStates;
    private ChunkNibbleStorage blockLight;
    private ChunkNibbleStorage skyLight;

    public WorldChunkSection(int offsetY, boolean hasSkyLight) {
        this.offsetY = offsetY;
        this.blockStates = new char[4096];
        this.blockLight = new ChunkNibbleStorage();
        if (hasSkyLight) {
            this.skyLight = new ChunkNibbleStorage();
        }
    }

    public BlockState getBlockState(int x, int y, int z) {
        BlockState blockstate = Block.STATE_REGISTRY.get(this.blockStates[y << 8 | z << 4 | x]);
        return blockstate != null ? blockstate : Blocks.AIR.defaultState();
    }

    public void setBlockState(int x, int y, int z, BlockState state) {
        BlockState blockstate = this.getBlockState(x, y, z);
        Block block = blockstate.getBlock();
        Block block1 = state.getBlock();
        if (block != Blocks.AIR) {
            this.nonAirBlockCount--;
            if (block.ticksRandomly()) {
                this.randomTickingBlockCount--;
            }
        }

        if (block1 != Blocks.AIR) {
            this.nonAirBlockCount++;
            if (block1.ticksRandomly()) {
                this.randomTickingBlockCount++;
            }
        }

        this.blockStates[y << 8 | z << 4 | x] = (char)Block.STATE_REGISTRY.getId(state);
    }

    public Block getBlock(int x, int y, int z) {
        return this.getBlockState(x, y, z).getBlock();
    }

    public int getBlockMetadata(int x, int y, int z) {
        BlockState blockstate = this.getBlockState(x, y, z);
        return blockstate.getBlock().getMetadataFromState(blockstate);
    }

    public boolean isEmpty() {
        return this.nonAirBlockCount == 0;
    }

    public boolean hasRandomTickingBlocks() {
        return this.randomTickingBlockCount > 0;
    }

    public int getOffsetY() {
        return this.offsetY;
    }

    public void setSkyLight(int x, int y, int z, int light) {
        this.skyLight.set(x, y, z, light);
    }

    public int getSkyLight(int x, int y, int z) {
        return this.skyLight.get(x, y, z);
    }

    public void setBlockLight(int x, int y, int z, int light) {
        this.blockLight.set(x, y, z, light);
    }

    public int getBlockLight(int x, int y, int z) {
        return this.blockLight.get(x, y, z);
    }

    public void validateBlockCounters() {
        this.nonAirBlockCount = 0;
        this.randomTickingBlockCount = 0;

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    Block block = this.getBlock(i, j, k);
                    if (block != Blocks.AIR) {
                        this.nonAirBlockCount++;
                        if (block.ticksRandomly()) {
                            this.randomTickingBlockCount++;
                        }
                    }
                }
            }
        }
    }

    public char[] getBlockStates() {
        return this.blockStates;
    }

    public void setBlockStates(char[] blockStates) {
        this.blockStates = blockStates;
    }

    public ChunkNibbleStorage getBlockLightStorage() {
        return this.blockLight;
    }

    public ChunkNibbleStorage getSkyLightStorage() {
        return this.skyLight;
    }

    public void setBlockLightStorage(ChunkNibbleStorage blockLight) {
        this.blockLight = blockLight;
    }

    public void setSkyLightStorage(ChunkNibbleStorage skyLight) {
        this.skyLight = skyLight;
    }
}
