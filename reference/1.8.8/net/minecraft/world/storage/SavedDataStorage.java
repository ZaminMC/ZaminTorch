package net.minecraft.world.storage;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtShort;
import net.minecraft.world.saveddata.SavedData;

public class SavedDataStorage {
    private WorldStorage storage;
    protected Map<String, SavedData> savedDataById = Maps.newHashMap();
    private List<SavedData> savedData = Lists.newArrayList();
    private Map<String, Short> idCounts = Maps.newHashMap();

    public SavedDataStorage(WorldStorage storage) {
        this.storage = storage;
        this.loadIdCounts();
    }

    public SavedData load(Class<? extends SavedData> type, String id) {
        SavedData saveddata = this.savedDataById.get(id);
        if (saveddata != null) {
            return saveddata;
        }

        if (this.storage != null) {
            try {
                File file1 = this.storage.getDataFile(id);
                if (file1 != null && file1.exists()) {
                    try {
                        saveddata = type.getConstructor(String.class).newInstance(id);
                    } catch (Exception exception) {
                        throw new RuntimeException("Failed to instantiate " + type.toString(), exception);
                    }

                    FileInputStream fileinputstream = new FileInputStream(file1);
                    NbtCompound nbtcompound = NbtIo.readCompressed(fileinputstream);
                    fileinputstream.close();
                    saveddata.readNbt(nbtcompound.getCompound("data"));
                }
            } catch (Exception exception1) {
                exception1.printStackTrace();
            }
        }

        if (saveddata != null) {
            this.savedDataById.put(id, saveddata);
            this.savedData.add(saveddata);
        }

        return saveddata;
    }

    public void set(String id, SavedData data) {
        if (this.savedDataById.containsKey(id)) {
            this.savedData.remove(this.savedDataById.remove(id));
        }

        this.savedDataById.put(id, data);
        this.savedData.add(data);
    }

    public void save() {
        for (int i = 0; i < this.savedData.size(); i++) {
            SavedData saveddata = this.savedData.get(i);
            if (saveddata.isDirty()) {
                this.save(saveddata);
                saveddata.setDirty(false);
            }
        }
    }

    private void save(SavedData data) {
        if (this.storage != null) {
            try {
                File file1 = this.storage.getDataFile(data.id);
                if (file1 != null) {
                    NbtCompound nbtcompound = new NbtCompound();
                    data.writeNbt(nbtcompound);
                    NbtCompound nbtcompound1 = new NbtCompound();
                    nbtcompound1.put("data", nbtcompound);
                    FileOutputStream fileoutputstream = new FileOutputStream(file1);
                    NbtIo.writeCompressed(nbtcompound1, fileoutputstream);
                    fileoutputstream.close();
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private void loadIdCounts() {
        try {
            this.idCounts.clear();
            if (this.storage == null) {
                return;
            }

            File file1 = this.storage.getDataFile("idcounts");
            if (file1 != null && file1.exists()) {
                DataInputStream datainputstream = new DataInputStream(new FileInputStream(file1));
                NbtCompound nbtcompound = NbtIo.read(datainputstream);
                datainputstream.close();

                for (String s : nbtcompound.getKeys()) {
                    NbtElement nbtelement = nbtcompound.get(s);
                    if (nbtelement instanceof NbtShort) {
                        NbtShort nbtshort = (NbtShort)nbtelement;
                        String s1 = s;
                        short short1 = nbtshort.getShort();
                        this.idCounts.put(s1, short1);
                    }
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public int getNextCount(String id) {
        Short oshort = this.idCounts.get(id);
        if (oshort == null) {
            oshort = (short)0;
        } else {
            oshort = (short)(oshort + 1);
        }

        this.idCounts.put(id, oshort);
        if (this.storage == null) {
            return oshort;
        }

        try {
            File file1 = this.storage.getDataFile("idcounts");
            if (file1 != null) {
                NbtCompound nbtcompound = new NbtCompound();

                for (String s : this.idCounts.keySet()) {
                    short short1 = this.idCounts.get(s);
                    nbtcompound.putShort(s, short1);
                }

                DataOutputStream dataoutputstream = new DataOutputStream(new FileOutputStream(file1));
                NbtIo.write(nbtcompound, dataoutputstream);
                dataoutputstream.close();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        return oshort;
    }
}
