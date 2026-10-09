package net.minecraft.entity.data;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Rotation;
import org.apache.commons.lang3.ObjectUtils;

public class SyncedData {
    private final Entity entity;
    private boolean empty = true;
    private static final Map<Class<?>, Integer> DATA_TYPES = Maps.newHashMap();
    private final Map<Integer, SyncedData.Entry> entries = Maps.newHashMap();
    private boolean dirty;
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    public SyncedData(Entity entity) {
        this.entity = entity;
    }

    public <T> void register(int id, T value) {
        Integer integer = DATA_TYPES.get(value.getClass());
        if (integer == null) {
            throw new IllegalArgumentException("Unknown data type: " + value.getClass());
        }

        if (id > 31) {
            throw new IllegalArgumentException("Data value id is too big with " + id + "! (Max is " + 31 + ")");
        }

        if (this.entries.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate id value for " + id + "!");
        }

        SyncedData.Entry synceddata$entry = new SyncedData.Entry(integer, id, value);
        this.lock.writeLock().lock();
        this.entries.put(id, synceddata$entry);
        this.lock.writeLock().unlock();
        this.empty = false;
    }

    public void add(int type, int id) {
        SyncedData.Entry synceddata$entry = new SyncedData.Entry(id, type, null);
        this.lock.writeLock().lock();
        this.entries.put(type, synceddata$entry);
        this.lock.writeLock().unlock();
        this.empty = false;
    }

    public byte getByte(int id) {
        return (Byte)this.getEntry(id).getValue();
    }

    public short getShort(int id) {
        return (Short)this.getEntry(id).getValue();
    }

    public int getInt(int id) {
        return (Integer)this.getEntry(id).getValue();
    }

    public float getFloat(int id) {
        return (Float)this.getEntry(id).getValue();
    }

    public String getString(int id) {
        return (String)this.getEntry(id).getValue();
    }

    public ItemStack getItem(int id) {
        return (ItemStack)this.getEntry(id).getValue();
    }

    private SyncedData.Entry getEntry(int id) {
        this.lock.readLock().lock();

        SyncedData.Entry synceddata$entry;
        try {
            synceddata$entry = this.entries.get(id);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Getting synched entity data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Synched entity data");
            crashreportcategory.add("Data ID", id);
            throw new CrashException(crashreport);
        }

        this.lock.readLock().unlock();
        return synceddata$entry;
    }

    public Rotation getRotation(int id) {
        return (Rotation)this.getEntry(id).getValue();
    }

    public <T> void update(int id, T value) {
        SyncedData.Entry synceddata$entry = this.getEntry(id);
        if (ObjectUtils.notEqual(value, synceddata$entry.getValue())) {
            synceddata$entry.setValue(value);
            this.entity.onDataValueChanged(id);
            synceddata$entry.setDirty(true);
            this.dirty = true;
        }
    }

    public void markDirty(int id) {
        this.getEntry(id).dirty = true;
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public static void write(List<SyncedData.Entry> entries, PacketByteBuf buffer) throws IOException {
        if (entries != null) {
            for (SyncedData.Entry synceddata$entry : entries) {
                writeEntry(buffer, synceddata$entry);
            }
        }

        buffer.writeByte(127);
    }

    public List<SyncedData.Entry> collectDirty() {
        List<SyncedData.Entry> list = null;
        if (this.dirty) {
            this.lock.readLock().lock();

            for (SyncedData.Entry synceddata$entry : this.entries.values()) {
                if (synceddata$entry.isDirty()) {
                    synceddata$entry.setDirty(false);
                    if (list == null) {
                        list = Lists.newArrayList();
                    }

                    list.add(synceddata$entry);
                }
            }

            this.lock.readLock().unlock();
        }

        this.dirty = false;
        return list;
    }

    public void write(PacketByteBuf buffer) throws IOException {
        this.lock.readLock().lock();

        for (SyncedData.Entry synceddata$entry : this.entries.values()) {
            writeEntry(buffer, synceddata$entry);
        }

        this.lock.readLock().unlock();
        buffer.writeByte(127);
    }

    public List<SyncedData.Entry> getAll() {
        List<SyncedData.Entry> list = null;
        this.lock.readLock().lock();

        for (SyncedData.Entry synceddata$entry : this.entries.values()) {
            if (list == null) {
                list = Lists.newArrayList();
            }

            list.add(synceddata$entry);
        }

        this.lock.readLock().unlock();
        return list;
    }

    private static void writeEntry(PacketByteBuf buffer, SyncedData.Entry entry) throws IOException {
        int i = (entry.getType() << 5 | entry.getId() & 31) & 0xFF;
        buffer.writeByte(i);
        switch (entry.getType()) {
            case 0:
                buffer.writeByte((Byte)entry.getValue());
                break;
            case 1:
                buffer.writeShort((Short)entry.getValue());
                break;
            case 2:
                buffer.writeInt((Integer)entry.getValue());
                break;
            case 3:
                buffer.writeFloat((Float)entry.getValue());
                break;
            case 4:
                buffer.writeString((String)entry.getValue());
                break;
            case 5:
                ItemStack itemstack = (ItemStack)entry.getValue();
                buffer.writeItem(itemstack);
                break;
            case 6:
                BlockPos blockpos = (BlockPos)entry.getValue();
                buffer.writeInt(blockpos.getX());
                buffer.writeInt(blockpos.getY());
                buffer.writeInt(blockpos.getZ());
                break;
            case 7:
                Rotation rotation = (Rotation)entry.getValue();
                buffer.writeFloat(rotation.getPitch());
                buffer.writeFloat(rotation.getYaw());
                buffer.writeFloat(rotation.getRoll());
        }
    }

    public static List<SyncedData.Entry> read(PacketByteBuf buffer) throws IOException {
        List<SyncedData.Entry> list = null;

        for (int i = buffer.readByte(); i != 127; i = buffer.readByte()) {
            if (list == null) {
                list = Lists.newArrayList();
            }

            int j = (i & 224) >> 5;
            int k = i & 31;
            SyncedData.Entry synceddata$entry = null;
            switch (j) {
                case 0:
                    synceddata$entry = new SyncedData.Entry(j, k, buffer.readByte());
                    break;
                case 1:
                    synceddata$entry = new SyncedData.Entry(j, k, buffer.readShort());
                    break;
                case 2:
                    synceddata$entry = new SyncedData.Entry(j, k, buffer.readInt());
                    break;
                case 3:
                    synceddata$entry = new SyncedData.Entry(j, k, buffer.readFloat());
                    break;
                case 4:
                    synceddata$entry = new SyncedData.Entry(j, k, buffer.readString(32767));
                    break;
                case 5:
                    synceddata$entry = new SyncedData.Entry(j, k, buffer.readItem());
                    break;
                case 6:
                    int l = buffer.readInt();
                    int i1 = buffer.readInt();
                    int j1 = buffer.readInt();
                    synceddata$entry = new SyncedData.Entry(j, k, new BlockPos(l, i1, j1));
                    break;
                case 7:
                    float f = buffer.readFloat();
                    float f1 = buffer.readFloat();
                    float f2 = buffer.readFloat();
                    synceddata$entry = new SyncedData.Entry(j, k, new Rotation(f, f1, f2));
            }

            list.add(synceddata$entry);
        }

        return list;
    }

    public void update(List<SyncedData.Entry> entries) {
        this.lock.writeLock().lock();

        for (SyncedData.Entry synceddata$entry : entries) {
            SyncedData.Entry synceddata$entry1 = this.entries.get(synceddata$entry.getId());
            if (synceddata$entry1 != null) {
                synceddata$entry1.setValue(synceddata$entry.getValue());
                this.entity.onDataValueChanged(synceddata$entry.getId());
            }
        }

        this.lock.writeLock().unlock();
        this.dirty = true;
    }

    public boolean isEmpty() {
        return this.empty;
    }

    public void markClean() {
        this.dirty = false;
    }

    static {
        DATA_TYPES.put(Byte.class, 0);
        DATA_TYPES.put(Short.class, 1);
        DATA_TYPES.put(Integer.class, 2);
        DATA_TYPES.put(Float.class, 3);
        DATA_TYPES.put(String.class, 4);
        DATA_TYPES.put(ItemStack.class, 5);
        DATA_TYPES.put(BlockPos.class, 6);
        DATA_TYPES.put(Rotation.class, 7);
    }

    public static class Entry {
        private final int type;
        private final int id;
        private Object value;
        private boolean dirty;

        public Entry(int type, int id, Object value) {
            this.id = id;
            this.value = value;
            this.type = type;
            this.dirty = true;
        }

        public int getId() {
            return this.id;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return this.value;
        }

        public int getType() {
            return this.type;
        }

        public boolean isDirty() {
            return this.dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }
    }
}
