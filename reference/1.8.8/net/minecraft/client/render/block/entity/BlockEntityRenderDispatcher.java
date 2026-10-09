package net.minecraft.client.render.block.entity;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.MovingBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockEntityRenderDispatcher {
    private Map<Class<? extends BlockEntity>, BlockEntityRenderer<? extends BlockEntity>> renderers = Maps.newHashMap();
    public static BlockEntityRenderDispatcher INSTANCE = new BlockEntityRenderDispatcher();
    private TextRenderer textRenderer;
    public static double offsetX;
    public static double offsetY;
    public static double offsetZ;
    public TextureManager textureManager;
    public World world;
    public Entity camera;
    public float cameraYaw;
    public float cameraPitch;
    public double cameraX;
    public double cameraY;
    public double cameraZ;

    private BlockEntityRenderDispatcher() {
        this.renderers.put(SignBlockEntity.class, new SignRenderer());
        this.renderers.put(MobSpawnerBlockEntity.class, new MobSpawnerRenderer());
        this.renderers.put(MovingBlockEntity.class, new MovingBlockRenderer());
        this.renderers.put(ChestBlockEntity.class, new ChestRenderer());
        this.renderers.put(EnderChestBlockEntity.class, new EnderChestRenderer());
        this.renderers.put(EnchantingTableBlockEntity.class, new EnchantingTableRenderer());
        this.renderers.put(EndPortalBlockEntity.class, new EndPortalRenderer());
        this.renderers.put(BeaconBlockEntity.class, new BeaconRenderer());
        this.renderers.put(SkullBlockEntity.class, new SkullRenderer());
        this.renderers.put(BannerBlockEntity.class, new BannerRenderer());

        for (BlockEntityRenderer<?> blockentityrenderer : this.renderers.values()) {
            blockentityrenderer.init(this);
        }
    }

    public <T extends BlockEntity> BlockEntityRenderer<T> getRenderer(Class<? extends BlockEntity> type) {
        BlockEntityRenderer<? extends BlockEntity> blockentityrenderer = this.renderers.get(type);
        if (blockentityrenderer == null && type != BlockEntity.class) {
            blockentityrenderer = this.getRenderer((Class<? extends BlockEntity>)type.getSuperclass());
            this.renderers.put(type, blockentityrenderer);
        }

        return (BlockEntityRenderer<T>)blockentityrenderer;
    }

    public <T extends BlockEntity> BlockEntityRenderer<T> getRenderer(BlockEntity blockEntity) {
        return blockEntity == null ? null : this.getRenderer((Class<? extends BlockEntity>)blockEntity.getClass());
    }

    public void prepare(World world, TextureManager textureManager, TextRenderer textRenderer, Entity camera, float tickDelta) {
        if (this.world != world) {
            this.setWorld(world);
        }

        this.textureManager = textureManager;
        this.camera = camera;
        this.textRenderer = textRenderer;
        this.cameraYaw = camera.lastYaw + (camera.yaw - camera.lastYaw) * tickDelta;
        this.cameraPitch = camera.lastPitch + (camera.pitch - camera.lastPitch) * tickDelta;
        this.cameraX = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        this.cameraY = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        this.cameraZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
    }

    public void render(BlockEntity blockEntity, float tickDelta, int blockMiningProgress) {
        if (blockEntity.squaredDistanceTo(this.cameraX, this.cameraY, this.cameraZ) < blockEntity.getSquaredViewDistance()) {
            int i = this.world.getLightColor(blockEntity.getPos(), 0);
            int j = i % 65536;
            int k = i / 65536;
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, j / 1.0F, k / 1.0F);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            BlockPos blockpos = blockEntity.getPos();
            this.render(blockEntity, blockpos.getX() - offsetX, blockpos.getY() - offsetY, blockpos.getZ() - offsetZ, tickDelta, blockMiningProgress);
        }
    }

    public void render(BlockEntity blockEntity, double dx, double dy, double dz, float tickDelta) {
        this.render(blockEntity, dx, dy, dz, tickDelta, -1);
    }

    public void render(BlockEntity blockEntity, double dx, double dy, double dz, float tickDelta, int blockMiningProgress) {
        BlockEntityRenderer<BlockEntity> blockentityrenderer = this.getRenderer(blockEntity);
        if (blockentityrenderer != null) {
            try {
                blockentityrenderer.render(blockEntity, dx, dy, dz, tickDelta, blockMiningProgress);
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Rendering Block Entity");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Block Entity Details");
                blockEntity.populateCrashReport(crashreportcategory);
                throw new CrashException(crashreport);
            }
        }
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public TextRenderer getTextRenderer() {
        return this.textRenderer;
    }
}
