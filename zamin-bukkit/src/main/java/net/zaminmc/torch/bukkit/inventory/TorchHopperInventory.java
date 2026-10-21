package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.hopper.HopperBlockEntity;
import net.zaminmc.torch.server.hopper.HopperBlocks;

import org.bukkit.event.inventory.InventoryType;

/**
 * The hopper's legacy {@link org.bukkit.inventory.Inventory} view (the
 * slice-4 inventory family): the 5 world-resident slots of one {@link
 * HopperBlockEntity}, read and written through the block entity's own
 * public accessors (the same surfaces the engine's own §27.3 container
 * view rides). InventoryType.HOPPER, the family title "Hopper" (the
 * engine's own window-title family), the custom name overriding when the
 * block entity carries one.
 */
public final class TorchHopperInventory extends TorchInventory {

    private final HopperBlockEntity entity;

    private TorchHopperInventory(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            World world, BlockPosition position, HopperBlockEntity entity) {
        super(adapter, world, position);
        this.entity = entity;
    }

    /**
     * The hopper view at the cell, or null when no hopper stands there or
     * its state has not materialized (the caller's loud-skip law).
     */
    public static TorchHopperInventory of(
            net.zaminmc.torch.bukkit.BukkitAdapter adapter, World world,
            BlockPosition position) {
        if (!HopperBlocks.isHopper(world.getBlock(position))) {
            return null;
        }
        HopperBlockEntity entity = adapter.engine().hopper().peek(position);
        if (entity == null) {
            return null;
        }
        TorchHopperInventory view = new TorchHopperInventory(adapter, world, position, entity);
        view.bindHolder(new TorchBlockInventoryHolder(view, world, position));
        return view;
    }

    @Override
    protected int engineSize() {
        return HopperBlockEntity.SLOT_COUNT;
    }

    @Override
    protected net.zaminmc.torch.item.ItemStack engineSlot(int index) {
        net.zaminmc.torch.item.ItemStack stack = entity.getItem(index);
        return stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    @Override
    protected void engineSetSlot(int index, net.zaminmc.torch.item.ItemStack stack) {
        entity.setItem(index, stack);
    }

    @Override
    public InventoryType getType() {
        return InventoryType.HOPPER;
    }

    @Override
    protected String familyTitle() {
        return "Hopper";
    }

    @Override
    protected String customName() {
        return null; // the hopper block entity carries no custom-name row
    }

    @Override
    protected void routeOwnedWrite(String operation, Runnable action) {
        // unreachable: the hopper form is always position-keyed
        throw new IllegalStateException("the hopper inventory is position-keyed");
    }

    @Override
    protected java.util.List<org.bukkit.entity.HumanEntity> ownedViewers() {
        return java.util.List.of(); // unreachable: the hopper form is position-keyed
    }
}
