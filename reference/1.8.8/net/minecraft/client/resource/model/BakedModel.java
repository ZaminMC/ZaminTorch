package net.minecraft.client.resource.model;

import java.util.List;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.util.math.Direction;

public interface BakedModel {
    List<BakedQuad> getQuads(Direction face);

    List<BakedQuad> getQuads();

    boolean useAmbientOcclusion();

    boolean isGui3d();

    boolean isCustomRenderer();

    TextureAtlasSprite getParticleIcon();

    ModelTransformations getTransformations();
}
