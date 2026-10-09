package net.minecraft.realms;

import net.minecraft.client.render.vertex.VertexFormatElement;

public class RealmsVertexFormatElement {
    private VertexFormatElement inner;

    public RealmsVertexFormatElement(VertexFormatElement vertexFormatElement) {
        this.inner = vertexFormatElement;
    }

    public VertexFormatElement getVertexFormatElement() {
        return this.inner;
    }

    public boolean isPosition() {
        return this.inner.isPosition();
    }

    public int getIndex() {
        return this.inner.getIndex();
    }

    public int getByteSize() {
        return this.inner.getByteSize();
    }

    public int getCount() {
        return this.inner.getCount();
    }

    @Override
    public int hashCode() {
        return this.inner.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return this.inner.equals(object);
    }

    @Override
    public String toString() {
        return this.inner.toString();
    }
}
