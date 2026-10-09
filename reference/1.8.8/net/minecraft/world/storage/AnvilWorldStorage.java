package net.minecraft.world.storage;

import java.io.File;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.WorldData;
import net.minecraft.world.chunk.storage.AnvilChunkStorage;
import net.minecraft.world.chunk.storage.ChunkStorage;
import net.minecraft.world.chunk.storage.RegionIo;
import net.minecraft.world.chunk.storage.io.ChunkIo;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.dimension.NetherDimension;
import net.minecraft.world.dimension.TheEndDimension;

public class AnvilWorldStorage extends AlphaWorldStorage {
    public AnvilWorldStorage(File file, String string, boolean bl) {
        super(file, string, bl);
    }

    @Override
    public ChunkStorage getChunkStorage(Dimension dimension) {
        File file1 = this.getDirectory();
        if (dimension instanceof NetherDimension) {
            File file3 = new File(file1, "DIM-1");
            file3.mkdirs();
            return new AnvilChunkStorage(file3);
        } else if (dimension instanceof TheEndDimension) {
            File file2 = new File(file1, "DIM1");
            file2.mkdirs();
            return new AnvilChunkStorage(file2);
        } else {
            return new AnvilChunkStorage(file1);
        }
    }

    @Override
    public void saveData(WorldData data, NbtCompound playerData) {
        data.setVersion(19133);
        super.saveData(data, playerData);
    }

    @Override
    public void forceSave() {
        try {
            ChunkIo.getInstance().waitUntilFinished();
        } catch (InterruptedException interruptedexception) {
            interruptedexception.printStackTrace();
        }

        RegionIo.flush();
    }
}
