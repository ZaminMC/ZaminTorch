package net.minecraft.realms;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.storage.WorldSaveInfo;
import net.minecraft.world.storage.WorldStorageSource;
import net.minecraft.world.storage.exception.WorldStorageException;

public class RealmsAnvilLevelStorageSource {
    private WorldStorageSource levelStorageSource;

    public RealmsAnvilLevelStorageSource(WorldStorageSource storageSource) {
        this.levelStorageSource = storageSource;
    }

    public String getName() {
        return this.levelStorageSource.getName();
    }

    public boolean levelExists(String saveName) {
        return this.levelStorageSource.exists(saveName);
    }

    public boolean convertLevel(String saveName, ProgressListener listener) {
        return this.levelStorageSource.convert(saveName, listener);
    }

    public boolean requiresConversion(String saveName) {
        return this.levelStorageSource.needsConversion(saveName);
    }

    public boolean isNewLevelIdAcceptable(String saveName) {
        return this.levelStorageSource.canCreate(saveName);
    }

    public boolean deleteLevel(String saveName) {
        return this.levelStorageSource.delete(saveName);
    }

    public boolean isConvertible(String saveName) {
        return this.levelStorageSource.isConvertible(saveName);
    }

    public void renameLevel(String saveName, String newName) {
        this.levelStorageSource.rename(saveName, newName);
    }

    public void clearAll() {
        this.levelStorageSource.flush();
    }

    public List<RealmsLevelSummary> getLevelList() throws WorldStorageException {
        List<RealmsLevelSummary> list = Lists.newArrayList();

        for (WorldSaveInfo worldsaveinfo : this.levelStorageSource.getAll()) {
            list.add(new RealmsLevelSummary(worldsaveinfo));
        }

        return list;
    }
}
