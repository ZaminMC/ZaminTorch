package net.zaminmc.torch.server.world.feature;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.TreeFeatures;
import net.zaminmc.torch.server.world.Biome;
import net.zaminmc.torch.server.world.noise.PerlinSimplexNoise;
import net.zaminmc.torch.util.Identifier;

import java.util.Random;

/**
 * The feature decorator walk (the VIII14 decorators slice — the
 * reference's {@code FeatureDecorator.decorate}, lines 98-298, whole):
 * every stage in the reference's exact order with the reference's exact
 * dice — the shared chunk-population random; the {@code nextInt(16) + 8}
 * band offsets; the per-stage {@code nextInt} shapes; the per-stage
 * attempt counts; the count rolls.
 *
 * <p><b>THE POPULATE SEQUENCE</b> ({@code OverworldChunkGenerator
 * .populateChunk} lines 353-382): the water lake (the desert pair's
 * refusal, the 1-in-4 chance), the lava lake (the 1-in-8 chance with the
 * nested depth roll and the sea-level gate), the eight dungeon attempts,
 * then {@code biome.decorate} — one shared random stream, the reference's
 * own order.</p>
 *
 * <p><b>THE REGION RE-derivation WINDOW</b> (the engine's deterministic
 * contract, the village slice's own answer): the reference's features
 * root in the {@code +8..+24} band and write across chunk borders into
 * the live neighbor chunks; the engine's populate runs on one detached
 * chunk with out-of-chunk writes clipped. So every chunk's pass replays
 * the populate sequences of the {self, -x} x {self, -z} quadrant — each
 * sequence seed-pure in its source chunk, so each foreign cell's writer
 * is exactly one pass, and the whole world rebuilds identically from
 * the seed alone.</p>
 *
 * <p><b>THE SEED LAW</b> (lines 342-348): {@code setSeed(worldSeed);
 * k = nextLong()/2*2+1; l = nextLong()/2*2+1; setSeed(chunkX*k +
 * chunkZ*l ^ worldSeed)} — the two odd longs drawn once per world, the
 * per-chunk population seed the reference's own form.</p>
 *
 * <p><b>THE LEDGERED STAGE</b>: {@code placeVeins} (lines 333-345) —
 * the ore and stone-family veins — stays retired whole: the engine's
 * ores ride the generation-side depth ladder ({@code oreOrStone}, the
 * documented standing adaptation), whose position-pure answers the
 * populate-time out-of-chunk reads REQUIRE (a decorate-time vein write
 * is chunk-local; the pure terrain function could never see it). The
 * stone-family composition (the dirt/gravel/granite/diorite/andesite
 * veins) rides the same ledger.</p>
 *
 * <p><b>The counted notes</b> (the named adaptations, recorded in the
 * slice's PR): the village gate on the lakes drops (the structure
 * pass's boolean is another lane's surface — a rare lake-under-village
 * carve diverges); the flowing-liquid forms ride the source stand-in;
 * the huge mushroom's kind rolls per walk (the reference's per-decorator
 * instance state cannot survive the re-derivation — the stateless
 * answer); the animal seeding keeps its own earlier per-chunk seed (its
 * own slice's documented shape) rather than joining this stream's
 * tail.</p>
 */
public final class FeatureDecorator {

    private final long worldSeed;
    private final long firstLong;
    private final long secondLong;

    // The resolved placement vocabulary.
    private final BlockType clay;
    private final BlockType sand;
    private final BlockType gravel;
    private final BlockType mossyCobblestone;
    private final BlockType podzol;
    private final BlockType emeraldOre;
    private final BlockType infestedStone;
    /** The two liquid sources (the populate lakes' + falls' own placements). */
    private final BlockType water;
    private final BlockType lava;
    /** The two mushroom families' sixteen-state bands. */
    private final BlockType[][] mushroomStates;

    /**
     * The reference's own FOLIAGE_NOISE (Biome.java line 595): the fixed
     * 2345 seed, one level — identical across every world, the plains and
     * flower-forest flower bands' own consult.
     */
    private static final PerlinSimplexNoise FOLIAGE_NOISE =
            new PerlinSimplexNoise(new Random(2345L), 1);

    public FeatureDecorator(BlockRegistry registry, long worldSeed) {
        Random seedRandom = new Random(worldSeed);
        this.worldSeed = worldSeed;
        this.firstLong = seedRandom.nextLong() / 2L * 2L + 1L;
        this.secondLong = seedRandom.nextLong() / 2L * 2L + 1L;
        this.clay = registry.require(Identifier.parse("minecraft:clay"));
        this.sand = registry.require(Identifier.parse("minecraft:sand"));
        this.gravel = registry.require(Identifier.parse("minecraft:gravel"));
        this.mossyCobblestone = registry.require(Identifier.parse("minecraft:mossy_cobblestone"));
        this.podzol = registry.require(Identifier.parse("minecraft:podzol"));
        this.emeraldOre = registry.require(Identifier.parse("minecraft:emerald_ore"));
        this.infestedStone = registry.require(Identifier.parse("minecraft:infested_stone"));
        this.water = registry.require(Identifier.parse("minecraft:water"));
        this.lava = registry.require(Identifier.parse("minecraft:lava"));
        this.mushroomStates = resolveMushroomStates(registry);
    }

    /**
     * The per-chunk populate entry (the reference's populateChunk's
     * populate side): the region re-derivation window — the quadrant of
     * populate sequences whose feature bands write into this chunk, in a
     * fixed order.
     */
    public void populate(int chunkX, int chunkZ, FeatureWorld world) {
        for (int cz = chunkZ - 1; cz <= chunkZ; cz++) {
            for (int cx = chunkX - 1; cx <= chunkX; cx++) {
                populateSequence(cx, cz, world);
            }
        }
    }

    /**
     * One source chunk's populate sequence (populateChunk lines 341-393,
     * the shared random stream): the biome read at the +16 corner, the
     * water lake, the lava lake, the dungeons, then the biome's decorate.
     */
    private void populateSequence(int chunkX, int chunkZ, FeatureWorld world) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        Biome biome = world.biomeAt(baseX + 16, baseZ + 16);
        Random random = new Random(chunkX * firstLong + chunkZ * secondLong ^ worldSeed);
        // The water lake (lines 368-375): the desert pair's refusal, the
        // 1-in-4 chance (the village gate drops — the counted note).
        if (biome != Biome.DESERT && biome != Biome.DESERT_HILLS
                && random.nextInt(4) == 0) {
            int i1 = random.nextInt(16) + 8;
            int j1 = random.nextInt(256);
            int k1 = random.nextInt(16) + 8;
            new TerrainFeatures.LakeFeature(water)
                    .place(world, random, baseX + i1, j1, baseZ + k1);
        }
        // The lava lake (lines 377-387): the 1-in-8 chance, the nested
        // depth roll, the sea-level gate.
        if (random.nextInt(8) == 0) {
            int i2 = random.nextInt(16) + 8;
            int l2 = random.nextInt(random.nextInt(248) + 8);
            int k3 = random.nextInt(16) + 8;
            if (l2 < world.seaLevel() || random.nextInt(10) == 0) {
                new TerrainFeatures.LakeFeature(lava)
                        .place(world, random, baseX + i2, l2, baseZ + k3);
            }
        }
        // The dungeons (lines 389-396): the eight attempts.
        for (int j2 = 0; j2 < 8; j2++) {
            int i3 = random.nextInt(16) + 8;
            int l3 = random.nextInt(256);
            int l1 = random.nextInt(16) + 8;
            new TerrainFeatures.DungeonFeature()
                    .place(world, random, baseX + i3, l3, baseZ + l1);
        }
        // biome.decorate (line 392): the flavor passes + the walk.
        decorate(biome, random, baseX, baseZ, world);
    }

    // ------------------------------------------------------------ the decorate

    /**
     * The biome's decorate (the per-class overrides' own pass order over
     * the walk): the pre-passes, the walk whole, the post-passes.
     */
    private void decorate(Biome biome, Random random, int baseX, int baseZ,
                          FeatureWorld world) {
        DecoratorProfile profile = biome.decorator();
        // The pre-passes (each subclass's own order).
        if (profile.flavor().contains(DecoratorProfile.Flavor.ROOFED_GRID)) {
            roofedGridPass(random, baseX, baseZ, world);
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.MEGA_BOULDERS)) {
            boulderPass(random, baseX, baseZ, world);
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.TAIGA_FERNS)) {
            taigaFernPass(random, baseX, baseZ, world);
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.SAVANNA_DOUBLE_PLANTS)) {
            doublePlantPass(random, baseX, baseZ, world, 2, 7);
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.PLAINS_PASS)) {
            plainsPass(random, baseX, baseZ, world, profile);
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.ICE_SPIKES)) {
            iceSpikesPass(random, baseX, baseZ, world);
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.FOREST_DOUBLE_PLANTS)) {
            forestDoublePlantPass(random, baseX, baseZ, world, profile);
        }
        // The walk (FeatureDecorator.decorate, lines 98-298).
        walk(biome, profile, random, baseX, baseZ, world);
        // The post-passes.
        if (profile.flavor().contains(DecoratorProfile.Flavor.JUNGLE_TAIL)) {
            jungleTail(random, baseX, baseZ, world);
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.DESERT_WELL)) {
            desertWellPass(random, baseX, baseZ, world);
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.HILLS_VEINS)) {
            hillsVeinsPass(random, baseX, baseZ, world);
        }
    }

    /**
     * The walk whole (lines 98-298, every stage in the reference's exact
     * order with the exact dice — the veins stage ledgered out).
     */
    private void walk(Biome biome, DecoratorProfile profile, Random random,
                      int baseX, int baseZ, FeatureWorld world) {
        // The working counts (the plains band mutates its own pair
        // pre-walk — the reference's live field writes).
        int flowerAttempts = profile.flowerAttempts();
        int grassAttempts = profile.grassAttempts();
        if (profile.flavor().contains(DecoratorProfile.Flavor.PLAINS_PASS)) {
            // PlainsBiome.decorate's own consult (lines 56-66): the chunk
            // band read at the +8 center.
            double d0 = FOLIAGE_NOISE.getValue((baseX + 8) / 200.0, (baseZ + 8) / 200.0);
            if (d0 < -0.8) {
                flowerAttempts = 15;
                grassAttempts = 5;
            } else {
                flowerAttempts = 4;
                grassAttempts = 10;
            }
        }
        // The disk patches (lines 101-117): sand x3, clay x1, gravel x1 —
        // the underwater gate + the circle law inside each feature.
        FloraFeatures.Feature sandPatch =
                new PatchFeatures.DiskPatchFeature(sand, 7);
        FloraFeatures.Feature clayPatch =
                new PatchFeatures.DiskPatchFeature(clay, 4);
        FloraFeatures.Feature gravelPatch =
                new PatchFeatures.DiskPatchFeature(gravel, 6);
        for (int i = 0; i < profile.sandPatchAttempts(); i++) {
            int j = random.nextInt(16) + 8;
            int k = random.nextInt(16) + 8;
            sandPatch.place(world, random,
                    baseX + j, world.surfaceHeightAt(baseX + j, baseZ + k), baseZ + k);
        }
        for (int i1 = 0; i1 < profile.clayPatchAttempts(); i1++) {
            int l1 = random.nextInt(16) + 8;
            int i6 = random.nextInt(16) + 8;
            clayPatch.place(world, random,
                    baseX + l1, world.surfaceHeightAt(baseX + l1, baseZ + i6), baseZ + i6);
        }
        for (int j1 = 0; j1 < profile.gravelPatchAttempts(); j1++) {
            int i2 = random.nextInt(16) + 8;
            int j6 = random.nextInt(16) + 8;
            gravelPatch.place(world, random,
                    baseX + i2, world.surfaceHeightAt(baseX + i2, baseZ + j6), baseZ + j6);
        }
        // The trees (lines 119-133): the count roll, the band pick, the
        // biome's own tree law, the soil patch on success.
        int treeAttempts = profile.treeAttempts();
        if (random.nextInt(10) == 0) {
            treeAttempts++;
        }
        for (int j2 = 0; j2 < treeAttempts; j2++) {
            int k6 = random.nextInt(16) + 8;
            int l = random.nextInt(16) + 8;
            TreePlacement tree = pickTree(biome, random);
            int x = baseX + k6;
            int z = baseZ + l;
            int y = world.heightAt(x, z);
            if (tree.feature().place(world, random, new BlockPosition(x, y, z))
                    && tree.giantSpruce()) {
                placePodzolSoil(world, random, x, y, z);
            }
        }
        // The huge mushrooms (lines 135-139).
        HugeMushroomFeature hugeMushroom = new HugeMushroomFeature(mushroomStates);
        for (int k2 = 0; k2 < profile.hugeMushroomAttempts(); k2++) {
            int l6 = random.nextInt(16) + 8;
            int k10 = random.nextInt(16) + 8;
            hugeMushroom.place(world, random, baseX + l6,
                    world.heightAt(baseX + l6, baseZ + k10), baseZ + k10);
        }
        // The flowers (lines 141-155): the +32 band, the biome's own
        // flower law.
        FloraFeatures.FlowerFeature flower =
                new FloraFeatures.FlowerFeature(BuiltinBlocks.DANDELION);
        for (int l2 = 0; l2 < flowerAttempts; l2++) {
            int i7 = random.nextInt(16) + 8;
            int l10 = random.nextInt(16) + 8;
            int j14 = world.heightAt(baseX + i7, baseZ + l10) + 32;
            if (j14 > 0) {
                int k17 = random.nextInt(j14);
                BlockType picked = pickFlower(biome, random,
                        baseX + i7, k17, baseZ + l10, profile);
                if (picked != null) {
                    flower.set(picked);
                    flower.place(world, random, baseX + i7, k17, baseZ + l10);
                }
            }
        }
        // The grass (lines 157-165): the x2 band, the biome's plant law.
        for (int i3 = 0; i3 < grassAttempts; i3++) {
            int j7 = random.nextInt(16) + 8;
            int i11 = random.nextInt(16) + 8;
            int k14 = world.heightAt(baseX + j7, baseZ + i11) * 2;
            if (k14 > 0) {
                int l17 = random.nextInt(k14);
                new FloraFeatures.TallPlantFeature(pickPlant(biome, random, profile))
                        .place(world, random, baseX + j7, l17, baseZ + i11);
            }
        }
        // The dead bushes (lines 167-175).
        for (int j3 = 0; j3 < profile.deadBushAttempts(); j3++) {
            int k7 = random.nextInt(16) + 8;
            int j11 = random.nextInt(16) + 8;
            int l14 = world.heightAt(baseX + k7, baseZ + j11) * 2;
            if (l14 > 0) {
                int i18 = random.nextInt(l14);
                new FloraFeatures.DeadBushFeature()
                        .place(world, random, baseX + k7, i18, baseZ + j11);
            }
        }
        // The lily pads (lines 177-196): the x2 band + the drop-down.
        for (int k3 = 0; k3 < profile.lilyPadAttempts(); k3++) {
            int l7 = random.nextInt(16) + 8;
            int k11 = random.nextInt(16) + 8;
            int i15 = world.heightAt(baseX + l7, baseZ + k11) * 2;
            if (i15 > 0) {
                int j18 = random.nextInt(i15);
                while (j18 > 0 && world.isAirAt(baseX + l7, j18 - 1, baseZ + k11)) {
                    j18--;
                }
                new FloraFeatures.LilyPadFeature()
                        .place(world, random, baseX + l7, j18, baseZ + k11);
            }
        }
        // The mushrooms (lines 198-216): the per-attempt pair — the brown
        // at the surface on 1-in-4, the red in the x2 band on 1-in-8.
        FloraFeatures.MushroomFeature brownMushroom =
                new FloraFeatures.MushroomFeature(BuiltinBlocks.BROWN_MUSHROOM);
        FloraFeatures.MushroomFeature redMushroom =
                new FloraFeatures.MushroomFeature(BuiltinBlocks.RED_MUSHROOM);
        for (int l3 = 0; l3 < profile.mushroomAttempts(); l3++) {
            if (random.nextInt(4) == 0) {
                int i8 = random.nextInt(16) + 8;
                int l11 = random.nextInt(16) + 8;
                brownMushroom.place(world, random, baseX + i8,
                        world.heightAt(baseX + i8, baseZ + l11), baseZ + l11);
            }
            if (random.nextInt(8) == 0) {
                int j8 = random.nextInt(16) + 8;
                int i12 = random.nextInt(16) + 8;
                int j15 = world.heightAt(baseX + j8, baseZ + i12) * 2;
                if (j15 > 0) {
                    int k18 = random.nextInt(j15);
                    redMushroom.place(world, random, baseX + j8, k18, baseZ + i12);
                }
            }
        }
        // The one-shot mushrooms (lines 218-236).
        if (random.nextInt(4) == 0) {
            int i4 = random.nextInt(16) + 8;
            int k8 = random.nextInt(16) + 8;
            int j12 = world.heightAt(baseX + i4, baseZ + k8) * 2;
            if (j12 > 0) {
                int k15 = random.nextInt(j12);
                brownMushroom.place(world, random, baseX + i4, k15, baseZ + k8);
            }
        }
        if (random.nextInt(8) == 0) {
            int j4 = random.nextInt(16) + 8;
            int l8 = random.nextInt(16) + 8;
            int k12 = world.heightAt(baseX + j4, baseZ + l8) * 2;
            if (k12 > 0) {
                int l15 = random.nextInt(k12);
                redMushroom.place(world, random, baseX + j4, l15, baseZ + l8);
            }
        }
        // The reeds (lines 238-256): the count attempts + the ten extras.
        FloraFeatures.SugarcaneFeature sugarcane = new FloraFeatures.SugarcaneFeature();
        for (int k4 = 0; k4 < profile.sugarcaneAttempts(); k4++) {
            int i9 = random.nextInt(16) + 8;
            int l12 = random.nextInt(16) + 8;
            int i16 = world.heightAt(baseX + i9, baseZ + l12) * 2;
            if (i16 > 0) {
                int l18 = random.nextInt(i16);
                sugarcane.place(world, random, baseX + i9, l18, baseZ + l12);
            }
        }
        for (int l4 = 0; l4 < 10; l4++) {
            int j9 = random.nextInt(16) + 8;
            int i13 = random.nextInt(16) + 8;
            int j16 = world.heightAt(baseX + j9, baseZ + i13) * 2;
            if (j16 > 0) {
                int i19 = random.nextInt(j16);
                sugarcane.place(world, random, baseX + j9, i19, baseZ + i13);
            }
        }
        // The pumpkin (lines 258-266): the 1-in-32 roll.
        if (random.nextInt(32) == 0) {
            int i5 = random.nextInt(16) + 8;
            int k9 = random.nextInt(16) + 8;
            int j13 = world.heightAt(baseX + i5, baseZ + k9) * 2;
            if (j13 > 0) {
                int k16 = random.nextInt(j13);
                new FloraFeatures.PumpkinPatchFeature()
                        .place(world, random, baseX + i5, k16, baseZ + k9);
            }
        }
        // The cacti (lines 268-276).
        FloraFeatures.CactusFeature cactus = new FloraFeatures.CactusFeature();
        for (int l9 = 0; l9 < profile.cactusAttempts(); l9++) {
            int k13 = random.nextInt(16) + 8;
            int l16 = random.nextInt(16) + 8;
            int j19 = world.heightAt(baseX + k13, baseZ + l16) * 2;
            if (j19 > 0) {
                int roll = random.nextInt(j19);
                cactus.place(world, random, baseX + k13, roll, baseZ + l16);
            }
        }
        // The liquid falls (lines 278-297): the fifty water and twenty
        // lava springs.
        for (int k5 = 0; k5 < 50; k5++) {
            int i10 = random.nextInt(16) + 8;
            int l13 = random.nextInt(16) + 8;
            int i17 = random.nextInt(248) + 8;
            if (i17 > 0) {
                int k19 = random.nextInt(i17);
                new TerrainFeatures.LiquidFallFeature(water)
                        .place(world, random, baseX + i10, k19, baseZ + l13);
            }
        }
        for (int l5 = 0; l5 < 20; l5++) {
            int j10 = random.nextInt(16) + 8;
            int i14 = random.nextInt(16) + 8;
            int j17 = random.nextInt(random.nextInt(random.nextInt(240) + 8) + 8);
            new TerrainFeatures.LiquidFallFeature(lava)
                    .place(world, random, baseX + j10, j17, baseZ + i14);
        }
    }

    // ------------------------------------------------------------ the pick laws

    /** One placed tree + its soil-pass flag. */
    private record TreePlacement(TreeFeatures.Feature feature, boolean giantSpruce) {
    }

    /**
     * The biome's tree law ({@code pickTree} + the class overrides).
     */
    private TreePlacement pickTree(Biome biome, Random random) {
        switch (biome.decorator().treePick()) {
            case FOREST_OAK:
                return placement(random.nextInt(5) != 0
                        ? TreeFeatures.oak(random) : TreeFeatures.birch(random));
            case FOREST_BIRCH:
                return placement(TreeFeatures.birch(random));
            case FOREST_ROOFED:
                if (random.nextInt(3) > 0) {
                    return placement(TreeFeatures.darkOak(random));
                }
                return placement(random.nextInt(5) != 0
                        ? TreeFeatures.oak(random) : TreeFeatures.birch(random));
            case BIRCH_TALL:
                return placement(random.nextBoolean()
                        ? TreeFeatures.tallBirch(random) : TreeFeatures.birch(random));
            case TAIGA:
                return placement(random.nextInt(3) == 0
                        ? TreeFeatures.pine(random) : TreeFeatures.spruce(random));
            case TAIGA_MEGA:
                if (random.nextInt(3) == 0) {
                    return giantPlacement(random.nextInt(13) != 0
                            ? TreeFeatures.giantSpruce(random, false)
                            : TreeFeatures.giantSpruce(random, true));
                }
                return placement(random.nextInt(3) == 0
                        ? TreeFeatures.pine(random) : TreeFeatures.spruce(random));
            case TAIGA_MEGA_SPRUCE:
                if (random.nextInt(3) == 0) {
                    return giantPlacement(TreeFeatures.giantSpruce(random, true));
                }
                return placement(random.nextInt(3) == 0
                        ? TreeFeatures.pine(random) : TreeFeatures.spruce(random));
            case JUNGLE:
                if (random.nextInt(10) == 0) {
                    return placement(TreeFeatures.largeOak(random));
                }
                if (random.nextInt(2) == 0) {
                    return placement(TreeFeatures.jungleBush(random));
                }
                return placement(random.nextInt(3) == 0
                        ? TreeFeatures.giantJungle(random)
                        : TreeFeatures.jungleSmall(random));
            case JUNGLE_EDGE:
                if (random.nextInt(10) == 0) {
                    return placement(TreeFeatures.largeOak(random));
                }
                if (random.nextInt(2) == 0) {
                    return placement(TreeFeatures.jungleBush(random));
                }
                return placement(TreeFeatures.jungleSmall(random));
            case SAVANNA:
                return placement(random.nextInt(5) > 0
                        ? TreeFeatures.acacia(random) : TreeFeatures.oak(random));
            case MESA_OAK:
                return placement(TreeFeatures.oak(random));
            case ICE_SPRUCE:
                return placement(TreeFeatures.spruce(random));
            case HILLS:
                if (random.nextInt(3) > 0) {
                    return placement(TreeFeatures.spruce(random));
                }
                return placement(random.nextInt(10) == 0
                        ? TreeFeatures.largeOak(random) : TreeFeatures.oak(random));
            case SWAMP:
                return placement(TreeFeatures.swampTree(random));
            case FOREST_FLOWER:
            case DEFAULT:
            default:
                return placement(random.nextInt(10) == 0
                        ? TreeFeatures.largeOak(random) : TreeFeatures.oak(random));
        }
    }

    private static TreePlacement placement(TreeFeatures.Feature feature) {
        return new TreePlacement(feature, false);
    }

    private static TreePlacement giantPlacement(TreeFeatures.Feature feature) {
        return new TreePlacement(feature, true);
    }

    /**
     * The biome's ground-cover plant law ({@code pickPlant}).
     */
    private BlockType pickPlant(Biome biome, Random random, DecoratorProfile profile) {
        switch (profile.plantPick()) {
            case TAIGA_FERN:
                return random.nextInt(5) > 0 ? BuiltinBlocks.FERN : BuiltinBlocks.TALL_GRASS;
            case JUNGLE_FERN:
                return random.nextInt(4) == 0 ? BuiltinBlocks.FERN : BuiltinBlocks.TALL_GRASS;
            case DEFAULT:
            default:
                return BuiltinBlocks.TALL_GRASS;
        }
    }

    /**
     * The biome's flower law ({@code getRandomFlower}): the base law, the
     * plains' tulip band, the flower forest's nine-flower noise walk, the
     * swamp's orchid.
     */
    private BlockType pickFlower(Biome biome, Random random, int x, int y, int z,
                                 DecoratorProfile profile) {
        switch (profile.flowerPick()) {
            case PLAINS_BAND:
                double d0 = FOLIAGE_NOISE.getValue(x / 200.0, z / 200.0);
                if (d0 < -0.8) {
                    int j = random.nextInt(4);
                    switch (j) {
                        case 0:
                            return FloraFeatures.flower(5); // orange_tulip
                        case 1:
                            return FloraFeatures.flower(4); // red_tulip
                        case 2:
                            return FloraFeatures.flower(7); // pink_tulip
                        default:
                            return FloraFeatures.flower(6); // white_tulip
                    }
                } else if (random.nextInt(3) > 0) {
                    int i = random.nextInt(3);
                    return FloraFeatures.flower(i == 0 ? 0 : i == 1 ? 3 : 8);
                }
                return BuiltinBlocks.DANDELION;
            case FOREST_FLOWER_BAND:
                double band = Math.max(0.0, Math.min(0.9999,
                        (1.0 + FOLIAGE_NOISE.getValue(x / 48.0, z / 48.0)) / 2.0));
                int type = (int) (band * 9);
                // The blue orchid never rides the forest band (the
                // reference's own swap).
                return FloraFeatures.flower(type == 1 ? 0 : type);
            case SWAMP_ORCHID:
                return FloraFeatures.flower(1); // blue_orchid
            case DEFAULT:
            default:
                return random.nextInt(3) > 0
                        ? BuiltinBlocks.DANDELION : FloraFeatures.flower(0);
        }
    }

    /**
     * The giant spruce's soil patch (GiantSpruceTreeFeature.placeSoil
     * lines 80-93 + placePodzolPatch/placePodzol lines 96-119, 1:1): the
     * four corner patches, the five edge rolls, and the column walk's
     * grass/dirt-to-podzol swap.
     */
    private void placePodzolSoil(FeatureWorld world, Random random, int x, int y, int z) {
        placePodzolPatch(world, x - 1, y, z - 1);
        placePodzolPatch(world, x + 2, y, z - 1);
        placePodzolPatch(world, x - 1, y, z + 2);
        placePodzolPatch(world, x + 2, y, z + 2);
        for (int i = 0; i < 5; i++) {
            int j = random.nextInt(64);
            int k = j % 8;
            int l = j / 8;
            if (k == 0 || k == 7 || l == 0 || l == 7) {
                placePodzolPatch(world, x - 3 + k, y, z - 3 + l);
            }
        }
    }

    private void placePodzolPatch(FeatureWorld world, int x, int y, int z) {
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (Math.abs(i) != 2 || Math.abs(j) != 2) {
                    placePodzol(world, x + i, y, z + j);
                }
            }
        }
    }

    private void placePodzol(FeatureWorld world, int x, int y, int z) {
        for (int i = 2; i >= -3; i--) {
            BlockType at = world.blockAt(x, y + i, z);
            if (at.equals(BuiltinBlocks.GRASS_BLOCK) || at.equals(BuiltinBlocks.DIRT)) {
                world.setBlockAt(x, y + i, z, podzol);
                break;
            }
            if (!world.isAirAt(x, y + i, z) && i < 0) {
                break;
            }
        }
    }

    // -------------------------------------------------------- the flavor passes

    /**
     * The roofed forest's grid pass (ForestBiome.decorate lines 95-117):
     * the 4x4 grid of 4-cell cells, the 1-in-20 huge mushroom beside the
     * tree law, the placement + soil on success.
     */
    private void roofedGridPass(Random random, int baseX, int baseZ, FeatureWorld world) {
        HugeMushroomFeature hugeMushroom = new HugeMushroomFeature(mushroomStates);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int k = i * 4 + 1 + 8 + random.nextInt(3);
                int l = j * 4 + 1 + 8 + random.nextInt(3);
                int x = baseX + k;
                int z = baseZ + l;
                int y = world.heightAt(x, z);
                if (random.nextInt(20) == 0) {
                    hugeMushroom.place(world, random, x, y, z);
                } else {
                    TreePlacement tree = pickTree(Biome.ROOFED_FOREST, random);
                    if (tree.feature().place(world, random, new BlockPosition(x, y, z))
                            && tree.giantSpruce()) {
                        placePodzolSoil(world, random, x, y, z);
                    }
                }
            }
        }
    }

    /**
     * The mega taiga's boulder roll (TaigaBiome.decorate lines 65-74):
     * nextInt(3) mossy boulders at the surface.
     */
    private void boulderPass(Random random, int baseX, int baseZ, FeatureWorld world) {
        int count = random.nextInt(3);
        for (int j = 0; j < count; j++) {
            int k = random.nextInt(16) + 8;
            int l = random.nextInt(16) + 8;
            new PatchFeatures.BoulderFeature(mossyCobblestone, 0)
                    .place(world, random, baseX + k,
                            world.heightAt(baseX + k, baseZ + l), baseZ + l);
        }
    }

    /**
     * The taiga's fern pass (TaigaBiome.decorate lines 76-85): seven
     * large-fern placements in the +32 band.
     */
    private void taigaFernPass(Random random, int baseX, int baseZ, FeatureWorld world) {
        FloraFeatures.DoublePlantFeature ferns = new FloraFeatures.DoublePlantFeature();
        ferns.setVariant(3); // the large fern
        for (int i1 = 0; i1 < 7; i1++) {
            int j1 = random.nextInt(16) + 8;
            int k1 = random.nextInt(16) + 8;
            int l1 = random.nextInt(world.heightAt(baseX + j1, baseZ + k1) + 32);
            ferns.place(world, random, baseX + j1, l1, baseZ + k1);
        }
    }

    /**
     * The shared double-plant pass (the savanna's own shape — the plains'
     * and the forest's ride their own wrappers): seven placements of one
     * variant in the +32 band.
     */
    private void doublePlantPass(Random random, int baseX, int baseZ, FeatureWorld world,
                                 int variant, int attempts) {
        FloraFeatures.DoublePlantFeature plants = new FloraFeatures.DoublePlantFeature();
        plants.setVariant(variant);
        for (int i = 0; i < attempts; i++) {
            int j = random.nextInt(16) + 8;
            int k = random.nextInt(16) + 8;
            int l = random.nextInt(world.heightAt(baseX + j, baseZ + k) + 32);
            plants.place(world, random, baseX + j, l, baseZ + k);
        }
    }

    /**
     * The plains' pass (PlainsBiome.decorate lines 48-77): the band
     * consult rides the walk's own count swap (handled there); the
     * non-tulip branch plants the seven double grass, and the mutated
     * form adds ten sunflowers after.
     */
    private void plainsPass(Random random, int baseX, int baseZ, FeatureWorld world,
                            DecoratorProfile profile) {
        double d0 = FOLIAGE_NOISE.getValue((baseX + 8) / 200.0, (baseZ + 8) / 200.0);
        if (d0 >= -0.8) {
            doublePlantPass(random, baseX, baseZ, world, 2, 7); // the double tall grass
        }
        if (profile.flavor().contains(DecoratorProfile.Flavor.SUNFLOWER_PASS)) {
            doublePlantPass(random, baseX, baseZ, world, 0, 10); // the sunflower
        }
    }

    /**
     * The forest's double-plant pass (ForestBiome.decorate lines 119-141):
     * the nextInt(5)-3 roll (the flower variant's +2), the three-flower
     * pick, and the five-attempt inner walk with the break on success.
     */
    private void forestDoublePlantPass(Random random, int baseX, int baseZ,
                                       FeatureWorld world, DecoratorProfile profile) {
        FloraFeatures.DoublePlantFeature plants = new FloraFeatures.DoublePlantFeature();
        int roll = random.nextInt(5) - 3;
        if (profile.flavor().contains(
                DecoratorProfile.Flavor.FLOWER_FOREST_DOUBLE_BONUS)) {
            roll += 2;
        }
        for (int k1 = 0; k1 < roll; k1++) {
            int l1 = random.nextInt(3);
            plants.setVariant(l1 == 0 ? 1 : l1 == 1 ? 4 : 5); // syringa, rose, peony
            for (int i2 = 0; i2 < 5; i2++) {
                int j2 = random.nextInt(16) + 8;
                int k2 = random.nextInt(16) + 8;
                int i1 = random.nextInt(world.heightAt(baseX + j2, baseZ + k2) + 32);
                if (plants.place(world, random, baseX + j2, i1, baseZ + k2)) {
                    break;
                }
            }
        }
    }

    /**
     * The ice plains spikes' pass (IceBiome.decorate lines 34-51): three
     * spikes and two patches at the surface.
     */
    private void iceSpikesPass(Random random, int baseX, int baseZ, FeatureWorld world) {
        TerrainFeatures.IceSpikeFeature spike = new TerrainFeatures.IceSpikeFeature();
        PatchFeatures.IcePatchFeature patch = new PatchFeatures.IcePatchFeature(4);
        for (int i = 0; i < 3; i++) {
            int j = random.nextInt(16) + 8;
            int k = random.nextInt(16) + 8;
            spike.place(world, random, baseX + j,
                    world.heightAt(baseX + j, baseZ + k), baseZ + k);
        }
        for (int l = 0; l < 2; l++) {
            int i1 = random.nextInt(16) + 8;
            int j1 = random.nextInt(16) + 8;
            patch.place(world, random, baseX + i1,
                    world.heightAt(baseX + i1, baseZ + j1), baseZ + j1);
        }
    }

    /**
     * The jungle's tail (JungleBiome.decorate lines 63-78): the melon in
     * the x2 band, then the fifty vine climbs at y 128.
     */
    private void jungleTail(Random random, int baseX, int baseZ, FeatureWorld world) {
        int i = random.nextInt(16) + 8;
        int j = random.nextInt(16) + 8;
        int k = random.nextInt(world.heightAt(baseX + i, baseZ + j) * 2);
        new FloraFeatures.MelonPatchFeature().place(world, random, baseX + i, k, baseZ + j);
        FloraFeatures.VineFeature vine = new FloraFeatures.VineFeature();
        for (int j1 = 0; j1 < 50; j1++) {
            int k1 = random.nextInt(16) + 8;
            int i1 = random.nextInt(16) + 8;
            vine.place(world, random, baseX + k1, 128, baseZ + i1);
        }
    }

    /**
     * The desert's well (DesertBiome.decorate lines 27-36): the 1-in-1000
     * roll, the surface + 1 placement.
     */
    private void desertWellPass(Random random, int baseX, int baseZ, FeatureWorld world) {
        if (random.nextInt(1000) == 0) {
            int i = random.nextInt(16) + 8;
            int j = random.nextInt(16) + 8;
            new TerrainFeatures.DesertWellFeature(water).place(world, random,
                    baseX + i, world.heightAt(baseX + i, baseZ + j) + 1, baseZ + j);
        }
    }

    /**
     * The extreme hills' veins (ExtremeHillsBiome.decorate lines 50-69):
     * the 3+rand(6) emerald attempts (the stone-only gate), then the
     * seven monster-egg veins — the reference's own rand(16) band WITHOUT
     * the +8 shift.
     */
    private void hillsVeinsPass(Random random, int baseX, int baseZ, FeatureWorld world) {
        int count = 3 + random.nextInt(6);
        for (int j = 0; j < count; j++) {
            int k = random.nextInt(16);
            int l = random.nextInt(28) + 4;
            int i1 = random.nextInt(16);
            int x = baseX + k;
            int y = l;
            int z = baseZ + i1;
            if (world.blockAt(x, y, z).equals(BuiltinBlocks.STONE)) {
                world.setBlockAt(x, y, z, emeraldOre);
            }
        }
        PatchFeatures.VeinFeature monsterEggVein =
                new PatchFeatures.VeinFeature(infestedStone, 9);
        for (int j1 = 0; j1 < 7; j1++) {
            int k1 = random.nextInt(16);
            int l1 = random.nextInt(64);
            int i2 = random.nextInt(16);
            monsterEggVein.place(world, random, baseX + k1, l1, baseZ + i2);
        }
    }

    // ------------------------------------------------------ the huge mushroom

    /**
     * The huge mushroom (HugeMushroomFeature 1:1, lines 17-163): the
     * 4+rand(3) height, the full-band clearance (radius 0 through the
     * first four rows, 3 above), the dirt/grass/mycelium soil gate, the
     * brown's 3-radius dome against the red's flat 1-2-radius cap, the
     * corner pores with the directional rewrites, the all-inside
     * interior, and the stem column; the kind rolls per walk (the
     * reference's per-decorator instance state cannot survive the
     * re-derivation — the stateless answer, the counted note).
     */
    static final class HugeMushroomFeature implements FloraFeatures.Feature {
        /** [0] the brown family, [1] the red — each the 16-variant band. */
        private final BlockType[][] states;
        private int family = -1;

        HugeMushroomFeature(BlockType[][] states) {
            this.states = states;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            if (family < 0) {
                family = random.nextBoolean() ? 0 : 1;
            }
            int height = random.nextInt(3) + 4;
            boolean fits = true;
            if (y >= 1 && y + height + 1 < 256) {
                // lines 33-56: the clearance walk.
                for (int j = y; j <= y + 1 + height; j++) {
                    int k = j <= y + 3 ? 0 : 3;
                    for (int l = x - k; l <= x + k && fits; l++) {
                        for (int i1 = z - k; i1 <= z + k && fits; i1++) {
                            BlockType at = world.blockAt(l, j, i1);
                            if (!world.isAirAt(l, j, i1)
                                    && !at.identifier().value().endsWith("_leaves")) {
                                fits = false;
                            }
                        }
                    }
                }
                if (!fits) {
                    return false;
                }
                String soil = world.blockAt(x, y - 1, z).identifier().value();
                if (!soil.equals("dirt") && !soil.equals("grass_block")
                        && !soil.equals("mycelium")) {
                    return false;
                }
                int capY = family == 1 ? y + height - 3 : y + height;
                // lines 70-140: the cap.
                for (int l2 = capY; l2 <= y + height; l2++) {
                    int j3 = 1;
                    if (l2 < y + height) {
                        j3++;
                    }
                    if (family == 0) {
                        j3 = 3;
                    }
                    int k3 = x - j3;
                    int l3 = x + j3;
                    int j1 = z - j3;
                    int k1 = z + j3;
                    for (int l1 = k3; l1 <= l3; l1++) {
                        for (int i2 = j1; i2 <= k1; i2++) {
                            int j2 = 5;
                            if (l1 == k3) {
                                j2--;
                            } else if (l1 == l3) {
                                j2++;
                            }
                            if (i2 == j1) {
                                j2 -= 3;
                            } else if (i2 == k1) {
                                j2 += 3;
                            }
                            int variant = j2;
                            if (family == 0 || l2 < y + height) {
                                if ((l1 == k3 || l1 == l3) && (i2 == j1 || i2 == k1)) {
                                    continue;
                                }
                                if (l1 == x - (j3 - 1) && i2 == j1) {
                                    variant = 1; // north_west
                                }
                                if (l1 == k3 && i2 == z - (j3 - 1)) {
                                    variant = 1; // north_west
                                }
                                if (l1 == x + (j3 - 1) && i2 == j1) {
                                    variant = 3; // north_east
                                }
                                if (l1 == l3 && i2 == z - (j3 - 1)) {
                                    variant = 3; // north_east
                                }
                                if (l1 == x - (j3 - 1) && i2 == k1) {
                                    variant = 7; // south_west
                                }
                                if (l1 == k3 && i2 == z + (j3 - 1)) {
                                    variant = 7; // south_west
                                }
                                if (l1 == x + (j3 - 1) && i2 == k1) {
                                    variant = 9; // south_east
                                }
                                if (l1 == l3 && i2 == z + (j3 - 1)) {
                                    variant = 9; // south_east
                                }
                            }
                            if (variant == 5 && l2 < y + height) {
                                variant = 0; // all_inside
                            }
                            if (y >= y + height - 1 || variant != 0) {
                                if (!world.isOpaqueAt(l1, l2, i2)) {
                                    world.setBlockAt(l1, l2, i2, states[family][variant]);
                                }
                            }
                        }
                    }
                }
                // lines 142-149: the stem column.
                for (int i3 = 0; i3 < height; i3++) {
                    if (!world.isOpaqueAt(x, y + i3, z)) {
                        world.setBlockAt(x, y + i3, z, states[family][10]);
                    }
                }
                return true;
            }
            return false;
        }
    }

    /**
     * The two families' sixteen-state bands resolved from the registry
     * (the engine's flat variant states over the 99/100 wire ids —
     * MushroomBlocks' own rows).
     */
    private static BlockType[][] resolveMushroomStates(BlockRegistry registry) {
        String[] families = {"brown_mushroom_block", "red_mushroom_block"};
        String[] keys = {null, "north_west", "north", "north_east", "west",
                "center", "east", "south_west", "south", "south_east", "stem",
                null, null, null, null, "all_stem"};
        BlockType[][] states = new BlockType[2][16];
        for (int f = 0; f < 2; f++) {
            for (int variant = 0; variant < 16; variant++) {
                String value = variant == 14 ? families[f]
                        : keys[variant] != null ? families[f] + "_" + keys[variant]
                        : families[f];
                states[f][variant] = registry.require(
                        Identifier.parse("minecraft:" + value));
            }
        }
        return states;
    }
}
