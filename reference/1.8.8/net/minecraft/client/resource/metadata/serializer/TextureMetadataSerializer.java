package net.minecraft.client.resource.metadata.serializer;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import net.minecraft.client.resource.metadata.TextureMetadata;
import net.minecraft.util.JsonUtils;

public class TextureMetadataSerializer extends AbstractResourceMetadataSerializer<TextureMetadata> {
    public TextureMetadata deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonobject = jsonElement.getAsJsonObject();
        boolean flag = JsonUtils.getBooleanOrDefault(jsonobject, "blur", false);
        boolean flag1 = JsonUtils.getBooleanOrDefault(jsonobject, "clamp", false);
        List<Integer> list = Lists.newArrayList();
        if (jsonobject.has("mipmaps")) {
            try {
                JsonArray jsonarray = jsonobject.getAsJsonArray("mipmaps");

                for (int i = 0; i < jsonarray.size(); i++) {
                    JsonElement jsonelement = jsonarray.get(i);
                    if (jsonelement.isJsonPrimitive()) {
                        try {
                            list.add(jsonelement.getAsInt());
                        } catch (NumberFormatException numberformatexception) {
                            throw new JsonParseException("Invalid texture->mipmap->" + i + ": expected number, was " + jsonelement, numberformatexception);
                        }
                    } else if (jsonelement.isJsonObject()) {
                        throw new JsonParseException("Invalid texture->mipmap->" + i + ": expected number, was " + jsonelement);
                    }
                }
            } catch (ClassCastException classcastexception) {
                throw new JsonParseException("Invalid texture->mipmaps: expected array, was " + jsonobject.get("mipmaps"), classcastexception);
            }
        }

        return new TextureMetadata(flag, flag1, list);
    }

    @Override
    public String getName() {
        return "texture";
    }
}
