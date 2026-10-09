package net.minecraft.world.gen.structure;

import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.Generator;
import net.minecraft.world.gen.feature.SavedFeatureData;

public abstract class StructureFeature extends Generator {
    private SavedFeatureData savedData;
    protected Map<Long, StructureStart> structures = Maps.newHashMap();

    public abstract String getName();

    @Override
    protected final void place(World world, int startChunkX, int startChunkZ, int chunkX, int chunkZ, BlockStateStorage blocks) {
        this.loadSavedData(world);
        if (!this.structures.containsKey(ChunkPos.toLong(startChunkX, startChunkZ))) {
            this.random.nextInt();

            try {
                if (this.isFeatureChunk(startChunkX, startChunkZ)) {
                    StructureStart structurestart = this.createStructure(startChunkX, startChunkZ);
                    this.structures.put(ChunkPos.toLong(startChunkX, startChunkZ), structurestart);
                    this.saveStructure(startChunkX, startChunkZ, structurestart);
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Exception preparing structure feature");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Feature being prepared");
                crashreportcategory.add("Is feature chunk", new Callable<String>() {
                    public String call() throws Exception {
                        return StructureFeature.this.isFeatureChunk(startChunkX, startChunkZ) ? "True" : "False";
                    }
                });
                crashreportcategory.add("Chunk location", String.format("%d,%d", startChunkX, startChunkZ));
                crashreportcategory.add("Chunk pos hash", new Callable<String>() {
                    public String call() throws Exception {
                        return String.valueOf(ChunkPos.toLong(startChunkX, startChunkZ));
                    }
                });
                crashreportcategory.add("Structure type", new Callable<String>() {
                    public String call() throws Exception {
                        return StructureFeature.this.getClass().getCanonicalName();
                    }
                });
                throw new CrashException(crashreport);
            }
        }
    }

    public boolean place(World world, Random random, ChunkPos pos) {
        this.loadSavedData(world);
        int i = (pos.x << 4) + 8;
        int j = (pos.z << 4) + 8;
        boolean flag = false;

        for (StructureStart structurestart : this.structures.values()) {
            if (structurestart.isValid() && structurestart.isValid(pos) && structurestart.getBounds().intersects(i, j, i + 15, j + 15)) {
                structurestart.postProcess(world, random, new StructureBox(i, j, i + 15, j + 15));
                structurestart.postPlacement(pos);
                flag = true;
                this.saveStructure(structurestart.getChunkX(), structurestart.getChunkZ(), structurestart);
            }
        }

        return flag;
    }

    public boolean isInside(BlockPos pos) {
        this.loadSavedData(this.world);
        return this.findStructure(pos) != null;
    }

    protected StructureStart findStructure(BlockPos pos) {
        for (StructureStart structurestart : this.structures.values()) {
            if (structurestart.isValid() && structurestart.getBounds().contains(pos)) {
                for (StructurePiece structurepiece : structurestart.getPieces()) {
                    if (structurepiece.getBounds().contains(pos)) {
                        return structurestart;
                    }
                }
            }
        }

        return null;
    }

    public boolean isInsideBounds(World world, BlockPos pos) {
        this.loadSavedData(world);

        for (StructureStart structurestart : this.structures.values()) {
            if (structurestart.isValid() && structurestart.getBounds().contains(pos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Find the nearest position where this structure has generated.
     */
    public BlockPos findNearestPosition(World world, BlockPos pos) {
        this.world = world;
        this.loadSavedData(world);
        this.random.setSeed(world.getSeed());
        long i = this.random.nextLong();
        long j = this.random.nextLong();
        long k = (pos.getX() >> 4) * i;
        long l = (pos.getZ() >> 4) * j;
        this.random.setSeed(k ^ l ^ world.getSeed());
        this.place(world, pos.getX() >> 4, pos.getZ() >> 4, 0, 0, null);
        double d0 = Double.MAX_VALUE;
        BlockPos blockpos = null;

        for (StructureStart structurestart : this.structures.values()) {
            if (structurestart.isValid()) {
                StructurePiece structurepiece = structurestart.getPieces().get(0);
                BlockPos blockpos1 = structurepiece.getCenterPos();
                double d1 = blockpos1.squaredDistanceTo(pos);
                if (d1 < d0) {
                    d0 = d1;
                    blockpos = blockpos1;
                }
            }
        }

        if (blockpos != null) {
            return blockpos;
        }

        List<BlockPos> list = this.getPotentialPositions();
        if (list != null) {
            BlockPos blockpos2 = null;

            for (BlockPos blockpos3 : list) {
                double d2 = blockpos3.squaredDistanceTo(pos);
                if (d2 < d0) {
                    d0 = d2;
                    blockpos2 = blockpos3;
                }
            }

            return blockpos2;
        } else {
            return null;
        }
    }

    /**
     * Returns all positions where this structure may generate.
     */
    protected List<BlockPos> getPotentialPositions() {
        return null;
    }

    private void loadSavedData(World world) {
        if (this.savedData == null) {
            this.savedData = (SavedFeatureData)world.loadSavedData(SavedFeatureData.class, this.getName());
            if (this.savedData == null) {
                this.savedData = new SavedFeatureData(this.getName());
                world.setSavedData(this.getName(), this.savedData);
            } else {
                NbtCompound nbtcompound = this.savedData.getFeatures();

                for (String s : nbtcompound.getKeys()) {
                    NbtElement nbtelement = nbtcompound.get(s);
                    if (nbtelement.getType() == 10) {
                        NbtCompound nbtcompound1 = (NbtCompound)nbtelement;
                        if (nbtcompound1.contains("ChunkX") && nbtcompound1.contains("ChunkZ")) {
                            int i = nbtcompound1.getInt("ChunkX");
                            int j = nbtcompound1.getInt("ChunkZ");
                            StructureStart structurestart = StructureRegistry.getStartFromNbt(nbtcompound1, world);
                            if (structurestart != null) {
                                this.structures.put(ChunkPos.toLong(i, j), structurestart);
                            }
                        }
                    }
                }
            }
        }
    }

    private void saveStructure(int chunkX, int chunkZ, StructureStart structure) {
        this.savedData.put(structure.toNbt(chunkX, chunkZ), chunkX, chunkZ);
        this.savedData.markDirty();
    }

    protected abstract boolean isFeatureChunk(int chunkX, int chunkZ);

    protected abstract StructureStart createStructure(int x, int z);
}
