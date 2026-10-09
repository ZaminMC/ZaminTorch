package net.minecraft.client.render.model;

import net.minecraft.util.math.Vec3d;

public class Vertex {
    public Vec3d pos;
    public float u;
    public float v;

    public Vertex(float x, float y, float z, float u, float v) {
        this(new Vec3d(x, y, z), u, v);
    }

    public Vertex remap(float u, float v) {
        return new Vertex(this, u, v);
    }

    public Vertex(Vertex pos, float u, float v) {
        this.pos = pos.pos;
        this.u = u;
        this.v = v;
    }

    public Vertex(Vec3d pos, float u, float v) {
        this.pos = pos;
        this.u = u;
        this.v = v;
    }
}
