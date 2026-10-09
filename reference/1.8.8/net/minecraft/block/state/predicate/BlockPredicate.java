package net.minecraft.block.state.predicate;

import com.google.common.base.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;

public class BlockPredicate implements Predicate<BlockState> {
    private final Block block;

    private BlockPredicate(Block block) {
        this.block = block;
    }

    public static BlockPredicate of(Block block) {
        return new BlockPredicate(block);
    }

    public boolean apply(BlockState blockState) {
        return blockState != null && blockState.getBlock() == this.block;
    }
}
