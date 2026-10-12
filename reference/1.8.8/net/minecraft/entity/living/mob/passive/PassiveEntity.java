package net.minecraft.entity.living.mob.passive;

import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public abstract class PassiveEntity extends PathFinderMobEntity {
    protected int breedingAge;
    protected int forcedAge;
    protected int happyTicksRemaining;
    private float ageWidth = -1.0F;
    private float ageHeight;

    public PassiveEntity(World world) {
        super(world);
    }

    public abstract PassiveEntity makeChild(PassiveEntity mate);

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() == Items.SPAWN_EGG) {
            if (!this.world.isClient) {
                Class<? extends Entity> oclass = Entities.getType(itemstack.getMetadata());
                if (oclass != null && this.getClass() == oclass) {
                    PassiveEntity passiveentity = this.makeChild(this);
                    if (passiveentity != null) {
                        passiveentity.setBreedingAge(-24000);
                        passiveentity.setPositionAndAngles(this.x, this.y, this.z, 0.0F, 0.0F);
                        this.world.addEntity(passiveentity);
                        if (itemstack.hasCustomHoverName()) {
                            passiveentity.setCustomName(itemstack.getHoverName());
                        }

                        if (!player.abilities.creativeMode) {
                            itemstack.size--;
                            if (itemstack.size <= 0) {
                                player.inventory.setItem(player.inventory.selectedSlot, null);
                            }
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(12, (byte)0);
    }

    public int getBreedingAge() {
        return this.world.isClient ? this.syncedData.getByte(12) : this.breedingAge;
    }

    public void growUp(int age, boolean overGrow) {
        int i = this.getBreedingAge();
        int j = i;
        i += age * 20;
        if (i > 0) {
            i = 0;
            if (j < 0) {
                this.onGrowUp();
            }
        }

        int k = i - j;
        this.setBreedingAge(i);
        if (overGrow) {
            this.forcedAge += k;
            if (this.happyTicksRemaining == 0) {
                this.happyTicksRemaining = 40;
            }
        }

        if (this.getBreedingAge() == 0) {
            this.setBreedingAge(this.forcedAge);
        }
    }

    public void growUp(int age) {
        this.growUp(age, false);
    }

    public void setBreedingAge(int age) {
        this.syncedData.update(12, (byte)MathHelper.clamp(age, -1, 1));
        this.breedingAge = age;
        this.setAgeSize(this.isBaby());
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("Age", this.getBreedingAge());
        nbt.putInt("ForcedAge", this.forcedAge);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setBreedingAge(nbt.getInt("Age"));
        this.forcedAge = nbt.getInt("ForcedAge");
    }

    @Override
    public void mobTick() {
        super.mobTick();
        if (this.world.isClient) {
            if (this.happyTicksRemaining > 0) {
                if (this.happyTicksRemaining % 4 == 0) {
                    this.world
                        .addParticle(
                            ParticleType.VILLAGER_HAPPY,
                            this.x + this.random.nextFloat() * this.width * 2.0F - this.width,
                            this.y + 0.5 + this.random.nextFloat() * this.height,
                            this.z + this.random.nextFloat() * this.width * 2.0F - this.width,
                            0.0,
                            0.0,
                            0.0
                        );
                }

                this.happyTicksRemaining--;
            }

            this.setAgeSize(this.isBaby());
        } else {
            int i = this.getBreedingAge();
            if (i < 0) {
                this.setBreedingAge(++i);
                if (i == 0) {
                    this.onGrowUp();
                }
            } else if (i > 0) {
                this.setBreedingAge(--i);
            }
        }
    }

    protected void onGrowUp() {
    }

    @Override
    public boolean isBaby() {
        return this.getBreedingAge() < 0;
    }

    public void setAgeSize(boolean isBaby) {
        this.resizeBounds(isBaby ? 0.5F : 1.0F);
    }

    @Override
    protected final void setSize(float width, float height) {
        boolean flag = this.ageWidth > 0.0F;
        this.ageWidth = width;
        this.ageHeight = height;
        if (!flag) {
            this.resizeBounds(1.0F);
        }
    }

    protected final void resizeBounds(float size) {
        super.setSize(this.ageWidth * size, this.ageHeight * size);
    }
}
