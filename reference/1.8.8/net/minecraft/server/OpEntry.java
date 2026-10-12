package net.minecraft.server;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

public class OpEntry extends StoredUserEntry<GameProfile> {
    private final int permissionLevel;
    private final boolean bypassesPlayerLimit;

    public OpEntry(GameProfile profile, int permissionLevel, boolean bypassesPlayerLimit) {
        super(profile);
        this.permissionLevel = permissionLevel;
        this.bypassesPlayerLimit = bypassesPlayerLimit;
    }

    public OpEntry(JsonObject json) {
        super(deserialize(json), json);
        this.permissionLevel = json.has("level") ? json.get("level").getAsInt() : 0;
        this.bypassesPlayerLimit = json.has("bypassesPlayerLimit") && json.get("bypassesPlayerLimit").getAsBoolean();
    }

    public int getPermissionLevel() {
        return this.permissionLevel;
    }

    public boolean bypassesPlayerLimit() {
        return this.bypassesPlayerLimit;
    }

    @Override
    protected void serialize(JsonObject json) {
        if (this.getUser() != null) {
            json.addProperty("uuid", this.getUser().getId() == null ? "" : this.getUser().getId().toString());
            json.addProperty("name", this.getUser().getName());
            super.serialize(json);
            json.addProperty("level", this.permissionLevel);
            json.addProperty("bypassesPlayerLimit", this.bypassesPlayerLimit);
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
