package net.minecraft.client.render;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.shaders.Uniform;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.util.JsonUtils;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

public class PostChain {
    private RenderTarget screenTarget;
    private ResourceManager resourceManager;
    private String name;
    private final List<PostPass> passes = Lists.newArrayList();
    private final Map<String, RenderTarget> customRenderTargets = Maps.newHashMap();
    private final List<RenderTarget> fullSizedTargets = Lists.newArrayList();
    private Matrix4f shaderOrthoMatrix;
    private int screenWidth;
    private int screenHeight;
    private float time;
    private float lastStamp;

    public PostChain(TextureManager textureManager, ResourceManager resourceManager, RenderTarget screenTarget, Identifier id) throws IOException, JsonSyntaxException {
        this.resourceManager = resourceManager;
        this.screenTarget = screenTarget;
        this.time = 0.0F;
        this.lastStamp = 0.0F;
        this.screenWidth = screenTarget.viewWidth;
        this.screenHeight = screenTarget.viewHeight;
        this.name = id.toString();
        this.updateOrthoMatrix();
        this.load(textureManager, id);
    }

    public void load(TextureManager textureManager, Identifier id) throws IOException, JsonSyntaxException {
        JsonParser jsonparser = new JsonParser();
        InputStream inputstream = null;

        try {
            Resource resource = this.resourceManager.getResource(id);
            inputstream = resource.asStream();
            JsonObject jsonobject = jsonparser.parse(IOUtils.toString(inputstream, Charsets.UTF_8)).getAsJsonObject();
            if (JsonUtils.hasJsonArray(jsonobject, "targets")) {
                JsonArray jsonarray = jsonobject.getAsJsonArray("targets");
                int i = 0;

                for (JsonElement jsonelement : jsonarray) {
                    try {
                        this.parseTargetNode(jsonelement);
                    } catch (Exception exception1) {
                        ChainedJsonException chainedjsonexception1 = ChainedJsonException.forException(exception1);
                        chainedjsonexception1.prependJsonKey("targets[" + i + "]");
                        throw chainedjsonexception1;
                    }

                    i++;
                }
            }

            if (JsonUtils.hasJsonArray(jsonobject, "passes")) {
                JsonArray jsonarray1 = jsonobject.getAsJsonArray("passes");
                int j = 0;

                for (JsonElement jsonelement1 : jsonarray1) {
                    try {
                        this.parsePassNode(textureManager, jsonelement1);
                    } catch (Exception exception) {
                        ChainedJsonException chainedjsonexception2 = ChainedJsonException.forException(exception);
                        chainedjsonexception2.prependJsonKey("passes[" + j + "]");
                        throw chainedjsonexception2;
                    }

                    j++;
                }
            }
        } catch (Exception exception2) {
            ChainedJsonException chainedjsonexception = ChainedJsonException.forException(exception2);
            chainedjsonexception.setFileNameAndFlush(id.getPath());
            throw chainedjsonexception;
        } finally {
            IOUtils.closeQuietly(inputstream);
        }
    }

    private void parseTargetNode(JsonElement json) throws ChainedJsonException {
        if (JsonUtils.isString(json)) {
            this.addTempTarget(json.getAsString(), this.screenWidth, this.screenHeight);
        } else {
            JsonObject jsonobject = JsonUtils.asJsonObject(json, "target");
            String s = JsonUtils.getString(jsonobject, "name");
            int i = JsonUtils.getIntegerOrDefault(jsonobject, "width", this.screenWidth);
            int j = JsonUtils.getIntegerOrDefault(jsonobject, "height", this.screenHeight);
            if (this.customRenderTargets.containsKey(s)) {
                throw new ChainedJsonException(s + " is already defined");
            }

            this.addTempTarget(s, i, j);
        }
    }

    private void parsePassNode(TextureManager textureManager, JsonElement json) throws IOException {
        JsonObject jsonobject = JsonUtils.asJsonObject(json, "pass");
        String s = JsonUtils.getString(jsonobject, "name");
        String s1 = JsonUtils.getString(jsonobject, "intarget");
        String s2 = JsonUtils.getString(jsonobject, "outtarget");
        RenderTarget rendertarget = this.getRenderTarget(s1);
        RenderTarget rendertarget1 = this.getRenderTarget(s2);
        if (rendertarget == null) {
            throw new ChainedJsonException("Input target '" + s1 + "' does not exist");
        }

        if (rendertarget1 == null) {
            throw new ChainedJsonException("Output target '" + s2 + "' does not exist");
        }

        PostPass postpass = this.addPass(s, rendertarget, rendertarget1);
        JsonArray jsonarray = JsonUtils.getJsonArrayOrDefault(jsonobject, "auxtargets", null);
        if (jsonarray != null) {
            int i = 0;

            for (JsonElement jsonelement : jsonarray) {
                try {
                    JsonObject jsonobject1 = JsonUtils.asJsonObject(jsonelement, "auxtarget");
                    String s4 = JsonUtils.getString(jsonobject1, "name");
                    String s3 = JsonUtils.getString(jsonobject1, "id");
                    RenderTarget rendertarget2 = this.getRenderTarget(s3);
                    if (rendertarget2 == null) {
                        Identifier identifier = new Identifier("textures/effect/" + s3 + ".png");

                        try {
                            this.resourceManager.getResource(identifier);
                        } catch (FileNotFoundException filenotfoundexception) {
                            throw new ChainedJsonException("Render target or texture '" + s3 + "' does not exist");
                        }

                        textureManager.bind(identifier);
                        Texture texture = textureManager.get(identifier);
                        int j = JsonUtils.getInteger(jsonobject1, "width");
                        int k = JsonUtils.getInteger(jsonobject1, "height");
                        boolean flag = JsonUtils.getBoolean(jsonobject1, "bilinear");
                        if (flag) {
                            GL11.glTexParameteri(3553, 10241, 9729);
                            GL11.glTexParameteri(3553, 10240, 9729);
                        } else {
                            GL11.glTexParameteri(3553, 10241, 9728);
                            GL11.glTexParameteri(3553, 10240, 9728);
                        }

                        postpass.addAuxAsset(s4, texture.getGlId(), j, k);
                    } else {
                        postpass.addAuxAsset(s4, rendertarget2, rendertarget2.width, rendertarget2.height);
                    }
                } catch (Exception exception1) {
                    ChainedJsonException chainedjsonexception = ChainedJsonException.forException(exception1);
                    chainedjsonexception.prependJsonKey("auxtargets[" + i + "]");
                    throw chainedjsonexception;
                }

                i++;
            }
        }

        JsonArray jsonarray1 = JsonUtils.getJsonArrayOrDefault(jsonobject, "uniforms", null);
        if (jsonarray1 != null) {
            int l = 0;

            for (JsonElement jsonelement1 : jsonarray1) {
                try {
                    this.parseUniformNode(jsonelement1);
                } catch (Exception exception) {
                    ChainedJsonException chainedjsonexception1 = ChainedJsonException.forException(exception);
                    chainedjsonexception1.prependJsonKey("uniforms[" + l + "]");
                    throw chainedjsonexception1;
                }

                l++;
            }
        }
    }

    private void parseUniformNode(JsonElement json) throws ChainedJsonException {
        JsonObject jsonobject = JsonUtils.asJsonObject(json, "uniform");
        String s = JsonUtils.getString(jsonobject, "name");
        Uniform uniform = this.passes.get(this.passes.size() - 1).getEffect().getUniform(s);
        if (uniform == null) {
            throw new ChainedJsonException("Uniform '" + s + "' does not exist");
        }

        float[] afloat = new float[4];
        int i = 0;

        for (JsonElement jsonelement : JsonUtils.getJsonArray(jsonobject, "values")) {
            try {
                afloat[i] = JsonUtils.asFloat(jsonelement, "value");
            } catch (Exception exception) {
                ChainedJsonException chainedjsonexception = ChainedJsonException.forException(exception);
                chainedjsonexception.prependJsonKey("values[" + i + "]");
                throw chainedjsonexception;
            }

            i++;
        }

        switch (i) {
            case 0:
            default:
                break;
            case 1:
                uniform.set(afloat[0]);
                break;
            case 2:
                uniform.set(afloat[0], afloat[1]);
                break;
            case 3:
                uniform.set(afloat[0], afloat[1], afloat[2]);
                break;
            case 4:
                uniform.set(afloat[0], afloat[1], afloat[2], afloat[3]);
        }
    }

    public RenderTarget getTempTarget(String name) {
        return this.customRenderTargets.get(name);
    }

    public void addTempTarget(String name, int width, int height) {
        RenderTarget rendertarget = new RenderTarget(width, height, true);
        rendertarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.customRenderTargets.put(name, rendertarget);
        if (width == this.screenWidth && height == this.screenHeight) {
            this.fullSizedTargets.add(rendertarget);
        }
    }

    public void close() {
        for (RenderTarget rendertarget : this.customRenderTargets.values()) {
            rendertarget.destroyBuffers();
        }

        for (PostPass postpass : this.passes) {
            postpass.close();
        }

        this.passes.clear();
    }

    public PostPass addPass(String name, RenderTarget inTarget, RenderTarget outTarget) throws IOException {
        PostPass postpass = new PostPass(this.resourceManager, name, inTarget, outTarget);
        this.passes.add(this.passes.size(), postpass);
        return postpass;
    }

    private void updateOrthoMatrix() {
        this.shaderOrthoMatrix = new Matrix4f();
        this.shaderOrthoMatrix.setIdentity();
        this.shaderOrthoMatrix.m00 = 2.0F / this.screenTarget.width;
        this.shaderOrthoMatrix.m11 = 2.0F / -this.screenTarget.height;
        this.shaderOrthoMatrix.m22 = -0.0020001999F;
        this.shaderOrthoMatrix.m33 = 1.0F;
        this.shaderOrthoMatrix.m03 = -1.0F;
        this.shaderOrthoMatrix.m13 = 1.0F;
        this.shaderOrthoMatrix.m23 = -1.0001999F;
    }

    public void resize(int width, int height) {
        this.screenWidth = this.screenTarget.width;
        this.screenHeight = this.screenTarget.height;
        this.updateOrthoMatrix();

        for (PostPass postpass : this.passes) {
            postpass.setOrthoMatrix(this.shaderOrthoMatrix);
        }

        for (RenderTarget rendertarget : this.fullSizedTargets) {
            rendertarget.resize(width, height);
        }
    }

    public void process(float tickDelta) {
        if (tickDelta < this.lastStamp) {
            this.time = this.time + (1.0F - this.lastStamp);
            this.time += tickDelta;
        } else {
            this.time = this.time + (tickDelta - this.lastStamp);
        }

        this.lastStamp = tickDelta;

        while (this.time > 20.0F) {
            this.time -= 20.0F;
        }

        for (PostPass postpass : this.passes) {
            postpass.process(this.time / 20.0F);
        }
    }

    public final String getName() {
        return this.name;
    }

    private RenderTarget getRenderTarget(String name) {
        if (name == null) {
            return null;
        } else {
            return name.equals("minecraft:main") ? this.screenTarget : this.customRenderTargets.get(name);
        }
    }
}
