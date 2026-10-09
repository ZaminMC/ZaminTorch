package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class OceanMonumentStructure extends StructureFeature {
    private int spacing = 32;
    private int separation = 5;
    public static final List<Biome> VALID_BIOMES = Arrays.asList(Biome.OCEAN, Biome.DEEP_OCEAN, Biome.RIVER, Biome.FROZEN_OCEAN, Biome.FROZEN_RIVER);
    private static final List<Biome.SpawnEntry> SPAWN_ENTRIES = Lists.newArrayList();

    public OceanMonumentStructure() {
    }

    public OceanMonumentStructure(Map<String, String> options) {
        this();

        for (Entry<String, String> entry : options.entrySet()) {
            if (entry.getKey().equals("spacing")) {
                this.spacing = MathHelper.parseInt(entry.getValue(), this.spacing, 1);
            } else if (entry.getKey().equals("separation")) {
                this.separation = MathHelper.parseInt(entry.getValue(), this.separation, 1);
            }
        }
    }

    @Override
    public String getName() {
        return "Monument";
    }

    @Override
    protected boolean isFeatureChunk(int chunkX, int chunkZ) {
        int i = chunkX;
        int j = chunkZ;
        if (chunkX < 0) {
            chunkX -= this.spacing - 1;
        }

        if (chunkZ < 0) {
            chunkZ -= this.spacing - 1;
        }

        int k = chunkX / this.spacing;
        int l = chunkZ / this.spacing;
        Random random = this.world.setRandomSeed(k, l, 10387313);
        k *= this.spacing;
        l *= this.spacing;
        k += (random.nextInt(this.spacing - this.separation) + random.nextInt(this.spacing - this.separation)) / 2;
        l += (random.nextInt(this.spacing - this.separation) + random.nextInt(this.spacing - this.separation)) / 2;
        chunkX = i;
        chunkZ = j;
        if (chunkX == k && chunkZ == l) {
            if (this.world.getBiomeSource().getBiome(new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8), null) != Biome.DEEP_OCEAN) {
                return false;
            }

            boolean flag = this.world.getBiomeSource().isBiomeWithin(chunkX * 16 + 8, chunkZ * 16 + 8, 29, VALID_BIOMES);
            if (flag) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected StructureStart createStructure(int x, int z) {
        return new OceanMonumentStructure.Start(this.world, this.random, x, z);
    }

    public List<Biome.SpawnEntry> getSpawnEntries() {
        return SPAWN_ENTRIES;
    }

    static {
        SPAWN_ENTRIES.add(new Biome.SpawnEntry(GuardianEntity.class, 1, 2, 4));
    }

    public static class Start extends StructureStart {
        private Set<ChunkPos> placedMonuments = Sets.newHashSet();
        private boolean initialized;

        public Start() {
        }

        public Start(World world, Random random, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            this.init(world, random, chunkX, chunkZ);
        }

        private void init(World world, Random random, int chunkX, int chunkZ) {
            random.setSeed(world.getSeed());
            long i = random.nextLong();
            long j = random.nextLong();
            long k = chunkX * i;
            long l = chunkZ * j;
            random.setSeed(k ^ l ^ world.getSeed());
            int i1 = chunkX * 16 + 8 - 29;
            int j1 = chunkZ * 16 + 8 - 29;
            Direction direction = Direction.Plane.HORIZONTAL.pick(random);
            this.pieces.add(new OceanMonumentPieces.OceanMonument(random, i1, j1, direction));
            this.findBounds();
            this.initialized = true;
        }

        @Override
        public void postProcess(World world, Random random, StructureBox chunkBounds) {
            if (!this.initialized) {
                this.pieces.clear();
                this.init(world, random, this.getChunkX(), this.getChunkZ());
            }

            super.postProcess(world, random, chunkBounds);
        }

        @Override
        public boolean isValid(ChunkPos pos) {
            return !this.placedMonuments.contains(pos) && super.isValid(pos);
        }

        @Override
        public void postPlacement(ChunkPos pos) {
            super.postPlacement(pos);
            this.placedMonuments.add(pos);
        }

        @Override
        public void writeValidityNbt(NbtCompound nbt) {
            super.writeValidityNbt(nbt);
            NbtList nbtlist = new NbtList();

            for (ChunkPos chunkpos : this.placedMonuments) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putInt("X", chunkpos.x);
                nbtcompound.putInt("Z", chunkpos.z);
                nbtlist.addElement(nbtcompound);
            }

            nbt.put("Processed", nbtlist);
        }

        @Override
        public void readValidityNbt(NbtCompound nbt) {
            super.readValidityNbt(nbt);
            if (nbt.contains("Processed", 9)) {
                NbtList nbtlist = nbt.getList("Processed", 10);

                for (int i = 0; i < nbtlist.size(); i++) {
                    NbtCompound nbtcompound = nbtlist.getCompound(i);
                    this.placedMonuments.add(new ChunkPos(nbtcompound.getInt("X"), nbtcompound.getInt("Z")));
                }
            }
        }
    }
}
