package net.minecraft.server;

import com.google.gson.JsonObject;

public class StoredUserEntry<T> {
    private final T user;

    public StoredUserEntry(T user) {
        this.user = user;
    }

    protected StoredUserEntry(T user, JsonObject json) {
        this.user = user;
    }

    T getUser() {
        return this.user;
    }

    boolean hasExpired() {
        return false;
    }

    protected void serialize(JsonObject json) {
    }
}
