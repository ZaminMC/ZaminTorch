package net.minecraft.entity.living.player;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.FoodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.Difficulty;

public class HungerManager {
    private int foodLevel = 20;
    private float saturationLevel;
    private float exhaustion;
    private int starvationTimer;
    private int lastFoodLevel = 20;

    public HungerManager() {
        this.saturationLevel = 5.0F;
    }

    public void add(int food, float saturation) {
        this.foodLevel = Math.min(food + this.foodLevel, 20);
        this.saturationLevel = Math.min(this.saturationLevel + food * saturation * 2.0F, this.foodLevel);
    }

    public void add(FoodItem food, ItemStack item) {
        this.add(food.getHungerPoints(item), food.getSaturation(item));
    }

    public void tick(PlayerEntity player) {
        Difficulty difficulty = player.world.getDifficulty();
        this.lastFoodLevel = this.foodLevel;
        if (this.exhaustion > 4.0F) {
            this.exhaustion -= 4.0F;
            if (this.saturationLevel > 0.0F) {
                this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
            } else if (difficulty != Difficulty.PEACEFUL) {
                this.foodLevel = Math.max(this.foodLevel - 1, 0);
            }
        }

        if (player.world.getGameRules().getBoolean("naturalRegeneration") && this.foodLevel >= 18 && player.needsHealing()) {
            this.starvationTimer++;
            if (this.starvationTimer >= 80) {
                player.heal(1.0F);
                this.addExhaustion(3.0F);
                this.starvationTimer = 0;
            }
        } else if (this.foodLevel <= 0) {
            this.starvationTimer++;
            if (this.starvationTimer >= 80) {
                if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
                    player.takeDamage(DamageSource.STARVE, 1.0F);
                }

                this.starvationTimer = 0;
            }
        } else {
            this.starvationTimer = 0;
        }
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("foodLevel", 99)) {
            this.foodLevel = nbt.getInt("foodLevel");
            this.starvationTimer = nbt.getInt("foodTickTimer");
            this.saturationLevel = nbt.getFloat("foodSaturationLevel");
            this.exhaustion = nbt.getFloat("foodExhaustionLevel");
        }
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putInt("foodLevel", this.foodLevel);
        nbt.putInt("foodTickTimer", this.starvationTimer);
        nbt.putFloat("foodSaturationLevel", this.saturationLevel);
        nbt.putFloat("foodExhaustionLevel", this.exhaustion);
    }

    public int getFoodLevel() {
        return this.foodLevel;
    }

    public int getLastFoodLevel() {
        return this.lastFoodLevel;
    }

    public boolean needsFood() {
        return this.foodLevel < 20;
    }

    public void addExhaustion(float exhaustion) {
        this.exhaustion = Math.min(this.exhaustion + exhaustion, 40.0F);
    }

    public float getSaturationLevel() {
        return this.saturationLevel;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = foodLevel;
    }

    public void setSaturationLevel(float saturationLevel) {
        this.saturationLevel = saturationLevel;
    }
}
