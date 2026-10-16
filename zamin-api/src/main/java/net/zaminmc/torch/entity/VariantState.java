package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Identifier;

/**
 * The variant-specific read view of a special minecart (architecture
 * §27.3's complete exposure — the engine-side state behind the wire's
 * synced slots, surfaced through {@link Vehicle#variantState()}).
 *
 * <p>One nested view per variant kind: the furnace cart's fuel and push
 * vector, the TNT cart's fuse, the spawner cart's rolling spawner law,
 * the command cart's executor rows. The boat and the plain and chest
 * carts answer {@link java.util.Optional#empty()} (the chest's container
 * rides {@link Vehicle#cargo()}).</p>
 *
 * <p>Threading contract (§27.6): every method reads live authoritative
 * state and runs on the world's simulation context — the documented
 * owner path is {@code World.execute(runnable)}. There is no separate
 * snapshot carrier: the values are scalars read once per call; a
 * multi-field consistent view is the caller's composition inside one
 * owner tick.</p>
 */
public interface VariantState {

    /**
     * The furnace minecart's own state (the reference's fuel field and
     * push vector — the coal-fed pusher's law).
     */
    interface Furnace extends VariantState {

        /**
         * The remaining fuel ticks (one coal feed adds the vanilla 3600;
         * the tank burns one per tick and the lit byte is the
         * {@code fuelTicks() > 0} case).
         */
        int fuelTicks();

        /** Whether the tank holds fuel (the synced lit byte's state). */
        boolean lit();

        /** The push vector's x component (the rolling direction). */
        double pushX();

        /** The push vector's z component (the rolling direction). */
        double pushZ();
    }

    /**
     * The TNT minecart's own state (the reference's fuse field — the
     * 80-tick fuse on rails).
     */
    interface Tnt extends VariantState {

        /**
         * The remaining fuse ticks, or {@code -1} when the cart is not
         * primed (the unprimed hull's own field value).
         */
        int fuseTicks();

        /** Whether the fuse burns (a primed cart explodes at zero). */
        boolean primed();
    }

    /**
     * The spawner minecart's own state (the reference's anonymous
     * MobSpawner rows — the rolling spawner's law).
     */
    interface Spawner extends VariantState {

        /**
         * The spawned kind's identifier (the EntityId row; the vanilla
         * field default is the pig).
         */
        Identifier spawnType();

        /** The remaining spawn-delay ticks (the Delay row). */
        int delayTicks();

        /** The reroll band's floor (the vanilla 200). */
        int minSpawnDelay();

        /** The reroll band's ceiling (the vanilla 800). */
        int maxSpawnDelay();

        /** The spawn attempts per cycle (the vanilla 4). */
        int spawnCount();

        /** The same-kind nearby cap that aborts a cycle (the vanilla 6). */
        int maxNearbyEntities();

        /** The player gate's range in blocks (the vanilla 16). */
        int requiredPlayerRange();

        /** The spawn roll's scatter range in blocks (the vanilla 4). */
        int spawnRange();
    }

    /**
     * The command-block minecart's own state (the reference's
     * CommandExecutor rows — the rolling executor's law).
     */
    interface Command extends VariantState {

        /** The command string (empty when never set). */
        String command();

        /** The accumulated success count. */
        int successCount();

        /** Whether output tracking is on (the track-output flag). */
        boolean trackOutput();

        /**
         * The last recorded output (the last-output JSON's text), or
         * {@code null} when none was recorded.
         */
        String lastOutput();
    }
}
