package net.minecraft.world.gen.feature;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.saveddata.SavedData;

public class SavedFeatureData extends SavedData {
    private NbtCompound features = new NbtCompound();

    public SavedFeatureData(String string) {
        super(string);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        this.features = nbt.getCompound("Features");
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        nbt.put("Features", this.features);
    }

    public void put(NbtCompound nbt, int chunkX, int chunkZ) {
        this.features.put(toKey(chunkX, chunkZ), nbt);
    }

    public static String toKey(int chunkX, int chunkZ) {
        return "[" + chunkX + "," + chunkZ + "]";
    }

    public NbtCompound getFeatures() {
        return this.features;
    }
}
