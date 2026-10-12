package net.minecraft.world.gen.structure;

import com.google.common.base.Objects;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

public class StructureBox {
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;

    public StructureBox() {
    }

    public StructureBox(int[] coords) {
        if (coords.length == 6) {
            this.minX = coords[0];
            this.minY = coords[1];
            this.minZ = coords[2];
            this.maxX = coords[3];
            this.maxY = coords[4];
            this.maxZ = coords[5];
        }
    }

    public static StructureBox infinite() {
        return new StructureBox(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static StructureBox orient(int x, int y, int z, int offsetX, int offsetY, int offsetZ, int sizeX, int sizeY, int sizeZ, Direction dir) {
        switch (dir) {
            case NORTH:
                return new StructureBox(x + offsetX, y + offsetY, z - sizeZ + 1 + offsetZ, x + sizeX - 1 + offsetX, y + sizeY - 1 + offsetY, z + offsetZ);
            case SOUTH:
                return new StructureBox(x + offsetX, y + offsetY, z + offsetZ, x + sizeX - 1 + offsetX, y + sizeY - 1 + offsetY, z + sizeZ - 1 + offsetZ);
            case WEST:
                return new StructureBox(x - sizeZ + 1 + offsetZ, y + offsetY, z + offsetX, x + offsetZ, y + sizeY - 1 + offsetY, z + sizeX - 1 + offsetX);
            case EAST:
                return new StructureBox(x + offsetZ, y + offsetY, z + offsetX, x + sizeZ - 1 + offsetZ, y + sizeY - 1 + offsetY, z + sizeX - 1 + offsetX);
            default:
                return new StructureBox(x + offsetX, y + offsetY, z + offsetZ, x + sizeX - 1 + offsetX, y + sizeY - 1 + offsetY, z + sizeZ - 1 + offsetZ);
        }
    }

    public static StructureBox of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new StructureBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public StructureBox(StructureBox box) {
        this.minX = box.minX;
        this.minY = box.minY;
        this.minZ = box.minZ;
        this.maxX = box.maxX;
        this.maxY = box.maxY;
        this.maxZ = box.maxZ;
    }

    public StructureBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public StructureBox(Vec3i vec1, Vec3i vec2) {
        this.minX = Math.min(vec1.getX(), vec2.getX());
        this.minY = Math.min(vec1.getY(), vec2.getY());
        this.minZ = Math.min(vec1.getZ(), vec2.getZ());
        this.maxX = Math.max(vec1.getX(), vec2.getX());
        this.maxY = Math.max(vec1.getY(), vec2.getY());
        this.maxZ = Math.max(vec1.getZ(), vec2.getZ());
    }

    public StructureBox(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.minY = 1;
        this.maxY = 512;
    }

    public boolean intersects(StructureBox box) {
        return this.maxX >= box.minX
            && this.minX <= box.maxX
            && this.maxZ >= box.minZ
            && this.minZ <= box.maxZ
            && this.maxY >= box.minY
            && this.minY <= box.maxY;
    }

    public boolean intersects(int minX, int minZ, int maxX, int maxZ) {
        return this.maxX >= minX && this.minX <= maxX && this.maxZ >= minZ && this.minZ <= maxZ;
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

    public boolean contains(Vec3i vec) {
        return vec.getX() >= this.minX
            && vec.getX() <= this.maxX
            && vec.getZ() >= this.minZ
            && vec.getZ() <= this.maxZ
            && vec.getY() >= this.minY
            && vec.getY() <= this.maxY;
    }

    public Vec3i getDiagonal() {
        return new Vec3i(this.maxX - this.minX, this.maxY - this.minY, this.maxZ - this.minZ);
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

    public Vec3i getCenter() {
        return new BlockPos(
            this.minX + (this.maxX - this.minX + 1) / 2, this.minY + (this.maxY - this.minY + 1) / 2, this.minZ + (this.maxZ - this.minZ + 1) / 2
        );
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
            .add("x0", this.minX)
            .add("y0", this.minY)
            .add("z0", this.minZ)
            .add("x1", this.maxX)
            .add("y1", this.maxY)
            .add("z1", this.maxZ)
            .toString();
    }

    public NbtIntArray toNbt() {
        return new NbtIntArray(new int[]{this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ});
    }
}
