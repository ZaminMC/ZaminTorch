package net.zaminmc.torch.server.world.layer;

/**
 * The river arm's seed (RiverInitLayer 1:1): every land cell rolls the
 * 2..300000 river marker (the (marker - 2) % 29 walk drives the curve
 * bands downstream); ocean passes through as 0.
 */
public class RiverInitLayer extends Layer {

    public RiverInitLayer(long seed, Layer parent) {
        super(seed);
        this.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] parentArea = this.parent.getArea(x, z, sizeX, sizeZ);
        int[] area = IntCache.push(sizeX * sizeZ);
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(j + x, i + z);
                area[j + i * sizeX] = parentArea[j + i * sizeX] > 0
                        ? this.nextInt(299999) + 2 : 0;
            }
        }
        return area;
    }
}
