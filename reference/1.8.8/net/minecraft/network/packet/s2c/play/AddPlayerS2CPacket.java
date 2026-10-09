package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.data.SyncedData;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.MathHelper;

public class AddPlayerS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private UUID uuid;
    private int x;
    private int y;
    private int z;
    private byte yaw;
    private byte pitch;
    private int itemInHand;
    private SyncedData syncedData;
    private List<SyncedData.Entry> dataEntries;

    public AddPlayerS2CPacket() {
    }

    public AddPlayerS2CPacket(PlayerEntity player) {
        this.id = player.getNetworkId();
        this.uuid = player.getGameProfile().getId();
        this.x = MathHelper.floor(player.x * 32.0);
        this.y = MathHelper.floor(player.y * 32.0);
        this.z = MathHelper.floor(player.z * 32.0);
        this.yaw = (byte)(player.yaw * 256.0F / 360.0F);
        this.pitch = (byte)(player.pitch * 256.0F / 360.0F);
        ItemStack itemstack = player.inventory.getSelectedItem();
        this.itemInHand = itemstack == null ? 0 : Item.getId(itemstack.getItem());
        this.syncedData = player.getSyncedData();
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.uuid = buffer.readUuid();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.yaw = buffer.readByte();
        this.pitch = buffer.readByte();
        this.itemInHand = buffer.readShort();
        this.dataEntries = SyncedData.read(buffer);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeUuid(this.uuid);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeByte(this.yaw);
        buffer.writeByte(this.pitch);
        buffer.writeShort(this.itemInHand);
        this.syncedData.write(buffer);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleAddPlayer(this);
    }

    public List<SyncedData.Entry> getDataEntries() {
        if (this.dataEntries == null) {
            this.dataEntries = this.syncedData.getAll();
        }

        return this.dataEntries;
    }

    public int getId() {
        return this.id;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public byte getYaw() {
        return this.yaw;
    }

    public byte getPitch() {
        return this.pitch;
    }

    public int getMainHandItem() {
        return this.itemInHand;
    }
}
