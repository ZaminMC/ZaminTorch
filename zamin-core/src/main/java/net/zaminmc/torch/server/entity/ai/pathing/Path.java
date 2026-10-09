package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * The found route — the exact vanilla 1.8.8 Path (reference/1.8.8
 * net/minecraft/entity/ai/pathing/Path.java): the node array, the walking
 * current index, the truncate used by sun-avoidance trimming and the
 * waypoint centering rule ({@code node + (int)(width + 1) * 0.5} — the mob
 * walks the cell's width-adjusted center, not its corner).
 */
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

    /**
     * The waypoint for the node at {@code index}: X/Z at the width-adjusted
     * cell center ({@code (int)(width + 1) * 0.5} — a 0.6-wide mob walks
     * half a block off the cell corner), Y at the node's feet level.
     */
    public double[] getPos(double entityWidth, int index) {
        double d0 = this.nodes[index].x + (int) (entityWidth + 1.0F) * 0.5;
        double d1 = this.nodes[index].y;
        double d2 = this.nodes[index].z + (int) (entityWidth + 1.0F) * 0.5;
        return new double[]{d0, d1, d2};
    }

    public double[] getCurrentPos(double entityWidth) {
        return this.getPos(entityWidth, this.currentIndex);
    }

    public boolean equals(Path other) {
        if (other == null) {
            return false;
        }

        if (other.nodes.length != this.nodes.length) {
            return false;
        }

        for (int i = 0; i < this.nodes.length; i++) {
            if (this.nodes[i].x != other.nodes[i].x || this.nodes[i].y != other.nodes[i].y
                    || this.nodes[i].z != other.nodes[i].z) {
                return false;
            }
        }

        return true;
    }

    public boolean isOnTarget(double x, double z) {
        PathNode pathnode = this.getTarget();
        return pathnode != null && pathnode.x == (int) x && pathnode.z == (int) z;
    }
}
