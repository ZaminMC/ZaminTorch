package net.minecraft.world.storage;

import java.util.List;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.WorldData;
import net.minecraft.world.storage.exception.WorldStorageException;

public interface WorldStorageSource {
    String getName();

    WorldStorage get(String saveName, boolean createPlayerDataDir);

    List<WorldSaveInfo> getAll() throws WorldStorageException;

    void flush();

    WorldData getData(String saveName);

    boolean canCreate(String saveName);

    boolean delete(String saveName);

    void rename(String saveName, String newName);

    boolean isConvertible(String saveName);

    boolean needsConversion(String saveName);

    boolean convert(String saveName, ProgressListener listener);

    boolean exists(String saveName);
}
