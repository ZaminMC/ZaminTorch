package net.minecraft.client.resource.skin;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.HttpImageProcessor;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.client.render.texture.SkinImageProcessor;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;

public class SkinManager {
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(0, 2, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
    private final TextureManager textureManager;
    private final File skinsDir;
    private final MinecraftSessionService sessionService;
    private final LoadingCache<GameProfile, Map<Type, MinecraftProfileTexture>> skinCache;

    public SkinManager(TextureManager textureManager, File skinsDir, MinecraftSessionService sessionService) {
        this.textureManager = textureManager;
        this.skinsDir = skinsDir;
        this.sessionService = sessionService;
        this.skinCache = CacheBuilder.newBuilder()
            .expireAfterAccess(15L, TimeUnit.SECONDS)
            .build(new CacheLoader<GameProfile, Map<Type, MinecraftProfileTexture>>() {
                public Map<Type, MinecraftProfileTexture> load(GameProfile gameProfile) throws Exception {
                    return Minecraft.getInstance().getSessionService().getTextures(gameProfile, false);
                }
            });
    }

    public Identifier register(MinecraftProfileTexture texture, Type type) {
        return this.register(texture, type, null);
    }

    public Identifier register(MinecraftProfileTexture texture, Type type, SkinManager.SkinTextureCallback callback) {
        final Identifier identifier = new Identifier("skins/" + texture.getHash());
        Texture texturex = this.textureManager.get(identifier);
        if (texturex != null) {
            if (callback != null) {
                callback.textureAvailable(type, identifier, texture);
            }
        } else {
            File file1 = new File(this.skinsDir, texture.getHash().length() > 2 ? texture.getHash().substring(0, 2) : "xx");
            File file2 = new File(file1, texture.getHash());
            final HttpImageProcessor httpimageprocessor = type == Type.SKIN ? new SkinImageProcessor() : null;
            HttpTexture httptexture = new HttpTexture(file2, texture.getUrl(), DefaultSkinUtils.getDefaultSkin(), new HttpImageProcessor() {
                @Override
                public BufferedImage process(BufferedImage image) {
                    if (httpimageprocessor != null) {
                        image = httpimageprocessor.process(image);
                    }

                    return image;
                }

                @Override
                public void onTextureDownloaded() {
                    if (httpimageprocessor != null) {
                        httpimageprocessor.onTextureDownloaded();
                    }

                    if (callback != null) {
                        callback.textureAvailable(type, identifier, texture);
                    }
                }
            });
            this.textureManager.register(identifier, httptexture);
        }

        return identifier;
    }

    public void register(GameProfile profile, SkinManager.SkinTextureCallback callback, boolean requireSecure) {
        EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                final Map<Type, MinecraftProfileTexture> map = Maps.newHashMap();

                try {
                    map.putAll(SkinManager.this.sessionService.getTextures(profile, requireSecure));
                } catch (InsecureTextureException insecuretextureexception) {
                }

                if (map.isEmpty() && profile.getId().equals(Minecraft.getInstance().getSession().getProfile().getId())) {
                    profile.getProperties().clear();
                    profile.getProperties().putAll(Minecraft.getInstance().getProfileProperties());
                    map.putAll(SkinManager.this.sessionService.getTextures(profile, false));
                }

                Minecraft.getInstance().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (map.containsKey(Type.SKIN)) {
                            SkinManager.this.register(map.get(Type.SKIN), Type.SKIN, callback);
                        }

                        if (map.containsKey(Type.CAPE)) {
                            SkinManager.this.register(map.get(Type.CAPE), Type.CAPE, callback);
                        }
                    }
                });
            }
        });
    }

    public Map<Type, MinecraftProfileTexture> getTextures(GameProfile profile) {
        return this.skinCache.getUnchecked(profile);
    }

    public interface SkinTextureCallback {
        void textureAvailable(Type type, Identifier location, MinecraftProfileTexture texture);
    }
}
