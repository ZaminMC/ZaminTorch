package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-3 — the tripwire occupancy's own-box law (the Phase E
 * boundary arms over the tripwire family): the reference gathers
 * through {@code world.getEntities(null, Box(pos + minX..maxX ...))}
 * (TripwireBlock.updateOutputState lines 176-181) over the wire's own
 * shape box — [cell, cell+1] x [y, y+0.15625] x [cell, cell+1],
 * setShape line 44's attached form — each body's OWN box against it,
 * STRICT both sides (exactly touching is never intersecting), the
 * walk's only filter canAvoidTraps (the spectator players alone).
 *
 * <p>The pre-fix engine consulted the body's CENTER inside the box with
 * inclusive edges, and walked the mobs, the players and the items
 * alone: a body standing on the cell's EDGE — its box overlapping the
 * wire's box by a hair while its center sits outside the cell — never
 * tripped the wire, and the armor-stand family (a living body — the
 * stand's 0.5x1.975 hull trips vanilla's wire exactly like a mob's)
 * was never consulted at all.</p>
 */
class TripwireBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "twb" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE DISCRIMINATOR: the player's edge-stand trips the
    // wire (the body's box over the cell, the center outside)
    // ------------------------------------------------------------------

    /**
     * The player's 0.6x1.8 box standing with their center at x+1.2 —
     * the box [x+0.9, x+1.5] overlapping the wire's own box [x, x+1]
     * by 0.1, the center 0.2 OUTSIDE the cell — trips the wire. The
     * pre-fix center consult never saw them: RED pre-fix.
     */
    @Test
    void thePlayersEdgeStandTripsTheWire() throws Exception {
        int x = 12, z = 10, y = 40;
        placeWireRig(x, y, z);
        PlayerSession body = joined("EdgeStand");
        server.teleportPlayer(body, new Position(x + 1.2, y, z + 0.5));
        await(() -> TripwireBlocks.wirePowered(typeAt(x, y, z)),
                "the player's edge-stand trips the wire (the body's box over "
                        + "the cell, the center outside)");
        // The departure: the wire releases after its 10-tick re-scan.
        server.teleportPlayer(body, new Position(x + 1.2, y, z + 6.5));
        await(() -> !TripwireBlocks.wirePowered(typeAt(x, y, z)),
                "the wire released after the edge-stand body left");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE COVERAGE: the armor stand trips the wire (the
    // Entity-class census's living family)
    // ------------------------------------------------------------------

    /**
     * The reference's gather has NO class filter beyond
     * canAvoidTraps — the armor stand (a living body, the 0.5x1.975
     * hull of ArmorStandEntity's setSize) standing on the wire's cell
     * trips it exactly like a mob. Pre-fix the stand walk was absent
     * whole: RED pre-fix.
     */
    @Test
    void theArmorStandTripsTheWire() throws Exception {
        int x = 32, z = 10, y = 40;
        placeWireRig(x, y, z);
        AtomicReference<net.zaminmc.torch.server.armorstand.ArmorStand> stand =
                new AtomicReference<>();
        server.ticker().submit(() -> stand.set(server.armorStands().summonAt(
                x + 0.5, y, z + 0.5, 0, null)));
        await(() -> stand.get() != null, "the stand summoned on the wire");
        await(() -> TripwireBlocks.wirePowered(typeAt(x, y, z)),
                "the armor stand trips the wire (the Entity census — the "
                        + "stand's own 0.5x1.975 box over the cell)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the strictness straddle: a hair past the cell's face
    // never trips; a hair inside does
    // ------------------------------------------------------------------

    /**
     * Box.intersects refuses the exact touch on every face: a player
     * whose box's west edge sits a HAIR (1e-9) past the wire's east
     * face (the center at (x+1)+0.3+1e-9) never trips the wire over a
     * 3s never-window; the same player a hair inside trips it. The
     * boundary is pinned to a nanometer each side.
     */
    @Test
    void theExactTouchAtTheCellEdgeNeverTrips() throws Exception {
        int x = 52, z = 10, y = 40;
        placeWireRig(x, y, z);
        PlayerSession body = joined("Straddler");
        // The negative arm: a hair past the east face.
        server.teleportPlayer(body, new Position(x + 1 + 0.3 + 1e-9, y, z + 0.5));
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            assertFalse(TripwireBlocks.wirePowered(typeAt(x, y, z)),
                    "the box edge a hair past the cell's east face never trips "
                            + "the wire (the strict box law)");
            Thread.sleep(50);
        }
        // The positive arm: the same body a hair inside.
        server.teleportPlayer(body, new Position(x + 1 + 0.3 - 1e-9, y, z + 0.5));
        await(() -> TripwireBlocks.wirePowered(typeAt(x, y, z)),
                "the same body a hair inside the cell's face trips the wire");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** The hook-wire-hook chain on its stone floor, the wire at (x, y, z). */
    private void placeWireRig(int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                server.world().setBlock(new BlockPosition(x + dx, y - 1, z),
                        BuiltinBlocks.STONE);
            }
            server.world().setBlock(new BlockPosition(x - 3, y, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x + 3, y, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x - 2, y, z),
                    TripwireBlocks.hookOf(5, false, false)); // EAST
            server.world().setBlock(new BlockPosition(x + 2, y, z),
                    TripwireBlocks.hookOf(4, false, false)); // WEST
            server.world().setBlock(new BlockPosition(x, y, z),
                    TripwireBlocks.wireOf(false, false, false, false));
        });
        await(() -> TripwireBlocks.isTripwire(typeAt(x, y, z)), "the wire placed");
    }

    private BlockType typeAt(int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private PlayerSession joined(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
