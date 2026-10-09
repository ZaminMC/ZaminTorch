package net.minecraft.client.render.block.entity;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import net.minecraft.world.World;

public abstract class BlockEntityRenderer<T extends BlockEntity> {
    protected static final Identifier[] MINING_PROGRESS_LOCATIONS = new Identifier[]{
        new Identifier("textures/blocks/destroy_stage_0.png"),
        new Identifier("textures/blocks/destroy_stage_1.png"),
        new Identifier("textures/blocks/destroy_stage_2.png"),
        new Identifier("textures/blocks/destroy_stage_3.png"),
        new Identifier("textures/blocks/destroy_stage_4.png"),
        new Identifier("textures/blocks/destroy_stage_5.png"),
        new Identifier("textures/blocks/destroy_stage_6.png"),
        new Identifier("textures/blocks/destroy_stage_7.png"),
        new Identifier("textures/blocks/destroy_stage_8.png"),
        new Identifier("textures/blocks/destroy_stage_9.png")
    };
    protected BlockEntityRenderDispatcher dispatcher;

    public abstract void render(T blockEntity, double dx, double dy, double dz, float tickDelta, int blockMiningProgress);

    protected void bindTexture(Identifier location) {
        TextureManager texturemanager = this.dispatcher.textureManager;
        if (texturemanager != null) {
            texturemanager.bind(location);
        }
    }

    protected World getWorld() {
        return this.dispatcher.world;
    }

    public void init(BlockEntityRenderDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public TextRenderer getTextRenderer() {
        return this.dispatcher.getTextRenderer();
    }

    public boolean shouldRenderOffScreen() {
        return false;
    }
}
