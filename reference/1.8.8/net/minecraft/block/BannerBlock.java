package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class BannerBlock extends BlockWithBlockEntity {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);
    public static final IntegerProperty ROTATION = IntegerProperty.of("rotation", 0, 15);

    protected BannerBlock() {
        super(Material.WOOD);
        float f = 0.25F;
        float f1 = 1.0F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f1, 0.5F + f);
    }

    @Override
    public String getName() {
        return I18n.translate("item.banner.white.name");
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        this.updateShape(world, pos);
        return super.getOutlineShape(world, pos);
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean canRespawnIn() {
        return true;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new BannerBlockEntity();
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.BANNER;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.BANNER;
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof BannerBlockEntity) {
            ItemStack itemstack = new ItemStack(Items.BANNER, 1, ((BannerBlockEntity)blockentity).getBase());
            NbtCompound nbtcompound = new NbtCompound();
            blockentity.writeNbt(nbtcompound);
            nbtcompound.remove("x");
            nbtcompound.remove("y");
            nbtcompound.remove("z");
            nbtcompound.remove("id");
            itemstack.addToNbt("BlockEntityTag", nbtcompound);
            dropItem(world, pos, itemstack);
        } else {
            super.dropItems(world, pos, state, luck, fortuneLevel);
        }
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return !this.isNeighboringCactus(world, pos) && super.canBePlaced(world, pos);
    }

    @Override
    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (blockEntity instanceof BannerBlockEntity) {
            BannerBlockEntity bannerblockentity = (BannerBlockEntity)blockEntity;
            ItemStack itemstack = new ItemStack(Items.BANNER, 1, ((BannerBlockEntity)blockEntity).getBase());
            NbtCompound nbtcompound = new NbtCompound();
            BannerBlockEntity.writeNbt(nbtcompound, bannerblockentity.getBase(), bannerblockentity.getPatternsNbt());
            itemstack.addToNbt("BlockEntityTag", nbtcompound);
            dropItem(world, pos, itemstack);
        } else {
            super.afterMinedByPlayer(world, player, pos, state, null);
        }
    }

    public static class Standing extends BannerBlock {
        public Standing() {
            this.setDefaultState(this.stateDefinition.any().set(ROTATION, 0));
        }

        @Override
        public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
            if (!world.getBlockState(pos.down()).getBlock().getMaterial().isSolid()) {
                this.dropItems(world, pos, state, 0);
                world.removeBlock(pos);
            }

            super.neighborChanged(world, pos, state, neighborBlock);
        }

        @Override
        public BlockState getStateFromMetadata(int metadata) {
            return this.defaultState().set(ROTATION, metadata);
        }

        @Override
        public int getMetadataFromState(BlockState state) {
            return state.get(ROTATION);
        }

        @Override
        protected StateDefinition createStateDefinition() {
            return new StateDefinition(this, ROTATION);
        }
    }

    public static class Wall extends BannerBlock {
        public Wall() {
            this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH));
        }

        @Override
        public void updateShape(WorldView world, BlockPos pos) {
            Direction direction = world.getBlockState(pos).get(FACING);
            float f = 0.0F;
            float f1 = 0.78125F;
            float f2 = 0.0F;
            float f3 = 1.0F;
            float f4 = 0.125F;
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
            switch (direction) {
                case NORTH:
                default:
                    this.setShape(f2, f, 1.0F - f4, f3, f1, 1.0F);
                    break;
                case SOUTH:
                    this.setShape(f2, f, 0.0F, f3, f1, f4);
                    break;
                case WEST:
                    this.setShape(1.0F - f4, f, f2, 1.0F, f1, f3);
                    break;
                case EAST:
                    this.setShape(0.0F, f, f2, f4, f1, f3);
            }
        }

        @Override
        public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
            Direction direction = state.get(FACING);
            if (!world.getBlockState(pos.offset(direction.getOpposite())).getBlock().getMaterial().isSolid()) {
                this.dropItems(world, pos, state, 0);
                world.removeBlock(pos);
            }

            super.neighborChanged(world, pos, state, neighborBlock);
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
}
