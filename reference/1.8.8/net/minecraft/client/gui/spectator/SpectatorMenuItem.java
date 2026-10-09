package net.minecraft.client.gui.spectator;

import net.minecraft.text.Text;

public interface SpectatorMenuItem {
    void select(SpectatorMenu hud);

    Text getDisplayName();

    void render(float tickDelta, int slot);

    boolean isEnabled();
}
