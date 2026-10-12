package net.minecraft.util;

public class Long2ObjectHashMap<V> {
    private transient Long2ObjectHashMap.Node<V>[] nodes;
    private transient int size;
    private int cap;
    private int threshold;
    private final float loadFactor = 0.75F;
    private transient volatile int modCount;

    public Long2ObjectHashMap() {
        this.threshold = 3072;
        this.nodes = new Long2ObjectHashMap.Node[4096];
        this.cap = this.nodes.length - 1;
    }

    private static int hash(long key) {
        return hash((int)(key ^ key >>> 32));
    }

    private static int hash(int key) {
        key ^= key >>> 20 ^ key >>> 12;
        return key ^ key >>> 7 ^ key >>> 4;
    }

    private static int index(int hash, int cap) {
        return hash & cap;
    }

    public int size() {
        return this.size;
    }

    public V get(long key) {
        int i = hash(key);

        for (Long2ObjectHashMap.Node<V> node = this.nodes[index(i, this.cap)]; node != null; node = node.next) {
            if (node.key == key) {
                return node.value;
            }
        }

        return null;
    }

    public boolean contains(long key) {
        return this.getNode(key) != null;
    }

    final Long2ObjectHashMap.Node<V> getNode(long key) {
        int i = hash(key);

        for (Long2ObjectHashMap.Node<V> node = this.nodes[index(i, this.cap)]; node != null; node = node.next) {
            if (node.key == key) {
                return node;
            }
        }

        return null;
    }

    public void put(long key, V value) {
        int i = hash(key);
        int j = index(i, this.cap);

        for (Long2ObjectHashMap.Node<V> node = this.nodes[j]; node != null; node = node.next) {
            if (node.key == key) {
                node.value = value;
                return;
            }
        }

        this.modCount++;
        this.insertNode(i, key, value, j);
    }

    private void resize(int cap) {
        Long2ObjectHashMap.Node<V>[] node = this.nodes;
        int i = node.length;
        if (i == 1073741824) {
            this.threshold = Integer.MAX_VALUE;
        } else {
            Long2ObjectHashMap.Node<V>[] node1 = new Long2ObjectHashMap.Node[cap];
            this.addAll(node1);
            this.nodes = node1;
            this.cap = this.nodes.length - 1;
            this.threshold = (int)(cap * this.loadFactor);
        }
    }

    private void addAll(Long2ObjectHashMap.Node<V>[] nodes) {
        Long2ObjectHashMap.Node<V>[] node = this.nodes;
        int i = nodes.length;

        for (int j = 0; j < node.length; j++) {
            Long2ObjectHashMap.Node<V> node1 = node[j];
            if (node1 != null) {
                node[j] = null;

                while (true) {
                    Long2ObjectHashMap.Node<V> node2 = node1.next;
                    int k = index(node1.hash, i - 1);
                    node1.next = nodes[k];
                    nodes[k] = node1;
                    node1 = node2;
                    if (node1 == null) {
                        break;
                    }
                }
            }
        }
    }

    public V remove(long key) {
        Long2ObjectHashMap.Node<V> node = this.removeNode(key);
        return node == null ? null : node.value;
    }

    final Long2ObjectHashMap.Node<V> removeNode(long key) {
        int i = hash(key);
        int j = index(i, this.cap);
        Long2ObjectHashMap.Node<V> node = this.nodes[j];
        Long2ObjectHashMap.Node<V> node1 = node;

        while (node1 != null) {
            Long2ObjectHashMap.Node<V> node2 = node1.next;
            if (node1.key == key) {
                this.modCount++;
                this.size--;
                if (node == node1) {
                    this.nodes[j] = node2;
                } else {
                    node.next = node2;
                }

                return node1;
            }

            node = node1;
            node1 = node2;
        }

        return node1;
    }

    private void insertNode(int hash, long key, V value, int index) {
        Long2ObjectHashMap.Node<V> node = this.nodes[index];
        this.nodes[index] = new Long2ObjectHashMap.Node<>(hash, key, value, node);
        if (this.size++ >= this.threshold) {
            this.resize(2 * this.nodes.length);
        }
    }

    static class Node<V> {
        final long key;
        V value;
        Long2ObjectHashMap.Node<V> next;
        final int hash;

        Node(int hash, long key, V value, Long2ObjectHashMap.Node<V> next) {
            this.value = value;
            this.next = next;
            this.key = key;
            this.hash = hash;
        }

        public final long getKey() {
            return this.key;
        }

        public final V getValue() {
            return this.value;
        }

        @Override
        public final boolean equals(Object object) {
            if (!(object instanceof Long2ObjectHashMap.Node)) {
                return false;
            }

            Long2ObjectHashMap.Node<V> node = (Long2ObjectHashMap.Node<V>)object;
            Object objectx = this.getKey();
            Object object1 = node.getKey();
            if (objectx == object1 || objectx != null && objectx.equals(object1)) {
                Object object2 = this.getValue();
                Object object3 = node.getValue();
                if (object2 == object3 || object2 != null && object2.equals(object3)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public final int hashCode() {
            return Long2ObjectHashMap.hash(this.key);
        }

        @Override
        public final String toString() {
            return this.getKey() + "=" + this.getValue();
        }
    }
}
