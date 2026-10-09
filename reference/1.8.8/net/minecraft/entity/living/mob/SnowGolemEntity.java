package net.minecraft.entity.living.mob;

import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.mob.monster.RangedAttackMob;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class SnowGolemEntity extends GolemEntity implements RangedAttackMob {
    public SnowGolemEntity(World world) {
        super(world);
        this.setSize(0.7F, 1.9F);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.goalSelector.addGoal(1, new ProjectileAttackGoal(this, 1.25, 20, 10.0F));
        this.goalSelector.addGoal(2, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(3, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(4, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new ActiveTargetGoal<>(this, MobEntity.class, 10, true, false, Monster.FILTER));
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(4.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.2F);
    }

    @Override
    public void mobTick() {
        super.mobTick();
        if (!this.world.isClient) {
            int i = MathHelper.floor(this.x);
            int j = MathHelper.floor(this.y);
            int k = MathHelper.floor(this.z);
            if (this.isInWaterOrRain()) {
                this.takeDamage(DamageSource.DROWN, 1.0F);
            }

            if (this.world.getBiome(new BlockPos(i, 0, k)).getTemperature(new BlockPos(i, j, k)) > 1.0F) {
                this.takeDamage(DamageSource.ON_FIRE, 1.0F);
            }

            for (int l = 0; l < 4; l++) {
                i = MathHelper.floor(this.x + (l % 2 * 2 - 1) * 0.25F);
                j = MathHelper.floor(this.y);
                k = MathHelper.floor(this.z + (l / 2 % 2 * 2 - 1) * 0.25F);
                BlockPos blockpos = new BlockPos(i, j, k);
                if (this.world.getBlockState(blockpos).getBlock().getMaterial() == Material.AIR
                    && this.world.getBiome(new BlockPos(i, 0, k)).getTemperature(blockpos) < 0.8F
                    && Blocks.SNOW_LAYER.canBePlaced(this.world, blockpos)) {
                    this.world.setBlockState(blockpos, Blocks.SNOW_LAYER.defaultState());
                }
            }
        }
    }

    @Override
    protected Item getDropItem() {
        return Items.SNOWBALL;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(16);

        for (int j = 0; j < i; j++) {
            this.dropItem(Items.SNOWBALL, 1);
        }
    }

    @Override
    public void doRangedAttack(LivingEntity target, float range) {
        SnowballEntity snowballentity = new SnowballEntity(this.world, this);
        double d0 = target.y + target.getEyeHeight() - 1.1F;
        double d1 = target.x - this.x;
        double d2 = d0 - snowballentity.y;
        double d3 = target.z - this.z;
        float f = MathHelper.sqrt(d1 * d1 + d3 * d3) * 0.2F;
        snowballentity.dispense(d1, d2 + f, d3, 1.6F, 12.0F);
        this.playSound("random.bow", 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.world.addEntity(snowballentity);
    }

    @Override
    public float getEyeHeight() {
        return 1.7F;
    }
}
