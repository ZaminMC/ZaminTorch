package net.minecraft.client;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.particle.BarrierParticle;
import net.minecraft.client.entity.particle.BlockDustParticle;
import net.minecraft.client.entity.particle.BlockParticle;
import net.minecraft.client.entity.particle.CloudParticle;
import net.minecraft.client.entity.particle.CriticalHitParticle;
import net.minecraft.client.entity.particle.DepthSuspendParticle;
import net.minecraft.client.entity.particle.EmitterParticle;
import net.minecraft.client.entity.particle.EmotionParticle;
import net.minecraft.client.entity.particle.EnchantingParticle;
import net.minecraft.client.entity.particle.ExplosionParticle;
import net.minecraft.client.entity.particle.FireworksParticles;
import net.minecraft.client.entity.particle.FlameParticle;
import net.minecraft.client.entity.particle.FootstepParticle;
import net.minecraft.client.entity.particle.HugeExplosionParticle;
import net.minecraft.client.entity.particle.ItemParticle;
import net.minecraft.client.entity.particle.LargeExplosionParticle;
import net.minecraft.client.entity.particle.LargeSmokeParticle;
import net.minecraft.client.entity.particle.LavaParticle;
import net.minecraft.client.entity.particle.LiquidDripParticle;
import net.minecraft.client.entity.particle.MobAppearanceParticle;
import net.minecraft.client.entity.particle.NoteParticle;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.entity.particle.ParticleFactory;
import net.minecraft.client.entity.particle.PortalParticle;
import net.minecraft.client.entity.particle.RainSplashParticle;
import net.minecraft.client.entity.particle.RedstoneParticle;
import net.minecraft.client.entity.particle.SmokeParticle;
import net.minecraft.client.entity.particle.SnowShovelParticle;
import net.minecraft.client.entity.particle.SpellParticle;
import net.minecraft.client.entity.particle.SuspendedParticle;
import net.minecraft.client.entity.particle.WakeParticle;
import net.minecraft.client.entity.particle.WaterBubbleParticle;
import net.minecraft.client.entity.particle.WaterSplashParticle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.resource.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class ParticleManager {
    private static final Identifier PARTICLES_LOCATION = new Identifier("textures/particle/particles.png");
    protected World world;
    private List<Particle>[][] particles = new List[4][];
    private List<EmitterParticle> emitters = Lists.newArrayList();
    private TextureManager textureManager;
    private Random random = new Random();
    private Map<Integer, ParticleFactory> factories = Maps.newHashMap();

    public ParticleManager(World world, TextureManager textureManager) {
        this.world = world;
        this.textureManager = textureManager;

        for (int i = 0; i < 4; i++) {
            this.particles[i] = new List[2];

            for (int j = 0; j < 2; j++) {
                this.particles[i][j] = Lists.newArrayList();
            }
        }

        this.registerFactories();
    }

    private void registerFactories() {
        this.register(ParticleType.EXPLOSION_NORMAL.getId(), new ExplosionParticle.Factory());
        this.register(ParticleType.WATER_BUBBLE.getId(), new WaterBubbleParticle.Factory());
        this.register(ParticleType.WATER_SPLASH.getId(), new WaterSplashParticle.Factory());
        this.register(ParticleType.WATER_WAKE.getId(), new WakeParticle.Factory());
        this.register(ParticleType.WATER_DROP.getId(), new RainSplashParticle.Factory());
        this.register(ParticleType.SUSPENDED.getId(), new SuspendedParticle.Factory());
        this.register(ParticleType.SUSPENDED_DEPTH.getId(), new DepthSuspendParticle.Factory());
        this.register(ParticleType.CRIT.getId(), new CriticalHitParticle.Factory());
        this.register(ParticleType.CRIT_MAGIC.getId(), new CriticalHitParticle.MagicFactory());
        this.register(ParticleType.SMOKE_NORMAL.getId(), new SmokeParticle.Factory());
        this.register(ParticleType.SMOKE_LARGE.getId(), new LargeSmokeParticle.Factory());
        this.register(ParticleType.SPELL.getId(), new SpellParticle.Factory());
        this.register(ParticleType.SPELL_INSTANT.getId(), new SpellParticle.InstantFactory());
        this.register(ParticleType.SPELL_MOB.getId(), new SpellParticle.MobFactory());
        this.register(ParticleType.SPELL_MOB_AMBIENT.getId(), new SpellParticle.MobAmbientFactory());
        this.register(ParticleType.SPELL_WITCH.getId(), new SpellParticle.WitchFactory());
        this.register(ParticleType.DRIP_WATER.getId(), new LiquidDripParticle.WaterFactory());
        this.register(ParticleType.DRIP_LAVA.getId(), new LiquidDripParticle.LavaFactory());
        this.register(ParticleType.VILLAGER_ANGRY.getId(), new EmotionParticle.AngryFactory());
        this.register(ParticleType.VILLAGER_HAPPY.getId(), new DepthSuspendParticle.HappyFactory());
        this.register(ParticleType.TOWN_AURA.getId(), new DepthSuspendParticle.Factory());
        this.register(ParticleType.NOTE.getId(), new NoteParticle.Factory());
        this.register(ParticleType.PORTAL.getId(), new PortalParticle.Factory());
        this.register(ParticleType.ENCHANTMENT_TABLE.getId(), new EnchantingParticle.Factory());
        this.register(ParticleType.FLAME.getId(), new FlameParticle.Factory());
        this.register(ParticleType.LAVA.getId(), new LavaParticle.Factory());
        this.register(ParticleType.FOOTSTEP.getId(), new FootstepParticle.Factory());
        this.register(ParticleType.CLOUD.getId(), new CloudParticle.Factory());
        this.register(ParticleType.REDSTONE.getId(), new RedstoneParticle.Factory());
        this.register(ParticleType.SNOWBALL.getId(), new ItemParticle.SnowballFactory());
        this.register(ParticleType.SNOW_SHOVEL.getId(), new SnowShovelParticle.Factory());
        this.register(ParticleType.SLIME.getId(), new ItemParticle.SlimeBallFactory());
        this.register(ParticleType.HEART.getId(), new EmotionParticle.Factory());
        this.register(ParticleType.BARRIER.getId(), new BarrierParticle.Factory());
        this.register(ParticleType.ITEM_CRACK.getId(), new ItemParticle.Factory());
        this.register(ParticleType.BLOCK_CRACK.getId(), new BlockParticle.Factory());
        this.register(ParticleType.BLOCK_DUST.getId(), new BlockDustParticle.Factory());
        this.register(ParticleType.EXPLOSION_HUGE.getId(), new HugeExplosionParticle.Factory());
        this.register(ParticleType.EXPLOSION_LARGE.getId(), new LargeExplosionParticle.Factory());
        this.register(ParticleType.FIREWORKS_SPARK.getId(), new FireworksParticles.Factory());
        this.register(ParticleType.MOB_APPEARANCE.getId(), new MobAppearanceParticle.Factory());
    }

    public void register(int type, ParticleFactory factory) {
        this.factories.put(type, factory);
    }

    public void addEmitter(Entity entity, ParticleType type) {
        this.emitters.add(new EmitterParticle(this.world, entity, type));
    }

    public Particle addParticle(int type, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
        ParticleFactory particlefactory = this.factories.get(type);
        if (particlefactory != null) {
            Particle particle = particlefactory.create(type, this.world, x, y, z, velocityX, velocityY, velocityZ, parameters);
            if (particle != null) {
                this.add(particle);
                return particle;
            }
        }

        return null;
    }

    public void add(Particle particle) {
        int i = particle.getAtlasType();
        int j = particle.getAlpha() != 1.0F ? 0 : 1;
        if (this.particles[i][j].size() >= 4000) {
            this.particles[i][j].remove(0);
        }

        this.particles[i][j].add(particle);
    }

    public void tick() {
        for (int i = 0; i < 4; i++) {
            this.tickParticles(i);
        }

        List<EmitterParticle> list = Lists.newArrayList();

        for (EmitterParticle emitterparticle : this.emitters) {
            emitterparticle.tick();
            if (emitterparticle.removed) {
                list.add(emitterparticle);
            }
        }

        this.emitters.removeAll(list);
    }

    private void tickParticles(int renderType) {
        for (int i = 0; i < 2; i++) {
            this.tickParticles(this.particles[renderType][i]);
        }
    }

    private void tickParticles(List<Particle> particles) {
        List<Particle> list = Lists.newArrayList();

        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            this.tickParticle(particle);
            if (particle.removed) {
                list.add(particle);
            }
        }

        particles.removeAll(list);
    }

    private void tickParticle(Particle particle) {
        try {
            particle.tick();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Ticking Particle");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being ticked");
            final int i = particle.getAtlasType();
            crashreportcategory.add("Particle", new Callable<String>() {
                public String call() throws Exception {
                    return particle.toString();
                }
            });
            crashreportcategory.add("Particle Type", new Callable<String>() {
                public String call() throws Exception {
                    if (i == 0) {
                        return "MISC_TEXTURE";
                    } else if (i == 1) {
                        return "TERRAIN_TEXTURE";
                    } else {
                        return i == 3 ? "ENTITY_PARTICLE_TEXTURE" : "Unknown - " + i;
                    }
                }
            });
            throw new CrashException(crashreport);
        }
    }

    public void render(Entity camera, float tickDelta) {
        float f = Camera.dx();
        float f1 = Camera.dz();
        float f2 = Camera.forwards();
        float f3 = Camera.sideways();
        float f4 = Camera.dy();
        Particle.lerpCameraX = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        Particle.lerpCameraY = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        Particle.lerpCameraZ = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.alphaFunc(516, 0.003921569F);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                if (!this.particles[i][j].isEmpty()) {
                    switch (j) {
                        case 0:
                            GlStateManager.depthMask(false);
                            break;
                        case 1:
                            GlStateManager.depthMask(true);
                    }

                    switch (i) {
                        case 0:
                        default:
                            this.textureManager.bind(PARTICLES_LOCATION);
                            break;
                        case 1:
                            this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
                    }

                    GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                    Tesselator tesselator = Tesselator.getInstance();
                    BufferBuilder bufferbuilder = tesselator.getBuffer();
                    bufferbuilder.begin(7, DefaultVertexFormat.PARTICLE);

                    for (int k = 0; k < this.particles[i][j].size(); k++) {
                        final Particle particle = this.particles[i][j].get(k);

                        try {
                            particle.render(bufferbuilder, camera, tickDelta, f, f4, f1, f2, f3);
                        } catch (Throwable throwable) {
                            CrashReport crashreport = CrashReport.of(throwable, "Rendering Particle");
                            CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being rendered");
                            final int l = i;
                            crashreportcategory.add("Particle", new Callable<String>() {
                                public String call() throws Exception {
                                    return particle.toString();
                                }
                            });
                            crashreportcategory.add("Particle Type", new Callable<String>() {
                                public String call() throws Exception {
                                    if (l == 0) {
                                        return "MISC_TEXTURE";
                                    } else if (l == 1) {
                                        return "TERRAIN_TEXTURE";
                                    } else {
                                        return l == 3 ? "ENTITY_PARTICLE_TEXTURE" : "Unknown - " + l;
                                    }
                                }
                            });
                            throw new CrashException(crashreport);
                        }
                    }

                    tesselator.end();
                }
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.alphaFunc(516, 0.1F);
    }

    public void renderLit(Entity camera, float tickDelta) {
        float f = (float) (Math.PI / 180.0);
        float f1 = MathHelper.cos(camera.yaw * (float) (Math.PI / 180.0));
        float f2 = MathHelper.sin(camera.yaw * (float) (Math.PI / 180.0));
        float f3 = -f2 * MathHelper.sin(camera.pitch * (float) (Math.PI / 180.0));
        float f4 = f1 * MathHelper.sin(camera.pitch * (float) (Math.PI / 180.0));
        float f5 = MathHelper.cos(camera.pitch * (float) (Math.PI / 180.0));

        for (int i = 0; i < 2; i++) {
            List<Particle> list = this.particles[3][i];
            if (!list.isEmpty()) {
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferbuilder = tesselator.getBuffer();

                for (int j = 0; j < list.size(); j++) {
                    Particle particle = list.get(j);
                    particle.render(bufferbuilder, camera, tickDelta, f1, f5, f2, f3, f4);
                }
            }
        }
    }

    public void setWorld(World world) {
        this.world = world;

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                this.particles[i][j].clear();
            }
        }

        this.emitters.clear();
    }

    public void addBlockMiningParticles(BlockPos pos, BlockState state) {
        if (state.getBlock().getMaterial() != Material.AIR) {
            state = state.getBlock().resolveVirtualProperties(state, this.world, pos);
            int i = 4;

            for (int j = 0; j < i; j++) {
                for (int k = 0; k < i; k++) {
                    for (int l = 0; l < i; l++) {
                        double d0 = pos.getX() + (j + 0.5) / i;
                        double d1 = pos.getY() + (k + 0.5) / i;
                        double d2 = pos.getZ() + (l + 0.5) / i;
                        this.add(
                            new BlockParticle(this.world, d0, d1, d2, d0 - pos.getX() - 0.5, d1 - pos.getY() - 0.5, d2 - pos.getZ() - 0.5, state).init(pos)
                        );
                    }
                }
            }
        }
    }

    public void addBlockMiningParticles(BlockPos pos, Direction face) {
        BlockState blockstate = this.world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (block.getRenderType() != -1) {
            int i = pos.getX();
            int j = pos.getY();
            int k = pos.getZ();
            float f = 0.1F;
            double d0 = i + this.random.nextDouble() * (block.getMaxX() - block.getMinX() - f * 2.0F) + f + block.getMinX();
            double d1 = j + this.random.nextDouble() * (block.getMaxY() - block.getMinY() - f * 2.0F) + f + block.getMinY();
            double d2 = k + this.random.nextDouble() * (block.getMaxZ() - block.getMinZ() - f * 2.0F) + f + block.getMinZ();
            if (face == Direction.DOWN) {
                d1 = j + block.getMinY() - f;
            }

            if (face == Direction.UP) {
                d1 = j + block.getMaxY() + f;
            }

            if (face == Direction.NORTH) {
                d2 = k + block.getMinZ() - f;
            }

            if (face == Direction.SOUTH) {
                d2 = k + block.getMaxZ() + f;
            }

            if (face == Direction.WEST) {
                d0 = i + block.getMinX() - f;
            }

            if (face == Direction.EAST) {
                d0 = i + block.getMaxX() + f;
            }

            this.add(new BlockParticle(this.world, d0, d1, d2, 0.0, 0.0, 0.0, blockstate).init(pos).multiplyVelocity(0.2F).multiplySize(0.6F));
        }
    }

    public void setTranslucent(Particle particle) {
        this.changeLayer(particle, 1, 0);
    }

    public void setOpaque(Particle particle) {
        this.changeLayer(particle, 0, 1);
    }

    private void changeLayer(Particle particle, int fromLayer, int toLayer) {
        for (int i = 0; i < 4; i++) {
            if (this.particles[i][fromLayer].contains(particle)) {
                this.particles[i][fromLayer].remove(particle);
                this.particles[i][toLayer].add(particle);
            }
        }
    }

    public String getDebugInfo() {
        int i = 0;

        for (int j = 0; j < 4; j++) {
            for (int k = 0; k < 2; k++) {
                i += this.particles[j][k].size();
            }
        }

        return "" + i;
    }
}
