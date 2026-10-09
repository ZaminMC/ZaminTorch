package net.minecraft.block.pattern;

import com.google.common.base.Predicate;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockPointer {
    private final World world;
    private final BlockPos pos;
    private final boolean loadChunks;
    private BlockState state;
    private BlockEntity blockEntity;
    private boolean hasBlockEntity;

    public BlockPointer(World world, BlockPos pos, boolean loadChunks) {
        this.world = world;
        this.pos = pos;
        this.loadChunks = loadChunks;
    }

    public BlockState getState() {
        if (this.state == null && (this.loadChunks || this.world.isChunkLoaded(this.pos))) {
            this.state = this.world.getBlockState(this.pos);
        }

        return this.state;
    }

    public BlockEntity getBlockEntity() {
        if (this.blockEntity == null && !this.hasBlockEntity) {
            this.blockEntity = this.world.getBlockEntity(this.pos);
            this.hasBlockEntity = true;
        }

        return this.blockEntity;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public static Predicate<BlockPointer> hasState(Predicate<BlockState> predicate) {
        return new Predicate<BlockPointer>() {
            public boolean apply(BlockPointer blockPointer) {
                return blockPointer != null && predicate.apply(blockPointer.getState());
            }
        };
    }
}
