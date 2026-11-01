package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FlowerPotBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-028 SEAT (F-SP1's lane — docs/vanilla-conformance-audit.md,
 * the fortieth pass): the SUPPORT consult family reads the reference's
 * STRUCTURAL law, never the engine's MOVEMENT consult.
 *
 * <p><b>The reference's law</b> (1.8.8): the support consults are their own
 * law family, distinct from every solidity model the engine already carries.
 * {@code World.hasSolidTop} (World.java lines 1740-1752) =
 * {@code material.isSolidBlocking() && block.isCube()} — the OPAQUE FULL
 * CUBE — refined by the shaped tops (a TOP stair, a TOP slab, the hopper,
 * the 7-layer snow) and extended per family by explicit allowlists (the
 * wire's glowstone, RedstoneWireBlock.canBePlaced lines 92-94; the plate's
 * fence, AbstractPressurePlateBlock line 85; the torch's
 * {@code canSitOnTop}, TorchBlock lines 49-54 — fence, GLASS, the cobble
 * wall, the stained glass). The torch's HORIZONTAL mount consults
 * {@code World.isSolidBlockingCube} alone (TorchBlock.canAttach lines 69-73
 * — {@code material.isSolidBlocking() && isCube()}, World lines 1754-1766).
 * The translucent materials — GLASS (Material.java line 21,
 * {@code setTranslucent()}), the leaves, the glowstone, the TNT, the ice,
 * the cactus — FAIL {@code isSolidBlocking()} (Material lines 100-102:
 * {@code !translucent && blocksMovement()}), so they FAIL every floor law:
 * vanilla REFUSES the wire, the rail, the diode, the plate, the pot and the
 * door on a glass floor (each family's {@code canBePlaced} —
 * RedstoneWireBlock 92-94, AbstractRailBlock 71-73, DiodeBlock 29-31,
 * AbstractPressurePlateBlock 72-74, FlowerPotBlock 137, DoorBlock 214) and
 * POPS them when the floor becomes glass (each family's
 * {@code neighborChanged} re-consult — RedstoneWireBlock 235-244,
 * AbstractRailBlock 90-102, DiodeBlock 80-91, FlowerPotBlock 142-145,
 * DoorBlock 175-180). A torch hangs on NO glass side
 * ({@code isSolidBlockingCube} false) — but STANDS on a glass TOP
 * ({@code canSitOnTop}'s own allowlist names the glass, TorchBlock lines
 * 49-54).</p>
 *
 * <p><b>The engine's shape</b> (develop {@code 149eb1f0}): every support
 * site reads {@code WorldSolidity.isSolid} — the MOVEMENT consult (what
 * blocks a body). The glass, the leaves, the glowstone, the TNT and the ice
 * are movement-SOLID, so the engine keeps every member the reference pops.
 * {@code RedstoneSystem.hasSupportBelow} (lines 2297-2299, the wire + the
 * diodes + the plates), {@code torchSupported} (lines 1685-1688),
 * {@code railNeighborChanged}'s bed and headwall (lines 1155-1160),
 * {@code doorNeighborChanged}'s floor (line 1047) and the placement gate's
 * pot arm (EngineServer line 3440 — the comment even cites
 * "must have a SOLID TOP" while the code reads the movement consult) all
 * stand on the one wrong law; the wire, the rail and the torch families
 * have NO placement-time consult at all (the gate's families are the
 * flora, the cake, the pot and the mushrooms alone, EngineServer lines
 * 3413-3457 — {@code BlockItem.useOn} line 43's
 * {@code World.canPlace → block.canBePlaced} consult has no engine
 * counterpart for them, so a wire PLACES on glass where vanilla refuses
 * the placement itself).</p>
 *
 * <p><b>The seat pins the vanilla law</b> — RED on develop at every
 * finding member, GREEN at every control. Do not re-pin the asserts to the
 * engine's current answers; the finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StructuralSupportLawDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "suplaw" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.CREATIVE, true, "flat", false,
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
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private void settle() throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 4, "the settle");
    }

    /**
     * VT-028, member 1 — THE PLACEMENT ARM (the missing gate). Vanilla
     * refuses the wire's placement on a glass floor outright:
     * {@code BlockItem.useOn} line 43 consults {@code World.canPlace}, the
     * target cell's air is replaceable, so the wire's own
     * {@code canBePlaced} runs — {@code hasSolidTop(glass) || glowstone}
     * (RedstoneWireBlock lines 92-94) — and the GLASS material is
     * translucent (Material line 21), failing
     * {@code isSolidBlocking()} (lines 100-102). The placement NEVER
     * COMMITS; the cell stays air.
     *
     * <p>The engine has no wire arm in the placement-support gate (the
     * flora, the cake, the pot and the mushrooms alone) and no support
     * consult in {@code onAdded} (the power walk + the wake rings only) —
     * the wire COMMITS on the glass floor. RED on develop.</p>
     */
    @Test
    void theWirePlacementIsRefusedOnTheGlassFloor() throws Exception {
        PlayerSession player = join("WirePlacer");
        server.ticker().submit(() -> sessionMove(player,
                new Position(100.5, 61.5, 98.5)));
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(100, 60, 100), BuiltinBlocks.GLASS));
        await(() -> BuiltinBlocks.GLASS.equals(server.world()
                .getBlock(new BlockPosition(100, 60, 100))), "the glass floor stands");
        settle();

        server.blockInteraction().submitPlace(player,
                new BlockPosition(100, 60, 100), 1, RedstoneBlocks.wireOfPower(0));
        settle();

        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(new BlockPosition(100, 61, 100)),
                "VT-028: the wire's placement on a GLASS floor is REFUSED — the reference's "
                        + "canBePlaced consult (RedstoneWireBlock lines 92-94: hasSolidTop || "
                        + "GLOWSTONE alone) fails the translucent glass material "
                        + "(Material.setTranslucent line 21, isSolidBlocking lines 100-102), so "
                        + "BlockItem.useOn line 43's World.canPlace never commits the wire. "
                        + "Observed: the wire stands on the glass — the engine's placement path "
                        + "carries no wire consult at all (EngineServer's support gate lines "
                        + "3413-3457 own the flora/cake/pot/mushroom arms alone; RedstoneSystem"
                        + ".onAdded runs the power walk, never a support check)");
    }

    /**
     * VT-028, member 1's control — the stone floor places in both models
     * ({@code hasSolidTop(stone)} true; the engine's movement consult
     * solid). The seat cannot pass by refusing every placement.
     */
    @Test
    void theControlWirePlacementStandsOnStone() throws Exception {
        PlayerSession player = join("WireStoner");
        server.ticker().submit(() -> sessionMove(player,
                new Position(100.5, 61.5, 102.5)));
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(100, 60, 102), BuiltinBlocks.STONE));
        await(() -> BuiltinBlocks.STONE.equals(server.world()
                .getBlock(new BlockPosition(100, 60, 102))), "the stone floor stands");
        settle();

        server.blockInteraction().submitPlace(player,
                new BlockPosition(100, 60, 102), 1, RedstoneBlocks.wireOfPower(0));
        await(() -> RedstoneBlocks.isWire(server.world()
                .getBlock(new BlockPosition(100, 61, 102))), "the wire lands on stone");
    }

    /**
     * VT-028, member 1's allowlist control — the WIRE ON GLOWSTONE stands
     * in both models: the reference's {@code canBePlaced} names the
     * glowstone explicitly (RedstoneWireBlock line 93), the engine's
     * movement consult reads it solid. The allowlist member the engine
     * gets RIGHT — the finding is the consult's law, not a blanket flip.
     */
    @Test
    void theControlWirePlacementStandsOnGlowstone() throws Exception {
        PlayerSession player = join("WireGlow");
        server.ticker().submit(() -> sessionMove(player,
                new Position(100.5, 61.5, 104.5)));
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(100, 60, 104), BuiltinBlocks.GLOWSTONE));
        await(() -> BuiltinBlocks.GLOWSTONE.equals(server.world()
                .getBlock(new BlockPosition(100, 60, 104))), "the glowstone floor stands");
        settle();

        server.blockInteraction().submitPlace(player,
                new BlockPosition(100, 60, 104), 1, RedstoneBlocks.wireOfPower(0));
        await(() -> RedstoneBlocks.isWire(server.world()
                .getBlock(new BlockPosition(100, 61, 104))),
                "the wire lands on the glowstone (the reference's own allowlist)");
    }

    /**
     * VT-028, member 2 — THE SURVIVAL ARM (the movement-consult stand-in).
     * A wire standing legally on stone, its floor swapped to glass:
     * vanilla's {@code neighborChanged} re-consults {@code canBePlaced}
     * (RedstoneWireBlock lines 235-244), the glass fails
     * {@code hasSolidTop}, and the wire POPS with its redstone drop.
     *
     * <p>The engine's {@code RedstoneSystem.hasSupportBelow} (lines
     * 2297-2299) reads {@code WorldSolidity.isSolid} — the MOVEMENT
     * consult — and the glass is movement-solid: the wire STANDS on glass
     * forever. RED on develop.</p>
     */
    @Test
    void theWirePopsWhenItsFloorBecomesGlass() throws Exception {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(102, 60, 100), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(102, 61, 100), RedstoneBlocks.wireOfPower(0));
        });
        await(() -> RedstoneBlocks.isWire(server.world()
                .getBlock(new BlockPosition(102, 61, 100))), "the wire stands on stone");
        settle();

        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(102, 60, 100), BuiltinBlocks.GLASS));
        settle();

        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(new BlockPosition(102, 61, 100)),
                "VT-028: a wire whose floor became GLASS pops — the reference's "
                        + "neighborChanged re-consult (RedstoneWireBlock lines 235-244 over "
                        + "canBePlaced lines 92-94) fails hasSolidTop on the translucent glass "
                        + "material and drops the wire as redstone. Observed: the wire stands — "
                        + "RedstoneSystem.hasSupportBelow (lines 2297-2299) reads the MOVEMENT "
                        + "consult (WorldSolidity.isSolid), and glass blocks bodies");
    }

    /**
     * VT-028, member 2's control — the vanishing floor pops the wire in
     * BOTH models (the air fails every law). The pop wiring is live in
     * this boot; the glass member above cannot hide behind a dead arm.
     */
    @Test
    void theControlWirePopsWhenItsFloorVanishes() throws Exception {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(102, 60, 102), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(102, 61, 102), RedstoneBlocks.wireOfPower(0));
        });
        await(() -> RedstoneBlocks.isWire(server.world()
                .getBlock(new BlockPosition(102, 61, 102))), "the wire stands on stone");
        settle();

        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(102, 60, 102), BuiltinBlocks.AIR));
        settle();

        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(new BlockPosition(102, 61, 102)),
                "the wire pops when its floor vanishes (both laws refuse the air floor)");
    }

    /**
     * VT-028, member 3 — THE TORCH'S HORIZONTAL MOUNT
     * ({@code isSolidBlockingCube}). A redstone torch hung on a glass wall:
     * vanilla's {@code tryBreak} (TorchBlock lines 100-121) consults
     * {@code canAttach} — the horizontal arm is
     * {@code world.isSolidBlockingCube(blockpos, true)} ALONE (lines
     * 69-73) — and the glass fails it (the translucent material). The
     * torch pops on the very neighbor update.
     *
     * <p>The engine's {@code torchSupported} (lines 1685-1688) reads the
     * movement consult: the glass is movement-solid, the torch hangs
     * forever. RED on develop.</p>
     */
    @Test
    void theWallTorchPopsOnTheGlassWall() throws Exception {
        // The glass wall the torch hangs on; the torch faces EAST (FACING
        // 5), its attachment sits one cell WEST (torchAttachmentStep).
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(104, 60, 100), BuiltinBlocks.GLASS);
            server.world().setBlock(new BlockPosition(105, 60, 100),
                    RedstoneBlocks.torchOfFacing(5, true));
        });
        await(() -> BuiltinBlocks.GLASS.equals(server.world()
                .getBlock(new BlockPosition(104, 60, 100))), "the glass wall stands");
        await(() -> RedstoneBlocks.isTorch(server.world()
                .getBlock(new BlockPosition(105, 60, 100))), "the wall torch hangs");
        settle();

        // The neighbor trigger: a stone lands ABOVE the torch (a neighbor
        // of the torch, never the glass itself — the consult target stays
        // the glass wall).
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(105, 61, 100), BuiltinBlocks.STONE));
        settle();

        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(new BlockPosition(105, 60, 100)),
                "VT-028: a wall torch hung on GLASS pops on the neighbor update — the "
                        + "reference's tryBreak (TorchBlock lines 100-121) consults canAttach's "
                        + "horizontal arm (lines 69-73: isSolidBlockingCube ALONE) and the "
                        + "translucent glass material fails it. Observed: the torch hangs — "
                        + "RedstoneSystem.torchSupported (lines 1685-1688) reads the MOVEMENT "
                        + "consult, and glass blocks bodies");
    }

    /**
     * VT-028, member 3's control — the stone wall holds the torch in both
     * models ({@code isSolidBlockingCube(stone)} true; movement-solid).
     */
    @Test
    void theControlWallTorchStandsOnTheStoneWall() throws Exception {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(104, 60, 102), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(105, 60, 102),
                    RedstoneBlocks.torchOfFacing(5, true));
        });
        await(() -> RedstoneBlocks.isTorch(server.world()
                .getBlock(new BlockPosition(105, 60, 102))), "the wall torch hangs");
        settle();

        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(105, 61, 102), BuiltinBlocks.STONE));
        settle();

        assertTrue(RedstoneBlocks.isTorch(server.world()
                        .getBlock(new BlockPosition(105, 60, 102))),
                "the wall torch stands on the stone wall through the neighbor update (both laws hold it)");
    }

    /**
     * VT-028, member 3's allowlist control — the STANDING torch on a glass
     * TOP stands in BOTH models: the reference's {@code canSitOnTop}
     * (TorchBlock lines 49-54) names the GLASS explicitly beside the fence
     * and the cobble wall; the engine's movement consult reads it solid.
     * The vertical arm's allowlist is a vanilla exception the engine gets
     * right — the finding's law is the consult each site reads, and the
     * horizontal arm alone consults {@code isSolidBlockingCube} bare.
     */
    @Test
    void theControlStandingTorchStandsOnTheGlassFloor() throws Exception {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(104, 60, 104), BuiltinBlocks.GLASS);
            server.world().setBlock(new BlockPosition(104, 61, 104),
                    RedstoneBlocks.torchStandingLit());
        });
        await(() -> RedstoneBlocks.isTorch(server.world()
                .getBlock(new BlockPosition(104, 61, 104))), "the standing torch stands");
        settle();

        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(104, 62, 104), BuiltinBlocks.STONE));
        settle();

        assertTrue(RedstoneBlocks.isTorch(server.world()
                        .getBlock(new BlockPosition(104, 61, 104))),
                "the standing torch stands on the glass TOP (the reference's canSitOnTop "
                        + "allowlist names the glass, TorchBlock lines 49-54)");
    }

    /**
     * VT-028, member 4 — THE POT'S GATE (the stand-in consult, the site
     * that already knows the law). {@code FlowerPotBlock.canBePlaced}
     * (lines 137-139) demands {@code World.hasSolidTop(pos.down())}; the
     * glass fails it (translucent), and the placement is REFUSED.
     *
     * <p>The engine's placement gate HAS a pot arm (EngineServer line
     * 3440) whose comment cites the law — "the block below must have a
     * SOLID TOP" — while the code reads {@code WorldSolidity.isSolid},
     * the MOVEMENT consult: the pot lands on the glass. RED on develop,
     * the finding's sharpest single-site member.</p>
     */
    @Test
    void thePotPlacementIsRefusedOnTheGlassFloor() throws Exception {
        PlayerSession player = join("PotPlacer");
        // The player stands on a platform ONE CELL WEST of the target — the
        // pot is movement-solid, so a player inside the target cell would
        // trip the placement's inside-player gate (a wrong-law refusal).
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(105, 60, 100), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(106, 60, 100), BuiltinBlocks.GLASS);
        });
        await(() -> BuiltinBlocks.GLASS.equals(server.world()
                .getBlock(new BlockPosition(106, 60, 100))), "the glass floor stands");
        server.ticker().submit(() -> sessionMove(player,
                new Position(105.5, 61.5, 100.5)));
        await(() -> player.position().x() == 105.5, "the player stood on the platform");
        settle();

        server.blockInteraction().submitPlace(player,
                new BlockPosition(106, 60, 100), 1, FlowerPotBlocks.state(0));
        settle();

        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(new BlockPosition(106, 61, 100)),
                "VT-028: the pot's placement on a GLASS floor is REFUSED — FlowerPotBlock"
                        + ".canBePlaced (lines 137-139) demands hasSolidTop and the translucent "
                        + "glass material fails it. Observed: the pot stands — the engine's "
                        + "placement gate (EngineServer line 3440) cites the law in its comment "
                        + "but reads WorldSolidity.isSolid, the MOVEMENT consult");
    }

    /** VT-028, member 4's control — the pot places on stone in both models. */
    @Test
    void theControlPotPlacementStandsOnStone() throws Exception {
        PlayerSession player = join("PotStoner");
        // The same one-cell-west platform (the inside-player gate again).
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(105, 60, 102), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(106, 60, 102), BuiltinBlocks.STONE);
        });
        await(() -> BuiltinBlocks.STONE.equals(server.world()
                .getBlock(new BlockPosition(106, 60, 102))), "the stone floor stands");
        server.ticker().submit(() -> sessionMove(player,
                new Position(105.5, 61.5, 102.5)));
        await(() -> player.position().x() == 105.5, "the player stood on the platform");
        settle();

        server.blockInteraction().submitPlace(player,
                new BlockPosition(106, 60, 102), 1, FlowerPotBlocks.state(0));
        await(() -> FlowerPotBlocks.isPot(server.world()
                        .getBlock(new BlockPosition(106, 61, 102)).identifier()),
                "the pot lands on stone");
    }

    /**
     * VT-028, member 5 — THE RAIL'S PLACEMENT ARM (the missing gate, the
     * second family). {@code AbstractRailBlock.canBePlaced} (lines 71-73)
     * demands {@code World.hasSolidTop(pos.down())}; the glass fails it
     * and the placement is REFUSED. The engine has no rail arm in the
     * placement gate: the rail lands. RED on develop.
     */
    @Test
    void theRailPlacementIsRefusedOnTheGlassFloor() throws Exception {
        PlayerSession player = join("RailPlacer");
        server.ticker().submit(() -> sessionMove(player,
                new Position(106.5, 61.5, 104.5)));
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(106, 60, 104), BuiltinBlocks.GLASS));
        await(() -> BuiltinBlocks.GLASS.equals(server.world()
                .getBlock(new BlockPosition(106, 60, 104))), "the glass floor stands");
        settle();

        server.blockInteraction().submitPlace(player,
                new BlockPosition(106, 60, 104), 1, BuiltinBlocks.RAIL);
        settle();

        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(new BlockPosition(106, 61, 104)),
                "VT-028: the rail's placement on a GLASS floor is REFUSED — "
                        + "AbstractRailBlock.canBePlaced (lines 71-73) demands hasSolidTop and "
                        + "the translucent glass material fails it. Observed: the rail stands — "
                        + "the engine's placement path carries no rail consult");
    }

    /** VT-028, member 5's control — the rail places on stone in both models. */
    @Test
    void theControlRailPlacementStandsOnStone() throws Exception {
        PlayerSession player = join("RailStoner");
        server.ticker().submit(() -> sessionMove(player,
                new Position(108.5, 61.5, 100.5)));
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(108, 60, 100), BuiltinBlocks.STONE));
        await(() -> BuiltinBlocks.STONE.equals(server.world()
                .getBlock(new BlockPosition(108, 60, 100))), "the stone floor stands");
        settle();

        server.blockInteraction().submitPlace(player,
                new BlockPosition(108, 60, 100), 1, BuiltinBlocks.RAIL);
        await(() -> BuiltinBlocks.RAIL.equals(server.world()
                        .getBlock(new BlockPosition(108, 61, 100))),
                "the rail lands on stone");
    }

    /** The test thread's movement submit (the EC seat's applyMovement form). */
    private void sessionMove(PlayerSession session, Position at) {
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
    }
}
