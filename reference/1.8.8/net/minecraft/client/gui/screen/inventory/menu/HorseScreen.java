package net.minecraft.client.gui.screen.inventory.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.HorseMenu;
import net.minecraft.resource.Identifier;

public class HorseScreen extends InventoryMenuScreen {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/horse.png");
    private Inventory armorInventory;
    private Inventory chestInventory;
    private HorseBaseEntity horse;
    private float mouseX;
    private float mouseY;

    public HorseScreen(Inventory armorInventory, Inventory chestInventory, HorseBaseEntity horse) {
        super(new HorseMenu(armorInventory, chestInventory, horse, Minecraft.getInstance().player));
        this.armorInventory = armorInventory;
        this.chestInventory = chestInventory;
        this.horse = horse;
        this.passEvents = false;
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        this.textRenderer.draw(this.chestInventory.getDisplayName().getString(), 8, 6, 4210752);
        this.textRenderer.draw(this.armorInventory.getDisplayName().getString(), 8, this.backgroundHeight - 96 + 2, 4210752);
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(i, j, 0, 0, this.backgroundWidth, this.backgroundHeight);
        if (this.horse.hasChest()) {
            this.drawTexture(i + 79, j + 17, 0, this.backgroundHeight, 90, 54);
        }

        if (this.horse.canHaveArmor()) {
            this.drawTexture(i + 7, j + 35, 0, this.backgroundHeight + 54, 18, 18);
        }

        SurvivalInventoryScreen.renderEntity(i + 51, j + 60, 17, i + 51 - this.mouseX, j + 75 - 50 - this.mouseY, this.horse);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(mouseX, mouseY, tickDelta);
    }
}
