package net.minecraft.block;

import net.minecraft.block.state.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PotatoesBlock extends WheatBlock {
    @Override
    protected Item getSeedItem() {
        return Items.POTATO;
    }

    @Override
    protected Item getPlantItem() {
        return Items.POTATO;
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        super.dropItems(world, pos, state, luck, fortuneLevel);
        if (!world.isClient) {
            if (state.get(AGE) >= 7 && world.random.nextInt(50) == 0) {
                dropItem(world, pos, new ItemStack(Items.POISONOUS_POTATO));
            }
        }
    }
}
