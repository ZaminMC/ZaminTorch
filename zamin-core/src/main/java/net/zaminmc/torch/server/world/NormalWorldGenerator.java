package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * The full-world terrain generator (the historical "default" level-type):
 * value-noise heightmap with seas, beaches, dirt skins, stone cores, noise
 * caves, the depth-banded ore ladder, and oak trees on the grass.
 *
 * <p>Determinism contract (the same one FlatWorldGenerator keeps): the seed
 * is fixed per world name, so every boot rebuilds identical terrain without
 * storing generated chunks. Adopted in shape from the community independent
 * 1.8 server implementations (BlueDragonMC/Server, credited in
 * COMMUNITY_REFERENCES.md); the noise core is our own.</p>
 */
public final class NormalWorldGenerator implements WorldGenerator {

    /** The historical sea level: water fills every column up to here. */
    public static final int SEA_LEVEL = 62;

    private final BlockRegistry registry;
    private final long seed;
    /** The mineshaft placement layer (Slice 12a): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.MineshaftStructure mineshafts;
    /** The village placement layer (Slice 12b): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.VillageStructure villages;
    /** The stronghold placement layer (Slice 12c): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.StrongholdStructure strongholds;
    /** The ocean monument placement layer (Slice 12e): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.OceanMonumentStructure monuments;
    /** The temple placement layer (Slice 12f): deterministic per chunk. */
    private final net.zaminmc.torch.server.world.structure.TempleStructure temples;
    private final net.zaminmc.torch.server.world.structure.StructurePalette structurePalette;

    @Override
    public long seed() {
        return seed;
    }

    private final BlockType air;
    private final BlockType bedrock;
    private final BlockType stone;
    private final BlockType dirt;
    private final BlockType grass;
    private final BlockType sand;
    private final BlockType gravel;
    private final BlockType water;
    private final BlockType coalOre;
    private final BlockType ironOre;
    private final BlockType goldOre;
    private final BlockType redstoneOre;
    private final BlockType diamondOre;
    private final BlockType oakLog;
    private final BlockType oakLeaves;
    private final BlockType tallGrass;
    private final BlockType deadBush;
    private final BlockType dandelion;
    private final BlockType poppy;
    private final BlockType sandstone;
    private final BlockType sugarCane;
    private final BlockType cactus;

    /** The 1:4 biome grid (Layer.init's first answer — the terrain consult). */
    private final net.zaminmc.torch.server.world.layer.Layer gridLayer;
    /** The block-level biome map (Layer.init's second answer — the wire array). */
    private final net.zaminmc.torch.server.world.layer.Layer voronoiLayer;
    /**
     * The voronoi sample cache: 16x16-block chunks of the block-level
     * map (the reference's BiomeCache shape — the values pure in the
     * seed, the eviction transparent). Bounded, eldest-out.
     */
    private final java.util.LinkedHashMap<Long, int[]> blockBiomeCache =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<Long, int[]> eldest) {
                    return size() > 1024;
                }
            };
    /**
     * The 1:4 grid cache: 64x64-block regions (16x16 grid cells) of the
     * terrain's own biome consult — the height bands read this map.
     */
    private final java.util.LinkedHashMap<Long, int[]> gridBiomeCache =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<Long, int[]> eldest) {
                    return size() > 512;
                }
            };

    public NormalWorldGenerator(BlockRegistry registry, String worldName) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.seed = 0x5A4D494E5C00L ^ worldName.hashCode() * 0x9E3779B97F4L;
        // The biome layout (VIII14's second slice — Layer.init 1:1, the
        // DEFAULT type's chain): grid[0] answers the 1:4 biome grid the
        // terrain's height bands consult, voronoi[1] answers the
        // block-level biome map the chunk's wire array carries. The
        // caches hold the last regions sampled (bounded LRU — the
        // values are pure functions of the seed, eviction transparent).
        net.zaminmc.torch.server.world.layer.Layer[] layers =
                net.zaminmc.torch.server.world.layer.Layer.init(this.seed);
        this.gridLayer = layers[0];
        this.voronoiLayer = layers[1];
        this.mineshafts = new net.zaminmc.torch.server.world.structure.MineshaftStructure(this.seed);
        this.villages = new net.zaminmc.torch.server.world.structure.VillageStructure(this.seed);
        this.structurePalette = net.zaminmc.torch.server.world.structure.StructurePalette.of(registry);
        // The strongholds need the biome probe at construction time (the
        // positions walk's findBiome snap).
        net.zaminmc.torch.server.world.structure.StructureWorld positionView =
                new net.zaminmc.torch.server.world.structure.StructureWorld() {
                    @Override
                    public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                        return null;
                    }

                    @Override
                    public boolean isLiquid(net.zaminmc.torch.block.BlockType type) {
                        return false;
                    }

                    @Override
                    public void setBlock(int x, int y, int z, net.zaminmc.torch.block.BlockType type) {
                    }

                    @Override
                    public int seaLevel() {
                        return SEA_LEVEL;
                    }

                    @Override
                    public net.zaminmc.torch.server.world.structure.StructurePalette palette() {
                        return structurePalette;
                    }

                    @Override
                    public boolean isOpaque(net.zaminmc.torch.block.BlockType type) {
                        return false;
                    }

                    @Override
                    public Biome biomeAt(int x, int z) {
                        return NormalWorldGenerator.this.biomeAt(x, z);
                    }
                };
        this.strongholds = new net.zaminmc.torch.server.world.structure.StrongholdStructure(
                this.seed, positionView);
        this.monuments = new net.zaminmc.torch.server.world.structure.OceanMonumentStructure(this.seed);
        this.temples = new net.zaminmc.torch.server.world.structure.TempleStructure(this.seed);
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
        this.stone = registry.require(Identifier.parse("minecraft:stone"));
        this.dirt = registry.require(Identifier.parse("minecraft:dirt"));
        this.grass = registry.require(Identifier.parse("minecraft:grass_block"));
        this.sand = registry.require(Identifier.parse("minecraft:sand"));
        this.gravel = registry.require(Identifier.parse("minecraft:gravel"));
        this.water = registry.require(Identifier.parse("minecraft:water"));
        this.coalOre = registry.require(Identifier.parse("minecraft:coal_ore"));
        this.ironOre = registry.require(Identifier.parse("minecraft:iron_ore"));
        this.goldOre = registry.require(Identifier.parse("minecraft:gold_ore"));
        this.redstoneOre = registry.require(Identifier.parse("minecraft:redstone_ore"));
        this.diamondOre = registry.require(Identifier.parse("minecraft:diamond_ore"));
        this.oakLog = registry.require(Identifier.parse("minecraft:oak_log"));
        this.oakLeaves = registry.require(Identifier.parse("minecraft:oak_leaves"));
        this.tallGrass = registry.require(Identifier.parse("minecraft:tall_grass"));
        this.deadBush = registry.require(Identifier.parse("minecraft:dead_bush"));
        this.dandelion = registry.require(Identifier.parse("minecraft:dandelion"));
        this.poppy = registry.require(Identifier.parse("minecraft:poppy"));
        this.sandstone = registry.require(Identifier.parse("minecraft:sandstone"));
        this.sugarCane = registry.require(Identifier.parse("minecraft:sugar_cane"));
        this.cactus = registry.require(Identifier.parse("minecraft:cactus"));
        // The spawn scan LAST (the layers and the structures are up; the
        // scan consults biomeAt and heightAt over the layout).
        this.spawnColumnHeight = scanSpawnColumn();
    }

    /**
     * The column's biome (VIII14's second slice — the vanilla layout):
     * the block-level voronoi map over the ~30-layer chain, the chunk
     * cache answering (the reference's BiomeCache shape —
     * BiomeSource.getBiome over alayer[1]). The old climate-noise
     * shading is retired: every biome the reference's DEFAULT chain
     * produces can now generate, the mutated forms included (the
     * RegionHills +128 band, the sunflower roll).
     */
    public Biome biomeAt(int x, int z) {
        long key = (long) (x >> 4) | ((long) (z >> 4) << 32);
        int[] cell;
        synchronized (blockBiomeCache) {
            cell = blockBiomeCache.get(key);
            if (cell == null) {
                cell = sampleArea(voronoiLayer, (x >> 4) << 4,
                        (z >> 4) << 4, 16, 16);
                blockBiomeCache.put(key, cell);
            }
        }
        int id = cell[(x & 15) + (z & 15) * 16] & 0xFF;
        Biome biome = Biome.byLegacyId(id);
        return biome == null ? Biome.PLAINS : biome;
    }

    /**
     * The terrain's own biome consult: the 1:4 grid sample (the
     * reference's getNoiseBiomes — the height-band input the generator's
     * own 5x5 kernel walk reads).
     */
    private Biome gridBiomeAt(int x, int z) {
        int cellX = x >> 2;
        int cellZ = z >> 2;
        long key = (long) (cellX >> 4) | ((long) (cellZ >> 4) << 32);
        int[] region;
        synchronized (gridBiomeCache) {
            region = gridBiomeCache.get(key);
            if (region == null) {
                region = sampleArea(gridLayer, (cellX >> 4) << 4,
                        (cellZ >> 4) << 4, 16, 16);
                gridBiomeCache.put(key, region);
            }
        }
        int id = region[(cellX & 15) + (cellZ & 15) * 16] & 0xFF;
        Biome biome = Biome.byLegacyId(id);
        return biome == null ? Biome.PLAINS : biome;
    }

    /**
     * The shared sampling lock: the grid and voronoi chains share their
     * layers (the voronoi's parent IS the grid layer), and every layer
     * carries mutable seed state — the reference's own single-threaded
     * discipline, the engine's lock.
     */
    private final Object layerLock = new Object();

    /**
     * One top-level area query: the reference's IntCache discipline
     * (pop the previous query's arrays back to the reuse pool, push the
     * new one) with the result CLONED out — the cached array must never
     * ride the reuse pool (a later query would overwrite it).
     */
    private int[] sampleArea(
            net.zaminmc.torch.server.world.layer.Layer layer,
            int x, int z, int sizeX, int sizeZ) {
        synchronized (layerLock) {
            net.zaminmc.torch.server.world.layer.IntCache.pop();
            return layer.getArea(x, z, sizeX, sizeZ).clone();
        }
    }

    @Override
    public int groundLevel() {
        return spawnColumnHeight;
    }

    @Override
    public net.zaminmc.torch.util.Position spawnPosition() {
        return new net.zaminmc.torch.util.Position(spawnX + 0.5, spawnColumnHeight + 1.0, spawnZ + 0.5);
    }

    /** The first dry column on the +x diagonal (deterministic spawn anchor). */
    private int spawnX;
    private int spawnZ;
    private int spawnColumnHeight;

    /**
     * The spawn column scan (the reference's own allowlist —
     * BiomeSource.biomesForSpawnPoint: FOREST, PLAINS, TAIGA,
     * TAIGA_HILLS, FOREST_HILLS, JUNGLE, JUNGLE_HILLS): the first dry
     * column on the +x diagonal inside one of the seven, the (0,0)
     * land pin guaranteeing the search starts on a continent.
     */
    private int scanSpawnColumn() {
        for (int r = 0; r < 256; r += 4) {
            int x = r;
            int z = r;
            Biome biome = biomeAt(x, z);
            boolean allowed = biome == Biome.FOREST || biome == Biome.PLAINS
                    || biome == Biome.TAIGA || biome == Biome.TAIGA_HILLS
                    || biome == Biome.FOREST_HILLS || biome == Biome.JUNGLE
                    || biome == Biome.JUNGLE_HILLS;
            int h = heightAt(x, z);
            if (allowed && h > SEA_LEVEL + 1 && h < 100) {
                spawnX = x;
                spawnZ = z;
                return h;
            }
        }
        spawnX = 0;
        spawnZ = 0;
        return Math.max(heightAt(0, 0), SEA_LEVEL + 1);
    }

    @Override
    public void generate(EngineChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        net.zaminmc.torch.block.ChunkPosition at = chunk.position();
        int baseX = at.x() << 4;
        int baseZ = at.z() << 4;
        int[][] heights = new int[16][16];
        Biome[][] biomes = new Biome[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                heights[lx][lz] = heightAt(baseX + lx, baseZ + lz);
                biomes[lx][lz] = biomeAt(baseX + lx, baseZ + lz);
                chunk.setBiome(lx, lz, (byte) biomes[lx][lz].legacyId());
            }
        }
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                int h = heights[lx][lz];
                boolean underwater = h < SEA_LEVEL;
                for (int y = 0; y <= Math.max(h, SEA_LEVEL); y++) {
                    chunk.setBlock(lx, y, lz, blockAt(worldX, y, worldZ, h, underwater,
                            biomes[lx][lz]));
                }
            }
        }
        plantTrees(chunk, baseX, baseZ, heights, biomes);
        plantFlora(chunk, baseX, baseZ, heights, biomes);
        // The structure pass (Slice 12a — the populate hook): the region
        // scan + the clipped piece writes, after the terrain and the
        // flora exactly like the reference's populate step.
        populateStructures(chunk, baseX, baseZ);
        // The animal seeding (the reference's populate step's LAST pass —
        // OverworldChunkGenerator line 393, after biome.decorate).
        seedAnimals(chunk, baseX, baseZ, heights);
    }

    /**
     * The structure pass: the mineshaft region walk over this chunk's
     * own view (in-chunk reads from the chunk, out-of-chunk reads from
     * the pure terrain — the deterministic equivalent of the reference's
     * populate-time world reads).
     */
    private void populateStructures(EngineChunk chunk, int baseX, int baseZ) {
        net.zaminmc.torch.server.world.structure.StructureWorld view =
                new net.zaminmc.torch.server.world.structure.StructureWorld() {
                    @Override
                    public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                        if (y < 0 || y > 255) {
                            return null;
                        }
                        int localX = x - baseX;
                        int localZ = z - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            return chunk.getBlock(localX, y, localZ);
                        }
                        return surfaceBlockAt(x, y, z);
                    }

                    @Override
                    public boolean isLiquid(net.zaminmc.torch.block.BlockType type) {
                        if (type == null) {
                            return false;
                        }
                        net.zaminmc.torch.util.Identifier id = type.identifier();
                        return id.namespace().equals("minecraft")
                                && (id.value().equals("water") || id.value().equals("lava"));
                    }

                    @Override
                    public void setBlock(int x, int y, int z, net.zaminmc.torch.block.BlockType type) {
                        if (y < 0 || y > 255) {
                            return;
                        }
                        int localX = x - baseX;
                        int localZ = z - baseZ;
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            chunk.setBlock(localX, y, localZ, type);
                        }
                    }

                    @Override
                    public int seaLevel() {
                        return SEA_LEVEL;
                    }

                    @Override
                    public net.zaminmc.torch.server.world.structure.StructurePalette palette() {
                        return structurePalette;
                    }

                    @Override
                    public boolean isOpaque(net.zaminmc.torch.block.BlockType type) {
                        return net.zaminmc.torch.server.block.WorldSolidity.isSolid(type);
                    }

                    @Override
                    public int surfaceHeight(int x, int z) {
                        // The pure probe — never the chunk's array — so a
                        // piece spanning chunks re-bases identically in
                        // every chunk that touches it (Slice 12b's
                        // deterministic-contract note).
                        return heightAt(x, z);
                    }

                    @Override
                    public Biome biomeAt(int x, int z) {
                        return NormalWorldGenerator.this.biomeAt(x, z);
                    }

                    @Override
                    public void spawnVillager(int x, int y, int z, int profession) {
                        chunk.addPendingVillager(x, y, z, profession);
                    }

                    @Override
                    public void spawnElderGuardian(int x, int y, int z) {
                        chunk.addPendingElderGuardian(x, y, z);
                    }

                    @Override
                    public void placeGenChest(int x, int y, int z,
                            java.util.List<net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot> slots) {
                        chunk.addPendingGenChest(x, y, z, slots);
                    }

                    @Override
                    public void placeGenDispenser(int x, int y, int z, int facing,
                            java.util.List<net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot> slots) {
                        chunk.addPendingGenDispenser(x, y, z, facing, slots);
                    }

                    @Override
                    public void placeGenChestMinecart(int x, int y, int z,
                            java.util.List<net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot> slots) {
                        chunk.addPendingChestMinecart(x, y, z, slots);
                    }

                    @Override
                    public void placeMobSpawner(int x, int y, int z,
                            net.zaminmc.torch.server.entity.MobType mobType) {
                        chunk.addPendingMobSpawner(x, y, z, mobType);
                    }
                };
        mineshafts.populate(chunk.position().x(), chunk.position().z(), view);
        villages.populate(chunk.position().x(), chunk.position().z(), view);
        strongholds.populate(chunk.position().x(), chunk.position().z(), view);
        monuments.populate(chunk.position().x(), chunk.position().z(), view);
        temples.populate(chunk.position().x(), chunk.position().z(), view);
    }

    /**
     * The nearest stronghold's start-piece position to a point (the eye
     * of ender's target consult — the reference's
     * {@code world.findNearestStructure("Stronghold", pos)}, EnderEyeItem
     * lines 121-133): the positions table's nearest entry answered as the
     * START PIECE'S own placement — ((chunkX &lt;&lt; 4) + 2, 64, (chunkZ
     * &lt;&lt; 4) + 2), the pieces' own start-room convention (the 1.8.8
     * StrongholdStart at y=64, the +2 start corner) — the derivation
     * named per §27.9. The engine's table always answers (the three
     * positions exist by construction); the reference's not-found no-op
     * never occurs on the engine's gen.
     */
    public double[] nearestStrongholdTo(double x, double z) {
        int[][] table = strongholds.positionTable();
        int[] best = table[0];
        double bestDistance = Double.MAX_VALUE;
        for (int[] position : table) {
            double px = (position[0] << 4) + 2.0;
            double pz = (position[1] << 4) + 2.0;
            double dx = px - x;
            double dz = pz - z;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = position;
            }
        }
        return new double[]{(best[0] << 4) + 2.0, 64.0, (best[1] << 4) + 2.0};
    }

    /** The block for one column cell: core, skin, water, cave or ore. */
    private BlockType blockAt(int x, int y, int z, int h, boolean underwater, Biome biome) {
        if (y == 0) {
            return bedrock;
        }
        if (y > h) {
            return y <= SEA_LEVEL ? water : air;
        }
        // Caves carve the core below the skin; the surface stays intact.
        if (y > 4 && y < h - 3 && isCave(x, y, z)) {
            return air;
        }
        int skinDepth = h - y;
        if (surfaceIsSandFamily(biome) && !underwater) {
            // The sand family's skin (the registration table's own
            // surface pair — the desert/beach bands): three sand over a
            // sandstone band, the historical sand-sandstone column shape.
            if (skinDepth <= 2) {
                return sand;
            }
            if (skinDepth <= 6) {
                return sandstone;
            }
            return oreOrStone(x, y, z);
        }
        if (biome == Biome.STONE_BEACH && !underwater && skinDepth <= 3) {
            // The stone beach's skin (the registration pair: stone/stone).
            return stone;
        }
        if (skinDepth == 0) {
            if (underwater) {
                return seaFloor(x, z);
            }
            return surfaceMaterialOf(biome, h);
        }
        if (skinDepth <= 3) {
            return underwater ? sand : dirt;
        }
        return oreOrStone(x, y, z);
    }

    /**
     * The sand-surface family (the registration table's surface pair:
     * the desert pair, the beach pair, and their mutated forms).
     */
    private static boolean surfaceIsSandFamily(Biome biome) {
        return biome.surfaceLegacyId() == 12;
    }

    /**
     * The surface material (the registration table's surfaceLegacyId —
     * the first slice's own consult): grass on the temperate band, the
     * mycelium on the mushroom isle, the snow on the spikes, the red
     * sand on the mesa family; the height band's own beach sand stays
     * the near-sea fallback.
     */
    private BlockType surfaceMaterialOf(Biome biome, int h) {
        if (h <= SEA_LEVEL + 1) {
            return sand; // the near-sea band's own beach
        }
        return switch (biome.surfaceLegacyId()) {
            case 1 -> stone;
            case 12 -> sand;
            case 80 -> Objects.requireNonNull(
                    registry.lookup(Identifier.parse("minecraft:snow"))
                            .orElse(grass), "snow"); // the ice spikes' surface
            case 110 -> Objects.requireNonNull(
                    registry.lookup(Identifier.parse("minecraft:mycelium"))
                            .orElse(grass), "mycelium");
            case 179 -> Objects.requireNonNull(
                    registry.lookup(Identifier.parse("minecraft:red_sand"))
                            .orElse(sand), "red sand");
            default -> grass; // the grass/dirt temperate default
        };
    }

    private BlockType seaFloor(int x, int z) {
        return hash2(x, z, 0x9E37) % 5 == 0 ? gravel : sand;
    }

    /** The depth-banded ore ladder (community blocks.json distributions). */
    private BlockType oreOrStone(int x, int y, int z) {
        long roll = hash3(x, y, z, 0x51ED);
        if (y <= 13 && roll % 10_000 < 12) {
            return diamondOre;
        }
        if (y <= 16 && roll % 10_000 < 22) {
            return redstoneOre;
        }
        if (y <= 30 && roll % 10_000 < 20) {
            return goldOre;
        }
        if (y <= 54 && roll % 10_000 < 60) {
            return ironOre;
        }
        if (y <= 60 && roll % 10_000 < 80) {
            return coalOre;
        }
        return stone;
    }

    /**
     * The column height (VIII14's second slice — the biome-band
     * coupling): the 1:4 grid biome's own registration height pair
     * (the baseHeight/heightVariation the registration table carries)
     * names the band — {@code 64 + baseHeight * 17} over the engine's
     * own fbm variation scaled by the biome's variation band (the
     * named adaptation: the reference's own 4-noise density stack
     * interpolates the same bands through its 5x5 kernel — the engine's
     * column model answers the same ordering and magnitudes, its fbm
     * standing in for the perlin stack). The land/ocean coherence is
     * the law: ocean cells sit below the sea, land cells above, rivers
     * carve (the river band's -0.5 base), the plateaus tower.
     */
    public int heightAt(int x, int z) {
        Biome biome = gridBiomeAt(x, z);
        double rolling = fbm2(x * 0.0085, z * 0.0085, 3);
        double mountains = fbm2(x * 0.0035 + 1_000, z * 0.0035 - 1_000, 2);
        int base = 64 + Math.round(biome.baseHeight() * 17.0f);
        double hill = (rolling - 0.5) * (6.0 + biome.heightVariation() * 17.0);
        double ridge = Math.max(0.0, mountains - 0.58) * 90.0
                * (0.5 + biome.heightVariation());
        int h = base + (int) Math.round(hill + ridge);
        return Math.max(8, Math.min(120, h));
    }

    /**
     * The world block of one column cell (the diagnostics view of the
     * generation surface: biome-aware, no chunk allocation).
     */
    public BlockType surfaceBlockAt(int x, int y, int z) {
        int h = heightAt(x, z);
        return blockAt(x, y, z, h, h < SEA_LEVEL, biomeAt(x, z));
    }

    private boolean isCave(int x, int y, int z) {
        double worm = fbm3(x * 0.045, y * 0.09, z * 0.045);
        return worm > 0.74;
    }

    // ---------------------------------------------------------- worldgen animal seeding
    // (NaturalSpawner.populateChunk 1:1 — the reference's populate step's
    // LAST pass: OverworldChunkGenerator.populateChunk line 393)

    /** Biome.getAnimalSpawnChance (line 401): the flat 10% seeding chance. */
    private static final float ANIMAL_SPAWN_CHANCE = 0.1F;

    /**
     * The animal seeding walk (NaturalSpawner.populateChunk lines 164-215,
     * 1:1): the {@code while (nextFloat() < 0.1)} group roll, the weighted
     * pick, the group size, the 4-attempt placement per member with the
     * +8/+16 region and the nextInt(5) drift walk with the leash re-anchor,
     * the horse group's shared dice (the reference's initialize: the first
     * member's type + color, the re-rolled marking per member, the 1-in-5
     * baby), and the yaw roll per placed member.
     *
     * <p>The animals join the world through the chunk's pending records —
     * the drain materializes them exactly once (the villagers' own
     * pattern). The region is the reference's own {@code [chunk+8,
     * chunk+24)} span: the records ride THIS chunk even when a member
     * walks into the neighbor's strip (the reference's addEntity is
     * world-global; the engine's mob manager is too).</p>
     */
    private void seedAnimals(EngineChunk chunk, int baseX, int baseZ, int[][] heights) {
        // The reference reads the biome at the chunk's +16 corner
        // (OverworldChunkGenerator line 336: blockpos.add(16, 0, 16)).
        Biome biome = biomeAt(baseX + 16, baseZ + 16);
        // The registration table's own creature list (the spawn-tables
        // slice: the reference's populateChunk reads
        // biome.getSpawnEntries(CREATURE) — the desert/ocean/river/
        // beach/ice/mesa/stone families' cleared tables refuse the
        // seeding here now, the jungle's second chicken carries its
        // weight twice, and the ocelot NEVER seeds at populate (its row
        // rides the monster table — the natural cycle's jungle consult,
        // the engine's own earlier stand-in retired).
        List<Biome.SpawnEntry> rows = biome.creatureEntries();
        if (rows.isEmpty()) {
            return;
        }
        // The deterministic per-chunk random (the reference's
        // setRandomSeed(chunkX * k + chunkZ * l ^ seed) equivalent — the
        // engine's hash-seed family).
        long chunkSeed = seed ^ (chunk.position().x() * 0x68E31DA4L)
                ^ (chunk.position().z() * 0xB5297A4DL);
        java.util.Random random = new java.util.Random(chunkSeed);
        int totalWeight = 0;
        for (Biome.SpawnEntry row : rows) {
            totalWeight += row.weight();
        }
        int centerX = baseX + 8;
        int centerZ = baseZ + 8;

        while (random.nextFloat() < ANIMAL_SPAWN_CHANCE) {
            // WeightedPicker.pick: the cumulative walk over the table.
            int roll = random.nextInt(totalWeight);
            Biome.SpawnEntry picked = rows.get(rows.size() - 1);
            int cumulative = 0;
            for (Biome.SpawnEntry row : rows) {
                cumulative += row.weight();
                if (roll < cumulative) {
                    picked = row;
                    break;
                }
            }
            int groupSize = picked.minGroup()
                    + random.nextInt(1 + picked.maxGroup() - picked.minGroup());
            int j = centerX + random.nextInt(16);
            int k = centerZ + random.nextInt(16);
            int anchorJ = j;
            int anchorK = k;
            // The horse group's shared entitydata (the reference's
            // EntityHorseGroupData: null until the first horse initializes).
            Integer horseSubtype = null;
            int horseColor = 0;

            for (int member = 0; member < groupSize; member++) {
                boolean placed = false;
                for (int attempt = 0; !placed && attempt < 4; attempt++) {
                    int surfaceY = surfaceYFor(chunk, heights, baseX, baseZ, j, k);
                    if (validAnimalGround(chunk, baseX, baseZ, j, k, surfaceY)) {
                        float yaw = random.nextFloat() * 360.0F;
                        int subtype = 0;
                        int variant = 0;
                        if (picked.type() == net.zaminmc.torch.server.entity.MobType.HORSE) {
                            // HorseBaseEntity.initialize (lines 1262-1280): the
                            // first member rolls the type + the coat, the pack
                            // shares the type + the color and re-rolls markings.
                            if (horseSubtype == null) {
                                if (random.nextInt(10) == 0) {
                                    horseSubtype = net.zaminmc.torch.server.entity.MobEntity
                                            .HORSE_SUBTYPE_DONKEY;
                                    subtype = horseSubtype;
                                } else {
                                    horseSubtype = net.zaminmc.torch.server.entity.MobEntity
                                            .HORSE_SUBTYPE_HORSE;
                                    subtype = horseSubtype;
                                    variant = random.nextInt(7) | random.nextInt(5) << 8;
                                    horseColor = variant & 0xFF;
                                }
                            } else {
                                subtype = horseSubtype;
                                variant = horseColor | random.nextInt(5) << 8;
                            }
                        }
                        boolean baby = picked.type() == net.zaminmc.torch.server.entity.MobType.HORSE
                                && random.nextInt(5) == 0;
                        chunk.addPendingAnimal(new net.zaminmc.torch.server.world.EngineChunk
                                .PendingAnimal(picked.type(), j + 0.5, surfaceY, k + 0.5,
                                yaw, baby, subtype, variant));
                        placed = true;
                    }
                    // The drift + the leash (the reference's own loop shape,
                    // lines 205-215 — the k bound checks rangeX, the
                    // reference's own quirk; identical here at 16 == 16).
                    j += random.nextInt(5) - random.nextInt(5);
                    for (k += random.nextInt(5) - random.nextInt(5);
                            j < centerX || j >= centerX + 16
                                    || k < centerZ || k >= centerZ + 16;
                            k = anchorK + random.nextInt(5) - random.nextInt(5)) {
                        j = anchorJ + random.nextInt(5) - random.nextInt(5);
                    }
                }
            }
        }
    }

    /**
     * The surface height for the seeding (the reference's
     * getSurfaceHeight): the first free cell above the ground — in-chunk
     * the generated terrain's own height, out-of-chunk the pure heightAt
     * (the deterministic neighbor answer, Slice 12b's own contract).
     */
    private int surfaceYFor(EngineChunk chunk, int[][] heights, int baseX, int baseZ,
                            int x, int z) {
        int localX = x - baseX;
        int localZ = z - baseZ;
        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
            return heights[localX][localZ] + 1;
        }
        return heightAt(x, z) + 1;
    }

    /**
     * The placement gate (isValidSpawnPos' ON_GROUND arm, lines 137-157):
     * the ground below is a solid block (not bedrock), the cell itself is
     * not solid, and the cell above is not solid either. In-chunk reads
     * from the chunk (the trees' trunks refuse), out-of-chunk from the
     * pure terrain (the Slice 12b contract).
     */
    private boolean validAnimalGround(EngineChunk chunk, int baseX, int baseZ,
                                      int x, int z, int surfaceY) {
        if (surfaceY <= 1 || surfaceY > 255) {
            return false;
        }
        BlockType ground = blockFor(chunk, baseX, baseZ, x, surfaceY - 1, z);
        if (!net.zaminmc.torch.server.block.WorldSolidity.isSolid(ground)
                || ground.equals(bedrock)) {
            return false;
        }
        BlockType cell = blockFor(chunk, baseX, baseZ, x, surfaceY, z);
        BlockType above = blockFor(chunk, baseX, baseZ, x, surfaceY + 1, z);
        return !net.zaminmc.torch.server.block.WorldSolidity.isSolid(cell)
                && !net.zaminmc.torch.server.block.WorldSolidity.isSolid(above);
    }

    /** One cell read: the chunk inside, the pure terrain outside. */
    private BlockType blockFor(EngineChunk chunk, int baseX, int baseZ,
                               int x, int y, int z) {
        int localX = x - baseX;
        int localZ = z - baseZ;
        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
            return chunk.getBlock(localX, y, localZ);
        }
        return surfaceBlockAt(x, y, z);
    }

    // ------------------------------------------------------------------ noise core

    /** 2D value noise fBm: three octaves, quintic-smoothed bilinear lattice. */
    private double fbm2(double x, double z, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double norm = 0.0;
        for (int o = 0; o < octaves; o++) {
            sum += valueNoise2(x * frequency, z * frequency) * amplitude;
            norm += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / norm;
    }

    private double fbm3(double x, double y, double z) {
        return (valueNoise3(x, y, z) * 0.65
                + valueNoise3(x * 2.03 + 31.7, y * 2.03, z * 2.03 - 17.3) * 0.35);
    }

    private double valueNoise2(double x, double z) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        double tx = smooth(x - xi);
        double tz = smooth(z - zi);
        double v00 = lattice2(xi, zi);
        double v10 = lattice2(xi + 1, zi);
        double v01 = lattice2(xi, zi + 1);
        double v11 = lattice2(xi + 1, zi + 1);
        return lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz);
    }

    private double valueNoise3(double x, double y, double z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double tx = smooth(x - xi);
        double ty = smooth(y - yi);
        double tz = smooth(z - zi);
        double c000 = lattice3(xi, yi, zi);
        double c100 = lattice3(xi + 1, yi, zi);
        double c010 = lattice3(xi, yi + 1, zi);
        double c110 = lattice3(xi + 1, yi + 1, zi);
        double c001 = lattice3(xi, yi, zi + 1);
        double c101 = lattice3(xi + 1, yi, zi + 1);
        double c011 = lattice3(xi, yi + 1, zi + 1);
        double c111 = lattice3(xi + 1, yi + 1, zi + 1);
        return lerp(
                lerp(lerp(c000, c100, tx), lerp(c010, c110, tx), ty),
                lerp(lerp(c001, c101, tx), lerp(c011, c111, tx), ty),
                tz);
    }

    private double lattice2(int x, int z) {
        long h = seed ^ (x * 0x27D4EB2DL) ^ (z * 0x165667B1L);
        h = (h ^ (h >>> 15)) * 0x2C1B3C6DL;
        h = (h ^ (h >>> 12)) * 0x297F2D39L;
        return ((h ^ (h >>> 15)) & 0xFFFFFFL) / (double) 0xFFFFFFL;
    }

    private double lattice3(int x, int y, int z) {
        long h = seed ^ (x * 0x27D4EB2DL) ^ (y * 0x9E3779B1L) ^ (z * 0x165667B1L);
        h = (h ^ (h >>> 15)) * 0x2C1B3C6DL;
        h = (h ^ (h >>> 13)) * 0x297F2D39L;
        return ((h ^ (h >>> 16)) & 0xFFFFFFL) / (double) 0xFFFFFFL;
    }

    private long hash2(int x, int z, int salt) {
        long h = seed ^ (salt * 0x2545F491L) ^ (x * 0x27D4EB2DL) ^ (z * 0x165667B1L);
        return (h ^ (h >>> 15)) * 0x2C1B3C6DL ^ (h >>> 7);
    }

    private long hash3(int x, int y, int z, int salt) {
        long h = seed ^ (salt * 0x2545F491L) ^ (x * 0x27D4EB2DL)
                ^ (y * 0x9E3779B1L) ^ (z * 0x165667B1L);
        return (h ^ (h >>> 15)) * 0x2C1B3C6DL ^ (h >>> 7);
    }

    private static double smooth(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------ trees

    /**
     * Plants 0-6 oak trees per chunk on grass: the biome decides the density
     * (forest 2-5, plains 0-2, desert none). Positions stay inside the 2..13
     * margin so the two-radius canopy never leaks across the chunk border
     * (the cheap deterministic answer to cross-chunk structures).
     */
    private void plantTrees(EngineChunk chunk, int baseX, int baseZ, int[][] heights,
                            Biome[][] biomes) {
        net.zaminmc.torch.block.ChunkPosition at = chunk.position();
        long chunkSeed = seed ^ (at.x() * 0x5DEECE66DL) ^ (at.z() * 0x2545F4914F6CDD1DL);
        java.util.Random random = new java.util.Random(chunkSeed);
        Biome chunkBiome = biomes[8][8];
        int trees;
        switch (chunkBiome) {
            case FOREST -> trees = 2 + random.nextInt(4);
            case PLAINS -> trees = random.nextInt(3);
            default -> trees = 0; // deserts never grow oaks
        }
        for (int i = 0; i < trees; i++) {
            int lx = 2 + random.nextInt(12);
            int lz = 2 + random.nextInt(12);
            int h = heights[lx][lz];
            if (h <= SEA_LEVEL + 1 || h > 110) {
                continue; // no trees on beaches or in water or on peaks
            }
            if (biomes[lx][lz] == Biome.DESERT || chunk.getBlock(lx, h, lz).equals(sand)) {
                continue;
            }
            int trunk = 4 + random.nextInt(3);
            for (int y = 1; y <= trunk; y++) {
                chunk.setBlock(lx, h + y, lz, oakLog);
            }
            for (int dy = trunk - 2; dy <= trunk + 1; dy++) {
                int radius = dy >= trunk ? 1 : 2;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx == 0 && dz == 0 && dy <= trunk) {
                            continue; // the trunk cell stays wood
                        }
                        if (Math.abs(dx) == radius && Math.abs(dz) == radius
                                && random.nextBoolean()) {
                            continue; // the historical rounded corners
                        }
                        int leafX = lx + dx;
                        int leafZ = lz + dz;
                        int leafY = h + dy;
                        if (leafY > 127 || !chunk.getBlock(leafX, leafY, leafZ).equals(air)) {
                            continue;
                        }
                        chunk.setBlock(leafX, leafY, leafZ, oakLeaves);
                    }
                }
            }
        }
    }

    /**
     * The biome flora rolls: grass and the two flowers on the plains and the
     * forest floor, dead bushes on the desert sand. All placements target
     * air cells directly above the surface skin (never underwater), the
     * historical decoration pass's shape.
     */
    private void plantFlora(EngineChunk chunk, int baseX, int baseZ, int[][] heights,
                            Biome[][] biomes) {
        net.zaminmc.torch.block.ChunkPosition at = chunk.position();
        long chunkSeed = seed ^ (at.x() * 0x4FA3F0B1L) ^ (at.z() * 0x9E3779B97F4L);
        java.util.Random random = new java.util.Random(chunkSeed);
        Biome chunkBiome = biomes[8][8];
        if (chunkBiome == Biome.DESERT) {
            // Dead bushes: four rolls per desert chunk on the sand skin.
            for (int i = 0; i < 4; i++) {
                int lx = random.nextInt(16);
                int lz = random.nextInt(16);
                int h = heights[lx][lz];
                if (h <= SEA_LEVEL || !chunk.getBlock(lx, h, lz).equals(sand)) {
                    continue;
                }
                if (chunk.getBlock(lx, h + 1, lz).equals(air)) {
                    chunk.setBlock(lx, h + 1, lz, deadBush);
                }
            }
            // Cacti: two rolls per desert chunk, 1-3 tall on open sand (the
            // historical decoration pass's desert pair).
            for (int i = 0; i < 2; i++) {
                int lx = random.nextInt(16);
                int lz = random.nextInt(16);
                int h = heights[lx][lz];
                if (h <= SEA_LEVEL || !chunk.getBlock(lx, h, lz).equals(sand)) {
                    continue;
                }
                if (!chunk.getBlock(lx, h + 1, lz).equals(air)) {
                    continue;
                }
                int tall = 1 + random.nextInt(3);
                for (int y = 1; y <= tall; y++) {
                    if (!chunk.getBlock(lx, h + y, lz).equals(air)) {
                        break; // an overhang stops the stack
                    }
                    chunk.setBlock(lx, h + y, lz, cactus);
                }
            }
            return;
        }
        // Grass: eight rolls, the meadow's main texture.
        for (int i = 0; i < 8; i++) {
            int lx = random.nextInt(16);
            int lz = random.nextInt(16);
            int h = heights[lx][lz];
            if (h <= SEA_LEVEL || !chunk.getBlock(lx, h, lz).equals(grass)) {
                continue;
            }
            if (chunk.getBlock(lx, h + 1, lz).equals(air)) {
                chunk.setBlock(lx, h + 1, lz, tallGrass);
            }
        }
        // Flowers: two rolls, poppy or dandelion (the historical pair).
        for (int i = 0; i < 2; i++) {
            int lx = random.nextInt(16);
            int lz = random.nextInt(16);
            int h = heights[lx][lz];
            if (h <= SEA_LEVEL || !chunk.getBlock(lx, h, lz).equals(grass)) {
                continue;
            }
            if (chunk.getBlock(lx, h + 1, lz).equals(air)) {
                chunk.setBlock(lx, h + 1, lz, random.nextBoolean() ? poppy : dandelion);
            }
        }
        // Sugar cane: three rolls on shore columns — a land column beside a
        // water column (within this chunk's own grid) grows a 1-3 reed. The
        // within-chunk scan misses a few chunk-edge shores; the growth clock
        // spreads the reeds from any planted one.
        for (int i = 0; i < 3; i++) {
            int lx = 1 + random.nextInt(14);
            int lz = 1 + random.nextInt(14);
            int h = heights[lx][lz];
            if (h <= SEA_LEVEL || !chunk.getBlock(lx, h, lz).equals(grass)
                    && !chunk.getBlock(lx, h, lz).equals(sand)) {
                continue;
            }
            if (!chunk.getBlock(lx, h + 1, lz).equals(air)) {
                continue;
            }
            boolean shore = false;
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (heights[lx + dir[0]][lz + dir[1]] <= SEA_LEVEL) {
                    shore = true;
                    break;
                }
            }
            if (!shore) {
                continue;
            }
            int tall = 1 + random.nextInt(3);
            for (int y = 1; y <= tall; y++) {
                chunk.setBlock(lx, h + y, lz, sugarCane);
            }
        }
    }
}
