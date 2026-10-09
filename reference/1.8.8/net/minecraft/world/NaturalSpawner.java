package net.minecraft.world;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.SpawnPlacement;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WeightedPicker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;

public final class NaturalSpawner {
    private static final int MOB_CAPACITY_CHUNK_AREA = (int)Math.pow(17.0, 2.0);
    /**
     * This set keeps track of chunks that allow mob spawning. Mob spawning can occur within
     * a 15x15 grid of chunks around players. The ring of chunks around that do not allow
     * mob spawning, but do contribute to the mob cap.
     */
    private final Set<ChunkPos> mobSpawningChunks = Sets.newHashSet();

    public int tick(ServerWorld world, boolean spawnAnimals, boolean spawnMonsters, boolean spawnRareMobs) {
        if (!spawnAnimals && !spawnMonsters) {
            return 0;
        }

        this.mobSpawningChunks.clear();
        int i = 0;

        for (PlayerEntity playerentity : world.players) {
            if (!playerentity.isSpectator()) {
                int j = MathHelper.floor(playerentity.x / 16.0);
                int k = MathHelper.floor(playerentity.z / 16.0);
                int l = 8;

                for (int i1 = -l; i1 <= l; i1++) {
                    for (int j1 = -l; j1 <= l; j1++) {
                        boolean flag = i1 == -l || i1 == l || j1 == -l || j1 == l;
                        ChunkPos chunkpos = new ChunkPos(i1 + j, j1 + k);
                        if (!this.mobSpawningChunks.contains(chunkpos)) {
                            i++;
                            if (!flag && world.getWorldBorder().contains(chunkpos)) {
                                this.mobSpawningChunks.add(chunkpos);
                            }
                        }
                    }
                }
            }
        }

        int i4 = 0;
        BlockPos blockpos2 = world.getSpawnPoint();

        for (MobCategory mobcategory : MobCategory.values()) {
            if ((!mobcategory.isPeaceful() || spawnMonsters) && (mobcategory.isPeaceful() || spawnAnimals) && (!mobcategory.isRare() || spawnRareMobs)) {
                int j4 = world.getEntityCount(mobcategory.getType());
                int k4 = mobcategory.getCap() * i / MOB_CAPACITY_CHUNK_AREA;
                if (j4 <= k4) {
                    label129:
                    for (ChunkPos chunkpos1 : this.mobSpawningChunks) {
                        BlockPos blockpos = getRandomPosInChunk(world, chunkpos1.x, chunkpos1.z);
                        int k1 = blockpos.getX();
                        int l1 = blockpos.getY();
                        int i2 = blockpos.getZ();
                        Block block = world.getBlockState(blockpos).getBlock();
                        if (!block.isSolid()) {
                            int j2 = 0;

                            for (int k2 = 0; k2 < 3; k2++) {
                                int l2 = k1;
                                int i3 = l1;
                                int j3 = i2;
                                int k3 = 6;
                                Biome.SpawnEntry biome$spawnentry = null;
                                EntityData entitydata = null;

                                for (int l3 = 0; l3 < 4; l3++) {
                                    l2 += world.random.nextInt(k3) - world.random.nextInt(k3);
                                    i3 += world.random.nextInt(1) - world.random.nextInt(1);
                                    j3 += world.random.nextInt(k3) - world.random.nextInt(k3);
                                    BlockPos blockpos1 = new BlockPos(l2, i3, j3);
                                    float f = l2 + 0.5F;
                                    float f1 = j3 + 0.5F;
                                    if (!world.isPlayerWithinRange(f, i3, f1, 24.0) && !(blockpos2.squaredDistanceTo(f, i3, f1) < 576.0)) {
                                        if (biome$spawnentry == null) {
                                            biome$spawnentry = world.pickSpawnEntry(mobcategory, blockpos1);
                                            if (biome$spawnentry == null) {
                                                break;
                                            }
                                        }

                                        if (world.isValidSpawnEntry(mobcategory, biome$spawnentry, blockpos1)
                                            && isValidSpawnPos(SpawnPlacement.getEnvironment(biome$spawnentry.type), world, blockpos1)) {
                                            MobEntity mobentity;
                                            try {
                                                mobentity = biome$spawnentry.type.getConstructor(World.class).newInstance(world);
                                            } catch (Exception exception) {
                                                exception.printStackTrace();
                                                return i4;
                                            }

                                            mobentity.setPositionAndAngles(f, i3, f1, world.random.nextFloat() * 360.0F, 0.0F);
                                            if (mobentity.canSpawn() && mobentity.isUnobstructed()) {
                                                entitydata = mobentity.initialize(world.getLocalDifficulty(new BlockPos(mobentity)), entitydata);
                                                if (mobentity.isUnobstructed()) {
                                                    j2++;
                                                    world.addEntity(mobentity);
                                                }

                                                if (j2 >= mobentity.getLimitPerChunk()) {
                                                    continue label129;
                                                }
                                            }

                                            i4 += j2;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return i4;
    }

    protected static BlockPos getRandomPosInChunk(World world, int chunkX, int chunkZ) {
        WorldChunk worldchunk = world.getChunkAt(chunkX, chunkZ);
        int i = chunkX * 16 + world.random.nextInt(16);
        int j = chunkZ * 16 + world.random.nextInt(16);
        int k = MathHelper.roundUp(worldchunk.getHeight(new BlockPos(i, 0, j)) + 1, 16);
        int l = world.random.nextInt(k > 0 ? k : worldchunk.getHighestSectionOffset() + 16 - 1);
        return new BlockPos(i, l, j);
    }

    public static boolean isValidSpawnPos(MobEntity.SpawnEnvironment environment, World world, BlockPos pos) {
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }

        Block block = world.getBlockState(pos).getBlock();
        if (environment == MobEntity.SpawnEnvironment.IN_WATER) {
            return block.getMaterial().isLiquid()
                && world.getBlockState(pos.down()).getBlock().getMaterial().isLiquid()
                && !world.getBlockState(pos.up()).getBlock().isSolid();
        }

        BlockPos blockpos = pos.down();
        if (!World.hasSolidTop(world, blockpos)) {
            return false;
        }

        Block block1 = world.getBlockState(blockpos).getBlock();
        boolean flag = block1 != Blocks.BEDROCK && block1 != Blocks.BARRIER;
        return flag && !block.isSolid() && !block.getMaterial().isLiquid() && !world.getBlockState(pos.up()).getBlock().isSolid();
    }

    public static void populateChunk(World world, Biome biome, int chunkCenterX, int chunkCenterZ, int rangeX, int rangeZ, Random random) {
        List<Biome.SpawnEntry> list = biome.getSpawnEntries(MobCategory.CREATURE);
        if (!list.isEmpty()) {
            while (random.nextFloat() < biome.getAnimalSpawnChance()) {
                Biome.SpawnEntry biome$spawnentry = WeightedPicker.pick(world.random, list);
                int i = biome$spawnentry.minGroupSize + random.nextInt(1 + biome$spawnentry.maxGroupSize - biome$spawnentry.minGroupSize);
                EntityData entitydata = null;
                int j = chunkCenterX + random.nextInt(rangeX);
                int k = chunkCenterZ + random.nextInt(rangeZ);
                int l = j;
                int i1 = k;

                for (int j1 = 0; j1 < i; j1++) {
                    boolean flag = false;

                    for (int k1 = 0; !flag && k1 < 4; k1++) {
                        BlockPos blockpos = world.getSurfaceHeight(new BlockPos(j, 0, k));
                        if (isValidSpawnPos(MobEntity.SpawnEnvironment.ON_GROUND, world, blockpos)) {
                            MobEntity mobentity;
                            try {
                                mobentity = biome$spawnentry.type.getConstructor(World.class).newInstance(world);
                            } catch (Exception exception) {
                                exception.printStackTrace();
                                continue;
                            }

                            mobentity.setPositionAndAngles(j + 0.5F, blockpos.getY(), k + 0.5F, random.nextFloat() * 360.0F, 0.0F);
                            world.addEntity(mobentity);
                            entitydata = mobentity.initialize(world.getLocalDifficulty(new BlockPos(mobentity)), entitydata);
                            flag = true;
                        }

                        j += random.nextInt(5) - random.nextInt(5);

                        for (k += random.nextInt(5) - random.nextInt(5);
                            j < chunkCenterX || j >= chunkCenterX + rangeX || k < chunkCenterZ || k >= chunkCenterZ + rangeX;
                            k = i1 + random.nextInt(5) - random.nextInt(5)
                        ) {
                            j = l + random.nextInt(5) - random.nextInt(5);
                        }
                    }
                }
            }
        }
    }
}
