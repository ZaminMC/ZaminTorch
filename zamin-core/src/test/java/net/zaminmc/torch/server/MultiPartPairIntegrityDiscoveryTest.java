package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-009 — the BED family's neighbor-changed pair-integrity arm
 * (BedBlock.neighborChanged lines 109-121): a half whose SIBLING departs
 * removes ITSELF — and a FOOT half's self-removal DROPS the bed item
 * (line 118's {@code dropItems(world, pos, state, 0)}). The arm runs
 * from ANY sibling removal path — a mined neighbor, a blast clipping
 * one half, a fluid wash, a piston, a plugin write — because the
 * removal's neighbor updates fire synchronously (World.removeBlock =
 * setBlockState flags 3 → onBlockChanged → updateNeighbors → the six
 * neighbors' neighborChanged).
 *
 * <p>THE ENGINE'S GAP: the bed pair's integrity rides the BREAK walk
 * alone ({@code bedSiblingCleanup}) and the wrong-world blast arm
 * (PR #102's {@code detonateBedOnTick}); a NON-BREAK removal of one
 * half leaves the survivor STANDING — vanilla clears it (and the bed
 * foot's departure drops the item). THE DOOR FAMILY IS COVERED — the
 * pass's first construction included a door seat that PASSED on
 * develop, and the source hunt found the compensating path:
 * {@code RedstoneSystem.doorNeighborChanged} (DoorBlock
 * neighborChanged lines 157-198, 1:1) — the orphaned upper removes
 * itself silently, the lower's floor-support failure pops the pair
 * with the item, the signal edge flips POWERED/OPEN. The door half of
 * the original VT-009 hypothesis is REJECTED (compensated); the bed
 * half stands.</p>
 *
 * <p>THE SEATS drive the trigger through the engine's own commit wire
 * ({@code world.setBlock} on the tick thread — the §466 change listener
 * wakes the six neighbors exactly as any removal path does): the head
 * leaves, and the FOOT must follow within the update drain — RED on
 * the unguarded tree BY CONTRACT (the discovery-seat convention),
 * GREEN only under the correction. The third seat pins the DOOR
 * family's existing arm as a regression guard (the compensating
 * behavior the finding must not break).</p>
 */
class MultiPartPairIntegrityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld world;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "multipart" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        world = server.world();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /** The bed pair, NORTH-facing: the foot at (64,101,64), the head north of it. */
    private static final BlockPosition BED_FOOT = new BlockPosition(64, 101, 64);
    private static final BlockPosition BED_HEAD = new BlockPosition(64, 101, 63);

    /** The door pair: the lower at (72,101,64), the upper one above it. */
    private static final BlockPosition DOOR_LOWER = new BlockPosition(72, 101, 64);
    private static final BlockPosition DOOR_UPPER = new BlockPosition(72, 102, 64);

    /**
     * Lays the scene: a dirt platform at y=100 with headroom carved to
     * y=107, the bed pair and the door pair one above it.
     */
    private void layPlatformAndPairs() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 15; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    world.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        world.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                world.airType());
                    }
                }
            }
            world.setBlock(BED_FOOT, BuiltinBlocks.BED_FOOT_NORTH);
            world.setBlock(BED_HEAD, BuiltinBlocks.BED_HEAD_NORTH);
            world.setBlock(DOOR_LOWER, BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_N);
            world.setBlock(DOOR_UPPER, BuiltinBlocks.OAK_DOOR_UPPER);
        });
        EngineAwaits.awaitCondition(world,
                () -> EngineServer.isBedHalf(world.getBlock(BED_HEAD))
                        && !world.getBlock(DOOR_UPPER).equals(world.airType()),
                40, "the pairs seeded");
    }

    /**
     * SEAT ONE — the orphaned bed foot self-removes (BedBlock
     * .neighborChanged lines 115-119): the head leaves through the
     * engine's own commit wire, and the foot — whose adjacent
     * opposite-class sibling is gone — must remove ITSELF. On the
     * unguarded tree the foot FLOATS (no bed arm in the update drain).
     */
    @Test
    void theOrphanedBedFootSelfRemoves() throws Exception {
        layPlatformAndPairs();
        // The trigger: the head's removal — ANY non-break removal path in
        // vanilla fires the same synchronous neighbor updates.
        server.ticker().submit(() -> world.setBlock(BED_HEAD, world.airType()));
        EngineAwaits.awaitCondition(world,
                () -> world.getBlock(BED_HEAD).equals(world.airType()),
                40, "the bed head left");
        EngineAwaits.awaitCondition(world,
                () -> world.getBlock(BED_FOOT).equals(world.airType()),
                40, "the orphaned bed foot self-removed (BedBlock.neighborChanged"
                        + " lines 115-119 — the half whose sibling departs removes itself)");
    }

    /**
     * SEAT TWO — the orphaned foot's departure DROPS the bed item
     * (BedBlock.neighborChanged line 118: {@code dropItems(world, pos,
     * state, 0)} — the FOOT half's own drop law): the item lands in the
     * foot's cell region. On the unguarded tree nothing drops (the
     * surviving half is mined later for its behavior-row drop instead).
     */
    @Test
    void theOrphanedBedFootDropsItsBed() throws Exception {
        layPlatformAndPairs();
        ConcurrentLinkedQueue<ItemEntity> spawned = new ConcurrentLinkedQueue<>();
        server.addItemListener(new net.zaminmc.torch.server.entity.ItemEntityManager.Listener() {
            @Override public void onItemSpawned(ItemEntity entity) {
                spawned.add(entity);
            }
            @Override public void onItemMoved(ItemEntity entity) { }
            @Override public void onItemCollected(ItemEntity entity,
                    net.zaminmc.torch.server.player.PlayerSession collector,
                    int collectedCount) { }
            @Override public void onItemStackChanged(ItemEntity entity) { }
            @Override public void onItemRemoved(ItemEntity entity, String reason) { }
        });
        server.ticker().submit(() -> world.setBlock(BED_HEAD, world.airType()));
        EngineAwaits.awaitCondition(world,
                () -> world.getBlock(BED_HEAD).equals(world.airType()),
                40, "the bed head left");
        EngineAwaits.awaitCondition(world,
                () -> spawned.stream().anyMatch(entity ->
                        entity.stack().type().identifier()
                                .equals(net.zaminmc.torch.server.item.BuiltinItems.BED.identifier())
                        && entity.dimension() == 0
                        && Math.abs(entity.position().x() - (BED_FOOT.x() + 0.5)) < 2.0
                        && Math.abs(entity.position().z() - (BED_FOOT.z() + 0.5)) < 2.0),
                40, "the orphaned foot's departure dropped the bed item"
                        + " (BedBlock.neighborChanged line 118)");
    }

    /**
     * SEAT THREE (THE GUARD — the door family's EXISTING arm): the
     * orphaned door upper self-removes through the RedstoneSystem's own
     * {@code doorNeighborChanged} (DoorBlock.neighborChanged lines
     * 157-198) — the compensating path the pass's first construction
     * found when this seat PASSED on the unguarded tree. It rides here
     * as the regression pin: the bed correction must not touch the
     * door's law.
     */
    @Test
    void theOrphanedDoorUpperSelfRemoves() throws Exception {
        layPlatformAndPairs();
        server.ticker().submit(() -> world.setBlock(DOOR_LOWER, world.airType()));
        EngineAwaits.awaitCondition(world,
                () -> world.getBlock(DOOR_LOWER).equals(world.airType()),
                40, "the door lower left");
        EngineAwaits.awaitCondition(world,
                () -> world.getBlock(DOOR_UPPER).equals(world.airType()),
                40, "the orphaned door upper self-removed (DoorBlock.neighborChanged"
                        + " lines 157-198 — RedstoneSystem.doorNeighborChanged's own arm)");
    }

    /**
     * SEAT FOUR — the orphan-direction pair (BedBlock.neighborChanged
     * lines 110-114, the HEAD arm): the FOOT leaves through the commit
     * wire, and the HEAD — whose sibling is gone — must remove ITSELF
     * with NO drop (the reference's drop law: the FOOT arm alone drops,
     * line 118; the HEAD arm is a silent removeBlock). On the unguarded
     * tree the head FLOATS (the cell-clearing await is the RED
     * discriminator; the no-drop pin guards the fix's own law).
     */
    @Test
    void theOrphanedBedHeadSelfRemovesWithNoDrop() throws Exception {
        layPlatformAndPairs();
        ConcurrentLinkedQueue<ItemEntity> spawned = new ConcurrentLinkedQueue<>();
        server.addItemListener(new net.zaminmc.torch.server.entity.ItemEntityManager.Listener() {
            @Override public void onItemSpawned(ItemEntity entity) {
                spawned.add(entity);
            }
            @Override public void onItemMoved(ItemEntity entity) { }
            @Override public void onItemCollected(ItemEntity entity,
                    net.zaminmc.torch.server.player.PlayerSession collector,
                    int collectedCount) { }
            @Override public void onItemStackChanged(ItemEntity entity) { }
            @Override public void onItemRemoved(ItemEntity entity, String reason) { }
        });
        server.ticker().submit(() -> world.setBlock(BED_FOOT, world.airType()));
        EngineAwaits.awaitCondition(world,
                () -> world.getBlock(BED_FOOT).equals(world.airType()),
                40, "the bed foot left");
        EngineAwaits.awaitCondition(world,
                () -> world.getBlock(BED_HEAD).equals(world.airType()),
                40, "the orphaned bed head self-removed (BedBlock.neighborChanged"
                        + " lines 110-114 — the HEAD arm's silent removeBlock)");
        EngineAwaits.awaitTicks(world, 10, "the departure settled");
        assertTrue(spawned.stream().noneMatch(entity ->
                        entity.stack().type().identifier().equals(
                                net.zaminmc.torch.server.item.BuiltinItems.BED.identifier())),
                "the HEAD arm's self-removal drops NOTHING (the FOOT arm alone"
                        + " drops — BedBlock.neighborChanged line 118)");
    }
}
