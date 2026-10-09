package net.minecraft.world.village;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class VillageDoor {
    private final BlockPos pos;
    private final BlockPos indoorsPos;
    private final Direction facing;
    private int lastUpdateTick;
    private boolean isOutsideVillage;
    private int restrictedTicks;

    public VillageDoor(BlockPos pos, int indoorsOffsetX, int indoorsOffsetZ, int lastVillageTick) {
        this(pos, getFacing(indoorsOffsetX, indoorsOffsetZ), lastVillageTick);
    }

    private static Direction getFacing(int dx, int dz) {
        if (dx < 0) {
            return Direction.WEST;
        } else if (dx > 0) {
            return Direction.EAST;
        } else {
            return dz < 0 ? Direction.NORTH : Direction.SOUTH;
        }
    }

    public VillageDoor(BlockPos pos, Direction facing, int lastVillageTick) {
        this.pos = pos;
        this.facing = facing;
        this.indoorsPos = pos.offset(facing, 2);
        this.lastUpdateTick = lastVillageTick;
    }

    public int squaredDistanceTo(int x, int y, int z) {
        return (int)this.pos.squaredDistanceTo(x, y, z);
    }

    public int squaredDistanceTo(BlockPos pos) {
        return (int)pos.squaredDistanceTo(this.getPos());
    }

    public int squaredDistanceToIndoors(BlockPos pos) {
        return (int)this.indoorsPos.squaredDistanceTo(pos);
    }

    public boolean isIndoors(BlockPos pos) {
        int i = pos.getX() - this.pos.getX();
        int j = pos.getZ() - this.pos.getY();
        return i * this.facing.getOffsetX() + j * this.facing.getOffsetZ() >= 0;
    }

    public void resetRestrictedTicks() {
        this.restrictedTicks = 0;
    }

    public void restrictOpening() {
        this.restrictedTicks++;
    }

    public int getRestrictedTicks() {
        return this.restrictedTicks;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public BlockPos getIndoorsPos() {
        return this.indoorsPos;
    }

    public int getIndoorsOffsetX() {
        return this.facing.getOffsetX() * 2;
    }

    public int getIndoorsOffsetZ() {
        return this.facing.getOffsetZ() * 2;
    }

    public int getLastUpdateTick() {
        return this.lastUpdateTick;
    }

    public void setLastUpdateTick(int tick) {
        this.lastUpdateTick = tick;
    }

    public boolean isOutsideVillage() {
        return this.isOutsideVillage;
    }

    public void setOutSideVillage(boolean isOutsideVillage) {
        this.isOutsideVillage = isOutsideVillage;
    }
}
