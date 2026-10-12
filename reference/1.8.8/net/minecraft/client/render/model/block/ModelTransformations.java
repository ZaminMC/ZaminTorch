package net.minecraft.client.render.model.block;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.render.platform.GlStateManager;

public class ModelTransformations {
    public static final ModelTransformations NONE = new ModelTransformations();
    public static float translateX = 0.0F;
    public static float translateY = 0.0F;
    public static float translateZ = 0.0F;
    public static float rotateX = 0.0F;
    public static float rotateY = 0.0F;
    public static float rotateZ = 0.0F;
    public static float scaleX = 0.0F;
    public static float scaleY = 0.0F;
    public static float scaleZ = 0.0F;
    public final ModelTransformation thirdPerson;
    public final ModelTransformation firstPerson;
    public final ModelTransformation head;
    public final ModelTransformation gui;
    public final ModelTransformation ground;
    public final ModelTransformation fixed;

    private ModelTransformations() {
        this(
            ModelTransformation.NONE,
            ModelTransformation.NONE,
            ModelTransformation.NONE,
            ModelTransformation.NONE,
            ModelTransformation.NONE,
            ModelTransformation.NONE
        );
    }

    public ModelTransformations(ModelTransformations transformations) {
        this.thirdPerson = transformations.thirdPerson;
        this.firstPerson = transformations.firstPerson;
        this.head = transformations.head;
        this.gui = transformations.gui;
        this.ground = transformations.ground;
        this.fixed = transformations.fixed;
    }

    public ModelTransformations(
        ModelTransformation thirdPerson,
        ModelTransformation firstPerson,
        ModelTransformation head,
        ModelTransformation gui,
        ModelTransformation ground,
        ModelTransformation fixed
    ) {
        this.thirdPerson = thirdPerson;
        this.firstPerson = firstPerson;
        this.head = head;
        this.gui = gui;
        this.ground = ground;
        this.fixed = fixed;
    }

    public void apply(ModelTransformations.Type type) {
        ModelTransformation modeltransformation = this.get(type);
        if (modeltransformation != ModelTransformation.NONE) {
            GlStateManager.translatef(
                modeltransformation.translation.x + translateX, modeltransformation.translation.y + translateY, modeltransformation.translation.z + translateZ
            );
            GlStateManager.rotatef(modeltransformation.rotation.y + rotateY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(modeltransformation.rotation.x + rotateX, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(modeltransformation.rotation.z + rotateZ, 0.0F, 0.0F, 1.0F);
            GlStateManager.scalef(modeltransformation.scale.x + scaleX, modeltransformation.scale.y + scaleY, modeltransformation.scale.z + scaleZ);
        }
    }

    public ModelTransformation get(ModelTransformations.Type type) {
        switch (type) {
            case THIRD_PERSON:
                return this.thirdPerson;
            case FIRST_PERSON:
                return this.firstPerson;
            case HEAD:
                return this.head;
            case GUI:
                return this.gui;
            case GROUND:
                return this.ground;
            case FIXED:
                return this.fixed;
            default:
                return ModelTransformation.NONE;
        }
    }

    public boolean has(ModelTransformations.Type type) {
        return !this.get(type).equals(ModelTransformation.NONE);
    }

    static class Serializer implements JsonDeserializer<ModelTransformations> {
        public ModelTransformations deserialize(JsonElement jsonElement, java.lang.reflect.Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = jsonElement.getAsJsonObject();
            ModelTransformation modeltransformation = this.deserializeItemTransform(jsonDeserializationContext, jsonobject, "thirdperson");
            ModelTransformation modeltransformation1 = this.deserializeItemTransform(jsonDeserializationContext, jsonobject, "firstperson");
            ModelTransformation modeltransformation2 = this.deserializeItemTransform(jsonDeserializationContext, jsonobject, "head");
            ModelTransformation modeltransformation3 = this.deserializeItemTransform(jsonDeserializationContext, jsonobject, "gui");
            ModelTransformation modeltransformation4 = this.deserializeItemTransform(jsonDeserializationContext, jsonobject, "ground");
            ModelTransformation modeltransformation5 = this.deserializeItemTransform(jsonDeserializationContext, jsonobject, "fixed");
            return new ModelTransformations(
                modeltransformation, modeltransformation1, modeltransformation2, modeltransformation3, modeltransformation4, modeltransformation5
            );
        }

        private ModelTransformation deserializeItemTransform(JsonDeserializationContext context, JsonObject json, String key) {
            return json.has(key) ? context.deserialize(json.get(key), ModelTransformation.class) : ModelTransformation.NONE;
        }
    }

    public enum Type {
        NONE,
        THIRD_PERSON,
        FIRST_PERSON,
        HEAD,
        GUI,
        GROUND,
        FIXED;
    }
}
