package net.minecraft.server;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.GameEventS2CPacket;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class DemoServerPlayerInteractionManager extends ServerPlayerInteractionManager {
    private boolean sentHelp;
    private boolean demoEnded;
    private int reminderTicks;
    private int ticks;

    public DemoServerPlayerInteractionManager(World world) {
        super(world);
    }

    @Override
    public void tick() {
        super.tick();
        this.ticks++;
        long i = this.world.getTime();
        long j = i / 24000L + 1L;
        if (!this.sentHelp && this.ticks > 20) {
            this.sentHelp = true;
            this.player.networkHandler.sendPacket(new GameEventS2CPacket(5, 0.0F));
        }

        this.demoEnded = i > 120500L;
        if (this.demoEnded) {
            this.reminderTicks++;
        }

        if (i % 24000L == 500L) {
            if (j <= 6L) {
                this.player.sendMessage(new TranslatableText("demo.day." + j));
            }
        } else if (j == 1L) {
            if (i == 100L) {
                this.player.networkHandler.sendPacket(new GameEventS2CPacket(5, 101.0F));
            } else if (i == 175L) {
                this.player.networkHandler.sendPacket(new GameEventS2CPacket(5, 102.0F));
            } else if (i == 250L) {
                this.player.networkHandler.sendPacket(new GameEventS2CPacket(5, 103.0F));
            }
        } else if (j == 5L && i % 24000L == 22000L) {
            this.player.sendMessage(new TranslatableText("demo.day.warning"));
        }
    }

    private void sendDemoReminder() {
        if (this.reminderTicks > 100) {
            this.player.sendMessage(new TranslatableText("demo.reminder"));
            this.reminderTicks = 0;
        }
    }

    @Override
    public void startMiningBlock(BlockPos pos, Direction face) {
        if (this.demoEnded) {
            this.sendDemoReminder();
        } else {
            super.startMiningBlock(pos, face);
        }
    }

    @Override
    public void finishMiningBlock(BlockPos pos) {
        if (!this.demoEnded) {
            super.finishMiningBlock(pos);
        }
    }

    @Override
    public boolean tryMineBlock(BlockPos pos) {
        return !this.demoEnded && super.tryMineBlock(pos);
    }

    @Override
    public boolean useItem(PlayerEntity player, World world, ItemStack itemInHand) {
        if (this.demoEnded) {
            this.sendDemoReminder();
            return false;
        } else {
            return super.useItem(player, world, itemInHand);
        }
    }

    @Override
    public boolean useBlock(PlayerEntity player, World world, ItemStack itemInHand, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (this.demoEnded) {
            this.sendDemoReminder();
            return false;
        } else {
            return super.useBlock(player, world, itemInHand, pos, face, faceX, faceY, faceZ);
        }
    }
}
