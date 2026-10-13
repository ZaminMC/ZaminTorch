package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.server.chest.ChestBlockEntity;
import net.zaminmc.torch.util.Position;

import java.util.Random;

/**
 * The chest minecart (Slice 21a — the reference's ChestMinecartEntity over
 * InventoryMinecartEntity): the cart body carries a 27-slot container with
 * exactly the chest's own menu semantics (the reference's interact opens
 * {@code ChestMenu} over the cart — the same click paths the engine's
 * {@link ChestBlockEntity} already owns), the chest's fullness drags the
 * rail ride ({@code applySlowdown}: {@code 0.98 + (15 - signal) * 0.001},
 * a fuller cart rolls slower), and the death spills three drops — the
 * MINECART item, the contents, then a CHEST block (the reference's own
 * dropItems chain: MinecartEntity drops the cart item, InventoryMinecart
 * spills the contents, ChestMinecart adds the chest — never a
 * chest-minecart item).
 */
public final class ChestMinecartEntity extends MinecartEntity {

    /** The 27-slot container (the chest's own click semantics, 1:1). */
    private final ChestBlockEntity chest = new ChestBlockEntity();

    public ChestMinecartEntity(int entityId, Position position, Random random) {
        super(entityId, Kind.CHEST_MINECART, position, random);
    }

    /** The container (click paths, snapshots, the loot install). */
    public ChestBlockEntity chest() {
        return chest;
    }

    /**
     * The wire's minecart type index: 1 = chest (MinecartEntity.Type.CHEST
     * — the Spawn Object objectData the client renders the chest box from).
     */
    @Override
    public int minecartTypeIndex() {
        return 1;
    }

    /**
     * The fullness drag (InventoryMinecartEntity.applySlowdown lines
     * 167-173): {@code 15 - InventoryMenu.getAnalogSignal(this)} grown into
     * {@code 0.98 + i * 0.001} — an empty cart multiplies 0.995, a full one
     * 0.98 (the fuller the chest, the heavier the ride).
     */
    @Override
    protected double railDragFactor() {
        int signal = net.zaminmc.torch.server.redstone.RedstoneSystem.containerFullness(
                chest.snapshotSlots(), 64);
        return 0.98 + (15 - signal) * 0.001;
    }

    /**
     * The public cargo view (§27.3's vehicle family — the container cart's
     * 27 slots): the engine's own chest IS the handle (§27.5); the write
     * verb runs on the world's authoritative context like every container
     * write (the VehicleManager's lane).
     */
    @Override
    public java.util.Optional<Cargo> cargo() {
        return java.util.Optional.of(new CargoView());
    }

    /** The live cargo view over the cart's own chest. */
    private final class CargoView implements Cargo {

        @Override public int slotCount() {
            return ChestBlockEntity.SLOT_COUNT;
        }

        @Override public net.zaminmc.torch.item.ItemStack[] slots() {
            net.zaminmc.torch.item.ItemStack[] snapshot = chest.snapshotSlots();
            net.zaminmc.torch.item.ItemStack[] copy =
                    new net.zaminmc.torch.item.ItemStack[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) {
                copy[i] = snapshot[i] == null
                        ? net.zaminmc.torch.item.ItemStack.EMPTY : snapshot[i];
            }
            return copy;
        }

        @Override public net.zaminmc.torch.item.ItemStack slot(int index) {
            if (index < 0 || index >= ChestBlockEntity.SLOT_COUNT) {
                return net.zaminmc.torch.item.ItemStack.EMPTY;
            }
            net.zaminmc.torch.item.ItemStack[] all = slots();
            return index < all.length ? all[index] : net.zaminmc.torch.item.ItemStack.EMPTY;
        }

        @Override public void setSlot(int index, net.zaminmc.torch.item.ItemStack stack) {
            if (index < 0 || index >= ChestBlockEntity.SLOT_COUNT || stack == null) {
                return;
            }
            chest.setSlot(index, stack);
        }
    }
}
