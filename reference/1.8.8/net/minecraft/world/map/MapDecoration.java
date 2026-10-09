package net.minecraft.world.map;

public class MapDecoration {
    private byte type;
    private byte x;
    private byte y;
    private byte rotation;

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
        return this.type;
    }

    public byte getX() {
        return this.x;
    }

    public byte getY() {
        return this.y;
    }

    public byte getRotation() {
        return this.rotation;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof MapDecoration)) {
            return false;
        }

        MapDecoration mapdecoration = (MapDecoration)object;
        return this.type == mapdecoration.type && this.rotation == mapdecoration.rotation && this.x == mapdecoration.x && this.y == mapdecoration.y;
    }

    @Override
    public int hashCode() {
        int i = this.type;
        i = 31 * i + this.x;
        i = 31 * i + this.y;
        return 31 * i + this.rotation;
    }
}
