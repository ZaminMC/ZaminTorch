package net.minecraft.client.resource.metadata.serializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import net.minecraft.client.resource.metadata.ResourcePackMetadata;
import net.minecraft.text.Text;
import net.minecraft.util.JsonUtils;

public class PackMetadataSerializer extends AbstractResourceMetadataSerializer<ResourcePackMetadata> implements JsonSerializer<ResourcePackMetadata> {
    public ResourcePackMetadata deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonobject = jsonElement.getAsJsonObject();
        Text text = jsonDeserializationContext.deserialize(jsonobject.get("description"), Text.class);
        if (text == null) {
            throw new JsonParseException("Invalid/missing description!");
        }

        int i = JsonUtils.getInteger(jsonobject, "pack_format");
        return new ResourcePackMetadata(text, i);
    }

    public JsonElement serialize(ResourcePackMetadata resourcePackMetadata, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("pack_format", resourcePackMetadata.getFormat());
        jsonobject.add("description", jsonSerializationContext.serialize(resourcePackMetadata.getDescription()));
        return jsonobject;
    }

    @Override
    public String getName() {
        return "pack";
    }
}
