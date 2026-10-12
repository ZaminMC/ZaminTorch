package net.minecraft.network.packet.s2c.play;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class EntityAttributesS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private final List<EntityAttributesS2CPacket.Entry> entries = Lists.newArrayList();

    public EntityAttributesS2CPacket() {
    }

    public EntityAttributesS2CPacket(int id, Collection<EntityAttributeInstance> attributes) {
        this.id = id;

        for (EntityAttributeInstance entityattributeinstance : attributes) {
            this.entries
                .add(
                    new EntityAttributesS2CPacket.Entry(
                        entityattributeinstance.getAttribute().getName(), entityattributeinstance.getBase(), entityattributeinstance.getModifiers()
                    )
                );
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        int i = buffer.readInt();

        for (int j = 0; j < i; j++) {
            String s = buffer.readString(64);
            double d0 = buffer.readDouble();
            List<AttributeModifier> list = Lists.newArrayList();
            int k = buffer.readVarInt();

            for (int l = 0; l < k; l++) {
                UUID uuid = buffer.readUuid();
                list.add(new AttributeModifier(uuid, "Unknown synced attribute modifier", buffer.readDouble(), buffer.readByte()));
            }

            this.entries.add(new EntityAttributesS2CPacket.Entry(s, d0, list));
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeInt(this.entries.size());

        for (EntityAttributesS2CPacket.Entry entityattributess2cpacket$entry : this.entries) {
            buffer.writeString(entityattributess2cpacket$entry.getId());
            buffer.writeDouble(entityattributess2cpacket$entry.getBaseValue());
            buffer.writeVarInt(entityattributess2cpacket$entry.getModifiers().size());

            for (AttributeModifier attributemodifier : entityattributess2cpacket$entry.getModifiers()) {
                buffer.writeUuid(attributemodifier.getId());
                buffer.writeDouble(attributemodifier.get());
                buffer.writeByte(attributemodifier.getOperation());
            }
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityAttributes(this);
    }

    public int getEntityId() {
        return this.id;
    }

    public List<EntityAttributesS2CPacket.Entry> getEntries() {
        return this.entries;
    }

    public class Entry {
        private final String id;
        private final double baseValue;
        private final Collection<AttributeModifier> modifiers;

        public Entry(String id, double baseValue, Collection<AttributeModifier> modifiers) {
            this.id = id;
            this.baseValue = baseValue;
            this.modifiers = modifiers;
        }

        public String getId() {
            return this.id;
        }

        public double getBaseValue() {
            return this.baseValue;
        }

        public Collection<AttributeModifier> getModifiers() {
            return this.modifiers;
        }
    }
}
