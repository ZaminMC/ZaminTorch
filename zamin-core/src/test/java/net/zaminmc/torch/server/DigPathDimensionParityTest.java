package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE DIG/PLACE PATH'S OWN DIMENSION ROUTING (VT-002's remaining slice —
 * the FL4-F3 wave's ledgered "dig/place GENERIC surface" tail: the
 * reference's per-packet world resolution, applied to
 * BlockInteractionService's dig/place walks).
 *
 * <p>THE LAW (reference/1.8.8 net/minecraft/server/network/handler/
 * ServerPlayNetworkHandler.java, handlePlayerHandAction lines 403-456 and
 * handlePlayerUse line 462): {@code ServerWorld serverworld = this.server
 * .getWorld(this.player.dimension)} — the dig, the use and the placement
 * all read and write the world the player stands in, in every dimension;
 * the interaction manager itself is constructed per world
 * (PlayerManager lines 349-372: {@code new ServerPlayerInteractionManager(
 * this.server.getWorld(player.dimension))}). The engine's single
 * BlockInteractionService consulted its overworld field for every player:
 * a nether dig never broke the nether block and destroyed the OVERWORLD
 * cell at the same coordinates instead (the persistent cross-dimension
 * corruption the twenty-first pass's seat demonstrated whole).</p>
 *
 * <p>Every seat asserts the REFERENCE behavior, not the engine's current
 * behavior: the seats are the fix branch's regression guards for the
 * routing correction (Wave VT-002). The nether harness is the FL4-F3
 * seat's own shape ({@code setDimension(-1)} + the deterministic
 * platform): the crossing walk's own re-fire law (the portal clock stays
 * at its max after a crossing — the engine's documented shape) makes the
 * portal-crossing harness seed-flaky, and the crossing is not the
 * variable under test; the WORLD CHOICE is.</p>
 *
 * <p>Seats in this file:</p>
 * <ul>
 *   <li>{@code theNetherSurvivalDigBreaksTheNetherBlockNotTheOverworldCell}:
 *       the VT-002 seat whole — the survival dig of a nether player
 *       breaks the NETHER cell, leaves the overworld probe untouched, and
 *       the drop stamps the NETHER's dimension (the four-arg TF-9 form).</li>
 *   <li>{@code theNetherCreativeBreakBreaksTheNetherCellOnly}: the creative
 *       break arm routes the same way (creativeBreakOnTick's own consults).</li>
 *   <li>{@code theNetherPlacementLandsInTheNetherNotTheOverworld}: the
 *       generic placement arm (placeOnTick) writes the nether's cell; the
 *       overworld target at the same coordinates is not part of the
 *       placement.</li>
 *   <li>{@code theOverworldDigStillBreaksTheOverworldBlock}: the standing
 *       control — the overworld dig resolves through the same consult and
 *       keeps working (the regression guard).</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DigPathDimensionParityTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    /** The nether platform's floor Y — high above the nether's natural terrain (the FL4-F3 fixture's own shape). */
    private static final int FLOOR_Y = 100;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "digdim" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var accepted = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /**
     * Carves the deterministic nether platform (the FL4-F3 fixture's own
     * shape): the stone plate floor at {@link #FLOOR_Y} with cleared air
     * above, around the working origin (64, 64).
     */
    private void carvePlatform(EngineWorld nether) throws Exception {
        for (int cx = 60 >> 4; cx <= 68 >> 4; cx++) {
            for (int cz = 60 >> 4; cz <= 68 >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!nether.isChunkLoaded(at)) {
                    server.requestChunkLoad(nether.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.isChunkLoaded(new ChunkPosition(60 >> 4, 60 >> 4))
                        && nether.isChunkLoaded(new ChunkPosition(68 >> 4, 68 >> 4)),
                200, "the platform chunks loaded");
        server.ticker().submit(() -> {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                    nether.setBlock(new BlockPosition(64 + dx, FLOOR_Y, 64 + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(new BlockPosition(64, FLOOR_Y, 64))),
                80, "the platform floor landed");
    }

    /**
     * Joins the named player and stands it IN THE NETHER on the platform,
     * two cells south of the working origin — the FL4-F3 harness's own
     * stable shape ({@code setDimension(-1)} + the authoritative
     * movement with onGround=true, so the dig's airborne 5x divisor never
     * parks).
     */
    private PlayerSession standInNether(String name) throws Exception {
        PlayerSession walker = join(name);
        walker.setDimension(-1);
        server.ticker().submit(() -> walker.applyMovement(
                new Position(64.5, FLOOR_Y + 1.0, 66.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 4, "the stand lands");
        assertEquals(-1, walker.dimension(), "the body stands in the nether");
        assertTrue(walker.position().distanceSquared(
                        new Position(64.5, FLOOR_Y + 1.0, 66.5)) < 4.0,
                "the body stands beside the nether target (position " + walker.position() + ")");
        return walker;
    }

    /**
     * VT-002 (the remaining slice's primary seat): a survival dig
     * submitted by a nether-standing player reads and writes the NETHER
     * cell; the overworld probe at the same coordinates is never part of
     * the dig; the drop stamps the nether's dimension.
     */
    @Test
    void theNetherSurvivalDigBreaksTheNetherBlockNotTheOverworldCell() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        EngineWorld over = server.world();
        carvePlatform(nether);
        ensureOverworldLoaded(over);

        BlockPosition target = new BlockPosition(64, FLOOR_Y + 1, 64);
        // The corruption probe: overworld DIRT at the SAME coordinates —
        // the same block the dig target holds, so every dig gate (digability,
        // progress, the finish's 0.7 floor, the anti-cheat budget) passes
        // identically against either cell and the WORLD CHOICE is the only
        // variable (the discovery seat's own design note).
        server.ticker().submit(() -> {
            nether.setBlock(target, BuiltinBlocks.DIRT);
            over.setBlock(target, BuiltinBlocks.DIRT);
        });
        await(() -> nether.getBlock(target).equals(BuiltinBlocks.DIRT)
                && over.getBlock(target).equals(BuiltinBlocks.DIRT),
                "both cells landed (nether dirt + the overworld probe dirt)");

        PlayerSession walker = standInNether("NetherDigger");

        server.blockInteraction().submitMiningStart(walker, target, 1);
        EngineAwaits.awaitTicks(server.world(), 40,
                "dirt by hand: the 15-tick nominal dig plus margin");
        server.blockInteraction().submitMiningFinished(walker, target);
        EngineAwaits.awaitTicks(server.world(), 12, "the finish walk lands");

        net.zaminmc.torch.block.BlockType netherCell = nether.getBlock(target);
        net.zaminmc.torch.block.BlockType overworldCell = over.getBlock(target);
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(BuiltinBlocks.AIR, netherCell,
                        "VT-002: the nether dig breaks the NETHER block (the reference's per-world manager)"),
                () -> assertEquals(BuiltinBlocks.DIRT, overworldCell,
                        "VT-002: the overworld cell at the same coordinates is NOT part of a nether dig"),
                () -> assertEquals(-1, walker.dimension(),
                        "the digger still stands in the nether"));

        // THE DROP'S OWN DIMENSION STAMP (the four-arg TF-9 form): the
        // broken dirt's drop is a NETHER entity — the misrouted walk would
        // stamp the overworld's 0 at the same coordinates.
        boolean netherDrop = false;
        boolean overworldDrop = false;
        for (var entity : server.itemEntities().all()) {
            if (!entity.stack().type().identifier().equals(BuiltinBlocks.DIRT.identifier())) {
                continue;
            }
            if (entity.dimension() == -1) {
                netherDrop = true;
            } else if (entity.dimension() == 0) {
                overworldDrop = true;
            }
        }
        assertTrue(netherDrop, "the nether dig's drop stamps the nether dimension (-1)");
        assertFalse(overworldDrop, "no dirt drop stamps the overworld dimension (the misroute's own drop)");
    }

    /**
     * The creative break arm routes the same way: a nether-standing
     * creative player's instant break removes the NETHER cell only.
     */
    @Test
    void theNetherCreativeBreakBreaksTheNetherCellOnly() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        EngineWorld over = server.world();
        carvePlatform(nether);
        ensureOverworldLoaded(over);

        BlockPosition target = new BlockPosition(64, FLOOR_Y + 1, 64);
        server.ticker().submit(() -> {
            nether.setBlock(target, BuiltinBlocks.DIRT);
            over.setBlock(target, BuiltinBlocks.DIRT);
        });
        await(() -> nether.getBlock(target).equals(BuiltinBlocks.DIRT)
                && over.getBlock(target).equals(BuiltinBlocks.DIRT),
                "both cells landed (nether dirt + the overworld probe dirt)");

        PlayerSession walker = standInNether("NetherSmasher");

        server.blockInteraction().submitCreativeBreak(walker, target, 1);
        EngineAwaits.awaitTicks(server.world(), 12, "the creative break lands");

        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(BuiltinBlocks.AIR, nether.getBlock(target),
                        "the creative break removes the NETHER cell"),
                () -> assertEquals(BuiltinBlocks.DIRT, over.getBlock(target),
                        "the overworld probe is not part of a nether creative break"),
                () -> assertEquals(-1, walker.dimension(),
                        "the breaker still stands in the nether"));
    }

    /**
     * The generic placement arm (placeOnTick — the FL4-F3 wave's ledgered
     * "generic fallback placement" tail): a nether-standing player's
     * placement writes the NETHER's cell; the overworld target at the same
     * coordinates is untouched. The clicked-cell probe (overworld stone at
     * the clicked coordinates) keeps the unfixed walk's clicked-gate
     * passing against the overworld too, so the WORLD CHOICE stays the
     * only variable on the write.
     */
    @Test
    void theNetherPlacementLandsInTheNetherNotTheOverworld() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        EngineWorld over = server.world();
        carvePlatform(nether);
        ensureOverworldLoaded(over);

        // The click lands on the nether platform's floor stone; the target
        // is the air cell above it.
        BlockPosition clicked = new BlockPosition(64, FLOOR_Y, 64);
        BlockPosition target = new BlockPosition(64, FLOOR_Y + 1, 64);
        // The clicked-gate probe: overworld STONE at the clicked
        // coordinates (air above), so the unfixed walk's clicked consult
        // passes against the overworld as well.
        server.ticker().submit(() -> {
            over.setBlock(clicked, BuiltinBlocks.STONE);
            over.setBlock(target, over.airType());
        });
        await(() -> over.getBlock(clicked).equals(BuiltinBlocks.STONE),
                "the overworld clicked-gate probe landed");

        PlayerSession walker = standInNether("NetherPlacer");

        server.blockInteraction().submitPlace(walker, clicked, 1, BuiltinBlocks.STONE);
        EngineAwaits.awaitTicks(server.world(), 12, "the placement lands");

        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(BuiltinBlocks.STONE, nether.getBlock(target),
                        "the nether placement writes the NETHER cell"),
                () -> assertEquals(over.airType(), over.getBlock(target),
                        "the overworld target at the same coordinates is not part of the placement"),
                () -> assertEquals(-1, walker.dimension(),
                        "the placer still stands in the nether"));
    }

    /**
     * The standing control: the overworld dig resolves through the same
     * per-walk consult and keeps working (the regression guard — the
     * routing must not disturb the overworld's own digs).
     */
    @Test
    void theOverworldDigStillBreaksTheOverworldBlock() throws Exception {
        PlayerSession digger = join("OverworldDigger");
        BlockPosition target = new BlockPosition(8, 5, 8);
        // Seed the floor the engine's own placement commits, then the
        // target dirt, then stand beside it (the dig-handler test's own
        // pattern).
        server.blockInteraction().submitPlace(digger, target.offset(0, -1, 0), 1,
                BuiltinBlocks.DIRT);
        await(() -> server.world().getBlock(target.offset(0, -1, 0)).equals(BuiltinBlocks.DIRT),
                "the floor block landed");
        server.ticker().submit(() -> server.world().setBlock(target, BuiltinBlocks.DIRT));
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.DIRT),
                "the target dirt landed");
        server.ticker().submit(() -> digger.applyMovement(new Position(8.5, 5.0, 10.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 6, "the stand lands");

        server.blockInteraction().submitMiningStart(digger, target, 1);
        EngineAwaits.awaitTicks(server.world(), 25,
                "dirt by hand: the 15-tick nominal dig plus margin");
        server.blockInteraction().submitMiningFinished(digger, target);
        EngineAwaits.awaitTicks(server.world(), 12, "the finish walk lands");

        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(target),
                "the overworld control dig still breaks the overworld block");
        assertEquals(0, digger.dimension(),
                "the control digger stands in the overworld");
    }

    /** Loads the overworld chunks around the working origin (the probe cells' world). */
    private void ensureOverworldLoaded(EngineWorld over) throws Exception {
        for (int cx = 60 >> 4; cx <= 68 >> 4; cx++) {
            for (int cz = 60 >> 4; cz <= 68 >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!over.isChunkLoaded(at)) {
                    server.requestChunkLoad(over.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(over,
                () -> over.isChunkLoaded(new ChunkPosition(60 >> 4, 60 >> 4))
                        && over.isChunkLoaded(new ChunkPosition(68 >> 4, 68 >> 4)),
                200, "the overworld probe chunks loaded");
    }
}
