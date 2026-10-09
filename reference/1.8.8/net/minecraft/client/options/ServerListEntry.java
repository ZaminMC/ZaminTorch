package net.minecraft.client.options;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class ServerListEntry {
    public String name;
    public String ip;
    public String status;
    public String motd;
    public long ping;
    public int protocol = 47;
    public String version = "1.8.8";
    public boolean loaded;
    public String onlinePlayers;
    private ServerListEntry.ResourcePackStatus resourcePackStatus = ServerListEntry.ResourcePackStatus.PROMPT;
    private String icon;
    private boolean local;

    public ServerListEntry(String name, String address, boolean local) {
        this.name = name;
        this.ip = address;
        this.local = local;
    }

    public NbtCompound toNbt() {
        NbtCompound nbtcompound = new NbtCompound();
        nbtcompound.putString("name", this.name);
        nbtcompound.putString("ip", this.ip);
        if (this.icon != null) {
            nbtcompound.putString("icon", this.icon);
        }

        if (this.resourcePackStatus == ServerListEntry.ResourcePackStatus.ENABLED) {
            nbtcompound.putBoolean("acceptTextures", true);
        } else if (this.resourcePackStatus == ServerListEntry.ResourcePackStatus.DISABLED) {
            nbtcompound.putBoolean("acceptTextures", false);
        }

        return nbtcompound;
    }

    public ServerListEntry.ResourcePackStatus getResourcePackStatus() {
        return this.resourcePackStatus;
    }

    public void setResourcePackStatus(ServerListEntry.ResourcePackStatus status) {
        this.resourcePackStatus = status;
    }

    public static ServerListEntry fromNbt(NbtCompound nbt) {
        ServerListEntry serverlistentry = new ServerListEntry(nbt.getString("name"), nbt.getString("ip"), false);
        if (nbt.contains("icon", 8)) {
            serverlistentry.setIcon(nbt.getString("icon"));
        }

        if (nbt.contains("acceptTextures", 1)) {
            if (nbt.getBoolean("acceptTextures")) {
                serverlistentry.setResourcePackStatus(ServerListEntry.ResourcePackStatus.ENABLED);
            } else {
                serverlistentry.setResourcePackStatus(ServerListEntry.ResourcePackStatus.DISABLED);
            }
        } else {
            serverlistentry.setResourcePackStatus(ServerListEntry.ResourcePackStatus.PROMPT);
        }

        return serverlistentry;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isLocal() {
        return this.local;
    }

    public void set(ServerListEntry server) {
        this.ip = server.ip;
        this.name = server.name;
        this.setResourcePackStatus(server.getResourcePackStatus());
        this.icon = server.icon;
        this.local = server.local;
    }

    public enum ResourcePackStatus {
        ENABLED("enabled"),
        DISABLED("disabled"),
        PROMPT("prompt");

        private final Text message;

        ResourcePackStatus(String id) {
            this.message = new TranslatableText("addServer.resourcePack." + id);
        }

        public Text getMessage() {
            return this.message;
        }
    }
}
