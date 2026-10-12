package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class GravelBlock extends FallingBlock {
    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        if (fortuneLevel > 3) {
            fortuneLevel = 3;
        }

        return random.nextInt(10 - fortuneLevel * 3) == 0 ? Items.FLINT : Item.byBlock(this);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.STONE;
    }
}
