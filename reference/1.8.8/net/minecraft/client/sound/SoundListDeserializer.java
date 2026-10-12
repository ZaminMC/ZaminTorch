package net.minecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.minecraft.util.JsonUtils;
import org.apache.commons.lang3.Validate;

public class SoundListDeserializer implements JsonDeserializer<SoundList> {
    public SoundList deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonobject = JsonUtils.asJsonObject(jsonElement, "entry");
        SoundList soundlist = new SoundList();
        soundlist.setReplacable(JsonUtils.getBooleanOrDefault(jsonobject, "replace", false));
        SoundCategory soundcategory = SoundCategory.byName(JsonUtils.getStringOrDefault(jsonobject, "category", SoundCategory.MASTER.getName()));
        soundlist.setCategory(soundcategory);
        Validate.notNull(soundcategory, "Invalid category");
        if (jsonobject.has("sounds")) {
            JsonArray jsonarray = JsonUtils.getJsonArray(jsonobject, "sounds");

            for (int i = 0; i < jsonarray.size(); i++) {
                JsonElement jsonelement = jsonarray.get(i);
                SoundList.Sound soundlist$sound = new SoundList.Sound();
                if (JsonUtils.isString(jsonelement)) {
                    soundlist$sound.setName(JsonUtils.asString(jsonelement, "sound"));
                } else {
                    JsonObject jsonobject1 = JsonUtils.asJsonObject(jsonelement, "sound");
                    soundlist$sound.setName(JsonUtils.getString(jsonobject1, "name"));
                    if (jsonobject1.has("type")) {
                        SoundList.Sound.Type soundlist$sound$type = SoundList.Sound.Type.byName(JsonUtils.getString(jsonobject1, "type"));
                        Validate.notNull(soundlist$sound$type, "Invalid type");
                        soundlist$sound.setType(soundlist$sound$type);
                    }

                    if (jsonobject1.has("volume")) {
                        float f = JsonUtils.getFloat(jsonobject1, "volume");
                        Validate.isTrue(f > 0.0F, "Invalid volume");
                        soundlist$sound.setVolume(f);
                    }

                    if (jsonobject1.has("pitch")) {
                        float f1 = JsonUtils.getFloat(jsonobject1, "pitch");
                        Validate.isTrue(f1 > 0.0F, "Invalid pitch");
                        soundlist$sound.setPitch(f1);
                    }

                    if (jsonobject1.has("weight")) {
                        int j = JsonUtils.getInteger(jsonobject1, "weight");
                        Validate.isTrue(j > 0, "Invalid weight");
                        soundlist$sound.setWeight(j);
                    }

                    if (jsonobject1.has("stream")) {
                        soundlist$sound.setStream(JsonUtils.getBoolean(jsonobject1, "stream"));
                    }
                }

                soundlist.getSounds().add(soundlist$sound);
            }
        }

        return soundlist;
    }
}
