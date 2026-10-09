package net.minecraft.server.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorderListener;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.village.SavedVillageData;

public class ReadOnlyServerWorld extends ServerWorld {
    private ServerWorld delegate;

    public ReadOnlyServerWorld(MinecraftServer server, WorldStorage storage, int dimension, ServerWorld delegate, Profiler profiler) {
        super(server, storage, new ReadOnlyWorldData(delegate.getData()), dimension, profiler);
        this.delegate = delegate;
        delegate.getWorldBorder().addListener(new WorldBorderListener() {
            @Override
            public void onSizeChanged(WorldBorder border, double size) {
                ReadOnlyServerWorld.this.getWorldBorder().setSize(size);
            }

            @Override
            public void onSizeChanged(WorldBorder border, double size, double sizeLerpTarget, long sizeLerpTime) {
                ReadOnlyServerWorld.this.getWorldBorder().setSize(size, sizeLerpTarget, sizeLerpTime);
            }

            @Override
            public void onCenterChanged(WorldBorder border, double centerX, double centerZ) {
                ReadOnlyServerWorld.this.getWorldBorder().setCenter(centerX, centerZ);
            }

            @Override
            public void onWarningTimeChanged(WorldBorder border, int warningTime) {
                ReadOnlyServerWorld.this.getWorldBorder().setWarningTime(warningTime);
            }

            @Override
            public void onWarningBlocksChanged(WorldBorder border, int warningBlocks) {
                ReadOnlyServerWorld.this.getWorldBorder().setWarningDistance(warningBlocks);
            }

            @Override
            public void onDamagePerBlockChanged(WorldBorder border, double damagePerBlock) {
                ReadOnlyServerWorld.this.getWorldBorder().setDamagePerBlock(damagePerBlock);
            }

            @Override
            public void onSafeZoneChanged(WorldBorder border, double safeZone) {
                ReadOnlyServerWorld.this.getWorldBorder().setSafeZone(safeZone);
            }
        });
    }

    @Override
    protected void saveData() {
    }

    @Override
    public World load() {
        this.savedDataStorage = this.delegate.getSavedDataStorage();
        this.scoreboard = this.delegate.getScoreboard();
        String s = SavedVillageData.getId(this.dimension);
        SavedVillageData savedvillagedata = (SavedVillageData)this.savedDataStorage.load(SavedVillageData.class, s);
        if (savedvillagedata == null) {
            this.villages = new SavedVillageData(this);
            this.savedDataStorage.set(s, this.villages);
        } else {
            this.villages = savedvillagedata;
            this.villages.setWorld(this);
        }

        return this;
    }
}
