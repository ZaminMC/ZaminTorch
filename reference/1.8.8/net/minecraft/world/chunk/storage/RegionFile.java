package net.minecraft.world.chunk.storage;

import com.google.common.collect.Lists;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.server.MinecraftServer;

public class RegionFile {
    /**
     * Unused buffer to store a single 4KB block of memory.
     */
    private static final byte[] BLOCK_BUFFER = new byte[4096];
    private final File file;
    private RandomAccessFile randomAccessFile;
    /**
     * Contains the 4KB block offset and number of blocks that each chunk occupies. If
     * a chunk does not store any data, then the array entry contains zero. The format
     * of a non-zero entry in the array is as follows:
     * <ul>
     *     <li>bits[0:7] , the number of blocks that the chunk data occupies</li>
     *     <li>bits[8:31], the block offset of the first byte in the chunk data</li>
     * </ul>
     * The above is specified in lsb first. This array is also written to the region
     * file starting at byte 0.
     * <br><br>
     * Note: A block in this context should be understood as 4096 continous bytes in
     * the region file.
     */
    private final int[] chunkBlockInfo = new int[1024];
    /**
     * Contains the times for the latest modification to each chunk. This array
     * is also written to the region file starting at byte 4096 (block offset 1).
     */
    private final int[] chunkSaveTimes = new int[1024];
    /**
     * A list containing flags for each of the 4KB blocks in the file indicating whether
     * it should be considered empty or occupied. In particular, if a value at index
     * {@code blockOffset} is {@code true} then it is empty, and otherwise occupied.
     */
    private List<Boolean> blockEmptyFlags;
    /**
     * The total number of bytes written to the region file, since this {@code RegionFile}
     * object was instantiated. Note that this is always a multiple of 4096 (the size
     * of a single 4KB block).
     */
    private int bytesWritten;
    private long lastModifiedTime;

    public RegionFile(File file) {
        this.file = file;
        this.bytesWritten = 0;

        try {
            if (file.exists()) {
                this.lastModifiedTime = file.lastModified();
            }

            this.randomAccessFile = new RandomAccessFile(file, "rw");
            if (this.randomAccessFile.length() < 4096L) {
                for (int i = 0; i < 1024; i++) {
                    this.randomAccessFile.writeInt(0);
                }

                for (int i1 = 0; i1 < 1024; i1++) {
                    this.randomAccessFile.writeInt(0);
                }

                this.bytesWritten += 8192;
            }

            if ((this.randomAccessFile.length() & 4095L) != 0L) {
                for (int j1 = 0; j1 < (this.randomAccessFile.length() & 4095L); j1++) {
                    this.randomAccessFile.write(0);
                }
            }

            int k1 = (int)this.randomAccessFile.length() / 4096;
            this.blockEmptyFlags = Lists.newArrayListWithCapacity(k1);

            for (int j = 0; j < k1; j++) {
                this.blockEmptyFlags.add(true);
            }

            this.blockEmptyFlags.set(0, false);
            this.blockEmptyFlags.set(1, false);
            this.randomAccessFile.seek(0L);

            for (int l1 = 0; l1 < 1024; l1++) {
                int k = this.randomAccessFile.readInt();
                this.chunkBlockInfo[l1] = k;
                if (k != 0 && (k >> 8) + (k & 0xFF) <= this.blockEmptyFlags.size()) {
                    for (int l = 0; l < (k & 0xFF); l++) {
                        this.blockEmptyFlags.set((k >> 8) + l, false);
                    }
                }
            }

            for (int i2 = 0; i2 < 1024; i2++) {
                int j2 = this.randomAccessFile.readInt();
                this.chunkSaveTimes[i2] = j2;
            }
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
        }
    }

    public synchronized DataInputStream getChunkInputStream(int chunkX, int chunkZ) {
        if (this.isOutsideRegion(chunkX, chunkZ)) {
            return null;
        }

        try {
            int i = this.getChunkBlockInfo(chunkX, chunkZ);
            if (i == 0) {
                return null;
            } else {
                int j = i >> 8;
                int k = i & 0xFF;
                if (j + k > this.blockEmptyFlags.size()) {
                    return null;
                } else {
                    this.randomAccessFile.seek(j * 4096);
                    int l = this.randomAccessFile.readInt();
                    if (l > 4096 * k) {
                        return null;
                    } else if (l <= 0) {
                        return null;
                    } else {
                        byte b0 = this.randomAccessFile.readByte();
                        if (b0 == 1) {
                            byte[] abyte1 = new byte[l - 1];
                            this.randomAccessFile.read(abyte1);
                            return new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(abyte1))));
                        } else if (b0 == 2) {
                            byte[] abyte = new byte[l - 1];
                            this.randomAccessFile.read(abyte);
                            return new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(abyte))));
                        } else {
                            return null;
                        }
                    }
                }
            }
        } catch (IOException ioexception) {
            return null;
        }
    }

    public DataOutputStream getChunkOutputStream(int chunkX, int chunkZ) {
        return this.isOutsideRegion(chunkX, chunkZ) ? null : new DataOutputStream(new DeflaterOutputStream(new RegionFile.ChunkOutputStream(chunkX, chunkZ)));
    }

    /**
     * Writes the chunk data at the given coordinates to the region file. Any
     * previous chunk data at the same coordinates will be discarded.
     * @param chunkX the relative x-coordinate of the chunk inside the region
     * @param chunkZ the relative z-coordinate of the chunk inside the region
     * @param data the chunk data
     * @param size the number of bytes in {@code data} that should be written
     */
    protected synchronized void writeChunkData(int chunkX, int chunkZ, byte[] data, int size) {
        try {
            int i = this.getChunkBlockInfo(chunkX, chunkZ);
            int j = i >> 8;
            int k = i & 0xFF;
            int l = (size + 5) / 4096 + 1;
            if (l >= 256) {
                return;
            }

            if (j != 0 && k == l) {
                this.writeChunkData(j, data, size);
            } else {
                for (int i1 = 0; i1 < k; i1++) {
                    this.blockEmptyFlags.set(j + i1, true);
                }

                int l1 = this.blockEmptyFlags.indexOf(true);
                int j1 = 0;
                if (l1 != -1) {
                    for (int k1 = l1; k1 < this.blockEmptyFlags.size(); k1++) {
                        if (j1 != 0) {
                            if (this.blockEmptyFlags.get(k1)) {
                                j1++;
                            } else {
                                j1 = 0;
                            }
                        } else if (this.blockEmptyFlags.get(k1)) {
                            l1 = k1;
                            j1 = 1;
                        }

                        if (j1 >= l) {
                            break;
                        }
                    }
                }

                if (j1 >= l) {
                    j = l1;
                    this.writeChunkBlockInfo(chunkX, chunkZ, j << 8 | l);

                    for (int j2 = 0; j2 < l; j2++) {
                        this.blockEmptyFlags.set(j + j2, false);
                    }

                    this.writeChunkData(j, data, size);
                } else {
                    this.randomAccessFile.seek(this.randomAccessFile.length());
                    j = this.blockEmptyFlags.size();

                    for (int i2 = 0; i2 < l; i2++) {
                        this.randomAccessFile.write(BLOCK_BUFFER);
                        this.blockEmptyFlags.add(false);
                    }

                    this.bytesWritten += 4096 * l;
                    this.writeChunkData(j, data, size);
                    this.writeChunkBlockInfo(chunkX, chunkZ, j << 8 | l);
                }
            }

            this.writeChunkSaveTime(chunkX, chunkZ, (int)(MinecraftServer.getTimeMillis() / 1000L));
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
        }
    }

    /**
     * Writes the given chunk data to the specified block offset.
     * @param blockOffset the offset of the first 4KB block where the chunk data
     *                    should be written.
     * @param data the chunk data
     * @param size the number of bytes that should be written
     */
    private void writeChunkData(int blockOffset, byte[] data, int size) throws IOException {
        this.randomAccessFile.seek(blockOffset * 4096);
        this.randomAccessFile.writeInt(size + 1);
        this.randomAccessFile.writeByte(2);
        this.randomAccessFile.write(data, 0, size);
    }

    private boolean isOutsideRegion(int chunkX, int chunkZ) {
        return chunkX < 0 || chunkX >= 32 || chunkZ < 0 || chunkZ >= 32;
    }

    /**
     * @return The chunk block info for the chunk at the specified location. See
     *         {@link #chunkBlockInfo} for information about the format of the
     *         block info.
     * @param chunkX the relative x-coordinate of the chunk inside the region
     * @param chunkZ the relative z-coordinate of the chunk inside the region
     */
    private int getChunkBlockInfo(int chunkX, int chunkZ) {
        return this.chunkBlockInfo[chunkX + chunkZ * 32];
    }

    /**
     * @return True, if this region file contains chunk data for the specified
     *         coordinates, false otherwise.
     * @param chunkX the relative x-coordinate of the chunk inside the region
     * @param chunkZ the relative z-coordinate of the chunk inside the region
     */
    public boolean hasChunkData(int chunkX, int chunkZ) {
        return this.getChunkBlockInfo(chunkX, chunkZ) != 0;
    }

    /**
     * Writes the given chunk block info to the region file. See {@link #chunkBlockInfo}
     * for information about the format of the block info.
     * @param chunkX the x-coordinate of the chunk inside the region
     * @param chunkZ the z-coordinate of the chunk inside the region
     * @param blockInfo the block info for the specified chunk
     */
    private void writeChunkBlockInfo(int chunkX, int chunkZ, int blockInfo) throws IOException {
        this.chunkBlockInfo[chunkX + chunkZ * 32] = blockInfo;
        this.randomAccessFile.seek((chunkX + chunkZ * 32) * 4);
        this.randomAccessFile.writeInt(blockInfo);
    }

    /**
     * Writes the given chunk data save time to the region file.
     * @param chunkX the relative x-coordinate of the chunk inside the region
     * @param chunkZ the relative z-coordinate of the chunk inside the region
     * @param timeSeconds the time in seconds since midnight, January 1, 1970 UTC
     */
    private void writeChunkSaveTime(int chunkX, int chunkZ, int timeSeconds) throws IOException {
        this.chunkSaveTimes[chunkX + chunkZ * 32] = timeSeconds;
        this.randomAccessFile.seek(4096 + (chunkX + chunkZ * 32) * 4);
        this.randomAccessFile.writeInt(timeSeconds);
    }

    /**
     * Flushes and closes the region file
     */
    public void close() throws IOException {
        if (this.randomAccessFile != null) {
            this.randomAccessFile.close();
        }
    }

    class ChunkOutputStream extends ByteArrayOutputStream {
        private int chunkX;
        private int chunkZ;

        public ChunkOutputStream(int chunkX, int chunkZ) {
            super(8096);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public void close() {
            RegionFile.this.writeChunkData(this.chunkX, this.chunkZ, this.buf, this.count);
        }
    }
}
