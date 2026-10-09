package net.minecraft.entity.living.mob.monster;

import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

public class MagmaCubeEntity extends SlimeEntity {
    public MagmaCubeEntity(World world) {
        super(world);
        this.immuneToFire = true;
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.2F);
    }

    @Override
    public boolean canSpawn() {
        return this.world.getDifficulty() != Difficulty.PEACEFUL;
    }

    @Override
    public boolean isUnobstructed() {
        return this.world.isUnobstructed(this.getShape(), this)
            && this.world.getCollisions(this, this.getShape()).isEmpty()
            && !this.world.containsLiquid(this.getShape());
    }

    @Override
    public int getArmorProtection() {
        return this.getSize() * 3;
    }

    @Override
    public int getLightLevel(float tickDelta) {
        return 15728880;
    }

    @Override
    public float getBrightness(float tickDelta) {
        return 1.0F;
    }

    @Override
    protected ParticleType getParticleType() {
        return ParticleType.FLAME;
    }

    @Override
    protected SlimeEntity getInstance() {
        return new MagmaCubeEntity(this.world);
    }

    @Override
    protected Item getDropItem() {
        return Items.MAGMA_CREAM;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        Item item = this.getDropItem();
        if (item != null && this.getSize() > 1) {
            int i = this.random.nextInt(4) - 2;
            if (lootingMultiplier > 0) {
                i += this.random.nextInt(lootingMultiplier + 1);
            }

            for (int j = 0; j < i; j++) {
                this.dropItem(item, 1);
            }
        }
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected int getTicksUntilNextJump() {
        return super.getTicksUntilNextJump() * 4;
    }

    @Override
    protected void updateStretch() {
        this.targetStretch *= 0.9F;
    }

    @Override
    protected void jump() {
        this.velocityY = 0.42F + this.getSize() * 0.1F;
        this.velocityDirty = true;
    }

    @Override
    protected void jumpInLava() {
        this.velocityY = 0.22F + this.getSize() * 0.05F;
        this.velocityDirty = true;
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
    }

    @Override
    protected boolean isBig() {
        return true;
    }

    @Override
    protected int getDamageAmount() {
        return super.getDamageAmount() + 2;
    }

    @Override
    protected String getSoundName() {
        return this.getSize() > 1 ? "mob.magmacube.big" : "mob.magmacube.small";
    }

    @Override
    protected boolean makesLandSound() {
        return true;
    }
}
