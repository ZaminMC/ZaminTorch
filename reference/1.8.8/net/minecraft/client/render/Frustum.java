package net.minecraft.client.render;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.util.math.MathHelper;

public class Frustum extends FrustumData {
    private static Frustum INSTANCE = new Frustum();
    private FloatBuffer projectionBuffer = MemoryTracker.createFloatBuffer(16);
    private FloatBuffer modelBuffer = MemoryTracker.createFloatBuffer(16);
    private FloatBuffer clipBuffer = MemoryTracker.createFloatBuffer(16);

    public static FrustumData getInstance() {
        INSTANCE.compute();
        return INSTANCE;
    }

    private void normalize(float[] plane) {
        float f = MathHelper.sqrt(plane[0] * plane[0] + plane[1] * plane[1] + plane[2] * plane[2]);
        plane[0] /= f;
        plane[1] /= f;
        plane[2] /= f;
        plane[3] /= f;
    }

    public void compute() {
        ((Buffer)this.projectionBuffer).clear();
        ((Buffer)this.modelBuffer).clear();
        ((Buffer)this.clipBuffer).clear();
        GlStateManager.getFloat(2983, this.projectionBuffer);
        GlStateManager.getFloat(2982, this.modelBuffer);
        float[] afloat = this.projectionMatrix;
        float[] afloat1 = this.modelMatrix;
        ((Buffer)this.projectionBuffer).flip().limit(16);
        this.projectionBuffer.get(afloat);
        ((Buffer)this.modelBuffer).flip().limit(16);
        this.modelBuffer.get(afloat1);
        this.clipMatrix[0] = afloat1[0] * afloat[0] + afloat1[1] * afloat[4] + afloat1[2] * afloat[8] + afloat1[3] * afloat[12];
        this.clipMatrix[1] = afloat1[0] * afloat[1] + afloat1[1] * afloat[5] + afloat1[2] * afloat[9] + afloat1[3] * afloat[13];
        this.clipMatrix[2] = afloat1[0] * afloat[2] + afloat1[1] * afloat[6] + afloat1[2] * afloat[10] + afloat1[3] * afloat[14];
        this.clipMatrix[3] = afloat1[0] * afloat[3] + afloat1[1] * afloat[7] + afloat1[2] * afloat[11] + afloat1[3] * afloat[15];
        this.clipMatrix[4] = afloat1[4] * afloat[0] + afloat1[5] * afloat[4] + afloat1[6] * afloat[8] + afloat1[7] * afloat[12];
        this.clipMatrix[5] = afloat1[4] * afloat[1] + afloat1[5] * afloat[5] + afloat1[6] * afloat[9] + afloat1[7] * afloat[13];
        this.clipMatrix[6] = afloat1[4] * afloat[2] + afloat1[5] * afloat[6] + afloat1[6] * afloat[10] + afloat1[7] * afloat[14];
        this.clipMatrix[7] = afloat1[4] * afloat[3] + afloat1[5] * afloat[7] + afloat1[6] * afloat[11] + afloat1[7] * afloat[15];
        this.clipMatrix[8] = afloat1[8] * afloat[0] + afloat1[9] * afloat[4] + afloat1[10] * afloat[8] + afloat1[11] * afloat[12];
        this.clipMatrix[9] = afloat1[8] * afloat[1] + afloat1[9] * afloat[5] + afloat1[10] * afloat[9] + afloat1[11] * afloat[13];
        this.clipMatrix[10] = afloat1[8] * afloat[2] + afloat1[9] * afloat[6] + afloat1[10] * afloat[10] + afloat1[11] * afloat[14];
        this.clipMatrix[11] = afloat1[8] * afloat[3] + afloat1[9] * afloat[7] + afloat1[10] * afloat[11] + afloat1[11] * afloat[15];
        this.clipMatrix[12] = afloat1[12] * afloat[0] + afloat1[13] * afloat[4] + afloat1[14] * afloat[8] + afloat1[15] * afloat[12];
        this.clipMatrix[13] = afloat1[12] * afloat[1] + afloat1[13] * afloat[5] + afloat1[14] * afloat[9] + afloat1[15] * afloat[13];
        this.clipMatrix[14] = afloat1[12] * afloat[2] + afloat1[13] * afloat[6] + afloat1[14] * afloat[10] + afloat1[15] * afloat[14];
        this.clipMatrix[15] = afloat1[12] * afloat[3] + afloat1[13] * afloat[7] + afloat1[14] * afloat[11] + afloat1[15] * afloat[15];
        float[] afloat2 = this.frustum[0];
        afloat2[0] = this.clipMatrix[3] - this.clipMatrix[0];
        afloat2[1] = this.clipMatrix[7] - this.clipMatrix[4];
        afloat2[2] = this.clipMatrix[11] - this.clipMatrix[8];
        afloat2[3] = this.clipMatrix[15] - this.clipMatrix[12];
        this.normalize(afloat2);
        float[] afloat3 = this.frustum[1];
        afloat3[0] = this.clipMatrix[3] + this.clipMatrix[0];
        afloat3[1] = this.clipMatrix[7] + this.clipMatrix[4];
        afloat3[2] = this.clipMatrix[11] + this.clipMatrix[8];
        afloat3[3] = this.clipMatrix[15] + this.clipMatrix[12];
        this.normalize(afloat3);
        float[] afloat4 = this.frustum[2];
        afloat4[0] = this.clipMatrix[3] + this.clipMatrix[1];
        afloat4[1] = this.clipMatrix[7] + this.clipMatrix[5];
        afloat4[2] = this.clipMatrix[11] + this.clipMatrix[9];
        afloat4[3] = this.clipMatrix[15] + this.clipMatrix[13];
        this.normalize(afloat4);
        float[] afloat5 = this.frustum[3];
        afloat5[0] = this.clipMatrix[3] - this.clipMatrix[1];
        afloat5[1] = this.clipMatrix[7] - this.clipMatrix[5];
        afloat5[2] = this.clipMatrix[11] - this.clipMatrix[9];
        afloat5[3] = this.clipMatrix[15] - this.clipMatrix[13];
        this.normalize(afloat5);
        float[] afloat6 = this.frustum[4];
        afloat6[0] = this.clipMatrix[3] - this.clipMatrix[2];
        afloat6[1] = this.clipMatrix[7] - this.clipMatrix[6];
        afloat6[2] = this.clipMatrix[11] - this.clipMatrix[10];
        afloat6[3] = this.clipMatrix[15] - this.clipMatrix[14];
        this.normalize(afloat6);
        float[] afloat7 = this.frustum[5];
        afloat7[0] = this.clipMatrix[3] + this.clipMatrix[2];
        afloat7[1] = this.clipMatrix[7] + this.clipMatrix[6];
        afloat7[2] = this.clipMatrix[11] + this.clipMatrix[10];
        afloat7[3] = this.clipMatrix[15] + this.clipMatrix[14];
        this.normalize(afloat7);
    }
}
