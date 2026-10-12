package net.minecraft.client.gui.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.resource.language.I18n;

public class VideoOptionsScreen extends Screen {
    private Screen parent;
    protected String title = "Video Settings";
    private GameOptions options;
    private EntryListWidget listWidget;
    private static final GameOptions.Option[] VIDEO_OPTIONS = new GameOptions.Option[]{
        GameOptions.Option.GRAPHICS,
        GameOptions.Option.RENDER_DISTANCE,
        GameOptions.Option.AMBIENT_OCCLUSION,
        GameOptions.Option.FRAMERATE_LIMIT,
        GameOptions.Option.ANAGLYPH,
        GameOptions.Option.VIEW_BOBBING,
        GameOptions.Option.GUI_SCALE,
        GameOptions.Option.GAMMA,
        GameOptions.Option.RENDER_CLOUDS,
        GameOptions.Option.PARTICLES,
        GameOptions.Option.USE_FULLSCREEN,
        GameOptions.Option.ENABLE_VSYNC,
        GameOptions.Option.MAPMAP_LEVELS,
        GameOptions.Option.BLOCK_ALTERNATIVES,
        GameOptions.Option.USE_VBO,
        GameOptions.Option.ENTITY_SHADOWS
    };

    public VideoOptionsScreen(Screen parent, GameOptions options) {
        this.parent = parent;
        this.options = options;
    }

    @Override
    public void init() {
        this.title = I18n.translate("options.videoTitle");
        this.buttons.clear();
        this.buttons.add(new ButtonWidget(200, this.width / 2 - 100, this.height - 27, I18n.translate("gui.done")));
        if (!GLX.useVbos) {
            GameOptions.Option[] agameoptions$option = new GameOptions.Option[VIDEO_OPTIONS.length - 1];
            int i = 0;

            for (GameOptions.Option gameoptions$option : VIDEO_OPTIONS) {
                if (gameoptions$option == GameOptions.Option.USE_VBO) {
                    break;
                }

                agameoptions$option[i] = gameoptions$option;
                i++;
            }

            this.listWidget = new OptionListWidget(this.minecraft, this.width, this.height, 32, this.height - 32, 25, agameoptions$option);
        } else {
            this.listWidget = new OptionListWidget(this.minecraft, this.width, this.height, 32, this.height - 32, 25, VIDEO_OPTIONS);
        }
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.listWidget.handleMouse();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 200) {
                this.minecraft.options.save();
                this.minecraft.openScreen(this.parent);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int i = this.options.guiScale;
        super.mouseClicked(mouseX, mouseY, button);
        this.listWidget.mouseClicked(mouseX, mouseY, button);
        if (this.options.guiScale != i) {
            Window window = new Window(this.minecraft);
            int j = window.getWidth();
            int k = window.getHeight();
            this.init(this.minecraft, j, k);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        int i = this.options.guiScale;
        super.mouseReleased(mouseX, mouseY, button);
        this.listWidget.mouseReleased(mouseX, mouseY, button);
        if (this.options.guiScale != i) {
            Window window = new Window(this.minecraft);
            int j = window.getWidth();
            int k = window.getHeight();
            this.init(this.minecraft, j, k);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.listWidget.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 5, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }
}
