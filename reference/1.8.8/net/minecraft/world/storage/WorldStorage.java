package net.minecraft.world.storage;

import java.io.File;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.WorldData;
import net.minecraft.world.chunk.storage.ChunkStorage;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.storage.exception.SessionLockException;

public interface WorldStorage {
    WorldData loadData();

    void checkSessionLock() throws SessionLockException;

    ChunkStorage getChunkStorage(Dimension dimension);

    void saveData(WorldData data, NbtCompound playerData);

    void saveData(WorldData data);

    PlayerDataStorage getPlayerDataStorage();

    void forceSave();

    File getDirectory();

    File getDataFile(String name);

    String getName();
}
