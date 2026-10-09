package net.minecraft.client.gui.screen.inventory.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.resource.Identifier;

public class ChestScreen extends InventoryMenuScreen {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/generic_54.png");
    private Inventory playerInventory;
    private Inventory inventory;
    private int rows;

    public ChestScreen(Inventory playerInventory, Inventory inventory) {
        super(new ChestMenu(playerInventory, inventory, Minecraft.getInstance().player));
        this.playerInventory = playerInventory;
        this.inventory = inventory;
        this.passEvents = false;
        int i = 222;
        int j = i - 108;
        this.rows = inventory.getSize() / 9;
        this.backgroundHeight = j + this.rows * 18;
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        this.textRenderer.draw(this.inventory.getDisplayName().getString(), 8, 6, 4210752);
        this.textRenderer.draw(this.playerInventory.getDisplayName().getString(), 8, this.backgroundHeight - 96 + 2, 4210752);
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(i, j, 0, 0, this.backgroundWidth, this.rows * 18 + 17);
        this.drawTexture(i, j + this.rows * 18 + 17, 0, 126, this.backgroundWidth, 96);
    }
}
