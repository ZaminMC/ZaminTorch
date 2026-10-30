package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE TNT FAMILY'S DIMENSION ROW (Wave FL4-F7 — the FL4-F3 wave's
 * newly-ledgered TNT tail): the reference's every TNT arm receives the
 * acting world — {@code ignite(World world, ...)} (TntBlock lines 66-74),
 * {@code onExploded(World world, ...)} (lines 58-65), {@code use(World
 * world, ...)} (lines 96-113), {@code onEntityCollision(World world, ...)}
 * — and the primed body is CONSTRUCTED IN that world ({@code new
 * PrimedTntEntity(world, ...)}), so its physics, its detonation and its
 * tracker all range the body's OWN dimension. The pre-fix engine held the
 * OVERWORLD in TntSystem's constructor: a nether flint-and-steel use read
 * the OVERWORLD's cell at the foreign coordinates (never opening), and had
 * it opened, the prime's departure write and the blast would have torn the
 * OVERWORLD's same-coordinate cells.
 *
 * <p>THE SEAT drives the REAL use wire ({@code useItemOnBlock} → the flint
 * dispatch → the TNT ignite arm) with a nether-dimension player holding a
 * flint and steel over a nether TNT, while the OVERWORLD holds a glass
 * marker at the SAME coordinates: the nether TNT primes (its cell opens in
 * the nether, the body carrying the NETHER's dimension stamp), the fuse's
 * 4.0F detonation tears the NETHER's platform, a second TNT consumed by
 * the blast primes the chain reaction IN THE NETHER, and the overworld's
 * glass marker SURVIVES the whole sequence.</p>
 */
class NetherTntRoutingAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tntroute" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    /**
     * THE CORE FL4-F7 CASE: the nether flint use primes the NETHER's TNT
     * (never the overworld's same-coordinate cells), the primed body
     * carries the NETHER's dimension stamp, and the fuse's detonation —
     * plus the chain reaction it triggers — tears the NETHER's platform
     * while the overworld's glass marker at the SAME coordinates stands.
     */
    @Test
    void theNetherFlintPrimesTheNetherTntAndTheBlastStaysInTheNether() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        PlayerSession igniter = join("NetherIgniter");
        igniter.setDimension(-1);

        // The nether platform at y=100; TNT A on it with the chain-reaction
        // TNT B two cells east; the overworld's glass marker at A's SAME
        // coordinates (the discriminator — a misrouted prime or blast
        // would tear it).
        int fy = 100;
        BlockPosition tntA = new BlockPosition(64, fy + 1, 64);
        BlockPosition tntB = new BlockPosition(66, fy + 1, 64);
        BlockPosition floorUnderA = new BlockPosition(64, fy, 64);
        BlockPosition floorNearB = new BlockPosition(66, fy, 64);
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 4; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(tntA, BuiltinBlocks.TNT);
            nether.setBlock(tntB, BuiltinBlocks.TNT);
            server.world().setBlock(tntA, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.TNT.equals(nether.getBlock(tntA))
                        && BuiltinBlocks.TNT.equals(nether.getBlock(tntB))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(tntA)),
                40, "the nether TNT pair and the overworld marker landed");

        // The igniter stands on the platform, one cell south of TNT A,
        // holding the flint and steel.
        igniter.applyMovement(new net.zaminmc.torch.util.Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the igniter settles");
        igniter.inventory().setSlot(0, new ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.FLINT_AND_STEEL, 1));
        igniter.inventory().selectHotbarSlot(0);

        // The REAL use wire: the flint clicked on TNT A (any face — the
        // ignite arm precedes the face consult in the reference's use).
        server.useItemOnBlock(igniter, tntA, 1, Optional.empty(), windowId -> { });

        // Phase 2 — the prime landed IN THE NETHER: A's cell opened there
        // while the overworld's marker at the SAME coordinates SURVIVES.
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(tntA).equals(nether.airType()),
                40, "the nether TNT primed out of its nether cell");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(tntA)),
                "the overworld's marker at the same coordinates survives the prime");

        // The primed body carries the NETHER's dimension stamp (the
        // pre-fix body always carried the overworld's 0 — the
        // spawn-into-the-wrong-dimension's own row).
        EngineAwaits.awaitCondition(server.world(),
                () -> server.tnt().primedBodies().stream()
                        .anyMatch(body -> body.dimension() == -1
                                && (int) Math.floor(body.x()) == 64
                                && (int) Math.floor(body.z()) == 64),
                20, "the primed body carries the nether's dimension stamp");
        assertFalse(server.tnt().primedBodies().stream()
                        .anyMatch(body -> body.dimension() == 0),
                "no body was ever primed into the overworld");

        // Phase 3 — the fuse's 4.0F detonation tears the NETHER's platform
        // (the floor under A opens), the chain reaction consumes TNT B
        // (primeFromBlast — the blast-routed short-fuse body), and BOTH
        // blasts stay in the nether: the overworld's marker STILL stands.
        // The budget covers the 81-tick fuse PLUS the chain body's own
        // 10-29-tick staggered fuse.
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(floorUnderA).equals(nether.airType())
                        && nether.getBlock(floorNearB).equals(nether.airType())
                        && nether.getBlock(tntB).equals(nether.airType()),
                200, "the nether blast and the chain reaction tore the nether platform");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(tntA)),
                "the overworld's marker at the same coordinates survives the blasts");
    }
}
