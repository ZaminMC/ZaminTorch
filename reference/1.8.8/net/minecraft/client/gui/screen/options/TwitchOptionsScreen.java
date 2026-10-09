package net.minecraft.client.gui.screen.options;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.StreamIngestScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.gui.widget.OptionSliderWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;

public class TwitchOptionsScreen extends Screen {
    private static final GameOptions.Option[] STREAMING_OPTIONS = new GameOptions.Option[]{
        GameOptions.Option.STREAM_BYTES_PER_PIXEL,
        GameOptions.Option.STREAM_FPS,
        GameOptions.Option.STREAM_KBPS,
        GameOptions.Option.STREAM_SEND_METADATA,
        GameOptions.Option.STREAM_VOLUME_MIC,
        GameOptions.Option.STREAM_VOLUME_SYSTEM,
        GameOptions.Option.STREAM_MIC_TOGGLE_BEHAVIOR,
        GameOptions.Option.STREAM_COMPRESSION
    };
    private static final GameOptions.Option[] CHAT_OPTIONS = new GameOptions.Option[]{
        GameOptions.Option.STREAM_CHAT_ENABLED, GameOptions.Option.STREAM_CHAT_USER_FILTER
    };
    private final Screen parent;
    private final GameOptions options;
    private String title;
    private String chatTitle;
    private int chatTitleY;
    private boolean warning = false;

    public TwitchOptionsScreen(Screen parent, GameOptions options) {
        this.parent = parent;
        this.options = options;
    }

    @Override
    public void init() {
        int i = 0;
        this.title = I18n.translate("options.stream.title");
        this.chatTitle = I18n.translate("options.stream.chat.title");

        for (GameOptions.Option gameoptions$option : STREAMING_OPTIONS) {
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

        if (i % 2 == 1) {
            i++;
        }

        this.chatTitleY = this.height / 6 + 24 * (i >> 1) + 6;
        i += 2;

        for (GameOptions.Option gameoptions$option1 : CHAT_OPTIONS) {
            if (gameoptions$option1.isFloat()) {
                this.buttons
                    .add(
                        new OptionSliderWidget(
                            gameoptions$option1.getId(), this.width / 2 - 155 + i % 2 * 160, this.height / 6 + 24 * (i >> 1), gameoptions$option1
                        )
                    );
            } else {
                this.buttons
                    .add(
                        new OptionButtonWidget(
                            gameoptions$option1.getId(),
                            this.width / 2 - 155 + i % 2 * 160,
                            this.height / 6 + 24 * (i >> 1),
                            gameoptions$option1,
                            this.options.getAsString(gameoptions$option1)
                        )
                    );
            }

            i++;
        }

        this.buttons.add(new ButtonWidget(200, this.width / 2 - 155, this.height / 6 + 168, 150, 20, I18n.translate("gui.done")));
        ButtonWidget buttonwidget = new ButtonWidget(201, this.width / 2 + 5, this.height / 6 + 168, 150, 20, I18n.translate("options.stream.ingestSelection"));
        buttonwidget.active = this.minecraft.getTwitchStream().isReadyToBroadcast() && this.minecraft.getTwitchStream().getServers().length > 0
            || this.minecraft.getTwitchStream().isIngestTesting();
        this.buttons.add(buttonwidget);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id < 100 && button instanceof OptionButtonWidget) {
                GameOptions.Option gameoptions$option = ((OptionButtonWidget)button).getOption();
                this.options.set(gameoptions$option, 1);
                button.message = this.options.getAsString(GameOptions.Option.byId(button.id));
                if (this.minecraft.getTwitchStream().isBroadcasting()
                    && gameoptions$option != GameOptions.Option.STREAM_CHAT_ENABLED
                    && gameoptions$option != GameOptions.Option.STREAM_CHAT_USER_FILTER) {
                    this.warning = true;
                }
            } else if (button instanceof OptionSliderWidget) {
                if (button.id == GameOptions.Option.STREAM_VOLUME_MIC.getId()) {
                    this.minecraft.getTwitchStream().updateVolume();
                } else if (button.id == GameOptions.Option.STREAM_VOLUME_SYSTEM.getId()) {
                    this.minecraft.getTwitchStream().updateVolume();
                } else if (this.minecraft.getTwitchStream().isBroadcasting()) {
                    this.warning = true;
                }
            }

            if (button.id == 200) {
                this.minecraft.options.save();
                this.minecraft.openScreen(this.parent);
            } else if (button.id == 201) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new StreamIngestScreen(this));
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 20, 16777215);
        this.drawCenteredString(this.textRenderer, this.chatTitle, this.width / 2, this.chatTitleY, 16777215);
        if (this.warning) {
            this.drawCenteredString(
                this.textRenderer, Formatting.RED + I18n.translate("options.stream.changes"), this.width / 2, 20 + this.textRenderer.fontHeight, 16777215
            );
        }

        super.render(mouseX, mouseY, tickDelta);
    }
}
