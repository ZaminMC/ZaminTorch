package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.FenceBlock;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class LeadItem extends Item {
    public LeadItem() {
        this.setCreativeModeTab(CreativeModeTab.TOOLS);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        Block block = world.getBlockState(pos).getBlock();
        if (block instanceof FenceBlock) {
            if (world.isClient) {
                return true;
            }

            attachLead(player, world, pos);
            return true;
        } else {
            return false;
        }
    }

    public static boolean attachLead(PlayerEntity player, World world, BlockPos pos) {
        LeadKnotEntity leadknotentity = LeadKnotEntity.getOrCreate(world, pos);
        boolean flag = false;
        double d0 = 7.0;
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        for (MobEntity mobentity : world.getEntitiesOfType(MobEntity.class, new Box(i - d0, j - d0, k - d0, i + d0, j + d0, k + d0))) {
            if (mobentity.isLeashed() && mobentity.getLeashHolder() == player) {
                if (leadknotentity == null) {
                    leadknotentity = LeadKnotEntity.attatch(world, pos);
                }

                mobentity.attachLeash(leadknotentity, true);
                flag = true;
            }
        }

        return flag;
    }
}
