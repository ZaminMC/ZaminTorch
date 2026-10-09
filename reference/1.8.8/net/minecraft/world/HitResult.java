package net.minecraft.world;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class HitResult {
    private BlockPos pos;
    public HitResult.Type type;
    public Direction face;
    /**
     * The hit's precise position on the face of the block or entity.
     */
    public Vec3d facePos;
    public Entity entity;

    public HitResult(Vec3d offset, Direction face, BlockPos pos) {
        this(HitResult.Type.BLOCK, offset, face, pos);
    }

    public HitResult(Vec3d offset, Direction face) {
        this(HitResult.Type.BLOCK, offset, face, BlockPos.ORIGIN);
    }

    public HitResult(Entity entity) {
        this(entity, new Vec3d(entity.x, entity.y, entity.z));
    }

    public HitResult(HitResult.Type type, Vec3d facePos, Direction face, BlockPos pos) {
        this.type = type;
        this.pos = pos;
        this.face = face;
        this.facePos = new Vec3d(facePos.x, facePos.y, facePos.z);
    }

    public HitResult(Entity entity, Vec3d facePos) {
        this.type = HitResult.Type.ENTITY;
        this.entity = entity;
        this.facePos = facePos;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public String toString() {
        return "HitResult{type=" + this.type + ", blockpos=" + this.pos + ", f=" + this.face + ", pos=" + this.facePos + ", entity=" + this.entity + '}';
    }

    public enum Type {
        MISS,
        BLOCK,
        ENTITY;
    }
}
