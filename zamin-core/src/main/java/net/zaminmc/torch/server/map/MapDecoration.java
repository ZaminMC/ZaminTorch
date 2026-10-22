package net.zaminmc.torch.server.map;

/**
 * One map icon (the reference's {@code world/map/MapDecoration.java} 1:1):
 * the type byte (0 the player's own marker, 1 the item frame's, 6 the
 * off-map border arrow), the -128..127 canvas coordinates, and the
 * sixteen-step rotation byte — the whole wire form the 0x34 packet's icon
 * rows carry.
 *
 * <p>Immutable and value-equal exactly like the reference's own
 * {@code equals}/{@code hashCode} (the type/rotation/x/y tuple).</p>
 */
public final class MapDecoration {

    private final byte type;
    private final byte x;
    private final byte y;
    private final byte rotation;

    public MapDecoration(byte type, byte x, byte y, byte rotation) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
    }

    public MapDecoration(MapDecoration decoration) {
        this.type = decoration.type;
        this.x = decoration.x;
        this.y = decoration.y;
        this.rotation = decoration.rotation;
    }

    public byte getType() {
        return type;
    }

    public byte getX() {
        return x;
    }

    public byte getY() {
        return y;
    }

    public byte getRotation() {
        return rotation;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MapDecoration decoration)) {
            return false;
        }
        return this.type == decoration.type && this.rotation == decoration.rotation
                && this.x == decoration.x && this.y == decoration.y;
    }

    @Override
    public int hashCode() {
        int i = this.type;
        i = 31 * i + this.x;
        i = 31 * i + this.y;
        return 31 * i + this.rotation;
    }
}
