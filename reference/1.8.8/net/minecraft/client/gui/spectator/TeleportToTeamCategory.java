package net.minecraft.client.gui.spectator;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.skin.DefaultSkinUtils;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class TeleportToTeamCategory implements SpectatorMenuCategory, SpectatorMenuItem {
    private final List<SpectatorMenuItem> items = Lists.newArrayList();

    public TeleportToTeamCategory() {
        Minecraft minecraft = Minecraft.getInstance();

        for (Team team : minecraft.world.getScoreboard().getTeams()) {
            this.items.add(new TeleportToTeamCategory.TeamMenuItem(team));
        }
    }

    @Override
    public List<SpectatorMenuItem> getItems() {
        return this.items;
    }

    @Override
    public Text getPrompt() {
        return new LiteralText("Select a team to teleport to");
    }

    @Override
    public void select(SpectatorMenu hud) {
        hud.setCategory(this);
    }

    @Override
    public Text getDisplayName() {
        return new LiteralText("Teleport to team member");
    }

    @Override
    public void render(float tickDelta, int slot) {
        Minecraft.getInstance().getTextureManager().bind(SpectatorGui.SPECTATOR_WIDGETS_LOCATION);
        GuiElement.drawTexture(0, 0, 16.0F, 0.0F, 16, 16, 256.0F, 256.0F);
    }

    @Override
    public boolean isEnabled() {
        for (SpectatorMenuItem spectatormenuitem : this.items) {
            if (spectatormenuitem.isEnabled()) {
                return true;
            }
        }

        return false;
    }

    class TeamMenuItem implements SpectatorMenuItem {
        private final Team team;
        private final Identifier textures;
        private final List<PlayerInfo> players;

        public TeamMenuItem(Team team) {
            this.team = team;
            this.players = Lists.newArrayList();

            for (String s : team.getMembers()) {
                PlayerInfo playerinfo = Minecraft.getInstance().getNetworkHandler().getOnlinePlayer(s);
                if (playerinfo != null) {
                    this.players.add(playerinfo);
                }
            }

            if (!this.players.isEmpty()) {
                String s1 = this.players.get(new Random().nextInt(this.players.size())).getProfile().getName();
                this.textures = ClientPlayerEntity.getSkinTextureLocation(s1);
                ClientPlayerEntity.loadSkinTexture(this.textures, s1);
            } else {
                this.textures = DefaultSkinUtils.getDefaultSkin();
            }
        }

        @Override
        public void select(SpectatorMenu hud) {
            hud.setCategory(new TeleportToPlayerCategory(this.players));
        }

        @Override
        public Text getDisplayName() {
            return new LiteralText(this.team.getDisplayName());
        }

        @Override
        public void render(float tickDelta, int slot) {
            int i = -1;
            String s = TextRenderer.isolateFormatting(this.team.getPrefix());
            if (s.length() >= 2) {
                i = Minecraft.getInstance().textRenderer.getColor(s.charAt(1));
            }

            if (i >= 0) {
                float f = (i >> 16 & 0xFF) / 255.0F;
                float f1 = (i >> 8 & 0xFF) / 255.0F;
                float f2 = (i & 0xFF) / 255.0F;
                GuiElement.fill(1, 1, 15, 15, MathHelper.packRGB(f * tickDelta, f1 * tickDelta, f2 * tickDelta) | slot << 24);
            }

            Minecraft.getInstance().getTextureManager().bind(this.textures);
            GlStateManager.color4f(tickDelta, tickDelta, tickDelta, slot / 255.0F);
            GuiElement.drawTexture(2, 2, 8.0F, 8.0F, 8, 8, 12, 12, 64.0F, 64.0F);
            GuiElement.drawTexture(2, 2, 40.0F, 8.0F, 8, 8, 12, 12, 64.0F, 64.0F);
        }

        @Override
        public boolean isEnabled() {
            return !this.players.isEmpty();
        }
    }
}
