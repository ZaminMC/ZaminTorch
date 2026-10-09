package net.minecraft.world.gen.chunk;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.resource.Identifier;

public class FlatWorldLayer {
    private final int biomeId;
    private BlockState state;
    private int size = 1;
    private int y;

    public FlatWorldLayer(int size, Block block) {
        this(3, size, block);
    }

    public FlatWorldLayer(int biomeId, int size, Block block) {
        this.biomeId = biomeId;
        this.size = size;
        this.state = block.defaultState();
    }

    public FlatWorldLayer(int biomeId, int size, Block block, int metadata) {
        this(biomeId, size, block);
        this.state = block.getStateFromMetadata(metadata);
    }

    public int getSize() {
        return this.size;
    }

    public BlockState getBlockState() {
        return this.state;
    }

    private Block getBlock() {
        return this.state.getBlock();
    }

    private int getBlockMetadata() {
        return this.state.getBlock().getMetadataFromState(this.state);
    }

    public int getY() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public String toString() {
        String s;
        if (this.biomeId >= 3) {
            Identifier identifier = Block.REGISTRY.getKey(this.getBlock());
            s = identifier == null ? "null" : identifier.toString();
            if (this.size > 1) {
                s = this.size + "*" + s;
            }
        } else {
            s = Integer.toString(Block.getId(this.getBlock()));
            if (this.size > 1) {
                s = this.size + "x" + s;
            }
        }

        int i = this.getBlockMetadata();
        if (i > 0) {
            s = s + ":" + i;
        }

        return s;
    }
}
