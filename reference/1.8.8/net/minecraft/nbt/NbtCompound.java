package net.minecraft.nbt;

import com.google.common.collect.Maps;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;

public class NbtCompound extends NbtElement {
    private Map<String, NbtElement> elements = Maps.newHashMap();

    @Override
    void write(DataOutput output) throws IOException {
        for (String s : this.elements.keySet()) {
            NbtElement nbtelement = this.elements.get(s);
            writeElement(s, nbtelement, output);
        }

        output.writeByte(0);
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(384L);
        if (depth > 512) {
            throw new RuntimeException("Tried to read NBT tag with too high complexity, depth > 512");
        }

        this.elements.clear();

        byte b0;
        while ((b0 = readByte(input, limiter)) != 0) {
            String s = readString(input, limiter);
            limiter.read(224 + 16 * s.length());
            NbtElement nbtelement = createElement(b0, s, input, depth + 1, limiter);
            if (this.elements.put(s, nbtelement) != null) {
                limiter.read(288L);
            }
        }
    }

    public Set<String> getKeys() {
        return this.elements.keySet();
    }

    @Override
    public byte getType() {
        return 10;
    }

    public void put(String key, NbtElement element) {
        this.elements.put(key, element);
    }

    public void putByte(String key, byte value) {
        this.elements.put(key, new NbtByte(value));
    }

    public void putShort(String key, short value) {
        this.elements.put(key, new NbtShort(value));
    }

    public void putInt(String key, int value) {
        this.elements.put(key, new NbtInt(value));
    }

    public void putLong(String key, long value) {
        this.elements.put(key, new NbtLong(value));
    }

    public void putFloat(String key, float value) {
        this.elements.put(key, new NbtFloat(value));
    }

    public void putDouble(String key, double value) {
        this.elements.put(key, new NbtDouble(value));
    }

    public void putString(String key, String value) {
        this.elements.put(key, new NbtString(value));
    }

    public void putByteArray(String key, byte[] value) {
        this.elements.put(key, new NbtByteArray(value));
    }

    public void putIntArray(String key, int[] value) {
        this.elements.put(key, new NbtIntArray(value));
    }

    public void putBoolean(String key, boolean value) {
        this.putByte(key, (byte)(value ? 1 : 0));
    }

    public NbtElement get(String key) {
        return this.elements.get(key);
    }

    public byte getType(String key) {
        NbtElement nbtelement = this.elements.get(key);
        return nbtelement != null ? nbtelement.getType() : 0;
    }

    public boolean contains(String key) {
        return this.elements.containsKey(key);
    }

    public boolean contains(String key, int type) {
        int i = this.getType(key);
        if (i == type) {
            return true;
        }

        if (type != 99) {
            if (i > 0) {
            }

            return false;
        } else {
            return i == 1 || i == 2 || i == 3 || i == 4 || i == 5 || i == 6;
        }
    }

    public byte getByte(String key) {
        try {
            return !this.contains(key, 99) ? 0 : ((NbtElement.Number)this.elements.get(key)).getByte();
        } catch (ClassCastException classcastexception) {
            return 0;
        }
    }

    public short getShort(String key) {
        try {
            return !this.contains(key, 99) ? 0 : ((NbtElement.Number)this.elements.get(key)).getShort();
        } catch (ClassCastException classcastexception) {
            return 0;
        }
    }

    public int getInt(String key) {
        try {
            return !this.contains(key, 99) ? 0 : ((NbtElement.Number)this.elements.get(key)).getInt();
        } catch (ClassCastException classcastexception) {
            return 0;
        }
    }

    public long getLong(String key) {
        try {
            return !this.contains(key, 99) ? 0L : ((NbtElement.Number)this.elements.get(key)).getLong();
        } catch (ClassCastException classcastexception) {
            return 0L;
        }
    }

    public float getFloat(String key) {
        try {
            return !this.contains(key, 99) ? 0.0F : ((NbtElement.Number)this.elements.get(key)).getFloat();
        } catch (ClassCastException classcastexception) {
            return 0.0F;
        }
    }

    public double getDouble(String key) {
        try {
            return !this.contains(key, 99) ? 0.0 : ((NbtElement.Number)this.elements.get(key)).getDouble();
        } catch (ClassCastException classcastexception) {
            return 0.0;
        }
    }

    public String getString(String key) {
        try {
            return !this.contains(key, 8) ? "" : this.elements.get(key).asString();
        } catch (ClassCastException classcastexception) {
            return "";
        }
    }

    public byte[] getByteArray(String key) {
        try {
            return !this.contains(key, 7) ? new byte[0] : ((NbtByteArray)this.elements.get(key)).getByteArray();
        } catch (ClassCastException classcastexception) {
            throw new CrashException(this.createCrashReport(key, 7, classcastexception));
        }
    }

    public int[] getIntArray(String key) {
        try {
            return !this.contains(key, 11) ? new int[0] : ((NbtIntArray)this.elements.get(key)).getIntArray();
        } catch (ClassCastException classcastexception) {
            throw new CrashException(this.createCrashReport(key, 11, classcastexception));
        }
    }

    public NbtCompound getCompound(String key) {
        try {
            return !this.contains(key, 10) ? new NbtCompound() : (NbtCompound)this.elements.get(key);
        } catch (ClassCastException classcastexception) {
            throw new CrashException(this.createCrashReport(key, 10, classcastexception));
        }
    }

    public NbtList getList(String key, int type) {
        try {
            if (this.getType(key) != 9) {
                return new NbtList();
            }

            NbtList nbtlist = (NbtList)this.elements.get(key);
            return nbtlist.size() > 0 && nbtlist.getElementType() != type ? new NbtList() : nbtlist;
        } catch (ClassCastException classcastexception) {
            throw new CrashException(this.createCrashReport(key, 9, classcastexception));
        }
    }

    public boolean getBoolean(String key) {
        return this.getByte(key) != 0;
    }

    public void remove(String key) {
        this.elements.remove(key);
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder("{");

        for (Entry<String, NbtElement> entry : this.elements.entrySet()) {
            if (stringbuilder.length() != 1) {
                stringbuilder.append(',');
            }

            stringbuilder.append(entry.getKey()).append(':').append(entry.getValue());
        }

        return stringbuilder.append('}').toString();
    }

    @Override
    public boolean isEmpty() {
        return this.elements.isEmpty();
    }

    private CrashReport createCrashReport(String key, int expectedType, ClassCastException exception) {
        CrashReport crashreport = CrashReport.of(exception, "Reading NBT data");
        CrashReportCategory crashreportcategory = crashreport.addCategory("Corrupt NBT tag", 1);
        crashreportcategory.add("Tag type found", new Callable<String>() {
            public String call() throws Exception {
                return NbtElement.TYPE_NAMES[NbtCompound.this.elements.get(key).getType()];
            }
        });
        crashreportcategory.add("Tag type expected", new Callable<String>() {
            public String call() throws Exception {
                return NbtElement.TYPE_NAMES[expectedType];
            }
        });
        crashreportcategory.add("Tag name", key);
        return crashreport;
    }

    @Override
    public NbtElement copy() {
        NbtCompound nbtcompound = new NbtCompound();

        for (String s : this.elements.keySet()) {
            nbtcompound.put(s, this.elements.get(s).copy());
        }

        return nbtcompound;
    }

    @Override
    public boolean equals(Object object) {
        if (super.equals(object)) {
            NbtCompound nbtcompound = (NbtCompound)object;
            return this.elements.entrySet().equals(nbtcompound.elements.entrySet());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ this.elements.hashCode();
    }

    private static void writeElement(String key, NbtElement element, DataOutput output) throws IOException {
        output.writeByte(element.getType());
        if (element.getType() != 0) {
            output.writeUTF(key);
            element.write(output);
        }
    }

    private static byte readByte(DataInput input, NbtReadLimiter limiter) throws IOException {
        return input.readByte();
    }

    private static String readString(DataInput input, NbtReadLimiter limiter) throws IOException {
        return input.readUTF();
    }

    static NbtElement createElement(byte type, String key, DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        NbtElement nbtelement = NbtElement.create(type);

        try {
            nbtelement.read(input, depth, limiter);
            return nbtelement;
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.of(ioexception, "Loading NBT data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("NBT Tag");
            crashreportcategory.add("Tag name", key);
            crashreportcategory.add("Tag type", type);
            throw new CrashException(crashreport);
        }
    }

    public void merge(NbtCompound nbt) {
        for (String s : nbt.elements.keySet()) {
            NbtElement nbtelement = nbt.elements.get(s);
            if (nbtelement.getType() == 10) {
                if (this.contains(s, 10)) {
                    NbtCompound nbtcompound = this.getCompound(s);
                    nbtcompound.merge((NbtCompound)nbtelement);
                } else {
                    this.put(s, nbtelement.copy());
                }
            } else {
                this.put(s, nbtelement.copy());
            }
        }
    }
}
