package net.minecraft.client.render.platform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class MemoryTracker {
    public static synchronized int getLists(int s) {
        int i = GL11.glGenLists(s);
        if (i == 0) {
            int j = GL11.glGetError();
            String sx = "No error code reported";
            if (j != 0) {
                sx = GLU.gluErrorString(j);
            }

            throw new IllegalStateException("glGenLists returned an ID of 0 for a count of " + s + ", GL error (" + j + "): " + sx);
        } else {
            return i;
        }
    }

    public static synchronized void releaseLists(int list, int range) {
        GL11.glDeleteLists(list, range);
    }

    public static synchronized void releaseList(int list) {
        GL11.glDeleteLists(list, 1);
    }

    public static synchronized ByteBuffer createByteBuffer(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }

    public static IntBuffer createIntBuffer(int capacity) {
        return createByteBuffer(capacity << 2).asIntBuffer();
    }

    public static FloatBuffer createFloatBuffer(int capacity) {
        return createByteBuffer(capacity << 2).asFloatBuffer();
    }
}
