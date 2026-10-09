package net.minecraft.client.gui.spectator;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class SpectatorMenu {
    private static final SpectatorMenuItem CLOSE_HUD = new SpectatorMenu.CloseMenuItem();
    private static final SpectatorMenuItem SCROLL_LEFT = new SpectatorMenu.PaginationMenuItem(-1, true);
    private static final SpectatorMenuItem SCROLL_RIGHT_ENABLED = new SpectatorMenu.PaginationMenuItem(1, true);
    private static final SpectatorMenuItem SCROLL_RIGHT_DISABLED = new SpectatorMenu.PaginationMenuItem(1, false);
    public static final SpectatorMenuItem EMPTY_SLOT = new SpectatorMenuItem() {
        @Override
        public void select(SpectatorMenu hud) {
        }

        @Override
        public Text getDisplayName() {
            return new LiteralText("");
        }

        @Override
        public void render(float tickDelta, int slot) {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    };
    private final SpectatorMenuListener listener;
    private final List<SpectatorMenuPage> previousPages = Lists.newArrayList();
    private SpectatorMenuCategory category;
    private int selectedSlot = -1;
    private int page;

    public SpectatorMenu(SpectatorMenuListener listener) {
        this.category = new RootCategory();
        this.listener = listener;
    }

    public SpectatorMenuItem getItem(int slot) {
        int i = slot + this.page * 6;
        if (this.page > 0 && slot == 0) {
            return SCROLL_LEFT;
        } else if (slot == 7) {
            return i < this.category.getItems().size() ? SCROLL_RIGHT_ENABLED : SCROLL_RIGHT_DISABLED;
        } else if (slot == 8) {
            return CLOSE_HUD;
        } else {
            return i >= 0 && i < this.category.getItems().size() ? Objects.firstNonNull(this.category.getItems().get(i), EMPTY_SLOT) : EMPTY_SLOT;
        }
    }

    public List<SpectatorMenuItem> getItems() {
        List<SpectatorMenuItem> list = Lists.newArrayList();

        for (int i = 0; i <= 8; i++) {
            list.add(this.getItem(i));
        }

        return list;
    }

    public SpectatorMenuItem getSelectedItem() {
        return this.getItem(this.selectedSlot);
    }

    public SpectatorMenuCategory getCategory() {
        return this.category;
    }

    public void selectSlot(int slot) {
        SpectatorMenuItem spectatormenuitem = this.getItem(slot);
        if (spectatormenuitem != EMPTY_SLOT) {
            if (this.selectedSlot == slot && spectatormenuitem.isEnabled()) {
                spectatormenuitem.select(this);
            } else {
                this.selectedSlot = slot;
            }
        }
    }

    public void close() {
        this.listener.onSpectatorMenuClosed(this);
    }

    public int getSelectedSlot() {
        return this.selectedSlot;
    }

    public void setCategory(SpectatorMenuCategory category) {
        this.previousPages.add(this.getSelectedPage());
        this.category = category;
        this.selectedSlot = -1;
        this.page = 0;
    }

    public SpectatorMenuPage getSelectedPage() {
        return new SpectatorMenuPage(this.category, this.getItems(), this.selectedSlot);
    }

    static class CloseMenuItem implements SpectatorMenuItem {
        private CloseMenuItem() {
        }

        @Override
        public void select(SpectatorMenu hud) {
            hud.close();
        }

        @Override
        public Text getDisplayName() {
            return new LiteralText("Close menu");
        }

        @Override
        public void render(float tickDelta, int slot) {
            Minecraft.getInstance().getTextureManager().bind(SpectatorGui.SPECTATOR_WIDGETS_LOCATION);
            GuiElement.drawTexture(0, 0, 128.0F, 0.0F, 16, 16, 256.0F, 256.0F);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    static class PaginationMenuItem implements SpectatorMenuItem {
        private final int slot;
        private final boolean enabled;

        public PaginationMenuItem(int slot, boolean enabled) {
            this.slot = slot;
            this.enabled = enabled;
        }

        @Override
        public void select(SpectatorMenu hud) {
            hud.page += this.slot;
        }

        @Override
        public Text getDisplayName() {
            return this.slot < 0 ? new LiteralText("Previous Page") : new LiteralText("Next Page");
        }

        @Override
        public void render(float tickDelta, int slot) {
            Minecraft.getInstance().getTextureManager().bind(SpectatorGui.SPECTATOR_WIDGETS_LOCATION);
            if (this.slot < 0) {
                GuiElement.drawTexture(0, 0, 144.0F, 0.0F, 16, 16, 256.0F, 256.0F);
            } else {
                GuiElement.drawTexture(0, 0, 160.0F, 0.0F, 16, 16, 256.0F, 256.0F);
            }
        }

        @Override
        public boolean isEnabled() {
            return this.enabled;
        }
    }
}
