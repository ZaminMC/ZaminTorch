package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class EnchantingTableBlock extends BlockWithBlockEntity {
    protected EnchantingTableBlock() {
        super(Material.STONE, MapColor.RED);
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.75F, 1.0F);
        this.setOpacity(0);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        super.randomDisplayTick(world, pos, state, random);

        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (i > -2 && i < 2 && j == -1) {
                    j = 2;
                }

                if (random.nextInt(16) == 0) {
                    for (int k = 0; k <= 1; k++) {
                        BlockPos blockpos = pos.add(i, k, j);
                        if (world.getBlockState(blockpos).getBlock() == Blocks.BOOKSHELF) {
                            if (!world.isAir(pos.add(i / 2, 0, j / 2))) {
                                break;
                            }

                            world.addParticle(
                                ParticleType.ENCHANTMENT_TABLE,
                                pos.getX() + 0.5,
                                pos.getY() + 2.0,
                                pos.getZ() + 0.5,
                                i + random.nextFloat() - 0.5,
                                k - random.nextFloat() - 1.0F,
                                j + random.nextFloat() - 0.5
                            );
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 3;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new EnchantingTableBlockEntity();
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof EnchantingTableBlockEntity) {
            player.openMenu((EnchantingTableBlockEntity)blockentity);
        }

        return true;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        super.onPlaced(world, pos, state, entity, item);
        if (item.hasCustomHoverName()) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof EnchantingTableBlockEntity) {
                ((EnchantingTableBlockEntity)blockentity).setCustomName(item.getHoverName());
            }
        }
    }
}
