package net.minecraft.world.chunk;

public class ChunkNibbleStorage {
    private final byte[] data;

    public ChunkNibbleStorage() {
        this.data = new byte[2048];
    }

    public ChunkNibbleStorage(byte[] data) {
        this.data = data;
        if (data.length != 2048) {
            throw new IllegalArgumentException("ChunkNibbleArrays should be 2048 bytes not: " + data.length);
        }
    }

    public int get(int x, int y, int z) {
        return this.get(this.getId(x, y, z));
    }

    public void set(int x, int y, int z, int value) {
        this.set(this.getId(x, y, z), value);
    }

    private int getId(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    public int get(int id) {
        int i = this.getIndex(id);
        return this.isLower(id) ? this.data[i] & 15 : this.data[i] >> 4 & 15;
    }

    public void set(int id, int value) {
        int i = this.getIndex(id);
        if (this.isLower(id)) {
            this.data[i] = (byte)(this.data[i] & 240 | value & 15);
        } else {
            this.data[i] = (byte)(this.data[i] & 15 | (value & 15) << 4);
        }
    }

    private boolean isLower(int id) {
        return (id & 1) == 0;
    }

    private int getIndex(int id) {
        return id >> 1;
    }

    public byte[] getData() {
        return this.data;
    }
}
