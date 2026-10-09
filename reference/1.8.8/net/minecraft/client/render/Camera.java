package net.minecraft.client.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.block.Block;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class Camera {
    private static final IntBuffer glIntBuffer = MemoryTracker.createIntBuffer(16);
    private static final FloatBuffer glFloatBuffer1 = MemoryTracker.createFloatBuffer(16);
    private static final FloatBuffer glFloatBuffer2 = MemoryTracker.createFloatBuffer(16);
    private static final FloatBuffer POSITION_BUFFER = MemoryTracker.createFloatBuffer(3);
    private static Vec3d offset = new Vec3d(0.0, 0.0, 0.0);
    private static float dx;
    private static float dy;
    private static float dz;
    private static float forwards;
    private static float sideways;

    public static void setup(PlayerEntity camera, boolean thirdPerson) {
        GlStateManager.getFloat(2982, glFloatBuffer1);
        GlStateManager.getFloat(2983, glFloatBuffer2);
        GL11.glGetInteger(2978, glIntBuffer);
        float f = (glIntBuffer.get(0) + glIntBuffer.get(2)) / 2;
        float f1 = (glIntBuffer.get(1) + glIntBuffer.get(3)) / 2;
        GLU.gluUnProject(f, f1, 0.0F, glFloatBuffer1, glFloatBuffer2, glIntBuffer, POSITION_BUFFER);
        offset = new Vec3d(POSITION_BUFFER.get(0), POSITION_BUFFER.get(1), POSITION_BUFFER.get(2));
        int i = thirdPerson ? 1 : 0;
        float f2 = camera.pitch;
        float f3 = camera.yaw;
        dx = MathHelper.cos(f3 * (float) Math.PI / 180.0F) * (1 - i * 2);
        dz = MathHelper.sin(f3 * (float) Math.PI / 180.0F) * (1 - i * 2);
        forwards = -dz * MathHelper.sin(f2 * (float) Math.PI / 180.0F) * (1 - i * 2);
        sideways = dx * MathHelper.sin(f2 * (float) Math.PI / 180.0F) * (1 - i * 2);
        dy = MathHelper.cos(f2 * (float) Math.PI / 180.0F);
    }

    public static Vec3d getPos(Entity camera, double tickDelta) {
        double d0 = camera.lastX + (camera.x - camera.lastX) * tickDelta;
        double d1 = camera.lastY + (camera.y - camera.lastY) * tickDelta;
        double d2 = camera.lastZ + (camera.z - camera.lastZ) * tickDelta;
        double d3 = d0 + offset.x;
        double d4 = d1 + offset.y;
        double d5 = d2 + offset.z;
        return new Vec3d(d3, d4, d5);
    }

    public static Block getBlockInside(World world, Entity camera, float tickDelta) {
        Vec3d vec3d = getPos(camera, tickDelta);
        BlockPos blockpos = new BlockPos(vec3d);
        BlockState blockstate = world.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        if (block.getMaterial().isLiquid()) {
            float f = 0.0F;
            if (blockstate.getBlock() instanceof LiquidBlock) {
                f = LiquidBlock.getHeightLoss(blockstate.get(LiquidBlock.LEVEL)) - 0.11111111F;
            }

            float f1 = blockpos.getY() + 1 - f;
            if (vec3d.y >= f1) {
                block = world.getBlockState(blockpos.up()).getBlock();
            }
        }

        return block;
    }

    public static Vec3d offset() {
        return offset;
    }

    public static float dx() {
        return dx;
    }

    public static float dy() {
        return dy;
    }

    public static float dz() {
        return dz;
    }

    public static float forwards() {
        return forwards;
    }

    public static float sideways() {
        return sideways;
    }
}
