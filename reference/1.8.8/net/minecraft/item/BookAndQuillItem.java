package net.minecraft.item;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;

public class BookAndQuillItem extends Item {
    public BookAndQuillItem() {
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        player.openEditBookScreen(stack);
        player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        return stack;
    }

    public static boolean isValid(NbtCompound tag) {
        if (tag == null) {
            return false;
        }

        if (!tag.contains("pages", 9)) {
            return false;
        }

        NbtList nbtlist = tag.getList("pages", 8);

        for (int i = 0; i < nbtlist.size(); i++) {
            String s = nbtlist.getString(i);
            if (s == null) {
                return false;
            }

            if (s.length() > 32767) {
                return false;
            }
        }

        return true;
    }
}
