package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The mushroom isle (AddMushroomIslandLayer 1:1): an ocean cell whose
 * 3x3 neighborhood is all ocean rolls 1-in-100 to become MUSHROOM_ISLAND
 * — the rare isle.
 */
public class AddMushroomIslandLayer extends Layer {

    public AddMushroomIslandLayer(long seed, Layer parent) {
        super(seed);
        this.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int i = x - 1;
        int j = z - 1;
        int k = sizeX + 2;
        int l = sizeZ + 2;
        int[] parentArea = this.parent.getArea(i, j, k, l);
        int[] area = IntCache.push(sizeX * sizeZ);
        for (int i1 = 0; i1 < sizeZ; i1++) {
            for (int j1 = 0; j1 < sizeX; j1++) {
                int k1 = parentArea[j1 + 0 + (i1 + 0) * k];
                int l1 = parentArea[j1 + 2 + (i1 + 0) * k];
                int i2 = parentArea[j1 + 0 + (i1 + 2) * k];
                int j2 = parentArea[j1 + 2 + (i1 + 2) * k];
                int k2 = parentArea[j1 + 1 + (i1 + 1) * k];
                this.setChunkSeed(j1 + x, i1 + z);
                if (k2 == 0 && k1 == 0 && l1 == 0 && i2 == 0 && j2 == 0
                        && this.nextInt(100) == 0) {
                    area[j1 + i1 * sizeX] = Biome.MUSHROOM_ISLAND.legacyId();
                } else {
                    area[j1 + i1 * sizeX] = k2;
                }
            }
        }
        return area;
    }
}
