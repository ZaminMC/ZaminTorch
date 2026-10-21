package net.zaminmc.torch.server.village;

import net.zaminmc.torch.block.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The village surface (VIII10 — SavedVillageData 1:1 over the engine's
 * per-system shape): the villages list, the pending-update queue (64
 * deep, ONE processed per tick — the reference's own throttle), the
 * pending-door list (a door with no village found creates one), and the
 * 400-tick dirty cadence (the engine's save pipeline owns the actual
 * persistence points — the store rides the saveAllNow walk).
 *
 * <p>The door census (addOrUpdateDoorsAround, lines 121-141): the
 * ±16/±4/±16 box around a marked position — every wooden door cell
 * refreshes its row's tick or enters the pending list; the facing walk
 * (addDoor, lines 162-170) reads the door's own facing, walks the
 * sky-access distance five cells along the facing and its opposite
 * (the min-capped second pass, the reference's own shape), and the
 * door faces the side with the SMALLER sky count — the darker half is
 * the inside. A door whose two sides read the SAME sky count never
 * lands (the reference's {@code i != j} gate: an unsheltered door is
 * not a village door).</p>
 */
public final class VillageSystem {

    /**
     * The world consults the census + the spawn + the defense walks read
     * (the reference's {@code World} field — the engine's per-system
     * surface seam, the endereye/fishing precedents).
     */
    public interface Surface {

        /** isWoodenDoor (SavedVillageData lines 196-199): the door read at a cell. */
        boolean isWoodenDoor(int x, int y, int z);

        /**
         * DoorBlock.getFacing(world, pos) — the door's own facing (the
         * census's inside walk reads it + its opposite).
         */
        Direction doorFacing(int x, int y, int z);

        /** hasSkyAccess (the inside/outside walk's own read). */
        boolean hasSkyAccess(int x, int y, int z);

        /** The villager census in the box (updatePopulationSize's read). */
        int countVillagersIn(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ);

        /** The golem census in the box (updateIronGolemCount's read). */
        int countGolemsIn(double minX, double minY, double minZ,
                          double maxX, double maxY, double maxZ);

        /** The iron-golem spawn (the village's own roll lands here). */
        void spawnGolem(net.zaminmc.torch.util.Position at);

        /** The solid read at a cell (the golem's spawn gates). */
        boolean solidAt(int x, int y, int z);

        /** The engine id's liveness (the attacker registry's clean-up). */
        boolean entityAlive(int engineId);

        /** The engine id's live position (the defense consult's walk). */
        double[] entityPosition(int engineId);

        /** The player's live position by NAME (the bad-reputation walk). */
        double[] playerPosition(String name);

        /** The player's ENGINE id by NAME (the defense consult's player arm). */
        int engineIdOfPlayer(String name);
    }

    /** The snapshot row the store persists (one village's whole state). */
    public record VillageRow(int popSize, int radius, int golems, int stable,
                             int tick, int matingTick, int cx, int cy, int cz,
                             int acx, int acy, int acz,
                             List<DoorRow> doors,
                             List<java.util.Map.Entry<String, Integer>> players) {
        /** One door's persistence row (the reference's X/Y/Z/IDX/IDZ/TS). */
        public record DoorRow(int x, int y, int z, int idx, int idz, int ts) {
        }
    }

    private final Surface surface;
    private final Random random;
    private final List<Village> villages = new ArrayList<>();
    private final List<int[]> pendingUpdates = new ArrayList<>();
    private final List<VillageDoor> pendingDoors = new ArrayList<>();
    private int ticks;

    public VillageSystem(Surface surface, Random random) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** @return the villages list (the public consult's own read). */
    public List<Village> villages() {
        return villages;
    }

    /**
     * The golem's defense consult — the attacker census's read (the
     * creeper excluded): the nearest live attacker's ENGINE id, -1 when
     * none stands.
     */
    public int defenseAttackerId(Village village, int x, int y, int z) {
        return village.defenseAttackerId(surface, x, y, z);
    }

    /**
     * The golem's defense consult's fallback — the nearest
     * BAD-REPUTATION player's NAME, null when none (the 1-in-20 roll
     * rides the caller's own gate, the reference's lines 30-33).
     */
    public String defensePlayerName(Village village, int x, int y, int z) {
        return village.defensePlayerName(surface, x, y, z);
    }

    /** The surface's own player-id consult (the defense consult's arm). */
    public int engineIdOfPlayer(String name) {
        return surface.engineIdOfPlayer(name);
    }

    /**
     * markForUpdate (SavedVillageData lines 42-48): the villager's own
     * cadence marks its cell — the queue caps at 64 and never double-marks.
     */
    public void markForUpdate(int x, int y, int z) {
        if (pendingUpdates.size() <= 64 && !isMarkedForUpdate(x, y, z)) {
            pendingUpdates.add(new int[] {x, y, z});
        }
    }

    /**
     * The system's own tick (SavedVillageData.tick lines 50-63 1:1): the
     * clock, every village's own walk, the empty-village removal, ONE
     * pending update per tick (the reference's own throttle), the
     * pending doors' village resolution, and the 400-tick dirty cadence
     * (the engine's save pipeline owns the persistence points).
     */
    public void tick() {
        ticks++;
        for (Village village : villages) {
            village.tick(surface, random, ticks);
        }
        removeEmptyVillages();
        runPendingUpdate();
        addPendingDoors();
    }

    /** removeEmptyVillages (lines 65-75): a doorless village leaves. */
    private void removeEmptyVillages() {
        Iterator<Village> iterator = villages.iterator();
        while (iterator.hasNext()) {
            Village village = iterator.next();
            if (village.isEmpty()) {
                iterator.remove();
            }
        }
    }

    /**
     * getNearestVillage (lines 81-97 1:1): the closest center inside the
     * {@code range + radius} band.
     */
    public Village nearestVillage(int x, int y, int z, int range) {
        Village best = null;
        double bestDistance = Float.MAX_VALUE;
        for (Village village : villages) {
            int dx = village.centerX() - x;
            int dy = village.centerY() - y;
            int dz = village.centerZ() - z;
            double distance = (double) dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                float band = range + village.radius();
                if (distance <= (double) band * band) {
                    best = village;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** runPendingUpdate (lines 99-103): ONE marked cell per tick. */
    private void runPendingUpdate() {
        if (!pendingUpdates.isEmpty()) {
            int[] pos = pendingUpdates.remove(0);
            addOrUpdateDoorsAround(pos[0], pos[1], pos[2]);
        }
    }

    /** addPendingDoors (lines 105-119): the resolved doors join their village — or found one. */
    private void addPendingDoors() {
        for (int i = 0; i < pendingDoors.size(); i++) {
            VillageDoor door = pendingDoors.get(i);
            Village village = nearestVillage(door.x(), door.y(), door.z(), 32);
            if (village == null) {
                village = new Village();
                villages.add(village);
            }
            village.addDoor(door);
        }
        pendingDoors.clear();
    }

    /**
     * addOrUpdateDoorsAround (lines 121-141 1:1): the ±16/±4/±16 box
     * walk — every wooden door cell either refreshes its row's tick or
     * enters the pending list.
     */
    private void addOrUpdateDoorsAround(int x, int y, int z) {
        for (int dx = -16; dx < 16; dx++) {
            for (int dy = -4; dy < 4; dy++) {
                for (int dz = -16; dz < 16; dz++) {
                    int cellX = x + dx;
                    int cellY = y + dy;
                    int cellZ = z + dz;
                    if (surface.isWoodenDoor(cellX, cellY, cellZ)) {
                        VillageDoor door = getDoor(cellX, cellY, cellZ);
                        if (door == null) {
                            addDoor(cellX, cellY, cellZ);
                        } else {
                            door.setLastUpdateTick(ticks);
                        }
                    }
                }
            }
        }
    }

    /**
     * getDoor (lines 143-160): the pending list first, then the villages'
     * own rows — the XZ exact match with the Y inside ±1.
     */
    private VillageDoor getDoor(int x, int y, int z) {
        for (VillageDoor door : pendingDoors) {
            if (door.x() == x && door.z() == z && Math.abs(door.y() - y) <= 1) {
                return door;
            }
        }
        for (Village village : villages) {
            VillageDoor door = village.doorAt(x, y, z);
            if (door != null) {
                return door;
            }
        }
        return null;
    }

    /**
     * addDoor (lines 162-170 1:1): the facing + its opposite, the
     * sky-access distance five cells out on each side (the second pass
     * min-capped at the first's count + 1), and the INSIDE is the side
     * with the SMALLER sky count — a door whose sides read the same
     * count never lands at all.
     */
    private void addDoor(int x, int y, int z) {
        Direction facing = surface.doorFacing(x, y, z);
        if (facing == null) {
            return;
        }
        Direction opposite = facing.opposite();
        int facingSky = distanceToSkyAccess(x, y, z, facing, 5);
        int oppositeSky = distanceToSkyAccess(x, y, z, opposite, facingSky + 1);
        if (facingSky != oppositeSky) {
            pendingDoors.add(new VillageDoor(x, y, z,
                    facingSky < oppositeSky ? facing : opposite, ticks));
        }
    }

    /**
     * getDistanceToSkyAccess (lines 172-184 1:1): five cells along the
     * direction, counting the sky-lit ones — the walk stops at the min
     * cap (the second pass's own early exit).
     */
    private int distanceToSkyAccess(int x, int y, int z, Direction dir, int min) {
        int count = 0;
        for (int step = 1; step <= 5; step++) {
            if (surface.hasSkyAccess(x + dir.offsetX() * step,
                    y + dir.offsetY() * step, z + dir.offsetZ() * step)) {
                if (++count >= min) {
                    return count;
                }
            }
        }
        return count;
    }

    /** isMarkedForUpdate (lines 186-194): the queue's own duplicate read. */
    private boolean isMarkedForUpdate(int x, int y, int z) {
        for (int[] pos : pendingUpdates) {
            if (pos[0] == x && pos[1] == y && pos[2] == z) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ the persistence

    /** The whole-state capture (the store's save read — the tick-lane snapshot). */
    public record Snapshot(int ticks, List<VillageRow> rows) {
    }

    /**
     * The whole-state snapshot (the store's save read — the tick-lane
     * capture the engine's save pipeline serializes off-lane).
     */
    public Snapshot snapshot() {
        List<VillageRow> rows = new ArrayList<>();
        for (Village village : villages) {
            List<VillageRow.DoorRow> doorRows = new ArrayList<>();
            for (VillageDoor door : village.doors()) {
                doorRows.add(new VillageRow.DoorRow(door.x(), door.y(), door.z(),
                        door.indoorsOffsetX(), door.indoorsOffsetZ(), door.lastUpdateTick()));
            }
            rows.add(new VillageRow(village.populationSize(), village.radius(),
                    village.golems(), village.lastUpdateTick(), village.ticksRow(),
                    village.lastMatingTicks(), village.centerX(), village.centerY(),
                    village.centerZ(), village.combinedDoorX(), village.combinedDoorY(),
                    village.combinedDoorZ(), doorRows, List.copyOf(village.reputations()
                    .entrySet())));
        }
        return new Snapshot(ticks, rows);
    }

    /**
     * The restore (the store's load walk): the rows land whole — the
     * doors through the offset form (the facing derives), the
     * reputations by name.
     */
    public void restore(List<VillageRow> rows, int savedTicks) {
        this.ticks = savedTicks;
        for (VillageRow row : rows) {
            Village village = new Village();
            village.restore(row.popSize(), row.radius(), row.golems(), row.stable(),
                    row.tick(), row.matingTick(), row.cx(), row.cy(), row.cz(),
                    row.acx(), row.acy(), row.acz());
            for (VillageRow.DoorRow door : row.doors()) {
                village.doors().add(new VillageDoor(door.x(), door.y(), door.z(),
                        door.idx(), door.idz(), door.ts()));
            }
            for (java.util.Map.Entry<String, Integer> player : row.players()) {
                village.reputations().put(player.getKey(), player.getValue());
            }
            villages.add(village);
        }
    }

    /** @return the system's own clock (the store's Tick row). */
    public int ticks() {
        return ticks;
    }
}
