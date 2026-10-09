package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CropItem extends FoodItem {
    private Block plant;
    private Block soil;

    public CropItem(int hungerPoints, float saturation, Block plant, Block soil) {
        super(hungerPoints, saturation, false);
        this.plant = plant;
        this.soil = soil;
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (face != Direction.UP) {
            return false;
        } else if (!player.canUseItemOn(pos.offset(face), face, stack)) {
            return false;
        } else if (world.getBlockState(pos).getBlock() == this.soil && world.isAir(pos.up())) {
            world.setBlockState(pos.up(), this.plant.defaultState());
            stack.size--;
            return true;
        } else {
            return false;
        }
    }
}
