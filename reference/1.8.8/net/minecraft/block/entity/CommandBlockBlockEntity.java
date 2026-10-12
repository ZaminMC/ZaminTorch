package net.minecraft.block.entity;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.command.source.CommandExecutor;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CommandBlockBlockEntity extends BlockEntity {
    private final CommandExecutor executor = new CommandExecutor() {
        @Override
        public BlockPos getCommandSourceBlockPos() {
            return CommandBlockBlockEntity.this.pos;
        }

        @Override
        public Vec3d getCommandSourcePos() {
            return new Vec3d(
                CommandBlockBlockEntity.this.pos.getX() + 0.5, CommandBlockBlockEntity.this.pos.getY() + 0.5, CommandBlockBlockEntity.this.pos.getZ() + 0.5
            );
        }

        @Override
        public World getCommandSourceWorld() {
            return CommandBlockBlockEntity.this.getWorld();
        }

        @Override
        public void setCommand(String command) {
            super.setCommand(command);
            CommandBlockBlockEntity.this.markDirty();
        }

        @Override
        public void markDirty() {
            CommandBlockBlockEntity.this.getWorld().notifyBlockChanged(CommandBlockBlockEntity.this.pos);
        }

        @Override
        public int getType() {
            return 0;
        }

        @Override
        public void writeInfo(ByteBuf buffer) {
            buffer.writeInt(CommandBlockBlockEntity.this.pos.getX());
            buffer.writeInt(CommandBlockBlockEntity.this.pos.getY());
            buffer.writeInt(CommandBlockBlockEntity.this.pos.getZ());
        }

        @Override
        public Entity asEntity() {
            return null;
        }
    };

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        this.executor.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.executor.readNbt(nbt);
    }

    @Override
    public Packet createUpdatePacket() {
        NbtCompound nbtcompound = new NbtCompound();
        this.writeNbt(nbtcompound);
        return new BlockEntityUpdateS2CPacket(this.pos, 2, nbtcompound);
    }

    @Override
    public boolean requireOpForPlacingWithNbt() {
        return true;
    }

    public CommandExecutor getCommandExecutor() {
        return this.executor;
    }

    public CommandResults getCommandResults() {
        return this.executor.getResults();
    }
}
