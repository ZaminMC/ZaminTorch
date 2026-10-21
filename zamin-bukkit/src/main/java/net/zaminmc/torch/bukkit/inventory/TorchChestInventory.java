package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.chest.ChestBlockEntity;

import org.bukkit.event.inventory.InventoryType;

import java.util.List;

/**
 * The chest family's legacy {@link org.bukkit.inventory.Inventory} view
 * (the slice-4 inventory family): the 27 world-resident slots of one
 * {@link ChestBlockEntity} — the plain and trapped chest families' world
 * form, and the ender form over the session's own private grid (the door
 * block names the window; the storage is the player's own §27.3 grid).
 *
 * <p><b>Two forms:</b> {@link #ofWorld} reads the world chest at the cell
 * (the block-entity manager's own peek — the container exists once the
 * engine opened or fed it; null when no chest state stands there, the
 * caller's loud-skip law); {@link #ofEnder} wraps the session's own
 * ender-chest grid (InventoryType.ENDER_CHEST, the holder the player, the
 * writes routed to the player's own owner — the grid is session state,
 * never world state).</p>
 */
public final class TorchChestInventory extends TorchInventory {

    private final ChestBlockEntity entity;
    private final InventoryType type;
    /** The ender form's owning session (null: the world form). */
    private final net.zaminmc.torch.server.player.PlayerSession ownerSession;

    private TorchChestInventory(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            World world, BlockPosition position, ChestBlockEntity entity,
            InventoryType type,
            net.zaminmc.torch.server.player.PlayerSession ownerSession) {
        super(adapter, world, position);
        this.entity = entity;
        this.type = type;
        this.ownerSession = ownerSession;
    }

    /**
     * The world form: the chest block entity at the cell, or null when no
     * chest state stands there (the caller's loud-skip law).
     */
    public static TorchChestInventory ofWorld(
            net.zaminmc.torch.bukkit.BukkitAdapter adapter, World world,
            BlockPosition position) {
        ChestBlockEntity entity = adapter.engine().chests().peek(position);
        if (entity == null) {
            return null;
        }
        TorchChestInventory view = new TorchChestInventory(adapter, world, position,
                entity, InventoryType.CHEST, null);
        view.bindHolder(new TorchBlockInventoryHolder(view, world, position));
        return view;
    }

    /**
     * The ender form: the session's own private 27-slot grid — the door
     * block names the window, the storage rides the player (§27.3's own
     * shape). The holder is the player (the ender grid is the player's
     * own — the 1.8.8 convention).
     */
    public static TorchChestInventory ofEnder(
            net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            net.zaminmc.torch.server.player.PlayerSession session) {
        TorchChestInventory view = new TorchChestInventory(adapter, null, null,
                session.enderChest(), InventoryType.ENDER_CHEST, session);
        view.bindHolder(adapter.player(session));
        return view;
    }

    @Override
    protected int engineSize() {
        return ChestBlockEntity.SLOT_COUNT;
    }

    @Override
    protected net.zaminmc.torch.item.ItemStack engineSlot(int index) {
        net.zaminmc.torch.item.ItemStack stack = entity.getItem(index);
        return stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    @Override
    protected void engineSetSlot(int index, net.zaminmc.torch.item.ItemStack stack) {
        entity.setSlot(index, stack);
    }

    @Override
    public InventoryType getType() {
        return type;
    }

    @Override
    protected String familyTitle() {
        return type == InventoryType.ENDER_CHEST ? "Ender Chest" : "Chest";
    }

    @Override
    protected String customName() {
        return entity.customName();
    }

    @Override
    protected void routeOwnedWrite(String operation, Runnable action) {
        // The ender grid is session state — the player's own owner (the
        // pre-spawn window falls back to the default world, the adapter's
        // own worldFor law).
        World owner = ownerSession.world() != null
                ? ownerSession.world() : adapter.engine().world();
        adapter.mutatePosition(owner,
                ownerSession.position().toBlockPosition(), operation, action);
    }

    @Override
    protected List<org.bukkit.entity.HumanEntity> ownedViewers() {
        // The ender grid is private — only its owner ever views it.
        return List.of(adapter.player(ownerSession));
    }

    @Override
    protected String inventoryLabel() {
        return ownerSession != null ? "EnderChestInventory" : "ChestInventory";
    }
}
