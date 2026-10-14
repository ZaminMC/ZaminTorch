package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dye-coloring families over the real wire (Slice 31): the creative
 * slot's damage-domain claim (a lime wool pick resolves the ONE wool item
 * with damage 5 — never the flattened lime_wool block, which has no item),
 * the placement's damage-aware block claim (id 35 damage 5 places the lime
 * member), and the block change's combined id<<4|meta write back (the same
 * 565 the vanilla server would send).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DyeFamilyWireAcceptanceTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    private net.zaminmc.torch.block.BlockPosition floorAt(int x, int y, int z) {
        return new net.zaminmc.torch.block.BlockPosition(x, y + 1, z);
    }

    /** The surface cell beside the player: the topmost non-air block at
     *  (anchor.x + 1, ., anchor.z) — clicking its top face places into the
     *  open air above it, clear of the player's box. */
    private int[] surfaceCellBeside(net.zaminmc.torch.server.player.PlayerSession session) {
        var anchor = session.position();
        int fx = (int) Math.floor(anchor.x()) + 1;
        int fz = (int) Math.floor(anchor.z());
        int fy = Math.min(250, (int) Math.floor(anchor.y()) + 6);
        while (fy > 2 && server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(fx, fy, fz)).equals(
                net.zaminmc.torch.server.block.BuiltinBlocks.AIR)) {
            fy--;
        }
        return new int[]{fx, fy, fz};
    }

    /** The cell the face-1 placement writes (above the surface cell). */
    private net.zaminmc.torch.block.BlockPosition placedCell(int x, int y, int z) {
        return new net.zaminmc.torch.block.BlockPosition(x, y + 1, z);
    }

    @Test
    void theCreativeClaimAndPlacementRoundTripTheColor() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Decorator");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            var session = server.playerRegistry().byName("Decorator").orElseThrow();
            server.ticker().submit(() -> session.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            Thread.sleep(200);

            // The creative pick: lime wool — item id 35 with damage 5 (the
            // DyeColor id domain). The slot claim must resolve the FAMILY
            // ITEM (minecraft:wool), never the flattened lime_wool block
            // (which carries no item of its own).
            client.sendCreativeSlot(36, 35, 5);
            Thread.sleep(200);
            var held = session.inventory().held();
            assertTrue(!held.isEmpty(), "the claim landed in the hotbar");
            assertEquals("wool", held.type().identifier().value(),
                    "the claim resolves the single family item");
            assertEquals(5, held.damage(), "the lime color rides the damage field");

            // The placement: a floor cell beside spawn, the claim carrying
            // id 35 damage 5 — the world takes the lime member and the
            // block change writes the combined 35<<4|5 = 565.
            int[] cell = surfaceCellBeside(session);
            int fx = cell[0];
            int fy = cell[1];
            int fz = cell[2];
            client.sendBlockPlacement(fx, fy, fz, 1, 35, 5);

            var lime = net.zaminmc.torch.util.Identifier.parse("minecraft:lime_wool");
            awaitCondition(() -> server.world().getBlock(placedCell(fx, fy, fz))
                    .identifier().equals(lime), "the lime member placed");
            int[] change = client.readBlockChange(10_000);
            assertEquals(35, change[3], "the block change names the wool family id");
            assertEquals(net.zaminmc.torch.server.block.DyeFamilyBlocks.colorOf(
                    server.world().getBlock(placedCell(fx, fy, fz)).identifier()), 5,
                    "the world holds the lime member");
        }
        awaitEmptyServer();
    }

    @Test
    void theStainedGlassClaimPlacesItsMember() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Glazier");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            var session = server.playerRegistry().byName("Glazier").orElseThrow();
            server.ticker().submit(() -> session.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            Thread.sleep(200);

            // The blue stained glass — item id 95 damage 11 (DyeColor.BLUE).
            client.sendCreativeSlot(36, 95, 11);
            Thread.sleep(200);
            var held = session.inventory().held();
            assertTrue(!held.isEmpty());
            assertEquals("stained_glass", held.type().identifier().value(),
                    "the family item, not the flattened member");
            assertEquals(11, held.damage());

            int[] cell = surfaceCellBeside(session);
            int fx = cell[0];
            int fy = cell[1];
            int fz = cell[2];
            client.sendBlockPlacement(fx, fy, fz, 1, 95, 11);

            var blue = net.zaminmc.torch.util.Identifier.parse("minecraft:stained_glass_blue");
            awaitCondition(() -> server.world().getBlock(placedCell(fx, fy, fz))
                    .identifier().equals(blue), "the blue member placed");
            int[] change = client.readBlockChange(10_000);
            assertEquals(95, change[3], "the block change names the stained glass id");
            assertEquals(net.zaminmc.torch.server.block.DyeFamilyBlocks.colorOf(
                    server.world().getBlock(placedCell(fx, fy, fz)).identifier()), 11,
                    "the world holds the blue member");
        }
        awaitEmptyServer();
    }
}
