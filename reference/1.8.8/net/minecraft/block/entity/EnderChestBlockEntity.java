package net.minecraft.block.entity;

import net.minecraft.block.Blocks;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.Tickable;

public class EnderChestBlockEntity extends BlockEntity implements Tickable {
    public float animationProgress;
    public float lastAnimationProgress;
    public int viewerCount;
    private int ticks;

    @Override
    public void tick() {
        if (++this.ticks % 20 * 4 == 0) {
            this.world.addBlockEvent(this.pos, Blocks.ENDER_CHEST, 1, this.viewerCount);
        }

        this.lastAnimationProgress = this.animationProgress;
        int i = this.pos.getX();
        int j = this.pos.getY();
        int k = this.pos.getZ();
        float f = 0.1F;
        if (this.viewerCount > 0 && this.animationProgress == 0.0F) {
            double d0 = i + 0.5;
            double d1 = k + 0.5;
            this.world.playSound(d0, j + 0.5, d1, "random.chestopen", 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
        }

        if (this.viewerCount == 0 && this.animationProgress > 0.0F || this.viewerCount > 0 && this.animationProgress < 1.0F) {
            float f2 = this.animationProgress;
            if (this.viewerCount > 0) {
                this.animationProgress += f;
            } else {
                this.animationProgress -= f;
            }

            if (this.animationProgress > 1.0F) {
                this.animationProgress = 1.0F;
            }

            float f1 = 0.5F;
            if (this.animationProgress < f1 && f2 >= f1) {
                double d3 = i + 0.5;
                double d2 = k + 0.5;
                this.world.playSound(d3, j + 0.5, d2, "random.chestclosed", 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
            }

            if (this.animationProgress < 0.0F) {
                this.animationProgress = 0.0F;
            }
        }
    }

    @Override
    public boolean doEvent(int type, int data) {
        if (type == 1) {
            this.viewerCount = data;
            return true;
        } else {
            return super.doEvent(type, data);
        }
    }

    @Override
    public void markRemoved() {
        this.clearBlockCache();
        super.markRemoved();
    }

    public void onOpen() {
        this.viewerCount++;
        this.world.addBlockEvent(this.pos, Blocks.ENDER_CHEST, 1, this.viewerCount);
    }

    public void onClose() {
        this.viewerCount--;
        this.world.addBlockEvent(this.pos, Blocks.ENDER_CHEST, 1, this.viewerCount);
    }

    public boolean isValid(PlayerEntity player) {
        return this.world.getBlockEntity(this.pos) == this
            && !(player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0);
    }
}
