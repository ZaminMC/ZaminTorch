package net.minecraft.client.resource.model;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

public enum ModelRotation {
    X0_Y0(0, 0),
    X0_Y90(0, 90),
    X0_Y180(0, 180),
    X0_Y270(0, 270),
    X90_Y0(90, 0),
    X90_Y90(90, 90),
    X90_Y180(90, 180),
    X90_Y270(90, 270),
    X180_Y0(180, 0),
    X180_Y90(180, 90),
    X180_Y180(180, 180),
    X180_Y270(180, 270),
    X270_Y0(270, 0),
    X270_Y90(270, 90),
    X270_Y180(270, 180),
    X270_Y270(270, 270);

    private static final Map<Integer, ModelRotation> BY_INDEX = Maps.newHashMap();
    private final int index;
    private final Matrix4f rotation;
    private final int stepsX;
    private final int stepsY;

    private static int getIndex(int x, int y) {
        return x * 360 + y;
    }

    ModelRotation(int x, int y) {
        this.index = getIndex(x, y);
        this.rotation = new Matrix4f();
        Matrix4f matrix4f = new Matrix4f();
        matrix4f.setIdentity();
        Matrix4f.rotate(-x * (float) (Math.PI / 180.0), new Vector3f(1.0F, 0.0F, 0.0F), matrix4f, matrix4f);
        this.stepsX = MathHelper.abs(x / 90);
        Matrix4f matrix4f1 = new Matrix4f();
        matrix4f1.setIdentity();
        Matrix4f.rotate(-y * (float) (Math.PI / 180.0), new Vector3f(0.0F, 1.0F, 0.0F), matrix4f1, matrix4f1);
        this.stepsY = MathHelper.abs(y / 90);
        Matrix4f.mul(matrix4f1, matrix4f, this.rotation);
    }

    public Matrix4f getRotationVector() {
        return this.rotation;
    }

    public Direction clockwise(Direction dir) {
        Direction direction = dir;

        for (int i = 0; i < this.stepsX; i++) {
            direction = direction.clockwise(Direction.Axis.X);
        }

        if (direction.getAxis() != Direction.Axis.Y) {
            for (int j = 0; j < this.stepsY; j++) {
                direction = direction.clockwise(Direction.Axis.Y);
            }
        }

        return direction;
    }

    public int apply(Direction dir, int vertex) {
        int i = vertex;
        if (dir.getAxis() == Direction.Axis.X) {
            i = (i + this.stepsX) % 4;
        }

        Direction direction = dir;

        for (int j = 0; j < this.stepsX; j++) {
            direction = direction.clockwise(Direction.Axis.X);
        }

        if (direction.getAxis() == Direction.Axis.Y) {
            i = (i + this.stepsY) % 4;
        }

        return i;
    }

    public static ModelRotation by(int x, int y) {
        return BY_INDEX.get(getIndex(MathHelper.floorMod(x, 360), MathHelper.floorMod(y, 360)));
    }

    static {
        for (ModelRotation modelrotation : values()) {
            BY_INDEX.put(modelrotation.index, modelrotation);
        }
    }
}
