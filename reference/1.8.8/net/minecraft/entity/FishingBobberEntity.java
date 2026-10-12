package net.minecraft.entity;

import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.DyeColor;
import net.minecraft.item.FishItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.WeightedPicker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class FishingBobberEntity extends Entity {
    private static final List<FishingLootEntry> JUNK_LOOT = Arrays.asList(
        new FishingLootEntry(new ItemStack(Items.LEATHER_BOOTS), 10).setDamage(0.9F),
        new FishingLootEntry(new ItemStack(Items.LEATHER), 10),
        new FishingLootEntry(new ItemStack(Items.BONE), 10),
        new FishingLootEntry(new ItemStack(Items.POTION), 10),
        new FishingLootEntry(new ItemStack(Items.STRING), 5),
        new FishingLootEntry(new ItemStack(Items.FISHING_ROD), 2).setDamage(0.9F),
        new FishingLootEntry(new ItemStack(Items.BOWL), 10),
        new FishingLootEntry(new ItemStack(Items.STICK), 5),
        new FishingLootEntry(new ItemStack(Items.DYE, 10, DyeColor.BLACK.getMetadata()), 1),
        new FishingLootEntry(new ItemStack(Blocks.TRIPWIRE_HOOK), 10),
        new FishingLootEntry(new ItemStack(Items.ROTTEN_FLESH), 10)
    );
    private static final List<FishingLootEntry> TREASURE_LOOT = Arrays.asList(
        new FishingLootEntry(new ItemStack(Blocks.LILY_PAD), 1),
        new FishingLootEntry(new ItemStack(Items.NAME_TAG), 1),
        new FishingLootEntry(new ItemStack(Items.SADDLE), 1),
        new FishingLootEntry(new ItemStack(Items.BOW), 1).setDamage(0.25F).setEnchantable(),
        new FishingLootEntry(new ItemStack(Items.FISHING_ROD), 1).setDamage(0.25F).setEnchantable(),
        new FishingLootEntry(new ItemStack(Items.BOOK), 1).setEnchantable()
    );
    private static final List<FishingLootEntry> FISH_LOOT = Arrays.asList(
        new FishingLootEntry(new ItemStack(Items.FISH, 1, FishItem.Type.COD.getId()), 60),
        new FishingLootEntry(new ItemStack(Items.FISH, 1, FishItem.Type.SALMON.getId()), 25),
        new FishingLootEntry(new ItemStack(Items.FISH, 1, FishItem.Type.CLOWNFISH.getId()), 2),
        new FishingLootEntry(new ItemStack(Items.FISH, 1, FishItem.Type.PUFFERFISH.getId()), 13)
    );
    private int blockX = -1;
    private int blockY = -1;
    private int blockZ = -1;
    private Block inBlock;
    private boolean inGround;
    public int shake;
    public PlayerEntity thrower;
    private int inBlockTicks;
    private int inAirTicks;
    private int selfHitTimer;
    private int catchTimer;
    private int fishTravelTimer;
    private float fishAngle;
    public Entity hookedEntity;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    private double lerpVelocityX;
    private double lerpVelocityY;
    private double lerpVelocityZ;

    public static List<FishingLootEntry> getFishLoot() {
        return FISH_LOOT;
    }

    public FishingBobberEntity(World world) {
        super(world);
        this.setSize(0.25F, 0.25F);
        this.ignoreCameraFrustum = true;
    }

    public FishingBobberEntity(World world, double x, double y, double z, PlayerEntity thrower) {
        this(world);
        this.setPosition(x, y, z);
        this.ignoreCameraFrustum = true;
        this.thrower = thrower;
        thrower.fishingBobber = this;
    }

    public FishingBobberEntity(World world, PlayerEntity thrower) {
        super(world);
        this.ignoreCameraFrustum = true;
        this.thrower = thrower;
        this.thrower.fishingBobber = this;
        this.setSize(0.25F, 0.25F);
        this.setPositionAndAngles(thrower.x, thrower.y + thrower.getEyeHeight(), thrower.z, thrower.yaw, thrower.pitch);
        this.x = this.x - MathHelper.cos(this.yaw / 180.0F * (float) Math.PI) * 0.16F;
        this.y -= 0.1F;
        this.z = this.z - MathHelper.sin(this.yaw / 180.0F * (float) Math.PI) * 0.16F;
        this.setPosition(this.x, this.y, this.z);
        float f = 0.4F;
        this.velocityX = -MathHelper.sin(this.yaw / 180.0F * (float) Math.PI) * MathHelper.cos(this.pitch / 180.0F * (float) Math.PI) * f;
        this.velocityZ = MathHelper.cos(this.yaw / 180.0F * (float) Math.PI) * MathHelper.cos(this.pitch / 180.0F * (float) Math.PI) * f;
        this.velocityY = -MathHelper.sin(this.pitch / 180.0F * (float) Math.PI) * f;
        this.thrown(this.velocityX, this.velocityY, this.velocityZ, 1.5F, 1.0F);
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    public boolean shouldRender(double squaredDistanceToCamera) {
        double d0 = this.getShape().getAverageSideLength() * 4.0;
        if (Double.isNaN(d0)) {
            d0 = 4.0;
        }

        d0 *= 64.0;
        return squaredDistanceToCamera < d0 * d0;
    }

    public void thrown(double velocityX, double velocityY, double velocityZ, float scale, float min) {
        float f = MathHelper.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        velocityX /= f;
        velocityY /= f;
        velocityZ /= f;
        velocityX += this.random.nextGaussian() * 0.0075F * min;
        velocityY += this.random.nextGaussian() * 0.0075F * min;
        velocityZ += this.random.nextGaussian() * 0.0075F * min;
        velocityX *= scale;
        velocityY *= scale;
        velocityZ *= scale;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        float f1 = MathHelper.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        this.lastYaw = this.yaw = (float)(MathHelper.fastAtan2(velocityX, velocityZ) * 180.0 / (float) Math.PI);
        this.lastPitch = this.pitch = (float)(MathHelper.fastAtan2(velocityY, f1) * 180.0 / (float) Math.PI);
        this.inBlockTicks = 0;
    }

    @Override
    public void lerpPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
        this.lerpSteps = steps;
        this.velocityX = this.lerpVelocityX;
        this.velocityY = this.lerpVelocityY;
        this.velocityZ = this.lerpVelocityZ;
    }

    @Override
    public void lerpVelocity(double velocityX, double velocityY, double velocityZ) {
        this.lerpVelocityX = this.velocityX = velocityX;
        this.lerpVelocityY = this.velocityY = velocityY;
        this.lerpVelocityZ = this.velocityZ = velocityZ;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.lerpSteps > 0) {
            double d7 = this.x + (this.lerpX - this.x) / this.lerpSteps;
            double d8 = this.y + (this.lerpY - this.y) / this.lerpSteps;
            double d9 = this.z + (this.lerpZ - this.z) / this.lerpSteps;
            double d1 = MathHelper.wrapDegrees(this.lerpYaw - this.yaw);
            this.yaw = (float)(this.yaw + d1 / this.lerpSteps);
            this.pitch = (float)(this.pitch + (this.lerpPitch - this.pitch) / this.lerpSteps);
            this.lerpSteps--;
            this.setPosition(d7, d8, d9);
            this.setRotation(this.yaw, this.pitch);
        } else {
            if (!this.world.isClient) {
                ItemStack itemstack = this.thrower.getItemInHand();
                if (this.thrower.removed
                    || !this.thrower.isAlive()
                    || itemstack == null
                    || itemstack.getItem() != Items.FISHING_ROD
                    || this.squaredDistanceTo(this.thrower) > 1024.0) {
                    this.remove();
                    this.thrower.fishingBobber = null;
                    return;
                }

                if (this.hookedEntity != null) {
                    if (!this.hookedEntity.removed) {
                        this.x = this.hookedEntity.x;
                        double d17 = this.hookedEntity.height;
                        this.y = this.hookedEntity.getShape().minY + d17 * 0.8;
                        this.z = this.hookedEntity.z;
                        return;
                    }

                    this.hookedEntity = null;
                }
            }

            if (this.shake > 0) {
                this.shake--;
            }

            if (this.inGround) {
                if (this.world.getBlockState(new BlockPos(this.blockX, this.blockY, this.blockZ)).getBlock() == this.inBlock) {
                    this.inBlockTicks++;
                    if (this.inBlockTicks == 1200) {
                        this.remove();
                    }

                    return;
                }

                this.inGround = false;
                this.velocityX = this.velocityX * (this.random.nextFloat() * 0.2F);
                this.velocityY = this.velocityY * (this.random.nextFloat() * 0.2F);
                this.velocityZ = this.velocityZ * (this.random.nextFloat() * 0.2F);
                this.inBlockTicks = 0;
                this.inAirTicks = 0;
            } else {
                this.inAirTicks++;
            }

            Vec3d vec3d1 = new Vec3d(this.x, this.y, this.z);
            Vec3d vec3d = new Vec3d(this.x + this.velocityX, this.y + this.velocityY, this.z + this.velocityZ);
            HitResult hitresult = this.world.rayTrace(vec3d1, vec3d);
            vec3d1 = new Vec3d(this.x, this.y, this.z);
            vec3d = new Vec3d(this.x + this.velocityX, this.y + this.velocityY, this.z + this.velocityZ);
            if (hitresult != null) {
                vec3d = new Vec3d(hitresult.facePos.x, hitresult.facePos.y, hitresult.facePos.z);
            }

            Entity entity = null;
            List<Entity> list = this.world.getEntities(this, this.getShape().expanded(this.velocityX, this.velocityY, this.velocityZ).grown(1.0, 1.0, 1.0));
            double d0 = 0.0;

            for (int i = 0; i < list.size(); i++) {
                Entity entity1 = list.get(i);
                if (entity1.hasCollision() && (entity1 != this.thrower || this.inAirTicks >= 5)) {
                    float f = 0.3F;
                    Box box = entity1.getShape().grown(f, f, f);
                    HitResult hitresult1 = box.clip(vec3d1, vec3d);
                    if (hitresult1 != null) {
                        double d2 = vec3d1.squaredDistanceTo(hitresult1.facePos);
                        if (d2 < d0 || d0 == 0.0) {
                            entity = entity1;
                            d0 = d2;
                        }
                    }
                }
            }

            if (entity != null) {
                hitresult = new HitResult(entity);
            }

            if (hitresult != null) {
                if (hitresult.entity != null) {
                    if (hitresult.entity.takeDamage(DamageSource.thrown(this, this.thrower), 0.0F)) {
                        this.hookedEntity = hitresult.entity;
                    }
                } else {
                    this.inGround = true;
                }
            }

            if (!this.inGround) {
                this.move(this.velocityX, this.velocityY, this.velocityZ);
                float f5 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
                this.yaw = (float)(MathHelper.fastAtan2(this.velocityX, this.velocityZ) * 180.0 / (float) Math.PI);
                this.pitch = (float)(MathHelper.fastAtan2(this.velocityY, f5) * 180.0 / (float) Math.PI);

                while (this.pitch - this.lastPitch < -180.0F) {
                    this.lastPitch -= 360.0F;
                }

                while (this.pitch - this.lastPitch >= 180.0F) {
                    this.lastPitch += 360.0F;
                }

                while (this.yaw - this.lastYaw < -180.0F) {
                    this.lastYaw -= 360.0F;
                }

                while (this.yaw - this.lastYaw >= 180.0F) {
                    this.lastYaw += 360.0F;
                }

                this.pitch = this.lastPitch + (this.pitch - this.lastPitch) * 0.2F;
                this.yaw = this.lastYaw + (this.yaw - this.lastYaw) * 0.2F;
                float f6 = 0.92F;
                if (this.onGround || this.collidingHorizontally) {
                    f6 = 0.5F;
                }

                int j = 5;
                double d10 = 0.0;

                for (int k = 0; k < j; k++) {
                    Box box1 = this.getShape();
                    double d3 = box1.maxY - box1.minY;
                    double d4 = box1.minY + d3 * k / j;
                    double d5 = box1.minY + d3 * (k + 1) / j;
                    Box box2 = new Box(box1.minX, d4, box1.minZ, box1.maxX, d5, box1.maxZ);
                    if (this.world.containsLiquid(box2, Material.WATER)) {
                        d10 += 1.0 / j;
                    }
                }

                if (!this.world.isClient && d10 > 0.0) {
                    ServerWorld serverworld = (ServerWorld)this.world;
                    int l = 1;
                    BlockPos blockpos = new BlockPos(this).up();
                    if (this.random.nextFloat() < 0.25F && this.world.isRaining(blockpos)) {
                        l = 2;
                    }

                    if (this.random.nextFloat() < 0.5F && !this.world.hasSkyAccess(blockpos)) {
                        l--;
                    }

                    if (this.selfHitTimer > 0) {
                        this.selfHitTimer--;
                        if (this.selfHitTimer <= 0) {
                            this.catchTimer = 0;
                            this.fishTravelTimer = 0;
                        }
                    } else if (this.fishTravelTimer > 0) {
                        this.fishTravelTimer -= l;
                        if (this.fishTravelTimer <= 0) {
                            this.velocityY -= 0.2F;
                            this.playSound("random.splash", 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                            float f8 = MathHelper.floor(this.getShape().minY);
                            serverworld.addParticle(
                                ParticleType.WATER_BUBBLE, this.x, f8 + 1.0F, this.z, (int)(1.0F + this.width * 20.0F), this.width, 0.0, this.width, 0.2F
                            );
                            serverworld.addParticle(
                                ParticleType.WATER_WAKE, this.x, f8 + 1.0F, this.z, (int)(1.0F + this.width * 20.0F), this.width, 0.0, this.width, 0.2F
                            );
                            this.selfHitTimer = MathHelper.nextInt(this.random, 10, 30);
                        } else {
                            this.fishAngle = (float)(this.fishAngle + this.random.nextGaussian() * 4.0);
                            float f7 = this.fishAngle * (float) (Math.PI / 180.0);
                            float f10 = MathHelper.sin(f7);
                            float f11 = MathHelper.cos(f7);
                            double d13 = this.x + f10 * this.fishTravelTimer * 0.1F;
                            double d15 = MathHelper.floor(this.getShape().minY) + 1.0F;
                            double d16 = this.z + f11 * this.fishTravelTimer * 0.1F;
                            Block block1 = serverworld.getBlockState(new BlockPos((int)d13, (int)d15 - 1, (int)d16)).getBlock();
                            if (block1 == Blocks.WATER || block1 == Blocks.FLOWING_WATER) {
                                if (this.random.nextFloat() < 0.15F) {
                                    serverworld.addParticle(ParticleType.WATER_BUBBLE, d13, d15 - 0.1F, d16, 1, f10, 0.1, f11, 0.0);
                                }

                                float f3 = f10 * 0.04F;
                                float f4 = f11 * 0.04F;
                                serverworld.addParticle(ParticleType.WATER_WAKE, d13, d15, d16, 0, f4, 0.01, -f3, 1.0);
                                serverworld.addParticle(ParticleType.WATER_WAKE, d13, d15, d16, 0, -f4, 0.01, f3, 1.0);
                            }
                        }
                    } else if (this.catchTimer > 0) {
                        this.catchTimer -= l;
                        float f1 = 0.15F;
                        if (this.catchTimer < 20) {
                            f1 = (float)(f1 + (20 - this.catchTimer) * 0.05);
                        } else if (this.catchTimer < 40) {
                            f1 = (float)(f1 + (40 - this.catchTimer) * 0.02);
                        } else if (this.catchTimer < 60) {
                            f1 = (float)(f1 + (60 - this.catchTimer) * 0.01);
                        }

                        if (this.random.nextFloat() < f1) {
                            float f9 = MathHelper.nextFloat(this.random, 0.0F, 360.0F) * (float) (Math.PI / 180.0);
                            float f2 = MathHelper.nextFloat(this.random, 25.0F, 60.0F);
                            double d12 = this.x + MathHelper.sin(f9) * f2 * 0.1F;
                            double d14 = MathHelper.floor(this.getShape().minY) + 1.0F;
                            double d6 = this.z + MathHelper.cos(f9) * f2 * 0.1F;
                            Block block = serverworld.getBlockState(new BlockPos((int)d12, (int)d14 - 1, (int)d6)).getBlock();
                            if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
                                serverworld.addParticle(ParticleType.WATER_SPLASH, d12, d14, d6, 2 + this.random.nextInt(2), 0.1F, 0.0, 0.1F, 0.0);
                            }
                        }

                        if (this.catchTimer <= 0) {
                            this.fishAngle = MathHelper.nextFloat(this.random, 0.0F, 360.0F);
                            this.fishTravelTimer = MathHelper.nextInt(this.random, 20, 80);
                        }
                    } else {
                        this.catchTimer = MathHelper.nextInt(this.random, 100, 900);
                        this.catchTimer = this.catchTimer - EnchantmentHelper.getLureLevel(this.thrower) * 20 * 5;
                    }

                    if (this.selfHitTimer > 0) {
                        this.velocityY = this.velocityY - this.random.nextFloat() * this.random.nextFloat() * this.random.nextFloat() * 0.2;
                    }
                }

                double d11 = d10 * 2.0 - 1.0;
                this.velocityY += 0.04F * d11;
                if (d10 > 0.0) {
                    f6 = (float)(f6 * 0.9);
                    this.velocityY *= 0.8;
                }

                this.velocityX *= f6;
                this.velocityY *= f6;
                this.velocityZ *= f6;
                this.setPosition(this.x, this.y, this.z);
            }
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putShort("xTile", (short)this.blockX);
        nbt.putShort("yTile", (short)this.blockY);
        nbt.putShort("zTile", (short)this.blockZ);
        Identifier identifier = Block.REGISTRY.getKey(this.inBlock);
        nbt.putString("inTile", identifier == null ? "" : identifier.toString());
        nbt.putByte("shake", (byte)this.shake);
        nbt.putByte("inGround", (byte)(this.inGround ? 1 : 0));
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.blockX = nbt.getShort("xTile");
        this.blockY = nbt.getShort("yTile");
        this.blockZ = nbt.getShort("zTile");
        if (nbt.contains("inTile", 8)) {
            this.inBlock = Block.byKey(nbt.getString("inTile"));
        } else {
            this.inBlock = Block.byId(nbt.getByte("inTile") & 255);
        }

        this.shake = nbt.getByte("shake") & 255;
        this.inGround = nbt.getByte("inGround") == 1;
    }

    public int retrieve() {
        if (this.world.isClient) {
            return 0;
        }

        int i = 0;
        if (this.hookedEntity != null) {
            double d0 = this.thrower.x - this.x;
            double d2 = this.thrower.y - this.y;
            double d4 = this.thrower.z - this.z;
            double d6 = MathHelper.sqrt(d0 * d0 + d2 * d2 + d4 * d4);
            double d8 = 0.1;
            this.hookedEntity.velocityX += d0 * d8;
            this.hookedEntity.velocityY = this.hookedEntity.velocityY + (d2 * d8 + MathHelper.sqrt(d6) * 0.08);
            this.hookedEntity.velocityZ += d4 * d8;
            i = 3;
        } else if (this.selfHitTimer > 0) {
            ItemEntity itementity = new ItemEntity(this.world, this.x, this.y, this.z, this.getResult());
            double d1 = this.thrower.x - this.x;
            double d3 = this.thrower.y - this.y;
            double d5 = this.thrower.z - this.z;
            double d7 = MathHelper.sqrt(d1 * d1 + d3 * d3 + d5 * d5);
            double d9 = 0.1;
            itementity.velocityX = d1 * d9;
            itementity.velocityY = d3 * d9 + MathHelper.sqrt(d7) * 0.08;
            itementity.velocityZ = d5 * d9;
            this.world.addEntity(itementity);
            this.thrower
                .world
                .addEntity(new ExperienceOrbEntity(this.thrower.world, this.thrower.x, this.thrower.y + 0.5, this.thrower.z + 0.5, this.random.nextInt(6) + 1));
            i = 1;
        }

        if (this.inGround) {
            i = 2;
        }

        this.remove();
        this.thrower.fishingBobber = null;
        return i;
    }

    private ItemStack getResult() {
        float f = this.world.random.nextFloat();
        int i = EnchantmentHelper.getLuckOfTheSeaLevel(this.thrower);
        int j = EnchantmentHelper.getLureLevel(this.thrower);
        float f1 = 0.1F - i * 0.025F - j * 0.01F;
        float f2 = 0.05F + i * 0.01F - j * 0.01F;
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        f2 = MathHelper.clamp(f2, 0.0F, 1.0F);
        if (f < f1) {
            this.thrower.incrementStat(Stats.JUNK_FISHED);
            return WeightedPicker.pick(this.random, JUNK_LOOT).getItem(this.random);
        } else {
            f -= f1;
            if (f < f2) {
                this.thrower.incrementStat(Stats.TREASURE_FISHED);
                return WeightedPicker.pick(this.random, TREASURE_LOOT).getItem(this.random);
            } else {
                f -= f2;
                this.thrower.incrementStat(Stats.FISH_CAUGHT);
                return WeightedPicker.pick(this.random, FISH_LOOT).getItem(this.random);
            }
        }
    }

    @Override
    public void remove() {
        super.remove();
        if (this.thrower != null) {
            this.thrower.fishingBobber = null;
        }
    }
}
