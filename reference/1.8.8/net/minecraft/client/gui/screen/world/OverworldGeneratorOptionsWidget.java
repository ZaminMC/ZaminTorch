package net.minecraft.client.gui.screen.world;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.LabelWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.Int2ObjectHashMap;

public class OverworldGeneratorOptionsWidget extends EntryListWidget {
    private final List<OverworldGeneratorOptionsWidget.GeneratorOptionsEntry> entries = Lists.newArrayList();
    private final Int2ObjectHashMap<GuiElement> elements = new Int2ObjectHashMap<>();
    private final List<TextFieldWidget> textFields = Lists.newArrayList();
    private final OverworldGeneratorOptionsWidget.GeneratorOption[][] options;
    private int page;
    private OverworldGeneratorOptionsWidget.Controller controller;
    private GuiElement selected;

    public OverworldGeneratorOptionsWidget(
        Minecraft minecraft,
        int width,
        int height,
        int minY,
        int maxY,
        int entryHeight,
        OverworldGeneratorOptionsWidget.Controller controller,
        OverworldGeneratorOptionsWidget.GeneratorOption[]... options
    ) {
        super(minecraft, width, height, minY, maxY, entryHeight);
        this.controller = controller;
        this.options = options;
        this.centerAlongY = false;
        this.init();
        this.buildPage();
    }

    private void init() {
        for (OverworldGeneratorOptionsWidget.GeneratorOption[] aoverworldgeneratoroptionswidget$generatoroption : this.options) {
            for (int i = 0; i < aoverworldgeneratoroptionswidget$generatoroption.length; i += 2) {
                OverworldGeneratorOptionsWidget.GeneratorOption overworldgeneratoroptionswidget$generatoroption = aoverworldgeneratoroptionswidget$generatoroption[i];
                OverworldGeneratorOptionsWidget.GeneratorOption overworldgeneratoroptionswidget$generatoroption1 = i
                        < aoverworldgeneratoroptionswidget$generatoroption.length - 1
                    ? aoverworldgeneratoroptionswidget$generatoroption[i + 1]
                    : null;
                GuiElement guielement = this.createElement(
                    overworldgeneratoroptionswidget$generatoroption, 0, overworldgeneratoroptionswidget$generatoroption1 == null
                );
                GuiElement guielement1 = this.createElement(
                    overworldgeneratoroptionswidget$generatoroption1, 160, overworldgeneratoroptionswidget$generatoroption == null
                );
                OverworldGeneratorOptionsWidget.GeneratorOptionsEntry overworldgeneratoroptionswidget$generatoroptionsentry = new OverworldGeneratorOptionsWidget.GeneratorOptionsEntry(
                    guielement, guielement1
                );
                this.entries.add(overworldgeneratoroptionswidget$generatoroptionsentry);
                if (overworldgeneratoroptionswidget$generatoroption != null && guielement != null) {
                    this.elements.put(overworldgeneratoroptionswidget$generatoroption.getId(), guielement);
                    if (guielement instanceof TextFieldWidget) {
                        this.textFields.add((TextFieldWidget)guielement);
                    }
                }

                if (overworldgeneratoroptionswidget$generatoroption1 != null && guielement1 != null) {
                    this.elements.put(overworldgeneratoroptionswidget$generatoroption1.getId(), guielement1);
                    if (guielement1 instanceof TextFieldWidget) {
                        this.textFields.add((TextFieldWidget)guielement1);
                    }
                }
            }
        }
    }

    private void buildPage() {
        this.entries.clear();

        for (int i = 0; i < this.options[this.page].length; i += 2) {
            OverworldGeneratorOptionsWidget.GeneratorOption overworldgeneratoroptionswidget$generatoroption = this.options[this.page][i];
            OverworldGeneratorOptionsWidget.GeneratorOption overworldgeneratoroptionswidget$generatoroption1 = i < this.options[this.page].length - 1
                ? this.options[this.page][i + 1]
                : null;
            GuiElement guielement = this.elements.get(overworldgeneratoroptionswidget$generatoroption.getId());
            GuiElement guielement1 = overworldgeneratoroptionswidget$generatoroption1 != null
                ? this.elements.get(overworldgeneratoroptionswidget$generatoroption1.getId())
                : null;
            OverworldGeneratorOptionsWidget.GeneratorOptionsEntry overworldgeneratoroptionswidget$generatoroptionsentry = new OverworldGeneratorOptionsWidget.GeneratorOptionsEntry(
                guielement, guielement1
            );
            this.entries.add(overworldgeneratoroptionswidget$generatoroptionsentry);
        }
    }

    public void selectPage(int page) {
        if (page != this.page) {
            int i = this.page;
            this.page = page;
            this.buildPage();
            this.changePage(i, page);
            this.scrollAmount = 0.0F;
        }
    }

    public int getPage() {
        return this.page;
    }

    public int getOptionCount() {
        return this.options.length;
    }

    public GuiElement getSelected() {
        return this.selected;
    }

    public void prevPage() {
        if (this.page > 0) {
            this.selectPage(this.page - 1);
        }
    }

    public void nextPage() {
        if (this.page < this.options.length - 1) {
            this.selectPage(this.page + 1);
        }
    }

    public GuiElement getElement(int id) {
        return this.elements.get(id);
    }

    private void changePage(int from, int to) {
        for (OverworldGeneratorOptionsWidget.GeneratorOption overworldgeneratoroptionswidget$generatoroption : this.options[from]) {
            if (overworldgeneratoroptionswidget$generatoroption != null) {
                this.setVisible(this.elements.get(overworldgeneratoroptionswidget$generatoroption.getId()), false);
            }
        }

        for (OverworldGeneratorOptionsWidget.GeneratorOption overworldgeneratoroptionswidget$generatoroption1 : this.options[to]) {
            if (overworldgeneratoroptionswidget$generatoroption1 != null) {
                this.setVisible(this.elements.get(overworldgeneratoroptionswidget$generatoroption1.getId()), true);
            }
        }
    }

    private void setVisible(GuiElement option, boolean visible) {
        if (option instanceof ButtonWidget) {
            ((ButtonWidget)option).visible = visible;
        } else if (option instanceof TextFieldWidget) {
            ((TextFieldWidget)option).setVisible(visible);
        } else if (option instanceof LabelWidget) {
            ((LabelWidget)option).visible = visible;
        }
    }

    private GuiElement createElement(OverworldGeneratorOptionsWidget.GeneratorOption option, int x, boolean alone) {
        if (option instanceof OverworldGeneratorOptionsWidget.NumberOption) {
            return this.createSlider(this.width / 2 - 155 + x, 0, (OverworldGeneratorOptionsWidget.NumberOption)option);
        } else if (option instanceof OverworldGeneratorOptionsWidget.ToggleOption) {
            return this.createToggle(this.width / 2 - 155 + x, 0, (OverworldGeneratorOptionsWidget.ToggleOption)option);
        } else if (option instanceof OverworldGeneratorOptionsWidget.TextOption) {
            return this.createTextField(this.width / 2 - 155 + x, 0, (OverworldGeneratorOptionsWidget.TextOption)option);
        } else {
            return option instanceof OverworldGeneratorOptionsWidget.Label
                ? this.createLabel(this.width / 2 - 155 + x, 0, (OverworldGeneratorOptionsWidget.Label)option, alone)
                : null;
        }
    }

    public void setActive(boolean active) {
        for (OverworldGeneratorOptionsWidget.GeneratorOptionsEntry overworldgeneratoroptionswidget$generatoroptionsentry : this.entries) {
            if (overworldgeneratoroptionswidget$generatoroptionsentry.left instanceof ButtonWidget) {
                ((ButtonWidget)overworldgeneratoroptionswidget$generatoroptionsentry.left).active = active;
            }

            if (overworldgeneratoroptionswidget$generatoroptionsentry.right instanceof ButtonWidget) {
                ((ButtonWidget)overworldgeneratoroptionswidget$generatoroptionsentry.right).active = active;
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        boolean flag = super.mouseClicked(mouseX, mouseY, button);
        int i = this.getEntryAt(mouseX, mouseY);
        if (i >= 0) {
            OverworldGeneratorOptionsWidget.GeneratorOptionsEntry overworldgeneratoroptionswidget$generatoroptionsentry = this.getEntry(i);
            if (this.selected != overworldgeneratoroptionswidget$generatoroptionsentry.selected
                && this.selected != null
                && this.selected instanceof TextFieldWidget) {
                ((TextFieldWidget)this.selected).setFocused(false);
            }

            this.selected = overworldgeneratoroptionswidget$generatoroptionsentry.selected;
        }

        return flag;
    }

    private GeneratorOptionSlider createSlider(int x, int y, OverworldGeneratorOptionsWidget.NumberOption option) {
        GeneratorOptionSlider generatoroptionslider = new GeneratorOptionSlider(
            this.controller, option.getId(), x, y, option.getMessage(), option.getMin(), option.getMax(), option.getDefaultValue(), option.getFormatter()
        );
        generatoroptionslider.visible = option.isVisible();
        return generatoroptionslider;
    }

    private GeneratorOptionToggle createToggle(int x, int y, OverworldGeneratorOptionsWidget.ToggleOption option) {
        GeneratorOptionToggle generatoroptiontoggle = new GeneratorOptionToggle(
            this.controller, option.getId(), x, y, option.getMessage(), option.getDefaultValue()
        );
        generatoroptiontoggle.visible = option.isVisible();
        return generatoroptiontoggle;
    }

    private TextFieldWidget createTextField(int x, int y, OverworldGeneratorOptionsWidget.TextOption option) {
        TextFieldWidget textfieldwidget = new TextFieldWidget(option.getId(), this.minecraft.textRenderer, x, y, 150, 20);
        textfieldwidget.setText(option.getMessage());
        textfieldwidget.setController(this.controller);
        textfieldwidget.setVisible(option.isVisible());
        textfieldwidget.setFilter(option.getValidator());
        return textfieldwidget;
    }

    private LabelWidget createLabel(int x, int y, OverworldGeneratorOptionsWidget.Label label, boolean alone) {
        LabelWidget labelwidget;
        if (alone) {
            labelwidget = new LabelWidget(this.minecraft.textRenderer, label.getId(), x, y, this.width - x * 2, 20, -1);
        } else {
            labelwidget = new LabelWidget(this.minecraft.textRenderer, label.getId(), x, y, 150, 20, -1);
        }

        labelwidget.visible = label.isVisible();
        labelwidget.add(label.getMessage());
        labelwidget.setCentered();
        return labelwidget;
    }

    public void keyPressed(char chr, int code) {
        if (this.selected instanceof TextFieldWidget) {
            TextFieldWidget textfieldwidget = (TextFieldWidget)this.selected;
            if (!Screen.isPaste(code)) {
                if (code == 15) {
                    textfieldwidget.setFocused(false);
                    int k = this.textFields.indexOf(this.selected);
                    if (Screen.isShiftDown()) {
                        if (k == 0) {
                            k = this.textFields.size() - 1;
                        } else {
                            k--;
                        }
                    } else if (k == this.textFields.size() - 1) {
                        k = 0;
                    } else {
                        k++;
                    }

                    this.selected = this.textFields.get(k);
                    textfieldwidget = (TextFieldWidget)this.selected;
                    textfieldwidget.setFocused(true);
                    int l = textfieldwidget.y + this.entryHeight;
                    int i1 = textfieldwidget.y;
                    if (l > this.maxY) {
                        this.scrollAmount = this.scrollAmount + (l - this.maxY);
                    } else if (i1 < this.minY) {
                        this.scrollAmount = i1;
                    }
                } else {
                    textfieldwidget.keyPressed(chr, code);
                }
            } else {
                String s = Screen.getClipboard();
                String[] astring = s.split(";");
                int i = this.textFields.indexOf(this.selected);
                int j = i;

                for (String s1 : astring) {
                    this.textFields.get(j).setText(s1);
                    if (j == this.textFields.size() - 1) {
                        j = 0;
                    } else {
                        j++;
                    }

                    if (j == i) {
                        break;
                    }
                }
            }
        }
    }

    public OverworldGeneratorOptionsWidget.GeneratorOptionsEntry getEntry(int i) {
        return this.entries.get(i);
    }

    @Override
    public int size() {
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

    public interface Controller {
        void setValue(int id, boolean value);

        void setValue(int id, float value);

        void setValue(int id, String value);
    }

    public static class GeneratorOption {
        private final int id;
        private final String message;
        private final boolean visible;

        public GeneratorOption(int id, String message, boolean visible) {
            this.id = id;
            this.message = message;
            this.visible = visible;
        }

        public int getId() {
            return this.id;
        }

        public String getMessage() {
            return this.message;
        }

        public boolean isVisible() {
            return this.visible;
        }
    }

    public static class GeneratorOptionsEntry implements EntryListWidget.Entry {
        private final Minecraft minecraft = Minecraft.getInstance();
        private final GuiElement left;
        private final GuiElement right;
        private GuiElement selected;

        public GeneratorOptionsEntry(GuiElement left, GuiElement right) {
            this.left = left;
            this.right = right;
        }

        public GuiElement getLeft() {
            return this.left;
        }

        public GuiElement getRight() {
            return this.right;
        }

        @Override
        public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            this.renderOption(this.left, y, mouseX, mouseY, false);
            this.renderOption(this.right, y, mouseX, mouseY, false);
        }

        private void renderOption(GuiElement option, int y, int mouseX, int mouseY, boolean outOfBounds) {
            if (option != null) {
                if (option instanceof ButtonWidget) {
                    this.renderButton((ButtonWidget)option, y, mouseX, mouseY, outOfBounds);
                } else if (option instanceof TextFieldWidget) {
                    this.renderTextField((TextFieldWidget)option, y, outOfBounds);
                } else if (option instanceof LabelWidget) {
                    this.renderLabel((LabelWidget)option, y, mouseX, mouseY, outOfBounds);
                }
            }
        }

        private void renderButton(ButtonWidget option, int y, int mouseX, int mouseY, boolean outOfBounds) {
            option.y = y;
            if (!outOfBounds) {
                option.render(this.minecraft, mouseX, mouseY);
            }
        }

        private void renderTextField(TextFieldWidget option, int y, boolean outOfBounds) {
            option.y = y;
            if (!outOfBounds) {
                option.render();
            }
        }

        private void renderLabel(LabelWidget option, int y, int mouseX, int mouseY, boolean outOfBounds) {
            option.y = y;
            if (!outOfBounds) {
                option.render(this.minecraft, mouseX, mouseY);
            }
        }

        @Override
        public void renderOutOfBounds(int index, int x, int y) {
            this.renderOption(this.left, y, 0, 0, true);
            this.renderOption(this.right, y, 0, 0, true);
        }

        @Override
        public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
            boolean flag = this.mouseClicked(this.left, mouseX, mouseY, button);
            boolean flag1 = this.mouseClicked(this.right, mouseX, mouseY, button);
            return flag || flag1;
        }

        private boolean mouseClicked(GuiElement option, int mouseX, int mouseY, int button) {
            if (option == null) {
                return false;
            }

            if (option instanceof ButtonWidget) {
                return this.mouseClicked((ButtonWidget)option, mouseX, mouseY, button);
            }

            if (option instanceof TextFieldWidget) {
                this.mouseClicked((TextFieldWidget)option, mouseX, mouseY, button);
            }

            return false;
        }

        private boolean mouseClicked(ButtonWidget option, int mouseX, int mouseY, int button) {
            boolean flag = option.mouseClicked(this.minecraft, mouseX, mouseY);
            if (flag) {
                this.selected = option;
            }

            return flag;
        }

        private void mouseClicked(TextFieldWidget option, int mouseX, int mouseY, int button) {
            option.mouseClicked(mouseX, mouseY, button);
            if (option.isFocused()) {
                this.selected = option;
            }
        }

        @Override
        public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
            this.mouseReleased(this.left, mouseX, mouseY, button);
            this.mouseReleased(this.right, mouseX, mouseY, button);
        }

        private void mouseReleased(GuiElement option, int mouseX, int mouseY, int button) {
            if (option != null) {
                if (option instanceof ButtonWidget) {
                    this.mouseReleased((ButtonWidget)option, mouseX, mouseY, button);
                }
            }
        }

        private void mouseReleased(ButtonWidget option, int mouseX, int mouseY, int button) {
            option.mouseReleased(mouseX, mouseY);
        }
    }

    public static class Label extends OverworldGeneratorOptionsWidget.GeneratorOption {
        public Label(int i, String string, boolean bl) {
            super(i, string, bl);
        }
    }

    public static class NumberOption extends OverworldGeneratorOptionsWidget.GeneratorOption {
        private final GeneratorOptionSlider.ValueFormatter formatter;
        private final float min;
        private final float max;
        private final float defaultValue;

        public NumberOption(int id, String message, boolean visible, GeneratorOptionSlider.ValueFormatter formatter, float min, float max, float defaultValue) {
            super(id, message, visible);
            this.formatter = formatter;
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }

        public GeneratorOptionSlider.ValueFormatter getFormatter() {
            return this.formatter;
        }

        public float getMin() {
            return this.min;
        }

        public float getMax() {
            return this.max;
        }

        public float getDefaultValue() {
            return this.defaultValue;
        }
    }

    public static class TextOption extends OverworldGeneratorOptionsWidget.GeneratorOption {
        private final Predicate<String> validator;

        public TextOption(int id, String message, boolean visible, Predicate<String> validator) {
            super(id, message, visible);
            this.validator = Objects.firstNonNull(validator, Predicates.alwaysTrue());
        }

        public Predicate<String> getValidator() {
            return this.validator;
        }
    }

    public static class ToggleOption extends OverworldGeneratorOptionsWidget.GeneratorOption {
        private final boolean defaultValue;

        public ToggleOption(int id, String message, boolean visible, boolean defaultValue) {
            super(id, message, visible);
            this.defaultValue = defaultValue;
        }

        public boolean getDefaultValue() {
            return this.defaultValue;
        }
    }
}
