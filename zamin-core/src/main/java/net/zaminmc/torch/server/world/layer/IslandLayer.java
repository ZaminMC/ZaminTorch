package net.zaminmc.torch.server.world.layer;

/**
 * The chain's root (IslandLayer 1:1): the 1-in-10 land roll with the
 * origin pin — the (0,0) cell is ALWAYS land (the spawn continent).
 */
public class IslandLayer extends Layer {

    public IslandLayer(long seed) {
        super(seed);
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] area = IntCache.push(sizeX * sizeZ);
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(x + j, z + i);
                area[j + i * sizeX] = this.nextInt(10) == 0 ? 1 : 0;
            }
        }
        if (x > -sizeX && x <= 0 && z > -sizeZ && z <= 0) {
            area[-x + -z * sizeX] = 1;
        }
        return area;
    }
}
