package net.minecraft.client.resource.model;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.render.model.block.BlockModel;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.util.math.Direction;

public class BasicBakedModel implements BakedModel {
    protected final List<BakedQuad> unculledFaces;
    protected final List<List<BakedQuad>> culledFaces;
    protected final boolean ambientOcclusion;
    protected final boolean gui3d;
    protected final TextureAtlasSprite particleIcon;
    protected final ModelTransformations transforms;

    public BasicBakedModel(
        List<BakedQuad> unculledFaces,
        List<List<BakedQuad>> culledFaces,
        boolean ambientOcclusion,
        boolean gui3d,
        TextureAtlasSprite particleIcon,
        ModelTransformations transforms
    ) {
        this.unculledFaces = unculledFaces;
        this.culledFaces = culledFaces;
        this.ambientOcclusion = ambientOcclusion;
        this.gui3d = gui3d;
        this.particleIcon = particleIcon;
        this.transforms = transforms;
    }

    @Override
    public List<BakedQuad> getQuads(Direction face) {
        return this.culledFaces.get(face.ordinal());
    }

    @Override
    public List<BakedQuad> getQuads() {
        return this.unculledFaces;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.ambientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return this.gui3d;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.particleIcon;
    }

    @Override
    public ModelTransformations getTransformations() {
        return this.transforms;
    }

    public static class Builder {
        private final List<BakedQuad> unculledFaces = Lists.newArrayList();
        private final List<List<BakedQuad>> culledFaces = Lists.newArrayListWithCapacity(6);
        private final boolean ambientOcclusion;
        private TextureAtlasSprite particleIcon;
        private boolean gui3d;
        private ModelTransformations transforms;

        public Builder(BlockModel model) {
            this(model.usesAmbientOcclusion(), model.isGui3d(), model.getTransformations());
        }

        public Builder(BakedModel model, TextureAtlasSprite miningSprite) {
            this(model.useAmbientOcclusion(), model.isGui3d(), model.getTransformations());
            this.particleIcon = model.getParticleIcon();

            for (Direction direction : Direction.values()) {
                this.culledMiningFaces(model, miningSprite, direction);
            }

            this.unculledMiningFaces(model, miningSprite);
        }

        private void culledMiningFaces(BakedModel model, TextureAtlasSprite miningSprite, Direction face) {
            for (BakedQuad bakedquad : model.getQuads(face)) {
                this.culledFace(face, new MiningQuad(bakedquad, miningSprite));
            }
        }

        private void unculledMiningFaces(BakedModel model, TextureAtlasSprite miningSprite) {
            for (BakedQuad bakedquad : model.getQuads()) {
                this.unculledFace(new MiningQuad(bakedquad, miningSprite));
            }
        }

        private Builder(boolean ambientOcclusion, boolean gui3d, ModelTransformations transforms) {
            for (Direction direction : Direction.values()) {
                this.culledFaces.add(Lists.newArrayList());
            }

            this.ambientOcclusion = ambientOcclusion;
            this.gui3d = gui3d;
            this.transforms = transforms;
        }

        public BasicBakedModel.Builder culledFace(Direction face, BakedQuad quad) {
            this.culledFaces.get(face.ordinal()).add(quad);
            return this;
        }

        public BasicBakedModel.Builder unculledFace(BakedQuad quad) {
            this.unculledFaces.add(quad);
            return this;
        }

        public BasicBakedModel.Builder particleIcon(TextureAtlasSprite particleIcon) {
            this.particleIcon = particleIcon;
            return this;
        }

        public BakedModel build() {
            if (this.particleIcon == null) {
                throw new RuntimeException("Missing particle!");
            } else {
                return new BasicBakedModel(this.unculledFaces, this.culledFaces, this.ambientOcclusion, this.gui3d, this.particleIcon, this.transforms);
            }
        }
    }
}
