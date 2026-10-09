package net.minecraft.nbt;

import com.google.common.collect.Lists;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NbtList extends NbtElement {
    private static final Logger LOGGER = LogManager.getLogger();
    private List<NbtElement> elements = Lists.newArrayList();
    private byte type = 0;

    @Override
    void write(DataOutput output) throws IOException {
        if (!this.elements.isEmpty()) {
            this.type = this.elements.get(0).getType();
        } else {
            this.type = 0;
        }

        output.writeByte(this.type);
        output.writeInt(this.elements.size());

        for (int i = 0; i < this.elements.size(); i++) {
            this.elements.get(i).write(output);
        }
    }

    @Override
    void read(DataInput input, int depth, NbtReadLimiter limiter) throws IOException {
        limiter.read(296L);
        if (depth > 512) {
            throw new RuntimeException("Tried to read NBT tag with too high complexity, depth > 512");
        }

        this.type = input.readByte();
        int i = input.readInt();
        if (this.type == 0 && i > 0) {
            throw new RuntimeException("Missing type on ListTag");
        }

        limiter.read(32L * i);
        this.elements = Lists.newArrayListWithCapacity(i);

        for (int j = 0; j < i; j++) {
            NbtElement nbtelement = NbtElement.create(this.type);
            nbtelement.read(input, depth + 1, limiter);
            this.elements.add(nbtelement);
        }
    }

    @Override
    public byte getType() {
        return 9;
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder("[");

        for (int i = 0; i < this.elements.size(); i++) {
            if (i != 0) {
                stringbuilder.append(',');
            }

            stringbuilder.append(i).append(':').append(this.elements.get(i));
        }

        return stringbuilder.append(']').toString();
    }

    public void addElement(NbtElement element) {
        if (element.getType() == 0) {
            LOGGER.warn("Invalid TagEnd added to ListTag");
        } else {
            if (this.type == 0) {
                this.type = element.getType();
            } else if (this.type != element.getType()) {
                LOGGER.warn("Adding mismatching tag types to tag list");
                return;
            }

            this.elements.add(element);
        }
    }

    public void setElement(int index, NbtElement element) {
        if (element.getType() == 0) {
            LOGGER.warn("Invalid TagEnd added to ListTag");
        } else if (index >= 0 && index < this.elements.size()) {
            if (this.type == 0) {
                this.type = element.getType();
            } else if (this.type != element.getType()) {
                LOGGER.warn("Adding mismatching tag types to tag list");
                return;
            }

            this.elements.set(index, element);
        } else {
            LOGGER.warn("index out of bounds to set tag in tag list");
        }
    }

    public NbtElement removeElement(int index) {
        return this.elements.remove(index);
    }

    @Override
    public boolean isEmpty() {
        return this.elements.isEmpty();
    }

    public NbtCompound getCompound(int index) {
        if (index >= 0 && index < this.elements.size()) {
            NbtElement nbtelement = this.elements.get(index);
            return nbtelement.getType() == 10 ? (NbtCompound)nbtelement : new NbtCompound();
        } else {
            return new NbtCompound();
        }
    }

    public int[] getIntArray(int index) {
        if (index >= 0 && index < this.elements.size()) {
            NbtElement nbtelement = this.elements.get(index);
            return nbtelement.getType() == 11 ? ((NbtIntArray)nbtelement).getIntArray() : new int[0];
        } else {
            return new int[0];
        }
    }

    public double getDouble(int index) {
        if (index >= 0 && index < this.elements.size()) {
            NbtElement nbtelement = this.elements.get(index);
            return nbtelement.getType() == 6 ? ((NbtDouble)nbtelement).getDouble() : 0.0;
        } else {
            return 0.0;
        }
    }

    public float getFloat(int index) {
        if (index >= 0 && index < this.elements.size()) {
            NbtElement nbtelement = this.elements.get(index);
            return nbtelement.getType() == 5 ? ((NbtFloat)nbtelement).getFloat() : 0.0F;
        } else {
            return 0.0F;
        }
    }

    public String getString(int index) {
        if (index >= 0 && index < this.elements.size()) {
            NbtElement nbtelement = this.elements.get(index);
            return nbtelement.getType() == 8 ? nbtelement.asString() : nbtelement.toString();
        } else {
            return "";
        }
    }

    public NbtElement getElement(int index) {
        return index >= 0 && index < this.elements.size() ? this.elements.get(index) : new NbtEnd();
    }

    public int size() {
        return this.elements.size();
    }

    @Override
    public NbtElement copy() {
        NbtList nbtlist = new NbtList();
        nbtlist.type = this.type;

        for (NbtElement nbtelement : this.elements) {
            NbtElement nbtelement1 = nbtelement.copy();
            nbtlist.elements.add(nbtelement1);
        }

        return nbtlist;
    }

    @Override
    public boolean equals(Object object) {
        if (super.equals(object)) {
            NbtList nbtlist = (NbtList)object;
            if (this.type == nbtlist.type) {
                return this.elements.equals(nbtlist.elements);
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ this.elements.hashCode();
    }

    public int getElementType() {
        return this.type;
    }
}
