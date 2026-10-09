package net.minecraft.client.gui.screen.inventory.menu;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.BrewingStandMenu;
import net.minecraft.resource.Identifier;

public class BrewingStandScreen extends InventoryMenuScreen {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/brewing_stand.png");
    private final PlayerInventory playerInventory;
    private Inventory inventory;

    public BrewingStandScreen(PlayerInventory playerInventory, Inventory inventory) {
        super(new BrewingStandMenu(playerInventory, inventory));
        this.playerInventory = playerInventory;
        this.inventory = inventory;
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        String s = this.inventory.getDisplayName().getString();
        this.textRenderer.draw(s, this.backgroundWidth / 2 - this.textRenderer.getWidth(s) / 2, 6, 4210752);
        this.textRenderer.draw(this.playerInventory.getDisplayName().getString(), 8, this.backgroundHeight - 96 + 2, 4210752);
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(i, j, 0, 0, this.backgroundWidth, this.backgroundHeight);
        int k = this.inventory.getData(0);
        if (k > 0) {
            int l = (int)(28.0F * (1.0F - k / 400.0F));
            if (l > 0) {
                this.drawTexture(i + 97, j + 16, 176, 0, 9, l);
            }

            int i1 = k / 2 % 7;
            switch (i1) {
                case 0:
                    l = 29;
                    break;
                case 1:
                    l = 24;
                    break;
                case 2:
                    l = 20;
                    break;
                case 3:
                    l = 16;
                    break;
                case 4:
                    l = 11;
                    break;
                case 5:
                    l = 6;
                    break;
                case 6:
                    l = 0;
            }

            if (l > 0) {
                this.drawTexture(i + 65, j + 14 + 29 - l, 185, 29 - l, 12, l);
            }
        }
    }
}
