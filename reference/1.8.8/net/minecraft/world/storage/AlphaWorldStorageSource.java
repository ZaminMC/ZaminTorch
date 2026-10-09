package net.minecraft.world.storage;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.WorldData;
import net.minecraft.world.storage.exception.WorldStorageException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AlphaWorldStorageSource implements WorldStorageSource {
    private static final Logger LOGGER = LogManager.getLogger();
    protected final File dir;

    public AlphaWorldStorageSource(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
        }

        this.dir = dir;
    }

    @Override
    public String getName() {
        return "Old Format";
    }

    @Override
    public List<WorldSaveInfo> getAll() throws WorldStorageException {
        List<WorldSaveInfo> list = Lists.newArrayList();

        for (int i = 0; i < 5; i++) {
            String s = "World" + (i + 1);
            WorldData worlddata = this.getData(s);
            if (worlddata != null) {
                list.add(
                    new WorldSaveInfo(
                        s,
                        "",
                        worlddata.getLastPlayed(),
                        worlddata.getSizeOnDisk(),
                        worlddata.getDefaultGamemode(),
                        false,
                        worlddata.isHardcore(),
                        worlddata.allowCommands()
                    )
                );
            }
        }

        return list;
    }

    @Override
    public void flush() {
    }

    @Override
    public WorldData getData(String saveName) {
        File file1 = new File(this.dir, saveName);
        if (!file1.exists()) {
            return null;
        }

        File file2 = new File(file1, "level.dat");
        if (file2.exists()) {
            try {
                NbtCompound nbtcompound2 = NbtIo.readCompressed(new FileInputStream(file2));
                NbtCompound nbtcompound3 = nbtcompound2.getCompound("Data");
                return new WorldData(nbtcompound3);
            } catch (Exception exception1) {
                LOGGER.error("Exception reading " + file2, exception1);
            }
        }

        file2 = new File(file1, "level.dat_old");
        if (file2.exists()) {
            try {
                NbtCompound nbtcompound = NbtIo.readCompressed(new FileInputStream(file2));
                NbtCompound nbtcompound1 = nbtcompound.getCompound("Data");
                return new WorldData(nbtcompound1);
            } catch (Exception exception) {
                LOGGER.error("Exception reading " + file2, exception);
            }
        }

        return null;
    }

    @Override
    public void rename(String saveName, String newName) {
        File file1 = new File(this.dir, saveName);
        if (file1.exists()) {
            File file2 = new File(file1, "level.dat");
            if (file2.exists()) {
                try {
                    NbtCompound nbtcompound = NbtIo.readCompressed(new FileInputStream(file2));
                    NbtCompound nbtcompound1 = nbtcompound.getCompound("Data");
                    nbtcompound1.putString("LevelName", newName);
                    NbtIo.writeCompressed(nbtcompound, new FileOutputStream(file2));
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean canCreate(String saveName) {
        File file1 = new File(this.dir, saveName);
        if (file1.exists()) {
            return false;
        }

        try {
            file1.mkdir();
            file1.delete();
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Couldn't make new level", throwable);
            return false;
        }
    }

    @Override
    public boolean delete(String saveName) {
        File file1 = new File(this.dir, saveName);
        if (!file1.exists()) {
            return true;
        }

        LOGGER.info("Deleting level " + saveName);

        for (int i = 1; i <= 5; i++) {
            LOGGER.info("Attempt " + i + "...");
            if (deleteFilesAndDirs(file1.listFiles())) {
                break;
            }

            LOGGER.warn("Unsuccessful in deleting contents.");
            if (i < 5) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException interruptedexception) {
                }
            }
        }

        return file1.delete();
    }

    protected static boolean deleteFilesAndDirs(File[] files) {
        for (int i = 0; i < files.length; i++) {
            File file1 = files[i];
            LOGGER.debug("Deleting " + file1);
            if (file1.isDirectory() && !deleteFilesAndDirs(file1.listFiles())) {
                LOGGER.warn("Couldn't delete directory " + file1);
                return false;
            }

            if (!file1.delete()) {
                LOGGER.warn("Couldn't delete file " + file1);
                return false;
            }
        }

        return true;
    }

    @Override
    public WorldStorage get(String saveName, boolean createPlayerDataDir) {
        return new AlphaWorldStorage(this.dir, saveName, createPlayerDataDir);
    }

    @Override
    public boolean isConvertible(String saveName) {
        return false;
    }

    @Override
    public boolean needsConversion(String saveName) {
        return false;
    }

    @Override
    public boolean convert(String saveName, ProgressListener listener) {
        return false;
    }

    @Override
    public boolean exists(String saveName) {
        File file1 = new File(this.dir, saveName);
        return file1.isDirectory();
    }
}
