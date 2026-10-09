package net.minecraft.client.render;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.shaders.BlendMode;
import net.minecraft.client.render.shaders.DummyUniform;
import net.minecraft.client.render.shaders.Program;
import net.minecraft.client.render.shaders.ProgramManager;
import net.minecraft.client.render.shaders.Uniform;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.util.JsonUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Effect {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DummyUniform DUMMY_UNIFORM = new DummyUniform();
    private static Effect lastAppliedEffect = null;
    private static int lastProgramId = -1;
    private static boolean cullEnabled = true;
    private final Map<String, Object> samplerMap = Maps.newHashMap();
    private final List<String> samplerNames = Lists.newArrayList();
    private final List<Integer> samplerLocations = Lists.newArrayList();
    private final List<Uniform> uniforms = Lists.newArrayList();
    private final List<Integer> uniformLocations = Lists.newArrayList();
    private final Map<String, Uniform> uniformMap = Maps.newHashMap();
    private final int programId;
    private final String name;
    private final boolean cull;
    private boolean dirty;
    private final BlendMode blend;
    private final List<Integer> attributes;
    private final List<String> attributeNames;
    private final Program vertexProgram;
    private final Program fragmentProgram;

    public Effect(ResourceManager resourceManager, String name) throws IOException {
        JsonParser jsonparser = new JsonParser();
        Identifier identifier = new Identifier("shaders/program/" + name + ".json");
        this.name = name;
        InputStream inputstream = null;

        try {
            inputstream = resourceManager.getResource(identifier).asStream();
            JsonObject jsonobject = jsonparser.parse(IOUtils.toString(inputstream, Charsets.UTF_8)).getAsJsonObject();
            String s = JsonUtils.getString(jsonobject, "vertex");
            String s1 = JsonUtils.getString(jsonobject, "fragment");
            JsonArray jsonarray = JsonUtils.getJsonArrayOrDefault(jsonobject, "samplers", null);
            if (jsonarray != null) {
                int i = 0;

                for (JsonElement jsonelement : jsonarray) {
                    try {
                        this.parseSamplerNode(jsonelement);
                    } catch (Exception exception2) {
                        ChainedJsonException chainedjsonexception1 = ChainedJsonException.forException(exception2);
                        chainedjsonexception1.prependJsonKey("samplers[" + i + "]");
                        throw chainedjsonexception1;
                    }

                    i++;
                }
            }

            JsonArray jsonarray1 = JsonUtils.getJsonArrayOrDefault(jsonobject, "attributes", null);
            if (jsonarray1 != null) {
                int j = 0;
                this.attributes = Lists.newArrayListWithCapacity(jsonarray1.size());
                this.attributeNames = Lists.newArrayListWithCapacity(jsonarray1.size());

                for (JsonElement jsonelement1 : jsonarray1) {
                    try {
                        this.attributeNames.add(JsonUtils.asString(jsonelement1, "attribute"));
                    } catch (Exception exception1) {
                        ChainedJsonException chainedjsonexception2 = ChainedJsonException.forException(exception1);
                        chainedjsonexception2.prependJsonKey("attributes[" + j + "]");
                        throw chainedjsonexception2;
                    }

                    j++;
                }
            } else {
                this.attributes = null;
                this.attributeNames = null;
            }

            JsonArray jsonarray2 = JsonUtils.getJsonArrayOrDefault(jsonobject, "uniforms", null);
            if (jsonarray2 != null) {
                int k = 0;

                for (JsonElement jsonelement2 : jsonarray2) {
                    try {
                        this.parseUniformNode(jsonelement2);
                    } catch (Exception exception) {
                        ChainedJsonException chainedjsonexception3 = ChainedJsonException.forException(exception);
                        chainedjsonexception3.prependJsonKey("uniforms[" + k + "]");
                        throw chainedjsonexception3;
                    }

                    k++;
                }
            }

            this.blend = BlendMode.fromJson(JsonUtils.getJsonObjectOrDefault(jsonobject, "blend", null));
            this.cull = JsonUtils.getBooleanOrDefault(jsonobject, "cull", true);
            this.vertexProgram = Program.compileShader(resourceManager, Program.Type.VERTEX, s);
            this.fragmentProgram = Program.compileShader(resourceManager, Program.Type.FRAGMENT, s1);
            this.programId = ProgramManager.getInstance().createProgram();
            ProgramManager.getInstance().linkProgram(this);
            this.updateLocations();
            if (this.attributeNames != null) {
                for (String s2 : this.attributeNames) {
                    int l = GLX.getAttribLocation(this.programId, s2);
                    this.attributes.add(l);
                }
            }
        } catch (Exception exception3) {
            ChainedJsonException chainedjsonexception = ChainedJsonException.forException(exception3);
            chainedjsonexception.setFileNameAndFlush(identifier.getPath());
            throw chainedjsonexception;
        } finally {
            IOUtils.closeQuietly(inputstream);
        }

        this.markDirty();
    }

    public void close() {
        ProgramManager.getInstance().releaseProgram(this);
    }

    public void clear() {
        GLX.useProgram(0);
        lastProgramId = -1;
        lastAppliedEffect = null;
        cullEnabled = true;

        for (int i = 0; i < this.samplerLocations.size(); i++) {
            if (this.samplerMap.get(this.samplerNames.get(i)) != null) {
                GlStateManager.activeTexture(GLX.GL_TEXTURE0 + i);
                GlStateManager.bindTexture(0);
            }
        }
    }

    public void apply() {
        this.dirty = false;
        lastAppliedEffect = this;
        this.blend.apply();
        if (this.programId != lastProgramId) {
            GLX.useProgram(this.programId);
            lastProgramId = this.programId;
        }

        if (this.cull) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }

        for (int i = 0; i < this.samplerLocations.size(); i++) {
            if (this.samplerMap.get(this.samplerNames.get(i)) != null) {
                GlStateManager.activeTexture(GLX.GL_TEXTURE0 + i);
                GlStateManager.enableTexture();
                Object object = this.samplerMap.get(this.samplerNames.get(i));
                int j = -1;
                if (object instanceof RenderTarget) {
                    j = ((RenderTarget)object).colorTextureId;
                } else if (object instanceof Texture) {
                    j = ((Texture)object).getGlId();
                } else if (object instanceof Integer) {
                    j = (Integer)object;
                }

                if (j != -1) {
                    GlStateManager.bindTexture(j);
                    GLX.uniform1i(GLX.getUniformLocation(this.programId, this.samplerNames.get(i)), i);
                }
            }
        }

        for (Uniform uniform : this.uniforms) {
            uniform.upload();
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public Uniform getUniform(String name) {
        return this.uniformMap.containsKey(name) ? this.uniformMap.get(name) : null;
    }

    public Uniform safeGetUniform(String name) {
        return this.uniformMap.containsKey(name) ? this.uniformMap.get(name) : DUMMY_UNIFORM;
    }

    private void updateLocations() {
        int i = 0;

        for (int j = 0; i < this.samplerNames.size(); j++) {
            String s = this.samplerNames.get(i);
            int k = GLX.getUniformLocation(this.programId, s);
            if (k == -1) {
                LOGGER.warn("Shader " + this.name + "could not find sampler named " + s + " in the specified shader program.");
                this.samplerMap.remove(s);
                this.samplerNames.remove(j);
                j--;
            } else {
                this.samplerLocations.add(k);
            }

            i++;
        }

        for (Uniform uniform : this.uniforms) {
            String s1 = uniform.getName();
            int l = GLX.getUniformLocation(this.programId, s1);
            if (l == -1) {
                LOGGER.warn("Could not find uniform named " + s1 + " in the specified" + " shader program.");
            } else {
                this.uniformLocations.add(l);
                uniform.setLocation(l);
                this.uniformMap.put(s1, uniform);
            }
        }
    }

    private void parseSamplerNode(JsonElement json) throws ChainedJsonException {
        JsonObject jsonobject = JsonUtils.asJsonObject(json, "sampler");
        String s = JsonUtils.getString(jsonobject, "name");
        if (!JsonUtils.hasString(jsonobject, "file")) {
            this.samplerMap.put(s, null);
            this.samplerNames.add(s);
        } else {
            this.samplerNames.add(s);
        }
    }

    public void setSampler(String name, Object sampler) {
        if (this.samplerMap.containsKey(name)) {
            this.samplerMap.remove(name);
        }

        this.samplerMap.put(name, sampler);
        this.markDirty();
    }

    private void parseUniformNode(JsonElement json) throws ChainedJsonException {
        JsonObject jsonobject = JsonUtils.asJsonObject(json, "uniform");
        String s = JsonUtils.getString(jsonobject, "name");
        int i = Uniform.getTypeFromString(JsonUtils.getString(jsonobject, "type"));
        int j = JsonUtils.getInteger(jsonobject, "count");
        float[] afloat = new float[Math.max(j, 16)];
        JsonArray jsonarray = JsonUtils.getJsonArray(jsonobject, "values");
        if (jsonarray.size() != j && jsonarray.size() > 1) {
            throw new ChainedJsonException("Invalid amount of values specified (expected " + j + ", found " + jsonarray.size() + ")");
        }

        int k = 0;

        for (JsonElement jsonelement : jsonarray) {
            try {
                afloat[k] = JsonUtils.asFloat(jsonelement, "value");
            } catch (Exception exception) {
                ChainedJsonException chainedjsonexception = ChainedJsonException.forException(exception);
                chainedjsonexception.prependJsonKey("values[" + k + "]");
                throw chainedjsonexception;
            }

            k++;
        }

        if (j > 1 && jsonarray.size() == 1) {
            while (k < j) {
                afloat[k] = afloat[0];
                k++;
            }
        }

        int l = j > 1 && j <= 4 && i < 8 ? j - 1 : 0;
        Uniform uniform = new Uniform(s, i + l, j, this);
        if (i <= 3) {
            uniform.setSafe((int)afloat[0], (int)afloat[1], (int)afloat[2], (int)afloat[3]);
        } else if (i <= 7) {
            uniform.setSafe(afloat[0], afloat[1], afloat[2], afloat[3]);
        } else {
            uniform.set(afloat);
        }

        this.uniforms.add(uniform);
    }

    public Program getVertexProgram() {
        return this.vertexProgram;
    }

    public Program getFragmentProgram() {
        return this.fragmentProgram;
    }

    public int getId() {
        return this.programId;
    }
}
