package net.minecraft.client.resource.model;

import java.util.Arrays;
import net.minecraft.client.render.model.block.FaceBakery;
import net.minecraft.client.render.texture.TextureAtlasSprite;

public class MiningQuad extends BakedQuad {
    private final TextureAtlasSprite miningSprite;

    public MiningQuad(BakedQuad delegate, TextureAtlasSprite miningSprite) {
        super(Arrays.copyOf(delegate.getVertices(), delegate.getVertices().length), delegate.tintIndex, FaceBakery.getFacing(delegate.getVertices()));
        this.miningSprite = miningSprite;
        this.calculateTextureCoords();
    }

    private void calculateTextureCoords() {
        for (int i = 0; i < 4; i++) {
            this.calculateTextureCoords(i);
        }
    }

    private void calculateTextureCoords(int vertex) {
        int i = 7 * vertex;
        float f = Float.intBitsToFloat(this.vertices[i]);
        float f1 = Float.intBitsToFloat(this.vertices[i + 1]);
        float f2 = Float.intBitsToFloat(this.vertices[i + 2]);
        float f3 = 0.0F;
        float f4 = 0.0F;
        switch (this.face) {
            case DOWN:
                f3 = f * 16.0F;
                f4 = (1.0F - f2) * 16.0F;
                break;
            case UP:
                f3 = f * 16.0F;
                f4 = f2 * 16.0F;
                break;
            case NORTH:
                f3 = (1.0F - f) * 16.0F;
                f4 = (1.0F - f1) * 16.0F;
                break;
            case SOUTH:
                f3 = f * 16.0F;
                f4 = (1.0F - f1) * 16.0F;
                break;
            case WEST:
                f3 = f2 * 16.0F;
                f4 = (1.0F - f1) * 16.0F;
                break;
            case EAST:
                f3 = (1.0F - f2) * 16.0F;
                f4 = (1.0F - f1) * 16.0F;
        }

        this.vertices[i + 4] = Float.floatToRawIntBits(this.miningSprite.getU(f3));
        this.vertices[i + 4 + 1] = Float.floatToRawIntBits(this.miningSprite.getV(f4));
    }
}
