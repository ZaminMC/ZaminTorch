package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;

public class ItemSpriteRenderer<T extends Entity> extends EntityRenderer<T> {
    protected final Item item;
    private final ItemRenderer itemRenderer;

    public ItemSpriteRenderer(EntityRenderDispatcher dispatcher, Item item, ItemRenderer itemRenderer) {
        super(dispatcher);
        this.item = item;
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(T entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)dx, (float)dy, (float)dz);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scalef(0.5F, 0.5F, 0.5F);
        GlStateManager.rotatef(-this.dispatcher.cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(this.dispatcher.cameraPitch, 1.0F, 0.0F, 0.0F);
        this.bindTexture(TextureAtlas.BLOCKS_LOCATION);
        this.itemRenderer.renderItemInHand(this.asItem(entity), ModelTransformations.Type.GROUND);
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        super.render(entity, dx, dy, dz, yaw, tickDelta);
    }

    public ItemStack asItem(T potion) {
        return new ItemStack(this.item, 1, 0);
    }

    @Override
    protected Identifier getTextureLocation(Entity entity) {
        return TextureAtlas.BLOCKS_LOCATION;
    }
}
