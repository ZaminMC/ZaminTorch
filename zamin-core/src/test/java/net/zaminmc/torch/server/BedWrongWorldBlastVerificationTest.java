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
 * VT-008's verification seats (the twenty-seventh pass) — this stream's
 * INDEPENDENT confirmation that merged PR #102 (F-EX6, the other stream's
 * carrier) covers the bed's out-of-dimension blast arm whole. The
 * reference's gate is {@code world.dimension.hasSpawnPoint() &&
 * world.getBiome(pos) != Biome.HELL} (BedBlock.use line 48): the NETHER
 * and the END both answer {@code hasSpawnPoint()} FALSE
 * (NetherDimension line 55, TheEndDimension line 54), so the else arm
 * (use lines 68-79) runs in BOTH wrong worlds — the pair removed whole
 * (the head, then the sibling foot), then
 * {@code world.explode(null, head + 0.5, ..., 5.0F, true, true)}: power
 * 5, createFire ALWAYS, destructive ALWAYS, source null, in the ACTING
 * world.
 *
 * <p>PR #102's own seat ({@code BedWrongWorldBlastAcceptanceTest}) covers
 * the NETHER through the FOOT click. These seats add the coverage its
 * record deferred: (1) THE END — the gate's second wrong world, never
 * seated by either stream; (2) an independent NETHER re-run through the
 * HEAD-half click — the head-normalization identity path, a different
 * entry cell than the merged seat's foot redirect. Both must hold GREEN
 * on the post-#102 develop — a red here would mean #102's coverage is
 * incomplete and the finding reopens.</p>
 */
class BedWrongWorldBlastVerificationTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld nether;
    private EngineWorld end;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bedverify" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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

    /** The bed pair used by both seats: the foot at (x, y, z), the head south. */
    private static final BlockPosition FOOT = new BlockPosition(64, 101, 64);
    private static final BlockPosition HEAD = new BlockPosition(64, 101, 65);

    /**
     * Lays the scene in the named wrong world: an 11x11 dirt platform at
     * y=100 with cleared air above, the bed pair one above it, and the
     * OVERWORLD's GLASS markers at BOTH halves' coordinates (the
     * discriminator — a misrouted blast would tear them).
     */
    private void layWrongWorldScene(EngineWorld wrongWorld) throws Exception {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    wrongWorld.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        wrongWorld.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                wrongWorld.airType());
                    }
                }
            }
            wrongWorld.setBlock(FOOT, BuiltinBlocks.BED_FOOT_SOUTH);
            wrongWorld.setBlock(HEAD, BuiltinBlocks.BED_HEAD_SOUTH);
            server.world().setBlock(FOOT, BuiltinBlocks.GLASS);
            server.world().setBlock(HEAD, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> EngineServer.isBedHalf(wrongWorld.getBlock(HEAD))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(HEAD))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(FOOT)),
                80, "the wrong-world bed and the overworld markers landed");
    }

    /** Whether FIRE landed anywhere in the blast's crater region of the world. */
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

    /**
     * THE END SEAT (the gate's second wrong world — coverage neither
     * stream's record seated): the End bed click explodes — the pair
     * leaves whole in the END, the blast carries power 5 and the END's
     * own dimension, the overworld markers at the same coordinates
     * stand, FIRE lands in the crater, and no sleep walk ran.
     */
    @Test
    void theEndBedClickExplodesInsteadOfSleeping() throws Exception {
        layWrongWorldScene(end);
        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession camper = join("EndCamper",
                new Position(71.5, 101.0, 64.5), 1);
        EngineAwaits.awaitCondition(server.world(),
                () -> camper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        // The REAL use wire on the FOOT half (the reference's foot redirect).
        server.useItemOnBlock(camper, FOOT, 1, java.util.Optional.empty(),
                windowId -> { });

        EngineAwaits.awaitCondition(server.world(),
                () -> end.getBlock(HEAD).equals(end.airType())
                        && end.getBlock(FOOT).equals(end.airType()),
                40, "the End bed's pair left whole");
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                40, "the End bed's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(1, blast.get().dimension(),
                "the blast ranges the End's own dimension");
        assertEquals(64.5, blast.get().x(), 1e-9, "the blast's center x (the head's cell)");
        assertEquals(101.5, blast.get().y(), 1e-9, "the blast's center y (the head's cell)");
        assertEquals(65.5, blast.get().z(), 1e-9, "the blast's center z (the head's cell)");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(HEAD))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(FOOT)),
                "the overworld's glass markers at the pair's coordinates survived");
        assertFalse(camper.sleeping(),
                "the End bed never entered the sleep walk");
        assertTrue(fireLandedNearTheBlast(end),
                "the always-true createFire row placed FIRE in the End's crater");
    }

    /**
     * THE NETHER RE-RUN through the HEAD-half click — this stream's
     * independent construction (the merged seat clicks the FOOT; the
     * head click exercises the head-normalization IDENTITY path, the
     * other half of BedBlock.use lines 38-45). The same law must hold:
     * the pair leaves whole in the NETHER, the power-5 blast in the
     * player's own dimension, the overworld markers standing, FIRE in
     * the crater, no sleep.
     */
    @Test
    void theNetherBedHeadClickExplodesInsteadOfSleeping() throws Exception {
        layWrongWorldScene(nether);
        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession camper = join("HeadClicker",
                new Position(71.5, 101.0, 64.5), -1);
        EngineAwaits.awaitCondition(server.world(),
                () -> camper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        // The REAL use wire on the HEAD half — no foot redirect needed.
        server.useItemOnBlock(camper, HEAD, 1, java.util.Optional.empty(),
                windowId -> { });

        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(HEAD).equals(nether.airType())
                        && nether.getBlock(FOOT).equals(nether.airType()),
                40, "the nether bed's pair left whole (the head click removes the pair too)");
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                40, "the nether bed's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(-1, blast.get().dimension(),
                "the blast ranges the player's own dimension");
        assertEquals(64.5, blast.get().x(), 1e-9, "the blast's center x (the clicked head's cell)");
        assertEquals(65.5, blast.get().z(), 1e-9, "the blast's center z (the clicked head's cell)");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(HEAD))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(FOOT)),
                "the overworld's glass markers at the pair's coordinates survived");
        assertFalse(camper.sleeping(),
                "the nether bed never entered the sleep walk");
        assertTrue(fireLandedNearTheBlast(nether),
                "the always-true createFire row placed FIRE in the nether's crater");
    }
}
