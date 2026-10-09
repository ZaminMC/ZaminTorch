package net.minecraft.server;

import com.google.gson.JsonObject;
import java.util.Date;

public class IpBanEntry extends BanEntry<String> {
    public IpBanEntry(String ip) {
        this(ip, null, null, null, null);
    }

    public IpBanEntry(String ip, Date startDate, String source, Date expirationDate, String reason) {
        super(ip, startDate, source, expirationDate, reason);
    }

    public IpBanEntry(JsonObject json) {
        super(deserialize(json), json);
    }

    private static String deserialize(JsonObject json) {
        return json.has("ip") ? json.get("ip").getAsString() : null;
    }

    @Override
    protected void serialize(JsonObject json) {
        if (this.getUser() != null) {
            json.addProperty("ip", this.getUser());
            super.serialize(json);
        }
    }
}
