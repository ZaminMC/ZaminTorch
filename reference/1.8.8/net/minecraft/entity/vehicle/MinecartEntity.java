package net.minecraft.entity.vehicle;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Nameable;
import net.minecraft.world.World;

public abstract class MinecartEntity extends Entity implements Nameable {
    private boolean flipped;
    private String customName;
    private static final int[][][] ADJACENT_RAIL_POSITIONS_BY_SHAPE = new int[][][]{
        {{0, 0, -1}, {0, 0, 1}},
        {{-1, 0, 0}, {1, 0, 0}},
        {{-1, -1, 0}, {1, 0, 0}},
        {{-1, 0, 0}, {1, -1, 0}},
        {{0, 0, -1}, {0, -1, 1}},
        {{0, -1, -1}, {0, 0, 1}},
        {{0, 0, 1}, {1, 0, 0}},
        {{0, 0, 1}, {-1, 0, 0}},
        {{0, 0, -1}, {-1, 0, 0}},
        {{0, 0, -1}, {1, 0, 0}}
    };
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYaw;
    private double lerpPitch;
    private double lerpVelocityX;
    private double lerpVelocityY;
    private double lerpVelocityZ;

    public MinecartEntity(World world) {
        super(world);
        this.blocksBuilding = true;
        this.setSize(0.98F, 0.7F);
    }

    public static MinecartEntity create(World world, double x, double y, double z, MinecartEntity.Type type) {
        switch (type) {
            case CHEST:
                return new ChestMinecartEntity(world, x, y, z);
            case FURNACE:
                return new FurnaceMinecartEntity(world, x, y, z);
            case TNT:
                return new TntMinecartEntity(world, x, y, z);
            case SPAWNER:
                return new SpawnerMinecartEntity(world, x, y, z);
            case HOPPER:
                return new HopperMinecartEntity(world, x, y, z);
            case COMMAND_BLOCK:
                return new CommandBlockMinecartEntity(world, x, y, z);
            default:
                return new RideableMinecartEntity(world, x, y, z);
        }
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    protected void registerSyncedData() {
        this.syncedData.register(17, new Integer(0));
        this.syncedData.register(18, new Integer(1));
        this.syncedData.register(19, new Float(0.0F));
        this.syncedData.register(20, new Integer(0));
        this.syncedData.register(21, new Integer(6));
        this.syncedData.register(22, (byte)0);
    }

    @Override
    public Box getCollisionAgainstShape(Entity other) {
        return other.isPushable() ? other.getShape() : null;
    }

    @Override
    public Box getCollisionShape() {
        return null;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    public MinecartEntity(World world, double x, double y, double z) {
        this(world);
        this.setPosition(x, y, z);
        this.velocityX = 0.0;
        this.velocityY = 0.0;
        this.velocityZ = 0.0;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    @Override
    public double getMountHeight() {
        return 0.0;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.world.isClient || this.removed) {
            return true;
        }

        if (this.isInvulnerable(source)) {
            return false;
        }

        this.setDamagedSwingDirection(-this.getDamagedSwingDirection());
        this.setDamagedTimer(10);
        this.markDamaged();
        this.setDamage(this.getDamage() + amount * 10.0F);
        boolean flag = source.getAttacker() instanceof PlayerEntity && ((PlayerEntity)source.getAttacker()).abilities.creativeMode;
        if (flag || this.getDamage() > 40.0F) {
            if (this.rider != null) {
                this.rider.startRiding(null);
            }

            if (flag && !this.hasCustomName()) {
                this.remove();
            } else {
                this.dropItems(source);
            }
        }

        return true;
    }

    public void dropItems(DamageSource damageSource) {
        this.remove();
        if (this.world.getGameRules().getBoolean("doEntityDrops")) {
            ItemStack itemstack = new ItemStack(Items.MINECART, 1);
            if (this.customName != null) {
                itemstack.setHoverName(this.customName);
            }

            this.dropItem(itemstack, 0.0F);
        }
    }

    @Override
    public void animateDamage() {
        this.setDamagedSwingDirection(-this.getDamagedSwingDirection());
        this.setDamagedTimer(10);
        this.setDamage(this.getDamage() + this.getDamage() * 10.0F);
    }

    @Override
    public boolean hasCollision() {
        return !this.removed;
    }

    @Override
    public void remove() {
        super.remove();
    }

    @Override
    public void tick() {
        if (this.getDamagedTimer() > 0) {
            this.setDamagedTimer(this.getDamagedTimer() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        if (this.y < -64.0) {
            this.voidTick();
        }

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

        if (this.world.isClient) {
            if (this.lerpSteps > 0) {
                double d4 = this.x + (this.lerpX - this.x) / this.lerpSteps;
                double d5 = this.y + (this.lerpY - this.y) / this.lerpSteps;
                double d6 = this.z + (this.lerpZ - this.z) / this.lerpSteps;
                double d1 = MathHelper.wrapDegrees(this.lerpYaw - this.yaw);
                this.yaw = (float)(this.yaw + d1 / this.lerpSteps);
                this.pitch = (float)(this.pitch + (this.lerpPitch - this.pitch) / this.lerpSteps);
                this.lerpSteps--;
                this.setPosition(d4, d5, d6);
                this.setRotation(this.yaw, this.pitch);
            } else {
                this.setPosition(this.x, this.y, this.z);
                this.setRotation(this.yaw, this.pitch);
            }
        } else {
            this.lastX = this.x;
            this.lastY = this.y;
            this.lastZ = this.z;
            this.velocityY -= 0.04F;
            int k = MathHelper.floor(this.x);
            int l = MathHelper.floor(this.y);
            int i1 = MathHelper.floor(this.z);
            if (AbstractRailBlock.isRail(this.world, new BlockPos(k, l - 1, i1))) {
                l--;
            }

            BlockPos blockpos = new BlockPos(k, l, i1);
            BlockState blockstate = this.world.getBlockState(blockpos);
            if (AbstractRailBlock.isRail(blockstate)) {
                this.moveOnRail(blockpos, blockstate);
                if (blockstate.getBlock() == Blocks.ACTIVATOR_RAIL) {
                    this.onActivatorRail(k, l, i1, blockstate.get(PoweredRailBlock.POWERED));
                }
            } else {
                this.moveOffRail();
            }

            this.checkBlockCollisions();
            this.pitch = 0.0F;
            double d0 = this.lastX - this.x;
            double d2 = this.lastZ - this.z;
            if (d0 * d0 + d2 * d2 > 0.001) {
                this.yaw = (float)(MathHelper.fastAtan2(d2, d0) * 180.0 / Math.PI);
                if (this.flipped) {
                    this.yaw += 180.0F;
                }
            }

            double d3 = MathHelper.wrapDegrees(this.yaw - this.lastYaw);
            if (d3 < -170.0 || d3 >= 170.0) {
                this.yaw += 180.0F;
                this.flipped = !this.flipped;
            }

            this.setRotation(this.yaw, this.pitch);

            for (Entity entity : this.world.getEntities(this, this.getShape().grown(0.2F, 0.0, 0.2F))) {
                if (entity != this.rider && entity.isPushable() && entity instanceof MinecartEntity) {
                    entity.push(this);
                }
            }

            if (this.rider != null && this.rider.removed) {
                if (this.rider.vehicle == this) {
                    this.rider.vehicle = null;
                }

                this.rider = null;
            }

            this.checkWaterCollisions();
        }
    }

    protected double getMaxSpeed() {
        return 0.4;
    }

    public void onActivatorRail(int x, int y, int z, boolean powered) {
    }

    protected void moveOffRail() {
        double d0 = this.getMaxSpeed();
        this.velocityX = MathHelper.clamp(this.velocityX, -d0, d0);
        this.velocityZ = MathHelper.clamp(this.velocityZ, -d0, d0);
        if (this.onGround) {
            this.velocityX *= 0.5;
            this.velocityY *= 0.5;
            this.velocityZ *= 0.5;
        }

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        if (!this.onGround) {
            this.velocityX *= 0.95F;
            this.velocityY *= 0.95F;
            this.velocityZ *= 0.95F;
        }
    }

    protected void moveOnRail(BlockPos pos, BlockState state) {
        this.fallDistance = 0.0F;
        Vec3d vec3d = this.snapPositionToRail(this.x, this.y, this.z);
        this.y = pos.getY();
        boolean flag = false;
        boolean flag1 = false;
        AbstractRailBlock abstractrailblock = (AbstractRailBlock)state.getBlock();
        if (abstractrailblock == Blocks.POWERED_RAIL) {
            flag = state.get(PoweredRailBlock.POWERED);
            flag1 = !flag;
        }

        double d0 = 0.0078125;
        AbstractRailBlock.Shape abstractrailblock$shape = state.get(abstractrailblock.getShapeProperty());
        switch (abstractrailblock$shape) {
            case ASCENDING_EAST:
                this.velocityX -= 0.0078125;
                this.y++;
                break;
            case ASCENDING_WEST:
                this.velocityX += 0.0078125;
                this.y++;
                break;
            case ASCENDING_NORTH:
                this.velocityZ += 0.0078125;
                this.y++;
                break;
            case ASCENDING_SOUTH:
                this.velocityZ -= 0.0078125;
                this.y++;
        }

        int[][] aint = ADJACENT_RAIL_POSITIONS_BY_SHAPE[abstractrailblock$shape.getId()];
        double d1 = aint[1][0] - aint[0][0];
        double d2 = aint[1][2] - aint[0][2];
        double d3 = Math.sqrt(d1 * d1 + d2 * d2);
        double d4 = this.velocityX * d1 + this.velocityZ * d2;
        if (d4 < 0.0) {
            d1 = -d1;
            d2 = -d2;
        }

        double d5 = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        if (d5 > 2.0) {
            d5 = 2.0;
        }

        this.velocityX = d5 * d1 / d3;
        this.velocityZ = d5 * d2 / d3;
        if (this.rider instanceof LivingEntity) {
            double d6 = ((LivingEntity)this.rider).forwardSpeed;
            if (d6 > 0.0) {
                double d7 = -Math.sin(this.rider.yaw * (float) Math.PI / 180.0F);
                double d8 = Math.cos(this.rider.yaw * (float) Math.PI / 180.0F);
                double d9 = this.velocityX * this.velocityX + this.velocityZ * this.velocityZ;
                if (d9 < 0.01) {
                    this.velocityX += d7 * 0.1;
                    this.velocityZ += d8 * 0.1;
                    flag1 = false;
                }
            }
        }

        if (flag1) {
            double d17 = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            if (d17 < 0.03) {
                this.velocityX *= 0.0;
                this.velocityY *= 0.0;
                this.velocityZ *= 0.0;
            } else {
                this.velocityX *= 0.5;
                this.velocityY *= 0.0;
                this.velocityZ *= 0.5;
            }
        }

        double d18 = 0.0;
        double d19 = pos.getX() + 0.5 + aint[0][0] * 0.5;
        double d20 = pos.getZ() + 0.5 + aint[0][2] * 0.5;
        double d21 = pos.getX() + 0.5 + aint[1][0] * 0.5;
        double d10 = pos.getZ() + 0.5 + aint[1][2] * 0.5;
        d1 = d21 - d19;
        d2 = d10 - d20;
        if (d1 == 0.0) {
            this.x = pos.getX() + 0.5;
            d18 = this.z - pos.getZ();
        } else if (d2 == 0.0) {
            this.z = pos.getZ() + 0.5;
            d18 = this.x - pos.getX();
        } else {
            double d11 = this.x - d19;
            double d12 = this.z - d20;
            d18 = (d11 * d1 + d12 * d2) * 2.0;
        }

        this.x = d19 + d1 * d18;
        this.z = d20 + d2 * d18;
        this.setPosition(this.x, this.y, this.z);
        double d22 = this.velocityX;
        double d23 = this.velocityZ;
        if (this.rider != null) {
            d22 *= 0.75;
            d23 *= 0.75;
        }

        double d13 = this.getMaxSpeed();
        d22 = MathHelper.clamp(d22, -d13, d13);
        d23 = MathHelper.clamp(d23, -d13, d13);
        this.move(d22, 0.0, d23);
        if (aint[0][1] != 0 && MathHelper.floor(this.x) - pos.getX() == aint[0][0] && MathHelper.floor(this.z) - pos.getZ() == aint[0][2]) {
            this.setPosition(this.x, this.y + aint[0][1], this.z);
        } else if (aint[1][1] != 0 && MathHelper.floor(this.x) - pos.getX() == aint[1][0] && MathHelper.floor(this.z) - pos.getZ() == aint[1][2]) {
            this.setPosition(this.x, this.y + aint[1][1], this.z);
        }

        this.applySlowdown();
        Vec3d vec3d1 = this.snapPositionToRail(this.x, this.y, this.z);
        if (vec3d1 != null && vec3d != null) {
            double d14 = (vec3d.y - vec3d1.y) * 0.05;
            d5 = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            if (d5 > 0.0) {
                this.velocityX = this.velocityX / d5 * (d5 + d14);
                this.velocityZ = this.velocityZ / d5 * (d5 + d14);
            }

            this.setPosition(this.x, vec3d1.y, this.z);
        }

        int j = MathHelper.floor(this.x);
        int i = MathHelper.floor(this.z);
        if (j != pos.getX() || i != pos.getZ()) {
            d5 = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            this.velocityX = d5 * (j - pos.getX());
            this.velocityZ = d5 * (i - pos.getZ());
        }

        if (flag) {
            double d15 = Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
            if (d15 > 0.01) {
                double d16 = 0.06;
                this.velocityX = this.velocityX + this.velocityX / d15 * d16;
                this.velocityZ = this.velocityZ + this.velocityZ / d15 * d16;
            } else if (abstractrailblock$shape == AbstractRailBlock.Shape.EAST_WEST) {
                if (this.world.getBlockState(pos.west()).getBlock().isSolid()) {
                    this.velocityX = 0.02;
                } else if (this.world.getBlockState(pos.east()).getBlock().isSolid()) {
                    this.velocityX = -0.02;
                }
            } else if (abstractrailblock$shape == AbstractRailBlock.Shape.NORTH_SOUTH) {
                if (this.world.getBlockState(pos.north()).getBlock().isSolid()) {
                    this.velocityZ = 0.02;
                } else if (this.world.getBlockState(pos.south()).getBlock().isSolid()) {
                    this.velocityZ = -0.02;
                }
            }
        }
    }

    protected void applySlowdown() {
        if (this.rider != null) {
            this.velocityX *= 0.997F;
            this.velocityY *= 0.0;
            this.velocityZ *= 0.997F;
        } else {
            this.velocityX *= 0.96F;
            this.velocityY *= 0.0;
            this.velocityZ *= 0.96F;
        }
    }

    @Override
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        float f = this.width / 2.0F;
        float f1 = this.height;
        this.setShape(new Box(x - f, y, z - f, x + f, y + f1, z + f));
    }

    /**
     * This method is used to determine the minecart's render orientation, by computing a position along the rail slightly before and slightly after the minecart's actual position.
     */
    public Vec3d snapPositionToRailWithOffset(double x, double y, double z, double offset) {
        int i = MathHelper.floor(x);
        int j = MathHelper.floor(y);
        int k = MathHelper.floor(z);
        if (AbstractRailBlock.isRail(this.world, new BlockPos(i, j - 1, k))) {
            j--;
        }

        BlockState blockstate = this.world.getBlockState(new BlockPos(i, j, k));
        if (AbstractRailBlock.isRail(blockstate)) {
            AbstractRailBlock.Shape abstractrailblock$shape = blockstate.get(((AbstractRailBlock)blockstate.getBlock()).getShapeProperty());
            y = j;
            if (abstractrailblock$shape.isAscending()) {
                y = j + 1;
            }

            int[][] aint = ADJACENT_RAIL_POSITIONS_BY_SHAPE[abstractrailblock$shape.getId()];
            double d0 = aint[1][0] - aint[0][0];
            double d1 = aint[1][2] - aint[0][2];
            double d2 = Math.sqrt(d0 * d0 + d1 * d1);
            d0 /= d2;
            d1 /= d2;
            x += d0 * offset;
            z += d1 * offset;
            if (aint[0][1] != 0 && MathHelper.floor(x) - i == aint[0][0] && MathHelper.floor(z) - k == aint[0][2]) {
                y += aint[0][1];
            } else if (aint[1][1] != 0 && MathHelper.floor(x) - i == aint[1][0] && MathHelper.floor(z) - k == aint[1][2]) {
                y += aint[1][1];
            }

            return this.snapPositionToRail(x, y, z);
        } else {
            return null;
        }
    }

    public Vec3d snapPositionToRail(double x, double y, double z) {
        int i = MathHelper.floor(x);
        int j = MathHelper.floor(y);
        int k = MathHelper.floor(z);
        if (AbstractRailBlock.isRail(this.world, new BlockPos(i, j - 1, k))) {
            j--;
        }

        BlockState blockstate = this.world.getBlockState(new BlockPos(i, j, k));
        if (AbstractRailBlock.isRail(blockstate)) {
            AbstractRailBlock.Shape abstractrailblock$shape = blockstate.get(((AbstractRailBlock)blockstate.getBlock()).getShapeProperty());
            int[][] aint = ADJACENT_RAIL_POSITIONS_BY_SHAPE[abstractrailblock$shape.getId()];
            double d0 = 0.0;
            double d1 = i + 0.5 + aint[0][0] * 0.5;
            double d2 = j + 0.0625 + aint[0][1] * 0.5;
            double d3 = k + 0.5 + aint[0][2] * 0.5;
            double d4 = i + 0.5 + aint[1][0] * 0.5;
            double d5 = j + 0.0625 + aint[1][1] * 0.5;
            double d6 = k + 0.5 + aint[1][2] * 0.5;
            double d7 = d4 - d1;
            double d8 = (d5 - d2) * 2.0;
            double d9 = d6 - d3;
            if (d7 == 0.0) {
                x = i + 0.5;
                d0 = z - k;
            } else if (d9 == 0.0) {
                z = k + 0.5;
                d0 = x - i;
            } else {
                double d10 = x - d1;
                double d11 = z - d3;
                d0 = (d10 * d7 + d11 * d9) * 2.0;
            }

            x = d1 + d7 * d0;
            y = d2 + d8 * d0;
            z = d3 + d9 * d0;
            if (d8 < 0.0) {
                y++;
            }

            if (d8 > 0.0) {
                y += 0.5;
            }

            return new Vec3d(x, y, z);
        } else {
            return null;
        }
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        if (nbt.getBoolean("CustomDisplayTile")) {
            int i = nbt.getInt("DisplayData");
            if (nbt.contains("DisplayTile", 8)) {
                Block block = Block.byKey(nbt.getString("DisplayTile"));
                if (block == null) {
                    this.setDisplayBlock(Blocks.AIR.defaultState());
                } else {
                    this.setDisplayBlock(block.getStateFromMetadata(i));
                }
            } else {
                Block block1 = Block.byId(nbt.getInt("DisplayTile"));
                if (block1 == null) {
                    this.setDisplayBlock(Blocks.AIR.defaultState());
                } else {
                    this.setDisplayBlock(block1.getStateFromMetadata(i));
                }
            }

            this.setDisplayBlockOffset(nbt.getInt("DisplayOffset"));
        }

        if (nbt.contains("CustomName", 8) && nbt.getString("CustomName").length() > 0) {
            this.customName = nbt.getString("CustomName");
        }
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        if (this.hasCustomDisplayBlock()) {
            nbt.putBoolean("CustomDisplayTile", true);
            BlockState blockstate = this.getDisplayBlock();
            Identifier identifier = Block.REGISTRY.getKey(blockstate.getBlock());
            nbt.putString("DisplayTile", identifier == null ? "" : identifier.toString());
            nbt.putInt("DisplayData", blockstate.getBlock().getMetadataFromState(blockstate));
            nbt.putInt("DisplayOffset", this.getDisplayBlockOffset());
        }

        if (this.customName != null && this.customName.length() > 0) {
            nbt.putString("CustomName", this.customName);
        }
    }

    @Override
    public void push(Entity entity) {
        if (!this.world.isClient) {
            if (!entity.noClip && !this.noClip) {
                if (entity != this.rider) {
                    if (entity instanceof LivingEntity
                        && !(entity instanceof PlayerEntity)
                        && !(entity instanceof IronGolemEntity)
                        && this.getMinecartType() == MinecartEntity.Type.RIDEABLE
                        && this.velocityX * this.velocityX + this.velocityZ * this.velocityZ > 0.01
                        && this.rider == null
                        && entity.vehicle == null) {
                        entity.startRiding(this);
                    }

                    double d0 = entity.x - this.x;
                    double d1 = entity.z - this.z;
                    double d2 = d0 * d0 + d1 * d1;
                    if (d2 >= 1.0E-4F) {
                        d2 = MathHelper.sqrt(d2);
                        d0 /= d2;
                        d1 /= d2;
                        double d3 = 1.0 / d2;
                        if (d3 > 1.0) {
                            d3 = 1.0;
                        }

                        d0 *= d3;
                        d1 *= d3;
                        d0 *= 0.1F;
                        d1 *= 0.1F;
                        d0 *= 1.0F - this.pushSpeedReduction;
                        d1 *= 1.0F - this.pushSpeedReduction;
                        d0 *= 0.5;
                        d1 *= 0.5;
                        if (entity instanceof MinecartEntity) {
                            double d4 = entity.x - this.x;
                            double d5 = entity.z - this.z;
                            Vec3d vec3d = new Vec3d(d4, 0.0, d5).normalize();
                            Vec3d vec3d1 = new Vec3d(
                                    MathHelper.cos(this.yaw * (float) Math.PI / 180.0F), 0.0, MathHelper.sin(this.yaw * (float) Math.PI / 180.0F)
                                )
                                .normalize();
                            double d6 = Math.abs(vec3d.dot(vec3d1));
                            if (d6 < 0.8F) {
                                return;
                            }

                            double d7 = entity.velocityX + this.velocityX;
                            double d8 = entity.velocityZ + this.velocityZ;
                            if (((MinecartEntity)entity).getMinecartType() == MinecartEntity.Type.FURNACE
                                && this.getMinecartType() != MinecartEntity.Type.FURNACE) {
                                this.velocityX *= 0.2F;
                                this.velocityZ *= 0.2F;
                                this.addVelocity(entity.velocityX - d0, 0.0, entity.velocityZ - d1);
                                entity.velocityX *= 0.95F;
                                entity.velocityZ *= 0.95F;
                            } else if (((MinecartEntity)entity).getMinecartType() != MinecartEntity.Type.FURNACE
                                && this.getMinecartType() == MinecartEntity.Type.FURNACE) {
                                entity.velocityX *= 0.2F;
                                entity.velocityZ *= 0.2F;
                                entity.addVelocity(this.velocityX + d0, 0.0, this.velocityZ + d1);
                                this.velocityX *= 0.95F;
                                this.velocityZ *= 0.95F;
                            } else {
                                d7 /= 2.0;
                                d8 /= 2.0;
                                this.velocityX *= 0.2F;
                                this.velocityZ *= 0.2F;
                                this.addVelocity(d7 - d0, 0.0, d8 - d1);
                                entity.velocityX *= 0.2F;
                                entity.velocityZ *= 0.2F;
                                entity.addVelocity(d7 + d0, 0.0, d8 + d1);
                            }
                        } else {
                            this.addVelocity(-d0, 0.0, -d1);
                            entity.addVelocity(d0 / 4.0, 0.0, d1 / 4.0);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void lerpPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
        this.lerpSteps = steps + 2;
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

    public void setDamage(float daamage) {
        this.syncedData.update(19, daamage);
    }

    public float getDamage() {
        return this.syncedData.getFloat(19);
    }

    public void setDamagedTimer(int ticks) {
        this.syncedData.update(17, ticks);
    }

    public int getDamagedTimer() {
        return this.syncedData.getInt(17);
    }

    public void setDamagedSwingDirection(int dir) {
        this.syncedData.update(18, dir);
    }

    public int getDamagedSwingDirection() {
        return this.syncedData.getInt(18);
    }

    public abstract MinecartEntity.Type getMinecartType();

    public BlockState getDisplayBlock() {
        return !this.hasCustomDisplayBlock() ? this.getDefaultDisplayBlock() : Block.deserialize(this.getSyncedData().getInt(20));
    }

    public BlockState getDefaultDisplayBlock() {
        return Blocks.AIR.defaultState();
    }

    public int getDisplayBlockOffset() {
        return !this.hasCustomDisplayBlock() ? this.getDefaultDisplayBlockOffset() : this.getSyncedData().getInt(21);
    }

    public int getDefaultDisplayBlockOffset() {
        return 6;
    }

    public void setDisplayBlock(BlockState state) {
        this.getSyncedData().update(20, Block.serialize(state));
        this.setHasCustomDisplayBlock(true);
    }

    public void setDisplayBlockOffset(int offset) {
        this.getSyncedData().update(21, offset);
        this.setHasCustomDisplayBlock(true);
    }

    public boolean hasCustomDisplayBlock() {
        return this.getSyncedData().getByte(22) == 1;
    }

    public void setHasCustomDisplayBlock(boolean hasCustomDisplayBlock) {
        this.getSyncedData().update(22, (byte)(hasCustomDisplayBlock ? 1 : 0));
    }

    @Override
    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public String getName() {
        return this.customName != null ? this.customName : super.getName();
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null;
    }

    @Override
    public String getCustomName() {
        return this.customName;
    }

    @Override
    public Text getDisplayName() {
        if (this.hasCustomName()) {
            LiteralText literaltext = new LiteralText(this.customName);
            literaltext.getStyle().setHoverEvent(this.getHoverEvent());
            literaltext.getStyle().setInsertion(this.getUuid().toString());
            return literaltext;
        } else {
            TranslatableText translatabletext = new TranslatableText(this.getName());
            translatabletext.getStyle().setHoverEvent(this.getHoverEvent());
            translatabletext.getStyle().setInsertion(this.getUuid().toString());
            return translatabletext;
        }
    }

    public enum Type {
        RIDEABLE(0, "MinecartRideable"),
        CHEST(1, "MinecartChest"),
        FURNACE(2, "MinecartFurnace"),
        TNT(3, "MinecartTNT"),
        SPAWNER(4, "MinecartSpawner"),
        HOPPER(5, "MinecartHopper"),
        COMMAND_BLOCK(6, "MinecartCommandBlock");

        private static final Map<Integer, MinecartEntity.Type> BY_INDEX = Maps.newHashMap();
        private final int index;
        private final String name;

        Type(int index, String name) {
            this.index = index;
            this.name = name;
        }

        public int getIndex() {
            return this.index;
        }

        public String getName() {
            return this.name;
        }

        public static MinecartEntity.Type byIndex(int index) {
            MinecartEntity.Type minecartentity$type = BY_INDEX.get(index);
            return minecartentity$type == null ? RIDEABLE : minecartentity$type;
        }

        static {
            for (MinecartEntity.Type minecartentity$type : values()) {
                BY_INDEX.put(minecartentity$type.getIndex(), minecartentity$type);
            }
        }
    }
}
