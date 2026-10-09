package net.minecraft.server;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.Date;
import java.util.UUID;

public class PlayerBanEntry extends BanEntry<GameProfile> {
    public PlayerBanEntry(GameProfile profile) {
        this(profile, null, null, null, null);
    }

    public PlayerBanEntry(GameProfile profile, Date startDate, String source, Date expirationDate, String reason) {
        super(profile, expirationDate, source, expirationDate, reason);
    }

    public PlayerBanEntry(JsonObject json) {
        super(deserialize(json), json);
    }

    @Override
    protected void serialize(JsonObject json) {
        if (this.getUser() != null) {
            json.addProperty("uuid", this.getUser().getId() == null ? "" : this.getUser().getId().toString());
            json.addProperty("name", this.getUser().getName());
            super.serialize(json);
        }
    }

    private static GameProfile deserialize(JsonObject json) {
        if (json.has("uuid") && json.has("name")) {
            String s = json.get("uuid").getAsString();

            UUID uuid;
            try {
                uuid = UUID.fromString(s);
            } catch (Throwable throwable) {
                return null;
            }

            return new GameProfile(uuid, json.get("name").getAsString());
        } else {
            return null;
        }
    }
}
