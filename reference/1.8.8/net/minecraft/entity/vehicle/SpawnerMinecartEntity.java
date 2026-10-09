package net.minecraft.entity.vehicle;

import net.minecraft.block.Blocks;
import net.minecraft.block.spawner.MobSpawner;
import net.minecraft.block.state.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SpawnerMinecartEntity extends MinecartEntity {
    private final MobSpawner spawner = new MobSpawner() {
        @Override
        public void broacastEvent(int event) {
            SpawnerMinecartEntity.this.world.doEntityEvent(SpawnerMinecartEntity.this, (byte)event);
        }

        @Override
        public World getWorld() {
            return SpawnerMinecartEntity.this.world;
        }

        @Override
        public BlockPos getPos() {
            return new BlockPos(SpawnerMinecartEntity.this);
        }
    };

    public SpawnerMinecartEntity(World world) {
        super(world);
    }

    public SpawnerMinecartEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    public MinecartEntity.Type getMinecartType() {
        return MinecartEntity.Type.SPAWNER;
    }

    @Override
    public BlockState getDefaultDisplayBlock() {
        return Blocks.MOB_SPAWNER.defaultState();
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.spawner.readNbt(nbt);
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        this.spawner.writeNbt(nbt);
    }

    @Override
    public void doEvent(byte event) {
        this.spawner.doEvent(event);
    }

    @Override
    public void tick() {
        super.tick();
        this.spawner.tick();
    }

    public MobSpawner getSpawner() {
        return this.spawner;
    }
}
