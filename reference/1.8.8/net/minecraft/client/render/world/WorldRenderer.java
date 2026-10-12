package net.minecraft.client.render.world;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.FrustumData;
import net.minecraft.client.render.FrustumMatrix;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.shaders.ProgramManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.client.render.vertex.VertexFormatElement;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.resource.Identifier;
import net.minecraft.util.TypeInstanceMultiMap;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vector3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.WorldEventListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class WorldRenderer implements WorldEventListener, ResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier MOON_PHASES_LOCATION = new Identifier("textures/environment/moon_phases.png");
    private static final Identifier SUN_LOCATION = new Identifier("textures/environment/sun.png");
    private static final Identifier CLOUDS_LOCATION = new Identifier("textures/environment/clouds.png");
    private static final Identifier END_SKY_LOCATION = new Identifier("textures/environment/end_sky.png");
    private static final Identifier WORLD_BORDER_LOCATION = new Identifier("textures/misc/forcefield.png");
    private final Minecraft minecraft;
    private final TextureManager textureManager;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private ClientWorld world;
    private Set<RenderChunk> dirtyChunks = Sets.newLinkedHashSet();
    private List<WorldRenderer.RenderChunkInfo> compiledChunks = Lists.newArrayListWithCapacity(69696);
    private final Set<BlockEntity> globalBlockEntities = Sets.newHashSet();
    private RenderChunkStorage chunkStorage;
    private int starsGlList = -1;
    private int lightSkyGlList = -1;
    private int darkSkyGlList = -1;
    private VertexFormat skyVertexFormat;
    private VertexBuffer starsBuffer;
    private VertexBuffer lightSkyBuffer;
    private VertexBuffer darkSkyBuffer;
    private int ticks;
    private final Map<Integer, BlockMiningProgress> miningProgress = Maps.newHashMap();
    private final Map<BlockPos, SoundInstance> playingSongs = Maps.newHashMap();
    private final TextureAtlasSprite[] miningProgressSprites = new TextureAtlasSprite[10];
    private RenderTarget entityOutlineRenderTarget;
    private PostChain entityOutline;
    private double prevCameraX = Double.MIN_VALUE;
    private double prevCameraY = Double.MIN_VALUE;
    private double prevCameraZ = Double.MIN_VALUE;
    private int prevCameraChunkX = Integer.MIN_VALUE;
    private int prevCameraChunkY = Integer.MIN_VALUE;
    private int prevCameraChunkZ = Integer.MIN_VALUE;
    private double cameraX = Double.MIN_VALUE;
    private double cameraY = Double.MIN_VALUE;
    private double cameraZ = Double.MIN_VALUE;
    private double cameraPitch = Double.MIN_VALUE;
    private double cameraYaw = Double.MIN_VALUE;
    private final ChunkRenderDispatcher chunkRenderDispatcher = new ChunkRenderDispatcher();
    private RenderChunkList chunkList;
    private int lastViewDistance = -1;
    private int entityRenderCooldown = 2;
    private int entityCount;
    private int renderedEntityCount;
    private int culledEntityCount;
    private boolean captureFrustum = false;
    private FrustumData capturedFrustum;
    private final Vector4f[] frustumPoints = new Vector4f[8];
    private final Vector3d frustumPos = new Vector3d();
    private boolean useVbo = false;
    RenderChunkFactory chunkFactory;
    private double lastCameraX;
    private double lastCameraY;
    private double lastCameraZ;
    private boolean viewChanged = true;

    public WorldRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.entityRenderDispatcher = minecraft.getEntityRenderDispatcher();
        this.textureManager = minecraft.getTextureManager();
        this.textureManager.bind(WORLD_BORDER_LOCATION);
        GL11.glTexParameteri(3553, 10242, 10497);
        GL11.glTexParameteri(3553, 10243, 10497);
        GlStateManager.bindTexture(0);
        this.reloadMiningProgressTextures();
        this.useVbo = GLX.useVbo();
        if (this.useVbo) {
            this.chunkList = new VboRenderChunkList();
            this.chunkFactory = new VboRenderChunkFactory();
        } else {
            this.chunkList = new GlListRenderChunkList();
            this.chunkFactory = new GlListRenderChunkFactory();
        }

        this.skyVertexFormat = new VertexFormat();
        this.skyVertexFormat.addElement(new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3));
        this.renderStars();
        this.renderLightSky();
        this.renderDarkSky();
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        this.reloadMiningProgressTextures();
    }

    private void reloadMiningProgressTextures() {
        TextureAtlas textureatlas = this.minecraft.getBlocksAtlas();

        for (int i = 0; i < this.miningProgressSprites.length; i++) {
            this.miningProgressSprites[i] = textureatlas.getSprite("minecraft:blocks/destroy_stage_" + i);
        }
    }

    public void loadEntityOutline() {
        if (GLX.usePostProcess) {
            if (ProgramManager.getInstance() == null) {
                ProgramManager.createInstance();
            }

            Identifier identifier = new Identifier("shaders/post/entity_outline.json");

            try {
                this.entityOutline = new PostChain(
                    this.minecraft.getTextureManager(), this.minecraft.getResourceManager(), this.minecraft.getRenderTarget(), identifier
                );
                this.entityOutline.resize(this.minecraft.width, this.minecraft.height);
                this.entityOutlineRenderTarget = this.entityOutline.getTempTarget("final");
            } catch (IOException ioexception) {
                LOGGER.warn("Failed to load shader: " + identifier, ioexception);
                this.entityOutline = null;
                this.entityOutlineRenderTarget = null;
            } catch (JsonSyntaxException jsonsyntaxexception) {
                LOGGER.warn("Failed to load shader: " + identifier, jsonsyntaxexception);
                this.entityOutline = null;
                this.entityOutlineRenderTarget = null;
            }
        } else {
            this.entityOutline = null;
            this.entityOutlineRenderTarget = null;
        }
    }

    public void renderEntityOutlines() {
        if (this.shouldRenderEntityOutlines()) {
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 0, 1);
            this.entityOutlineRenderTarget.draw(this.minecraft.width, this.minecraft.height, false);
            GlStateManager.disableBlend();
        }
    }

    protected boolean shouldRenderEntityOutlines() {
        return this.entityOutlineRenderTarget != null
            && this.entityOutline != null
            && this.minecraft.player != null
            && this.minecraft.player.isSpectator()
            && this.minecraft.options.spectatorOutlinesKey.isPressed();
    }

    private void renderDarkSky() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        if (this.darkSkyBuffer != null) {
            this.darkSkyBuffer.delete();
        }

        if (this.darkSkyGlList >= 0) {
            MemoryTracker.releaseList(this.darkSkyGlList);
            this.darkSkyGlList = -1;
        }

        if (this.useVbo) {
            this.darkSkyBuffer = new VertexBuffer(this.skyVertexFormat);
            this.renderSkyHemisphere(bufferbuilder, -16.0F, true);
            bufferbuilder.end();
            bufferbuilder.clear();
            this.darkSkyBuffer.upload(bufferbuilder.getBuffer());
        } else {
            this.darkSkyGlList = MemoryTracker.getLists(1);
            GL11.glNewList(this.darkSkyGlList, 4864);
            this.renderSkyHemisphere(bufferbuilder, -16.0F, true);
            tesselator.end();
            GL11.glEndList();
        }
    }

    private void renderLightSky() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        if (this.lightSkyBuffer != null) {
            this.lightSkyBuffer.delete();
        }

        if (this.lightSkyGlList >= 0) {
            MemoryTracker.releaseList(this.lightSkyGlList);
            this.lightSkyGlList = -1;
        }

        if (this.useVbo) {
            this.lightSkyBuffer = new VertexBuffer(this.skyVertexFormat);
            this.renderSkyHemisphere(bufferbuilder, 16.0F, false);
            bufferbuilder.end();
            bufferbuilder.clear();
            this.lightSkyBuffer.upload(bufferbuilder.getBuffer());
        } else {
            this.lightSkyGlList = MemoryTracker.getLists(1);
            GL11.glNewList(this.lightSkyGlList, 4864);
            this.renderSkyHemisphere(bufferbuilder, 16.0F, false);
            tesselator.end();
            GL11.glEndList();
        }
    }

    private void renderSkyHemisphere(BufferBuilder bufferBuilder, float y, boolean dark) {
        int i = 64;
        int j = 6;
        bufferBuilder.begin(7, DefaultVertexFormat.POSITION);

        for (int k = -384; k <= 384; k += 64) {
            for (int l = -384; l <= 384; l += 64) {
                float f = k;
                float f1 = k + 64;
                if (dark) {
                    f1 = k;
                    f = k + 64;
                }

                bufferBuilder.vertex(f, y, l).nextVertex();
                bufferBuilder.vertex(f1, y, l).nextVertex();
                bufferBuilder.vertex(f1, y, l + 64).nextVertex();
                bufferBuilder.vertex(f, y, l + 64).nextVertex();
            }
        }
    }

    private void renderStars() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        if (this.starsBuffer != null) {
            this.starsBuffer.delete();
        }

        if (this.starsGlList >= 0) {
            MemoryTracker.releaseList(this.starsGlList);
            this.starsGlList = -1;
        }

        if (this.useVbo) {
            this.starsBuffer = new VertexBuffer(this.skyVertexFormat);
            this.renderStars(bufferbuilder);
            bufferbuilder.end();
            bufferbuilder.clear();
            this.starsBuffer.upload(bufferbuilder.getBuffer());
        } else {
            this.starsGlList = MemoryTracker.getLists(1);
            GlStateManager.pushMatrix();
            GL11.glNewList(this.starsGlList, 4864);
            this.renderStars(bufferbuilder);
            tesselator.end();
            GL11.glEndList();
            GlStateManager.popMatrix();
        }
    }

    private void renderStars(BufferBuilder bufferBuilder) {
        Random random = new Random(10842L);
        bufferBuilder.begin(7, DefaultVertexFormat.POSITION);

        for (int i = 0; i < 1500; i++) {
            double d0 = random.nextFloat() * 2.0F - 1.0F;
            double d1 = random.nextFloat() * 2.0F - 1.0F;
            double d2 = random.nextFloat() * 2.0F - 1.0F;
            double d3 = 0.15F + random.nextFloat() * 0.1F;
            double d4 = d0 * d0 + d1 * d1 + d2 * d2;
            if (d4 < 1.0 && d4 > 0.01) {
                d4 = 1.0 / Math.sqrt(d4);
                d0 *= d4;
                d1 *= d4;
                d2 *= d4;
                double d5 = d0 * 100.0;
                double d6 = d1 * 100.0;
                double d7 = d2 * 100.0;
                double d8 = Math.atan2(d0, d2);
                double d9 = Math.sin(d8);
                double d10 = Math.cos(d8);
                double d11 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
                double d12 = Math.sin(d11);
                double d13 = Math.cos(d11);
                double d14 = random.nextDouble() * Math.PI * 2.0;
                double d15 = Math.sin(d14);
                double d16 = Math.cos(d14);

                for (int j = 0; j < 4; j++) {
                    double d17 = 0.0;
                    double d18 = ((j & 2) - 1) * d3;
                    double d19 = ((j + 1 & 2) - 1) * d3;
                    double d20 = 0.0;
                    double d21 = d18 * d16 - d19 * d15;
                    double d22 = d19 * d16 + d18 * d15;
                    double d23 = d22;
                    double d24 = d21 * d12 + 0.0 * d13;
                    double d25 = 0.0 * d12 - d21 * d13;
                    double d26 = d25 * d9 - d23 * d10;
                    double d27 = d24;
                    double d28 = d23 * d9 + d25 * d10;
                    bufferBuilder.vertex(d5 + d26, d6 + d27, d7 + d28).nextVertex();
                }
            }
        }
    }

    public void setWorld(ClientWorld world) {
        if (this.world != null) {
            this.world.removeEventListener(this);
        }

        this.prevCameraX = Double.MIN_VALUE;
        this.prevCameraY = Double.MIN_VALUE;
        this.prevCameraZ = Double.MIN_VALUE;
        this.prevCameraChunkX = Integer.MIN_VALUE;
        this.prevCameraChunkY = Integer.MIN_VALUE;
        this.prevCameraChunkZ = Integer.MIN_VALUE;
        this.entityRenderDispatcher.setWorld(world);
        this.world = world;
        if (world != null) {
            world.addEventListener(this);
            this.reload();
        }
    }

    public void reload() {
        if (this.world != null) {
            this.viewChanged = true;
            Blocks.LEAVES.setCulling(this.minecraft.options.fancyGraphics);
            Blocks.LEAVES2.setCulling(this.minecraft.options.fancyGraphics);
            this.lastViewDistance = this.minecraft.options.viewDistance;
            boolean flag = this.useVbo;
            this.useVbo = GLX.useVbo();
            if (flag && !this.useVbo) {
                this.chunkList = new GlListRenderChunkList();
                this.chunkFactory = new GlListRenderChunkFactory();
            } else if (!flag && this.useVbo) {
                this.chunkList = new VboRenderChunkList();
                this.chunkFactory = new VboRenderChunkFactory();
            }

            if (flag != this.useVbo) {
                this.renderStars();
                this.renderLightSky();
                this.renderDarkSky();
            }

            if (this.chunkStorage != null) {
                this.chunkStorage.releaseBuffers();
            }

            this.resetDirtyChunks();
            synchronized (this.globalBlockEntities) {
                this.globalBlockEntities.clear();
            }

            this.chunkStorage = new RenderChunkStorage(this.world, this.minecraft.options.viewDistance, this, this.chunkFactory);
            if (this.world != null) {
                Entity entity = this.minecraft.getCamera();
                if (entity != null) {
                    this.chunkStorage.updateCameraPos(entity.x, entity.z);
                }
            }

            this.entityRenderCooldown = 2;
        }
    }

    protected void resetDirtyChunks() {
        this.dirtyChunks.clear();
        this.chunkRenderDispatcher.runTasks();
    }

    public void onResolutionChanged(int width, int height) {
        if (GLX.usePostProcess) {
            if (this.entityOutline != null) {
                this.entityOutline.resize(width, height);
            }
        }
    }

    public void renderEntities(Entity camera, Culler culler, float tickDelta) {
        if (this.entityRenderCooldown > 0) {
            this.entityRenderCooldown--;
        } else {
            double d0 = camera.lastX + (camera.x - camera.lastX) * tickDelta;
            double d1 = camera.lastY + (camera.y - camera.lastY) * tickDelta;
            double d2 = camera.lastZ + (camera.z - camera.lastZ) * tickDelta;
            this.world.profiler.push("prepare");
            BlockEntityRenderDispatcher.INSTANCE
                .prepare(this.world, this.minecraft.getTextureManager(), this.minecraft.textRenderer, this.minecraft.getCamera(), tickDelta);
            this.entityRenderDispatcher
                .prepare(this.world, this.minecraft.textRenderer, this.minecraft.getCamera(), this.minecraft.targetEntity, this.minecraft.options, tickDelta);
            this.entityCount = 0;
            this.renderedEntityCount = 0;
            this.culledEntityCount = 0;
            Entity entity = this.minecraft.getCamera();
            double d3 = entity.prevX + (entity.x - entity.prevX) * tickDelta;
            double d4 = entity.prevY + (entity.y - entity.prevY) * tickDelta;
            double d5 = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
            BlockEntityRenderDispatcher.offsetX = d3;
            BlockEntityRenderDispatcher.offsetY = d4;
            BlockEntityRenderDispatcher.offsetZ = d5;
            this.entityRenderDispatcher.setCameraPos(d3, d4, d5);
            this.minecraft.gameRenderer.enableLightMap();
            this.world.profiler.swap("global");
            List<Entity> list = this.world.getEntities();
            this.entityCount = list.size();

            for (int i = 0; i < this.world.globalEntities.size(); i++) {
                Entity entity1 = this.world.globalEntities.get(i);
                this.renderedEntityCount++;
                if (entity1.shouldRender(d0, d1, d2)) {
                    this.entityRenderDispatcher.render(entity1, tickDelta);
                }
            }

            if (this.shouldRenderEntityOutlines()) {
                GlStateManager.depthFunc(519);
                GlStateManager.disableFog();
                this.entityOutlineRenderTarget.clear();
                this.entityOutlineRenderTarget.bindWrite(false);
                this.world.profiler.swap("entityOutlines");
                Lighting.turnOff();
                this.entityRenderDispatcher.setSolidRender(true);

                for (int j = 0; j < list.size(); j++) {
                    Entity entity3 = list.get(j);
                    boolean flag = this.minecraft.getCamera() instanceof LivingEntity && ((LivingEntity)this.minecraft.getCamera()).isSleeping();
                    boolean flag1 = entity3.shouldRender(d0, d1, d2)
                        && (entity3.ignoreCameraFrustum || culler.isVisible(entity3.getShape()) || entity3.rider == this.minecraft.player)
                        && entity3 instanceof PlayerEntity;
                    if ((entity3 != this.minecraft.getCamera() || this.minecraft.options.perspective != 0 || flag) && flag1) {
                        this.entityRenderDispatcher.render(entity3, tickDelta);
                    }
                }

                this.entityRenderDispatcher.setSolidRender(false);
                Lighting.turnOn();
                GlStateManager.depthMask(false);
                this.entityOutline.process(tickDelta);
                GlStateManager.enableLighting();
                GlStateManager.depthMask(true);
                this.minecraft.getRenderTarget().bindWrite(false);
                GlStateManager.enableFog();
                GlStateManager.enableBlend();
                GlStateManager.enableColorMaterial();
                GlStateManager.depthFunc(515);
                GlStateManager.enableDepthTest();
                GlStateManager.enableAlphaTest();
            }

            this.world.profiler.swap("entities");

            for (WorldRenderer.RenderChunkInfo worldrenderer$renderchunkinfo : this.compiledChunks) {
                WorldChunk worldchunk = this.world.getChunk(worldrenderer$renderchunkinfo.chunk.getOrigin());
                TypeInstanceMultiMap<Entity> typeinstancemultimap = worldchunk.getEntities()[worldrenderer$renderchunkinfo.chunk.getOrigin().getY() / 16];
                if (!typeinstancemultimap.isEmpty()) {
                    for (Entity entity2 : typeinstancemultimap) {
                        boolean flag2 = this.entityRenderDispatcher.shouldRender(entity2, culler, d0, d1, d2) || entity2.rider == this.minecraft.player;
                        if (flag2) {
                            boolean flag3 = this.minecraft.getCamera() instanceof LivingEntity && ((LivingEntity)this.minecraft.getCamera()).isSleeping();
                            if (entity2 == this.minecraft.getCamera() && this.minecraft.options.perspective == 0 && !flag3
                                || entity2.y >= 0.0 && entity2.y < 256.0 && !this.world.isChunkLoaded(new BlockPos(entity2))) {
                                continue;
                            }

                            this.renderedEntityCount++;
                            this.entityRenderDispatcher.render(entity2, tickDelta);
                        }

                        if (!flag2 && entity2 instanceof WitherSkullEntity) {
                            this.minecraft.getEntityRenderDispatcher().renderNameTag(entity2, tickDelta);
                        }
                    }
                }
            }

            this.world.profiler.swap("blockentities");
            Lighting.turnOn();

            for (WorldRenderer.RenderChunkInfo worldrenderer$renderchunkinfo1 : this.compiledChunks) {
                List<BlockEntity> list1 = worldrenderer$renderchunkinfo1.chunk.getCompiledChunk().getRenderableBlockEntities();
                if (!list1.isEmpty()) {
                    for (BlockEntity blockentity2 : list1) {
                        BlockEntityRenderDispatcher.INSTANCE.render(blockentity2, tickDelta, -1);
                    }
                }
            }

            synchronized (this.globalBlockEntities) {
                for (BlockEntity blockentity : this.globalBlockEntities) {
                    BlockEntityRenderDispatcher.INSTANCE.render(blockentity, tickDelta, -1);
                }
            }

            this.setupMiningProgressState();

            for (BlockMiningProgress blockminingprogress : this.miningProgress.values()) {
                BlockPos blockpos = blockminingprogress.getPos();
                BlockEntity blockentity1 = this.world.getBlockEntity(blockpos);
                if (blockentity1 instanceof ChestBlockEntity) {
                    ChestBlockEntity chestblockentity = (ChestBlockEntity)blockentity1;
                    if (chestblockentity.westNeighbor != null) {
                        blockpos = blockpos.offset(Direction.WEST);
                        blockentity1 = this.world.getBlockEntity(blockpos);
                    } else if (chestblockentity.northNeighbor != null) {
                        blockpos = blockpos.offset(Direction.NORTH);
                        blockentity1 = this.world.getBlockEntity(blockpos);
                    }
                }

                Block block = this.world.getBlockState(blockpos).getBlock();
                if (blockentity1 != null
                    && (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof SignBlock || block instanceof SkullBlock)) {
                    BlockEntityRenderDispatcher.INSTANCE.render(blockentity1, tickDelta, blockminingprogress.getProgress());
                }
            }

            this.restoreMiningProgressState();
            this.minecraft.gameRenderer.disableLightMap();
            this.minecraft.profiler.pop();
        }
    }

    public String getChunkDebugInfo() {
        int i = this.chunkStorage.chunks.length;
        int j = 0;

        for (WorldRenderer.RenderChunkInfo worldrenderer$renderchunkinfo : this.compiledChunks) {
            CompiledChunk compiledchunk = worldrenderer$renderchunkinfo.chunk.compiled;
            if (compiledchunk != CompiledChunk.UNCOMPILED && !compiledchunk.isEmpty()) {
                j++;
            }
        }

        return String.format(
            "C: %d/%d %sD: %d, %s", j, i, this.minecraft.smartCull ? "(s) " : "", this.lastViewDistance, this.chunkRenderDispatcher.getChunkDebugInfo()
        );
    }

    public String getEntityDebugInfo() {
        return "E: "
            + this.renderedEntityCount
            + "/"
            + this.entityCount
            + ", B: "
            + this.culledEntityCount
            + ", I: "
            + (this.entityCount - this.culledEntityCount - this.renderedEntityCount);
    }

    public void setupRender(Entity camera, double tickDelta, Culler culler, int frame, boolean loadChunks) {
        if (this.minecraft.options.viewDistance != this.lastViewDistance) {
            this.reload();
        }

        this.world.profiler.push("camera");
        double d0 = camera.x - this.prevCameraX;
        double d1 = camera.y - this.prevCameraY;
        double d2 = camera.z - this.prevCameraZ;
        if (this.prevCameraChunkX != camera.chunkX
            || this.prevCameraChunkY != camera.chunkY
            || this.prevCameraChunkZ != camera.chunkZ
            || d0 * d0 + d1 * d1 + d2 * d2 > 16.0) {
            this.prevCameraX = camera.x;
            this.prevCameraY = camera.y;
            this.prevCameraZ = camera.z;
            this.prevCameraChunkX = camera.chunkX;
            this.prevCameraChunkY = camera.chunkY;
            this.prevCameraChunkZ = camera.chunkZ;
            this.chunkStorage.updateCameraPos(camera.x, camera.z);
        }

        this.world.profiler.swap("renderlistcamera");
        double d3 = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double d4 = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double d5 = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        this.chunkList.setCameraPos(d3, d4, d5);
        this.world.profiler.swap("cull");
        if (this.capturedFrustum != null) {
            FrustumCuller frustumculler = new FrustumCuller(this.capturedFrustum);
            frustumculler.prepare(this.frustumPos.x, this.frustumPos.y, this.frustumPos.z);
            culler = frustumculler;
        }

        this.minecraft.profiler.swap("culling");
        BlockPos blockpos1 = new BlockPos(d3, d4 + camera.getEyeHeight(), d5);
        RenderChunk renderchunk = this.chunkStorage.getChunk(blockpos1);
        BlockPos blockpos = new BlockPos(MathHelper.floor(d3 / 16.0) * 16, MathHelper.floor(d4 / 16.0) * 16, MathHelper.floor(d5 / 16.0) * 16);
        this.viewChanged = this.viewChanged
            || !this.dirtyChunks.isEmpty()
            || camera.x != this.cameraX
            || camera.y != this.cameraY
            || camera.z != this.cameraZ
            || camera.pitch != this.cameraPitch
            || camera.yaw != this.cameraYaw;
        this.cameraX = camera.x;
        this.cameraY = camera.y;
        this.cameraZ = camera.z;
        this.cameraPitch = camera.pitch;
        this.cameraYaw = camera.yaw;
        boolean flag = this.capturedFrustum != null;
        if (!flag && this.viewChanged) {
            this.viewChanged = false;
            this.compiledChunks = Lists.newArrayList();
            Queue<WorldRenderer.RenderChunkInfo> queue = Lists.newLinkedList();
            boolean flag1 = this.minecraft.smartCull;
            if (renderchunk != null) {
                boolean flag2 = false;
                WorldRenderer.RenderChunkInfo worldrenderer$renderchunkinfo3 = new WorldRenderer.RenderChunkInfo(renderchunk, null, 0);
                Set<Direction> set1 = this.getOpenChunkFaces(blockpos1);
                if (set1.size() == 1) {
                    Vector3f vector3f = this.getLookVector(camera, tickDelta);
                    Direction direction = Direction.getNearest(vector3f.x, vector3f.y, vector3f.z).getOpposite();
                    set1.remove(direction);
                }

                if (set1.isEmpty()) {
                    flag2 = true;
                }

                if (flag2 && !loadChunks) {
                    this.compiledChunks.add(worldrenderer$renderchunkinfo3);
                } else {
                    if (loadChunks && this.world.getBlockState(blockpos1).getBlock().isSolidRender()) {
                        flag1 = false;
                    }

                    renderchunk.setFrame(frame);
                    queue.add(worldrenderer$renderchunkinfo3);
                }
            } else {
                int i = blockpos1.getY() > 0 ? 248 : 8;

                for (int j = -this.lastViewDistance; j <= this.lastViewDistance; j++) {
                    for (int k = -this.lastViewDistance; k <= this.lastViewDistance; k++) {
                        RenderChunk renderchunk1 = this.chunkStorage.getChunk(new BlockPos((j << 4) + 8, i, (k << 4) + 8));
                        if (renderchunk1 != null && culler.isVisible(renderchunk1.bounds)) {
                            renderchunk1.setFrame(frame);
                            queue.add(new WorldRenderer.RenderChunkInfo(renderchunk1, null, 0));
                        }
                    }
                }
            }

            while (!queue.isEmpty()) {
                WorldRenderer.RenderChunkInfo worldrenderer$renderchunkinfo1 = queue.poll();
                RenderChunk renderchunk3 = worldrenderer$renderchunkinfo1.chunk;
                Direction direction2 = worldrenderer$renderchunkinfo1.sourceDir;
                BlockPos blockpos2 = renderchunk3.getOrigin();
                this.compiledChunks.add(worldrenderer$renderchunkinfo1);

                for (Direction direction1 : Direction.values()) {
                    RenderChunk renderchunk2 = this.getNeighborChunk(blockpos, renderchunk3, direction1);
                    if ((!flag1 || !worldrenderer$renderchunkinfo1.culling.contains(direction1.getOpposite()))
                        && (!flag1 || direction2 == null || renderchunk3.getCompiledChunk().isVisible(direction2.getOpposite(), direction1))
                        && renderchunk2 != null
                        && renderchunk2.setFrame(frame)
                        && culler.isVisible(renderchunk2.bounds)) {
                        WorldRenderer.RenderChunkInfo worldrenderer$renderchunkinfo = new WorldRenderer.RenderChunkInfo(
                            renderchunk2, direction1, worldrenderer$renderchunkinfo1.step + 1
                        );
                        worldrenderer$renderchunkinfo.culling.addAll(worldrenderer$renderchunkinfo1.culling);
                        worldrenderer$renderchunkinfo.culling.add(direction1);
                        queue.add(worldrenderer$renderchunkinfo);
                    }
                }
            }
        }

        if (this.captureFrustum) {
            this.captureFrustum(d3, d4, d5);
            this.captureFrustum = false;
        }

        this.chunkRenderDispatcher.clear();
        Set<RenderChunk> set = this.dirtyChunks;
        this.dirtyChunks = Sets.newLinkedHashSet();

        for (WorldRenderer.RenderChunkInfo worldrenderer$renderchunkinfo2 : this.compiledChunks) {
            RenderChunk renderchunk4 = worldrenderer$renderchunkinfo2.chunk;
            if (renderchunk4.isDirty() || set.contains(renderchunk4)) {
                this.viewChanged = true;
                if (this.isNear(blockpos, worldrenderer$renderchunkinfo2.chunk)) {
                    this.minecraft.profiler.push("build near");
                    this.chunkRenderDispatcher.rebuildSync(renderchunk4);
                    renderchunk4.setDirty(false);
                    this.minecraft.profiler.pop();
                } else {
                    this.dirtyChunks.add(renderchunk4);
                }
            }
        }

        this.dirtyChunks.addAll(set);
        this.minecraft.profiler.pop();
    }

    private boolean isNear(BlockPos pos, RenderChunk chunk) {
        BlockPos blockpos = chunk.getOrigin();
        return MathHelper.abs(pos.getX() - blockpos.getX()) <= 16
            && MathHelper.abs(pos.getY() - blockpos.getY()) <= 16
            && MathHelper.abs(pos.getZ() - blockpos.getZ()) <= 16;
    }

    private Set<Direction> getOpenChunkFaces(BlockPos pos) {
        ChunkOcclusionGraph chunkocclusiongraph = new ChunkOcclusionGraph();
        BlockPos blockpos = new BlockPos(pos.getX() >> 4 << 4, pos.getY() >> 4 << 4, pos.getZ() >> 4 << 4);
        WorldChunk worldchunk = this.world.getChunk(blockpos);

        for (BlockPos.Mutable blockpos$mutable : BlockPos.iterateRegionMutable(blockpos, blockpos.add(15, 15, 15))) {
            if (worldchunk.getBlock(blockpos$mutable).isSolidRender()) {
                chunkocclusiongraph.close(blockpos$mutable);
            }
        }

        return chunkocclusiongraph.getFaces(pos);
    }

    private RenderChunk getNeighborChunk(BlockPos cameraPos, RenderChunk chunk, Direction dir) {
        BlockPos blockpos = chunk.getRelativeOrigin(dir);
        if (MathHelper.abs(cameraPos.getX() - blockpos.getX()) > this.lastViewDistance * 16) {
            return null;
        } else if (blockpos.getY() < 0 || blockpos.getY() >= 256) {
            return null;
        } else {
            return MathHelper.abs(cameraPos.getZ() - blockpos.getZ()) > this.lastViewDistance * 16 ? null : this.chunkStorage.getChunk(blockpos);
        }
    }

    private void captureFrustum(double x, double y, double z) {
        this.capturedFrustum = new Frustum();
        ((Frustum)this.capturedFrustum).compute();
        FrustumMatrix frustummatrix = new FrustumMatrix(this.capturedFrustum.modelMatrix);
        frustummatrix.transpose();
        FrustumMatrix frustummatrix1 = new FrustumMatrix(this.capturedFrustum.projectionMatrix);
        frustummatrix1.transpose();
        FrustumMatrix frustummatrix2 = new FrustumMatrix();
        FrustumMatrix.mul(frustummatrix1, frustummatrix, frustummatrix2);
        frustummatrix2.invert();
        this.frustumPos.x = x;
        this.frustumPos.y = y;
        this.frustumPos.z = z;
        this.frustumPoints[0] = new Vector4f(-1.0F, -1.0F, -1.0F, 1.0F);
        this.frustumPoints[1] = new Vector4f(1.0F, -1.0F, -1.0F, 1.0F);
        this.frustumPoints[2] = new Vector4f(1.0F, 1.0F, -1.0F, 1.0F);
        this.frustumPoints[3] = new Vector4f(-1.0F, 1.0F, -1.0F, 1.0F);
        this.frustumPoints[4] = new Vector4f(-1.0F, -1.0F, 1.0F, 1.0F);
        this.frustumPoints[5] = new Vector4f(1.0F, -1.0F, 1.0F, 1.0F);
        this.frustumPoints[6] = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.frustumPoints[7] = new Vector4f(-1.0F, 1.0F, 1.0F, 1.0F);

        for (int i = 0; i < 8; i++) {
            FrustumMatrix.transform(frustummatrix2, this.frustumPoints[i], this.frustumPoints[i]);
            this.frustumPoints[i].x = this.frustumPoints[i].x / this.frustumPoints[i].w;
            this.frustumPoints[i].y = this.frustumPoints[i].y / this.frustumPoints[i].w;
            this.frustumPoints[i].z = this.frustumPoints[i].z / this.frustumPoints[i].w;
            this.frustumPoints[i].w = 1.0F;
        }
    }

    protected Vector3f getLookVector(Entity camera, double tickDelta) {
        float f = (float)(camera.lastPitch + (camera.pitch - camera.lastPitch) * tickDelta);
        float f1 = (float)(camera.lastYaw + (camera.yaw - camera.lastYaw) * tickDelta);
        if (Minecraft.getInstance().options.perspective == 2) {
            f += 180.0F;
        }

        float f2 = MathHelper.cos(-f1 * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f3 = MathHelper.sin(-f1 * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f4 = -MathHelper.cos(-f * (float) (Math.PI / 180.0));
        float f5 = MathHelper.sin(-f * (float) (Math.PI / 180.0));
        return new Vector3f(f3 * f4, f5, f2 * f4);
    }

    public int render(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera) {
        Lighting.turnOff();
        if (layer == BlockLayer.TRANSLUCENT) {
            this.minecraft.profiler.push("translucent_sort");
            double d0 = camera.x - this.lastCameraX;
            double d1 = camera.y - this.lastCameraY;
            double d2 = camera.z - this.lastCameraZ;
            if (d0 * d0 + d1 * d1 + d2 * d2 > 1.0) {
                this.lastCameraX = camera.x;
                this.lastCameraY = camera.y;
                this.lastCameraZ = camera.z;
                int k = 0;

                for (WorldRenderer.RenderChunkInfo worldrenderer$renderchunkinfo : this.compiledChunks) {
                    if (worldrenderer$renderchunkinfo.chunk.compiled.hasLayer(layer) && k++ < 15) {
                        this.chunkRenderDispatcher.resortTransparency(worldrenderer$renderchunkinfo.chunk);
                    }
                }
            }

            this.minecraft.profiler.pop();
        }

        this.minecraft.profiler.push("filterempty");
        int l = 0;
        boolean flag = layer == BlockLayer.TRANSLUCENT;
        int i1 = flag ? this.compiledChunks.size() - 1 : 0;
        int i = flag ? -1 : this.compiledChunks.size();
        int j1 = flag ? -1 : 1;

        for (int j = i1; j != i; j += j1) {
            RenderChunk renderchunk = this.compiledChunks.get(j).chunk;
            if (!renderchunk.getCompiledChunk().hasBlock(layer)) {
                l++;
                this.chunkList.add(renderchunk, layer);
            }
        }

        this.minecraft.profiler.swap("render_" + layer);
        this.renderLastChunks(layer);
        this.minecraft.profiler.pop();
        return l;
    }

    private void renderLastChunks(BlockLayer layer) {
        this.minecraft.gameRenderer.enableLightMap();
        if (GLX.useVbo()) {
            GL11.glEnableClientState(32884);
            GLX.clientActiveTexture(GLX.GL_TEXTURE0);
            GL11.glEnableClientState(32888);
            GLX.clientActiveTexture(GLX.GL_TEXTURE1);
            GL11.glEnableClientState(32888);
            GLX.clientActiveTexture(GLX.GL_TEXTURE0);
            GL11.glEnableClientState(32886);
        }

        this.chunkList.render(layer);
        if (GLX.useVbo()) {
            for (VertexFormatElement vertexformatelement : DefaultVertexFormat.BLOCK.getElements()) {
                VertexFormatElement.Usage vertexformatelement$usage = vertexformatelement.getUsage();
                int i = vertexformatelement.getIndex();
                switch (vertexformatelement$usage) {
                    case POSITION:
                        GL11.glDisableClientState(32884);
                        break;
                    case UV:
                        GLX.clientActiveTexture(GLX.GL_TEXTURE0 + i);
                        GL11.glDisableClientState(32888);
                        GLX.clientActiveTexture(GLX.GL_TEXTURE0);
                        break;
                    case COLOR:
                        GL11.glDisableClientState(32886);
                        GlStateManager.clearColor();
                }
            }
        }

        this.minecraft.gameRenderer.disableLightMap();
    }

    private void updateMiningProgress(Iterator<BlockMiningProgress> miningProgress) {
        while (miningProgress.hasNext()) {
            BlockMiningProgress blockminingprogress = miningProgress.next();
            int i = blockminingprogress.getLastUpdateTick();
            if (this.ticks - i > 400) {
                miningProgress.remove();
            }
        }
    }

    public void tick() {
        this.ticks++;
        if (this.ticks % 20 == 0) {
            this.updateMiningProgress(this.miningProgress.values().iterator());
        }
    }

    private void renderEndSky() {
        GlStateManager.disableFog();
        GlStateManager.disableAlphaTest();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        Lighting.turnOff();
        GlStateManager.depthMask(false);
        this.textureManager.bind(END_SKY_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();

        for (int i = 0; i < 6; i++) {
            GlStateManager.pushMatrix();
            if (i == 1) {
                GlStateManager.rotatef(90.0F, 1.0F, 0.0F, 0.0F);
            }

            if (i == 2) {
                GlStateManager.rotatef(-90.0F, 1.0F, 0.0F, 0.0F);
            }

            if (i == 3) {
                GlStateManager.rotatef(180.0F, 1.0F, 0.0F, 0.0F);
            }

            if (i == 4) {
                GlStateManager.rotatef(90.0F, 0.0F, 0.0F, 1.0F);
            }

            if (i == 5) {
                GlStateManager.rotatef(-90.0F, 0.0F, 0.0F, 1.0F);
            }

            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.vertex(-100.0, -100.0, -100.0).texture(0.0, 0.0).color(40, 40, 40, 255).nextVertex();
            bufferbuilder.vertex(-100.0, -100.0, 100.0).texture(0.0, 16.0).color(40, 40, 40, 255).nextVertex();
            bufferbuilder.vertex(100.0, -100.0, 100.0).texture(16.0, 16.0).color(40, 40, 40, 255).nextVertex();
            bufferbuilder.vertex(100.0, -100.0, -100.0).texture(16.0, 0.0).color(40, 40, 40, 255).nextVertex();
            tesselator.end();
            GlStateManager.popMatrix();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture();
        GlStateManager.enableAlphaTest();
    }

    public void renderSky(float tickDelta, int anaglyphRenderPass) {
        if (this.minecraft.world.dimension.getId() == 1) {
            this.renderEndSky();
        } else if (this.minecraft.world.dimension.isNatural()) {
            GlStateManager.disableTexture();
            Vec3d vec3d = this.world.getSkyColor(this.minecraft.getCamera(), tickDelta);
            float f = (float)vec3d.x;
            float f1 = (float)vec3d.y;
            float f2 = (float)vec3d.z;
            if (anaglyphRenderPass != 2) {
                float f3 = (f * 30.0F + f1 * 59.0F + f2 * 11.0F) / 100.0F;
                float f4 = (f * 30.0F + f1 * 70.0F) / 100.0F;
                float f5 = (f * 30.0F + f2 * 70.0F) / 100.0F;
                f = f3;
                f1 = f4;
                f2 = f5;
            }

            GlStateManager.color3f(f, f1, f2);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            GlStateManager.depthMask(false);
            GlStateManager.enableFog();
            GlStateManager.color3f(f, f1, f2);
            if (this.useVbo) {
                this.lightSkyBuffer.bind();
                GL11.glEnableClientState(32884);
                GL11.glVertexPointer(3, 5126, 12, 0L);
                this.lightSkyBuffer.draw(7);
                this.lightSkyBuffer.unbind();
                GL11.glDisableClientState(32884);
            } else {
                GlStateManager.callList(this.lightSkyGlList);
            }

            GlStateManager.disableFog();
            GlStateManager.disableAlphaTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            Lighting.turnOff();
            float[] afloat = this.world.dimension.getSunriseColor(this.world.getTimeOfDay(tickDelta), tickDelta);
            if (afloat != null) {
                GlStateManager.disableTexture();
                GlStateManager.shadeModel(7425);
                GlStateManager.pushMatrix();
                GlStateManager.rotatef(90.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(MathHelper.sin(this.world.getSunAngle(tickDelta)) < 0.0F ? 180.0F : 0.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotatef(90.0F, 0.0F, 0.0F, 1.0F);
                float f6 = afloat[0];
                float f7 = afloat[1];
                float f8 = afloat[2];
                if (anaglyphRenderPass != 2) {
                    float f9 = (f6 * 30.0F + f7 * 59.0F + f8 * 11.0F) / 100.0F;
                    float f10 = (f6 * 30.0F + f7 * 70.0F) / 100.0F;
                    float f11 = (f6 * 30.0F + f8 * 70.0F) / 100.0F;
                    f6 = f9;
                    f7 = f10;
                    f8 = f11;
                }

                bufferbuilder.begin(6, DefaultVertexFormat.POSITION_COLOR);
                bufferbuilder.vertex(0.0, 100.0, 0.0).color(f6, f7, f8, afloat[3]).nextVertex();
                int j = 16;

                for (int l = 0; l <= 16; l++) {
                    float f21 = l * (float) Math.PI * 2.0F / 16.0F;
                    float f12 = MathHelper.sin(f21);
                    float f13 = MathHelper.cos(f21);
                    bufferbuilder.vertex(f12 * 120.0F, f13 * 120.0F, -f13 * 40.0F * afloat[3]).color(afloat[0], afloat[1], afloat[2], 0.0F).nextVertex();
                }

                tesselator.end();
                GlStateManager.popMatrix();
                GlStateManager.shadeModel(7424);
            }

            GlStateManager.enableTexture();
            GlStateManager.blendFuncSeparate(770, 1, 1, 0);
            GlStateManager.pushMatrix();
            float f16 = 1.0F - this.world.getRain(tickDelta);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, f16);
            GlStateManager.rotatef(-90.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(this.world.getTimeOfDay(tickDelta) * 360.0F, 1.0F, 0.0F, 0.0F);
            float f17 = 30.0F;
            this.textureManager.bind(SUN_LOCATION);
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(-f17, 100.0, -f17).texture(0.0, 0.0).nextVertex();
            bufferbuilder.vertex(f17, 100.0, -f17).texture(1.0, 0.0).nextVertex();
            bufferbuilder.vertex(f17, 100.0, f17).texture(1.0, 1.0).nextVertex();
            bufferbuilder.vertex(-f17, 100.0, f17).texture(0.0, 1.0).nextVertex();
            tesselator.end();
            f17 = 20.0F;
            this.textureManager.bind(MOON_PHASES_LOCATION);
            int i = this.world.getMoonPhase();
            int k = i % 4;
            int i1 = i / 4 % 2;
            float f22 = (k + 0) / 4.0F;
            float f23 = (i1 + 0) / 2.0F;
            float f24 = (k + 1) / 4.0F;
            float f14 = (i1 + 1) / 2.0F;
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(-f17, -100.0, f17).texture(f24, f14).nextVertex();
            bufferbuilder.vertex(f17, -100.0, f17).texture(f22, f14).nextVertex();
            bufferbuilder.vertex(f17, -100.0, -f17).texture(f22, f23).nextVertex();
            bufferbuilder.vertex(-f17, -100.0, -f17).texture(f24, f23).nextVertex();
            tesselator.end();
            GlStateManager.disableTexture();
            float f15 = this.world.getStarBrightness(tickDelta) * f16;
            if (f15 > 0.0F) {
                GlStateManager.color4f(f15, f15, f15, f15);
                if (this.useVbo) {
                    this.starsBuffer.bind();
                    GL11.glEnableClientState(32884);
                    GL11.glVertexPointer(3, 5126, 12, 0L);
                    this.starsBuffer.draw(7);
                    this.starsBuffer.unbind();
                    GL11.glDisableClientState(32884);
                } else {
                    GlStateManager.callList(this.starsGlList);
                }
            }

            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.enableAlphaTest();
            GlStateManager.enableFog();
            GlStateManager.popMatrix();
            GlStateManager.disableTexture();
            GlStateManager.color3f(0.0F, 0.0F, 0.0F);
            double d0 = this.minecraft.player.getEyePosition(tickDelta).y - this.world.getHorizonHeight();
            if (d0 < 0.0) {
                GlStateManager.pushMatrix();
                GlStateManager.translatef(0.0F, 12.0F, 0.0F);
                if (this.useVbo) {
                    this.darkSkyBuffer.bind();
                    GL11.glEnableClientState(32884);
                    GL11.glVertexPointer(3, 5126, 12, 0L);
                    this.darkSkyBuffer.draw(7);
                    this.darkSkyBuffer.unbind();
                    GL11.glDisableClientState(32884);
                } else {
                    GlStateManager.callList(this.darkSkyGlList);
                }

                GlStateManager.popMatrix();
                float f18 = 1.0F;
                float f19 = -((float)(d0 + 65.0));
                float f20 = -1.0F;
                f22 = f19;
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
                bufferbuilder.vertex(-1.0, f22, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, f22, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, -1.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, -1.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, -1.0, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, -1.0, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, f22, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, f22, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, -1.0, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, -1.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, f22, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, f22, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, f22, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, f22, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, -1.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, -1.0, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, -1.0, -1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(-1.0, -1.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, -1.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(1.0, -1.0, -1.0).color(0, 0, 0, 255).nextVertex();
                tesselator.end();
            }

            if (this.world.dimension.hasGround()) {
                GlStateManager.color3f(f * 0.2F + 0.04F, f1 * 0.2F + 0.04F, f2 * 0.6F + 0.1F);
            } else {
                GlStateManager.color3f(f, f1, f2);
            }

            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.0F, -((float)(d0 - 16.0)), 0.0F);
            GlStateManager.callList(this.darkSkyGlList);
            GlStateManager.popMatrix();
            GlStateManager.enableTexture();
            GlStateManager.depthMask(true);
        }
    }

    public void renderClouds(float tickDelta, int anaglyphRenderPass) {
        if (this.minecraft.world.dimension.isNatural()) {
            if (this.minecraft.options.getCloudRenderMode() == 2) {
                this.renderFancyClouds(tickDelta, anaglyphRenderPass);
            } else {
                GlStateManager.disableCull();
                float f = (float)(this.minecraft.getCamera().prevY + (this.minecraft.getCamera().y - this.minecraft.getCamera().prevY) * tickDelta);
                int i = 32;
                int j = 8;
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferbuilder = tesselator.getBuffer();
                this.textureManager.bind(CLOUDS_LOCATION);
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                Vec3d vec3d = this.world.getCloudColor(tickDelta);
                float f1 = (float)vec3d.x;
                float f2 = (float)vec3d.y;
                float f3 = (float)vec3d.z;
                if (anaglyphRenderPass != 2) {
                    float f4 = (f1 * 30.0F + f2 * 59.0F + f3 * 11.0F) / 100.0F;
                    float f5 = (f1 * 30.0F + f2 * 70.0F) / 100.0F;
                    float f6 = (f1 * 30.0F + f3 * 70.0F) / 100.0F;
                    f1 = f4;
                    f2 = f5;
                    f3 = f6;
                }

                float f10 = 4.8828125E-4F;
                double d2 = this.ticks + tickDelta;
                double d0 = this.minecraft.getCamera().lastX + (this.minecraft.getCamera().x - this.minecraft.getCamera().lastX) * tickDelta + d2 * 0.03F;
                double d1 = this.minecraft.getCamera().lastZ + (this.minecraft.getCamera().z - this.minecraft.getCamera().lastZ) * tickDelta;
                int k = MathHelper.floor(d0 / 2048.0);
                int l = MathHelper.floor(d1 / 2048.0);
                d0 -= k * 2048;
                d1 -= l * 2048;
                float f7 = this.world.dimension.getCloudHeight() - f + 0.33F;
                float f8 = (float)(d0 * 4.8828125E-4);
                float f9 = (float)(d1 * 4.8828125E-4);
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);

                for (int i1 = -256; i1 < 256; i1 += 32) {
                    for (int j1 = -256; j1 < 256; j1 += 32) {
                        bufferbuilder.vertex(i1 + 0, f7, j1 + 32)
                            .texture((i1 + 0) * 4.8828125E-4F + f8, (j1 + 32) * 4.8828125E-4F + f9)
                            .color(f1, f2, f3, 0.8F)
                            .nextVertex();
                        bufferbuilder.vertex(i1 + 32, f7, j1 + 32)
                            .texture((i1 + 32) * 4.8828125E-4F + f8, (j1 + 32) * 4.8828125E-4F + f9)
                            .color(f1, f2, f3, 0.8F)
                            .nextVertex();
                        bufferbuilder.vertex(i1 + 32, f7, j1 + 0)
                            .texture((i1 + 32) * 4.8828125E-4F + f8, (j1 + 0) * 4.8828125E-4F + f9)
                            .color(f1, f2, f3, 0.8F)
                            .nextVertex();
                        bufferbuilder.vertex(i1 + 0, f7, j1 + 0)
                            .texture((i1 + 0) * 4.8828125E-4F + f8, (j1 + 0) * 4.8828125E-4F + f9)
                            .color(f1, f2, f3, 0.8F)
                            .nextVertex();
                    }
                }

                tesselator.end();
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.disableBlend();
                GlStateManager.enableCull();
            }
        }
    }

    public boolean hasThiccFog(double x, double y, double z, float tickDelta) {
        return false;
    }

    private void renderFancyClouds(float tickDelta, int anaglyphRenderPass) {
        GlStateManager.disableCull();
        float f = (float)(this.minecraft.getCamera().prevY + (this.minecraft.getCamera().y - this.minecraft.getCamera().prevY) * tickDelta);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        float f1 = 12.0F;
        float f2 = 4.0F;
        double d0 = this.ticks + tickDelta;
        double d1 = (this.minecraft.getCamera().lastX + (this.minecraft.getCamera().x - this.minecraft.getCamera().lastX) * tickDelta + d0 * 0.03F) / 12.0;
        double d2 = (this.minecraft.getCamera().lastZ + (this.minecraft.getCamera().z - this.minecraft.getCamera().lastZ) * tickDelta) / 12.0 + 0.33F;
        float f3 = this.world.dimension.getCloudHeight() - f + 0.33F;
        int i = MathHelper.floor(d1 / 2048.0);
        int j = MathHelper.floor(d2 / 2048.0);
        d1 -= i * 2048;
        d2 -= j * 2048;
        this.textureManager.bind(CLOUDS_LOCATION);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        Vec3d vec3d = this.world.getCloudColor(tickDelta);
        float f4 = (float)vec3d.x;
        float f5 = (float)vec3d.y;
        float f6 = (float)vec3d.z;
        if (anaglyphRenderPass != 2) {
            float f7 = (f4 * 30.0F + f5 * 59.0F + f6 * 11.0F) / 100.0F;
            float f8 = (f4 * 30.0F + f5 * 70.0F) / 100.0F;
            float f9 = (f4 * 30.0F + f6 * 70.0F) / 100.0F;
            f4 = f7;
            f5 = f8;
            f6 = f9;
        }

        float f26 = f4 * 0.9F;
        float f27 = f5 * 0.9F;
        float f28 = f6 * 0.9F;
        float f10 = f4 * 0.7F;
        float f11 = f5 * 0.7F;
        float f12 = f6 * 0.7F;
        float f13 = f4 * 0.8F;
        float f14 = f5 * 0.8F;
        float f15 = f6 * 0.8F;
        float f16 = 0.00390625F;
        float f17 = MathHelper.floor(d1) * 0.00390625F;
        float f18 = MathHelper.floor(d2) * 0.00390625F;
        float f19 = (float)(d1 - MathHelper.floor(d1));
        float f20 = (float)(d2 - MathHelper.floor(d2));
        int k = 8;
        int l = 4;
        float f21 = 9.765625E-4F;
        GlStateManager.scalef(12.0F, 1.0F, 12.0F);

        for (int i1 = 0; i1 < 2; i1++) {
            if (i1 == 0) {
                GlStateManager.colorMask(false, false, false, false);
            } else {
                switch (anaglyphRenderPass) {
                    case 0:
                        GlStateManager.colorMask(false, true, true, true);
                        break;
                    case 1:
                        GlStateManager.colorMask(true, false, false, true);
                        break;
                    case 2:
                        GlStateManager.colorMask(true, true, true, true);
                }
            }

            for (int j1 = -3; j1 <= 4; j1++) {
                for (int k1 = -3; k1 <= 4; k1++) {
                    bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
                    float f22 = j1 * 8;
                    float f23 = k1 * 8;
                    float f24 = f22 - f19;
                    float f25 = f23 - f20;
                    if (f3 > -5.0F) {
                        bufferbuilder.vertex(f24 + 0.0F, f3 + 0.0F, f25 + 8.0F)
                            .texture((f22 + 0.0F) * 0.00390625F + f17, (f23 + 8.0F) * 0.00390625F + f18)
                            .color(f10, f11, f12, 0.8F)
                            .normal(0.0F, -1.0F, 0.0F)
                            .nextVertex();
                        bufferbuilder.vertex(f24 + 8.0F, f3 + 0.0F, f25 + 8.0F)
                            .texture((f22 + 8.0F) * 0.00390625F + f17, (f23 + 8.0F) * 0.00390625F + f18)
                            .color(f10, f11, f12, 0.8F)
                            .normal(0.0F, -1.0F, 0.0F)
                            .nextVertex();
                        bufferbuilder.vertex(f24 + 8.0F, f3 + 0.0F, f25 + 0.0F)
                            .texture((f22 + 8.0F) * 0.00390625F + f17, (f23 + 0.0F) * 0.00390625F + f18)
                            .color(f10, f11, f12, 0.8F)
                            .normal(0.0F, -1.0F, 0.0F)
                            .nextVertex();
                        bufferbuilder.vertex(f24 + 0.0F, f3 + 0.0F, f25 + 0.0F)
                            .texture((f22 + 0.0F) * 0.00390625F + f17, (f23 + 0.0F) * 0.00390625F + f18)
                            .color(f10, f11, f12, 0.8F)
                            .normal(0.0F, -1.0F, 0.0F)
                            .nextVertex();
                    }

                    if (f3 <= 5.0F) {
                        bufferbuilder.vertex(f24 + 0.0F, f3 + 4.0F - 9.765625E-4F, f25 + 8.0F)
                            .texture((f22 + 0.0F) * 0.00390625F + f17, (f23 + 8.0F) * 0.00390625F + f18)
                            .color(f4, f5, f6, 0.8F)
                            .normal(0.0F, 1.0F, 0.0F)
                            .nextVertex();
                        bufferbuilder.vertex(f24 + 8.0F, f3 + 4.0F - 9.765625E-4F, f25 + 8.0F)
                            .texture((f22 + 8.0F) * 0.00390625F + f17, (f23 + 8.0F) * 0.00390625F + f18)
                            .color(f4, f5, f6, 0.8F)
                            .normal(0.0F, 1.0F, 0.0F)
                            .nextVertex();
                        bufferbuilder.vertex(f24 + 8.0F, f3 + 4.0F - 9.765625E-4F, f25 + 0.0F)
                            .texture((f22 + 8.0F) * 0.00390625F + f17, (f23 + 0.0F) * 0.00390625F + f18)
                            .color(f4, f5, f6, 0.8F)
                            .normal(0.0F, 1.0F, 0.0F)
                            .nextVertex();
                        bufferbuilder.vertex(f24 + 0.0F, f3 + 4.0F - 9.765625E-4F, f25 + 0.0F)
                            .texture((f22 + 0.0F) * 0.00390625F + f17, (f23 + 0.0F) * 0.00390625F + f18)
                            .color(f4, f5, f6, 0.8F)
                            .normal(0.0F, 1.0F, 0.0F)
                            .nextVertex();
                    }

                    if (j1 > -1) {
                        for (int l1 = 0; l1 < 8; l1++) {
                            bufferbuilder.vertex(f24 + l1 + 0.0F, f3 + 0.0F, f25 + 8.0F)
                                .texture((f22 + l1 + 0.5F) * 0.00390625F + f17, (f23 + 8.0F) * 0.00390625F + f18)
                                .color(f26, f27, f28, 0.8F)
                                .normal(-1.0F, 0.0F, 0.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + l1 + 0.0F, f3 + 4.0F, f25 + 8.0F)
                                .texture((f22 + l1 + 0.5F) * 0.00390625F + f17, (f23 + 8.0F) * 0.00390625F + f18)
                                .color(f26, f27, f28, 0.8F)
                                .normal(-1.0F, 0.0F, 0.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + l1 + 0.0F, f3 + 4.0F, f25 + 0.0F)
                                .texture((f22 + l1 + 0.5F) * 0.00390625F + f17, (f23 + 0.0F) * 0.00390625F + f18)
                                .color(f26, f27, f28, 0.8F)
                                .normal(-1.0F, 0.0F, 0.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + l1 + 0.0F, f3 + 0.0F, f25 + 0.0F)
                                .texture((f22 + l1 + 0.5F) * 0.00390625F + f17, (f23 + 0.0F) * 0.00390625F + f18)
                                .color(f26, f27, f28, 0.8F)
                                .normal(-1.0F, 0.0F, 0.0F)
                                .nextVertex();
                        }
                    }

                    if (j1 <= 1) {
                        for (int i2 = 0; i2 < 8; i2++) {
                            bufferbuilder.vertex(f24 + i2 + 1.0F - 9.765625E-4F, f3 + 0.0F, f25 + 8.0F)
                                .texture((f22 + i2 + 0.5F) * 0.00390625F + f17, (f23 + 8.0F) * 0.00390625F + f18)
                                .color(f26, f27, f28, 0.8F)
                                .normal(1.0F, 0.0F, 0.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + i2 + 1.0F - 9.765625E-4F, f3 + 4.0F, f25 + 8.0F)
                                .texture((f22 + i2 + 0.5F) * 0.00390625F + f17, (f23 + 8.0F) * 0.00390625F + f18)
                                .color(f26, f27, f28, 0.8F)
                                .normal(1.0F, 0.0F, 0.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + i2 + 1.0F - 9.765625E-4F, f3 + 4.0F, f25 + 0.0F)
                                .texture((f22 + i2 + 0.5F) * 0.00390625F + f17, (f23 + 0.0F) * 0.00390625F + f18)
                                .color(f26, f27, f28, 0.8F)
                                .normal(1.0F, 0.0F, 0.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + i2 + 1.0F - 9.765625E-4F, f3 + 0.0F, f25 + 0.0F)
                                .texture((f22 + i2 + 0.5F) * 0.00390625F + f17, (f23 + 0.0F) * 0.00390625F + f18)
                                .color(f26, f27, f28, 0.8F)
                                .normal(1.0F, 0.0F, 0.0F)
                                .nextVertex();
                        }
                    }

                    if (k1 > -1) {
                        for (int j2 = 0; j2 < 8; j2++) {
                            bufferbuilder.vertex(f24 + 0.0F, f3 + 4.0F, f25 + j2 + 0.0F)
                                .texture((f22 + 0.0F) * 0.00390625F + f17, (f23 + j2 + 0.5F) * 0.00390625F + f18)
                                .color(f13, f14, f15, 0.8F)
                                .normal(0.0F, 0.0F, -1.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + 8.0F, f3 + 4.0F, f25 + j2 + 0.0F)
                                .texture((f22 + 8.0F) * 0.00390625F + f17, (f23 + j2 + 0.5F) * 0.00390625F + f18)
                                .color(f13, f14, f15, 0.8F)
                                .normal(0.0F, 0.0F, -1.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + 8.0F, f3 + 0.0F, f25 + j2 + 0.0F)
                                .texture((f22 + 8.0F) * 0.00390625F + f17, (f23 + j2 + 0.5F) * 0.00390625F + f18)
                                .color(f13, f14, f15, 0.8F)
                                .normal(0.0F, 0.0F, -1.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + 0.0F, f3 + 0.0F, f25 + j2 + 0.0F)
                                .texture((f22 + 0.0F) * 0.00390625F + f17, (f23 + j2 + 0.5F) * 0.00390625F + f18)
                                .color(f13, f14, f15, 0.8F)
                                .normal(0.0F, 0.0F, -1.0F)
                                .nextVertex();
                        }
                    }

                    if (k1 <= 1) {
                        for (int k2 = 0; k2 < 8; k2++) {
                            bufferbuilder.vertex(f24 + 0.0F, f3 + 4.0F, f25 + k2 + 1.0F - 9.765625E-4F)
                                .texture((f22 + 0.0F) * 0.00390625F + f17, (f23 + k2 + 0.5F) * 0.00390625F + f18)
                                .color(f13, f14, f15, 0.8F)
                                .normal(0.0F, 0.0F, 1.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + 8.0F, f3 + 4.0F, f25 + k2 + 1.0F - 9.765625E-4F)
                                .texture((f22 + 8.0F) * 0.00390625F + f17, (f23 + k2 + 0.5F) * 0.00390625F + f18)
                                .color(f13, f14, f15, 0.8F)
                                .normal(0.0F, 0.0F, 1.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + 8.0F, f3 + 0.0F, f25 + k2 + 1.0F - 9.765625E-4F)
                                .texture((f22 + 8.0F) * 0.00390625F + f17, (f23 + k2 + 0.5F) * 0.00390625F + f18)
                                .color(f13, f14, f15, 0.8F)
                                .normal(0.0F, 0.0F, 1.0F)
                                .nextVertex();
                            bufferbuilder.vertex(f24 + 0.0F, f3 + 0.0F, f25 + k2 + 1.0F - 9.765625E-4F)
                                .texture((f22 + 0.0F) * 0.00390625F + f17, (f23 + k2 + 0.5F) * 0.00390625F + f18)
                                .color(f13, f14, f15, 0.8F)
                                .normal(0.0F, 0.0F, 1.0F)
                                .nextVertex();
                        }
                    }

                    tesselator.end();
                }
            }
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
    }

    public void compileChunksUntil(long time) {
        this.viewChanged = this.viewChanged | this.chunkRenderDispatcher.runTasksUntil(time);
        if (!this.dirtyChunks.isEmpty()) {
            Iterator<RenderChunk> iterator = this.dirtyChunks.iterator();

            while (iterator.hasNext()) {
                RenderChunk renderchunk = iterator.next();
                if (!this.chunkRenderDispatcher.rebuildAsync(renderchunk)) {
                    break;
                }

                renderchunk.setDirty(false);
                iterator.remove();
                long i = time - System.nanoTime();
                if (i < 0L) {
                    break;
                }
            }
        }
    }

    public void renderWorldBorder(Entity camera, float tickDelta) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        WorldBorder worldborder = this.world.getWorldBorder();
        double d0 = this.minecraft.options.viewDistance * 16;
        if (!(camera.x < worldborder.getMaxX() - d0)
            || !(camera.x > worldborder.getMinX() + d0)
            || !(camera.z < worldborder.getMaxZ() - d0)
            || !(camera.z > worldborder.getMinZ() + d0)) {
            double d1 = 1.0 - worldborder.getDistanceFrom(camera) / d0;
            d1 = Math.pow(d1, 4.0);
            double d2 = camera.prevX + (camera.x - camera.prevX) * tickDelta;
            double d3 = camera.prevY + (camera.y - camera.prevY) * tickDelta;
            double d4 = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 1, 1, 0);
            this.textureManager.bind(WORLD_BORDER_LOCATION);
            GlStateManager.depthMask(false);
            GlStateManager.pushMatrix();
            int i = worldborder.getStatus().getColor();
            float f = (i >> 16 & 0xFF) / 255.0F;
            float f1 = (i >> 8 & 0xFF) / 255.0F;
            float f2 = (i & 0xFF) / 255.0F;
            GlStateManager.color4f(f, f1, f2, (float)d1);
            GlStateManager.polygonOffset(-3.0F, -3.0F);
            GlStateManager.enablePolygonOffset();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.enableAlphaTest();
            GlStateManager.disableCull();
            float f3 = (float)(Minecraft.getTime() % 3000L) / 3000.0F;
            float f4 = 0.0F;
            float f5 = 0.0F;
            float f6 = 128.0F;
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.offset(-d2, -d3, -d4);
            double d5 = Math.max(MathHelper.floor(d4 - d0), worldborder.getMinZ());
            double d6 = Math.min(MathHelper.ceil(d4 + d0), worldborder.getMaxZ());
            if (d2 > worldborder.getMaxX() - d0) {
                float f7 = 0.0F;

                for (double d7 = d5; d7 < d6; f7 += 0.5F) {
                    double d8 = Math.min(1.0, d6 - d7);
                    float f8 = (float)d8 * 0.5F;
                    bufferbuilder.vertex(worldborder.getMaxX(), 256.0, d7).texture(f3 + f7, f3 + 0.0F).nextVertex();
                    bufferbuilder.vertex(worldborder.getMaxX(), 256.0, d7 + d8).texture(f3 + f8 + f7, f3 + 0.0F).nextVertex();
                    bufferbuilder.vertex(worldborder.getMaxX(), 0.0, d7 + d8).texture(f3 + f8 + f7, f3 + 128.0F).nextVertex();
                    bufferbuilder.vertex(worldborder.getMaxX(), 0.0, d7).texture(f3 + f7, f3 + 128.0F).nextVertex();
                    d7++;
                }
            }

            if (d2 < worldborder.getMinX() + d0) {
                float f9 = 0.0F;

                for (double d9 = d5; d9 < d6; f9 += 0.5F) {
                    double d12 = Math.min(1.0, d6 - d9);
                    float f12 = (float)d12 * 0.5F;
                    bufferbuilder.vertex(worldborder.getMinX(), 256.0, d9).texture(f3 + f9, f3 + 0.0F).nextVertex();
                    bufferbuilder.vertex(worldborder.getMinX(), 256.0, d9 + d12).texture(f3 + f12 + f9, f3 + 0.0F).nextVertex();
                    bufferbuilder.vertex(worldborder.getMinX(), 0.0, d9 + d12).texture(f3 + f12 + f9, f3 + 128.0F).nextVertex();
                    bufferbuilder.vertex(worldborder.getMinX(), 0.0, d9).texture(f3 + f9, f3 + 128.0F).nextVertex();
                    d9++;
                }
            }

            d5 = Math.max(MathHelper.floor(d2 - d0), worldborder.getMinX());
            d6 = Math.min(MathHelper.ceil(d2 + d0), worldborder.getMaxX());
            if (d4 > worldborder.getMaxZ() - d0) {
                float f10 = 0.0F;

                for (double d10 = d5; d10 < d6; f10 += 0.5F) {
                    double d13 = Math.min(1.0, d6 - d10);
                    float f13 = (float)d13 * 0.5F;
                    bufferbuilder.vertex(d10, 256.0, worldborder.getMaxZ()).texture(f3 + f10, f3 + 0.0F).nextVertex();
                    bufferbuilder.vertex(d10 + d13, 256.0, worldborder.getMaxZ()).texture(f3 + f13 + f10, f3 + 0.0F).nextVertex();
                    bufferbuilder.vertex(d10 + d13, 0.0, worldborder.getMaxZ()).texture(f3 + f13 + f10, f3 + 128.0F).nextVertex();
                    bufferbuilder.vertex(d10, 0.0, worldborder.getMaxZ()).texture(f3 + f10, f3 + 128.0F).nextVertex();
                    d10++;
                }
            }

            if (d4 < worldborder.getMinZ() + d0) {
                float f11 = 0.0F;

                for (double d11 = d5; d11 < d6; f11 += 0.5F) {
                    double d14 = Math.min(1.0, d6 - d11);
                    float f14 = (float)d14 * 0.5F;
                    bufferbuilder.vertex(d11, 256.0, worldborder.getMinZ()).texture(f3 + f11, f3 + 0.0F).nextVertex();
                    bufferbuilder.vertex(d11 + d14, 256.0, worldborder.getMinZ()).texture(f3 + f14 + f11, f3 + 0.0F).nextVertex();
                    bufferbuilder.vertex(d11 + d14, 0.0, worldborder.getMinZ()).texture(f3 + f14 + f11, f3 + 128.0F).nextVertex();
                    bufferbuilder.vertex(d11, 0.0, worldborder.getMinZ()).texture(f3 + f11, f3 + 128.0F).nextVertex();
                    d11++;
                }
            }

            tesselator.end();
            bufferbuilder.offset(0.0, 0.0, 0.0);
            GlStateManager.enableCull();
            GlStateManager.disableAlphaTest();
            GlStateManager.polygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
            GlStateManager.enableAlphaTest();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            GlStateManager.depthMask(true);
        }
    }

    private void setupMiningProgressState() {
        GlStateManager.blendFuncSeparate(774, 768, 1, 0);
        GlStateManager.enableBlend();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 0.5F);
        GlStateManager.polygonOffset(-3.0F, -3.0F);
        GlStateManager.enablePolygonOffset();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableAlphaTest();
        GlStateManager.pushMatrix();
    }

    private void restoreMiningProgressState() {
        GlStateManager.disableAlphaTest();
        GlStateManager.polygonOffset(0.0F, 0.0F);
        GlStateManager.disablePolygonOffset();
        GlStateManager.enableAlphaTest();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    public void renderMiningProgress(Tesselator tesselator, BufferBuilder bufferBuilder, Entity camera, float tickDelta) {
        double d0 = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double d1 = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double d2 = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        if (!this.miningProgress.isEmpty()) {
            this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
            this.setupMiningProgressState();
            bufferBuilder.begin(7, DefaultVertexFormat.BLOCK);
            bufferBuilder.offset(-d0, -d1, -d2);
            bufferBuilder.uncolored();
            Iterator<BlockMiningProgress> iterator = this.miningProgress.values().iterator();

            while (iterator.hasNext()) {
                BlockMiningProgress blockminingprogress = iterator.next();
                BlockPos blockpos = blockminingprogress.getPos();
                double d3 = blockpos.getX() - d0;
                double d4 = blockpos.getY() - d1;
                double d5 = blockpos.getZ() - d2;
                Block block = this.world.getBlockState(blockpos).getBlock();
                if (!(block instanceof ChestBlock) && !(block instanceof EnderChestBlock) && !(block instanceof SignBlock) && !(block instanceof SkullBlock)) {
                    if (d3 * d3 + d4 * d4 + d5 * d5 > 1024.0) {
                        iterator.remove();
                    } else {
                        BlockState blockstate = this.world.getBlockState(blockpos);
                        if (blockstate.getBlock().getMaterial() != Material.AIR) {
                            int i = blockminingprogress.getProgress();
                            TextureAtlasSprite textureatlassprite = this.miningProgressSprites[i];
                            BlockRenderDispatcher blockrenderdispatcher = this.minecraft.getBlockRenderDispatcher();
                            blockrenderdispatcher.renderMiningProgress(blockstate, blockpos, textureatlassprite, this.world);
                        }
                    }
                }
            }

            tesselator.end();
            bufferBuilder.offset(0.0, 0.0, 0.0);
            this.restoreMiningProgressState();
        }
    }

    public void renderBlockOutline(PlayerEntity camera, HitResult hit, int i, float tickDelta) {
        if (i == 0 && hit.type == HitResult.Type.BLOCK) {
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color4f(0.0F, 0.0F, 0.0F, 0.4F);
            GL11.glLineWidth(2.0F);
            GlStateManager.disableTexture();
            GlStateManager.depthMask(false);
            float f = 0.002F;
            BlockPos blockpos = hit.getPos();
            Block block = this.world.getBlockState(blockpos).getBlock();
            if (block.getMaterial() != Material.AIR && this.world.getWorldBorder().contains(blockpos)) {
                block.updateShape(this.world, blockpos);
                double d0 = camera.prevX + (camera.x - camera.prevX) * tickDelta;
                double d1 = camera.prevY + (camera.y - camera.prevY) * tickDelta;
                double d2 = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
                renderOutlineShape(block.getOutlineShape(this.world, blockpos).grown(0.002F, 0.002F, 0.002F).moved(-d0, -d1, -d2));
            }

            GlStateManager.depthMask(true);
            GlStateManager.enableTexture();
            GlStateManager.disableBlend();
        }
    }

    public static void renderOutlineShape(Box shape) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(3, DefaultVertexFormat.POSITION);
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.minZ).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.maxZ).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.maxZ).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).nextVertex();
        tesselator.end();
        bufferbuilder.begin(3, DefaultVertexFormat.POSITION);
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.minZ).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.maxZ).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.maxZ).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).nextVertex();
        tesselator.end();
        bufferbuilder.begin(1, DefaultVertexFormat.POSITION);
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.minZ).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.minZ).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.maxZ).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.maxZ).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.maxZ).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.maxZ).nextVertex();
        tesselator.end();
    }

    public static void renderOutlineShape(Box shape, int r, int g, int b, int a) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(3, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.minZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.maxZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.maxZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).color(r, g, b, a).nextVertex();
        tesselator.end();
        bufferbuilder.begin(3, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.minZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.maxZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.maxZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).color(r, g, b, a).nextVertex();
        tesselator.end();
        bufferbuilder.begin(1, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.minZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.minZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.maxZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.maxZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.maxZ).color(r, g, b, a).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.maxZ).color(r, g, b, a).nextVertex();
        tesselator.end();
    }

    private void markDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.chunkStorage.markDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public void notifyBlockChanged(BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        this.markDirty(i - 1, j - 1, k - 1, i + 1, j + 1, k + 1);
    }

    @Override
    public void notifyLightChanged(BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        this.markDirty(i - 1, j - 1, k - 1, i + 1, j + 1, k + 1);
    }

    @Override
    public void notifyRegionChanged(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.markDirty(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
    }

    @Override
    public void playRecordMusic(String record, BlockPos pos) {
        SoundInstance soundinstance = this.playingSongs.get(pos);
        if (soundinstance != null) {
            this.minecraft.getSoundManager().stop(soundinstance);
            this.playingSongs.remove(pos);
        }

        if (record != null) {
            MusicDiscItem musicdiscitem = MusicDiscItem.getByName(record);
            if (musicdiscitem != null) {
                this.minecraft.gui.setRecordPlayingOverlay(musicdiscitem.getDescription());
            }

            soundinstance = SimpleSoundInstance.of(new Identifier(record), pos.getX(), pos.getY(), pos.getZ());
            this.playingSongs.put(pos, soundinstance);
            this.minecraft.getSoundManager().play(soundinstance);
        }
    }

    @Override
    public void playSound(String sound, double x, double y, double z, float volume, float pitch) {
    }

    @Override
    public void playSound(PlayerEntity source, String sound, double x, double y, double z, float volume, float pitch) {
    }

    @Override
    public void addParticle(
        int type, boolean ignoreDistance, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters
    ) {
        try {
            this.tryAddParticle(type, ignoreDistance, x, y, z, velocityX, velocityY, velocityZ, parameters);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Exception while adding particle");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being added");
            crashreportcategory.add("ID", type);
            if (parameters != null) {
                crashreportcategory.add("Parameters", parameters);
            }

            crashreportcategory.add("Position", new Callable<String>() {
                public String call() throws Exception {
                    return CrashReportCategory.formatPosition(x, y, z);
                }
            });
            throw new CrashException(crashreport);
        }
    }

    private void addParticle(ParticleType type, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
        this.addParticle(type.getId(), type.ignoreDistance(), x, y, z, velocityX, velocityY, velocityZ, parameters);
    }

    private Particle tryAddParticle(
        int type, boolean ignoreDistance, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters
    ) {
        if (this.minecraft != null && this.minecraft.getCamera() != null && this.minecraft.particleManager != null) {
            int i = this.minecraft.options.particles;
            if (i == 1 && this.world.random.nextInt(3) == 0) {
                i = 2;
            }

            double d0 = this.minecraft.getCamera().x - x;
            double d1 = this.minecraft.getCamera().y - y;
            double d2 = this.minecraft.getCamera().z - z;
            if (ignoreDistance) {
                return this.minecraft.particleManager.addParticle(type, x, y, z, velocityX, velocityY, velocityZ, parameters);
            } else {
                double d3 = 16.0;
                if (d0 * d0 + d1 * d1 + d2 * d2 > 256.0) {
                    return null;
                } else {
                    return i > 1 ? null : this.minecraft.particleManager.addParticle(type, x, y, z, velocityX, velocityY, velocityZ, parameters);
                }
            }
        } else {
            return null;
        }
    }

    @Override
    public void notifyEntityAdded(Entity entity) {
    }

    @Override
    public void notifyEntityRemoved(Entity entity) {
    }

    public void releaseGlLists() {
    }

    @Override
    public void doGlobalEvent(int type, BlockPos pos, int data) {
        switch (type) {
            case 1013:
            case 1018:
                if (this.minecraft.getCamera() != null) {
                    double d0 = pos.getX() - this.minecraft.getCamera().x;
                    double d1 = pos.getY() - this.minecraft.getCamera().y;
                    double d2 = pos.getZ() - this.minecraft.getCamera().z;
                    double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                    double d4 = this.minecraft.getCamera().x;
                    double d5 = this.minecraft.getCamera().y;
                    double d6 = this.minecraft.getCamera().z;
                    if (d3 > 0.0) {
                        d4 += d0 / d3 * 2.0;
                        d5 += d1 / d3 * 2.0;
                        d6 += d2 / d3 * 2.0;
                    }

                    if (type == 1013) {
                        this.world.playSound(d4, d5, d6, "mob.wither.spawn", 1.0F, 1.0F, false);
                    } else {
                        this.world.playSound(d4, d5, d6, "mob.enderdragon.end", 5.0F, 1.0F, false);
                    }
                }
        }
    }

    @Override
    public void doEvent(PlayerEntity source, int type, BlockPos pos, int data) {
        Random random = this.world.random;
        switch (type) {
            case 1000:
                this.world.playSound(pos, "random.click", 1.0F, 1.0F, false);
                break;
            case 1001:
                this.world.playSound(pos, "random.click", 1.0F, 1.2F, false);
                break;
            case 1002:
                this.world.playSound(pos, "random.bow", 1.0F, 1.2F, false);
                break;
            case 1003:
                this.world.playSound(pos, "random.door_open", 1.0F, this.world.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1004:
                this.world.playSound(pos, "random.fizz", 0.5F, 2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F, false);
                break;
            case 1005:
                if (Item.byId(data) instanceof MusicDiscItem) {
                    this.world.playRecordMusic(pos, "records." + ((MusicDiscItem)Item.byId(data)).record);
                } else {
                    this.world.playRecordMusic(pos, null);
                }
                break;
            case 1006:
                this.world.playSound(pos, "random.door_close", 1.0F, this.world.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1007:
                this.world.playSound(pos, "mob.ghast.charge", 10.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1008:
                this.world.playSound(pos, "mob.ghast.fireball", 10.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1009:
                this.world.playSound(pos, "mob.ghast.fireball", 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1010:
                this.world.playSound(pos, "mob.zombie.wood", 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1011:
                this.world.playSound(pos, "mob.zombie.metal", 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1012:
                this.world.playSound(pos, "mob.zombie.woodbreak", 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1014:
                this.world.playSound(pos, "mob.wither.shoot", 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1015:
                this.world.playSound(pos, "mob.bat.takeoff", 0.05F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1016:
                this.world.playSound(pos, "mob.zombie.infect", 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1017:
                this.world.playSound(pos, "mob.zombie.unfect", 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
                break;
            case 1020:
                this.world.playSound(pos, "random.anvil_break", 1.0F, this.world.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1021:
                this.world.playSound(pos, "random.anvil_use", 1.0F, this.world.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 1022:
                this.world.playSound(pos, "random.anvil_land", 0.3F, this.world.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 2000:
                int l = data % 3 - 1;
                int i = data / 3 % 3 - 1;
                double d15 = pos.getX() + l * 0.6 + 0.5;
                double d17 = pos.getY() + 0.5;
                double d19 = pos.getZ() + i * 0.6 + 0.5;

                for (int k1 = 0; k1 < 10; k1++) {
                    double d20 = random.nextDouble() * 0.2 + 0.01;
                    double d21 = d15 + l * 0.01 + (random.nextDouble() - 0.5) * i * 0.5;
                    double d4 = d17 + (random.nextDouble() - 0.5) * 0.5;
                    double d6 = d19 + i * 0.01 + (random.nextDouble() - 0.5) * l * 0.5;
                    double d8 = l * d20 + random.nextGaussian() * 0.01;
                    double d10 = -0.03 + random.nextGaussian() * 0.01;
                    double d12 = i * d20 + random.nextGaussian() * 0.01;
                    this.addParticle(ParticleType.SMOKE_NORMAL, d21, d4, d6, d8, d10, d12);
                }
                break;
            case 2001:
                Block block = Block.byId(data & 4095);
                if (block.getMaterial() != Material.AIR) {
                    this.minecraft
                        .getSoundManager()
                        .play(
                            new SimpleSoundInstance(
                                new Identifier(block.sounds.getBreaking()),
                                (block.sounds.getVolume() + 1.0F) / 2.0F,
                                block.sounds.getPitch() * 0.8F,
                                pos.getX() + 0.5F,
                                pos.getY() + 0.5F,
                                pos.getZ() + 0.5F
                            )
                        );
                }

                this.minecraft.particleManager.addBlockMiningParticles(pos, block.getStateFromMetadata(data >> 12 & 0xFF));
                break;
            case 2002:
                double d13 = pos.getX();
                double d14 = pos.getY();
                double d16 = pos.getZ();

                for (int i1 = 0; i1 < 8; i1++) {
                    this.addParticle(
                        ParticleType.ITEM_CRACK,
                        d13,
                        d14,
                        d16,
                        random.nextGaussian() * 0.15,
                        random.nextDouble() * 0.2,
                        random.nextGaussian() * 0.15,
                        Item.getId(Items.POTION),
                        data
                    );
                }

                int j1 = Items.POTION.getPotionColor(data);
                float f = (j1 >> 16 & 0xFF) / 255.0F;
                float f1 = (j1 >> 8 & 0xFF) / 255.0F;
                float f2 = (j1 >> 0 & 0xFF) / 255.0F;
                ParticleType particletype = ParticleType.SPELL;
                if (Items.POTION.hasInstantPotionEffect(data)) {
                    particletype = ParticleType.SPELL_INSTANT;
                }

                for (int l1 = 0; l1 < 100; l1++) {
                    double d22 = random.nextDouble() * 4.0;
                    double d23 = random.nextDouble() * Math.PI * 2.0;
                    double d24 = Math.cos(d23) * d22;
                    double d9 = 0.01 + random.nextDouble() * 0.5;
                    double d11 = Math.sin(d23) * d22;
                    Particle particle = this.tryAddParticle(
                        particletype.getId(), particletype.ignoreDistance(), d13 + d24 * 0.1, d14 + 0.3, d16 + d11 * 0.1, d24, d9, d11
                    );
                    if (particle != null) {
                        float f3 = 0.75F + random.nextFloat() * 0.25F;
                        particle.setColor(f * f3, f1 * f3, f2 * f3);
                        particle.multiplyVelocity((float)d22);
                    }
                }

                this.world.playSound(pos, "game.potion.smash", 1.0F, this.world.random.nextFloat() * 0.1F + 0.9F, false);
                break;
            case 2003:
                double d0 = pos.getX() + 0.5;
                double d1 = pos.getY();
                double d2 = pos.getZ() + 0.5;

                for (int j = 0; j < 8; j++) {
                    this.addParticle(
                        ParticleType.ITEM_CRACK,
                        d0,
                        d1,
                        d2,
                        random.nextGaussian() * 0.15,
                        random.nextDouble() * 0.2,
                        random.nextGaussian() * 0.15,
                        Item.getId(Items.ENDER_EYE)
                    );
                }

                for (double d18 = 0.0; d18 < Math.PI * 2; d18 += Math.PI / 20) {
                    this.addParticle(
                        ParticleType.PORTAL, d0 + Math.cos(d18) * 5.0, d1 - 0.4, d2 + Math.sin(d18) * 5.0, Math.cos(d18) * -5.0, 0.0, Math.sin(d18) * -5.0
                    );
                    this.addParticle(
                        ParticleType.PORTAL, d0 + Math.cos(d18) * 5.0, d1 - 0.4, d2 + Math.sin(d18) * 5.0, Math.cos(d18) * -7.0, 0.0, Math.sin(d18) * -7.0
                    );
                }
                break;
            case 2004:
                for (int k = 0; k < 20; k++) {
                    double d3 = pos.getX() + 0.5 + (this.world.random.nextFloat() - 0.5) * 2.0;
                    double d5 = pos.getY() + 0.5 + (this.world.random.nextFloat() - 0.5) * 2.0;
                    double d7 = pos.getZ() + 0.5 + (this.world.random.nextFloat() - 0.5) * 2.0;
                    this.world.addParticle(ParticleType.SMOKE_NORMAL, d3, d5, d7, 0.0, 0.0, 0.0);
                    this.world.addParticle(ParticleType.FLAME, d3, d5, d7, 0.0, 0.0, 0.0);
                }
                break;
            case 2005:
                DyeItem.spawnParticles(this.world, pos, data);
        }
    }

    @Override
    public void updateBlockMiningProgress(int id, BlockPos pos, int progress) {
        if (progress >= 0 && progress < 10) {
            BlockMiningProgress blockminingprogress = this.miningProgress.get(id);
            if (blockminingprogress == null
                || blockminingprogress.getPos().getX() != pos.getX()
                || blockminingprogress.getPos().getY() != pos.getY()
                || blockminingprogress.getPos().getZ() != pos.getZ()) {
                blockminingprogress = new BlockMiningProgress(id, pos);
                this.miningProgress.put(id, blockminingprogress);
            }

            blockminingprogress.setProgress(progress);
            blockminingprogress.setLastUpdateTick(this.ticks);
        } else {
            this.miningProgress.remove(id);
        }
    }

    public void onViewChanged() {
        this.viewChanged = true;
    }

    public void updateGlobalBlockEntities(Collection<BlockEntity> remove, Collection<BlockEntity> add) {
        synchronized (this.globalBlockEntities) {
            this.globalBlockEntities.removeAll(remove);
            this.globalBlockEntities.addAll(add);
        }
    }

    class RenderChunkInfo {
        final RenderChunk chunk;
        final Direction sourceDir;
        final Set<Direction> culling = EnumSet.noneOf(Direction.class);
        final int step;

        private RenderChunkInfo(RenderChunk chunk, Direction sourceDir, int step) {
            this.chunk = chunk;
            this.sourceDir = sourceDir;
            this.step = step;
        }
    }
}
