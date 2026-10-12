package net.minecraft.util;

import java.util.Collection;
import java.util.Random;

public class WeightedPicker {
    public static int getTotalWeight(Collection<? extends WeightedPicker.Entry> entries) {
        int i = 0;

        for (WeightedPicker.Entry weightedpicker$entry : entries) {
            i += weightedpicker$entry.weight;
        }

        return i;
    }

    public static <T extends WeightedPicker.Entry> T pick(Random random, Collection<T> entries, int totalWeight) {
        if (totalWeight <= 0) {
            throw new IllegalArgumentException();
        }

        int i = random.nextInt(totalWeight);
        return pick(entries, i);
    }

    public static <T extends WeightedPicker.Entry> T pick(Collection<T> entries, int weight) {
        for (T t : entries) {
            weight -= t.weight;
            if (weight < 0) {
                return t;
            }
        }

        return null;
    }

    public static <T extends WeightedPicker.Entry> T pick(Random random, Collection<T> entries) {
        return pick(random, entries, getTotalWeight(entries));
    }

    public static class Entry {
        protected int weight;

        public Entry(int weight) {
            this.weight = weight;
        }
    }
}
