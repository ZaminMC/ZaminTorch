package net.zaminmc.torch.server.world;

/**
 * The engine's biomes (the subset the full-world generator shades): the 1.8
 * numeric ids ride the chunk packet's biome array, which is what the real
 * client colors grass and foliage from — a desert looks yellow-green and a
 * forest looks lush without any engine-side rendering.
 *
 * <p>Slice 21c adds the reference's registration temperatures (Biome.java's
 * own table lines 70-172: the {@code setTemperatureAndDownfall} values, the
 * class default 0.5) — the precipitation arms' gate ({@code > 0.15F}
 * refuses both the freeze and the snow). Every biome the engine generates
 * answers warm (the coldest is the ocean pair's 0.5), so the frozen-biome
 * arms stay no-ops on generated terrain exactly like vanilla plains — the
 * temperature field carries the gate for the cold families the generator
 * adds later. The y>64 altitude shave (getTemperature lines 413-418 — a
 * noise-based −0.05/30 walk) ledgers with the noise slice: on the engine's
 * warm set even the extreme shave cannot cross the gate (0.5 − 0.11).
 *
 * <p>Slice 23i adds the rain flag ({@code disableRain}'s model — the
 * desert and hell refuse it, reference lines 76/97; the temperate set
 * keeps the class default's true): the cauldron's rain-fill gate.</p>
 */
public enum Biome {

    /** The 1.8 biome id 0: the water body where the surface sits below the sea. */
    OCEAN(0, 0.5f, true),
    /** The 1.8 biome id 24: the deep water band (Slice 12e's monument gate). */
    DEEP_OCEAN(24, 0.5f, true),
    /** The 1.8 biome id 1: sparse oaks, grass and the flower rolls. */
    PLAINS(1, 0.8f, true),
    /** The 1.8 biome id 2: sand skin over a sandstone band, dead bushes. */
    DESERT(2, 2.0f, false),
    /** The 1.8 biome id 4: dense oak forest, grass and flowers. */
    FOREST(4, 0.7f, true),
    /** The 1.8 biome id 21: the hot-wet band (Slice 12f's jungle temple gate). */
    JUNGLE(21, 0.95f, true),
    /** The 1.8 biome id 8: the nether's FixedBiomeSource(HELL) — every cell. */
    HELL(8, 2.0f, false);

    private final int legacyId;
    private final float temperature;
    private final boolean rainy;

    Biome(int legacyId, float temperature, boolean rainy) {
        this.legacyId = legacyId;
        this.temperature = temperature;
        this.rainy = rainy;
    }

    /** @return the 1.8 wire id (the chunk packet's biome array value). */
    public int legacyId() {
        return legacyId;
    }

    /**
     * @return the registration temperature (the precipitation gate's input —
     * {@code canFreeze}/{@code canSnowFall}'s {@code > 0.15F} refusal).
     */
    public float temperature() {
        return temperature;
    }

    /**
     * @return the rain flag ({@code disableRain}'s model — the desert and
     * hell columns refuse the cauldron's rain fill, reference lines 76/97).
     */
    public boolean rainy() {
        return rainy;
    }
}
