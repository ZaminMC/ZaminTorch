package net.minecraft.world.biome;

import com.google.common.collect.Lists;
import java.util.List;

public class IntCache {
    private static int num = 256;
    private static List<int[]> tcache = Lists.newArrayList();
    private static List<int[]> tallocated = Lists.newArrayList();
    private static List<int[]> cache = Lists.newArrayList();
    private static List<int[]> allocated = Lists.newArrayList();

    public static synchronized int[] push(int size) {
        if (size <= 256) {
            if (tcache.isEmpty()) {
                int[] aint4 = new int[256];
                tallocated.add(aint4);
                return aint4;
            } else {
                int[] aint3 = tcache.remove(tcache.size() - 1);
                tallocated.add(aint3);
                return aint3;
            }
        } else if (size > num) {
            num = size;
            cache.clear();
            allocated.clear();
            int[] aint2 = new int[num];
            allocated.add(aint2);
            return aint2;
        } else if (cache.isEmpty()) {
            int[] aint1 = new int[num];
            allocated.add(aint1);
            return aint1;
        } else {
            int[] aint = cache.remove(cache.size() - 1);
            allocated.add(aint);
            return aint;
        }
    }

    public static synchronized void pop() {
        if (!cache.isEmpty()) {
            cache.remove(cache.size() - 1);
        }

        if (!tcache.isEmpty()) {
            tcache.remove(tcache.size() - 1);
        }

        cache.addAll(allocated);
        tcache.addAll(tallocated);
        allocated.clear();
        tallocated.clear();
    }

    public static synchronized String getDebugInfo() {
        return "cache: " + cache.size() + ", tcache: " + tcache.size() + ", allocated: " + allocated.size() + ", tallocated: " + tallocated.size();
    }
}
