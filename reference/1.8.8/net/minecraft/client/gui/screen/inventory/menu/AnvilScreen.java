package net.minecraft.client.gui.screen.inventory.menu;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.AnvilMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.InventoryMenuListener;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

public class AnvilScreen extends InventoryMenuScreen implements InventoryMenuListener {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/anvil.png");
    private AnvilMenu menu;
    private TextFieldWidget renameTextField;
    private PlayerInventory playerInventory;

    public AnvilScreen(PlayerInventory playerInventory, World world) {
        super(new AnvilMenu(playerInventory, world, Minecraft.getInstance().player));
        this.playerInventory = playerInventory;
        this.menu = (AnvilMenu)this.menu;
    }

    @Override
    public void init() {
        super.init();
        Keyboard.enableRepeatEvents(true);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.renameTextField = new TextFieldWidget(0, this.textRenderer, i + 62, j + 24, 103, 12);
        this.renameTextField.setEditableColor(-1);
        this.renameTextField.setUneditableColor(-1);
        this.renameTextField.setHasBorder(false);
        this.renameTextField.setMaxLength(30);
        this.menu.removeListener(this);
        this.menu.addListener(this);
    }

    @Override
    public void removed() {
        super.removed();
        Keyboard.enableRepeatEvents(false);
        this.menu.removeListener(this);
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        this.textRenderer.draw(I18n.translate("container.repair"), 60, 6, 4210752);
        if (this.menu.repairCost > 0) {
            int i = 8453920;
            boolean flag = true;
            String s = I18n.translate("container.repair.cost", this.menu.repairCost);
            if (this.menu.repairCost >= 40 && !this.minecraft.player.abilities.creativeMode) {
                s = I18n.translate("container.repair.expensive");
                i = 16736352;
            } else if (!this.menu.getSlot(2).hasItem()) {
                flag = false;
            } else if (!this.menu.getSlot(2).canPickUp(this.playerInventory.player)) {
                i = 16736352;
            }

            if (flag) {
                int j = 0xFF000000 | (i & 16579836) >> 2 | i & 0xFF000000;
                int k = this.backgroundWidth - 8 - this.textRenderer.getWidth(s);
                int l = 67;
                if (this.textRenderer.getUnicode()) {
                    fill(k - 3, l - 2, this.backgroundWidth - 7, l + 10, -16777216);
                    fill(k - 2, l - 1, this.backgroundWidth - 8, l + 9, -12895429);
                } else {
                    this.textRenderer.draw(s, k, l + 1, j);
                    this.textRenderer.draw(s, k + 1, l, j);
                    this.textRenderer.draw(s, k + 1, l + 1, j);
                }

                this.textRenderer.draw(s, k, l, i);
            }
        }

        GlStateManager.enableLighting();
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (this.renameTextField.keyPressed(chr, key)) {
            this.sendRenameUpdates();
        } else {
            super.keyPressed(chr, key);
        }
    }

    private void sendRenameUpdates() {
        String s = this.renameTextField.getText();
        InventorySlot inventoryslot = this.menu.getSlot(0);
        if (inventoryslot != null
            && inventoryslot.hasItem()
            && !inventoryslot.getItem().hasCustomHoverName()
            && s.equals(inventoryslot.getItem().getHoverName())) {
            s = "";
        }

        this.menu.setItemName(s);
        this.minecraft.player.networkHandler.sendPacket(new CustomPayloadC2SPacket("MC|ItemName", new PacketByteBuf(Unpooled.buffer()).writeString(s)));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        this.renameTextField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        super.render(mouseX, mouseY, tickDelta);
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        this.renameTextField.render();
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(i, j, 0, 0, this.backgroundWidth, this.backgroundHeight);
        this.drawTexture(i + 59, j + 20, 0, this.backgroundHeight + (this.menu.getSlot(0).hasItem() ? 0 : 16), 110, 16);
        if ((this.menu.getSlot(0).hasItem() || this.menu.getSlot(1).hasItem()) && !this.menu.getSlot(2).hasItem()) {
            this.drawTexture(i + 99, j + 45, this.backgroundWidth, 0, 28, 21);
        }
    }

    @Override
    public void onMenuChanged(InventoryMenu menu, List<ItemStack> items) {
        this.onSlotChanged(menu, 0, menu.getSlot(0).getItem());
    }

    @Override
    public void onSlotChanged(InventoryMenu menu, int slot, ItemStack item) {
        if (slot == 0) {
            this.renameTextField.setText(item == null ? "" : item.getHoverName());
            this.renameTextField.setEditable(item != null);
            if (item != null) {
                this.sendRenameUpdates();
            }
        }
    }

    @Override
    public void onDataChanged(InventoryMenu menu, int id, int value) {
    }

    @Override
    public void updateData(InventoryMenu menu, Inventory inventory) {
    }
}
