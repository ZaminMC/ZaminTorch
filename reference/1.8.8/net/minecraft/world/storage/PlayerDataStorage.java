package net.minecraft.world.storage;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

public interface PlayerDataStorage {
    void savePlayerData(PlayerEntity player);

    NbtCompound loadPlayerData(PlayerEntity player);

    String[] getSavedPlayerIds();
}
