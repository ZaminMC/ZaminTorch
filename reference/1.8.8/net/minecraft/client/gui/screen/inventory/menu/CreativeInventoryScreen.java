package net.minecraft.client.gui.screen.inventory.menu;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.menu.AchievementsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class CreativeInventoryScreen extends PlayerInventoryScreen {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/creative_inventory/tabs.png");
    private static SimpleInventory INVENTORY = new SimpleInventory("tmp", true, 45);
    private static int selectedTab = CreativeModeTab.BUILDING_BLOCKS.getId();
    private float scrollPosition;
    private boolean hasScrollBar;
    private boolean isMouseButtonDown;
    private TextFieldWidget searchField;
    private List<InventorySlot> slots;
    private InventorySlot slot;
    private boolean scrolling;
    private CreativeInventoryListener listener;

    public CreativeInventoryScreen(PlayerEntity player) {
        super(new CreativeInventoryScreen.CreativePlayerMenu(player));
        player.menu = this.menu;
        this.passEvents = true;
        this.backgroundHeight = 136;
        this.backgroundWidth = 195;
    }

    @Override
    public void tick() {
        if (!this.minecraft.interactionManager.hasCreativeInventory()) {
            this.minecraft.openScreen(new SurvivalInventoryScreen(this.minecraft.player));
        }

        this.checkStatusEffects();
    }

    @Override
    protected void clickSlot(InventorySlot actualSlot, int slot, int clickData, int actionType) {
        this.scrolling = true;
        boolean flag = actionType == 1;
        actionType = slot == -999 && actionType == 0 ? 4 : actionType;
        if (actualSlot == null && selectedTab != CreativeModeTab.INVENTORY.getId() && actionType != 5) {
            PlayerInventory playerinventory1 = this.minecraft.player.inventory;
            if (playerinventory1.getCursorItem() != null) {
                if (clickData == 0) {
                    this.minecraft.player.dropItem(playerinventory1.getCursorItem(), true);
                    this.minecraft.interactionManager.dropItemFromCreativeMenu(playerinventory1.getCursorItem());
                    playerinventory1.setCursorItem(null);
                }

                if (clickData == 1) {
                    ItemStack itemstack5 = playerinventory1.getCursorItem().split(1);
                    this.minecraft.player.dropItem(itemstack5, true);
                    this.minecraft.interactionManager.dropItemFromCreativeMenu(itemstack5);
                    if (playerinventory1.getCursorItem().size == 0) {
                        playerinventory1.setCursorItem(null);
                    }
                }
            }
        } else if (actualSlot == this.slot && flag) {
            for (int j = 0; j < this.minecraft.player.playerMenu.getItems().size(); j++) {
                this.minecraft.interactionManager.addItemToCreativeMenu(null, j);
            }
        } else if (selectedTab == CreativeModeTab.INVENTORY.getId()) {
            if (actualSlot == this.slot) {
                this.minecraft.player.inventory.setCursorItem(null);
            } else if (actionType == 4 && actualSlot != null && actualSlot.hasItem()) {
                ItemStack itemstack = actualSlot.removeItem(clickData == 0 ? 1 : actualSlot.getItem().getMaxSize());
                this.minecraft.player.dropItem(itemstack, true);
                this.minecraft.interactionManager.dropItemFromCreativeMenu(itemstack);
            } else if (actionType == 4 && this.minecraft.player.inventory.getCursorItem() != null) {
                this.minecraft.player.dropItem(this.minecraft.player.inventory.getCursorItem(), true);
                this.minecraft.interactionManager.dropItemFromCreativeMenu(this.minecraft.player.inventory.getCursorItem());
                this.minecraft.player.inventory.setCursorItem(null);
            } else {
                this.minecraft
                    .player
                    .playerMenu
                    .onClickSlot(
                        actualSlot == null ? slot : ((CreativeInventoryScreen.CreativeInventorySlot)actualSlot).slot.index,
                        clickData,
                        actionType,
                        this.minecraft.player
                    );
                this.minecraft.player.playerMenu.updateListeners();
            }
        } else if (actionType != 5 && actualSlot.inventory == INVENTORY) {
            PlayerInventory playerinventory = this.minecraft.player.inventory;
            ItemStack itemstack1 = playerinventory.getCursorItem();
            ItemStack itemstack2 = actualSlot.getItem();
            if (actionType == 2) {
                if (itemstack2 != null && clickData >= 0 && clickData < 9) {
                    ItemStack itemstack7 = itemstack2.copy();
                    itemstack7.size = itemstack7.getMaxSize();
                    this.minecraft.player.inventory.setItem(clickData, itemstack7);
                    this.minecraft.player.playerMenu.updateListeners();
                }

                return;
            }

            if (actionType == 3) {
                if (playerinventory.getCursorItem() == null && actualSlot.hasItem()) {
                    ItemStack itemstack6 = actualSlot.getItem().copy();
                    itemstack6.size = itemstack6.getMaxSize();
                    playerinventory.setCursorItem(itemstack6);
                }

                return;
            }

            if (actionType == 4) {
                if (itemstack2 != null) {
                    ItemStack itemstack3 = itemstack2.copy();
                    itemstack3.size = clickData == 0 ? 1 : itemstack3.getMaxSize();
                    this.minecraft.player.dropItem(itemstack3, true);
                    this.minecraft.interactionManager.dropItemFromCreativeMenu(itemstack3);
                }

                return;
            }

            if (itemstack1 != null && itemstack2 != null && itemstack1.matchesItem(itemstack2)) {
                if (clickData == 0) {
                    if (flag) {
                        itemstack1.size = itemstack1.getMaxSize();
                    } else if (itemstack1.size < itemstack1.getMaxSize()) {
                        itemstack1.size++;
                    }
                } else if (itemstack1.size <= 1) {
                    playerinventory.setCursorItem(null);
                } else {
                    itemstack1.size--;
                }
            } else if (itemstack2 != null && itemstack1 == null) {
                playerinventory.setCursorItem(ItemStack.copyOf(itemstack2));
                itemstack1 = playerinventory.getCursorItem();
                if (flag) {
                    itemstack1.size = itemstack1.getMaxSize();
                }
            } else {
                playerinventory.setCursorItem(null);
            }
        } else {
            this.menu.onClickSlot(actualSlot == null ? slot : actualSlot.index, clickData, actionType, this.minecraft.player);
            if (InventoryMenu.unpackClickDragStage(clickData) == 2) {
                for (int i = 0; i < 9; i++) {
                    this.minecraft.interactionManager.addItemToCreativeMenu(this.menu.getSlot(45 + i).getItem(), 36 + i);
                }
            } else if (actualSlot != null) {
                ItemStack itemstack4 = this.menu.getSlot(actualSlot.index).getItem();
                this.minecraft.interactionManager.addItemToCreativeMenu(itemstack4, actualSlot.index - this.menu.slots.size() + 9 + 36);
            }
        }
    }

    @Override
    protected void checkStatusEffects() {
        int i = this.x;
        super.checkStatusEffects();
        if (this.searchField != null && this.x != i) {
            this.searchField.x = this.x + 82;
        }
    }

    @Override
    public void init() {
        if (this.minecraft.interactionManager.hasCreativeInventory()) {
            super.init();
            this.buttons.clear();
            Keyboard.enableRepeatEvents(true);
            this.searchField = new TextFieldWidget(0, this.textRenderer, this.x + 82, this.y + 6, 89, this.textRenderer.fontHeight);
            this.searchField.setMaxLength(15);
            this.searchField.setHasBorder(false);
            this.searchField.setVisible(false);
            this.searchField.setEditableColor(16777215);
            int i = selectedTab;
            selectedTab = -1;
            this.setSelectedTab(CreativeModeTab.ALL[i]);
            this.listener = new CreativeInventoryListener(this.minecraft);
            this.minecraft.player.playerMenu.addListener(this.listener);
        } else {
            this.minecraft.openScreen(new SurvivalInventoryScreen(this.minecraft.player));
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (this.minecraft.player != null && this.minecraft.player.inventory != null) {
            this.minecraft.player.playerMenu.removeListener(this.listener);
        }

        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (selectedTab != CreativeModeTab.SEARCH.getId()) {
            if (GameOptions.isPressed(this.minecraft.options.chatKey)) {
                this.setSelectedTab(CreativeModeTab.SEARCH);
            } else {
                super.keyPressed(chr, key);
            }
        } else {
            if (this.scrolling) {
                this.scrolling = false;
                this.searchField.setText("");
            }

            if (!this.moveHoveredSlotToHotbar(key)) {
                if (this.searchField.keyPressed(chr, key)) {
                    this.search();
                } else {
                    super.keyPressed(chr, key);
                }
            }
        }
    }

    private void search() {
        CreativeInventoryScreen.CreativePlayerMenu creativeinventoryscreen$creativeplayermenu = (CreativeInventoryScreen.CreativePlayerMenu)this.menu;
        creativeinventoryscreen$creativeplayermenu.items.clear();

        for (Item item : Item.REGISTRY) {
            if (item != null && item.getCreativeModeTab() != null) {
                item.addToCreativeMenu(item, null, creativeinventoryscreen$creativeplayermenu.items);
            }
        }

        for (Enchantment enchantment : Enchantment.ALL) {
            if (enchantment != null && enchantment.category != null) {
                Items.ENCHANTED_BOOK.addToCreativeMenu(enchantment, creativeinventoryscreen$creativeplayermenu.items);
            }
        }

        Iterator<ItemStack> iterator = creativeinventoryscreen$creativeplayermenu.items.iterator();
        String s1 = this.searchField.getText().toLowerCase();

        while (iterator.hasNext()) {
            ItemStack itemstack = iterator.next();
            boolean flag = false;

            for (String s : itemstack.getTooltip(this.minecraft.player, this.minecraft.options.advancedItemTooltips)) {
                if (Formatting.strip(s).toLowerCase().contains(s1)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                iterator.remove();
            }
        }

        this.scrollPosition = 0.0F;
        creativeinventoryscreen$creativeplayermenu.scrollItems(0.0F);
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        CreativeModeTab creativemodetab = CreativeModeTab.ALL[selectedTab];
        if (creativemodetab.hasTooltips()) {
            GlStateManager.disableBlend();
            this.textRenderer.draw(I18n.translate(creativemodetab.getDisplayName()), 8, 6, 4210752);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            int i = mouseX - this.x;
            int j = mouseY - this.y;

            for (CreativeModeTab creativemodetab : CreativeModeTab.ALL) {
                if (this.isInRow(creativemodetab, i, j)) {
                    return;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            int i = mouseX - this.x;
            int j = mouseY - this.y;

            for (CreativeModeTab creativemodetab : CreativeModeTab.ALL) {
                if (this.isInRow(creativemodetab, i, j)) {
                    this.setSelectedTab(creativemodetab);
                    return;
                }
            }
        }

        super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean hasScrollbar() {
        return selectedTab != CreativeModeTab.INVENTORY.getId()
            && CreativeModeTab.ALL[selectedTab].hasScrollbar()
            && ((CreativeInventoryScreen.CreativePlayerMenu)this.menu).isMaxTabsReached();
    }

    private void setSelectedTab(CreativeModeTab tab) {
        int i = selectedTab;
        selectedTab = tab.getId();
        CreativeInventoryScreen.CreativePlayerMenu creativeinventoryscreen$creativeplayermenu = (CreativeInventoryScreen.CreativePlayerMenu)this.menu;
        this.draggedInvSlots.clear();
        creativeinventoryscreen$creativeplayermenu.items.clear();
        tab.addItems(creativeinventoryscreen$creativeplayermenu.items);
        if (tab == CreativeModeTab.INVENTORY) {
            InventoryMenu inventorymenu = this.minecraft.player.playerMenu;
            if (this.slots == null) {
                this.slots = creativeinventoryscreen$creativeplayermenu.slots;
            }

            creativeinventoryscreen$creativeplayermenu.slots = Lists.newArrayList();

            for (int j = 0; j < inventorymenu.slots.size(); j++) {
                InventorySlot inventoryslot = new CreativeInventoryScreen.CreativeInventorySlot(inventorymenu.slots.get(j), j);
                creativeinventoryscreen$creativeplayermenu.slots.add(inventoryslot);
                if (j >= 5 && j < 9) {
                    int j1 = j - 5;
                    int k1 = j1 / 2;
                    int l1 = j1 % 2;
                    inventoryslot.x = 9 + k1 * 54;
                    inventoryslot.y = 6 + l1 * 27;
                } else if (j >= 0 && j < 5) {
                    inventoryslot.y = -2000;
                    inventoryslot.x = -2000;
                } else if (j < inventorymenu.slots.size()) {
                    int k = j - 9;
                    int l = k % 9;
                    int i1 = k / 9;
                    inventoryslot.x = 9 + l * 18;
                    if (j >= 36) {
                        inventoryslot.y = 112;
                    } else {
                        inventoryslot.y = 54 + i1 * 18;
                    }
                }
            }

            this.slot = new InventorySlot(INVENTORY, 0, 173, 112);
            creativeinventoryscreen$creativeplayermenu.slots.add(this.slot);
        } else if (i == CreativeModeTab.INVENTORY.getId()) {
            creativeinventoryscreen$creativeplayermenu.slots = this.slots;
            this.slots = null;
        }

        if (this.searchField != null) {
            if (tab == CreativeModeTab.SEARCH) {
                this.searchField.setVisible(true);
                this.searchField.setFocusUnlocked(false);
                this.searchField.setFocused(true);
                this.searchField.setText("");
                this.search();
            } else {
                this.searchField.setVisible(false);
                this.searchField.setFocusUnlocked(true);
                this.searchField.setFocused(false);
            }
        }

        this.scrollPosition = 0.0F;
        creativeinventoryscreen$creativeplayermenu.scrollItems(0.0F);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int i = Mouse.getEventDWheel();
        if (i != 0 && this.hasScrollbar()) {
            int j = ((CreativeInventoryScreen.CreativePlayerMenu)this.menu).items.size() / 9 - 5;
            if (i > 0) {
                i = 1;
            }

            if (i < 0) {
                i = -1;
            }

            this.scrollPosition = (float)(this.scrollPosition - (double)i / j);
            this.scrollPosition = MathHelper.clamp(this.scrollPosition, 0.0F, 1.0F);
            ((CreativeInventoryScreen.CreativePlayerMenu)this.menu).scrollItems(this.scrollPosition);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        boolean flag = Mouse.isButtonDown(0);
        int i = this.x;
        int j = this.y;
        int k = i + 175;
        int l = j + 18;
        int i1 = k + 14;
        int j1 = l + 112;
        if (!this.isMouseButtonDown && flag && mouseX >= k && mouseY >= l && mouseX < i1 && mouseY < j1) {
            this.hasScrollBar = this.hasScrollbar();
        }

        if (!flag) {
            this.hasScrollBar = false;
        }

        this.isMouseButtonDown = flag;
        if (this.hasScrollBar) {
            this.scrollPosition = (mouseY - l - 7.5F) / (j1 - l - 15.0F);
            this.scrollPosition = MathHelper.clamp(this.scrollPosition, 0.0F, 1.0F);
            ((CreativeInventoryScreen.CreativePlayerMenu)this.menu).scrollItems(this.scrollPosition);
        }

        super.render(mouseX, mouseY, tickDelta);

        for (CreativeModeTab creativemodetab : CreativeModeTab.ALL) {
            if (this.renderTabTooltipIfHovered(creativemodetab, mouseX, mouseY)) {
                break;
            }
        }

        if (this.slot != null && selectedTab == CreativeModeTab.INVENTORY.getId() && this.isMouseInRegion(this.slot.x, this.slot.y, 16, 16, mouseX, mouseY)) {
            this.renderTooltip(I18n.translate("inventory.binSlot"), mouseX, mouseY);
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
    }

    @Override
    protected void renderTooltip(ItemStack item, int mouseX, int mouseY) {
        if (selectedTab == CreativeModeTab.SEARCH.getId()) {
            List<String> list = item.getTooltip(this.minecraft.player, this.minecraft.options.advancedItemTooltips);
            CreativeModeTab creativemodetab = item.getItem().getCreativeModeTab();
            if (creativemodetab == null && item.getItem() == Items.ENCHANTED_BOOK) {
                Map<Integer, Integer> map = EnchantmentHelper.getEnchantments(item);
                if (map.size() == 1) {
                    Enchantment enchantment = Enchantment.byId(map.keySet().iterator().next());

                    for (CreativeModeTab creativemodetab1 : CreativeModeTab.ALL) {
                        if (creativemodetab1.hasEchantmentCategory(enchantment.category)) {
                            creativemodetab = creativemodetab1;
                            break;
                        }
                    }
                }
            }

            if (creativemodetab != null) {
                list.add(1, "" + Formatting.BOLD + Formatting.BLUE + I18n.translate(creativemodetab.getDisplayName()));
            }

            for (int i = 0; i < list.size(); i++) {
                if (i == 0) {
                    list.set(i, item.getRarity().formatting + list.get(i));
                } else {
                    list.set(i, Formatting.GRAY + list.get(i));
                }
            }

            this.renderTooltip(list, mouseX, mouseY);
        } else {
            super.renderTooltip(item, mouseX, mouseY);
        }
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Lighting.turnOnGui();
        CreativeModeTab creativemodetab = CreativeModeTab.ALL[selectedTab];

        for (CreativeModeTab creativemodetab1 : CreativeModeTab.ALL) {
            this.minecraft.getTextureManager().bind(MENU_LOCATION);
            if (creativemodetab1.getId() != selectedTab) {
                this.renderTabIcon(creativemodetab1);
            }
        }

        this.minecraft.getTextureManager().bind(new Identifier("textures/gui/container/creative_inventory/tab_" + creativemodetab.getTexture()));
        this.drawTexture(this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
        this.searchField.render();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        int i = this.x + 175;
        int j = this.y + 18;
        int k = j + 112;
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        if (creativemodetab.hasScrollbar()) {
            this.drawTexture(i, j + (int)((k - j - 17) * this.scrollPosition), 232 + (this.hasScrollbar() ? 0 : 12), 0, 12, 15);
        }

        this.renderTabIcon(creativemodetab);
        if (creativemodetab == CreativeModeTab.INVENTORY) {
            SurvivalInventoryScreen.renderEntity(this.x + 43, this.y + 45, 20, this.x + 43 - mouseX, this.y + 45 - 30 - mouseY, this.minecraft.player);
        }
    }

    protected boolean isInRow(CreativeModeTab tab, int x, int z) {
        int i = tab.getColumn();
        int j = 28 * i;
        int k = 0;
        if (i == 5) {
            j = this.backgroundWidth - 28 + 2;
        } else if (i > 0) {
            j += i;
        }

        if (tab.isTopRow()) {
            k -= 32;
        } else {
            k += this.backgroundHeight;
        }

        return x >= j && x <= j + 28 && z >= k && z <= k + 32;
    }

    protected boolean renderTabTooltipIfHovered(CreativeModeTab tab, int mouseX, int mouseY) {
        int i = tab.getColumn();
        int j = 28 * i;
        int k = 0;
        if (i == 5) {
            j = this.backgroundWidth - 28 + 2;
        } else if (i > 0) {
            j += i;
        }

        if (tab.isTopRow()) {
            k -= 32;
        } else {
            k += this.backgroundHeight;
        }

        if (this.isMouseInRegion(j + 3, k + 3, 23, 27, mouseX, mouseY)) {
            this.renderTooltip(I18n.translate(tab.getDisplayName()), mouseX, mouseY);
            return true;
        } else {
            return false;
        }
    }

    protected void renderTabIcon(CreativeModeTab tab) {
        boolean flag = tab.getId() == selectedTab;
        boolean flag1 = tab.isTopRow();
        int i = tab.getColumn();
        int j = i * 28;
        int k = 0;
        int l = this.x + 28 * i;
        int i1 = this.y;
        int j1 = 32;
        if (flag) {
            k += 32;
        }

        if (i == 5) {
            l = this.x + this.backgroundWidth - 28;
        } else if (i > 0) {
            l += i;
        }

        if (flag1) {
            i1 -= 28;
        } else {
            k += 64;
            i1 += this.backgroundHeight - 4;
        }

        GlStateManager.disableLighting();
        this.drawTexture(l, i1, j, k, 28, j1);
        this.drawOffset = 100.0F;
        this.itemRenderer.zOffset = 100.0F;
        l += 6;
        i1 += 8 + (flag1 ? 1 : -1);
        GlStateManager.enableLighting();
        GlStateManager.enableRescaleNormal();
        ItemStack itemstack = tab.getIcon();
        this.itemRenderer.renderGuiItem(itemstack, l, i1);
        this.itemRenderer.renderGuiItemDecoration(this.textRenderer, itemstack, l, i1);
        GlStateManager.disableLighting();
        this.itemRenderer.zOffset = 0.0F;
        this.drawOffset = 0.0F;
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0) {
            this.minecraft.openScreen(new AchievementsScreen(this, this.minecraft.player.getStats()));
        }

        if (button.id == 1) {
            this.minecraft.openScreen(new StatsScreen(this, this.minecraft.player.getStats()));
        }
    }

    public int getSelectedTab() {
        return selectedTab;
    }

    class CreativeInventorySlot extends InventorySlot {
        private final InventorySlot slot;

        public CreativeInventorySlot(InventorySlot slot, int id) {
            super(slot.inventory, id, 0, 0);
            this.slot = slot;
        }

        @Override
        public void onItemRemoved(PlayerEntity player, ItemStack item) {
            this.slot.onItemRemoved(player, item);
        }

        @Override
        public boolean isItemAllowed(ItemStack item) {
            return this.slot.isItemAllowed(item);
        }

        @Override
        public ItemStack getItem() {
            return this.slot.getItem();
        }

        @Override
        public boolean hasItem() {
            return this.slot.hasItem();
        }

        @Override
        public void setItem(ItemStack item) {
            this.slot.setItem(item);
        }

        @Override
        public void markDirty() {
            this.slot.markDirty();
        }

        @Override
        public int getMaxStackSize() {
            return this.slot.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack item) {
            return this.slot.getMaxStackSize(item);
        }

        @Override
        public String getTexture() {
            return this.slot.getTexture();
        }

        @Override
        public ItemStack removeItem(int amount) {
            return this.slot.removeItem(amount);
        }

        @Override
        public boolean equals(Inventory inventory, int slot) {
            return this.slot.equals(inventory, slot);
        }
    }

    static class CreativePlayerMenu extends InventoryMenu {
        public List<ItemStack> items = Lists.newArrayList();

        public CreativePlayerMenu(PlayerEntity player) {
            PlayerInventory playerinventory = player.inventory;

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 9; j++) {
                    this.addSlot(new InventorySlot(CreativeInventoryScreen.INVENTORY, i * 9 + j, 9 + j * 18, 18 + i * 18));
                }
            }

            for (int k = 0; k < 9; k++) {
                this.addSlot(new InventorySlot(playerinventory, k, 9 + k * 18, 112));
            }

            this.scrollItems(0.0F);
        }

        @Override
        public boolean isValid(PlayerEntity player) {
            return true;
        }

        public void scrollItems(float position) {
            int i = (this.items.size() + 9 - 1) / 9 - 5;
            int j = (int)(position * i + 0.5);
            if (j < 0) {
                j = 0;
            }

            for (int k = 0; k < 5; k++) {
                for (int l = 0; l < 9; l++) {
                    int i1 = l + (k + j) * 9;
                    if (i1 >= 0 && i1 < this.items.size()) {
                        CreativeInventoryScreen.INVENTORY.setItem(l + k * 9, this.items.get(i1));
                    } else {
                        CreativeInventoryScreen.INVENTORY.setItem(l + k * 9, null);
                    }
                }
            }
        }

        public boolean isMaxTabsReached() {
            return this.items.size() > 45;
        }

        @Override
        protected void clickSlot(int slot, int button, boolean quickMove, PlayerEntity player) {
        }

        @Override
        public ItemStack quickMoveItem(PlayerEntity player, int slot) {
            if (slot >= this.slots.size() - 9 && slot < this.slots.size()) {
                InventorySlot inventoryslot = this.slots.get(slot);
                if (inventoryslot != null && inventoryslot.hasItem()) {
                    inventoryslot.setItem(null);
                }
            }

            return null;
        }

        @Override
        public boolean canRemoveForPickupAll(ItemStack item, InventorySlot slot) {
            return slot.y > 90;
        }

        @Override
        public boolean canClickDragInto(InventorySlot invSlot) {
            return invSlot.inventory instanceof PlayerInventory || invSlot.y > 90 && invSlot.x <= 162;
        }
    }
}
