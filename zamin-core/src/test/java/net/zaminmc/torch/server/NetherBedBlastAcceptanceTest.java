package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-008's VERIFICATION SEAT (docs/vanilla-conformance-audit.md, the
 * twenty-seventh pass) — the independent behavioral pass over PR #102's
 * MERGED correction: the wrong-world bed's blast arm.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8, BedBlock.use lines 45-84): a bed
 * used outside a spawn-point dimension — the nether and the End answer
 * {@code dimension.hasSpawnPoint()} false, a Hell-biome column refuses the
 * same way — runs the ELSE arm: both halves are removed (the head first,
 * then its sibling foot) and {@code world.explode(null, ..., 5.0F, true,
 * true)} tears the ACTING world at the head's center — power 5, fire
 * always, never the sleep walk.</p>
 *
 * <p>THE CONSTRUCTION (the pass-26 record's deferred seat — the
 * nether-anchored session): the body walks a real portal into the nether
 * (the DimensionWalk construction), a bed pair stands on a controlled
 * stone platform beside the arrival, and the right-click drives the use
 * path. THE VERIFICATION: the pair dies, the blast tears the NETHER's
 * platform (the holed-cell count), the identical platform in the OVERWORLD
 * at the same coordinates stays whole (the routing pin — a mis-routed
 * blast would carve it), and the sleep walk never runs (no spawn set —
 * the pre-fix shape's own signature).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetherBedBlastAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "netherbed" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " reached PLAYING");
        return session;
    }

    /** The 4x5 X-plane frame (the DimensionWalk test's own build shape). */
    private static void buildPortal(net.zaminmc.torch.server.world.EngineWorld world,
                                    int x, int y, int z) {
        for (int w = 0; w < 4; w++) {
            for (int h = -1; h < 4; h++) {
                boolean frameCell = w == 0 || w == 3 || h == -1 || h == 3;
                world.setBlock(new BlockPosition(x + w, y + h, z),
                        frameCell ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
            }
        }
    }

    /**
     * The controlled platform: a 13x13 stone floor at {@code floorY} under
     * a 13x13x9 air carve (the nether's seed-drifting natural terrain kept
     * out of the blast's reach — the pass-25 lesson), so the blast's
     * holed-cell count reads only the platform.
     */
    private static void layPlatform(net.zaminmc.torch.server.world.EngineWorld world,
                                    int cx, int floorY, int cz) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = 0; dy <= 8; dy++) {
                    world.setBlock(new BlockPosition(cx + dx, floorY + dy, cz + dz),
                            world.airType());
                }
                world.setBlock(new BlockPosition(cx + dx, floorY, cz + dz),
                        BuiltinBlocks.STONE);
            }
        }
    }

    private static long stoneCells(net.zaminmc.torch.server.world.EngineWorld world,
                                   int cx, int floorY, int cz) {
        long count = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (BuiltinBlocks.STONE.equals(world.getBlock(
                        new BlockPosition(cx + dx, floorY, cz + dz)))) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * VT-008's verification: the nether bed's right-click blasts — the pair
     * dies, the nether's platform tears, the overworld's twin stays whole,
     * and the sleep walk never runs.
     */
    @Test
    void theNetherBedBlastsItsOwnWorldOnUse() throws Exception {
        PlayerSession walker = join("Walker");
        var overworld = server.world();
        var nether = server.netherWorld();

        // The overworld portal at (40, 20, 40) — the DimensionWalk
        // construction; the body walks the real crossing into the nether.
        int frameX = 40;
        int frameZ = 40;
        int frameY = 20;
        server.ticker().submit(() -> buildPortal(overworld, frameX, frameY, frameZ));
        await(() -> overworld.getBlock(new BlockPosition(frameX + 1, frameY, frameZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");
        server.teleportPlayer(walker, new Position(frameX + 1.5, frameY + 0.1, frameZ + 0.5));
        EngineAwaits.awaitCondition(server.world(), () -> walker.dimension() == -1,
                400, "the body crossed to the nether");

        // The arrival: the forcer's placement near the scaled destination
        // (40/8 = 5). The bed site sits HIGH ABOVE the nether's natural
        // terrain (y=100 — the 13x13x9 carve clears whatever the seed
        // drifted there) and AWAY from the arrival frame (the +30 offset:
        // the pair must not stand in portal cells, and the carve must not
        // disturb the frame the body stands in). The use path carries no
        // reach gate (the reference trusts the client's own reach), so the
        // body's position never constrains the click.
        Position arrival = walker.position();
        int bedX = (int) Math.floor(arrival.x()) + 30;
        int bedZ = (int) Math.floor(arrival.z()) + 30;
        int floorY = 100;

        // Load the bed site's chunk before the platform lands.
        ChunkPosition bedChunk = new ChunkPosition(bedX >> 4, bedZ >> 4);
        if (!nether.isChunkLoaded(bedChunk)) {
            server.requestChunkLoad(-1, bedChunk, chunk -> { });
        }
        EngineAwaits.awaitCondition(overworld, () -> nether.isChunkLoaded(bedChunk),
                200, "the bed site chunk loaded");

        // The twin platforms: the nether's (the blast's target) and the
        // overworld's at the SAME coordinates (the routing pin).
        server.ticker().submit(() -> {
            layPlatform(nether, bedX, floorY, bedZ);
            layPlatform(overworld, bedX, floorY, bedZ);
        });
        await(() -> BuiltinBlocks.STONE.equals(nether.getBlock(
                        new BlockPosition(bedX, floorY, bedZ)))
                        && BuiltinBlocks.STONE.equals(overworld.getBlock(
                        new BlockPosition(bedX, floorY, bedZ))),
                "the twin platforms stand");
        EngineAwaits.awaitTicks(overworld, 10, "the platforms settle");
        long netherStoneBefore = stoneCells(nether, bedX, floorY, bedZ);
        long overworldStoneBefore = stoneCells(overworld, bedX, floorY, bedZ);
        assertEquals(169, netherStoneBefore, "the nether platform whole before the use");
        assertEquals(169, overworldStoneBefore, "the overworld platform whole before the use");

        // The body steps off the arrival frame's cells (the stand clock
        // decays, no re-crossing) and onto the platform's west corner —
        // clear of the pair's cells.
        server.teleportPlayer(walker, new Position(bedX - 2.5, floorY + 1.0, bedZ + 0.5));
        await(() -> walker.position().distanceSquared(
                new Position(bedX - 2.5, floorY + 1.0, bedZ + 0.5)) < 4.0,
                "the body stands on the platform");

        // The bed pair: the foot south of the head, both on the platform —
        // set directly (the placement path is SleepAcceptanceTest's own
        // seat; this seat drives the USE arm).
        BlockPosition foot = new BlockPosition(bedX, floorY + 1, bedZ);
        BlockPosition head = foot.offset(0, 0, 1);
        server.ticker().submit(() -> {
            nether.setBlock(foot, BuiltinBlocks.BED_FOOT_SOUTH);
            nether.setBlock(head, BuiltinBlocks.BED_HEAD_SOUTH);
        });
        await(() -> EngineServer.isBedHalf(nether.getBlock(foot))
                        && EngineServer.isBedHalf(nether.getBlock(head)),
                "the bed pair stands in the nether");

        // The right-click: the use path drives the bed arm.
        server.useItemOnBlock(walker, foot, 1, java.util.Optional.empty(),
                windowId -> { });

        // THE PAIR DIES (use lines 70-75: the head first, then the foot).
        await(() -> nether.getBlock(foot).equals(nether.airType())
                        && nether.getBlock(head).equals(nether.airType()),
                "the pair removed by the use");

        // THE BLAST TEARS THE NETHER (use line 78: the power-5 fire blast
        // at the head's center in the ACTING world). The holed-cell count
        // over the platform: a power-5 blast's footprint.
        EngineAwaits.awaitTicks(overworld, 5, "the blast settles");
        long netherStoneAfter = stoneCells(nether, bedX, floorY, bedZ);
        long overworldStoneAfter = stoneCells(overworld, bedX, floorY, bedZ);
        assertTrue(netherStoneBefore - netherStoneAfter >= 10,
                "the nether platform torn by the power-5 blast (before "
                        + netherStoneBefore + ", after " + netherStoneAfter + ")");
        assertEquals(169, overworldStoneAfter,
                "the overworld twin whole — the blast never leaves the acting world (before "
                        + overworldStoneBefore + ", after " + overworldStoneAfter + ")");

        // THE SLEEP WALK NEVER RAN (the pre-fix shape's signature: the
        // spawn set + the body in bed).
        assertNull(walker.bedSpawn(), "no spawn point set by a nether bed");
        assertTrue(!walker.sleeping(), "the body never entered the bed");
    }
}
