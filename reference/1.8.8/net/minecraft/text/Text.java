package net.minecraft.text;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.LowercaseEnumTypeAdapterFactory;

public interface Text extends Iterable<Text> {
    Text setStyle(Style style);

    Style getStyle();

    Text append(String text);

    Text append(Text text);

    String getContent();

    String getString();

    String getFormattedString();

    List<Text> getSiblings();

    Text copy();

    class Serializer implements JsonDeserializer<Text>, JsonSerializer<Text> {
        private static final Gson GSON;

        public Text deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            if (jsonElement.isJsonPrimitive()) {
                return new LiteralText(jsonElement.getAsString());
            }

            if (!jsonElement.isJsonObject()) {
                if (jsonElement.isJsonArray()) {
                    JsonArray jsonarray1 = jsonElement.getAsJsonArray();
                    Text text1 = null;

                    for (JsonElement jsonelement : jsonarray1) {
                        Text text2 = this.deserialize(jsonelement, jsonelement.getClass(), jsonDeserializationContext);
                        if (text1 == null) {
                            text1 = text2;
                        } else {
                            text1.append(text2);
                        }
                    }

                    return text1;
                } else {
                    throw new JsonParseException("Don't know how to turn " + jsonElement.toString() + " into a Component");
                }
            } else {
                JsonObject jsonobject = jsonElement.getAsJsonObject();
                Text text;
                if (jsonobject.has("text")) {
                    text = new LiteralText(jsonobject.get("text").getAsString());
                } else if (jsonobject.has("translate")) {
                    String s = jsonobject.get("translate").getAsString();
                    if (jsonobject.has("with")) {
                        JsonArray jsonarray = jsonobject.getAsJsonArray("with");
                        Object[] aobject = new Object[jsonarray.size()];

                        for (int i = 0; i < aobject.length; i++) {
                            aobject[i] = this.deserialize(jsonarray.get(i), type, jsonDeserializationContext);
                            if (aobject[i] instanceof LiteralText) {
                                LiteralText literaltext = (LiteralText)aobject[i];
                                if (literaltext.getStyle().isEmpty() && literaltext.getSiblings().isEmpty()) {
                                    aobject[i] = literaltext.getRawString();
                                }
                            }
                        }

                        text = new TranslatableText(s, aobject);
                    } else {
                        text = new TranslatableText(s);
                    }
                } else if (jsonobject.has("score")) {
                    JsonObject jsonobject1 = jsonobject.getAsJsonObject("score");
                    if (!jsonobject1.has("name") || !jsonobject1.has("objective")) {
                        throw new JsonParseException("A score component needs a least a name and an objective");
                    }

                    text = new ScoreText(JsonUtils.getString(jsonobject1, "name"), JsonUtils.getString(jsonobject1, "objective"));
                    if (jsonobject1.has("value")) {
                        ((ScoreText)text).setValue(JsonUtils.getString(jsonobject1, "value"));
                    }
                } else {
                    if (!jsonobject.has("selector")) {
                        throw new JsonParseException("Don't know how to turn " + jsonElement.toString() + " into a Component");
                    }

                    text = new SelectorText(JsonUtils.getString(jsonobject, "selector"));
                }

                if (jsonobject.has("extra")) {
                    JsonArray jsonarray2 = jsonobject.getAsJsonArray("extra");
                    if (jsonarray2.size() <= 0) {
                        throw new JsonParseException("Unexpected empty array of components");
                    }

                    for (int j = 0; j < jsonarray2.size(); j++) {
                        text.append(this.deserialize(jsonarray2.get(j), type, jsonDeserializationContext));
                    }
                }

                text.setStyle(jsonDeserializationContext.deserialize(jsonElement, Style.class));
                return text;
            }
        }

        private void addStyle(Style style, JsonObject json, JsonSerializationContext context) {
            JsonElement jsonelement = context.serialize(style);
            if (jsonelement.isJsonObject()) {
                JsonObject jsonobject = (JsonObject)jsonelement;

                for (Entry<String, JsonElement> entry : jsonobject.entrySet()) {
                    json.add(entry.getKey(), entry.getValue());
                }
            }
        }

        public JsonElement serialize(Text text, Type type, JsonSerializationContext jsonSerializationContext) {
            if (text instanceof LiteralText && text.getStyle().isEmpty() && text.getSiblings().isEmpty()) {
                return new JsonPrimitive(((LiteralText)text).getRawString());
            }

            JsonObject jsonobject = new JsonObject();
            if (!text.getStyle().isEmpty()) {
                this.addStyle(text.getStyle(), jsonobject, jsonSerializationContext);
            }

            if (!text.getSiblings().isEmpty()) {
                JsonArray jsonarray = new JsonArray();

                for (Text textx : text.getSiblings()) {
                    jsonarray.add(this.serialize(textx, textx.getClass(), jsonSerializationContext));
                }

                jsonobject.add("extra", jsonarray);
            }

            if (text instanceof LiteralText) {
                jsonobject.addProperty("text", ((LiteralText)text).getRawString());
            } else if (text instanceof TranslatableText) {
                TranslatableText translatabletext = (TranslatableText)text;
                jsonobject.addProperty("translate", translatabletext.getKey());
                if (translatabletext.getArgs() != null && translatabletext.getArgs().length > 0) {
                    JsonArray jsonarray1 = new JsonArray();

                    for (Object object : translatabletext.getArgs()) {
                        if (object instanceof Text) {
                            jsonarray1.add(this.serialize((Text)object, object.getClass(), jsonSerializationContext));
                        } else {
                            jsonarray1.add(new JsonPrimitive(String.valueOf(object)));
                        }
                    }

                    jsonobject.add("with", jsonarray1);
                }
            } else if (text instanceof ScoreText) {
                ScoreText scoretext = (ScoreText)text;
                JsonObject jsonobject1 = new JsonObject();
                jsonobject1.addProperty("name", scoretext.getOwner());
                jsonobject1.addProperty("objective", scoretext.getObjective());
                jsonobject1.addProperty("value", scoretext.getContent());
                jsonobject.add("score", jsonobject1);
            } else {
                if (!(text instanceof SelectorText)) {
                    throw new IllegalArgumentException("Don't know how to serialize " + text + " as a Component");
                }

                SelectorText selectortext = (SelectorText)text;
                jsonobject.addProperty("selector", selectortext.getPattern());
            }

            return jsonobject;
        }

        public static String toJson(Text text) {
            return GSON.toJson(text);
        }

        public static Text fromJson(String s) {
            return GSON.fromJson(s, Text.class);
        }

        static {
            GsonBuilder gsonbuilder = new GsonBuilder();
            gsonbuilder.registerTypeHierarchyAdapter(Text.class, new Text.Serializer());
            gsonbuilder.registerTypeHierarchyAdapter(Style.class, new Style.Serializer());
            gsonbuilder.registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory());
            GSON = gsonbuilder.create();
        }
    }
}
