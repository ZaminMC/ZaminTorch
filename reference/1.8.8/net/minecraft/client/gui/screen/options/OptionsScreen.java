package net.minecraft.client.gui.screen.options;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ConfirmationListener;
import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SkinCustomizationScreen;
import net.minecraft.client.gui.screen.SnooperScreen;
import net.minecraft.client.gui.screen.SoundsScreen;
import net.minecraft.client.gui.screen.StreamUnavailableScreen;
import net.minecraft.client.gui.screen.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.LockButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.gui.widget.OptionSliderWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.client.twitch.TwitchStream;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.world.Difficulty;

public class OptionsScreen extends Screen implements ConfirmationListener {
    private static final GameOptions.Option[] RENDER_OPTIONS = new GameOptions.Option[]{GameOptions.Option.FOV};
    private final Screen parent;
    private final GameOptions options;
    private ButtonWidget difficultyButton;
    private LockButtonWidget lockButton;
    protected String title = "Options";

    public OptionsScreen(Screen parent, GameOptions options) {
        this.parent = parent;
        this.options = options;
    }

    @Override
    public void init() {
        int i = 0;
        this.title = I18n.translate("options.title");

        for (GameOptions.Option gameoptions$option : RENDER_OPTIONS) {
            if (gameoptions$option.isFloat()) {
                this.buttons
                    .add(
                        new OptionSliderWidget(
                            gameoptions$option.getId(), this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), gameoptions$option
                        )
                    );
            } else {
                OptionButtonWidget optionbuttonwidget = new OptionButtonWidget(
                    gameoptions$option.getId(),
                    this.width / 2 - 155 + i % 2 * 160,
                    this.height / 6 - 12 + 24 * (i >> 1),
                    gameoptions$option,
                    this.options.getAsString(gameoptions$option)
                );
                this.buttons.add(optionbuttonwidget);
            }

            i++;
        }

        if (this.minecraft.world != null) {
            Difficulty difficulty = this.minecraft.world.getDifficulty();
            this.difficultyButton = new ButtonWidget(
                108, this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), 150, 20, this.getButtonLabel(difficulty)
            );
            this.buttons.add(this.difficultyButton);
            if (this.minecraft.isSingleplayer() && !this.minecraft.world.getData().isHardcore()) {
                this.difficultyButton.setWidth(this.difficultyButton.getWidth() - 20);
                this.lockButton = new LockButtonWidget(109, this.difficultyButton.x + this.difficultyButton.getWidth(), this.difficultyButton.y);
                this.buttons.add(this.lockButton);
                this.lockButton.setLocked(this.minecraft.world.getData().isDifficultyLocked());
                this.lockButton.active = !this.lockButton.isLocked();
                this.difficultyButton.active = !this.lockButton.isLocked();
            } else {
                this.difficultyButton.active = false;
            }
        }

        this.buttons.add(new ButtonWidget(110, this.width / 2 - 155, this.height / 6 + 48 - 6, 150, 20, I18n.translate("options.skinCustomisation")));
        this.buttons
            .add(
                new ButtonWidget(8675309, this.width / 2 + 5, this.height / 6 + 48 - 6, 150, 20, "Super Secret Settings...") {
                    @Override
                    public void playClickSound(SoundManager soundManager) {
                        SoundPool soundpool = soundManager.getRandom(
                            SoundCategory.ANIMALS, SoundCategory.BLOCKS, SoundCategory.MOBS, SoundCategory.PLAYERS, SoundCategory.WEATHER
                        );
                        if (soundpool != null) {
                            soundManager.play(SimpleSoundInstance.of(soundpool.getLocation(), 0.5F));
                        }
                    }
                }
            );
        this.buttons.add(new ButtonWidget(106, this.width / 2 - 155, this.height / 6 + 72 - 6, 150, 20, I18n.translate("options.sounds")));
        this.buttons.add(new ButtonWidget(107, this.width / 2 + 5, this.height / 6 + 72 - 6, 150, 20, I18n.translate("options.stream")));
        this.buttons.add(new ButtonWidget(101, this.width / 2 - 155, this.height / 6 + 96 - 6, 150, 20, I18n.translate("options.video")));
        this.buttons.add(new ButtonWidget(100, this.width / 2 + 5, this.height / 6 + 96 - 6, 150, 20, I18n.translate("options.controls")));
        this.buttons.add(new ButtonWidget(102, this.width / 2 - 155, this.height / 6 + 120 - 6, 150, 20, I18n.translate("options.language")));
        this.buttons.add(new ButtonWidget(103, this.width / 2 + 5, this.height / 6 + 120 - 6, 150, 20, I18n.translate("options.chat.title")));
        this.buttons.add(new ButtonWidget(105, this.width / 2 - 155, this.height / 6 + 144 - 6, 150, 20, I18n.translate("options.resourcepack")));
        this.buttons.add(new ButtonWidget(104, this.width / 2 + 5, this.height / 6 + 144 - 6, 150, 20, I18n.translate("options.snooper.view")));
        this.buttons.add(new ButtonWidget(200, this.width / 2 - 100, this.height / 6 + 168, I18n.translate("gui.done")));
    }

    public String getButtonLabel(Difficulty difficulty) {
        Text text = new LiteralText("");
        text.append(new TranslatableText("options.difficulty"));
        text.append(": ");
        text.append(new TranslatableText(difficulty.getKey()));
        return text.getFormattedString();
    }

    @Override
    public void confirmResult(boolean result, int id) {
        this.minecraft.openScreen(this);
        if (id == 109 && result && this.minecraft.world != null) {
            this.minecraft.world.getData().setDifficultyLocked(true);
            this.lockButton.setLocked(true);
            this.lockButton.active = false;
            this.difficultyButton.active = false;
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id < 100 && button instanceof OptionButtonWidget) {
                GameOptions.Option gameoptions$option = ((OptionButtonWidget)button).getOption();
                this.options.set(gameoptions$option, 1);
                button.message = this.options.getAsString(GameOptions.Option.byId(button.id));
            }

            if (button.id == 108) {
                this.minecraft.world.getData().setDifficulty(Difficulty.byId(this.minecraft.world.getDifficulty().getId() + 1));
                this.difficultyButton.message = this.getButtonLabel(this.minecraft.world.getDifficulty());
            }

            if (button.id == 109) {
                this.minecraft
                    .openScreen(
                        new ConfirmScreen(
                            this,
                            new TranslatableText("difficulty.lock.title").getFormattedString(),
                            new TranslatableText("difficulty.lock.question", new TranslatableText(this.minecraft.world.getData().getDifficulty().getKey()))
                                .getFormattedString(),
                            109
                        )
                    );
            }

            if (button.id == 110) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new SkinCustomizationScreen(this));
            }

            if (button.id == 8675309) {
                this.minecraft.gameRenderer.nextShader();
            }

            if (button.id == 101) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new VideoOptionsScreen(this, this.options));
            }

            if (button.id == 100) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new ControlsOptionsScreen(this, this.options));
            }

            if (button.id == 102) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new LanguageOptionsScreen(this, this.options, this.minecraft.getLanguageManager()));
            }

            if (button.id == 103) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new ChatOptionsScreen(this, this.options));
            }

            if (button.id == 104) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new SnooperScreen(this, this.options));
            }

            if (button.id == 200) {
                this.minecraft.options.save();
                this.minecraft.openScreen(this.parent);
            }

            if (button.id == 105) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new ResourcePacksScreen(this));
            }

            if (button.id == 106) {
                this.minecraft.options.save();
                this.minecraft.openScreen(new SoundsScreen(this, this.options));
            }

            if (button.id == 107) {
                this.minecraft.options.save();
                TwitchStream twitchstream = this.minecraft.getTwitchStream();
                if (twitchstream.canBroadcast() && twitchstream.m_2070231()) {
                    this.minecraft.openScreen(new TwitchOptionsScreen(this, this.options));
                } else {
                    StreamUnavailableScreen.m_8434758(this);
                }
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 15, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }
}
