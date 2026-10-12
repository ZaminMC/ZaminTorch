package net.minecraft.block;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public class BeaconBlock extends BlockWithBlockEntity {
    public BeaconBlock() {
        super(Material.GLASS, MapColor.DIAMOND);
        this.setStrength(3.0F);
        this.setCreativeModeTab(CreativeModeTab.MISC);
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new BeaconBlockEntity();
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof BeaconBlockEntity) {
            player.openChestMenu((BeaconBlockEntity)blockentity);
            player.incrementStat(Stats.BEACON_INTERACTIONS);
        }

        return true;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 3;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        super.onPlaced(world, pos, state, entity, item);
        if (item.hasCustomHoverName()) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof BeaconBlockEntity) {
                ((BeaconBlockEntity)blockentity).setCustomName(item.getHoverName());
            }
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof BeaconBlockEntity) {
            ((BeaconBlockEntity)blockentity).update();
            world.addBlockEvent(pos, this, 1, 0);
        }
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    public static void updateBeam(World world, BlockPos pos) {
        HttpUtil.DOWNLOAD_THREAD_FACTORY.submit(new Runnable() {
            @Override
            public void run() {
                WorldChunk worldchunk = world.getChunk(pos);

                for (int i = pos.getY() - 1; i >= 0; i--) {
                    final BlockPos blockpos = new BlockPos(pos.getX(), i, pos.getZ());
                    if (!worldchunk.hasSkyAccess(blockpos)) {
                        break;
                    }

                    BlockState blockstate = world.getBlockState(blockpos);
                    if (blockstate.getBlock() == Blocks.BEACON) {
                        ((ServerWorld)world).execute(new Runnable() {
                            @Override
                            public void run() {
                                BlockEntity blockentity = world.getBlockEntity(blockpos);
                                if (blockentity instanceof BeaconBlockEntity) {
                                    ((BeaconBlockEntity)blockentity).update();
                                    world.addBlockEvent(blockpos, Blocks.BEACON, 1, 0);
                                }
                            }
                        });
                    }
                }
            }
        });
    }
}
