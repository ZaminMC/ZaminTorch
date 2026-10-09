package net.minecraft.client.gui.screen.menu;

import com.google.common.base.Predicate;
import java.net.IDN;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.input.Keyboard;

public class AddServerScreen extends Screen {
    private final Screen parent;
    private final ServerListEntry server;
    private TextFieldWidget addressField;
    private TextFieldWidget serverNameField;
    private ButtonWidget resourcePackButton;
    private Predicate<String> addressFilter = new Predicate<String>() {
        public boolean apply(String string) {
            if (string.length() == 0) {
                return true;
            }

            String[] astring = string.split(":");
            if (astring.length == 0) {
                return true;
            }

            try {
                String s = IDN.toASCII(astring[0]);
                return true;
            } catch (IllegalArgumentException illegalargumentexception) {
                return false;
            }
        }
    };

    public AddServerScreen(Screen parent, ServerListEntry server) {
        this.parent = parent;
        this.server = server;
    }

    @Override
    public void tick() {
        this.serverNameField.tick();
        this.addressField.tick();
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.buttons.clear();
        this.buttons.add(new ButtonWidget(0, this.width / 2 - 100, this.height / 4 + 96 + 18, I18n.translate("addServer.add")));
        this.buttons.add(new ButtonWidget(1, this.width / 2 - 100, this.height / 4 + 120 + 18, I18n.translate("gui.cancel")));
        this.buttons
            .add(
                this.resourcePackButton = new ButtonWidget(
                    2,
                    this.width / 2 - 100,
                    this.height / 4 + 72,
                    I18n.translate("addServer.resourcePack") + ": " + this.server.getResourcePackStatus().getMessage().getFormattedString()
                )
            );
        this.serverNameField = new TextFieldWidget(0, this.textRenderer, this.width / 2 - 100, 66, 200, 20);
        this.serverNameField.setFocused(true);
        this.serverNameField.setText(this.server.name);
        this.addressField = new TextFieldWidget(1, this.textRenderer, this.width / 2 - 100, 106, 200, 20);
        this.addressField.setMaxLength(128);
        this.addressField.setText(this.server.ip);
        this.addressField.setFilter(this.addressFilter);
        this.buttons.get(0).active = this.addressField.getText().length() > 0
            && this.addressField.getText().split(":").length > 0
            && this.serverNameField.getText().length() > 0;
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 2) {
                this.server
                    .setResourcePackStatus(
                        ServerListEntry.ResourcePackStatus.values()[(this.server.getResourcePackStatus().ordinal() + 1)
                            % ServerListEntry.ResourcePackStatus.values().length]
                    );
                this.resourcePackButton.message = I18n.translate("addServer.resourcePack")
                    + ": "
                    + this.server.getResourcePackStatus().getMessage().getFormattedString();
            } else if (button.id == 1) {
                this.parent.confirmResult(false, 0);
            } else if (button.id == 0) {
                this.server.name = this.serverNameField.getText();
                this.server.ip = this.addressField.getText();
                this.parent.confirmResult(true, 0);
            }
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        this.serverNameField.keyPressed(chr, key);
        this.addressField.keyPressed(chr, key);
        if (key == 15) {
            this.serverNameField.setFocused(!this.serverNameField.isFocused());
            this.addressField.setFocused(!this.addressField.isFocused());
        }

        if (key == 28 || key == 156) {
            this.buttonClicked(this.buttons.get(0));
        }

        this.buttons.get(0).active = this.addressField.getText().length() > 0
            && this.addressField.getText().split(":").length > 0
            && this.serverNameField.getText().length() > 0;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        this.addressField.mouseClicked(mouseX, mouseY, button);
        this.serverNameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, I18n.translate("addServer.title"), this.width / 2, 17, 16777215);
        this.drawString(this.textRenderer, I18n.translate("addServer.enterName"), this.width / 2 - 100, 53, 10526880);
        this.drawString(this.textRenderer, I18n.translate("addServer.enterIp"), this.width / 2 - 100, 94, 10526880);
        this.serverNameField.render();
        this.addressField.render();
        super.render(mouseX, mouseY, tickDelta);
    }
}
