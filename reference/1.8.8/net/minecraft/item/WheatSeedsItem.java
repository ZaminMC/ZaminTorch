package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class WheatSeedsItem extends Item {
    private Block plantBlock;
    private Block soilBlock;

    public WheatSeedsItem(Block plant, Block soil) {
        this.plantBlock = plant;
        this.soilBlock = soil;
        this.setCreativeModeTab(CreativeModeTab.MATERIALS);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (face != Direction.UP) {
            return false;
        } else if (!player.canUseItemOn(pos.offset(face), face, stack)) {
            return false;
        } else if (world.getBlockState(pos).getBlock() == this.soilBlock && world.isAir(pos.up())) {
            world.setBlockState(pos.up(), this.plantBlock.defaultState());
            stack.size--;
            return true;
        } else {
            return false;
        }
    }
}
