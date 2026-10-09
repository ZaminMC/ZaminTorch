package net.minecraft.client.render.model.block;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.util.vector.Vector3f;

public class BlockElement {
    public final Vector3f from;
    public final Vector3f to;
    public final Map<Direction, BlockElementFace> faces;
    public final BlockElementRotation rotation;
    public final boolean shade;

    public BlockElement(Vector3f from, Vector3f to, Map<Direction, BlockElementFace> faces, BlockElementRotation rotation, boolean shade) {
        this.from = from;
        this.to = to;
        this.faces = faces;
        this.rotation = rotation;
        this.shade = shade;
        this.init();
    }

    private void init() {
        for (Entry<Direction, BlockElementFace> entry : this.faces.entrySet()) {
            float[] afloat = this.getTextureCoords(entry.getKey());
            entry.getValue().textureCoords.setCoordinates(afloat);
        }
    }

    private float[] getTextureCoords(Direction face) {
        float[] afloat;
        switch (face) {
            case DOWN:
            case UP:
                afloat = new float[]{this.from.x, this.from.z, this.to.x, this.to.z};
                break;
            case NORTH:
            case SOUTH:
                afloat = new float[]{this.from.x, 16.0F - this.to.y, this.to.x, 16.0F - this.from.y};
                break;
            case WEST:
            case EAST:
                afloat = new float[]{this.from.z, 16.0F - this.to.y, this.to.z, 16.0F - this.from.y};
                break;
            default:
                throw new NullPointerException();
        }

        return afloat;
    }

    static class Serializer implements JsonDeserializer<BlockElement> {
        public BlockElement deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = jsonElement.getAsJsonObject();
            Vector3f vector3f = this.deserializeFrom(jsonobject);
            Vector3f vector3f1 = this.deserializeTo(jsonobject);
            BlockElementRotation blockelementrotation = this.deserializeRotation(jsonobject);
            Map<Direction, BlockElementFace> map = this.deserializeFaces(jsonDeserializationContext, jsonobject);
            if (jsonobject.has("shade") && !JsonUtils.hasBoolean(jsonobject, "shade")) {
                throw new JsonParseException("Expected shade to be a Boolean");
            }

            boolean flag = JsonUtils.getBooleanOrDefault(jsonobject, "shade", true);
            return new BlockElement(vector3f, vector3f1, map, blockelementrotation, flag);
        }

        private BlockElementRotation deserializeRotation(JsonObject json) {
            BlockElementRotation blockelementrotation = null;
            if (json.has("rotation")) {
                JsonObject jsonobject = JsonUtils.getJsonObject(json, "rotation");
                Vector3f vector3f = this.deserializeVector3f(jsonobject, "origin");
                vector3f.scale(0.0625F);
                Direction.Axis direction$axis = this.deserializeAxis(jsonobject);
                float f = this.deserializeAngle(jsonobject);
                boolean flag = JsonUtils.getBooleanOrDefault(jsonobject, "rescale", false);
                blockelementrotation = new BlockElementRotation(vector3f, direction$axis, f, flag);
            }

            return blockelementrotation;
        }

        private float deserializeAngle(JsonObject json) {
            float f = JsonUtils.getFloat(json, "angle");
            if (f != 0.0F && MathHelper.abs(f) != 22.5F && MathHelper.abs(f) != 45.0F) {
                throw new JsonParseException("Invalid rotation " + f + " found, only -45/-22.5/0/22.5/45 allowed");
            } else {
                return f;
            }
        }

        private Direction.Axis deserializeAxis(JsonObject json) {
            String s = JsonUtils.getString(json, "axis");
            Direction.Axis direction$axis = Direction.Axis.byName(s.toLowerCase());
            if (direction$axis == null) {
                throw new JsonParseException("Invalid rotation axis: " + s);
            } else {
                return direction$axis;
            }
        }

        private Map<Direction, BlockElementFace> deserializeFaces(JsonDeserializationContext context, JsonObject json) {
            Map<Direction, BlockElementFace> map = this.deserializeFacesFilterNull(context, json);
            if (map.isEmpty()) {
                throw new JsonParseException("Expected between 1 and 6 unique faces, got 0");
            } else {
                return map;
            }
        }

        private Map<Direction, BlockElementFace> deserializeFacesFilterNull(JsonDeserializationContext context, JsonObject json) {
            Map<Direction, BlockElementFace> map = Maps.newEnumMap(Direction.class);
            JsonObject jsonobject = JsonUtils.getJsonObject(json, "faces");

            for (Entry<String, JsonElement> entry : jsonobject.entrySet()) {
                Direction direction = this.deserializeFacing(entry.getKey());
                map.put(direction, context.deserialize(entry.getValue(), BlockElementFace.class));
            }

            return map;
        }

        private Direction deserializeFacing(String key) {
            Direction direction = Direction.byKey(key);
            if (direction == null) {
                throw new JsonParseException("Unknown facing: " + key);
            } else {
                return direction;
            }
        }

        private Vector3f deserializeTo(JsonObject json) {
            Vector3f vector3f = this.deserializeVector3f(json, "to");
            if (!(vector3f.x < -16.0F)
                && !(vector3f.y < -16.0F)
                && !(vector3f.z < -16.0F)
                && !(vector3f.x > 32.0F)
                && !(vector3f.y > 32.0F)
                && !(vector3f.z > 32.0F)) {
                return vector3f;
            } else {
                throw new JsonParseException("'to' specifier exceeds the allowed boundaries: " + vector3f);
            }
        }

        private Vector3f deserializeFrom(JsonObject json) {
            Vector3f vector3f = this.deserializeVector3f(json, "from");
            if (!(vector3f.x < -16.0F)
                && !(vector3f.y < -16.0F)
                && !(vector3f.z < -16.0F)
                && !(vector3f.x > 32.0F)
                && !(vector3f.y > 32.0F)
                && !(vector3f.z > 32.0F)) {
                return vector3f;
            } else {
                throw new JsonParseException("'from' specifier exceeds the allowed boundaries: " + vector3f);
            }
        }

        private Vector3f deserializeVector3f(JsonObject json, String key) {
            JsonArray jsonarray = JsonUtils.getJsonArray(json, key);
            if (jsonarray.size() != 3) {
                throw new JsonParseException("Expected 3 " + key + " values, found: " + jsonarray.size());
            }

            float[] afloat = new float[3];

            for (int i = 0; i < afloat.length; i++) {
                afloat[i] = JsonUtils.asFloat(jsonarray.get(i), key + "[" + i + "]");
            }

            return new Vector3f(afloat[0], afloat[1], afloat[2]);
        }
    }
}
