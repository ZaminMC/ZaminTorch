package net.minecraft.world.gen.structure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class VillageStructure extends StructureFeature {
    public static final List<Biome> VALID_BIOMES = Arrays.asList(Biome.PLAINS, Biome.DESERT, Biome.SAVANNA);
    private int size;
    private int distance = 32;
    private int minDistance = 8;

    public VillageStructure() {
    }

    public VillageStructure(Map<String, String> options) {
        this();

        for (Entry<String, String> entry : options.entrySet()) {
            if (entry.getKey().equals("size")) {
                this.size = MathHelper.parseInt(entry.getValue(), this.size, 0);
            } else if (entry.getKey().equals("distance")) {
                this.distance = MathHelper.parseInt(entry.getValue(), this.distance, this.minDistance + 1);
            }
        }
    }

    @Override
    public String getName() {
        return "Village";
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
        Random random = this.world.setRandomSeed(k, l, 10387312);
        k *= this.distance;
        l *= this.distance;
        k += random.nextInt(this.distance - this.minDistance);
        l += random.nextInt(this.distance - this.minDistance);
        chunkX = i;
        chunkZ = j;
        if (chunkX == k && chunkZ == l) {
            boolean flag = this.world.getBiomeSource().isBiomeWithin(chunkX * 16 + 8, chunkZ * 16 + 8, 0, VALID_BIOMES);
            if (flag) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected StructureStart createStructure(int x, int z) {
        return new VillageStructure.Start(this.world, this.random, x, z, this.size);
    }

    public static class Start extends StructureStart {
        private boolean valid;

        public Start() {
        }

        public Start(World world, Random random, int chunkX, int chunkZ, int size) {
            super(chunkX, chunkZ);
            List<VillagePieces.VillagePieceWeight> list = VillagePieces.getPieceWeights(random, size);
            VillagePieces.Start villagepieces$start = new VillagePieces.Start(
                world.getBiomeSource(), 0, random, (chunkX << 4) + 2, (chunkZ << 4) + 2, list, size
            );
            this.pieces.add(villagepieces$start);
            villagepieces$start.addChildren(villagepieces$start, this.pieces, random);
            List<StructurePiece> list1 = villagepieces$start.roadPieces;
            List<StructurePiece> list2 = villagepieces$start.buildingPieces;

            while (!list1.isEmpty() || !list2.isEmpty()) {
                if (list1.isEmpty()) {
                    int i = random.nextInt(list2.size());
                    StructurePiece structurepiece = list2.remove(i);
                    structurepiece.addChildren(villagepieces$start, this.pieces, random);
                } else {
                    int j = random.nextInt(list1.size());
                    StructurePiece structurepiece2 = list1.remove(j);
                    structurepiece2.addChildren(villagepieces$start, this.pieces, random);
                }
            }

            this.findBounds();
            int k = 0;

            for (StructurePiece structurepiece1 : this.pieces) {
                if (!(structurepiece1 instanceof VillagePieces.RoadPiece)) {
                    k++;
                }
            }

            this.valid = k > 2;
        }

        @Override
        public boolean isValid() {
            return this.valid;
        }

        @Override
        public void writeValidityNbt(NbtCompound nbt) {
            super.writeValidityNbt(nbt);
            nbt.putBoolean("Valid", this.valid);
        }

        @Override
        public void readValidityNbt(NbtCompound nbt) {
            super.readValidityNbt(nbt);
            this.valid = nbt.getBoolean("Valid");
        }
    }
}
