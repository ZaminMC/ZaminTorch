package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;
import net.minecraft.util.math.BlockPos;

public class PlayerUseC2SPacket implements Packet<ServerPlayPacketHandler> {
    private static final BlockPos INVALID_POS = new BlockPos(-1, -1, -1);
    private BlockPos pos;
    /**
     * The face of the block on which the interaction takes place. Can be set to 255 if the
     * player is using an item while not looking at a block.
     */
    private int face;
    private ItemStack itemInHand;
    private float faceX;
    private float faceY;
    private float faceZ;

    public PlayerUseC2SPacket() {
    }

    public PlayerUseC2SPacket(ItemStack itemInHand) {
        this(INVALID_POS, 255, itemInHand, 0.0F, 0.0F, 0.0F);
    }

    public PlayerUseC2SPacket(BlockPos pos, int face, ItemStack itemInHand, float faceX, float faceY, float faceZ) {
        this.pos = pos;
        this.face = face;
        this.itemInHand = itemInHand != null ? itemInHand.copy() : null;
        this.faceX = faceX;
        this.faceY = faceY;
        this.faceZ = faceZ;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.pos = buffer.readBlockPos();
        this.face = buffer.readUnsignedByte();
        this.itemInHand = buffer.readItem();
        this.faceX = buffer.readUnsignedByte() / 16.0F;
        this.faceY = buffer.readUnsignedByte() / 16.0F;
        this.faceZ = buffer.readUnsignedByte() / 16.0F;
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBlockPos(this.pos);
        buffer.writeByte(this.face);
        buffer.writeItem(this.itemInHand);
        buffer.writeByte((int)(this.faceX * 16.0F));
        buffer.writeByte((int)(this.faceY * 16.0F));
        buffer.writeByte((int)(this.faceZ * 16.0F));
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handlePlayerUse(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getFace() {
        return this.face;
    }

    public ItemStack getItemInHand() {
        return this.itemInHand;
    }

    public float getFaceX() {
        return this.faceX;
    }

    public float getFaceY() {
        return this.faceY;
    }

    public float getFaceZ() {
        return this.faceZ;
    }
}
