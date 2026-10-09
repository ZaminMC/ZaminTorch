package net.minecraft.server.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;

public class ReadOnlyWorldData extends WorldData {
    private final WorldData properties;

    public ReadOnlyWorldData(WorldData worldData) {
        this.properties = worldData;
    }

    @Override
    public NbtCompound toNbt() {
        return this.properties.toNbt();
    }

    @Override
    public NbtCompound toNbt(NbtCompound playerData) {
        return this.properties.toNbt(playerData);
    }

    @Override
    public long getSeed() {
        return this.properties.getSeed();
    }

    @Override
    public int getSpawnX() {
        return this.properties.getSpawnX();
    }

    @Override
    public int getSpawnY() {
        return this.properties.getSpawnY();
    }

    @Override
    public int getSpawnZ() {
        return this.properties.getSpawnZ();
    }

    @Override
    public long getTime() {
        return this.properties.getTime();
    }

    @Override
    public long getTimeOfDay() {
        return this.properties.getTimeOfDay();
    }

    @Override
    public long getSizeOnDisk() {
        return this.properties.getSizeOnDisk();
    }

    @Override
    public NbtCompound getPlayerData() {
        return this.properties.getPlayerData();
    }

    @Override
    public String getName() {
        return this.properties.getName();
    }

    @Override
    public int getVersion() {
        return this.properties.getVersion();
    }

    @Override
    public long getLastPlayed() {
        return this.properties.getLastPlayed();
    }

    @Override
    public boolean isThundering() {
        return this.properties.isThundering();
    }

    @Override
    public int getThunderTime() {
        return this.properties.getThunderTime();
    }

    @Override
    public boolean isRaining() {
        return this.properties.isRaining();
    }

    @Override
    public int getRainTime() {
        return this.properties.getRainTime();
    }

    @Override
    public WorldSettings.GameMode getDefaultGamemode() {
        return this.properties.getDefaultGamemode();
    }

    @Override
    public void setSpawnX(int x) {
    }

    @Override
    public void setSpawnY(int y) {
    }

    @Override
    public void setSpawnZ(int z) {
    }

    @Override
    public void setTime(long time) {
    }

    @Override
    public void setTimeOfDay(long time) {
    }

    @Override
    public void setSpawnPoint(BlockPos pos) {
    }

    @Override
    public void setName(String name) {
    }

    @Override
    public void setVersion(int version) {
    }

    @Override
    public void setThundering(boolean thundering) {
    }

    @Override
    public void setThunderTime(int thunderTime) {
    }

    @Override
    public void setRaining(boolean raining) {
    }

    @Override
    public void setRainTime(int rainTime) {
    }

    @Override
    public boolean allowStructures() {
        return this.properties.allowStructures();
    }

    @Override
    public boolean isHardcore() {
        return this.properties.isHardcore();
    }

    @Override
    public WorldGeneratorType getGeneratorType() {
        return this.properties.getGeneratorType();
    }

    @Override
    public void setGeneratorType(WorldGeneratorType type) {
    }

    @Override
    public boolean allowCommands() {
        return this.properties.allowCommands();
    }

    @Override
    public void setAllowCommands(boolean allowCommands) {
    }

    @Override
    public boolean isInitialized() {
        return this.properties.isInitialized();
    }

    @Override
    public void setInitialized(boolean initialized) {
    }

    @Override
    public GameRules getGameRules() {
        return this.properties.getGameRules();
    }

    @Override
    public Difficulty getDifficulty() {
        return this.properties.getDifficulty();
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.properties.isDifficultyLocked();
    }

    @Override
    public void setDifficultyLocked(boolean locked) {
    }
}
