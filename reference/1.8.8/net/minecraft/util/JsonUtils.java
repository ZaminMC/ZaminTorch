package net.minecraft.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;

public class JsonUtils {
    public static boolean hasString(JsonObject json, String key) {
        return hasJsonPrimitive(json, key) && json.getAsJsonPrimitive(key).isString();
    }

    public static boolean isString(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    public static boolean hasBoolean(JsonObject json, String key) {
        return hasJsonPrimitive(json, key) && json.getAsJsonPrimitive(key).isBoolean();
    }

    public static boolean hasJsonArray(JsonObject json, String key) {
        return hasElement(json, key) && json.get(key).isJsonArray();
    }

    public static boolean hasJsonPrimitive(JsonObject json, String key) {
        return hasElement(json, key) && json.get(key).isJsonPrimitive();
    }

    public static boolean hasElement(JsonObject json, String key) {
        return json != null && json.get(key) != null;
    }

    public static String asString(JsonElement element, String key) {
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a string, was " + getType(element));
        }
    }

    public static String getString(JsonObject json, String key) {
        if (json.has(key)) {
            return asString(json.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a string");
        }
    }

    public static String getStringOrDefault(JsonObject json, String key, String defaultValue) {
        return json.has(key) ? asString(json.get(key), key) : defaultValue;
    }

    public static boolean asBoolean(JsonElement element, String key) {
        if (element.isJsonPrimitive()) {
            return element.getAsBoolean();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a Boolean, was " + getType(element));
        }
    }

    public static boolean getBoolean(JsonObject json, String key) {
        if (json.has(key)) {
            return asBoolean(json.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a Boolean");
        }
    }

    public static boolean getBooleanOrDefault(JsonObject json, String key, boolean defaultValue) {
        return json.has(key) ? asBoolean(json.get(key), key) : defaultValue;
    }

    public static float asFloat(JsonElement element, String key) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsFloat();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a Float, was " + getType(element));
        }
    }

    public static float getFloat(JsonObject json, String key) {
        if (json.has(key)) {
            return asFloat(json.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a Float");
        }
    }

    public static float getFloatOrDefault(JsonObject json, String key, float defaultValue) {
        return json.has(key) ? asFloat(json.get(key), key) : defaultValue;
    }

    public static int asInteger(JsonElement element, String key) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a Int, was " + getType(element));
        }
    }

    public static int getInteger(JsonObject json, String key) {
        if (json.has(key)) {
            return asInteger(json.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a Int");
        }
    }

    public static int getIntegerOrDefault(JsonObject json, String key, int defaultValue) {
        return json.has(key) ? asInteger(json.get(key), key) : defaultValue;
    }

    public static JsonObject asJsonObject(JsonElement element, String key) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a JsonObject, was " + getType(element));
        }
    }

    public static JsonObject getJsonObject(JsonObject json, String key) {
        if (json.has(key)) {
            return asJsonObject(json.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a JsonObject");
        }
    }

    public static JsonObject getJsonObjectOrDefault(JsonObject json, String key, JsonObject defaultValue) {
        return json.has(key) ? asJsonObject(json.get(key), key) : defaultValue;
    }

    public static JsonArray asJsonArray(JsonElement element, String key) {
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        } else {
            throw new JsonSyntaxException("Expected " + key + " to be a JsonArray, was " + getType(element));
        }
    }

    public static JsonArray getJsonArray(JsonObject json, String key) {
        if (json.has(key)) {
            return asJsonArray(json.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find a JsonArray");
        }
    }

    public static JsonArray getJsonArrayOrDefault(JsonObject json, String key, JsonArray defaultValue) {
        return json.has(key) ? asJsonArray(json.get(key), key) : defaultValue;
    }

    public static String getType(JsonElement element) {
        String s = StringUtils.abbreviateMiddle(String.valueOf(element), "...", 10);
        if (element == null) {
            return "null (missing)";
        }

        if (element.isJsonNull()) {
            return "null (json)";
        }

        if (element.isJsonArray()) {
            return "an array (" + s + ")";
        }

        if (element.isJsonObject()) {
            return "an object (" + s + ")";
        }

        if (element.isJsonPrimitive()) {
            JsonPrimitive jsonprimitive = element.getAsJsonPrimitive();
            if (jsonprimitive.isNumber()) {
                return "a number (" + s + ")";
            }

            if (jsonprimitive.isBoolean()) {
                return "a boolean (" + s + ")";
            }
        }

        return s;
    }
}
