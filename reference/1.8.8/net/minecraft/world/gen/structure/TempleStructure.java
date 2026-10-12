package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class TempleStructure extends StructureFeature {
    private static final List<Biome> VALID_BIOMES = Arrays.asList(Biome.DESERT, Biome.DESERT_HILLS, Biome.JUNGLE, Biome.JUNGLE_HILLS, Biome.SWAMPLAND);
    private List<Biome.SpawnEntry> spawnEntries = Lists.newArrayList();
    private int distance = 32;
    private int minDistance = 8;

    public TempleStructure() {
        this.spawnEntries.add(new Biome.SpawnEntry(WitchEntity.class, 1, 1, 1));
    }

    public TempleStructure(Map<String, String> options) {
        this();

        for (Entry<String, String> entry : options.entrySet()) {
            if (entry.getKey().equals("distance")) {
                this.distance = MathHelper.parseInt(entry.getValue(), this.distance, this.minDistance + 1);
            }
        }
    }

    @Override
    public String getName() {
        return "Temple";
    }

    @Override
    protected boolean isFeatureChunk(int chunkX, int chunkZ) {
        int i = chunkX;
        int j = chunkZ;
        if (chunkX < 0) {
            chunkX -= this.distance - 1;
        }

        if (chunkZ < 0) {
            chunkZ -= this.distance - 1;
        }

        int k = chunkX / this.distance;
        int l = chunkZ / this.distance;
        Random random = this.world.setRandomSeed(k, l, 14357617);
        k *= this.distance;
        l *= this.distance;
        k += random.nextInt(this.distance - this.minDistance);
        l += random.nextInt(this.distance - this.minDistance);
        chunkX = i;
        chunkZ = j;
        if (chunkX == k && chunkZ == l) {
            Biome biome = this.world.getBiomeSource().getBiome(new BlockPos(chunkX * 16 + 8, 0, chunkZ * 16 + 8));
            if (biome == null) {
                return false;
            }

            for (Biome biome1 : VALID_BIOMES) {
                if (biome == biome1) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected StructureStart createStructure(int x, int z) {
        return new TempleStructure.Start(this.world, this.random, x, z);
    }

    public boolean isWitchHut(BlockPos pos) {
        StructureStart structurestart = this.findStructure(pos);
        if (structurestart != null && structurestart instanceof TempleStructure.Start && !structurestart.pieces.isEmpty()) {
            StructurePiece structurepiece = structurestart.pieces.getFirst();
            return structurepiece instanceof TemplePieces.WitchHut;
        } else {
            return false;
        }
    }

    public List<Biome.SpawnEntry> getSpawnEntries() {
        return this.spawnEntries;
    }

    public static class Start extends StructureStart {
        public Start() {
        }

        public Start(World world, Random random, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            Biome biome = world.getBiome(new BlockPos(chunkX * 16 + 8, 0, chunkZ * 16 + 8));
            if (biome == Biome.JUNGLE || biome == Biome.JUNGLE_HILLS) {
                TemplePieces.JungleTemple templepieces$jungletemple = new TemplePieces.JungleTemple(random, chunkX * 16, chunkZ * 16);
                this.pieces.add(templepieces$jungletemple);
            } else if (biome == Biome.SWAMPLAND) {
                TemplePieces.WitchHut templepieces$witchhut = new TemplePieces.WitchHut(random, chunkX * 16, chunkZ * 16);
                this.pieces.add(templepieces$witchhut);
            } else if (biome == Biome.DESERT || biome == Biome.DESERT_HILLS) {
                TemplePieces.DesertPyramid templepieces$desertpyramid = new TemplePieces.DesertPyramid(random, chunkX * 16, chunkZ * 16);
                this.pieces.add(templepieces$desertpyramid);
            }

            this.findBounds();
        }
    }
}
