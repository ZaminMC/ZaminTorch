package net.minecraft.client.render.platform;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public class Lighting {
    private static FloatBuffer BUFFER = MemoryTracker.createFloatBuffer(16);
    private static final Vec3d LIGHT_0 = new Vec3d(0.2F, 1.0, -0.7F).normalize();
    private static final Vec3d LIGHT_1 = new Vec3d(-0.2F, 1.0, 0.7F).normalize();

    public static void turnOff() {
        GlStateManager.disableLighting();
        GlStateManager.disableLight(0);
        GlStateManager.disableLight(1);
        GlStateManager.disableColorMaterial();
    }

    public static void turnOn() {
        GlStateManager.enableLighting();
        GlStateManager.enableLight(0);
        GlStateManager.enableLight(1);
        GlStateManager.enableColorMaterial();
        GlStateManager.colorMaterial(1032, 5634);
        float f = 0.4F;
        float f1 = 0.6F;
        float f2 = 0.0F;
        GL11.glLight(16384, 4611, getBuffer(LIGHT_0.x, LIGHT_0.y, LIGHT_0.z, 0.0));
        GL11.glLight(16384, 4609, getBuffer(f1, f1, f1, 1.0F));
        GL11.glLight(16384, 4608, getBuffer(0.0F, 0.0F, 0.0F, 1.0F));
        GL11.glLight(16384, 4610, getBuffer(f2, f2, f2, 1.0F));
        GL11.glLight(16385, 4611, getBuffer(LIGHT_1.x, LIGHT_1.y, LIGHT_1.z, 0.0));
        GL11.glLight(16385, 4609, getBuffer(f1, f1, f1, 1.0F));
        GL11.glLight(16385, 4608, getBuffer(0.0F, 0.0F, 0.0F, 1.0F));
        GL11.glLight(16385, 4610, getBuffer(f2, f2, f2, 1.0F));
        GlStateManager.shadeModel(7424);
        GL11.glLightModel(2899, getBuffer(f, f, f, 1.0F));
    }

    private static FloatBuffer getBuffer(double r, double g, double b, double a) {
        return getBuffer((float)r, (float)g, (float)b, (float)a);
    }

    private static FloatBuffer getBuffer(float r, float g, float b, float a) {
        ((Buffer)BUFFER).clear();
        BUFFER.put(r).put(g).put(b).put(a);
        ((Buffer)BUFFER).flip();
        return BUFFER;
    }

    public static void turnOnGui() {
        GlStateManager.pushMatrix();
        GlStateManager.rotatef(-30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(165.0F, 1.0F, 0.0F, 0.0F);
        turnOn();
        GlStateManager.popMatrix();
    }
}
