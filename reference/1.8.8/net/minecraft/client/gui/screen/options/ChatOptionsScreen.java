package net.minecraft.client.gui.screen.options;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.gui.widget.OptionSliderWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.language.I18n;

public class ChatOptionsScreen extends Screen {
    private static final GameOptions.Option[] CHAT_OPTIONS = new GameOptions.Option[]{
        GameOptions.Option.CHAT_VISIBILITY,
        GameOptions.Option.CHAT_COLOR,
        GameOptions.Option.CHAT_LINKS,
        GameOptions.Option.CHAT_OPACITY,
        GameOptions.Option.CHAT_LINKS_PROMPT,
        GameOptions.Option.CHAT_SCALE,
        GameOptions.Option.CHAT_HEIGHT_FOCUSED,
        GameOptions.Option.CHAT_HEIGHT_UNFOCUSED,
        GameOptions.Option.CHAT_WIDTH,
        GameOptions.Option.REDUCED_DEBUG_INFO
    };
    private final Screen parent;
    private final GameOptions options;
    private String chatOptionsTitle;

    public ChatOptionsScreen(Screen parent, GameOptions options) {
        this.parent = parent;
        this.options = options;
    }

    @Override
    public void init() {
        int i = 0;
        this.chatOptionsTitle = I18n.translate("options.chat.title");

        for (GameOptions.Option gameoptions$option : CHAT_OPTIONS) {
            if (gameoptions$option.isFloat()) {
                this.buttons
                    .add(
                        new OptionSliderWidget(
                            gameoptions$option.getId(), this.width / 2 - 155 + i % 2 * 160, this.height / 6 + 24 * (i >> 1), gameoptions$option
                        )
                    );
            } else {
                this.buttons
                    .add(
                        new OptionButtonWidget(
                            gameoptions$option.getId(),
                            this.width / 2 - 155 + i % 2 * 160,
                            this.height / 6 + 24 * (i >> 1),
                            gameoptions$option,
                            this.options.getAsString(gameoptions$option)
                        )
                    );
            }

            i++;
        }

        this.buttons.add(new ButtonWidget(200, this.width / 2 - 100, this.height / 6 + 120, I18n.translate("gui.done")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id < 100 && button instanceof OptionButtonWidget) {
                this.options.set(((OptionButtonWidget)button).getOption(), 1);
                button.message = this.options.getAsString(GameOptions.Option.byId(button.id));
            }

            if (button.id == 200) {
                this.minecraft.options.save();
                this.minecraft.openScreen(this.parent);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, this.chatOptionsTitle, this.width / 2, 20, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }
}
