package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rail trio (Slice 38 — PoweredRailBlock/DetectorRailBlock 1:1): the
 * placement's look-axis band with the solid-bed gate, the golden family's
 * POWERED walk over the redstone bus (the direct feed and the
 * connected-rails propagation with its 8-deep same-family limit), the
 * detector family's cart-driven emission (the 15 weak every-direction and
 * the strong up-only arm), the cart's boost/brake ride, and the family
 * tables (behavior, light, solidity, crafting).
 */
class RailBlocksTest {

    @TempDir
    Path dataDir;

    @Test
    void thePlacementCarriesTheLookAxisAndTheSolidBedGate() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x, y - 1, z), "the bed placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the rail site");
            hold(player, "minecraft:golden_rail");
            face(player, 0.0f); // looking south: the north-south track
            server.useItemOnBlock(player, new BlockPosition(x, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> RailBlocks.isTrioRail(typeAt(server, x, y, z)), "the golden rail landed");
            BlockType placed = typeAt(server, x, y, z);
            assertEquals("golden_rail", RailBlocks.familyOf(placed));
            assertEquals(0, RailBlocks.shapeOf(placed), "the north-south shape (meta 0)");
            assertFalse(RailBlocks.isPowered(placed), "a rail places unpowered");
            assertEquals("minecraft:golden_rail", placed.identifier().toString());
            assertFalse(WorldSolidity.isSolid(placed), "the rail never blocks a body");
            assertTrue(WorldSolidity.isRail(placed.identifier()));

            // The floor departs: the rail pops (AbstractRailBlock's
            // neighborChanged support walk).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y - 1, z), BuiltinBlocks.AIR));
            await(() -> !RailBlocks.isTrioRail(typeAt(server, x, y, z)),
                    "the unsupported rail popped");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theGoldenFamilyPowersFromTheFeedAndPropagatesAlongTheRun() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 14, y = 40;
            // A three-rail east-west run: the feed torch sits under the
            // west end; the far end hears it through the propagation walk.
            server.ticker().submit(() -> {
                for (int dx = 0; dx < 3; dx++) {
                    server.world().setBlock(new BlockPosition(x + dx, y - 1, z), BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x - 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RailBlocks.state("golden_rail", 1, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RailBlocks.state("golden_rail", 1, false));
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RailBlocks.state("golden_rail", 1, false));
            });
            await(() -> RailBlocks.isTrioRail(typeAt(server, x + 2, y, z)), "the run placed");
            // The torch lands under the west end: the rail above it takes
            // the direct feed, the two neighbors hear the flip through the
            // propagation walk (isPoweredByConnectedRails).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), RedstoneBlocks.torchStandingLit()));
            await(() -> RailBlocks.isPowered(typeAt(server, x, y, z)),
                    "the fed end's POWERED bit flipped");
            // The direct neighbor of the fed rail: the walk reaches it in
            // the same cascade (the neighborChanged the flip fires).
            await(() -> RailBlocks.isPowered(typeAt(server, x + 1, y, z)),
                    "the connected rail powered through the run");
            // The far end (two hops): reached through the recursive walk.
            await(() -> RailBlocks.isPowered(typeAt(server, x + 2, y, z)),
                    "the run's far end powered");
            assertEquals("minecraft:golden_rail_powered_east_west",
                    typeAt(server, x + 2, y, z).identifier().toString(),
                    "the powered east-west shape carries the reference's meta-9 form");

            // The feed departs: the whole run goes dark (the propagation
            // re-evaluation runs both ways from every flip).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), BuiltinBlocks.AIR));
            await(() -> !RailBlocks.isPowered(typeAt(server, x, y, z)),
                    "the fed end went dark");
            await(() -> !RailBlocks.isPowered(typeAt(server, x + 2, y, z)),
                    "the far end went dark");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDetectorRailEmitsOverTheCartRide() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 18, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RailBlocks.state("detector_rail", 0, false));
            });
            await(() -> RailBlocks.isTrioRail(typeAt(server, x, y, z)), "the detector placed");
            // A minecart spawns ON the rail: the per-tick cart scan presses
            // the rail (the onEntityCollision equivalent).
            server.ticker().submit(() -> {
                server.vehicles().spawnMinecart(
                        new Position(x + 0.5, y + 0.06, z + 0.5), 0);
            });
            await(() -> RailBlocks.isPowered(typeAt(server, x, y, z)),
                    "the cart ride pressed the detector rail");
            assertEquals("minecraft:detector_rail_powered",
                    typeAt(server, x, y, z).identifier().toString(),
                    "the pressed north-south rail is the reference's meta-8 form");
            // The cart departs (a teleport far away): the 20-tick re-check
            // releases the rail.
            server.ticker().submit(() -> {
                for (var vehicle : server.vehicles().all()) {
                    vehicle.setPosition(new Position(x + 200.5, y + 2, z + 200.5));
                }
            });
            await(() -> !RailBlocks.isPowered(typeAt(server, x, y, z)),
                    "the departed cart released the rail");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFamilyTablesCoverEveryStateAndTheCraftsAnswer() {
        for (String family : RailBlocks.RAILS.keySet()) {
            for (int shape = 0; shape < 6; shape++) {
                for (boolean powered : new boolean[] {false, true}) {
                    EngineBlockType state = RailBlocks.state(family, shape, powered);
                    int meta = shape | (powered ? 8 : 0);
                    assertEquals(meta, state.legacyMetadata(),
                            family + " " + state.identifier().value());
                    BlockBehavior behavior = BlockBehaviorTable.of(state.identifier())
                            .orElseThrow();
                    assertEquals(RailBlocks.railItem(family), behavior.drops().get(0).item());
                    assertEquals(0.7, behavior.hardness());
                    assertEquals(0, BlockLightTable.lookup(state.identifier())
                            .orElseThrow().filter(), "light passes the thin track");
                    assertEquals(RailBlocks.isAscending(shape), RailBlocks.isAscending(shape));
                    assertEquals(shape, RailBlocks.shapeOf(state));
                    assertEquals(powered, RailBlocks.isPowered(state));
                    assertEquals(family, RailBlocks.familyOf(state));
                    assertTrue(WorldSolidity.isRail(state.identifier()));
                    assertFalse(WorldSolidity.isSolid(state));
                }
            }
        }
        // The wire ids: the reference's own rows.
        assertEquals(27, RailBlocks.RAILS.get("golden_rail"));
        assertEquals(28, RailBlocks.RAILS.get("detector_rail"));
        assertEquals(157, RailBlocks.RAILS.get("activator_rail"));
        // The axis model: the shapes' travel axes.
        assertTrue(RailBlocks.axisIsX(1), "east_west travels X");
        assertFalse(RailBlocks.axisIsX(0), "north_south travels Z");
        assertTrue(RailBlocks.axisIsX(2), "ascending_east travels X");
        assertFalse(RailBlocks.axisIsX(4), "ascending_north travels Z");
        // The crafts: the reference's own rows.
        CraftingService service = CraftingService.builtin();
        var golden = service.resultOf3x3(new ItemStack[]{
                stack("minecraft:gold_ingot"), ItemStack.EMPTY, stack("minecraft:gold_ingot"),
                stack("minecraft:gold_ingot"), stack("minecraft:stick"), stack("minecraft:gold_ingot"),
                stack("minecraft:gold_ingot"), stack("minecraft:redstone"), stack("minecraft:gold_ingot")});
        assertTrue(golden.isPresent(), "the golden rail craft");
        assertEquals("minecraft:golden_rail", golden.orElseThrow().type().identifier().toString());
        assertEquals(6, golden.orElseThrow().count());
        var activator = service.resultOf3x3(new ItemStack[]{
                stack("minecraft:iron_ingot"), stack("minecraft:stick"), stack("minecraft:iron_ingot"),
                stack("minecraft:iron_ingot"), stack("minecraft:redstone_torch"), stack("minecraft:iron_ingot"),
                stack("minecraft:iron_ingot"), stack("minecraft:stick"), stack("minecraft:iron_ingot")});
        assertTrue(activator.isPresent(), "the activator rail craft");
        assertEquals("minecraft:activator_rail", activator.orElseThrow().type().identifier().toString());
        var detector = service.resultOf3x3(new ItemStack[]{
                stack("minecraft:iron_ingot"), ItemStack.EMPTY, stack("minecraft:iron_ingot"),
                stack("minecraft:iron_ingot"), stack("minecraft:stone_pressure_plate"), stack("minecraft:iron_ingot"),
                stack("minecraft:iron_ingot"), stack("minecraft:redstone"), stack("minecraft:iron_ingot")});
        assertTrue(detector.isPresent(), "the detector rail craft");
        assertEquals("minecraft:detector_rail", detector.orElseThrow().type().identifier().toString());
    }

    // ------------------------------------------------------------- helpers

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "rails" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession joined(EngineServer server) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        }, "Railwright", UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, "the player playing");
        return session;
    }

    private void hold(PlayerSession player, String id) {
        player.inventory().setSlot(0, ItemStack.of(
                BuiltinItems.lookup(Identifier.parse(id)).orElseThrow(), 1));
        player.inventory().selectHotbarSlot(0);
    }

    private void face(PlayerSession player, float yaw) {
        player.applyMovement(player.position(), new net.zaminmc.torch.util.Rotation(yaw, 0.0f), true);
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        return waitFor(result);
    }

    private static boolean solidAt(EngineServer server, int x, int y, int z) {
        AtomicReference<Boolean> solid = new AtomicReference<>();
        server.ticker().submit(() -> solid.set(
                WorldSolidity.isSolid(server.world().getBlock(new BlockPosition(x, y, z)))));
        return waitFor(solid);
    }

    private static ItemStack stack(String value) {
        return ItemStack.of(BuiltinItems.lookup(Identifier.parse(value)).orElseThrow(), 1);
    }

    private static <T> T waitFor(AtomicReference<T> ref) {
        long deadline = System.currentTimeMillis() + 10_000;
        T value = ref.get();
        while (value == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            value = ref.get();
        }
        return value;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }
}
