package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.bukkit.BukkitAdapter;

import java.util.Map;
import java.util.UUID;

/**
 * The offline view for a name the engine has no live session under (§28.7's
 * object inventory): a pure identity holder — the engine's name-derived
 * identity model answers it with no network lookup (§28.2's
 * no-hidden-blocking rule). The live arms (online player, first/last
 * played) answer the honest constants until the persistence views land.
 */
public final class TorchOfflinePlayer implements org.bukkit.OfflinePlayer {

    private final BukkitAdapter adapter;
    private final String name;
    private final UUID uuid;

    public TorchOfflinePlayer(BukkitAdapter adapter, String name, UUID uuid) {
        this.adapter = adapter;
        this.name = name;
        this.uuid = uuid;
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public boolean isOp() {
        return false;
    }

    @Override
    public void setOp(boolean value) {
        throw adapter.unbridged("OfflinePlayer.setOp");
    }

    @Override
    public boolean isBanned() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBanned(boolean banned) {
        throw adapter.unbridged("OfflinePlayer.setBanned");
    }

    @Override
    public boolean isWhitelisted() {
        return true;
    }

    @Override
    public void setWhitelisted(boolean value) {
        throw adapter.unbridged("OfflinePlayer.setWhitelisted");
    }

    @Override
    public org.bukkit.entity.Player getPlayer() {
        return adapter.playerByExactName(name);
    }

    @Override
    public long getFirstPlayed() {
        return 0;
    }

    @Override
    public long getLastPlayed() {
        return 0;
    }

    @Override
    public boolean hasPlayedBefore() {
        return false;
    }

    @Override
    public org.bukkit.Location getBedSpawnLocation() {
        return null;
    }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("UUID", uuid.toString());
    }
}
