package net.minecraft.world.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldData;
import net.minecraft.world.chunk.storage.ChunkStorage;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.storage.exception.SessionLockException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AlphaWorldStorage implements WorldStorage, PlayerDataStorage {
    private static final Logger LOGGER = LogManager.getLogger();
    private final File dir;
    private final File playerDataDir;
    private final File dataDir;
    private final long startTime = MinecraftServer.getTimeMillis();
    private final String name;

    public AlphaWorldStorage(File savesDir, String name, boolean createPlayerDataDir) {
        this.dir = new File(savesDir, name);
        this.dir.mkdirs();
        this.playerDataDir = new File(this.dir, "playerdata");
        this.dataDir = new File(this.dir, "data");
        this.dataDir.mkdirs();
        this.name = name;
        if (createPlayerDataDir) {
            this.playerDataDir.mkdirs();
        }

        this.writeSessionLock();
    }

    private void writeSessionLock() {
        try {
            File file1 = new File(this.dir, "session.lock");
            DataOutputStream dataoutputstream = new DataOutputStream(new FileOutputStream(file1));

            try {
                dataoutputstream.writeLong(this.startTime);
            } finally {
                dataoutputstream.close();
            }
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
            throw new RuntimeException("Failed to check session lock, aborting");
        }
    }

    @Override
    public File getDirectory() {
        return this.dir;
    }

    @Override
    public void checkSessionLock() throws SessionLockException {
        try {
            File file1 = new File(this.dir, "session.lock");
            DataInputStream datainputstream = new DataInputStream(new FileInputStream(file1));

            try {
                if (datainputstream.readLong() != this.startTime) {
                    throw new SessionLockException("The save is being accessed from another location, aborting");
                }
            } finally {
                datainputstream.close();
            }
        } catch (IOException ioexception) {
            throw new SessionLockException("Failed to check session lock, aborting");
        }
    }

    @Override
    public ChunkStorage getChunkStorage(Dimension dimension) {
        throw new RuntimeException("Old Chunk Storage is no longer supported.");
    }

    @Override
    public WorldData loadData() {
        File file1 = new File(this.dir, "level.dat");
        if (file1.exists()) {
            try {
                NbtCompound nbtcompound2 = NbtIo.readCompressed(new FileInputStream(file1));
                NbtCompound nbtcompound3 = nbtcompound2.getCompound("Data");
                return new WorldData(nbtcompound3);
            } catch (Exception exception1) {
                exception1.printStackTrace();
            }
        }

        file1 = new File(this.dir, "level.dat_old");
        if (file1.exists()) {
            try {
                NbtCompound nbtcompound = NbtIo.readCompressed(new FileInputStream(file1));
                NbtCompound nbtcompound1 = nbtcompound.getCompound("Data");
                return new WorldData(nbtcompound1);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }

        return null;
    }

    @Override
    public void saveData(WorldData data, NbtCompound playerData) {
        NbtCompound nbtcompound = data.toNbt(playerData);
        NbtCompound nbtcompound1 = new NbtCompound();
        nbtcompound1.put("Data", nbtcompound);

        try {
            File file1 = new File(this.dir, "level.dat_new");
            File file2 = new File(this.dir, "level.dat_old");
            File file3 = new File(this.dir, "level.dat");
            NbtIo.writeCompressed(nbtcompound1, new FileOutputStream(file1));
            if (file2.exists()) {
                file2.delete();
            }

            file3.renameTo(file2);
            if (file3.exists()) {
                file3.delete();
            }

            file1.renameTo(file3);
            if (file1.exists()) {
                file1.delete();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void saveData(WorldData data) {
        NbtCompound nbtcompound = data.toNbt();
        NbtCompound nbtcompound1 = new NbtCompound();
        nbtcompound1.put("Data", nbtcompound);

        try {
            File file1 = new File(this.dir, "level.dat_new");
            File file2 = new File(this.dir, "level.dat_old");
            File file3 = new File(this.dir, "level.dat");
            NbtIo.writeCompressed(nbtcompound1, new FileOutputStream(file1));
            if (file2.exists()) {
                file2.delete();
            }

            file3.renameTo(file2);
            if (file3.exists()) {
                file3.delete();
            }

            file1.renameTo(file3);
            if (file1.exists()) {
                file1.delete();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void savePlayerData(PlayerEntity player) {
        try {
            NbtCompound nbtcompound = new NbtCompound();
            player.writeNbtWithoutId(nbtcompound);
            File file1 = new File(this.playerDataDir, player.getUuid().toString() + ".dat.tmp");
            File file2 = new File(this.playerDataDir, player.getUuid().toString() + ".dat");
            NbtIo.writeCompressed(nbtcompound, new FileOutputStream(file1));
            if (file2.exists()) {
                file2.delete();
            }

            file1.renameTo(file2);
        } catch (Exception exception) {
            LOGGER.warn("Failed to save player data for " + player.getName());
        }
    }

    @Override
    public NbtCompound loadPlayerData(PlayerEntity player) {
        NbtCompound nbtcompound = null;

        try {
            File file1 = new File(this.playerDataDir, player.getUuid().toString() + ".dat");
            if (file1.exists() && file1.isFile()) {
                nbtcompound = NbtIo.readCompressed(new FileInputStream(file1));
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load player data for " + player.getName());
        }

        if (nbtcompound != null) {
            player.readNbt(nbtcompound);
        }

        return nbtcompound;
    }

    @Override
    public PlayerDataStorage getPlayerDataStorage() {
        return this;
    }

    @Override
    public String[] getSavedPlayerIds() {
        String[] astring = this.playerDataDir.list();
        if (astring == null) {
            astring = new String[0];
        }

        for (int i = 0; i < astring.length; i++) {
            if (astring[i].endsWith(".dat")) {
                astring[i] = astring[i].substring(0, astring[i].length() - 4);
            }
        }

        return astring;
    }

    @Override
    public void forceSave() {
    }

    @Override
    public File getDataFile(String name) {
        return new File(this.dataDir, name + ".dat");
    }

    @Override
    public String getName() {
        return this.name;
    }
}
