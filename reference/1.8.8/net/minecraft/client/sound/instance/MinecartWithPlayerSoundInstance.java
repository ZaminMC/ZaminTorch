package net.minecraft.client.sound.instance;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class MinecartWithPlayerSoundInstance extends AbstractTickableSoundInstance {
    private final PlayerEntity player;
    private final MinecartEntity minecart;

    public MinecartWithPlayerSoundInstance(PlayerEntity player, MinecartEntity minecart) {
        super(new Identifier("minecraft:minecart.inside"));
        this.player = player;
        this.minecart = minecart;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = true;
        this.period = 0;
    }

    @Override
    public void tick() {
        if (!this.minecart.removed && this.player.isRiding() && this.player.vehicle == this.minecart) {
            float f = MathHelper.sqrt(this.minecart.velocityX * this.minecart.velocityX + this.minecart.velocityZ * this.minecart.velocityZ);
            if (f >= 0.01) {
                this.volume = 0.0F + MathHelper.clamp(f, 0.0F, 1.0F) * 0.75F;
            } else {
                this.volume = 0.0F;
            }
        } else {
            this.done = true;
        }
    }
}
