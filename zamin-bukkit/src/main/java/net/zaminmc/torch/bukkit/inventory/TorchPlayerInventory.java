package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Objects;

/**
 * The player's own legacy {@link PlayerInventory} view (the slice-4
 * inventory family): the 36 storage slots (0-8 the hotbar, 9-35 the main
 * grid — the engine's own §27.3 slot family), the four armor accessors,
 * and the held-item arms, all over the engine's own {@code
 * PlayerSession.inventory()} public surface — every read resolves to the
 * CURRENT authoritative session state (the §27.5 handle law), every write
 * routed through the player's own owner (§27.6).
 *
 * <p><b>The size:</b> {@code getSize()} answers the historical 36 storage
 * slots — the armor rides its own accessors (the 1.8.8 shape). The
 * InventoryView's bottom inventory is this view.</p>
 *
 * <p><b>The writes:</b> the per-slot {@code setItem} and the armor
 * setters ride the engine's own write arms (the slot write fires the
 * engine's own inventory-sync fan — the wire re-syncs exactly like the
 * engine's own paths); the armor kind gate is the engine's own (a
 * wrong-kind piece is refused by the vanilla rule, the §27.3 contract);
 * {@code setHeldItemSlot} rides the engine's own select walk (an
 * in-progress eat cancels — the historical rule).</p>
 */
public final class TorchPlayerInventory extends TorchInventory implements PlayerInventory {

    private final net.zaminmc.torch.server.player.PlayerSession session;

    private TorchPlayerInventory(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            net.zaminmc.torch.server.player.PlayerSession session) {
        super(adapter, null, null);
        this.session = Objects.requireNonNull(session, "session");
    }

    /** The player's own inventory view over the session's live §27.3 surface. */
    public static TorchPlayerInventory of(
            net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            net.zaminmc.torch.server.player.PlayerSession session) {
        TorchPlayerInventory view = new TorchPlayerInventory(adapter, session);
        view.bindHolder(adapter.player(session));
        return view;
    }

    /** The engine session this view resolves to (§27.5's handle law). */
    public net.zaminmc.torch.server.player.PlayerSession session() {
        return session;
    }

    // ------------------------------------------------------------------ the storage slots (0-35)

    @Override
    protected int engineSize() {
        return session.inventory().slotCount();
    }

    @Override
    protected net.zaminmc.torch.item.ItemStack engineSlot(int index) {
        net.zaminmc.torch.item.ItemStack stack = session.inventory().slot(index);
        return stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    @Override
    protected void engineSetSlot(int index, net.zaminmc.torch.item.ItemStack stack) {
        session.inventory().setSlot(index, stack);
    }

    @Override
    public InventoryType getType() {
        return InventoryType.PLAYER;
    }

    /** The 1.8.8 player-inventory name is the player's own name. */
    @Override
    protected String familyTitle() {
        return session.name();
    }

    @Override
    protected String customName() {
        return null; // the player's own inventory carries no custom name
    }

    @Override
    protected void routeOwnedWrite(String operation, Runnable action) {
        World owner = session.world() != null
                ? session.world() : adapter.engine().world();
        adapter.mutatePosition(owner, session.position().toBlockPosition(), operation, action);
    }

    @Override
    protected List<HumanEntity> ownedViewers() {
        return List.of(adapter.player(session));
    }

    @Override
    protected String inventoryLabel() {
        return "PlayerInventory";
    }

    // ------------------------------------------------------------------ the armor row (the 1.8.8 accessors)

    @Override
    public ItemStack[] getArmorContents() {
        ItemStack[] armor = new ItemStack[session.inventory().armorSlotCount()];
        for (int i = 0; i < armor.length; i++) {
            net.zaminmc.torch.item.ItemStack piece = session.inventory().armorAt(i);
            armor[i] = piece.isEmpty() ? null : TorchItemStacks.toLegacy(piece,
                    "PlayerInventory.armorAt", adapter.logger());
        }
        return armor;
    }

    @Override
    public ItemStack getHelmet() {
        return armorPiece(0);
    }

    @Override
    public ItemStack getChestplate() {
        return armorPiece(1);
    }

    @Override
    public ItemStack getLeggings() {
        return armorPiece(2);
    }

    @Override
    public ItemStack getBoots() {
        return armorPiece(3);
    }

    @Override
    public void setHelmet(ItemStack helmet) {
        setArmorPiece(0, helmet);
    }

    @Override
    public void setChestplate(ItemStack chestplate) {
        setArmorPiece(1, chestplate);
    }

    @Override
    public void setLeggings(ItemStack leggings) {
        setArmorPiece(2, leggings);
    }

    @Override
    public void setBoots(ItemStack boots) {
        setArmorPiece(3, boots);
    }

    @Override
    public void setArmorContents(ItemStack[] items) {
        if (items == null) {
            return;
        }
        for (int i = 0; i < 4 && i < items.length; i++) {
            setArmorPiece(i, items[i]);
        }
    }

    private ItemStack armorPiece(int slot) {
        net.zaminmc.torch.item.ItemStack piece = session.inventory().armorAt(slot);
        return piece.isEmpty() ? null : TorchItemStacks.toLegacy(piece,
                "PlayerInventory.armorAt", adapter.logger());
    }

    /**
     * One armor write: translated, routed to the player's owner, applied
     * through the engine's own armor arm (the vanilla kind gate — a
     * wrong-kind piece is refused by the engine's own rule, the §27.3
     * contract: the write answers false and nothing lands).
     */
    private void setArmorPiece(int slot, ItemStack legacy) {
        net.zaminmc.torch.item.ItemStack engine = TorchItemStacks.toEngine(legacy, adapter);
        routeWrite("bukkit-player-armor-write",
                () -> session.inventory().setArmor(slot, engine));
    }

    // ------------------------------------------------------------------ the held item

    @Override
    public ItemStack getItemInHand() {
        net.zaminmc.torch.item.ItemStack held = session.heldItem();
        return held.isEmpty() ? null : TorchItemStacks.toLegacy(held,
                "PlayerInventory.heldItem", adapter.logger());
    }

    @Override
    public void setItemInHand(ItemStack stack) {
        net.zaminmc.torch.item.ItemStack engine = TorchItemStacks.toEngine(stack, adapter);
        int heldSlot = session.inventory().heldSlot();
        routeWrite("bukkit-held-item-write",
                () -> session.inventory().setSlot(heldSlot, engine));
    }

    @Override
    public int getHeldItemSlot() {
        return session.inventory().heldSlot();
    }

    /**
     * The held-slot selection: the engine's own select walk (an in-progress
     * eat cancels — the historical rule), routed to the player's owner.
     */
    @Override
    public void setHeldItemSlot(int slot) {
        routeWrite("bukkit-held-slot-select",
                () -> session.inventory().selectHotbarSlot(slot));
    }

    // ------------------------------------------------------------------ the 1.8.8 clear-by-identity arm

    /**
     * Clears the matching items from the inventory (the 1.8.8
     * {@code clear(int id, int amount)} arm): every stack of the material
     * leaves, {@code amount} units at most (-1 clears them all), the
     * per-slot writes riding the same routed path. Returns how many units
     * actually left.
     */
    @Override
    public int clear(int materialId, int amount) {
        Material material = Material.getMaterial(materialId);
        if (material == null) {
            return 0;
        }
        int remaining = amount == -1 ? Integer.MAX_VALUE : Math.max(0, amount);
        int cleared = 0;
        for (int i = 0; i < engineSize() && remaining > 0; i++) {
            net.zaminmc.torch.item.ItemStack slot = engineSlot(i);
            if (slot.isEmpty() || !slotIsOf(slot, material)) {
                continue;
            }
            int take = Math.min(slot.count(), remaining);
            if (take >= slot.count()) {
                setItem(i, null);
            } else {
                final int index = i;
                final int newCount = slot.count() - take;
                routeWrite("bukkit-inventory-clear",
                        () -> engineSetSlot(index, slot.withCount(newCount)));
            }
            cleared += take;
            remaining -= take;
        }
        return cleared;
    }

    @Override
    public HumanEntity getHolder() {
        return adapter.player(session);
    }

    private static boolean slotIsOf(net.zaminmc.torch.item.ItemStack slot, Material material) {
        return slot.type().identifier().value()
                .equals(material.name().toLowerCase(java.util.Locale.ROOT));
    }
}
