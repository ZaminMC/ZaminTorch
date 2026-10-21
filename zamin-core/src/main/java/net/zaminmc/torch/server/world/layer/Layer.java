package net.zaminmc.torch.server.world.layer;

import net.zaminmc.torch.server.world.Biome;

/**
 * The biome layout's layer stack (VIII14's second slice) — the reference's
 * {@code world/biome/layer/Layer.java} 1:1: the LCG seed scrambling over
 * the 6364136223846793005/1442695040888963407 pair (three flavors: the
 * ctor salt, the world-seed mix, the chunk-seed walk), the {@code >> 24}
 * bounded nextInt, and the static {@link #init} chain — the ~30-layer
 * transformation stack that turns the 1-in-10 island noise into the
 * vanilla biome map (the reference's Layer.init lines 29-101: Island,
 * FuzzyZoom, the AddIsland ladder, RemoveTooMuchOcean, AddSnow, the three
 * AddEdge modes, the zooms, MushroomIsland, DeepOcean, the river arm, the
 * BiomeInit tables, BiomeEdge, RegionHills (+128), the River layer, Smooth,
 * AddSunflowerPlains, the biomeSize zoom ladder with Shore, OceanRiverMixer
 * and the VoronoiZoom).
 *
 * <p>The chain answers TWO layers: {@code grid} (the 1:4 biome grid —
 * alayer[0], the terrain's own consult) and {@code voronoi} (alayer[1],
 * the block-level biome map the chunk's wire array carries). The engine's
 * named scope: the reference's DEFAULT generator type alone (biomeSize 4,
 * riverSize 4); LARGE_BIOMES and CUSTOMIZED ledger with a generator-options
 * surface if a demand ever names them.
 */
public abstract class Layer {

    private long localWorldSeed;
    protected Layer parent;
    private long chunkSeed;
    protected long seed;

    /**
     * The whole chain (the reference's init, the DEFAULT type's shape):
     * builds the two answer layers over the caller's world seed.
     */
    public static Layer[] init(long worldSeed) {
        Layer layer = new IslandLayer(1L);
        layer = new FuzzyZoomLayer(2000L, layer);
        layer = new AddIslandLayer(1L, layer);
        layer = new NormalZoomLayer(2001L, layer);
        layer = new AddIslandLayer(2L, layer);
        layer = new AddIslandLayer(50L, layer);
        layer = new AddIslandLayer(70L, layer);
        layer = new RemoveTooMuchOceanLayer(2L, layer);
        layer = new AddSnowLayer(2L, layer);
        layer = new AddIslandLayer(3L, layer);
        layer = new AddEdgeLayer(2L, layer, AddEdgeLayer.Mode.COOL_WARM);
        layer = new AddEdgeLayer(2L, layer, AddEdgeLayer.Mode.HEAT_ICE);
        layer = new AddEdgeLayer(3L, layer, AddEdgeLayer.Mode.SPECIAL);
        layer = new NormalZoomLayer(2002L, layer);
        layer = new NormalZoomLayer(2003L, layer);
        layer = new AddIslandLayer(4L, layer);
        layer = new AddMushroomIslandLayer(5L, layer);
        layer = new AddDeepOceanLayer(4L, layer);
        layer = NormalZoomLayer.zoom(1000L, layer, 0);
        int biomeSize = 4;
        int riverSize = biomeSize;

        Layer layer1 = layer;
        layer1 = NormalZoomLayer.zoom(1000L, layer1, 0);
        layer1 = new RiverInitLayer(100L, layer1);
        Layer layer2 = layer;
        layer2 = new BiomeInitLayer(200L, layer2);
        layer2 = NormalZoomLayer.zoom(1000L, layer2, 2);
        layer2 = new BiomeEdgeLayer(1000L, layer2);
        Layer layer3 = layer1;
        layer3 = NormalZoomLayer.zoom(1000L, layer3, 2);
        layer2 = new RegionHillsLayer(1000L, layer2, layer3);
        layer1 = NormalZoomLayer.zoom(1000L, layer1, 2);
        layer1 = NormalZoomLayer.zoom(1000L, layer1, riverSize);
        layer1 = new RiverLayer(1L, layer1);
        layer1 = new SmoothLayer(1000L, layer1);
        layer2 = new AddSunflowerPlainsLayer(1001L, layer2);

        for (int k = 0; k < biomeSize; k++) {
            layer2 = new NormalZoomLayer(1000 + k, layer2);
            if (k == 0) {
                layer2 = new AddIslandLayer(3L, layer2);
            }
            if (k == 1 || biomeSize == 1) {
                layer2 = new ShoreLayer(1000L, layer2);
            }
        }

        layer2 = new SmoothLayer(1000L, layer2);
        layer2 = new OceanRiverMixerLayer(100L, layer2, layer1);
        Layer layer5 = layer2;
        Layer layer4 = new VoronoiZoomLayer(10L, layer2);
        layer2.setLocalWorldSeed(worldSeed);
        layer4.setLocalWorldSeed(worldSeed);
        return new Layer[]{layer2, layer4, layer5};
    }

    public Layer(long seed) {
        this.seed = seed;
        this.seed = this.seed * (this.seed * 6364136223846793005L
                + 1442695040888963407L);
        this.seed += seed;
        this.seed = this.seed * (this.seed * 6364136223846793005L
                + 1442695040888963407L);
        this.seed += seed;
        this.seed = this.seed * (this.seed * 6364136223846793005L
                + 1442695040888963407L);
        this.seed += seed;
    }

    public void setLocalWorldSeed(long worldSeed) {
        this.localWorldSeed = worldSeed;
        if (this.parent != null) {
            this.parent.setLocalWorldSeed(worldSeed);
        }

        this.localWorldSeed = this.localWorldSeed * (this.localWorldSeed
                * 6364136223846793005L + 1442695040888963407L);
        this.localWorldSeed = this.localWorldSeed + this.seed;
        this.localWorldSeed = this.localWorldSeed * (this.localWorldSeed
                * 6364136223846793005L + 1442695040888963407L);
        this.localWorldSeed = this.localWorldSeed + this.seed;
        this.localWorldSeed = this.localWorldSeed * (this.localWorldSeed
                * 6364136223846793005L + 1442695040888963407L);
        this.localWorldSeed = this.localWorldSeed + this.seed;
    }

    public void setChunkSeed(long chunkX, long chunkZ) {
        this.chunkSeed = this.localWorldSeed;
        this.chunkSeed = this.chunkSeed * (this.chunkSeed
                * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed += chunkX;
        this.chunkSeed = this.chunkSeed * (this.chunkSeed
                * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed += chunkZ;
        this.chunkSeed = this.chunkSeed * (this.chunkSeed
                * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed += chunkX;
        this.chunkSeed = this.chunkSeed * (this.chunkSeed
                * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed += chunkZ;
    }

    protected int nextInt(int bound) {
        int i = (int) ((this.chunkSeed >> 24) % bound);
        if (i < 0) {
            i += bound;
        }

        this.chunkSeed = this.chunkSeed * (this.chunkSeed
                * 6364136223846793005L + 1442695040888963407L);
        this.chunkSeed = this.chunkSeed + this.localWorldSeed;
        return i;
    }

    /** The area walk — the layer's whole answer surface. */
    public abstract int[] getArea(int x, int z, int sizeX, int sizeZ);

    /**
     * The same-biome consult (the reference's isSame lines 128-152): the
     * mesa plateau pair special-cased (either plateaus match either),
     * then the type-family consult ({@code Biome.is} — the mutated forms
     * resolve through their originals). The reference wraps the null row
     * in a crash report; the engine's plain null-safe consult is the
     * same observable law.
     */
    protected static boolean isSame(int biome1, int biome2) {
        if (biome1 == biome2) {
            return true;
        }

        if (biome1 != Biome.MESA_PLATEAU_F.legacyId()
                && biome1 != Biome.MESA_PLATEAU.legacyId()) {
            Biome row = Biome.byLegacyId(biome1);
            Biome other = Biome.byLegacyId(biome2);
            return row != null && other != null && row.is(other);
        }
        return biome2 == Biome.MESA_PLATEAU_F.legacyId()
                || biome2 == Biome.MESA_PLATEAU.legacyId();
    }

    /** The ocean band (the reference's isOcean — the frozen ocean rides). */
    protected static boolean isOcean(int biome) {
        return biome == Biome.OCEAN.legacyId()
                || biome == Biome.DEEP_OCEAN.legacyId()
                || biome == Biome.FROZEN_OCEAN.legacyId();
    }

    protected int pickInt(int... ints) {
        return ints[this.nextInt(ints.length)];
    }

    /**
     * The zoom's 4-corner mode (the reference's getModeOrRandom lines
     * 174-194): any three-way (or paired) agreement wins outright; a
     * four-way disagreement rolls one of the four.
     */
    protected int getModeOrRandom(int i1, int i2, int i3, int i4) {
        if (i2 == i3 && i3 == i4) {
            return i2;
        } else if (i1 == i2 && i1 == i3) {
            return i1;
        } else if (i1 == i2 && i1 == i4) {
            return i1;
        } else if (i1 == i3 && i1 == i4) {
            return i1;
        } else if (i1 == i2 && i3 != i4) {
            return i1;
        } else if (i1 == i3 && i2 != i4) {
            return i1;
        } else if (i1 == i4 && i2 != i3) {
            return i1;
        } else if (i2 == i3 && i1 != i4) {
            return i2;
        } else if (i2 == i4 && i1 != i3) {
            return i2;
        } else {
            return i3 == i4 && i1 != i2 ? i3 : this.pickInt(i1, i2, i3, i4);
        }
    }
}
