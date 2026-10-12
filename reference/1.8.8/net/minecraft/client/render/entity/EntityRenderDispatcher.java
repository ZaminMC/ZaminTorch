package net.minecraft.client.render.entity;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.model.entity.ChickenModel;
import net.minecraft.client.render.model.entity.CowModel;
import net.minecraft.client.render.model.entity.HorseModel;
import net.minecraft.client.render.model.entity.OcelotModel;
import net.minecraft.client.render.model.entity.PigModel;
import net.minecraft.client.render.model.entity.RabbitModel;
import net.minecraft.client.render.model.entity.SheepModel;
import net.minecraft.client.render.model.entity.SlimeModel;
import net.minecraft.client.render.model.entity.SquidModel;
import net.minecraft.client.render.model.entity.WolfModel;
import net.minecraft.client.render.model.entity.ZombieModel;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.EnderCrystalEntity;
import net.minecraft.entity.EnderEyeEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.FireworksEntity;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.PrimedTntEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.SnowGolemEntity;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.living.mob.monster.CaveSpiderEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.entity.living.mob.monster.EndermiteEntity;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.GiantEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.entity.living.mob.monster.MagmaCubeEntity;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.monster.SpiderEntity;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.monster.ZombiePigmanEntity;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.mob.passive.animal.CowEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.MooshroomEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.living.mob.passive.animal.RabbitEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.mob.water.SquidEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.ExperienceBottleEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.vehicle.SpawnerMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EntityRenderDispatcher {
    private Map<Class<? extends Entity>, EntityRenderer<? extends Entity>> renderers = Maps.newHashMap();
    private Map<String, PlayerRenderer> playerRenderers = Maps.newHashMap();
    private PlayerRenderer defaultPlayerRenderer;
    private TextRenderer textRenderer;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    public TextureManager textureManager;
    public World world;
    public Entity camera;
    public Entity targetEntity;
    public float cameraYaw;
    public float cameraPitch;
    public GameOptions options;
    public double cameraX;
    public double cameraY;
    public double cameraZ;
    private boolean solidRender = false;
    private boolean renderShadow = true;
    private boolean renderHitboxes = false;

    public EntityRenderDispatcher(TextureManager textureManager, ItemRenderer itemRenderer) {
        this.textureManager = textureManager;
        this.renderers.put(CaveSpiderEntity.class, new CaveSpiderRenderer(this));
        this.renderers.put(SpiderEntity.class, new SpiderRenderer(this));
        this.renderers.put(PigEntity.class, new PigRenderer(this, new PigModel(), 0.7F));
        this.renderers.put(SheepEntity.class, new SheepRenderer(this, new SheepModel(), 0.7F));
        this.renderers.put(CowEntity.class, new CowRenderer(this, new CowModel(), 0.7F));
        this.renderers.put(MooshroomEntity.class, new MooshroomRenderer(this, new CowModel(), 0.7F));
        this.renderers.put(WolfEntity.class, new WolfRenderer(this, new WolfModel(), 0.5F));
        this.renderers.put(ChickenEntity.class, new ChickenRenderer(this, new ChickenModel(), 0.3F));
        this.renderers.put(OcelotEntity.class, new OcelotRenderer(this, new OcelotModel(), 0.4F));
        this.renderers.put(RabbitEntity.class, new RabbitRenderer(this, new RabbitModel(), 0.3F));
        this.renderers.put(SilverfishEntity.class, new SilverfishRenderer(this));
        this.renderers.put(EndermiteEntity.class, new EndermiteRenderer(this));
        this.renderers.put(CreeperEntity.class, new CreeperRenderer(this));
        this.renderers.put(EndermanEntity.class, new EndermanRenderer(this));
        this.renderers.put(SnowGolemEntity.class, new SnowGolemRenderer(this));
        this.renderers.put(SkeletonEntity.class, new SkeletonRenderer(this));
        this.renderers.put(WitchEntity.class, new WitchRenderer(this));
        this.renderers.put(BlazeEntity.class, new BlazeRenderer(this));
        this.renderers.put(ZombiePigmanEntity.class, new ZombiePigmanRenderer(this));
        this.renderers.put(ZombieEntity.class, new ZombieRenderer(this));
        this.renderers.put(SlimeEntity.class, new SlimeRenderer(this, new SlimeModel(16), 0.25F));
        this.renderers.put(MagmaCubeEntity.class, new MagmaCubeRenderer(this));
        this.renderers.put(GiantEntity.class, new GiantRenderer(this, new ZombieModel(), 0.5F, 6.0F));
        this.renderers.put(GhastEntity.class, new GhastRenderer(this));
        this.renderers.put(SquidEntity.class, new SquidRenderer(this, new SquidModel(), 0.7F));
        this.renderers.put(VillagerEntity.class, new VillagerRenderer(this));
        this.renderers.put(IronGolemEntity.class, new IronGolemRenderer(this));
        this.renderers.put(BatEntity.class, new BatRenderer(this));
        this.renderers.put(GuardianEntity.class, new GuardianRenderer(this));
        this.renderers.put(EnderDragonEntity.class, new EnderDragonRenderer(this));
        this.renderers.put(EnderCrystalEntity.class, new EnderCrystalRenderer(this));
        this.renderers.put(WitherEntity.class, new WitherRenderer(this));
        this.renderers.put(Entity.class, new DefaultRenderer(this));
        this.renderers.put(PaintingEntity.class, new PaintingRenderer(this));
        this.renderers.put(ItemFrameEntity.class, new ItemFrameRenderer(this, itemRenderer));
        this.renderers.put(LeadKnotEntity.class, new LeadKnotRenderer(this));
        this.renderers.put(ArrowEntity.class, new ArrowRenderer(this));
        this.renderers.put(SnowballEntity.class, new ItemSpriteRenderer<>(this, Items.SNOWBALL, itemRenderer));
        this.renderers.put(EnderPearlEntity.class, new ItemSpriteRenderer<>(this, Items.ENDER_PEARL, itemRenderer));
        this.renderers.put(EnderEyeEntity.class, new ItemSpriteRenderer<>(this, Items.ENDER_EYE, itemRenderer));
        this.renderers.put(EggEntity.class, new ItemSpriteRenderer<>(this, Items.EGG, itemRenderer));
        this.renderers.put(PotionEntity.class, new PotionRenderer(this, itemRenderer));
        this.renderers.put(ExperienceBottleEntity.class, new ItemSpriteRenderer<>(this, Items.EXPERIENCE_BOTTLE, itemRenderer));
        this.renderers.put(FireworksEntity.class, new ItemSpriteRenderer<>(this, Items.FIREWORKS, itemRenderer));
        this.renderers.put(FireballEntity.class, new ProjectileRenderer(this, 2.0F));
        this.renderers.put(SmallFireballEntity.class, new ProjectileRenderer(this, 0.5F));
        this.renderers.put(WitherSkullEntity.class, new WitherSkullRenderer(this));
        this.renderers.put(ItemEntity.class, new ItemEntityRenderer(this, itemRenderer));
        this.renderers.put(ExperienceOrbEntity.class, new ExperienceOrbRenderer(this));
        this.renderers.put(PrimedTntEntity.class, new PrimedTntRenderer(this));
        this.renderers.put(FallingBlockEntity.class, new FallingBlockRenderer(this));
        this.renderers.put(ArmorStandEntity.class, new ArmorStandRenderer(this));
        this.renderers.put(TntMinecartEntity.class, new TntMinecartRenderer(this));
        this.renderers.put(SpawnerMinecartEntity.class, new SpawnerMinecartRenderer(this));
        this.renderers.put(MinecartEntity.class, new MinecartRenderer(this));
        this.renderers.put(BoatEntity.class, new BoatRenderer(this));
        this.renderers.put(FishingBobberEntity.class, new FishingBobberRenderer(this));
        this.renderers.put(HorseBaseEntity.class, new HorseRenderer(this, new HorseModel(), 0.75F));
        this.renderers.put(LightningBoltEntity.class, new LightningBoltRenderer(this));
        this.defaultPlayerRenderer = new PlayerRenderer(this);
        this.playerRenderers.put("default", this.defaultPlayerRenderer);
        this.playerRenderers.put("slim", new PlayerRenderer(this, true));
    }

    public void setCameraPos(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    public <T extends Entity> EntityRenderer<T> getRenderer(Class<? extends Entity> type) {
        EntityRenderer<? extends Entity> entityrenderer = this.renderers.get(type);
        if (entityrenderer == null && type != Entity.class) {
            entityrenderer = this.getRenderer((Class<? extends Entity>)type.getSuperclass());
            this.renderers.put(type, entityrenderer);
        }

        return (EntityRenderer<T>)entityrenderer;
    }

    public <T extends Entity> EntityRenderer<T> getRenderer(Entity entity) {
        if (entity instanceof ClientPlayerEntity) {
            String s = ((ClientPlayerEntity)entity).getModelType();
            PlayerRenderer playerrenderer = this.playerRenderers.get(s);
            return playerrenderer != null ? playerrenderer : this.defaultPlayerRenderer;
        } else {
            return this.getRenderer((Class<? extends Entity>)entity.getClass());
        }
    }

    public void prepare(World world, TextRenderer textRenderer, Entity camera, Entity targetEntity, GameOptions options, float tickDelta) {
        this.world = world;
        this.options = options;
        this.camera = camera;
        this.targetEntity = targetEntity;
        this.textRenderer = textRenderer;
        if (camera instanceof LivingEntity && ((LivingEntity)camera).isSleeping()) {
            BlockState blockstate = world.getBlockState(new BlockPos(camera));
            Block block = blockstate.getBlock();
            if (block == Blocks.BED) {
                int i = blockstate.get(BedBlock.FACING).getIdHorizontal();
                this.cameraYaw = i * 90 + 180;
                this.cameraPitch = 0.0F;
            }
        } else {
            this.cameraYaw = camera.lastYaw + (camera.yaw - camera.lastYaw) * tickDelta;
            this.cameraPitch = camera.lastPitch + (camera.pitch - camera.lastPitch) * tickDelta;
        }

        if (options.perspective == 2) {
            this.cameraYaw += 180.0F;
        }

        this.cameraX = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        this.cameraY = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        this.cameraZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
    }

    public void setCameraYaw(float yaw) {
        this.cameraYaw = yaw;
    }

    public boolean shouldRenderShadow() {
        return this.renderShadow;
    }

    public void setRenderShadow(boolean renderShadow) {
        this.renderShadow = renderShadow;
    }

    public void setRenderHitboxes(boolean renderHitboxes) {
        this.renderHitboxes = renderHitboxes;
    }

    public boolean shouldRenderHitboxes() {
        return this.renderHitboxes;
    }

    public boolean render(Entity entity, float tickDelta) {
        return this.render(entity, tickDelta, false);
    }

    public boolean shouldRender(Entity entity, Culler view, double cameraX, double cameraY, double cameraZ) {
        EntityRenderer<Entity> entityrenderer = this.getRenderer(entity);
        return entityrenderer != null && entityrenderer.shouldRender(entity, view, cameraX, cameraY, cameraZ);
    }

    public boolean render(Entity entity, float tickDelta, boolean skipHitbox) {
        if (entity.ticks == 0) {
            entity.prevX = entity.x;
            entity.prevY = entity.y;
            entity.prevZ = entity.z;
        }

        double d0 = entity.prevX + (entity.x - entity.prevX) * tickDelta;
        double d1 = entity.prevY + (entity.y - entity.prevY) * tickDelta;
        double d2 = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
        float f = entity.lastYaw + (entity.yaw - entity.lastYaw) * tickDelta;
        int i = entity.getLightLevel(tickDelta);
        if (entity.isOnFire()) {
            i = 15728880;
        }

        int j = i % 65536;
        int k = i / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, j / 1.0F, k / 1.0F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        return this.render(entity, d0 - this.offsetX, d1 - this.offsetY, d2 - this.offsetZ, f, tickDelta, skipHitbox);
    }

    public void renderNameTag(Entity entity, float tickDelta) {
        double d0 = entity.prevX + (entity.x - entity.prevX) * tickDelta;
        double d1 = entity.prevY + (entity.y - entity.prevY) * tickDelta;
        double d2 = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
        EntityRenderer<Entity> entityrenderer = this.getRenderer(entity);
        if (entityrenderer != null && this.textureManager != null) {
            int i = entity.getLightLevel(tickDelta);
            int j = i % 65536;
            int k = i / 65536;
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, j / 1.0F, k / 1.0F);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            entityrenderer.renderNameTag(entity, d0 - this.offsetX, d1 - this.offsetY, d2 - this.offsetZ);
        }
    }

    public boolean render(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        return this.render(entity, dx, dy, dz, yaw, tickDelta, false);
    }

    public boolean render(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta, boolean skipHitbox) {
        EntityRenderer<Entity> entityrenderer = null;

        try {
            entityrenderer = this.getRenderer(entity);
            if (entityrenderer != null && this.textureManager != null) {
                try {
                    if (entityrenderer instanceof LivingEntityRenderer) {
                        ((LivingEntityRenderer)entityrenderer).setSolidRender(this.solidRender);
                    }

                    entityrenderer.render(entity, dx, dy, dz, yaw, tickDelta);
                } catch (Throwable throwable2) {
                    throw new CrashException(CrashReport.of(throwable2, "Rendering entity in world"));
                }

                try {
                    if (!this.solidRender) {
                        entityrenderer.postRender(entity, dx, dy, dz, yaw, tickDelta);
                    }
                } catch (Throwable throwable1) {
                    throw new CrashException(CrashReport.of(throwable1, "Post-rendering entity in world"));
                }

                if (this.renderHitboxes && !entity.isInvisible() && !skipHitbox) {
                    try {
                        this.renderHitbox(entity, dx, dy, dz, yaw, tickDelta);
                    } catch (Throwable throwable) {
                        throw new CrashException(CrashReport.of(throwable, "Rendering entity hitbox in world"));
                    }
                }
            } else if (this.textureManager != null) {
                return false;
            }

            return true;
        } catch (Throwable throwable3) {
            CrashReport crashreport = CrashReport.of(throwable3, "Rendering entity in world");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being rendered");
            entity.populateCrashReport(crashreportcategory);
            CrashReportCategory crashreportcategory1 = crashreport.addCategory("Renderer details");
            crashreportcategory1.add("Assigned renderer", entityrenderer);
            crashreportcategory1.add("Location", CrashReportCategory.formatPosition(dx, dy, dz));
            crashreportcategory1.add("Rotation", yaw);
            crashreportcategory1.add("Delta", tickDelta);
            throw new CrashException(crashreport);
        }
    }

    private void renderHitbox(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        GlStateManager.depthMask(false);
        GlStateManager.disableTexture();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        float f = entity.width / 2.0F;
        Box box = entity.getShape();
        Box box1 = new Box(
            box.minX - entity.x + dx,
            box.minY - entity.y + dy,
            box.minZ - entity.z + dz,
            box.maxX - entity.x + dx,
            box.maxY - entity.y + dy,
            box.maxZ - entity.z + dz
        );
        WorldRenderer.renderOutlineShape(box1, 255, 255, 255, 255);
        if (entity instanceof LivingEntity) {
            float f1 = 0.01F;
            WorldRenderer.renderOutlineShape(
                new Box(dx - f, dy + entity.getEyeHeight() - 0.01F, dz - f, dx + f, dy + entity.getEyeHeight() + 0.01F, dz + f), 255, 0, 0, 255
            );
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        Vec3d vec3d = entity.getRotationVec(tickDelta);
        bufferbuilder.begin(3, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(dx, dy + entity.getEyeHeight(), dz).color(0, 0, 255, 255).nextVertex();
        bufferbuilder.vertex(dx + vec3d.x * 2.0, dy + entity.getEyeHeight() + vec3d.y * 2.0, dz + vec3d.z * 2.0).color(0, 0, 255, 255).nextVertex();
        tesselator.end();
        GlStateManager.enableTexture();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public double squaredDistanceToCamera(double x, double y, double z) {
        double d0 = x - this.cameraX;
        double d1 = y - this.cameraY;
        double d2 = z - this.cameraZ;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public TextRenderer getTextRenderer() {
        return this.textRenderer;
    }

    public void setSolidRender(boolean solidRender) {
        this.solidRender = solidRender;
    }
}
