package net.minecraft.client.resource.model;

import net.minecraft.client.render.block.BlockModelShaper;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.util.registry.Registry;

public class ModelManager implements ResourceReloadListener {
    private Registry<ModelIdentifier, BakedModel> registry;
    private final TextureAtlas blocksAtlas;
    private final BlockModelShaper modelShaper;
    private BakedModel missing;

    public ModelManager(TextureAtlas blocksAtlas) {
        this.blocksAtlas = blocksAtlas;
        this.modelShaper = new BlockModelShaper(this);
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        ModelBakery modelbakery = new ModelBakery(resourceManager, this.blocksAtlas, this.modelShaper);
        this.registry = modelbakery.getBakedModels();
        this.missing = this.registry.get(ModelBakery.MISSING);
        this.modelShaper.rebuildCache();
    }

    public BakedModel getModel(ModelIdentifier id) {
        if (id == null) {
            return this.missing;
        }

        BakedModel bakedmodel = this.registry.get(id);
        return bakedmodel == null ? this.missing : bakedmodel;
    }

    public BakedModel getMissingModel() {
        return this.missing;
    }

    public TextureAtlas getBlocksAtlas() {
        return this.blocksAtlas;
    }

    public BlockModelShaper getModelShaper() {
        return this.modelShaper;
    }
}
