package net.minecraft.client.gui.screen.options;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Window;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.resource.language.Language;
import net.minecraft.client.resource.language.LanguageManager;

public class LanguageOptionsScreen extends Screen {
    protected Screen parent;
    private LanguageOptionsScreen.LanguageSelectionListWidget languageSelectionListWidget;
    private final GameOptions options;
    private final LanguageManager languageManager;
    private OptionButtonWidget forceUnicodeFontButton;
    private OptionButtonWidget doneButton;

    public LanguageOptionsScreen(Screen parent, GameOptions options, LanguageManager languageManager) {
        this.parent = parent;
        this.options = options;
        this.languageManager = languageManager;
    }

    @Override
    public void init() {
        this.buttons
            .add(
                this.forceUnicodeFontButton = new OptionButtonWidget(
                    100,
                    this.width / 2 - 155,
                    this.height - 38,
                    GameOptions.Option.FORCE_UNICODE_FONT,
                    this.options.getAsString(GameOptions.Option.FORCE_UNICODE_FONT)
                )
            );
        this.buttons.add(this.doneButton = new OptionButtonWidget(6, this.width / 2 - 155 + 160, this.height - 38, I18n.translate("gui.done")));
        this.languageSelectionListWidget = new LanguageOptionsScreen.LanguageSelectionListWidget(this.minecraft);
        this.languageSelectionListWidget.setScrollButtonIds(7, 8);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.languageSelectionListWidget.handleMouse();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            switch (button.id) {
                case 5:
                    break;
                case 6:
                    this.minecraft.openScreen(this.parent);
                    break;
                case 100:
                    if (button instanceof OptionButtonWidget) {
                        this.options.set(((OptionButtonWidget)button).getOption(), 1);
                        button.message = this.options.getAsString(GameOptions.Option.FORCE_UNICODE_FONT);
                        Window window = new Window(this.minecraft);
                        int i = window.getWidth();
                        int j = window.getHeight();
                        this.init(this.minecraft, i, j);
                    }
                    break;
                default:
                    this.languageSelectionListWidget.buttonClicked(button);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.languageSelectionListWidget.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, I18n.translate("options.language"), this.width / 2, 16, 16777215);
        this.drawCenteredString(this.textRenderer, "(" + I18n.translate("options.languageWarning") + ")", this.width / 2, this.height - 56, 8421504);
        super.render(mouseX, mouseY, tickDelta);
    }

    class LanguageSelectionListWidget extends ListWidget {
        private final List<String> languageCodes = Lists.newArrayList();
        private final Map<String, Language> languages = Maps.newHashMap();

        public LanguageSelectionListWidget(Minecraft minecraft) {
            super(minecraft, LanguageOptionsScreen.this.width, LanguageOptionsScreen.this.height, 32, LanguageOptionsScreen.this.height - 65 + 4, 18);

            for (Language language : LanguageOptionsScreen.this.languageManager.getLanguages()) {
                this.languages.put(language.getCode(), language);
                this.languageCodes.add(language.getCode());
            }
        }

        @Override
        protected int size() {
            return this.languageCodes.size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            Language language = this.languages.get(this.languageCodes.get(index));
            LanguageOptionsScreen.this.languageManager.setLanguage(language);
            LanguageOptionsScreen.this.options.language = language.getCode();
            this.minecraft.reloadResources();
            LanguageOptionsScreen.this.textRenderer
                .setUnicode(LanguageOptionsScreen.this.languageManager.isUnicode() || LanguageOptionsScreen.this.options.forceUnicodeFont);
            LanguageOptionsScreen.this.textRenderer.setBidirectional(LanguageOptionsScreen.this.languageManager.isBidirectional());
            LanguageOptionsScreen.this.doneButton.message = I18n.translate("gui.done");
            LanguageOptionsScreen.this.forceUnicodeFontButton.message = LanguageOptionsScreen.this.options.getAsString(GameOptions.Option.FORCE_UNICODE_FONT);
            LanguageOptionsScreen.this.options.save();
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return this.languageCodes.get(index).equals(LanguageOptionsScreen.this.languageManager.getLanguage().getCode());
        }

        @Override
        protected int getHeight() {
            return this.size() * 18;
        }

        @Override
        protected void renderBackground() {
            LanguageOptionsScreen.this.renderBackground();
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            LanguageOptionsScreen.this.textRenderer.setBidirectional(true);
            LanguageOptionsScreen.this.drawCenteredString(
                LanguageOptionsScreen.this.textRenderer, this.languages.get(this.languageCodes.get(index)).toString(), this.width / 2, y + 1, 16777215
            );
            LanguageOptionsScreen.this.textRenderer.setBidirectional(LanguageOptionsScreen.this.languageManager.getLanguage().isBidirectional());
        }
    }
}
