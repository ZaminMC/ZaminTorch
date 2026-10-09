package net.minecraft.client.render.vertex;

import com.google.common.collect.Lists;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VertexFormat {
    private static final Logger LOGGER = LogManager.getLogger();
    private final List<VertexFormatElement> elements = Lists.newArrayList();
    private final List<Integer> offsets = Lists.newArrayList();
    private int size = 0;
    private int colorOffset = -1;
    private List<Integer> offsetsUv = Lists.newArrayList();
    private int normalOffset = -1;

    public VertexFormat(VertexFormat format) {
        this();

        for (int i = 0; i < format.getElementCount(); i++) {
            this.addElement(format.getElement(i));
        }

        this.size = format.getVertexSize();
    }

    public VertexFormat() {
    }

    public void clear() {
        this.elements.clear();
        this.offsets.clear();
        this.colorOffset = -1;
        this.offsetsUv.clear();
        this.normalOffset = -1;
        this.size = 0;
    }

    public VertexFormat addElement(VertexFormatElement element) {
        if (element.isPosition() && this.hasPositionElement()) {
            LOGGER.warn("VertexFormat error: Trying to add a position VertexFormatElement when one already exists, ignoring.");
            return this;
        }

        this.elements.add(element);
        this.offsets.add(this.size);
        switch (element.getUsage()) {
            case NORMAL:
                this.normalOffset = this.size;
                break;
            case COLOR:
                this.colorOffset = this.size;
                break;
            case UV:
                this.offsetsUv.add(element.getIndex(), this.size);
        }

        this.size = this.size + element.getByteSize();
        return this;
    }

    public boolean hasNormal() {
        return this.normalOffset >= 0;
    }

    public int getNormalOffset() {
        return this.normalOffset;
    }

    public boolean hasColor() {
        return this.colorOffset >= 0;
    }

    public int getColorOffset() {
        return this.colorOffset;
    }

    public boolean hasUv(int index) {
        return this.offsetsUv.size() - 1 >= index;
    }

    public int getUvOffset(int index) {
        return this.offsetsUv.get(index);
    }

    @Override
    public String toString() {
        String s = "format: " + this.elements.size() + " elements: ";

        for (int i = 0; i < this.elements.size(); i++) {
            s = s + this.elements.get(i).toString();
            if (i != this.elements.size() - 1) {
                s = s + " ";
            }
        }

        return s;
    }

    private boolean hasPositionElement() {
        int i = 0;

        for (int j = this.elements.size(); i < j; i++) {
            VertexFormatElement vertexformatelement = this.elements.get(i);
            if (vertexformatelement.isPosition()) {
                return true;
            }
        }

        return false;
    }

    public int getIntSize() {
        return this.getVertexSize() / 4;
    }

    public int getVertexSize() {
        return this.size;
    }

    public List<VertexFormatElement> getElements() {
        return this.elements;
    }

    public int getElementCount() {
        return this.elements.size();
    }

    public VertexFormatElement getElement(int index) {
        return this.elements.get(index);
    }

    public int getOffset(int index) {
        return this.offsets.get(index);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            VertexFormat vertexformat = (VertexFormat)object;
            return this.size == vertexformat.size && this.elements.equals(vertexformat.elements) && this.offsets.equals(vertexformat.offsets);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.elements.hashCode();
        i = 31 * i + this.offsets.hashCode();
        return 31 * i + this.size;
    }
}
