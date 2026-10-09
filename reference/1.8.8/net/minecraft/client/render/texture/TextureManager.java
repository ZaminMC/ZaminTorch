package net.minecraft.client.render.texture;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import net.minecraft.client.render.Tickable;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.resource.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TextureManager implements Tickable, ResourceReloadListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Map<Identifier, Texture> textures = Maps.newHashMap();
    private final List<Tickable> tickableTextures = Lists.newArrayList();
    private final Map<String, Integer> dynamicIdCounter = Maps.newHashMap();
    private ResourceManager resourceManager;

    public TextureManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void bind(Identifier location) {
        Texture texture = this.textures.get(location);
        if (texture == null) {
            texture = new SimpleTexture(location);
            this.register(location, texture);
        }

        TextureUtil.bind(texture.getGlId());
    }

    public boolean register(Identifier location, TickableTexture tickableTexture) {
        if (this.register(location, (Texture)tickableTexture)) {
            this.tickableTextures.add(tickableTexture);
            return true;
        } else {
            return false;
        }
    }

    public boolean register(Identifier location, Texture texture) {
        boolean flag = true;

        try {
            texture.load(this.resourceManager);
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load texture: " + location, ioexception);
            texture = TextureUtil.MISSING_TEXTURE;
            this.textures.put(location, texture);
            flag = false;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Registering texture");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Resource location being registered");
            final Texture texturex = texture;
            crashreportcategory.add("Resource location", location);
            crashreportcategory.add("Texture object class", new Callable<String>() {
                public String call() throws Exception {
                    return texture.getClass().getName();
                }
            });
            throw new CrashException(crashreport);
        }

        this.textures.put(location, texture);
        return flag;
    }

    public Texture get(Identifier id) {
        return this.textures.get(id);
    }

    public Identifier register(String path, DynamicTexture dynamicTexture) {
        Integer integer = this.dynamicIdCounter.get(path);
        if (integer == null) {
            integer = 1;
        } else {
            integer = integer + 1;
        }

        this.dynamicIdCounter.put(path, integer);
        Identifier identifier = new Identifier(String.format("dynamic/%s_%d", path, integer));
        this.register(identifier, dynamicTexture);
        return identifier;
    }

    @Override
    public void tick() {
        for (Tickable tickable : this.tickableTextures) {
            tickable.tick();
        }
    }

    public void close(Identifier id) {
        Texture texture = this.get(id);
        if (texture != null) {
            TextureUtil.deleteTextures(texture.getGlId());
        }
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        for (Entry<Identifier, Texture> entry : this.textures.entrySet()) {
            this.register(entry.getKey(), entry.getValue());
        }
    }
}
