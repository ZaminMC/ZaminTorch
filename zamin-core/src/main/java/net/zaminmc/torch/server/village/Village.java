package net.zaminmc.torch.server.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The village itself (VIII10 — Village 1:1, the reference's own class
 * whole): the door census (the combined-positions sum, the center walk,
 * the 32-minimum radius over the farthest door), the per-player
 * reputation table (clamped −30..10, the bad band −15), the population
 * and golem censuses (the 20- and 30-tick cadences), the iron-golem
 * spawn (the 1/7000 roll at the pop/10 band with the 21-door gate),
 * the mating window (the 3600-tick cooldown after a monster kill), and
 * the attacker registry the golem's village-defense consult walks.
 *
 * <p>The world consults (the mob censuses, the golem spawn, the door
 * reads) ride the {@link VillageSystem.Surface} the system owns — the
 * reference's {@code World} field, the engine's per-system surface
 * seam (the endereye/fishing precedents).</p>
 */
public final class Village {

    private final List<VillageDoor> doors = new ArrayList<>();
    /**
     * The sum of all door positions (the reference's
     * combinedDoorPositions) — the center walk's own numerator.
     */
    private int combinedDoorX;
    private int combinedDoorY;
    private int combinedDoorZ;
    private int centerX;
    private int centerY;
    private int centerZ;
    private int radius;
    private int lastUpdateTick;
    private int ticks;
    private int populationSize;
    private int lastMatingTicks;
    /**
     * The per-player reputation table (the reference's TreeMap keyed by
     * NAME — the 1.8.8 identity, kept 1:1).
     */
    private final TreeMap<String, Integer> playerReputations = new TreeMap<>();
    private final List<Attacker> attackers = new ArrayList<>();
    private int golems;

    /** The reference's Attacker row (lines 451-459): the attacker + the tick. */
    static final class Attacker {
        /** The attacker's ENGINE id (the mob or the player — the shared wire space). */
        final int engineId;
        /** Whether the attacker is a PLAYER (the liveness + position consults split). */
        final boolean player;
        /**
         * The attacker's mob name ("creeper" for the creeper — the defense
         * goal's own exclusion, IronGolemEntity.canAttack line 137 /
         * TrackIronGolemTargetGoal line 26); null for players.
         */
        final String mobName;
        /** The reference's lastUpdateTick (the 300-tick window). */
        int lastUpdateTick;

        Attacker(int engineId, boolean player, String mobName, int tick) {
            this.engineId = engineId;
            this.player = player;
            this.mobName = mobName;
            this.lastUpdateTick = tick;
        }
    }

    /** The reference's empty ctor (the NBT read builds one). */
    public Village() {
    }

    /** @return the center cell (the center walk's own answer). */
    public int centerX() {
        return centerX;
    }

    public int centerY() {
        return centerY;
    }

    public int centerZ() {
        return centerZ;
    }

    /** @return the radius (the 32-minimum walk's own answer). */
    public int radius() {
        return radius;
    }

    /** @return the door census (the reference's getDoorCount). */
    public int doorCount() {
        return doors.size();
    }

    /** getTicksSinceUpdate (lines 157-159). */
    public int ticksSinceUpdate() {
        return ticks - lastUpdateTick;
    }

    /** getPopulationSize (lines 161-163). */
    public int populationSize() {
        return populationSize;
    }

    /** getGolems (the persisted row the spawn gate reads). */
    public int golems() {
        return golems;
    }

    /** @return the door rows (the census's own snapshot shape). */
    public List<VillageDoor> doors() {
        return doors;
    }

    /**
     * contains (lines 165-167): the squared radius band around the
     * center.
     */
    public boolean contains(int x, int y, int z) {
        int dx = x - centerX;
        int dy = y - centerY;
        int dz = z - centerZ;
        return dx * dx + dy * dy + dz * dz < radius * radius;
    }

    /** getNearestDoor (lines 173-186): the closest door row by the squared distance. */
    public VillageDoor nearestDoor(int x, int y, int z) {
        VillageDoor best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (VillageDoor door : doors) {
            int distance = door.squaredDistanceTo(x, y, z);
            if (distance < bestDistance) {
                best = door;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * getDoor (lines 209-223): the row at the cell — the XZ exact match
     * with the Y inside ±1 (either half answers).
     */
    public VillageDoor doorAt(int x, int y, int z) {
        if (contains(x, y, z)) {
            for (VillageDoor door : doors) {
                if (door.x() == x && door.z() == z
                        && Math.abs(door.y() - y) <= 1) {
                    return door;
                }
            }
        }
        return null;
    }

    /**
     * addDoor (lines 225-230): the row lands, the combined sum grows,
     * the center walk runs, and the village's own stability clock takes
     * the door's tick.
     */
    public void addDoor(VillageDoor door) {
        doors.add(door);
        combinedDoorX += door.x();
        combinedDoorY += door.y();
        combinedDoorZ += door.z();
        updateCenterAndRadius();
        lastUpdateTick = door.lastUpdateTick();
    }

    /** isEmpty (lines 232-234): no doors left. */
    public boolean isEmpty() {
        return doors.isEmpty();
    }

    /**
     * The center + radius walk (updateCenterAndRadius, lines 323-338
     * 1:1): the center is the door sum's average, the radius the
     * 32-minimum over the farthest door's square root plus one.
     */
    private void updateCenterAndRadius() {
        int count = doors.size();
        if (count == 0) {
            centerX = 0;
            centerY = 0;
            centerZ = 0;
            radius = 0;
            return;
        }
        centerX = combinedDoorX / count;
        centerY = combinedDoorY / count;
        centerZ = combinedDoorZ / count;
        int maxSquared = 0;
        for (VillageDoor door : doors) {
            maxSquared = Math.max(door.squaredDistanceTo(centerX, centerY, centerZ), maxSquared);
        }
        radius = Math.max(32, (int) Math.sqrt(maxSquared) + 1);
    }

    // ------------------------------------------------------------ the reputation

    /** getReputation (lines 340-343): 0 for the unknown. */
    public int reputation(String playerName) {
        Integer value = playerReputations.get(playerName);
        return value != null ? value : 0;
    }

    /**
     * updateReputation (lines 345-350): the clamp band −30..10 over the
     * running sum.
     */
    public int updateReputation(String playerName, int delta) {
        int current = reputation(playerName);
        int updated = Math.max(-30, Math.min(current + delta, 10));
        playerReputations.put(playerName, updated);
        return updated;
    }

    /** hasBadReputation (lines 352-354): the −15 band. */
    public boolean hasBadReputation(String playerName) {
        return reputation(playerName) <= -15;
    }

    /** updateAllReputations (lines 445-449): the cured-zombie bonus walk. */
    public void updateAllReputations(int delta) {
        for (String name : playerReputations.keySet()) {
            updateReputation(name, delta);
        }
    }

    /** The reputation table's own snapshot (the persistence read). */
    public Map<String, Integer> reputations() {
        return playerReputations;
    }

    // ------------------------------------------------------------ the mating window

    /** stopMating (lines 437-439): a monster kill closes the window. */
    public void stopMating() {
        lastMatingTicks = ticks;
    }

    /** canMate (lines 441-443): the 3600-tick window after the close. */
    public boolean canMate() {
        return lastMatingTicks == 0 || ticks - lastMatingTicks >= 3600;
    }

    // ------------------------------------------------------------ the attacker census

    /**
     * addOrUpdateAttacker (lines 236-245): a wounded villager registers
     * its living attacker — the row refreshes when it already stands in
     * the registry, else lands at the current tick.
     *
     * @param mobName the attacker's mob name (the creeper exclusion's own
     *                read; null for players)
     */
    public void addOrUpdateAttacker(int engineId, boolean player, String mobName) {
        for (Attacker attacker : attackers) {
            if (attacker.engineId == engineId) {
                attacker.lastUpdateTick = ticks;
                return;
            }
        }
        attackers.add(new Attacker(engineId, player, mobName, ticks));
    }

    /**
     * cleanUpAttackers (lines 283-292): the dead or 300-tick-stale rows
     * leave the registry.
     */
    private void cleanUpAttackers(VillageSystem.Surface surface) {
        attackers.removeIf(attacker ->
                !surface.entityAlive(attacker.engineId)
                        || Math.abs(ticks - attacker.lastUpdateTick) > 300);
    }

    /**
     * The golem's defense consult (TrackIronGolemTargetGoal.canStart
     * lines 19-39 over the manager's wiring): the nearest live attacker
     * (the creeper excluded — line 26's own gate) answers its ENGINE id,
     * -1 when none stands.
     */
    public int defenseAttackerId(VillageSystem.Surface surface, int x, int y, int z) {
        int bestId = -1;
        double bestDistance = Double.MAX_VALUE;
        for (Attacker attacker : attackers) {
            if (attacker.mobName != null && attacker.mobName.equals("creeper")) {
                continue; // the creeper never turns the village's golem
            }
            if (!surface.entityAlive(attacker.engineId)) {
                continue;
            }
            double[] at = surface.entityPosition(attacker.engineId);
            if (at == null) {
                continue;
            }
            double dx = at[0] - x;
            double dy = at[1] - y;
            double dz = at[2] - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) {
                bestId = attacker.engineId;
                bestDistance = distance;
            }
        }
        return bestId;
    }

    /**
     * getNearestAttacker's position (the reference's own walk — the
     * consult's position read).
     */
    double[] nearestAttacker(VillageSystem.Surface surface, int x, int y, int z) {
        double[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Attacker attacker : attackers) {
            double[] at = surface.entityPosition(attacker.engineId);
            if (at == null) {
                continue;
            }
            double dx = at[0] - x;
            double dy = at[1] - y;
            double dz = at[2] - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) {
                best = at;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The defense consult's fallback (lines 30-33): the 1-in-20 roll
     * after a failed attacker read answers the nearest BAD-REPUTATION
     * player's NAME, null when none.
     */
    public String defensePlayerName(VillageSystem.Surface surface, int x, int y, int z) {
        return nearestBadReputationPlayer(surface, x, y, z);
    }

    /**
     * getNearestPlayer (lines 263-281): the nearest BAD-REPUTATION
     * player (the defense consult's 1-in-20 fallback — the golem turns
     * on the village's enemy).
     */
    String nearestBadReputationPlayer(VillageSystem.Surface surface, int x, int y, int z) {
        String bestName = null;
        double bestDistance = Double.MAX_VALUE;
        for (String name : playerReputations.keySet()) {
            if (!hasBadReputation(name)) {
                continue;
            }
            double[] at = surface.playerPosition(name);
            if (at == null) {
                continue;
            }
            double dx = at[0] - x;
            double dy = at[1] - y;
            double dz = at[2] - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) {
                bestName = name;
                bestDistance = distance;
            }
        }
        return bestName;
    }

    // ------------------------------------------------------------ the tick

    /**
     * The village's own tick (lines 54-76 1:1): the staleness walk (the
     * 1-in-50 restricted reset, the not-a-door or 1200-tick-stale
     * removal with the center walk after), the attacker registry's
     * clean-up, the %20 population census (the reputation table clears
     * when the village empties), the %30 golem census, and the
     * iron-golem spawn roll — {@code golems < pop/10 && doors > 20 &&
     * nextInt(7000) == 0} — the reference's own band.
     */
    void tick(VillageSystem.Surface surface, java.util.Random random, int ticks) {
        this.ticks = ticks;
        removeInvalidDoors(surface, random);
        cleanUpAttackers(surface);
        if (ticks % 20 == 0) {
            updatePopulationSize(surface);
        }
        if (ticks % 30 == 0) {
            updateIronGolemCount(surface);
        }
        int allowed = populationSize / 10;
        if (golems < allowed && doors.size() > 20 && random.nextInt(7000) == 0) {
            int[] at = spawnPosForIronGolem(surface, random);
            if (at != null) {
                surface.spawnGolem(new net.zaminmc.torch.util.Position(
                        at[0], at[1], at[2]));
                golems++;
            }
        }
    }

    /**
     * removeInvalidDoors (lines 294-316 1:1): the 1-in-50 restricted
     * reset, then the rows whose cell stopped being a wooden door or
     * whose last update fell 1200 ticks stale leave — the outside flag
     * marks them, the combined sum shrinks, the center walk re-runs.
     */
    private void removeInvalidDoors(VillageSystem.Surface surface, java.util.Random random) {
        boolean changed = false;
        boolean resetRestricted = random.nextInt(50) == 0;
        java.util.Iterator<VillageDoor> iterator = doors.iterator();
        while (iterator.hasNext()) {
            VillageDoor door = iterator.next();
            if (resetRestricted) {
                door.resetRestrictedTicks();
            }
            if (!surface.isWoodenDoor(door.x(), door.y(), door.z())
                    || Math.abs(ticks - door.lastUpdateTick()) > 1200) {
                combinedDoorX -= door.x();
                combinedDoorY -= door.y();
                combinedDoorZ -= door.z();
                changed = true;
                door.setOutsideVillage(true);
                iterator.remove();
            }
        }
        if (changed) {
            updateCenterAndRadius();
        }
    }

    /**
     * updatePopulationSize (lines 126-143): the villager census in the
     * radius box; an empty village clears the whole reputation table.
     */
    private void updatePopulationSize(VillageSystem.Surface surface) {
        populationSize = surface.countVillagersIn(
                centerX - radius, centerY - 4, centerZ - radius,
                centerX + radius, centerY + 4, centerZ + radius);
        if (populationSize == 0) {
            playerReputations.clear();
        }
    }

    /** updateIronGolemCount (lines 110-124): the golem census in the box. */
    private void updateIronGolemCount(VillageSystem.Surface surface) {
        golems = surface.countGolemsIn(
                centerX - radius, centerY - 4, centerZ - radius,
                centerX + radius, centerY + 4, centerZ + radius);
    }

    /**
     * getSpawnPosForIronGolem (lines 78-87): the 10-try walk — the
     * center + the ±8/±3/±8 offsets, the containment band, and the
     * spawn gates below.
     */
    private int[] spawnPosForIronGolem(VillageSystem.Surface surface, java.util.Random random) {
        for (int i = 0; i < 10; i++) {
            int x = centerX + random.nextInt(16) - 8;
            int y = centerY + random.nextInt(6) - 3;
            int z = centerZ + random.nextInt(16) - 8;
            if (contains(x, y, z) && canIronGolemSpawn(surface, x, y, z)) {
                return new int[] {x, y, z};
            }
        }
        return null;
    }

    /**
     * canIronGolemSpawn (lines 89-108 1:1): the solid top below the cell
     * and the 2x4x2 box free of solid blocks (the reference's own range
     * shape — {@code rangeX/2 = 1} at each side, the 4-tall column, the
     * golem's own body band).
     */
    private boolean canIronGolemSpawn(VillageSystem.Surface surface, int x, int y, int z) {
        if (!surface.solidAt(x, y - 1, z)) {
            return false;
        }
        for (int boxX = x - 1; boxX < x + 1; boxX++) {
            for (int boxY = y; boxY < y + 4; boxY++) {
                for (int boxZ = z - 1; boxZ < z + 1; boxZ++) {
                    if (surface.solidAt(boxX, boxY, boxZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------ the persistence rows

    /** The state rows the store reads (the restore's own ctor pair). */
    void restore(int populationSize, int radius, int golems, int lastUpdateTick,
                 int ticks, int lastMatingTicks, int centerX, int centerY, int centerZ,
                 int combinedDoorX, int combinedDoorY, int combinedDoorZ) {
        this.populationSize = populationSize;
        this.radius = radius;
        this.golems = golems;
        this.lastUpdateTick = lastUpdateTick;
        this.ticks = ticks;
        this.lastMatingTicks = lastMatingTicks;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.combinedDoorX = combinedDoorX;
        this.combinedDoorY = combinedDoorY;
        this.combinedDoorZ = combinedDoorZ;
    }

    int lastUpdateTick() {
        return lastUpdateTick;
    }

    int ticksRow() {
        return ticks;
    }

    int lastMatingTicks() {
        return lastMatingTicks;
    }

    int combinedDoorX() {
        return combinedDoorX;
    }

    int combinedDoorY() {
        return combinedDoorY;
    }

    int combinedDoorZ() {
        return combinedDoorZ;
    }
}
