package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.sign.SignManager;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PLACEMENT FAMILIES' USE-PATH WORLD ROUTING (Wave FL4-F3 — the FL4
 * wave's second surfaced tail): the reference's every use/place arm
 * receives the world the PLAYER IS IN ({@code player.world} — the use
 * wire's own parameter), never a global one. The engine's arms read the
 * OVERWORLD field whatever the player's dimension — a nether player's
 * sign/chest/door placement consulted and wrote the overworld's cells at
 * the nether's coordinates (the bucket path was the fluid family's own
 * FL4 site and was fixed; this wave routes the rest).
 *
 * <p>THE SEAT: a player standing in the NETHER places a SIGN through the
 * REAL USE WIRE ({@code useItemOnBlock} → the use dispatch → the sign's
 * own placement arm). The sign must land in the NETHER at the clicked
 * cell — and the OVERWORLD at the very same coordinates must stay
 * untouched. The pre-fix engine wrote the overworld's cell (or consulted
 * it for the gates): the nether never saw the block.</p>
 */
class NetherPlacementRoutingAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "routing" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
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
     * THE SIGN LANDS IN THE NETHER: the nether-dimension player clicks
     * the UP face of a nether stone floor with a sign held — the
     * placement arm's consults (the clicked-material gate, the occupancy
     * gate, the cactus gate, the write) all run against the NETHER's
     * cells, and the overworld at the same coordinates never changes.
     */
    @Test
    void theNetherPlayersSignPlacesInTheNether() throws Exception {
        var nether = server.worldFor(-1);
        PlayerSession placer = join("NetherPlacer");
        placer.setDimension(-1);

        // The nether platform: stone floor at y=100, cleared air above —
        // far above the nether's natural terrain, the deterministic
        // chamber shape.
        int fy = 100;
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 3; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(new BlockPosition(64, fy, 64))),
                40, "the nether platform landed");

        // The player stands on the platform, one cell south of the click.
        placer.applyMovement(new net.zaminmc.torch.util.Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the placer settles");
        placer.inventory().setSlot(0, new ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.SIGN, 2, 0));
        placer.inventory().selectHotbarSlot(0);

        // The use: the floor's UP face (face 1) — the standing sign arm.
        server.useItemOnBlock(placer, new BlockPosition(64, fy, 64), 1,
                java.util.Optional.empty(), windowId -> { });

        BlockPosition placed = new BlockPosition(64, fy + 1, 64);
        EngineAwaits.awaitCondition(server.world(),
                () -> SignManager.isSignType(nether.getBlock(placed)),
                40, "the sign landed in the NETHER");
        assertTrue(SignManager.isSignType(nether.getBlock(placed)),
                "the nether cell holds the placed sign");
        assertEquals(nether.airType(), server.world().getBlock(placed),
                "the OVERWORLD at the same coordinates stays untouched");
        assertEquals(1, placer.inventory().held().count(),
                "the placement consumed one sign");
        assertEquals(-1, placer.dimension(), "the placer stands in the nether");
        assertFalse(SignManager.isSignType(server.world().getBlock(placed)),
                "no sign leaked into the overworld");
    }
}
