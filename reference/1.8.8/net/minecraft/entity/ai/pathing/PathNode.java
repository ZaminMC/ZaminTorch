package net.minecraft.entity.ai.pathing;

import net.minecraft.util.math.MathHelper;

public class PathNode {
    public final int x;
    public final int y;
    public final int z;
    private final int hash;
    int heapIndex = -1;
    float distanceFromStart;
    float distanceToTarget;
    float weight;
    PathNode prev;
    public boolean visited;

    public PathNode(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = hash(x, y, z);
    }

    public static int hash(int x, int y, int z) {
        return y & 0xFF | (x & 32767) << 8 | (z & 32767) << 24 | (x < 0 ? Integer.MIN_VALUE : 0) | (z < 0 ? 32768 : 0);
    }

    public float distanceTo(PathNode other) {
        float f = other.x - this.x;
        float f1 = other.y - this.y;
        float f2 = other.z - this.z;
        return MathHelper.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public float squaredDistanceTo(PathNode other) {
        float f = other.x - this.x;
        float f1 = other.y - this.y;
        float f2 = other.z - this.z;
        return f * f + f1 * f1 + f2 * f2;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof PathNode)) {
            return false;
        }

        PathNode pathnode = (PathNode)object;
        return this.hash == pathnode.hash && this.x == pathnode.x && this.y == pathnode.y && this.z == pathnode.z;
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    public boolean isInHeap() {
        return this.heapIndex >= 0;
    }

    @Override
    public String toString() {
        return this.x + ", " + this.y + ", " + this.z;
    }
}
