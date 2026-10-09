package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AddPaintingS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private BlockPos pos;
    private Direction facing;
    private String motive;

    public AddPaintingS2CPacket() {
    }

    public AddPaintingS2CPacket(PaintingEntity painting) {
        this.id = painting.getNetworkId();
        this.pos = painting.getBlockPos();
        this.facing = painting.dir;
        this.motive = painting.motive.name;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.motive = buffer.readString(PaintingEntity.Motive.SKULL_AND_ROSES_LENGTH);
        this.pos = buffer.readBlockPos();
        this.facing = Direction.byIdHorizontal(buffer.readUnsignedByte());
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeString(this.motive);
        buffer.writeBlockPos(this.pos);
        buffer.writeByte(this.facing.getIdHorizontal());
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleAddPainting(this);
    }

    public int getId() {
        return this.id;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Direction getFacing() {
        return this.facing;
    }

    public String getMotive() {
        return this.motive;
    }
}
