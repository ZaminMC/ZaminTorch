package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.damage.DamageTracker;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class PlayerCombatS2CPacket implements Packet<ClientPlayPacketHandler> {
    public PlayerCombatS2CPacket.Event event;
    public int playerId;
    public int killerId;
    public int duration;
    public String message;

    public PlayerCombatS2CPacket() {
    }

    public PlayerCombatS2CPacket(DamageTracker tracker, PlayerCombatS2CPacket.Event event) {
        this.event = event;
        LivingEntity livingentity = tracker.getInitialAttacker();
        switch (event) {
            case END_COMBAT:
                this.duration = tracker.getDuration();
                this.killerId = livingentity == null ? -1 : livingentity.getNetworkId();
                break;
            case ENTITY_DIED:
                this.playerId = tracker.getPlayer().getNetworkId();
                this.killerId = livingentity == null ? -1 : livingentity.getNetworkId();
                this.message = tracker.getDeathMessage().getString();
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.event = buffer.readEnum(PlayerCombatS2CPacket.Event.class);
        if (this.event == PlayerCombatS2CPacket.Event.END_COMBAT) {
            this.duration = buffer.readVarInt();
            this.killerId = buffer.readInt();
        } else if (this.event == PlayerCombatS2CPacket.Event.ENTITY_DIED) {
            this.playerId = buffer.readVarInt();
            this.killerId = buffer.readInt();
            this.message = buffer.readString(32767);
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeEnum(this.event);
        if (this.event == PlayerCombatS2CPacket.Event.END_COMBAT) {
            buffer.writeVarInt(this.duration);
            buffer.writeInt(this.killerId);
        } else if (this.event == PlayerCombatS2CPacket.Event.ENTITY_DIED) {
            buffer.writeVarInt(this.playerId);
            buffer.writeInt(this.killerId);
            buffer.writeString(this.message);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handlePlayerCombat(this);
    }

    public enum Event {
        ENTER_COMBAT,
        END_COMBAT,
        ENTITY_DIED;
    }
}
