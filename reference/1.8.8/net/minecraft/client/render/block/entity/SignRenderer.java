package net.minecraft.client.render.block.entity;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.render.TextRenderUtils;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.model.block.entity.SignModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Text;
import org.lwjgl.opengl.GL11;

public class SignRenderer extends BlockEntityRenderer<SignBlockEntity> {
    private static final Identifier BACKGROUND_LOCATION = new Identifier("textures/entity/sign.png");
    private final SignModel model = new SignModel();

    public void render(SignBlockEntity signBlockEntity, double d, double e, double f, float g, int i) {
        Block block = signBlockEntity.getBlock();
        GlStateManager.pushMatrix();
        float fx = 0.6666667F;
        if (block == Blocks.STANDING_SIGN) {
            GlStateManager.translatef((float)d + 0.5F, (float)e + 0.75F * fx, (float)f + 0.5F);
            float f1 = signBlockEntity.getBlockMetadata() * 360 / 16.0F;
            GlStateManager.rotatef(-f1, 0.0F, 1.0F, 0.0F);
            this.model.pole.visible = true;
        } else {
            int k = signBlockEntity.getBlockMetadata();
            float f2 = 0.0F;
            if (k == 2) {
                f2 = 180.0F;
            }

            if (k == 4) {
                f2 = 90.0F;
            }

            if (k == 5) {
                f2 = -90.0F;
            }

            GlStateManager.translatef((float)d + 0.5F, (float)e + 0.75F * fx, (float)f + 0.5F);
            GlStateManager.rotatef(-f2, 0.0F, 1.0F, 0.0F);
            GlStateManager.translatef(0.0F, -0.3125F, -0.4375F);
            this.model.pole.visible = false;
        }

        if (i >= 0) {
            this.bindTexture(MINING_PROGRESS_LOCATIONS[i]);
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            GlStateManager.scalef(4.0F, 2.0F, 1.0F);
            GlStateManager.translatef(0.0625F, 0.0625F, 0.0625F);
            GlStateManager.matrixMode(5888);
        } else {
            this.bindTexture(BACKGROUND_LOCATION);
        }

        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        GlStateManager.scalef(fx, -fx, -fx);
        this.model.render();
        GlStateManager.popMatrix();
        TextRenderer textrenderer = this.getTextRenderer();
        float f3 = 0.015625F * fx;
        GlStateManager.translatef(0.0F, 0.5F * fx, 0.07F * fx);
        GlStateManager.scalef(f3, -f3, f3);
        GL11.glNormal3f(0.0F, 0.0F, -1.0F * f3);
        GlStateManager.depthMask(false);
        int ix = 0;
        if (i < 0) {
            for (int j = 0; j < signBlockEntity.lines.length; j++) {
                if (signBlockEntity.lines[j] != null) {
                    Text text = signBlockEntity.lines[j];
                    List<Text> list = TextRenderUtils.wrapText(text, 90, textrenderer, false, true);
                    String s = list != null && list.size() > 0 ? list.get(0).getFormattedString() : "";
                    if (j == signBlockEntity.currentRow) {
                        s = "> " + s + " <";
                        textrenderer.draw(s, -textrenderer.getWidth(s) / 2, j * 10 - signBlockEntity.lines.length * 5, ix);
                    } else {
                        textrenderer.draw(s, -textrenderer.getWidth(s) / 2, j * 10 - signBlockEntity.lines.length * 5, ix);
                    }
                }
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        if (i >= 0) {
            GlStateManager.matrixMode(5890);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
        }
    }
}
