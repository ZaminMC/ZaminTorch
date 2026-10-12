package net.minecraft.client.world.color;

public class GrassColors {
    private static int[] colors = new int[65536];

    public static void set(int[] colors) {
        GrassColors.colors = colors;
    }

    public static int getColor(double temperature, double humidity) {
        humidity *= temperature;
        int i = (int)((1.0 - temperature) * 255.0);
        int j = (int)((1.0 - humidity) * 255.0);
        int k = j << 8 | i;
        return k > colors.length ? -65281 : colors[k];
    }
}
