package net.minecraft.world.chunk.storage;

import com.google.common.collect.Maps;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class RegionIo {
    private static final Map<File, RegionFile> REGION_FILES = Maps.newHashMap();

    /**
     * @return the region file for the specified chunk. If the region file does not
     *         exist in the cache, then it will be created upon invoking this method.
     * @param worldDir the world directory
     * @param chunkX the absolute x-coordinate of the chunk
     * @param chunkZ the absolute z-coordinate of the chunk
     */
    public static synchronized RegionFile getRegionFile(File worldDir, int chunkX, int chunkZ) {
        File file1 = new File(worldDir, "region");
        File file2 = new File(file1, "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
        RegionFile regionfile = REGION_FILES.get(file2);
        if (regionfile != null) {
            return regionfile;
        }

        if (!file1.exists()) {
            file1.mkdirs();
        }

        if (REGION_FILES.size() >= 256) {
            flush();
        }

        RegionFile regionfile1 = new RegionFile(file2);
        REGION_FILES.put(file2, regionfile1);
        return regionfile1;
    }

    /**
     * Closes every region file in the cache, flushing them to the file system.
     */
    public static synchronized void flush() {
        for (RegionFile regionfile : REGION_FILES.values()) {
            try {
                if (regionfile != null) {
                    regionfile.close();
                }
            } catch (IOException ioexception) {
                ioexception.printStackTrace();
            }
        }

        REGION_FILES.clear();
    }

    /**
     * @return an input stream for the specified chunk.
     * @param worldDir the world directory
     * @param chunkX the absolute x-coordinate of the chunk
     * @param chunkZ the absolute z-coordinate of the chunk
     */
    public static DataInputStream getChunkInputStream(File worldDir, int chunkX, int chunkZ) {
        RegionFile regionfile = getRegionFile(worldDir, chunkX, chunkZ);
        return regionfile.getChunkInputStream(chunkX & 31, chunkZ & 31);
    }

    /**
     * @return an output stream for the specified chunk
     * @param worldDir the world directory
     * @param chunkX the absolute x-coordinate for the specified chunk
     * @param chunkZ the absolute z-coordinate for the specified chunk
     */
    public static DataOutputStream getChunkOutputStream(File worldDir, int chunkX, int chunkZ) {
        RegionFile regionfile = getRegionFile(worldDir, chunkX, chunkZ);
        return regionfile.getChunkOutputStream(chunkX & 31, chunkZ & 31);
    }
}
