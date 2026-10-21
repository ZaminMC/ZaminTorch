package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The biome dispatch (BiomeInitLayer 1:1, the DEFAULT type's tables):
 * the temperature band (1 temperate, 2 blend, 3 cool, 4 snow) picks its
 * table row; the special marker (bits 8-11, the AddEdge SPECIAL roll)
 * picks the mesa pair on temperate, the jungle on blend, the mega taiga
 * on cool; the mushroom isle passes through; ocean passes whole; any
 * other land value answers the mushroom isle (the chain never produces
 * one — the reference's own dead arm kept).
 */
public class BiomeInitLayer extends Layer {

    private final Biome[] warmBiomes = new Biome[]{Biome.DESERT,
            Biome.DESERT, Biome.DESERT, Biome.SAVANNA, Biome.SAVANNA,
            Biome.PLAINS};
    private final Biome[] temperateBiomes = new Biome[]{Biome.FOREST,
            Biome.ROOFED_FOREST, Biome.EXTREME_HILLS, Biome.PLAINS,
            Biome.BIRCH_FOREST, Biome.SWAMPLAND};
    private final Biome[] coolBiomes = new Biome[]{Biome.FOREST,
            Biome.EXTREME_HILLS, Biome.TAIGA, Biome.PLAINS};
    private final Biome[] snowyBiomes = new Biome[]{Biome.ICE_PLAINS,
            Biome.ICE_PLAINS, Biome.ICE_PLAINS, Biome.COLD_TAIGA};

    public BiomeInitLayer(long seed, Layer parent) {
        super(seed);
        this.parent = parent;
    }

    @Override
    public int[] getArea(int x, int z, int sizeX, int sizeZ) {
        int[] aint = this.parent.getArea(x, z, sizeX, sizeZ);
        int[] aint1 = IntCache.push(sizeX * sizeZ);
        for (int i = 0; i < sizeZ; i++) {
            for (int j = 0; j < sizeX; j++) {
                this.setChunkSeed(j + x, i + z);
                int k = aint[j + i * sizeX];
                int l = (k & 3840) >> 8;
                k &= -3841;
                if (isOcean(k)) {
                    aint1[j + i * sizeX] = k;
                } else if (k == Biome.MUSHROOM_ISLAND.legacyId()) {
                    aint1[j + i * sizeX] = k;
                } else if (k == 1) {
                    if (l > 0) {
                        if (this.nextInt(3) == 0) {
                            aint1[j + i * sizeX] =
                                    Biome.MESA_PLATEAU.legacyId();
                        } else {
                            aint1[j + i * sizeX] =
                                    Biome.MESA_PLATEAU_F.legacyId();
                        }
                    } else {
                        aint1[j + i * sizeX] = this.warmBiomes[
                                this.nextInt(this.warmBiomes.length)]
                                .legacyId();
                    }
                } else if (k == 2) {
                    if (l > 0) {
                        aint1[j + i * sizeX] = Biome.JUNGLE.legacyId();
                    } else {
                        aint1[j + i * sizeX] = this.temperateBiomes[
                                this.nextInt(this.temperateBiomes.length)]
                                .legacyId();
                    }
                } else if (k == 3) {
                    if (l > 0) {
                        aint1[j + i * sizeX] = Biome.MEGA_TAIGA.legacyId();
                    } else {
                        aint1[j + i * sizeX] = this.coolBiomes[
                                this.nextInt(this.coolBiomes.length)]
                                .legacyId();
                    }
                } else if (k == 4) {
                    aint1[j + i * sizeX] = this.snowyBiomes[
                            this.nextInt(this.snowyBiomes.length)]
                            .legacyId();
                } else {
                    aint1[j + i * sizeX] = Biome.MUSHROOM_ISLAND.legacyId();
                }
            }
        }
        return aint1;
    }
}
