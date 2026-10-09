package net.minecraft.client.render.block;

import net.minecraft.block.Block;
import net.minecraft.client.render.entity.BlockEntityItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.item.ItemStack;

public class AnimatedBlockEntityRenderer {
    public void render(Block block, float brightness) {
        GlStateManager.color4f(brightness, brightness, brightness, 1.0F);
        GlStateManager.rotatef(90.0F, 0.0F, 1.0F, 0.0F);
        BlockEntityItemRenderer.INSTANCE.render(new ItemStack(block));
    }
}
