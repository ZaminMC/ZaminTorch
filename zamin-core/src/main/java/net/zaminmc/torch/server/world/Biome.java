package net.zaminmc.torch.server.world;

/**
 * The engine's biomes (the subset the full-world generator shades): the 1.8
 * numeric ids ride the chunk packet's biome array, which is what the real
 * client colors grass and foliage from — a desert looks yellow-green and a
 * forest looks lush without any engine-side rendering.
 */
public enum Biome {

    /** The 1.8 biome id 1: sparse oaks, grass and the flower rolls. */
    PLAINS(1),
    /** The 1.8 biome id 2: sand skin over a sandstone band, dead bushes. */
    DESERT(2),
    /** The 1.8 biome id 4: dense oak forest, grass and flowers. */
    FOREST(4),
    /** The 1.8 biome id 8: the nether's FixedBiomeSource(HELL) — every cell. */
    HELL(8);

    private final int legacyId;

    Biome(int legacyId) {
        this.legacyId = legacyId;
    }

    /** @return the 1.8 wire id (the chunk packet's biome array value). */
    public int legacyId() {
        return legacyId;
    }
}
