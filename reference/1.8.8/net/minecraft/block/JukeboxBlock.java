package net.minecraft.block;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class JukeboxBlock extends BlockWithBlockEntity {
    public static final BooleanProperty HAS_RECORD = BooleanProperty.of("has_record");

    protected JukeboxBlock() {
        super(Material.WOOD, MapColor.DIRT);
        this.setDefaultState(this.stateDefinition.any().set(HAS_RECORD, false));
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (state.get(HAS_RECORD)) {
            this.removeRecord(world, pos, state);
            state = state.set(HAS_RECORD, false);
            world.setBlockState(pos, state, 2);
            return true;
        } else {
            return false;
        }
    }

    public void setRecord(World world, BlockPos pos, BlockState state, ItemStack item) {
        if (!world.isClient) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof JukeboxBlock.JukeboxBlockEntity) {
                ((JukeboxBlock.JukeboxBlockEntity)blockentity).setRecord(new ItemStack(item.getItem(), 1, item.getMetadata()));
                world.setBlockState(pos, state.set(HAS_RECORD, true), 2);
            }
        }
    }

    private void removeRecord(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof JukeboxBlock.JukeboxBlockEntity) {
                JukeboxBlock.JukeboxBlockEntity jukeboxblock$jukeboxblockentity = (JukeboxBlock.JukeboxBlockEntity)blockentity;
                ItemStack itemstack = jukeboxblock$jukeboxblockentity.getRecord();
                if (itemstack != null) {
                    world.doEvent(1005, pos, 0);
                    world.playRecordMusic(pos, null);
                    jukeboxblock$jukeboxblockentity.setRecord(null);
                    float f = 0.7F;
                    double d0 = world.random.nextFloat() * f + (1.0F - f) * 0.5;
                    double d1 = world.random.nextFloat() * f + (1.0F - f) * 0.2 + 0.6;
                    double d2 = world.random.nextFloat() * f + (1.0F - f) * 0.5;
                    ItemStack itemstack1 = itemstack.copy();
                    ItemEntity itementity = new ItemEntity(world, pos.getX() + d0, pos.getY() + d1, pos.getZ() + d2, itemstack1);
                    itementity.setDefaultPickUpDelay();
                    world.addEntity(itementity);
                }
            }
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        this.removeRecord(world, pos, state);
        super.onRemoved(world, pos, state);
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        if (!world.isClient) {
            super.dropItems(world, pos, state, luck, 0);
        }
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new JukeboxBlock.JukeboxBlockEntity();
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof JukeboxBlock.JukeboxBlockEntity) {
            ItemStack itemstack = ((JukeboxBlock.JukeboxBlockEntity)blockentity).getRecord();
            if (itemstack != null) {
                return Item.getId(itemstack.getItem()) + 1 - Item.getId(Items.RECORD_13);
            }
        }

        return 0;
    }

    @Override
    public int getRenderType() {
        return 3;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(HAS_RECORD, metadata > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(HAS_RECORD) ? 1 : 0;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, HAS_RECORD);
    }

    public static class JukeboxBlockEntity extends BlockEntity {
        private ItemStack record;

        @Override
        public void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            if (nbt.contains("RecordItem", 10)) {
                this.setRecord(ItemStack.fromNbt(nbt.getCompound("RecordItem")));
            } else if (nbt.getInt("Record") > 0) {
                this.setRecord(new ItemStack(Item.byId(nbt.getInt("Record")), 1, 0));
            }
        }

        @Override
        public void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            if (this.getRecord() != null) {
                nbt.put("RecordItem", this.getRecord().writeNbt(new NbtCompound()));
            }
        }

        public ItemStack getRecord() {
            return this.record;
        }

        public void setRecord(ItemStack record) {
            this.record = record;
            this.markDirty();
        }
    }
}
