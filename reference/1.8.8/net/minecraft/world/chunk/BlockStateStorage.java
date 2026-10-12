package net.minecraft.world.chunk;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;

public class BlockStateStorage {
    private final short[] states = new short[65536];
    private final BlockState defaultState = Blocks.AIR.defaultState();

    public BlockState get(int x, int y, int z) {
        int i = x << 12 | z << 8 | y;
        return this.get(i);
    }

    public BlockState get(int index) {
        if (index >= 0 && index < this.states.length) {
            BlockState blockstate = Block.STATE_REGISTRY.get(this.states[index]);
            return blockstate != null ? blockstate : this.defaultState;
        } else {
            throw new IndexOutOfBoundsException("The coordinate is out of range");
        }
    }

    public void set(int x, int y, int z, BlockState state) {
        int i = x << 12 | z << 8 | y;
        this.set(i, state);
    }

    public void set(int index, BlockState state) {
        if (index >= 0 && index < this.states.length) {
            this.states[index] = (short)Block.STATE_REGISTRY.getId(state);
        } else {
            throw new IndexOutOfBoundsException("The coordinate is out of range");
        }
    }
}
