package net.minecraft.client.world;

import com.google.common.collect.Sets;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.particle.FireworksParticles;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.sound.instance.EmptyMinecartSoundInstance;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.client.world.storage.ClientSavedDataStorage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.text.LiteralText;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.storage.EmptyWorldStorage;

public class ClientWorld extends World {
    private ClientPlayNetworkHandler networkHandler;
    private ClientChunkCache chunkCache;
    private final Set<Entity> forcedEntities = Sets.newHashSet();
    private final Set<Entity> pendingEntities = Sets.newHashSet();
    private final Minecraft minecraft = Minecraft.getInstance();
    /**
     * All chunks that were ticking the previous tick.
     */
    private final Set<ChunkPos> lastTickingChunks = Sets.newHashSet();

    public ClientWorld(ClientPlayNetworkHandler networkHandler, WorldSettings settings, int dimension, Difficulty difficulty, Profiler profiler) {
        super(new EmptyWorldStorage(), new WorldData(settings, "MpServer"), Dimension.fromId(dimension), profiler, true);
        this.networkHandler = networkHandler;
        this.getData().setDifficulty(difficulty);
        this.setSpawnPoint(new BlockPos(8, 64, 8));
        this.dimension.init(this);
        this.chunkSource = this.createChunkCache();
        this.savedDataStorage = new ClientSavedDataStorage();
        this.initAmbientDarkness();
        this.initWeather();
    }

    @Override
    public void tick() {
        super.tick();
        this.setTime(this.getTime() + 1L);
        if (this.getGameRules().getBoolean("doDaylightCycle")) {
            this.setTimeOfDay(this.getTimeOfDay() + 1L);
        }

        this.profiler.push("reEntryProcessing");

        for (int i = 0; i < 10 && !this.pendingEntities.isEmpty(); i++) {
            Entity entity = this.pendingEntities.iterator().next();
            this.pendingEntities.remove(entity);
            if (!this.entities.contains(entity)) {
                this.addEntity(entity);
            }
        }

        this.profiler.swap("chunkCache");
        this.chunkCache.tick();
        this.profiler.swap("blocks");
        this.tickChunks();
        this.profiler.pop();
    }

    public void clearBlockResets(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    @Override
    protected ChunkSource createChunkCache() {
        this.chunkCache = new ClientChunkCache(this);
        return this.chunkCache;
    }

    @Override
    protected void tickChunks() {
        super.tickChunks();
        this.lastTickingChunks.retainAll(this.tickingChunks);
        if (this.lastTickingChunks.size() == this.tickingChunks.size()) {
            this.lastTickingChunks.clear();
        }

        int i = 0;

        for (ChunkPos chunkpos : this.tickingChunks) {
            if (!this.lastTickingChunks.contains(chunkpos)) {
                int j = chunkpos.x * 16;
                int k = chunkpos.z * 16;
                this.profiler.push("getChunk");
                WorldChunk worldchunk = this.getChunkAt(chunkpos.x, chunkpos.z);
                this.tickAmbienceAndLight(j, k, worldchunk);
                this.profiler.pop();
                this.lastTickingChunks.add(chunkpos);
                if (++i >= 10) {
                    return;
                }
            }
        }
    }

    public void updateChunk(int chunkX, int chunkZ, boolean load) {
        if (load) {
            this.chunkCache.loadChunk(chunkX, chunkZ);
        } else {
            this.chunkCache.unloadChunk(chunkX, chunkZ);
        }

        if (!load) {
            this.notifyRegionChanged(chunkX * 16, 0, chunkZ * 16, chunkX * 16 + 15, 256, chunkZ * 16 + 15);
        }
    }

    @Override
    public boolean addEntity(Entity entity) {
        boolean flag = super.addEntity(entity);
        this.forcedEntities.add(entity);
        if (!flag) {
            this.pendingEntities.add(entity);
        } else if (entity instanceof MinecartEntity) {
            this.minecraft.getSoundManager().play(new EmptyMinecartSoundInstance((MinecartEntity)entity));
        }

        return flag;
    }

    @Override
    public void removeEntity(Entity entity) {
        super.removeEntity(entity);
        this.forcedEntities.remove(entity);
    }

    @Override
    protected void notifyEntityAdded(Entity entity) {
        super.notifyEntityAdded(entity);
        if (this.pendingEntities.contains(entity)) {
            this.pendingEntities.remove(entity);
        }
    }

    @Override
    protected void notifyEntityRemoved(Entity entity) {
        super.notifyEntityRemoved(entity);
        boolean flag = false;
        if (this.forcedEntities.contains(entity)) {
            if (entity.isAlive()) {
                this.pendingEntities.add(entity);
                flag = true;
            } else {
                this.forcedEntities.remove(entity);
            }
        }
    }

    public void forceEntity(int networkId, Entity entity) {
        Entity entityx = this.getEntity(networkId);
        if (entityx != null) {
            this.removeEntity(entityx);
        }

        this.forcedEntities.add(entity);
        entity.setNetworkId(networkId);
        if (!this.addEntity(entity)) {
            this.pendingEntities.add(entity);
        }

        this.entitiesById.put(networkId, entity);
    }

    @Override
    public Entity getEntity(int networkId) {
        return networkId == this.minecraft.player.getNetworkId() ? this.minecraft.player : super.getEntity(networkId);
    }

    public Entity removeEntity(int networkId) {
        Entity entity = this.entitiesById.remove(networkId);
        if (entity != null) {
            this.forcedEntities.remove(entity);
            this.removeEntity(entity);
        }

        return entity;
    }

    public boolean setBlockStateFromPacket(BlockPos pos, BlockState state) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        this.clearBlockResets(i, j, k, i, j, k);
        return super.setBlockState(pos, state, 3);
    }

    @Override
    public void disconnect() {
        this.networkHandler.getConnection().disconnect(new LiteralText("Quitting"));
    }

    @Override
    protected void tickWeather() {
    }

    @Override
    protected int getChunkViewDistance() {
        return this.minecraft.options.viewDistance;
    }

    public void doRandomDisplayTicks(int x, int y, int z) {
        int i = 16;
        Random random = new Random();
        ItemStack itemstack = this.minecraft.player.getDisplayItemInHand();
        boolean flag = this.minecraft.interactionManager.getGameMode() == WorldSettings.GameMode.CREATIVE
            && itemstack != null
            && Block.byItem(itemstack.getItem()) == Blocks.BARRIER;
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int j = 0; j < 1000; j++) {
            int k = x + this.random.nextInt(i) - this.random.nextInt(i);
            int l = y + this.random.nextInt(i) - this.random.nextInt(i);
            int i1 = z + this.random.nextInt(i) - this.random.nextInt(i);
            blockpos$mutable.set(k, l, i1);
            BlockState blockstate = this.getBlockState(blockpos$mutable);
            blockstate.getBlock().randomDisplayTick(this, blockpos$mutable, blockstate, random);
            if (flag && blockstate.getBlock() == Blocks.BARRIER) {
                this.addParticle(ParticleType.BARRIER, k + 0.5F, l + 0.5F, i1 + 0.5F, 0.0, 0.0, 0.0);
            }
        }
    }

    public void unloadEntities() {
        this.entities.removeAll(this.entitiesToRemove);

        for (int i = 0; i < this.entitiesToRemove.size(); i++) {
            Entity entity = this.entitiesToRemove.get(i);
            int j = entity.chunkX;
            int k = entity.chunkZ;
            if (entity.inChunk && this.isChunkLoadedAt(j, k, true)) {
                this.getChunkAt(j, k).removeEntity(entity);
            }
        }

        for (int l = 0; l < this.entitiesToRemove.size(); l++) {
            this.notifyEntityRemoved(this.entitiesToRemove.get(l));
        }

        this.entitiesToRemove.clear();

        for (int i1 = 0; i1 < this.entities.size(); i1++) {
            Entity entity1 = this.entities.get(i1);
            if (entity1.vehicle != null) {
                if (!entity1.vehicle.removed && entity1.vehicle.rider == entity1) {
                    continue;
                }

                entity1.vehicle.rider = null;
                entity1.vehicle = null;
            }

            if (entity1.removed) {
                int j1 = entity1.chunkX;
                int k1 = entity1.chunkZ;
                if (entity1.inChunk && this.isChunkLoadedAt(j1, k1, true)) {
                    this.getChunkAt(j1, k1).removeEntity(entity1);
                }

                this.entities.remove(i1--);
                this.notifyEntityRemoved(entity1);
            }
        }
    }

    @Override
    public CrashReportCategory populateCrashReport(CrashReport report) {
        CrashReportCategory crashreportcategory = super.populateCrashReport(report);
        crashreportcategory.add("Forced entities", new Callable<String>() {
            public String call() {
                return ClientWorld.this.forcedEntities.size() + " total; " + ClientWorld.this.forcedEntities.toString();
            }
        });
        crashreportcategory.add("Retry entities", new Callable<String>() {
            public String call() {
                return ClientWorld.this.pendingEntities.size() + " total; " + ClientWorld.this.pendingEntities.toString();
            }
        });
        crashreportcategory.add("Server brand", new Callable<String>() {
            public String call() throws Exception {
                return ClientWorld.this.minecraft.player.getServerBrand();
            }
        });
        crashreportcategory.add("Server type", new Callable<String>() {
            public String call() throws Exception {
                return ClientWorld.this.minecraft.getServer() == null ? "Non-integrated multiplayer server" : "Integrated singleplayer server";
            }
        });
        return crashreportcategory;
    }

    public void playSound(BlockPos pos, String sound, float volume, float pitch, boolean ignoreDistance) {
        this.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, volume, pitch, ignoreDistance);
    }

    @Override
    public void playSound(double x, double y, double z, String sound, float volume, float pitch, boolean ignoreDistance) {
        double d0 = this.minecraft.getCamera().squaredDistanceTo(x, y, z);
        SimpleSoundInstance simplesoundinstance = new SimpleSoundInstance(new Identifier(sound), volume, pitch, (float)x, (float)y, (float)z);
        if (ignoreDistance && d0 > 100.0) {
            double d1 = Math.sqrt(d0) / 40.0;
            this.minecraft.getSoundManager().play(simplesoundinstance, (int)(d1 * 20.0));
        } else {
            this.minecraft.getSoundManager().play(simplesoundinstance);
        }
    }

    @Override
    public void addFireworksParticle(double x, double y, double z, double velocityX, double velocityY, double velocityZ, NbtCompound nbt) {
        this.minecraft.particleManager.add(new FireworksParticles.Starter(this, x, y, z, velocityX, velocityY, velocityZ, this.minecraft.particleManager, nbt));
    }

    public void setScoreboard(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    @Override
    public void setTimeOfDay(long time) {
        if (time < 0L) {
            time = -time;
            this.getGameRules().set("doDaylightCycle", "false");
        } else {
            this.getGameRules().set("doDaylightCycle", "true");
        }

        super.setTimeOfDay(time);
    }
}
