package net.minecraft.server;

import com.google.gson.JsonObject;
import java.io.File;
import java.net.SocketAddress;

public class IpBans extends StoredUserList<String, IpBanEntry> {
    public IpBans(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<String> deserialize(JsonObject json) {
        return new IpBanEntry(json);
    }

    public boolean isBanned(SocketAddress adress) {
        String s = this.getIp(adress);
        return this.contains(s);
    }

    public IpBanEntry get(SocketAddress address) {
        String s = this.getIp(address);
        return this.get(s);
    }

    private String getIp(SocketAddress address) {
        String s = address.toString();
        if (s.contains("/")) {
            s = s.substring(s.indexOf(47) + 1);
        }

        if (s.contains(":")) {
            s = s.substring(0, s.indexOf(58));
        }

        return s;
    }
}
