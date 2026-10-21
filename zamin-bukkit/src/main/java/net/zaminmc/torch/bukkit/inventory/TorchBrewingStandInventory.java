package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.brewing.BrewingStandBlockEntity;

import org.bukkit.event.inventory.InventoryType;

/**
 * The brewing stand's legacy {@link org.bukkit.inventory.Inventory} view
 * (the slice-4 inventory family): the 4 world-resident slots of one {@link
 * BrewingStandBlockEntity} — slots 0-2 the bottles, slot 3 the ingredient
 * (the engine's own slot constants), read and written through the block
 * entity's own public accessors (the same surface the engine's §27.3
 * container view rides, the isItemAllowed gate included — a wrong-kind
 * write throws the reference's own refusal, never a silent no-op).
 * InventoryType.BREWING, the family title "Brewing" (the engine's own
 * window-title family), the custom name overriding when the block entity
 * carries one.
 */
public final class TorchBrewingStandInventory extends TorchInventory {

    private final BrewingStandBlockEntity entity;

    private TorchBrewingStandInventory(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            World world, BlockPosition position, BrewingStandBlockEntity entity) {
        super(adapter, world, position);
        this.entity = entity;
    }

    /**
     * The brewing stand view at the cell, or null when no stand state
     * stands there (the caller's loud-skip law).
     */
    public static TorchBrewingStandInventory of(
            net.zaminmc.torch.bukkit.BukkitAdapter adapter, World world,
            BlockPosition position) {
        BrewingStandBlockEntity entity = adapter.engine().brewingStands().peek(position);
        if (entity == null) {
            return null;
        }
        TorchBrewingStandInventory view =
                new TorchBrewingStandInventory(adapter, world, position, entity);
        view.bindHolder(new TorchBlockInventoryHolder(view, world, position));
        return view;
    }

    @Override
    protected int engineSize() {
        return BrewingStandBlockEntity.SLOT_COUNT;
    }

    @Override
    protected net.zaminmc.torch.item.ItemStack engineSlot(int index) {
        net.zaminmc.torch.item.ItemStack stack = entity.getItem(index);
        return stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    @Override
    protected void engineSetSlot(int index, net.zaminmc.torch.item.ItemStack stack) {
        // The reference's own slot gate (isItemAllowed) — the same refusal
        // the engine's §27.3 container view throws: a wrong-kind stack is
        // refused loudly, never a silent no-op.
        if (!stack.isEmpty()
                && !BrewingStandBlockEntity.isItemAllowed(index, stack)) {
            throw new IllegalArgumentException("The brewing stand's slot " + index
                    + " refuses " + stack.type().identifier()
                    + " (the reference's isItemAllowed gate)");
        }
        entity.setItem(index, stack);
    }

    @Override
    public InventoryType getType() {
        return InventoryType.BREWING;
    }

    @Override
    protected String familyTitle() {
        return "Brewing";
    }

    @Override
    protected String customName() {
        return entity.customName();
    }

    @Override
    protected void routeOwnedWrite(String operation, Runnable action) {
        // unreachable: the brewing form is always position-keyed
        throw new IllegalStateException("the brewing inventory is position-keyed");
    }

    @Override
    protected java.util.List<org.bukkit.entity.HumanEntity> ownedViewers() {
        return java.util.List.of(); // unreachable: the brewing form is position-keyed
    }
}
