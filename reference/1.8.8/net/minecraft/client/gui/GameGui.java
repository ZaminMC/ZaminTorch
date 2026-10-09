package net.minecraft.client.gui;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.gui.overlay.DebugOverlay;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import net.minecraft.client.gui.overlay.StreamOverlay;
import net.minecraft.client.gui.spectator.SpectatorGui;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.player.HungerManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Formatting;
import net.minecraft.text.StringUtils;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HitResult;
import net.minecraft.world.border.WorldBorder;

public class GameGui extends GuiElement {
    private static final Identifier VIGNETTE_LOCATION = new Identifier("textures/misc/vignette.png");
    private static final Identifier WIDGETS_LOCATION = new Identifier("textures/gui/widgets.png");
    private static final Identifier PUMPKIN_BLUR_LOCATION = new Identifier("textures/misc/pumpkinblur.png");
    private final Random random = new Random();
    private final Minecraft minecraft;
    private final ItemRenderer itemRenderer;
    private final ChatGui chat;
    private final StreamOverlay streamOverlay;
    private int ticks;
    private String overlayMessage = "";
    private int overlayMessageCooldown;
    private boolean overlayMessageTinted;
    public float vignetteBrightness = 1.0F;
    private int mainHandMessageTimer;
    private ItemStack itemInMainHand;
    private final DebugOverlay debugOverlay;
    private final SpectatorGui spectatorGui;
    private final PlayerTabOverlay playerTabOverlay;
    private int titleTime;
    private String title = "";
    private String subtitle = "";
    private int titleFadeInTime;
    private int titleDuration;
    private int titleFadeOutTime;
    private int lastHealth = 0;
    private int displayHealth = 0;
    private long lastHealthTime = 0L;
    private long lastHealthAnimationTime = 0L;

    public GameGui(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.itemRenderer = minecraft.getItemRenderer();
        this.debugOverlay = new DebugOverlay(minecraft);
        this.spectatorGui = new SpectatorGui(minecraft);
        this.chat = new ChatGui(minecraft);
        this.streamOverlay = new StreamOverlay(minecraft);
        this.playerTabOverlay = new PlayerTabOverlay(minecraft, this);
        this.resetTitleTimes();
    }

    public void resetTitleTimes() {
        this.titleFadeInTime = 10;
        this.titleDuration = 70;
        this.titleFadeOutTime = 20;
    }

    public void render(float tickDelta) {
        Window window = new Window(this.minecraft);
        int i = window.getWidth();
        int j = window.getHeight();
        this.minecraft.gameRenderer.setupGuiState();
        GlStateManager.enableBlend();
        if (Minecraft.isFancyGraphicsEnabled()) {
            this.renderVignette(this.minecraft.player.getBrightness(tickDelta), window);
        } else {
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        }

        ItemStack itemstack = this.minecraft.player.inventory.getArmor(3);
        if (this.minecraft.options.perspective == 0 && itemstack != null && itemstack.getItem() == Item.byBlock(Blocks.PUMPKIN)) {
            this.renderPumpkinOverlay(window);
        }

        if (!this.minecraft.player.hasStatusEffect(StatusEffect.NAUSEA)) {
            float f = this.minecraft.player.lastPortalTime + (this.minecraft.player.portalTime - this.minecraft.player.lastPortalTime) * tickDelta;
            if (f > 0.0F) {
                this.renderNauseaOverlay(f, window);
            }
        }

        if (this.minecraft.interactionManager.hidesGui()) {
            this.spectatorGui.renderHotbar(window, tickDelta);
        } else {
            this.renderHotbar(window, tickDelta);
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(ICONS_LOCATION);
        GlStateManager.enableBlend();
        if (this.hasCrosshair()) {
            GlStateManager.blendFuncSeparate(775, 769, 1, 0);
            GlStateManager.enableAlphaTest();
            this.drawTexture(i / 2 - 7, j / 2 - 7, 0, 0, 16, 16);
        }

        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        this.minecraft.profiler.push("bossHealth");
        this.renderBossBars();
        this.minecraft.profiler.pop();
        if (this.minecraft.interactionManager.hasStatusBars()) {
            this.renderStatusBars(window);
        }

        GlStateManager.disableBlend();
        if (this.minecraft.player.getSleepingTime() > 0) {
            this.minecraft.profiler.push("sleep");
            GlStateManager.disableDepthTest();
            GlStateManager.disableAlphaTest();
            int j1 = this.minecraft.player.getSleepingTime();
            float f1 = j1 / 100.0F;
            if (f1 > 1.0F) {
                f1 = 1.0F - (j1 - 100) / 10.0F;
            }

            int k = (int)(220.0F * f1) << 24 | 1052704;
            fill(0, 0, i, j, k);
            GlStateManager.enableAlphaTest();
            GlStateManager.enableDepthTest();
            this.minecraft.profiler.pop();
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        int k1 = i / 2 - 91;
        if (this.minecraft.player.isRidingRideableMob()) {
            this.renderJumpBar(window, k1);
        } else if (this.minecraft.interactionManager.hasXpBar()) {
            this.renderXpBar(window, k1);
        }

        if (this.minecraft.options.itemInHandTooltips && !this.minecraft.interactionManager.hidesGui()) {
            this.renderMainHandMessage(window);
        } else if (this.minecraft.player.isSpectator()) {
            this.spectatorGui.renderTooltip(window);
        }

        if (this.minecraft.isDemo()) {
            this.renderDemoMessage(window);
        }

        if (this.minecraft.options.debugEnabled) {
            this.debugOverlay.render(window);
        }

        if (this.overlayMessageCooldown > 0) {
            this.minecraft.profiler.push("overlayMessage");
            float f2 = this.overlayMessageCooldown - tickDelta;
            int l1 = (int)(f2 * 255.0F / 20.0F);
            if (l1 > 255) {
                l1 = 255;
            }

            if (l1 > 8) {
                GlStateManager.pushMatrix();
                GlStateManager.translatef(i / 2, j - 68, 0.0F);
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                int l = 16777215;
                if (this.overlayMessageTinted) {
                    l = MathHelper.toRgb(f2 / 50.0F, 0.7F, 0.6F) & 16777215;
                }

                this.getTextRenderer().draw(this.overlayMessage, -this.getTextRenderer().getWidth(this.overlayMessage) / 2, -4, l + (l1 << 24 & 0xFF000000));
                GlStateManager.disableBlend();
                GlStateManager.popMatrix();
            }

            this.minecraft.profiler.pop();
        }

        if (this.titleTime > 0) {
            this.minecraft.profiler.push("titleAndSubtitle");
            float f3 = this.titleTime - tickDelta;
            int i2 = 255;
            if (this.titleTime > this.titleFadeOutTime + this.titleDuration) {
                float f4 = this.titleFadeInTime + this.titleDuration + this.titleFadeOutTime - f3;
                i2 = (int)(f4 * 255.0F / this.titleFadeInTime);
            }

            if (this.titleTime <= this.titleFadeOutTime) {
                float f5 = f3;
                i2 = (int)(f5 * 255.0F / this.titleFadeOutTime);
            }

            i2 = MathHelper.clamp(i2, 0, 255);
            if (i2 > 8) {
                GlStateManager.pushMatrix();
                GlStateManager.translatef(i / 2, j / 2, 0.0F);
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                GlStateManager.pushMatrix();
                GlStateManager.scalef(4.0F, 4.0F, 4.0F);
                int j2 = i2 << 24 & 0xFF000000;
                this.getTextRenderer().draw(this.title, -this.getTextRenderer().getWidth(this.title) / 2, -10.0F, 16777215 | j2, true);
                GlStateManager.popMatrix();
                GlStateManager.pushMatrix();
                GlStateManager.scalef(2.0F, 2.0F, 2.0F);
                this.getTextRenderer().draw(this.subtitle, -this.getTextRenderer().getWidth(this.subtitle) / 2, 5.0F, 16777215 | j2, true);
                GlStateManager.popMatrix();
                GlStateManager.disableBlend();
                GlStateManager.popMatrix();
            }

            this.minecraft.profiler.pop();
        }

        Scoreboard scoreboard = this.minecraft.world.getScoreboard();
        ScoreboardObjective scoreboardobjective = null;
        Team team = scoreboard.getTeamOfMember(this.minecraft.player.getName());
        if (team != null) {
            int i1 = team.getColor().getId();
            if (i1 >= 0) {
                scoreboardobjective = scoreboard.getDisplayObjective(3 + i1);
            }
        }

        ScoreboardObjective scoreboardobjective1 = scoreboardobjective != null ? scoreboardobjective : scoreboard.getDisplayObjective(1);
        if (scoreboardobjective1 != null) {
            this.renderScoreboardObjective(scoreboardobjective1, window);
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlphaTest();
        GlStateManager.pushMatrix();
        GlStateManager.translatef(0.0F, j - 48, 0.0F);
        this.minecraft.profiler.push("chat");
        this.chat.render(this.ticks);
        this.minecraft.profiler.pop();
        GlStateManager.popMatrix();
        scoreboardobjective1 = scoreboard.getDisplayObjective(0);
        if (!this.minecraft.options.playerListKey.isPressed()
            || this.minecraft.isIntegratedServerRunning()
                && this.minecraft.player.networkHandler.getOnlinePlayers().size() <= 1
                && scoreboardobjective1 == null) {
            this.playerTabOverlay.setVisible(false);
        } else {
            this.playerTabOverlay.setVisible(true);
            this.playerTabOverlay.render(i, scoreboard, scoreboardobjective1);
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableAlphaTest();
    }

    protected void renderHotbar(Window window, float tickDelta) {
        if (this.minecraft.getCamera() instanceof PlayerEntity) {
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.minecraft.getTextureManager().bind(WIDGETS_LOCATION);
            PlayerEntity playerentity = (PlayerEntity)this.minecraft.getCamera();
            int i = window.getWidth() / 2;
            float f = this.drawOffset;
            this.drawOffset = -90.0F;
            this.drawTexture(i - 91, window.getHeight() - 22, 0, 0, 182, 22);
            this.drawTexture(i - 91 - 1 + playerentity.inventory.selectedSlot * 20, window.getHeight() - 22 - 1, 0, 22, 24, 22);
            this.drawOffset = f;
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            Lighting.turnOnGui();

            for (int j = 0; j < 9; j++) {
                int k = window.getWidth() / 2 - 90 + j * 20 + 2;
                int l = window.getHeight() - 16 - 3;
                this.renderItemSlot(j, k, l, tickDelta, playerentity);
            }

            Lighting.turnOff();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableBlend();
        }
    }

    public void renderJumpBar(Window window, int x) {
        this.minecraft.profiler.push("jumpBar");
        this.minecraft.getTextureManager().bind(GuiElement.ICONS_LOCATION);
        float f = this.minecraft.player.getRidingJumpProgress();
        int i = 182;
        int j = (int)(f * (i + 1));
        int k = window.getHeight() - 32 + 3;
        this.drawTexture(x, k, 0, 84, i, 5);
        if (j > 0) {
            this.drawTexture(x, k, 0, 89, j, 5);
        }

        this.minecraft.profiler.pop();
    }

    public void renderXpBar(Window window, int x) {
        this.minecraft.profiler.push("expBar");
        this.minecraft.getTextureManager().bind(GuiElement.ICONS_LOCATION);
        int i = this.minecraft.player.getNextLevelExperience();
        if (i > 0) {
            int j = 182;
            int k = (int)(this.minecraft.player.xpProgress * (j + 1));
            int l = window.getHeight() - 32 + 3;
            this.drawTexture(x, l, 0, 64, j, 5);
            if (k > 0) {
                this.drawTexture(x, l, 0, 69, k, 5);
            }
        }

        this.minecraft.profiler.pop();
        if (this.minecraft.player.xpLevel > 0) {
            this.minecraft.profiler.push("expLevel");
            int k1 = 8453920;
            String s = "" + this.minecraft.player.xpLevel;
            int l1 = (window.getWidth() - this.getTextRenderer().getWidth(s)) / 2;
            int i1 = window.getHeight() - 31 - 4;
            int j1 = 0;
            this.getTextRenderer().draw(s, l1 + 1, i1, 0);
            this.getTextRenderer().draw(s, l1 - 1, i1, 0);
            this.getTextRenderer().draw(s, l1, i1 + 1, 0);
            this.getTextRenderer().draw(s, l1, i1 - 1, 0);
            this.getTextRenderer().draw(s, l1, i1, k1);
            this.minecraft.profiler.pop();
        }
    }

    public void renderMainHandMessage(Window window) {
        this.minecraft.profiler.push("selectedItemName");
        if (this.mainHandMessageTimer > 0 && this.itemInMainHand != null) {
            String s = this.itemInMainHand.getHoverName();
            if (this.itemInMainHand.hasCustomHoverName()) {
                s = Formatting.ITALIC + s;
            }

            int i = (window.getWidth() - this.getTextRenderer().getWidth(s)) / 2;
            int j = window.getHeight() - 59;
            if (!this.minecraft.interactionManager.hasStatusBars()) {
                j += 14;
            }

            int k = (int)(this.mainHandMessageTimer * 256.0F / 10.0F);
            if (k > 255) {
                k = 255;
            }

            if (k > 0) {
                GlStateManager.pushMatrix();
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                this.getTextRenderer().drawWithShadow(s, i, j, 16777215 + (k << 24));
                GlStateManager.disableBlend();
                GlStateManager.popMatrix();
            }
        }

        this.minecraft.profiler.pop();
    }

    public void renderDemoMessage(Window window) {
        this.minecraft.profiler.push("demo");
        String s = "";
        if (this.minecraft.world.getTime() >= 120500L) {
            s = I18n.translate("demo.demoExpired");
        } else {
            s = I18n.translate("demo.remainingTime", StringUtils.getDurationString((int)(120500L - this.minecraft.world.getTime())));
        }

        int i = this.getTextRenderer().getWidth(s);
        this.getTextRenderer().drawWithShadow(s, window.getWidth() - i - 10, 5.0F, 16777215);
        this.minecraft.profiler.pop();
    }

    protected boolean hasCrosshair() {
        if (this.minecraft.options.debugEnabled && !this.minecraft.player.hasReducedDebugInfo() && !this.minecraft.options.reducedDebugInfo) {
            return false;
        }

        if (this.minecraft.interactionManager.hidesGui()) {
            if (this.minecraft.targetEntity != null) {
                return true;
            }

            if (this.minecraft.crosshairTarget != null && this.minecraft.crosshairTarget.type == HitResult.Type.BLOCK) {
                BlockPos blockpos = this.minecraft.crosshairTarget.getPos();
                if (this.minecraft.world.getBlockEntity(blockpos) instanceof Inventory) {
                    return true;
                }
            }

            return false;
        } else {
            return true;
        }
    }

    public void renderStreamOverlay(Window window) {
        this.streamOverlay.render(window.getWidth() - 10, 10);
    }

    private void renderScoreboardObjective(ScoreboardObjective objective, Window width) {
        Scoreboard scoreboard = objective.getScoreboard();
        Collection<ScoreboardScore> collection = scoreboard.getScores(objective);
        List<ScoreboardScore> list = Lists.newArrayList(Iterables.filter(collection, new Predicate<ScoreboardScore>() {
            public boolean apply(ScoreboardScore scoreboardScore) {
                return scoreboardScore.getOwner() != null && !scoreboardScore.getOwner().startsWith("#");
            }
        }));
        if (list.size() > 15) {
            collection = Lists.newArrayList(Iterables.skip(list, collection.size() - 15));
        } else {
            collection = list;
        }

        int i = this.getTextRenderer().getWidth(objective.getDisplayName());

        for (ScoreboardScore scoreboardscore : collection) {
            Team team = scoreboard.getTeamOfMember(scoreboardscore.getOwner());
            String s = Team.getMemberDisplayName(team, scoreboardscore.getOwner()) + ": " + Formatting.RED + scoreboardscore.get();
            i = Math.max(i, this.getTextRenderer().getWidth(s));
        }

        int j1 = collection.size() * this.getTextRenderer().fontHeight;
        int k1 = width.getHeight() / 2 + j1 / 3;
        int l1 = 3;
        int i2 = width.getWidth() - i - l1;
        int j = 0;

        for (ScoreboardScore scoreboardscore1 : collection) {
            j++;
            Team team1 = scoreboard.getTeamOfMember(scoreboardscore1.getOwner());
            String s1 = Team.getMemberDisplayName(team1, scoreboardscore1.getOwner());
            String s2 = Formatting.RED + "" + scoreboardscore1.get();
            int k = i2;
            int l = k1 - j * this.getTextRenderer().fontHeight;
            int i1 = width.getWidth() - l1 + 2;
            fill(k - 2, l, i1, l + this.getTextRenderer().fontHeight, 1342177280);
            this.getTextRenderer().draw(s1, k, l, 553648127);
            this.getTextRenderer().draw(s2, i1 - this.getTextRenderer().getWidth(s2), l, 553648127);
            if (j == collection.size()) {
                String s3 = objective.getDisplayName();
                fill(k - 2, l - this.getTextRenderer().fontHeight - 1, i1, l - 1, 1610612736);
                fill(k - 2, l - 1, i1, l, 1342177280);
                this.getTextRenderer().draw(s3, k + i / 2 - this.getTextRenderer().getWidth(s3) / 2, l - this.getTextRenderer().fontHeight, 553648127);
            }
        }
    }

    private void renderStatusBars(Window width) {
        if (this.minecraft.getCamera() instanceof PlayerEntity) {
            PlayerEntity playerentity = (PlayerEntity)this.minecraft.getCamera();
            int i = MathHelper.ceil(playerentity.getHealth());
            boolean flag = this.lastHealthAnimationTime > this.ticks && (this.lastHealthAnimationTime - this.ticks) / 3L % 2L == 1L;
            if (i < this.lastHealth && playerentity.invulnerableTimer > 0) {
                this.lastHealthTime = Minecraft.getTime();
                this.lastHealthAnimationTime = this.ticks + 20;
            } else if (i > this.lastHealth && playerentity.invulnerableTimer > 0) {
                this.lastHealthTime = Minecraft.getTime();
                this.lastHealthAnimationTime = this.ticks + 10;
            }

            if (Minecraft.getTime() - this.lastHealthTime > 1000L) {
                this.lastHealth = i;
                this.displayHealth = i;
                this.lastHealthTime = Minecraft.getTime();
            }

            this.lastHealth = i;
            int j = this.displayHealth;
            this.random.setSeed(this.ticks * 312871);
            boolean flag1 = false;
            HungerManager hungermanager = playerentity.getHungerManager();
            int k = hungermanager.getFoodLevel();
            int l = hungermanager.getLastFoodLevel();
            EntityAttributeInstance entityattributeinstance = playerentity.getAttribute(EntityAttributes.MAX_HEALTH);
            int i1 = width.getWidth() / 2 - 91;
            int j1 = width.getWidth() / 2 + 91;
            int k1 = width.getHeight() - 39;
            float f = (float)entityattributeinstance.get();
            float f1 = playerentity.getAbsorption();
            int l1 = MathHelper.ceil((f + f1) / 2.0F / 10.0F);
            int i2 = Math.max(10 - (l1 - 2), 3);
            int j2 = k1 - (l1 - 1) * i2 - 10;
            float f2 = f1;
            int k2 = playerentity.getArmorProtection();
            int l2 = -1;
            if (playerentity.hasStatusEffect(StatusEffect.REGENERATION)) {
                l2 = this.ticks % MathHelper.ceil(f + 5.0F);
            }

            this.minecraft.profiler.push("armor");

            for (int i3 = 0; i3 < 10; i3++) {
                if (k2 > 0) {
                    int j3 = i1 + i3 * 8;
                    if (i3 * 2 + 1 < k2) {
                        this.drawTexture(j3, j2, 34, 9, 9, 9);
                    }

                    if (i3 * 2 + 1 == k2) {
                        this.drawTexture(j3, j2, 25, 9, 9, 9);
                    }

                    if (i3 * 2 + 1 > k2) {
                        this.drawTexture(j3, j2, 16, 9, 9, 9);
                    }
                }
            }

            this.minecraft.profiler.swap("health");

            for (int i6 = MathHelper.ceil((f + f1) / 2.0F) - 1; i6 >= 0; i6--) {
                int j6 = 16;
                if (playerentity.hasStatusEffect(StatusEffect.POISON)) {
                    j6 += 36;
                } else if (playerentity.hasStatusEffect(StatusEffect.WITHER)) {
                    j6 += 72;
                }

                int k3 = 0;
                if (flag) {
                    k3 = 1;
                }

                int l3 = MathHelper.ceil((i6 + 1) / 10.0F) - 1;
                int i4 = i1 + i6 % 10 * 8;
                int j4 = k1 - l3 * i2;
                if (i <= 4) {
                    j4 += this.random.nextInt(2);
                }

                if (i6 == l2) {
                    j4 -= 2;
                }

                int k4 = 0;
                if (playerentity.world.getData().isHardcore()) {
                    k4 = 5;
                }

                this.drawTexture(i4, j4, 16 + k3 * 9, 9 * k4, 9, 9);
                if (flag) {
                    if (i6 * 2 + 1 < j) {
                        this.drawTexture(i4, j4, j6 + 54, 9 * k4, 9, 9);
                    }

                    if (i6 * 2 + 1 == j) {
                        this.drawTexture(i4, j4, j6 + 63, 9 * k4, 9, 9);
                    }
                }

                if (f2 > 0.0F) {
                    if (f2 == f1 && f1 % 2.0F == 1.0F) {
                        this.drawTexture(i4, j4, j6 + 153, 9 * k4, 9, 9);
                    } else {
                        this.drawTexture(i4, j4, j6 + 144, 9 * k4, 9, 9);
                    }

                    f2 -= 2.0F;
                } else {
                    if (i6 * 2 + 1 < i) {
                        this.drawTexture(i4, j4, j6 + 36, 9 * k4, 9, 9);
                    }

                    if (i6 * 2 + 1 == i) {
                        this.drawTexture(i4, j4, j6 + 45, 9 * k4, 9, 9);
                    }
                }
            }

            Entity entity = playerentity.vehicle;
            if (entity == null) {
                this.minecraft.profiler.swap("food");

                for (int k6 = 0; k6 < 10; k6++) {
                    int i7 = k1;
                    int l7 = 16;
                    int j8 = 0;
                    if (playerentity.hasStatusEffect(StatusEffect.HUNGER)) {
                        l7 += 36;
                        j8 = 13;
                    }

                    if (playerentity.getHungerManager().getSaturationLevel() <= 0.0F && this.ticks % (k * 3 + 1) == 0) {
                        i7 += this.random.nextInt(3) - 1;
                    }

                    if (flag1) {
                        j8 = 1;
                    }

                    int i9 = j1 - k6 * 8 - 9;
                    this.drawTexture(i9, i7, 16 + j8 * 9, 27, 9, 9);
                    if (flag1) {
                        if (k6 * 2 + 1 < l) {
                            this.drawTexture(i9, i7, l7 + 54, 27, 9, 9);
                        }

                        if (k6 * 2 + 1 == l) {
                            this.drawTexture(i9, i7, l7 + 63, 27, 9, 9);
                        }
                    }

                    if (k6 * 2 + 1 < k) {
                        this.drawTexture(i9, i7, l7 + 36, 27, 9, 9);
                    }

                    if (k6 * 2 + 1 == k) {
                        this.drawTexture(i9, i7, l7 + 45, 27, 9, 9);
                    }
                }
            } else if (entity instanceof LivingEntity) {
                this.minecraft.profiler.swap("mountHealth");
                LivingEntity livingentity = (LivingEntity)entity;
                int j7 = (int)Math.ceil(livingentity.getHealth());
                float f3 = livingentity.getMaxHealth();
                int k8 = (int)(f3 + 0.5F) / 2;
                if (k8 > 30) {
                    k8 = 30;
                }

                int j9 = k1;

                for (int k9 = 0; k8 > 0; k9 += 20) {
                    int l4 = Math.min(k8, 10);
                    k8 -= l4;

                    for (int i5 = 0; i5 < l4; i5++) {
                        int j5 = 52;
                        int k5 = 0;
                        if (flag1) {
                            k5 = 1;
                        }

                        int l5 = j1 - i5 * 8 - 9;
                        this.drawTexture(l5, j9, j5 + k5 * 9, 9, 9, 9);
                        if (i5 * 2 + 1 + k9 < j7) {
                            this.drawTexture(l5, j9, j5 + 36, 9, 9, 9);
                        }

                        if (i5 * 2 + 1 + k9 == j7) {
                            this.drawTexture(l5, j9, j5 + 45, 9, 9, 9);
                        }
                    }

                    j9 -= 10;
                }
            }

            this.minecraft.profiler.swap("air");
            if (playerentity.isSubmergedIn(Material.WATER)) {
                int l6 = this.minecraft.player.getBreath();
                int k7 = MathHelper.ceil((l6 - 2) * 10.0 / 300.0);
                int i8 = MathHelper.ceil(l6 * 10.0 / 300.0) - k7;

                for (int l8 = 0; l8 < k7 + i8; l8++) {
                    if (l8 < k7) {
                        this.drawTexture(j1 - l8 * 8 - 9, j2, 16, 18, 9, 9);
                    } else {
                        this.drawTexture(j1 - l8 * 8 - 9, j2, 25, 18, 9, 9);
                    }
                }
            }

            this.minecraft.profiler.pop();
        }
    }

    private void renderBossBars() {
        if (BossBar.name != null && BossBar.timer > 0) {
            BossBar.timer--;
            TextRenderer textrenderer = this.minecraft.textRenderer;
            Window window = new Window(this.minecraft);
            int i = window.getWidth();
            int j = 182;
            int k = i / 2 - j / 2;
            int l = (int)(BossBar.health * (j + 1));
            int i1 = 12;
            this.drawTexture(k, i1, 0, 74, j, 5);
            this.drawTexture(k, i1, 0, 74, j, 5);
            if (l > 0) {
                this.drawTexture(k, i1, 0, 79, l, 5);
            }

            String s = BossBar.name;
            this.getTextRenderer().drawWithShadow(s, i / 2 - this.getTextRenderer().getWidth(s) / 2, i1 - 10, 16777215);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.minecraft.getTextureManager().bind(ICONS_LOCATION);
        }
    }

    private void renderPumpkinOverlay(Window width) {
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableAlphaTest();
        this.minecraft.getTextureManager().bind(PUMPKIN_BLUR_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(0.0, width.getHeight(), -90.0).texture(0.0, 1.0).nextVertex();
        bufferbuilder.vertex(width.getWidth(), width.getHeight(), -90.0).texture(1.0, 1.0).nextVertex();
        bufferbuilder.vertex(width.getWidth(), 0.0, -90.0).texture(1.0, 0.0).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, -90.0).texture(0.0, 0.0).nextVertex();
        tesselator.end();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepthTest();
        GlStateManager.enableAlphaTest();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderVignette(float brightnessAtEyes, Window width) {
        brightnessAtEyes = 1.0F - brightnessAtEyes;
        brightnessAtEyes = MathHelper.clamp(brightnessAtEyes, 0.0F, 1.0F);
        WorldBorder worldborder = this.minecraft.world.getWorldBorder();
        float f = (float)worldborder.getDistanceFrom(this.minecraft.player);
        double d0 = Math.min(
            worldborder.getSizeChangeSpeed() * worldborder.getWarningTime() * 1000.0, Math.abs(worldborder.getSizeLerpTarget() - worldborder.getLerpSize())
        );
        double d1 = Math.max(worldborder.getWarningDistance(), d0);
        if (f < d1) {
            f = 1.0F - (float)(f / d1);
        } else {
            f = 0.0F;
        }

        this.vignetteBrightness = (float)(this.vignetteBrightness + (brightnessAtEyes - this.vignetteBrightness) * 0.01);
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.blendFuncSeparate(0, 769, 1, 0);
        if (f > 0.0F) {
            GlStateManager.color4f(0.0F, f, f, 1.0F);
        } else {
            GlStateManager.color4f(this.vignetteBrightness, this.vignetteBrightness, this.vignetteBrightness, 1.0F);
        }

        this.minecraft.getTextureManager().bind(VIGNETTE_LOCATION);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(0.0, width.getHeight(), -90.0).texture(0.0, 1.0).nextVertex();
        bufferbuilder.vertex(width.getWidth(), width.getHeight(), -90.0).texture(1.0, 1.0).nextVertex();
        bufferbuilder.vertex(width.getWidth(), 0.0, -90.0).texture(1.0, 0.0).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, -90.0).texture(0.0, 0.0).nextVertex();
        tesselator.end();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepthTest();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
    }

    private void renderNauseaOverlay(float portalTime, Window width) {
        if (portalTime < 1.0F) {
            portalTime *= portalTime;
            portalTime *= portalTime;
            portalTime = portalTime * 0.8F + 0.2F;
        }

        GlStateManager.disableAlphaTest();
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, portalTime);
        this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
        TextureAtlasSprite textureatlassprite = this.minecraft.getBlockRenderDispatcher().getModelShaper().getParticleIcon(Blocks.NETHER_PORTAL.defaultState());
        float f = textureatlassprite.getUMin();
        float f1 = textureatlassprite.getVMin();
        float f2 = textureatlassprite.getUMax();
        float f3 = textureatlassprite.getVMax();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(0.0, width.getHeight(), -90.0).texture(f, f3).nextVertex();
        bufferbuilder.vertex(width.getWidth(), width.getHeight(), -90.0).texture(f2, f3).nextVertex();
        bufferbuilder.vertex(width.getWidth(), 0.0, -90.0).texture(f2, f1).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, -90.0).texture(f, f1).nextVertex();
        tesselator.end();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepthTest();
        GlStateManager.enableAlphaTest();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderItemSlot(int slot, int x, int z, float tickDelta, PlayerEntity player) {
        ItemStack itemstack = player.inventory.items[slot];
        if (itemstack != null) {
            float f = itemstack.popAnimationTime - tickDelta;
            if (f > 0.0F) {
                GlStateManager.pushMatrix();
                float f1 = 1.0F + f / 5.0F;
                GlStateManager.translatef(x + 8, z + 12, 0.0F);
                GlStateManager.scalef(1.0F / f1, (f1 + 1.0F) / 2.0F, 1.0F);
                GlStateManager.translatef(-(x + 8), -(z + 12), 0.0F);
            }

            this.itemRenderer.renderGuiItem(itemstack, x, z);
            if (f > 0.0F) {
                GlStateManager.popMatrix();
            }

            this.itemRenderer.renderGuiItemDecoration(this.minecraft.textRenderer, itemstack, x, z);
        }
    }

    public void tick() {
        if (this.overlayMessageCooldown > 0) {
            this.overlayMessageCooldown--;
        }

        if (this.titleTime > 0) {
            this.titleTime--;
            if (this.titleTime <= 0) {
                this.title = "";
                this.subtitle = "";
            }
        }

        this.ticks++;
        this.streamOverlay.m_6080915();
        if (this.minecraft.player != null) {
            ItemStack itemstack = this.minecraft.player.inventory.getSelectedItem();
            if (itemstack == null) {
                this.mainHandMessageTimer = 0;
            } else if (this.itemInMainHand != null
                && itemstack.getItem() == this.itemInMainHand.getItem()
                && ItemStack.matchesNbt(itemstack, this.itemInMainHand)
                && (itemstack.isDamageable() || itemstack.getMetadata() == this.itemInMainHand.getMetadata())) {
                if (this.mainHandMessageTimer > 0) {
                    this.mainHandMessageTimer--;
                }
            } else {
                this.mainHandMessageTimer = 40;
            }

            this.itemInMainHand = itemstack;
        }
    }

    public void setRecordPlayingOverlay(String record) {
        this.setOverlayMessage(I18n.translate("record.nowPlaying", record), true);
    }

    public void setOverlayMessage(String overlayMessage, boolean tinted) {
        this.overlayMessage = overlayMessage;
        this.overlayMessageCooldown = 60;
        this.overlayMessageTinted = tinted;
    }

    public void setTitles(String title, String subtitle, int titleFadeInTime, int titleDuration, int titleFadeOutTime) {
        if (title == null && subtitle == null && titleFadeInTime < 0 && titleDuration < 0 && titleFadeOutTime < 0) {
            this.title = "";
            this.subtitle = "";
            this.titleTime = 0;
        } else if (title != null) {
            this.title = title;
            this.titleTime = this.titleFadeInTime + this.titleDuration + this.titleFadeOutTime;
        } else if (subtitle != null) {
            this.subtitle = subtitle;
        } else {
            if (titleFadeInTime >= 0) {
                this.titleFadeInTime = titleFadeInTime;
            }

            if (titleDuration >= 0) {
                this.titleDuration = titleDuration;
            }

            if (titleFadeOutTime >= 0) {
                this.titleFadeOutTime = titleFadeOutTime;
            }

            if (this.titleTime > 0) {
                this.titleTime = this.titleFadeInTime + this.titleDuration + this.titleFadeOutTime;
            }
        }
    }

    public void setOverlayMessage(Text message, boolean tinted) {
        this.setOverlayMessage(message.getString(), tinted);
    }

    public ChatGui getChat() {
        return this.chat;
    }

    public int getTicks() {
        return this.ticks;
    }

    public TextRenderer getTextRenderer() {
        return this.minecraft.textRenderer;
    }

    public SpectatorGui getSpectatorGui() {
        return this.spectatorGui;
    }

    public PlayerTabOverlay getPlayerTabOverlay() {
        return this.playerTabOverlay;
    }

    public void resetPlayerTabOverlay() {
        this.playerTabOverlay.reset();
    }
}
