package net.minecraft.client.render.entity;

import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.entity.layer.CapeLayer;
import net.minecraft.client.render.entity.layer.Deadmou5Layer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.entity.layer.StuckArrowLayer;
import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;

public class PlayerRenderer extends LivingEntityRenderer<ClientPlayerEntity> {
    private boolean thinArms;

    public PlayerRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        this(entityRenderDispatcher, false);
    }

    public PlayerRenderer(EntityRenderDispatcher dispatcher, boolean thinArms) {
        super(dispatcher, new PlayerModel(0.0F, thinArms), 0.5F);
        this.thinArms = thinArms;
        this.addLayer(new ArmorLayer(this));
        this.addLayer(new ItemInHandLayer(this));
        this.addLayer(new StuckArrowLayer(this));
        this.addLayer(new Deadmou5Layer(this));
        this.addLayer(new CapeLayer(this));
        this.addLayer(new WornSkullLayer(this.getModel().head));
    }

    public PlayerModel getModel() {
        return (PlayerModel)super.getModel();
    }

    public void render(ClientPlayerEntity clientPlayerEntity, double d, double e, double f, float g, float h) {
        if (!clientPlayerEntity.isLocal() || this.dispatcher.camera == clientPlayerEntity) {
            double d0 = e;
            if (clientPlayerEntity.isSneaking() && !(clientPlayerEntity instanceof LocalClientPlayerEntity)) {
                d0 -= 0.125;
            }

            this.setModelStatus(clientPlayerEntity);
            super.render(clientPlayerEntity, d, d0, f, g, h);
        }
    }

    private void setModelStatus(ClientPlayerEntity entity) {
        PlayerModel playermodel = this.getModel();
        if (entity.isSpectator()) {
            playermodel.setVisible(false);
            playermodel.head.visible = true;
            playermodel.hat.visible = true;
        } else {
            ItemStack itemstack = entity.inventory.getSelectedItem();
            playermodel.setVisible(true);
            playermodel.hat.visible = entity.isModelPartVisible(PlayerModelPart.HAT);
            playermodel.jacket.visible = entity.isModelPartVisible(PlayerModelPart.JACKET);
            playermodel.leftPants.visible = entity.isModelPartVisible(PlayerModelPart.LEFT_PANTS_LEG);
            playermodel.rightPants.visible = entity.isModelPartVisible(PlayerModelPart.RIGHT_PANTS_LEG);
            playermodel.leftSleeve.visible = entity.isModelPartVisible(PlayerModelPart.LEFT_SLEEVE);
            playermodel.rightSleeve.visible = entity.isModelPartVisible(PlayerModelPart.RIGHT_SLEEVE);
            playermodel.itemInLeftHand = 0;
            playermodel.aimingBow = false;
            playermodel.sneaking = entity.isSneaking();
            if (itemstack == null) {
                playermodel.itemInRightHand = 0;
            } else {
                playermodel.itemInRightHand = 1;
                if (entity.getItemUseTimer() > 0) {
                    UseAction useaction = itemstack.getUseAction();
                    if (useaction == UseAction.BLOCK) {
                        playermodel.itemInRightHand = 3;
                    } else if (useaction == UseAction.BOW) {
                        playermodel.aimingBow = true;
                    }
                }
            }
        }
    }

    protected Identifier getTextureLocation(ClientPlayerEntity clientPlayerEntity) {
        return clientPlayerEntity.getSkinTextureLocation();
    }

    @Override
    public void glTranslate() {
        GlStateManager.translatef(0.0F, 0.1875F, 0.0F);
    }

    protected void applyScale(ClientPlayerEntity clientPlayerEntity, float f) {
        float fx = 0.9375F;
        GlStateManager.scalef(fx, fx, fx);
    }

    protected void renderNameTag(ClientPlayerEntity clientPlayerEntity, double d, double e, double f, String string, float g, double h) {
        if (h < 100.0) {
            Scoreboard scoreboard = clientPlayerEntity.getScoreboard();
            ScoreboardObjective scoreboardobjective = scoreboard.getDisplayObjective(2);
            if (scoreboardobjective != null) {
                ScoreboardScore scoreboardscore = scoreboard.getScore(clientPlayerEntity.getName(), scoreboardobjective);
                this.renderNameTag(clientPlayerEntity, scoreboardscore.get() + " " + scoreboardobjective.getDisplayName(), d, e, f, 64);
                e += this.getTextRenderer().fontHeight * 1.15F * g;
            }
        }

        super.renderNameTag(clientPlayerEntity, d, e, f, string, g, h);
    }

    public void renderRightHand(ClientPlayerEntity player) {
        float f = 1.0F;
        GlStateManager.color3f(f, f, f);
        PlayerModel playermodel = this.getModel();
        this.setModelStatus(player);
        playermodel.attackAnimationProgress = 0.0F;
        playermodel.sneaking = false;
        playermodel.setupAnimation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, player);
        playermodel.renderRightArm();
    }

    public void renderPlayerLeftHandModel(ClientPlayerEntity player) {
        float f = 1.0F;
        GlStateManager.color3f(f, f, f);
        PlayerModel playermodel = this.getModel();
        this.setModelStatus(player);
        playermodel.sneaking = false;
        playermodel.attackAnimationProgress = 0.0F;
        playermodel.setupAnimation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, player);
        playermodel.renderLeftArm();
    }

    protected void applyTranslation(ClientPlayerEntity clientPlayerEntity, double d, double e, double f) {
        if (clientPlayerEntity.isAlive() && clientPlayerEntity.isSleeping()) {
            super.applyTranslation(
                clientPlayerEntity,
                d + clientPlayerEntity.sleepingCameraOffsetX,
                e + clientPlayerEntity.sleepingCameraOffsetY,
                f + clientPlayerEntity.sleepingCameraOffsetZ
            );
        } else {
            super.applyTranslation(clientPlayerEntity, d, e, f);
        }
    }

    protected void applyRotation(ClientPlayerEntity clientPlayerEntity, float f, float g, float h) {
        if (clientPlayerEntity.isAlive() && clientPlayerEntity.isSleeping()) {
            GlStateManager.rotatef(clientPlayerEntity.getSleepingCameraAngle(), 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(this.getDeathYaw(clientPlayerEntity), 0.0F, 0.0F, 1.0F);
            GlStateManager.rotatef(270.0F, 0.0F, 1.0F, 0.0F);
        } else {
            super.applyRotation(clientPlayerEntity, f, g, h);
        }
    }
}
