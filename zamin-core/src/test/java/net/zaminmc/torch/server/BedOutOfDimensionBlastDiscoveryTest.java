package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-008 — the bed's out-of-dimension blast arm, the INDEPENDENT
 * verification seat set (this stream's construction; the convergence
 * carrier is PR #102's own {@code BedWrongWorldBlastAcceptanceTest}).
 *
 * <p>THE LAW (BedBlock.use lines 45-84, the 1.8.8 reference): the sleep
 * walk is gated on {@code world.dimension.hasSpawnPoint() &&
 * world.getBiome(pos) != Biome.HELL} — the NETHER and the END answer
 * {@code hasSpawnPoint()} FALSE (NetherDimension line 55,
 * TheEndDimension line 54) — and the ELSE arm (lines 76-82) removes the
 * head, then the sibling foot, then detonates in the bed's OWN world:
 * {@code world.explode(null, x + 0.5, y + 0.5, z + 0.5, 5.0F, true,
 * true)} — power 5, createFire ALWAYS true, destructive ALWAYS true,
 * source null, and NO spawn is ever set.</p>
 *
 * <p>THE PRE-FIX ENGINE routed every bed use straight to the sleep walk
 * ({@code isBedHalf -> sleepOnTick}, no dimension consult, no biome
 * consult, no pair removal, no blast): a nether bed click at night set
 * the spawn and slept the body against the OVERWORLD's clock.</p>
 *
 * <p>THE SEATS — each an angle the carrier's own seats do not carry:</p>
 * <ol>
 *   <li>THE CORE, portal-walk anchored (DimensionWalk's own construction —
 *       a body that REACHED the nether through the real crossing, not a
 *       stamped session): the nether bed click at night explodes — the
 *       pair leaves, power 5, the blast ranges the nether, fire lands in
 *       the NETHER crater (the createFire row's routing evidence), no
 *       sleep, NO SPAWN SET (the else arm's own law).</li>
 *   <li>THE CLOCK PIN: the else arm never consults the time — the nether
 *       bed explodes at DAY too (the pre-fix tree refused through the
 *       overworld's night gate here).</li>
 *   <li>THE END: dimension 1 answers {@code hasSpawnPoint()} false the
 *       same way — the End bed click explodes in the End world.</li>
 *   <li>THE BIOME CONJUNCT: an overworld bed in a HELL-biome column (the
 *       anvil-import custom-map edge; this seat writes the chunk's own
 *       biome row directly) explodes IN THE OVERWORLD — the conjunct is
 *       its own gate, independent of the dimension.</li>
 *   <li>THE GUARD: the overworld bed in a plains column still sleeps at
 *       night — the gate must not touch the sleep arm.</li>
 * </ol>
 *
 * <p>RED on the unfixed develop BY CONTRACT at each wrong-world await
 * (the vanilla-answer assertion alone); GREEN on the converged fix.</p>
 */
class BedOutOfDimensionBlastDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld nether;
    private EngineWorld end;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "beddim" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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

    /**
     * SEAT 1 — THE CORE (the portal-walk anchoring): a body that crossed
     * to the nether through the REAL portal machinery clicks a nether bed
     * at night. The vanilla answer: the pair leaves whole, the power-5
     * blast ranges the NETHER, fire lands in the nether crater, the body
     * never sleeps, and NO spawn point is set. The pre-fix engine slept
     * the body against the overworld's clock at a phantom overworld bed
     * (the head normalization read the overworld field) and set the spawn.
     */
    @Test
    void thePortalWalkedNetherBedExplodesAtNight() throws Exception {
        PlayerSession walker = join("Walker");

        // The overworld portal (DimensionWalk's own 4x5 X-plane shape) at
        // (40, 20, 40) — high above the fixture's terrain.
        int frameX = 40;
        int frameY = 20;
        int frameZ = 40;
        server.ticker().submit(() -> {
            for (int w = 0; w < 4; w++) {
                for (int h = -1; h < 4; h++) {
                    boolean frame = w == 0 || w == 3 || h == -1 || h == 3;
                    server.world().setBlock(new BlockPosition(frameX + w, frameY + h, frameZ),
                            frame ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
                }
            }
        });
        await(() -> server.world().getBlock(new BlockPosition(frameX + 1, frameY, frameZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), 100, "the overworld portal frame built");

        // The crossing: stand the body in the cells, the 80-tick stand
        // clock, the arrival in the nether (the tick-denominated budget —
        // DimensionWalk's own form).
        Position portalStand = new Position(frameX + 1.5, frameY + 0.1, frameZ + 0.5);
        server.teleportPlayer(walker, portalStand);
        await(() -> walker.position().distanceSquared(portalStand) < 4.0,
                100, "the body stands in the portal");
        await(() -> walker.dimension() == -1, 400, "the body crossed to the nether");
        assertEquals(-1, walker.dimension(), "the crossing stamped the nether");

        // The scene: walk the body out of the arrival cells, then carve a
        // clean room around the stand-out spot (the seed-independent
        // carve — the nether's natural terrain never reaches the scene).
        Position arrival = walker.position();
        int sx = (int) Math.floor(arrival.x()) + 6;
        int sy = (int) Math.floor(arrival.y());
        int sz = (int) Math.floor(arrival.z());
        Position standOut = new Position(sx + 0.5, sy, sz + 0.5);
        server.teleportPlayer(walker, standOut);
        await(() -> walker.position().distanceSquared(standOut) < 4.0,
                100, "the body left the arrival cells");

        BlockPosition foot = new BlockPosition(sx + 2, sy, sz);
        BlockPosition head = new BlockPosition(sx + 2, sy, sz + 1);
        server.ticker().submit(() -> {
            carveRoom(nether, sx, sy, sz);
            placeBed(nether, foot);
        });
        await(() -> BuiltinBlocks.BED_HEAD_SOUTH.equals(nether.getBlock(head))
                && BuiltinBlocks.BED_FOOT_SOUTH.equals(nether.getBlock(foot)),
                100, "the nether bed pair landed");

        // Night on the OVERWORLD's clock — the very clock the pre-fix
        // sleep walk read for a nether body (the strongest wrong form:
        // the pre-fix tree walks into the spawn-set and the sleep).
        server.ticker().submit(() -> server.world()
                .setTimeOfDay(MobManager.NIGHT_START + 100));
        await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START,
                40, "night falls on the clock the sleep walk reads");

        AtomicReference<EngineServer.ExplosionEvent> blast = new AtomicReference<>();
        server.addExplosionListener(blast::set);

        // The REAL use wire: the right-click on the FOOT half (the
        // reference's own foot redirect exercises the head normalization).
        server.useItemOnBlock(walker, foot, 1, Optional.empty(), windowId -> { });

        // THE VANILLA ANSWER: the pair leaves whole (the head first, then
        // the sibling foot — use lines 76-79).
        await(() -> nether.getBlock(foot).equals(nether.airType())
                && nether.getBlock(head).equals(nether.airType()),
                60, "VT-008: the wrong-world bed's pair left whole");
        await(() -> blast.get() != null, 60, "the wrong-world bed's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(-1, blast.get().dimension(),
                "the blast ranges the body's own dimension (the nether)");
        assertFalse(walker.sleeping(),
                "the wrong-world bed never entered the sleep walk");
        assertNull(walker.bedSpawn(),
                "the wrong-world arm never sets the spawn (use lines 76-82 carry no setBedSpawn)");
        assertTrue(fireLandedNear(nether, sx + 2, sy, sz),
                "the always-true createFire row placed fire in the NETHER crater");
    }

    /**
     * SEAT 2 — THE CLOCK PIN: the else arm never consults the time of day
     * (the reference's gate is the dimension and the biome alone — the
     * night window lives INSIDE the sleep walk). A nether bed clicked at
     * DAY explodes the same as at night. The pre-fix tree refused here
     * through the overworld's night gate ("You can only sleep at night")
     * and nothing happened at all.
     */
    @Test
    void theNetherBedBlastIgnoresTheOverworldClock() throws Exception {
        PlayerSession camper = joinStamped("DayCamper",
                new Position(51.5, 101.0, 48.5), -1);
        BlockPosition foot = new BlockPosition(48, 101, 48);
        BlockPosition head = new BlockPosition(48, 101, 49);
        server.ticker().submit(() -> {
            carveRoom(nether, 48, 101, 48);
            placeBed(nether, foot);
        });
        await(() -> BuiltinBlocks.BED_HEAD_SOUTH.equals(nether.getBlock(head))
                && BuiltinBlocks.BED_FOOT_SOUTH.equals(nether.getBlock(foot)),
                100, "the nether bed pair landed");

        // DAY (the morning): the boot's own dawn, pinned explicitly.
        server.ticker().submit(() -> server.world().setTimeOfDay(1000));
        await(() -> server.world().timeOfDay() < MobManager.NIGHT_START,
                40, "the overworld clock stands at day");

        AtomicReference<EngineServer.ExplosionEvent> blast = new AtomicReference<>();
        server.addExplosionListener(blast::set);
        server.useItemOnBlock(camper, foot, 1, Optional.empty(), windowId -> { });

        await(() -> nether.getBlock(foot).equals(nether.airType())
                        && nether.getBlock(head).equals(nether.airType()),
                60, "VT-008: the daytime wrong-world pair still leaves whole");
        await(() -> blast.get() != null, 60,
                "the daytime bed blast fired (the else arm carries no clock consult)");
        assertEquals(-1, blast.get().dimension(),
                "the blast ranges the nether at any hour");
        assertEquals(5.0f, blast.get().radius(),
                "the bed blast's power (BedBlock.use line 78's 5.0F)");
        assertFalse(camper.sleeping(),
                "no sleep walk ran at day (the refusal is the pre-fix shape, not vanilla's)");
    }

    /**
     * SEAT 3 — THE END: TheEndDimension answers {@code hasSpawnPoint()}
     * false exactly like the nether (line 54), so the End bed explodes
     * the same way — the blast ranges the END world (dimension 1), the
     * pair leaves, no sleep, no spawn set, fire in the crater.
     */
    @Test
    void theEndBedClickExplodesInTheEndWorld() throws Exception {
        PlayerSession camper = joinStamped("EndCamper",
                new Position(19.5, 64.0, 16.5), 1);
        BlockPosition foot = new BlockPosition(16, 64, 16);
        BlockPosition head = new BlockPosition(16, 64, 17);
        server.ticker().submit(() -> {
            carveRoom(end, 16, 64, 16);
            placeBed(end, foot);
        });
        await(() -> BuiltinBlocks.BED_HEAD_SOUTH.equals(end.getBlock(head))
                && BuiltinBlocks.BED_FOOT_SOUTH.equals(end.getBlock(foot)),
                100, "the End bed pair landed");

        server.ticker().submit(() -> server.world()
                .setTimeOfDay(MobManager.NIGHT_START + 100));
        await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START,
                40, "night falls on the clock the pre-fix sleep walk reads");

        AtomicReference<EngineServer.ExplosionEvent> blast = new AtomicReference<>();
        server.addExplosionListener(blast::set);
        server.useItemOnBlock(camper, foot, 1, Optional.empty(), windowId -> { });

        await(() -> end.getBlock(foot).equals(end.airType())
                        && end.getBlock(head).equals(end.airType()),
                60, "VT-008: the End bed's pair left whole");
        await(() -> blast.get() != null, 60, "the End bed's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(1, blast.get().dimension(),
                "the blast ranges the End (the acting world)");
        assertFalse(camper.sleeping(), "the End bed never entered the sleep walk");
        assertNull(camper.bedSpawn(), "the End arm never sets the spawn");
        assertTrue(fireLandedNear(end, 16, 64, 16),
                "the always-true createFire row placed fire in the End crater");
    }

    /**
     * SEAT 4 — THE BIOME CONJUNCT (the anvil-import custom-map edge, seated
     * without the import fixture): the gate's second conjunct is its own
     * law — an OVERWORLD bed (dimension 0, {@code hasSpawnPoint()} true)
     * in a HELL-biome column refuses the sleep walk and explodes IN THE
     * OVERWORLD. The seat writes the chunk's own biome rows directly
     * (the same rows the anvil import would carry; HELL's 1.8 wire id 8).
     */
    @Test
    void theOverworldHellBiomeColumnBedExplodes() throws Exception {
        PlayerSession camper = joinStamped("HellCamper",
                new Position(11.5, 5.0, 16.5), 0);
        BlockPosition foot = new BlockPosition(8, 5, 16);
        BlockPosition head = new BlockPosition(8, 5, 17);
        server.ticker().submit(() -> {
            server.world().setBlock(foot, BuiltinBlocks.BED_FOOT_SOUTH);
            server.world().setBlock(head, BuiltinBlocks.BED_HEAD_SOUTH);
        });
        await(() -> EngineServer.isBedHalf(server.world().getBlock(head)),
                100, "the overworld bed pair landed");

        // The HELL biome rows: both the foot's and the head's columns
        // (the gate consults the head's; the foot's row rides along).
        server.ticker().submit(() -> {
            EngineChunk chunk = server.world().peek(new ChunkPosition(8 >> 4, 16 >> 4));
            chunk.setBiome(8 & 15, 16 & 15, (byte) 8); // Biome.HELL's wire id
            chunk.setBiome(8 & 15, 17 & 15, (byte) 8);
        });
        await(() -> "hell".equals(server.world().biomeAt(head)),
                40, "the head's column answers HELL (the conjunct's own consult)");

        server.ticker().submit(() -> server.world()
                .setTimeOfDay(MobManager.NIGHT_START + 100));
        await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START,
                40, "night falls (the pre-fix tree would sleep here)");

        AtomicReference<EngineServer.ExplosionEvent> blast = new AtomicReference<>();
        server.addExplosionListener(blast::set);
        server.useItemOnBlock(camper, foot, 1, Optional.empty(), windowId -> { });

        await(() -> server.world().getBlock(foot).equals(server.world().airType())
                        && server.world().getBlock(head).equals(server.world().airType()),
                60, "VT-008: the hell-column pair left whole");
        await(() -> blast.get() != null, 60, "the hell-column bed blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(0, blast.get().dimension(),
                "the blast ranges the OVERWORLD (the acting world — the conjunct alone refuses)");
        assertFalse(camper.sleeping(),
                "the HELL conjunct refuses the sleep walk even in the overworld");
        assertNull(camper.bedSpawn(), "the hell-column arm never sets the spawn");
    }

    /**
     * SEAT 5 — THE GUARD: the overworld bed in a plains column still
     * sleeps at night — the dimension/biome gate must not touch the sleep
     * arm. Passes on both the fixed and the pre-fix tree (the pin against
     * over-correction).
     */
    @Test
    void theOverworldBedStillSleepsAtNight() throws Exception {
        PlayerSession camper = joinStamped("Camper",
                new Position(6.5, 5.0, 24.5), 0);
        BlockPosition foot = new BlockPosition(4, 5, 24);
        BlockPosition head = new BlockPosition(4, 5, 25);
        server.ticker().submit(() -> {
            server.world().setBlock(foot, BuiltinBlocks.BED_FOOT_SOUTH);
            server.world().setBlock(head, BuiltinBlocks.BED_HEAD_SOUTH);
        });
        await(() -> EngineServer.isBedHalf(server.world().getBlock(head)),
                100, "the overworld bed pair landed");
        server.ticker().submit(() -> server.world()
                .setTimeOfDay(MobManager.NIGHT_START + 100));
        await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START,
                40, "night falls");

        AtomicReference<EngineServer.ExplosionEvent> blast = new AtomicReference<>();
        server.addExplosionListener(blast::set);
        server.useItemOnBlock(camper, foot, 1, Optional.empty(), windowId -> { });

        // The persistent outcomes of the sleep walk (SleepAcceptanceTest's
        // own await form — the sleeping flag itself is transient: tickSleep
        // jumps the clock to dawn and wakes the single sleeper within a
        // tick of the click): the world wakes at dawn and the spawn is set.
        await(() -> server.world().timeOfDay() < MobManager.NIGHT_START,
                60, "the world wakes at dawn (the sleep walk ran)");
        assertTrue(camper.bedSpawn() != null,
                "the sleep walk set the spawn (the ordinary arm's own law)");
        assertTrue(EngineServer.isBedHalf(server.world().getBlock(head))
                        && EngineServer.isBedHalf(server.world().getBlock(foot)),
                "the overworld bed's pair stands whole");
        assertNull(blast.get(), "no blast ever fired in the overworld arm");
    }

    // ------------------------------------------------------------------ harness

    /** The ordinary join (dimension 0) — DimensionWalk's own harness shape. */
    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var accepted = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), 100, name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, 100, name + " playing");
        return session;
    }

    /**
     * The stamped join — the dimension stamp + the placement (the crossing's
     * own end state, applied directly; the dispatch consults the stamp).
     */
    private PlayerSession joinStamped(String name, Position at, int dimension)
            throws InterruptedException {
        PlayerSession session = join(name);
        session.setDimension(dimension);
        session.applyMovement(at, Rotation.ZERO, true);
        return session;
    }

    /**
     * The bed pair: the foot at the given cell, the head SOUTH of it
     * (the pair layout every scene rides; the FACING row the reference's
     * else arm walks back from the head).
     */
    private static void placeBed(EngineWorld world, BlockPosition foot) {
        world.setBlock(foot, BuiltinBlocks.BED_FOOT_SOUTH);
        world.setBlock(foot.offset(0, 0, 1), BuiltinBlocks.BED_HEAD_SOUTH);
    }

    /**
     * The carve: a 15x15 room — the dirt floor at {@code y - 1}, air
     * {@code y .. y + 6} (the seed-independent scene; the natural terrain
     * never reaches the bed).
     */
    private static void carveRoom(EngineWorld world, int cx, int y, int cz) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                world.setBlock(new BlockPosition(cx + dx, y - 1, cz + dz), BuiltinBlocks.DIRT);
                for (int yy = y; yy <= y + 6; yy++) {
                    world.setBlock(new BlockPosition(cx + dx, yy, cz + dz), world.airType());
                }
            }
        }
    }

    /** Whether FIRE landed anywhere in the blast's crater region. */
    private static boolean fireLandedNear(EngineWorld world, int cx, int y, int cz) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int yy = y - 1; yy <= y + 5; yy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (BuiltinBlocks.FIRE.equals(world.getBlock(
                            new BlockPosition(cx + dx, yy, cz + dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The census's EngineAwaits form (the tick-denominated budget). */
    private void await(BooleanSupplier condition, long tickBudget, String description)
            throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, tickBudget, description);
    }
}
