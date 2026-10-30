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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE DIG/PLACE GENERIC SURFACE'S WORLD ROUTING (Wave FL4-F5 — the first
 * of the FL4-F3 wave's newly-ledgered tails): the reference's every
 * interaction arm receives the world the player is in ({@code
 * player.world} — ServerPlayerInteractionManager's own field, the dig
 * wire's parameter), so a nether player's dig consults, breaks, drops
 * and re-syncs THEIR OWN dimension's cells. The pre-fix
 * BlockInteractionService held the OVERWORLD in its constructor and read
 * it for every player — a nether dig never even OPENED (the start gate
 * read the overworld's air at the foreign coordinates), and had it
 * opened, the break would have committed against the overworld's cell
 * with the drops stranded there.
 *
 * <p>THE SEAT drives the REAL dig wire (submitMiningStart → the dig
 * clock → submitMiningFinished) with a nether-dimension player holding
 * a diamond pickaxe over a nether stone, while the OVERWORLD holds a
 * glass marker at the SAME coordinates: the nether stone breaks, the
 * overworld's glass survives, and the cobblestone drop carries the
 * NETHER's dimension stamp (the pre-fix three-arg drop form's honest 0
 * stranded every foreign dig's drops in the overworld).</p>
 */
class NetherDigRoutingAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "digroute" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * THE CORE FL4-F5 CASE: the nether player's dig breaks the NETHER's
     * cell (the start gate, the dig clock and the finish walk all reading
     * the nether's own cells — the pre-fix service read the overworld's
     * AIR at those coordinates and the dig never opened), the overworld's
     * glass marker at the SAME coordinates survives, and the cobblestone
     * drop lands with the NETHER's dimension stamp.
     */
    @Test
    void theNetherDigBreaksTheNetherCellNeverTheOverworlds() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        PlayerSession digger = join("NetherDigger");
        digger.setDimension(-1);

        // The nether platform at y=100 with the dig's stone on top; the
        // overworld's glass marker at the SAME coordinates.
        int fy = 100;
        BlockPosition target = new BlockPosition(64, fy + 1, 64);
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
            nether.setBlock(target, BuiltinBlocks.STONE);
            server.world().setBlock(target, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(target))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(target)),
                40, "the nether stone and the overworld marker landed");

        // The digger stands on the platform, one cell south of the target,
        // holding the diamond pickaxe (stone: 6 ticks nominal).
        digger.applyMovement(new net.zaminmc.torch.util.Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the digger settles");
        digger.inventory().setSlot(0, new ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_PICKAXE, 1));
        digger.inventory().selectHotbarSlot(0);

        // The real dig wire: START, the dig clock, FINISH.
        server.blockInteraction().submitMiningStart(digger, target);
        EngineAwaits.awaitTicks(server.world(), 12, "the dig accumulates past the accept band");
        server.blockInteraction().submitMiningFinished(digger, target);

        // Phase 2 — the break landed IN THE NETHER.
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(target).equals(nether.airType()),
                40, "the nether stone broke on the nether dig wire");

        // The overworld's glass marker SURVIVES the nether dig (the
        // pre-fix service would have broken THE OVERWORLD's cell — the
        // dig opened against whichever cell the overworld held there).
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(target)),
                "the overworld's marker at the same coordinates survives");
        assertFalse(nether.getBlock(target).equals(BuiltinBlocks.STONE),
                "the nether cell is empty");

        // The cobblestone drop carries the NETHER's dimension stamp (the
        // pre-fix publishDrops' three-arg form stamped the honest 0).
        EngineAwaits.awaitCondition(server.world(),
                () -> server.itemEntities().all().stream()
                        .anyMatch(item -> item.dimension() == -1
                                && item.stack().type().identifier().value().equals("cobblestone")),
                40, "the cobblestone drop landed in the nether");
        boolean overworldDrop = server.itemEntities().all().stream()
                .anyMatch(item -> item.dimension() == 0
                        && (int) Math.floor(item.position().x()) == 64
                        && (int) Math.floor(item.position().z()) == 64);
        assertFalse(overworldDrop, "no dig drop stranded in the overworld");
    }
}
