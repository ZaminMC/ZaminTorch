package net.minecraft.client.render.model;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.math.Vec3d;

public class Polygon {
    public Vertex[] vertices;
    public int vertexCount;
    private boolean flipNormal;

    public Polygon(Vertex[] vertices) {
        this.vertices = vertices;
        this.vertexCount = vertices.length;
    }

    public Polygon(Vertex[] vertices, int u1, int v1, int u2, int v2, float width, float height) {
        this(vertices);
        float f = 0.0F / width;
        float f1 = 0.0F / height;
        vertices[0] = vertices[0].remap(u2 / width - f, v1 / height + f1);
        vertices[1] = vertices[1].remap(u1 / width + f, v1 / height + f1);
        vertices[2] = vertices[2].remap(u1 / width + f, v2 / height - f1);
        vertices[3] = vertices[3].remap(u2 / width - f, v2 / height - f1);
    }

    public void flip() {
        Vertex[] avertex = new Vertex[this.vertices.length];

        for (int i = 0; i < this.vertices.length; i++) {
            avertex[i] = this.vertices[this.vertices.length - i - 1];
        }

        this.vertices = avertex;
    }

    public void compile(BufferBuilder bufferBuilder, float scale) {
        Vec3d vec3d = this.vertices[1].pos.subtractFrom(this.vertices[0].pos);
        Vec3d vec3d1 = this.vertices[1].pos.subtractFrom(this.vertices[2].pos);
        Vec3d vec3d2 = vec3d1.cross(vec3d).normalize();
        float f = (float)vec3d2.x;
        float f1 = (float)vec3d2.y;
        float f2 = (float)vec3d2.z;
        if (this.flipNormal) {
            f = -f;
            f1 = -f1;
            f2 = -f2;
        }

        bufferBuilder.begin(7, DefaultVertexFormat.ENTITY);

        for (int i = 0; i < 4; i++) {
            Vertex vertex = this.vertices[i];
            bufferBuilder.vertex(vertex.pos.x * scale, vertex.pos.y * scale, vertex.pos.z * scale).texture(vertex.u, vertex.v).normal(f, f1, f2).nextVertex();
        }

        Tesselator.getInstance().end();
    }
}
