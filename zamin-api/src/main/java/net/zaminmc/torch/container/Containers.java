package net.zaminmc.torch.container;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;

import java.util.Optional;

/**
 * The position-keyed container family (§27.3's block-entity read
 * surface): the chests, furnaces, and hoppers of one world, reached
 * through {@link World#containers()}.
 *
 * <p><b>Threading (§27.6):</b> the lookups are best-effort from any
 * thread (a container that does not exist answers empty); the returned
 * views are LIVE handles over the engine's own block entities — reads
 * are owner-context consistent. A plugin reading off the simulation
 * lane wraps the read in {@link World#execute} for a consistent
 * snapshot of the multi-slot state.</p>
 */
public interface Containers {

    /** The empty world's answer (every lookup empty). */
    Containers EMPTY = new Containers() {
        @Override public Optional<Chest> chestAt(BlockPosition position) {
            return Optional.empty();
        }

        @Override public Optional<Furnace> furnaceAt(BlockPosition position) {
            return Optional.empty();
        }

        @Override public Optional<Hopper> hopperAt(BlockPosition position) {
            return Optional.empty();
        }
    };

    /**
     * @return the chest at the position (a live handle), or empty when
     *         no chest block entity stands there.
     */
    Optional<Chest> chestAt(BlockPosition position);

    /**
     * @return the furnace at the position (a live handle), or empty when
     *         no furnace block entity stands there.
     */
    Optional<Furnace> furnaceAt(BlockPosition position);

    /**
     * @return the hopper at the position (a live handle), or empty when
     *         no hopper block entity stands there.
     */
    Optional<Hopper> hopperAt(BlockPosition position);

    /** A chest's live read surface (the 27-slot container). */
    interface Chest {
        /** @return the chest's own grid position. */
        BlockPosition position();

        /** @return the slot count (the vanilla chest's 27). */
        int slotCount();

        /**
         * @return a copy of the 27 slots (empty cells as empty stacks) —
         *         owner-context consistent (wrap in {@link World#execute}
         *         for a consistent multi-slot read off the lane).
         */
        ItemStack[] slots();

        /** @return one slot's stack copy (empty when out of range). */
        ItemStack slot(int index);
    }

    /** A furnace's live read surface (the burn/cook state + the 3 slots). */
    interface Furnace {
        /** @return the furnace's own grid position. */
        BlockPosition position();

        /** @return the input slot's stack copy. */
        ItemStack input();

        /** @return the fuel slot's stack copy. */
        ItemStack fuel();

        /** @return the output slot's stack copy. */
        ItemStack output();

        /** @return the remaining burn ticks (0 = cold). */
        int burnTimeRemaining();

        /** @return the current fuel item's total burn ticks. */
        int burnTimeTotal();

        /** @return the cook progress ticks (the 200-tick walk to 200). */
        int cookTime();
    }

    /** A hopper's live read surface (the 5-slot container + the cooldown). */
    interface Hopper {
        /** @return the hopper's own grid position. */
        BlockPosition position();

        /** @return the slot count (the vanilla hopper's 5). */
        int slotCount();

        /**
         * @return a copy of the 5 slots (empty cells as empty stacks) —
         *         owner-context consistent.
         */
        ItemStack[] slots();

        /** @return one slot's stack copy (empty when out of range). */
        ItemStack slot(int index);

        /** @return the remaining transfer cooldown ticks (0 = ready). */
        int transferCooldown();
    }
}
