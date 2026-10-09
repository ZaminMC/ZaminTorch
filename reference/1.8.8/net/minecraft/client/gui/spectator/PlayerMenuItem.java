package net.minecraft.client.gui.spectator;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.network.packet.c2s.play.PlayerSpectateC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class PlayerMenuItem implements SpectatorMenuItem {
    private final GameProfile profile;
    private final Identifier texture;

    public PlayerMenuItem(GameProfile profile) {
        this.profile = profile;
        this.texture = ClientPlayerEntity.getSkinTextureLocation(profile.getName());
        ClientPlayerEntity.loadSkinTexture(this.texture, profile.getName());
    }

    @Override
    public void select(SpectatorMenu hud) {
        Minecraft.getInstance().getNetworkHandler().sendPacket(new PlayerSpectateC2SPacket(this.profile.getId()));
    }

    @Override
    public Text getDisplayName() {
        return new LiteralText(this.profile.getName());
    }

    @Override
    public void render(float tickDelta, int slot) {
        Minecraft.getInstance().getTextureManager().bind(this.texture);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, slot / 255.0F);
        GuiElement.drawTexture(2, 2, 8.0F, 8.0F, 8, 8, 12, 12, 64.0F, 64.0F);
        GuiElement.drawTexture(2, 2, 40.0F, 8.0F, 8, 8, 12, 12, 64.0F, 64.0F);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
