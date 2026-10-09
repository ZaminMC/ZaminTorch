package net.minecraft.world.border;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

public class WorldBorder {
    private final List<WorldBorderListener> listeners = Lists.newArrayList();
    private double centerX = 0.0;
    private double centerZ = 0.0;
    private double size = 6.0E7;
    private double sizeLerpTarget = this.size;
    private long sizeChangeEnd;
    private long sizeChangeStart;
    private int maxSize = 29999984;
    private double damagePerBlock = 0.2;
    private double safeZone = 5.0;
    private int warningTime = 15;
    private int warningDistance = 5;

    public boolean contains(BlockPos pos) {
        return pos.getX() + 1 > this.getMinX() && pos.getX() < this.getMaxX() && pos.getZ() + 1 > this.getMinZ() && pos.getZ() < this.getMaxZ();
    }

    public boolean contains(ChunkPos pos) {
        return pos.getMaxBlockPosX() > this.getMinX()
            && pos.getMinBlockPosX() < this.getMaxX()
            && pos.getMaxBlockPosZ() > this.getMinZ()
            && pos.getMinBlockPosZ() < this.getMaxZ();
    }

    public boolean contains(Box box) {
        return box.maxX > this.getMinX() && box.minX < this.getMaxX() && box.maxZ > this.getMinZ() && box.minZ < this.getMaxZ();
    }

    public double getDistanceFrom(Entity entity) {
        return this.getDistanceFrom(entity.x, entity.z);
    }

    public double getDistanceFrom(double x, double z) {
        double d0 = z - this.getMinZ();
        double d1 = this.getMaxZ() - z;
        double d2 = x - this.getMinX();
        double d3 = this.getMaxX() - x;
        double d4 = Math.min(d2, d3);
        d4 = Math.min(d4, d0);
        return Math.min(d4, d1);
    }

    public BorderStatus getStatus() {
        if (this.sizeLerpTarget < this.size) {
            return BorderStatus.SHRINKING;
        } else {
            return this.sizeLerpTarget > this.size ? BorderStatus.GROWING : BorderStatus.STATIONARY;
        }
    }

    public double getMinX() {
        double d0 = this.getCenterX() - this.getLerpSize() / 2.0;
        if (d0 < -this.maxSize) {
            d0 = -this.maxSize;
        }

        return d0;
    }

    public double getMinZ() {
        double d0 = this.getCenterZ() - this.getLerpSize() / 2.0;
        if (d0 < -this.maxSize) {
            d0 = -this.maxSize;
        }

        return d0;
    }

    public double getMaxX() {
        double d0 = this.getCenterX() + this.getLerpSize() / 2.0;
        if (d0 > this.maxSize) {
            d0 = this.maxSize;
        }

        return d0;
    }

    public double getMaxZ() {
        double d0 = this.getCenterZ() + this.getLerpSize() / 2.0;
        if (d0 > this.maxSize) {
            d0 = this.maxSize;
        }

        return d0;
    }

    public double getCenterX() {
        return this.centerX;
    }

    public double getCenterZ() {
        return this.centerZ;
    }

    public void setCenter(double x, double z) {
        this.centerX = x;
        this.centerZ = z;

        for (WorldBorderListener worldborderlistener : this.getListeners()) {
            worldborderlistener.onCenterChanged(this, x, z);
        }
    }

    public double getLerpSize() {
        if (this.getStatus() != BorderStatus.STATIONARY) {
            double d0 = (float)(System.currentTimeMillis() - this.sizeChangeStart) / (float)(this.sizeChangeEnd - this.sizeChangeStart);
            if (!(d0 >= 1.0)) {
                return this.size + (this.sizeLerpTarget - this.size) * d0;
            }

            this.setSize(this.sizeLerpTarget);
        }

        return this.size;
    }

    public long getLerpTime() {
        return this.getStatus() != BorderStatus.STATIONARY ? this.sizeChangeEnd - System.currentTimeMillis() : 0L;
    }

    public double getSizeLerpTarget() {
        return this.sizeLerpTarget;
    }

    public void setSize(double size) {
        this.size = size;
        this.sizeLerpTarget = size;
        this.sizeChangeEnd = System.currentTimeMillis();
        this.sizeChangeStart = this.sizeChangeEnd;

        for (WorldBorderListener worldborderlistener : this.getListeners()) {
            worldborderlistener.onSizeChanged(this, size);
        }
    }

    public void setSize(double size, double sizeLerpTarget, long sizeLerpTime) {
        this.size = size;
        this.sizeLerpTarget = sizeLerpTarget;
        this.sizeChangeStart = System.currentTimeMillis();
        this.sizeChangeEnd = this.sizeChangeStart + sizeLerpTime;

        for (WorldBorderListener worldborderlistener : this.getListeners()) {
            worldborderlistener.onSizeChanged(this, size, sizeLerpTarget, sizeLerpTime);
        }
    }

    protected List<WorldBorderListener> getListeners() {
        return Lists.newArrayList(this.listeners);
    }

    public void addListener(WorldBorderListener listener) {
        this.listeners.add(listener);
    }

    public void setMaxSize(int size) {
        this.maxSize = size;
    }

    public int getMaxSize() {
        return this.maxSize;
    }

    public double getSafeZone() {
        return this.safeZone;
    }

    public void setSafeZone(double zone) {
        this.safeZone = zone;

        for (WorldBorderListener worldborderlistener : this.getListeners()) {
            worldborderlistener.onSafeZoneChanged(this, zone);
        }
    }

    public double getDamagePerBlock() {
        return this.damagePerBlock;
    }

    public void setDamagePerBlock(double damage) {
        this.damagePerBlock = damage;

        for (WorldBorderListener worldborderlistener : this.getListeners()) {
            worldborderlistener.onDamagePerBlockChanged(this, damage);
        }
    }

    public double getSizeChangeSpeed() {
        return this.sizeChangeEnd == this.sizeChangeStart ? 0.0 : Math.abs(this.size - this.sizeLerpTarget) / (this.sizeChangeEnd - this.sizeChangeStart);
    }

    public int getWarningTime() {
        return this.warningTime;
    }

    public void setWarningTime(int time) {
        this.warningTime = time;

        for (WorldBorderListener worldborderlistener : this.getListeners()) {
            worldborderlistener.onWarningTimeChanged(this, time);
        }
    }

    public int getWarningDistance() {
        return this.warningDistance;
    }

    public void setWarningDistance(int distance) {
        this.warningDistance = distance;

        for (WorldBorderListener worldborderlistener : this.getListeners()) {
            worldborderlistener.onWarningBlocksChanged(this, distance);
        }
    }
}
