package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class StrongholdStructure extends StructureFeature {
    private List<Biome> validBiomes;
    private boolean positionsFound;
    private ChunkPos[] positions = new ChunkPos[3];
    private double distance = 32.0;
    private int spread = 3;

    public StrongholdStructure() {
        this.validBiomes = Lists.newArrayList();

        for (Biome biome : Biome.getAll()) {
            if (biome != null && biome.baseHeight > 0.0F) {
                this.validBiomes.add(biome);
            }
        }
    }

    public StrongholdStructure(Map<String, String> options) {
        this();

        for (Entry<String, String> entry : options.entrySet()) {
            if (entry.getKey().equals("distance")) {
                this.distance = MathHelper.parseDouble(entry.getValue(), this.distance, 1.0);
            } else if (entry.getKey().equals("count")) {
                this.positions = new ChunkPos[MathHelper.parseInt(entry.getValue(), this.positions.length, 1)];
            } else if (entry.getKey().equals("spread")) {
                this.spread = MathHelper.parseInt(entry.getValue(), this.spread, 1);
            }
        }
    }

    @Override
    public String getName() {
        return "Stronghold";
    }

    @Override
    protected boolean isFeatureChunk(int chunkX, int chunkZ) {
        if (!this.positionsFound) {
            Random random = new Random();
            random.setSeed(this.world.getSeed());
            double d0 = random.nextDouble() * Math.PI * 2.0;
            int i = 1;

            for (int j = 0; j < this.positions.length; j++) {
                double d1 = (1.25 * i + random.nextDouble()) * (this.distance * i);
                int k = (int)Math.round(Math.cos(d0) * d1);
                int l = (int)Math.round(Math.sin(d0) * d1);
                BlockPos blockpos = this.world.getBiomeSource().findBiome((k << 4) + 8, (l << 4) + 8, 112, this.validBiomes, random);
                if (blockpos != null) {
                    k = blockpos.getX() >> 4;
                    l = blockpos.getZ() >> 4;
                }

                this.positions[j] = new ChunkPos(k, l);
                d0 += (Math.PI * 2) * i / this.spread;
                if (j == this.spread) {
                    i += 2 + random.nextInt(5);
                    this.spread = this.spread + 1 + random.nextInt(2);
                }
            }

            this.positionsFound = true;
        }

        for (ChunkPos chunkpos : this.positions) {
            if (chunkX == chunkpos.x && chunkZ == chunkpos.z) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected List<BlockPos> getPotentialPositions() {
        List<BlockPos> list = Lists.newArrayList();

        for (ChunkPos chunkpos : this.positions) {
            if (chunkpos != null) {
                list.add(chunkpos.getCenterPos(64));
            }
        }

        return list;
    }

    @Override
    protected StructureStart createStructure(int x, int z) {
        StrongholdStructure.Start strongholdstructure$start = new StrongholdStructure.Start(this.world, this.random, x, z);

        while (strongholdstructure$start.getPieces().isEmpty() || ((StrongholdPieces.Start)strongholdstructure$start.getPieces().get(0)).endPortalRoom == null) {
            strongholdstructure$start = new StrongholdStructure.Start(this.world, this.random, x, z);
        }

        return strongholdstructure$start;
    }

    public static class Start extends StructureStart {
        public Start() {
        }

        public Start(World world, Random random, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            StrongholdPieces.resetWeights();
            StrongholdPieces.Start strongholdpieces$start = new StrongholdPieces.Start(0, random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
            this.pieces.add(strongholdpieces$start);
            strongholdpieces$start.addChildren(strongholdpieces$start, this.pieces, random);
            List<StructurePiece> list = strongholdpieces$start.children;

            while (!list.isEmpty()) {
                int i = random.nextInt(list.size());
                StructurePiece structurepiece = list.remove(i);
                structurepiece.addChildren(strongholdpieces$start, this.pieces, random);
            }

            this.findBounds();
            this.moveBelowSeaLevel(world, random, 10);
        }
    }
}
