package net.minecraft.world.explosion;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.PrimedTntEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class Explosion {
    private final boolean createFire;
    private final boolean destructive;
    private final Random random = new Random();
    private final World world;
    private final double x;
    private final double y;
    private final double z;
    private final Entity source;
    private final float power;
    private final List<BlockPos> damagedBlocks = Lists.newArrayList();
    private final Map<PlayerEntity, Vec3d> damagedPlayers = Maps.newHashMap();

    public Explosion(World world, Entity source, double x, double y, double z, float power, List<BlockPos> damagedBlocks) {
        this(world, source, x, y, z, power, false, true, damagedBlocks);
    }

    public Explosion(
        World world, Entity source, double x, double y, double z, float power, boolean createFire, boolean destructive, List<BlockPos> damagedBlocks
    ) {
        this(world, source, x, y, z, power, createFire, destructive);
        this.damagedBlocks.addAll(damagedBlocks);
    }

    public Explosion(World world, Entity source, double x, double y, double z, float power, boolean createFire, boolean destructive) {
        this.world = world;
        this.source = source;
        this.power = power;
        this.x = x;
        this.y = y;
        this.z = z;
        this.createFire = createFire;
        this.destructive = destructive;
    }

    public void damageEntities() {
        Set<BlockPos> set = Sets.newHashSet();
        int i = 16;

        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                for (int l = 0; l < 16; l++) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d0 = j / 15.0F * 2.0F - 1.0F;
                        double d1 = k / 15.0F * 2.0F - 1.0F;
                        double d2 = l / 15.0F * 2.0F - 1.0F;
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = this.power * (0.7F + this.world.random.nextFloat() * 0.6F);
                        double d4 = this.x;
                        double d6 = this.y;
                        double d8 = this.z;
                        float f1 = 0.3F;

                        while (f > 0.0F) {
                            BlockPos blockpos = new BlockPos(d4, d6, d8);
                            BlockState blockstate = this.world.getBlockState(blockpos);
                            if (blockstate.getBlock().getMaterial() != Material.AIR) {
                                float f2 = this.source != null
                                    ? this.source.getBlastResistance(this, this.world, blockpos, blockstate)
                                    : blockstate.getBlock().getBlastResistance(null);
                                f -= (f2 + 0.3F) * 0.3F;
                            }

                            if (f > 0.0F && (this.source == null || this.source.canExplodeBlock(this, this.world, blockpos, blockstate, f))) {
                                set.add(blockpos);
                            }

                            d4 += d0 * 0.3F;
                            d6 += d1 * 0.3F;
                            d8 += d2 * 0.3F;
                            f -= 0.22500001F;
                        }
                    }
                }
            }
        }

        this.damagedBlocks.addAll(set);
        float f3 = this.power * 2.0F;
        int k1 = MathHelper.floor(this.x - f3 - 1.0);
        int l1 = MathHelper.floor(this.x + f3 + 1.0);
        int i2 = MathHelper.floor(this.y - f3 - 1.0);
        int i1 = MathHelper.floor(this.y + f3 + 1.0);
        int j2 = MathHelper.floor(this.z - f3 - 1.0);
        int j1 = MathHelper.floor(this.z + f3 + 1.0);
        List<Entity> list = this.world.getEntities(this.source, new Box(k1, i2, j2, l1, i1, j1));
        Vec3d vec3d = new Vec3d(this.x, this.y, this.z);

        for (int k2 = 0; k2 < list.size(); k2++) {
            Entity entity = list.get(k2);
            if (!entity.isImmuneToExplosions()) {
                double d12 = entity.distanceTo(this.x, this.y, this.z) / f3;
                if (d12 <= 1.0) {
                    double d5 = entity.x - this.x;
                    double d7 = entity.y + entity.getEyeHeight() - this.y;
                    double d9 = entity.z - this.z;
                    double d13 = MathHelper.sqrt(d5 * d5 + d7 * d7 + d9 * d9);
                    if (d13 != 0.0) {
                        d5 /= d13;
                        d7 /= d13;
                        d9 /= d13;
                        double d14 = this.world.getBlockDensity(vec3d, entity.getShape());
                        double d10 = (1.0 - d12) * d14;
                        entity.takeDamage(DamageSource.explosion(this), (int)((d10 * d10 + d10) / 2.0 * 8.0 * f3 + 1.0));
                        double d11 = ProtectionEnchantment.modifyExplosionDamage(entity, d10);
                        entity.velocityX += d5 * d11;
                        entity.velocityY += d7 * d11;
                        entity.velocityZ += d9 * d11;
                        if (entity instanceof PlayerEntity && !((PlayerEntity)entity).abilities.invulnerable) {
                            this.damagedPlayers.put((PlayerEntity)entity, new Vec3d(d5 * d10, d7 * d10, d9 * d10));
                        }
                    }
                }
            }
        }
    }

    public void damageBlocks(boolean createFire) {
        this.world
            .playSound(this.x, this.y, this.z, "random.explode", 4.0F, (1.0F + (this.world.random.nextFloat() - this.world.random.nextFloat()) * 0.2F) * 0.7F);
        if (!(this.power < 2.0F) && this.destructive) {
            this.world.addParticle(ParticleType.EXPLOSION_HUGE, this.x, this.y, this.z, 1.0, 0.0, 0.0);
        } else {
            this.world.addParticle(ParticleType.EXPLOSION_LARGE, this.x, this.y, this.z, 1.0, 0.0, 0.0);
        }

        if (this.destructive) {
            for (BlockPos blockpos : this.damagedBlocks) {
                Block block = this.world.getBlockState(blockpos).getBlock();
                if (createFire) {
                    double d0 = blockpos.getX() + this.world.random.nextFloat();
                    double d1 = blockpos.getY() + this.world.random.nextFloat();
                    double d2 = blockpos.getZ() + this.world.random.nextFloat();
                    double d3 = d0 - this.x;
                    double d4 = d1 - this.y;
                    double d5 = d2 - this.z;
                    double d6 = MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
                    d3 /= d6;
                    d4 /= d6;
                    d5 /= d6;
                    double d7 = 0.5 / (d6 / this.power + 0.1);
                    d7 *= this.world.random.nextFloat() * this.world.random.nextFloat() + 0.3F;
                    d3 *= d7;
                    d4 *= d7;
                    d5 *= d7;
                    this.world
                        .addParticle(ParticleType.EXPLOSION_NORMAL, (d0 + this.x * 1.0) / 2.0, (d1 + this.y * 1.0) / 2.0, (d2 + this.z * 1.0) / 2.0, d3, d4, d5);
                    this.world.addParticle(ParticleType.SMOKE_NORMAL, d0, d1, d2, d3, d4, d5);
                }

                if (block.getMaterial() != Material.AIR) {
                    if (block.shouldDropItemsOnExplosion(this)) {
                        block.dropItems(this.world, blockpos, this.world.getBlockState(blockpos), 1.0F / this.power, 0);
                    }

                    this.world.setBlockState(blockpos, Blocks.AIR.defaultState(), 3);
                    block.onExploded(this.world, blockpos, this);
                }
            }
        }

        if (this.createFire) {
            for (BlockPos blockpos1 : this.damagedBlocks) {
                if (this.world.getBlockState(blockpos1).getBlock().getMaterial() == Material.AIR
                    && this.world.getBlockState(blockpos1.down()).getBlock().isOpaque()
                    && this.random.nextInt(3) == 0) {
                    this.world.setBlockState(blockpos1, Blocks.FIRE.defaultState());
                }
            }
        }
    }

    public Map<PlayerEntity, Vec3d> getDamagedPlayers() {
        return this.damagedPlayers;
    }

    public LivingEntity getSource() {
        if (this.source == null) {
            return null;
        } else if (this.source instanceof PrimedTntEntity) {
            return ((PrimedTntEntity)this.source).getIgniter();
        } else {
            return this.source instanceof LivingEntity ? (LivingEntity)this.source : null;
        }
    }

    public void clearDamagedBlocks() {
        this.damagedBlocks.clear();
    }

    public List<BlockPos> getDamagedBlocks() {
        return this.damagedBlocks;
    }
}
