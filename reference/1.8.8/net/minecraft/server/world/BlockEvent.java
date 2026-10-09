package net.minecraft.server.world;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public class BlockEvent {
    private BlockPos pos;
    private Block block;
    private int type;
    private int data;

    public BlockEvent(BlockPos pos, Block block, int type, int data) {
        this.pos = pos;
        this.type = type;
        this.data = data;
        this.block = block;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getType() {
        return this.type;
    }

    public int getData() {
        return this.data;
    }

    public Block getBlock() {
        return this.block;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof BlockEvent)) {
            return false;
        }

        BlockEvent blockevent = (BlockEvent)object;
        return this.pos.equals(blockevent.pos) && this.type == blockevent.type && this.data == blockevent.data && this.block == blockevent.block;
    }

    @Override
    public String toString() {
        return "TE(" + this.pos + ")," + this.type + "," + this.data + "," + this.block;
    }
}
