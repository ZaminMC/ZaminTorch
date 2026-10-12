package net.minecraft.world.gen.chunk;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.Generator;
import net.minecraft.world.gen.feature.DungeonFeature;
import net.minecraft.world.gen.feature.LakeFeature;
import net.minecraft.world.gen.structure.MineshaftStructure;
import net.minecraft.world.gen.structure.OceanMonumentStructure;
import net.minecraft.world.gen.structure.StrongholdStructure;
import net.minecraft.world.gen.structure.StructureFeature;
import net.minecraft.world.gen.structure.TempleStructure;
import net.minecraft.world.gen.structure.VillageStructure;

public class FlatChunkGenerator implements ChunkSource {
    private World world;
    private Random random;
    private final BlockState[] blocks = new BlockState[256];
    private final FlatWorldGeneratorSettings settings;
    private final List<StructureFeature> structures = Lists.newArrayList();
    private final boolean hasDecoration;
    private final boolean hasDungeons;
    private LakeFeature waterLake;
    private LakeFeature lavaLake;

    public FlatChunkGenerator(World world, long seed, boolean structures, String preset) {
        this.world = world;
        this.random = new Random(seed);
        this.settings = FlatWorldGeneratorSettings.of(preset);
        if (structures) {
            Map<String, Map<String, String>> map = this.settings.getFeatures();
            if (map.containsKey("village")) {
                Map<String, String> map1 = map.get("village");
                if (!map1.containsKey("size")) {
                    map1.put("size", "1");
                }

                this.structures.add(new VillageStructure(map1));
            }

            if (map.containsKey("biome_1")) {
                this.structures.add(new TempleStructure(map.get("biome_1")));
            }

            if (map.containsKey("mineshaft")) {
                this.structures.add(new MineshaftStructure(map.get("mineshaft")));
            }

            if (map.containsKey("stronghold")) {
                this.structures.add(new StrongholdStructure(map.get("stronghold")));
            }

            if (map.containsKey("oceanmonument")) {
                this.structures.add(new OceanMonumentStructure(map.get("oceanmonument")));
            }
        }

        if (this.settings.getFeatures().containsKey("lake")) {
            this.waterLake = new LakeFeature(Blocks.WATER);
        }

        if (this.settings.getFeatures().containsKey("lava_lake")) {
            this.lavaLake = new LakeFeature(Blocks.LAVA);
        }

        this.hasDungeons = this.settings.getFeatures().containsKey("dungeon");
        int j = 0;
        int k = 0;
        boolean flag = true;

        for (FlatWorldLayer flatworldlayer : this.settings.getLayers()) {
            for (int i = flatworldlayer.getY(); i < flatworldlayer.getY() + flatworldlayer.getSize(); i++) {
                BlockState blockstate = flatworldlayer.getBlockState();
                if (blockstate.getBlock() != Blocks.AIR) {
                    flag = false;
                    this.blocks[i] = blockstate;
                }
            }

            if (flatworldlayer.getBlockState().getBlock() == Blocks.AIR) {
                k += flatworldlayer.getSize();
            } else {
                j += flatworldlayer.getSize() + k;
                k = 0;
            }
        }

        world.setSeaLevel(j);
        this.hasDecoration = !flag && this.settings.getFeatures().containsKey("decoration");
    }

    @Override
    public WorldChunk getChunk(int chunkX, int chunkZ) {
        BlockStateStorage blockstatestorage = new BlockStateStorage();

        for (int i = 0; i < this.blocks.length; i++) {
            BlockState blockstate = this.blocks[i];
            if (blockstate != null) {
                for (int j = 0; j < 16; j++) {
                    for (int k = 0; k < 16; k++) {
                        blockstatestorage.set(j, i, k, blockstate);
                    }
                }
            }
        }

        for (Generator generator : this.structures) {
            generator.place(this, this.world, chunkX, chunkZ, blockstatestorage);
        }

        WorldChunk worldchunk = new WorldChunk(this.world, blockstatestorage, chunkX, chunkZ);
        Biome[] abiome = this.world.getBiomeSource().getBiomes(null, chunkX * 16, chunkZ * 16, 16, 16);
        byte[] abyte = worldchunk.getBiomes();

        for (int l = 0; l < abyte.length; l++) {
            abyte[l] = (byte)abiome[l].id;
        }

        worldchunk.populateHeightMap();
        return worldchunk;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public void populateChunk(ChunkSource source, int chunkX, int chunkZ) {
        int i = chunkX * 16;
        int j = chunkZ * 16;
        BlockPos blockpos = new BlockPos(i, 0, j);
        Biome biome = this.world.getBiome(new BlockPos(i + 16, 0, j + 16));
        boolean flag = false;
        this.random.setSeed(this.world.getSeed());
        long k = this.random.nextLong() / 2L * 2L + 1L;
        long l = this.random.nextLong() / 2L * 2L + 1L;
        this.random.setSeed(chunkX * k + chunkZ * l ^ this.world.getSeed());
        ChunkPos chunkpos = new ChunkPos(chunkX, chunkZ);

        for (StructureFeature structurefeature : this.structures) {
            boolean flag1 = structurefeature.place(this.world, this.random, chunkpos);
            if (structurefeature instanceof VillageStructure) {
                flag |= flag1;
            }
        }

        if (this.waterLake != null && !flag && this.random.nextInt(4) == 0) {
            this.waterLake.place(this.world, this.random, blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(256), this.random.nextInt(16) + 8));
        }

        if (this.lavaLake != null && !flag && this.random.nextInt(8) == 0) {
            BlockPos blockpos1 = blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(this.random.nextInt(248) + 8), this.random.nextInt(16) + 8);
            if (blockpos1.getY() < this.world.getSeaLevel() || this.random.nextInt(10) == 0) {
                this.lavaLake.place(this.world, this.random, blockpos1);
            }
        }

        if (this.hasDungeons) {
            for (int i1 = 0; i1 < 8; i1++) {
                new DungeonFeature()
                    .place(this.world, this.random, blockpos.add(this.random.nextInt(16) + 8, this.random.nextInt(256), this.random.nextInt(16) + 8));
            }
        }

        if (this.hasDecoration) {
            biome.decorate(this.world, this.random, blockpos);
        }
    }

    @Override
    public boolean populateChunkAfterTerrain(ChunkSource source, WorldChunk chunk, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean save(boolean saveEntities, ProgressListener listener) {
        return true;
    }

    @Override
    public void flush() {
    }

    @Override
    public boolean tick() {
        return false;
    }

    @Override
    public boolean shouldSave() {
        return true;
    }

    @Override
    public String getDebugInfo() {
        return "FlatLevelSource";
    }

    @Override
    public List<Biome.SpawnEntry> getSpawnEntries(MobCategory category, BlockPos pos) {
        Biome biome = this.world.getBiome(pos);
        return biome.getSpawnEntries(category);
    }

    @Override
    public BlockPos findNearestStructure(World world, String type, BlockPos pos) {
        if ("Stronghold".equals(type)) {
            for (StructureFeature structurefeature : this.structures) {
                if (structurefeature instanceof StrongholdStructure) {
                    return structurefeature.findNearestPosition(world, pos);
                }
            }
        }

        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void placeStructures(WorldChunk chunk, int chunkX, int chunkZ) {
        for (StructureFeature structurefeature : this.structures) {
            structurefeature.place(this, this.world, chunkX, chunkZ, null);
        }
    }

    @Override
    public WorldChunk getChunk(BlockPos pos) {
        return this.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
