package net.minecraft.client.gui.screen.inventory.menu;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.CraftingTableMenu;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CraftingTableScreen extends InventoryMenuScreen {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/crafting_table.png");

    public CraftingTableScreen(PlayerInventory playerInventory, World world) {
        this(playerInventory, world, BlockPos.ORIGIN);
    }

    public CraftingTableScreen(PlayerInventory playerInventory, World world, BlockPos pos) {
        super(new CraftingTableMenu(playerInventory, world, pos));
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        this.textRenderer.draw(I18n.translate("container.crafting"), 28, 6, 4210752);
        this.textRenderer.draw(I18n.translate("container.inventory"), 8, this.backgroundHeight - 96 + 2, 4210752);
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
