package net.minecraft.client.gui.screen.inventory.menu;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

public abstract class InventoryMenuScreen extends Screen {
    protected static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/inventory.png");
    protected int backgroundWidth = 176;
    protected int backgroundHeight = 166;
    public InventoryMenu menu;
    protected int x;
    protected int y;
    private InventorySlot hoveredSlot;
    private InventorySlot touchDragSlotStart;
    private boolean touchIsRightClickDrag;
    private ItemStack touchDraggedItem;
    private int touchDropX;
    private int touchDropY;
    private InventorySlot touchDropOriginSlot;
    private long touchDropTime;
    private ItemStack touchDropReturningItem;
    private InventorySlot draggedInvSlot;
    private long touchDropTimer;
    protected final Set<InventorySlot> draggedInvSlots = Sets.newHashSet();
    protected boolean isDraggingItem;
    /**
     * The mode of dragging the item stack.
     * <br>0: split evenly across all slots
     * <br>1: add 1 item to every slot
     */
    private int clickDragMode;
    /**
     * The mouse button that is used to drag the item stack
     */
    private int clickDragButton;
    private boolean cancelNextMouseRelease;
    private int draggedItemRemainder;
    private long lastButtonClickTime;
    private InventorySlot clickedInvSlot;
    private int lastClickedButton;
    private boolean isDoubleClicking;
    private ItemStack shiftClickedItem;

    public InventoryMenuScreen(InventoryMenu menu) {
        this.menu = menu;
        this.cancelNextMouseRelease = true;
    }

    @Override
    public void init() {
        super.init();
        this.minecraft.player.menu = this.menu;
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int i = this.x;
        int j = this.y;
        this.renderMenuBackground(tickDelta, mouseX, mouseY);
        GlStateManager.disableRescaleNormal();
        Lighting.turnOff();
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        super.render(mouseX, mouseY, tickDelta);
        Lighting.turnOnGui();
        GlStateManager.pushMatrix();
        GlStateManager.translatef(i, j, 0.0F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableRescaleNormal();
        this.hoveredSlot = null;
        int k = 240;
        int l = 240;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, k / 1.0F, l / 1.0F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        for (int i1 = 0; i1 < this.menu.slots.size(); i1++) {
            InventorySlot inventoryslot = this.menu.slots.get(i1);
            this.renderSlot(inventoryslot);
            if (this.isMouseOverSlot(inventoryslot, mouseX, mouseY) && inventoryslot.isActive()) {
                this.hoveredSlot = inventoryslot;
                GlStateManager.disableLighting();
                GlStateManager.disableDepthTest();
                int j1 = inventoryslot.x;
                int k1 = inventoryslot.y;
                GlStateManager.colorMask(true, true, true, false);
                this.fillGradient(j1, k1, j1 + 16, k1 + 16, -2130706433, -2130706433);
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.enableLighting();
                GlStateManager.enableDepthTest();
            }
        }

        Lighting.turnOff();
        this.renderLabels(mouseX, mouseY);
        Lighting.turnOnGui();
        PlayerInventory playerinventory = this.minecraft.player.inventory;
        ItemStack itemstack = this.touchDraggedItem == null ? playerinventory.getCursorItem() : this.touchDraggedItem;
        if (itemstack != null) {
            int j2 = 8;
            int k2 = this.touchDraggedItem == null ? 8 : 16;
            String s = null;
            if (this.touchDraggedItem != null && this.touchIsRightClickDrag) {
                itemstack = itemstack.copy();
                itemstack.size = MathHelper.ceil(itemstack.size / 2.0F);
            } else if (this.isDraggingItem && this.draggedInvSlots.size() > 1) {
                itemstack = itemstack.copy();
                itemstack.size = this.draggedItemRemainder;
                if (itemstack.size == 0) {
                    s = "" + Formatting.YELLOW + "0";
                }
            }

            this.drawItem(itemstack, mouseX - i - j2, mouseY - j - k2, s);
        }

        if (this.touchDropReturningItem != null) {
            float f = (float)(Minecraft.getTime() - this.touchDropTime) / 100.0F;
            if (f >= 1.0F) {
                f = 1.0F;
                this.touchDropReturningItem = null;
            }

            int l2 = this.touchDropOriginSlot.x - this.touchDropX;
            int i3 = this.touchDropOriginSlot.y - this.touchDropY;
            int l1 = this.touchDropX + (int)(l2 * f);
            int i2 = this.touchDropY + (int)(i3 * f);
            this.drawItem(this.touchDropReturningItem, l1, i2, null);
        }

        GlStateManager.popMatrix();
        if (playerinventory.getCursorItem() == null && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack itemstack1 = this.hoveredSlot.getItem();
            this.renderTooltip(itemstack1, mouseX, mouseY);
        }

        GlStateManager.enableLighting();
        GlStateManager.enableDepthTest();
        Lighting.turnOn();
    }

    private void drawItem(ItemStack item, int x, int y, String itemInfo) {
        GlStateManager.translatef(0.0F, 0.0F, 32.0F);
        this.drawOffset = 200.0F;
        this.itemRenderer.zOffset = 200.0F;
        this.itemRenderer.renderGuiItem(item, x, y);
        this.itemRenderer.renderGuiItemDecorations(this.textRenderer, item, x, y - (this.touchDraggedItem == null ? 0 : 8), itemInfo);
        this.drawOffset = 0.0F;
        this.itemRenderer.zOffset = 0.0F;
    }

    protected void renderLabels(int mouseX, int mouseY) {
    }

    protected abstract void renderMenuBackground(float tickDelta, int mouseX, int mouseY);

    private void renderSlot(InventorySlot slot) {
        int i = slot.x;
        int j = slot.y;
        ItemStack itemstack = slot.getItem();
        boolean flag = false;
        boolean flag1 = slot == this.touchDragSlotStart && this.touchDraggedItem != null && !this.touchIsRightClickDrag;
        ItemStack itemstack1 = this.minecraft.player.inventory.getCursorItem();
        String s = null;
        if (slot == this.touchDragSlotStart && this.touchDraggedItem != null && this.touchIsRightClickDrag && itemstack != null) {
            itemstack = itemstack.copy();
            itemstack.size /= 2;
        } else if (this.isDraggingItem && this.draggedInvSlots.contains(slot) && itemstack1 != null) {
            if (this.draggedInvSlots.size() == 1) {
                return;
            }

            if (InventoryMenu.canClickDragInto(slot, itemstack1, true) && this.menu.canClickDragInto(slot)) {
                itemstack = itemstack1.copy();
                flag = true;
                InventoryMenu.updateClickDragStackSize(this.draggedInvSlots, this.clickDragMode, itemstack, slot.getItem() == null ? 0 : slot.getItem().size);
                if (itemstack.size > itemstack.getMaxSize()) {
                    s = Formatting.YELLOW + "" + itemstack.getMaxSize();
                    itemstack.size = itemstack.getMaxSize();
                }

                if (itemstack.size > slot.getMaxStackSize(itemstack)) {
                    s = Formatting.YELLOW + "" + slot.getMaxStackSize(itemstack);
                    itemstack.size = slot.getMaxStackSize(itemstack);
                }
            } else {
                this.draggedInvSlots.remove(slot);
                this.updateDraggedStackRemainder();
            }
        }

        this.drawOffset = 100.0F;
        this.itemRenderer.zOffset = 100.0F;
        if (itemstack == null) {
            String s1 = slot.getTexture();
            if (s1 != null) {
                TextureAtlasSprite textureatlassprite = this.minecraft.getBlocksAtlas().getSprite(s1);
                GlStateManager.disableLighting();
                this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
                this.drawSprite(i, j, textureatlassprite, 16, 16);
                GlStateManager.enableLighting();
                flag1 = true;
            }
        }

        if (!flag1) {
            if (flag) {
                fill(i, j, i + 16, j + 16, -2130706433);
            }

            GlStateManager.enableDepthTest();
            this.itemRenderer.renderGuiItem(itemstack, i, j);
            this.itemRenderer.renderGuiItemDecorations(this.textRenderer, itemstack, i, j, s);
        }

        this.itemRenderer.zOffset = 0.0F;
        this.drawOffset = 0.0F;
    }

    private void updateDraggedStackRemainder() {
        ItemStack itemstack = this.minecraft.player.inventory.getCursorItem();
        if (itemstack != null && this.isDraggingItem) {
            this.draggedItemRemainder = itemstack.size;

            for (InventorySlot inventoryslot : this.draggedInvSlots) {
                ItemStack itemstack1 = itemstack.copy();
                int i = inventoryslot.getItem() == null ? 0 : inventoryslot.getItem().size;
                InventoryMenu.updateClickDragStackSize(this.draggedInvSlots, this.clickDragMode, itemstack1, i);
                if (itemstack1.size > itemstack1.getMaxSize()) {
                    itemstack1.size = itemstack1.getMaxSize();
                }

                if (itemstack1.size > inventoryslot.getMaxStackSize(itemstack1)) {
                    itemstack1.size = inventoryslot.getMaxStackSize(itemstack1);
                }

                this.draggedItemRemainder = this.draggedItemRemainder - (itemstack1.size - i);
            }
        }
    }

    private InventorySlot getSlot(int x, int y) {
        for (int i = 0; i < this.menu.slots.size(); i++) {
            InventorySlot inventoryslot = this.menu.slots.get(i);
            if (this.isMouseOverSlot(inventoryslot, x, y)) {
                return inventoryslot;
            }
        }

        return null;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        boolean flag = button == this.minecraft.options.pickItemKey.getKeyCode() + 100;
        InventorySlot inventoryslot = this.getSlot(mouseX, mouseY);
        long i = Minecraft.getTime();
        this.isDoubleClicking = this.clickedInvSlot == inventoryslot && i - this.lastButtonClickTime < 250L && this.lastClickedButton == button;
        this.cancelNextMouseRelease = false;
        if (button == 0 || button == 1 || flag) {
            int j = this.x;
            int k = this.y;
            boolean flag1 = mouseX < j || mouseY < k || mouseX >= j + this.backgroundWidth || mouseY >= k + this.backgroundHeight;
            int l = -1;
            if (inventoryslot != null) {
                l = inventoryslot.index;
            }

            if (flag1) {
                l = -999;
            }

            if (this.minecraft.options.touchscreen && flag1 && this.minecraft.player.inventory.getCursorItem() == null) {
                this.minecraft.openScreen(null);
                return;
            }

            if (l != -1) {
                if (this.minecraft.options.touchscreen) {
                    if (inventoryslot != null && inventoryslot.hasItem()) {
                        this.touchDragSlotStart = inventoryslot;
                        this.touchDraggedItem = null;
                        this.touchIsRightClickDrag = button == 1;
                    } else {
                        this.touchDragSlotStart = null;
                    }
                } else if (!this.isDraggingItem) {
                    if (this.minecraft.player.inventory.getCursorItem() == null) {
                        if (button == this.minecraft.options.pickItemKey.getKeyCode() + 100) {
                            this.clickSlot(inventoryslot, l, button, 3);
                        } else {
                            boolean flag2 = l != -999 && (Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54));
                            int i1 = 0;
                            if (flag2) {
                                this.shiftClickedItem = inventoryslot != null && inventoryslot.hasItem() ? inventoryslot.getItem() : null;
                                i1 = 1;
                            } else if (l == -999) {
                                i1 = 4;
                            }

                            this.clickSlot(inventoryslot, l, button, i1);
                        }

                        this.cancelNextMouseRelease = true;
                    } else {
                        this.isDraggingItem = true;
                        this.clickDragButton = button;
                        this.draggedInvSlots.clear();
                        if (button == 0) {
                            this.clickDragMode = 0;
                        } else if (button == 1) {
                            this.clickDragMode = 1;
                        } else if (button == this.minecraft.options.pickItemKey.getKeyCode() + 100) {
                            this.clickDragMode = 2;
                        }
                    }
                }
            }
        }

        this.clickedInvSlot = inventoryslot;
        this.lastButtonClickTime = i;
        this.lastClickedButton = button;
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long duration) {
        InventorySlot inventoryslot = this.getSlot(mouseX, mouseY);
        ItemStack itemstack = this.minecraft.player.inventory.getCursorItem();
        if (this.touchDragSlotStart != null && this.minecraft.options.touchscreen) {
            if (button == 0 || button == 1) {
                if (this.touchDraggedItem == null) {
                    if (inventoryslot != this.touchDragSlotStart && this.touchDragSlotStart.getItem() != null) {
                        this.touchDraggedItem = this.touchDragSlotStart.getItem().copy();
                    }
                } else if (this.touchDraggedItem.size > 1
                    && inventoryslot != null
                    && InventoryMenu.canClickDragInto(inventoryslot, this.touchDraggedItem, false)) {
                    long i = Minecraft.getTime();
                    if (this.draggedInvSlot == inventoryslot) {
                        if (i - this.touchDropTimer > 500L) {
                            this.clickSlot(this.touchDragSlotStart, this.touchDragSlotStart.index, 0, 0);
                            this.clickSlot(inventoryslot, inventoryslot.index, 1, 0);
                            this.clickSlot(this.touchDragSlotStart, this.touchDragSlotStart.index, 0, 0);
                            this.touchDropTimer = i + 750L;
                            this.touchDraggedItem.size--;
                        }
                    } else {
                        this.draggedInvSlot = inventoryslot;
                        this.touchDropTimer = i;
                    }
                }
            }
        } else if (this.isDraggingItem
            && inventoryslot != null
            && itemstack != null
            && itemstack.size > this.draggedInvSlots.size()
            && InventoryMenu.canClickDragInto(inventoryslot, itemstack, true)
            && inventoryslot.isItemAllowed(itemstack)
            && this.menu.canClickDragInto(inventoryslot)) {
            this.draggedInvSlots.add(inventoryslot);
            this.updateDraggedStackRemainder();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        InventorySlot inventoryslot = this.getSlot(mouseX, mouseY);
        int i = this.x;
        int j = this.y;
        boolean flag = mouseX < i || mouseY < j || mouseX >= i + this.backgroundWidth || mouseY >= j + this.backgroundHeight;
        int k = -1;
        if (inventoryslot != null) {
            k = inventoryslot.index;
        }

        if (flag) {
            k = -999;
        }

        if (this.isDoubleClicking && inventoryslot != null && button == 0 && this.menu.canRemoveForPickupAll(null, inventoryslot)) {
            if (isShiftDown()) {
                if (inventoryslot != null && inventoryslot.inventory != null && this.shiftClickedItem != null) {
                    for (InventorySlot inventoryslot2 : this.menu.slots) {
                        if (inventoryslot2 != null
                            && inventoryslot2.canPickUp(this.minecraft.player)
                            && inventoryslot2.hasItem()
                            && inventoryslot2.inventory == inventoryslot.inventory
                            && InventoryMenu.canClickDragInto(inventoryslot2, this.shiftClickedItem, true)) {
                            this.clickSlot(inventoryslot2, inventoryslot2.index, button, 1);
                        }
                    }
                }
            } else {
                this.clickSlot(inventoryslot, k, button, 6);
            }

            this.isDoubleClicking = false;
            this.lastButtonClickTime = 0L;
        } else {
            if (this.isDraggingItem && this.clickDragButton != button) {
                this.isDraggingItem = false;
                this.draggedInvSlots.clear();
                this.cancelNextMouseRelease = true;
                return;
            }

            if (this.cancelNextMouseRelease) {
                this.cancelNextMouseRelease = false;
                return;
            }

            if (this.touchDragSlotStart != null && this.minecraft.options.touchscreen) {
                if (button == 0 || button == 1) {
                    if (this.touchDraggedItem == null && inventoryslot != this.touchDragSlotStart) {
                        this.touchDraggedItem = this.touchDragSlotStart.getItem();
                    }

                    boolean flag2 = InventoryMenu.canClickDragInto(inventoryslot, this.touchDraggedItem, false);
                    if (k != -1 && this.touchDraggedItem != null && flag2) {
                        this.clickSlot(this.touchDragSlotStart, this.touchDragSlotStart.index, button, 0);
                        this.clickSlot(inventoryslot, k, 0, 0);
                        if (this.minecraft.player.inventory.getCursorItem() != null) {
                            this.clickSlot(this.touchDragSlotStart, this.touchDragSlotStart.index, button, 0);
                            this.touchDropX = mouseX - i;
                            this.touchDropY = mouseY - j;
                            this.touchDropOriginSlot = this.touchDragSlotStart;
                            this.touchDropReturningItem = this.touchDraggedItem;
                            this.touchDropTime = Minecraft.getTime();
                        } else {
                            this.touchDropReturningItem = null;
                        }
                    } else if (this.touchDraggedItem != null) {
                        this.touchDropX = mouseX - i;
                        this.touchDropY = mouseY - j;
                        this.touchDropOriginSlot = this.touchDragSlotStart;
                        this.touchDropReturningItem = this.touchDraggedItem;
                        this.touchDropTime = Minecraft.getTime();
                    }

                    this.touchDraggedItem = null;
                    this.touchDragSlotStart = null;
                }
            } else if (this.isDraggingItem && !this.draggedInvSlots.isEmpty()) {
                this.clickSlot(null, -999, InventoryMenu.packClickData(0, this.clickDragMode), 5);

                for (InventorySlot inventoryslot1 : this.draggedInvSlots) {
                    this.clickSlot(inventoryslot1, inventoryslot1.index, InventoryMenu.packClickData(1, this.clickDragMode), 5);
                }

                this.clickSlot(null, -999, InventoryMenu.packClickData(2, this.clickDragMode), 5);
            } else if (this.minecraft.player.inventory.getCursorItem() != null) {
                if (button == this.minecraft.options.pickItemKey.getKeyCode() + 100) {
                    this.clickSlot(inventoryslot, k, button, 3);
                } else {
                    boolean flag1 = k != -999 && (Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54));
                    if (flag1) {
                        this.shiftClickedItem = inventoryslot != null && inventoryslot.hasItem() ? inventoryslot.getItem() : null;
                    }

                    this.clickSlot(inventoryslot, k, button, flag1 ? 1 : 0);
                }
            }
        }

        if (this.minecraft.player.inventory.getCursorItem() == null) {
            this.lastButtonClickTime = 0L;
        }

        this.isDraggingItem = false;
    }

    private boolean isMouseOverSlot(InventorySlot slot, int mouseX, int mouseY) {
        return this.isMouseInRegion(slot.x, slot.y, 16, 16, mouseX, mouseY);
    }

    /**
     * Determine if the mouse is within the region defined by x, y, width, and height.
     * The dimensions of the region are relative to this screen.
     */
    protected boolean isMouseInRegion(int x, int y, int width, int height, int mouseX, int mouseY) {
        int i = this.x;
        int j = this.y;
        mouseX -= i;
        mouseY -= j;
        return mouseX >= x - 1 && mouseX < x + width + 1 && mouseY >= y - 1 && mouseY < y + height + 1;
    }

    protected void clickSlot(InventorySlot actualSlot, int slot, int clickData, int actionType) {
        if (actualSlot != null) {
            slot = actualSlot.index;
        }

        this.minecraft.interactionManager.clickSlot(this.menu.networkId, slot, clickData, actionType, this.minecraft.player);
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == 1 || key == this.minecraft.options.inventoryKey.getKeyCode()) {
            this.minecraft.player.closeMenu();
        }

        this.moveHoveredSlotToHotbar(key);
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (key == this.minecraft.options.pickItemKey.getKeyCode()) {
                this.clickSlot(this.hoveredSlot, this.hoveredSlot.index, 0, 3);
            } else if (key == this.minecraft.options.dropKey.getKeyCode()) {
                this.clickSlot(this.hoveredSlot, this.hoveredSlot.index, isControlDown() ? 1 : 0, 4);
            }
        }
    }

    protected boolean moveHoveredSlotToHotbar(int key) {
        if (this.minecraft.player.inventory.getCursorItem() == null && this.hoveredSlot != null) {
            for (int i = 0; i < 9; i++) {
                if (key == this.minecraft.options.hotbarKeyBindings[i].getKeyCode()) {
                    this.clickSlot(this.hoveredSlot, this.hoveredSlot.index, i, 2);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void removed() {
        if (this.minecraft.player != null) {
            this.menu.close(this.minecraft.player);
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.minecraft.player.isAlive() || this.minecraft.player.removed) {
            this.minecraft.player.closeMenu();
        }
    }
}
