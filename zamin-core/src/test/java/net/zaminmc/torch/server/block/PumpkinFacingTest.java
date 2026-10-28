package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pumpkin pair's FACING states (the metadata slice — PumpkinBlock
 * 1:1): the placement's look-opposite law over the HORIZONTAL-id nibble
 * band 0-3 (a DIFFERENT domain than the chest family's Direction band —
 * 0 south, 1 west, 2 north, 3 east, Direction.java lines 14-17's own
 * idHorizontal assignments), the look band's own vanilla formula (the
 * +45 centering and the &amp;3 mask — the half-quadrant law the old
 * truncation paid wrong, yaw 45 a WEST look in the reference), the
 * default-state laws (the stem's fruit and the dispense arm both place
 * FACING NORTH — the ctor's own setDefaultState, horizontal id 2 — not
 * the bare south form), and the golem-head consult over every state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PumpkinFacingTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession player;
    private int counter;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1  ".trim(), 0,
                "pumpkin" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (server.state() == net.zaminmc.torch.ServerState.RUNNING) {
                return server;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("the server never reached RUNNING");
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
                "the joined player reached PLAYING");
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private void look(float yaw) {
        server.ticker().submit(() -> player.applyMovement(player.position(),
                new Rotation(yaw, 0.0f), true));
        try {
            await(() -> Math.abs(player.rotation().yaw() - yaw) < 0.01f, "the look landed");
        } catch (InterruptedException e) {
            throw new AssertionError("interrupted awaiting the look", e);
        }
    }

    private String placeAt(ItemType held) throws InterruptedException {
        // Anchored BESIDE the player (the use-reach gate): a fresh cell per
        // placement, one block out on a small ring.
        var at = player.position();
        int x = (int) Math.floor(at.x()) + (counter % 3) - 1;
        int z = (int) Math.floor(at.z()) + ((counter / 3) % 3) - 1;
        counter++;
        int y = (int) Math.floor(at.y());
        BlockPosition floor = new BlockPosition(x, y - 1, z);
        BlockPosition atop = floor.offset(0, 1, 0);
        server.ticker().submit(() -> server.world().setBlock(floor, BuiltinBlocks.STONE));
        await(() -> server.world().getBlock(floor).equals(BuiltinBlocks.STONE), "the floor");
        player.inventory().setSlot(0, ItemStack.of(held, 1));
        player.inventory().selectHotbarSlot(0);
        server.useItemOnBlock(player, floor, 1, java.util.Optional.empty(), windowId -> { });
        await(() -> !server.world().getBlock(atop).equals(server.world().airType()),
                "the pumpkin landed");
        return server.world().getBlock(atop).identifier().toString();
    }

    // ------------------------------------------------------------------
    // The placement's look-opposite law (getPlacementState line 129)
    // ------------------------------------------------------------------

    @Test
    void thePlacementWritesTheLookOppositeState() throws Exception {
        boot();
        try {
            player = join("Placer");
            // The four cardinals: the pumpkin's face looks back at the
            // placer (the look's OPPOSITE over the horizontal band).
            look(0.0f);   // the south look
            assertEquals("minecraft:pumpkin_north", placeAt(net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN),
                    "the south look's pumpkin faces north");
            look(90.0f);  // the west look
            assertEquals("minecraft:pumpkin_east", placeAt(net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN),
                    "the west look's pumpkin faces east");
            look(180.0f); // the north look
            assertEquals("minecraft:pumpkin", placeAt(net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN),
                    "the north look's pumpkin faces south — the bare form IS the south state");
            look(270.0f); // the east look
            assertEquals("minecraft:pumpkin_west", placeAt(net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN),
                    "the east look's pumpkin faces west");
            // The half-quadrant law (the reference's own band edges): yaw 45
            // is a WEST look under floor(yaw/90 + 0.5) & 3 — the old
            // truncation's (int)(yaw/90) % 4 paid it a SOUTH one.
            look(45.0f);
            assertEquals("minecraft:pumpkin_east", placeAt(net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN),
                    "yaw 45 is a west look (the +45 centering), the pumpkin faces east");
            // The negative-yaw mask (& 3): the client's -90 is the east look.
            look(-90.0f);
            assertEquals("minecraft:pumpkin_west", placeAt(net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN),
                    "the negative east look masks to the west-facing state");
            // The lit form rides the same law.
            look(0.0f);
            assertEquals("minecraft:lit_pumpkin_north", placeAt(
                    net.zaminmc.torch.server.item.BuiltinItems.LIT_PUMPKIN),
                    "the lit lantern's face looks back too");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------
    // The pure consults (the family model)
    // ------------------------------------------------------------------

    @Test
    void theLookBandFollowsTheReferenceFormula() {
        // Direction.byRotation: byIdHorizontal(floor(yaw / 90 + 0.5) & 3).
        assertEquals(0, PumpkinBlocks.lookBandOf(0.0f), "yaw 0: the south band's CENTER");
        assertEquals(0, PumpkinBlocks.lookBandOf(44.9f), "still south at 44.9");
        assertEquals(1, PumpkinBlocks.lookBandOf(45.0f), "the +45 centering: 45 is west");
        assertEquals(1, PumpkinBlocks.lookBandOf(134.9f), "still west at 134.9");
        assertEquals(2, PumpkinBlocks.lookBandOf(135.0f), "135 is north");
        assertEquals(3, PumpkinBlocks.lookBandOf(225.0f), "225 is east");
        assertEquals(0, PumpkinBlocks.lookBandOf(315.0f), "315 is south again");
        // The negative-yaw mask (two's complement & 3): the client's
        // [-180, 180) range rides the same bands.
        assertEquals(3, PumpkinBlocks.lookBandOf(-90.0f), "-90 masks to east");
        assertEquals(3, PumpkinBlocks.lookBandOf(-45.1f), "-45.1 is east");
        assertEquals(0, PumpkinBlocks.lookBandOf(-44.9f), "-44.9 is south");
        assertEquals(2, PumpkinBlocks.lookBandOf(-180.0f), "-180 is north");
        assertEquals(2, PumpkinBlocks.lookBandOf(-135.1f), "-135.1 is north");
        // The opposite (Direction.getOpposite over the band): south<->north,
        // west<->east — the band's own ^2.
        assertEquals(2, PumpkinBlocks.oppositeHorizontal(0));
        assertEquals(0, PumpkinBlocks.oppositeHorizontal(2));
        assertEquals(3, PumpkinBlocks.oppositeHorizontal(1));
        assertEquals(1, PumpkinBlocks.oppositeHorizontal(3));
    }

    @Test
    void theFamilyConsultsAnswerTheHorizontalBand() {
        // pumpkinOf: the bare form IS the south state (metadata 0); the
        // rotated states carry 1/2/3.
        assertEquals("minecraft:pumpkin", PumpkinBlocks.pumpkinOf(false, 0).identifier().toString());
        assertEquals("minecraft:pumpkin_west", PumpkinBlocks.pumpkinOf(false, 1).identifier().toString());
        assertEquals("minecraft:pumpkin_north", PumpkinBlocks.pumpkinOf(false, 2).identifier().toString());
        assertEquals("minecraft:pumpkin_east", PumpkinBlocks.pumpkinOf(false, 3).identifier().toString());
        assertEquals("minecraft:lit_pumpkin", PumpkinBlocks.pumpkinOf(true, 0).identifier().toString());
        assertEquals("minecraft:lit_pumpkin_west", PumpkinBlocks.pumpkinOf(true, 1).identifier().toString());
        assertEquals("minecraft:lit_pumpkin_north", PumpkinBlocks.pumpkinOf(true, 2).identifier().toString());
        assertEquals("minecraft:lit_pumpkin_east", PumpkinBlocks.pumpkinOf(true, 3).identifier().toString());
        // horizontalFacingOf: the round trip over every state.
        for (int id = 0; id < 4; id++) {
            assertEquals(id, PumpkinBlocks.horizontalFacingOf(PumpkinBlocks.pumpkinOf(false, id)),
                    "the plain round trip " + id);
            assertEquals(id, PumpkinBlocks.horizontalFacingOf(PumpkinBlocks.pumpkinOf(true, id)),
                    "the lit round trip " + id);
        }
        // isGolemHead: EVERY state is the golem head (PumpkinBlock's
        // IS_GOLEM_BLOCK predicate covers both blocks whole); the stems and
        // the melon are not.
        for (int id = 0; id < 4; id++) {
            assertTrue(PumpkinBlocks.isGolemHead(PumpkinBlocks.pumpkinOf(false, id)),
                    "the plain state " + id + " is a golem head");
            assertTrue(PumpkinBlocks.isGolemHead(PumpkinBlocks.pumpkinOf(true, id)),
                    "the lit state " + id + " is a golem head");
        }
        assertFalse(PumpkinBlocks.isGolemHead(MelonBlocks.stemStage("pumpkin_stem", 3)),
                "the stem is not the head");
        assertFalse(PumpkinBlocks.isGolemHead(BuiltinBlocks.MELON_BLOCK), "the melon is not the head");
    }

    // ------------------------------------------------------------------
    // The default-state laws (the stem's fruit — StemBlock line 77)
    // ------------------------------------------------------------------

    @Test
    void theStemsFruitIsTheDefaultNorthStateAndTheKindGateCountsEveryFacing() {
        // A pure-world walk (the MelonBlocksTest shape): the stem at AGE 7
        // on farmland, a dirt cell east — the fruit lands FACING NORTH
        // (this.plant.defaultState(), horizontal id 2), never the bare
        // south form.
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("pumpkin-it", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        BlockPosition stem = new BlockPosition(10, 40, 10);
        world.setBlock(stem, MelonBlocks.stemStage("pumpkin_stem", 7));
        world.setBlock(stem.offset(0, -1, 0), BuiltinBlocks.FARMLAND);
        world.setBlock(stem.offset(1, -1, 0), BuiltinBlocks.DIRT);
        world.setBlock(stem.offset(1, 0, 0), world.airType());
        java.util.Random random = new java.util.Random(99L);
        boolean spawned = false;
        for (int i = 0; i < 200 && !spawned; i++) {
            world.setBlock(stem.offset(1, 0, 0), world.airType());
            spawned = MelonBlocks.trySpawnFruit(world, stem, "pumpkin_stem", random);
        }
        assertTrue(spawned, "the grown stem spawned its fruit");
        assertEquals("minecraft:pumpkin_north",
                world.getBlock(stem.offset(1, 0, 0)).identifier().toString(),
                "the fruit is the DEFAULT state (FACING NORTH, horizontal id 2)");
        // The already-fruiting gate (StemBlock lines 70-73): the KIND
        // consult — a ROTATED pumpkin beside the stem ends the walk too
        // (getBlock() == this.plant, every state counts).
        world.setBlock(stem.offset(1, 0, 0), PumpkinBlocks.pumpkinOf(false, 3));
        assertFalse(MelonBlocks.trySpawnFruit(world, stem, "pumpkin_stem", random),
                "an east-facing pumpkin beside the stem still ends the walk");
    }
}
