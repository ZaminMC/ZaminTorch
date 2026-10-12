package net.minecraft.client.render.model.block;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.resource.model.ModelRotation;
import net.minecraft.resource.Identifier;
import net.minecraft.util.JsonUtils;

public class BlockModelDefinition {
    static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BlockModelDefinition.class, new BlockModelDefinition.Serializer())
        .registerTypeAdapter(BlockModelDefinition.Variant.class, new BlockModelDefinition.Variant.Serializer())
        .create();
    private final Map<String, BlockModelDefinition.MultiVariant> variants = Maps.newHashMap();

    public static BlockModelDefinition fromJson(Reader reader) {
        return GSON.fromJson(reader, BlockModelDefinition.class);
    }

    public BlockModelDefinition(Collection<BlockModelDefinition.MultiVariant> variants) {
        for (BlockModelDefinition.MultiVariant blockmodeldefinition$multivariant : variants) {
            this.variants.put(blockmodeldefinition$multivariant.name, blockmodeldefinition$multivariant);
        }
    }

    public BlockModelDefinition(List<BlockModelDefinition> models) {
        for (BlockModelDefinition blockmodeldefinition : models) {
            this.variants.putAll(blockmodeldefinition.variants);
        }
    }

    public BlockModelDefinition.MultiVariant getVariant(String name) {
        BlockModelDefinition.MultiVariant blockmodeldefinition$multivariant = this.variants.get(name);
        if (blockmodeldefinition$multivariant == null) {
            throw new BlockModelDefinition.NoSuchVariantException();
        } else {
            return blockmodeldefinition$multivariant;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof BlockModelDefinition) {
            BlockModelDefinition blockmodeldefinition = (BlockModelDefinition)object;
            return this.variants.equals(blockmodeldefinition.variants);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.variants.hashCode();
    }

    public static class MultiVariant {
        private final String name;
        private final List<BlockModelDefinition.Variant> variants;

        public MultiVariant(String name, List<BlockModelDefinition.Variant> variants) {
            this.name = name;
            this.variants = variants;
        }

        public List<BlockModelDefinition.Variant> getVariants() {
            return this.variants;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof BlockModelDefinition.MultiVariant)) {
                return false;
            }

            BlockModelDefinition.MultiVariant blockmodeldefinition$multivariant = (BlockModelDefinition.MultiVariant)object;
            return this.name.equals(blockmodeldefinition$multivariant.name) && this.variants.equals(blockmodeldefinition$multivariant.variants);
        }

        @Override
        public int hashCode() {
            int i = this.name.hashCode();
            return 31 * i + this.variants.hashCode();
        }
    }

    public class NoSuchVariantException extends RuntimeException {
        protected NoSuchVariantException() {
        }
    }

    public static class Serializer implements JsonDeserializer<BlockModelDefinition> {
        public BlockModelDefinition deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = jsonElement.getAsJsonObject();
            List<BlockModelDefinition.MultiVariant> list = this.deserializeVariants(jsonDeserializationContext, jsonobject);
            return new BlockModelDefinition((Collection<BlockModelDefinition.MultiVariant>)list);
        }

        protected List<BlockModelDefinition.MultiVariant> deserializeVariants(JsonDeserializationContext context, JsonObject json) {
            JsonObject jsonobject = JsonUtils.getJsonObject(json, "variants");
            List<BlockModelDefinition.MultiVariant> list = Lists.newArrayList();

            for (Entry<String, JsonElement> entry : jsonobject.entrySet()) {
                list.add(this.deserializeVariant(context, entry));
            }

            return list;
        }

        protected BlockModelDefinition.MultiVariant deserializeVariant(JsonDeserializationContext context, Entry<String, JsonElement> entry) {
            String s = entry.getKey();
            List<BlockModelDefinition.Variant> list = Lists.newArrayList();
            JsonElement jsonelement = entry.getValue();
            if (jsonelement.isJsonArray()) {
                for (JsonElement jsonelement1 : jsonelement.getAsJsonArray()) {
                    list.add(context.deserialize(jsonelement1, BlockModelDefinition.Variant.class));
                }
            } else {
                list.add(context.deserialize(jsonelement, BlockModelDefinition.Variant.class));
            }

            return new BlockModelDefinition.MultiVariant(s, list);
        }
    }

    public static class Variant {
        private final Identifier location;
        private final ModelRotation rotation;
        private final boolean uvLock;
        private final int weight;

        public Variant(Identifier location, ModelRotation rotation, boolean uvLock, int weight) {
            this.location = location;
            this.rotation = rotation;
            this.uvLock = uvLock;
            this.weight = weight;
        }

        public Identifier getLocation() {
            return this.location;
        }

        public ModelRotation getRotation() {
            return this.rotation;
        }

        public boolean isUvLocked() {
            return this.uvLock;
        }

        public int getWeight() {
            return this.weight;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof BlockModelDefinition.Variant)) {
                return false;
            }

            BlockModelDefinition.Variant blockmodeldefinition$variant = (BlockModelDefinition.Variant)object;
            return this.location.equals(blockmodeldefinition$variant.location)
                && this.rotation == blockmodeldefinition$variant.rotation
                && this.uvLock == blockmodeldefinition$variant.uvLock;
        }

        @Override
        public int hashCode() {
            int i = this.location.hashCode();
            i = 31 * i + (this.rotation != null ? this.rotation.hashCode() : 0);
            return 31 * i + (this.uvLock ? 1 : 0);
        }

        public static class Serializer implements JsonDeserializer<BlockModelDefinition.Variant> {
            public BlockModelDefinition.Variant deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
                JsonObject jsonobject = jsonElement.getAsJsonObject();
                String s = this.deserializePath(jsonobject);
                ModelRotation modelrotation = this.deserializeRotation(jsonobject);
                boolean flag = this.deserializeUvLock(jsonobject);
                int i = this.deserializeWeight(jsonobject);
                return new BlockModelDefinition.Variant(this.getLocation(s), modelrotation, flag, i);
            }

            private Identifier getLocation(String path) {
                Identifier identifier = new Identifier(path);
                return new Identifier(identifier.getNamespace(), "block/" + identifier.getPath());
            }

            private boolean deserializeUvLock(JsonObject json) {
                return JsonUtils.getBooleanOrDefault(json, "uvlock", false);
            }

            protected ModelRotation deserializeRotation(JsonObject json) {
                int i = JsonUtils.getIntegerOrDefault(json, "x", 0);
                int j = JsonUtils.getIntegerOrDefault(json, "y", 0);
                ModelRotation modelrotation = ModelRotation.by(i, j);
                if (modelrotation == null) {
                    throw new JsonParseException("Invalid BlockModelRotation x: " + i + ", y: " + j);
                } else {
                    return modelrotation;
                }
            }

            protected String deserializePath(JsonObject json) {
                return JsonUtils.getString(json, "model");
            }

            protected int deserializeWeight(JsonObject json) {
                return JsonUtils.getIntegerOrDefault(json, "weight", 1);
            }
        }
    }
}
