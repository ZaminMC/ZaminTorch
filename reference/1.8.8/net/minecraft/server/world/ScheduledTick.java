package net.minecraft.server.world;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public class ScheduledTick implements Comparable<ScheduledTick> {
    private static long idCounter;
    private final Block block;
    public final BlockPos pos;
    public long time;
    public int priority;
    private long id;

    public ScheduledTick(BlockPos pos, Block block) {
        this.id = idCounter++;
        this.pos = pos;
        this.block = block;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ScheduledTick)) {
            return false;
        }

        ScheduledTick scheduledtick = (ScheduledTick)object;
        return this.pos.equals(scheduledtick.pos) && Block.areEqual(this.block, scheduledtick.block);
    }

    @Override
    public int hashCode() {
        return this.pos.hashCode();
    }

    public ScheduledTick setTime(long time) {
        this.time = time;
        return this;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int compareTo(ScheduledTick scheduledTick) {
        if (this.time < scheduledTick.time) {
            return -1;
        } else if (this.time > scheduledTick.time) {
            return 1;
        } else if (this.priority != scheduledTick.priority) {
            return this.priority - scheduledTick.priority;
        } else if (this.id < scheduledTick.id) {
            return -1;
        } else {
            return this.id > scheduledTick.id ? 1 : 0;
        }
    }

    @Override
    public String toString() {
        return Block.getId(this.block) + ": " + this.pos + ", " + this.time + ", " + this.priority + ", " + this.id;
    }

    public Block getBlock() {
        return this.block;
    }
}
