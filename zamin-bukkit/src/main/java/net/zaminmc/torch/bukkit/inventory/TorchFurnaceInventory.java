package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;

import org.bukkit.event.inventory.InventoryType;

/**
 * The furnace family's legacy {@link org.bukkit.inventory.Inventory} view
 * (the slice-4 inventory family): the 3 world-resident slots of one {@link
 * FurnaceBlockEntity} — slot 0 the input, slot 1 the fuel, slot 2 the
 * output (the engine's own slot constants), read and written through the
 * block entity's own public accessors. InventoryType.FURNACE, the family
 * title "Furnace" (the engine's own window-title family), the custom name
 * overriding when the block entity carries one.
 */
public final class TorchFurnaceInventory extends TorchInventory {

    private final FurnaceBlockEntity entity;

    private TorchFurnaceInventory(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            World world, BlockPosition position, FurnaceBlockEntity entity) {
        super(adapter, world, position);
        this.entity = entity;
    }

    /**
     * The furnace view at the cell, or null when no furnace state stands
     * there (the caller's loud-skip law).
     */
    public static TorchFurnaceInventory of(
            net.zaminmc.torch.bukkit.BukkitAdapter adapter, World world,
            BlockPosition position) {
        FurnaceBlockEntity entity = adapter.engine().furnaces().peek(position);
        if (entity == null) {
            return null;
        }
        TorchFurnaceInventory view = new TorchFurnaceInventory(adapter, world, position, entity);
        view.bindHolder(new TorchBlockInventoryHolder(view, world, position));
        return view;
    }

    @Override
    protected int engineSize() {
        return FurnaceBlockEntity.SLOT_COUNT;
    }

    @Override
    protected net.zaminmc.torch.item.ItemStack engineSlot(int index) {
        net.zaminmc.torch.item.ItemStack stack = entity.snapshotSlots()[index];
        return stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    @Override
    protected void engineSetSlot(int index, net.zaminmc.torch.item.ItemStack stack) {
        entity.setSlot(index, stack);
    }

    @Override
    public InventoryType getType() {
        return InventoryType.FURNACE;
    }

    @Override
    protected String familyTitle() {
        return "Furnace";
    }

    @Override
    protected String customName() {
        return entity.customName();
    }

    @Override
    protected void routeOwnedWrite(String operation, Runnable action) {
        // unreachable: the furnace form is always position-keyed
        throw new IllegalStateException("the furnace inventory is position-keyed");
    }

    @Override
    protected java.util.List<org.bukkit.entity.HumanEntity> ownedViewers() {
        return java.util.List.of(); // unreachable: the furnace form is position-keyed
    }
}
