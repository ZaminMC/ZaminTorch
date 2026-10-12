package net.minecraft.entity.vehicle;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.source.CommandExecutor;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CommandBlockMinecartEntity extends MinecartEntity {
    private final CommandExecutor executor = new CommandExecutor() {
        @Override
        public void markDirty() {
            CommandBlockMinecartEntity.this.getSyncedData().update(23, this.getCommand());
            CommandBlockMinecartEntity.this.getSyncedData().update(24, Text.Serializer.toJson(this.getLastOutput()));
        }

        @Override
        public int getType() {
            return 1;
        }

        @Override
        public void writeInfo(ByteBuf buffer) {
            buffer.writeInt(CommandBlockMinecartEntity.this.getNetworkId());
        }

        @Override
        public BlockPos getCommandSourceBlockPos() {
            return new BlockPos(CommandBlockMinecartEntity.this.x, CommandBlockMinecartEntity.this.y + 0.5, CommandBlockMinecartEntity.this.z);
        }

        @Override
        public Vec3d getCommandSourcePos() {
            return new Vec3d(CommandBlockMinecartEntity.this.x, CommandBlockMinecartEntity.this.y, CommandBlockMinecartEntity.this.z);
        }

        @Override
        public World getCommandSourceWorld() {
            return CommandBlockMinecartEntity.this.world;
        }

        @Override
        public Entity asEntity() {
            return CommandBlockMinecartEntity.this;
        }
    };
    private int lastExecuted = 0;

    public CommandBlockMinecartEntity(World world) {
        super(world);
    }

    public CommandBlockMinecartEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.getSyncedData().register(23, "");
        this.getSyncedData().register(24, "");
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.executor.readNbt(nbt);
        this.getSyncedData().update(23, this.getCommandExecutor().getCommand());
        this.getSyncedData().update(24, Text.Serializer.toJson(this.getCommandExecutor().getLastOutput()));
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        this.executor.writeNbt(nbt);
    }

    @Override
    public MinecartEntity.Type getMinecartType() {
        return MinecartEntity.Type.COMMAND_BLOCK;
    }

    @Override
    public BlockState getDefaultDisplayBlock() {
        return Blocks.COMMAND_BLOCK.defaultState();
    }

    public CommandExecutor getCommandExecutor() {
        return this.executor;
    }

    @Override
    public void onActivatorRail(int x, int y, int z, boolean powered) {
        if (powered && this.ticks - this.lastExecuted >= 4) {
            this.getCommandExecutor().run(this.world);
            this.lastExecuted = this.ticks;
        }
    }

    @Override
    public boolean interact(PlayerEntity player) {
        this.executor.openScreen(player);
        return false;
    }

    @Override
    public void onDataValueChanged(int id) {
        super.onDataValueChanged(id);
        if (id == 24) {
            try {
                this.executor.setLastOutput(Text.Serializer.fromJson(this.getSyncedData().getString(24)));
            } catch (Throwable throwable) {
            }
        } else if (id == 23) {
            this.executor.setCommand(this.getSyncedData().getString(23));
        }
    }
}
