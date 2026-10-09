package net.minecraft.client.gui.overlay;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.Property;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.FrameTimeLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HitResult;
import net.minecraft.world.LightType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public class DebugOverlay extends GuiElement {
    private final Minecraft minecraft;
    private final TextRenderer textRenderer;

    public DebugOverlay(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.textRenderer = minecraft.textRenderer;
    }

    public void render(Window window) {
        this.minecraft.profiler.push("debug");
        GlStateManager.pushMatrix();
        this.drawGameInfo();
        this.drawSystemInfo(window);
        GlStateManager.popMatrix();
        if (this.minecraft.options.debugTpsEnabled) {
            this.drawTpsChart();
        }

        this.minecraft.profiler.pop();
    }

    private boolean showReducedInfo() {
        return this.minecraft.player.hasReducedDebugInfo() || this.minecraft.options.reducedDebugInfo;
    }

    protected void drawGameInfo() {
        List<String> list = this.getGameInfo();

        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (!Strings.isNullOrEmpty(s)) {
                int j = this.textRenderer.fontHeight;
                int k = this.textRenderer.getWidth(s);
                int l = 2;
                int i1 = 2 + j * i;
                fill(1, i1 - 1, 2 + k + 1, i1 + j - 1, -1873784752);
                this.textRenderer.draw(s, 2, i1, 14737632);
            }
        }
    }

    protected void drawSystemInfo(Window window) {
        List<String> list = this.getSystemInfo();

        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (!Strings.isNullOrEmpty(s)) {
                int j = this.textRenderer.fontHeight;
                int k = this.textRenderer.getWidth(s);
                int l = window.getWidth() - 2 - k;
                int i1 = 2 + j * i;
                fill(l - 1, i1 - 1, l + k + 1, i1 + j - 1, -1873784752);
                this.textRenderer.draw(s, l, i1, 14737632);
            }
        }
    }

    protected List<String> getGameInfo() {
        BlockPos blockpos = new BlockPos(this.minecraft.getCamera().x, this.minecraft.getCamera().getShape().minY, this.minecraft.getCamera().z);
        if (this.showReducedInfo()) {
            return Lists.newArrayList(
                "Minecraft 1.8.8 (" + this.minecraft.getGameVersion() + "/" + ClientBrandRetriever.getClientModName() + ")",
                this.minecraft.fpsDebugInfo,
                this.minecraft.worldRenderer.getChunkDebugInfo(),
                this.minecraft.worldRenderer.getEntityDebugInfo(),
                "P: " + this.minecraft.particleManager.getDebugInfo() + ". T: " + this.minecraft.world.getDebugInfo(),
                this.minecraft.world.getChunkSourceDebugInfo(),
                "",
                String.format("Chunk-relative: %d %d %d", blockpos.getX() & 15, blockpos.getY() & 15, blockpos.getZ() & 15)
            );
        }

        Entity entity = this.minecraft.getCamera();
        Direction direction = entity.getHorizontalFacing();
        String s = "Invalid";
        switch (direction) {
            case NORTH:
                s = "Towards negative Z";
                break;
            case SOUTH:
                s = "Towards positive Z";
                break;
            case WEST:
                s = "Towards negative X";
                break;
            case EAST:
                s = "Towards positive X";
        }

        List<String> list = Lists.newArrayList(
            "Minecraft 1.8.8 (" + this.minecraft.getGameVersion() + "/" + ClientBrandRetriever.getClientModName() + ")",
            this.minecraft.fpsDebugInfo,
            this.minecraft.worldRenderer.getChunkDebugInfo(),
            this.minecraft.worldRenderer.getEntityDebugInfo(),
            "P: " + this.minecraft.particleManager.getDebugInfo() + ". T: " + this.minecraft.world.getDebugInfo(),
            this.minecraft.world.getChunkSourceDebugInfo(),
            "",
            String.format("XYZ: %.3f / %.5f / %.3f", this.minecraft.getCamera().x, this.minecraft.getCamera().getShape().minY, this.minecraft.getCamera().z),
            String.format("Block: %d %d %d", blockpos.getX(), blockpos.getY(), blockpos.getZ()),
            String.format(
                "Chunk: %d %d %d in %d %d %d",
                blockpos.getX() & 15,
                blockpos.getY() & 15,
                blockpos.getZ() & 15,
                blockpos.getX() >> 4,
                blockpos.getY() >> 4,
                blockpos.getZ() >> 4
            ),
            String.format("Facing: %s (%s) (%.1f / %.1f)", direction, s, MathHelper.wrapDegrees(entity.yaw), MathHelper.wrapDegrees(entity.pitch))
        );
        if (this.minecraft.world != null && this.minecraft.world.isChunkLoaded(blockpos)) {
            WorldChunk worldchunk = this.minecraft.world.getChunk(blockpos);
            list.add("Biome: " + worldchunk.getBiome(blockpos, this.minecraft.world.getBiomeSource()).name);
            list.add(
                "Light: "
                    + worldchunk.getLight(blockpos, 0)
                    + " ("
                    + worldchunk.getLight(LightType.SKY, blockpos)
                    + " sky, "
                    + worldchunk.getLight(LightType.BLOCK, blockpos)
                    + " block)"
            );
            LocalDifficulty localdifficulty = this.minecraft.world.getLocalDifficulty(blockpos);
            if (this.minecraft.isIntegratedServerRunning() && this.minecraft.getServer() != null) {
                ServerPlayerEntity serverplayerentity = this.minecraft.getServer().getPlayerManager().get(this.minecraft.player.getUuid());
                if (serverplayerentity != null) {
                    localdifficulty = serverplayerentity.world.getLocalDifficulty(new BlockPos(serverplayerentity));
                }
            }

            list.add(String.format("Local Difficulty: %.2f (Day %d)", localdifficulty.get(), this.minecraft.world.getTimeOfDay() / 24000L));
        }

        if (this.minecraft.gameRenderer != null && this.minecraft.gameRenderer.hasShader()) {
            list.add("Shader: " + this.minecraft.gameRenderer.getShader().getName());
        }

        if (this.minecraft.crosshairTarget != null
            && this.minecraft.crosshairTarget.type == HitResult.Type.BLOCK
            && this.minecraft.crosshairTarget.getPos() != null) {
            BlockPos blockpos1 = this.minecraft.crosshairTarget.getPos();
            list.add(String.format("Looking at: %d %d %d", blockpos1.getX(), blockpos1.getY(), blockpos1.getZ()));
        }

        return list;
    }

    protected List<String> getSystemInfo() {
        long i = Runtime.getRuntime().maxMemory();
        long j = Runtime.getRuntime().totalMemory();
        long k = Runtime.getRuntime().freeMemory();
        long l = j - k;
        List<String> list = Lists.newArrayList(
            String.format("Java: %s %dbit", System.getProperty("java.version"), this.minecraft.is64Bit() ? 64 : 32),
            String.format("Mem: % 2d%% %03d/%03dMB", l * 100L / i, convertBytesToMegaBytes(l), convertBytesToMegaBytes(i)),
            String.format("Allocated: % 2d%% %03dMB", j * 100L / i, convertBytesToMegaBytes(j)),
            "",
            String.format("CPU: %s", GLX.getCpuInfo()),
            "",
            String.format("Display: %dx%d (%s)", Display.getWidth(), Display.getHeight(), GL11.glGetString(7936)),
            GL11.glGetString(7937),
            GL11.glGetString(7938)
        );
        if (this.showReducedInfo()) {
            return list;
        }

        if (this.minecraft.crosshairTarget != null
            && this.minecraft.crosshairTarget.type == HitResult.Type.BLOCK
            && this.minecraft.crosshairTarget.getPos() != null) {
            BlockPos blockpos = this.minecraft.crosshairTarget.getPos();
            BlockState blockstate = this.minecraft.world.getBlockState(blockpos);
            if (this.minecraft.world.getGeneratorType() != WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
                blockstate = blockstate.getBlock().resolveVirtualProperties(blockstate, this.minecraft.world, blockpos);
            }

            list.add("");
            list.add(String.valueOf(Block.REGISTRY.getKey(blockstate.getBlock())));

            for (Entry<Property, Comparable> entry : blockstate.values().entrySet()) {
                String s = entry.getValue().toString();
                if (entry.getValue() == Boolean.TRUE) {
                    s = Formatting.GREEN + s;
                } else if (entry.getValue() == Boolean.FALSE) {
                    s = Formatting.RED + s;
                }

                list.add(entry.getKey().getName() + ": " + s);
            }
        }

        return list;
    }

    private void drawTpsChart() {
        GlStateManager.disableDepthTest();
        FrameTimeLogger frametimelogger = this.minecraft.getFrameTimeLogger();
        int i = frametimelogger.getStartTime();
        int j = frametimelogger.getEndTime();
        long[] along = frametimelogger.getFrameTimes();
        Window window = new Window(this.minecraft);
        int k = i;
        int l = 0;
        fill(0, window.getHeight() - 60, 240, window.getHeight(), -1873784752);

        while (k != j) {
            int i1 = frametimelogger.scaleFrameTime(along[k], 30);
            int j1 = this.getFrameColor(MathHelper.clamp(i1, 0, 60), 0, 30, 60);
            this.drawVerticalLine(l, window.getHeight(), window.getHeight() - i1, j1);
            l++;
            k = frametimelogger.wrapIndex(k + 1);
        }

        fill(1, window.getHeight() - 30 + 1, 14, window.getHeight() - 30 + 10, -1873784752);
        this.textRenderer.draw("60", 2, window.getHeight() - 30 + 2, 14737632);
        this.drawHorizontalLine(0, 239, window.getHeight() - 30, -1);
        fill(1, window.getHeight() - 60 + 1, 14, window.getHeight() - 60 + 10, -1873784752);
        this.textRenderer.draw("30", 2, window.getHeight() - 60 + 2, 14737632);
        this.drawHorizontalLine(0, 239, window.getHeight() - 60, -1);
        this.drawHorizontalLine(0, 239, window.getHeight() - 1, -1);
        this.drawVerticalLine(0, window.getHeight() - 60, window.getHeight(), -1);
        this.drawVerticalLine(239, window.getHeight() - 60, window.getHeight(), -1);
        if (this.minecraft.options.fpsLimit <= 120) {
            this.drawHorizontalLine(0, 239, window.getHeight() - 60 + this.minecraft.options.fpsLimit / 2, -16711681);
        }

        GlStateManager.enableDepthTest();
    }

    private int getFrameColor(int frameTime, int min, int mid, int max) {
        return frameTime < mid ? this.lerpColor(-16711936, -256, (float)frameTime / mid) : this.lerpColor(-256, -65536, (float)(frameTime - mid) / (max - mid));
    }

    private int lerpColor(int from, int to, float delta) {
        int i = from >> 24 & 0xFF;
        int j = from >> 16 & 0xFF;
        int k = from >> 8 & 0xFF;
        int l = from & 0xFF;
        int i1 = to >> 24 & 0xFF;
        int j1 = to >> 16 & 0xFF;
        int k1 = to >> 8 & 0xFF;
        int l1 = to & 0xFF;
        int i2 = MathHelper.clamp((int)(i + (i1 - i) * delta), 0, 255);
        int j2 = MathHelper.clamp((int)(j + (j1 - j) * delta), 0, 255);
        int k2 = MathHelper.clamp((int)(k + (k1 - k) * delta), 0, 255);
        int l2 = MathHelper.clamp((int)(l + (l1 - l) * delta), 0, 255);
        return i2 << 24 | j2 << 16 | k2 << 8 | l2;
    }

    private static long convertBytesToMegaBytes(long bytes) {
        return bytes / 1024L / 1024L;
    }
}
