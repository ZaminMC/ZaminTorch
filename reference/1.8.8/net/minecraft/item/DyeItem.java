package net.minecraft.item;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class DyeItem extends Item {
    public static final int[] COLORS = new int[]{
        1973019, 11743532, 3887386, 5320730, 2437522, 8073150, 2651799, 11250603, 4408131, 14188952, 4312372, 14602026, 6719955, 12801229, 15435844, 15790320
    };

    public DyeItem() {
        this.setHasCustomData(true);
        this.setMaxDamage(0);
        this.setCreativeModeTab(CreativeModeTab.MATERIALS);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int i = stack.getMetadata();
        return super.getTranslationKey() + "." + DyeColor.byMetadata(i).getName();
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (!player.canUseItemOn(pos.offset(face), face, stack)) {
            return false;
        }

        DyeColor dyecolor = DyeColor.byMetadata(stack.getMetadata());
        if (dyecolor == DyeColor.WHITE) {
            if (fertilize(stack, world, pos)) {
                if (!world.isClient) {
                    world.doEvent(2005, pos, 0);
                }

                return true;
            }
        } else if (dyecolor == DyeColor.BROWN) {
            BlockState blockstate = world.getBlockState(pos);
            Block block = blockstate.getBlock();
            if (block == Blocks.LOG && blockstate.get(PlanksBlock.VARIANT) == PlanksBlock.Variant.JUNGLE) {
                if (face == Direction.DOWN) {
                    return false;
                }

                if (face == Direction.UP) {
                    return false;
                }

                pos = pos.offset(face);
                if (world.isAir(pos)) {
                    BlockState blockstate1 = Blocks.COCOA.getPlacementState(world, pos, face, faceX, faceY, faceZ, 0, player);
                    world.setBlockState(pos, blockstate1, 2);
                    if (!player.abilities.creativeMode) {
                        stack.size--;
                    }
                }

                return true;
            }
        }

        return false;
    }

    public static boolean fertilize(ItemStack stack, World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() instanceof Fertilizable) {
            Fertilizable fertilizable = (Fertilizable)blockstate.getBlock();
            if (fertilizable.canGrow(world, pos, blockstate, world.isClient)) {
                if (!world.isClient) {
                    if (fertilizable.canBeFertilized(world, world.random, pos, blockstate)) {
                        fertilizable.grow(world, world.random, pos, blockstate);
                    }

                    stack.size--;
                }

                return true;
            }
        }

        return false;
    }

    public static void spawnParticles(World world, BlockPos x, int y) {
        if (y == 0) {
            y = 15;
        }

        Block block = world.getBlockState(x).getBlock();
        if (block.getMaterial() != Material.AIR) {
            block.updateShape(world, x);

            for (int i = 0; i < y; i++) {
                double d0 = random.nextGaussian() * 0.02;
                double d1 = random.nextGaussian() * 0.02;
                double d2 = random.nextGaussian() * 0.02;
                world.addParticle(
                    ParticleType.VILLAGER_HAPPY,
                    x.getX() + random.nextFloat(),
                    x.getY() + random.nextFloat() * block.getMaxY(),
                    x.getZ() + random.nextFloat(),
                    d0,
                    d1,
                    d2
                );
            }
        }
    }

    @Override
    public boolean interact(ItemStack stack, PlayerEntity player, LivingEntity entity) {
        if (entity instanceof SheepEntity) {
            SheepEntity sheepentity = (SheepEntity)entity;
            DyeColor dyecolor = DyeColor.byMetadata(stack.getMetadata());
            if (!sheepentity.isSheared() && sheepentity.getColor() != dyecolor) {
                sheepentity.setColor(dyecolor);
                stack.size--;
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (int i = 0; i < 16; i++) {
            inventory.add(new ItemStack(item, 1, i));
        }
    }
}
