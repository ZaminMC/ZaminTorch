package net.minecraft.client.render.model.block;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.math.Direction;

public class BlockElementFace {
    public static final Direction UNCULLED = null;
    public final Direction cullFace;
    public final int tintIndex;
    public final String texture;
    public final BlockElementTexture textureCoords;

    public BlockElementFace(Direction cullFace, int tintIndex, String texture, BlockElementTexture textureCoords) {
        this.cullFace = cullFace;
        this.tintIndex = tintIndex;
        this.texture = texture;
        this.textureCoords = textureCoords;
    }

    static class Serializer implements JsonDeserializer<BlockElementFace> {
        public BlockElementFace deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = jsonElement.getAsJsonObject();
            Direction direction = this.deserializeCullFace(jsonobject);
            int i = this.deserializeTintIndex(jsonobject);
            String s = this.deserializeTexture(jsonobject);
            BlockElementTexture blockelementtexture = jsonDeserializationContext.deserialize(jsonobject, BlockElementTexture.class);
            return new BlockElementFace(direction, i, s, blockelementtexture);
        }

        protected int deserializeTintIndex(JsonObject json) {
            return JsonUtils.getIntegerOrDefault(json, "tintindex", -1);
        }

        private String deserializeTexture(JsonObject json) {
            return JsonUtils.getString(json, "texture");
        }

        private Direction deserializeCullFace(JsonObject json) {
            String s = JsonUtils.getStringOrDefault(json, "cullface", "");
            return Direction.byKey(s);
        }
    }
}
