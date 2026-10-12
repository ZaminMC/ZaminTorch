package net.minecraft.server;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

public class WhitelistEntry extends StoredUserEntry<GameProfile> {
    public WhitelistEntry(GameProfile profile) {
        super(profile);
    }

    public WhitelistEntry(JsonObject json) {
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
