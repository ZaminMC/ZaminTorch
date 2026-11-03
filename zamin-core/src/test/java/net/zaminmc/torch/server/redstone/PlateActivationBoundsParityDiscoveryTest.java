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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-2 — the plate family's activation law (the Phase E boundary
 * arms over the occupancy consult): the reference gathers through
 * {@code entity.getShape().intersects(bounds)} (WorldChunk.getEntities
 * lines 739-760) against the activation box
 * {@code [x+0.125, x+0.875] x [y, y+0.25] x [z+0.875]} — each body's
 * OWN box, STRICT both sides (exactly touching is never intersecting).
 *
 * <p>The pre-fix engine consulted the body's CENTER inside the box with
 * inclusive edges: a body standing on the plate's EDGE — its box
 * overlapping the activation box by a hair while its center sits
 * outside the inset — never fired, vanilla's own edge-stand behavior
 * absent (a player's center at x+1.0 with their 0.6-wide box over the
 * plate's east half; the weighted pair never counting them either).</p>
 */
class PlateActivationBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "pab" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE DISCRIMINATOR: the player's edge-stand fires the
    // plate (the body's box over the plate, the center outside)
    // ------------------------------------------------------------------

    /**
     * The player's 0.6x1.8 box (PlayerEntity line 424) standing with
     * their center at x+1.0 — the box [x+0.7, x+1.3] overlapping the
     * activation box [x+0.125, x+0.875] by 0.175 on the plate's east
     * half, the center 0.125 OUTSIDE the inset — presses the stone
     * plate (15 out, the strong arm re-radiating to the dust). The
     * pre-fix center consult never saw them: RED pre-fix.
     */
    @Test
    void thePlayersEdgeStandPressesThePlate() throws Exception {
        int x = 10, z = 10, y = 40;
        placeStonePlateRig(x, y, z);
        PlayerSession body = joined("EdgeStand");
        server.teleportPlayer(body, new Position(x + 1.0, y, z + 0.5));
        await(() -> RedstoneBlocks.platePowered(typeAt(x, y, z)),
                "the player's edge-stand presses the plate (the body's box "
                        + "over the plate, the center outside the inset)");
        await(() -> wirePower(x + 1, y - 1, z) == 15,
                "the mount re-radiates the full 15 to the dust");
        // The departure: the plate releases after its debounce.
        server.teleportPlayer(body, new Position(x + 1.0, y, z + 6.5));
        await(() -> !RedstoneBlocks.platePowered(typeAt(x, y, z)),
                "the plate released after the edge-stand body left");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE DISCRIMINATOR: the mob's edge-stand (the mob walk's
    // own box law over the type's size)
    // ------------------------------------------------------------------

    /**
     * The mob consult reads the TYPE'S own box ([pos ± width/2] x
     * [pos.y, pos.y + height]): a zombie (0.6x1.8) standing with its
     * center at x+1.1 — the box [x+0.8, x+1.4] overlapping the
     * activation box by 0.075, the center 0.225 outside the inset —
     * presses the stone plate. The pre-fix center consult never saw
     * it: RED pre-fix.
     */
    @Test
    void theMobsEdgeStandPressesThePlate() throws Exception {
        int x = 30, z = 10, y = 40;
        placeStonePlateRig(x, y, z);
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> mob =
                new AtomicReference<>();
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity spawned = server.mobs().spawnAt(
                    net.zaminmc.torch.server.entity.MobType.ZOMBIE,
                    new Position(x + 1.1, y, z + 0.5), 0);
            spawned.setNoAi(true); // the seat's own determinism pin — a
            // wandering mind would walk the box back into the inset
            // (the PE-3 wave's seat-discipline find)
            mob.set(spawned);
        });
        await(() -> mob.get() != null, "the zombie spawned at the edge");
        await(() -> RedstoneBlocks.platePowered(typeAt(x, y, z)),
                "the zombie's edge-stand presses the plate (the type's own "
                        + "box over the plate, the center outside)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the strictness straddle: a hair past the box's face
    // never fires; a hair inside fires
    // ------------------------------------------------------------------

    /**
     * Box.intersects lines 164-171 refuses the exact touch on every
     * face: a player whose box's west edge sits a HAIR (1e-9) past the
     * activation box's east edge (the center at (x+0.875) + 0.3 + 1e-9)
     * never presses the plate; the same player a hair INSIDE (the
     * center at (x+0.875) + 0.3 - 1e-9) presses it. The boundary is
     * pinned to a nanometer on each side.
     */
    @Test
    void theExactTouchAtTheActivationEdgeNeverFires() throws Exception {
        int x = 50, z = 10, y = 40;
        placeStonePlateRig(x, y, z);
        PlayerSession body = joined("Straddler");
        // The negative arm: a hair past the east edge.
        server.teleportPlayer(body, new Position(x + 0.875 + 0.3 + 1e-9, y, z + 0.5));
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            assertTrue(!RedstoneBlocks.platePowered(typeAt(x, y, z)),
                    "the box edge a hair past the activation edge never "
                            + "presses the plate (the strict box law)");
            Thread.sleep(50);
        }
        // The positive arm: the same body a hair inside.
        server.teleportPlayer(body, new Position(x + 0.875 + 0.3 - 1e-9, y, z + 0.5));
        await(() -> RedstoneBlocks.platePowered(typeAt(x, y, z)),
                "the same body a hair inside the edge presses the plate");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE DISCRIMINATOR: the weighted pair counts the
    // edge-standers (the count probe's own box law)
    // ------------------------------------------------------------------

    /**
     * WeightedPressurePlateBlock.calculateOutputSignal lines 25-36:
     * the count walk gathers through the SAME box law — two players
     * edge-standing on the light plate (their centers at x+1.0 and
     * z+1.0, both boxes overlapping the activation box, both centers
     * outside the inset) count TWO, the light plate reading
     * ceil(2/15 * 15) = 2. The pre-fix center consult counted ZERO:
     * RED pre-fix.
     */
    @Test
    void theWeightedPlateCountsTheEdgeStanders() throws Exception {
        int x = 70, z = 10, y = 40;
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x, y, z),
                    WeightedPlateBlocks.state(0, 0));
        });
        await(() -> WeightedPlateBlocks.isWeightedPlate(
                        typeAt(x, y, z).identifier()),
                "the light weighted plate placed");
        PlayerSession east = joined("EastStand");
        PlayerSession south = joined("SouthStand");
        server.teleportPlayer(east, new Position(x + 1.0, y, z + 0.5));
        server.teleportPlayer(south, new Position(x + 0.5, y, z + 1.0));
        await(() -> WeightedPlateBlocks.powerOf(typeAt(x, y, z).identifier()) == 2,
                "the two edge-standers count TWO on the light plate (the "
                        + "count walk's own box law)");
        // The departure: the count falls back to zero.
        server.teleportPlayer(east, new Position(x + 1.0, y, z + 6.5));
        server.teleportPlayer(south, new Position(x + 1.0, y, z + 7.5));
        await(() -> WeightedPlateBlocks.powerOf(typeAt(x, y, z).identifier()) == 0,
                "the count released after both edge-standers left");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** The stone plate on its stone mount + the sky-blocking roof (the
     *  undead burn consult reads sky access — a burning zombie's health
     *  walk must never confound the seats). */
    private void placeStonePlateRig(int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x, y, z),
                    RedstoneBlocks.plateOf(false, false));
            server.world().setBlock(new BlockPosition(x + 1, y - 2, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x + 1, y - 1, z),
                    RedstoneBlocks.wireOfPower(0));
            // The roof covers BOTH the plate's cell and the edge-stand
            // band east of it (the zombie's own cell).
            server.world().setBlock(new BlockPosition(x, y + 7, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x + 1, y + 7, z), BuiltinBlocks.STONE);
        });
        await(() -> RedstoneBlocks.isPlate(typeAt(x, y, z)), "the stone plate placed");
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

    private int wirePower(int x, int y, int z) {
        BlockType type = typeAt(x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
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

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
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

    @SuppressWarnings("unused")
    private static void unused(BooleanSupplier supplier) {
        assertEquals(true, supplier.getAsBoolean());
    }
}
