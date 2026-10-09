package net.minecraft.client.gui.screen.options;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.gui.widget.OptionSliderWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.resource.language.I18n;

public class ControlsOptionsScreen extends Screen {
    private static final GameOptions.Option[] OPTIONS = new GameOptions.Option[]{
        GameOptions.Option.INVERT_MOUSE, GameOptions.Option.SENSITIVITY, GameOptions.Option.TOUCHSCREEN
    };
    private Screen parent;
    protected String title = "Controls";
    private GameOptions options;
    public KeyBinding selectedKeyBinding = null;
    public long time;
    private ControlsListWidget controlsList;
    private ButtonWidget resetAllButton;

    public ControlsOptionsScreen(Screen parent, GameOptions options) {
        this.parent = parent;
        this.options = options;
    }

    @Override
    public void init() {
        this.controlsList = new ControlsListWidget(this, this.minecraft);
        this.buttons.add(new ButtonWidget(200, this.width / 2 - 155, this.height - 29, 150, 20, I18n.translate("gui.done")));
        this.buttons
            .add(this.resetAllButton = new ButtonWidget(201, this.width / 2 - 155 + 160, this.height - 29, 150, 20, I18n.translate("controls.resetAll")));
        this.title = I18n.translate("controls.title");
        int i = 0;

        for (GameOptions.Option gameoptions$option : OPTIONS) {
            if (gameoptions$option.isFloat()) {
                this.buttons
                    .add(new OptionSliderWidget(gameoptions$option.getId(), this.width / 2 - 155 + i % 2 * 160, 18 + 24 * (i >> 1), gameoptions$option));
            } else {
                this.buttons
                    .add(
                        new OptionButtonWidget(
                            gameoptions$option.getId(),
                            this.width / 2 - 155 + i % 2 * 160,
                            18 + 24 * (i >> 1),
                            gameoptions$option,
                            this.options.getAsString(gameoptions$option)
                        )
                    );
            }

            i++;
        }
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.controlsList.handleMouse();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 200) {
            this.minecraft.openScreen(this.parent);
        } else if (button.id == 201) {
            for (KeyBinding keybinding : this.minecraft.options.keyBindings) {
                keybinding.setKeyCode(keybinding.getDefaultKeyCode());
            }

            KeyBinding.resetMapping();
        } else if (button.id < 100 && button instanceof OptionButtonWidget) {
            this.options.set(((OptionButtonWidget)button).getOption(), 1);
            button.message = this.options.getAsString(GameOptions.Option.byId(button.id));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.selectedKeyBinding != null) {
            this.options.setKeyCode(this.selectedKeyBinding, -100 + button);
            this.selectedKeyBinding = null;
            KeyBinding.resetMapping();
        } else if (button != 0 || !this.controlsList.mouseClicked(mouseX, mouseY, button)) {
            super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button != 0 || !this.controlsList.mouseReleased(mouseX, mouseY, button)) {
            super.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (this.selectedKeyBinding != null) {
            if (key == 1) {
                this.options.setKeyCode(this.selectedKeyBinding, 0);
            } else if (key != 0) {
                this.options.setKeyCode(this.selectedKeyBinding, key);
            } else if (chr > 0) {
                this.options.setKeyCode(this.selectedKeyBinding, chr + 256);
            }

            this.selectedKeyBinding = null;
            this.time = Minecraft.getTime();
            KeyBinding.resetMapping();
        } else {
            super.keyPressed(chr, key);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.controlsList.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 8, 16777215);
        boolean flag = true;

        for (KeyBinding keybinding : this.options.keyBindings) {
            if (keybinding.getKeyCode() != keybinding.getDefaultKeyCode()) {
                flag = false;
                break;
            }
        }

        this.resetAllButton.active = !flag;
        super.render(mouseX, mouseY, tickDelta);
    }
}
