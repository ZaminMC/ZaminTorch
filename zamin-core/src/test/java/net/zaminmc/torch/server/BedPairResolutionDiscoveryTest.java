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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-010 (the twenty-seventh pass's discovery) — the bed pair's
 * SIBLING RESOLUTION is a positional scan, not the reference's
 * FACING-EXACT law. Every bed walk in the engine ({@code
 * EngineServer.bedHeadCellIn} lines 15128-15141, {@code bedHeadCell}
 * lines 15198-15214, {@code detonateBedOnTick}'s foot removal lines
 * 15103-15111, and {@code bedSiblingCleanup}'s selection lines
 * 14684-14696) scans the four horizontal neighbors for ANY half of the
 * opposite class — while the reference reads the half's OWN facing
 * property: a FOOT redirects to {@code pos.offset(state.get(FACING))}
 * (BedBlock.use lines 40-41), the else arm removes the foot at
 * {@code pos.offset(state.get(FACING).getOpposite())} (use lines 71-75),
 * and neighborChanged's pair walk tests the cell the facing names
 * (lines 156-165). The engine's own identifiers CARRY the pair direction
 * ({@code bed_head_south}'s foot sits north) — the placement writes the
 * suffix for both halves — so the facing-exact cell is computable and the
 * scan is pure approximation.
 *
 * <p>THE DIVERGENCE IS REACHABLE WITH TWO ADJACENT BEDS (normal
 * placement — nothing in vanilla or the engine forbids a foreign bed
 * half adjacent to a pair): a foreign opposite-class half adjacent to
 * the clicked or broken half wins the scan, and the engine resolves the
 * WRONG PAIR — the sleep walk (and its spawn point) lands on the foreign
 * bed's head, the nether blast tears at the foreign pair's center, and
 * the break-path pair death kills the foreign pair's half while
 * orphaning the clicked pair's own sibling. Every one of these
 * divergences is observable in the OVERWORLD too — the model is
 * dimension-blind.</p>
 *
 * <p>THE SEATS build the foreign-half adjacency through the engine's own
 * placement geometry (two pairs at a right angle) and drive the real
 * use/break wires. Vanilla law: the clicked/broken half's OWN facing
 * names the sibling; the foreign pair is never touched by the pair arm.</p>
 */
class BedPairResolutionDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld over;
    private EngineWorld nether;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bedpair" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        over = server.world();
        nether = server.worldFor(-1);
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

    /**
     * THE BREAK SELECTION: bed A (foot (20,5,0) south-facing, head
     * (20,5,1)) with bed B's FOOT at (21,5,1) east of A's head — bed B:
     * foot (21,5,1) east-facing, head (22,5,1). Bed A's HEAD is broken:
     * the reference removes A's OWN foot at the head's facing-opposite
     * ((20,5,0) — bed_head_south's foot sits north) and never touches the
     * foreign pair. The engine's scan consults (21,5,1) FIRST (+x wins)
     * and kills BED B's foot while orphaning A's own.
     */
    @Test
    void theBrokenHeadRemovesItsOwnFootNotTheForeignPair() throws Exception {
        BlockPosition aFoot = new BlockPosition(20, 5, 0);
        BlockPosition aHead = new BlockPosition(20, 5, 1);
        BlockPosition bFoot = new BlockPosition(21, 5, 1);
        BlockPosition bHead = new BlockPosition(22, 5, 1);
        server.ticker().submit(() -> {
            over.setBlock(aFoot, BuiltinBlocks.BED_FOOT_SOUTH);
            over.setBlock(aHead, BuiltinBlocks.BED_HEAD_SOUTH);
            over.setBlock(bFoot, BuiltinBlocks.BED_FOOT_EAST);
            over.setBlock(bHead, BuiltinBlocks.BED_HEAD_EAST);
        });
        EngineAwaits.awaitCondition(over,
                () -> EngineServer.isBedHalf(over.getBlock(aHead))
                        && EngineServer.isBedHalf(over.getBlock(bHead)),
                80, "both pairs landed");

        PlayerSession breaker = join("HeadBreaker", new Position(20.5, 5.0, 3.5), 0);
        EngineAwaits.awaitCondition(over,
                () -> breaker.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the breaker reaches PLAYING");
        server.ticker().submit(() -> breaker.setGamemode(GameMode.CREATIVE));

        server.blockInteraction().submitCreativeBreak(breaker, aHead);
        EngineAwaits.awaitCondition(over,
                () -> over.getBlock(aHead).equals(over.airType()),
                40, "the head's break committed");
        EngineAwaits.awaitTicks(over, 6, "the pair-death walk settles");

        net.zaminmc.torch.block.BlockType ownFoot = over.getBlock(aFoot);
        net.zaminmc.torch.block.BlockType foreignFoot = over.getBlock(bFoot);
        assertAll(
                () -> assertEquals(over.airType(), ownFoot,
                        "VT-010: the head's OWN foot dies with it (BedBlock.neighborChanged lines 159-165 — the facing-opposite cell, bed_head_south's foot sits NORTH at (20,5,0))"),
                () -> assertEquals(BuiltinBlocks.BED_FOOT_EAST, foreignFoot,
                        "VT-010: the foreign pair's foot stands (the reference reads the head's OWN facing — the scan has no such law, +x wins and kills the wrong half)"),
                () -> assertEquals(BuiltinBlocks.BED_HEAD_EAST, over.getBlock(bHead),
                        "the foreign pair's head was never adjacent to the broken half (the control)"));
    }

    /**
     * THE SLEEP REDIRECT: bed A (foot (20,5,0) south-facing, head
     * (20,5,1)) with bed C's HEAD at (21,5,0) east of A's foot — bed C:
     * head (21,5,0) west-facing, foot (22,5,0). Clicking A's FOOT at
     * night: the reference redirects by the foot's OWN facing
     * (bed_foot_south → the head SOUTH at (20,5,1)) and the sleep walk
     * sets the spawn there. The engine's scan consults (21,5,0) FIRST and
     * resolves BED C's head — the sleep pose and the bed spawn land on
     * the FOREIGN pair.
     */
    @Test
    void theFootClickSleepsInItsOwnHeadNotTheForeignPair() throws Exception {
        BlockPosition aFoot = new BlockPosition(20, 5, 0);
        BlockPosition aHead = new BlockPosition(20, 5, 1);
        BlockPosition cHead = new BlockPosition(21, 5, 0);
        BlockPosition cFoot = new BlockPosition(22, 5, 0);
        server.ticker().submit(() -> {
            over.setBlock(aFoot, BuiltinBlocks.BED_FOOT_SOUTH);
            over.setBlock(aHead, BuiltinBlocks.BED_HEAD_SOUTH);
            over.setBlock(cHead, BuiltinBlocks.BED_HEAD_WEST);
            over.setBlock(cFoot, BuiltinBlocks.BED_FOOT_WEST);
        });
        EngineAwaits.awaitCondition(over,
                () -> EngineServer.isBedHalf(over.getBlock(aHead))
                        && EngineServer.isBedHalf(over.getBlock(cHead)),
                80, "both pairs landed");

        PlayerSession sleeper = join("NapSeeker", new Position(20.5, 5.0, 3.5), 0);
        EngineAwaits.awaitCondition(over,
                () -> sleeper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the sleeper reaches PLAYING");

        server.ticker().submit(() -> server.world()
                .setTimeOfDay(net.zaminmc.torch.server.entity.MobManager.NIGHT_START + 100));
        EngineAwaits.awaitCondition(over,
                () -> server.world().timeOfDay()
                        >= net.zaminmc.torch.server.entity.MobManager.NIGHT_START,
                40, "night falls");

        server.useItemOnBlock(sleeper, aFoot, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitCondition(over, sleeper::sleeping,
                40, "the foot click entered the sleep walk (both trees — the divergence is WHERE)");

        Position spawn = sleeper.bedSpawn();
        assertNotNullSpawn(spawn);
        assertAll(
                () -> assertEquals(20.5, spawn.x(), 1e-9,
                        "VT-010: the bed spawn lands on the clicked foot's OWN head (use lines 40-41 — bed_foot_south's head sits SOUTH at (20,5,1))"),
                () -> assertEquals(1.5, spawn.z(), 1e-9,
                        "VT-010: the spawn's z names the own head's cell (the pre-fix scan resolves the foreign head at (21,5,0) — x 21.5, z 0.5)"));
    }

    private static void assertNotNullSpawn(Position spawn) {
        assertTrue(spawn != null, "the sleep walk set a bed spawn (both trees)");
    }

    /**
     * THE NETHER BLAST CENTER: bed A (foot (64,101,64) south-facing, head
     * (64,101,65)) with bed D's HEAD at (65,101,64) east of A's foot —
     * bed D: head (65,101,64) west-facing, foot (66,101,64). Clicking A's
     * FOOT in the nether: the reference's else arm resolves the OWN head
     * at (64,101,65) — the pair removed there and the power-5 fire blast
     * centered at ITS cell (use lines 68-78). The engine's scan resolves
     * BED D's head first — the pair-arm removes the FOREIGN pair and the
     * blast tears at the foreign head's center.
     */
    @Test
    void theNetherBlastTearsAtTheClickedPairsOwnHead() throws Exception {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(new BlockPosition(64, 101, 64), BuiltinBlocks.BED_FOOT_SOUTH);
            nether.setBlock(new BlockPosition(64, 101, 65), BuiltinBlocks.BED_HEAD_SOUTH);
            nether.setBlock(new BlockPosition(65, 101, 64), BuiltinBlocks.BED_HEAD_WEST);
            nether.setBlock(new BlockPosition(66, 101, 64), BuiltinBlocks.BED_FOOT_WEST);
        });
        BlockPosition aFoot = new BlockPosition(64, 101, 64);
        BlockPosition aHead = new BlockPosition(64, 101, 65);
        EngineAwaits.awaitCondition(over,
                () -> EngineServer.isBedHalf(nether.getBlock(aHead))
                        && EngineServer.isBedHalf(nether.getBlock(aFoot)),
                80, "the nether pairs landed");

        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession camper = join("NetherNapAttempt",
                new Position(66.5, 101.0, 66.5), -1);
        EngineAwaits.awaitCondition(over,
                () -> camper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the camper reaches PLAYING");

        server.useItemOnBlock(camper, aFoot, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitCondition(over, () -> blast.get() != null,
                40, "the wrong-world blast fired (both trees — the divergence is WHERE)");

        EngineServer.ExplosionEvent event = blast.get();
        assertAll(
                () -> assertEquals(64.5, event.x(), 1e-9,
                        "VT-010: the blast centers on the clicked foot's OWN head (use line 78's explode at the redirected head — (64,101,65); the pre-fix scan resolves the foreign head at (65,101,64) — x 65.5)"),
                () -> assertEquals(65.5, event.z(), 1e-9,
                        "VT-010: the blast's z names the own head's cell (the pre-fix scan's answer is z 64.5)"),
                () -> assertEquals(5.0f, event.radius(),
                        "the bed blast's power (the wrong-world law — passes both trees)"),
                () -> assertEquals(-1, event.dimension(),
                        "the blast ranges the player's own dimension (passes both trees)"),
                () -> assertFalse(camper.sleeping(),
                        "the wrong-world bed never entered the sleep walk (passes both trees)"));
    }
}
