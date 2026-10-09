package net.minecraft.world.biome.layer;

import net.minecraft.world.biome.IntCache;

public class AddEdgeLayer extends Layer {
    private final AddEdgeLayer.Mode mode;

    public AddEdgeLayer(long seed, Layer parent, AddEdgeLayer.Mode mode) {
        super(seed);
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        switch (this.mode) {
            case COOL_WARM:
            default:
                return this.nextValuesCooldWarm(x, z, sizeX, sizeZ);
            case HEAT_ICE:
                return this.nextValuesHeatIce(x, z, sizeX, sizeZ);
            case SPECIAL:
                return this.nextValuesSpecial(x, z, sizeX, sizeZ);
        }
    }

    private int[] nextValuesCooldWarm(int x, int z, int width, int length) {
        int i = x - 1;
        int j = z - 1;
        int k = 1 + width + 1;
        int l = 1 + length + 1;
        int[] aint = this.parent.getArea(i, j, k, l);
        int[] aint1 = IntCache.push(width * length);

        for (int i1 = 0; i1 < length; i1++) {
            for (int j1 = 0; j1 < width; j1++) {
                this.setChunkSeed(j1 + x, i1 + z);
                int k1 = aint[j1 + 1 + (i1 + 1) * k];
                if (k1 == 1) {
                    int l1 = aint[j1 + 1 + (i1 + 1 - 1) * k];
                    int i2 = aint[j1 + 1 + 1 + (i1 + 1) * k];
                    int j2 = aint[j1 + 1 - 1 + (i1 + 1) * k];
                    int k2 = aint[j1 + 1 + (i1 + 1 + 1) * k];
                    boolean flag = l1 == 3 || i2 == 3 || j2 == 3 || k2 == 3;
                    boolean flag1 = l1 == 4 || i2 == 4 || j2 == 4 || k2 == 4;
                    if (flag || flag1) {
                        k1 = 2;
                    }
                }

                aint1[j1 + i1 * width] = k1;
            }
        }

        return aint1;
    }

    private int[] nextValuesHeatIce(int x, int z, int width, int length) {
        int i = x - 1;
        int j = z - 1;
        int k = 1 + width + 1;
        int l = 1 + length + 1;
        int[] aint = this.parent.getArea(i, j, k, l);
        int[] aint1 = IntCache.push(width * length);

        for (int i1 = 0; i1 < length; i1++) {
            for (int j1 = 0; j1 < width; j1++) {
                int k1 = aint[j1 + 1 + (i1 + 1) * k];
                if (k1 == 4) {
                    int l1 = aint[j1 + 1 + (i1 + 1 - 1) * k];
                    int i2 = aint[j1 + 1 + 1 + (i1 + 1) * k];
                    int j2 = aint[j1 + 1 - 1 + (i1 + 1) * k];
                    int k2 = aint[j1 + 1 + (i1 + 1 + 1) * k];
                    boolean flag = l1 == 2 || i2 == 2 || j2 == 2 || k2 == 2;
                    boolean flag1 = l1 == 1 || i2 == 1 || j2 == 1 || k2 == 1;
                    if (flag1 || flag) {
                        k1 = 3;
                    }
                }

                aint1[j1 + i1 * width] = k1;
            }
        }

        return aint1;
    }

    private int[] nextValuesSpecial(int x, int z, int width, int length) {
        int[] aint = this.parent.getArea(x, z, width, length);
        int[] aint1 = IntCache.push(width * length);

        for (int i = 0; i < length; i++) {
            for (int j = 0; j < width; j++) {
                this.setChunkSeed(j + x, i + z);
                int k = aint[j + i * width];
                if (k != 0 && this.nextInt(13) == 0) {
                    k |= 1 + this.nextInt(15) << 8 & 3840;
                }

                aint1[j + i * width] = k;
            }
        }

        return aint1;
    }

    public enum Mode {
        COOL_WARM,
        HEAT_ICE,
        SPECIAL;
    }
}
