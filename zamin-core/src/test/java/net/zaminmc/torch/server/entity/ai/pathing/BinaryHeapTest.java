package net.zaminmc.torch.server.entity.ai.pathing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The A* priority queue — the exact vanilla BinaryHeap: weighted pops come
 * out ascending, the decrease-key floats the node to the top, the increase
 * sinks it, and a thousand random rekeys never lose a node.
 */
class BinaryHeapTest {

    @Test
    void weightedPopsComeOutAscending() {
        BinaryHeap heap = new BinaryHeap();
        PathNode a = new PathNode(1, 0, 1);
        PathNode b = new PathNode(2, 0, 2);
        PathNode c = new PathNode(3, 0, 3);
        // Seeds ride through setWeight on the heap (the field is package
        // scope exactly as vanilla ships it).
        heap.insert(a);
        heap.setWeight(a, 5.0f);
        heap.insert(b);
        heap.setWeight(b, 3.0f);
        heap.insert(c);
        heap.setWeight(c, 7.0f);
        assertEquals(b, heap.pop(), "the lightest pops first");
        assertEquals(a, heap.pop());
        assertEquals(c, heap.pop());
        assertTrue(heap.isEmpty());
    }

    @Test
    void decreaseKeyFloatsIncreaseKeySinks() {
        BinaryHeap heap = new BinaryHeap();
        PathNode a = new PathNode(1, 0, 1);
        PathNode b = new PathNode(2, 0, 2);
        PathNode c = new PathNode(3, 0, 3);
        heap.insert(a);
        heap.setWeight(a, 5.0f);
        heap.insert(b);
        heap.setWeight(b, 3.0f);
        heap.insert(c);
        heap.setWeight(c, 7.0f);
        heap.setWeight(c, 1.0f);
        assertEquals(c, heap.pop(), "the decreased node popped first");
        assertEquals(b, heap.pop());
        assertEquals(a, heap.pop());
    }

    @Test
    void aThousandRandomRekeysKeepEveryNodeAndTheOrder() {
        BinaryHeap heap = new BinaryHeap();
        List<PathNode> nodes = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            PathNode node = new PathNode(i % 10, i / 10, 0);
            nodes.add(node);
            heap.insert(node);
            heap.setWeight(node, random.nextInt(1000));
        }
        for (int i = 0; i < 1000; i++) {
            heap.setWeight(nodes.get(random.nextInt(nodes.size())), random.nextInt(1000));
        }
        float last = -1.0f;
        int popped = 0;
        while (!heap.isEmpty()) {
            PathNode node = heap.pop();
            assertTrue(node.weight >= last, "the pops stay ascending");
            last = node.weight;
            popped++;
        }
        assertEquals(100, popped, "no node was lost through the rekeys");
    }
}
