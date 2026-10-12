package net.minecraft.client.render.vertex;

public class DefaultVertexFormat {
    public static final VertexFormat BLOCK = new VertexFormat();
    public static final VertexFormat BLOCK_NORMALS = new VertexFormat();
    public static final VertexFormat ENTITY = new VertexFormat();
    public static final VertexFormat PARTICLE = new VertexFormat();
    public static final VertexFormat POSITION = new VertexFormat();
    public static final VertexFormat POSITION_COLOR = new VertexFormat();
    public static final VertexFormat POSITION_TEX = new VertexFormat();
    public static final VertexFormat POSITION_NORMAL = new VertexFormat();
    public static final VertexFormat POSITION_TEX_COLOR = new VertexFormat();
    public static final VertexFormat POSITION_TEX_NORMAL = new VertexFormat();
    public static final VertexFormat POSITION_TEX2_COLOR = new VertexFormat();
    public static final VertexFormat POSITION_TEX_COLOR_NORMAL = new VertexFormat();
    public static final VertexFormatElement POSITION_ELEMENT = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3);
    public static final VertexFormatElement COLOR_ELEMENT = new VertexFormatElement(0, VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR, 4);
    public static final VertexFormatElement UV0_ELEMENT = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement UV1_ELEMENT = new VertexFormatElement(1, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement NORMAL_ELEMENT = new VertexFormatElement(0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.NORMAL, 3);
    public static final VertexFormatElement PADDING_ELEMENT = new VertexFormatElement(0, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.PADDING, 1);

    static {
        BLOCK.addElement(POSITION_ELEMENT);
        BLOCK.addElement(COLOR_ELEMENT);
        BLOCK.addElement(UV0_ELEMENT);
        BLOCK.addElement(UV1_ELEMENT);
        BLOCK_NORMALS.addElement(POSITION_ELEMENT);
        BLOCK_NORMALS.addElement(COLOR_ELEMENT);
        BLOCK_NORMALS.addElement(UV0_ELEMENT);
        BLOCK_NORMALS.addElement(NORMAL_ELEMENT);
        BLOCK_NORMALS.addElement(PADDING_ELEMENT);
        ENTITY.addElement(POSITION_ELEMENT);
        ENTITY.addElement(UV0_ELEMENT);
        ENTITY.addElement(NORMAL_ELEMENT);
        ENTITY.addElement(PADDING_ELEMENT);
        PARTICLE.addElement(POSITION_ELEMENT);
        PARTICLE.addElement(UV0_ELEMENT);
        PARTICLE.addElement(COLOR_ELEMENT);
        PARTICLE.addElement(UV1_ELEMENT);
        POSITION.addElement(POSITION_ELEMENT);
        POSITION_COLOR.addElement(POSITION_ELEMENT);
        POSITION_COLOR.addElement(COLOR_ELEMENT);
        POSITION_TEX.addElement(POSITION_ELEMENT);
        POSITION_TEX.addElement(UV0_ELEMENT);
        POSITION_NORMAL.addElement(POSITION_ELEMENT);
        POSITION_NORMAL.addElement(NORMAL_ELEMENT);
        POSITION_NORMAL.addElement(PADDING_ELEMENT);
        POSITION_TEX_COLOR.addElement(POSITION_ELEMENT);
        POSITION_TEX_COLOR.addElement(UV0_ELEMENT);
        POSITION_TEX_COLOR.addElement(COLOR_ELEMENT);
        POSITION_TEX_NORMAL.addElement(POSITION_ELEMENT);
        POSITION_TEX_NORMAL.addElement(UV0_ELEMENT);
        POSITION_TEX_NORMAL.addElement(NORMAL_ELEMENT);
        POSITION_TEX_NORMAL.addElement(PADDING_ELEMENT);
        POSITION_TEX2_COLOR.addElement(POSITION_ELEMENT);
        POSITION_TEX2_COLOR.addElement(UV0_ELEMENT);
        POSITION_TEX2_COLOR.addElement(UV1_ELEMENT);
        POSITION_TEX2_COLOR.addElement(COLOR_ELEMENT);
        POSITION_TEX_COLOR_NORMAL.addElement(POSITION_ELEMENT);
        POSITION_TEX_COLOR_NORMAL.addElement(UV0_ELEMENT);
        POSITION_TEX_COLOR_NORMAL.addElement(COLOR_ELEMENT);
        POSITION_TEX_COLOR_NORMAL.addElement(NORMAL_ELEMENT);
        POSITION_TEX_COLOR_NORMAL.addElement(PADDING_ELEMENT);
    }
}
