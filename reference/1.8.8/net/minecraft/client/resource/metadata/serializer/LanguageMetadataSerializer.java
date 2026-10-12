package net.minecraft.client.resource.metadata.serializer;

import com.google.common.collect.Sets;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.resource.language.Language;
import net.minecraft.client.resource.metadata.LanguageMetadata;
import net.minecraft.util.JsonUtils;

public class LanguageMetadataSerializer extends AbstractResourceMetadataSerializer<LanguageMetadata> {
    public LanguageMetadata deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonobject = jsonElement.getAsJsonObject();
        Set<Language> set = Sets.newHashSet();

        for (Entry<String, JsonElement> entry : jsonobject.entrySet()) {
            String s = entry.getKey();
            JsonObject jsonobject1 = JsonUtils.asJsonObject(entry.getValue(), "language");
            String s1 = JsonUtils.getString(jsonobject1, "region");
            String s2 = JsonUtils.getString(jsonobject1, "name");
            boolean flag = JsonUtils.getBooleanOrDefault(jsonobject1, "bidirectional", false);
            if (s1.isEmpty()) {
                throw new JsonParseException("Invalid language->'" + s + "'->region: empty value");
            }

            if (s2.isEmpty()) {
                throw new JsonParseException("Invalid language->'" + s + "'->name: empty value");
            }

            if (!set.add(new Language(s, s1, s2, flag))) {
                throw new JsonParseException("Duplicate language->'" + s + "' defined");
            }
        }

        return new LanguageMetadata(set);
    }

    @Override
    public String getName() {
        return "language";
    }
}
