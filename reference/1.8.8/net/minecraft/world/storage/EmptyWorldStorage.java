package net.minecraft.world.storage;

import java.io.File;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.WorldData;
import net.minecraft.world.chunk.storage.ChunkStorage;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.storage.exception.SessionLockException;

public class EmptyWorldStorage implements WorldStorage {
    @Override
    public WorldData loadData() {
        return null;
    }

    @Override
    public void checkSessionLock() throws SessionLockException {
    }

    @Override
    public ChunkStorage getChunkStorage(Dimension dimension) {
        return null;
    }

    @Override
    public void saveData(WorldData data, NbtCompound playerData) {
    }

    @Override
    public void saveData(WorldData data) {
    }

    @Override
    public PlayerDataStorage getPlayerDataStorage() {
        return null;
    }

    @Override
    public void forceSave() {
    }

    @Override
    public File getDataFile(String name) {
        return null;
    }

    @Override
    public String getName() {
        return "none";
    }

    @Override
    public File getDirectory() {
        return null;
    }
}
