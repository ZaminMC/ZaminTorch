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
import net.zaminmc.torch.util.Rotation;
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
 * The fence-gate families (Slice 37 — FenceGateBlock.java 1:1): the
 * placement's look-facing band with the solid-floor gate, the use swing's
 * open/close flip with the far-side facing rewrite, the signal arm's
 * open/close edge over the redstone neighbor bus (POWERED latching with
 * the held-open stability), and the family tables (behavior, light,
 * crafting, walk-through).
 */
class FenceGateBlocksTest {

    @TempDir
    Path dataDir;

    @Test
    void thePlacementCarriesTheLookFacingAndTheSolidFloorGate() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                // the flora-floor site (canBePlaced's refusal)
                server.world().setBlock(new BlockPosition(x + 6, y - 1, z), BuiltinBlocks.GRASS_BLOCK);
                server.world().setBlock(new BlockPosition(x + 6, y, z), BuiltinBlocks.TALL_GRASS);
            });
            await(() -> solidAt(server, x, y - 1, z), "the floor placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the gate site");
            hold(player, "minecraft:spruce_fence_gate");
            face(player, 180.0f); // looking north
            server.useItemOnBlock(player, new BlockPosition(x, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> FenceGateBlocks.isGate(typeAt(server, x, y, z)),
                    "the spruce gate landed");
            // Looking north: getHorizontalFacing writes the facing band's
            // own byte (2 = north — the exact metadata the 1.8.8 client
            // decodes for a north-facing gate).
            BlockType placed = typeAt(server, x, y, z);
            assertEquals("spruce", FenceGateBlocks.familyOf(placed));
            assertEquals(2, FenceGateBlocks.facingOf(placed));
            assertFalse(FenceGateBlocks.isOpen(placed), "a gate places closed");
            assertFalse(FenceGateBlocks.isPowered(placed), "a gate places unpowered");
            assertEquals("minecraft:spruce_fence_gate_north", placed.identifier().toString());
            // The state's own legacyMetadata band (the exact byte the 1.8.8
            // client decodes — getMetadataFromState's facing | open 4 |
            // powered 8).
            assertEquals(2, FenceGateBlocks.state("spruce", 2, false, false).legacyMetadata());

            // The far-side open: the player looks south at a north-facing
            // gate — the open arm rewrites the facing to the opener.
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            face(player, 0.0f); // looking south (the gate faces away)
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "the gate swung open");
            assertEquals(0, FenceGateBlocks.facingOf(typeAt(server, x, y, z)),
                    "the far-side open turned the gate to face the opener");
            assertTrue(WorldSolidity.isOpenFenceGate(typeAt(server, x, y, z).identifier()),
                    "the open gate never blocks a body");
            assertFalse(WorldSolidity.isSolid(typeAt(server, x, y, z)),
                    "the open gate is walk-through (getCollisionShape null)");

            // The close keeps the facing.
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> !FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "the gate swung closed");
            assertEquals(0, FenceGateBlocks.facingOf(typeAt(server, x, y, z)),
                    "the close never rewrites the facing");
            assertTrue(WorldSolidity.isSolid(typeAt(server, x, y, z)),
                    "the closed gate stays solid");

            // The flora floor refuses the placement (canBePlaced's solid
            // material gate): clicking the tall grass's cell never places.
            server.teleportPlayer(player, new Position(x + 6.5, y + 1, z + 1.5));
            await(() -> player.position().distanceSquared(
                    new Position(x + 6.5, y + 1, z + 1.5)) < 4.0, "the player stood by the grass");
            hold(player, "minecraft:oak_fence_gate");
            face(player, 0.0f);
            server.useItemOnBlock(player, new BlockPosition(x + 6, y, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> true, "the flora-floor click settles");
            assertFalse(FenceGateBlocks.isGate(typeAt(server, x + 6, y + 1, z)),
                    "the grass floor refused the gate");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSignalArmOpensClosesAndHoldsThroughUnrelatedChanges() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 14, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 1, y - 1, z), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x - 1, y - 1, z), "the floors placed");
            PlayerSession player = joined(server);
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 1.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 1.5)) < 4.0,
                    "the player stood at the gate site");
            hold(player, "minecraft:dark_oak_fence_gate");
            face(player, 0.0f); // looking south: the gate faces south
            server.useItemOnBlock(player, new BlockPosition(x, y - 1, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> FenceGateBlocks.isGate(typeAt(server, x, y, z)), "the gate placed");
            assertFalse(FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "a gate never evaluates its feed on placement");

            // The torch lands NEXT to the gate: its commit fires the gate's
            // neighborChanged — the rising edge opens with POWERED latching.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), RedstoneBlocks.torchStandingLit()));
            await(() -> FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "the torch-fed gate swung open");
            BlockType fed = typeAt(server, x, y, z);
            assertTrue(FenceGateBlocks.isPowered(fed), "the rising edge latched POWERED");
            assertEquals("minecraft:dark_oak_fence_gate_powered_open",
                    fed.identifier().toString(),
                    "the open+powered south gate is the reference's own meta-12 form");
            assertEquals(12, FenceGateBlocks.state("dark_oak", 0, true, true).legacyMetadata());

            // The held-open stability: an unrelated neighbor change never
            // re-toggles the gate (the edge-shaped arms).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y + 3, z), BuiltinBlocks.STONE));
            await(() -> true, "the unrelated neighbor change settles");
            assertTrue(FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "the signal-held gate stays open through unrelated changes");

            // The manual close of a signal-held gate: the powered-open form
            // drops to the powered-closed form (OPEN only — POWERED kept).
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> !FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "the manual close dropped OPEN");
            assertTrue(FenceGateBlocks.isPowered(typeAt(server, x, y, z)),
                    "the manual close kept POWERED (the reference's independent bits)");

            // The feed departs with the gate manually closed: the POWERED
            // re-latch arm (flag != POWERED) drops the bit without opening.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), BuiltinBlocks.AIR));
            await(() -> !FenceGateBlocks.isPowered(typeAt(server, x, y, z)),
                    "the POWERED bit dropped with the signal");
            assertFalse(FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "the closed gate never opened on the falling edge");

            // A manually-opened gate latches POWERED when a feed arrives,
            // and the departing feed then closes it (the arm2 pair
            // !flag && open && powered — the reference never asks WHO
            // opened it: the bits alone decide).
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "the manual open");
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), RedstoneBlocks.torchStandingLit()));
            await(() -> FenceGateBlocks.isPowered(typeAt(server, x, y, z)),
                    "the arriving feed latched POWERED");
            assertTrue(FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "an already-open gate never re-toggles on the feed");
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), BuiltinBlocks.AIR));
            await(() -> !FenceGateBlocks.isPowered(typeAt(server, x, y, z)),
                    "the feed departed");
            assertFalse(FenceGateBlocks.isOpen(typeAt(server, x, y, z)),
                    "the powered-open gate closed on the feed's departure (arm2)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFamilyTablesCoverEveryStateAndTheCraftsAnswer() {
        for (String family : FenceGateBlocks.GATES.keySet()) {
            for (int facing = 0; facing < 4; facing++) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean powered : new boolean[] {false, true}) {
                        EngineBlockType state =
                                FenceGateBlocks.state(family, facing, open, powered);
                        int meta = facing | (open ? 4 : 0) | (powered ? 8 : 0);
                        assertEquals(meta, state.legacyMetadata(),
                                family + " " + state.identifier().value());
                        // The behavior rows: the family item drops, wood class.
                        BlockBehavior behavior = BlockBehaviorTable.of(state.identifier())
                                .orElseThrow();
                        assertEquals(FenceGateBlocks.gateItem(family),
                                behavior.drops().get(0).item());
                        assertEquals(2.0, behavior.hardness());
                        // The light rows: the CUTOUT family passes light whole.
                        assertEquals(15, BlockLightTable.lookup(state.identifier())
                                .orElseThrow().filter());
                        // The solidity: only the open band walks through.
                        assertEquals(open, WorldSolidity.isOpenFenceGate(state.identifier()));
                        assertEquals(!open, WorldSolidity.isSolid(state));
                        // The round trip through the state helpers.
                        assertEquals(family, FenceGateBlocks.familyOf(state));
                        assertEquals(facing, FenceGateBlocks.facingOf(state));
                        assertEquals(open, FenceGateBlocks.isOpen(state));
                        assertEquals(powered, FenceGateBlocks.isPowered(state));
                    }
                }
            }
        }
        // The wire ids: the reference's own rows.
        assertEquals(107, FenceGateBlocks.GATES.get("oak"));
        assertEquals(183, FenceGateBlocks.GATES.get("spruce"));
        assertEquals(184, FenceGateBlocks.GATES.get("birch"));
        assertEquals(185, FenceGateBlocks.GATES.get("jungle"));
        assertEquals(186, FenceGateBlocks.GATES.get("dark_oak"));
        assertEquals(187, FenceGateBlocks.GATES.get("acacia"));
        // The item forms exist.
        for (String wood : FenceGateBlocks.GATES.keySet()) {
            assertTrue(BuiltinItems.lookup(
                            Identifier.parse("minecraft:" + wood + "_fence_gate")).isPresent(),
                    wood + " gate item registered");
        }
        // The crafts: four sticks + two exact-variant planks -> one gate.
        CraftingService service = CraftingService.builtin();
        ItemStack planks = ItemStack.of(BuiltinItems.lookup(
                Identifier.parse("minecraft:planks")).orElseThrow(), 1);
        ItemStack stick = ItemStack.of(BuiltinItems.lookup(
                Identifier.parse("minecraft:stick")).orElseThrow(), 1);
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        for (int variant = 0; variant < woods.length; variant++) {
            ItemStack p = new ItemStack(planks.type(), 1, variant);
            var result = service.resultOf3x3(new ItemStack[]{
                    stick, p, stick,
                    stick, p, stick,
                    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY});
            assertTrue(result.isPresent(), "the " + woods[variant] + " gate craft");
            assertEquals("minecraft:" + woods[variant] + "_fence_gate",
                    result.orElseThrow().type().identifier().toString());
            assertEquals(1, result.orElseThrow().count());
        }
    }

    // ------------------------------------------------------------- helpers

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "gates" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
        }, "Gatewright", UUID.randomUUID());
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
        player.applyMovement(player.position(), new Rotation(yaw, 0.0f), true);
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
