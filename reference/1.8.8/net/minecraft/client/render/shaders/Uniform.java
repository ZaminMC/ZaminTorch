package net.minecraft.client.render.shaders;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.render.Effect;
import net.minecraft.client.render.platform.GLX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;

public class Uniform {
    private static final Logger LOGGER = LogManager.getLogger();
    private int location;
    private final int count;
    private final int type;
    private final IntBuffer intValues;
    private final FloatBuffer floatValues;
    private final String name;
    private boolean dirty;
    private final Effect parent;

    public Uniform(String name, int type, int count, Effect parent) {
        this.name = name;
        this.count = count;
        this.type = type;
        this.parent = parent;
        if (type <= 3) {
            this.intValues = BufferUtils.createIntBuffer(count);
            this.floatValues = null;
        } else {
            this.intValues = null;
            this.floatValues = BufferUtils.createFloatBuffer(count);
        }

        this.location = -1;
        this.markDirty();
    }

    private void markDirty() {
        this.dirty = true;
        if (this.parent != null) {
            this.parent.markDirty();
        }
    }

    public static int getTypeFromString(String s) {
        int i = -1;
        if (s.equals("int")) {
            i = 0;
        } else if (s.equals("float")) {
            i = 4;
        } else if (s.startsWith("matrix")) {
            if (s.endsWith("2x2")) {
                i = 8;
            } else if (s.endsWith("3x3")) {
                i = 9;
            } else if (s.endsWith("4x4")) {
                i = 10;
            }
        }

        return i;
    }

    public void setLocation(int location) {
        this.location = location;
    }

    public String getName() {
        return this.name;
    }

    public void set(float value0) {
        ((Buffer)this.floatValues).position(0);
        this.floatValues.put(0, value0);
        this.markDirty();
    }

    public void set(float value0, float value1) {
        ((Buffer)this.floatValues).position(0);
        this.floatValues.put(0, value0);
        this.floatValues.put(1, value1);
        this.markDirty();
    }

    public void set(float value0, float value1, float value2) {
        ((Buffer)this.floatValues).position(0);
        this.floatValues.put(0, value0);
        this.floatValues.put(1, value1);
        this.floatValues.put(2, value2);
        this.markDirty();
    }

    public void set(float value0, float value1, float value2, float value3) {
        ((Buffer)this.floatValues).position(0);
        this.floatValues.put(value0);
        this.floatValues.put(value1);
        this.floatValues.put(value2);
        this.floatValues.put(value3);
        ((Buffer)this.floatValues).flip();
        this.markDirty();
    }

    public void setSafe(float value0, float value1, float value2, float value3) {
        ((Buffer)this.floatValues).position(0);
        if (this.type >= 4) {
            this.floatValues.put(0, value0);
        }

        if (this.type >= 5) {
            this.floatValues.put(1, value1);
        }

        if (this.type >= 6) {
            this.floatValues.put(2, value2);
        }

        if (this.type >= 7) {
            this.floatValues.put(3, value3);
        }

        this.markDirty();
    }

    public void setSafe(int value0, int value1, int value2, int value3) {
        ((Buffer)this.intValues).position(0);
        if (this.type >= 0) {
            this.intValues.put(0, value0);
        }

        if (this.type >= 1) {
            this.intValues.put(1, value1);
        }

        if (this.type >= 2) {
            this.intValues.put(2, value2);
        }

        if (this.type >= 3) {
            this.intValues.put(3, value3);
        }

        this.markDirty();
    }

    public void set(float[] values) {
        if (values.length < this.count) {
            LOGGER.warn("Uniform.set called with a too-small value array (expected " + this.count + ", got " + values.length + "). Ignoring.");
        } else {
            ((Buffer)this.floatValues).position(0);
            this.floatValues.put(values);
            ((Buffer)this.floatValues).position(0);
            this.markDirty();
        }
    }

    public void set(
        float value0,
        float value1,
        float value2,
        float value3,
        float value4,
        float value5,
        float value6,
        float value7,
        float value8,
        float value9,
        float value10,
        float value11,
        float value12,
        float value13,
        float value14,
        float value15
    ) {
        ((Buffer)this.floatValues).position(0);
        this.floatValues.put(0, value0);
        this.floatValues.put(1, value1);
        this.floatValues.put(2, value2);
        this.floatValues.put(3, value3);
        this.floatValues.put(4, value4);
        this.floatValues.put(5, value5);
        this.floatValues.put(6, value6);
        this.floatValues.put(7, value7);
        this.floatValues.put(8, value8);
        this.floatValues.put(9, value9);
        this.floatValues.put(10, value10);
        this.floatValues.put(11, value11);
        this.floatValues.put(12, value12);
        this.floatValues.put(13, value13);
        this.floatValues.put(14, value14);
        this.floatValues.put(15, value15);
        this.markDirty();
    }

    public void set(Matrix4f values) {
        this.set(
            values.m00,
            values.m01,
            values.m02,
            values.m03,
            values.m10,
            values.m11,
            values.m12,
            values.m13,
            values.m20,
            values.m21,
            values.m22,
            values.m23,
            values.m30,
            values.m31,
            values.m32,
            values.m33
        );
    }

    public void upload() {
        if (!this.dirty) {
        }

        this.dirty = false;
        if (this.type <= 3) {
            this.uploadAsInteger();
        } else if (this.type <= 7) {
            this.uploadAsFloat();
        } else {
            if (this.type > 10) {
                LOGGER.warn("Uniform.upload called, but type value (" + this.type + ") is not " + "a valid type. Ignoring.");
                return;
            }

            this.uploadAsMatrix();
        }
    }

    private void uploadAsInteger() {
        switch (this.type) {
            case 0:
                GLX.uniform1(this.location, this.intValues);
                break;
            case 1:
                GLX.uniform2(this.location, this.intValues);
                break;
            case 2:
                GLX.uniform3(this.location, this.intValues);
                break;
            case 3:
                GLX.uniform4(this.location, this.intValues);
                break;
            default:
                LOGGER.warn("Uniform.upload called, but count value (" + this.count + ") is " + " not in the range of 1 to 4. Ignoring.");
        }
    }

    private void uploadAsFloat() {
        switch (this.type) {
            case 4:
                GLX.uniform1(this.location, this.floatValues);
                break;
            case 5:
                GLX.uniform2(this.location, this.floatValues);
                break;
            case 6:
                GLX.uniform3(this.location, this.floatValues);
                break;
            case 7:
                GLX.uniform4(this.location, this.floatValues);
                break;
            default:
                LOGGER.warn("Uniform.upload called, but count value (" + this.count + ") is " + "not in the range of 1 to 4. Ignoring.");
        }
    }

    private void uploadAsMatrix() {
        switch (this.type) {
            case 8:
                GLX.uniformMatrix2(this.location, true, this.floatValues);
                break;
            case 9:
                GLX.uniformMatrix3(this.location, true, this.floatValues);
                break;
            case 10:
                GLX.uniformMatrix4(this.location, true, this.floatValues);
        }
    }
}
