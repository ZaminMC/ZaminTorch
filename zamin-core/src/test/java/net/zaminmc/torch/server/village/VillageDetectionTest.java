package net.zaminmc.torch.server.village;

import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The village detection (VIII10 — Village + SavedVillageData over the
 * engine's per-system shape): the door census (the markForUpdate queue's
 * one-per-tick walk, the inside-facing sky walk, the equal-sky
 * refusal), the center + radius walk (the door average, the 32-minimum
 * over the farthest door), the iron-golem spawn gate (the 1/7000 roll
 * at the pop/10 band with the 21-door gate + the position walk), the
 * mating window + the census gate (canMate + pop &lt; doors * 0.35), the
 * reputation table (the −30..10 clamp, the −15 bad band), the attacker
 * census + the golem's defense consult (the creeper excluded), and the
 * ZVI store's round trip.
 */
class VillageDetectionTest {

    /** The scripted random: the forced rolls the gates read. */
    private static final class ScriptedRandom extends java.util.Random {
        int[] intResults = {};
        int intAt = 0;

        @Override
        public int nextInt(int bound) {
            if (intAt < intResults.length) {
                int value = intResults[intAt++];
                return Math.min(Math.max(value, 0), bound - 1);
            }
            return super.nextInt(bound);
        }
    }

    /** The stub surface: the door map, the sky map, the censuses, the entity reads. */
    private static final class Surface implements VillageSystem.Surface {
        /** The wooden-door cells (the identifier is the door's half). */
        final Map<Long, Identifier> doors = new HashMap<>();
        /** The door cells' facing bands (the VillageSystem consult). */
        final Map<Long, Direction> facings = new HashMap<>();
        int villagers;
        int golems;
        final List<Position> spawnedGolems = new java.util.ArrayList<>();
        final Map<Integer, double[]> positions = new HashMap<>();
        final Map<Integer, Boolean> alive = new HashMap<>();
        final Map<String, double[]> playerPositions = new HashMap<>();
        final Map<String, Integer> playerIds = new HashMap<>();

        @Override
        public boolean isWoodenDoor(int x, int y, int z) {
            return doors.containsKey(packed(x, y, z));
        }

        @Override
        public Direction doorFacing(int x, int y, int z) {
            return facings.getOrDefault(packed(x, y, z), Direction.EAST);
        }

        @Override
        public boolean hasSkyAccess(int x, int y, int z) {
            // The default world: the sky reads by the X sign — a sheltered
            // east side against an open west answers the facing walk.
            return x < 0;
        }

        @Override
        public int countVillagersIn(double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ) {
            return villagers;
        }

        @Override
        public int countGolemsIn(double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ) {
            return golems;
        }

        @Override
        public void spawnGolem(Position at) {
            spawnedGolems.add(at);
        }

        @Override
        public boolean solidAt(int x, int y, int z) {
            return y == 3; // the floor under the spawn cells
        }

        @Override
        public boolean entityAlive(int engineId) {
            return alive.getOrDefault(engineId, false);
        }

        @Override
        public double[] entityPosition(int engineId) {
            return positions.get(engineId);
        }

        @Override
        public double[] playerPosition(String name) {
            return playerPositions.get(name);
        }

        @Override
        public int engineIdOfPlayer(String name) {
            return playerIds.getOrDefault(name, -1);
        }
    }

    private static long packed(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) y << 20) ^ z;
    }

    private static void placeDoor(Surface surface, int x, int y, int z) {
        surface.doors.put(packed(x, y, z),
                Identifier.parse("minecraft:oak_door_upper"));
    }

    // ------------------------------------------------------------- the door census

    /**
     * The census walk (SavedVillageData.markForUpdate + tick lines 42-63
     * + addOrUpdateDoorsAround/addDoor lines 121-170): the villager's
     * marked cell processes ONE queue row per tick, the scan finds the
     * wooden door, and the facing walk derives the INSIDE from the sky
     * distances — the SMALLER-sky side wins (the stub's open west /
     * covered east answers EAST).
     */
    @Test
    void theDoorCensusRegistersVillageDoorsWithTheInsideFacing() {
        Surface surface = new Surface();
        placeDoor(surface, 0, 5, 0);
        surface.facings.put(packed(0, 5, 0), Direction.EAST);
        VillageSystem system = new VillageSystem(surface, new java.util.Random(1L));

        system.markForUpdate(0, 4, 0);
        assertTrue(system.villages().isEmpty(), "the door waits its queue turn");
        system.tick(); // the pending update processes + the door joins its village

        assertEquals(1, system.villages().size(), "the door founded its village");
        Village village = system.villages().get(0);
        assertEquals(1, village.doorCount());
        VillageDoor door = village.doors().get(0);
        assertEquals(Direction.EAST, door.facing(),
                "the inside is the covered side (the smaller sky count)");
        assertEquals(2, door.indoorsOffsetX(), "the east facing's own doubled X");
        assertEquals(0, door.indoorsOffsetZ(), "the east facing's zero Z");
        assertEquals(0, door.x());
        assertEquals(5, door.y());
        assertEquals(1, system.ticks(), "the clock advanced one tick");

        // The staleness walk (removeInvalidDoors lines 294-316): the row
        // survives the whole 1200-tick window (the wooden cell still
        // standing, the row's own tick not stale), then the 1201st tick
        // removes it and the empty village leaves.
        for (int i = 0; i < 1200; i++) {
            system.tick();
        }
        assertEquals(1, system.villages().size(), "the row survives the 1200 window");
        system.tick();
        assertTrue(system.villages().isEmpty(),
                "the stale row leaves and the empty village goes");
    }

    /**
     * The equal-sky refusal (addDoor line 167's {@code i != j}): a door
     * with the SAME sky counts on both sides is not a village door —
     * the reference's own unsheltered-door gate.
     */
    @Test
    void theUnshelteredDoorNeverLands() {
        Surface surface = new Surface();
        placeDoor(surface, 0, 5, 0);
        VillageSystem system = new VillageSystem(surface, new java.util.Random(1L));
        // The equal-sky world: the stub's hasSkyAccess answers x < 0; a
        // door facing NORTH (the z axis) reads cells (0, y, z ± step)
        // whose x is 0 — both sides skyless: equal counts, no door.
        surface.facings.put(packed(0, 5, 0), Direction.NORTH);
        system.markForUpdate(0, 4, 0);
        system.tick();
        assertTrue(system.villages().isEmpty(), "the equal-sky door never lands");
    }

    // ------------------------------------------------------------- the center + radius walk

    /**
     * updateCenterAndRadius (Village lines 323-338): the center is the
     * door sum's average, the radius the 32-minimum over the farthest
     * door's square root plus one — and the contains band reads the
     * squared radius.
     */
    @Test
    void theCenterAndRadiusWalkOverTheDoorCensus() {
        Village village = new Village();
        village.addDoor(new VillageDoor(0, 4, 0, Direction.EAST, 1));
        village.addDoor(new VillageDoor(10, 4, 0, Direction.EAST, 2));
        village.addDoor(new VillageDoor(0, 4, 10, Direction.EAST, 3));
        assertEquals(3, village.doorCount());
        assertEquals(3, village.centerX(), "the x average");
        assertEquals(4, village.centerY(), "the y average");
        assertEquals(3, village.centerZ(), "the z average");
        assertEquals(32, village.radius(), "the 32-minimum over the near doors");
        assertTrue(village.contains(village.centerX(), village.centerY(), village.centerZ()));
        assertFalse(village.contains(village.centerX() + 40, village.centerY(),
                village.centerZ()), "beyond the radius refuses");

        Village spread = new Village();
        spread.addDoor(new VillageDoor(0, 4, 0, Direction.EAST, 1));
        spread.addDoor(new VillageDoor(100, 4, 0, Direction.EAST, 2));
        // The center (50, 4, 0): the farthest door sits 50 off — the
        // radius walks sqrt(2500) + 1 = 51, past the 32-minimum.
        assertEquals(51, spread.radius(), "the far door grows the band");
        assertTrue(spread.contains(50, 4, 0));
        assertFalse(spread.contains(110, 4, 0), "beyond the radius refuses");
    }

    // ------------------------------------------------------------- the golem spawn

    /**
     * The iron-golem spawn (Village.tick lines 66-75): the 1/7000 roll
     * under the pop/10 band with the 21-door gate, the 10-try position
     * walk, and the solid-top + clear-box gates — and the refusals (the
     * 20-door village, the full golem band).
     */
    @Test
    void theGolemSpawnGateRollsOneInSevenThousand() {
        Surface surface = new Surface();
        surface.villagers = 20; // the pop/10 band answers 2
        for (int i = 0; i < 21; i++) {
            placeDoor(surface, i, 4, 0);
            surface.facings.put(packed(i, 4, 0), Direction.EAST);
        }
        VillageSystem system = new VillageSystem(surface, new java.util.Random(7L));

        Village village = new Village();
        for (int i = 0; i < 21; i++) {
            village.addDoor(new VillageDoor(i, 4, 0, Direction.EAST, i + 1));
        }
        assertEquals(21, village.doorCount(), "the 21-door gate opens");

        // The scripted rolls: the staleness walk's nextInt(50) fires first,
        // then the 1/7000 answers 0 — the walk fires; the position walk's
        // nextInt(16)/nextInt(6) answer 8/3/8 (the center's own cell),
        // the contains band holds, and the spawn gates pass (the floor at
        // y-1, the clear box above).
        ScriptedRandom random = new ScriptedRandom();
        random.intResults = new int[] {0, 0, 8, 3, 8};
        village.tick(surface, random, 100);
        assertEquals(1, surface.spawnedGolems.size(),
                "the 1/7000 roll spawned the golem");
        assertEquals(village.centerX(), (int) surface.spawnedGolems.get(0).x());
        assertEquals(village.centerY(), (int) surface.spawnedGolems.get(0).y());
        assertEquals(1, village.golems(), "the village counts its golem");

        // The 20-door village never rolls the spawn at all.
        Surface small = new Surface();
        small.villagers = 20;
        for (int i = 0; i < 20; i++) {
            placeDoor(small, i, 4, 0);
            small.facings.put(packed(i, 4, 0), Direction.EAST);
        }
        Village tiny = new Village();
        for (int i = 0; i < 20; i++) {
            tiny.addDoor(new VillageDoor(i, 4, 0, Direction.EAST, i + 1));
        }
        ScriptedRandom forced = new ScriptedRandom();
        forced.intResults = new int[] {0, 0, 8, 3, 8};
        tiny.tick(small, forced, 100);
        assertTrue(small.spawnedGolems.isEmpty(), "the 21-door gate refuses");

        // The full golem band (pop 20 allows 2; the census answers 2).
        Surface full = new Surface();
        full.villagers = 20;
        full.golems = 2;
        for (int i = 0; i < 21; i++) {
            placeDoor(full, i, 4, 0);
            full.facings.put(packed(i, 4, 0), Direction.EAST);
        }
        Village saturated = new Village();
        for (int i = 0; i < 21; i++) {
            saturated.addDoor(new VillageDoor(i, 4, 0, Direction.EAST, i + 1));
        }
        saturated.tick(full, forced, 130);
        assertTrue(full.spawnedGolems.isEmpty(), "the pop/10 band refuses");
    }

    // ------------------------------------------------------------- the mating gates

    /**
     * The mating window + the census gate (Village.canMate/stopMating
     * lines 437-443 + VillagerMatingGoal.checkVillageValidity lines
     * 83-90): the window opens fresh, closes at a monster kill, and
     * reopens past 3600 ticks; the population rides under the 0.35 band
     * per door.
     */
    @Test
    void theMatingWindowAndTheCensusGate() {
        Surface surface = new Surface();
        surface.villagers = 6;
        for (int i = 0; i < 20; i++) {
            placeDoor(surface, i, 4, 0);
            surface.facings.put(packed(i, 4, 0), Direction.EAST);
        }
        Village village = new Village();
        for (int i = 0; i < 20; i++) {
            village.addDoor(new VillageDoor(i, 4, 0, Direction.EAST, i + 1));
        }
        assertTrue(village.canMate(), "the window opens fresh");

        // checkVillageValidity: pop < (int)(doors * 0.35) — 20 doors
        // answer 7; seven villagers refuse, six pass.
        assertFalse(village.canMate() && 7 < (int) (village.doorCount() * 0.35),
                "the band refuses");
        assertTrue(village.canMate() && 6 < (int) (village.doorCount() * 0.35),
                "the band passes");

        // stopMating closes the window for 3600 ticks (the reference's own
        // fresh marker: lastMatingTicks == 0 answers canMate TRUE — the
        // stop must land on a TICKED village for the marker to differ,
        // the decompile's own shape).
        village.tick(surface, new java.util.Random(2L), 1);
        village.stopMating();
        assertFalse(village.canMate());
        for (int i = 2; i < 3600; i++) {
            village.tick(surface, new java.util.Random(3L), i);
        }
        assertFalse(village.canMate(), "the window stays closed inside 3600");
        assertEquals(0, village.doorCount(),
                "the stale rows left — the 1200 window closed without a re-mark");
        village.tick(surface, new java.util.Random(4L), 3601);
        assertTrue(village.canMate(), "the window reopens past 3600");
    }

    // ------------------------------------------------------------- the reputation + the defense

    /**
     * The reputation table (Village lines 340-354): the −30..10 clamp,
     * the −15 bad band, and the golem's defense consult over the
     * attacker census — the nearest live attacker answers (the creeper
     * excluded), the dead rows never do, and the bad-reputation player
     * fallback answers the village's enemy.
     */
    @Test
    void theReputationTableAndTheGolemDefenseConsult() {
        Surface surface = new Surface();
        VillageSystem system = new VillageSystem(surface, new java.util.Random(5L));
        Village village = new Village();

        // The clamp: +11 at 0 clamps to 10; −41 clamps to −30.
        assertEquals(10, village.updateReputation("Hero", 11));
        assertEquals(-30, village.updateReputation("Enemy", -41));
        assertEquals(10, village.reputation("Hero"));
        assertEquals(-30, village.reputation("Enemy"));
        assertFalse(village.hasBadReputation("Hero"));
        assertTrue(village.hasBadReputation("Enemy"), "the −15 band");
        assertEquals(-29, village.updateReputation("Enemy", 1), "the table accumulates");

        // The defense consult: the attacker census.
        surface.alive.put(500, true); // a zombie attacker
        surface.positions.put(500, new double[] {2, 4, 2});
        village.addOrUpdateAttacker(500, false, "ZOMBIE");
        assertEquals(500, system.defenseAttackerId(village, 0, 4, 0),
                "the nearest live attacker answers");

        // The creeper exclusion (TrackIronGolemTargetGoal line 26).
        village.addOrUpdateAttacker(501, false, "CREEPER");
        surface.alive.put(501, true);
        surface.positions.put(501, new double[] {1, 4, 1});
        assertEquals(500, system.defenseAttackerId(village, 0, 4, 0),
                "the creeper never turns the village's golem");

        // The dead row never answers (the clean-up's own read).
        surface.alive.put(500, false);
        assertEquals(-1, system.defenseAttackerId(village, 0, 4, 0));

        // The bad-reputation fallback: the enemy player answers.
        surface.playerPositions.put("Enemy", new double[] {3, 4, 3});
        surface.playerIds.put("Enemy", 700);
        assertEquals("Enemy", system.defensePlayerName(village, 0, 4, 0));
        assertEquals(700, system.engineIdOfPlayer("Enemy"));
    }

    // ------------------------------------------------------------- the villager's food model

    /**
     * The villager's food/willing model (VillagerEntity lines 566-591 +
     * 851-870) over the engine's body: the 3-bread unit arms the willing
     * (the heart burst fires), the 12-potato band the same, the
     * below-band stock never does, and the consume eats exactly the
     * breeding unit.
     */
    @Test
    void theVillagerFoodModelArmsTheWilling() {
        var world = new net.zaminmc.torch.server.entity.MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }
            @Override public net.zaminmc.torch.util.Position nearestPlayer(
                    double x, double y, double z, double range) {
                return null;
            }
        };
        var bread = net.zaminmc.torch.item.ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.BREAD, 2);
        var villager = new net.zaminmc.torch.server.entity.MobEntity(
                980_000, net.zaminmc.torch.server.entity.MobType.VILLAGER,
                new Position(0.5, 4.0, 0.5), new java.util.Random(11L), world);
        // Two bread: below the 3-unit band — no excess, no consume.
        assertNull(villager.villagerAcceptFood(bread));
        assertFalse(villager.villagerHasExcessFood());
        assertFalse(villager.villagerConsumeAvailableFood(true));
        assertFalse(villager.villagerWilling());

        // The third bread arms the excess + the consume eats the unit.
        villager.villagerAcceptFood(net.zaminmc.torch.item.ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.BREAD, 1));
        assertTrue(villager.villagerHasExcessFood());
        assertTrue(villager.villagerConsumeAvailableFood(true));
        assertTrue(villager.villagerWilling());
        assertTrue(villager.villagerConsumeAvailableFood(false),
                "the shouldContinue read answers the standing willing");
        int left = villager.villagerFoodSlots()[0] == null ? 0
                : villager.villagerFoodSlots()[0].count();
        assertEquals(0, left, "the 3-bread unit ate the whole stack");

        // The trade's own arm (the landed villagerTrade row) stays pinned
        // by VillagerTradeTest — the willing flag's coverage rides there.
        villager.setVillagerWilling(false);
        assertFalse(villager.villagerWilling(), "the breed's own clear");
    }

    // ------------------------------------------------------------- the store round trip

    /**
     * The ZVI store (the reference's SavedVillageData rows over the
     * engine's data-store conventions): the snapshot serializes and the
     * load answers the same census — the centers, the doors with their
     * inside offsets, and the per-player reputations.
     */
    @Test
    void theStoreRoundTripsTheWholeCensus() throws Exception {
        Surface surface = new Surface();
        VillageSystem system = new VillageSystem(surface, new java.util.Random(6L));
        system.markForUpdate(0, 4, 0);
        placeDoor(surface, 0, 5, 0);
        surface.facings.put(packed(0, 5, 0), Direction.EAST);
        system.tick();
        Village village = system.villages().get(0);
        village.updateReputation("Hero", 3);
        village.updateReputation("Enemy", -20);
        surface.villagers = 5;

        VillageDataStore store = new VillageDataStore(
                java.nio.file.Files.createTempDirectory("villages").resolve("villages.bin"));
        VillageSystem.Snapshot snapshot = system.snapshot();
        byte[] bytes = store.serialize(snapshot);
        store.writeSerialized(bytes);

        VillageDataStore.Loaded loaded = store.load();
        assertEquals(snapshot.ticks(), loaded.ticks());
        assertEquals(1, loaded.rows().size());
        var row = loaded.rows().get(0);
        assertEquals(village.centerX(), row.cx());
        assertEquals(village.radius(), row.radius());
        assertEquals(1, row.doors().size(), "the door row rides the census");
        assertEquals(0, row.doors().get(0).x());
        assertEquals(2, row.doors().get(0).idx(), "the east facing's own doubled X");
        assertEquals(0, row.doors().get(0).idz(), "the east facing's zero Z");
        assertEquals(2, row.players().size(), "the reputation table rides whole");
        assertEquals(-20, row.players().stream()
                .filter(e -> e.getKey().equals("Enemy")).findFirst().orElseThrow().getValue());

        // The restore: the rows land whole.
        VillageSystem restored = new VillageSystem(surface, new java.util.Random(7L));
        restored.restore(loaded.rows(), loaded.ticks());
        assertEquals(1, restored.villages().size());
        Village back = restored.villages().get(0);
        assertEquals(village.centerX(), back.centerX());
        assertEquals(village.doorCount(), back.doorCount());
        assertEquals(-20, back.reputation("Enemy"));
        assertEquals(2, back.doors().get(0).indoorsOffsetX(),
                "the facing derives from the offsets");
    }
}
