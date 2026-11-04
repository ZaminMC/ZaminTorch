package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Wave PE-16 — the HUNT SCANS' OWN-BOX FORM over the Phase E boundary
 * arms: the reference's {@code ActiveTargetGoal.canStart} lines 101-107 —
 * {@code world.getEntitiesOfType(targetType, mob.getShape().grown
 * (followRange, 4.0, followRange), filters)} then the distance
 * comparator's sort taking the NEAREST member.
 *
 * <p>The pre-fix engine's hunts read a CENTER-RANGE consult (the plain
 * 3D distance ≤ 32): a villager whose own box GRAZES the grown box while
 * its center sits beyond the range was never hunted, and a villager on a
 * high ledge — its center well within 32 but its own box entirely beyond
 * the grown box's ±4 vertical band — WAS hunted. Both arms RED
 * pre-fix; the near control green by design.</p>
 */
class HuntScanBoxParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    private static final int CENTER_X = 20;
    private static final int CENTER_Z = 20;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "huntbox" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void buildPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int x = 2; x <= 58; x++) {
                for (int z = 14; z <= 26; z++) {
                    server.world().setBlock(new BlockPosition(x, 38, z),
                            BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, 39, z),
                            BuiltinBlocks.STONE);
                    for (int y = 40; y <= 50; y++) {
                        server.world().setBlock(new BlockPosition(x, y, z),
                                BuiltinBlocks.AIR);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(CENTER_X, 39, CENTER_Z))),
                200, "the platform stands");
    }

    private MobEntity spawnStatic(MobType type, Position at) throws InterruptedException {
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(type, at, 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            spawned.set(mob);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the static " + type + " spawned");
        return spawned.get();
    }

    private final AtomicReference<Boolean> scanDone = new AtomicReference<>(false);

    /** The scan's own window: the 1-in-10 roll gives up after 400 passes
     * (the deterministic read — a null answer after the full window). */
    private MobEntity scanUntilSettled(MobEntity zombie) throws InterruptedException {
        AtomicReference<MobEntity> found = new AtomicReference<>();
        server.ticker().submit(() -> {
            for (int i = 0; i < 400; i++) {
                MobEntity answer = zombie.tickMobTargeting();
                if (answer != null) {
                    found.set(answer);
                    return;
                }
            }
            scanDone.set(true);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> found.get() != null || scanDone.get(), 100, "the scan settled");
        return found.get();
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE NEAR CONTROL: the villager 5 east is hunted
    // ------------------------------------------------------------------

    /** Green by design in both engines: the plain in-range villager. */
    @Test
    void theNearVillagerIsHunted() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnStatic(MobType.ZOMBIE,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        MobEntity villager = spawnStatic(MobType.VILLAGER,
                new Position(CENTER_X + 5.5, 40.0, CENTER_Z + 0.5));
        assertEquals(villager, scanUntilSettled(zombie),
                "the near villager (5 east, same level) is hunted");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE GRAZE: the villager 32.2 east, its own box inside
    // the grown box
    // ------------------------------------------------------------------

    /**
     * The gather box is the zombie's OWN shape grown 32: [x ± 0.3 + 32]
     * — the face at 32.3 from the zombie's center. A villager whose
     * center sits 32.2 out has its own box [31.9, 32.5] overlapping the
     * face by 0.4: vanilla's gather finds it. Pre-fix RED: the plain
     * center-range consult (≤ 32) refused 32.2 whole.
     */
    @Test
    void theGrazingVillagerIsHunted() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnStatic(MobType.ZOMBIE,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        MobEntity villager = spawnStatic(MobType.VILLAGER,
                new Position(CENTER_X + 32.7, 40.0, CENTER_Z + 0.5)); // 32.2 east
        assertEquals(villager, scanUntilSettled(zombie),
                "the grazing villager (32.2 out, its own box overlapping the "
                        + "grown box's face by 0.4) is hunted");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE LEDGE: the villager 6.2 above, outside the ±4 band
    // ------------------------------------------------------------------

    /**
     * The grown box's vertical band is [y − 4, y + height + 4] — the
     * zombie's own 1.8 height puts the band's top at y + 5.8. A
     * villager standing on a ledge 7 above (its own box [y + 7.0,
     * y + 8.8] entirely beyond the band) is OUTSIDE vanilla's hunt box
     * however small the column distance. Pre-fix RED: the plain 3D
     * center consult (the distance ~6.3 ≤ 32) hunted it.
     */
    @Test
    void theLedgeVillagerSixAboveIsNeverHunted() throws Exception {
        buildPlatform();
        // The ledge: a pillar at the zombie's own column, its top at
        // y = 46 — the villager's feet at 46.2 (6.2 over the zombie's 40).
        server.ticker().submit(() -> {
            for (int y = 40; y <= 45; y++) {
                server.world().setBlock(new BlockPosition(CENTER_X, y, CENTER_Z),
                        BuiltinBlocks.STONE);
            }
            server.world().setBlock(new BlockPosition(CENTER_X, 46, CENTER_Z),
                    BuiltinBlocks.STONE);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(CENTER_X, 46, CENTER_Z))),
                100, "the pillar stands");
        MobEntity zombie = spawnStatic(MobType.ZOMBIE,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        // The pillar's top holds the villager at 47.0 — wait: the pillar
        // occupies the zombie's column; the villager stands beside the
        // pillar's top on a shelf one cell east.
        server.ticker().submit(() ->
                server.world().setBlock(new BlockPosition(CENTER_X + 1, 46, CENTER_Z),
                        BuiltinBlocks.STONE));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(CENTER_X + 1, 46, CENTER_Z))),
                100, "the shelf stands");
        MobEntity villager = spawnStatic(MobType.VILLAGER,
                new Position(CENTER_X + 1.5, 47.0, CENTER_Z + 0.5)); // 7 over
        assertNull(scanUntilSettled(zombie),
                "the ledge villager (7 above, its own box beyond the grown "
                        + "box's [y - 4, y + 5.8] band) is never hunted");
        assertNotNull(villager, "the rig's villager stands");
    }
}
