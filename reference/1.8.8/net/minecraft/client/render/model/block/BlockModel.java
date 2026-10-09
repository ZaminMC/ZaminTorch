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
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resource.Identifier;
import net.minecraft.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockModel {
    private static final Logger LOGGER = LogManager.getLogger();
    static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BlockModel.class, new BlockModel.Serializer())
        .registerTypeAdapter(BlockElement.class, new BlockElement.Serializer())
        .registerTypeAdapter(BlockElementFace.class, new BlockElementFace.Serializer())
        .registerTypeAdapter(BlockElementTexture.class, new BlockElementTexture.Serializer())
        .registerTypeAdapter(ModelTransformation.class, new ModelTransformation.Serializer())
        .registerTypeAdapter(ModelTransformations.class, new ModelTransformations.Serializer())
        .create();
    private final List<BlockElement> elements;
    private final boolean gui3d;
    private final boolean ambientOcclusion;
    private ModelTransformations transformations;
    public String name = "";
    protected final Map<String, String> textures;
    protected BlockModel parent;
    protected Identifier parentLocation;

    public static BlockModel fromJson(Reader reader) {
        return GSON.fromJson(reader, BlockModel.class);
    }

    public static BlockModel fromJson(String s) {
        return fromJson(new StringReader(s));
    }

    protected BlockModel(
        List<BlockElement> elements, Map<String, String> textures, boolean ambientOcclusion, boolean gui3d, ModelTransformations transformations
    ) {
        this(null, elements, textures, ambientOcclusion, gui3d, transformations);
    }

    protected BlockModel(Identifier parentLocation, Map<String, String> textures, boolean ambientOcclusion, boolean gui3d, ModelTransformations transformations) {
        this(parentLocation, Collections.emptyList(), textures, ambientOcclusion, gui3d, transformations);
    }

    private BlockModel(
        Identifier parentLocation,
        List<BlockElement> elements,
        Map<String, String> textures,
        boolean ambientOcclusion,
        boolean gui3d,
        ModelTransformations transformations
    ) {
        this.elements = elements;
        this.ambientOcclusion = ambientOcclusion;
        this.gui3d = gui3d;
        this.textures = textures;
        this.parentLocation = parentLocation;
        this.transformations = transformations;
    }

    public List<BlockElement> getElements() {
        return this.hasParent() ? this.parent.getElements() : this.elements;
    }

    private boolean hasParent() {
        return this.parent != null;
    }

    public boolean usesAmbientOcclusion() {
        return this.hasParent() ? this.parent.usesAmbientOcclusion() : this.ambientOcclusion;
    }

    public boolean isGui3d() {
        return this.gui3d;
    }

    public boolean isComplete() {
        return this.parentLocation == null || this.parent != null && this.parent.isComplete();
    }

    public void findParent(Map<Identifier, BlockModel> models) {
        if (this.parentLocation != null) {
            this.parent = models.get(this.parentLocation);
        }
    }

    public boolean hasTexture(String path) {
        return !"missingno".equals(this.getTexture(path));
    }

    public String getTexture(String path) {
        if (!this.isTextureReference(path)) {
            path = '#' + path;
        }

        return this.getTexture(path, new BlockModel.TextureContext(this));
    }

    private String getTexture(String path, BlockModel.TextureContext context) {
        if (this.isTextureReference(path)) {
            if (this == context.current) {
                LOGGER.warn("Unable to resolve texture due to upward reference: " + path + " in " + this.name);
                return "missingno";
            }

            String s = this.textures.get(path.substring(1));
            if (s == null && this.hasParent()) {
                s = this.parent.getTexture(path, context);
            }

            context.current = this;
            if (s != null && this.isTextureReference(s)) {
                s = context.root.getTexture(s, context);
            }

            return s != null && !this.isTextureReference(s) ? s : "missingno";
        } else {
            return path;
        }
    }

    private boolean isTextureReference(String path) {
        return path.charAt(0) == '#';
    }

    public Identifier getParentLocation() {
        return this.parentLocation;
    }

    public BlockModel getRoot() {
        return this.hasParent() ? this.parent.getRoot() : this;
    }

    public ModelTransformations getTransformations() {
        ModelTransformation modeltransformation = this.getTransformation(ModelTransformations.Type.THIRD_PERSON);
        ModelTransformation modeltransformation1 = this.getTransformation(ModelTransformations.Type.FIRST_PERSON);
        ModelTransformation modeltransformation2 = this.getTransformation(ModelTransformations.Type.HEAD);
        ModelTransformation modeltransformation3 = this.getTransformation(ModelTransformations.Type.GUI);
        ModelTransformation modeltransformation4 = this.getTransformation(ModelTransformations.Type.GROUND);
        ModelTransformation modeltransformation5 = this.getTransformation(ModelTransformations.Type.FIXED);
        return new ModelTransformations(
            modeltransformation, modeltransformation1, modeltransformation2, modeltransformation3, modeltransformation4, modeltransformation5
        );
    }

    private ModelTransformation getTransformation(ModelTransformations.Type type) {
        return this.parent != null && !this.transformations.has(type) ? this.parent.getTransformation(type) : this.transformations.get(type);
    }

    public static void checkHierarchy(Map<Identifier, BlockModel> models) {
        for (BlockModel blockmodel : models.values()) {
            try {
                BlockModel blockmodel1 = blockmodel.parent;

                for (BlockModel blockmodel2 = blockmodel1.parent; blockmodel1 != blockmodel2; blockmodel2 = blockmodel2.parent.parent) {
                    blockmodel1 = blockmodel1.parent;
                }

                throw new BlockModel.InvalidHierarchyException();
            } catch (NullPointerException nullpointerexception) {
            }
        }
    }

    public static class InvalidHierarchyException extends RuntimeException {
    }

    public static class Serializer implements JsonDeserializer<BlockModel> {
        public BlockModel deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = jsonElement.getAsJsonObject();
            List<BlockElement> list = this.deserializeElements(jsonDeserializationContext, jsonobject);
            String s = this.getParentName(jsonobject);
            boolean flag = StringUtils.isEmpty(s);
            boolean flag1 = list.isEmpty();
            if (flag1 && flag) {
                throw new JsonParseException("BlockModel requires either elements or parent, found neither");
            }

            if (!flag && !flag1) {
                throw new JsonParseException("BlockModel requires either elements or parent, found both");
            }

            Map<String, String> map = this.deserializeTextures(jsonobject);
            boolean flag2 = this.getAmbientOcclusion(jsonobject);
            ModelTransformations modeltransformations = ModelTransformations.NONE;
            if (jsonobject.has("display")) {
                JsonObject jsonobject1 = JsonUtils.getJsonObject(jsonobject, "display");
                modeltransformations = jsonDeserializationContext.deserialize(jsonobject1, ModelTransformations.class);
            }

            return flag1
                ? new BlockModel(new Identifier(s), map, flag2, true, modeltransformations)
                : new BlockModel(list, map, flag2, true, modeltransformations);
        }

        private Map<String, String> deserializeTextures(JsonObject json) {
            Map<String, String> map = Maps.newHashMap();
            if (json.has("textures")) {
                JsonObject jsonobject = json.getAsJsonObject("textures");

                for (Entry<String, JsonElement> entry : jsonobject.entrySet()) {
                    map.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            return map;
        }

        private String getParentName(JsonObject json) {
            return JsonUtils.getStringOrDefault(json, "parent", "");
        }

        protected boolean getAmbientOcclusion(JsonObject json) {
            return JsonUtils.getBooleanOrDefault(json, "ambientocclusion", true);
        }

        protected List<BlockElement> deserializeElements(JsonDeserializationContext context, JsonObject json) {
            List<BlockElement> list = Lists.newArrayList();
            if (json.has("elements")) {
                for (JsonElement jsonelement : JsonUtils.getJsonArray(json, "elements")) {
                    list.add(context.deserialize(jsonelement, BlockElement.class));
                }
            }

            return list;
        }
    }

    static final class TextureContext {
        public final BlockModel root;
        public BlockModel current;

        private TextureContext(BlockModel root) {
            this.root = root;
        }
    }
}
