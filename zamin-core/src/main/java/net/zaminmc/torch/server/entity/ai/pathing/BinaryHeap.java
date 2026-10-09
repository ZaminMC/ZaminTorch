package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * The A* priority queue — the exact vanilla 1.8.8 BinaryHeap (reference/
 * 1.8.8 net/minecraft/entity/ai/pathing/BinaryHeap.java): the node carries
 * its own slot ({@code heapIndex}) so decrease/increase-key are O(log n)
 * through the same upHeap/shiftDown primitives, the historical growth
 * (double when full) and the "OW KNOWS!" double-insert guard included.
 */
public class BinaryHeap {
    private PathNode[] heap = new PathNode[1024];
    private int size;

    public PathNode insert(PathNode node) {
        if (node.heapIndex >= 0) {
            throw new IllegalStateException("OW KNOWS!");
        }

        if (this.size == this.heap.length) {
            PathNode[] apathnode = new PathNode[this.size << 1];
            System.arraycopy(this.heap, 0, apathnode, 0, this.size);
            this.heap = apathnode;
        }

        this.heap[this.size] = node;
        node.heapIndex = this.size;
        this.upHeap(this.size++);
        return node;
    }

    public void clear() {
        this.size = 0;
    }

    public PathNode pop() {
        PathNode pathnode = this.heap[0];
        this.heap[0] = this.heap[--this.size];
        this.heap[this.size] = null;
        if (this.size > 0) {
            this.shiftDown(0);
        }

        pathnode.heapIndex = -1;
        return pathnode;
    }

    public void setWeight(PathNode node, float weight) {
        float f = node.weight;
        node.weight = weight;
        if (weight < f) {
            this.upHeap(node.heapIndex);
        } else {
            this.shiftDown(node.heapIndex);
        }
    }

    private void upHeap(int index) {
        PathNode pathnode = this.heap[index];
        float f = pathnode.weight;

        while (index > 0) {
            int i = index - 1 >> 1;
            PathNode pathnode1 = this.heap[i];
            if (!(f < pathnode1.weight)) {
                break;
            }

            this.heap[index] = pathnode1;
            pathnode1.heapIndex = index;
            index = i;
        }

        this.heap[index] = pathnode;
        pathnode.heapIndex = index;
    }

    private void shiftDown(int index) {
        PathNode pathnode = this.heap[index];
        float f = pathnode.weight;

        while (true) {
            int i = 1 + (index << 1);
            int j = i + 1;
            if (i >= this.size) {
                break;
            }

            PathNode pathnode1 = this.heap[i];
            float f1 = pathnode1.weight;
            PathNode pathnode2;
            float f2;
            if (j >= this.size) {
                pathnode2 = null;
                f2 = Float.POSITIVE_INFINITY;
            } else {
                pathnode2 = this.heap[j];
                f2 = pathnode2.weight;
            }

            if (f1 < f2) {
                if (!(f1 < f)) {
                    break;
                }

                this.heap[index] = pathnode1;
                pathnode1.heapIndex = index;
                index = i;
            } else {
                if (!(f2 < f)) {
                    break;
                }

                this.heap[index] = pathnode2;
                pathnode2.heapIndex = index;
                index = j;
            }
        }

        this.heap[index] = pathnode;
        pathnode.heapIndex = index;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }
}
