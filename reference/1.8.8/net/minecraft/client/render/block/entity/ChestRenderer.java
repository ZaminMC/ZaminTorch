package net.minecraft.client.render.block.entity;

import java.util.Calendar;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.render.model.block.entity.ChestModel;
import net.minecraft.client.render.model.block.entity.LargeChestModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;

public class ChestRenderer extends BlockEntityRenderer<ChestBlockEntity> {
    private static final Identifier TRAPPED_DOUBLE_CHEST_LOCATION = new Identifier("textures/entity/chest/trapped_double.png");
    private static final Identifier CHRISTMAS_DOUBLE_CHEST_LOCATION = new Identifier("textures/entity/chest/christmas_double.png");
    private static final Identifier DOUBLE_CHEST_LOCATION = new Identifier("textures/entity/chest/normal_double.png");
    private static final Identifier TRAPPED_CHEST_LOCATION = new Identifier("textures/entity/chest/trapped.png");
    private static final Identifier CHRISTMAS_CHEST_LOCATION = new Identifier("textures/entity/chest/christmas.png");
    private static final Identifier CHEST_LOCATION = new Identifier("textures/entity/chest/normal.png");
    private ChestModel singleChestModel = new ChestModel();
    private ChestModel doubleChestModel = new LargeChestModel();
    private boolean isChristmas;

    public ChestRenderer() {
        Calendar calendar = Calendar.getInstance();
        if (calendar.get(2) + 1 == 12 && calendar.get(5) >= 24 && calendar.get(5) <= 26) {
            this.isChristmas = true;
        }
    }

    public void render(ChestBlockEntity chestBlockEntity, double d, double e, double f, float g, int i) {
        GlStateManager.enableDepthTest();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
        int ix;
        if (!chestBlockEntity.hasWorld()) {
            ix = 0;
        } else {
            Block block = chestBlockEntity.getBlock();
            ix = chestBlockEntity.getBlockMetadata();
            if (block instanceof ChestBlock && ix == 0) {
                ((ChestBlock)block)
                    .updateState(chestBlockEntity.getWorld(), chestBlockEntity.getPos(), chestBlockEntity.getWorld().getBlockState(chestBlockEntity.getPos()));
                ix = chestBlockEntity.getBlockMetadata();
            }

            chestBlockEntity.updateShape();
        }

        if (chestBlockEntity.northNeighbor == null && chestBlockEntity.westNeighbor == null) {
            ChestModel chestmodel;
            if (chestBlockEntity.eastNeighbor == null && chestBlockEntity.southNeighbor == null) {
                chestmodel = this.singleChestModel;
                if (i >= 0) {
                    this.bindTexture(MINING_PROGRESS_LOCATIONS[i]);
                    GlStateManager.matrixMode(5890);
                    GlStateManager.pushMatrix();
                    GlStateManager.scalef(4.0F, 4.0F, 1.0F);
                    GlStateManager.translatef(0.0625F, 0.0625F, 0.0625F);
                    GlStateManager.matrixMode(5888);
                } else if (chestBlockEntity.getChestType() == 1) {
                    this.bindTexture(TRAPPED_CHEST_LOCATION);
                } else if (this.isChristmas) {
                    this.bindTexture(CHRISTMAS_CHEST_LOCATION);
                } else {
                    this.bindTexture(CHEST_LOCATION);
                }
            } else {
                chestmodel = this.doubleChestModel;
                if (i >= 0) {
                    this.bindTexture(MINING_PROGRESS_LOCATIONS[i]);
                    GlStateManager.matrixMode(5890);
                    GlStateManager.pushMatrix();
                    GlStateManager.scalef(8.0F, 4.0F, 1.0F);
                    GlStateManager.translatef(0.0625F, 0.0625F, 0.0625F);
                    GlStateManager.matrixMode(5888);
                } else if (chestBlockEntity.getChestType() == 1) {
                    this.bindTexture(TRAPPED_DOUBLE_CHEST_LOCATION);
                } else if (this.isChristmas) {
                    this.bindTexture(CHRISTMAS_DOUBLE_CHEST_LOCATION);
                } else {
                    this.bindTexture(DOUBLE_CHEST_LOCATION);
                }
            }

            GlStateManager.pushMatrix();
            GlStateManager.enableRescaleNormal();
            if (i < 0) {
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            }

            GlStateManager.translatef((float)d, (float)e + 1.0F, (float)f + 1.0F);
            GlStateManager.scalef(1.0F, -1.0F, -1.0F);
            GlStateManager.translatef(0.5F, 0.5F, 0.5F);
            int j = 0;
            if (ix == 2) {
                j = 180;
            }

            if (ix == 3) {
                j = 0;
            }

            if (ix == 4) {
                j = 90;
            }

            if (ix == 5) {
                j = -90;
            }

            if (ix == 2 && chestBlockEntity.eastNeighbor != null) {
                GlStateManager.translatef(1.0F, 0.0F, 0.0F);
            }

            if (ix == 5 && chestBlockEntity.southNeighbor != null) {
                GlStateManager.translatef(0.0F, 0.0F, -1.0F);
            }

            GlStateManager.rotatef(j, 0.0F, 1.0F, 0.0F);
            GlStateManager.translatef(-0.5F, -0.5F, -0.5F);
            float fx = chestBlockEntity.lastAnimationProgress + (chestBlockEntity.animationProgress - chestBlockEntity.lastAnimationProgress) * g;
            if (chestBlockEntity.northNeighbor != null) {
                float f1 = chestBlockEntity.northNeighbor.lastAnimationProgress
                    + (chestBlockEntity.northNeighbor.animationProgress - chestBlockEntity.northNeighbor.lastAnimationProgress) * g;
                if (f1 > fx) {
                    fx = f1;
                }
            }

            if (chestBlockEntity.westNeighbor != null) {
                float f2 = chestBlockEntity.westNeighbor.lastAnimationProgress
                    + (chestBlockEntity.westNeighbor.animationProgress - chestBlockEntity.westNeighbor.lastAnimationProgress) * g;
                if (f2 > fx) {
                    fx = f2;
                }
            }

            fx = 1.0F - fx;
            fx = 1.0F - fx * fx * fx;
            chestmodel.lid.rotationX = -(fx * (float) Math.PI / 2.0F);
            chestmodel.renderParts();
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (i >= 0) {
                GlStateManager.matrixMode(5890);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(5888);
            }
        }
    }
}
