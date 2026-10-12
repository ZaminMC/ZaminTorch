package net.minecraft.client.render.item;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.ItemModelProvider;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemModelShaper {
    private final Map<Integer, ModelIdentifier> models = Maps.newHashMap();
    private final Map<Integer, BakedModel> modelCache = Maps.newHashMap();
    private final Map<Item, ItemModelProvider> modelProviders = Maps.newHashMap();
    private final ModelManager manager;

    public ItemModelShaper(ModelManager manager) {
        this.manager = manager;
    }

    public TextureAtlasSprite getParticleIcon(Item item) {
        return this.getParticleIcon(item, 0);
    }

    public TextureAtlasSprite getParticleIcon(Item item, int metadata) {
        return this.getModel(new ItemStack(item, 1, metadata)).getParticleIcon();
    }

    public BakedModel getModel(ItemStack item) {
        Item itemx = item.getItem();
        BakedModel bakedmodel = this.getModel(itemx, this.getModelMetadata(item));
        if (bakedmodel == null) {
            ItemModelProvider itemmodelprovider = this.modelProviders.get(itemx);
            if (itemmodelprovider != null) {
                bakedmodel = this.manager.getModel(itemmodelprovider.provide(item));
            }
        }

        if (bakedmodel == null) {
            bakedmodel = this.manager.getMissingModel();
        }

        return bakedmodel;
    }

    protected int getModelMetadata(ItemStack item) {
        return item.isDamageable() ? 0 : item.getMetadata();
    }

    protected BakedModel getModel(Item item, int metadata) {
        return this.modelCache.get(this.index(item, metadata));
    }

    private int index(Item item, int metadata) {
        return Item.getId(item) << 16 | metadata;
    }

    public void register(Item item, int metadata, ModelIdentifier location) {
        this.models.put(this.index(item, metadata), location);
        this.modelCache.put(this.index(item, metadata), this.manager.getModel(location));
    }

    public void register(Item item, ItemModelProvider provider) {
        this.modelProviders.put(item, provider);
    }

    public ModelManager getManager() {
        return this.manager;
    }

    public void rebuildCache() {
        this.modelCache.clear();

        for (Entry<Integer, ModelIdentifier> entry : this.models.entrySet()) {
            this.modelCache.put(entry.getKey(), this.manager.getModel(entry.getValue()));
        }
    }
}
