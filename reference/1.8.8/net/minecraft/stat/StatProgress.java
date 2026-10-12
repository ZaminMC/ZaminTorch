package net.minecraft.stat;

import com.google.gson.JsonElement;

public interface StatProgress {
    void update(JsonElement progress);

    JsonElement toJson();
}
