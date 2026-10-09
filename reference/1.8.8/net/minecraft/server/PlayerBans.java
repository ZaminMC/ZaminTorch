package net.minecraft.server;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;

public class PlayerBans extends StoredUserList<GameProfile, PlayerBanEntry> {
    public PlayerBans(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<GameProfile> deserialize(JsonObject json) {
        return new PlayerBanEntry(json);
    }

    public boolean isBanned(GameProfile profile) {
        return this.contains(profile);
    }

    @Override
    public String[] getNames() {
        String[] astring = new String[this.getEntries().size()];
        int i = 0;

        for (PlayerBanEntry playerbanentry : this.getEntries().values()) {
            astring[i++] = playerbanentry.getUser().getName();
        }

        return astring;
    }

    protected String getKey(GameProfile gameProfile) {
        return gameProfile.getId().toString();
    }

    public GameProfile getProfile(String name) {
        for (PlayerBanEntry playerbanentry : this.getEntries().values()) {
            if (name.equalsIgnoreCase(playerbanentry.getUser().getName())) {
                return playerbanentry.getUser();
            }
        }

        return null;
    }
}
