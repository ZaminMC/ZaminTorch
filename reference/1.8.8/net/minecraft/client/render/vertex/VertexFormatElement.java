package net.minecraft.client.render.vertex;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VertexFormatElement {
    private static final Logger LOGGER = LogManager.getLogger();
    private final VertexFormatElement.Type type;
    private final VertexFormatElement.Usage usage;
    private int index;
    private int count;

    public VertexFormatElement(int index, VertexFormatElement.Type type, VertexFormatElement.Usage usage, int count) {
        if (!this.supportsUsage(index, usage)) {
            LOGGER.warn("Multiple vertex elements of the same type other than UVs are not supported. Forcing type to UV.");
            this.usage = VertexFormatElement.Usage.UV;
        } else {
            this.usage = usage;
        }

        this.type = type;
        this.index = index;
        this.count = count;
    }

    private final boolean supportsUsage(int index, VertexFormatElement.Usage usage) {
        return index == 0 || usage == VertexFormatElement.Usage.UV;
    }

    public final VertexFormatElement.Type getType() {
        return this.type;
    }

    public final VertexFormatElement.Usage getUsage() {
        return this.usage;
    }

    public final int getCount() {
        return this.count;
    }

    public final int getIndex() {
        return this.index;
    }

    @Override
    public String toString() {
        return this.count + "," + this.usage.getName() + "," + this.type.getName();
    }

    public final int getByteSize() {
        return this.type.getSize() * this.count;
    }

    public final boolean isPosition() {
        return this.usage == VertexFormatElement.Usage.POSITION;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            VertexFormatElement vertexformatelement = (VertexFormatElement)object;
            return this.count == vertexformatelement.count
                && this.index == vertexformatelement.index
                && this.type == vertexformatelement.type
                && this.usage == vertexformatelement.usage;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.type.hashCode();
        i = 31 * i + this.usage.hashCode();
        i = 31 * i + this.index;
        return 31 * i + this.count;
    }

    public enum Type {
        FLOAT(4, "Float", 5126),
        UBYTE(1, "Unsigned Byte", 5121),
        BYTE(1, "Byte", 5120),
        USHORT(2, "Unsigned Short", 5123),
        SHORT(2, "Short", 5122),
        UINT(4, "Unsigned Int", 5125),
        INT(4, "Int", 5124);

        private final int size;
        private final String name;
        private final int glCode;

        Type(int size, String name, int glCode) {
            this.size = size;
            this.name = name;
            this.glCode = glCode;
        }

        public int getSize() {
            return this.size;
        }

        public String getName() {
            return this.name;
        }

        public int getGlCode() {
            return this.glCode;
        }
    }

    public enum Usage {
        POSITION("Position"),
        NORMAL("Normal"),
        COLOR("Vertex Color"),
        UV("UV"),
        MATRIX("Bone Matrix"),
        BLEND_WEIGHT("Blend Weight"),
        PADDING("Padding");

        private final String name;

        Usage(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }
}
