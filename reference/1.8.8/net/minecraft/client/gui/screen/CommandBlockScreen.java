package net.minecraft.client.gui.screen;

import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.server.command.source.CommandExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class CommandBlockScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private TextFieldWidget input;
    private TextFieldWidget output;
    private final CommandExecutor executor;
    private ButtonWidget doneButton;
    private ButtonWidget cancelButton;
    private ButtonWidget toggleOutputButton;
    private boolean trackOutput;

    public CommandBlockScreen(CommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void tick() {
        this.input.tick();
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.buttons.clear();
        this.buttons.add(this.doneButton = new ButtonWidget(0, this.width / 2 - 4 - 150, this.height / 4 + 120 + 12, 150, 20, I18n.translate("gui.done")));
        this.buttons.add(this.cancelButton = new ButtonWidget(1, this.width / 2 + 4, this.height / 4 + 120 + 12, 150, 20, I18n.translate("gui.cancel")));
        this.buttons.add(this.toggleOutputButton = new ButtonWidget(4, this.width / 2 + 150 - 20, 150, 20, 20, "O"));
        this.input = new TextFieldWidget(2, this.textRenderer, this.width / 2 - 150, 50, 300, 20);
        this.input.setMaxLength(32767);
        this.input.setFocused(true);
        this.input.setText(this.executor.getCommand());
        this.output = new TextFieldWidget(3, this.textRenderer, this.width / 2 - 150, 150, 276, 20);
        this.output.setMaxLength(32767);
        this.output.setEditable(false);
        this.output.setText("-");
        this.trackOutput = this.executor.trackOutput();
        this.updateToggleOutputButton();
        this.doneButton.active = this.input.getText().trim().length() > 0;
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 1) {
                this.executor.setTrackOutput(this.trackOutput);
                this.minecraft.openScreen(null);
            } else if (button.id == 0) {
                PacketByteBuf packetbytebuf = new PacketByteBuf(Unpooled.buffer());
                packetbytebuf.writeByte(this.executor.getType());
                this.executor.writeInfo(packetbytebuf);
                packetbytebuf.writeString(this.input.getText());
                packetbytebuf.writeBoolean(this.executor.trackOutput());
                this.minecraft.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket("MC|AdvCdm", packetbytebuf));
                if (!this.executor.trackOutput()) {
                    this.executor.setLastOutput(null);
                }

                this.minecraft.openScreen(null);
            } else if (button.id == 4) {
                this.executor.setTrackOutput(!this.executor.trackOutput());
                this.updateToggleOutputButton();
            }
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        this.input.keyPressed(chr, key);
        this.output.keyPressed(chr, key);
        this.doneButton.active = this.input.getText().trim().length() > 0;
        if (key == 28 || key == 156) {
            this.buttonClicked(this.doneButton);
        } else if (key == 1) {
            this.buttonClicked(this.cancelButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        this.input.mouseClicked(mouseX, mouseY, button);
        this.output.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, I18n.translate("advMode.setCommand"), this.width / 2, 20, 16777215);
        this.drawString(this.textRenderer, I18n.translate("advMode.command"), this.width / 2 - 150, 37, 10526880);
        this.input.render();
        int i = 75;
        int j = 0;
        this.drawString(this.textRenderer, I18n.translate("advMode.nearestPlayer"), this.width / 2 - 150, i + j++ * this.textRenderer.fontHeight, 10526880);
        this.drawString(this.textRenderer, I18n.translate("advMode.randomPlayer"), this.width / 2 - 150, i + j++ * this.textRenderer.fontHeight, 10526880);
        this.drawString(this.textRenderer, I18n.translate("advMode.allPlayers"), this.width / 2 - 150, i + j++ * this.textRenderer.fontHeight, 10526880);
        this.drawString(this.textRenderer, I18n.translate("advMode.allEntities"), this.width / 2 - 150, i + j++ * this.textRenderer.fontHeight, 10526880);
        this.drawString(this.textRenderer, "", this.width / 2 - 150, i + j++ * this.textRenderer.fontHeight, 10526880);
        if (this.output.getText().length() > 0) {
            i += j * this.textRenderer.fontHeight + 16;
            this.drawString(this.textRenderer, I18n.translate("advMode.previousOutput"), this.width / 2 - 150, i, 10526880);
            this.output.render();
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    private void updateToggleOutputButton() {
        if (this.executor.trackOutput()) {
            this.toggleOutputButton.message = "O";
            if (this.executor.getLastOutput() != null) {
                this.output.setText(this.executor.getLastOutput().getString());
            }
        } else {
            this.toggleOutputButton.message = "X";
            this.output.setText("-");
        }
    }
}
