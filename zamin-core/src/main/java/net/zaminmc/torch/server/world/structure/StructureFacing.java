package net.zaminmc.torch.server.world.structure;

/**
 * The horizontal facing (Slice 12a — the reference's {@code Direction}
 * subset the structure pieces use): the four horizontals with the
 * reference's own horizontal ids (SOUTH 0, WEST 1, NORTH 2, EAST 3 —
 * Direction.java lines 14-17's third ctor field) and the opposite
 * mapping.
 */
public enum StructureFacing {
    SOUTH(0),
    WEST(1),
    NORTH(2),
    EAST(3);

    private final int horizontalId;

    StructureFacing(int horizontalId) {
        this.horizontalId = horizontalId;
    }

    /** The reference's getIdHorizontal. */
    public int horizontalId() {
        return horizontalId;
    }

    /** The reference's byIdHorizontal (unknown ids answer NORTH). */
    public static StructureFacing byHorizontalId(int id) {
        for (StructureFacing facing : values()) {
            if (facing.horizontalId == id) {
                return facing;
            }
        }
        return NORTH;
    }
}
