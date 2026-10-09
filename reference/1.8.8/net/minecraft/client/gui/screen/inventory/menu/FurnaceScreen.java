package net.minecraft.client.gui.screen.inventory.menu;

import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.FurnaceMenu;
import net.minecraft.resource.Identifier;

public class FurnaceScreen extends InventoryMenuScreen {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/furnace.png");
    private final PlayerInventory playerInventory;
    private Inventory inventory;

    public FurnaceScreen(PlayerInventory playerInventory, Inventory inventory) {
        super(new FurnaceMenu(playerInventory, inventory));
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
        if (FurnaceBlockEntity.isLit(this.inventory)) {
            int k = this.getFireHeight(13);
            this.drawTexture(i + 56, j + 36 + 12 - k, 176, 12 - k, 14, k + 1);
        }

        int l = this.getArrowWidth(24);
        this.drawTexture(i + 79, j + 34, 176, 14, l + 1, 16);
    }

    private int getArrowWidth(int scale) {
        int i = this.inventory.getData(2);
        int j = this.inventory.getData(3);
        return j != 0 && i != 0 ? i * scale / j : 0;
    }

    private int getFireHeight(int scale) {
        int i = this.inventory.getData(1);
        if (i == 0) {
            i = 200;
        }

        return this.inventory.getData(0) * scale / i;
    }
}
