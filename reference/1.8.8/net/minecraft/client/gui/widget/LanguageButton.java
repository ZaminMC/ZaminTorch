package net.minecraft.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;

public class LanguageButton extends ButtonWidget {
    public LanguageButton(int x, int y, int id) {
        super(x, y, id, 20, 20, "");
    }

    @Override
    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        if (this.visible) {
            minecraft.getTextureManager().bind(ButtonWidget.WIDGETS_LOCATION);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            boolean flag = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int i = 106;
            if (flag) {
                i += this.height;
            }

            this.drawTexture(this.x, this.y, 0, i, this.width, this.height);
        }
    }
}
