package net.minecraft.entity.projectile;

import java.util.List;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class PotionEntity extends ThrownEntity {
    private ItemStack statusEffect;

    public PotionEntity(World world) {
        super(world);
    }

    public PotionEntity(World world, LivingEntity thrower, int statusEffect) {
        this(world, thrower, new ItemStack(Items.POTION, 1, statusEffect));
    }

    public PotionEntity(World world, LivingEntity thrower, ItemStack statusEffect) {
        super(world, thrower);
        this.statusEffect = statusEffect;
    }

    public PotionEntity(World world, double x, double y, double z, int statusEffect) {
        this(world, x, y, z, new ItemStack(Items.POTION, 1, statusEffect));
    }

    public PotionEntity(World world, double x, double y, double z, ItemStack statusEffect) {
        super(world, x, y, z);
        this.statusEffect = statusEffect;
    }

    @Override
    protected float getGravity() {
        return 0.05F;
    }

    @Override
    protected float getPower() {
        return 0.5F;
    }

    @Override
    protected float getStartPitchOffset() {
        return -20.0F;
    }

    public void setPotionValue(int damage) {
        if (this.statusEffect == null) {
            this.statusEffect = new ItemStack(Items.POTION, 1, 0);
        }

        this.statusEffect.setDamage(damage);
    }

    public int getStatusEffect() {
        if (this.statusEffect == null) {
            this.statusEffect = new ItemStack(Items.POTION, 1, 0);
        }

        return this.statusEffect.getMetadata();
    }

    @Override
    protected void onCollision(HitResult result) {
        if (!this.world.isClient) {
            List<StatusEffectInstance> list = Items.POTION.getPotionEffects(this.statusEffect);
            if (list != null && !list.isEmpty()) {
                Box box = this.getShape().grown(4.0, 2.0, 4.0);
                List<LivingEntity> list1 = this.world.getEntitiesOfType(LivingEntity.class, box);
                if (!list1.isEmpty()) {
                    for (LivingEntity livingentity : list1) {
                        double d0 = this.squaredDistanceTo(livingentity);
                        if (d0 < 16.0) {
                            double d1 = 1.0 - Math.sqrt(d0) / 4.0;
                            if (livingentity == result.entity) {
                                d1 = 1.0;
                            }

                            for (StatusEffectInstance statuseffectinstance : list) {
                                int i = statuseffectinstance.getId();
                                if (StatusEffect.BY_ID[i].isInstant()) {
                                    StatusEffect.BY_ID[i].affectHealth(this, this.getThrower(), livingentity, statuseffectinstance.getAmplifier(), d1);
                                } else {
                                    int j = (int)(d1 * statuseffectinstance.getDuration() + 0.5);
                                    if (j > 20) {
                                        livingentity.addStatusEffect(new StatusEffectInstance(i, j, statuseffectinstance.getAmplifier()));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            this.world.doEvent(2002, new BlockPos(this), this.getStatusEffect());
            this.remove();
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.contains("Potion", 10)) {
            this.statusEffect = ItemStack.fromNbt(nbt.getCompound("Potion"));
        } else {
            this.setPotionValue(nbt.getInt("potionValue"));
        }

        if (this.statusEffect == null) {
            this.remove();
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        if (this.statusEffect != null) {
            nbt.put("Potion", this.statusEffect.writeNbt(new NbtCompound()));
        }
    }
}
