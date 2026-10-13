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
}
