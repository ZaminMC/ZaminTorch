package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The changeDimension walk on the live engine (the 8b-iii-b slice — the
 * PlayerManager port): a body standing in an overworld portal crosses the
 * stand clock's threshold, the 8:1 walk scales the coordinates, the nether
 * portal forcer builds the arrival frame, the body's dimension swaps to -1,
 * and the vanilla round trip brings it home — the arrival frame stands the
 * body in a portal again, the cooldown runs out, the return walk ×8 finds
 * the original overworld frame.
 */
class DimensionWalkTest {

    @TempDir
    Path dataDir;

    @Test
    void portalCrossingWalksToTheNetherAndBackHome() throws Exception {
        EngineServer server = boot();
        PlayerSession walker = join(server, "Walker");

        // The overworld portal: a 4x5 X-plane frame at (40, ground, 40) —
        // the walk's landing spot builds in the world the body stands in.
        // The tick thread owns the world; the frame lands there.
        int frameX = 40;
        int frameZ = 40;
        int frameY = 20; // high above the fixture's terrain: no interference
        server.ticker().submit(() -> buildOverworldPortal(server, frameX, frameY, frameZ));
        await(() -> server.world().isChunkLoaded(new ChunkPosition(2, 2))
                        && server.world().getBlock(new BlockPosition(frameX + 1, frameY, frameZ))
                        .equals(BuiltinBlocks.NETHER_PORTAL),
                "the overworld portal frame built");

        // Stand the body inside the frame's interior and let the stand
        // clock run (80 survival ticks; the tick walk drives everything).
        Position portalStand = new Position(frameX + 1.5, frameY + 0.1, frameZ + 0.5);
        server.teleportPlayer(walker, portalStand);
        await(() -> walker.position().distanceSquared(portalStand) < 4.0,
                "the body stands in the portal");

        // The crossing: the threshold at the 80th stand tick arms the walk —
        // the body arrives in the nether (dimension -1).
        await(() -> walker.dimension() == -1, "the body crossed to the nether");
        assertEquals(-1, walker.dimension());

        // The arrival frame: the forcer built a portal in the nether near
        // the scaled position (40/8 = 5) — the body stands inside portal
        // cells (the placement math lands the arrival inside the frame).
        Position netherArrival = walker.position();
        assertTrue(netherArrival.x() > -64 && netherArrival.x() < 64
                        && netherArrival.z() > -64 && netherArrival.z() < 64,
                "the arrival rides the 8:1 scale (got " + netherArrival + ")");
        server.ticker().submit(() -> { }); // sync point
        // The nether world holds the arrival chunk.
        await(() -> server.netherWorld().isChunkLoaded(new ChunkPosition(
                ((int) Math.floor(netherArrival.x())) >> 4,
                ((int) Math.floor(netherArrival.z())) >> 4)), "the arrival chunk loaded");

        // The vanilla round trip: the body stands in the arrival frame, the
        // 10-tick cooldown runs out, the stand clock crosses again — the
        // return walk ×8 finds the original overworld portal.
        await(() -> walker.dimension() == 0, 30_000,
                "the round trip walked the body home");
        assertEquals(0, walker.dimension());
        // The homecoming lands inside the original frame (the return search
        // finds the overworld portal it left from).
        Position home = walker.position();
        assertTrue(Math.abs(home.x() - (frameX + 1.5)) < 40
                        && Math.abs(home.z() - (frameZ + 0.5)) < 40,
                "the homecoming rides the ×8 scale back to the frame (got " + home + ")");
    }

    private static void buildOverworldPortal(EngineServer server, int x, int y, int z) {
        // The 4x5 X-plane frame: obsidian columns at x and x+3, portal
        // interior between (the PortalForcer's build shape).
        for (int w = 0; w < 4; w++) {
            for (int h = -1; h < 4; h++) {
                boolean frame = w == 0 || w == 3 || h == -1 || h == 3;
                server.world().setBlock(new BlockPosition(x + w, y + h, z),
                        frame ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
            }
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "dimwalk", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        await(condition, 20_000, description);
    }

    private void await(BooleanSupplier condition, long timeoutMillis, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
