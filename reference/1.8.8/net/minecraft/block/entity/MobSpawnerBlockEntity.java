package net.minecraft.block.entity;

import net.minecraft.block.Blocks;
import net.minecraft.block.spawner.MobSpawner;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MobSpawnerBlockEntity extends BlockEntity implements Tickable {
    private final MobSpawner spawner = new MobSpawner() {
        @Override
        public void broacastEvent(int event) {
            MobSpawnerBlockEntity.this.world.addBlockEvent(MobSpawnerBlockEntity.this.pos, Blocks.MOB_SPAWNER, event, 0);
        }

        @Override
        public World getWorld() {
            return MobSpawnerBlockEntity.this.world;
        }

        @Override
        public BlockPos getPos() {
            return MobSpawnerBlockEntity.this.pos;
        }

        @Override
        public void setNextEntry(MobSpawner.Entry entry) {
            super.setNextEntry(entry);
            if (this.getWorld() != null) {
                this.getWorld().notifyBlockChanged(MobSpawnerBlockEntity.this.pos);
            }
        }
    };

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.spawner.readNbt(nbt);
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        this.spawner.writeNbt(nbt);
    }

    @Override
    public void tick() {
        this.spawner.tick();
    }

    @Override
    public Packet createUpdatePacket() {
        NbtCompound nbtcompound = new NbtCompound();
        this.writeNbt(nbtcompound);
        nbtcompound.remove("SpawnPotentials");
        return new BlockEntityUpdateS2CPacket(this.pos, 1, nbtcompound);
    }

    @Override
    public boolean doEvent(int type, int data) {
        return this.spawner.doEvent(type) || super.doEvent(type, data);
    }

    @Override
    public boolean requireOpForPlacingWithNbt() {
        return true;
    }

    public MobSpawner getSpawner() {
        return this.spawner;
    }
}
