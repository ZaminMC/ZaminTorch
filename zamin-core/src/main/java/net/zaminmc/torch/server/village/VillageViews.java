package net.zaminmc.torch.server.village;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The public village-census view (§27.3's family — the VIII10 exposure):
 * the {@link VillageSystem}'s own census dressed as the
 * {@code net.zaminmc.torch.village.Villages} query surface. Every answer
 * COPIES the census at query time (§27.6): the village list, each
 * village's door rows, and each reputation table land as immutable
 * snapshots — freely consumable from any thread, best-effort consistent
 * (the census mutates on the tick lane; an off-lane query sees some
 * instant between ticks, never a half-written walk). The census's
 * mutation arms stay engine-owned.
 *
 * <p>(The engine type names are fully qualified below: the implemented
 * interface's own nested {@code Village}/{@code Door} records would
 * otherwise shadow the same-package engine classes inside this class
 * body — Java's inherited-member scope rule.)</p>
 */
public final class VillageViews implements net.zaminmc.torch.village.Villages {

    private final VillageSystem system;

    public VillageViews(VillageSystem system) {
        this.system = system;
    }

    @Override
    public List<net.zaminmc.torch.village.Villages.Village> villages() {
        // §27.6: the snapshot builds under the system's monitor (the
        // same monitor the census's mutation entry points hold) with each
        // row taken under its village's own monitor (the doors list +
        // the reputation table share it) — the copy is tear-free against
        // the tick lane, any calling thread.
        synchronized (system) {
            return system.villages().stream().map(VillageViews::view).toList();
        }
    }

    @Override
    public Optional<net.zaminmc.torch.village.Villages.Village> nearest(
            int x, int y, int z, int range) {
        synchronized (system) {
            net.zaminmc.torch.server.village.Village found =
                    system.nearestVillage(x, y, z, range);
            return found == null ? Optional.empty() : Optional.of(view(found));
        }
    }

    /**
     * One village's snapshot (the copied door rows + reputation table) —
     * built inside the village's own monitor (the doors list and the
     * reputation table share it with the census's mutation arms), so the
     * copy is tear-free against the tick lane.
     */
    private static net.zaminmc.torch.village.Villages.Village view(
            net.zaminmc.torch.server.village.Village village) {
        synchronized (village) {
            List<net.zaminmc.torch.village.Villages.Door> doors = village.doors().stream()
                    .map(VillageViews::door)
                    .toList();
            return new net.zaminmc.torch.village.Villages.Village(
                    village.centerX(), village.centerY(), village.centerZ(),
                    village.radius(), village.doorCount(), village.populationSize(),
                    village.golems(), village.canMate(), doors,
                    Map.copyOf(village.reputations()));
        }
    }

    /** One door row's snapshot. */
    private static net.zaminmc.torch.village.Villages.Door door(
            net.zaminmc.torch.server.village.VillageDoor row) {
        return new net.zaminmc.torch.village.Villages.Door(
                row.x(), row.y(), row.z(), row.facing(),
                row.indoorsOffsetX(), row.indoorsOffsetZ(),
                row.lastUpdateTick(), row.isOutsideVillage(), row.restrictedTicks());
    }
}
