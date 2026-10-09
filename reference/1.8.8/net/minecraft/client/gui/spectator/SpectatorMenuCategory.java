package net.minecraft.client.gui.spectator;

import java.util.List;
import net.minecraft.text.Text;

public interface SpectatorMenuCategory {
    List<SpectatorMenuItem> getItems();

    Text getPrompt();
}
