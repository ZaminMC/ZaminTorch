package net.minecraft.item;

import net.minecraft.entity.decoration.DecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class DecorationItem extends Item {
    private final Class<? extends DecorationEntity> type;

    public DecorationItem(Class<? extends DecorationEntity> type) {
        this.type = type;
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (face == Direction.DOWN) {
            return false;
        }

        if (face == Direction.UP) {
            return false;
        }

        BlockPos blockpos = pos.offset(face);
        if (!player.canUseItemOn(blockpos, face, stack)) {
            return false;
        }

        DecorationEntity decorationentity = this.place(world, blockpos, face);
        if (decorationentity != null && decorationentity.canSurvive()) {
            if (!world.isClient) {
                world.addEntity(decorationentity);
            }

            stack.size--;
        }

        return true;
    }

    private DecorationEntity place(World world, BlockPos pos, Direction facing) {
        if (this.type == PaintingEntity.class) {
            return new PaintingEntity(world, pos, facing);
        } else {
            return this.type == ItemFrameEntity.class ? new ItemFrameEntity(world, pos, facing) : null;
        }
    }
}
