package net.minecraft.client;

import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.util.UUIDTypeAdapter;
import java.util.Map;
import java.util.UUID;

public class Session {
    private final String username;
    private final String uuid;
    private final String accessToken;
    private final Session.Type type;

    public Session(String username, String uuid, String accessToken, String type) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.type = Session.Type.byId(type);
    }

    public String getSessionId() {
        return "token:" + this.accessToken + ":" + this.uuid;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getUsername() {
        return this.username;
    }

    public String getAccessToken() {
        return this.accessToken;
    }

    public GameProfile getProfile() {
        try {
            UUID uuid = UUIDTypeAdapter.fromString(this.getUuid());
            return new GameProfile(uuid, this.getUsername());
        } catch (IllegalArgumentException illegalargumentexception) {
            return new GameProfile(null, this.getUsername());
        }
    }

    public Session.Type getType() {
        return this.type;
    }

    public enum Type {
        LEGACY("legacy"),
        MOJANG("mojang");

        private static final Map<String, Session.Type> BY_ID = Maps.newHashMap();
        private final String id;

        Type(String id) {
            this.id = id;
        }

        public static Session.Type byId(String id) {
            return BY_ID.get(id.toLowerCase());
        }

        static {
            for (Session.Type session$type : values()) {
                BY_ID.put(session$type.id, session$type);
            }
        }
    }
}
