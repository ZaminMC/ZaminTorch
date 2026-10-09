package net.minecraft.client.util;

public class IntegerBuffer {
    private static final Integer[] INTEGERS = new Integer[65535];

    public static Integer get(int i) {
        return i > 0 && i < INTEGERS.length ? INTEGERS[i] : i;
    }

    static {
        int i = 0;

        for (int j = INTEGERS.length; i < j; i++) {
            INTEGERS[i] = i;
        }
    }
}
