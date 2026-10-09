package net.minecraft.client.render;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.UseAction;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.map.SavedMapData;
import org.lwjgl.opengl.GL11;

public class ItemInHandRenderer {
    private static final Identifier MAP_BACKGROUND_LOCATION = new Identifier("textures/map/map_background.png");
    private static final Identifier UNDERWATER_LOCATION = new Identifier("textures/misc/underwater.png");
    private final Minecraft minecraft;
    private ItemStack itemInHand;
    private float handHeight;
    private float lastHandHeight;
    private final EntityRenderDispatcher dispatcher;
    private final ItemRenderer renderer;
    private int selectedSlot = -1;

    public ItemInHandRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.dispatcher = minecraft.getEntityRenderDispatcher();
        this.renderer = minecraft.getItemRenderer();
    }

    public void render(LivingEntity entity, ItemStack item, ModelTransformations.Type transform) {
        if (item != null) {
            Item itemx = item.getItem();
            Block block = Block.byItem(itemx);
            GlStateManager.pushMatrix();
            if (this.renderer.isGui3d(item)) {
                GlStateManager.scalef(2.0F, 2.0F, 2.0F);
                if (this.isTranslucent(block)) {
                    GlStateManager.depthMask(false);
                }
            }

            this.renderer.renderItemInHand(item, entity, transform);
            if (this.isTranslucent(block)) {
                GlStateManager.depthMask(true);
            }

            GlStateManager.popMatrix();
        }
    }

    private boolean isTranslucent(Block block) {
        return block != null && block.getRenderLayer() == BlockLayer.TRANSLUCENT;
    }

    private void rotate(float x, float y) {
        GlStateManager.pushMatrix();
        GlStateManager.rotatef(x, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef(y, 0.0F, 1.0F, 0.0F);
        Lighting.turnOn();
        GlStateManager.popMatrix();
    }

    private void setHandLightColor(ClientPlayerEntity player) {
        int i = this.minecraft.world.getLightColor(new BlockPos(player.x, player.y + player.getEyeHeight(), player.z), 0);
        float f = i & 65535;
        float f1 = i >> 16;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, f, f1);
    }

    private void applyHandSway(LocalClientPlayerEntity player, float tickDelta) {
        float f = player.lastEasedPitch + (player.easedPitch - player.lastEasedPitch) * tickDelta;
        float f1 = player.lastEasedYaw + (player.easedYaw - player.lastEasedYaw) * tickDelta;
        GlStateManager.rotatef((player.pitch - f) * 0.1F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef((player.yaw - f1) * 0.1F, 0.0F, 1.0F, 0.0F);
    }

    private float getMapPitch(float pitch) {
        float f = 1.0F - pitch / 45.0F + 0.1F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        return -MathHelper.cos(f * (float) Math.PI) * 0.5F + 0.5F;
    }

    private void renderRightArm(PlayerRenderer player) {
        GlStateManager.pushMatrix();
        GlStateManager.rotatef(54.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(64.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef(-62.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.translatef(0.25F, -0.85F, 0.75F);
        player.renderRightHand(this.minecraft.player);
        GlStateManager.popMatrix();
    }

    private void renderLeftArm(PlayerRenderer player) {
        GlStateManager.pushMatrix();
        GlStateManager.rotatef(92.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(45.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef(41.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.translatef(-0.3F, -1.1F, 0.45F);
        player.renderPlayerLeftHandModel(this.minecraft.player);
        GlStateManager.popMatrix();
    }

    private void renderArms(ClientPlayerEntity player) {
        this.minecraft.getTextureManager().bind(player.getSkinTextureLocation());
        EntityRenderer<ClientPlayerEntity> entityrenderer = this.dispatcher.getRenderer(this.minecraft.player);
        PlayerRenderer playerrenderer = (PlayerRenderer)entityrenderer;
        if (!player.isInvisible()) {
            GlStateManager.disableCull();
            this.renderRightArm(playerrenderer);
            this.renderLeftArm(playerrenderer);
            GlStateManager.enableCull();
        }
    }

    private void renderMap(ClientPlayerEntity player, float pitch, float swapProgress, float swingProgress) {
        float f = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI * 2.0F);
        float f2 = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translatef(f, f1, f2);
        float f3 = this.getMapPitch(pitch);
        GlStateManager.translatef(0.0F, 0.04F, -0.72F);
        GlStateManager.translatef(0.0F, swapProgress * -1.2F, 0.0F);
        GlStateManager.translatef(0.0F, f3 * -0.5F, 0.0F);
        GlStateManager.rotatef(90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(f3 * -85.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotatef(0.0F, 1.0F, 0.0F, 0.0F);
        this.renderArms(player);
        float f4 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f5 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        GlStateManager.rotatef(f4 * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(f5 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotatef(f5 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scalef(0.38F, 0.38F, 0.38F);
        GlStateManager.rotatef(90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(180.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotatef(0.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translatef(-1.0F, -1.0F, 0.0F);
        GlStateManager.scalef(0.015625F, 0.015625F, 0.015625F);
        this.minecraft.getTextureManager().bind(MAP_BACKGROUND_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        GL11.glNormal3f(0.0F, 0.0F, -1.0F);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(-7.0, 135.0, 0.0).texture(0.0, 1.0).nextVertex();
        bufferbuilder.vertex(135.0, 135.0, 0.0).texture(1.0, 1.0).nextVertex();
        bufferbuilder.vertex(135.0, -7.0, 0.0).texture(1.0, 0.0).nextVertex();
        bufferbuilder.vertex(-7.0, -7.0, 0.0).texture(0.0, 0.0).nextVertex();
        tesselator.end();
        SavedMapData savedmapdata = Items.FILLED_MAP.getSavedMapData(this.itemInHand, this.minecraft.world);
        if (savedmapdata != null) {
            this.minecraft.gameRenderer.getMapRenderer().draw(savedmapdata, false);
        }
    }

    private void renderHand(ClientPlayerEntity player, float swapProgress, float swingProgress) {
        float f = -0.3F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float f1 = 0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI * 2.0F);
        float f2 = -0.4F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translatef(f, f1, f2);
        GlStateManager.translatef(0.64000005F, -0.6F, -0.71999997F);
        GlStateManager.translatef(0.0F, swapProgress * -0.6F, 0.0F);
        GlStateManager.rotatef(45.0F, 0.0F, 1.0F, 0.0F);
        float f3 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f4 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        GlStateManager.rotatef(f4 * 70.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(f3 * -20.0F, 0.0F, 0.0F, 1.0F);
        this.minecraft.getTextureManager().bind(player.getSkinTextureLocation());
        GlStateManager.translatef(-1.0F, 3.6F, 3.5F);
        GlStateManager.rotatef(120.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotatef(200.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scalef(1.0F, 1.0F, 1.0F);
        GlStateManager.translatef(5.6F, 0.0F, 0.0F);
        EntityRenderer<ClientPlayerEntity> entityrenderer = this.dispatcher.getRenderer(this.minecraft.player);
        GlStateManager.disableCull();
        PlayerRenderer playerrenderer = (PlayerRenderer)entityrenderer;
        playerrenderer.renderRightHand(this.minecraft.player);
        GlStateManager.enableCull();
    }

    private void applyArmSwing(float swingProgress) {
        float f = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI * 2.0F);
        float f2 = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translatef(f, f1, f2);
    }

    private void applyConsuming(ClientPlayerEntity player, float tickDelta) {
        float f = player.getItemUseTimer() - tickDelta + 1.0F;
        float f1 = f / this.itemInHand.getUseDuration();
        float f2 = MathHelper.abs(MathHelper.cos(f / 4.0F * (float) Math.PI) * 0.1F);
        if (f1 >= 0.8F) {
            f2 = 0.0F;
        }

        GlStateManager.translatef(0.0F, f2, 0.0F);
        float f3 = 1.0F - (float)Math.pow(f1, 27.0);
        GlStateManager.translatef(f3 * 0.6F, f3 * -0.5F, f3 * 0.0F);
        GlStateManager.rotatef(f3 * 90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(f3 * 10.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef(f3 * 30.0F, 0.0F, 0.0F, 1.0F);
    }

    private void applyFirstPersonTransform(float xU, float yU) {
        GlStateManager.translatef(0.56F, -0.52F, -0.71999997F);
        GlStateManager.translatef(0.0F, xU * -0.6F, 0.0F);
        GlStateManager.rotatef(45.0F, 0.0F, 1.0F, 0.0F);
        float f = MathHelper.sin(yU * yU * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt(yU) * (float) Math.PI);
        GlStateManager.rotatef(f * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotatef(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scalef(0.4F, 0.4F, 0.4F);
    }

    private void applyBowNocking(float tickDelta, ClientPlayerEntity player) {
        GlStateManager.rotatef(-18.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotatef(-12.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-8.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translatef(-0.9F, 0.2F, 0.0F);
        float f = this.itemInHand.getUseDuration() - (player.getItemUseTimer() - tickDelta + 1.0F);
        float f1 = f / 20.0F;
        f1 = (f1 * f1 + f1 * 2.0F) / 3.0F;
        if (f1 > 1.0F) {
            f1 = 1.0F;
        }

        if (f1 > 0.1F) {
            float f2 = MathHelper.sin((f - 0.1F) * 1.3F);
            float f3 = f1 - 0.1F;
            float f4 = f2 * f3;
            GlStateManager.translatef(f4 * 0.0F, f4 * 0.01F, f4 * 0.0F);
        }

        GlStateManager.translatef(f1 * 0.0F, f1 * 0.0F, f1 * 0.1F);
        GlStateManager.scalef(1.0F, 1.0F, 1.0F + f1 * 0.2F);
    }

    private void applySwordBlocking() {
        GlStateManager.translatef(-0.5F, 0.2F, 0.0F);
        GlStateManager.rotatef(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef(60.0F, 0.0F, 1.0F, 0.0F);
    }

    public void renderInFirstPerson(float tickDelta) {
        float f = 1.0F - (this.lastHandHeight + (this.handHeight - this.lastHandHeight) * tickDelta);
        ClientPlayerEntity clientplayerentity = this.minecraft.player;
        float f1 = clientplayerentity.getAttackAnimationProgress(tickDelta);
        float f2 = clientplayerentity.lastPitch + (clientplayerentity.pitch - clientplayerentity.lastPitch) * tickDelta;
        float f3 = clientplayerentity.lastYaw + (clientplayerentity.yaw - clientplayerentity.lastYaw) * tickDelta;
        this.rotate(f2, f3);
        this.setHandLightColor(clientplayerentity);
        this.applyHandSway((LocalClientPlayerEntity)clientplayerentity, tickDelta);
        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        if (this.itemInHand != null) {
            if (this.itemInHand.getItem() == Items.FILLED_MAP) {
                this.renderMap(clientplayerentity, f2, f, f1);
            } else if (clientplayerentity.getItemUseTimer() > 0) {
                UseAction useaction = this.itemInHand.getUseAction();
                switch (useaction) {
                    case NONE:
                        this.applyFirstPersonTransform(f, 0.0F);
                        break;
                    case EAT:
                    case DRINK:
                        this.applyConsuming(clientplayerentity, tickDelta);
                        this.applyFirstPersonTransform(f, 0.0F);
                        break;
                    case BLOCK:
                        this.applyFirstPersonTransform(f, 0.0F);
                        this.applySwordBlocking();
                        break;
                    case BOW:
                        this.applyFirstPersonTransform(f, 0.0F);
                        this.applyBowNocking(tickDelta, clientplayerentity);
                }
            } else {
                this.applyArmSwing(f1);
                this.applyFirstPersonTransform(f, f1);
            }

            this.render(clientplayerentity, this.itemInHand, ModelTransformations.Type.FIRST_PERSON);
        } else if (!clientplayerentity.isInvisible()) {
            this.renderHand(clientplayerentity, f, f1);
        }

        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        Lighting.turnOff();
    }

    public void renderScreenEffects(float tickDelta) {
        GlStateManager.disableAlphaTest();
        if (this.minecraft.player.isInWall()) {
            BlockState blockstate = this.minecraft.world.getBlockState(new BlockPos(this.minecraft.player));
            PlayerEntity playerentity = this.minecraft.player;

            for (int i = 0; i < 8; i++) {
                double d0 = playerentity.x + ((i >> 0) % 2 - 0.5F) * playerentity.width * 0.8F;
                double d1 = playerentity.y + ((i >> 1) % 2 - 0.5F) * 0.1F;
                double d2 = playerentity.z + ((i >> 2) % 2 - 0.5F) * playerentity.width * 0.8F;
                BlockPos blockpos = new BlockPos(d0, d1 + playerentity.getEyeHeight(), d2);
                BlockState blockstate1 = this.minecraft.world.getBlockState(blockpos);
                if (blockstate1.getBlock().isViewBlocking()) {
                    blockstate = blockstate1;
                }
            }

            if (blockstate.getBlock().getRenderType() != -1) {
                this.renderInWallEffect(tickDelta, this.minecraft.getBlockRenderDispatcher().getModelShaper().getParticleIcon(blockstate));
            }
        }

        if (!this.minecraft.player.isSpectator()) {
            if (this.minecraft.player.isSubmergedIn(Material.WATER)) {
                this.renderInWaterEffect(tickDelta);
            }

            if (this.minecraft.player.isOnFire()) {
                this.renderOnFireEffect(tickDelta);
            }
        }

        GlStateManager.enableAlphaTest();
    }

    private void renderInWallEffect(float tickDelta, TextureAtlasSprite sprite) {
        this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        float f = 0.1F;
        GlStateManager.color4f(0.1F, 0.1F, 0.1F, 0.5F);
        GlStateManager.pushMatrix();
        float f1 = -1.0F;
        float f2 = 1.0F;
        float f3 = -1.0F;
        float f4 = 1.0F;
        float f5 = -0.5F;
        float f6 = sprite.getUMin();
        float f7 = sprite.getUMax();
        float f8 = sprite.getVMin();
        float f9 = sprite.getVMax();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(-1.0, -1.0, -0.5).texture(f7, f9).nextVertex();
        bufferbuilder.vertex(1.0, -1.0, -0.5).texture(f6, f9).nextVertex();
        bufferbuilder.vertex(1.0, 1.0, -0.5).texture(f6, f8).nextVertex();
        bufferbuilder.vertex(-1.0, 1.0, -0.5).texture(f7, f8).nextVertex();
        tesselator.end();
        GlStateManager.popMatrix();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderInWaterEffect(float tickDelta) {
        this.minecraft.getTextureManager().bind(UNDERWATER_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        float f = this.minecraft.player.getBrightness(tickDelta);
        GlStateManager.color4f(f, f, f, 0.5F);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.pushMatrix();
        float f1 = 4.0F;
        float f2 = -1.0F;
        float f3 = 1.0F;
        float f4 = -1.0F;
        float f5 = 1.0F;
        float f6 = -0.5F;
        float f7 = -this.minecraft.player.yaw / 64.0F;
        float f8 = this.minecraft.player.pitch / 64.0F;
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(-1.0, -1.0, -0.5).texture(4.0F + f7, 4.0F + f8).nextVertex();
        bufferbuilder.vertex(1.0, -1.0, -0.5).texture(0.0F + f7, 4.0F + f8).nextVertex();
        bufferbuilder.vertex(1.0, 1.0, -0.5).texture(0.0F + f7, 0.0F + f8).nextVertex();
        bufferbuilder.vertex(-1.0, 1.0, -0.5).texture(4.0F + f7, 0.0F + f8).nextVertex();
        tesselator.end();
        GlStateManager.popMatrix();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    private void renderOnFireEffect(float tickDelta) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 0.9F);
        GlStateManager.depthFunc(519);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        float f = 1.0F;

        for (int i = 0; i < 2; i++) {
            GlStateManager.pushMatrix();
            TextureAtlasSprite textureatlassprite = this.minecraft.getBlocksAtlas().getSprite("minecraft:blocks/fire_layer_1");
            this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
            float f1 = textureatlassprite.getUMin();
            float f2 = textureatlassprite.getUMax();
            float f3 = textureatlassprite.getVMin();
            float f4 = textureatlassprite.getVMax();
            float f5 = (0.0F - f) / 2.0F;
            float f6 = f5 + f;
            float f7 = 0.0F - f / 2.0F;
            float f8 = f7 + f;
            float f9 = -0.5F;
            GlStateManager.translatef(-(i * 2 - 1) * 0.24F, -0.3F, 0.0F);
            GlStateManager.rotatef((i * 2 - 1) * 10.0F, 0.0F, 1.0F, 0.0F);
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(f5, f7, f9).texture(f2, f4).nextVertex();
            bufferbuilder.vertex(f6, f7, f9).texture(f1, f4).nextVertex();
            bufferbuilder.vertex(f6, f8, f9).texture(f1, f3).nextVertex();
            bufferbuilder.vertex(f5, f8, f9).texture(f2, f3).nextVertex();
            tesselator.end();
            GlStateManager.popMatrix();
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
    }

    public void tick() {
        this.lastHandHeight = this.handHeight;
        PlayerEntity playerentity = this.minecraft.player;
        ItemStack itemstack = playerentity.inventory.getSelectedItem();
        boolean flag = false;
        if (this.itemInHand != null && itemstack != null) {
            if (!this.itemInHand.isEqualForHoldAnimation(itemstack)) {
                flag = true;
            }
        } else if (this.itemInHand == null && itemstack == null) {
            flag = false;
        } else {
            flag = true;
        }

        float f = 0.4F;
        float f1 = flag ? 0.0F : 1.0F;
        float f2 = MathHelper.clamp(f1 - this.handHeight, -f, f);
        this.handHeight += f2;
        if (this.handHeight < 0.1F) {
            this.itemInHand = itemstack;
            this.selectedSlot = playerentity.inventory.selectedSlot;
        }
    }

    public void onBlockUsed() {
        this.handHeight = 0.0F;
    }

    public void onItemUsed() {
        this.handHeight = 0.0F;
    }
}
