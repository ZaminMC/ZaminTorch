package net.minecraft.server;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;

public class Whitelist extends StoredUserList<GameProfile, WhitelistEntry> {
    public Whitelist(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<GameProfile> deserialize(JsonObject json) {
        return new WhitelistEntry(json);
    }

    @Override
    public String[] getNames() {
        String[] astring = new String[this.getEntries().size()];
        int i = 0;

        for (WhitelistEntry whitelistentry : this.getEntries().values()) {
            astring[i++] = whitelistentry.getUser().getName();
        }

        return astring;
    }

    protected String getKey(GameProfile gameProfile) {
        return gameProfile.getId().toString();
    }

    public GameProfile getProfile(String name) {
        for (WhitelistEntry whitelistentry : this.getEntries().values()) {
            if (name.equalsIgnoreCase(whitelistentry.getUser().getName())) {
                return whitelistentry.getUser();
            }
        }

        return null;
    }
}
