package net.zaminmc.torch.server.sign;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockUpdateSystem;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.FallingBlockEntityManager;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.world.anvil.AnvilBlockIds;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE STANDING SIGN'S SIXTEEN ROTATIONS (the twelfth verifier pass — the
 * reference's own laws, every seat anchored):
 *
 * <p>SignItem.useOn line 44's band — {@code floor((yaw + 180) * 16 / 360 +
 * 0.5) & 15} — places one of SIXTEEN rotations (StandingSignBlock.ROTATION,
 * IntegerProperty 0-15, the wire metadata nibble the rotation itself). The
 * pre-audit engine folded the sixteen to the four cardinals and every
 * diagonal look rendered the sign at the wrong angle (yaw 22.5 vanilla 9
 * NNE vs the engine's 8 N; yaw 45 vanilla 10 NE vs 12 E). These seats pin
 * the law at the band's inner edges, the cardinals, the negative-yaw
 * arithmetic and the 360 wrap, through the real use dispatch.</p>
 *
 * <p>The gates (SignItem.useOn lines 21-27 + SignBlock.canBePlaced lines
 * 82-84): the bottom-face refusal, the clicked-block MATERIAL-solid consult
 * (the sign's own WOOD lets a sign stack on a sign; a torch's DECORATION
 * refuses), and the four-horizontal cactus-neighbour refusal.</p>
 *
 * <p>The support arms (StandingSignBlock.neighborChanged lines 22-29 +
 * WallSignBlock.neighborChanged lines 43-51): the standing sign pops over a
 * non-material-solid floor but STANDS on leaves (the LEAVES material is
 * solid), and the wall sign pops when its wall goes.</p>
 *
 * <p>The surface: all sixteen states register their nibbles, and the anvil
 * import resolves every vanilla rotation (63, 0-15) — the pre-audit import
 * isolated the twelve intermediates as AIR.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignRotationParityTest {

    private EngineServer server;

    @TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "signrot", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
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

    /** Joins a fresh survival player holding four signs, aimed at the yaw. */
    private PlayerSession signer(String name, float yaw) {
        PlayerSession player = join(name);
        ItemType signItem = net.zaminmc.torch.server.item.BuiltinItems.SIGN;
        player.inventory().setSlot(0, ItemStack.of(signItem, 4));
        player.inventory().selectHotbarSlot(0);
        player.applyMovement(player.position(), new Rotation(yaw, 0.0f), true);
        return player;
    }

    /** Runs one right-click use on the tick thread (the empty claim form). */
    private void useOn(PlayerSession player, BlockPosition at, int face) {
        server.useItemOnBlock(player, at, face, Optional.empty(), windowId -> { });
    }

    /** A stone floor two cells east of the named player's spawn column. */
    private BlockPosition ground(String name) {
        PlayerSession anchor = server.playerRegistry().byName(name).orElseThrow();
        BlockPosition floor = new BlockPosition(
                (int) Math.floor(anchor.position().x()) + 2,
                (int) Math.floor(anchor.position().y()) - 1,
                (int) Math.floor(anchor.position().z()));
        server.ticker().submit(() -> server.world().setBlock(floor, BuiltinBlocks.STONE));
        return floor;
    }

    // ---------------------------------------------------------- the rotation law

    @Test
    void theSixteenStepBandPlacesEveryRotation() throws Exception {
        server = boot();
        // (yaw, expected state name) — SignItem line 44's own arithmetic:
        // rotation = floor((yaw + 180) * 16 / 360 + 0.5) & 15. The band's
        // inner edges (11.24/11.25), the cardinals, the half steps, the
        // negative arithmetic and the 360 wrap all seated.
        Object[][] seats = {
                {0.0f, "sign_north"},      // 8: a south look faces the placer north
                {11.24f, "sign_north"},    // 8: still inside the band (edge at 11.25)
                {11.26f, "sign_nne"},      // 9: the boundary crossed
                {22.5f, "sign_nne"},       // 9: the band's center
                {45.0f, "sign_ne"},        // 10
                {67.5f, "sign_ene"},       // 11
                {90.0f, "sign_east"},      // 12: a west look faces east
                {135.0f, "sign_se"},       // 14
                {180.0f, "sign"},          // 0: the wrap (16 & 15 = 0), a north look faces south
                {225.0f, "sign_sw"},       // 2
                {270.0f, "sign_west"},     // 4: an east look faces west
                {315.0f, "sign_nw"},       // 6
                {-22.5f, "sign_nnw"},      // 7: the negative arithmetic (157.5 -> 7.5 -> 7)
                {-45.0f, "sign_nw"},       // 6
                {-90.0f, "sign_west"},     // 4
                {-135.0f, "sign_sw"},      // 2
                {-180.0f, "sign"},         // 0
        };
        for (int i = 0; i < seats.length; i++) {
            float yaw = (Float) seats[i][0];
            String expected = (String) seats[i][1];
            PlayerSession placer = join("Rot" + i);
            // Each seat its own column (the spawn is shared — the previous
            // seat's sign would occupy the shared target cell).
            double baseX = Math.floor(placer.position().x()) + i * 6;
            double y = Math.floor(placer.position().y());
            double z = Math.floor(placer.position().z());
            placer.applyMovement(new net.zaminmc.torch.util.Position(baseX, y, z),
                    new Rotation(yaw, 0.0f), true);
            BlockPosition floor = new BlockPosition((int) baseX + 1, (int) y - 1, (int) z);
            server.ticker().submit(() -> server.world().setBlock(floor, BuiltinBlocks.STONE));
            await(() -> server.world().getBlock(floor).equals(BuiltinBlocks.STONE),
                    "floor placed (seat " + i + ")");
            placer.inventory().setSlot(0,
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SIGN, 4));
            placer.inventory().selectHotbarSlot(0);
            useOn(placer, floor, 1);
            BlockPosition target = floor.offset(0, 1, 0);
            await(() -> expected.equals(server.world().getBlock(target).identifier().value()),
                    "yaw " + yaw + " lands " + expected);
        }
    }

    // ----------------------------------------------------------------- the gates

    @Test
    void aBottomFaceClickPlacesNothing() throws Exception {
        server = boot();
        PlayerSession placer = signer("Downface", 0.0f);
        // A RAISED stone with air below: the face-0 target cell must be
        // empty so the gate (not an occupied cell) is what refuses.
        double bx = Math.floor(placer.position().x()) + 2;
        double by = Math.floor(placer.position().y()) + 3;
        double bz = Math.floor(placer.position().z());
        BlockPosition raised = new BlockPosition((int) bx, (int) by, (int) bz);
        server.ticker().submit(() -> server.world().setBlock(raised, BuiltinBlocks.STONE));
        await(() -> server.world().getBlock(raised).equals(BuiltinBlocks.STONE), "raised stone placed");
        assertEquals(server.world().airType(), server.world().getBlock(raised.offset(0, -1, 0)),
                "the cell below the raised stone starts empty");
        useOn(placer, raised, 0); // the clicked stone's BOTTOM face (face 0 = down)
        Thread.sleep(300);
        assertEquals(server.world().airType(), server.world().getBlock(raised.offset(0, -1, 0)),
                "SignItem lines 21-23: a bottom-face click places NOTHING");
    }

    @Test
    void aClickedTorchRefusesButAClickedSignStacks() throws Exception {
        server = boot();
        PlayerSession placer = signer("Clicksolid", 0.0f);
        BlockPosition floor = ground("Clicksolid");
        await(() -> server.world().getBlock(floor).equals(BuiltinBlocks.STONE), "floor placed");
        // A torch on the floor: clicking IT with the sign refuses (the
        // torch's DECORATION material is not solid — SignItem lines 25-27).
        BlockPosition torchAt = floor.offset(0, 1, 0);
        server.ticker().submit(() -> server.world().setBlock(torchAt, BuiltinBlocks.TORCH));
        await(() -> server.world().getBlock(torchAt).equals(BuiltinBlocks.TORCH), "torch placed");
        useOn(placer, torchAt, 1);
        Thread.sleep(300);
        assertEquals(BuiltinBlocks.TORCH, server.world().getBlock(torchAt),
                "the torch's cell is untouched — the placement refused at the gate");
        // A sign: clicking IT with another sign stacks (the sign's own WOOD
        // material IS solid — SignBlock line 25).
        BlockPosition stand = floor.offset(0, 3, 0);
        BlockPosition base = stand.offset(0, -1, 0);
        server.ticker().submit(() -> {
            server.world().setBlock(base, BuiltinBlocks.STONE);
            server.world().setBlock(stand, BuiltinBlocks.SIGN_NORTH);
        });
        await(() -> server.world().getBlock(stand).equals(BuiltinBlocks.SIGN_NORTH),
                "the lower sign stands");
        useOn(placer, stand, 1);
        BlockPosition above = stand.offset(0, 1, 0);
        await(() -> "air".equals(server.world().getBlock(above).identifier().value()) == false
                        && server.world().getBlock(above).identifier().value().startsWith("sign"),
                "a sign stacks on a sign (WOOD material-solid)");
    }

    @Test
    void aSignBesideACactusRefuses() throws Exception {
        server = boot();
        PlayerSession placer = signer("Cactusgate", 0.0f);
        BlockPosition floorWest = ground("Cactusgate").offset(-1, 0, 0);
        BlockPosition sand = floorWest.offset(1, 0, 0);
        BlockPosition cactusAt = sand.offset(0, 1, 0);
        server.ticker().submit(() -> {
            server.world().setBlock(floorWest, BuiltinBlocks.STONE);
            server.world().setBlock(sand, BuiltinBlocks.SAND);
        });
        await(() -> server.world().getBlock(sand).equals(BuiltinBlocks.SAND), "sand placed");
        server.ticker().submit(() -> server.world().setBlock(cactusAt, BuiltinBlocks.CACTUS));
        await(() -> server.world().getBlock(cactusAt).equals(BuiltinBlocks.CACTUS), "cactus placed");
        useOn(placer, floorWest, 1);
        Thread.sleep(300);
        assertEquals(server.world().airType(), server.world().getBlock(floorWest.offset(0, 1, 0)),
                "SignBlock.canBePlaced lines 82-84: no sign beside a cactus");
        assertEquals(BuiltinBlocks.CACTUS, server.world().getBlock(cactusAt),
                "the cactus itself is untouched (the gate refused before any write)");
    }

    // ---------------------------------------------------------- the support arms

    @Test
    void theStandingSignPopsOnBrokenFloorButStandsOnLeaves() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        EngineWorld world = new EngineWorld("signsupport", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(20, 20));
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(11), 3000);
        FallingBlockEntityManager falling = new FallingBlockEntityManager(
                (x, y, z) -> false, dimension -> world,
                (position, stack, dimension) -> { }, new Random(12), 3100);
        BlockUpdateSystem system = new BlockUpdateSystem(world, items, falling);

        // Scene A: a sign standing on stone; the stone breaks -> the sign
        // pops as the item and the cell clears.
        BlockPosition stoneA = new BlockPosition(200, 5, 200);
        BlockPosition signA = stoneA.offset(0, 1, 0);
        world.setBlock(stoneA, BuiltinBlocks.STONE);
        world.setBlock(signA, BuiltinBlocks.SIGN_SSE);
        world.setBlock(stoneA, world.airType());
        system.onBlockChanged(world, stoneA, world.airType());
        system.tick();
        assertEquals(world.airType(), world.getBlock(signA),
                "StandingSignBlock lines 22-29: the sign popped with its floor");
        assertEquals(1, items.size(), "the pop dropped the sign item");
        assertEquals("minecraft:sign", items.all().get(0).stack().type().identifier().toString());

        // Scene B: a sign standing on LEAVES — the LEAVES material is solid
        // (Material.java line 13) — the sign STANDS.
        BlockPosition leaves = new BlockPosition(210, 5, 210);
        BlockPosition signB = leaves.offset(0, 1, 0);
        world.setBlock(leaves, BuiltinBlocks.OAK_LEAVES);
        world.setBlock(signB, BuiltinBlocks.SIGN_NNE);
        world.setBlock(leaves.offset(1, 0, 0), world.airType()); // an unrelated wake
        system.onBlockChanged(world, leaves.offset(1, 0, 0), world.airType());
        system.tick();
        assertEquals(BuiltinBlocks.SIGN_NNE, world.getBlock(signB),
                "a sign stands on leaves — the material model, not the air-only consult");

        // Scene C: the wall sign's wall goes -> the sign pops.
        BlockPosition wall = new BlockPosition(220, 5, 220);
        BlockPosition wallSign = wall.offset(0, 0, -1); // faces north: hangs on the south wall
        world.setBlock(wall, BuiltinBlocks.STONE);
        world.setBlock(wallSign, BuiltinBlocks.WALL_SIGN_NORTH);
        world.setBlock(wall, world.airType());
        system.onBlockChanged(world, wall, world.airType());
        system.tick();
        assertEquals(world.airType(), world.getBlock(wallSign),
                "WallSignBlock lines 43-51: the sign popped with its wall");
        assertEquals(2, items.size(), "two pops, two item entities");
    }

    // ------------------------------------------------------- the surface consults

    @Test
    void allSixteenStatesRegisterTheirNibblesAndImport() {
        String[] names = {"sign", "sign_ssw", "sign_sw", "sign_wsw", "sign_west", "sign_wnw",
                "sign_nw", "sign_nnw", "sign_north", "sign_nne", "sign_ne", "sign_ene",
                "sign_east", "sign_ese", "sign_se", "sign_sse"};
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        for (int meta = 0; meta < 16; meta++) {
            var id = net.zaminmc.torch.util.Identifier.parse("minecraft:" + names[meta]);
            Optional<BlockType> found = registry.lookup(id);
            assertTrue(found.isPresent(), names[meta] + " is registered");
            assertEquals(meta,
                    ((net.zaminmc.torch.server.block.EngineBlockType) found.get()).legacyMetadata(),
                    names[meta] + " carries the wire nibble " + meta);
            // The anvil import resolves every vanilla rotation (block 63):
            // the pre-audit import isolated the twelve intermediates as AIR.
            assertEquals(Optional.of(id), AnvilBlockIds.resolve(63, meta),
                    "(63, " + meta + ") imports as " + names[meta]);
        }
    }
}
