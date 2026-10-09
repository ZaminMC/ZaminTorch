package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class EnderChestBlock extends BlockWithBlockEntity {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);

    protected EnderChestBlock() {
        super(Material.STONE);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH));
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
        this.setShape(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.875F, 0.9375F);
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
        return 2;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.OBSIDIAN);
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 8;
    }

    @Override
    protected boolean hasSilkTouchDrops() {
        return true;
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, entity.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        world.setBlockState(pos, state.set(FACING, entity.getHorizontalFacing().getOpposite()), 2);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        EnderChestInventory enderchestinventory = player.getEnderChestInventory();
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (enderchestinventory == null || !(blockentity instanceof EnderChestBlockEntity)) {
            return true;
        }

        if (world.getBlockState(pos.up()).getBlock().isSolid()) {
            return true;
        }

        if (world.isClient) {
            return true;
        }

        enderchestinventory.setCurrentBlockEntity((EnderChestBlockEntity)blockentity);
        player.openChestMenu(enderchestinventory);
        player.incrementStat(Stats.ENDER_CHESTS_OPENED);
        return true;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new EnderChestBlockEntity();
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        for (int i = 0; i < 3; i++) {
            int j = random.nextInt(2) * 2 - 1;
            int k = random.nextInt(2) * 2 - 1;
            double d0 = pos.getX() + 0.5 + 0.25 * j;
            double d1 = pos.getY() + random.nextFloat();
            double d2 = pos.getZ() + 0.5 + 0.25 * k;
            double d3 = random.nextFloat() * j;
            double d4 = (random.nextFloat() - 0.5) * 0.125;
            double d5 = random.nextFloat() * k;
            world.addParticle(ParticleType.PORTAL, d0, d1, d2, d3, d4, d5);
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        Direction direction = Direction.byId(metadata);
        if (direction.getAxis() == Direction.Axis.Y) {
            direction = Direction.NORTH;
        }

        return this.defaultState().set(FACING, direction);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(FACING).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING);
    }
}
