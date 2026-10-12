package net.minecraft.client.sound.instance;

import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class EmptyMinecartSoundInstance extends AbstractTickableSoundInstance {
    private final MinecartEntity minecart;
    private float pitch = 0.0F;

    public EmptyMinecartSoundInstance(MinecartEntity minecart) {
        super(new Identifier("minecraft:minecart.base"));
        this.minecart = minecart;
        this.looping = true;
        this.period = 0;
    }

    @Override
    public void tick() {
        if (this.minecart.removed) {
            this.done = true;
        } else {
            this.x = (float)this.minecart.x;
            this.y = (float)this.minecart.y;
            this.z = (float)this.minecart.z;
            float f = MathHelper.sqrt(this.minecart.velocityX * this.minecart.velocityX + this.minecart.velocityZ * this.minecart.velocityZ);
            if (f >= 0.01) {
                this.pitch = MathHelper.clamp(this.pitch + 0.0025F, 0.0F, 1.0F);
                this.volume = 0.0F + MathHelper.clamp(f, 0.0F, 0.5F) * 0.7F;
            } else {
                this.pitch = 0.0F;
                this.volume = 0.0F;
            }
        }
    }
}
