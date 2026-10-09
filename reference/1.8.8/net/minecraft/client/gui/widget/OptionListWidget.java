package net.minecraft.client.gui.widget;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;

public class OptionListWidget extends EntryListWidget {
    private final List<OptionListWidget.Entry> entries = Lists.newArrayList();

    public OptionListWidget(Minecraft minecraft, int width, int height, int yStart, int yEnd, int entryHeight, GameOptions.Option... options) {
        super(minecraft, width, height, yStart, yEnd, entryHeight);
        this.centerAlongY = false;

        for (int i = 0; i < options.length; i += 2) {
            GameOptions.Option gameoptions$option = options[i];
            GameOptions.Option gameoptions$option1 = i < options.length - 1 ? options[i + 1] : null;
            ButtonWidget buttonwidget = this.createWidget(minecraft, width / 2 - 155, 0, gameoptions$option);
            ButtonWidget buttonwidget1 = this.createWidget(minecraft, width / 2 - 155 + 160, 0, gameoptions$option1);
            this.entries.add(new OptionListWidget.Entry(buttonwidget, buttonwidget1));
        }
    }

    private ButtonWidget createWidget(Minecraft minecraft, int x, int y, GameOptions.Option option) {
        if (option == null) {
            return null;
        }

        int i = option.getId();
        return option.isFloat() ? new OptionSliderWidget(i, x, y, option) : new OptionButtonWidget(i, x, y, option, minecraft.options.getAsString(option));
    }

    public OptionListWidget.Entry getEntry(int i) {
        return this.entries.get(i);
    }

    @Override
    protected int size() {
        return this.entries.size();
    }

    @Override
    public int getRowWidth() {
        return 400;
    }

    @Override
    protected int getScrollbarPosition() {
        return super.getScrollbarPosition() + 32;
    }

    public static class Entry implements EntryListWidget.Entry {
        private final Minecraft minecraft = Minecraft.getInstance();
        private final ButtonWidget left;
        private final ButtonWidget right;

        public Entry(ButtonWidget left, ButtonWidget right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            if (this.left != null) {
                this.left.y = y;
                this.left.render(this.minecraft, mouseX, mouseY);
            }

            if (this.right != null) {
                this.right.y = y;
                this.right.render(this.minecraft, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
            if (this.left.mouseClicked(this.minecraft, mouseX, mouseY)) {
                if (this.left instanceof OptionButtonWidget) {
                    this.minecraft.options.set(((OptionButtonWidget)this.left).getOption(), 1);
                    this.left.message = this.minecraft.options.getAsString(GameOptions.Option.byId(this.left.id));
                }

                return true;
            } else if (this.right != null && this.right.mouseClicked(this.minecraft, mouseX, mouseY)) {
                if (this.right instanceof OptionButtonWidget) {
                    this.minecraft.options.set(((OptionButtonWidget)this.right).getOption(), 1);
                    this.right.message = this.minecraft.options.getAsString(GameOptions.Option.byId(this.right.id));
                }

                return true;
            } else {
                return false;
            }
        }

        @Override
        public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
            if (this.left != null) {
                this.left.mouseReleased(mouseX, mouseY);
            }

            if (this.right != null) {
                this.right.mouseReleased(mouseX, mouseY);
            }
        }

        @Override
        public void renderOutOfBounds(int index, int x, int y) {
        }
    }
}
