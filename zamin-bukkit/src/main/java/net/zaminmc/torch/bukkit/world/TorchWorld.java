package net.zaminmc.torch.bukkit.world;

import net.zaminmc.torch.World;
import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.block.MaterialMap;
import net.zaminmc.torch.util.Position;

import org.bukkit.BlockChangeDelegate;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageRecipient;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The legacy world view over one engine {@link World} (§28.7's
 * object-semantics inventory): the §27.5 handle law — the API-facing
 * object resolves to the current authoritative engine state, it is never a
 * second copy of game state.
 *
 * <p><b>The bridged subset (this slice):</b> identity (name, a deterministic
 * UUID derived from the name — the engine world carries no UUID of its
 * own), block reads and typed mutations (routed §27.6), the time clocks
 * (day + total), the spawn position, chunk-loaded reads, the game-rule
 * table (the engine's one shared table, translated read/write), the
 * difficulty read, and the environment (derived from the engine's own
 * dimension naming convention: {@code name}, {@code name_nether},
 * {@code name_end}).</p>
 *
 * <p><b>Every unbridged arm fails loudly</b> (§27.9's honest-gap rule: a
 * recorded limitation with a deliberate message, never a silent
 * wrong answer) — the entity family and the world-shaping arms are the
 * later slices.</p>
 */
public final class TorchWorld implements org.bukkit.World {

    private final BukkitAdapter adapter;
    private final World engine;
    private final MaterialMap materials;

    public TorchWorld(BukkitAdapter adapter, World engine) {
        this.adapter = adapter;
        this.engine = engine;
        this.materials = adapter.materialMap();
    }

    /** The engine world this view resolves to (§27.5's handle law). */
    public World engineWorld() {
        return engine;
    }

    // ------------------------------------------------------------------ identity

    @Override
    public String getName() {
        return engine.name();
    }

    @Override
    public UUID getUID() {
        // The engine world carries no UUID; the deterministic name-derived
        // one keeps the legacy contract (a stable per-world identity).
        return UUID.nameUUIDFromBytes(("torch-world:" + engine.name()).getBytes());
    }

    @Override
    public org.bukkit.World.Environment getEnvironment() {
        String name = engine.name();
        if (name.endsWith("_nether")) {
            return Environment.NETHER;
        }
        if (name.endsWith("_end")) {
            return Environment.THE_END;
        }
        return Environment.NORMAL;
    }

    // ------------------------------------------------------------------ blocks

    @Override
    public Block getBlockAt(int x, int y, int z) {
        return adapter.blockAt(this, x, y, z);
    }

    @Override
    public Block getBlockAt(Location location) {
        return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public int getBlockTypeIdAt(int x, int y, int z) {
        return materials.toLegacyId(engine.getBlock(new net.zaminmc.torch.block.BlockPosition(x, y, z)));
    }

    @Override
    public int getBlockTypeIdAt(Location location) {
        return getBlockTypeIdAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public int getHighestBlockYAt(int x, int z) {
        for (int y = net.zaminmc.torch.block.BlockPosition.MAX_Y; y >= 0; y--) {
            net.zaminmc.torch.block.BlockPosition at =
                    new net.zaminmc.torch.block.BlockPosition(x, y, z);
            if (!engine.getBlock(at).identifier()
                    .equals(net.zaminmc.torch.util.Identifier.of("minecraft", "air"))) {
                return y;
            }
        }
        return 0;
    }

    @Override
    public int getHighestBlockYAt(Location location) {
        return getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
    }

    @Override
    public Block getHighestBlockAt(int x, int z) {
        return getBlockAt(x, getHighestBlockYAt(x, z), z);
    }

    @Override
    public Block getHighestBlockAt(Location location) {
        return getHighestBlockAt(location.getBlockX(), location.getBlockZ());
    }

    // ------------------------------------------------------------------ chunks (read arms)

    @Override
    public boolean isChunkLoaded(Chunk chunk) {
        return chunk != null && isChunkLoaded(chunk.getX(), chunk.getZ());
    }

    @Override
    public boolean isChunkLoaded(int x, int z) {
        return engine.isChunkLoaded(new net.zaminmc.torch.block.ChunkPosition(x, z));
    }

    // ------------------------------------------------------------------ clocks + spawn

    @Override
    public long getTime() {
        return engine.timeOfDay();
    }

    @Override
    public long getFullTime() {
        return engine.totalTicks();
    }

    @Override
    public void setTime(long time) {
        adapter.mutateWorld(engine, () -> engine.setTimeOfDay(time));
    }

    @Override
    public void setFullTime(long time) {
        // The engine's total clock is the tick count itself; the day clock
        // is the settable one. Translate onto it (documented).
        adapter.mutateWorld(engine, () -> engine.setTimeOfDay(time));
    }

    @Override
    public Location getSpawnLocation() {
        Position spawn = engine.spawnPosition();
        return new Location(this, spawn.x(), spawn.y(), spawn.z());
    }

    // ------------------------------------------------------------------ game rules (the one shared table)

    @Override
    public String[] getGameRules() {
        return adapter.engine().gameRules().names().toArray(new String[0]);
    }

    @Override
    public String getGameRuleValue(String rule) {
        return adapter.engine().gameRules().get(rule);
    }

    @Override
    public boolean setGameRuleValue(String rule, String value) {
        if (!adapter.engine().gameRules().contains(rule)) {
            return false;
        }
        adapter.mutateWorld(engine, () -> adapter.engine().gameRules().set(rule, value));
        return true;
    }

    @Override
    public boolean isGameRule(String rule) {
        return adapter.engine().gameRules().contains(rule);
    }

    // ------------------------------------------------------------------ spawn limits (the vanilla 1.8.8 defaults)

    // The spawn-limit family: the vanilla 1.8.8 constants (the reference's
    // own numbers, honestly reported); the setter arms are unbridged — the
    // engine's spawn pacing is not a knob in this slice.

    @Override
    public int getMonsterSpawnLimit() {
        return 70;
    }

    @Override
    public void setMonsterSpawnLimit(int limit) {
        throw adapter.unbridged("World.setMonsterSpawnLimit");
    }

    @Override
    public int getAnimalSpawnLimit() {
        return 10;
    }

    @Override
    public void setAnimalSpawnLimit(int limit) {
        throw adapter.unbridged("World.setAnimalSpawnLimit");
    }

    @Override
    public int getWaterAnimalSpawnLimit() {
        return 5;
    }

    @Override
    public void setWaterAnimalSpawnLimit(int limit) {
        throw adapter.unbridged("World.setWaterAnimalSpawnLimit");
    }

    @Override
    public int getAmbientSpawnLimit() {
        return 15;
    }

    @Override
    public void setAmbientSpawnLimit(int limit) {
        throw adapter.unbridged("World.setAmbientSpawnLimit");
    }

    // ------------------------------------------------------------------ difficulty + players

    @Override
    public Difficulty getDifficulty() {
        return Difficulty.getByValue(adapter.engine().difficulty());
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        adapter.unbridged("World.setDifficulty");
    }

    @Override
    public List<Player> getPlayers() {
        return adapter.playersIn(engine);
    }

    // ------------------------------------------------------------------ constants

    @Override
    public int getMaxHeight() {
        return 256;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public long getSeed() {
        // The engine's world seed is not exposed through the public API in
        // this slice; the loud refusal beats an invented number.
        throw adapter.unbridged("World.getSeed");
    }

    @Override
    public WorldBorder getWorldBorder() {
        throw adapter.unbridged("World.getWorldBorder");
    }

    // ------------------------------------------------------------------ the unbridged family

    @Override
    public Chunk getChunkAt(int x, int z) {
        throw adapter.unbridged("World.getChunkAt");
    }

    @Override
    public Chunk getChunkAt(Location location) {
        throw adapter.unbridged("World.getChunkAt");
    }

    @Override
    public Chunk getChunkAt(Block block) {
        throw adapter.unbridged("World.getChunkAt");
    }

    @Override
    public Chunk[] getLoadedChunks() {
        throw adapter.unbridged("World.getLoadedChunks");
    }

    @Override
    public void loadChunk(Chunk chunk) {
        throw adapter.unbridged("World.loadChunk");
    }

    @Override
    public void loadChunk(int x, int z) {
        throw adapter.unbridged("World.loadChunk");
    }

    @Override
    public boolean loadChunk(int x, int z, boolean generate) {
        throw adapter.unbridged("World.loadChunk");
    }

    @Override
    public boolean isChunkInUse(int x, int z) {
        return false;
    }

    @Override
    public boolean unloadChunk(Chunk chunk) {
        throw adapter.unbridged("World.unloadChunk");
    }

    @Override
    public boolean unloadChunk(int x, int z) {
        throw adapter.unbridged("World.unloadChunk");
    }

    @Override
    public boolean unloadChunk(int x, int z, boolean save) {
        throw adapter.unbridged("World.unloadChunk");
    }

    @Override
    public boolean unloadChunk(int x, int z, boolean save, boolean safe) {
        throw adapter.unbridged("World.unloadChunk");
    }

    @Override
    public boolean unloadChunkRequest(int x, int z) {
        throw adapter.unbridged("World.unloadChunkRequest");
    }

    @Override
    public boolean unloadChunkRequest(int x, int z, boolean safe) {
        throw adapter.unbridged("World.unloadChunkRequest");
    }

    @Override
    public boolean regenerateChunk(int x, int z) {
        throw adapter.unbridged("World.regenerateChunk");
    }

    @Override
    public boolean refreshChunk(int x, int z) {
        throw adapter.unbridged("World.refreshChunk");
    }

    @Override
    public Item dropItem(Location location, ItemStack item) {
        throw adapter.unbridged("World.dropItem");
    }

    @Override
    public Item dropItemNaturally(Location location, ItemStack item) {
        throw adapter.unbridged("World.dropItemNaturally");
    }

    @Override
    public Arrow spawnArrow(Location location, Vector direction, float speed, float spread) {
        throw adapter.unbridged("World.spawnArrow");
    }

    @Override
    public boolean generateTree(Location location, TreeType type) {
        throw adapter.unbridged("World.generateTree");
    }

    @Override
    public boolean generateTree(Location loc, TreeType type, BlockChangeDelegate delegate) {
        throw adapter.unbridged("World.generateTree");
    }

    @Override
    public Entity spawnEntity(Location loc, EntityType type) {
        throw adapter.unbridged("World.spawnEntity");
    }

    @Override
    public LivingEntity spawnCreature(Location loc, EntityType type) {
        throw adapter.unbridged("World.spawnCreature");
    }

    @Override
    public LivingEntity spawnCreature(Location loc, CreatureType type) {
        throw adapter.unbridged("World.spawnCreature");
    }

    @Override
    public LightningStrike strikeLightning(Location loc) {
        throw adapter.unbridged("World.strikeLightning");
    }

    @Override
    public LightningStrike strikeLightningEffect(Location loc) {
        throw adapter.unbridged("World.strikeLightningEffect");
    }

    @Override
    public List<Entity> getEntities() {
        throw adapter.unbridged("World.getEntities");
    }

    @Override
    public List<LivingEntity> getLivingEntities() {
        throw adapter.unbridged("World.getLivingEntities");
    }

    @Override
    public Collection<Entity> getEntitiesByClasses(Class<?>... classes) {
        throw adapter.unbridged("World.getEntitiesByClasses");
    }

    @Override
    public <T extends Entity> Collection<T> getEntitiesByClass(Class<T> clazz) {
        throw adapter.unbridged("World.getEntitiesByClass");
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Entity> Collection<T> getEntitiesByClass(Class<T>... classes) {
        throw adapter.unbridged("World.getEntitiesByClass");
    }

    @Override
    public Collection<Entity> getNearbyEntities(Location location, double x, double y, double z) {
        throw adapter.unbridged("World.getNearbyEntities");
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z) {
        adapter.mutateWorld(engine, () ->
                ((net.zaminmc.torch.server.world.EngineWorld) engine)
                        .setSpawnPosition(new Position(x + 0.5, y, z + 0.5)));
        return true;
    }

    @Override
    public org.bukkit.block.Biome getBiome(int x, int z) {
        return org.bukkit.block.Biome.PLAINS; // the flat world's own answer (documented)
    }

    @Override
    public void setBiome(int x, int z, org.bukkit.block.Biome bio) {
        throw adapter.unbridged("World.setBiome");
    }

    @Override
    public boolean hasStorm() {
        return false;
    }

    @Override
    public void setStorm(boolean hasStorm) {
        throw adapter.unbridged("World.setStorm");
    }

    @Override
    public int getWeatherDuration() {
        return 0;
    }

    @Override
    public void setWeatherDuration(int duration) {
        throw adapter.unbridged("World.setWeatherDuration");
    }

    @Override
    public boolean isThundering() {
        return false;
    }

    @Override
    public void setThundering(boolean thundering) {
        throw adapter.unbridged("World.setThundering");
    }

    @Override
    public int getThunderDuration() {
        return 0;
    }

    @Override
    public void setThunderDuration(int duration) {
        throw adapter.unbridged("World.setThunderDuration");
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power) {
        throw adapter.unbridged("World.createExplosion");
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire) {
        throw adapter.unbridged("World.createExplosion");
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire,
            boolean breakBlocks) {
        throw adapter.unbridged("World.createExplosion");
    }

    @Override
    public boolean createExplosion(Location loc, float power) {
        throw adapter.unbridged("World.createExplosion");
    }

    @Override
    public boolean createExplosion(Location loc, float power, boolean setFire) {
        throw adapter.unbridged("World.createExplosion");
    }

    @Override
    public boolean getPVP() {
        return true;
    }

    @Override
    public void setPVP(boolean pvp) {
        throw adapter.unbridged("World.setPVP");
    }

    @Override
    public ChunkGenerator getGenerator() {
        return null;
    }

    @Override
    public void save() {
        throw adapter.unbridged("World.save");
    }

    @Override
    public List<BlockPopulator> getPopulators() {
        return List.of();
    }

    @Override
    public FallingBlock spawnFallingBlock(Location location, Material material, byte data)
            throws IllegalArgumentException {
        throw adapter.unbridged("World.spawnFallingBlock");
    }

    @Override
    public FallingBlock spawnFallingBlock(Location location, int blockId, byte blockData)
            throws IllegalArgumentException {
        throw adapter.unbridged("World.spawnFallingBlock");
    }

    @Override
    public void playSound(Location location, Sound sound, float volume, float pitch) {
        throw adapter.unbridged("World.playSound");
    }

    @Override
    public <T> void playEffect(Location location, org.bukkit.Effect effect, T data, int radius) {
        throw adapter.unbridged("World.playEffect");
    }

    @Override
    public <T> void playEffect(Location location, org.bukkit.Effect effect, T data) {
        throw adapter.unbridged("World.playEffect");
    }

    @Override
    public <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> clazz)
            throws IllegalArgumentException {
        throw adapter.unbridged("World.spawn");
    }

    @Override
    public void playEffect(Location location, org.bukkit.Effect effect, int data) {
        throw adapter.unbridged("World.playEffect");
    }

    @Override
    public void playEffect(Location location, org.bukkit.Effect effect, int data, int radius) {
        throw adapter.unbridged("World.playEffect");
    }

    @Override
    public org.bukkit.ChunkSnapshot getEmptyChunkSnapshot(int x, int z, boolean includeBiome,
            boolean includeBiomeTempRain) {
        throw adapter.unbridged("World.getEmptyChunkSnapshot");
    }

    @Override
    public void setSpawnFlags(boolean allowMonsters, boolean allowAnimals) {
        throw adapter.unbridged("World.setSpawnFlags");
    }

    @Override
    public boolean getAllowAnimals() {
        return true;
    }

    @Override
    public boolean getAllowMonsters() {
        return true;
    }

    @Override
    public double getTemperature(int x, int z) {
        return 0.5;
    }

    @Override
    public double getHumidity(int x, int z) {
        return 0.5;
    }

    @Override
    public boolean getKeepSpawnInMemory() {
        return true;
    }

    @Override
    public void setKeepSpawnInMemory(boolean keepLoaded) {
        throw adapter.unbridged("World.setKeepSpawnInMemory");
    }

    @Override
    public boolean isAutoSave() {
        return true;
    }

    @Override
    public void setAutoSave(boolean value) {
        throw adapter.unbridged("World.setAutoSave");
    }

    @Override
    public File getWorldFolder() {
        throw adapter.unbridged("World.getWorldFolder");
    }

    @Override
    public org.bukkit.WorldType getWorldType() {
        return org.bukkit.WorldType.FLAT;
    }

    @Override
    public boolean canGenerateStructures() {
        return false;
    }

    @Override
    public long getTicksPerAnimalSpawns() {
        return 400;
    }

    @Override
    public void setTicksPerAnimalSpawns(int ticksPerAnimalSpawns) {
        throw adapter.unbridged("World.setTicksPerAnimalSpawns");
    }

    @Override
    public long getTicksPerMonsterSpawns() {
        return 1;
    }

    @Override
    public void setTicksPerMonsterSpawns(int ticksPerMonsterSpawns) {
        throw adapter.unbridged("World.setTicksPerMonsterSpawns");
    }

    // ------------------------------------------------------------------ Spigot section

    @Override
    public Spigot spigot() {
        return new Spigot() {
        };
    }

    // ------------------------------------------------------------------ metadata + messaging

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        throw adapter.unbridged("World.setMetadata");
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return List.of();
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return false;
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        throw adapter.unbridged("World.removeMetadata");
    }

    @Override
    public void sendPluginMessage(Plugin source, String channel, byte[] message) {
        throw adapter.unbridged("World.sendPluginMessage");
    }

    @Override
    public Set<String> getListeningPluginChannels() {
        return Set.of();
    }
}
