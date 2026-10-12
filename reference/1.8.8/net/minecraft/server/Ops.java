package net.minecraft.server;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;

public class Ops extends StoredUserList<GameProfile, OpEntry> {
    public Ops(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<GameProfile> deserialize(JsonObject json) {
        return new OpEntry(json);
    }

    @Override
    public String[] getNames() {
        String[] astring = new String[this.getEntries().size()];
        int i = 0;

        for (OpEntry opentry : this.getEntries().values()) {
            astring[i++] = opentry.getUser().getName();
        }

        return astring;
    }

    public boolean bypassesPlayerLimit(GameProfile profile) {
        OpEntry opentry = this.get(profile);
        return opentry != null && opentry.bypassesPlayerLimit();
    }

    protected String getKey(GameProfile gameProfile) {
        return gameProfile.getId().toString();
    }

    public GameProfile getProfile(String name) {
        for (OpEntry opentry : this.getEntries().values()) {
            if (name.equalsIgnoreCase(opentry.getUser().getName())) {
                return opentry.getUser();
            }
        }

        return null;
    }
}
