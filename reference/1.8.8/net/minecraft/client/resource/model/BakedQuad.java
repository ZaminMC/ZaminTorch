package net.minecraft.client.resource.model;

import net.minecraft.util.math.Direction;

public class BakedQuad {
    protected final int[] vertices;
    protected final int tintIndex;
    protected final Direction face;

    public BakedQuad(int[] vertices, int tintIndex, Direction face) {
        this.vertices = vertices;
        this.tintIndex = tintIndex;
        this.face = face;
    }

    public int[] getVertices() {
        return this.vertices;
    }

    public boolean hasTint() {
        return this.tintIndex != -1;
    }

    public int getTintIndex() {
        return this.tintIndex;
    }

    public Direction getFace() {
        return this.face;
    }
}
