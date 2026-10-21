package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.dispenser.DispenserBlockEntity;
import net.zaminmc.torch.server.dispenser.DispenserBlocks;

import org.bukkit.event.inventory.InventoryType;

/**
 * The dispenser family's legacy {@link org.bukkit.inventory.Inventory}
 * view (the slice-4 inventory family): the 9 world-resident slots of one
 * {@link DispenserBlockEntity} — the dispenser and the dropper share the
 * container (the engine's own family law); the BLOCK at the cell names
 * which (InventoryType.DISPENSER or DROPPER, the family titles from the
 * engine's own window-title family, the custom name overriding when the
 * block entity carries one).
 */
public final class TorchDispenserInventory extends TorchInventory {

    private final DispenserBlockEntity entity;
    private final boolean dropper;

    private TorchDispenserInventory(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            World world, BlockPosition position, DispenserBlockEntity entity,
            boolean dropper) {
        super(adapter, world, position);
        this.entity = entity;
        this.dropper = dropper;
    }

    /**
     * The dispenser-family view at the cell, or null when no dispenser or
     * dropper stands there (the caller's loud-skip law). The block at the
     * cell names the family member.
     */
    public static TorchDispenserInventory of(
            net.zaminmc.torch.bukkit.BukkitAdapter adapter, World world,
            BlockPosition position) {
        net.zaminmc.torch.block.BlockType type = world.getBlock(position);
        if (!DispenserBlocks.isFamily(type)) {
            return null;
        }
        DispenserBlockEntity entity = adapter.engine().dispensers().peek(position);
        if (entity == null) {
            return null;
        }
        TorchDispenserInventory view = new TorchDispenserInventory(adapter, world,
                position, entity, DispenserBlocks.isDropper(type));
        view.bindHolder(new TorchBlockInventoryHolder(view, world, position));
        return view;
    }

    @Override
    protected int engineSize() {
        return DispenserBlockEntity.SLOT_COUNT;
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
        return dropper ? InventoryType.DROPPER : InventoryType.DISPENSER;
    }

    @Override
    protected String familyTitle() {
        return dropper ? "Dropper" : "Dispenser";
    }

    @Override
    protected String customName() {
        return entity.customName();
    }

    @Override
    protected void routeOwnedWrite(String operation, Runnable action) {
        // unreachable: the dispenser form is always position-keyed
        throw new IllegalStateException("the dispenser inventory is position-keyed");
    }

    @Override
    protected java.util.List<org.bukkit.entity.HumanEntity> ownedViewers() {
        return java.util.List.of(); // unreachable: the dispenser form is position-keyed
    }
}
