package net.minecraft.client.render.model;

import net.minecraft.client.render.vertex.BufferBuilder;

public class Box {
    private Vertex[] vertices;
    private Polygon[] faces;
    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;
    public String id;

    public Box(ModelPart part, int textureU, int textureV, float minX, float minY, float minZ, int sizeX, int sizeY, int sizeZ, float increase) {
        this(part, textureU, textureV, minX, minY, minZ, sizeX, sizeY, sizeZ, increase, part.flipped);
    }

    public Box(ModelPart part, int textureU, int textureV, float minX, float minY, float minZ, int sizeX, int sizeY, int sizeZ, float increase, boolean flipped) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = minX + sizeX;
        this.maxY = minY + sizeY;
        this.maxZ = minZ + sizeZ;
        this.vertices = new Vertex[8];
        this.faces = new Polygon[6];
        float f = minX + sizeX;
        float f1 = minY + sizeY;
        float f2 = minZ + sizeZ;
        minX -= increase;
        minY -= increase;
        minZ -= increase;
        f += increase;
        f1 += increase;
        f2 += increase;
        if (flipped) {
            float f3 = f;
            f = minX;
            minX = f3;
        }

        Vertex vertex7 = new Vertex(minX, minY, minZ, 0.0F, 0.0F);
        Vertex vertex = new Vertex(f, minY, minZ, 0.0F, 8.0F);
        Vertex vertex1 = new Vertex(f, f1, minZ, 8.0F, 8.0F);
        Vertex vertex2 = new Vertex(minX, f1, minZ, 8.0F, 0.0F);
        Vertex vertex3 = new Vertex(minX, minY, f2, 0.0F, 0.0F);
        Vertex vertex4 = new Vertex(f, minY, f2, 0.0F, 8.0F);
        Vertex vertex5 = new Vertex(f, f1, f2, 8.0F, 8.0F);
        Vertex vertex6 = new Vertex(minX, f1, f2, 8.0F, 0.0F);
        this.vertices[0] = vertex7;
        this.vertices[1] = vertex;
        this.vertices[2] = vertex1;
        this.vertices[3] = vertex2;
        this.vertices[4] = vertex3;
        this.vertices[5] = vertex4;
        this.vertices[6] = vertex5;
        this.vertices[7] = vertex6;
        this.faces[0] = new Polygon(
            new Vertex[]{vertex4, vertex, vertex1, vertex5},
            textureU + sizeZ + sizeX,
            textureV + sizeZ,
            textureU + sizeZ + sizeX + sizeZ,
            textureV + sizeZ + sizeY,
            part.textureWidth,
            part.textureHeight
        );
        this.faces[1] = new Polygon(
            new Vertex[]{vertex7, vertex3, vertex6, vertex2},
            textureU,
            textureV + sizeZ,
            textureU + sizeZ,
            textureV + sizeZ + sizeY,
            part.textureWidth,
            part.textureHeight
        );
        this.faces[2] = new Polygon(
            new Vertex[]{vertex4, vertex3, vertex7, vertex},
            textureU + sizeZ,
            textureV,
            textureU + sizeZ + sizeX,
            textureV + sizeZ,
            part.textureWidth,
            part.textureHeight
        );
        this.faces[3] = new Polygon(
            new Vertex[]{vertex1, vertex2, vertex6, vertex5},
            textureU + sizeZ + sizeX,
            textureV + sizeZ,
            textureU + sizeZ + sizeX + sizeX,
            textureV,
            part.textureWidth,
            part.textureHeight
        );
        this.faces[4] = new Polygon(
            new Vertex[]{vertex, vertex7, vertex2, vertex1},
            textureU + sizeZ,
            textureV + sizeZ,
            textureU + sizeZ + sizeX,
            textureV + sizeZ + sizeY,
            part.textureWidth,
            part.textureHeight
        );
        this.faces[5] = new Polygon(
            new Vertex[]{vertex3, vertex4, vertex5, vertex6},
            textureU + sizeZ + sizeX + sizeZ,
            textureV + sizeZ,
            textureU + sizeZ + sizeX + sizeZ + sizeX,
            textureV + sizeZ + sizeY,
            part.textureWidth,
            part.textureHeight
        );
        if (flipped) {
            for (int i = 0; i < this.faces.length; i++) {
                this.faces[i].flip();
            }
        }
    }

    public void compile(BufferBuilder bufferBuilder, float scale) {
        for (int i = 0; i < this.faces.length; i++) {
            this.faces[i].compile(bufferBuilder, scale);
        }
    }

    public Box setId(String id) {
        this.id = id;
        return this;
    }
}
