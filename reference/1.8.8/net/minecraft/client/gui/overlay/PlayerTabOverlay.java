package net.minecraft.client.gui.overlay;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.mojang.authlib.GameProfile;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldSettings;

public class PlayerTabOverlay extends GuiElement {
    private static final Ordering<PlayerInfo> PLAYER_ORDERING = Ordering.from(new PlayerTabOverlay.PlayerInfoComparator());
    private final Minecraft minecraft;
    private final GameGui gui;
    private Text footer;
    private Text header;
    private long visibility;
    private boolean visible;

    public PlayerTabOverlay(Minecraft minecraft, GameGui gui) {
        this.minecraft = minecraft;
        this.gui = gui;
    }

    public String getDisplayName(PlayerInfo player) {
        return player.getDisplayName() != null
            ? player.getDisplayName().getFormattedString()
            : Team.getMemberDisplayName(player.getTeam(), player.getProfile().getName());
    }

    public void setVisible(boolean visible) {
        if (visible && !this.visible) {
            this.visibility = Minecraft.getTime();
        }

        this.visible = visible;
    }

    public void render(int width, Scoreboard scoreboard, ScoreboardObjective displayObjective) {
        ClientPlayNetworkHandler clientplaynetworkhandler = this.minecraft.player.networkHandler;
        List<PlayerInfo> list = PLAYER_ORDERING.sortedCopy(clientplaynetworkhandler.getOnlinePlayers());
        int i = 0;
        int j = 0;

        for (PlayerInfo playerinfo : list) {
            int k = this.minecraft.textRenderer.getWidth(this.getDisplayName(playerinfo));
            i = Math.max(i, k);
            if (displayObjective != null && displayObjective.getRenderType() != ScoreboardCriterion.RenderType.HEARTS) {
                k = this.minecraft.textRenderer.getWidth(" " + scoreboard.getScore(playerinfo.getProfile().getName(), displayObjective).get());
                j = Math.max(j, k);
            }
        }

        list = list.subList(0, Math.min(list.size(), 80));
        int l3 = list.size();
        int i4 = l3;

        int j4;
        for (j4 = 1; i4 > 20; i4 = (l3 + j4 - 1) / j4) {
            j4++;
        }

        boolean flag = this.minecraft.isIntegratedServerRunning() || this.minecraft.getNetworkHandler().getConnection().isEncrypted();
        int l;
        if (displayObjective != null) {
            if (displayObjective.getRenderType() == ScoreboardCriterion.RenderType.HEARTS) {
                l = 90;
            } else {
                l = j;
            }
        } else {
            l = 0;
        }

        int i1 = Math.min(j4 * ((flag ? 9 : 0) + i + l + 13), width - 50) / j4;
        int j1 = width / 2 - (i1 * j4 + (j4 - 1) * 5) / 2;
        int k1 = 10;
        int l1 = i1 * j4 + (j4 - 1) * 5;
        List<String> list1 = null;
        List<String> list2 = null;
        if (this.header != null) {
            list1 = this.minecraft.textRenderer.split(this.header.getFormattedString(), width - 50);

            for (String s : list1) {
                l1 = Math.max(l1, this.minecraft.textRenderer.getWidth(s));
            }
        }

        if (this.footer != null) {
            list2 = this.minecraft.textRenderer.split(this.footer.getFormattedString(), width - 50);

            for (String s2 : list2) {
                l1 = Math.max(l1, this.minecraft.textRenderer.getWidth(s2));
            }
        }

        if (list1 != null) {
            fill(width / 2 - l1 / 2 - 1, k1 - 1, width / 2 + l1 / 2 + 1, k1 + list1.size() * this.minecraft.textRenderer.fontHeight, Integer.MIN_VALUE);

            for (String s3 : list1) {
                int i2 = this.minecraft.textRenderer.getWidth(s3);
                this.minecraft.textRenderer.drawWithShadow(s3, width / 2 - i2 / 2, k1, -1);
                k1 += this.minecraft.textRenderer.fontHeight;
            }

            k1++;
        }

        fill(width / 2 - l1 / 2 - 1, k1 - 1, width / 2 + l1 / 2 + 1, k1 + i4 * 9, Integer.MIN_VALUE);

        for (int k4 = 0; k4 < l3; k4++) {
            int l4 = k4 / i4;
            int i5 = k4 % i4;
            int j2 = j1 + l4 * i1 + l4 * 5;
            int k2 = k1 + i5 * 9;
            fill(j2, k2, j2 + i1, k2 + 8, 553648127);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableAlphaTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            if (k4 < list.size()) {
                PlayerInfo playerinfo1 = list.get(k4);
                String s1 = this.getDisplayName(playerinfo1);
                GameProfile gameprofile = playerinfo1.getProfile();
                if (flag) {
                    PlayerEntity playerentity = this.minecraft.world.getPlayer(gameprofile.getId());
                    boolean flag1 = playerentity != null
                        && playerentity.isModelPartVisible(PlayerModelPart.CAPE)
                        && (gameprofile.getName().equals("Dinnerbone") || gameprofile.getName().equals("Grumm"));
                    this.minecraft.getTextureManager().bind(playerinfo1.getSkinTexture());
                    int l2 = 8 + (flag1 ? 8 : 0);
                    int i3 = 8 * (flag1 ? -1 : 1);
                    GuiElement.drawTexture(j2, k2, 8.0F, l2, 8, i3, 8, 8, 64.0F, 64.0F);
                    if (playerentity != null && playerentity.isModelPartVisible(PlayerModelPart.HAT)) {
                        int j3 = 8 + (flag1 ? 8 : 0);
                        int k3 = 8 * (flag1 ? -1 : 1);
                        GuiElement.drawTexture(j2, k2, 40.0F, j3, 8, k3, 8, 8, 64.0F, 64.0F);
                    }

                    j2 += 9;
                }

                if (playerinfo1.getGameMode() == WorldSettings.GameMode.SPECTATOR) {
                    s1 = Formatting.ITALIC + s1;
                    this.minecraft.textRenderer.drawWithShadow(s1, j2, k2, -1862270977);
                } else {
                    this.minecraft.textRenderer.drawWithShadow(s1, j2, k2, -1);
                }

                if (displayObjective != null && playerinfo1.getGameMode() != WorldSettings.GameMode.SPECTATOR) {
                    int k5 = j2 + i + 1;
                    int l5 = k5 + l;
                    if (l5 - k5 > 5) {
                        this.renderDisplayScore(displayObjective, k2, gameprofile.getName(), k5, l5, playerinfo1);
                    }
                }

                this.renderPing(i1, j2 - (flag ? 9 : 0), k2, playerinfo1);
            }
        }

        if (list2 != null) {
            k1 += i4 * 9 + 1;
            fill(width / 2 - l1 / 2 - 1, k1 - 1, width / 2 + l1 / 2 + 1, k1 + list2.size() * this.minecraft.textRenderer.fontHeight, Integer.MIN_VALUE);

            for (String s4 : list2) {
                int j5 = this.minecraft.textRenderer.getWidth(s4);
                this.minecraft.textRenderer.drawWithShadow(s4, width / 2 - j5 / 2, k1, -1);
                k1 += this.minecraft.textRenderer.fontHeight;
            }
        }
    }

    protected void renderPing(int width, int x, int y, PlayerInfo player) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(ICONS_LOCATION);
        int i = 0;
        int j = 0;
        byte b0;
        if (player.getPing() < 0) {
            b0 = 5;
        } else if (player.getPing() < 150) {
            b0 = 0;
        } else if (player.getPing() < 300) {
            b0 = 1;
        } else if (player.getPing() < 600) {
            b0 = 2;
        } else if (player.getPing() < 1000) {
            b0 = 3;
        } else {
            b0 = 4;
        }

        this.drawOffset += 100.0F;
        this.drawTexture(x + width - 11, y, 0 + i * 10, 176 + b0 * 8, 10, 8);
        this.drawOffset -= 100.0F;
    }

    private void renderDisplayScore(ScoreboardObjective displayObjective, int width, String owner, int x, int y, PlayerInfo player) {
        int i = displayObjective.getScoreboard().getScore(owner, displayObjective).get();
        if (displayObjective.getRenderType() == ScoreboardCriterion.RenderType.HEARTS) {
            this.minecraft.getTextureManager().bind(ICONS_LOCATION);
            if (this.visibility == player.getRenderVisibility()) {
                if (i < player.getLastHealth()) {
                    player.setLastHealthUpdateTime(Minecraft.getTime());
                    player.setLastHealthAnimationTime(this.gui.getTicks() + 20);
                } else if (i > player.getLastHealth()) {
                    player.setLastHealthUpdateTime(Minecraft.getTime());
                    player.setLastHealthAnimationTime(this.gui.getTicks() + 10);
                }
            }

            if (Minecraft.getTime() - player.getLastHealthUpdateTime() > 1000L || this.visibility != player.getRenderVisibility()) {
                player.setLastHealth(i);
                player.setHealth(i);
                player.setLastHealthUpdateTime(Minecraft.getTime());
            }

            player.setRenderVisibility(this.visibility);
            player.setLastHealth(i);
            int j = MathHelper.ceil(Math.max(i, player.getHealth()) / 2.0F);
            int k = Math.max(MathHelper.ceil(i / 2), Math.max(MathHelper.ceil(player.getHealth() / 2), 10));
            boolean flag = player.getLastHealthAnimationTime() > this.gui.getTicks()
                && (player.getLastHealthAnimationTime() - this.gui.getTicks()) / 3L % 2L == 1L;
            if (j > 0) {
                float f = Math.min((float)(y - x - 4) / k, 9.0F);
                if (f > 3.0F) {
                    for (int l = j; l < k; l++) {
                        this.drawTexture(x + l * f, width, flag ? 25 : 16, 0, 9, 9);
                    }

                    for (int j1 = 0; j1 < j; j1++) {
                        this.drawTexture(x + j1 * f, width, flag ? 25 : 16, 0, 9, 9);
                        if (flag) {
                            if (j1 * 2 + 1 < player.getHealth()) {
                                this.drawTexture(x + j1 * f, width, 70, 0, 9, 9);
                            }

                            if (j1 * 2 + 1 == player.getHealth()) {
                                this.drawTexture(x + j1 * f, width, 79, 0, 9, 9);
                            }
                        }

                        if (j1 * 2 + 1 < i) {
                            this.drawTexture(x + j1 * f, width, j1 >= 10 ? 160 : 52, 0, 9, 9);
                        }

                        if (j1 * 2 + 1 == i) {
                            this.drawTexture(x + j1 * f, width, j1 >= 10 ? 169 : 61, 0, 9, 9);
                        }
                    }
                } else {
                    float f1 = MathHelper.clamp(i / 20.0F, 0.0F, 1.0F);
                    int i1 = (int)((1.0F - f1) * 255.0F) << 16 | (int)(f1 * 255.0F) << 8;
                    String s = "" + i / 2.0F;
                    if (y - this.minecraft.textRenderer.getWidth(s + "hp") >= x) {
                        s = s + "hp";
                    }

                    this.minecraft.textRenderer.drawWithShadow(s, (y + x) / 2 - this.minecraft.textRenderer.getWidth(s) / 2, width, i1);
                }
            }
        } else {
            String s1 = Formatting.YELLOW + "" + i;
            this.minecraft.textRenderer.drawWithShadow(s1, y - this.minecraft.textRenderer.getWidth(s1), width, 16777215);
        }
    }

    public void setFooter(Text footer) {
        this.footer = footer;
    }

    public void setHeader(Text header) {
        this.header = header;
    }

    public void reset() {
        this.header = null;
        this.footer = null;
    }

    static class PlayerInfoComparator implements Comparator<PlayerInfo> {
        private PlayerInfoComparator() {
        }

        public int compare(PlayerInfo playerInfo, PlayerInfo playerInfo2) {
            Team team = playerInfo.getTeam();
            Team team1 = playerInfo2.getTeam();
            return ComparisonChain.start()
                .compareTrueFirst(playerInfo.getGameMode() != WorldSettings.GameMode.SPECTATOR, playerInfo2.getGameMode() != WorldSettings.GameMode.SPECTATOR)
                .compare(team != null ? team.getName() : "", team1 != null ? team1.getName() : "")
                .compare(playerInfo.getProfile().getName(), playerInfo2.getProfile().getName())
                .result();
        }
    }
}
