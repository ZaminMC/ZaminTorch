package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-008's independent verification pass (the pass-3 seat) — the two rows
 * the landed acceptance seat (PR #102's {@code BedWrongWorldBlastAcceptance
 * Test}) does not cover:
 *
 * <ol>
 *   <li><b>THE END ARM.</b> The reference's gate reads
 *       {@code world.dimension.hasSpawnPoint()} (BedBlock.use line 45) —
 *       and {@code TheEndDimension.hasSpawnPoint()} answers FALSE (line 54,
 *       the override; the base {@code Dimension} answers true line 130, the
 *       overworld inherits it). So an END bed takes the ELSE arm exactly
 *       like a nether bed: the pair removed and the power-5 fire blast in
 *       the End's own world. The engine's gate is the dimension row
 *       {@code session.dimension() == 0} — the End (dimension 1) must route
 *       to the blast arm on the DIMENSION conjunct alone: the End's biome
 *       is {@code the_end} (THE_END id 9, the FixedBiomeSource's constant),
 *       NOT {@code hell}, so the biome conjunct would leave an End bed in
 *       the SLEEP walk if the dimension row were missing or wrong-shaped
 *       (a {@code != -1} form would sleep the End bed; a biome-only form
 *       would sleep it too). This seat pins the exact gate form.</li>
 *   <li><b>THE HEAD-HALF CLICK.</b> The reference's head normalization
 *       (BedBlock.use lines 37-43) passes a clicked HEAD straight through
 *       with no redirect — the landed seat clicks the FOOT alone. A head
 *       click in the wrong world must reach the same blast arm (the pair
 *       walks, the power-5 blast, the dimension row).</li>
 * </ol>
 *
 * <p>Both seats ride the REAL use wire ({@code useItemOnBlock}) exactly as
 * the landed acceptance seat does — the same construction, the same
 * geometry, the two uncovered rows added.</p>
 */
class BedWrongWorldEndArmVerificationTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld nether;
    private EngineWorld end;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bedend" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        nether = server.worldFor(-1);
        end = server.worldFor(1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        assertEquals(1, end.dimension(), "the End world carries its dimension row");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name, Position at, int dimension) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    /** The bed pair: the foot at (64, 101, 64), the head south. */
    private static final BlockPosition FOOT = new BlockPosition(64, 101, 64);
    private static final BlockPosition HEAD = new BlockPosition(64, 101, 65);

    /**
     * THE END SEAT: an End bed click explodes on the DIMENSION conjunct
     * alone — the End's biome is {@code the_end}, not {@code hell}, so only
     * the {@code hasSpawnPoint() == false} row (the engine's dimension
     * gate) routes the else arm. The pair leaves whole, the power-5 blast
     * carries the END's dimension row, FIRE lands in the crater, and the
     * OVERWORLD's glass marker at the head's same coordinates stands.
     */
    @Test
    void theEndBedClickExplodesInsteadOfSleeping() throws Exception {
        layPlatformAndBed(end);
        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession camper = join("EndCamper",
                new Position(71.5, 101.0, 64.5), 1);
        EngineAwaits.awaitCondition(server.world(),
                () -> camper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        // The REAL use wire: the right-click on the FOOT half (the
        // reference's own foot redirect — the same click shape as the
        // landed nether seat).
        server.useItemOnBlock(camper, FOOT, 1, java.util.Optional.empty(),
                windowId -> { });

        EngineAwaits.awaitCondition(server.world(),
                () -> end.getBlock(HEAD).equals(end.airType())
                        && end.getBlock(FOOT).equals(end.airType()),
                40, "the End bed's pair left whole");
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                40, "the End bed's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the End bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(1, blast.get().dimension(),
                "the blast ranges the End's own dimension (the dimension"
                        + " conjunct — the_end is not hell, so the row alone"
                        + " routes the else arm)");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(HEAD)),
                "the overworld's glass marker at the head's coordinates survived");
        assertFalse(camper.sleeping(),
                "the End bed never entered the sleep walk");
        assertTrue(fireLandedNearTheBlast(end),
                "the always-true createFire row placed FIRE in the End crater");
    }

    /**
     * THE HEAD-CLICK SEAT: the normalization's other path — a clicked HEAD
     * passes straight through with no redirect (BedBlock.use lines 37-43:
     * the FOOT arm alone walks the offset). The same wrong-world blast arm
     * must fire: the pair leaves whole and the power-5 blast carries the
     * nether's dimension row.
     */
    @Test
    void theNetherHeadHalfClickExplodesThePair() throws Exception {
        layPlatformAndBed(nether);
        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession camper = join("HeadClicker",
                new Position(71.5, 101.0, 64.5), -1);
        EngineAwaits.awaitCondition(server.world(),
                () -> camper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        // The REAL use wire: the right-click on the HEAD half directly.
        server.useItemOnBlock(camper, HEAD, 1, java.util.Optional.empty(),
                windowId -> { });

        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(HEAD).equals(nether.airType())
                        && nether.getBlock(FOOT).equals(nether.airType()),
                40, "the head-clicked pair left whole");
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                40, "the head click's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the head-click blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(-1, blast.get().dimension(),
                "the head-click blast ranges the nether");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(HEAD)),
                "the overworld's glass marker at the head's coordinates survived");
        assertFalse(camper.sleeping(),
                "the head click never entered the sleep walk");
    }

    /**
     * Lays the scene in the given world: an 11x11 dirt platform at y=100,
     * the bed pair one above it, and the OVERWORLD's GLASS marker at the
     * HEAD cell's same coordinates (the routing discriminator — a
     * misrouted blast would tear it).
     */
    private void layPlatformAndBed(EngineWorld world) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    world.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        world.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                world.airType());
                    }
                }
            }
            world.setBlock(FOOT, BuiltinBlocks.BED_FOOT_SOUTH);
            world.setBlock(HEAD, BuiltinBlocks.BED_HEAD_SOUTH);
            server.world().setBlock(HEAD, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> EngineServer.isBedHalf(world.getBlock(HEAD))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(HEAD)),
                40, "the wrong-world bed and the overworld marker landed");
    }

    /** Whether FIRE landed anywhere in the blast's crater region. */
    private boolean fireLandedNearTheBlast(EngineWorld world) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int y = 100; y <= 106; y++) {
                for (int dz = -7; dz <= 7; dz++) {
                    if (BuiltinBlocks.FIRE.equals(world.getBlock(
                            new BlockPosition(64 + dx, y, 64 + dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
