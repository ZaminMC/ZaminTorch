package net.minecraft.client.render;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.gui.BossBar;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.shaders.ProgramManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.client.util.SmoothUtil;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.entity.living.mob.monster.SpiderEntity;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.Biome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.glu.Project;

public class GameRenderer implements ResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier RAIN_TEXTURE_LOCATION = new Identifier("textures/environment/rain.png");
    private static final Identifier SNOW_TEXTURE_LOCATION = new Identifier("textures/environment/snow.png");
    public static boolean anaglyphEnabled;
    public static int anaglyphFilter;
    private Minecraft minecraft;
    private final ResourceManager resourceManager;
    private Random random = new Random();
    private float renderDistance;
    public final ItemInHandRenderer itemInHandRenderer;
    private final MapRenderer mapRenderer;
    private int ticks;
    private Entity targetEntity;
    private SmoothUtil smoothUtilMouseX = new SmoothUtil();
    private SmoothUtil smoothUtilMouseY = new SmoothUtil();
    private float oldCamPos = 4.0F;
    private float newCamPos = 4.0F;
    private float totalMouseDX;
    private float totalMouseDY;
    private float smoothMouseDX;
    private float smoothMouseDY;
    private float lastTickDelta;
    private float fov;
    private float lastFov;
    private float skyDarkness;
    private float lastSkyDarkness;
    private boolean thiccFog;
    private boolean newCamPitch = true;
    private boolean renderBlockOutline = true;
    private long lastActiveTime = Minecraft.getTime();
    private long lastWorldRenderTime;
    private final DynamicTexture lightmapTexture;
    private final int[] lightMapPixels;
    private final Identifier lightMapTextureLocation;
    private boolean lightMapDirty;
    private float lightMapFlicker;
    private float lightMapFlickerTotal;
    private int weatherSoundAttempts;
    private float[] rainSizeX = new float[1024];
    private float[] rainSizeZ = new float[1024];
    private FloatBuffer colorBuffer = MemoryTracker.createFloatBuffer(16);
    private float fogRed;
    private float fogGreen;
    private float fogBlue;
    private float lastFogBrightness;
    private float fogBrightness;
    private int hasNausea = 0;
    private boolean debugCamera = false;
    private double zoom = 1.0;
    private double zoomX;
    private double zoomY;
    private PostChain shaderEffect;
    private static final Identifier[] SHADERS = new Identifier[]{
        new Identifier("shaders/post/notch.json"),
        new Identifier("shaders/post/fxaa.json"),
        new Identifier("shaders/post/art.json"),
        new Identifier("shaders/post/bumpy.json"),
        new Identifier("shaders/post/blobs2.json"),
        new Identifier("shaders/post/pencil.json"),
        new Identifier("shaders/post/color_convolve.json"),
        new Identifier("shaders/post/deconverge.json"),
        new Identifier("shaders/post/flip.json"),
        new Identifier("shaders/post/invert.json"),
        new Identifier("shaders/post/ntsc.json"),
        new Identifier("shaders/post/outline.json"),
        new Identifier("shaders/post/phosphor.json"),
        new Identifier("shaders/post/scan_pincushion.json"),
        new Identifier("shaders/post/sobel.json"),
        new Identifier("shaders/post/bits.json"),
        new Identifier("shaders/post/desaturate.json"),
        new Identifier("shaders/post/green.json"),
        new Identifier("shaders/post/blur.json"),
        new Identifier("shaders/post/wobble.json"),
        new Identifier("shaders/post/blobs.json"),
        new Identifier("shaders/post/antialias.json"),
        new Identifier("shaders/post/creeper.json"),
        new Identifier("shaders/post/spider.json")
    };
    public static final int SHADER_NONE = SHADERS.length;
    private int shaderIndex = SHADER_NONE;
    private boolean shadersEnabled = false;
    private int frameId = 0;

    public GameRenderer(Minecraft minecraft, ResourceManager resourceManager) {
        this.minecraft = minecraft;
        this.resourceManager = resourceManager;
        this.itemInHandRenderer = minecraft.getItemInHandRenderer();
        this.mapRenderer = new MapRenderer(minecraft.getTextureManager());
        this.lightmapTexture = new DynamicTexture(16, 16);
        this.lightMapTextureLocation = minecraft.getTextureManager().register("lightMap", this.lightmapTexture);
        this.lightMapPixels = this.lightmapTexture.getPixels();
        this.shaderEffect = null;

        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                float f = j - 16;
                float f1 = i - 16;
                float f2 = MathHelper.sqrt(f * f + f1 * f1);
                this.rainSizeX[i << 5 | j] = -f1 / f2;
                this.rainSizeZ[i << 5 | j] = f / f2;
            }
        }
    }

    public boolean hasShader() {
        return GLX.usePostProcess && this.shaderEffect != null;
    }

    public void closeShader() {
        if (this.shaderEffect != null) {
            this.shaderEffect.close();
        }

        this.shaderEffect = null;
        this.shaderIndex = SHADER_NONE;
    }

    public void disableShader() {
        this.shadersEnabled = !this.shadersEnabled;
    }

    public void updateShader(Entity camera) {
        if (GLX.usePostProcess) {
            if (this.shaderEffect != null) {
                this.shaderEffect.close();
            }

            this.shaderEffect = null;
            if (camera instanceof CreeperEntity) {
                this.loadShader(new Identifier("shaders/post/creeper.json"));
            } else if (camera instanceof SpiderEntity) {
                this.loadShader(new Identifier("shaders/post/spider.json"));
            } else if (camera instanceof EndermanEntity) {
                this.loadShader(new Identifier("shaders/post/invert.json"));
            }
        }
    }

    public void nextShader() {
        if (GLX.usePostProcess) {
            if (this.minecraft.getCamera() instanceof PlayerEntity) {
                if (this.shaderEffect != null) {
                    this.shaderEffect.close();
                }

                this.shaderIndex = (this.shaderIndex + 1) % (SHADERS.length + 1);
                if (this.shaderIndex != SHADER_NONE) {
                    this.loadShader(SHADERS[this.shaderIndex]);
                } else {
                    this.shaderEffect = null;
                }
            }
        }
    }

    private void loadShader(Identifier location) {
        try {
            this.shaderEffect = new PostChain(this.minecraft.getTextureManager(), this.resourceManager, this.minecraft.getRenderTarget(), location);
            this.shaderEffect.resize(this.minecraft.width, this.minecraft.height);
            this.shadersEnabled = true;
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load shader: " + location, ioexception);
            this.shaderIndex = SHADER_NONE;
            this.shadersEnabled = false;
        } catch (JsonSyntaxException jsonsyntaxexception) {
            LOGGER.warn("Failed to load shader: " + location, jsonsyntaxexception);
            this.shaderIndex = SHADER_NONE;
            this.shadersEnabled = false;
        }
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        if (this.shaderEffect != null) {
            this.shaderEffect.close();
        }

        this.shaderEffect = null;
        if (this.shaderIndex != SHADER_NONE) {
            this.loadShader(SHADERS[this.shaderIndex]);
        } else {
            this.updateShader(this.minecraft.getCamera());
        }
    }

    public void tick() {
        if (GLX.usePostProcess && ProgramManager.getInstance() == null) {
            ProgramManager.createInstance();
        }

        this.tickFov();
        this.tickLightMap();
        this.lastFogBrightness = this.fogBrightness;
        this.newCamPos = this.oldCamPos;
        if (this.minecraft.options.smoothCamera) {
            float f = this.minecraft.options.mouseSensitivity * 0.6F + 0.2F;
            float f1 = f * f * f * 8.0F;
            this.smoothMouseDX = this.smoothUtilMouseX.smooth(this.totalMouseDX, 0.05F * f1);
            this.smoothMouseDY = this.smoothUtilMouseY.smooth(this.totalMouseDY, 0.05F * f1);
            this.lastTickDelta = 0.0F;
            this.totalMouseDX = 0.0F;
            this.totalMouseDY = 0.0F;
        } else {
            this.smoothMouseDX = 0.0F;
            this.smoothMouseDY = 0.0F;
            this.smoothUtilMouseX.reset();
            this.smoothUtilMouseY.reset();
        }

        if (this.minecraft.getCamera() == null) {
            this.minecraft.setCamera(this.minecraft.player);
        }

        float f3 = this.minecraft.world.getBrightness(new BlockPos(this.minecraft.getCamera()));
        float f4 = this.minecraft.options.viewDistance / 32.0F;
        float f2 = f3 * (1.0F - f4) + f4;
        this.fogBrightness = this.fogBrightness + (f2 - this.fogBrightness) * 0.1F;
        this.ticks++;
        this.itemInHandRenderer.tick();
        this.tickRain();
        this.lastSkyDarkness = this.skyDarkness;
        if (BossBar.modifiesSkyColor) {
            this.skyDarkness += 0.05F;
            if (this.skyDarkness > 1.0F) {
                this.skyDarkness = 1.0F;
            }

            BossBar.modifiesSkyColor = false;
        } else if (this.skyDarkness > 0.0F) {
            this.skyDarkness -= 0.0125F;
        }
    }

    public PostChain getShader() {
        return this.shaderEffect;
    }

    public void onResolutionChanged(int width, int height) {
        if (GLX.usePostProcess) {
            if (this.shaderEffect != null) {
                this.shaderEffect.resize(width, height);
            }

            this.minecraft.worldRenderer.onResolutionChanged(width, height);
        }
    }

    public void pick(float tickDelta) {
        Entity entity = this.minecraft.getCamera();
        if (entity != null) {
            if (this.minecraft.world != null) {
                this.minecraft.profiler.push("pick");
                this.minecraft.targetEntity = null;
                double d0 = this.minecraft.interactionManager.getReach();
                this.minecraft.crosshairTarget = entity.rayTrace(d0, tickDelta);
                double d1 = d0;
                Vec3d vec3d = entity.getEyePosition(tickDelta);
                boolean flag = false;
                int i = 3;
                if (this.minecraft.interactionManager.hasExtendedReach()) {
                    d0 = 6.0;
                    d1 = 6.0;
                } else {
                    if (d1 > 3.0) {
                        flag = true;
                    }

                    d0 = d1;
                }

                if (this.minecraft.crosshairTarget != null) {
                    d1 = this.minecraft.crosshairTarget.facePos.distanceTo(vec3d);
                }

                Vec3d vec3d1 = entity.getRotationVec(tickDelta);
                Vec3d vec3d2 = vec3d.add(vec3d1.x * d0, vec3d1.y * d0, vec3d1.z * d0);
                this.targetEntity = null;
                Vec3d vec3d3 = null;
                float f = 1.0F;
                List<Entity> list = this.minecraft
                    .world
                    .getEntities(
                        entity,
                        entity.getShape().expanded(vec3d1.x * d0, vec3d1.y * d0, vec3d1.z * d0).grown(f, f, f),
                        Predicates.and(EntityFilter.NOT_SPECTATOR, new Predicate<Entity>() {
                            public boolean apply(Entity entity) {
                                return entity.hasCollision();
                            }
                        })
                    );
                double d2 = d1;

                for (int j = 0; j < list.size(); j++) {
                    Entity entity1 = list.get(j);
                    float f1 = entity1.getPickRadius();
                    Box box = entity1.getShape().grown(f1, f1, f1);
                    HitResult hitresult = box.clip(vec3d, vec3d2);
                    if (box.contains(vec3d)) {
                        if (d2 >= 0.0) {
                            this.targetEntity = entity1;
                            vec3d3 = hitresult == null ? vec3d : hitresult.facePos;
                            d2 = 0.0;
                        }
                    } else if (hitresult != null) {
                        double d3 = vec3d.distanceTo(hitresult.facePos);
                        if (d3 < d2 || d2 == 0.0) {
                            if (entity1 == entity.vehicle) {
                                if (d2 == 0.0) {
                                    this.targetEntity = entity1;
                                    vec3d3 = hitresult.facePos;
                                }
                            } else {
                                this.targetEntity = entity1;
                                vec3d3 = hitresult.facePos;
                                d2 = d3;
                            }
                        }
                    }
                }

                if (this.targetEntity != null && flag && vec3d.distanceTo(vec3d3) > 3.0) {
                    this.targetEntity = null;
                    this.minecraft.crosshairTarget = new HitResult(HitResult.Type.MISS, vec3d3, null, new BlockPos(vec3d3));
                }

                if (this.targetEntity != null && (d2 < d1 || this.minecraft.crosshairTarget == null)) {
                    this.minecraft.crosshairTarget = new HitResult(this.targetEntity, vec3d3);
                    if (this.targetEntity instanceof LivingEntity || this.targetEntity instanceof ItemFrameEntity) {
                        this.minecraft.targetEntity = this.targetEntity;
                    }
                }

                this.minecraft.profiler.pop();
            }
        }
    }

    private void tickFov() {
        float f = 1.0F;
        if (this.minecraft.getCamera() instanceof ClientPlayerEntity) {
            ClientPlayerEntity clientplayerentity = (ClientPlayerEntity)this.minecraft.getCamera();
            f = clientplayerentity.getFovModifier();
        }

        this.lastFov = this.fov;
        this.fov = this.fov + (f - this.fov) * 0.5F;
        if (this.fov > 1.5F) {
            this.fov = 1.5F;
        }

        if (this.fov < 0.1F) {
            this.fov = 0.1F;
        }
    }

    private float getFov(float tickDelta, boolean fovChanged) {
        if (this.debugCamera) {
            return 90.0F;
        }

        Entity entity = this.minecraft.getCamera();
        float f = 70.0F;
        if (fovChanged) {
            f = this.minecraft.options.fov;
            f *= this.lastFov + (this.fov - this.lastFov) * tickDelta;
        }

        if (entity instanceof LivingEntity && ((LivingEntity)entity).getHealth() <= 0.0F) {
            float f1 = ((LivingEntity)entity).deathTicks + tickDelta;
            f /= (1.0F - 500.0F / (f1 + 500.0F)) * 2.0F + 1.0F;
        }

        Block block = Camera.getBlockInside(this.minecraft.world, entity, tickDelta);
        if (block.getMaterial() == Material.WATER) {
            f = f * 60.0F / 70.0F;
        }

        return f;
    }

    private void applyHurtCam(float tickDelta) {
        if (this.minecraft.getCamera() instanceof LivingEntity) {
            LivingEntity livingentity = (LivingEntity)this.minecraft.getCamera();
            float f = livingentity.damagedTimer - tickDelta;
            if (livingentity.getHealth() <= 0.0F) {
                float f1 = livingentity.deathTicks + tickDelta;
                GlStateManager.rotatef(40.0F - 8000.0F / (f1 + 200.0F), 0.0F, 0.0F, 1.0F);
            }

            if (f < 0.0F) {
                return;
            }

            f /= livingentity.damagedTime;
            f = MathHelper.sin(f * f * f * f * (float) Math.PI);
            float f2 = livingentity.damagedSwingDir;
            GlStateManager.rotatef(-f2, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(-f * 14.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotatef(f2, 0.0F, 1.0F, 0.0F);
        }
    }

    private void applyViewBobbing(float tickDelta) {
        if (this.minecraft.getCamera() instanceof PlayerEntity) {
            PlayerEntity playerentity = (PlayerEntity)this.minecraft.getCamera();
            float f = playerentity.walkDistance - playerentity.lastWalkDistance;
            float f1 = -(playerentity.walkDistance + f * tickDelta);
            float f2 = playerentity.lastBob + (playerentity.bob - playerentity.lastBob) * tickDelta;
            float f3 = playerentity.lastTilt + (playerentity.tilt - playerentity.lastTilt) * tickDelta;
            GlStateManager.translatef(MathHelper.sin(f1 * (float) Math.PI) * f2 * 0.5F, -Math.abs(MathHelper.cos(f1 * (float) Math.PI) * f2), 0.0F);
            GlStateManager.rotatef(MathHelper.sin(f1 * (float) Math.PI) * f2 * 3.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotatef(Math.abs(MathHelper.cos(f1 * (float) Math.PI - 0.2F) * f2) * 5.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(f3, 1.0F, 0.0F, 0.0F);
        }
    }

    private void transformCamera(float tickDelta) {
        Entity entity = this.minecraft.getCamera();
        float f = entity.getEyeHeight();
        double d0 = entity.lastX + (entity.x - entity.lastX) * tickDelta;
        double d1 = entity.lastY + (entity.y - entity.lastY) * tickDelta + f;
        double d2 = entity.lastZ + (entity.z - entity.lastZ) * tickDelta;
        if (entity instanceof LivingEntity && ((LivingEntity)entity).isSleeping()) {
            f = (float)(f + 1.0);
            GlStateManager.translatef(0.0F, 0.3F, 0.0F);
            if (!this.minecraft.options.debugCamera) {
                BlockPos blockpos = new BlockPos(entity);
                BlockState blockstate = this.minecraft.world.getBlockState(blockpos);
                Block block = blockstate.getBlock();
                if (block == Blocks.BED) {
                    int j = blockstate.get(BedBlock.FACING).getIdHorizontal();
                    GlStateManager.rotatef(j * 90, 0.0F, 1.0F, 0.0F);
                }

                GlStateManager.rotatef(entity.lastYaw + (entity.yaw - entity.lastYaw) * tickDelta + 180.0F, 0.0F, -1.0F, 0.0F);
                GlStateManager.rotatef(entity.lastPitch + (entity.pitch - entity.lastPitch) * tickDelta, -1.0F, 0.0F, 0.0F);
            }
        } else if (this.minecraft.options.perspective > 0) {
            double d3 = this.newCamPos + (this.oldCamPos - this.newCamPos) * tickDelta;
            if (this.minecraft.options.debugCamera) {
                GlStateManager.translatef(0.0F, 0.0F, (float)(-d3));
            } else {
                float f1 = entity.yaw;
                float f2 = entity.pitch;
                if (this.minecraft.options.perspective == 2) {
                    f2 += 180.0F;
                }

                double d4 = -MathHelper.sin(f1 / 180.0F * (float) Math.PI) * MathHelper.cos(f2 / 180.0F * (float) Math.PI) * d3;
                double d5 = MathHelper.cos(f1 / 180.0F * (float) Math.PI) * MathHelper.cos(f2 / 180.0F * (float) Math.PI) * d3;
                double d6 = -MathHelper.sin(f2 / 180.0F * (float) Math.PI) * d3;

                for (int i = 0; i < 8; i++) {
                    float f3 = (i & 1) * 2 - 1;
                    float f4 = (i >> 1 & 1) * 2 - 1;
                    float f5 = (i >> 2 & 1) * 2 - 1;
                    f3 *= 0.1F;
                    f4 *= 0.1F;
                    f5 *= 0.1F;
                    HitResult hitresult = this.minecraft
                        .world
                        .rayTrace(new Vec3d(d0 + f3, d1 + f4, d2 + f5), new Vec3d(d0 - d4 + f3 + f5, d1 - d6 + f4, d2 - d5 + f5));
                    if (hitresult != null) {
                        double d7 = hitresult.facePos.distanceTo(new Vec3d(d0, d1, d2));
                        if (d7 < d3) {
                            d3 = d7;
                        }
                    }
                }

                if (this.minecraft.options.perspective == 2) {
                    GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
                }

                GlStateManager.rotatef(entity.pitch - f2, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(entity.yaw - f1, 0.0F, 1.0F, 0.0F);
                GlStateManager.translatef(0.0F, 0.0F, (float)(-d3));
                GlStateManager.rotatef(f1 - entity.yaw, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotatef(f2 - entity.pitch, 1.0F, 0.0F, 0.0F);
            }
        } else {
            GlStateManager.translatef(0.0F, 0.0F, -0.1F);
        }

        if (!this.minecraft.options.debugCamera) {
            GlStateManager.rotatef(entity.lastPitch + (entity.pitch - entity.lastPitch) * tickDelta, 1.0F, 0.0F, 0.0F);
            if (entity instanceof AnimalEntity) {
                AnimalEntity animalentity = (AnimalEntity)entity;
                GlStateManager.rotatef(animalentity.lastHeadYaw + (animalentity.headYaw - animalentity.lastHeadYaw) * tickDelta + 180.0F, 0.0F, 1.0F, 0.0F);
            } else {
                GlStateManager.rotatef(entity.lastYaw + (entity.yaw - entity.lastYaw) * tickDelta + 180.0F, 0.0F, 1.0F, 0.0F);
            }
        }

        GlStateManager.translatef(0.0F, -f, 0.0F);
        d0 = entity.lastX + (entity.x - entity.lastX) * tickDelta;
        d1 = entity.lastY + (entity.y - entity.lastY) * tickDelta + f;
        d2 = entity.lastZ + (entity.z - entity.lastZ) * tickDelta;
        this.thiccFog = this.minecraft.worldRenderer.hasThiccFog(d0, d1, d2, tickDelta);
    }

    private void setupCamera(float tickDelta, int anaglyphRenderPass) {
        this.renderDistance = this.minecraft.options.viewDistance * 16;
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        float f = 0.07F;
        if (this.minecraft.options.anaglyph) {
            GlStateManager.translatef(-(anaglyphRenderPass * 2 - 1) * f, 0.0F, 0.0F);
        }

        if (this.zoom != 1.0) {
            GlStateManager.translatef((float)this.zoomX, (float)(-this.zoomY), 0.0F);
            GlStateManager.scaled(this.zoom, this.zoom, 1.0);
        }

        Project.gluPerspective(
            this.getFov(tickDelta, true), (float)this.minecraft.width / this.minecraft.height, 0.05F, this.renderDistance * MathHelper.SQRT_TWO
        );
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        if (this.minecraft.options.anaglyph) {
            GlStateManager.translatef((anaglyphRenderPass * 2 - 1) * 0.1F, 0.0F, 0.0F);
        }

        this.applyHurtCam(tickDelta);
        if (this.minecraft.options.viewBobbing) {
            this.applyViewBobbing(tickDelta);
        }

        float f1 = this.minecraft.player.lastPortalTime + (this.minecraft.player.portalTime - this.minecraft.player.lastPortalTime) * tickDelta;
        if (f1 > 0.0F) {
            int i = 20;
            if (this.minecraft.player.hasStatusEffect(StatusEffect.NAUSEA)) {
                i = 7;
            }

            float f2 = 5.0F / (f1 * f1 + 5.0F) - f1 * 0.04F;
            f2 *= f2;
            GlStateManager.rotatef((this.ticks + tickDelta) * i, 0.0F, 1.0F, 1.0F);
            GlStateManager.scalef(1.0F / f2, 1.0F, 1.0F);
            GlStateManager.rotatef(-(this.ticks + tickDelta) * i, 0.0F, 1.0F, 1.0F);
        }

        this.transformCamera(tickDelta);
        if (this.debugCamera) {
            switch (this.hasNausea) {
                case 0:
                    GlStateManager.rotatef(90.0F, 0.0F, 1.0F, 0.0F);
                    break;
                case 1:
                    GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
                    break;
                case 2:
                    GlStateManager.rotatef(-90.0F, 0.0F, 1.0F, 0.0F);
                    break;
                case 3:
                    GlStateManager.rotatef(90.0F, 1.0F, 0.0F, 0.0F);
                    break;
                case 4:
                    GlStateManager.rotatef(-90.0F, 1.0F, 0.0F, 0.0F);
            }
        }
    }

    private void renderItemInHand(float tickDelta, int anaglyphRenderPass) {
        if (!this.debugCamera) {
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            float f = 0.07F;
            if (this.minecraft.options.anaglyph) {
                GlStateManager.translatef(-(anaglyphRenderPass * 2 - 1) * f, 0.0F, 0.0F);
            }

            Project.gluPerspective(this.getFov(tickDelta, false), (float)this.minecraft.width / this.minecraft.height, 0.05F, this.renderDistance * 2.0F);
            GlStateManager.matrixMode(5888);
            GlStateManager.loadIdentity();
            if (this.minecraft.options.anaglyph) {
                GlStateManager.translatef((anaglyphRenderPass * 2 - 1) * 0.1F, 0.0F, 0.0F);
            }

            GlStateManager.pushMatrix();
            this.applyHurtCam(tickDelta);
            if (this.minecraft.options.viewBobbing) {
                this.applyViewBobbing(tickDelta);
            }

            boolean flag = this.minecraft.getCamera() instanceof LivingEntity && ((LivingEntity)this.minecraft.getCamera()).isSleeping();
            if (this.minecraft.options.perspective == 0 && !flag && !this.minecraft.options.hideGui && !this.minecraft.interactionManager.hidesGui()) {
                this.enableLightMap();
                this.itemInHandRenderer.renderInFirstPerson(tickDelta);
                this.disableLightMap();
            }

            GlStateManager.popMatrix();
            if (this.minecraft.options.perspective == 0 && !flag) {
                this.itemInHandRenderer.renderScreenEffects(tickDelta);
                this.applyHurtCam(tickDelta);
            }

            if (this.minecraft.options.viewBobbing) {
                this.applyViewBobbing(tickDelta);
            }
        }
    }

    public void disableLightMap() {
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.disableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
    }

    public void enableLightMap() {
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.matrixMode(5890);
        GlStateManager.loadIdentity();
        float f = 0.00390625F;
        GlStateManager.scalef(f, f, f);
        GlStateManager.translatef(8.0F, 8.0F, 8.0F);
        GlStateManager.matrixMode(5888);
        this.minecraft.getTextureManager().bind(this.lightMapTextureLocation);
        GL11.glTexParameteri(3553, 10241, 9729);
        GL11.glTexParameteri(3553, 10240, 9729);
        GL11.glTexParameteri(3553, 10242, 10496);
        GL11.glTexParameteri(3553, 10243, 10496);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
    }

    private void tickLightMap() {
        this.lightMapFlickerTotal = (float)(this.lightMapFlickerTotal + (Math.random() - Math.random()) * Math.random() * Math.random());
        this.lightMapFlickerTotal = (float)(this.lightMapFlickerTotal * 0.9);
        this.lightMapFlicker = this.lightMapFlicker + (this.lightMapFlickerTotal - this.lightMapFlicker) * 1.0F;
        this.lightMapDirty = true;
    }

    private void updateLightMap(float tickDelta) {
        if (this.lightMapDirty) {
            this.minecraft.profiler.push("lightTex");
            World world = this.minecraft.world;
            if (world != null) {
                float f = world.calculateAmbientLight(1.0F);
                float f1 = f * 0.95F + 0.05F;

                for (int i = 0; i < 256; i++) {
                    float f2 = world.dimension.getBrightnessTable()[i / 16] * f1;
                    float f3 = world.dimension.getBrightnessTable()[i % 16] * (this.lightMapFlicker * 0.1F + 1.5F);
                    if (world.getLightningCooldown() > 0) {
                        f2 = world.dimension.getBrightnessTable()[i / 16];
                    }

                    float f4 = f2 * (f * 0.65F + 0.35F);
                    float f5 = f2 * (f * 0.65F + 0.35F);
                    float f6 = f2;
                    float f7 = f3;
                    float f8 = f3 * ((f3 * 0.6F + 0.4F) * 0.6F + 0.4F);
                    float f9 = f3 * (f3 * f3 * 0.6F + 0.4F);
                    float f10 = f4 + f7;
                    float f11 = f5 + f8;
                    float f12 = f6 + f9;
                    f10 = f10 * 0.96F + 0.03F;
                    f11 = f11 * 0.96F + 0.03F;
                    f12 = f12 * 0.96F + 0.03F;
                    if (this.skyDarkness > 0.0F) {
                        float f13 = this.lastSkyDarkness + (this.skyDarkness - this.lastSkyDarkness) * tickDelta;
                        f10 = f10 * (1.0F - f13) + f10 * 0.7F * f13;
                        f11 = f11 * (1.0F - f13) + f11 * 0.6F * f13;
                        f12 = f12 * (1.0F - f13) + f12 * 0.6F * f13;
                    }

                    if (world.dimension.getId() == 1) {
                        f10 = 0.22F + f7 * 0.75F;
                        f11 = 0.28F + f8 * 0.75F;
                        f12 = 0.25F + f9 * 0.75F;
                    }

                    if (this.minecraft.player.hasStatusEffect(StatusEffect.NIGHTVISION)) {
                        float f17 = this.getNightVisionScale(this.minecraft.player, tickDelta);
                        float f14 = 1.0F / f10;
                        if (f14 > 1.0F / f11) {
                            f14 = 1.0F / f11;
                        }

                        if (f14 > 1.0F / f12) {
                            f14 = 1.0F / f12;
                        }

                        f10 = f10 * (1.0F - f17) + f10 * f14 * f17;
                        f11 = f11 * (1.0F - f17) + f11 * f14 * f17;
                        f12 = f12 * (1.0F - f17) + f12 * f14 * f17;
                    }

                    if (f10 > 1.0F) {
                        f10 = 1.0F;
                    }

                    if (f11 > 1.0F) {
                        f11 = 1.0F;
                    }

                    if (f12 > 1.0F) {
                        f12 = 1.0F;
                    }

                    float f18 = this.minecraft.options.gamma;
                    float f19 = 1.0F - f10;
                    float f15 = 1.0F - f11;
                    float f16 = 1.0F - f12;
                    f19 = 1.0F - f19 * f19 * f19 * f19;
                    f15 = 1.0F - f15 * f15 * f15 * f15;
                    f16 = 1.0F - f16 * f16 * f16 * f16;
                    f10 = f10 * (1.0F - f18) + f19 * f18;
                    f11 = f11 * (1.0F - f18) + f15 * f18;
                    f12 = f12 * (1.0F - f18) + f16 * f18;
                    f10 = f10 * 0.96F + 0.03F;
                    f11 = f11 * 0.96F + 0.03F;
                    f12 = f12 * 0.96F + 0.03F;
                    if (f10 > 1.0F) {
                        f10 = 1.0F;
                    }

                    if (f11 > 1.0F) {
                        f11 = 1.0F;
                    }

                    if (f12 > 1.0F) {
                        f12 = 1.0F;
                    }

                    if (f10 < 0.0F) {
                        f10 = 0.0F;
                    }

                    if (f11 < 0.0F) {
                        f11 = 0.0F;
                    }

                    if (f12 < 0.0F) {
                        f12 = 0.0F;
                    }

                    int j = 255;
                    int k = (int)(f10 * 255.0F);
                    int l = (int)(f11 * 255.0F);
                    int i1 = (int)(f12 * 255.0F);
                    this.lightMapPixels[i] = j << 24 | k << 16 | l << 8 | i1;
                }

                this.lightmapTexture.upload();
                this.lightMapDirty = false;
                this.minecraft.profiler.pop();
            }
        }
    }

    private float getNightVisionScale(LivingEntity player, float tickDelta) {
        int i = player.getEffectInstance(StatusEffect.NIGHTVISION).getDuration();
        return i > 200 ? 1.0F : 0.7F + MathHelper.sin((i - tickDelta) * (float) Math.PI * 0.2F) * 0.3F;
    }

    public void render(float tickDelta, long startTime) {
        boolean flag = Display.isActive();
        if (!flag && this.minecraft.options.pauseOnUnfocus && (!this.minecraft.options.touchscreen || !Mouse.isButtonDown(1))) {
            if (Minecraft.getTime() - this.lastActiveTime > 500L) {
                this.minecraft.pauseGame();
            }
        } else {
            this.lastActiveTime = Minecraft.getTime();
        }

        this.minecraft.profiler.push("mouse");
        if (flag && Minecraft.IS_MAC && this.minecraft.focused && !Mouse.isInsideWindow()) {
            Mouse.setGrabbed(false);
            Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
            Mouse.setGrabbed(true);
        }

        if (this.minecraft.focused && flag) {
            this.minecraft.mouse.tick();
            float f = this.minecraft.options.mouseSensitivity * 0.6F + 0.2F;
            float f1 = f * f * f * 8.0F;
            float f2 = this.minecraft.mouse.x * f1;
            float f3 = this.minecraft.mouse.y * f1;
            int i = 1;
            if (this.minecraft.options.invertMouseY) {
                i = -1;
            }

            if (this.minecraft.options.smoothCamera) {
                this.totalMouseDX += f2;
                this.totalMouseDY += f3;
                float f4 = tickDelta - this.lastTickDelta;
                this.lastTickDelta = tickDelta;
                f2 = this.smoothMouseDX * f4;
                f3 = this.smoothMouseDY * f4;
                this.minecraft.player.updateLocalPlayerCamera(f2, f3 * i);
            } else {
                this.totalMouseDX = 0.0F;
                this.totalMouseDY = 0.0F;
                this.minecraft.player.updateLocalPlayerCamera(f2, f3 * i);
            }
        }

        this.minecraft.profiler.pop();
        if (!this.minecraft.skipGameRender) {
            anaglyphEnabled = this.minecraft.options.anaglyph;
            final Window window = new Window(this.minecraft);
            int i1 = window.getWidth();
            int j1 = window.getHeight();
            final int k1 = Mouse.getX() * i1 / this.minecraft.width;
            final int l1 = j1 - Mouse.getY() * j1 / this.minecraft.height - 1;
            int i2 = this.minecraft.options.fpsLimit;
            if (this.minecraft.world != null) {
                this.minecraft.profiler.push("level");
                int j = Math.min(Minecraft.getCurrentFps(), i2);
                j = Math.max(j, 60);
                long k = System.nanoTime() - startTime;
                long l = Math.max(1000000000 / j / 4 - k, 0L);
                this.renderWorld(tickDelta, System.nanoTime() + l);
                if (GLX.usePostProcess) {
                    this.minecraft.worldRenderer.renderEntityOutlines();
                    if (this.shaderEffect != null && this.shadersEnabled) {
                        GlStateManager.matrixMode(5890);
                        GlStateManager.pushMatrix();
                        GlStateManager.loadIdentity();
                        this.shaderEffect.process(tickDelta);
                        GlStateManager.popMatrix();
                    }

                    this.minecraft.getRenderTarget().bindWrite(true);
                }

                this.lastWorldRenderTime = System.nanoTime();
                this.minecraft.profiler.swap("gui");
                if (!this.minecraft.options.hideGui || this.minecraft.screen != null) {
                    GlStateManager.alphaFunc(516, 0.1F);
                    this.minecraft.gui.render(tickDelta);
                }

                this.minecraft.profiler.pop();
            } else {
                GlStateManager.viewport(0, 0, this.minecraft.width, this.minecraft.height);
                GlStateManager.matrixMode(5889);
                GlStateManager.loadIdentity();
                GlStateManager.matrixMode(5888);
                GlStateManager.loadIdentity();
                this.setupGuiState();
                this.lastWorldRenderTime = System.nanoTime();
            }

            if (this.minecraft.screen != null) {
                GlStateManager.clear(256);

                try {
                    this.minecraft.screen.render(k1, l1, tickDelta);
                } catch (Throwable throwable) {
                    CrashReport crashreport = CrashReport.of(throwable, "Rendering screen");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Screen render details");
                    crashreportcategory.add("Screen name", new Callable<String>() {
                        public String call() throws Exception {
                            return GameRenderer.this.minecraft.screen.getClass().getCanonicalName();
                        }
                    });
                    crashreportcategory.add("Mouse location", new Callable<String>() {
                        public String call() throws Exception {
                            return String.format("Scaled: (%d, %d). Absolute: (%d, %d)", k1, l1, Mouse.getX(), Mouse.getY());
                        }
                    });
                    crashreportcategory.add(
                        "Screen size",
                        new Callable<String>() {
                            public String call() throws Exception {
                                return String.format(
                                    "Scaled: (%d, %d). Absolute: (%d, %d). Scale factor of %d",
                                    window.getWidth(),
                                    window.getHeight(),
                                    GameRenderer.this.minecraft.width,
                                    GameRenderer.this.minecraft.height,
                                    window.getScale()
                                );
                            }
                        }
                    );
                    throw new CrashException(crashreport);
                }
            }
        }
    }

    public void renderStreamOverlay(float tickDelta) {
        this.setupGuiState();
        this.minecraft.gui.renderStreamOverlay(new Window(this.minecraft));
    }

    private boolean shouldRenderBlockOutline() {
        if (!this.renderBlockOutline) {
            return false;
        }

        Entity entity = this.minecraft.getCamera();
        boolean flag = entity instanceof PlayerEntity && !this.minecraft.options.hideGui;
        if (flag && !((PlayerEntity)entity).abilities.canModifyWorld) {
            ItemStack itemstack = ((PlayerEntity)entity).getItemInHand();
            if (this.minecraft.crosshairTarget != null && this.minecraft.crosshairTarget.type == HitResult.Type.BLOCK) {
                BlockPos blockpos = this.minecraft.crosshairTarget.getPos();
                Block block = this.minecraft.world.getBlockState(blockpos).getBlock();
                if (this.minecraft.interactionManager.getGameMode() == WorldSettings.GameMode.SPECTATOR) {
                    flag = block.hasBlockEntity() && this.minecraft.world.getBlockEntity(blockpos) instanceof Inventory;
                } else {
                    flag = itemstack != null && (itemstack.hasMineBlockOverride(block) || itemstack.hasPlaceOnBlockOverride(block));
                }
            }
        }

        return flag;
    }

    private void renderAxisIndicators(float tickDelta) {
        if (this.minecraft.options.debugEnabled
            && !this.minecraft.options.hideGui
            && !this.minecraft.player.hasReducedDebugInfo()
            && !this.minecraft.options.reducedDebugInfo) {
            Entity entity = this.minecraft.getCamera();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GL11.glLineWidth(1.0F);
            GlStateManager.disableTexture();
            GlStateManager.depthMask(false);
            GlStateManager.pushMatrix();
            GlStateManager.matrixMode(5888);
            GlStateManager.loadIdentity();
            this.transformCamera(tickDelta);
            GlStateManager.translatef(0.0F, entity.getEyeHeight(), 0.0F);
            WorldRenderer.renderOutlineShape(new Box(0.0, 0.0, 0.0, 0.005, 1.0E-4, 1.0E-4), 255, 0, 0, 255);
            WorldRenderer.renderOutlineShape(new Box(0.0, 0.0, 0.0, 1.0E-4, 1.0E-4, 0.005), 0, 0, 255, 255);
            WorldRenderer.renderOutlineShape(new Box(0.0, 0.0, 0.0, 1.0E-4, 0.0033, 1.0E-4), 0, 255, 0, 255);
            GlStateManager.popMatrix();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture();
            GlStateManager.disableBlend();
        }
    }

    public void renderWorld(float tickDelta, long renderTimeLimit) {
        this.updateLightMap(tickDelta);
        if (this.minecraft.getCamera() == null) {
            this.minecraft.setCamera(this.minecraft.player);
        }

        this.pick(tickDelta);
        GlStateManager.enableDepthTest();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.5F);
        this.minecraft.profiler.push("center");
        if (this.minecraft.options.anaglyph) {
            anaglyphFilter = 0;
            GlStateManager.colorMask(false, true, true, false);
            this.render(0, tickDelta, renderTimeLimit);
            anaglyphFilter = 1;
            GlStateManager.colorMask(true, false, false, false);
            this.render(1, tickDelta, renderTimeLimit);
            GlStateManager.colorMask(true, true, true, false);
        } else {
            this.render(2, tickDelta, renderTimeLimit);
        }

        this.minecraft.profiler.pop();
    }

    private void render(int anaglyphRenderPass, float tickDelta, long renderTimeLimit) {
        WorldRenderer worldrenderer = this.minecraft.worldRenderer;
        ParticleManager particlemanager = this.minecraft.particleManager;
        boolean flag = this.shouldRenderBlockOutline();
        GlStateManager.enableCull();
        this.minecraft.profiler.swap("clear");
        GlStateManager.viewport(0, 0, this.minecraft.width, this.minecraft.height);
        this.setupClearColor(tickDelta);
        GlStateManager.clear(16640);
        this.minecraft.profiler.swap("camera");
        this.setupCamera(tickDelta, anaglyphRenderPass);
        Camera.setup(this.minecraft.player, this.minecraft.options.perspective == 2);
        this.minecraft.profiler.swap("frustum");
        Frustum.getInstance();
        this.minecraft.profiler.swap("culling");
        Culler culler = new FrustumCuller();
        Entity entity = this.minecraft.getCamera();
        double d0 = entity.prevX + (entity.x - entity.prevX) * tickDelta;
        double d1 = entity.prevY + (entity.y - entity.prevY) * tickDelta;
        double d2 = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
        culler.prepare(d0, d1, d2);
        if (this.minecraft.options.viewDistance >= 4) {
            this.setupFog(-1, tickDelta);
            this.minecraft.profiler.swap("sky");
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            Project.gluPerspective(this.getFov(tickDelta, true), (float)this.minecraft.width / this.minecraft.height, 0.05F, this.renderDistance * 2.0F);
            GlStateManager.matrixMode(5888);
            worldrenderer.renderSky(tickDelta, anaglyphRenderPass);
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            Project.gluPerspective(
                this.getFov(tickDelta, true), (float)this.minecraft.width / this.minecraft.height, 0.05F, this.renderDistance * MathHelper.SQRT_TWO
            );
            GlStateManager.matrixMode(5888);
        }

        this.setupFog(0, tickDelta);
        GlStateManager.shadeModel(7425);
        if (entity.y + entity.getEyeHeight() < 128.0) {
            this.renderClouds(worldrenderer, tickDelta, anaglyphRenderPass);
        }

        this.minecraft.profiler.swap("prepareterrain");
        this.setupFog(0, tickDelta);
        this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
        Lighting.turnOff();
        this.minecraft.profiler.swap("terrain_setup");
        worldrenderer.setupRender(entity, tickDelta, culler, this.frameId++, this.minecraft.player.isSpectator());
        if (anaglyphRenderPass == 0 || anaglyphRenderPass == 2) {
            this.minecraft.profiler.swap("updatechunks");
            this.minecraft.worldRenderer.compileChunksUntil(renderTimeLimit);
        }

        this.minecraft.profiler.swap("terrain");
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.disableAlphaTest();
        worldrenderer.render(BlockLayer.SOLID, tickDelta, anaglyphRenderPass, entity);
        GlStateManager.enableAlphaTest();
        worldrenderer.render(BlockLayer.CUTOUT_MIPPED, tickDelta, anaglyphRenderPass, entity);
        this.minecraft.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).pushFilter(false, false);
        worldrenderer.render(BlockLayer.CUTOUT, tickDelta, anaglyphRenderPass, entity);
        this.minecraft.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).popFilter();
        GlStateManager.shadeModel(7424);
        GlStateManager.alphaFunc(516, 0.1F);
        if (!this.debugCamera) {
            GlStateManager.matrixMode(5888);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            Lighting.turnOn();
            this.minecraft.profiler.swap("entities");
            worldrenderer.renderEntities(entity, culler, tickDelta);
            Lighting.turnOff();
            this.disableLightMap();
            GlStateManager.matrixMode(5888);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            if (this.minecraft.crosshairTarget != null && entity.isSubmergedIn(Material.WATER) && flag) {
                PlayerEntity playerentity = (PlayerEntity)entity;
                GlStateManager.disableAlphaTest();
                this.minecraft.profiler.swap("outline");
                worldrenderer.renderBlockOutline(playerentity, this.minecraft.crosshairTarget, 0, tickDelta);
                GlStateManager.enableAlphaTest();
            }
        }

        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        if (flag && this.minecraft.crosshairTarget != null && !entity.isSubmergedIn(Material.WATER)) {
            PlayerEntity playerentity1 = (PlayerEntity)entity;
            GlStateManager.disableAlphaTest();
            this.minecraft.profiler.swap("outline");
            worldrenderer.renderBlockOutline(playerentity1, this.minecraft.crosshairTarget, 0, tickDelta);
            GlStateManager.enableAlphaTest();
        }

        this.minecraft.profiler.swap("destroyProgress");
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 1, 1, 0);
        this.minecraft.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).pushFilter(false, false);
        worldrenderer.renderMiningProgress(Tesselator.getInstance(), Tesselator.getInstance().getBuffer(), entity, tickDelta);
        this.minecraft.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).popFilter();
        GlStateManager.disableBlend();
        if (!this.debugCamera) {
            this.enableLightMap();
            this.minecraft.profiler.swap("litParticles");
            particlemanager.renderLit(entity, tickDelta);
            Lighting.turnOff();
            this.setupFog(0, tickDelta);
            this.minecraft.profiler.swap("particles");
            particlemanager.render(entity, tickDelta);
            this.disableLightMap();
        }

        GlStateManager.depthMask(false);
        GlStateManager.enableCull();
        this.minecraft.profiler.swap("weather");
        this.renderSnowAndRain(tickDelta);
        GlStateManager.depthMask(true);
        worldrenderer.renderWorldBorder(entity, tickDelta);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.alphaFunc(516, 0.1F);
        this.setupFog(0, tickDelta);
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
        GlStateManager.shadeModel(7425);
        this.minecraft.profiler.swap("translucent");
        worldrenderer.render(BlockLayer.TRANSLUCENT, tickDelta, anaglyphRenderPass, entity);
        GlStateManager.shadeModel(7424);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableFog();
        if (entity.y + entity.getEyeHeight() >= 128.0) {
            this.minecraft.profiler.swap("aboveClouds");
            this.renderClouds(worldrenderer, tickDelta, anaglyphRenderPass);
        }

        this.minecraft.profiler.swap("hand");
        if (this.newCamPitch) {
            GlStateManager.clear(256);
            this.renderItemInHand(tickDelta, anaglyphRenderPass);
            this.renderAxisIndicators(tickDelta);
        }
    }

    private void renderClouds(WorldRenderer worldRenderer, float tickDelta, int anaglyphRenderPass) {
        if (this.minecraft.options.getCloudRenderMode() != 0) {
            this.minecraft.profiler.swap("clouds");
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            Project.gluPerspective(this.getFov(tickDelta, true), (float)this.minecraft.width / this.minecraft.height, 0.05F, this.renderDistance * 4.0F);
            GlStateManager.matrixMode(5888);
            GlStateManager.pushMatrix();
            this.setupFog(0, tickDelta);
            worldRenderer.renderClouds(tickDelta, anaglyphRenderPass);
            GlStateManager.disableFog();
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            Project.gluPerspective(
                this.getFov(tickDelta, true), (float)this.minecraft.width / this.minecraft.height, 0.05F, this.renderDistance * MathHelper.SQRT_TWO
            );
            GlStateManager.matrixMode(5888);
        }
    }

    private void tickRain() {
        float f = this.minecraft.world.getRain(1.0F);
        if (!this.minecraft.options.fancyGraphics) {
            f /= 2.0F;
        }

        if (f != 0.0F) {
            this.random.setSeed(this.ticks * 312987231L);
            Entity entity = this.minecraft.getCamera();
            World world = this.minecraft.world;
            BlockPos blockpos = new BlockPos(entity);
            int i = 10;
            double d0 = 0.0;
            double d1 = 0.0;
            double d2 = 0.0;
            int j = 0;
            int k = (int)(100.0F * f * f);
            if (this.minecraft.options.particles == 1) {
                k >>= 1;
            } else if (this.minecraft.options.particles == 2) {
                k = 0;
            }

            for (int l = 0; l < k; l++) {
                BlockPos blockpos1 = world.getPrecipitationHeight(
                    blockpos.add(this.random.nextInt(i) - this.random.nextInt(i), 0, this.random.nextInt(i) - this.random.nextInt(i))
                );
                Biome biome = world.getBiome(blockpos1);
                BlockPos blockpos2 = blockpos1.down();
                Block block = world.getBlockState(blockpos2).getBlock();
                if (blockpos1.getY() <= blockpos.getY() + i
                    && blockpos1.getY() >= blockpos.getY() - i
                    && biome.isRainy()
                    && biome.getTemperature(blockpos1) >= 0.15F) {
                    double d3 = this.random.nextDouble();
                    double d4 = this.random.nextDouble();
                    if (block.getMaterial() == Material.LAVA) {
                        this.minecraft
                            .world
                            .addParticle(
                                ParticleType.SMOKE_NORMAL,
                                blockpos1.getX() + d3,
                                blockpos1.getY() + 0.1F - block.getMinY(),
                                blockpos1.getZ() + d4,
                                0.0,
                                0.0,
                                0.0
                            );
                    } else if (block.getMaterial() != Material.AIR) {
                        block.updateShape(world, blockpos2);
                        if (this.random.nextInt(++j) == 0) {
                            d0 = blockpos2.getX() + d3;
                            d1 = blockpos2.getY() + 0.1F + block.getMaxY() - 1.0;
                            d2 = blockpos2.getZ() + d4;
                        }

                        this.minecraft
                            .world
                            .addParticle(
                                ParticleType.WATER_DROP, blockpos2.getX() + d3, blockpos2.getY() + 0.1F + block.getMaxY(), blockpos2.getZ() + d4, 0.0, 0.0, 0.0
                            );
                    }
                }
            }

            if (j > 0 && this.random.nextInt(3) < this.weatherSoundAttempts++) {
                this.weatherSoundAttempts = 0;
                if (d1 > blockpos.getY() + 1 && world.getPrecipitationHeight(blockpos).getY() > MathHelper.floor(blockpos.getY())) {
                    this.minecraft.world.playSound(d0, d1, d2, "ambient.weather.rain", 0.1F, 0.5F, false);
                } else {
                    this.minecraft.world.playSound(d0, d1, d2, "ambient.weather.rain", 0.2F, 1.0F, false);
                }
            }
        }
    }

    protected void renderSnowAndRain(float tickDelta) {
        float f = this.minecraft.world.getRain(tickDelta);
        if (!(f <= 0.0F)) {
            this.enableLightMap();
            Entity entity = this.minecraft.getCamera();
            World world = this.minecraft.world;
            int i = MathHelper.floor(entity.x);
            int j = MathHelper.floor(entity.y);
            int k = MathHelper.floor(entity.z);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            GlStateManager.disableCull();
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.alphaFunc(516, 0.1F);
            double d0 = entity.prevX + (entity.x - entity.prevX) * tickDelta;
            double d1 = entity.prevY + (entity.y - entity.prevY) * tickDelta;
            double d2 = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
            int l = MathHelper.floor(d1);
            int i1 = 5;
            if (this.minecraft.options.fancyGraphics) {
                i1 = 10;
            }

            int j1 = -1;
            float f1 = this.ticks + tickDelta;
            bufferbuilder.offset(-d0, -d1, -d2);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

            for (int k1 = k - i1; k1 <= k + i1; k1++) {
                for (int l1 = i - i1; l1 <= i + i1; l1++) {
                    int i2 = (k1 - k + 16) * 32 + l1 - i + 16;
                    double d3 = this.rainSizeX[i2] * 0.5;
                    double d4 = this.rainSizeZ[i2] * 0.5;
                    blockpos$mutable.set(l1, 0, k1);
                    Biome biome = world.getBiome(blockpos$mutable);
                    if (biome.isRainy() || biome.isSnowy()) {
                        int j2 = world.getPrecipitationHeight(blockpos$mutable).getY();
                        int k2 = j - i1;
                        int l2 = j + i1;
                        if (k2 < j2) {
                            k2 = j2;
                        }

                        if (l2 < j2) {
                            l2 = j2;
                        }

                        int i3 = j2;
                        if (i3 < l) {
                            i3 = l;
                        }

                        if (k2 != l2) {
                            this.random.setSeed(l1 * l1 * 3121 + l1 * 45238971 ^ k1 * k1 * 418711 + k1 * 13761);
                            blockpos$mutable.set(l1, k2, k1);
                            float f2 = biome.getTemperature(blockpos$mutable);
                            if (world.getBiomeSource().adjustTemperatureForHeight(f2, j2) >= 0.15F) {
                                if (j1 != 0) {
                                    if (j1 >= 0) {
                                        tesselator.end();
                                    }

                                    j1 = 0;
                                    this.minecraft.getTextureManager().bind(RAIN_TEXTURE_LOCATION);
                                    bufferbuilder.begin(7, DefaultVertexFormat.PARTICLE);
                                }

                                double d5 = ((double)(this.ticks + l1 * l1 * 3121 + l1 * 45238971 + k1 * k1 * 418711 + k1 * 13761 & 31) + tickDelta)
                                    / 32.0
                                    * (3.0 + this.random.nextDouble());
                                double d6 = l1 + 0.5F - entity.x;
                                double d7 = k1 + 0.5F - entity.z;
                                float f3 = MathHelper.sqrt(d6 * d6 + d7 * d7) / i1;
                                float f4 = ((1.0F - f3 * f3) * 0.5F + 0.5F) * f;
                                blockpos$mutable.set(l1, i3, k1);
                                int j3 = world.getLightColor(blockpos$mutable, 0);
                                int k3 = j3 >> 16 & 65535;
                                int l3 = j3 & 65535;
                                bufferbuilder.vertex(l1 - d3 + 0.5, k2, k1 - d4 + 0.5)
                                    .texture(0.0, k2 * 0.25 + d5)
                                    .color(1.0F, 1.0F, 1.0F, f4)
                                    .texture(k3, l3)
                                    .nextVertex();
                                bufferbuilder.vertex(l1 + d3 + 0.5, k2, k1 + d4 + 0.5)
                                    .texture(1.0, k2 * 0.25 + d5)
                                    .color(1.0F, 1.0F, 1.0F, f4)
                                    .texture(k3, l3)
                                    .nextVertex();
                                bufferbuilder.vertex(l1 + d3 + 0.5, l2, k1 + d4 + 0.5)
                                    .texture(1.0, l2 * 0.25 + d5)
                                    .color(1.0F, 1.0F, 1.0F, f4)
                                    .texture(k3, l3)
                                    .nextVertex();
                                bufferbuilder.vertex(l1 - d3 + 0.5, l2, k1 - d4 + 0.5)
                                    .texture(0.0, l2 * 0.25 + d5)
                                    .color(1.0F, 1.0F, 1.0F, f4)
                                    .texture(k3, l3)
                                    .nextVertex();
                            } else {
                                if (j1 != 1) {
                                    if (j1 >= 0) {
                                        tesselator.end();
                                    }

                                    j1 = 1;
                                    this.minecraft.getTextureManager().bind(SNOW_TEXTURE_LOCATION);
                                    bufferbuilder.begin(7, DefaultVertexFormat.PARTICLE);
                                }

                                double d8 = ((this.ticks & 511) + tickDelta) / 512.0F;
                                double d9 = this.random.nextDouble() + f1 * 0.01 * (float)this.random.nextGaussian();
                                double d10 = this.random.nextDouble() + f1 * (float)this.random.nextGaussian() * 0.001;
                                double d11 = l1 + 0.5F - entity.x;
                                double d12 = k1 + 0.5F - entity.z;
                                float f6 = MathHelper.sqrt(d11 * d11 + d12 * d12) / i1;
                                float f5 = ((1.0F - f6 * f6) * 0.3F + 0.5F) * f;
                                blockpos$mutable.set(l1, i3, k1);
                                int i4 = (world.getLightColor(blockpos$mutable, 0) * 3 + 15728880) / 4;
                                int j4 = i4 >> 16 & 65535;
                                int k4 = i4 & 65535;
                                bufferbuilder.vertex(l1 - d3 + 0.5, k2, k1 - d4 + 0.5)
                                    .texture(0.0 + d9, k2 * 0.25 + d8 + d10)
                                    .color(1.0F, 1.0F, 1.0F, f5)
                                    .texture(j4, k4)
                                    .nextVertex();
                                bufferbuilder.vertex(l1 + d3 + 0.5, k2, k1 + d4 + 0.5)
                                    .texture(1.0 + d9, k2 * 0.25 + d8 + d10)
                                    .color(1.0F, 1.0F, 1.0F, f5)
                                    .texture(j4, k4)
                                    .nextVertex();
                                bufferbuilder.vertex(l1 + d3 + 0.5, l2, k1 + d4 + 0.5)
                                    .texture(1.0 + d9, l2 * 0.25 + d8 + d10)
                                    .color(1.0F, 1.0F, 1.0F, f5)
                                    .texture(j4, k4)
                                    .nextVertex();
                                bufferbuilder.vertex(l1 - d3 + 0.5, l2, k1 - d4 + 0.5)
                                    .texture(0.0 + d9, l2 * 0.25 + d8 + d10)
                                    .color(1.0F, 1.0F, 1.0F, f5)
                                    .texture(j4, k4)
                                    .nextVertex();
                            }
                        }
                    }
                }
            }

            if (j1 >= 0) {
                tesselator.end();
            }

            bufferbuilder.offset(0.0, 0.0, 0.0);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.alphaFunc(516, 0.1F);
            this.disableLightMap();
        }
    }

    public void setupGuiState() {
        Window window = new Window(this.minecraft);
        GlStateManager.clear(256);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0, window.getScaledWidth(), window.getScaledHeight(), 0.0, 1000.0, 3000.0);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translatef(0.0F, 0.0F, -2000.0F);
    }

    private void setupClearColor(float tickDelta) {
        World world = this.minecraft.world;
        Entity entity = this.minecraft.getCamera();
        float f = 0.25F + 0.75F * this.minecraft.options.viewDistance / 32.0F;
        f = 1.0F - (float)Math.pow(f, 0.25);
        Vec3d vec3d = world.getSkyColor(this.minecraft.getCamera(), tickDelta);
        float f1 = (float)vec3d.x;
        float f2 = (float)vec3d.y;
        float f3 = (float)vec3d.z;
        Vec3d vec3d1 = world.getFogColor(tickDelta);
        this.fogRed = (float)vec3d1.x;
        this.fogGreen = (float)vec3d1.y;
        this.fogBlue = (float)vec3d1.z;
        if (this.minecraft.options.viewDistance >= 4) {
            double d0 = -1.0;
            Vec3d vec3d2 = MathHelper.sin(world.getSunAngle(tickDelta)) > 0.0F ? new Vec3d(d0, 0.0, 0.0) : new Vec3d(1.0, 0.0, 0.0);
            float f5 = (float)entity.getRotationVec(tickDelta).dot(vec3d2);
            if (f5 < 0.0F) {
                f5 = 0.0F;
            }

            if (f5 > 0.0F) {
                float[] afloat = world.dimension.getSunriseColor(world.getTimeOfDay(tickDelta), tickDelta);
                if (afloat != null) {
                    f5 *= afloat[3];
                    this.fogRed = this.fogRed * (1.0F - f5) + afloat[0] * f5;
                    this.fogGreen = this.fogGreen * (1.0F - f5) + afloat[1] * f5;
                    this.fogBlue = this.fogBlue * (1.0F - f5) + afloat[2] * f5;
                }
            }
        }

        this.fogRed = this.fogRed + (f1 - this.fogRed) * f;
        this.fogGreen = this.fogGreen + (f2 - this.fogGreen) * f;
        this.fogBlue = this.fogBlue + (f3 - this.fogBlue) * f;
        float f8 = world.getRain(tickDelta);
        if (f8 > 0.0F) {
            float f4 = 1.0F - f8 * 0.5F;
            float f10 = 1.0F - f8 * 0.4F;
            this.fogRed *= f4;
            this.fogGreen *= f4;
            this.fogBlue *= f10;
        }

        float f9 = world.getThunder(tickDelta);
        if (f9 > 0.0F) {
            float f11 = 1.0F - f9 * 0.5F;
            this.fogRed *= f11;
            this.fogGreen *= f11;
            this.fogBlue *= f11;
        }

        Block block = Camera.getBlockInside(this.minecraft.world, entity, tickDelta);
        if (this.thiccFog) {
            Vec3d vec3d3 = world.getCloudColor(tickDelta);
            this.fogRed = (float)vec3d3.x;
            this.fogGreen = (float)vec3d3.y;
            this.fogBlue = (float)vec3d3.z;
        } else if (block.getMaterial() == Material.WATER) {
            float f12 = EnchantmentHelper.getRespirationLevel(entity) * 0.2F;
            if (entity instanceof LivingEntity && ((LivingEntity)entity).hasStatusEffect(StatusEffect.WATER_BREATHING)) {
                f12 = f12 * 0.3F + 0.6F;
            }

            this.fogRed = 0.02F + f12;
            this.fogGreen = 0.02F + f12;
            this.fogBlue = 0.2F + f12;
        } else if (block.getMaterial() == Material.LAVA) {
            this.fogRed = 0.6F;
            this.fogGreen = 0.1F;
            this.fogBlue = 0.0F;
        }

        float f13 = this.lastFogBrightness + (this.fogBrightness - this.lastFogBrightness) * tickDelta;
        this.fogRed *= f13;
        this.fogGreen *= f13;
        this.fogBlue *= f13;
        double d1 = (entity.prevY + (entity.y - entity.prevY) * tickDelta) * world.dimension.getFogSize();
        if (entity instanceof LivingEntity && ((LivingEntity)entity).hasStatusEffect(StatusEffect.BLINDNESS)) {
            int i = ((LivingEntity)entity).getEffectInstance(StatusEffect.BLINDNESS).getDuration();
            if (i < 20) {
                d1 *= 1.0F - i / 20.0F;
            } else {
                d1 = 0.0;
            }
        }

        if (d1 < 1.0) {
            if (d1 < 0.0) {
                d1 = 0.0;
            }

            d1 *= d1;
            this.fogRed = (float)(this.fogRed * d1);
            this.fogGreen = (float)(this.fogGreen * d1);
            this.fogBlue = (float)(this.fogBlue * d1);
        }

        if (this.skyDarkness > 0.0F) {
            float f14 = this.lastSkyDarkness + (this.skyDarkness - this.lastSkyDarkness) * tickDelta;
            this.fogRed = this.fogRed * (1.0F - f14) + this.fogRed * 0.7F * f14;
            this.fogGreen = this.fogGreen * (1.0F - f14) + this.fogGreen * 0.6F * f14;
            this.fogBlue = this.fogBlue * (1.0F - f14) + this.fogBlue * 0.6F * f14;
        }

        if (entity instanceof LivingEntity && ((LivingEntity)entity).hasStatusEffect(StatusEffect.NIGHTVISION)) {
            float f15 = this.getNightVisionScale((LivingEntity)entity, tickDelta);
            float f6 = 1.0F / this.fogRed;
            if (f6 > 1.0F / this.fogGreen) {
                f6 = 1.0F / this.fogGreen;
            }

            if (f6 > 1.0F / this.fogBlue) {
                f6 = 1.0F / this.fogBlue;
            }

            this.fogRed = this.fogRed * (1.0F - f15) + this.fogRed * f6 * f15;
            this.fogGreen = this.fogGreen * (1.0F - f15) + this.fogGreen * f6 * f15;
            this.fogBlue = this.fogBlue * (1.0F - f15) + this.fogBlue * f6 * f15;
        }

        if (this.minecraft.options.anaglyph) {
            float f16 = (this.fogRed * 30.0F + this.fogGreen * 59.0F + this.fogBlue * 11.0F) / 100.0F;
            float f17 = (this.fogRed * 30.0F + this.fogGreen * 70.0F) / 100.0F;
            float f7 = (this.fogRed * 30.0F + this.fogBlue * 70.0F) / 100.0F;
            this.fogRed = f16;
            this.fogGreen = f17;
            this.fogBlue = f7;
        }

        GlStateManager.clearColor(this.fogRed, this.fogGreen, this.fogBlue, 0.0F);
    }

    private void setupFog(int mode, float tickDelta) {
        Entity entity = this.minecraft.getCamera();
        boolean flag = false;
        if (entity instanceof PlayerEntity) {
            flag = ((PlayerEntity)entity).abilities.creativeMode;
        }

        GL11.glFog(2918, this.updateColorBuffer(this.fogRed, this.fogGreen, this.fogBlue, 1.0F));
        GL11.glNormal3f(0.0F, -1.0F, 0.0F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Block block = Camera.getBlockInside(this.minecraft.world, entity, tickDelta);
        if (entity instanceof LivingEntity && ((LivingEntity)entity).hasStatusEffect(StatusEffect.BLINDNESS)) {
            float f1 = 5.0F;
            int i = ((LivingEntity)entity).getEffectInstance(StatusEffect.BLINDNESS).getDuration();
            if (i < 20) {
                f1 = 5.0F + (this.renderDistance - 5.0F) * (1.0F - i / 20.0F);
            }

            GlStateManager.fogMode(9729);
            if (mode == -1) {
                GlStateManager.fogStart(0.0F);
                GlStateManager.fogEnd(f1 * 0.8F);
            } else {
                GlStateManager.fogStart(f1 * 0.25F);
                GlStateManager.fogEnd(f1);
            }

            if (GLContext.getCapabilities().GL_NV_fog_distance) {
                GL11.glFogi(34138, 34139);
            }
        } else if (this.thiccFog) {
            GlStateManager.fogMode(2048);
            GlStateManager.fogDensity(0.1F);
        } else if (block.getMaterial() == Material.WATER) {
            GlStateManager.fogMode(2048);
            if (entity instanceof LivingEntity && ((LivingEntity)entity).hasStatusEffect(StatusEffect.WATER_BREATHING)) {
                GlStateManager.fogDensity(0.01F);
            } else {
                GlStateManager.fogDensity(0.1F - EnchantmentHelper.getRespirationLevel(entity) * 0.03F);
            }
        } else if (block.getMaterial() == Material.LAVA) {
            GlStateManager.fogMode(2048);
            GlStateManager.fogDensity(2.0F);
        } else {
            float f = this.renderDistance;
            GlStateManager.fogMode(9729);
            if (mode == -1) {
                GlStateManager.fogStart(0.0F);
                GlStateManager.fogEnd(f);
            } else {
                GlStateManager.fogStart(f * 0.75F);
                GlStateManager.fogEnd(f);
            }

            if (GLContext.getCapabilities().GL_NV_fog_distance) {
                GL11.glFogi(34138, 34139);
            }

            if (this.minecraft.world.dimension.isFogThick((int)entity.x, (int)entity.z)) {
                GlStateManager.fogStart(f * 0.05F);
                GlStateManager.fogEnd(Math.min(f, 192.0F) * 0.5F);
            }
        }

        GlStateManager.enableColorMaterial();
        GlStateManager.enableFog();
        GlStateManager.colorMaterial(1028, 4608);
    }

    private FloatBuffer updateColorBuffer(float f1, float f2, float f3, float f4) {
        ((Buffer)this.colorBuffer).clear();
        this.colorBuffer.put(f1).put(f2).put(f3).put(f4);
        ((Buffer)this.colorBuffer).flip();
        return this.colorBuffer;
    }

    public MapRenderer getMapRenderer() {
        return this.mapRenderer;
    }
}
