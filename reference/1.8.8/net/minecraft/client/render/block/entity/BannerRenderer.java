package net.minecraft.client.render.block.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.block.entity.BannerModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.LayeredColorMaskTexture;
import net.minecraft.item.DyeColor;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class BannerRenderer extends BlockEntityRenderer<BannerBlockEntity> {
    private static final Map<String, BannerRenderer.CachedTexture> TEXTURE_CACHE = Maps.newHashMap();
    private static final Identifier BASE_TEXTURE = new Identifier("textures/entity/banner_base.png");
    private BannerModel model = new BannerModel();

    public void render(BannerBlockEntity bannerBlockEntity, double d, double e, double f, float g, int i) {
        boolean flag = bannerBlockEntity.getWorld() != null;
        boolean flag1 = !flag || bannerBlockEntity.getBlock() == Blocks.STANDING_BANNER;
        int ix = flag ? bannerBlockEntity.getBlockMetadata() : 0;
        long j = flag ? bannerBlockEntity.getWorld().getTime() : 0L;
        GlStateManager.pushMatrix();
        float fx = 0.6666667F;
        if (flag1) {
            GlStateManager.translatef((float)d + 0.5F, (float)e + 0.75F * fx, (float)f + 0.5F);
            float f1 = ix * 360 / 16.0F;
            GlStateManager.rotatef(-f1, 0.0F, 1.0F, 0.0F);
            this.model.pole.visible = true;
        } else {
            int k = ix;
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

            GlStateManager.translatef((float)d + 0.5F, (float)e - 0.25F * fx, (float)f + 0.5F);
            GlStateManager.rotatef(-f2, 0.0F, 1.0F, 0.0F);
            GlStateManager.translatef(0.0F, -0.3125F, -0.4375F);
            this.model.pole.visible = false;
        }

        BlockPos blockpos = bannerBlockEntity.getPos();
        float f3 = blockpos.getX() * 7 + blockpos.getY() * 9 + blockpos.getZ() * 13 + (float)j + g;
        this.model.flag.rotationX = (-0.0125F + 0.01F * MathHelper.cos(f3 * (float) Math.PI * 0.02F)) * (float) Math.PI;
        GlStateManager.enableRescaleNormal();
        Identifier identifier = this.getTexture(bannerBlockEntity);
        if (identifier != null) {
            this.bindTexture(identifier);
            GlStateManager.pushMatrix();
            GlStateManager.scalef(fx, -fx, -fx);
            this.model.render();
            GlStateManager.popMatrix();
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private Identifier getTexture(BannerBlockEntity banner) {
        String s = banner.getTexture();
        if (s.isEmpty()) {
            return null;
        }

        BannerRenderer.CachedTexture bannerrenderer$cachedtexture = TEXTURE_CACHE.get(s);
        if (bannerrenderer$cachedtexture == null) {
            if (TEXTURE_CACHE.size() >= 256) {
                long i = System.currentTimeMillis();
                Iterator<String> iterator = TEXTURE_CACHE.keySet().iterator();

                while (iterator.hasNext()) {
                    String s1 = iterator.next();
                    BannerRenderer.CachedTexture bannerrenderer$cachedtexture1 = TEXTURE_CACHE.get(s1);
                    if (i - bannerrenderer$cachedtexture1.cachedTime > 60000L) {
                        Minecraft.getInstance().getTextureManager().close(bannerrenderer$cachedtexture1.texture);
                        iterator.remove();
                    }
                }

                if (TEXTURE_CACHE.size() >= 256) {
                    return null;
                }
            }

            List<BannerBlockEntity.Pattern> list1 = banner.getPatterns();
            List<DyeColor> list = banner.getColors();
            List<String> list2 = Lists.newArrayList();

            for (BannerBlockEntity.Pattern bannerblockentity$pattern : list1) {
                list2.add("textures/entity/banner/" + bannerblockentity$pattern.getKey() + ".png");
            }

            bannerrenderer$cachedtexture = new BannerRenderer.CachedTexture();
            bannerrenderer$cachedtexture.texture = new Identifier(s);
            Minecraft.getInstance().getTextureManager().register(bannerrenderer$cachedtexture.texture, new LayeredColorMaskTexture(BASE_TEXTURE, list2, list));
            TEXTURE_CACHE.put(s, bannerrenderer$cachedtexture);
        }

        bannerrenderer$cachedtexture.cachedTime = System.currentTimeMillis();
        return bannerrenderer$cachedtexture.texture;
    }

    static class CachedTexture {
        public long cachedTime;
        public Identifier texture;

        private CachedTexture() {
        }
    }
}
