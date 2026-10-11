package net.zaminmc.torch.server.world.structure;

/**
 * The structure bounding box (Slice 12a — the reference port of
 * gen/structure/StructureBox.java, 1:1): the mutable min/max cell pair
 * with the reference's own intersection/union/move semantics.
 *
 * <p>The four-field constructor keeps the reference's own quirk: a
 * chunk-clip box carries {@code minY = 1, maxY = 512} (wider than the
 * world — the clip is horizontal).</p>
 */
public final class StructureBox {
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;

    public StructureBox() {
    }

    public StructureBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * The chunk-clip constructor (the reference's own 4-field shape): a
     * horizontal box with the 1..512 vertical span.
     */
    public StructureBox(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.minY = 1;
        this.maxY = 512;
    }

    public static StructureBox infinite() {
        return new StructureBox(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public boolean intersects(StructureBox box) {
        return this.maxX >= box.minX
                && this.minX <= box.maxX
                && this.maxZ >= box.minZ
                && this.minZ <= box.maxZ
                && this.maxY >= box.minY
                && this.minY <= box.maxY;
    }

    public void union(StructureBox box) {
        this.minX = Math.min(this.minX, box.minX);
        this.minY = Math.min(this.minY, box.minY);
        this.minZ = Math.min(this.minZ, box.minZ);
        this.maxX = Math.max(this.maxX, box.maxX);
        this.maxY = Math.max(this.maxY, box.maxY);
        this.maxZ = Math.max(this.maxZ, box.maxZ);
    }

    public void move(int dx, int dy, int dz) {
        this.minX += dx;
        this.minY += dy;
        this.minZ += dz;
        this.maxX += dx;
        this.maxY += dy;
        this.maxZ += dz;
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX
                && x <= this.maxX
                && z >= this.minZ
                && z <= this.maxZ
                && y >= this.minY
                && y <= this.maxY;
    }

    public int getSpanX() {
        return this.maxX - this.minX + 1;
    }

    public int getSpanY() {
        return this.maxY - this.minY + 1;
    }

    public int getSpanZ() {
        return this.maxZ - this.minZ + 1;
    }

    @Override
    public String toString() {
        return "StructureBox[" + minX + ", " + minY + ", " + minZ + " -> "
                + maxX + ", " + maxY + ", " + maxZ + "]";
    }
}
