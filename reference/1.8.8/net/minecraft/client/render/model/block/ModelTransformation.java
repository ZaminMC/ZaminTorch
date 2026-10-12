package net.minecraft.client.render.model.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.util.vector.Vector3f;

public class ModelTransformation {
    public static final ModelTransformation NONE = new ModelTransformation(new Vector3f(), new Vector3f(), new Vector3f(1.0F, 1.0F, 1.0F));
    public final Vector3f rotation;
    public final Vector3f translation;
    public final Vector3f scale;

    public ModelTransformation(Vector3f rotation, Vector3f translation, Vector3f scale) {
        this.rotation = new Vector3f(rotation);
        this.translation = new Vector3f(translation);
        this.scale = new Vector3f(scale);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (this.getClass() != object.getClass()) {
            return false;
        }

        ModelTransformation modeltransformation = (ModelTransformation)object;
        return this.rotation.equals(modeltransformation.rotation)
            && this.scale.equals(modeltransformation.scale)
            && this.translation.equals(modeltransformation.translation);
    }

    @Override
    public int hashCode() {
        int i = this.rotation.hashCode();
        i = 31 * i + this.translation.hashCode();
        return 31 * i + this.scale.hashCode();
    }

    static class Serializer implements JsonDeserializer<ModelTransformation> {
        private static final Vector3f DEFAULT_ROTATION = new Vector3f(0.0F, 0.0F, 0.0F);
        private static final Vector3f DEFAULT_TRANSLATION = new Vector3f(0.0F, 0.0F, 0.0F);
        private static final Vector3f DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);

        public ModelTransformation deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = jsonElement.getAsJsonObject();
            Vector3f vector3f = this.deserializeVector3f(jsonobject, "rotation", DEFAULT_ROTATION);
            Vector3f vector3f1 = this.deserializeVector3f(jsonobject, "translation", DEFAULT_TRANSLATION);
            vector3f1.scale(0.0625F);
            vector3f1.x = MathHelper.clamp(vector3f1.x, -1.5F, 1.5F);
            vector3f1.y = MathHelper.clamp(vector3f1.y, -1.5F, 1.5F);
            vector3f1.z = MathHelper.clamp(vector3f1.z, -1.5F, 1.5F);
            Vector3f vector3f2 = this.deserializeVector3f(jsonobject, "scale", DEFAULT_SCALE);
            vector3f2.x = MathHelper.clamp(vector3f2.x, -4.0F, 4.0F);
            vector3f2.y = MathHelper.clamp(vector3f2.y, -4.0F, 4.0F);
            vector3f2.z = MathHelper.clamp(vector3f2.z, -4.0F, 4.0F);
            return new ModelTransformation(vector3f, vector3f1, vector3f2);
        }

        private Vector3f deserializeVector3f(JsonObject json, String key, Vector3f defaultValue) {
            if (!json.has(key)) {
                return defaultValue;
            }

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
