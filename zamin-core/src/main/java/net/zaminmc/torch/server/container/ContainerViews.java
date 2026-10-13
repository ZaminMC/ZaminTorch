package net.zaminmc.torch.server.container;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.container.Containers;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.chest.ChestBlockEntity;
import net.zaminmc.torch.server.chest.ChestManager;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.furnace.FurnaceManager;
import net.zaminmc.torch.server.hopper.HopperBlockEntity;
import net.zaminmc.torch.server.hopper.HopperSystem;

import java.util.Optional;

/**
 * The public container views (§27.3's block-entity read surface): the
 * three position-keyed managers dressed as the {@link Containers} family
 * — the engine's own block entities ARE the handles (§27.5), read
 * through live views (the owner-context consistency contract of
 * {@code World.containers()}). The write verbs assert the world's
 * authoritative mutation context (§27.6 — an off-lane write fails
 * diagnosably).
 */
public final class ContainerViews implements Containers {

    private final ChestManager chests;
    private final FurnaceManager furnaces;
    private final HopperSystem hoppers;
    private final net.zaminmc.torch.server.world.EngineWorld world;

    public ContainerViews(ChestManager chests, FurnaceManager furnaces,
                          HopperSystem hoppers) {
        this(chests, furnaces, hoppers, null);
    }

    /**
     * @param world the owning world (the write arms' ownership assert);
     *              null keeps the legacy read-only shape (the tests' bare
     *              wiring — the write verbs then skip the assert).
     */
    public ContainerViews(ChestManager chests, FurnaceManager furnaces,
                          HopperSystem hoppers,
                          net.zaminmc.torch.server.world.EngineWorld world) {
        this.chests = chests;
        this.furnaces = furnaces;
        this.hoppers = hoppers;
        this.world = world;
    }

    @Override
    public Optional<Chest> chestAt(BlockPosition position) {
        ChestBlockEntity entity = chests.peek(position);
        return entity == null ? Optional.empty() : Optional.of(new ChestView(entity, position));
    }

    @Override
    public Optional<Furnace> furnaceAt(BlockPosition position) {
        FurnaceBlockEntity entity = furnaces.peek(position);
        return entity == null ? Optional.empty() : Optional.of(new FurnaceView(entity, position));
    }

    @Override
    public Optional<Hopper> hopperAt(BlockPosition position) {
        HopperBlockEntity entity = hoppers.peek(position);
        return entity == null ? Optional.empty() : Optional.of(new HopperView(entity, position));
    }

    /** The chest's live view (the engine object behind the handle). */
    private final class ChestView implements Chest {
        private final ChestBlockEntity entity;
        private final BlockPosition position;

        ChestView(ChestBlockEntity entity, BlockPosition position) {
            this.entity = entity;
            this.position = position;
        }

        @Override public BlockPosition position() {
            return position;
        }

        @Override public int slotCount() {
            return ChestBlockEntity.SLOT_COUNT;
        }

        @Override public ItemStack[] slots() {
            ItemStack[] snapshot = entity.snapshotSlots();
            ItemStack[] copy = new ItemStack[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) {
                copy[i] = snapshot[i] == null ? ItemStack.EMPTY : snapshot[i];
            }
            return copy;
        }

        @Override public ItemStack slot(int index) {
            if (index < 0 || index >= ChestBlockEntity.SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            ItemStack[] all = slots();
            return index < all.length ? all[index] : ItemStack.EMPTY;
        }

        @Override public void setSlot(int index, ItemStack stack) {
            requireWrite();
            if (index < 0 || index >= ChestBlockEntity.SLOT_COUNT || stack == null) {
                return;
            }
            entity.setSlot(index, stack);
        }
    }

    /** The furnace's live view. */
    private final class FurnaceView implements Furnace {
        private final FurnaceBlockEntity entity;
        private final BlockPosition position;

        FurnaceView(FurnaceBlockEntity entity, BlockPosition position) {
            this.entity = entity;
            this.position = position;
        }

        @Override public BlockPosition position() {
            return position;
        }

        @Override public ItemStack input() {
            return copy(entity.input());
        }

        @Override public ItemStack fuel() {
            return copy(entity.fuel());
        }

        @Override public ItemStack output() {
            return copy(entity.output());
        }

        @Override public int burnTimeRemaining() {
            return entity.burnTimeRemaining();
        }

        @Override public int burnTimeTotal() {
            return entity.burnTimeTotal();
        }

        @Override public int cookTime() {
            return entity.cookTime();
        }

        @Override public void setInput(ItemStack stack) {
            requireWrite();
            entity.setSlot(0, java.util.Objects.requireNonNull(stack, "stack"));
        }

        @Override public void setFuel(ItemStack stack) {
            requireWrite();
            entity.setSlot(1, java.util.Objects.requireNonNull(stack, "stack"));
        }

        @Override public void setOutput(ItemStack stack) {
            requireWrite();
            entity.setSlot(2, java.util.Objects.requireNonNull(stack, "stack"));
        }

        private static ItemStack copy(ItemStack stack) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
    }

    /** The hopper's live view. */
    private final class HopperView implements Hopper {
        private final HopperBlockEntity entity;
        private final BlockPosition position;

        HopperView(HopperBlockEntity entity, BlockPosition position) {
            this.entity = entity;
            this.position = position;
        }

        @Override public BlockPosition position() {
            return position;
        }

        @Override public int slotCount() {
            return HopperBlockEntity.SLOT_COUNT;
        }

        @Override public ItemStack[] slots() {
            ItemStack[] copy = new ItemStack[HopperBlockEntity.SLOT_COUNT];
            for (int i = 0; i < copy.length; i++) {
                ItemStack stack = entity.getItem(i);
                copy[i] = stack == null ? ItemStack.EMPTY : stack;
            }
            return copy;
        }

        @Override public ItemStack slot(int index) {
            if (index < 0 || index >= HopperBlockEntity.SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = entity.getItem(index);
            return stack == null ? ItemStack.EMPTY : stack;
        }

        @Override public int transferCooldown() {
            return entity.isOnCooldown() ? HopperBlockEntity.TRANSFER_COOLDOWN : 0;
        }

        @Override public void setSlot(int index, ItemStack stack) {
            requireWrite();
            if (index < 0 || index >= HopperBlockEntity.SLOT_COUNT || stack == null) {
                return;
            }
            entity.setItem(index, stack);
        }
    }

    /** The write arms' ownership assert (§27.6). */
    private void requireWrite() {
        net.zaminmc.torch.server.world.EngineWorld owner = world;
        if (owner != null) {
            owner.requireMutationContext("setContainerSlot");
        }
    }
}
