package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MushroomBlock;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HugeMushroomFeature extends Feature {
    private Block mushroom;

    public HugeMushroomFeature(Block mushroom) {
        super(true);
        this.mushroom = mushroom;
    }

    public HugeMushroomFeature() {
        super(false);
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        if (this.mushroom == null) {
            this.mushroom = random.nextBoolean() ? Blocks.BROWN_MUSHROOM_BLOCK : Blocks.RED_MUSHROOM_BLOCK;
        }

        int i = random.nextInt(3) + 4;
        boolean flag = true;
        if (pos.getY() >= 1 && pos.getY() + i + 1 < 256) {
            for (int j = pos.getY(); j <= pos.getY() + 1 + i; j++) {
                int k = 3;
                if (j <= pos.getY() + 3) {
                    k = 0;
                }

                BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                for (int l = pos.getX() - k; l <= pos.getX() + k && flag; l++) {
                    for (int i1 = pos.getZ() - k; i1 <= pos.getZ() + k && flag; i1++) {
                        if (j >= 0 && j < 256) {
                            Block block = world.getBlockState(blockpos$mutable.set(l, j, i1)).getBlock();
                            if (block.getMaterial() != Material.AIR && block.getMaterial() != Material.LEAVES) {
                                flag = false;
                            }
                        } else {
                            flag = false;
                        }
                    }
                }
            }

            if (!flag) {
                return false;
            }

            Block block1 = world.getBlockState(pos.down()).getBlock();
            if (block1 != Blocks.DIRT && block1 != Blocks.GRASS && block1 != Blocks.MYCELIUM) {
                return false;
            }

            int k2 = pos.getY() + i;
            if (this.mushroom == Blocks.RED_MUSHROOM_BLOCK) {
                k2 = pos.getY() + i - 3;
            }

            for (int l2 = k2; l2 <= pos.getY() + i; l2++) {
                int j3 = 1;
                if (l2 < pos.getY() + i) {
                    j3++;
                }

                if (this.mushroom == Blocks.BROWN_MUSHROOM_BLOCK) {
                    j3 = 3;
                }

                int k3 = pos.getX() - j3;
                int l3 = pos.getX() + j3;
                int j1 = pos.getZ() - j3;
                int k1 = pos.getZ() + j3;

                for (int l1 = k3; l1 <= l3; l1++) {
                    for (int i2 = j1; i2 <= k1; i2++) {
                        int j2 = 5;
                        if (l1 == k3) {
                            j2--;
                        } else if (l1 == l3) {
                            j2++;
                        }

                        if (i2 == j1) {
                            j2 -= 3;
                        } else if (i2 == k1) {
                            j2 += 3;
                        }

                        MushroomBlock.Variant mushroomblock$variant = MushroomBlock.Variant.byId(j2);
                        if (this.mushroom == Blocks.BROWN_MUSHROOM_BLOCK || l2 < pos.getY() + i) {
                            if ((l1 == k3 || l1 == l3) && (i2 == j1 || i2 == k1)) {
                                continue;
                            }

                            if (l1 == pos.getX() - (j3 - 1) && i2 == j1) {
                                mushroomblock$variant = MushroomBlock.Variant.NORTH_WEST;
                            }

                            if (l1 == k3 && i2 == pos.getZ() - (j3 - 1)) {
                                mushroomblock$variant = MushroomBlock.Variant.NORTH_WEST;
                            }

                            if (l1 == pos.getX() + (j3 - 1) && i2 == j1) {
                                mushroomblock$variant = MushroomBlock.Variant.NORTH_EAST;
                            }

                            if (l1 == l3 && i2 == pos.getZ() - (j3 - 1)) {
                                mushroomblock$variant = MushroomBlock.Variant.NORTH_EAST;
                            }

                            if (l1 == pos.getX() - (j3 - 1) && i2 == k1) {
                                mushroomblock$variant = MushroomBlock.Variant.SOUTH_WEST;
                            }

                            if (l1 == k3 && i2 == pos.getZ() + (j3 - 1)) {
                                mushroomblock$variant = MushroomBlock.Variant.SOUTH_WEST;
                            }

                            if (l1 == pos.getX() + (j3 - 1) && i2 == k1) {
                                mushroomblock$variant = MushroomBlock.Variant.SOUTH_EAST;
                            }

                            if (l1 == l3 && i2 == pos.getZ() + (j3 - 1)) {
                                mushroomblock$variant = MushroomBlock.Variant.SOUTH_EAST;
                            }
                        }

                        if (mushroomblock$variant == MushroomBlock.Variant.CENTER && l2 < pos.getY() + i) {
                            mushroomblock$variant = MushroomBlock.Variant.ALL_INSIDE;
                        }

                        if (pos.getY() >= pos.getY() + i - 1 || mushroomblock$variant != MushroomBlock.Variant.ALL_INSIDE) {
                            BlockPos blockpos = new BlockPos(l1, l2, i2);
                            if (!world.getBlockState(blockpos).getBlock().isOpaque()) {
                                this.setBlockState(world, blockpos, this.mushroom.defaultState().set(MushroomBlock.VARIANT, mushroomblock$variant));
                            }
                        }
                    }
                }
            }

            for (int i3 = 0; i3 < i; i3++) {
                Block block2 = world.getBlockState(pos.up(i3)).getBlock();
                if (!block2.isOpaque()) {
                    this.setBlockState(world, pos.up(i3), this.mushroom.defaultState().set(MushroomBlock.VARIANT, MushroomBlock.Variant.STEM));
                }
            }

            return true;
        } else {
            return false;
        }
    }
}
