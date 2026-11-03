package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-10 — the breed goal's mate scan over the Phase E boundary
 * arms: the reference's {@code AnimalBreedGoal.findMate} lines 68-82 —
 * {@code world.getEntitiesOfType(animal.getClass(), animal.getShape()
 * .grown(8.0F, 8.0F, 8.0F))}, the closest legal partner whose OWN box
 * intersects the SEEKER'S OWN SHAPE grown 8 (the standard gather's
 * own-box law, STRICT both sides, the FEET-ANCHORED ASYMMETRIC y band
 * [y - 8, y + height + 8]), the per-world gather's dimension gate.
 *
 * <p>The pre-fix scan tested the candidate's CENTER inside a ±8
 * Chebyshev band with no dimension gate — a mate whose own box grazes
 * the grown box while its center sits outside was never found (a cow
 * at 8.2, its box overlapping the grown face at 8.95), a mate on a
 * ledge 8.2 below (its head reaching into the asymmetric band) was
 * never found, and a nether cow at the same coordinates as an
 * overworld cow COULD mate (the per-world gather never crosses).
 * Three arms RED pre-fix; the far control green by design.</p>
 */
class BreedScanBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "breedscan" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * The scan's own answer, taken synchronously inside one ticker
     * action (the cows placed, loved, and the consult run — no goal
     * walking, no wandering between the setup and the read).
     */
    private MobEntity mateFoundAt(MobType type, Position seekerAt, Position otherAt,
                                  int otherDimension) throws InterruptedException {
        AtomicReference<MobEntity> found = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity seeker = server.mobs().spawnAt(type, seekerAt, 0);
            MobEntity other = server.mobs().spawnAt(type, otherAt, otherDimension);
            if (seeker == null || other == null) {
                return;
            }
            seeker.setNoAi(true); // the seat discipline: no wandering
            other.setNoAi(true);
            seeker.setBreedingAge(0); // the adult gate
            other.setBreedingAge(0);
            seeker.enterLove(0); // the love window
            other.enterLove(0);
            found.set(seeker.findMate());
        });
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && found.get() == null) {
            Thread.sleep(20);
        }
        // The null answer is legitimate — the caller distinguishes by
        // re-reading; a timeout returns null only after the spawn
        // actually landed (verified by the caller's rig asserts).
        return found.get();
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE GRAZE: the center 8.2 out, the own box inside the
    // grown box
    // ------------------------------------------------------------------

    /**
     * A loved cow whose center sits 8.2 east of the seeker — its own
     * 0.9-wide box [center ± 0.45] overlapping the seeker's grown
     * box's east face (seeker ± 0.45 + 8 = 8.95 from the seeker's
     * center) by 0.7: vanilla's gather finds it. Pre-fix RED: the ±8
     * center Chebyshev band refused 8.2 whole.
     */
    @Test
    void theGrazingMateIsFound() throws Exception {
        MobEntity mate = mateFoundAt(MobType.COW,
                new Position(10.5, 40, 10.5),
                new Position(18.7, 40, 10.5), 0);
        assertNotNull(mate, "the grazing mate (8.2 out, its own box "
                + "overlapping the grown box by 0.7) is found");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE LEDGE: the asymmetric y band (a mate 8.2 below)
    // ------------------------------------------------------------------

    /**
     * A loved cow standing 8.2 BELOW the seeker — its own 1.3-high box
     * [y, y + 1.3] reaching up into the grown box's asymmetric y band
     * [seeker.y - 8, seeker.y + height + 8] by 1.1: vanilla's gather
     * finds it. Pre-fix RED: the symmetric center band refused |−8.2|.
     */
    @Test
    void theLedgeMateBelowIsFound() throws Exception {
        MobEntity mate = mateFoundAt(MobType.COW,
                new Position(10.5, 40, 10.5),
                new Position(10.5, 31.8, 10.5), 0);
        assertNotNull(mate, "the ledge mate (8.2 below, its head reaching "
                + "into the asymmetric y band) is found");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE DIMENSION GATE: the per-world gather
    // ------------------------------------------------------------------

    /**
     * A loved NETHER cow standing one block from an overworld seeker —
     * the per-world gather never crosses: no mate found. Pre-fix RED:
     * the scan carried no dimension gate (a nether cow and an overworld
     * cow at the same coordinates could mate).
     */
    @Test
    void theNetherCowNeverMatesTheOverworldCow() throws Exception {
        MobEntity mate = mateFoundAt(MobType.COW,
                new Position(10.5, 40, 10.5),
                new Position(11.5, 40, 10.5), -1);
        assertNull(mate, "the nether cow never mates the overworld cow "
                + "(the reference's per-world gather)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE FAR CONTROL: outside even the grown box
    // ------------------------------------------------------------------

    /**
     * A loved cow 9.6 east — its own box entirely beyond the grown
     * box's face (8.95): no mate found (the boundary's far side, green
     * by design in both engines).
     */
    @Test
    void theFarMateIsNeverFound() throws Exception {
        MobEntity mate = mateFoundAt(MobType.COW,
                new Position(10.5, 40, 10.5),
                new Position(20.1, 40, 10.5), 0);
        assertNull(mate, "the far mate (9.6 out, its own box beyond the "
                + "grown box's face) is never found");
        assertTrue(server.mobs().all().size() >= 2, "the rig's cows stand");
    }
}
