package net.minecraft.realms;

import net.minecraft.client.render.vertex.Tesselator;

public class Tezzelator {
    public static Tesselator tesselator = Tesselator.getInstance();
    public static final Tezzelator instance = new Tezzelator();

    public void end() {
        tesselator.end();
    }

    public Tezzelator vertex(double x, double y, double z) {
        tesselator.getBuffer().vertex(x, y, z);
        return this;
    }

    public void color(float r, float g, float b, float a) {
        tesselator.getBuffer().color(r, g, b, a);
    }

    public void tex2(short u, short v) {
        tesselator.getBuffer().texture(u, v);
    }

    public void normal(float x, float y, float z) {
        tesselator.getBuffer().normal(x, y, z);
    }

    public void begin(int drawMode, RealmsVertexFormat format) {
        tesselator.getBuffer().begin(drawMode, format.getVertexFormat());
    }

    public void endVertex() {
        tesselator.getBuffer().nextVertex();
    }

    public void offset(double offsetX, double offsetY, double offsetZ) {
        tesselator.getBuffer().offset(offsetX, offsetY, offsetZ);
    }

    public RealmsBufferBuilder color(int r, int g, int b, int a) {
        return new RealmsBufferBuilder(tesselator.getBuffer().color(r, g, b, a));
    }

    public Tezzelator tex(double u, double v) {
        tesselator.getBuffer().texture(u, v);
        return this;
    }
}
