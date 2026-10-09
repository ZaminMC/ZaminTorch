package net.minecraft.client.gui.spectator;

import com.google.common.base.Objects;
import java.util.List;

public class SpectatorMenuPage {
    private final SpectatorMenuCategory category;
    private final List<SpectatorMenuItem> items;
    private final int selectedSlot;

    public SpectatorMenuPage(SpectatorMenuCategory category, List<SpectatorMenuItem> items, int selectedSlot) {
        this.category = category;
        this.items = items;
        this.selectedSlot = selectedSlot;
    }

    public SpectatorMenuItem getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? Objects.firstNonNull(this.items.get(slot), SpectatorMenu.EMPTY_SLOT) : SpectatorMenu.EMPTY_SLOT;
    }

    public int getSelectedSlot() {
        return this.selectedSlot;
    }
}
