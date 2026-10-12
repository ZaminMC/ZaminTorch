package net.minecraft.client.gui.screen.inventory.menu;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.DispenserMenu;
import net.minecraft.resource.Identifier;

public class DispenserScreen extends InventoryMenuScreen {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/dispenser.png");
    private final PlayerInventory playerInventory;
    public Inventory inventory;

    public DispenserScreen(PlayerInventory playerInventory, Inventory inventory) {
        super(new DispenserMenu(playerInventory, inventory));
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
    }
}
