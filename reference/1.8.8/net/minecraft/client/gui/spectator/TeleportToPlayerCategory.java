package net.minecraft.client.gui.spectator;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.world.WorldSettings;

public class TeleportToPlayerCategory implements SpectatorMenuCategory, SpectatorMenuItem {
    private static final Ordering<PlayerInfo> PLAYER_ORDERING = Ordering.from(new Comparator<PlayerInfo>() {
        public int compare(PlayerInfo playerInfo, PlayerInfo playerInfo2) {
            return ComparisonChain.start().compare(playerInfo.getProfile().getId(), playerInfo2.getProfile().getId()).result();
        }
    });
    private final List<SpectatorMenuItem> items = Lists.newArrayList();

    public TeleportToPlayerCategory() {
        this(PLAYER_ORDERING.sortedCopy(Minecraft.getInstance().getNetworkHandler().getOnlinePlayers()));
    }

    public TeleportToPlayerCategory(Collection<PlayerInfo> players) {
        for (PlayerInfo playerinfo : PLAYER_ORDERING.sortedCopy(players)) {
            if (playerinfo.getGameMode() != WorldSettings.GameMode.SPECTATOR) {
                this.items.add(new PlayerMenuItem(playerinfo.getProfile()));
            }
        }
    }

    @Override
    public List<SpectatorMenuItem> getItems() {
        return this.items;
    }

    @Override
    public Text getPrompt() {
        return new LiteralText("Select a player to teleport to");
    }

    @Override
    public void select(SpectatorMenu hud) {
        hud.setCategory(this);
    }

    @Override
    public Text getDisplayName() {
        return new LiteralText("Teleport to player");
    }

    @Override
    public void render(float tickDelta, int slot) {
        Minecraft.getInstance().getTextureManager().bind(SpectatorGui.SPECTATOR_WIDGETS_LOCATION);
        GuiElement.drawTexture(0, 0, 0.0F, 0.0F, 16, 16, 256.0F, 256.0F);
    }

    @Override
    public boolean isEnabled() {
        return !this.items.isEmpty();
    }
}
