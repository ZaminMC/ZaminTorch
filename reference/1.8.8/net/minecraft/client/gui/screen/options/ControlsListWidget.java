package net.minecraft.client.gui.screen.options;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;
import org.apache.commons.lang3.ArrayUtils;

public class ControlsListWidget extends EntryListWidget {
    private final ControlsOptionsScreen parent;
    private final Minecraft minecraft;
    private final EntryListWidget.Entry[] entries;
    private int maxKeyNameLength = 0;

    public ControlsListWidget(ControlsOptionsScreen parent, Minecraft minecraft) {
        super(minecraft, parent.width, parent.height, 63, parent.height - 32, 20);
        this.parent = parent;
        this.minecraft = minecraft;
        KeyBinding[] akeybinding = ArrayUtils.clone(minecraft.options.keyBindings);
        this.entries = new EntryListWidget.Entry[akeybinding.length + KeyBinding.getCategories().size()];
        Arrays.sort(akeybinding);
        int i = 0;
        String s = null;

        for (KeyBinding keybinding : akeybinding) {
            String s1 = keybinding.getCategory();
            if (!s1.equals(s)) {
                s = s1;
                this.entries[i++] = new ControlsListWidget.CategoryEntry(s1);
            }

            int j = minecraft.textRenderer.getWidth(I18n.translate(keybinding.getName()));
            if (j > this.maxKeyNameLength) {
                this.maxKeyNameLength = j;
            }

            this.entries[i++] = new ControlsListWidget.KeyBindingEntry(keybinding);
        }
    }

    @Override
    protected int size() {
        return this.entries.length;
    }

    @Override
    public EntryListWidget.Entry getEntry(int index) {
        return this.entries[index];
    }

    @Override
    protected int getScrollbarPosition() {
        return super.getScrollbarPosition() + 15;
    }

    @Override
    public int getRowWidth() {
        return super.getRowWidth() + 32;
    }

    public class CategoryEntry implements EntryListWidget.Entry {
        private final String name;
        private final int nameWidth;

        public CategoryEntry(String key) {
            this.name = I18n.translate(key);
            this.nameWidth = ControlsListWidget.this.minecraft.textRenderer.getWidth(this.name);
        }

        @Override
        public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            ControlsListWidget.this.minecraft
                .textRenderer
                .draw(
                    this.name,
                    ControlsListWidget.this.minecraft.screen.width / 2 - this.nameWidth / 2,
                    y + height - ControlsListWidget.this.minecraft.textRenderer.fontHeight - 1,
                    16777215
                );
        }

        @Override
        public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
            return false;
        }

        @Override
        public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
        }

        @Override
        public void renderOutOfBounds(int index, int x, int y) {
        }
    }

    public class KeyBindingEntry implements EntryListWidget.Entry {
        private final KeyBinding keyBinding;
        private final String name;
        private final ButtonWidget keyBindingButton;
        private final ButtonWidget resetButton;

        private KeyBindingEntry(KeyBinding keyBinding) {
            this.keyBinding = keyBinding;
            this.name = I18n.translate(keyBinding.getName());
            this.keyBindingButton = new ButtonWidget(0, 0, 0, 75, 20, I18n.translate(keyBinding.getName()));
            this.resetButton = new ButtonWidget(0, 0, 0, 50, 20, I18n.translate("controls.reset"));
        }

        @Override
        public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            boolean flag = ControlsListWidget.this.parent.selectedKeyBinding == this.keyBinding;
            ControlsListWidget.this.minecraft
                .textRenderer
                .draw(
                    this.name,
                    x + 90 - ControlsListWidget.this.maxKeyNameLength,
                    y + height / 2 - ControlsListWidget.this.minecraft.textRenderer.fontHeight / 2,
                    16777215
                );
            this.resetButton.x = x + 190;
            this.resetButton.y = y;
            this.resetButton.active = this.keyBinding.getKeyCode() != this.keyBinding.getDefaultKeyCode();
            this.resetButton.render(ControlsListWidget.this.minecraft, mouseX, mouseY);
            this.keyBindingButton.x = x + 105;
            this.keyBindingButton.y = y;
            this.keyBindingButton.message = GameOptions.getKeyName(this.keyBinding.getKeyCode());
            boolean flag1 = false;
            if (this.keyBinding.getKeyCode() != 0) {
                for (KeyBinding keybinding : ControlsListWidget.this.minecraft.options.keyBindings) {
                    if (keybinding != this.keyBinding && keybinding.getKeyCode() == this.keyBinding.getKeyCode()) {
                        flag1 = true;
                        break;
                    }
                }
            }

            if (flag) {
                this.keyBindingButton.message = Formatting.WHITE + "> " + Formatting.YELLOW + this.keyBindingButton.message + Formatting.WHITE + " <";
            } else if (flag1) {
                this.keyBindingButton.message = Formatting.RED + this.keyBindingButton.message;
            }

            this.keyBindingButton.render(ControlsListWidget.this.minecraft, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
            if (this.keyBindingButton.mouseClicked(ControlsListWidget.this.minecraft, mouseX, mouseY)) {
                ControlsListWidget.this.parent.selectedKeyBinding = this.keyBinding;
                return true;
            } else if (this.resetButton.mouseClicked(ControlsListWidget.this.minecraft, mouseX, mouseY)) {
                ControlsListWidget.this.minecraft.options.setKeyCode(this.keyBinding, this.keyBinding.getDefaultKeyCode());
                KeyBinding.resetMapping();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
            this.keyBindingButton.mouseReleased(mouseX, mouseY);
            this.resetButton.mouseReleased(mouseX, mouseY);
        }

        @Override
        public void renderOutOfBounds(int index, int x, int y) {
        }
    }
}
