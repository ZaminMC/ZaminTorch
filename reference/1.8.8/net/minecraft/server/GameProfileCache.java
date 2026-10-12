package net.minecraft.server;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.living.player.PlayerEntity;
import org.apache.commons.io.IOUtils;

public class GameProfileCache {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    private final Map<String, GameProfileCache.Entry> profilesByName = Maps.newHashMap();
    private final Map<UUID, GameProfileCache.Entry> profilesByUuid = Maps.newHashMap();
    private final LinkedList<GameProfile> saveQueue = Lists.newLinkedList();
    private final MinecraftServer server;
    protected final Gson gson;
    private final File file;
    private static final ParameterizedType ENTRY_TYPE = new ParameterizedType() {
        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{GameProfileCache.Entry.class};
        }

        @Override
        public Type getRawType() {
            return List.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    };

    public GameProfileCache(MinecraftServer server, File file) {
        this.server = server;
        this.file = file;
        GsonBuilder gsonbuilder = new GsonBuilder();
        gsonbuilder.registerTypeHierarchyAdapter(GameProfileCache.Entry.class, new GameProfileCache.Serializer());
        this.gson = gsonbuilder.create();
        this.load();
    }

    private static GameProfile find(MinecraftServer server, String name) {
        final GameProfile[] agameprofile = new GameProfile[1];
        ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
            @Override
            public void onProfileLookupSucceeded(GameProfile profile) {
                agameprofile[0] = profile;
            }

            @Override
            public void onProfileLookupFailed(GameProfile profile, Exception e) {
                agameprofile[0] = null;
            }
        };
        server.getGameProfileRepository().findProfilesByNames(new String[]{name}, Agent.MINECRAFT, profilelookupcallback);
        if (!server.isOnlineMode() && agameprofile[0] == null) {
            UUID uuid = PlayerEntity.getUuid(new GameProfile(null, name));
            GameProfile gameprofile = new GameProfile(uuid, name);
            profilelookupcallback.onProfileLookupSucceeded(gameprofile);
        }

        return agameprofile[0];
    }

    public void add(GameProfile profile) {
        this.add(profile, null);
    }

    private void add(GameProfile profile, Date expirationDate) {
        UUID uuid = profile.getId();
        if (expirationDate == null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.add(2, 1);
            expirationDate = calendar.getTime();
        }

        String s = profile.getName().toLowerCase(Locale.ROOT);
        GameProfileCache.Entry gameprofilecache$entry = new GameProfileCache.Entry(profile, expirationDate);
        if (this.profilesByUuid.containsKey(uuid)) {
            GameProfileCache.Entry gameprofilecache$entry1 = this.profilesByUuid.get(uuid);
            this.profilesByName.remove(gameprofilecache$entry1.getProfile().getName().toLowerCase(Locale.ROOT));
            this.saveQueue.remove(profile);
        }

        this.profilesByName.put(profile.getName().toLowerCase(Locale.ROOT), gameprofilecache$entry);
        this.profilesByUuid.put(uuid, gameprofilecache$entry);
        this.saveQueue.addFirst(profile);
        this.save();
    }

    public GameProfile get(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        GameProfileCache.Entry gameprofilecache$entry = this.profilesByName.get(s);
        if (gameprofilecache$entry != null && new Date().getTime() >= gameprofilecache$entry.expirationDate.getTime()) {
            this.profilesByUuid.remove(gameprofilecache$entry.getProfile().getId());
            this.profilesByName.remove(gameprofilecache$entry.getProfile().getName().toLowerCase(Locale.ROOT));
            this.saveQueue.remove(gameprofilecache$entry.getProfile());
            gameprofilecache$entry = null;
        }

        if (gameprofilecache$entry != null) {
            GameProfile gameprofile = gameprofilecache$entry.getProfile();
            this.saveQueue.remove(gameprofile);
            this.saveQueue.addFirst(gameprofile);
        } else {
            GameProfile gameprofile1 = find(this.server, s);
            if (gameprofile1 != null) {
                this.add(gameprofile1);
                gameprofilecache$entry = this.profilesByName.get(s);
            }
        }

        this.save();
        return gameprofilecache$entry == null ? null : gameprofilecache$entry.getProfile();
    }

    public String[] getNames() {
        List<String> list = Lists.newArrayList(this.profilesByName.keySet());
        return list.toArray(new String[list.size()]);
    }

    public GameProfile get(UUID uuid) {
        GameProfileCache.Entry gameprofilecache$entry = this.profilesByUuid.get(uuid);
        return gameprofilecache$entry == null ? null : gameprofilecache$entry.getProfile();
    }

    private GameProfileCache.Entry getEntry(UUID uuid) {
        GameProfileCache.Entry gameprofilecache$entry = this.profilesByUuid.get(uuid);
        if (gameprofilecache$entry != null) {
            GameProfile gameprofile = gameprofilecache$entry.getProfile();
            this.saveQueue.remove(gameprofile);
            this.saveQueue.addFirst(gameprofile);
        }

        return gameprofilecache$entry;
    }

    public void load() {
        BufferedReader bufferedreader = null;

        try {
            bufferedreader = Files.newReader(this.file, Charsets.UTF_8);
            List<GameProfileCache.Entry> list = this.gson.fromJson(bufferedreader, ENTRY_TYPE);
            this.profilesByName.clear();
            this.profilesByUuid.clear();
            this.saveQueue.clear();

            for (GameProfileCache.Entry gameprofilecache$entry : Lists.reverse(list)) {
                if (gameprofilecache$entry != null) {
                    this.add(gameprofilecache$entry.getProfile(), gameprofilecache$entry.getExpirationDate());
                }
            }
        } catch (FileNotFoundException filenotfoundexception) {
        } catch (JsonParseException jsonparseexception) {
        } finally {
            IOUtils.closeQuietly(bufferedreader);
        }
    }

    public void save() {
        String s = this.gson.toJson(this.getSaveQueue(1000));
        BufferedWriter bufferedwriter = null;

        try {
            bufferedwriter = Files.newWriter(this.file, Charsets.UTF_8);
            bufferedwriter.write(s);
            return;
        } catch (FileNotFoundException filenotfoundexception) {
            return;
        } catch (IOException ioexception) {
        } finally {
            IOUtils.closeQuietly(bufferedwriter);
        }
    }

    private List<GameProfileCache.Entry> getSaveQueue(int amount) {
        ArrayList<GameProfileCache.Entry> arraylist = Lists.newArrayList();

        for (GameProfile gameprofile : Lists.newArrayList(Iterators.limit(this.saveQueue.iterator(), amount))) {
            GameProfileCache.Entry gameprofilecache$entry = this.getEntry(gameprofile.getId());
            if (gameprofilecache$entry != null) {
                arraylist.add(gameprofilecache$entry);
            }
        }

        return arraylist;
    }

    class Entry {
        private final GameProfile profile;
        private final Date expirationDate;

        private Entry(GameProfile profile, Date expirationDate) {
            this.profile = profile;
            this.expirationDate = expirationDate;
        }

        public GameProfile getProfile() {
            return this.profile;
        }

        public Date getExpirationDate() {
            return this.expirationDate;
        }
    }

    class Serializer implements JsonDeserializer<GameProfileCache.Entry>, JsonSerializer<GameProfileCache.Entry> {
        private Serializer() {
        }

        public JsonElement serialize(GameProfileCache.Entry entry, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject jsonobject = new JsonObject();
            jsonobject.addProperty("name", entry.getProfile().getName());
            UUID uuid = entry.getProfile().getId();
            jsonobject.addProperty("uuid", uuid == null ? "" : uuid.toString());
            jsonobject.addProperty("expiresOn", GameProfileCache.DATE_FORMAT.format(entry.getExpirationDate()));
            return jsonobject;
        }

        public GameProfileCache.Entry deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            if (jsonElement.isJsonObject()) {
                JsonObject jsonobject = jsonElement.getAsJsonObject();
                JsonElement jsonelement = jsonobject.get("name");
                JsonElement jsonelement1 = jsonobject.get("uuid");
                JsonElement jsonelement2 = jsonobject.get("expiresOn");
                if (jsonelement != null && jsonelement1 != null) {
                    String s = jsonelement1.getAsString();
                    String s1 = jsonelement.getAsString();
                    Date date = null;
                    if (jsonelement2 != null) {
                        try {
                            date = GameProfileCache.DATE_FORMAT.parse(jsonelement2.getAsString());
                        } catch (ParseException parseexception) {
                            date = null;
                        }
                    }

                    if (s1 != null && s != null) {
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(s);
                        } catch (Throwable throwable) {
                            return null;
                        }

                        return GameProfileCache.this.new Entry(new GameProfile(uuid, s1), date);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }
}
