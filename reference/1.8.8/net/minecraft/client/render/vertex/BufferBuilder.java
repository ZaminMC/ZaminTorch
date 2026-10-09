package net.minecraft.client.render.vertex;

import com.google.common.primitives.Floats;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;

public class BufferBuilder {
    private ByteBuffer byteBuffer;
    private IntBuffer intBuffer;
    private ShortBuffer shortBuffer;
    private FloatBuffer floatBuffer;
    private int vertexCount;
    private VertexFormatElement currentElement;
    private int color;
    private boolean uncolored;
    private int drawMode;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private VertexFormat format;
    private boolean building;

    public BufferBuilder(int size) {
        this.byteBuffer = MemoryTracker.createByteBuffer(size * 4);
        this.intBuffer = this.byteBuffer.asIntBuffer();
        this.shortBuffer = this.byteBuffer.asShortBuffer();
        this.floatBuffer = this.byteBuffer.asFloatBuffer();
    }

    private void grow(int amount) {
        if (amount > this.intBuffer.remaining()) {
            int i = this.byteBuffer.capacity();
            int j = i % 2097152;
            int k = j + (((this.intBuffer.position() + amount) * 4 - j) / 2097152 + 1) * 2097152;
            LogManager.getLogger().warn("Needed to grow BufferBuilder buffer: Old size " + i + " bytes, new size " + k + " bytes.");
            int l = this.intBuffer.position();
            ByteBuffer bytebuffer = MemoryTracker.createByteBuffer(k);
            ((Buffer)this.byteBuffer).position(0);
            bytebuffer.put(this.byteBuffer);
            ((Buffer)bytebuffer).rewind();
            this.byteBuffer = bytebuffer;
            this.floatBuffer = this.byteBuffer.asFloatBuffer().asReadOnlyBuffer();
            this.intBuffer = this.byteBuffer.asIntBuffer();
            ((Buffer)this.intBuffer).position(l);
            this.shortBuffer = this.byteBuffer.asShortBuffer();
            ((Buffer)this.shortBuffer).position(l << 1);
        }
    }

    public void sortQuads(float cameraX, float cameraY, float cameraZ) {
        int i = this.vertexCount / 4;
        final float[] afloat = new float[i];

        for (int j = 0; j < i; j++) {
            afloat[j] = squaredDistance(
                this.floatBuffer,
                (float)(cameraX + this.offsetX),
                (float)(cameraY + this.offsetY),
                (float)(cameraZ + this.offsetZ),
                this.format.getIntSize(),
                j * this.format.getVertexSize()
            );
        }

        Integer[] ainteger = new Integer[i];

        for (int k = 0; k < ainteger.length; k++) {
            ainteger[k] = k;
        }

        Arrays.sort(ainteger, new Comparator<Integer>() {
            public int compare(Integer integer, Integer integer2) {
                return Floats.compare(afloat[integer2], afloat[integer]);
            }
        });
        BitSet bitset = new BitSet();
        int l = this.format.getVertexSize();
        int[] aint = new int[l];

        for (int l1 = 0; (l1 = bitset.nextClearBit(l1)) < ainteger.length; l1++) {
            int i1 = ainteger[l1];
            if (i1 != l1) {
                ((Buffer)this.intBuffer).limit(i1 * l + l);
                ((Buffer)this.intBuffer).position(i1 * l);
                this.intBuffer.get(aint);
                int j1 = i1;

                for (int k1 = ainteger[j1]; j1 != l1; k1 = ainteger[j1]) {
                    ((Buffer)this.intBuffer).limit(k1 * l + l);
                    ((Buffer)this.intBuffer).position(k1 * l);
                    IntBuffer intbuffer = this.intBuffer.slice();
                    ((Buffer)this.intBuffer).limit(j1 * l + l);
                    ((Buffer)this.intBuffer).position(j1 * l);
                    this.intBuffer.put(intbuffer);
                    bitset.set(j1);
                    j1 = k1;
                }

                ((Buffer)this.intBuffer).limit(l1 * l + l);
                ((Buffer)this.intBuffer).position(l1 * l);
                this.intBuffer.put(aint);
            }

            bitset.set(l1);
        }
    }

    public BufferBuilder.State getState() {
        ((Buffer)this.intBuffer).rewind();
        int i = this.getBufferIndex();
        ((Buffer)this.intBuffer).limit(i);
        int[] aint = new int[i];
        this.intBuffer.get(aint);
        ((Buffer)this.intBuffer).limit(this.intBuffer.capacity());
        ((Buffer)this.intBuffer).position(i);
        return new BufferBuilder.State(aint, new VertexFormat(this.format));
    }

    private int getBufferIndex() {
        return this.vertexCount * this.format.getIntSize();
    }

    private static float squaredDistance(FloatBuffer buffer, float x, float y, float z, int dy, int dx) {
        float f = buffer.get(dx + dy * 0 + 0);
        float f1 = buffer.get(dx + dy * 0 + 1);
        float f2 = buffer.get(dx + dy * 0 + 2);
        float f3 = buffer.get(dx + dy * 1 + 0);
        float f4 = buffer.get(dx + dy * 1 + 1);
        float f5 = buffer.get(dx + dy * 1 + 2);
        float f6 = buffer.get(dx + dy * 2 + 0);
        float f7 = buffer.get(dx + dy * 2 + 1);
        float f8 = buffer.get(dx + dy * 2 + 2);
        float f9 = buffer.get(dx + dy * 3 + 0);
        float f10 = buffer.get(dx + dy * 3 + 1);
        float f11 = buffer.get(dx + dy * 3 + 2);
        float f12 = (f + f3 + f6 + f9) * 0.25F - x;
        float f13 = (f1 + f4 + f7 + f10) * 0.25F - y;
        float f14 = (f2 + f5 + f8 + f11) * 0.25F - z;
        return f12 * f12 + f13 * f13 + f14 * f14;
    }

    public void setState(BufferBuilder.State state) {
        ((Buffer)this.intBuffer).clear();
        this.grow(state.getBuffer().length);
        this.intBuffer.put(state.getBuffer());
        this.vertexCount = state.getVertexCount();
        this.format = new VertexFormat(state.getFormat());
    }

    public void clear() {
        this.vertexCount = 0;
        this.currentElement = null;
        this.color = 0;
    }

    public void begin(int drawMode, VertexFormat format) {
        if (this.building) {
            throw new IllegalStateException("Already building!");
        }

        this.building = true;
        this.clear();
        this.drawMode = drawMode;
        this.format = format;
        this.currentElement = format.getElement(this.color);
        this.uncolored = false;
        ((Buffer)this.byteBuffer).limit(this.byteBuffer.capacity());
    }

    public BufferBuilder texture(double u, double v) {
        int i = this.vertexCount * this.format.getVertexSize() + this.format.getOffset(this.color);
        switch (this.currentElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, (float)u);
                this.byteBuffer.putFloat(i + 4, (float)v);
                break;
            case UINT:
            case INT:
                this.byteBuffer.putInt(i, (int)u);
                this.byteBuffer.putInt(i + 4, (int)v);
                break;
            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short)v);
                this.byteBuffer.putShort(i + 2, (short)u);
                break;
            case UBYTE:
            case BYTE:
                this.byteBuffer.put(i, (byte)v);
                this.byteBuffer.put(i + 1, (byte)u);
        }

        this.nextElement();
        return this;
    }

    public BufferBuilder texture(int u, int v) {
        int i = this.vertexCount * this.format.getVertexSize() + this.format.getOffset(this.color);
        switch (this.currentElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, u);
                this.byteBuffer.putFloat(i + 4, v);
                break;
            case UINT:
            case INT:
                this.byteBuffer.putInt(i, u);
                this.byteBuffer.putInt(i + 4, v);
                break;
            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short)v);
                this.byteBuffer.putShort(i + 2, (short)u);
                break;
            case UBYTE:
            case BYTE:
                this.byteBuffer.put(i, (byte)v);
                this.byteBuffer.put(i + 1, (byte)u);
        }

        this.nextElement();
        return this;
    }

    public void lightColor(int r, int g, int b, int a) {
        int i = (this.vertexCount - 4) * this.format.getIntSize() + this.format.getUvOffset(1) / 4;
        int j = this.format.getVertexSize() >> 2;
        this.intBuffer.put(i, r);
        this.intBuffer.put(i + j, g);
        this.intBuffer.put(i + j * 2, b);
        this.intBuffer.put(i + j * 3, a);
    }

    public void postPosition(double x, double y, double z) {
        int i = this.format.getIntSize();
        int j = (this.vertexCount - 4) * i;

        for (int k = 0; k < 4; k++) {
            int l = j + k * i;
            int i1 = l + 1;
            int j1 = i1 + 1;
            this.intBuffer.put(l, Float.floatToRawIntBits((float)(x + this.offsetX) + Float.intBitsToFloat(this.intBuffer.get(l))));
            this.intBuffer.put(i1, Float.floatToRawIntBits((float)(y + this.offsetY) + Float.intBitsToFloat(this.intBuffer.get(i1))));
            this.intBuffer.put(j1, Float.floatToRawIntBits((float)(z + this.offsetZ) + Float.intBitsToFloat(this.intBuffer.get(j1))));
        }
    }

    private int getColorIndex(int index) {
        return ((this.vertexCount - index) * this.format.getVertexSize() + this.format.getColorOffset()) / 4;
    }

    public void multiplyColor(float r, float g, float b, int index) {
        int i = this.getColorIndex(index);
        int j = -1;
        if (!this.uncolored) {
            j = this.intBuffer.get(i);
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                int k = (int)((j & 0xFF) * r);
                int l = (int)((j >> 8 & 0xFF) * g);
                int i1 = (int)((j >> 16 & 0xFF) * b);
                j &= -16777216;
                j |= i1 << 16 | l << 8 | k;
            } else {
                int j1 = (int)((j >> 24 & 0xFF) * r);
                int k1 = (int)((j >> 16 & 0xFF) * g);
                int l1 = (int)((j >> 8 & 0xFF) * b);
                j &= 255;
                j |= j1 << 24 | k1 << 16 | l1 << 8;
            }
        }

        this.intBuffer.put(i, j);
    }

    private void setColor(int rgb, int index) {
        int i = this.getColorIndex(index);
        int j = rgb >> 16 & 0xFF;
        int k = rgb >> 8 & 0xFF;
        int l = rgb & 0xFF;
        int i1 = rgb >> 24 & 0xFF;
        this.setColor(i, j, k, l, i1);
    }

    public void setColor(float r, float g, float b, int index) {
        int i = this.getColorIndex(index);
        int j = MathHelper.clamp((int)(r * 255.0F), 0, 255);
        int k = MathHelper.clamp((int)(g * 255.0F), 0, 255);
        int l = MathHelper.clamp((int)(b * 255.0F), 0, 255);
        this.setColor(i, j, k, l, 255);
    }

    private void setColor(int index, int r, int g, int b, int a) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            this.intBuffer.put(index, a << 24 | b << 16 | g << 8 | r);
        } else {
            this.intBuffer.put(index, r << 24 | g << 16 | b << 8 | a);
        }
    }

    public void uncolored() {
        this.uncolored = true;
    }

    public BufferBuilder color(float r, float g, float b, float a) {
        return this.color((int)(r * 255.0F), (int)(g * 255.0F), (int)(b * 255.0F), (int)(a * 255.0F));
    }

    public BufferBuilder color(int r, int g, int b, int a) {
        if (this.uncolored) {
            return this;
        }

        int i = this.vertexCount * this.format.getVertexSize() + this.format.getOffset(this.color);
        switch (this.currentElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, r / 255.0F);
                this.byteBuffer.putFloat(i + 4, g / 255.0F);
                this.byteBuffer.putFloat(i + 8, b / 255.0F);
                this.byteBuffer.putFloat(i + 12, a / 255.0F);
                break;
            case UINT:
            case INT:
                this.byteBuffer.putFloat(i, r);
                this.byteBuffer.putFloat(i + 4, g);
                this.byteBuffer.putFloat(i + 8, b);
                this.byteBuffer.putFloat(i + 12, a);
                break;
            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short)r);
                this.byteBuffer.putShort(i + 2, (short)g);
                this.byteBuffer.putShort(i + 4, (short)b);
                this.byteBuffer.putShort(i + 6, (short)a);
                break;
            case UBYTE:
            case BYTE:
                if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                    this.byteBuffer.put(i, (byte)r);
                    this.byteBuffer.put(i + 1, (byte)g);
                    this.byteBuffer.put(i + 2, (byte)b);
                    this.byteBuffer.put(i + 3, (byte)a);
                } else {
                    this.byteBuffer.put(i, (byte)a);
                    this.byteBuffer.put(i + 1, (byte)b);
                    this.byteBuffer.put(i + 2, (byte)g);
                    this.byteBuffer.put(i + 3, (byte)r);
                }
        }

        this.nextElement();
        return this;
    }

    public void vertices(int[] vertices) {
        this.grow(vertices.length);
        ((Buffer)this.intBuffer).position(this.getBufferIndex());
        this.intBuffer.put(vertices);
        this.vertexCount = this.vertexCount + vertices.length / this.format.getIntSize();
    }

    public void nextVertex() {
        this.vertexCount++;
        this.grow(this.format.getIntSize());
    }

    public BufferBuilder vertex(double x, double y, double z) {
        int i = this.vertexCount * this.format.getVertexSize() + this.format.getOffset(this.color);
        switch (this.currentElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, (float)(x + this.offsetX));
                this.byteBuffer.putFloat(i + 4, (float)(y + this.offsetY));
                this.byteBuffer.putFloat(i + 8, (float)(z + this.offsetZ));
                break;
            case UINT:
            case INT:
                this.byteBuffer.putInt(i, Float.floatToRawIntBits((float)(x + this.offsetX)));
                this.byteBuffer.putInt(i + 4, Float.floatToRawIntBits((float)(y + this.offsetY)));
                this.byteBuffer.putInt(i + 8, Float.floatToRawIntBits((float)(z + this.offsetZ)));
                break;
            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short)(x + this.offsetX));
                this.byteBuffer.putShort(i + 2, (short)(y + this.offsetY));
                this.byteBuffer.putShort(i + 4, (short)(z + this.offsetZ));
                break;
            case UBYTE:
            case BYTE:
                this.byteBuffer.put(i, (byte)(x + this.offsetX));
                this.byteBuffer.put(i + 1, (byte)(y + this.offsetY));
                this.byteBuffer.put(i + 2, (byte)(z + this.offsetZ));
        }

        this.nextElement();
        return this;
    }

    public void postNormal(float x, float y, float z) {
        int i = (byte)(x * 127.0F) & 255;
        int j = (byte)(y * 127.0F) & 255;
        int k = (byte)(z * 127.0F) & 255;
        int l = i | j << 8 | k << 16;
        int i1 = this.format.getVertexSize() >> 2;
        int j1 = (this.vertexCount - 4) * i1 + this.format.getNormalOffset() / 4;
        this.intBuffer.put(j1, l);
        this.intBuffer.put(j1 + i1, l);
        this.intBuffer.put(j1 + i1 * 2, l);
        this.intBuffer.put(j1 + i1 * 3, l);
    }

    private void nextElement() {
        this.color++;
        this.color = this.color % this.format.getElementCount();
        this.currentElement = this.format.getElement(this.color);
        if (this.currentElement.getUsage() == VertexFormatElement.Usage.PADDING) {
            this.nextElement();
        }
    }

    public BufferBuilder normal(float x, float y, float z) {
        int i = this.vertexCount * this.format.getVertexSize() + this.format.getOffset(this.color);
        switch (this.currentElement.getType()) {
            case FLOAT:
                this.byteBuffer.putFloat(i, x);
                this.byteBuffer.putFloat(i + 4, y);
                this.byteBuffer.putFloat(i + 8, z);
                break;
            case UINT:
            case INT:
                this.byteBuffer.putInt(i, (int)x);
                this.byteBuffer.putInt(i + 4, (int)y);
                this.byteBuffer.putInt(i + 8, (int)z);
                break;
            case USHORT:
            case SHORT:
                this.byteBuffer.putShort(i, (short)((int)x * 32767 & 65535));
                this.byteBuffer.putShort(i + 2, (short)((int)y * 32767 & 65535));
                this.byteBuffer.putShort(i + 4, (short)((int)z * 32767 & 65535));
                break;
            case UBYTE:
            case BYTE:
                this.byteBuffer.put(i, (byte)((int)x * 127 & 0xFF));
                this.byteBuffer.put(i + 1, (byte)((int)y * 127 & 0xFF));
                this.byteBuffer.put(i + 2, (byte)((int)z * 127 & 0xFF));
        }

        this.nextElement();
        return this;
    }

    public void offset(double offsetX, double offsetY, double offsetZ) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public void end() {
        if (!this.building) {
            throw new IllegalStateException("Not building!");
        }

        this.building = false;
        ((Buffer)this.byteBuffer).position(0);
        ((Buffer)this.byteBuffer).limit(this.getBufferIndex() * 4);
    }

    public ByteBuffer getBuffer() {
        return this.byteBuffer;
    }

    public VertexFormat getFormat() {
        return this.format;
    }

    public int getVertexCount() {
        return this.vertexCount;
    }

    public int getDrawMode() {
        return this.drawMode;
    }

    public void setQuadColor(int rgb) {
        for (int i = 0; i < 4; i++) {
            this.setColor(rgb, i + 1);
        }
    }

    public void setQuadColor(float r, float g, float b) {
        for (int i = 0; i < 4; i++) {
            this.setColor(r, g, b, i + 1);
        }
    }

    public class State {
        private final int[] buffer;
        private final VertexFormat format;

        public State(int[] buffer, VertexFormat format) {
            this.buffer = buffer;
            this.format = format;
        }

        public int[] getBuffer() {
            return this.buffer;
        }

        public int getVertexCount() {
            return this.buffer.length / this.format.getIntSize();
        }

        public VertexFormat getFormat() {
            return this.format;
        }
    }
}
