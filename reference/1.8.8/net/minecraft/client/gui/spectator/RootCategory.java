package net.minecraft.client.gui.spectator;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class RootCategory implements SpectatorMenuCategory {
    private final List<SpectatorMenuItem> items = Lists.newArrayList();

    public RootCategory() {
        this.items.add(new TeleportToPlayerCategory());
        this.items.add(new TeleportToTeamCategory());
    }

    @Override
    public List<SpectatorMenuItem> getItems() {
        return this.items;
    }

    @Override
    public Text getPrompt() {
        return new LiteralText("Press a key to select a command, and again to use it.");
    }
}
