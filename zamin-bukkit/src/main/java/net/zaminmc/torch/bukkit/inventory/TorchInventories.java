package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.player.PlayerSession;

import org.bukkit.inventory.Inventory;

/**
 * The inventory-family factory (the slice-4 lane): resolves the legacy
 * {@link Inventory} view for a container the engine actually has — the
 * open-window bridge and the hopper-transfer bridge both resolve their
 * views through this one walk, so the two arms answer IDENTICAL views for
 * the same container.
 *
 * <p><b>The family resolution mirrors the engine's own container family
 * walk</b> (the hopper system's own {@code containerSlotsAt} family
 * checks): the chest families (plain, trapped), the hopper, the dispenser
 * family (the dropper keeps its own type), the furnace family, and the
 * brewing stand — every family the three bridged event surfaces reach.
 * The entity containers (the chest minecart, the hopper minecart) have no
 * view in this slice — the entity-adapter lane's own family (the §27.9
 * recorded gap, the caller's loud-skip law).</p>
 */
public final class TorchInventories {

    private TorchInventories() {
        // The factory family — no instances.
    }

    /**
     * The container view at the cell — whichever block-container family
     * stands there — or null when no modeled container stands at the cell
     * (the caller's loud-skip law: never a fake view).
     */
    public static Inventory containerAt(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            World world, BlockPosition position) {
        net.zaminmc.torch.block.BlockType type = world.getBlock(position);
        String path = type.identifier().value();
        if (net.zaminmc.torch.server.chest.ChestFacingBlocks.isPlainFamily(type)
                || net.zaminmc.torch.server.chest.ChestFacingBlocks.isTrappedFamily(type)
                || path.equals("chest") || path.equals("trapped_chest")) {
            return TorchChestInventory.ofWorld(adapter, world, position);
        }
        if (net.zaminmc.torch.server.hopper.HopperBlocks.isHopper(type)) {
            return TorchHopperInventory.of(adapter, world, position);
        }
        if (net.zaminmc.torch.server.dispenser.DispenserBlocks.isFamily(type)) {
            return TorchDispenserInventory.of(adapter, world, position);
        }
        if (net.zaminmc.torch.server.brewing.BrewingStandBlocks.isBrewingStand(type)) {
            return TorchBrewingStandInventory.of(adapter, world, position);
        }
        if (net.zaminmc.torch.server.furnace.FurnaceFacingBlocks.isAnyFacing(type)
                || path.equals("furnace") || path.equals("lit_furnace")) {
            return TorchFurnaceInventory.of(adapter, world, position);
        }
        return null; // no modeled container family at the cell
    }

    /**
     * The open window's top inventory for one session's committed open (the
     * InventoryOpenEvent bridge's own resolution): the kind names the
     * family, the position the block cell for the block forms. The ender
     * form wraps the session's own private grid. Answers null for the
     * kinds this slice models no view of (the crafting table, the
     * enchanting table, the anvil, the beacon, the villager, the entity
     * forms — the §27.9 recorded gaps, the caller's loud-skip law).
     */
    public static Inventory topOfOpenWindow(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            PlayerSession session, PlayerSession.ContainerKind kind,
            BlockPosition position) {
        World world = session.world() != null
                ? session.world() : adapter.engine().world();
        switch (kind) {
            case CHEST -> {
                return position == null ? null
                        : TorchChestInventory.ofWorld(adapter, world, position);
            }
            case ENDER_CHEST -> {
                return TorchChestInventory.ofEnder(adapter, session);
            }
            case HOPPER -> {
                return position == null ? null
                        : TorchHopperInventory.of(adapter, world, position);
            }
            case DISPENSER -> {
                return position == null ? null
                        : TorchDispenserInventory.of(adapter, world, position);
            }
            case FURNACE -> {
                return position == null ? null
                        : TorchFurnaceInventory.of(adapter, world, position);
            }
            case BREWING_STAND -> {
                return position == null ? null
                        : TorchBrewingStandInventory.of(adapter, world, position);
            }
            // The kinds this slice models no view of (the §27.9 gaps):
            // the crafting table (the session's own grid), the enchanting
            // table and the anvil (the menu state), the beacon (the
            // payment slot), the villager (the trade rows), and the entity
            // forms (the horse, the chest minecart, the hopper minecart —
            // the entity-adapter lane's own family).
            default -> {
                return null;
            }
        }
    }
}
