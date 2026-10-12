package net.minecraft.client.render.block.entity;

import net.minecraft.block.entity.EnchantingTableBlockEntity;
import net.minecraft.client.render.model.block.entity.EnchantingTableBookModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class EnchantingTableRenderer extends BlockEntityRenderer<EnchantingTableBlockEntity> {
    private static final Identifier BOOK_LOCATION = new Identifier("textures/entity/enchanting_table_book.png");
    private EnchantingTableBookModel bookModel = new EnchantingTableBookModel();

    public void render(EnchantingTableBlockEntity enchantingTableBlockEntity, double d, double e, double f, float g, int i) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)d + 0.5F, (float)e + 0.75F, (float)f + 0.5F);
        float fx = enchantingTableBlockEntity.ticks + g;
        GlStateManager.translatef(0.0F, 0.1F + MathHelper.sin(fx * 0.1F) * 0.01F, 0.0F);
        float f1 = enchantingTableBlockEntity.pageRotation - enchantingTableBlockEntity.lastPageRotation;

        while (f1 >= (float) Math.PI) {
            f1 -= (float) (Math.PI * 2);
        }

        while (f1 < (float) -Math.PI) {
            f1 += (float) (Math.PI * 2);
        }

        float f2 = enchantingTableBlockEntity.lastPageRotation + f1 * g;
        GlStateManager.rotatef(-f2 * 180.0F / (float) Math.PI, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(80.0F, 0.0F, 0.0F, 1.0F);
        this.bindTexture(BOOK_LOCATION);
        float f3 = enchantingTableBlockEntity.lastPageAngle + (enchantingTableBlockEntity.pageAngle - enchantingTableBlockEntity.lastPageAngle) * g + 0.25F;
        float f4 = enchantingTableBlockEntity.lastPageAngle + (enchantingTableBlockEntity.pageAngle - enchantingTableBlockEntity.lastPageAngle) * g + 0.75F;
        f3 = (f3 - MathHelper.fastFloor(f3)) * 1.6F - 0.3F;
        f4 = (f4 - MathHelper.fastFloor(f4)) * 1.6F - 0.3F;
        if (f3 < 0.0F) {
            f3 = 0.0F;
        }

        if (f4 < 0.0F) {
            f4 = 0.0F;
        }

        if (f3 > 1.0F) {
            f3 = 1.0F;
        }

        if (f4 > 1.0F) {
            f4 = 1.0F;
        }

        float f5 = enchantingTableBlockEntity.lastPageTurningSpeed
            + (enchantingTableBlockEntity.pageTurningSpeed - enchantingTableBlockEntity.lastPageTurningSpeed) * g;
        GlStateManager.enableCull();
        this.bookModel.render(null, fx, f3, f4, f5, 0.0F, 0.0625F);
        GlStateManager.popMatrix();
    }
}
