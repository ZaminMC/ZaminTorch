package net.minecraft.entity;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.resource.Identifier;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class FallingBlockEntity extends Entity {
    private BlockState state;
    public int fallingTicks;
    public boolean dropping = true;
    private boolean brokeAnvil;
    private boolean hurtingEntities;
    private int maxFallHurt = 40;
    private float fallHurtAmount = 2.0F;
    public NbtCompound nbt;

    public FallingBlockEntity(World world) {
        super(world);
    }

    public FallingBlockEntity(World world, double x, double y, double z, BlockState state) {
        super(world);
        this.state = state;
        this.blocksBuilding = true;
        this.setSize(0.98F, 0.98F);
        this.setPosition(x, y, z);
        this.velocityX = 0.0;
        this.velocityY = 0.0;
        this.velocityZ = 0.0;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    public boolean hasCollision() {
        return !this.removed;
    }

    @Override
    public void tick() {
        Block block = this.state.getBlock();
        if (block.getMaterial() == Material.AIR) {
            this.remove();
        } else {
            this.lastX = this.x;
            this.lastY = this.y;
            this.lastZ = this.z;
            if (this.fallingTicks++ == 0) {
                BlockPos blockpos = new BlockPos(this);
                if (this.world.getBlockState(blockpos).getBlock() == block) {
                    this.world.removeBlock(blockpos);
                } else if (!this.world.isClient) {
                    this.remove();
                    return;
                }
            }

            this.velocityY -= 0.04F;
            this.move(this.velocityX, this.velocityY, this.velocityZ);
            this.velocityX *= 0.98F;
            this.velocityY *= 0.98F;
            this.velocityZ *= 0.98F;
            if (!this.world.isClient) {
                BlockPos blockpos1 = new BlockPos(this);
                if (this.onGround) {
                    this.velocityX *= 0.7F;
                    this.velocityZ *= 0.7F;
                    this.velocityY *= -0.5;
                    if (this.world.getBlockState(blockpos1).getBlock() != Blocks.MOVING_BLOCK) {
                        this.remove();
                        if (!this.brokeAnvil) {
                            if (this.world.canPlace(block, blockpos1, true, Direction.UP, null, null)
                                && !FallingBlock.canFallThrough(this.world, blockpos1.down())
                                && this.world.setBlockState(blockpos1, this.state, 3)) {
                                if (block instanceof FallingBlock) {
                                    ((FallingBlock)block).onTickFallingBlockEntity(this.world, blockpos1);
                                }

                                if (this.nbt != null && block instanceof BlockEntityProvider) {
                                    BlockEntity blockentity = this.world.getBlockEntity(blockpos1);
                                    if (blockentity != null) {
                                        NbtCompound nbtcompound = new NbtCompound();
                                        blockentity.writeNbt(nbtcompound);

                                        for (String s : this.nbt.getKeys()) {
                                            NbtElement nbtelement = this.nbt.get(s);
                                            if (!s.equals("x") && !s.equals("y") && !s.equals("z")) {
                                                nbtcompound.put(s, nbtelement.copy());
                                            }
                                        }

                                        blockentity.readNbt(nbtcompound);
                                        blockentity.markDirty();
                                    }
                                }
                            } else if (this.dropping && this.world.getGameRules().getBoolean("doEntityDrops")) {
                                this.dropItem(new ItemStack(block, 1, block.getDropItemMetadata(this.state)), 0.0F);
                            }
                        }
                    }
                } else if (this.fallingTicks > 100 && !this.world.isClient && (blockpos1.getY() < 1 || blockpos1.getY() > 256) || this.fallingTicks > 600) {
                    if (this.dropping && this.world.getGameRules().getBoolean("doEntityDrops")) {
                        this.dropItem(new ItemStack(block, 1, block.getDropItemMetadata(this.state)), 0.0F);
                    }

                    this.remove();
                }
            }
        }
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
        Block block = this.state.getBlock();
        if (this.hurtingEntities) {
            int i = MathHelper.ceil(distance - 1.0F);
            if (i > 0) {
                List<Entity> list = Lists.newArrayList(this.world.getEntities(this, this.getShape()));
                boolean flag = block == Blocks.ANVIL;
                DamageSource damagesource = flag ? DamageSource.ANVIL : DamageSource.FALLING_BLOCK;

                for (Entity entity : list) {
                    entity.takeDamage(damagesource, Math.min(MathHelper.floor(i * this.fallHurtAmount), this.maxFallHurt));
                }

                if (flag && this.random.nextFloat() < 0.05F + i * 0.05) {
                    int j = this.state.get(AnvilBlock.DAMAGE);
                    if (++j > 2) {
                        this.brokeAnvil = true;
                    } else {
                        this.state = this.state.set(AnvilBlock.DAMAGE, j);
                    }
                }
            }
        }
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        Block block = this.state != null ? this.state.getBlock() : Blocks.AIR;
        Identifier identifier = Block.REGISTRY.getKey(block);
        nbt.putString("Block", identifier == null ? "" : identifier.toString());
        nbt.putByte("Data", (byte)block.getMetadataFromState(this.state));
        nbt.putByte("Time", (byte)this.fallingTicks);
        nbt.putBoolean("DropItem", this.dropping);
        nbt.putBoolean("HurtEntities", this.hurtingEntities);
        nbt.putFloat("FallHurtAmount", this.fallHurtAmount);
        nbt.putInt("FallHurtMax", this.maxFallHurt);
        if (this.nbt != null) {
            nbt.put("TileEntityData", this.nbt);
        }
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        int i = nbt.getByte("Data") & 255;
        if (nbt.contains("Block", 8)) {
            this.state = Block.byKey(nbt.getString("Block")).getStateFromMetadata(i);
        } else if (nbt.contains("TileID", 99)) {
            this.state = Block.byId(nbt.getInt("TileID")).getStateFromMetadata(i);
        } else {
            this.state = Block.byId(nbt.getByte("Tile") & 255).getStateFromMetadata(i);
        }

        this.fallingTicks = nbt.getByte("Time") & 255;
        Block block = this.state.getBlock();
        if (nbt.contains("HurtEntities", 99)) {
            this.hurtingEntities = nbt.getBoolean("HurtEntities");
            this.fallHurtAmount = nbt.getFloat("FallHurtAmount");
            this.maxFallHurt = nbt.getInt("FallHurtMax");
        } else if (block == Blocks.ANVIL) {
            this.hurtingEntities = true;
        }

        if (nbt.contains("DropItem", 99)) {
            this.dropping = nbt.getBoolean("DropItem");
        }

        if (nbt.contains("TileEntityData", 10)) {
            this.nbt = nbt.getCompound("TileEntityData");
        }

        if (block == null || block.getMaterial() == Material.AIR) {
            this.state = Blocks.SAND.defaultState();
        }
    }

    public World getWorld() {
        return this.world;
    }

    public void setHurtingEntities(boolean hurtingEntities) {
        this.hurtingEntities = hurtingEntities;
    }

    @Override
    public boolean shouldRenderOnFire() {
        return false;
    }

    @Override
    public void populateCrashReport(CrashReportCategory section) {
        super.populateCrashReport(section);
        if (this.state != null) {
            Block block = this.state.getBlock();
            section.add("Immitating block ID", Block.getId(block));
            section.add("Immitating block data", block.getMetadataFromState(this.state));
        }
    }

    public BlockState getBlock() {
        return this.state;
    }
}
