package net.minecraft.util;

public class Int2ObjectHashMap<V> {
    private transient Int2ObjectHashMap.Node<V>[] nodes;
    private transient int size;
    private int threshold;
    private final float loadFactor = 0.75F;

    public Int2ObjectHashMap() {
        this.threshold = 12;
        this.nodes = new Int2ObjectHashMap.Node[16];
    }

    private static int hash(int key) {
        key ^= key >>> 20 ^ key >>> 12;
        return key ^ key >>> 7 ^ key >>> 4;
    }

    private static int index(int hash, int cap) {
        return hash & cap - 1;
    }

    public V get(int key) {
        int i = hash(key);

        for (Int2ObjectHashMap.Node<V> node = this.nodes[index(i, this.nodes.length)]; node != null; node = node.next) {
            if (node.key == key) {
                return node.value;
            }
        }

        return null;
    }

    public boolean containsKey(int key) {
        return this.getNode(key) != null;
    }

    final Int2ObjectHashMap.Node<V> getNode(int key) {
        int i = hash(key);

        for (Int2ObjectHashMap.Node<V> node = this.nodes[index(i, this.nodes.length)]; node != null; node = node.next) {
            if (node.key == key) {
                return node;
            }
        }

        return null;
    }

    public void put(int key, V value) {
        int i = hash(key);
        int j = index(i, this.nodes.length);

        for (Int2ObjectHashMap.Node<V> node = this.nodes[j]; node != null; node = node.next) {
            if (node.key == key) {
                node.value = value;
                return;
            }
        }

        this.insertNode(i, key, value, j);
    }

    private void resize(int size) {
        Int2ObjectHashMap.Node<V>[] node = this.nodes;
        int i = node.length;
        if (i == 1073741824) {
            this.threshold = Integer.MAX_VALUE;
        } else {
            Int2ObjectHashMap.Node<V>[] node1 = new Int2ObjectHashMap.Node[size];
            this.addAll(node1);
            this.nodes = node1;
            this.threshold = (int)(size * this.loadFactor);
        }
    }

    private void addAll(Int2ObjectHashMap.Node<V>[] nodes) {
        Int2ObjectHashMap.Node<V>[] node = this.nodes;
        int i = nodes.length;

        for (int j = 0; j < node.length; j++) {
            Int2ObjectHashMap.Node<V> node1 = node[j];
            if (node1 != null) {
                node[j] = null;

                while (true) {
                    Int2ObjectHashMap.Node<V> node2 = node1.next;
                    int k = index(node1.hash, i);
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

    public V remove(int key) {
        Int2ObjectHashMap.Node<V> node = this.removeNode(key);
        return node == null ? null : node.value;
    }

    final Int2ObjectHashMap.Node<V> removeNode(int key) {
        int i = hash(key);
        int j = index(i, this.nodes.length);
        Int2ObjectHashMap.Node<V> node = this.nodes[j];
        Int2ObjectHashMap.Node<V> node1 = node;

        while (node1 != null) {
            Int2ObjectHashMap.Node<V> node2 = node1.next;
            if (node1.key == key) {
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

    public void clear() {
        Int2ObjectHashMap.Node<V>[] node = this.nodes;

        for (int i = 0; i < node.length; i++) {
            node[i] = null;
        }

        this.size = 0;
    }

    private void insertNode(int hash, int key, V value, int index) {
        Int2ObjectHashMap.Node<V> node = this.nodes[index];
        this.nodes[index] = new Int2ObjectHashMap.Node<>(hash, key, value, node);
        if (this.size++ >= this.threshold) {
            this.resize(2 * this.nodes.length);
        }
    }

    static class Node<V> {
        final int key;
        V value;
        Int2ObjectHashMap.Node<V> next;
        final int hash;

        Node(int hash, int key, V value, Int2ObjectHashMap.Node<V> next) {
            this.value = value;
            this.next = next;
            this.key = key;
            this.hash = hash;
        }

        public final int getKey() {
            return this.key;
        }

        public final V getValue() {
            return this.value;
        }

        @Override
        public final boolean equals(Object object) {
            if (!(object instanceof Int2ObjectHashMap.Node)) {
                return false;
            }

            Int2ObjectHashMap.Node<V> node = (Int2ObjectHashMap.Node<V>)object;
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
            return Int2ObjectHashMap.hash(this.key);
        }

        @Override
        public final String toString() {
            return this.getKey() + "=" + this.getValue();
        }
    }
}
