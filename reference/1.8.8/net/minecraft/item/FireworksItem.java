package net.minecraft.item;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.FireworksEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FireworksItem extends Item {
    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (!world.isClient) {
            FireworksEntity fireworksentity = new FireworksEntity(world, pos.getX() + faceX, pos.getY() + faceY, pos.getZ() + faceZ, stack);
            world.addEntity(fireworksentity);
            if (!player.abilities.creativeMode) {
                stack.size--;
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
        if (stack.hasNbt()) {
            NbtCompound nbtcompound = stack.getNbt().getCompound("Fireworks");
            if (nbtcompound != null) {
                if (nbtcompound.contains("Flight", 99)) {
                    tooltip.add(I18n.translate("item.fireworks.flight") + " " + nbtcompound.getByte("Flight"));
                }

                NbtList nbtlist = nbtcompound.getList("Explosions", 10);
                if (nbtlist != null && nbtlist.size() > 0) {
                    for (int i = 0; i < nbtlist.size(); i++) {
                        NbtCompound nbtcompound1 = nbtlist.getCompound(i);
                        List<String> list = Lists.newArrayList();
                        FireworksChargeItem.addExplosionInfo(nbtcompound1, list);
                        if (list.size() > 0) {
                            for (int j = 1; j < list.size(); j++) {
                                list.set(j, "  " + list.get(j));
                            }

                            tooltip.addAll(list);
                        }
                    }
                }
            }
        }
    }
}
