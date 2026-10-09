package net.minecraft.realms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.client.render.vertex.VertexFormatElement;

public class RealmsVertexFormat {
    private VertexFormat inner;

    public RealmsVertexFormat(VertexFormat vertexFormat) {
        this.inner = vertexFormat;
    }

    public RealmsVertexFormat from(VertexFormat inner) {
        this.inner = inner;
        return this;
    }

    public VertexFormat getVertexFormat() {
        return this.inner;
    }

    public void clear() {
        this.inner.clear();
    }

    public int getUvOffset(int index) {
        return this.inner.getUvOffset(index);
    }

    public int getElementCount() {
        return this.inner.getElementCount();
    }

    public boolean hasColor() {
        return this.inner.hasColor();
    }

    public boolean hasUv(int index) {
        return this.inner.hasUv(index);
    }

    public RealmsVertexFormatElement getElement(int index) {
        return new RealmsVertexFormatElement(this.inner.getElement(index));
    }

    public RealmsVertexFormat addElement(RealmsVertexFormatElement element) {
        return this.from(this.inner.addElement(element.getVertexFormatElement()));
    }

    public int getColorOffset() {
        return this.inner.getColorOffset();
    }

    public List<RealmsVertexFormatElement> getElements() {
        List<RealmsVertexFormatElement> list = new ArrayList<>();

        for (VertexFormatElement vertexformatelement : this.inner.getElements()) {
            list.add(new RealmsVertexFormatElement(vertexformatelement));
        }

        return list;
    }

    public boolean hasNormal() {
        return this.inner.hasNormal();
    }

    public int getVertexSize() {
        return this.inner.getVertexSize();
    }

    public int getOffset(int index) {
        return this.inner.getOffset(index);
    }

    public int getNormalOffset() {
        return this.inner.getNormalOffset();
    }

    public int getIntegerSize() {
        return this.inner.getIntSize();
    }

    @Override
    public boolean equals(Object object) {
        return this.inner.equals(object);
    }

    @Override
    public int hashCode() {
        return this.inner.hashCode();
    }

    @Override
    public String toString() {
        return this.inner.toString();
    }
}
