package net.minecraft.world.gen.feature;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.LootEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DungeonFeature extends Feature {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String[] TYPES = new String[]{"Skeleton", "Zombie", "Zombie", "Spider"};
    private static final List<LootEntry> LOOT_ENTRIES = Lists.newArrayList(
        new LootEntry(Items.SADDLE, 0, 1, 1, 10),
        new LootEntry(Items.IRON_INGOT, 0, 1, 4, 10),
        new LootEntry(Items.BREAD, 0, 1, 1, 10),
        new LootEntry(Items.WHEAT, 0, 1, 4, 10),
        new LootEntry(Items.GUNPOWDER, 0, 1, 4, 10),
        new LootEntry(Items.STRING, 0, 1, 4, 10),
        new LootEntry(Items.BUCKET, 0, 1, 1, 10),
        new LootEntry(Items.GOLDEN_APPLE, 0, 1, 1, 1),
        new LootEntry(Items.REDSTONE, 0, 1, 4, 10),
        new LootEntry(Items.RECORD_13, 0, 1, 1, 4),
        new LootEntry(Items.RECORD_CAT, 0, 1, 1, 4),
        new LootEntry(Items.NAME_TAG, 0, 1, 1, 10),
        new LootEntry(Items.GOLDEN_HORSE_ARMOR, 0, 1, 1, 2),
        new LootEntry(Items.IRON_HORSE_ARMOR, 0, 1, 1, 5),
        new LootEntry(Items.DIAMOND_HORSE_ARMOR, 0, 1, 1, 1)
    );

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        int i = 3;
        int j = random.nextInt(2) + 2;
        int k = -j - 1;
        int l = j + 1;
        int i1 = -1;
        int j1 = 4;
        int k1 = random.nextInt(2) + 2;
        int l1 = -k1 - 1;
        int i2 = k1 + 1;
        int j2 = 0;

        for (int k2 = k; k2 <= l; k2++) {
            for (int l2 = -1; l2 <= 4; l2++) {
                for (int i3 = l1; i3 <= i2; i3++) {
                    BlockPos blockpos = pos.add(k2, l2, i3);
                    Material material = world.getBlockState(blockpos).getBlock().getMaterial();
                    boolean flag = material.isSolid();
                    if (l2 == -1 && !flag) {
                        return false;
                    }

                    if (l2 == 4 && !flag) {
                        return false;
                    }

                    if ((k2 == k || k2 == l || i3 == l1 || i3 == i2) && l2 == 0 && world.isAir(blockpos) && world.isAir(blockpos.up())) {
                        j2++;
                    }
                }
            }
        }

        if (j2 >= 1 && j2 <= 5) {
            for (int k3 = k; k3 <= l; k3++) {
                for (int i4 = 3; i4 >= -1; i4--) {
                    for (int k4 = l1; k4 <= i2; k4++) {
                        BlockPos blockpos1 = pos.add(k3, i4, k4);
                        if (k3 != k && i4 != -1 && k4 != l1 && k3 != l && i4 != 4 && k4 != i2) {
                            if (world.getBlockState(blockpos1).getBlock() != Blocks.CHEST) {
                                world.removeBlock(blockpos1);
                            }
                        } else if (blockpos1.getY() >= 0 && !world.getBlockState(blockpos1.down()).getBlock().getMaterial().isSolid()) {
                            world.removeBlock(blockpos1);
                        } else if (world.getBlockState(blockpos1).getBlock().getMaterial().isSolid()
                            && world.getBlockState(blockpos1).getBlock() != Blocks.CHEST) {
                            if (i4 == -1 && random.nextInt(4) != 0) {
                                world.setBlockState(blockpos1, Blocks.MOSSY_COBBLESTONE.defaultState(), 2);
                            } else {
                                world.setBlockState(blockpos1, Blocks.COBBLESTONE.defaultState(), 2);
                            }
                        }
                    }
                }
            }

            for (int l3 = 0; l3 < 2; l3++) {
                for (int j4 = 0; j4 < 3; j4++) {
                    int l4 = pos.getX() + random.nextInt(j * 2 + 1) - j;
                    int i5 = pos.getY();
                    int j5 = pos.getZ() + random.nextInt(k1 * 2 + 1) - k1;
                    BlockPos blockpos2 = new BlockPos(l4, i5, j5);
                    if (world.isAir(blockpos2)) {
                        int j3 = 0;

                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            if (world.getBlockState(blockpos2.offset(direction)).getBlock().getMaterial().isSolid()) {
                                j3++;
                            }
                        }

                        if (j3 == 1) {
                            world.setBlockState(blockpos2, Blocks.CHEST.updateFacing(world, blockpos2, Blocks.CHEST.defaultState()), 2);
                            List<LootEntry> list = LootEntry.addAll(LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random));
                            BlockEntity blockentity1 = world.getBlockEntity(blockpos2);
                            if (blockentity1 instanceof ChestBlockEntity) {
                                LootEntry.addLoot(random, list, (ChestBlockEntity)blockentity1, 8);
                            }
                            break;
                        }
                    }
                }
            }

            world.setBlockState(pos, Blocks.MOB_SPAWNER.defaultState(), 2);
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof MobSpawnerBlockEntity) {
                ((MobSpawnerBlockEntity)blockentity).getSpawner().setType(this.pickDungeonType(random));
            } else {
                LOGGER.error("Failed to fetch mob spawner entity at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
            }

            return true;
        } else {
            return false;
        }
    }

    private String pickDungeonType(Random random) {
        return TYPES[random.nextInt(TYPES.length)];
    }
}
