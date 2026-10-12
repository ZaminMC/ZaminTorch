package net.minecraft.server;

import com.google.common.base.Charsets;
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
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StoredUserList<K, V extends StoredUserEntry<K>> {
    protected static final Logger LOGGER = LogManager.getLogger();
    protected final Gson gson;
    private final File file;
    private final Map<String, V> users = Maps.newHashMap();
    private boolean enabled = true;
    private static final ParameterizedType ENTRY_TYPE = new ParameterizedType() {
        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{StoredUserEntry.class};
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

    public StoredUserList(File file) {
        this.file = file;
        GsonBuilder gsonbuilder = new GsonBuilder().setPrettyPrinting();
        gsonbuilder.registerTypeHierarchyAdapter(StoredUserEntry.class, new StoredUserList.Serializer());
        this.gson = gsonbuilder.create();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void add(V entry) {
        this.users.put(this.getKey(entry.getUser()), entry);

        try {
            this.save();
        } catch (IOException ioexception) {
            LOGGER.warn("Could not save the list after adding a user.", ioexception);
        }
    }

    public V get(K user) {
        this.removeExpiredEntries();
        return this.users.get(this.getKey(user));
    }

    public void remove(K user) {
        this.users.remove(this.getKey(user));

        try {
            this.save();
        } catch (IOException ioexception) {
            LOGGER.warn("Could not save the list after removing a user.", ioexception);
        }
    }

    public String[] getNames() {
        return this.users.keySet().toArray(new String[this.users.size()]);
    }

    protected String getKey(K user) {
        return user.toString();
    }

    protected boolean contains(K user) {
        return this.users.containsKey(this.getKey(user));
    }

    private void removeExpiredEntries() {
        List<K> list = Lists.newArrayList();

        for (V v : this.users.values()) {
            if (v.hasExpired()) {
                list.add(v.getUser());
            }
        }

        for (K k : list) {
            this.users.remove(k);
        }
    }

    protected StoredUserEntry<K> deserialize(JsonObject json) {
        return new StoredUserEntry<>(null, json);
    }

    protected Map<String, V> getEntries() {
        return this.users;
    }

    public void save() throws IOException {
        Collection<V> collection = this.users.values();
        String s = this.gson.toJson(collection);
        BufferedWriter bufferedwriter = null;

        try {
            bufferedwriter = Files.newWriter(this.file, Charsets.UTF_8);
            bufferedwriter.write(s);
        } finally {
            IOUtils.closeQuietly(bufferedwriter);
        }
    }

    class Serializer implements JsonDeserializer<StoredUserEntry<K>>, JsonSerializer<StoredUserEntry<K>> {
        private Serializer() {
        }

        public JsonElement serialize(StoredUserEntry<K> storedUserEntry, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject jsonobject = new JsonObject();
            storedUserEntry.serialize(jsonobject);
            return jsonobject;
        }

        public StoredUserEntry<K> deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            if (jsonElement.isJsonObject()) {
                JsonObject jsonobject = jsonElement.getAsJsonObject();
                return StoredUserList.this.deserialize(jsonobject);
            } else {
                return null;
            }
        }
    }
}
