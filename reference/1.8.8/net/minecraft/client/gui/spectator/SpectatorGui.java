package net.minecraft.client.gui.spectator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class SpectatorGui extends GuiElement implements SpectatorMenuListener {
    private static final Identifier WIDGETS_LOCATION = new Identifier("textures/gui/widgets.png");
    public static final Identifier SPECTATOR_WIDGETS_LOCATION = new Identifier("textures/gui/spectator_widgets.png");
    private final Minecraft minecraft;
    private long lastHotbarSelectTime;
    private SpectatorMenu menu;

    public SpectatorGui(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void selectSlot(int slot) {
        this.lastHotbarSelectTime = Minecraft.getTime();
        if (this.menu != null) {
            this.menu.selectSlot(slot);
        } else {
            this.menu = new SpectatorMenu(this);
        }
    }

    private float getHotbarAlpha() {
        long i = this.lastHotbarSelectTime - Minecraft.getTime() + 5000L;
        return MathHelper.clamp((float)i / 2000.0F, 0.0F, 1.0F);
    }

    public void renderHotbar(Window window, float tickDelta) {
        if (this.menu != null) {
            float f = this.getHotbarAlpha();
            if (f <= 0.0F) {
                this.menu.close();
            } else {
                int i = window.getWidth() / 2;
                float f1 = this.drawOffset;
                this.drawOffset = -90.0F;
                float f2 = window.getHeight() - 22.0F * f;
                SpectatorMenuPage spectatormenupage = this.menu.getSelectedPage();
                this.renderMenu(window, f, i, f2, spectatormenupage);
                this.drawOffset = f1;
            }
        }
    }

    protected void renderMenu(Window window, float alpha, int x, float y, SpectatorMenuPage page) {
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, alpha);
        this.minecraft.getTextureManager().bind(WIDGETS_LOCATION);
        this.drawTexture(x - 91, y, 0, 0, 182, 22);
        if (page.getSelectedSlot() >= 0) {
            this.drawTexture(x - 91 - 1 + page.getSelectedSlot() * 20, y - 1.0F, 0, 22, 24, 22);
        }

        Lighting.turnOnGui();

        for (int i = 0; i < 9; i++) {
            this.renderSlot(i, window.getWidth() / 2 - 90 + i * 20 + 2, y + 3.0F, alpha, page.getItem(i));
        }

        Lighting.turnOff();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    private void renderSlot(int slot, int x, float y, float alpha, SpectatorMenuItem menuItem) {
        this.minecraft.getTextureManager().bind(SPECTATOR_WIDGETS_LOCATION);
        if (menuItem != SpectatorMenu.EMPTY_SLOT) {
            int i = (int)(alpha * 255.0F);
            GlStateManager.pushMatrix();
            GlStateManager.translatef(x, y, 0.0F);
            float f = menuItem.isEnabled() ? 1.0F : 0.25F;
            GlStateManager.color4f(f, f, f, alpha);
            menuItem.render(f, i);
            GlStateManager.popMatrix();
            String s = String.valueOf(GameOptions.getKeyName(this.minecraft.options.hotbarKeyBindings[slot].getKeyCode()));
            if (i > 3 && menuItem.isEnabled()) {
                this.minecraft.textRenderer.drawWithShadow(s, x + 19 - 2 - this.minecraft.textRenderer.getWidth(s), y + 6.0F + 3.0F, 16777215 + (i << 24));
            }
        }
    }

    public void renderTooltip(Window window) {
        int i = (int)(this.getHotbarAlpha() * 255.0F);
        if (i > 3 && this.menu != null) {
            SpectatorMenuItem spectatormenuitem = this.menu.getSelectedItem();
            String s = spectatormenuitem != SpectatorMenu.EMPTY_SLOT
                ? spectatormenuitem.getDisplayName().getFormattedString()
                : this.menu.getCategory().getPrompt().getFormattedString();
            if (s != null) {
                int j = (window.getWidth() - this.minecraft.textRenderer.getWidth(s)) / 2;
                int k = window.getHeight() - 35;
                GlStateManager.pushMatrix();
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                this.minecraft.textRenderer.drawWithShadow(s, j, k, 16777215 + (i << 24));
                GlStateManager.disableBlend();
                GlStateManager.popMatrix();
            }
        }
    }

    @Override
    public void onSpectatorMenuClosed(SpectatorMenu menu) {
        this.menu = null;
        this.lastHotbarSelectTime = 0L;
    }

    public boolean isMenuActive() {
        return this.menu != null;
    }

    public void mouseScrolled(int scroll) {
        int i = this.menu.getSelectedSlot() + scroll;

        while (i >= 0 && i <= 8 && (this.menu.getItem(i) == SpectatorMenu.EMPTY_SLOT || !this.menu.getItem(i).isEnabled())) {
            i += scroll;
        }

        if (i >= 0 && i <= 8) {
            this.menu.selectSlot(i);
            this.lastHotbarSelectTime = Minecraft.getTime();
        }
    }

    public void mouseMiddleClicked() {
        this.lastHotbarSelectTime = Minecraft.getTime();
        if (this.isMenuActive()) {
            int i = this.menu.getSelectedSlot();
            if (i != -1) {
                this.menu.selectSlot(i);
            }
        } else {
            this.menu = new SpectatorMenu(this);
        }
    }
}
