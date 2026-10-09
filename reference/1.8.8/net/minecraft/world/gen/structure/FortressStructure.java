package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.living.mob.monster.MagmaCubeEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.ZombiePigmanEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class FortressStructure extends StructureFeature {
    private List<Biome.SpawnEntry> monsterSpawnEntries = Lists.newArrayList();

    public FortressStructure() {
        this.monsterSpawnEntries.add(new Biome.SpawnEntry(BlazeEntity.class, 10, 2, 3));
        this.monsterSpawnEntries.add(new Biome.SpawnEntry(ZombiePigmanEntity.class, 5, 4, 4));
        this.monsterSpawnEntries.add(new Biome.SpawnEntry(SkeletonEntity.class, 10, 4, 4));
        this.monsterSpawnEntries.add(new Biome.SpawnEntry(MagmaCubeEntity.class, 3, 4, 4));
    }

    @Override
    public String getName() {
        return "Fortress";
    }

    public List<Biome.SpawnEntry> getMonsterSpawnEntries() {
        return this.monsterSpawnEntries;
    }

    @Override
    protected boolean isFeatureChunk(int chunkX, int chunkZ) {
        int i = chunkX >> 4;
        int j = chunkZ >> 4;
        this.random.setSeed(i ^ j << 4 ^ this.world.getSeed());
        this.random.nextInt();
        return this.random.nextInt(3) == 0 && chunkX == (i << 4) + 4 + this.random.nextInt(8) && chunkZ == (j << 4) + 4 + this.random.nextInt(8);
    }

    @Override
    protected StructureStart createStructure(int x, int z) {
        return new FortressStructure.Start(this.world, this.random, x, z);
    }

    public static class Start extends StructureStart {
        public Start() {
        }

        public Start(World world, Random random, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            FortressPieces.Start fortresspieces$start = new FortressPieces.Start(random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
            this.pieces.add(fortresspieces$start);
            fortresspieces$start.addChildren(fortresspieces$start, this.pieces, random);
            List<StructurePiece> list = fortresspieces$start.children;

            while (!list.isEmpty()) {
                int i = random.nextInt(list.size());
                StructurePiece structurepiece = list.remove(i);
                structurepiece.addChildren(fortresspieces$start, this.pieces, random);
            }

            this.findBounds();
            this.moveBetweenYCoords(world, random, 48, 70);
        }
    }
}
