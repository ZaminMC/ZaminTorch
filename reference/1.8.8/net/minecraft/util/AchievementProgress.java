package net.minecraft.util;

import com.google.common.collect.ForwardingSet;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Set;
import net.minecraft.stat.StatProgress;

public class AchievementProgress extends ForwardingSet<String> implements StatProgress {
    private final Set<String> progress = Sets.newHashSet();

    @Override
    public void update(JsonElement progress) {
        if (progress.isJsonArray()) {
            for (JsonElement jsonelement : progress.getAsJsonArray()) {
                this.add(jsonelement.getAsString());
            }
        }
    }

    @Override
    public JsonElement toJson() {
        JsonArray jsonarray = new JsonArray();

        for (String s : this) {
            jsonarray.add(new JsonPrimitive(s));
        }

        return jsonarray;
    }

    @Override
    protected Set<String> delegate() {
        return this.progress;
    }
}
