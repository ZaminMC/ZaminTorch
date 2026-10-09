package net.minecraft.entity.global;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

public class LightningBoltEntity extends GlobalEntity {
    /**
     * Determines what actions this lightning bolt performs each tick.
     * <br>- 2: play sounds.
     * <br>- 1/0: damage entities.
     * <br>- &lt; 0: attempt to summon fire
     * <br>
     * Once the lightning bolt has worked through all its attempts it is removed.
     */
    private int stage;
    public long seed;
    /**
     * The number of attempts this lightning bolt will do to summon fire.
     */
    private int fireAttempts;

    public LightningBoltEntity(World world, double x, double y, double z) {
        super(world);
        this.setPositionAndAngles(x, y, z, 0.0F, 0.0F);
        this.stage = 2;
        this.seed = this.random.nextLong();
        this.fireAttempts = this.random.nextInt(3) + 1;
        BlockPos blockpos = new BlockPos(this);
        if (!world.isClient
            && world.getGameRules().getBoolean("doFireTick")
            && (world.getDifficulty() == Difficulty.NORMAL || world.getDifficulty() == Difficulty.HARD)
            && world.isAreaLoaded(blockpos, 10)) {
            if (world.getBlockState(blockpos).getBlock().getMaterial() == Material.AIR && Blocks.FIRE.canBePlaced(world, blockpos)) {
                world.setBlockState(blockpos, Blocks.FIRE.defaultState());
            }

            for (int i = 0; i < 4; i++) {
                BlockPos blockpos1 = blockpos.add(this.random.nextInt(3) - 1, this.random.nextInt(3) - 1, this.random.nextInt(3) - 1);
                if (world.getBlockState(blockpos1).getBlock().getMaterial() == Material.AIR && Blocks.FIRE.canBePlaced(world, blockpos1)) {
                    world.setBlockState(blockpos1, Blocks.FIRE.defaultState());
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.stage == 2) {
            this.world.playSound(this.x, this.y, this.z, "ambient.weather.thunder", 10000.0F, 0.8F + this.random.nextFloat() * 0.2F);
            this.world.playSound(this.x, this.y, this.z, "random.explode", 2.0F, 0.5F + this.random.nextFloat() * 0.2F);
        }

        this.stage--;
        if (this.stage < 0) {
            if (this.fireAttempts == 0) {
                this.remove();
            } else if (this.stage < -this.random.nextInt(10)) {
                this.fireAttempts--;
                this.stage = 1;
                this.seed = this.random.nextLong();
                BlockPos blockpos = new BlockPos(this);
                if (!this.world.isClient
                    && this.world.getGameRules().getBoolean("doFireTick")
                    && this.world.isAreaLoaded(blockpos, 10)
                    && this.world.getBlockState(blockpos).getBlock().getMaterial() == Material.AIR
                    && Blocks.FIRE.canBePlaced(this.world, blockpos)) {
                    this.world.setBlockState(blockpos, Blocks.FIRE.defaultState());
                }
            }
        }

        if (this.stage >= 0) {
            if (this.world.isClient) {
                this.world.setLightningCooldown(2);
            } else {
                double d0 = 3.0;
                List<Entity> list = this.world.getEntities(this, new Box(this.x - d0, this.y - d0, this.z - d0, this.x + d0, this.y + 6.0 + d0, this.z + d0));

                for (int i = 0; i < list.size(); i++) {
                    Entity entity = list.get(i);
                    entity.struckByLightning(this);
                }
            }
        }
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
    }
}
