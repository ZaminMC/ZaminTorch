package net.minecraft.item;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;
import net.minecraft.world.map.SavedMapData;

public class EmptyMapItem extends NetworkSyncedItem {
    protected EmptyMapItem() {
        this.setCreativeModeTab(CreativeModeTab.MISC);
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        ItemStack itemstack = new ItemStack(Items.FILLED_MAP, 1, world.getSavedDataCount("map"));
        String s = "map_" + itemstack.getMetadata();
        SavedMapData savedmapdata = new SavedMapData(s);
        world.setSavedData(s, savedmapdata);
        savedmapdata.scale = 0;
        savedmapdata.updateCenter(player.x, player.z, savedmapdata.scale);
        savedmapdata.dimension = (byte)world.dimension.getId();
        savedmapdata.markDirty();
        stack.size--;
        if (stack.size <= 0) {
            return itemstack;
        }

        if (!player.inventory.addItem(itemstack.copy())) {
            player.dropItem(itemstack, false);
        }

        player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        return stack;
    }
}
