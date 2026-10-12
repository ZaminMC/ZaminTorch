package net.minecraft.client.render.model.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.minecraft.util.JsonUtils;

public class BlockElementTexture {
    public float[] coordinates;
    public final int rotation;

    public BlockElementTexture(float[] coordinates, int rotation) {
        this.coordinates = coordinates;
        this.rotation = rotation;
    }

    public float getU(int vertex) {
        if (this.coordinates == null) {
            throw new NullPointerException("uvs");
        }

        int i = this.index(vertex);
        return i != 0 && i != 1 ? this.coordinates[2] : this.coordinates[0];
    }

    public float getV(int vertex) {
        if (this.coordinates == null) {
            throw new NullPointerException("uvs");
        }

        int i = this.index(vertex);
        return i != 0 && i != 3 ? this.coordinates[3] : this.coordinates[1];
    }

    private int index(int vertex) {
        return (vertex + this.rotation / 90) % 4;
    }

    public int reverseIndex(int vertex) {
        return (vertex + (4 - this.rotation / 90)) % 4;
    }

    public void setCoordinates(float[] coordinates) {
        if (this.coordinates == null) {
            this.coordinates = coordinates;
        }
    }

    static class Serializer implements JsonDeserializer<BlockElementTexture> {
        public BlockElementTexture deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = jsonElement.getAsJsonObject();
            float[] afloat = this.deserializeCoordinates(jsonobject);
            int i = this.deserializeRotation(jsonobject);
            return new BlockElementTexture(afloat, i);
        }

        protected int deserializeRotation(JsonObject json) {
            int i = JsonUtils.getIntegerOrDefault(json, "rotation", 0);
            if (i >= 0 && i % 90 == 0 && i / 90 <= 3) {
                return i;
            } else {
                throw new JsonParseException("Invalid rotation " + i + " found, only 0/90/180/270 allowed");
            }
        }

        private float[] deserializeCoordinates(JsonObject json) {
            if (!json.has("uv")) {
                return null;
            }

            JsonArray jsonarray = JsonUtils.getJsonArray(json, "uv");
            if (jsonarray.size() != 4) {
                throw new JsonParseException("Expected 4 uv values, found: " + jsonarray.size());
            }

            float[] afloat = new float[4];

            for (int i = 0; i < afloat.length; i++) {
                afloat[i] = JsonUtils.asFloat(jsonarray.get(i), "uv[" + i + "]");
            }

            return afloat;
        }
    }
}
