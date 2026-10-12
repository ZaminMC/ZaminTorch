package net.minecraft.client.resource.metadata.serializer;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.List;
import net.minecraft.client.resource.metadata.AnimationFrame;
import net.minecraft.client.resource.metadata.AnimationMetadata;
import net.minecraft.util.JsonUtils;
import org.apache.commons.lang3.Validate;

public class AnimationMetadataSerializer extends AbstractResourceMetadataSerializer<AnimationMetadata> implements JsonSerializer<AnimationMetadata> {
    public AnimationMetadata deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        List<AnimationFrame> list = Lists.newArrayList();
        JsonObject jsonobject = JsonUtils.asJsonObject(jsonElement, "metadata section");
        int i = JsonUtils.getIntegerOrDefault(jsonobject, "frametime", 1);
        if (i != 1) {
            Validate.inclusiveBetween(1L, 2147483647L, i, "Invalid default frame time");
        }

        if (jsonobject.has("frames")) {
            try {
                JsonArray jsonarray = JsonUtils.getJsonArray(jsonobject, "frames");

                for (int j = 0; j < jsonarray.size(); j++) {
                    JsonElement jsonelement = jsonarray.get(j);
                    AnimationFrame animationframe = this.deserializeFrame(j, jsonelement);
                    if (animationframe != null) {
                        list.add(animationframe);
                    }
                }
            } catch (ClassCastException classcastexception) {
                throw new JsonParseException("Invalid animation->frames: expected array, was " + jsonobject.get("frames"), classcastexception);
            }
        }

        int k = JsonUtils.getIntegerOrDefault(jsonobject, "width", -1);
        int l = JsonUtils.getIntegerOrDefault(jsonobject, "height", -1);
        if (k != -1) {
            Validate.inclusiveBetween(1L, 2147483647L, k, "Invalid width");
        }

        if (l != -1) {
            Validate.inclusiveBetween(1L, 2147483647L, l, "Invalid height");
        }

        boolean flag = JsonUtils.getBooleanOrDefault(jsonobject, "interpolate", false);
        return new AnimationMetadata(list, k, l, i, flag);
    }

    private AnimationFrame deserializeFrame(int index, JsonElement json) {
        if (json.isJsonPrimitive()) {
            return new AnimationFrame(JsonUtils.asInteger(json, "frames[" + index + "]"));
        }

        if (json.isJsonObject()) {
            JsonObject jsonobject = JsonUtils.asJsonObject(json, "frames[" + index + "]");
            int i = JsonUtils.getIntegerOrDefault(jsonobject, "time", -1);
            if (jsonobject.has("time")) {
                Validate.inclusiveBetween(1L, 2147483647L, i, "Invalid frame time");
            }

            int j = JsonUtils.getInteger(jsonobject, "index");
            Validate.inclusiveBetween(0L, 2147483647L, j, "Invalid frame index");
            return new AnimationFrame(j, i);
        } else {
            return null;
        }
    }

    public JsonElement serialize(AnimationMetadata animationMetadata, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("frametime", animationMetadata.getDefaultFrameTime());
        if (animationMetadata.getFrameWidth() != -1) {
            jsonobject.addProperty("width", animationMetadata.getFrameWidth());
        }

        if (animationMetadata.getFrameHeight() != -1) {
            jsonobject.addProperty("height", animationMetadata.getFrameHeight());
        }

        if (animationMetadata.getFrameCount() > 0) {
            JsonArray jsonarray = new JsonArray();

            for (int i = 0; i < animationMetadata.getFrameCount(); i++) {
                if (animationMetadata.hasFrameTime(i)) {
                    JsonObject jsonobject1 = new JsonObject();
                    jsonobject1.addProperty("index", animationMetadata.getFrameIndex(i));
                    jsonobject1.addProperty("time", animationMetadata.getFrameTime(i));
                    jsonarray.add(jsonobject1);
                } else {
                    jsonarray.add(new JsonPrimitive(animationMetadata.getFrameIndex(i)));
                }
            }

            jsonobject.add("frames", jsonarray);
        }

        return jsonobject;
    }

    @Override
    public String getName() {
        return "animation";
    }
}
