package net.minecraft.network;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufProcessor;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtReadLimiter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class PacketByteBuf extends ByteBuf {
    private final ByteBuf delegate;

    public PacketByteBuf(ByteBuf delegate) {
        this.delegate = delegate;
    }

    public static int getVarIntSizeBytes(int size) {
        for (int i = 1; i < 5; i++) {
            if ((size & -1 << i * 7) == 0) {
                return i;
            }
        }

        return 5;
    }

    public void writeByteArray(byte[] bytes) {
        this.writeVarInt(bytes.length);
        this.writeBytes(bytes);
    }

    public byte[] readByteArray() {
        byte[] abyte = new byte[this.readVarInt()];
        this.readBytes(abyte);
        return abyte;
    }

    public BlockPos readBlockPos() {
        return BlockPos.fromLong(this.readLong());
    }

    public void writeBlockPos(BlockPos pos) {
        this.writeLong(pos.toLong());
    }

    public Text readText() throws IOException {
        return Text.Serializer.fromJson(this.readString(32767));
    }

    public void writeText(Text text) throws IOException {
        this.writeString(Text.Serializer.toJson(text));
    }

    public <T extends Enum<T>> T readEnum(Class<T> type) {
        return type.getEnumConstants()[this.readVarInt()];
    }

    public void writeEnum(Enum<?> value) {
        this.writeVarInt(value.ordinal());
    }

    public int readVarInt() {
        int i = 0;
        int j = 0;

        byte b0;
        do {
            b0 = this.readByte();
            i |= (b0 & 127) << j++ * 7;
            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((b0 & 128) == 128);

        return i;
    }

    public long readVarLong() {
        long i = 0L;
        int j = 0;

        byte b0;
        do {
            b0 = this.readByte();
            i |= (long)(b0 & 127) << j++ * 7;
            if (j > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while ((b0 & 128) == 128);

        return i;
    }

    public void writeUuid(UUID uuid) {
        this.writeLong(uuid.getMostSignificantBits());
        this.writeLong(uuid.getLeastSignificantBits());
    }

    public UUID readUuid() {
        return new UUID(this.readLong(), this.readLong());
    }

    public void writeVarInt(int i) {
        while ((i & -128) != 0) {
            this.writeByte(i & 127 | 128);
            i >>>= 7;
        }

        this.writeByte(i);
    }

    public void writeVarLong(long l) {
        while ((l & -128L) != 0L) {
            this.writeByte((int)(l & 127L) | 128);
            l >>>= 7;
        }

        this.writeByte((int)l);
    }

    public void writeNbtCompound(NbtCompound nbt) {
        if (nbt == null) {
            this.writeByte(0);
        } else {
            try {
                NbtIo.write(nbt, new ByteBufOutputStream(this));
            } catch (IOException ioexception) {
                throw new EncoderException(ioexception);
            }
        }
    }

    public NbtCompound readNbtCompound() throws IOException {
        int i = this.readerIndex();
        byte b0 = this.readByte();
        if (b0 == 0) {
            return null;
        }

        this.readerIndex(i);
        return NbtIo.read(new ByteBufInputStream(this), new NbtReadLimiter(2097152L));
    }

    public void writeItem(ItemStack item) {
        if (item == null) {
            this.writeShort(-1);
        } else {
            this.writeShort(Item.getId(item.getItem()));
            this.writeByte(item.size);
            this.writeShort(item.getMetadata());
            NbtCompound nbtcompound = null;
            if (item.getItem().isDamageable() || item.getItem().shouldSyncNbt()) {
                nbtcompound = item.getNbt();
            }

            this.writeNbtCompound(nbtcompound);
        }
    }

    public ItemStack readItem() throws IOException {
        ItemStack itemstack = null;
        int i = this.readShort();
        if (i >= 0) {
            int j = this.readByte();
            int k = this.readShort();
            itemstack = new ItemStack(Item.byId(i), j, k);
            itemstack.setNbt(this.readNbtCompound());
        }

        return itemstack;
    }

    public String readString(int maxLength) {
        int i = this.readVarInt();
        if (i > maxLength * 4) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + i + " > " + maxLength * 4 + ")");
        } else if (i < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            String s = new String(this.readBytes(i).array(), Charsets.UTF_8);
            if (s.length() > maxLength) {
                throw new DecoderException("The received string length is longer than maximum allowed (" + i + " > " + maxLength + ")");
            } else {
                return s;
            }
        }
    }

    public PacketByteBuf writeString(String s) {
        byte[] abyte = s.getBytes(Charsets.UTF_8);
        if (abyte.length > 32767) {
            throw new EncoderException("String too big (was " + s.length() + " bytes encoded, max " + 32767 + ")");
        }

        this.writeVarInt(abyte.length);
        this.writeBytes(abyte);
        return this;
    }

    @Override
    public int capacity() {
        return this.delegate.capacity();
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        return this.delegate.capacity(newCapacity);
    }

    @Override
    public int maxCapacity() {
        return this.delegate.maxCapacity();
    }

    @Override
    public ByteBufAllocator alloc() {
        return this.delegate.alloc();
    }

    @Override
    public ByteOrder order() {
        return this.delegate.order();
    }

    @Override
    public ByteBuf order(ByteOrder order) {
        return this.delegate.order(order);
    }

    @Override
    public ByteBuf unwrap() {
        return this.delegate.unwrap();
    }

    @Override
    public boolean isDirect() {
        return this.delegate.isDirect();
    }

    @Override
    public int readerIndex() {
        return this.delegate.readerIndex();
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        return this.delegate.readerIndex(readerIndex);
    }

    @Override
    public int writerIndex() {
        return this.delegate.writerIndex();
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        return this.delegate.writerIndex(writerIndex);
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        return this.delegate.setIndex(readerIndex, writerIndex);
    }

    @Override
    public int readableBytes() {
        return this.delegate.readableBytes();
    }

    @Override
    public int writableBytes() {
        return this.delegate.writableBytes();
    }

    @Override
    public int maxWritableBytes() {
        return this.delegate.maxWritableBytes();
    }

    @Override
    public boolean isReadable() {
        return this.delegate.isReadable();
    }

    @Override
    public boolean isReadable(int size) {
        return this.delegate.isReadable(size);
    }

    @Override
    public boolean isWritable() {
        return this.delegate.isWritable();
    }

    @Override
    public boolean isWritable(int size) {
        return this.delegate.isWritable(size);
    }

    @Override
    public ByteBuf clear() {
        return this.delegate.clear();
    }

    @Override
    public ByteBuf markReaderIndex() {
        return this.delegate.markReaderIndex();
    }

    @Override
    public ByteBuf resetReaderIndex() {
        return this.delegate.resetReaderIndex();
    }

    @Override
    public ByteBuf markWriterIndex() {
        return this.delegate.markWriterIndex();
    }

    @Override
    public ByteBuf resetWriterIndex() {
        return this.delegate.resetWriterIndex();
    }

    @Override
    public ByteBuf discardReadBytes() {
        return this.delegate.discardReadBytes();
    }

    @Override
    public ByteBuf discardSomeReadBytes() {
        return this.delegate.discardSomeReadBytes();
    }

    @Override
    public ByteBuf ensureWritable(int minWritableBytes) {
        return this.delegate.ensureWritable(minWritableBytes);
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        return this.delegate.ensureWritable(minWritableBytes, force);
    }

    @Override
    public boolean getBoolean(int index) {
        return this.delegate.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        return this.delegate.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        return this.delegate.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        return this.delegate.getShort(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        return this.delegate.getUnsignedShort(index);
    }

    @Override
    public int getMedium(int index) {
        return this.delegate.getMedium(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return this.delegate.getUnsignedMedium(index);
    }

    @Override
    public int getInt(int index) {
        return this.delegate.getInt(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return this.delegate.getUnsignedInt(index);
    }

    @Override
    public long getLong(int index) {
        return this.delegate.getLong(index);
    }

    @Override
    public char getChar(int index) {
        return this.delegate.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        return this.delegate.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        return this.delegate.getDouble(index);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        return this.delegate.getBytes(index, dst);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        return this.delegate.getBytes(index, dst, length);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        return this.delegate.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        return this.delegate.getBytes(index, dst);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        return this.delegate.getBytes(index, dst, dstIndex, length);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        return this.delegate.getBytes(index, dst);
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        return this.delegate.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return this.delegate.getBytes(index, out, length);
    }

    @Override
    public ByteBuf setBoolean(int index, boolean value) {
        return this.delegate.setBoolean(index, value);
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        return this.delegate.setByte(index, value);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        return this.delegate.setShort(index, value);
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        return this.delegate.setMedium(index, value);
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        return this.delegate.setInt(index, value);
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        return this.delegate.setLong(index, value);
    }

    @Override
    public ByteBuf setChar(int index, int value) {
        return this.delegate.setChar(index, value);
    }

    @Override
    public ByteBuf setFloat(int index, float value) {
        return this.delegate.setFloat(index, value);
    }

    @Override
    public ByteBuf setDouble(int index, double value) {
        return this.delegate.setDouble(index, value);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        return this.delegate.setBytes(index, src);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        return this.delegate.setBytes(index, src, length);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        return this.delegate.setBytes(index, src, srcIndex, length);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        return this.delegate.setBytes(index, src);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        return this.delegate.setBytes(index, src, srcIndex, length);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        return this.delegate.setBytes(index, src);
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return this.delegate.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return this.delegate.setBytes(index, in, length);
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        return this.delegate.setZero(index, length);
    }

    @Override
    public boolean readBoolean() {
        return this.delegate.readBoolean();
    }

    @Override
    public byte readByte() {
        return this.delegate.readByte();
    }

    @Override
    public short readUnsignedByte() {
        return this.delegate.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return this.delegate.readShort();
    }

    @Override
    public int readUnsignedShort() {
        return this.delegate.readUnsignedShort();
    }

    @Override
    public int readMedium() {
        return this.delegate.readMedium();
    }

    @Override
    public int readUnsignedMedium() {
        return this.delegate.readUnsignedMedium();
    }

    @Override
    public int readInt() {
        return this.delegate.readInt();
    }

    @Override
    public long readUnsignedInt() {
        return this.delegate.readUnsignedInt();
    }

    @Override
    public long readLong() {
        return this.delegate.readLong();
    }

    @Override
    public char readChar() {
        return this.delegate.readChar();
    }

    @Override
    public float readFloat() {
        return this.delegate.readFloat();
    }

    @Override
    public double readDouble() {
        return this.delegate.readDouble();
    }

    @Override
    public ByteBuf readBytes(int length) {
        return this.delegate.readBytes(length);
    }

    @Override
    public ByteBuf readSlice(int length) {
        return this.delegate.readSlice(length);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        return this.delegate.readBytes(dst);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        return this.delegate.readBytes(dst, length);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        return this.delegate.readBytes(dst, dstIndex, length);
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        return this.delegate.readBytes(dst);
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        return this.delegate.readBytes(dst, dstIndex, length);
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        return this.delegate.readBytes(dst);
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        return this.delegate.readBytes(out, length);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return this.delegate.readBytes(out, length);
    }

    @Override
    public ByteBuf skipBytes(int length) {
        return this.delegate.skipBytes(length);
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        return this.delegate.writeBoolean(value);
    }

    @Override
    public ByteBuf writeByte(int value) {
        return this.delegate.writeByte(value);
    }

    @Override
    public ByteBuf writeShort(int value) {
        return this.delegate.writeShort(value);
    }

    @Override
    public ByteBuf writeMedium(int value) {
        return this.delegate.writeMedium(value);
    }

    @Override
    public ByteBuf writeInt(int value) {
        return this.delegate.writeInt(value);
    }

    @Override
    public ByteBuf writeLong(long value) {
        return this.delegate.writeLong(value);
    }

    @Override
    public ByteBuf writeChar(int value) {
        return this.delegate.writeChar(value);
    }

    @Override
    public ByteBuf writeFloat(float value) {
        return this.delegate.writeFloat(value);
    }

    @Override
    public ByteBuf writeDouble(double value) {
        return this.delegate.writeDouble(value);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        return this.delegate.writeBytes(src);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        return this.delegate.writeBytes(src, length);
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        return this.delegate.writeBytes(src, srcIndex, length);
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        return this.delegate.writeBytes(src);
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        return this.delegate.writeBytes(src, srcIndex, length);
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        return this.delegate.writeBytes(src);
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        return this.delegate.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        return this.delegate.writeBytes(in, length);
    }

    @Override
    public ByteBuf writeZero(int length) {
        return this.delegate.writeZero(length);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return this.delegate.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return this.delegate.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return this.delegate.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return this.delegate.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteBufProcessor processor) {
        return this.delegate.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteBufProcessor processor) {
        return this.delegate.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteBufProcessor processor) {
        return this.delegate.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteBufProcessor processor) {
        return this.delegate.forEachByteDesc(index, length, processor);
    }

    @Override
    public ByteBuf copy() {
        return this.delegate.copy();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return this.delegate.copy(index, length);
    }

    @Override
    public ByteBuf slice() {
        return this.delegate.slice();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return this.delegate.slice(index, length);
    }

    @Override
    public ByteBuf duplicate() {
        return this.delegate.duplicate();
    }

    @Override
    public int nioBufferCount() {
        return this.delegate.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        return this.delegate.nioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return this.delegate.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return this.delegate.internalNioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return this.delegate.nioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return this.delegate.nioBuffers(index, length);
    }

    @Override
    public boolean hasArray() {
        return this.delegate.hasArray();
    }

    @Override
    public byte[] array() {
        return this.delegate.array();
    }

    @Override
    public int arrayOffset() {
        return this.delegate.arrayOffset();
    }

    @Override
    public boolean hasMemoryAddress() {
        return this.delegate.hasMemoryAddress();
    }

    @Override
    public long memoryAddress() {
        return this.delegate.memoryAddress();
    }

    @Override
    public String toString(Charset charset) {
        return this.delegate.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return this.delegate.toString(index, length, charset);
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return this.delegate.equals(object);
    }

    @Override
    public int compareTo(ByteBuf byteBuf) {
        return this.delegate.compareTo(byteBuf);
    }

    @Override
    public String toString() {
        return this.delegate.toString();
    }

    @Override
    public ByteBuf retain(int i) {
        return this.delegate.retain(i);
    }

    @Override
    public ByteBuf retain() {
        return this.delegate.retain();
    }

    @Override
    public int refCnt() {
        return this.delegate.refCnt();
    }

    @Override
    public boolean release() {
        return this.delegate.release();
    }

    @Override
    public boolean release(int decrement) {
        return this.delegate.release(decrement);
    }
}
