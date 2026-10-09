package net.minecraft.item;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.World;

public class NetworkSyncedItem extends Item {
    protected NetworkSyncedItem() {
    }

    @Override
    public boolean isNetworkSynced() {
        return true;
    }

    public Packet getUpdatePacket(ItemStack stack, World world, PlayerEntity player) {
        return null;
    }
}
