package net.minecraft.client.resource.model;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.util.WeightedPicker;
import net.minecraft.util.math.Direction;

public class WeightedBakedModel implements BakedModel {
    private final int totalWeight;
    private final List<WeightedBakedModel.ModelEntry> entries;
    private final BakedModel defaultModel;

    public WeightedBakedModel(List<WeightedBakedModel.ModelEntry> entries) {
        this.entries = entries;
        this.totalWeight = WeightedPicker.getTotalWeight(entries);
        this.defaultModel = entries.get(0).model;
    }

    @Override
    public List<BakedQuad> getQuads(Direction face) {
        return this.defaultModel.getQuads(face);
    }

    @Override
    public List<BakedQuad> getQuads() {
        return this.defaultModel.getQuads();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.defaultModel.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.defaultModel.isGui3d();
    }

    @Override
    public boolean isCustomRenderer() {
        return this.defaultModel.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.defaultModel.getParticleIcon();
    }

    @Override
    public ModelTransformations getTransformations() {
        return this.defaultModel.getTransformations();
    }

    public BakedModel pick(long weight) {
        return WeightedPicker.pick(this.entries, Math.abs((int)weight >> 16) % this.totalWeight).model;
    }

    public static class Builder {
        private List<WeightedBakedModel.ModelEntry> entries = Lists.newArrayList();

        public WeightedBakedModel.Builder add(BakedModel model, int weight) {
            this.entries.add(new WeightedBakedModel.ModelEntry(model, weight));
            return this;
        }

        public WeightedBakedModel build() {
            Collections.sort(this.entries);
            return new WeightedBakedModel(this.entries);
        }

        public BakedModel first() {
            return this.entries.get(0).model;
        }
    }

    static class ModelEntry extends WeightedPicker.Entry implements Comparable<WeightedBakedModel.ModelEntry> {
        protected final BakedModel model;

        public ModelEntry(BakedModel model, int weight) {
            super(weight);
            this.model = model;
        }

        public int compareTo(WeightedBakedModel.ModelEntry modelEntry) {
            return ComparisonChain.start().compare(modelEntry.weight, this.weight).compare(this.size(), modelEntry.size()).result();
        }

        protected int size() {
            int i = this.model.getQuads().size();

            for (Direction direction : Direction.values()) {
                i += this.model.getQuads(direction).size();
            }

            return i;
        }

        @Override
        public String toString() {
            return "MyWeighedRandomItem{weight=" + this.weight + ", model=" + this.model + '}';
        }
    }
}
