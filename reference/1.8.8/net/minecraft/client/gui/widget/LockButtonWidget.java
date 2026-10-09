package net.minecraft.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;

public class LockButtonWidget extends ButtonWidget {
    private boolean locked = false;

    public LockButtonWidget(int x, int y, int id) {
        super(x, y, id, 20, 20, "");
    }

    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        if (this.visible) {
            minecraft.getTextureManager().bind(ButtonWidget.WIDGETS_LOCATION);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            boolean flag = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            LockButtonWidget.Icon lockbuttonwidget$icon;
            if (this.locked) {
                if (!this.active) {
                    lockbuttonwidget$icon = LockButtonWidget.Icon.LOCKED_DISABLED;
                } else if (flag) {
                    lockbuttonwidget$icon = LockButtonWidget.Icon.LOCKED_HOVER;
                } else {
                    lockbuttonwidget$icon = LockButtonWidget.Icon.LOCKED;
                }
            } else if (!this.active) {
                lockbuttonwidget$icon = LockButtonWidget.Icon.UNLOCKED_DISABLED;
            } else if (flag) {
                lockbuttonwidget$icon = LockButtonWidget.Icon.UNLOCKED_HOVER;
            } else {
                lockbuttonwidget$icon = LockButtonWidget.Icon.UNLOCKED;
            }

            this.drawTexture(this.x, this.y, lockbuttonwidget$icon.getU(), lockbuttonwidget$icon.getV(), this.width, this.height);
        }
    }

    enum Icon {
        LOCKED(0, 146),
        LOCKED_HOVER(0, 166),
        LOCKED_DISABLED(0, 186),
        UNLOCKED(20, 146),
        UNLOCKED_HOVER(20, 166),
        UNLOCKED_DISABLED(20, 186);

        private final int u;
        private final int v;

        Icon(int u, int v) {
            this.u = u;
            this.v = v;
        }

        public int getU() {
            return this.u;
        }

        public int getV() {
            return this.v;
        }
    }
}
