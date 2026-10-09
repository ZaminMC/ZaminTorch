package net.minecraft.nbt;

public class NbtReadLimiter {
    public static final NbtReadLimiter UNLIMITED = new NbtReadLimiter(0L) {
        @Override
        public void read(long bytes) {
        }
    };
    private final long limit;
    private long bytesRead;

    public NbtReadLimiter(long limit) {
        this.limit = limit;
    }

    public void read(long bytes) {
        this.bytesRead += bytes / 8L;
        if (this.bytesRead > this.limit) {
            throw new RuntimeException(
                "Tried to read NBT tag that was too big; tried to allocate: " + this.bytesRead + "bytes where max allowed: " + this.limit
            );
        }
    }
}
