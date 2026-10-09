package net.minecraft.world.storage;

import com.google.common.collect.Lists;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.WorldData;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.chunk.storage.RegionChunkConverter;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionIo;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.storage.exception.WorldStorageException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AnvilWorldStorageSource extends AlphaWorldStorageSource {
    private static final Logger LOGGER = LogManager.getLogger();

    public AnvilWorldStorageSource(File file) {
        super(file);
    }

    @Override
    public String getName() {
        return "Anvil";
    }

    @Override
    public List<WorldSaveInfo> getAll() throws WorldStorageException {
        if (this.dir != null && this.dir.exists() && this.dir.isDirectory()) {
            List<WorldSaveInfo> list = Lists.newArrayList();
            File[] afile = this.dir.listFiles();

            for (File file1 : afile) {
                if (file1.isDirectory()) {
                    String s = file1.getName();
                    WorldData worlddata = this.getData(s);
                    if (worlddata != null && (worlddata.getVersion() == 19132 || worlddata.getVersion() == 19133)) {
                        boolean flag = worlddata.getVersion() != this.getVersion();
                        String s1 = worlddata.getName();
                        if (StringUtils.isEmpty(s1)) {
                            s1 = s;
                        }

                        long i = 0L;
                        list.add(
                            new WorldSaveInfo(
                                s, s1, worlddata.getLastPlayed(), i, worlddata.getDefaultGamemode(), flag, worlddata.isHardcore(), worlddata.allowCommands()
                            )
                        );
                    }
                }
            }

            return list;
        } else {
            throw new WorldStorageException("Unable to read or access folder where game worlds are saved!");
        }
    }

    protected int getVersion() {
        return 19133;
    }

    @Override
    public void flush() {
        RegionIo.flush();
    }

    @Override
    public WorldStorage get(String saveName, boolean createPlayerDataDir) {
        return new AnvilWorldStorage(this.dir, saveName, createPlayerDataDir);
    }

    @Override
    public boolean isConvertible(String saveName) {
        WorldData worlddata = this.getData(saveName);
        return worlddata != null && worlddata.getVersion() == 19132;
    }

    @Override
    public boolean needsConversion(String saveName) {
        WorldData worlddata = this.getData(saveName);
        return worlddata != null && worlddata.getVersion() != this.getVersion();
    }

    @Override
    public boolean convert(String saveName, ProgressListener listener) {
        listener.progressStagePercentage(0);
        List<File> list = Lists.newArrayList();
        List<File> list1 = Lists.newArrayList();
        List<File> list2 = Lists.newArrayList();
        File file1 = new File(this.dir, saveName);
        File file2 = new File(file1, "DIM-1");
        File file3 = new File(file1, "DIM1");
        LOGGER.info("Scanning folders...");
        this.collectRegionFiles(file1, list);
        if (file2.exists()) {
            this.collectRegionFiles(file2, list1);
        }

        if (file3.exists()) {
            this.collectRegionFiles(file3, list2);
        }

        int i = list.size() + list1.size() + list2.size();
        LOGGER.info("Total conversion count is " + i);
        WorldData worlddata = this.getData(saveName);
        BiomeSource biomesource = null;
        if (worlddata.getGeneratorType() == WorldGeneratorType.FLAT) {
            biomesource = new FixedBiomeSource(Biome.PLAINS, 0.5F);
        } else {
            biomesource = new BiomeSource(worlddata.getSeed(), worlddata.getGeneratorType(), worlddata.getGeneratorOptions());
        }

        this.convertRegionsToAnvil(new File(file1, "region"), list, biomesource, 0, i, listener);
        this.convertRegionsToAnvil(new File(file2, "region"), list1, new FixedBiomeSource(Biome.HELL, 0.0F), list.size(), i, listener);
        this.convertRegionsToAnvil(new File(file3, "region"), list2, new FixedBiomeSource(Biome.THE_END, 0.0F), list.size() + list1.size(), i, listener);
        worlddata.setVersion(19133);
        if (worlddata.getGeneratorType() == WorldGeneratorType.DEFAULT_1_1) {
            worlddata.setGeneratorType(WorldGeneratorType.DEFAULT);
        }

        this.createRegionDataBackup(saveName);
        WorldStorage worldstorage = this.get(saveName, false);
        worldstorage.saveData(worlddata);
        return true;
    }

    private void createRegionDataBackup(String saveName) {
        File file1 = new File(this.dir, saveName);
        if (!file1.exists()) {
            LOGGER.warn("Unable to create level.dat_mcr backup");
        } else {
            File file2 = new File(file1, "level.dat");
            if (!file2.exists()) {
                LOGGER.warn("Unable to create level.dat_mcr backup");
            } else {
                File file3 = new File(file1, "level.dat_mcr");
                if (!file2.renameTo(file3)) {
                    LOGGER.warn("Unable to create level.dat_mcr backup");
                }
            }
        }
    }

    private void convertRegionsToAnvil(File dir, Iterable<File> regionFiles, BiomeSource biomeSource, int index, int total, ProgressListener listener) {
        for (File file1 : regionFiles) {
            this.convertRegionToAnvil(dir, file1, biomeSource, index, total, listener);
            index++;
            int i = (int)Math.round(100.0 * index / total);
            listener.progressStagePercentage(i);
        }
    }

    private void convertRegionToAnvil(File dir, File regionFile, BiomeSource biomeSource, int index, int total, ProgressListener listener) {
        try {
            String s = regionFile.getName();
            RegionFile regionfile = new RegionFile(regionFile);
            RegionFile regionfile1 = new RegionFile(new File(dir, s.substring(0, s.length() - ".mcr".length()) + ".mca"));

            for (int i = 0; i < 32; i++) {
                for (int j = 0; j < 32; j++) {
                    if (regionfile.hasChunkData(i, j) && !regionfile1.hasChunkData(i, j)) {
                        DataInputStream datainputstream = regionfile.getChunkInputStream(i, j);
                        if (datainputstream == null) {
                            LOGGER.warn("Failed to fetch input stream");
                        } else {
                            NbtCompound nbtcompound = NbtIo.read(datainputstream);
                            datainputstream.close();
                            NbtCompound nbtcompound1 = nbtcompound.getCompound("Level");
                            RegionChunkConverter.RegionChunk regionchunkconverter$regionchunk = RegionChunkConverter.loadChunk(nbtcompound1);
                            NbtCompound nbtcompound2 = new NbtCompound();
                            NbtCompound nbtcompound3 = new NbtCompound();
                            nbtcompound2.put("Level", nbtcompound3);
                            RegionChunkConverter.convertChunkToAnvil(regionchunkconverter$regionchunk, nbtcompound3, biomeSource);
                            DataOutputStream dataoutputstream = regionfile1.getChunkOutputStream(i, j);
                            NbtIo.write(nbtcompound2, dataoutputstream);
                            dataoutputstream.close();
                        }
                    }
                }

                int k = (int)Math.round(100.0 * (index * 1024) / (total * 1024));
                int l = (int)Math.round(100.0 * ((i + 1) * 32 + index * 1024) / (total * 1024));
                if (l > k) {
                    listener.progressStagePercentage(l);
                }
            }

            regionfile.close();
            regionfile1.close();
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
        }
    }

    private void collectRegionFiles(File dir, Collection<File> regionFiles) {
        File file1 = new File(dir, "region");
        File[] afile = file1.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".mcr");
            }
        });
        if (afile != null) {
            Collections.addAll(regionFiles, afile);
        }
    }
}
