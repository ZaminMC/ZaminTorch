package net.minecraft.entity;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.block.state.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.SyncedData;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

public abstract class Entity implements CommandSource {
    private static final Box INITIAL_SHAPE = new Box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    private static int nextNetworkId;
    /**
     * A number that uniquely identifies this entity. It is used for server-client communication.
     */
    private int networkId;
    public double viewDistanceScaling;
    public boolean blocksBuilding;
    public Entity rider;
    public Entity vehicle;
    public boolean teleporting;
    public World world;
    public double lastX;
    public double lastY;
    public double lastZ;
    public double x;
    public double y;
    public double z;
    public double velocityX;
    public double velocityY;
    public double velocityZ;
    public float yaw;
    public float pitch;
    public float lastYaw;
    public float lastPitch;
    private Box shape;
    public boolean onGround;
    public boolean collidingHorizontally;
    public boolean collidingVertically;
    public boolean colliding;
    public boolean damaged;
    protected boolean inCobweb;
    private boolean outsideWorldBorder;
    public boolean removed;
    public float width;
    public float height;
    public float lastWalkDistance;
    public float walkDistance;
    public float distanceMoved;
    public float fallDistance;
    private int blocksWalkedOn;
    public double prevX;
    public double prevY;
    public double prevZ;
    public float stepHeight;
    public boolean noClip;
    public float pushSpeedReduction;
    protected Random random;
    public int ticks;
    /**
     * The number of ticks an entity can stay in a fire and step out without staying on fire.
     */
    public int safeOnFireTime;
    /**
     * The number of ticks left for which this entity will be on fire.
     */
    private int onFireTimer;
    protected boolean inWater;
    public int invulnerableTimer;
    /**
     * True if the entity is in the first tick of its existence.
     */
    protected boolean firstTick;
    protected boolean immuneToFire;
    protected SyncedData syncedData;
    private double ridingEntityPitchDelta;
    private double ridingEntityYawDelta;
    public boolean inChunk;
    public int chunkX;
    public int chunkY;
    public int chunkZ;
    public int lastKnownX;
    public int lastKnownY;
    public int lastKnownZ;
    public boolean ignoreCameraFrustum;
    public boolean velocityDirty;
    public int portalCooldown;
    protected boolean inPortal;
    protected int portalTime;
    public int dimension;
    protected BlockPos lastPortalPos;
    protected Vec3d lastPortalOffset;
    protected Direction lastPortalFacing;
    private boolean invulnerable;
    protected UUID uuid;
    private final CommandResults commandResults;

    public int getNetworkId() {
        return this.networkId;
    }

    public void setNetworkId(int id) {
        this.networkId = id;
    }

    public void discard() {
        this.remove();
    }

    public Entity(World world) {
        this.networkId = nextNetworkId++;
        this.viewDistanceScaling = 1.0;
        this.shape = INITIAL_SHAPE;
        this.width = 0.6F;
        this.height = 1.8F;
        this.blocksWalkedOn = 1;
        this.random = new Random();
        this.safeOnFireTime = 1;
        this.firstTick = true;
        this.uuid = MathHelper.nextUuid(this.random);
        this.commandResults = new CommandResults();
        this.world = world;
        this.setPosition(0.0, 0.0, 0.0);
        if (world != null) {
            this.dimension = world.dimension.getId();
        }

        this.syncedData = new SyncedData(this);
        this.syncedData.register(0, (byte)0);
        this.syncedData.register(1, (short)300);
        this.syncedData.register(3, (byte)0);
        this.syncedData.register(2, "");
        this.syncedData.register(4, (byte)0);
        this.registerSyncedData();
    }

    protected abstract void registerSyncedData();

    public SyncedData getSyncedData() {
        return this.syncedData;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Entity && ((Entity)object).networkId == this.networkId;
    }

    @Override
    public int hashCode() {
        return this.networkId;
    }

    protected void resetPos() {
        if (this.world != null) {
            while (this.y > 0.0 && this.y < 256.0) {
                this.setPosition(this.x, this.y, this.z);
                if (this.world.getCollisions(this, this.getShape()).isEmpty()) {
                    break;
                }

                this.y++;
            }

            this.velocityX = this.velocityY = this.velocityZ = 0.0;
            this.pitch = 0.0F;
        }
    }

    public void remove() {
        this.removed = true;
    }

    protected void setSize(float width, float height) {
        if (width != this.width || height != this.height) {
            float f = this.width;
            this.width = width;
            this.height = height;
            this.setShape(
                new Box(
                    this.getShape().minX,
                    this.getShape().minY,
                    this.getShape().minZ,
                    this.getShape().minX + this.width,
                    this.getShape().minY + this.height,
                    this.getShape().minZ + this.width
                )
            );
            if (this.width > f && !this.firstTick && !this.world.isClient) {
                this.move(f - this.width, 0.0, f - this.width);
            }
        }
    }

    protected void setRotation(float yaw, float pitch) {
        this.yaw = yaw % 360.0F;
        this.pitch = pitch % 360.0F;
    }

    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float f = this.width / 2.0F;
        float f1 = this.height;
        this.setShape(new Box(x - f, y, z - f, x + f, y + f1, z + f));
    }

    /**
     * Called by @see GameRenderer#render(float, long) every frame when not spectating an entity.
     * This is only ever called on the local player entity.
     * This causes the local player entity's prevPitch/Yaw to always be the same as its pitch/yaw.
     */
    public void updateLocalPlayerCamera(float yaw, float pitch) {
        float f = this.pitch;
        float f1 = this.yaw;
        this.yaw = (float)(this.yaw + yaw * 0.15);
        this.pitch = (float)(this.pitch - pitch * 0.15);
        this.pitch = MathHelper.clamp(this.pitch, -90.0F, 90.0F);
        this.lastPitch = this.lastPitch + (this.pitch - f);
        this.lastYaw = this.lastYaw + (this.yaw - f1);
    }

    public void tick() {
        this.baseTick();
    }

    public void baseTick() {
        this.world.profiler.push("entityBaseTick");
        if (this.vehicle != null && this.vehicle.removed) {
            this.vehicle = null;
        }

        this.lastWalkDistance = this.walkDistance;
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.lastPitch = this.pitch;
        this.lastYaw = this.yaw;
        if (!this.world.isClient && this.world instanceof ServerWorld) {
            this.world.profiler.push("portal");
            MinecraftServer minecraftserver = ((ServerWorld)this.world).getServer();
            int i = this.getMaxNetherPortalTime();
            if (this.inPortal) {
                if (minecraftserver.isNetherAllowed()) {
                    if (this.vehicle == null && this.portalTime++ >= i) {
                        this.portalTime = i;
                        this.portalCooldown = this.getPortalCooldown();
                        int j;
                        if (this.world.dimension.getId() == -1) {
                            j = 0;
                        } else {
                            j = -1;
                        }

                        this.changeDimension(j);
                    }

                    this.inPortal = false;
                }
            } else {
                if (this.portalTime > 0) {
                    this.portalTime -= 4;
                }

                if (this.portalTime < 0) {
                    this.portalTime = 0;
                }
            }

            if (this.portalCooldown > 0) {
                this.portalCooldown--;
            }

            this.world.profiler.pop();
        }

        this.tickSprintingEffect();
        this.checkWaterCollisions();
        if (this.world.isClient) {
            this.onFireTimer = 0;
        } else if (this.onFireTimer > 0) {
            if (this.immuneToFire) {
                this.onFireTimer -= 4;
                if (this.onFireTimer < 0) {
                    this.onFireTimer = 0;
                }
            } else {
                if (this.onFireTimer % 20 == 0) {
                    this.takeDamage(DamageSource.ON_FIRE, 1.0F);
                }

                this.onFireTimer--;
            }
        }

        if (this.isInLava()) {
            this.takeLavaDamage();
            this.fallDistance *= 0.5F;
        }

        if (this.y < -64.0) {
            this.voidTick();
        }

        if (!this.world.isClient) {
            this.setFlag(0, this.onFireTimer > 0);
        }

        this.firstTick = false;
        this.world.profiler.pop();
    }

    public int getMaxNetherPortalTime() {
        return 0;
    }

    protected void takeLavaDamage() {
        if (!this.immuneToFire) {
            this.takeDamage(DamageSource.LAVA, 4.0F);
            this.setOnFireFor(15);
        }
    }

    public void setOnFireFor(int seconds) {
        int i = seconds * 20;
        i = ProtectionEnchantment.modifyOnFireTimer(this, i);
        if (this.onFireTimer < i) {
            this.onFireTimer = i;
        }
    }

    public void extinguish() {
        this.onFireTimer = 0;
    }

    protected void voidTick() {
        this.remove();
    }

    public boolean canMove(double dx, double dy, double dz) {
        Box box = this.getShape().moved(dx, dy, dz);
        return this.doesNotCollide(box);
    }

    private boolean doesNotCollide(Box box) {
        return this.world.getCollisions(this, box).isEmpty() && !this.world.containsLiquid(box);
    }

    public void move(double dx, double dy, double dz) {
        if (this.noClip) {
            this.setShape(this.getShape().moved(dx, dy, dz));
            this.setPositionFromShape();
        } else {
            this.world.profiler.push("move");
            double d0 = this.x;
            double d1 = this.y;
            double d2 = this.z;
            if (this.inCobweb) {
                this.inCobweb = false;
                dx *= 0.25;
                dy *= 0.05F;
                dz *= 0.25;
                this.velocityX = 0.0;
                this.velocityY = 0.0;
                this.velocityZ = 0.0;
            }

            double d3 = dx;
            double d4 = dy;
            double d5 = dz;
            boolean flag = this.onGround && this.isSneaking() && this instanceof PlayerEntity;
            if (flag) {
                double d6 = 0.05;

                while (dx != 0.0 && this.world.getCollisions(this, this.getShape().moved(dx, -1.0, 0.0)).isEmpty()) {
                    if (dx < d6 && dx >= -d6) {
                        dx = 0.0;
                    } else if (dx > 0.0) {
                        dx -= d6;
                    } else {
                        dx += d6;
                    }

                    d3 = dx;
                }

                for (; dz != 0.0 && this.world.getCollisions(this, this.getShape().moved(0.0, -1.0, dz)).isEmpty(); d5 = dz) {
                    if (dz < d6 && dz >= -d6) {
                        dz = 0.0;
                    } else if (dz > 0.0) {
                        dz -= d6;
                    } else {
                        dz += d6;
                    }
                }

                for (; dx != 0.0 && dz != 0.0 && this.world.getCollisions(this, this.getShape().moved(dx, -1.0, dz)).isEmpty(); d5 = dz) {
                    if (dx < d6 && dx >= -d6) {
                        dx = 0.0;
                    } else if (dx > 0.0) {
                        dx -= d6;
                    } else {
                        dx += d6;
                    }

                    d3 = dx;
                    if (dz < d6 && dz >= -d6) {
                        dz = 0.0;
                    } else if (dz > 0.0) {
                        dz -= d6;
                    } else {
                        dz += d6;
                    }
                }
            }

            List<Box> list1 = this.world.getCollisions(this, this.getShape().expanded(dx, dy, dz));
            Box box = this.getShape();

            for (Box box1 : list1) {
                dy = box1.intersectY(this.getShape(), dy);
            }

            this.setShape(this.getShape().moved(0.0, dy, 0.0));
            boolean flag1 = this.onGround || d4 != dy && d4 < 0.0;

            for (Box box2 : list1) {
                dx = box2.intersectX(this.getShape(), dx);
            }

            this.setShape(this.getShape().moved(dx, 0.0, 0.0));

            for (Box box13 : list1) {
                dz = box13.intersectZ(this.getShape(), dz);
            }

            this.setShape(this.getShape().moved(0.0, 0.0, dz));
            if (this.stepHeight > 0.0F && flag1 && (d3 != dx || d5 != dz)) {
                double d11 = dx;
                double d7 = dy;
                double d8 = dz;
                Box box3 = this.getShape();
                this.setShape(box);
                dx = d3;
                dy = this.stepHeight;
                dz = d5;
                List<Box> list = this.world.getCollisions(this, this.getShape().expanded(dx, dy, dz));
                Box box4 = this.getShape();
                Box box5 = box4.expanded(dx, 0.0, dz);
                double d9 = dy;

                for (Box box6 : list) {
                    d9 = box6.intersectY(box5, d9);
                }

                box4 = box4.moved(0.0, d9, 0.0);
                double d15 = dx;

                for (Box box7 : list) {
                    d15 = box7.intersectX(box4, d15);
                }

                box4 = box4.moved(d15, 0.0, 0.0);
                double d16 = dz;

                for (Box box8 : list) {
                    d16 = box8.intersectZ(box4, d16);
                }

                box4 = box4.moved(0.0, 0.0, d16);
                Box box14 = this.getShape();
                double d17 = dy;

                for (Box box9 : list) {
                    d17 = box9.intersectY(box14, d17);
                }

                box14 = box14.moved(0.0, d17, 0.0);
                double d18 = dx;

                for (Box box10 : list) {
                    d18 = box10.intersectX(box14, d18);
                }

                box14 = box14.moved(d18, 0.0, 0.0);
                double d19 = dz;

                for (Box box11 : list) {
                    d19 = box11.intersectZ(box14, d19);
                }

                box14 = box14.moved(0.0, 0.0, d19);
                double d20 = d15 * d15 + d16 * d16;
                double d10 = d18 * d18 + d19 * d19;
                if (d20 > d10) {
                    dx = d15;
                    dz = d16;
                    dy = -d9;
                    this.setShape(box4);
                } else {
                    dx = d18;
                    dz = d19;
                    dy = -d17;
                    this.setShape(box14);
                }

                for (Box box12 : list) {
                    dy = box12.intersectY(this.getShape(), dy);
                }

                this.setShape(this.getShape().moved(0.0, dy, 0.0));
                if (d11 * d11 + d8 * d8 >= dx * dx + dz * dz) {
                    dx = d11;
                    dy = d7;
                    dz = d8;
                    this.setShape(box3);
                }
            }

            this.world.profiler.pop();
            this.world.profiler.push("rest");
            this.setPositionFromShape();
            this.collidingHorizontally = d3 != dx || d5 != dz;
            this.collidingVertically = d4 != dy;
            this.onGround = this.collidingVertically && d4 < 0.0;
            this.colliding = this.collidingHorizontally || this.collidingVertically;
            int i = MathHelper.floor(this.x);
            int j = MathHelper.floor(this.y - 0.2F);
            int k = MathHelper.floor(this.z);
            BlockPos blockpos = new BlockPos(i, j, k);
            Block block1 = this.world.getBlockState(blockpos).getBlock();
            if (block1.getMaterial() == Material.AIR) {
                Block block = this.world.getBlockState(blockpos.down()).getBlock();
                if (block instanceof FenceBlock || block instanceof WallBlock || block instanceof FenceGateBlock) {
                    block1 = block;
                    blockpos = blockpos.down();
                }
            }

            this.checkFallDamage(dy, this.onGround, block1, blockpos);
            if (d3 != dx) {
                this.velocityX = 0.0;
            }

            if (d5 != dz) {
                this.velocityZ = 0.0;
            }

            if (d4 != dy) {
                block1.beforeCollision(this.world, this);
            }

            if (this.makesSteps() && !flag && this.vehicle == null) {
                double d12 = this.x - d0;
                double d13 = this.y - d1;
                double d14 = this.z - d2;
                if (block1 != Blocks.LADDER) {
                    d13 = 0.0;
                }

                if (block1 != null && this.onGround) {
                    block1.onSteppedOn(this.world, blockpos, this);
                }

                this.walkDistance = (float)(this.walkDistance + MathHelper.sqrt(d12 * d12 + d14 * d14) * 0.6);
                this.distanceMoved = (float)(this.distanceMoved + MathHelper.sqrt(d12 * d12 + d13 * d13 + d14 * d14) * 0.6);
                if (this.distanceMoved > this.blocksWalkedOn && block1.getMaterial() != Material.AIR) {
                    this.blocksWalkedOn = (int)this.distanceMoved + 1;
                    if (this.isInWater()) {
                        float f = MathHelper.sqrt(
                                this.velocityX * this.velocityX * 0.2F + this.velocityY * this.velocityY + this.velocityZ * this.velocityZ * 0.2F
                            )
                            * 0.35F;
                        if (f > 1.0F) {
                            f = 1.0F;
                        }

                        this.playSound(this.getSwimSound(), f, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                    }

                    this.playStepSound(blockpos, block1);
                }
            }

            try {
                this.checkBlockCollisions();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Checking entity block collision");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being checked for collision");
                this.populateCrashReport(crashreportcategory);
                throw new CrashException(crashreport);
            }

            boolean flag2 = this.isInWaterOrRain();
            if (this.world.containsFireSource(this.getShape().contract(0.001, 0.001, 0.001))) {
                this.takeFireDamage(1);
                if (!flag2) {
                    this.onFireTimer++;
                    if (this.onFireTimer == 0) {
                        this.setOnFireFor(8);
                    }
                }
            } else if (this.onFireTimer <= 0) {
                this.onFireTimer = -this.safeOnFireTime;
            }

            if (flag2 && this.onFireTimer > 0) {
                this.playSound("random.fizz", 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                this.onFireTimer = -this.safeOnFireTime;
            }

            this.world.profiler.pop();
        }
    }

    private void setPositionFromShape() {
        this.x = (this.getShape().minX + this.getShape().maxX) / 2.0;
        this.y = this.getShape().minY;
        this.z = (this.getShape().minZ + this.getShape().maxZ) / 2.0;
    }

    protected String getSwimSound() {
        return "game.neutral.swim";
    }

    protected void checkBlockCollisions() {
        BlockPos blockpos = new BlockPos(this.getShape().minX + 0.001, this.getShape().minY + 0.001, this.getShape().minZ + 0.001);
        BlockPos blockpos1 = new BlockPos(this.getShape().maxX - 0.001, this.getShape().maxY - 0.001, this.getShape().maxZ - 0.001);
        if (this.world.isAreaLoaded(blockpos, blockpos1)) {
            for (int i = blockpos.getX(); i <= blockpos1.getX(); i++) {
                for (int j = blockpos.getY(); j <= blockpos1.getY(); j++) {
                    for (int k = blockpos.getZ(); k <= blockpos1.getZ(); k++) {
                        BlockPos blockpos2 = new BlockPos(i, j, k);
                        BlockState blockstate = this.world.getBlockState(blockpos2);

                        try {
                            blockstate.getBlock().onEntityCollision(this.world, blockpos2, blockstate, this);
                        } catch (Throwable throwable) {
                            CrashReport crashreport = CrashReport.of(throwable, "Colliding entity with block");
                            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being collided with");
                            CrashReportCategory.addBlockDetails(crashreportcategory, blockpos2, blockstate);
                            throw new CrashException(crashreport);
                        }
                    }
                }
            }
        }
    }

    protected void playStepSound(BlockPos pos, Block block) {
        Block.Sounds block$sounds = block.sounds;
        if (this.world.getBlockState(pos.up()).getBlock() == Blocks.SNOW_LAYER) {
            block$sounds = Blocks.SNOW_LAYER.sounds;
            this.playSound(block$sounds.getStepping(), block$sounds.getVolume() * 0.15F, block$sounds.getPitch());
        } else if (!block.getMaterial().isLiquid()) {
            this.playSound(block$sounds.getStepping(), block$sounds.getVolume() * 0.15F, block$sounds.getPitch());
        }
    }

    public void playSound(String id, float volume, float pitch) {
        if (!this.isSilent()) {
            this.world.playSound(this, id, volume, pitch);
        }
    }

    public boolean isSilent() {
        return this.syncedData.getByte(4) == 1;
    }

    public void setSilent(boolean silent) {
        this.syncedData.update(4, Byte.valueOf((byte)(silent ? 1 : 0)));
    }

    protected boolean makesSteps() {
        return true;
    }

    protected void checkFallDamage(double dy, boolean landed, Block block, BlockPos pos) {
        if (landed) {
            if (this.fallDistance > 0.0F) {
                if (block != null) {
                    block.onFallenOn(this.world, pos, this, this.fallDistance);
                } else {
                    this.takeFallDamage(this.fallDistance, 1.0F);
                }

                this.fallDistance = 0.0F;
            }
        } else if (dy < 0.0) {
            this.fallDistance = (float)(this.fallDistance - dy);
        }
    }

    public Box getCollisionShape() {
        return null;
    }

    protected void takeFireDamage(int amount) {
        if (!this.immuneToFire) {
            this.takeDamage(DamageSource.FIRE, amount);
        }
    }

    public final boolean isImmuneToFire() {
        return this.immuneToFire;
    }

    public void takeFallDamage(float distance, float damageMultiplier) {
        if (this.rider != null) {
            this.rider.takeFallDamage(distance, damageMultiplier);
        }
    }

    public boolean isInWaterOrRain() {
        return this.inWater
            || this.world.isRaining(new BlockPos(this.x, this.y, this.z))
            || this.world.isRaining(new BlockPos(this.x, this.y + this.height, this.z));
    }

    public boolean isInWater() {
        return this.inWater;
    }

    public boolean checkWaterCollisions() {
        if (this.world.applyLiquidDrag(this.getShape().grown(0.0, -0.4F, 0.0).contract(0.001, 0.001, 0.001), Material.WATER, this)) {
            if (!this.inWater && !this.firstTick) {
                this.doSplashEffect();
            }

            this.fallDistance = 0.0F;
            this.inWater = true;
            this.onFireTimer = 0;
        } else {
            this.inWater = false;
        }

        return this.inWater;
    }

    protected void doSplashEffect() {
        float f = MathHelper.sqrt(this.velocityX * this.velocityX * 0.2F + this.velocityY * this.velocityY + this.velocityZ * this.velocityZ * 0.2F) * 0.2F;
        if (f > 1.0F) {
            f = 1.0F;
        }

        this.playSound(this.getSplashSound(), f, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        float f1 = MathHelper.floor(this.getShape().minY);

        for (int i = 0; i < 1.0F + this.width * 20.0F; i++) {
            float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.width;
            float f3 = (this.random.nextFloat() * 2.0F - 1.0F) * this.width;
            this.world
                .addParticle(
                    ParticleType.WATER_BUBBLE,
                    this.x + f2,
                    f1 + 1.0F,
                    this.z + f3,
                    this.velocityX,
                    this.velocityY - this.random.nextFloat() * 0.2F,
                    this.velocityZ
                );
        }

        for (int j = 0; j < 1.0F + this.width * 20.0F; j++) {
            float f4 = (this.random.nextFloat() * 2.0F - 1.0F) * this.width;
            float f5 = (this.random.nextFloat() * 2.0F - 1.0F) * this.width;
            this.world.addParticle(ParticleType.WATER_SPLASH, this.x + f4, f1 + 1.0F, this.z + f5, this.velocityX, this.velocityY, this.velocityZ);
        }
    }

    public void tickSprintingEffect() {
        if (this.isSprinting() && !this.isInWater()) {
            this.doSprintingEffect();
        }
    }

    protected void doSprintingEffect() {
        int i = MathHelper.floor(this.x);
        int j = MathHelper.floor(this.y - 0.2F);
        int k = MathHelper.floor(this.z);
        BlockPos blockpos = new BlockPos(i, j, k);
        BlockState blockstate = this.world.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        if (block.getRenderType() != -1) {
            this.world
                .addParticle(
                    ParticleType.BLOCK_CRACK,
                    this.x + (this.random.nextFloat() - 0.5) * this.width,
                    this.getShape().minY + 0.1,
                    this.z + (this.random.nextFloat() - 0.5) * this.width,
                    -this.velocityX * 4.0,
                    1.5,
                    -this.velocityZ * 4.0,
                    Block.serialize(blockstate)
                );
        }
    }

    protected String getSplashSound() {
        return "game.neutral.swim.splash";
    }

    /**
     * Check if this entity is submerged in the given fluid, meaning the fluid covers its eyes.
     */
    public boolean isSubmergedIn(Material liquid) {
        double d0 = this.y + this.getEyeHeight();
        BlockPos blockpos = new BlockPos(this.x, d0, this.z);
        BlockState blockstate = this.world.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        if (block.getMaterial() == liquid) {
            float f = LiquidBlock.getHeightLoss(blockstate.getBlock().getMetadataFromState(blockstate)) - 0.11111111F;
            float f1 = blockpos.getY() + 1 - f;
            boolean flag = d0 < f1;
            return (flag || !(this instanceof PlayerEntity)) && flag;
        } else {
            return false;
        }
    }

    public boolean isInLava() {
        return this.world.containsMaterial(this.getShape().grown(-0.1F, -0.4F, -0.1F), Material.LAVA);
    }

    public void updateVelocity(float sideways, float forwards, float scale) {
        float f = sideways * sideways + forwards * forwards;
        if (!(f < 1.0E-4F)) {
            f = MathHelper.sqrt(f);
            if (f < 1.0F) {
                f = 1.0F;
            }

            f = scale / f;
            sideways *= f;
            forwards *= f;
            float f1 = MathHelper.sin(this.yaw * (float) Math.PI / 180.0F);
            float f2 = MathHelper.cos(this.yaw * (float) Math.PI / 180.0F);
            this.velocityX += sideways * f2 - forwards * f1;
            this.velocityZ += forwards * f2 + sideways * f1;
        }
    }

    public int getLightLevel(float tickDelta) {
        BlockPos blockpos = new BlockPos(this.x, this.y + this.getEyeHeight(), this.z);
        return this.world.isChunkLoaded(blockpos) ? this.world.getLightColor(blockpos, 0) : 0;
    }

    public float getBrightness(float tickDelta) {
        BlockPos blockpos = new BlockPos(this.x, this.y + this.getEyeHeight(), this.z);
        return this.world.isChunkLoaded(blockpos) ? this.world.getBrightness(blockpos) : 0.0F;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public void updatePositionAndAngles(double x, double y, double z, float yaw, float pitch) {
        this.lastX = this.x = x;
        this.lastY = this.y = y;
        this.lastZ = this.z = z;
        this.lastYaw = this.yaw = yaw;
        this.lastPitch = this.pitch = pitch;
        double d0 = this.lastYaw - yaw;
        if (d0 < -180.0) {
            this.lastYaw += 360.0F;
        }

        if (d0 >= 180.0) {
            this.lastYaw -= 360.0F;
        }

        this.setPosition(this.x, this.y, this.z);
        this.setRotation(yaw, pitch);
    }

    public void refreshPositionAndAngles(BlockPos pos, float yaw, float pitch) {
        this.setPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch);
    }

    public void setPositionAndAngles(double x, double y, double z, float yaw, float pitch) {
        this.prevX = this.lastX = this.x = x;
        this.prevY = this.lastY = this.y = y;
        this.prevZ = this.lastZ = this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.setPosition(this.x, this.y, this.z);
    }

    public float distanceTo(Entity entity) {
        float f = (float)(this.x - entity.x);
        float f1 = (float)(this.y - entity.y);
        float f2 = (float)(this.z - entity.z);
        return MathHelper.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public double squaredDistanceTo(double x, double y, double z) {
        double d0 = this.x - x;
        double d1 = this.y - y;
        double d2 = this.z - z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double getSquaredDistanceTo(BlockPos pos) {
        return pos.squaredDistanceTo(this.x, this.y, this.z);
    }

    public double getSquaredDistanceToCenter(BlockPos pos) {
        return pos.squaredDistanceToCenter(this.x, this.y, this.z);
    }

    public double distanceTo(double x, double y, double z) {
        double d0 = this.x - x;
        double d1 = this.y - y;
        double d2 = this.z - z;
        return MathHelper.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }

    public double squaredDistanceTo(Entity entity) {
        double d0 = this.x - entity.x;
        double d1 = this.y - entity.y;
        double d2 = this.z - entity.z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public void onPlayerCollision(PlayerEntity player) {
    }

    public void push(Entity entity) {
        if (entity.rider != this && entity.vehicle != this) {
            if (!entity.noClip && !this.noClip) {
                double d0 = entity.x - this.x;
                double d1 = entity.z - this.z;
                double d2 = MathHelper.absMax(d0, d1);
                if (d2 >= 0.01F) {
                    d2 = MathHelper.sqrt(d2);
                    d0 /= d2;
                    d1 /= d2;
                    double d3 = 1.0 / d2;
                    if (d3 > 1.0) {
                        d3 = 1.0;
                    }

                    d0 *= d3;
                    d1 *= d3;
                    d0 *= 0.05F;
                    d1 *= 0.05F;
                    d0 *= 1.0F - this.pushSpeedReduction;
                    d1 *= 1.0F - this.pushSpeedReduction;
                    if (this.rider == null) {
                        this.addVelocity(-d0, 0.0, -d1);
                    }

                    if (entity.rider == null) {
                        entity.addVelocity(d0, 0.0, d1);
                    }
                }
            }
        }
    }

    public void addVelocity(double dx, double dy, double dz) {
        this.velocityX += dx;
        this.velocityY += dy;
        this.velocityZ += dz;
        this.velocityDirty = true;
    }

    protected void markDamaged() {
        this.damaged = true;
    }

    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        this.markDamaged();
        return false;
    }

    public Vec3d getRotationVec(float tickDelta) {
        if (tickDelta == 1.0F) {
            return this.getRotationVector(this.pitch, this.yaw);
        }

        float f = this.lastPitch + (this.pitch - this.lastPitch) * tickDelta;
        float f1 = this.lastYaw + (this.yaw - this.lastYaw) * tickDelta;
        return this.getRotationVector(f, f1);
    }

    protected final Vec3d getRotationVector(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * (float) (Math.PI / 180.0));
        float f3 = MathHelper.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3d(f1 * f2, f3, f * f2);
    }

    public Vec3d getEyePosition(float tickDelta) {
        if (tickDelta == 1.0F) {
            return new Vec3d(this.x, this.y + this.getEyeHeight(), this.z);
        }

        double d0 = this.lastX + (this.x - this.lastX) * tickDelta;
        double d1 = this.lastY + (this.y - this.lastY) * tickDelta + this.getEyeHeight();
        double d2 = this.lastZ + (this.z - this.lastZ) * tickDelta;
        return new Vec3d(d0, d1, d2);
    }

    public HitResult rayTrace(double range, float tickDelta) {
        Vec3d vec3d = this.getEyePosition(tickDelta);
        Vec3d vec3d1 = this.getRotationVec(tickDelta);
        Vec3d vec3d2 = vec3d.add(vec3d1.x * range, vec3d1.y * range, vec3d1.z * range);
        return this.world.rayTrace(vec3d, vec3d2, false, false, true);
    }

    public boolean hasCollision() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public void takeKillScore(Entity victim, int score) {
    }

    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        double d0 = this.x - cameraX;
        double d1 = this.y - cameraY;
        double d2 = this.z - cameraZ;
        double d3 = d0 * d0 + d1 * d1 + d2 * d2;
        return this.shouldRender(d3);
    }

    public boolean shouldRender(double squaredDistanceToCamera) {
        double d0 = this.getShape().getAverageSideLength();
        if (Double.isNaN(d0)) {
            d0 = 1.0;
        }

        d0 *= 64.0 * this.viewDistanceScaling;
        return squaredDistanceToCamera < d0 * d0;
    }

    public boolean writeNbt(NbtCompound nbt) {
        String s = this.getTypeId();
        if (!this.removed && s != null) {
            nbt.putString("id", s);
            this.writeNbtWithoutId(nbt);
            return true;
        } else {
            return false;
        }
    }

    public boolean writeNbtIfNotMount(NbtCompound nbt) {
        String s = this.getTypeId();
        if (!this.removed && s != null && this.rider == null) {
            nbt.putString("id", s);
            this.writeNbtWithoutId(nbt);
            return true;
        } else {
            return false;
        }
    }

    public void writeNbtWithoutId(NbtCompound nbt) {
        try {
            nbt.put("Pos", this.toNbtList(this.x, this.y, this.z));
            nbt.put("Motion", this.toNbtList(this.velocityX, this.velocityY, this.velocityZ));
            nbt.put("Rotation", this.toNbtList(this.yaw, this.pitch));
            nbt.putFloat("FallDistance", this.fallDistance);
            nbt.putShort("Fire", (short)this.onFireTimer);
            nbt.putShort("Air", (short)this.getBreath());
            nbt.putBoolean("OnGround", this.onGround);
            nbt.putInt("Dimension", this.dimension);
            nbt.putBoolean("Invulnerable", this.invulnerable);
            nbt.putInt("PortalCooldown", this.portalCooldown);
            nbt.putLong("UUIDMost", this.getUuid().getMostSignificantBits());
            nbt.putLong("UUIDLeast", this.getUuid().getLeastSignificantBits());
            if (this.getCustomName() != null && this.getCustomName().length() > 0) {
                nbt.putString("CustomName", this.getCustomName());
                nbt.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            this.commandResults.writeNbt(nbt);
            if (this.isSilent()) {
                nbt.putBoolean("Silent", this.isSilent());
            }

            this.writeCustomNbt(nbt);
            if (this.vehicle != null) {
                NbtCompound nbtcompound = new NbtCompound();
                if (this.vehicle.writeNbt(nbtcompound)) {
                    nbt.put("Riding", nbtcompound);
                }
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Saving entity NBT");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being saved");
            this.populateCrashReport(crashreportcategory);
            throw new CrashException(crashreport);
        }
    }

    public void readNbt(NbtCompound nbt) {
        try {
            NbtList nbtlist = nbt.getList("Pos", 6);
            NbtList nbtlist1 = nbt.getList("Motion", 6);
            NbtList nbtlist2 = nbt.getList("Rotation", 5);
            this.velocityX = nbtlist1.getDouble(0);
            this.velocityY = nbtlist1.getDouble(1);
            this.velocityZ = nbtlist1.getDouble(2);
            if (Math.abs(this.velocityX) > 10.0) {
                this.velocityX = 0.0;
            }

            if (Math.abs(this.velocityY) > 10.0) {
                this.velocityY = 0.0;
            }

            if (Math.abs(this.velocityZ) > 10.0) {
                this.velocityZ = 0.0;
            }

            this.lastX = this.prevX = this.x = nbtlist.getDouble(0);
            this.lastY = this.prevY = this.y = nbtlist.getDouble(1);
            this.lastZ = this.prevZ = this.z = nbtlist.getDouble(2);
            this.lastYaw = this.yaw = nbtlist2.getFloat(0);
            this.lastPitch = this.pitch = nbtlist2.getFloat(1);
            this.setHeadYaw(this.yaw);
            this.setBodyYaw(this.yaw);
            this.fallDistance = nbt.getFloat("FallDistance");
            this.onFireTimer = nbt.getShort("Fire");
            this.setBreath(nbt.getShort("Air"));
            this.onGround = nbt.getBoolean("OnGround");
            this.dimension = nbt.getInt("Dimension");
            this.invulnerable = nbt.getBoolean("Invulnerable");
            this.portalCooldown = nbt.getInt("PortalCooldown");
            if (nbt.contains("UUIDMost", 4) && nbt.contains("UUIDLeast", 4)) {
                this.uuid = new UUID(nbt.getLong("UUIDMost"), nbt.getLong("UUIDLeast"));
            } else if (nbt.contains("UUID", 8)) {
                this.uuid = UUID.fromString(nbt.getString("UUID"));
            }

            this.setPosition(this.x, this.y, this.z);
            this.setRotation(this.yaw, this.pitch);
            if (nbt.contains("CustomName", 8) && nbt.getString("CustomName").length() > 0) {
                this.setCustomName(nbt.getString("CustomName"));
            }

            this.setCustomNameVisible(nbt.getBoolean("CustomNameVisible"));
            this.commandResults.readNbt(nbt);
            this.setSilent(nbt.getBoolean("Silent"));
            this.readCustomNbt(nbt);
            if (this.shouldSetPositionOnLoad()) {
                this.setPosition(this.x, this.y, this.z);
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Loading entity NBT");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being loaded");
            this.populateCrashReport(crashreportcategory);
            throw new CrashException(crashreport);
        }
    }

    protected boolean shouldSetPositionOnLoad() {
        return true;
    }

    protected final String getTypeId() {
        return Entities.getKey(this);
    }

    protected abstract void readCustomNbt(NbtCompound nbt);

    protected abstract void writeCustomNbt(NbtCompound nbt);

    public void load() {
    }

    protected NbtList toNbtList(double... values) {
        NbtList nbtlist = new NbtList();

        for (double d0 : values) {
            nbtlist.addElement(new NbtDouble(d0));
        }

        return nbtlist;
    }

    protected NbtList toNbtList(float... values) {
        NbtList nbtlist = new NbtList();

        for (float f : values) {
            nbtlist.addElement(new NbtFloat(f));
        }

        return nbtlist;
    }

    public ItemEntity dropItem(Item item, int count) {
        return this.dropItem(item, count, 0.0F);
    }

    public ItemEntity dropItem(Item item, int count, float offsetY) {
        return this.dropItem(new ItemStack(item, count, 0), offsetY);
    }

    public ItemEntity dropItem(ItemStack item, float offsetY) {
        if (item.size != 0 && item.getItem() != null) {
            ItemEntity itementity = new ItemEntity(this.world, this.x, this.y + offsetY, this.z, item);
            itementity.setDefaultPickUpDelay();
            this.world.addEntity(itementity);
            return itementity;
        } else {
            return null;
        }
    }

    public boolean isAlive() {
        return !this.removed;
    }

    public boolean isInWall() {
        if (this.noClip) {
            return false;
        }

        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        for (int i = 0; i < 8; i++) {
            int j = MathHelper.floor(this.y + ((i >> 0) % 2 - 0.5F) * 0.1F + this.getEyeHeight());
            int k = MathHelper.floor(this.x + ((i >> 1) % 2 - 0.5F) * this.width * 0.8F);
            int l = MathHelper.floor(this.z + ((i >> 2) % 2 - 0.5F) * this.width * 0.8F);
            if (blockpos$mutable.getX() != k || blockpos$mutable.getY() != j || blockpos$mutable.getZ() != l) {
                blockpos$mutable.set(k, j, l);
                if (this.world.getBlockState(blockpos$mutable).getBlock().isViewBlocking()) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean interact(PlayerEntity player) {
        return false;
    }

    public Box getCollisionAgainstShape(Entity other) {
        return null;
    }

    public void rideTick() {
        if (this.vehicle.removed) {
            this.vehicle = null;
        } else {
            this.velocityX = 0.0;
            this.velocityY = 0.0;
            this.velocityZ = 0.0;
            this.tick();
            if (this.vehicle != null) {
                this.vehicle.updateRiderPositon();
                this.ridingEntityYawDelta = this.ridingEntityYawDelta + (this.vehicle.yaw - this.vehicle.lastYaw);
                this.ridingEntityPitchDelta = this.ridingEntityPitchDelta + (this.vehicle.pitch - this.vehicle.lastPitch);

                while (this.ridingEntityYawDelta >= 180.0) {
                    this.ridingEntityYawDelta -= 360.0;
                }

                while (this.ridingEntityYawDelta < -180.0) {
                    this.ridingEntityYawDelta += 360.0;
                }

                while (this.ridingEntityPitchDelta >= 180.0) {
                    this.ridingEntityPitchDelta -= 360.0;
                }

                while (this.ridingEntityPitchDelta < -180.0) {
                    this.ridingEntityPitchDelta += 360.0;
                }

                double d0 = this.ridingEntityYawDelta * 0.5;
                double d1 = this.ridingEntityPitchDelta * 0.5;
                float f = 10.0F;
                if (d0 > f) {
                    d0 = f;
                }

                if (d0 < -f) {
                    d0 = -f;
                }

                if (d1 > f) {
                    d1 = f;
                }

                if (d1 < -f) {
                    d1 = -f;
                }

                this.ridingEntityYawDelta -= d0;
                this.ridingEntityPitchDelta -= d1;
            }
        }
    }

    public void updateRiderPositon() {
        if (this.rider != null) {
            this.rider.setPosition(this.x, this.y + this.getMountHeight() + this.rider.getRideHeight(), this.z);
        }
    }

    /**
     * Return the height offset of this entity when riding another entity.
     */
    public double getRideHeight() {
        return 0.0;
    }

    /**
     * Return the height at which other entities sit when riding this entity.
     */
    public double getMountHeight() {
        return this.height * 0.75;
    }

    public void startRiding(Entity entity) {
        this.ridingEntityPitchDelta = 0.0;
        this.ridingEntityYawDelta = 0.0;
        if (entity == null) {
            if (this.vehicle != null) {
                this.setPositionAndAngles(this.vehicle.x, this.vehicle.getShape().minY + this.vehicle.height, this.vehicle.z, this.yaw, this.pitch);
                this.vehicle.rider = null;
            }

            this.vehicle = null;
        } else {
            if (this.vehicle != null) {
                this.vehicle.rider = null;
            }

            if (entity != null) {
                for (Entity entityx = entity.vehicle; entityx != null; entityx = entityx.vehicle) {
                    if (entityx == this) {
                        return;
                    }
                }
            }

            this.vehicle = entity;
            entity.rider = this;
        }
    }

    public void lerpPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        this.setPosition(x, y, z);
        this.setRotation(yaw, pitch);
        List<Box> list = this.world.getCollisions(this, this.getShape().contract(0.03125, 0.0, 0.03125));
        if (!list.isEmpty()) {
            double d0 = 0.0;

            for (Box box : list) {
                if (box.maxY > d0) {
                    d0 = box.maxY;
                }
            }

            y += d0 - this.getShape().minY;
            this.setPosition(x, y, z);
        }
    }

    public float getPickRadius() {
        return 0.1F;
    }

    public Vec3d getLookVector() {
        return null;
    }

    /**
     * Called when this entity collides with a nether portal block.
     */
    public void onPortalCollision(BlockPos pos) {
        if (this.portalCooldown > 0) {
            this.portalCooldown = this.getPortalCooldown();
        } else {
            if (!this.world.isClient && !pos.equals(this.lastPortalPos)) {
                this.lastPortalPos = pos;
                BlockPattern.Match blockpattern$match = Blocks.NETHER_PORTAL.findPortalShape(this.world, pos);
                double d0 = blockpattern$match.getForward().getAxis() == Direction.Axis.X
                    ? blockpattern$match.getTopLeftFront().getZ()
                    : blockpattern$match.getTopLeftFront().getX();
                double d1 = blockpattern$match.getForward().getAxis() == Direction.Axis.X ? this.z : this.x;
                d1 = Math.abs(
                    MathHelper.inverseLerp(
                        d1 - (blockpattern$match.getForward().clockwiseY().getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1 : 0),
                        d0,
                        d0 - blockpattern$match.getWidth()
                    )
                );
                double d2 = MathHelper.inverseLerp(
                    this.y - 1.0, blockpattern$match.getTopLeftFront().getY(), blockpattern$match.getTopLeftFront().getY() - blockpattern$match.getHeight()
                );
                this.lastPortalOffset = new Vec3d(d1, d2, 0.0);
                this.lastPortalFacing = blockpattern$match.getForward();
            }

            this.inPortal = true;
        }
    }

    public int getPortalCooldown() {
        return 300;
    }

    public void lerpVelocity(double velocityX, double velocityY, double velocityZ) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
    }

    public void doEvent(byte event) {
    }

    public void animateDamage() {
    }

    public ItemStack[] getEquipment() {
        return null;
    }

    public void setEquipment(int slot, ItemStack item) {
    }

    public boolean isOnFire() {
        boolean flag = this.world != null && this.world.isClient;
        return !this.immuneToFire && (this.onFireTimer > 0 || flag && this.getFlag(0));
    }

    public boolean isRiding() {
        return this.vehicle != null;
    }

    public boolean isSneaking() {
        return this.getFlag(1);
    }

    public void setSneaking(boolean sneaking) {
        this.setFlag(1, sneaking);
    }

    public boolean isSprinting() {
        return this.getFlag(3);
    }

    public void setSprinting(boolean sprinting) {
        this.setFlag(3, sprinting);
    }

    public boolean isInvisible() {
        return this.getFlag(5);
    }

    public boolean isInvisibleTo(PlayerEntity player) {
        return !player.isSpectator() && this.isInvisible();
    }

    public void setInvisible(boolean invisible) {
        this.setFlag(5, invisible);
    }

    public boolean isUsingItem() {
        return this.getFlag(4);
    }

    public void setUsingItem(boolean usingItem) {
        this.setFlag(4, usingItem);
    }

    protected boolean getFlag(int index) {
        return (this.syncedData.getByte(0) & 1 << index) != 0;
    }

    protected void setFlag(int index, boolean value) {
        byte b0 = this.syncedData.getByte(0);
        if (value) {
            this.syncedData.update(0, (byte)(b0 | 1 << index));
        } else {
            this.syncedData.update(0, (byte)(b0 & ~(1 << index)));
        }
    }

    public int getBreath() {
        return this.syncedData.getShort(1);
    }

    public void setBreath(int breath) {
        this.syncedData.update(1, (short)breath);
    }

    public void struckByLightning(LightningBoltEntity lightning) {
        this.takeDamage(DamageSource.LIGHTNING_BOLT, 5.0F);
        this.onFireTimer++;
        if (this.onFireTimer == 0) {
            this.setOnFireFor(8);
        }
    }

    public void onKill(LivingEntity victim) {
    }

    protected boolean pushAwayFrom(double x, double y, double z) {
        BlockPos blockpos = new BlockPos(x, y, z);
        double d0 = x - blockpos.getX();
        double d1 = y - blockpos.getY();
        double d2 = z - blockpos.getZ();
        List<Box> list = this.world.getBlockCollisions(this.getShape());
        if (list.isEmpty() && !this.world.isFullCube(blockpos)) {
            return false;
        }

        int i = 3;
        double d3 = 9999.0;
        if (!this.world.isFullCube(blockpos.west()) && d0 < d3) {
            d3 = d0;
            i = 0;
        }

        if (!this.world.isFullCube(blockpos.east()) && 1.0 - d0 < d3) {
            d3 = 1.0 - d0;
            i = 1;
        }

        if (!this.world.isFullCube(blockpos.up()) && 1.0 - d1 < d3) {
            d3 = 1.0 - d1;
            i = 3;
        }

        if (!this.world.isFullCube(blockpos.north()) && d2 < d3) {
            d3 = d2;
            i = 4;
        }

        if (!this.world.isFullCube(blockpos.south()) && 1.0 - d2 < d3) {
            d3 = 1.0 - d2;
            i = 5;
        }

        float f = this.random.nextFloat() * 0.2F + 0.1F;
        if (i == 0) {
            this.velocityX = -f;
        }

        if (i == 1) {
            this.velocityX = f;
        }

        if (i == 3) {
            this.velocityY = f;
        }

        if (i == 4) {
            this.velocityZ = -f;
        }

        if (i == 5) {
            this.velocityZ = f;
        }

        return true;
    }

    public void onCobwebCollision() {
        this.inCobweb = true;
        this.fallDistance = 0.0F;
    }

    @Override
    public String getName() {
        if (this.hasCustomName()) {
            return this.getCustomName();
        }

        String s = Entities.getKey(this);
        if (s == null) {
            s = "generic";
        }

        return I18n.translate("entity." + s + ".name");
    }

    public Entity[] getParts() {
        return null;
    }

    public boolean is(Entity entity) {
        return this == entity;
    }

    public float getHeadYaw() {
        return 0.0F;
    }

    public void setHeadYaw(float headYaw) {
    }

    public void setBodyYaw(float yaw) {
    }

    public boolean canBePunched() {
        return true;
    }

    public boolean onPunched(Entity attacker) {
        return false;
    }

    @Override
    public String toString() {
        return String.format(
            "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f]",
            this.getClass().getSimpleName(),
            this.getName(),
            this.networkId,
            this.world == null ? "~NULL~" : this.world.getData().getName(),
            this.x,
            this.y,
            this.z
        );
    }

    public boolean isInvulnerable(DamageSource source) {
        return this.invulnerable && source != DamageSource.OUT_OF_WORLD && !source.isCreativePlayer();
    }

    public void copyPositionAndRotationFrom(Entity entity) {
        this.setPositionAndAngles(entity.x, entity.y, entity.z, entity.yaw, entity.pitch);
    }

    public void copyNbtFrom(Entity sourceEntity) {
        NbtCompound nbtcompound = new NbtCompound();
        sourceEntity.writeNbtWithoutId(nbtcompound);
        this.readNbt(nbtcompound);
        this.portalCooldown = sourceEntity.portalCooldown;
        this.lastPortalPos = sourceEntity.lastPortalPos;
        this.lastPortalOffset = sourceEntity.lastPortalOffset;
        this.lastPortalFacing = sourceEntity.lastPortalFacing;
    }

    public void changeDimension(int dimension) {
        if (!this.world.isClient && !this.removed) {
            this.world.profiler.push("changeDimension");
            MinecraftServer minecraftserver = MinecraftServer.getInstance();
            int i = this.dimension;
            ServerWorld serverworld = minecraftserver.getWorld(i);
            ServerWorld serverworld1 = minecraftserver.getWorld(dimension);
            this.dimension = dimension;
            if (i == 1 && dimension == 1) {
                serverworld1 = minecraftserver.getWorld(0);
                this.dimension = 0;
            }

            this.world.removeEntity(this);
            this.removed = false;
            this.world.profiler.push("reposition");
            minecraftserver.getPlayerManager().changeDimension(this, i, serverworld, serverworld1);
            this.world.profiler.swap("reloading");
            Entity entity = Entities.createSilently(Entities.getKey(this), serverworld1);
            if (entity != null) {
                entity.copyNbtFrom(this);
                if (i == 1 && dimension == 1) {
                    BlockPos blockpos = this.world.getSurfaceHeight(serverworld1.getSpawnPoint());
                    entity.refreshPositionAndAngles(blockpos, entity.yaw, entity.pitch);
                }

                serverworld1.addEntity(entity);
            }

            this.removed = true;
            this.world.profiler.pop();
            serverworld.resetIdleTimeout();
            serverworld1.resetIdleTimeout();
            this.world.profiler.pop();
        }
    }

    public float getBlastResistance(Explosion explosion, World world, BlockPos pos, BlockState state) {
        return state.getBlock().getBlastResistance(this);
    }

    /**
     * Check if an explosion caused by this entity can explode the given block.
     */
    public boolean canExplodeBlock(Explosion explosion, World world, BlockPos pos, BlockState state, float power) {
        return true;
    }

    public int getSafeFallDistance() {
        return 3;
    }

    public Vec3d getLastPortalOffset() {
        return this.lastPortalOffset;
    }

    public Direction getLastPortalFacing() {
        return this.lastPortalFacing;
    }

    public boolean canAvoidTraps() {
        return false;
    }

    public void populateCrashReport(CrashReportCategory section) {
        section.add("Entity Type", new Callable<String>() {
            public String call() throws Exception {
                return Entities.getKey(Entity.this) + " (" + Entity.this.getClass().getCanonicalName() + ")";
            }
        });
        section.add("Entity ID", this.networkId);
        section.add("Entity Name", new Callable<String>() {
            public String call() throws Exception {
                return Entity.this.getName();
            }
        });
        section.add("Entity's Exact location", String.format("%.2f, %.2f, %.2f", this.x, this.y, this.z));
        section.add("Entity's Block location", CrashReportCategory.formatPosition(MathHelper.floor(this.x), MathHelper.floor(this.y), MathHelper.floor(this.z)));
        section.add("Entity's Momentum", String.format("%.2f, %.2f, %.2f", this.velocityX, this.velocityY, this.velocityZ));
        section.add("Entity's Rider", new Callable<String>() {
            public String call() throws Exception {
                return Entity.this.rider.toString();
            }
        });
        section.add("Entity's Vehicle", new Callable<String>() {
            public String call() throws Exception {
                return Entity.this.vehicle.toString();
            }
        });
    }

    public boolean shouldRenderOnFire() {
        return this.isOnFire();
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public boolean hasLiquidCollision() {
        return true;
    }

    @Override
    public Text getDisplayName() {
        LiteralText literaltext = new LiteralText(this.getName());
        literaltext.getStyle().setHoverEvent(this.getHoverEvent());
        literaltext.getStyle().setInsertion(this.getUuid().toString());
        return literaltext;
    }

    public void setCustomName(String name) {
        this.syncedData.update(2, name);
    }

    public String getCustomName() {
        return this.syncedData.getString(2);
    }

    public boolean hasCustomName() {
        return this.syncedData.getString(2).length() > 0;
    }

    public void setCustomNameVisible(boolean visible) {
        this.syncedData.update(3, Byte.valueOf((byte)(visible ? 1 : 0)));
    }

    public boolean isCustomNameVisible() {
        return this.syncedData.getByte(3) == 1;
    }

    public void teleport(double x, double y, double z) {
        this.setPositionAndAngles(x, y, z, this.yaw, this.pitch);
    }

    public boolean shouldShowNameTag() {
        return this.isCustomNameVisible();
    }

    public void onDataValueChanged(int id) {
    }

    public Direction getHorizontalFacing() {
        return Direction.byIdHorizontal(MathHelper.floor(this.yaw * 4.0F / 360.0F + 0.5) & 3);
    }

    protected HoverEvent getHoverEvent() {
        NbtCompound nbtcompound = new NbtCompound();
        String s = Entities.getKey(this);
        nbtcompound.putString("id", this.getUuid().toString());
        if (s != null) {
            nbtcompound.putString("type", s);
        }

        nbtcompound.putString("name", this.getName());
        return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new LiteralText(nbtcompound.toString()));
    }

    public boolean broadcastTo(ServerPlayerEntity player) {
        return true;
    }

    public Box getShape() {
        return this.shape;
    }

    public void setShape(Box shape) {
        this.shape = shape;
    }

    public float getEyeHeight() {
        return this.height * 0.85F;
    }

    public boolean isOutsideWorldBorder() {
        return this.outsideWorldBorder;
    }

    public void setOutsideWorldBorder(boolean outsideWorldBorder) {
        this.outsideWorldBorder = outsideWorldBorder;
    }

    public boolean replaceItem(int slot, ItemStack item) {
        return false;
    }

    @Override
    public void sendMessage(Text message) {
    }

    @Override
    public boolean canUseCommand(int permissionLevel, String command) {
        return true;
    }

    @Override
    public BlockPos getCommandSourceBlockPos() {
        return new BlockPos(this.x, this.y + 0.5, this.z);
    }

    @Override
    public Vec3d getCommandSourcePos() {
        return new Vec3d(this.x, this.y, this.z);
    }

    @Override
    public World getCommandSourceWorld() {
        return this.world;
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public boolean sendCommandSuccessToOps() {
        return false;
    }

    @Override
    public void addResult(CommandResults.Type type, int result) {
        this.commandResults.add(this, type, result);
    }

    public CommandResults getCommandResults() {
        return this.commandResults;
    }

    public void copyCommandResults(Entity entity) {
        this.commandResults.copy(entity.getCommandResults());
    }

    public NbtCompound getSyncedNbt() {
        return null;
    }

    public void syncNbt(NbtCompound nbt) {
    }

    public boolean interactAt(PlayerEntity player, Vec3d offset) {
        return false;
    }

    public boolean isImmuneToExplosions() {
        return false;
    }

    protected void damageEntity(LivingEntity source, Entity entity) {
        if (entity instanceof LivingEntity) {
            EnchantmentHelper.applyProtectionWildcard((LivingEntity)entity, source);
        }

        EnchantmentHelper.applyDamageWildcard(source, entity);
    }
}
