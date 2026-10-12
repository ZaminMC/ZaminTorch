package net.minecraft.client.gui.screen.menu;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.stat.PlayerStats;
import net.minecraft.stat.achievement.AchievementStat;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;

public class AchievementsScreen extends Screen implements StatsListener {
    private static final int MIN_COLUMN = Achievements.minColumn * 24 - 112;
    private static final int MIN_ROW = Achievements.minRow * 24 - 112;
    private static final int MAX_COLUMN = Achievements.maxColumn * 24 - 77;
    private static final int MAX_ROW = Achievements.maxRow * 24 - 77;
    private static final Identifier BACKGROUND_LOCATION = new Identifier("textures/gui/achievement/achievement_background.png");
    protected Screen parent;
    protected int iconWidth = 256;
    protected int iconHeight = 202;
    protected int prevMouseX;
    protected int prevMouseY;
    protected float scale = 1.0F;
    protected double mouseX;
    protected double mouseY;
    protected double scaledMouseDx;
    protected double scaledMouseDy;
    protected double scrollX;
    protected double scrollY;
    private int scroll;
    private PlayerStats stats;
    private boolean downloading = true;

    public AchievementsScreen(Screen parent, PlayerStats statHandler) {
        this.parent = parent;
        this.stats = statHandler;
        int i = 141;
        int j = 141;
        this.mouseX = this.scaledMouseDx = this.scrollX = Achievements.OPEN_INVENTORY.column * 24 - i / 2 - 12;
        this.mouseY = this.scaledMouseDy = this.scrollY = Achievements.OPEN_INVENTORY.row * 24 - j / 2;
    }

    @Override
    public void init() {
        this.minecraft.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.REQUEST_STATS));
        this.buttons.clear();
        this.buttons.add(new OptionButtonWidget(1, this.width / 2 + 24, this.height / 2 + 74, 80, 20, I18n.translate("gui.done")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!this.downloading) {
            if (button.id == 1) {
                this.minecraft.openScreen(this.parent);
            }
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == this.minecraft.options.inventoryKey.getKeyCode()) {
            this.minecraft.openScreen(null);
            this.minecraft.lockMouse();
        } else {
            super.keyPressed(chr, key);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        if (this.downloading) {
            this.renderBackground();
            this.drawCenteredString(this.textRenderer, I18n.translate("multiplayer.downloadingStats"), this.width / 2, this.height / 2, 16777215);
            this.drawCenteredString(
                this.textRenderer,
                PROGRESS_BAR_STAGES[(int)(Minecraft.getTime() / 150L % PROGRESS_BAR_STAGES.length)],
                this.width / 2,
                this.height / 2 + this.textRenderer.fontHeight * 2,
                16777215
            );
        } else {
            if (Mouse.isButtonDown(0)) {
                int i = (this.width - this.iconWidth) / 2;
                int j = (this.height - this.iconHeight) / 2;
                int k = i + 8;
                int l = j + 17;
                if ((this.scroll == 0 || this.scroll == 1) && mouseX >= k && mouseX < k + 224 && mouseY >= l && mouseY < l + 155) {
                    if (this.scroll == 0) {
                        this.scroll = 1;
                    } else {
                        this.scaledMouseDx = this.scaledMouseDx - (mouseX - this.prevMouseX) * this.scale;
                        this.scaledMouseDy = this.scaledMouseDy - (mouseY - this.prevMouseY) * this.scale;
                        this.scrollX = this.mouseX = this.scaledMouseDx;
                        this.scrollY = this.mouseY = this.scaledMouseDy;
                    }

                    this.prevMouseX = mouseX;
                    this.prevMouseY = mouseY;
                }
            } else {
                this.scroll = 0;
            }

            int i1 = Mouse.getDWheel();
            float f3 = this.scale;
            if (i1 < 0) {
                this.scale += 0.25F;
            } else if (i1 > 0) {
                this.scale -= 0.25F;
            }

            this.scale = MathHelper.clamp(this.scale, 1.0F, 2.0F);
            if (this.scale != f3) {
                float f4 = f3 - this.scale;
                float f5 = f3 * this.iconWidth;
                float f = f3 * this.iconHeight;
                float f1 = this.scale * this.iconWidth;
                float f2 = this.scale * this.iconHeight;
                this.scaledMouseDx -= (f1 - f5) * 0.5F;
                this.scaledMouseDy -= (f2 - f) * 0.5F;
                this.scrollX = this.mouseX = this.scaledMouseDx;
                this.scrollY = this.mouseY = this.scaledMouseDy;
            }

            if (this.scrollX < MIN_COLUMN) {
                this.scrollX = MIN_COLUMN;
            }

            if (this.scrollY < MIN_ROW) {
                this.scrollY = MIN_ROW;
            }

            if (this.scrollX >= MAX_COLUMN) {
                this.scrollX = MAX_COLUMN - 1;
            }

            if (this.scrollY >= MAX_ROW) {
                this.scrollY = MAX_ROW - 1;
            }

            this.renderBackground();
            this.renderIcons(mouseX, mouseY, tickDelta);
            GlStateManager.disableLighting();
            GlStateManager.disableDepthTest();
            this.setTitle();
            GlStateManager.enableLighting();
            GlStateManager.enableDepthTest();
        }
    }

    @Override
    public void onStatsReady() {
        if (this.downloading) {
            this.downloading = false;
        }
    }

    @Override
    public void tick() {
        if (!this.downloading) {
            this.mouseX = this.scaledMouseDx;
            this.mouseY = this.scaledMouseDy;
            double d0 = this.scrollX - this.scaledMouseDx;
            double d1 = this.scrollY - this.scaledMouseDy;
            if (d0 * d0 + d1 * d1 < 4.0) {
                this.scaledMouseDx += d0;
                this.scaledMouseDy += d1;
            } else {
                this.scaledMouseDx += d0 * 0.85;
                this.scaledMouseDy += d1 * 0.85;
            }
        }
    }

    protected void setTitle() {
        int i = (this.width - this.iconWidth) / 2;
        int j = (this.height - this.iconHeight) / 2;
        this.textRenderer.draw(I18n.translate("gui.achievements"), i + 15, j + 5, 4210752);
    }

    protected void renderIcons(int mouseX, int mouseY, float tickdelta) {
        int i = MathHelper.floor(this.mouseX + (this.scaledMouseDx - this.mouseX) * tickdelta);
        int j = MathHelper.floor(this.mouseY + (this.scaledMouseDy - this.mouseY) * tickdelta);
        if (i < MIN_COLUMN) {
            i = MIN_COLUMN;
        }

        if (j < MIN_ROW) {
            j = MIN_ROW;
        }

        if (i >= MAX_COLUMN) {
            i = MAX_COLUMN - 1;
        }

        if (j >= MAX_ROW) {
            j = MAX_ROW - 1;
        }

        int k = (this.width - this.iconWidth) / 2;
        int l = (this.height - this.iconHeight) / 2;
        int i1 = k + 16;
        int j1 = l + 17;
        this.drawOffset = 0.0F;
        GlStateManager.depthFunc(518);
        GlStateManager.pushMatrix();
        GlStateManager.translatef(i1, j1, -200.0F);
        GlStateManager.scalef(1.0F / this.scale, 1.0F / this.scale, 0.0F);
        GlStateManager.enableTexture();
        GlStateManager.disableLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();
        int k1 = i + 288 >> 4;
        int l1 = j + 288 >> 4;
        int i2 = (i + 288) % 16;
        int j2 = (j + 288) % 16;
        int k2 = 4;
        int l2 = 8;
        int i3 = 10;
        int j3 = 22;
        int k3 = 37;
        Random random = new Random();
        float f = 16.0F / this.scale;
        float f1 = 16.0F / this.scale;

        for (int l3 = 0; l3 * f - j2 < 155.0F; l3++) {
            float f2 = 0.6F - (l1 + l3) / 25.0F * 0.3F;
            GlStateManager.color4f(f2, f2, f2, 1.0F);

            for (int i4 = 0; i4 * f1 - i2 < 224.0F; i4++) {
                random.setSeed(this.minecraft.getSession().getUuid().hashCode() + k1 + i4 + (l1 + l3) * 16);
                int j4 = random.nextInt(1 + l1 + l3) + (l1 + l3) / 2;
                TextureAtlasSprite textureatlassprite = this.getParticleIcon(Blocks.SAND);
                if (j4 > 37 || l1 + l3 == 35) {
                    Block block = Blocks.BEDROCK;
                    textureatlassprite = this.getParticleIcon(block);
                } else if (j4 == 22) {
                    if (random.nextInt(2) == 0) {
                        textureatlassprite = this.getParticleIcon(Blocks.DIAMOND_ORE);
                    } else {
                        textureatlassprite = this.getParticleIcon(Blocks.REDSTONE_ORE);
                    }
                } else if (j4 == 10) {
                    textureatlassprite = this.getParticleIcon(Blocks.IRON_ORE);
                } else if (j4 == 8) {
                    textureatlassprite = this.getParticleIcon(Blocks.COAL_ORE);
                } else if (j4 > 4) {
                    textureatlassprite = this.getParticleIcon(Blocks.STONE);
                } else if (j4 > 0) {
                    textureatlassprite = this.getParticleIcon(Blocks.DIRT);
                }

                this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
                this.drawSprite(i4 * 16 - i2, l3 * 16 - j2, textureatlassprite, 16, 16);
            }
        }

        GlStateManager.enableDepthTest();
        GlStateManager.depthFunc(515);
        this.minecraft.getTextureManager().bind(BACKGROUND_LOCATION);

        for (int j5 = 0; j5 < Achievements.ALL.size(); j5++) {
            AchievementStat achievementstat1 = Achievements.ALL.get(j5);
            if (achievementstat1.parent != null) {
                int k5 = achievementstat1.column * 24 - i + 11;
                int l5 = achievementstat1.row * 24 - j + 11;
                int j6 = achievementstat1.parent.column * 24 - i + 11;
                int k6 = achievementstat1.parent.row * 24 - j + 11;
                boolean flag = this.stats.hasAchievement(achievementstat1);
                boolean flag1 = this.stats.hasParentAchievement(achievementstat1);
                int k4 = this.stats.get(achievementstat1);
                if (k4 <= 4) {
                    int l4 = -16777216;
                    if (flag) {
                        l4 = -6250336;
                    } else if (flag1) {
                        l4 = -16711936;
                    }

                    this.drawHorizontalLine(k5, j6, l5, l4);
                    this.drawVerticalLine(j6, l5, k6, l4);
                    if (k5 > j6) {
                        this.drawTexture(k5 - 11 - 7, l5 - 5, 114, 234, 7, 11);
                    } else if (k5 < j6) {
                        this.drawTexture(k5 + 11, l5 - 5, 107, 234, 7, 11);
                    } else if (l5 > k6) {
                        this.drawTexture(k5 - 5, l5 - 11 - 7, 96, 234, 11, 7);
                    } else if (l5 < k6) {
                        this.drawTexture(k5 - 5, l5 + 11, 96, 241, 11, 7);
                    }
                }
            }
        }

        AchievementStat achievementstat = null;
        float f3 = (mouseX - i1) * this.scale;
        float f4 = (mouseY - j1) * this.scale;
        Lighting.turnOnGui();
        GlStateManager.disableLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();

        for (int i6 = 0; i6 < Achievements.ALL.size(); i6++) {
            AchievementStat achievementstat2 = Achievements.ALL.get(i6);
            int l6 = achievementstat2.column * 24 - i;
            int j7 = achievementstat2.row * 24 - j;
            if (l6 >= -24 && j7 >= -24 && l6 <= 224.0F * this.scale && j7 <= 155.0F * this.scale) {
                int l7 = this.stats.get(achievementstat2);
                if (this.stats.hasAchievement(achievementstat2)) {
                    float f5 = 0.75F;
                    GlStateManager.color4f(f5, f5, f5, 1.0F);
                } else if (this.stats.hasParentAchievement(achievementstat2)) {
                    float f6 = 1.0F;
                    GlStateManager.color4f(f6, f6, f6, 1.0F);
                } else if (l7 < 3) {
                    float f7 = 0.3F;
                    GlStateManager.color4f(f7, f7, f7, 1.0F);
                } else if (l7 == 3) {
                    float f8 = 0.2F;
                    GlStateManager.color4f(f8, f8, f8, 1.0F);
                } else {
                    if (l7 != 4) {
                        continue;
                    }

                    float f9 = 0.1F;
                    GlStateManager.color4f(f9, f9, f9, 1.0F);
                }

                this.minecraft.getTextureManager().bind(BACKGROUND_LOCATION);
                if (achievementstat2.isChallenge()) {
                    this.drawTexture(l6 - 2, j7 - 2, 26, 202, 26, 26);
                } else {
                    this.drawTexture(l6 - 2, j7 - 2, 0, 202, 26, 26);
                }

                if (!this.stats.hasParentAchievement(achievementstat2)) {
                    float f10 = 0.1F;
                    GlStateManager.color4f(f10, f10, f10, 1.0F);
                    this.itemRenderer.setUseCustomDisplayColor(false);
                }

                GlStateManager.enableLighting();
                GlStateManager.enableCull();
                this.itemRenderer.renderGuiItem(achievementstat2.icon, l6 + 3, j7 + 3);
                GlStateManager.blendFunc(770, 771);
                GlStateManager.disableLighting();
                if (!this.stats.hasParentAchievement(achievementstat2)) {
                    this.itemRenderer.setUseCustomDisplayColor(true);
                }

                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                if (f3 >= l6 && f3 <= l6 + 22 && f4 >= j7 && f4 <= j7 + 22) {
                    achievementstat = achievementstat2;
                }
            }
        }

        GlStateManager.disableDepthTest();
        GlStateManager.enableBlend();
        GlStateManager.popMatrix();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(BACKGROUND_LOCATION);
        this.drawTexture(k, l, 0, 0, this.iconWidth, this.iconHeight);
        this.drawOffset = 0.0F;
        GlStateManager.depthFunc(515);
        GlStateManager.disableDepthTest();
        GlStateManager.enableTexture();
        super.render(mouseX, mouseY, tickdelta);
        if (achievementstat != null) {
            String s = achievementstat.getDecoratedName().getString();
            String s1 = achievementstat.getDescription();
            int i7 = mouseX + 12;
            int k7 = mouseY - 4;
            int i8 = this.stats.get(achievementstat);
            if (this.stats.hasParentAchievement(achievementstat)) {
                int j8 = Math.max(this.textRenderer.getWidth(s), 120);
                int i9 = this.textRenderer.splitAndGetHeight(s1, j8);
                if (this.stats.hasAchievement(achievementstat)) {
                    i9 += 12;
                }

                this.fillGradient(i7 - 3, k7 - 3, i7 + j8 + 3, k7 + i9 + 3 + 12, -1073741824, -1073741824);
                this.textRenderer.splitAndDraw(s1, i7, k7 + 12, j8, -6250336);
                if (this.stats.hasAchievement(achievementstat)) {
                    this.textRenderer.drawWithShadow(I18n.translate("achievement.taken"), i7, k7 + i9 + 4, -7302913);
                }
            } else if (i8 == 3) {
                s = I18n.translate("achievement.unknown");
                int k8 = Math.max(this.textRenderer.getWidth(s), 120);
                String s2 = new TranslatableText("achievement.requires", achievementstat.parent.getDecoratedName()).getString();
                int i5 = this.textRenderer.splitAndGetHeight(s2, k8);
                this.fillGradient(i7 - 3, k7 - 3, i7 + k8 + 3, k7 + i5 + 12 + 3, -1073741824, -1073741824);
                this.textRenderer.splitAndDraw(s2, i7, k7 + 12, k8, -9416624);
            } else if (i8 < 3) {
                int l8 = Math.max(this.textRenderer.getWidth(s), 120);
                String s3 = new TranslatableText("achievement.requires", achievementstat.parent.getDecoratedName()).getString();
                int j9 = this.textRenderer.splitAndGetHeight(s3, l8);
                this.fillGradient(i7 - 3, k7 - 3, i7 + l8 + 3, k7 + j9 + 12 + 3, -1073741824, -1073741824);
                this.textRenderer.splitAndDraw(s3, i7, k7 + 12, l8, -9416624);
            } else {
                s = null;
            }

            if (s != null) {
                this.textRenderer
                    .drawWithShadow(
                        s,
                        i7,
                        k7,
                        this.stats.hasParentAchievement(achievementstat)
                            ? (achievementstat.isChallenge() ? -128 : -1)
                            : (achievementstat.isChallenge() ? -8355776 : -8355712)
                    );
            }
        }

        GlStateManager.enableDepthTest();
        GlStateManager.enableLighting();
        Lighting.turnOff();
    }

    private TextureAtlasSprite getParticleIcon(Block block) {
        return Minecraft.getInstance().getBlockRenderDispatcher().getModelShaper().getParticleIcon(block.defaultState());
    }

    @Override
    public boolean shouldPauseGame() {
        return !this.downloading;
    }
}
