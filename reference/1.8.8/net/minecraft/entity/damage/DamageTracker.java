package net.minecraft.entity.damage;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;

public class DamageTracker {
    private final List<DamageRecord> records = Lists.newArrayList();
    private final LivingEntity entity;
    private int lastDamageTime;
    private int ageOfLastDamage;
    private int lastStatusChangeAge;
    private boolean inCombatWithMob;
    private boolean takingDamage;
    private String nextFallLocation;

    public DamageTracker(LivingEntity entity) {
        this.entity = entity;
    }

    public void prepareForNextDamage() {
        this.clearFallLocation();
        if (this.entity.isClimbing()) {
            Block block = this.entity.world.getBlockState(new BlockPos(this.entity.x, this.entity.getShape().minY, this.entity.z)).getBlock();
            if (block == Blocks.LADDER) {
                this.nextFallLocation = "ladder";
            } else if (block == Blocks.VINE) {
                this.nextFallLocation = "vines";
            }
        } else if (this.entity.isInWater()) {
            this.nextFallLocation = "water";
        }
    }

    public void recordDamage(DamageSource damageSource, float originalHealth, float damage) {
        this.resetStatus();
        this.prepareForNextDamage();
        DamageRecord damagerecord = new DamageRecord(damageSource, this.entity.ticks, originalHealth, damage, this.nextFallLocation, this.entity.fallDistance);
        this.records.add(damagerecord);
        this.lastDamageTime = this.entity.ticks;
        this.takingDamage = true;
        if (damagerecord.isCombat() && !this.inCombatWithMob && this.entity.isAlive()) {
            this.inCombatWithMob = true;
            this.ageOfLastDamage = this.entity.ticks;
            this.lastStatusChangeAge = this.ageOfLastDamage;
            this.entity.enterCombat();
        }
    }

    public Text getDeathMessage() {
        if (this.records.size() == 0) {
            return new TranslatableText("death.attack.generic", this.entity.getDisplayName());
        }

        DamageRecord damagerecord = this.getDamageBeforeLargestFall();
        DamageRecord damagerecord1 = this.records.get(this.records.size() - 1);
        Text text1 = damagerecord1.getAttackerName();
        Entity entity = damagerecord1.getSource().getAttacker();
        Text text;
        if (damagerecord != null && damagerecord1.getSource() == DamageSource.FALL) {
            Text text2 = damagerecord.getAttackerName();
            if (damagerecord.getSource() == DamageSource.FALL || damagerecord.getSource() == DamageSource.OUT_OF_WORLD) {
                text = new TranslatableText("death.fell.accident." + this.getFallLocation(damagerecord), this.entity.getDisplayName());
            } else if (text2 != null && (text1 == null || !text2.equals(text1))) {
                Entity entity1 = damagerecord.getSource().getAttacker();
                ItemStack itemstack1 = entity1 instanceof LivingEntity ? ((LivingEntity)entity1).getDisplayItemInHand() : null;
                if (itemstack1 != null && itemstack1.hasCustomHoverName()) {
                    text = new TranslatableText("death.fell.assist.item", this.entity.getDisplayName(), text2, itemstack1.getDisplayName());
                } else {
                    text = new TranslatableText("death.fell.assist", this.entity.getDisplayName(), text2);
                }
            } else if (text1 != null) {
                ItemStack itemstack = entity instanceof LivingEntity ? ((LivingEntity)entity).getDisplayItemInHand() : null;
                if (itemstack != null && itemstack.hasCustomHoverName()) {
                    text = new TranslatableText("death.fell.finish.item", this.entity.getDisplayName(), text1, itemstack.getDisplayName());
                } else {
                    text = new TranslatableText("death.fell.finish", this.entity.getDisplayName(), text1);
                }
            } else {
                text = new TranslatableText("death.fell.killer", this.entity.getDisplayName());
            }
        } else {
            text = damagerecord1.getSource().getDeathMessage(this.entity);
        }

        return text;
    }

    public LivingEntity getInitialAttacker() {
        LivingEntity livingentity = null;
        PlayerEntity playerentity = null;
        float f = 0.0F;
        float f1 = 0.0F;

        for (DamageRecord damagerecord : this.records) {
            if (damagerecord.getSource().getAttacker() instanceof PlayerEntity && (playerentity == null || damagerecord.getDamage() > f1)) {
                f1 = damagerecord.getDamage();
                playerentity = (PlayerEntity)damagerecord.getSource().getAttacker();
            }

            if (damagerecord.getSource().getAttacker() instanceof LivingEntity && (livingentity == null || damagerecord.getDamage() > f)) {
                f = damagerecord.getDamage();
                livingentity = (LivingEntity)damagerecord.getSource().getAttacker();
            }
        }

        return playerentity != null && f1 >= f / 3.0F ? playerentity : livingentity;
    }

    private DamageRecord getDamageBeforeLargestFall() {
        DamageRecord damagerecord = null;
        DamageRecord damagerecord1 = null;
        int i = 0;
        float f = 0.0F;

        for (int j = 0; j < this.records.size(); j++) {
            DamageRecord damagerecord2 = this.records.get(j);
            DamageRecord damagerecord3 = j > 0 ? this.records.get(j - 1) : null;
            if ((damagerecord2.getSource() == DamageSource.FALL || damagerecord2.getSource() == DamageSource.OUT_OF_WORLD)
                && damagerecord2.getFallDistance() > 0.0F
                && (damagerecord == null || damagerecord2.getFallDistance() > f)) {
                if (j > 0) {
                    damagerecord = damagerecord3;
                } else {
                    damagerecord = damagerecord2;
                }

                f = damagerecord2.getFallDistance();
            }

            if (damagerecord2.getFallLocation() != null && (damagerecord1 == null || damagerecord2.getDamage() > i)) {
                damagerecord1 = damagerecord2;
            }
        }

        if (f > 5.0F && damagerecord != null) {
            return damagerecord;
        } else {
            return i > 5 && damagerecord1 != null ? damagerecord1 : null;
        }
    }

    private String getFallLocation(DamageRecord record) {
        return record.getFallLocation() == null ? "generic" : record.getFallLocation();
    }

    public int getDuration() {
        return this.inCombatWithMob ? this.entity.ticks - this.ageOfLastDamage : this.lastStatusChangeAge - this.ageOfLastDamage;
    }

    private void clearFallLocation() {
        this.nextFallLocation = null;
    }

    public void resetStatus() {
        int i = this.inCombatWithMob ? 300 : 100;
        if (this.takingDamage && (!this.entity.isAlive() || this.entity.ticks - this.lastDamageTime > i)) {
            boolean flag = this.inCombatWithMob;
            this.takingDamage = false;
            this.inCombatWithMob = false;
            this.lastStatusChangeAge = this.entity.ticks;
            if (flag) {
                this.entity.endCombat();
            }

            this.records.clear();
        }
    }

    public LivingEntity getPlayer() {
        return this.entity;
    }
}
