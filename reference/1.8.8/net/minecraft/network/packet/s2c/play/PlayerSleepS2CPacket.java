package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlayerSleepS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private BlockPos pos;

    public PlayerSleepS2CPacket() {
    }

    public PlayerSleepS2CPacket(PlayerEntity player, BlockPos pos) {
        this.id = player.getNetworkId();
        this.pos = pos;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeBlockPos(this.pos);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handlePlayerSleep(this);
    }

    public PlayerEntity getPlayer(World world) {
        return (PlayerEntity)world.getEntity(this.id);
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
