package net.minecraft.client.gui.screen.inventory.menu;

import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.network.packet.c2s.play.SignUpdateC2SPacket;
import net.minecraft.text.LiteralText;
import org.lwjgl.input.Keyboard;

public class SignEditScreen extends Screen {
    private SignBlockEntity sign;
    private int ticks;
    private int row;
    private ButtonWidget doneButton;

    public SignEditScreen(SignBlockEntity sign) {
        this.sign = sign;
    }

    @Override
    public void init() {
        this.buttons.clear();
        Keyboard.enableRepeatEvents(true);
        this.buttons.add(this.doneButton = new ButtonWidget(0, this.width / 2 - 100, this.height / 4 + 120, I18n.translate("gui.done")));
        this.sign.setEditable(false);
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
        ClientPlayNetworkHandler clientplaynetworkhandler = this.minecraft.getNetworkHandler();
        if (clientplaynetworkhandler != null) {
            clientplaynetworkhandler.sendPacket(new SignUpdateC2SPacket(this.sign.getPos(), this.sign.lines));
        }

        this.sign.setEditable(true);
    }

    @Override
    public void tick() {
        this.ticks++;
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 0) {
                this.sign.markDirty();
                this.minecraft.openScreen(null);
            }
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == 200) {
            this.row = this.row - 1 & 3;
        }

        if (key == 208 || key == 28 || key == 156) {
            this.row = this.row + 1 & 3;
        }

        String s = this.sign.lines[this.row].getString();
        if (key == 14 && s.length() > 0) {
            s = s.substring(0, s.length() - 1);
        }

        if (SharedConstants.isValidChatChar(chr) && this.textRenderer.getWidth(s + chr) <= 90) {
            s = s + chr;
        }

        this.sign.lines[this.row] = new LiteralText(s);
        if (key == 1) {
            this.buttonClicked(this.doneButton);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, I18n.translate("sign.edit"), this.width / 2, 40, 16777215);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translatef(this.width / 2, 0.0F, 50.0F);
        float f = 93.75F;
        GlStateManager.scalef(-f, -f, -f);
        GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
        Block block = this.sign.getBlock();
        if (block == Blocks.STANDING_SIGN) {
            float f1 = this.sign.getBlockMetadata() * 360 / 16.0F;
            GlStateManager.rotatef(f1, 0.0F, 1.0F, 0.0F);
            GlStateManager.translatef(0.0F, -1.0625F, 0.0F);
        } else {
            int i = this.sign.getBlockMetadata();
            float f2 = 0.0F;
            if (i == 2) {
                f2 = 180.0F;
            }

            if (i == 4) {
                f2 = 90.0F;
            }

            if (i == 5) {
                f2 = -90.0F;
            }

            GlStateManager.rotatef(f2, 0.0F, 1.0F, 0.0F);
            GlStateManager.translatef(0.0F, -1.0625F, 0.0F);
        }

        if (this.ticks / 6 % 2 == 0) {
            this.sign.currentRow = this.row;
        }

        BlockEntityRenderDispatcher.INSTANCE.render(this.sign, -0.5, -0.75, -0.5, 0.0F);
        this.sign.currentRow = -1;
        GlStateManager.popMatrix();
        super.render(mouseX, mouseY, tickDelta);
    }
}
