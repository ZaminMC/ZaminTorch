package net.minecraft.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Type;
import java.util.UUID;
import net.minecraft.text.Text;
import net.minecraft.util.JsonUtils;

public class ServerStatus {
    private Text description;
    private ServerStatus.Players players;
    private ServerStatus.Version version;
    private String favicon;

    public Text getDescription() {
        return this.description;
    }

    public void setDescription(Text description) {
        this.description = description;
    }

    public ServerStatus.Players getPlayers() {
        return this.players;
    }

    public void setPlayers(ServerStatus.Players players) {
        this.players = players;
    }

    public ServerStatus.Version getVersion() {
        return this.version;
    }

    public void setVersion(ServerStatus.Version version) {
        this.version = version;
    }

    public void setFavicon(String favicon) {
        this.favicon = favicon;
    }

    public String getFavicon() {
        return this.favicon;
    }

    public static class Players {
        private final int max;
        private final int online;
        private GameProfile[] profiles;

        public Players(int max, int online) {
            this.max = max;
            this.online = online;
        }

        public int getMax() {
            return this.max;
        }

        public int getOnline() {
            return this.online;
        }

        public GameProfile[] get() {
            return this.profiles;
        }

        public void set(GameProfile[] profiles) {
            this.profiles = profiles;
        }

        public static class Serializer implements JsonDeserializer<ServerStatus.Players>, JsonSerializer<ServerStatus.Players> {
            public ServerStatus.Players deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
                JsonObject jsonobject = JsonUtils.asJsonObject(jsonElement, "players");
                ServerStatus.Players serverstatus$players = new ServerStatus.Players(
                    JsonUtils.getInteger(jsonobject, "max"), JsonUtils.getInteger(jsonobject, "online")
                );
                if (JsonUtils.hasJsonArray(jsonobject, "sample")) {
                    JsonArray jsonarray = JsonUtils.getJsonArray(jsonobject, "sample");
                    if (jsonarray.size() > 0) {
                        GameProfile[] agameprofile = new GameProfile[jsonarray.size()];

                        for (int i = 0; i < agameprofile.length; i++) {
                            JsonObject jsonobject1 = JsonUtils.asJsonObject(jsonarray.get(i), "player[" + i + "]");
                            String s = JsonUtils.getString(jsonobject1, "id");
                            agameprofile[i] = new GameProfile(UUID.fromString(s), JsonUtils.getString(jsonobject1, "name"));
                        }

                        serverstatus$players.set(agameprofile);
                    }
                }

                return serverstatus$players;
            }

            public JsonElement serialize(ServerStatus.Players players, Type type, JsonSerializationContext jsonSerializationContext) {
                JsonObject jsonobject = new JsonObject();
                jsonobject.addProperty("max", players.getMax());
                jsonobject.addProperty("online", players.getOnline());
                if (players.get() != null && players.get().length > 0) {
                    JsonArray jsonarray = new JsonArray();

                    for (int i = 0; i < players.get().length; i++) {
                        JsonObject jsonobject1 = new JsonObject();
                        UUID uuid = players.get()[i].getId();
                        jsonobject1.addProperty("id", uuid == null ? "" : uuid.toString());
                        jsonobject1.addProperty("name", players.get()[i].getName());
                        jsonarray.add(jsonobject1);
                    }

                    jsonobject.add("sample", jsonarray);
                }

                return jsonobject;
            }
        }
    }

    public static class Serializer implements JsonDeserializer<ServerStatus>, JsonSerializer<ServerStatus> {
        public ServerStatus deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = JsonUtils.asJsonObject(jsonElement, "status");
            ServerStatus serverstatus = new ServerStatus();
            if (jsonobject.has("description")) {
                serverstatus.setDescription(jsonDeserializationContext.deserialize(jsonobject.get("description"), Text.class));
            }

            if (jsonobject.has("players")) {
                serverstatus.setPlayers(jsonDeserializationContext.deserialize(jsonobject.get("players"), ServerStatus.Players.class));
            }

            if (jsonobject.has("version")) {
                serverstatus.setVersion(jsonDeserializationContext.deserialize(jsonobject.get("version"), ServerStatus.Version.class));
            }

            if (jsonobject.has("favicon")) {
                serverstatus.setFavicon(JsonUtils.getString(jsonobject, "favicon"));
            }

            return serverstatus;
        }

        public JsonElement serialize(ServerStatus serverStatus, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject jsonobject = new JsonObject();
            if (serverStatus.getDescription() != null) {
                jsonobject.add("description", jsonSerializationContext.serialize(serverStatus.getDescription()));
            }

            if (serverStatus.getPlayers() != null) {
                jsonobject.add("players", jsonSerializationContext.serialize(serverStatus.getPlayers()));
            }

            if (serverStatus.getVersion() != null) {
                jsonobject.add("version", jsonSerializationContext.serialize(serverStatus.getVersion()));
            }

            if (serverStatus.getFavicon() != null) {
                jsonobject.addProperty("favicon", serverStatus.getFavicon());
            }

            return jsonobject;
        }
    }

    public static class Version {
        private final String name;
        private final int protocol;

        public Version(String name, int protocol) {
            this.name = name;
            this.protocol = protocol;
        }

        public String getName() {
            return this.name;
        }

        public int getProtocol() {
            return this.protocol;
        }

        public static class Serializer implements JsonDeserializer<ServerStatus.Version>, JsonSerializer<ServerStatus.Version> {
            public ServerStatus.Version deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
                JsonObject jsonobject = JsonUtils.asJsonObject(jsonElement, "version");
                return new ServerStatus.Version(JsonUtils.getString(jsonobject, "name"), JsonUtils.getInteger(jsonobject, "protocol"));
            }

            public JsonElement serialize(ServerStatus.Version version, Type type, JsonSerializationContext jsonSerializationContext) {
                JsonObject jsonobject = new JsonObject();
                jsonobject.addProperty("name", version.getName());
                jsonobject.addProperty("protocol", version.getProtocol());
                return jsonobject;
            }
        }
    }
}
