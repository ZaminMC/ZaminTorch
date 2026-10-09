package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.entity.living.player.PlayerAbilities;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class PlayerAbilitiesC2SPacket implements Packet<ServerPlayPacketHandler> {
    private boolean invulnerable;
    private boolean flying;
    private boolean allowFlying;
    private boolean creativeMode;
    private float flySpeed;
    private float walkSpeed;

    public PlayerAbilitiesC2SPacket() {
    }

    public PlayerAbilitiesC2SPacket(PlayerAbilities abilities) {
        this.setInvulnerable(abilities.invulnerable);
        this.setFlying(abilities.flying);
        this.setAllowFlying(abilities.canFly);
        this.setCreativeMode(abilities.creativeMode);
        this.setFlySpeed(abilities.getFlySpeed());
        this.setWalkSpeed(abilities.getWalkSpeed());
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        byte b0 = buffer.readByte();
        this.setInvulnerable((b0 & 1) > 0);
        this.setFlying((b0 & 2) > 0);
        this.setAllowFlying((b0 & 4) > 0);
        this.setCreativeMode((b0 & 8) > 0);
        this.setFlySpeed(buffer.readFloat());
        this.setWalkSpeed(buffer.readFloat());
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        byte b0 = 0;
        if (this.isInvulnerable()) {
            b0 = (byte)(b0 | 1);
        }

        if (this.isFlying()) {
            b0 = (byte)(b0 | 2);
        }

        if (this.allowsFlying()) {
            b0 = (byte)(b0 | 4);
        }

        if (this.isCreativeMode()) {
            b0 = (byte)(b0 | 8);
        }

        buffer.writeByte(b0);
        buffer.writeFloat(this.flySpeed);
        buffer.writeFloat(this.walkSpeed);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handlePlayerAbilities(this);
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public boolean isFlying() {
        return this.flying;
    }

    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    public boolean allowsFlying() {
        return this.allowFlying;
    }

    public void setAllowFlying(boolean allowFlying) {
        this.allowFlying = allowFlying;
    }

    public boolean isCreativeMode() {
        return this.creativeMode;
    }

    public void setCreativeMode(boolean creativeMode) {
        this.creativeMode = creativeMode;
    }

    public void setFlySpeed(float flySpeed) {
        this.flySpeed = flySpeed;
    }

    public void setWalkSpeed(float walkSpeed) {
        this.walkSpeed = walkSpeed;
    }
}
