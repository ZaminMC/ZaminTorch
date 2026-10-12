package net.minecraft.block;

import com.google.common.base.Predicate;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class Leaves2Block extends AbstractLeavesBlock {
    public static final EnumProperty<PlanksBlock.Variant> VARIANT = EnumProperty.of(
        "variant", PlanksBlock.Variant.class, new Predicate<PlanksBlock.Variant>() {
            public boolean apply(PlanksBlock.Variant variant) {
                return variant.getId() >= 4;
            }
        }
    );

    public Leaves2Block() {
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, PlanksBlock.Variant.ACACIA).set(CHECK_DECAY, true).set(DECAYABLE, true));
    }

    @Override
    protected void dropAppleWithChance(World world, BlockPos pos, BlockState state, int chance) {
        if (state.get(VARIANT) == PlanksBlock.Variant.DARK_OAK && world.random.nextInt(chance) == 0) {
            dropItem(world, pos, new ItemStack(Items.APPLE, 1, 0));
        }
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        return blockstate.getBlock().getMetadataFromState(blockstate) & 3;
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, 0));
        inventory.add(new ItemStack(item, 1, 1));
    }

    @Override
    protected ItemStack getSilkTouchDrop(BlockState state) {
        return new ItemStack(Item.byBlock(this), 1, state.get(VARIANT).getId() - 4);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, this.getVariant(metadata)).set(DECAYABLE, (metadata & 4) == 0).set(CHECK_DECAY, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(VARIANT).getId() - 4;
        if (!state.get(DECAYABLE)) {
            i |= 4;
        }

        if (state.get(CHECK_DECAY)) {
            i |= 8;
        }

        return i;
    }

    @Override
    public PlanksBlock.Variant getVariant(int metadata) {
        return PlanksBlock.Variant.byId((metadata & 3) + 4);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, VARIANT, CHECK_DECAY, DECAYABLE);
    }

    @Override
    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!world.isClient && player.getItemInHand() != null && player.getItemInHand().getItem() == Items.SHEARS) {
            player.incrementStat(Stats.BLOCKS_MINED[Block.getId(this)]);
            dropItem(world, pos, new ItemStack(Item.byBlock(this), 1, state.get(VARIANT).getId() - 4));
        } else {
            super.afterMinedByPlayer(world, player, pos, state, blockEntity);
        }
    }
}
