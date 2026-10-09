package net.minecraft.util;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public class LowercaseEnumTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<T> oclass = (Class<T>)typeToken.getRawType();
        if (!oclass.isEnum()) {
            return null;
        }

        final Map<String, T> map = Maps.newHashMap();

        for (T t : oclass.getEnumConstants()) {
            map.put(this.getKey(t), t);
        }

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter jsonWriter, T t) throws IOException {
                if (t == null) {
                    jsonWriter.nullValue();
                } else {
                    jsonWriter.value(LowercaseEnumTypeAdapterFactory.this.getKey(t));
                }
            }

            @Override
            public T read(JsonReader jsonReader) throws IOException {
                if (jsonReader.peek() == JsonToken.NULL) {
                    jsonReader.nextNull();
                    return null;
                } else {
                    return map.get(jsonReader.nextString());
                }
            }
        };
    }

    private String getKey(Object obj) {
        return obj instanceof Enum ? ((Enum)obj).name().toLowerCase(Locale.US) : obj.toString().toLowerCase(Locale.US);
    }
}
