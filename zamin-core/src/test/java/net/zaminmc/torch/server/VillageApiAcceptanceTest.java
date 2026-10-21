package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.village.Villages;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.Direction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The village census's public API surface (§27.3 — the VIII10 exposure,
 * the §27.10 capability test): the census READS a plugin needs — the
 * founded villages with their center/radius bands, the door rows, the
 * population and golem counts, the mating window, the per-player
 * reputation table, and the reference's own nearest-village consult —
 * exercised over the LIVE engine through the api types alone (no cast to
 * the engine's system), the census founding its village through the real
 * walks (the villager's own scan cadence marking the cell, the ±16/±4/±16
 * door scan, the sky-walk facing, the pending-door resolution).
 */
class VillageApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The scene's door cell (flat ground level 4: the door stands at y=5). */
    private static final int DOOR_X = 64;
    private static final int DOOR_Y = 5;
    private static final int DOOR_Z = 64;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "vqa" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * The whole village-census read family through the public surface: a
     * sheltered door + a resident villager found a village through the
     * engine's own walks, and every row reads through {@link Server}
     * .villages() — the snapshot consumable from a foreign thread (§27.6).
     */
    @Test
    void theVillageCensusReadsThroughThePublicSurface() throws Exception {
        // THE SCENE: an oak door at (64, 5, 64) — the LOWER_CLOSED_E state
        // (the meta decode band 2 answers WEST: the census walks five
        // cells WEST as the facing side) — with a stone lid roofing those
        // five west cells (the sky count 0 against the open east side's
        // 5: the INSIDE is the smaller-sky side, the facing WEST), and a
        // villager standing beside the door (its own scan cadence marks
        // the cell — the census's only trigger).
        var world = server.world();
        var doorCell = new BlockPosition(DOOR_X, DOOR_Y, DOOR_Z);
        // ONE chunk request (the door's own) — the roof's cells ride the
        // write's own getOrGenerate (the second explicit request installs
        // a fresh generated copy over the scene's writes when its async
        // load lands late; the write path's own generation does not
        // replace a loaded chunk).
        server.requestChunkLoad(doorCell.chunkPosition(), chunk -> { });
        await(() -> world.isChunkLoaded(doorCell.chunkPosition()), "the scene's chunk loaded");
        world.execute(() -> {
            // The roof (the facing side's five cells, one lid above the
            // standing band — hasSkyAccess reads the blocks above).
            for (int step = 1; step <= 5; step++) {
                world.setBlock(new BlockPosition(DOOR_X - step, 7, DOOR_Z),
                        BuiltinBlocks.STONE);
            }
            // The door's BOTH halves in ONE execute (the reference's own
            // pair law: a lower whose upper is missing pops — the
            // neighbor bus hears the pair only after both stand; the
            // census's ±1 Y dedup keeps them ONE door row).
            world.setBlock(new BlockPosition(DOOR_X, DOOR_Y, DOOR_Z),
                    BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_E);
            world.setBlock(new BlockPosition(DOOR_X, DOOR_Y + 1, DOOR_Z),
                    BuiltinBlocks.OAK_DOOR_UPPER);
        });
        await(() -> world.getBlock(new BlockPosition(DOOR_X, DOOR_Y, DOOR_Z))
                        .identifier().value().equals("oak_door_east"),
                "the door block committed (was " + world
                        .getBlock(new BlockPosition(DOOR_X, DOOR_Y, DOOR_Z))
                        .identifier().value() + ")");
        server.mobs().spawnAt(MobType.VILLAGER, new Position(DOOR_X + 1.5, DOOR_Y, DOOR_Z + 1.5));

        Villages census = server.villages();
        assertNotNull(census, "the api surface is wired");
        AtomicReference<Villages.Village> founded = new AtomicReference<>();
        await(() -> {
            for (Villages.Village village : census.villages()) {
                if (village.doorCount() >= 1) {
                    founded.set(village);
                    return true;
                }
            }
            return false;
        }, "the village founded through the real walks (the villager's cadence "
                + "marks the cell, the door scan finds the sheltered door)");

        Villages.Village village = founded.get();
        // THE CENSUS ROWS (the reference's own reads): the door count,
        // the 32-minimum radius band, the door row's cell + inside
        // facing (the roofed west side) + the indoors offsets (the
        // facing's own doubled).
        assertEquals(1, village.doorCount(), "one sheltered door row");
        assertTrue(village.radius() >= 32, "the 32-minimum radius band: " + village.radius());
        assertEquals(1, village.doors().size(), "the door rows copied whole");
        Villages.Door door = village.doors().get(0);
        assertEquals(DOOR_X, door.x(), "the door cell's x");
        assertEquals(DOOR_Y, door.y(), "the door cell's y");
        assertEquals(DOOR_Z, door.z(), "the door cell's z");
        assertEquals(Direction.WEST, door.facing(), "the inside faces the roofed (smaller-sky) side");
        assertEquals(-2, door.indoorsOffsetX(), "the indoors offset is the facing's doubled");
        assertEquals(0, door.indoorsOffsetZ(), "the north-south offsets stay 0");
        assertEquals(0, door.restrictedTicks(), "the over-use counter starts 0");
        assertFalse(door.outside(), "the door's own row starts inside");
        // The center band: the single door's own cell.
        assertEquals(DOOR_X, village.centerX(), "the center is the door average");
        assertEquals(DOOR_Z, village.centerZ(), "the center's z");
        assertTrue(village.contains(DOOR_X, DOOR_Y, DOOR_Z),
                "the door cell sits inside the radius band");
        assertFalse(village.contains(DOOR_X + 400, DOOR_Y, DOOR_Z),
                "a far cell sits outside the radius band");
        // The population census (the %20 cadence counts the resident).
        await(() -> server.villages().villages().stream()
                        .anyMatch(row -> row.doorCount() == 1 && row.populationSize() >= 1),
                "the resident counted at the %20 cadence");
        Villages.Village counted = server.villages().villages().get(0);
        assertEquals(1, counted.populationSize(), "the villager census");
        assertEquals(0, counted.golems(), "no golem spawned (the doors > 20 gate)");
        assertTrue(counted.canMate(), "a fresh village's mating window stands open");
        // The reputation family: the unknown player's absent row reads 0
        // (the reference's own default), never the bad band.
        assertEquals(0, counted.reputationOf("nobody"), "the absent row's 0 default");
        assertFalse(counted.hasBadReputation("nobody"), "0 never reads as the bad band");
        assertTrue(counted.reputations().isEmpty(), "no reputation rows yet");
        // THE NEAREST CONSULT (the reference's getNearestVillage): the
        // door's own cell finds the village at range 0; a far position
        // does not.
        var atDoor = server.villages().nearest(DOOR_X, DOOR_Y, DOOR_Z, 0);
        assertTrue(atDoor.isPresent(), "the reference's own consult finds the village");
        assertEquals(village.centerX(), atDoor.get().centerX(), "the consult's row is the same village");
        assertTrue(server.villages().nearest(DOOR_X + 2000, DOOR_Y, DOOR_Z + 2000, 0).isEmpty(),
                "a far position finds no village at range 0");
    }

    /**
     * The §27.6 any-thread contract: the snapshot reads stay tear-free
     * under concurrent tick-lane mutation — a foreign thread hammers the
     * census while the engine ticks, every answer a complete snapshot.
     */
    @Test
    void theCensusSnapshotReadsTearFreeFromAForeignThread() throws Exception {
        var world = server.world();
        var doorCell = new BlockPosition(DOOR_X, DOOR_Y, DOOR_Z);
        // ONE chunk request (the door's own) — the roof's cells ride the
        // write's own getOrGenerate (a second explicit request installs a
        // fresh generated copy over the scene's writes when its async
        // load lands late).
        server.requestChunkLoad(doorCell.chunkPosition(), chunk -> { });
        await(() -> world.isChunkLoaded(doorCell.chunkPosition()), "the scene's chunk loaded");
        world.execute(() -> {
            for (int step = 1; step <= 5; step++) {
                world.setBlock(new BlockPosition(DOOR_X - step, 7, DOOR_Z),
                        BuiltinBlocks.STONE);
            }
            world.setBlock(new BlockPosition(DOOR_X, DOOR_Y, DOOR_Z),
                    BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_E);
            // The pair's upper half (a lone lower pops — the reference's
            // DoorBlock law; both halves in one execute).
            world.setBlock(new BlockPosition(DOOR_X, DOOR_Y + 1, DOOR_Z),
                    BuiltinBlocks.OAK_DOOR_UPPER);
        });
        await(() -> world.getBlock(new BlockPosition(DOOR_X, DOOR_Y, DOOR_Z))
                        .identifier().value().equals("oak_door_east"),
                "the door block committed (was " + world
                        .getBlock(new BlockPosition(DOOR_X, DOOR_Y, DOOR_Z))
                        .identifier().value() + ")");
        server.mobs().spawnAt(MobType.VILLAGER, new Position(DOOR_X + 1.5, DOOR_Y, DOOR_Z + 1.5));
        await(() -> !server.villages().villages().isEmpty(),
                "the village founded before the hammer");

        // The hammer: 400 snapshot reads from a foreign thread against
        // the live tick lane (the villager's scans + the census walks).
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Thread hammer = new Thread(() -> {
            try {
                Villages census = server.villages();
                for (int i = 0; i < 400; i++) {
                    for (Villages.Village village : census.villages()) {
                        int doors = village.doors().size();
                        int reputation = village.reputationOf("hammer");
                        if (doors < 0 || reputation != 0) {
                            throw new IllegalStateException("a torn row: doors=" + doors);
                        }
                    }
                    Villages copy = server.villages();
                    copy.nearest(DOOR_X, DOOR_Y, DOOR_Z, 0);
                }
            } catch (RuntimeException e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        }, "village-snapshot-hammer");
        hammer.start();
        // Let the tick lane work against the hammer's reads.
        Thread.sleep(1500);
        hammer.join(30_000);
        assertTrue(done.getCount() == 0, "the hammer finished");
        RuntimeException caught = failure.get();
        if (caught != null) {
            throw caught;
        }
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
