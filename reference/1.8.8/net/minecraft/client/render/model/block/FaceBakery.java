package net.minecraft.client.render.model.block;

import net.minecraft.client.render.block.BlockFace;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.client.resource.model.ModelRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class FaceBakery {
    private static final float SCALE_22_5_DEGREES = 1.0F / (float)Math.cos((float) (Math.PI / 8)) - 1.0F;
    private static final float SCALE_45_DEGREES = 1.0F / (float)Math.cos((float) (Math.PI / 4)) - 1.0F;

    public BakedQuad bakeQuad(
        Vector3f from,
        Vector3f to,
        BlockElementFace elementFace,
        TextureAtlasSprite blockSprite,
        Direction face,
        ModelRotation rotation,
        BlockElementRotation elementRotation,
        boolean uvLock,
        boolean shade
    ) {
        int[] aint = this.bakeVertices(elementFace, blockSprite, face, this.getShape(from, to), rotation, elementRotation, uvLock, shade);
        Direction direction = getFacing(aint);
        if (uvLock) {
            this.calculateTextureCoords(aint, direction, elementFace.textureCoords, blockSprite);
        }

        if (elementRotation == null) {
            this.calculateWinding(aint, direction);
        }

        return new BakedQuad(aint, elementFace.tintIndex, direction);
    }

    private int[] bakeVertices(
        BlockElementFace elementFace,
        TextureAtlasSprite blockSprite,
        Direction face,
        float[] shape,
        ModelRotation rotation,
        BlockElementRotation elementRotation,
        boolean uvLock,
        boolean shade
    ) {
        int[] aint = new int[28];

        for (int i = 0; i < 4; i++) {
            this.bakeVertex(aint, i, face, elementFace, shape, blockSprite, rotation, elementRotation, uvLock, shade);
        }

        return aint;
    }

    private int getShadeValue(Direction face) {
        float f = this.getShade(face);
        int i = MathHelper.clamp((int)(f * 255.0F), 0, 255);
        return 0xFF000000 | i << 16 | i << 8 | i;
    }

    private float getShade(Direction face) {
        switch (face) {
            case DOWN:
                return 0.5F;
            case UP:
                return 1.0F;
            case NORTH:
            case SOUTH:
                return 0.8F;
            case WEST:
            case EAST:
                return 0.6F;
            default:
                return 1.0F;
        }
    }

    private float[] getShape(Vector3f from, Vector3f to) {
        float[] afloat = new float[Direction.values().length];
        afloat[BlockFace.Constants.MIN_X] = from.x / 16.0F;
        afloat[BlockFace.Constants.MIN_Y] = from.y / 16.0F;
        afloat[BlockFace.Constants.MIN_Z] = from.z / 16.0F;
        afloat[BlockFace.Constants.MAX_X] = to.x / 16.0F;
        afloat[BlockFace.Constants.MAX_Y] = to.y / 16.0F;
        afloat[BlockFace.Constants.MAX_Z] = to.z / 16.0F;
        return afloat;
    }

    private void bakeVertex(
        int[] vertices,
        int index,
        Direction face,
        BlockElementFace elementFace,
        float[] shape,
        TextureAtlasSprite blockAtlas,
        ModelRotation rotation,
        BlockElementRotation elementRotation,
        boolean uvLock,
        boolean shade
    ) {
        Direction direction = rotation.clockwise(face);
        int i = shade ? this.getShadeValue(direction) : -1;
        BlockFace.Vertex blockface$vertex = BlockFace.byDirection(face).getVertex(index);
        Vector3f vector3f = new Vector3f(shape[blockface$vertex.x], shape[blockface$vertex.y], shape[blockface$vertex.z]);
        this.applyElementRotation(vector3f, elementRotation);
        int j = this.applyModelRotation(vector3f, face, index, rotation, uvLock);
        this.fillVertex(vertices, j, index, vector3f, i, blockAtlas, elementFace.textureCoords);
    }

    private void fillVertex(
        int[] vertices, int rotatedIndex, int index, Vector3f shape, int shadeValue, TextureAtlasSprite blockSprite, BlockElementTexture textureCoords
    ) {
        int i = rotatedIndex * 7;
        vertices[i] = Float.floatToRawIntBits(shape.x);
        vertices[i + 1] = Float.floatToRawIntBits(shape.y);
        vertices[i + 2] = Float.floatToRawIntBits(shape.z);
        vertices[i + 3] = shadeValue;
        vertices[i + 4] = Float.floatToRawIntBits(blockSprite.getU(textureCoords.getU(index)));
        vertices[i + 4 + 1] = Float.floatToRawIntBits(blockSprite.getV(textureCoords.getV(index)));
    }

    private void applyElementRotation(Vector3f shape, BlockElementRotation rotation) {
        if (rotation != null) {
            Matrix4f matrix4f = this.identityMatrix();
            Vector3f vector3f = new Vector3f(0.0F, 0.0F, 0.0F);
            switch (rotation.axis) {
                case X:
                    Matrix4f.rotate(rotation.angle * (float) (Math.PI / 180.0), new Vector3f(1.0F, 0.0F, 0.0F), matrix4f, matrix4f);
                    vector3f.set(0.0F, 1.0F, 1.0F);
                    break;
                case Y:
                    Matrix4f.rotate(rotation.angle * (float) (Math.PI / 180.0), new Vector3f(0.0F, 1.0F, 0.0F), matrix4f, matrix4f);
                    vector3f.set(1.0F, 0.0F, 1.0F);
                    break;
                case Z:
                    Matrix4f.rotate(rotation.angle * (float) (Math.PI / 180.0), new Vector3f(0.0F, 0.0F, 1.0F), matrix4f, matrix4f);
                    vector3f.set(1.0F, 1.0F, 0.0F);
            }

            if (rotation.rescale) {
                if (Math.abs(rotation.angle) == 22.5F) {
                    vector3f.scale(SCALE_22_5_DEGREES);
                } else {
                    vector3f.scale(SCALE_45_DEGREES);
                }

                Vector3f.add(vector3f, new Vector3f(1.0F, 1.0F, 1.0F), vector3f);
            } else {
                vector3f.set(1.0F, 1.0F, 1.0F);
            }

            this.rotateVertex(shape, new Vector3f(rotation.origin), matrix4f, vector3f);
        }
    }

    public int applyModelRotation(Vector3f shape, Direction face, int index, ModelRotation rotation, boolean uvLock) {
        if (rotation == ModelRotation.X0_Y0) {
            return index;
        }

        this.rotateVertex(shape, new Vector3f(0.5F, 0.5F, 0.5F), rotation.getRotationVector(), new Vector3f(1.0F, 1.0F, 1.0F));
        return rotation.apply(face, index);
    }

    private void rotateVertex(Vector3f shape, Vector3f origin, Matrix4f rotation, Vector3f scale) {
        Vector4f vector4f = new Vector4f(shape.x - origin.x, shape.y - origin.y, shape.z - origin.z, 1.0F);
        Matrix4f.transform(rotation, vector4f, vector4f);
        vector4f.x = vector4f.x * scale.x;
        vector4f.y = vector4f.y * scale.y;
        vector4f.z = vector4f.z * scale.z;
        shape.set(vector4f.x + origin.x, vector4f.y + origin.y, vector4f.z + origin.z);
    }

    private Matrix4f identityMatrix() {
        Matrix4f matrix4f = new Matrix4f();
        matrix4f.setIdentity();
        return matrix4f;
    }

    public static Direction getFacing(int[] vertices) {
        Vector3f vector3f = new Vector3f(Float.intBitsToFloat(vertices[0]), Float.intBitsToFloat(vertices[1]), Float.intBitsToFloat(vertices[2]));
        Vector3f vector3f1 = new Vector3f(Float.intBitsToFloat(vertices[7]), Float.intBitsToFloat(vertices[8]), Float.intBitsToFloat(vertices[9]));
        Vector3f vector3f2 = new Vector3f(Float.intBitsToFloat(vertices[14]), Float.intBitsToFloat(vertices[15]), Float.intBitsToFloat(vertices[16]));
        Vector3f vector3f3 = new Vector3f();
        Vector3f vector3f4 = new Vector3f();
        Vector3f vector3f5 = new Vector3f();
        Vector3f.sub(vector3f, vector3f1, vector3f3);
        Vector3f.sub(vector3f2, vector3f1, vector3f4);
        Vector3f.cross(vector3f4, vector3f3, vector3f5);
        float f = (float)Math.sqrt(vector3f5.x * vector3f5.x + vector3f5.y * vector3f5.y + vector3f5.z * vector3f5.z);
        vector3f5.x /= f;
        vector3f5.y /= f;
        vector3f5.z /= f;
        Direction direction = null;
        float f1 = 0.0F;

        for (Direction direction1 : Direction.values()) {
            Vec3i vec3i = direction1.getNormal();
            Vector3f vector3f6 = new Vector3f(vec3i.getX(), vec3i.getY(), vec3i.getZ());
            float f2 = Vector3f.dot(vector3f5, vector3f6);
            if (f2 >= 0.0F && f2 > f1) {
                f1 = f2;
                direction = direction1;
            }
        }

        return direction == null ? Direction.UP : direction;
    }

    public void calculateTextureCoords(int[] vertices, Direction face, BlockElementTexture textureCoords, TextureAtlasSprite blockAtlas) {
        for (int i = 0; i < 4; i++) {
            this.calculateTextureCoords(i, vertices, face, textureCoords, blockAtlas);
        }
    }

    private void calculateWinding(int[] vertices, Direction facing) {
        int[] aint = new int[vertices.length];
        System.arraycopy(vertices, 0, aint, 0, vertices.length);
        float[] afloat = new float[Direction.values().length];
        afloat[BlockFace.Constants.MIN_X] = 999.0F;
        afloat[BlockFace.Constants.MIN_Y] = 999.0F;
        afloat[BlockFace.Constants.MIN_Z] = 999.0F;
        afloat[BlockFace.Constants.MAX_X] = -999.0F;
        afloat[BlockFace.Constants.MAX_Y] = -999.0F;
        afloat[BlockFace.Constants.MAX_Z] = -999.0F;

        for (int i = 0; i < 4; i++) {
            int j = 7 * i;
            float f = Float.intBitsToFloat(aint[j]);
            float f1 = Float.intBitsToFloat(aint[j + 1]);
            float f2 = Float.intBitsToFloat(aint[j + 2]);
            if (f < afloat[BlockFace.Constants.MIN_X]) {
                afloat[BlockFace.Constants.MIN_X] = f;
            }

            if (f1 < afloat[BlockFace.Constants.MIN_Y]) {
                afloat[BlockFace.Constants.MIN_Y] = f1;
            }

            if (f2 < afloat[BlockFace.Constants.MIN_Z]) {
                afloat[BlockFace.Constants.MIN_Z] = f2;
            }

            if (f > afloat[BlockFace.Constants.MAX_X]) {
                afloat[BlockFace.Constants.MAX_X] = f;
            }

            if (f1 > afloat[BlockFace.Constants.MAX_Y]) {
                afloat[BlockFace.Constants.MAX_Y] = f1;
            }

            if (f2 > afloat[BlockFace.Constants.MAX_Z]) {
                afloat[BlockFace.Constants.MAX_Z] = f2;
            }
        }

        BlockFace blockface = BlockFace.byDirection(facing);

        for (int i1 = 0; i1 < 4; i1++) {
            int j1 = 7 * i1;
            BlockFace.Vertex blockface$vertex = blockface.getVertex(i1);
            float f8 = afloat[blockface$vertex.x];
            float f3 = afloat[blockface$vertex.y];
            float f4 = afloat[blockface$vertex.z];
            vertices[j1] = Float.floatToRawIntBits(f8);
            vertices[j1 + 1] = Float.floatToRawIntBits(f3);
            vertices[j1 + 2] = Float.floatToRawIntBits(f4);

            for (int k = 0; k < 4; k++) {
                int l = 7 * k;
                float f5 = Float.intBitsToFloat(aint[l]);
                float f6 = Float.intBitsToFloat(aint[l + 1]);
                float f7 = Float.intBitsToFloat(aint[l + 2]);
                if (MathHelper.equalsApproximate(f8, f5) && MathHelper.equalsApproximate(f3, f6) && MathHelper.equalsApproximate(f4, f7)) {
                    vertices[j1 + 4] = aint[l + 4];
                    vertices[j1 + 4 + 1] = aint[l + 4 + 1];
                }
            }
        }
    }

    private void calculateTextureCoords(int index, int[] vertices, Direction face, BlockElementTexture textureCoords, TextureAtlasSprite blockAtlas) {
        int i = 7 * index;
        float f = Float.intBitsToFloat(vertices[i]);
        float f1 = Float.intBitsToFloat(vertices[i + 1]);
        float f2 = Float.intBitsToFloat(vertices[i + 2]);
        if (f < -0.1F || f >= 1.1F) {
            f -= MathHelper.floor(f);
        }

        if (f1 < -0.1F || f1 >= 1.1F) {
            f1 -= MathHelper.floor(f1);
        }

        if (f2 < -0.1F || f2 >= 1.1F) {
            f2 -= MathHelper.floor(f2);
        }

        float f3 = 0.0F;
        float f4 = 0.0F;
        switch (face) {
            case DOWN:
                f3 = f * 16.0F;
                f4 = (1.0F - f2) * 16.0F;
                break;
            case UP:
                f3 = f * 16.0F;
                f4 = f2 * 16.0F;
                break;
            case NORTH:
                f3 = (1.0F - f) * 16.0F;
                f4 = (1.0F - f1) * 16.0F;
                break;
            case SOUTH:
                f3 = f * 16.0F;
                f4 = (1.0F - f1) * 16.0F;
                break;
            case WEST:
                f3 = f2 * 16.0F;
                f4 = (1.0F - f1) * 16.0F;
                break;
            case EAST:
                f3 = (1.0F - f2) * 16.0F;
                f4 = (1.0F - f1) * 16.0F;
        }

        int j = textureCoords.reverseIndex(index) * 7;
        vertices[j + 4] = Float.floatToRawIntBits(blockAtlas.getU(f3));
        vertices[j + 4 + 1] = Float.floatToRawIntBits(blockAtlas.getV(f4));
    }
}
