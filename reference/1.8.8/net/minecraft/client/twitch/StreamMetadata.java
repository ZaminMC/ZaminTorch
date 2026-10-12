package net.minecraft.client.twitch;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import java.util.Map;

public class StreamMetadata {
    private static final Gson GSON = new Gson();
    private final String name;
    private String description;
    private Map<String, String> payload;

    public StreamMetadata(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public StreamMetadata(String name) {
        this(name, null);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description == null ? this.name : this.description;
    }

    public void put(String key, String value) {
        if (this.payload == null) {
            this.payload = Maps.newHashMap();
        }

        if (this.payload.size() > 50) {
            throw new IllegalArgumentException("Metadata payload is full, cannot add more to it!");
        }

        if (key == null) {
            throw new IllegalArgumentException("Metadata payload key cannot be null!");
        }

        if (key.length() > 255) {
            throw new IllegalArgumentException("Metadata payload key is too long!");
        }

        if (value == null) {
            throw new IllegalArgumentException("Metadata payload value cannot be null!");
        }

        if (value.length() > 255) {
            throw new IllegalArgumentException("Metadata payload value is too long!");
        }

        this.payload.put(key, value);
    }

    public String serialize() {
        return this.payload != null && !this.payload.isEmpty() ? GSON.toJson(this.payload) : null;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("name", this.name).add("description", this.description).add("data", this.serialize()).toString();
    }
}
