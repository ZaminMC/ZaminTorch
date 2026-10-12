package net.minecraft.entity.ai.pathing;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class Path {
    private final PathNode[] nodes;
    private int currentIndex;
    private int length;

    public Path(PathNode[] nodes) {
        this.nodes = nodes;
        this.length = nodes.length;
    }

    public void advance() {
        this.currentIndex++;
    }

    public boolean isDone() {
        return this.currentIndex >= this.length;
    }

    public PathNode getTarget() {
        return this.length > 0 ? this.nodes[this.length - 1] : null;
    }

    public PathNode getNode(int index) {
        return this.nodes[index];
    }

    public int length() {
        return this.length;
    }

    public void truncate(int length) {
        this.length = length;
    }

    public int getCurrentIndex() {
        return this.currentIndex;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    public Vec3d getPos(Entity target, int index) {
        double d0 = this.nodes[index].x + (int)(target.width + 1.0F) * 0.5;
        double d1 = this.nodes[index].y;
        double d2 = this.nodes[index].z + (int)(target.width + 1.0F) * 0.5;
        return new Vec3d(d0, d1, d2);
    }

    public Vec3d getCurrentPos(Entity target) {
        return this.getPos(target, this.currentIndex);
    }

    public boolean equals(Path other) {
        if (other == null) {
            return false;
        }

        if (other.nodes.length != this.nodes.length) {
            return false;
        }

        for (int i = 0; i < this.nodes.length; i++) {
            if (this.nodes[i].x != other.nodes[i].x || this.nodes[i].y != other.nodes[i].y || this.nodes[i].z != other.nodes[i].z) {
                return false;
            }
        }

        return true;
    }

    public boolean isOnTarget(Vec3d pos) {
        PathNode pathnode = this.getTarget();
        return pathnode != null && pathnode.x == (int)pos.x && pathnode.z == (int)pos.z;
    }
}
