package net.minecraft.entity.living.mob.monster.boss;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;

public class EnderDragonPart extends Entity {
    public final EnderDragon dragon;
    public final String name;

    public EnderDragonPart(EnderDragon dragon, String name, float width, float height) {
        super(dragon.getWorld());
        this.setSize(width, height);
        this.dragon = dragon;
        this.name = name;
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
    }

    @Override
    public boolean hasCollision() {
        return true;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        return !this.isInvulnerable(source) && this.dragon.takeDamage(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || this.dragon == entity;
    }
}
