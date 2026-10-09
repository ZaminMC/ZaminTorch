package net.minecraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.resource.Identifier;
import net.minecraft.stat.achievement.AchievementStat;

public class ToastGui extends GuiElement {
    private static final Identifier BACKGROUND_LOCATION = new Identifier("textures/gui/achievement/achievement_background.png");
    private Minecraft minecraft;
    private int width;
    private int height;
    private String title;
    private String description;
    private AchievementStat achievement;
    private long startTime;
    private ItemRenderer itemRenderer;
    private boolean tutorial;

    public ToastGui(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.itemRenderer = minecraft.getItemRenderer();
    }

    public void set(AchievementStat achievement) {
        this.title = I18n.translate("achievement.get");
        this.description = achievement.getDecoratedName().getString();
        this.startTime = Minecraft.getTime();
        this.achievement = achievement;
        this.tutorial = false;
    }

    public void setTutorial(AchievementStat achievement) {
        this.title = achievement.getDecoratedName().getString();
        this.description = achievement.getDescription();
        this.startTime = Minecraft.getTime() + 2500L;
        this.achievement = achievement;
        this.tutorial = true;
    }

    private void setupToastsState() {
        GlStateManager.viewport(0, 0, this.minecraft.width, this.minecraft.height);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        this.width = this.minecraft.width;
        this.height = this.minecraft.height;
        Window window = new Window(this.minecraft);
        this.width = window.getWidth();
        this.height = window.getHeight();
        GlStateManager.clear(256);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0, this.width, this.height, 0.0, 1000.0, 3000.0);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translatef(0.0F, 0.0F, -2000.0F);
    }

    public void render() {
        if (this.achievement != null && this.startTime != 0L && Minecraft.getInstance().player != null) {
            double d0 = (Minecraft.getTime() - this.startTime) / 3000.0;
            if (!this.tutorial) {
                if (d0 < 0.0 || d0 > 1.0) {
                    this.startTime = 0L;
                    return;
                }
            } else if (d0 > 0.5) {
                d0 = 0.5;
            }

            this.setupToastsState();
            GlStateManager.disableDepthTest();
            GlStateManager.depthMask(false);
            double d1 = d0 * 2.0;
            if (d1 > 1.0) {
                d1 = 2.0 - d1;
            }

            d1 *= 4.0;
            d1 = 1.0 - d1;
            if (d1 < 0.0) {
                d1 = 0.0;
            }

            d1 *= d1;
            d1 *= d1;
            int i = this.width - 160;
            int j = 0 - (int)(d1 * 36.0);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture();
            this.minecraft.getTextureManager().bind(BACKGROUND_LOCATION);
            GlStateManager.disableLighting();
            this.drawTexture(i, j, 96, 202, 160, 32);
            if (this.tutorial) {
                this.minecraft.textRenderer.splitAndDraw(this.description, i + 30, j + 7, 120, -1);
            } else {
                this.minecraft.textRenderer.draw(this.title, i + 30, j + 7, -256);
                this.minecraft.textRenderer.draw(this.description, i + 30, j + 18, -1);
            }

            Lighting.turnOnGui();
            GlStateManager.disableLighting();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableColorMaterial();
            GlStateManager.enableLighting();
            this.itemRenderer.renderGuiItem(this.achievement.icon, i + 8, j + 8);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepthTest();
        }
    }

    public void clear() {
        this.achievement = null;
        this.startTime = 0L;
    }
}
