package net.minecraft.client.gui.screen;

import java.net.URI;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DemoScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier BACKGROUND_LOCATION = new Identifier("textures/gui/demo_background.png");

    @Override
    public void init() {
        this.buttons.clear();
        int i = -16;
        this.buttons.add(new ButtonWidget(1, this.width / 2 - 116, this.height / 2 + 62 + i, 114, 20, I18n.translate("demo.help.buy")));
        this.buttons.add(new ButtonWidget(2, this.width / 2 + 2, this.height / 2 + 62 + i, 114, 20, I18n.translate("demo.help.later")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        switch (button.id) {
            case 1:
                button.active = false;

                try {
                    Class<?> oclass = Class.forName("java.awt.Desktop");
                    Object object = oclass.getMethod("getDesktop").invoke(null);
                    oclass.getMethod("browse", URI.class).invoke(object, new URI("http://www.minecraft.net/store?source=demo"));
                } catch (Throwable throwable) {
                    LOGGER.error("Couldn't open link", throwable);
                }
                break;
            case 2:
                this.minecraft.openScreen(null);
                this.minecraft.lockMouse();
        }
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void renderBackground() {
        super.renderBackground();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(BACKGROUND_LOCATION);
        int i = (this.width - 248) / 2;
        int j = (this.height - 166) / 2;
        this.drawTexture(i, j, 0, 0, 248, 166);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int i = (this.width - 248) / 2 + 10;
        int j = (this.height - 166) / 2 + 8;
        this.textRenderer.draw(I18n.translate("demo.help.title"), i, j, 2039583);
        j += 12;
        GameOptions gameoptions = this.minecraft.options;
        this.textRenderer
            .draw(
                I18n.translate(
                    "demo.help.movementShort",
                    GameOptions.getKeyName(gameoptions.forwardKey.getKeyCode()),
                    GameOptions.getKeyName(gameoptions.leftKey.getKeyCode()),
                    GameOptions.getKeyName(gameoptions.backKey.getKeyCode()),
                    GameOptions.getKeyName(gameoptions.rightKey.getKeyCode())
                ),
                i,
                j,
                5197647
            );
        this.textRenderer.draw(I18n.translate("demo.help.movementMouse"), i, j + 12, 5197647);
        this.textRenderer.draw(I18n.translate("demo.help.jump", GameOptions.getKeyName(gameoptions.jumpKey.getKeyCode())), i, j + 24, 5197647);
        this.textRenderer.draw(I18n.translate("demo.help.inventory", GameOptions.getKeyName(gameoptions.inventoryKey.getKeyCode())), i, j + 36, 5197647);
        this.textRenderer.splitAndDraw(I18n.translate("demo.help.fullWrapped"), i, j + 68, 218, 2039583);
        super.render(mouseX, mouseY, tickDelta);
    }
}
