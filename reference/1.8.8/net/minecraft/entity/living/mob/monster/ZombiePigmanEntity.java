package net.minecraft.entity.living.mob.monster;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class ZombiePigmanEntity extends ZombieEntity {
    private static final UUID ATTACKING_SPEED_BOOST_ID = UUID.fromString("49455A49-7EC5-45BA-B886-3B90B23A1718");
    private static final AttributeModifier ATTACKING_SPEED_BOOST = new AttributeModifier(ATTACKING_SPEED_BOOST_ID, "Attacking speed boost", 0.05, 0)
        .setSerialized(false);
    private int angerValue;
    private int angerSoundDelay;
    private UUID attackerUuid;

    public ZombiePigmanEntity(World world) {
        super(world);
        this.immuneToFire = true;
    }

    @Override
    public void setAttacker(LivingEntity attacker) {
        super.setAttacker(attacker);
        if (attacker != null) {
            this.attackerUuid = attacker.getUuid();
        }
    }

    @Override
    protected void initMoveGoals() {
        this.targetSelector.addGoal(1, new ZombiePigmanEntity.RevengeGoal(this));
        this.targetSelector.addGoal(2, new ZombiePigmanEntity.TargetPlayerGoal(this));
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(REINFORCEMENTS_ATTRIBUTE).setBase(0.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.23F);
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(5.0);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    protected void mobAiTick() {
        EntityAttributeInstance entityattributeinstance = this.getAttribute(EntityAttributes.MOVEMENT_SPEED);
        if (this.isAngry()) {
            if (!this.isBaby() && !entityattributeinstance.hasModifier(ATTACKING_SPEED_BOOST)) {
                entityattributeinstance.addModifier(ATTACKING_SPEED_BOOST);
            }

            this.angerValue--;
        } else if (entityattributeinstance.hasModifier(ATTACKING_SPEED_BOOST)) {
            entityattributeinstance.removeModifier(ATTACKING_SPEED_BOOST);
        }

        if (this.angerSoundDelay > 0 && --this.angerSoundDelay == 0) {
            this.playSound("mob.zombiepig.zpigangry", this.getSoundVolume() * 2.0F, ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * 1.8F);
        }

        if (this.angerValue > 0 && this.attackerUuid != null && this.getAttacker() == null) {
            PlayerEntity playerentity = this.world.getPlayer(this.attackerUuid);
            this.setAttacker(playerentity);
            this.attackingPlayer = playerentity;
            this.playerHitTimer = this.getLastAttackedTime();
        }

        super.mobAiTick();
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
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putShort("Anger", (short)this.angerValue);
        if (this.attackerUuid != null) {
            nbt.putString("HurtBy", this.attackerUuid.toString());
        } else {
            nbt.putString("HurtBy", "");
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.angerValue = nbt.getShort("Anger");
        String s = nbt.getString("HurtBy");
        if (s.length() > 0) {
            this.attackerUuid = UUID.fromString(s);
            PlayerEntity playerentity = this.world.getPlayer(this.attackerUuid);
            this.setAttacker(playerentity);
            if (playerentity != null) {
                this.attackingPlayer = playerentity;
                this.playerHitTimer = this.getLastAttackedTime();
            }
        }
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        Entity entity = source.getAttacker();
        if (entity instanceof PlayerEntity) {
            this.getAngryTo(entity);
        }

        return super.takeDamage(source, amount);
    }

    private void getAngryTo(Entity target) {
        this.angerValue = 400 + this.random.nextInt(400);
        this.angerSoundDelay = this.random.nextInt(40);
        if (target instanceof LivingEntity) {
            this.setAttacker((LivingEntity)target);
        }
    }

    public boolean isAngry() {
        return this.angerValue > 0;
    }

    @Override
    protected String getAmbientSound() {
        return "mob.zombiepig.zpig";
    }

    @Override
    protected String getHurtSound() {
        return "mob.zombiepig.zpighurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.zombiepig.zpigdeath";
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(2 + lootingMultiplier);

        for (int j = 0; j < i; j++) {
            this.dropItem(Items.ROTTEN_FLESH, 1);
        }

        i = this.random.nextInt(2 + lootingMultiplier);

        for (int k = 0; k < i; k++) {
            this.dropItem(Items.GOLD_NUGGET, 1);
        }
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        return false;
    }

    @Override
    protected void dropRareItem() {
        this.dropItem(Items.GOLD_INGOT, 1);
    }

    @Override
    protected void addRandomEquipment(LocalDifficulty difficulty) {
        this.setEquipment(0, new ItemStack(Items.GOLDEN_SWORD));
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        super.initialize(localDifficulty, data);
        this.setType(false);
        return data;
    }

    static class RevengeGoal extends net.minecraft.entity.ai.goal.RevengeGoal {
        public RevengeGoal(ZombiePigmanEntity pigman) {
            super(pigman, true);
        }

        @Override
        protected void setMobEntityTarget(PathFinderMobEntity mob, LivingEntity target) {
            super.setMobEntityTarget(mob, target);
            if (mob instanceof ZombiePigmanEntity) {
                ((ZombiePigmanEntity)mob).getAngryTo(target);
            }
        }
    }

    static class TargetPlayerGoal extends ActiveTargetGoal<PlayerEntity> {
        public TargetPlayerGoal(ZombiePigmanEntity pigman) {
            super(pigman, PlayerEntity.class, true);
        }

        @Override
        public boolean canStart() {
            return ((ZombiePigmanEntity)this.mob).isAngry() && super.canStart();
        }
    }
}
