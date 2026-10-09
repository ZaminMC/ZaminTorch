package net.minecraft.client.world.storage;

import net.minecraft.world.saveddata.SavedData;
import net.minecraft.world.storage.SavedDataStorage;

public class ClientSavedDataStorage extends SavedDataStorage {
    public ClientSavedDataStorage() {
        super(null);
    }

    @Override
    public SavedData load(Class<? extends SavedData> type, String id) {
        return this.savedDataById.get(id);
    }

    @Override
    public void set(String id, SavedData data) {
        this.savedDataById.put(id, data);
    }

    @Override
    public void save() {
    }

    @Override
    public int getNextCount(String id) {
        return 0;
    }
}
