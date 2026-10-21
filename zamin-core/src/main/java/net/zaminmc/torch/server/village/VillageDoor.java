package net.zaminmc.torch.server.village;

import net.zaminmc.torch.block.Direction;

/**
 * The village's door row (VIII10 — VillageDoor 1:1, the reference's own
 * class whole): the door cell, the INSIDE facing (the side the census
 * walk derives — the direction with the smaller sky distance, the door
 * faces its darker half), the indoors position two cells along it, the
 * last-update tick the 1200-tick staleness walk reads, the
 * outside-village flag, and the restricted-ticks counter the door's
 * own over-use penalty carries (the engine's stroll family reads none
 * of it yet — the row lands whole, the readers ledger with the wander
 * goal family the engine does not carry).
 *
 * <p>Safe from any thread (an immutable position + the mutable tick
 * rows guarded by the village system's own tick lane).</p>
 */
public final class VillageDoor {

    private final int x;
    private final int y;
    private final int z;
    /** The INSIDE facing (the reference's {@code facing}). */
    private final Direction facing;
    /** The reference's lastUpdateTick (the staleness walk's own row). */
    private int lastUpdateTick;
    /** The reference's isOutsideVillage (removed doors mark true). */
    private boolean outsideVillage;
    /** The reference's restrictedTicks (the over-use penalty). */
    private int restrictedTicks;

    /**
     * The persistence form (the reference's NBT row, SavedVillageData
     * lines 369-374): the cell + the IDX/IDZ offsets + the tick. The
     * offsets are the facing's own doubled ({@code offsetX * 2}).
     */
    public VillageDoor(int x, int y, int z, int indoorsOffsetX, int indoorsOffsetZ,
                       int lastVillageTick) {
        this(x, y, z, facingOf(indoorsOffsetX, indoorsOffsetZ), lastVillageTick);
    }

    /** The facing form (the reference's own ctor, lines 28-33). */
    public VillageDoor(int x, int y, int z, Direction facing, int lastVillageTick) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.lastUpdateTick = lastVillageTick;
    }

    /** The reference's getFacing (lines 18-26): the offset band's facing. */
    private static Direction facingOf(int dx, int dz) {
        if (dx < 0) {
            return Direction.WEST;
        } else if (dx > 0) {
            return Direction.EAST;
        } else {
            return dz < 0 ? Direction.NORTH : Direction.SOUTH;
        }
    }

    /** @return the door cell's x. */
    public int x() {
        return x;
    }

    /** @return the door cell's y. */
    public int y() {
        return y;
    }

    /** @return the door cell's z. */
    public int z() {
        return z;
    }

    /** @return the door's INSIDE facing. */
    public Direction facing() {
        return facing;
    }

    /**
     * squaredDistanceTo (lines 35-41): the integer-cast squared distance
     * to a cell.
     */
    public int squaredDistanceTo(int otherX, int otherY, int otherZ) {
        int dx = otherX - x;
        int dy = otherY - y;
        int dz = otherZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * squaredDistanceToIndoors (lines 43-45): the squared distance to the
     * indoors position (the door cell + the facing twice).
     */
    public int squaredDistanceToIndoors(int otherX, int otherY, int otherZ) {
        int inX = x + facing.offsetX() * 2;
        int inY = y + facing.offsetY() * 2;
        int inZ = z + facing.offsetZ() * 2;
        int dx = otherX - inX;
        int dy = otherY - inY;
        int dz = otherZ - inZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * isIndoors (lines 47-51): whether the position lies on the INSIDE
     * half-plane of the door's facing. THE REFERENCE'S OWN QUIRK, kept
     * 1:1: the second term reads {@code pos.getZ() - this.pos.getY()}
     * — the door's Y, not its Z (the famous vanilla bug, the decompile's
     * line 49 verbatim). No engine reader consults the row yet (the
     * wander family's own goals); the class lands whole with the
     * reference's own shape.
     */
    public boolean isIndoors(int posX, int posZ) {
        int i = posX - x;
        int j = posZ - y; // the reference's own Y-read (line 49)
        return i * facing.offsetX() + j * facing.offsetZ() >= 0;
    }

    /** resetRestrictedTicks (line 53). */
    public void resetRestrictedTicks() {
        restrictedTicks = 0;
    }

    /** restrictOpening (line 57): the over-use penalty's own counter. */
    public void restrictOpening() {
        restrictedTicks++;
    }

    /** getRestrictedTicks (line 61). */
    public int restrictedTicks() {
        return restrictedTicks;
    }

    /** @return the last-update tick (the staleness walk's read). */
    public int lastUpdateTick() {
        return lastUpdateTick;
    }

    /** setLastUpdateTick (lines 85-87): the census's own refresh. */
    public void setLastUpdateTick(int tick) {
        this.lastUpdateTick = tick;
    }

    /** isOutsideVillage (lines 89-91). */
    public boolean isOutsideVillage() {
        return outsideVillage;
    }

    /** setOutSideVillage (lines 93-95): the removal's own mark. */
    public void setOutsideVillage(boolean outsideVillage) {
        this.outsideVillage = outsideVillage;
    }

    /** The reference's getIndoorsOffsetX (line 73): the facing's own doubled. */
    public int indoorsOffsetX() {
        return facing.offsetX() * 2;
    }

    /** The reference's getIndoorsOffsetZ (line 78). */
    public int indoorsOffsetZ() {
        return facing.offsetZ() * 2;
    }
}
