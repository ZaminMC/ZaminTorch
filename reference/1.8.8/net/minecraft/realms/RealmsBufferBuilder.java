package net.minecraft.realms;

import java.nio.ByteBuffer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.VertexFormat;

public class RealmsBufferBuilder {
    private BufferBuilder f_7321008;

    public RealmsBufferBuilder(BufferBuilder bufferBuilder) {
        this.f_7321008 = bufferBuilder;
    }

    public RealmsBufferBuilder from(BufferBuilder bufferBuilder) {
        this.f_7321008 = bufferBuilder;
        return this;
    }

    public void sortQuads(float cameraX, float cameraY, float cameraZ) {
        this.f_7321008.sortQuads(cameraX, cameraY, cameraZ);
    }

    public void fixupQuadColor(int rgb) {
        this.f_7321008.setQuadColor(rgb);
    }

    public ByteBuffer getBuffer() {
        return this.f_7321008.getBuffer();
    }

    public void postNormal(float x, float y, float z) {
        this.f_7321008.postNormal(x, y, z);
    }

    public int getDrawMode() {
        return this.f_7321008.getDrawMode();
    }

    public void offset(double offsetX, double offsetY, double offsetZ) {
        this.f_7321008.offset(offsetX, offsetY, offsetZ);
    }

    public void restoreState(BufferBuilder.State state) {
        this.f_7321008.setState(state);
    }

    public void endVertex() {
        this.f_7321008.nextVertex();
    }

    public RealmsBufferBuilder normal(float x, float y, float z) {
        return this.from(this.f_7321008.normal(x, y, z));
    }

    public void end() {
        this.f_7321008.end();
    }

    public void begin(int drawMode, VertexFormat format) {
        this.f_7321008.begin(drawMode, format);
    }

    public RealmsBufferBuilder color(int r, int g, int b, int a) {
        return this.from(this.f_7321008.color(r, g, b, a));
    }

    public void faceTex2(int r, int g, int b, int a) {
        this.f_7321008.lightColor(r, g, b, a);
    }

    public void postProcessFacePosition(double x, double y, double z) {
        this.f_7321008.postPosition(x, y, z);
    }

    public void fixupVertexColor(float r, float g, float b, int index) {
        this.f_7321008.setColor(r, g, b, index);
    }

    public RealmsBufferBuilder color(float r, float g, float b, float a) {
        return this.from(this.f_7321008.color(r, g, b, a));
    }

    public RealmsVertexFormat getVertexFormat() {
        return new RealmsVertexFormat(this.f_7321008.getFormat());
    }

    public void faceTint(float r, float g, float b, int index) {
        this.f_7321008.multiplyColor(r, g, b, index);
    }

    public RealmsBufferBuilder tex2(int u, int v) {
        return this.from(this.f_7321008.texture(u, v));
    }

    public void putBulkData(int[] vertices) {
        this.f_7321008.vertices(vertices);
    }

    public RealmsBufferBuilder tex(double u, double v) {
        return this.from(this.f_7321008.texture(u, v));
    }

    public int getVertexCount() {
        return this.f_7321008.getVertexCount();
    }

    public void clear() {
        this.f_7321008.clear();
    }

    public RealmsBufferBuilder vertex(double x, double y, double z) {
        return this.from(this.f_7321008.vertex(x, y, z));
    }

    public void fixupQuadColor(float r, float g, float b) {
        this.f_7321008.setQuadColor(r, g, b);
    }

    public void noColor() {
        this.f_7321008.uncolored();
    }
}
