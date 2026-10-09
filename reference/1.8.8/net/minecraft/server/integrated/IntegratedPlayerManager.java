package net.minecraft.server.integrated;

import com.mojang.authlib.GameProfile;
import java.net.SocketAddress;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;

public class IntegratedPlayerManager extends PlayerManager {
    private NbtCompound playerData;

    public IntegratedPlayerManager(IntegratedServer server) {
        super(server);
        this.updateViewDistance(10);
    }

    @Override
    protected void save(ServerPlayerEntity player) {
        if (player.getName().equals(this.getServer().getUsername())) {
            this.playerData = new NbtCompound();
            player.writeNbtWithoutId(this.playerData);
        }

        super.save(player);
    }

    @Override
    public String canLogin(SocketAddress address, GameProfile profile) {
        return profile.getName().equalsIgnoreCase(this.getServer().getUsername()) && this.get(profile.getName()) != null
            ? "That name is already taken."
            : super.canLogin(address, profile);
    }

    public IntegratedServer getServer() {
        return (IntegratedServer)super.getServer();
    }

    @Override
    public NbtCompound getSingleplayerData() {
        return this.playerData;
    }
}
