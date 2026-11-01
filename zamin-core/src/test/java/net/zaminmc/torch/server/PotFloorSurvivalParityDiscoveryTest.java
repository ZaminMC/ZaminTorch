package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FlowerPotBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-POT's behavioral seats (pass 2) — the flower pot's FLOOR consult,
 * seated per divergent row class against the reference's law
 * (reference/1.8.8 FlowerPotBlock).
 *
 * <p>THE LAW (one consult, two sites): the pot rides
 * {@code World.hasSolidTop(pos.down())} at BOTH its sites —
 * {@code FlowerPotBlock.canBePlaced} (lines 136-137:
 * {@code super.canBePlaced(world, pos) && World.hasSolidTop(world,
 * pos.down())} — the placement gate) and
 * {@code FlowerPotBlock.neighborChanged} (lines 141-143:
 * {@code if (!World.hasSolidTop(world, pos.down())) → dropItems +
 * removeBlock} — the survival pop walk). The five arms of
 * {@code World.hasSolidTop} (World.java lines 1740-1752) turn on
 * {@code material.isSolidBlocking()} — the translucent materials (LEAVES,
 * GLASS, TNT, ICE, SNOW_LAYER, CACTUS — the glowstone and the sea lantern
 * among the Material.GLASS registrations) NEVER answer; the non-cubes
 * (fences, panes, walls) NEVER answer; the bottom halves NEVER answer.</p>
 *
 * <p>THE ENGINE'S SHAPE: both sites answer the ONE collision-solid
 * question {@code WorldSolidity.isSolid} — the placement gate
 * ({@code setPlacementSupportGate}'s flower_pot row,
 * EngineServer's placement-gate wiring) and the survival walk
 * ({@code FlowerPotBlocks.isSupported}, read by
 * {@code BlockUpdateSystem}'s neighbor-update arm). No material
 * awareness, no half awareness, no cube awareness.</p>
 *
 * <p>THE CONSTRUCTION: the survival seats build the pot on stone (the
 * agreeing bed), then REPLACE the stone with the divergent bed — the same
 * commit that fires the neighbor-update survival walk. Vanilla pops the
 * pot on that very walk; the engine's one-question consult keeps it. The
 * placement seats drive the real item-use path (the flower_pot item held,
 * the up-click) against the divergent and the agreeing bed. The seats are
 * RED BY CONTRACT on unfixed develop (the vanilla answer asserted); the
 * controls are GREEN on develop (the pop path itself and the agreeing
 * rows — the fix's regression pins — pinned).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PotFloorSurvivalParityDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "pot", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    private static BlockType block(EngineServer server, String id) {
        return server.blockRegistry().lookup(Identifier.parse(id)).orElseThrow(
                () -> new IllegalArgumentException("Unknown block type: " + id));
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Awaits the vanilla pop: the pot cell must read air. */
    private void awaitPopped(EngineServer server, BlockPosition cell, String law)
            throws InterruptedException {
        await(() -> {
            BlockType type = read(server, cell);
            return type == null || type.identifier().value().equals("air");
        }, "the vanilla pop never fired: " + law);
    }

    private static BlockType read(EngineServer server, BlockPosition cell) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(server.world().getBlock(cell)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return result.get();
    }

    // ------------------------------------------------------------------
    // The survival walk — FlowerPotBlock.neighborChanged (lines 141-143)
    // ------------------------------------------------------------------

    /**
     * A pot whose bed becomes GLASS pops in vanilla (hasSolidTop's first
     * arm refuses — Material.GLASS is translucent, Material.java line 22)
     * and SURVIVES in the engine (WorldSolidity.isSolid carries no
     * material awareness — glass passes).
     */
    @Test
    void thePotPopsWhenItsBedBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 12, y = 40, z = 12;
            BlockType pot = FlowerPotBlocks.state(0);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), pot);
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.GLASS);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the glass-bedded pot pops (hasSolidTop: GLASS is translucent)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A pot whose bed becomes an OAK FENCE pops in vanilla (the fence is
     * not a cube — hasSolidTop's first arm fails, no fence arm exists in
     * the pot's law) and SURVIVES in the engine (the fence is not in
     * WorldSolidity's exclusion list).
     */
    @Test
    void thePotPopsWhenItsBedBecomesAFence() throws Exception {
        EngineServer server = boot();
        try {
            int x = 12, y = 40, z = 12;
            BlockType fence = block(server, "minecraft:oak_fence");
            BlockType pot = FlowerPotBlocks.state(0);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), pot);
                server.world().setBlock(new BlockPosition(x, y - 1, z), fence);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the fence-bedded pot pops (non-cube; the pot's law has no fence arm)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A pot whose bed becomes a BOTTOM SLAB pops in vanilla (the slab's
     * HALF==TOP arm alone passes — the bottom half fails) and SURVIVES in
     * the engine (no half awareness in isSolid).
     */
    @Test
    void thePotPopsWhenItsBedBecomesABottomSlab() throws Exception {
        EngineServer server = boot();
        try {
            int x = 12, y = 40, z = 12;
            BlockType slab = block(server, "minecraft:stone_slab");
            BlockType pot = FlowerPotBlocks.state(0);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), pot);
                server.world().setBlock(new BlockPosition(x, y - 1, z), slab);
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the bottom-slab-bedded pot pops (the slab's HALF==TOP arm alone passes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the pop path live on develop): a pot whose bed becomes
     * AIR pops on BOTH trees — the survival walk and its drop arm fire
     * whichever consult answers. The fix's liveness pin.
     */
    @Test
    void thePotPopsWhenItsBedBecomesAir() throws Exception {
        EngineServer server = boot();
        try {
            int x = 12, y = 40, z = 12;
            BlockType pot = FlowerPotBlocks.state(0);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), pot);
                server.world().setBlock(new BlockPosition(x, y - 1, z), server.world().airType());
            });
            awaitPopped(server, new BlockPosition(x, y, z),
                    "the air-bedded pot pops (both trees — the pop path live)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the agreeing row, the survival side): a pot on STONE
     * stays on BOTH trees — hasSolidTop's arm 1 and isSolid both answer
     * the full opaque cube. The fix's regression pin (the stone row must
     * never pop).
     */
    @Test
    void thePotSurvivesOnStone() throws Exception {
        EngineServer server = boot();
        try {
            int x = 12, y = 40, z = 12;
            BlockType pot = FlowerPotBlocks.state(0);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), pot);
            });
            await(() -> FlowerPotBlocks.isPot(read(server, new BlockPosition(x, y, z)).identifier()),
                    "the stone-bedded pot lands");
            // A settle window: the pot must SURVIVE it (the stone row is
            // the agreeing bed on both trees).
            Thread.sleep(500);
            assertTrue(FlowerPotBlocks.isPot(read(server, new BlockPosition(x, y, z)).identifier()),
                    "the stone-bedded pot stays (hasSolidTop arm 1 and isSolid agree)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The placement gate — FlowerPotBlock.canBePlaced (lines 136-137)
    // ------------------------------------------------------------------

    /**
     * A flower_pot item clicked onto the TOP face of GLASS refuses in
     * vanilla (canBePlaced line 137: hasSolidTop(glass) false — the
     * translucent refusal) and PLACES in the engine (the placement gate's
     * flower_pot row reads isSolid — glass passes). The vanilla answer
     * asserted: no pot, no item spent.
     */
    @Test
    void thePotRefusesPlacementOnGlass() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession placer = join(server, "PotPlacer");
            await(() -> placer.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the placer reaches PLAYING");
            BlockPosition glass = anchorNear(server, placer);
            BlockPosition above = glass.offset(0, 1, 0);
            server.ticker().submit(() -> {
                server.world().setBlock(above, server.world().airType());
                server.world().setBlock(glass, BuiltinBlocks.GLASS);
            });
            await(() -> server.world().getBlock(glass).equals(BuiltinBlocks.GLASS),
                    "the glass bed seeded");
            ItemType potItem = net.zaminmc.torch.server.item.BuiltinItems.FLOWER_POT;
            placer.inventory().setSlot(0, ItemStack.of(potItem, 1));
            placer.inventory().selectHotbarSlot(0);
            server.useItemOnBlock(placer, glass, 1, java.util.Optional.empty(),
                    windowId -> { });
            // The settle window past, the vanilla verdict: NO pot on the
            // glass, the item UNSPENT (canBePlaced false → the use arm
            // never reaches the placement).
            Thread.sleep(500);
            assertEquals("air", server.world().getBlock(above).identifier().value(),
                    "no pot on the glass bed (FlowerPotBlock.canBePlaced line 137 — hasSolidTop refuses GLASS)");
            assertEquals(1, placer.inventory().held().count(),
                    "the flower_pot item unspent (the refused use never consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the placement path live on develop): the same click onto
     * the TOP face of STONE places the pot on BOTH trees — the gate's
     * agreeing row and the placement path's liveness pin.
     */
    @Test
    void thePotPlacesOnStone() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession placer = join(server, "PotPlacer");
            await(() -> placer.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the placer reaches PLAYING");
            BlockPosition stone = anchorNear(server, placer);
            BlockPosition above = stone.offset(0, 1, 0);
            server.ticker().submit(() -> {
                server.world().setBlock(above, server.world().airType());
                server.world().setBlock(stone, BuiltinBlocks.STONE);
            });
            await(() -> server.world().getBlock(stone).equals(BuiltinBlocks.STONE),
                    "the stone bed seeded");
            ItemType potItem = net.zaminmc.torch.server.item.BuiltinItems.FLOWER_POT;
            placer.inventory().setSlot(0, ItemStack.of(potItem, 1));
            placer.inventory().selectHotbarSlot(0);
            server.useItemOnBlock(placer, stone, 1, java.util.Optional.empty(),
                    windowId -> { });
            await(() -> FlowerPotBlocks.isPot(server.world().getBlock(above).identifier()),
                    "the pot lands on the stone bed (both trees — the agreeing row)");
            assertEquals(0, placer.inventory().held().count(),
                    "the flower_pot item spent (both trees)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /** A free anchor two blocks east of the player, at the feet's level. */
    private BlockPosition anchorNear(EngineServer server, PlayerSession player) {
        return new BlockPosition(
                (int) Math.floor(player.position().x()) + 2,
                (int) Math.floor(player.position().y()) - 1,
                (int) Math.floor(player.position().z()));
    }
}
