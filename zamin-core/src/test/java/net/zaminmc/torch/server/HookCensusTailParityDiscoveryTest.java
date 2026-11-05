package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.fishing.Fishing;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-13 — the fishing hook's CENSUS TAIL: the reference's remaining
 * {@code hasCollision} arms the engine's bobber consult did not carry —
 * the PRIMED TNT (PrimedTntEntity line 42's {@code !removed}, the live
 * body's own 0.98x0.98 hull), the ENDER CRYSTALS (EnderCrystalEntity
 * line 62's {@code true}, the 2x2 hull), and the DECORATIONS whole
 * (DecorationEntity line 123's {@code true} — the ITEM FRAMES at their
 * 12x12-pixel hanging box and the PAINTINGS at their motive's own span).
 *
 * <p>The pre-fix census never carried the four families — a bobber
 * passing straight through a primed TNT, a crystal, a frame or a
 * painting hooked nothing at all. All four arms RED pre-fix; the
 * outside control green by design.</p>
 *
 * <p>The rig rides the PE-9 flight's own schedule (the flat pitch-0
 * lob): the bobber spawns at the eye and descends on the fixed line
 * {@code angler.x - 0.16}, crossing y&asymp;41.3 at z+4 and y&asymp;40.9
 * at z+8.5 — the targets sit where the line passes through their grown
 * hulls. The frame and the painting hang on the corridor's WEST wall
 * (facing east), their grown hanging boxes' x-band covering the line,
 * the flight entering through the y-band's top face mid-corridor.</p>
 */
class HookCensusTailParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "hooktail" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private boolean hooked(PlayerSession angler) {
        return server.fishing().bobberOf(angler.engineEntityId())
                .map(Fishing.Bobber::hooked).orElse(false);
    }

    private PlayerSession join(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                200, name + " playing");
        return session;
    }

    /**
     * The overworld corridor (the PE-9 rig's own shape): a stone floor
     * at y = 39, the flight lane cleared to y = 45, the sky-blocking
     * roof at 46, and the WEST WALL column at x - 1 for the hanging
     * bodies (the frame and the painting face east into the lane). The
     * angler stands at x + 0.35 — near the wall so the flight line
     * (x + 0.35 - 0.16 = x + 0.19) runs INSIDE the grown hanging box's
     * x-band [wallFace - 0.3, wallFace + 0.36].
     */
    private PlayerSession rigAnglerBesideWall(int x, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = 0; dx <= 3; dx++) {
                for (int dz = -1; dz <= 14; dz++) {
                    server.world().setBlock(new BlockPosition(x + dx, 39, z + dz),
                            BuiltinBlocks.STONE);
                    for (int dy = 0; dy <= 6; dy++) {
                        server.world().setBlock(new BlockPosition(x + dx, 40 + dy, z + dz),
                                BuiltinBlocks.AIR);
                    }
                    server.world().setBlock(new BlockPosition(x + dx, 46, z + dz),
                            BuiltinBlocks.STONE);
                }
            }
            // The west wall the decorations hang on (x - 1, the flight
            // lane's whole length, two cells tall).
            for (int dz = -1; dz <= 14; dz++) {
                server.world().setBlock(new BlockPosition(x - 1, 40, z + dz),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 1, 41, z + dz),
                        BuiltinBlocks.STONE);
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(x - 1, 40, z + 5)))
                        && BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(x, 46, z + 8))),
                100, "the corridor and wall stand");
        PlayerSession angler = join("WallAngler");
        server.teleportPlayer(angler, new Position(x + 0.35, 40, z + 0.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> angler.position().y() == 40.0
                        && angler.position().x() == x + 0.35,
                200, "the angler standing beside the wall");
        assertTrue(angler.inventory().pickUp(ItemStack.of(BuiltinItems.FISHING_ROD)).isEmpty(),
                "the rod granted into the held slot");
        server.ticker().submit(() -> angler.applyMovement(angler.position(),
                new Rotation(0.0f, 0.0f), angler.onGround()));
        EngineAwaits.awaitCondition(server.world(),
                () -> angler.rotation().yaw() == 0.0f, 200, "the angler aims +z");
        return angler;
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE PRIMED TNT: the !removed census arm, the DEFLECT
    // verdict (Wave PE-20's repair of this seat's own first form)
    // ------------------------------------------------------------------

    /**
     * A primed TNT body standing in the flight lane at z+8 — its own
     * 0.98x0.98 hull grown 0.3 spans y [39.7, 41.28], and the flight's
     * descent passes y&asymp;40.9 through the lane: the bobber's scan
     * FINDS the body (the {@code !removed} census arm — the pre-PE-13
     * engine never carried the family), but the hook verdict REFUSES it
     * (Wave PE-20 — the reference's own law, FishingBobberEntity.tick
     * lines 269-271: {@code takeDamage(DamageSource.thrown(...), 0.0F)}
     * on the BASE Entity's walk answers FALSE, Entity.java lines
     * 741-748 — PrimedTntEntity overrides nothing): the bobber
     * DEFLECTS — no hook, and no stick either (the entity arm replaced
     * the block hit whole), the flight simply continuing through the
     * fuse's cell. This seat's own first form asserted the hook — the
     * PE-13 wave's misread of the verdict, repaired here.
     */
    @Test
    void theFlightDeflectsOffThePrimedTnt() throws Exception {
        int x = 10, z = 10;
        PlayerSession angler = rigAnglerBesideWall(x, z);
        double lineX = x + 0.35 - 0.16;
        server.ticker().submit(() -> server.tnt().spawnPrimedAt(
                server.world(), lineX, 40.0, z + 8.0));
        EngineAwaits.awaitCondition(server.world(),
                () -> !server.tnt().allPrimed().isEmpty(), 100,
                "the TNT primed in the lane");

        server.useItem(angler); // the cast
        EngineAwaits.awaitTicks(server.world(), 60, "the deflect window");
        assertFalse(hooked(angler),
                "the bobber DEFLECTS off the primed TNT (the base Entity's FALSE"
                        + " verdict — no hook, the PE-13 form's own repair)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE ENDER CRYSTAL: the End-world census arm
    // ------------------------------------------------------------------

    /**
     * An ender crystal beamed into the End corridor's lane — its 2x2
     * hull grown 0.3 spans y [39.7, 42.3] whole, and the flight enters
     * the band from the first tick: the bobber hooks it. The crystal's
     * own feet-cell fire write rides along (the End gate). Pre-fix RED:
     * the census never carried the family.
     */
    @Test
    void theFlightHooksTheEnderCrystal() throws Exception {
        int x = 10, z = 10;
        // The corridor stands in the END (the crystal's own world).
        var endWorld = server.worldFor(1);
        server.ticker().submit(() -> {
            for (int dx = 0; dx <= 3; dx++) {
                for (int dz = -1; dz <= 14; dz++) {
                    endWorld.setBlock(new BlockPosition(x + dx, 39, z + dz),
                            BuiltinBlocks.STONE);
                    for (int dy = 0; dy <= 6; dy++) {
                        endWorld.setBlock(new BlockPosition(x + dx, 40 + dy, z + dz),
                                BuiltinBlocks.AIR);
                    }
                    endWorld.setBlock(new BlockPosition(x + dx, 46, z + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(endWorld.getBlock(
                        new BlockPosition(x, 46, z + 8))), 100,
                "the End corridor stands");
        PlayerSession angler = join("EndAngler");
        server.ticker().submit(() -> angler.setDimension(1));
        server.teleportPlayer(angler, new Position(x + 0.5, 40, z + 0.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> angler.dimension() == 1 && angler.position().y() == 40.0,
                200, "the angler standing in the End corridor");
        assertTrue(angler.inventory().pickUp(ItemStack.of(BuiltinItems.FISHING_ROD)).isEmpty(),
                "the rod granted into the held slot");
        server.ticker().submit(() -> angler.applyMovement(angler.position(),
                new Rotation(0.0f, 0.0f), angler.onGround()));
        EngineAwaits.awaitCondition(server.world(),
                () -> angler.rotation().yaw() == 0.0f, 200, "the angler aims +z");
        server.ticker().submit(() -> server.enderCrystals()
                .spawn(x + 0.34, 40.0, z + 8.0, 0.0f));
        EngineAwaits.awaitCondition(server.world(),
                () -> !server.enderCrystals().all().isEmpty(), 100,
                "the crystal beamed into the lane");

        server.useItem(angler); // the cast
        EngineAwaits.awaitCondition(server.world(), () -> hooked(angler),
                100, "the flight hooks the ender crystal (the 2x2 hull)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE ITEM FRAME: the DecorationEntity census arm
    // ------------------------------------------------------------------

    /**
     * An item frame hanging on the corridor's west wall at (x, 41, z+5)
     * facing east — its 12x12-pixel hanging box pulled onto the wall
     * face, grown 0.3 into a 0.66-thick slab whose x-band covers the
     * flight line, the flight entering through the y-band's top face
     * (y = 41.175 crossed mid-window, inside the box's 1.35-wide
     * z-window): the bobber hooks it. Pre-fix RED: the census never
     * carried the family.
     */
    @Test
    void theFlightHooksTheItemFrame() throws Exception {
        int x = 10, z = 10;
        PlayerSession angler = rigAnglerBesideWall(x, z);
        server.ticker().submit(() -> server.decorations().restoreFrame(
                new BlockPosition(x, 41, z + 5), 3, null, 0));
        EngineAwaits.awaitCondition(server.world(),
                () -> server.decorations().frameAt(
                        new BlockPosition(x, 41, z + 5)) != null, 100,
                "the frame hung on the wall");

        server.useItem(angler); // the cast
        EngineAwaits.awaitCondition(server.world(), () -> hooked(angler),
                100, "the flight hooks the item frame (the hanging box)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE PAINTING: the same census, the motive's own span
    // ------------------------------------------------------------------

    /**
     * A Kebab painting (16x16 pixels — the 1x1 span) hanging on the
     * same wall at the same cell: the same hanging-box law, the
     * motive's own span answering the shape. The bobber hooks it.
     * Pre-fix RED: the census never carried the family.
     */
    @Test
    void theFlightHooksThePainting() throws Exception {
        int x = 10, z = 10;
        PlayerSession angler = rigAnglerBesideWall(x, z);
        server.ticker().submit(() -> server.decorations().restorePainting(
                new BlockPosition(x, 41, z + 5), 3,
                net.zaminmc.torch.server.decoration.Painting.Motive.KEBAB));
        EngineAwaits.awaitCondition(server.world(),
                () -> server.decorations().paintingAt(
                        new BlockPosition(x, 41, z + 5)) != null, 100,
                "the painting hung on the wall");

        server.useItem(angler); // the cast
        EngineAwaits.awaitCondition(server.world(), () -> hooked(angler),
                100, "the flight hooks the painting (the motive's own span)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE OUTSIDE CONTROL: the wall's far side never hooks
    // ------------------------------------------------------------------

    /**
     * The control: a frame hung on the wall FARTHER EAST than the
     * flight line (at x+2, facing east from a second wall column at
     * x+1) — its hanging box's x-band sits beyond the line and the
     * flight never enters: the bobber grounds unhooked. Green by
     * design on both engines.
     */
    @Test
    void theFlightNeverHooksTheFarFrame() throws Exception {
        int x = 10, z = 10;
        PlayerSession angler = rigAnglerBesideWall(x, z);
        // The second wall column at x+1 with a frame facing east at x+2.
        server.ticker().submit(() -> {
            for (int dz = -1; dz <= 14; dz++) {
                server.world().setBlock(new BlockPosition(x + 1, 40, z + dz),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, 41, z + dz),
                        BuiltinBlocks.STONE);
            }
            server.decorations().restoreFrame(
                    new BlockPosition(x + 2, 41, z + 5), 3, null, 0);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> server.decorations().frameAt(
                        new BlockPosition(x + 2, 41, z + 5)) != null, 100,
                "the far frame hung");
        assertFalse(x + 0.19 > x + 2 - 0.3,
                "the rig's own sanity: the flight line never reaches the far"
                        + " frame's x-band");

        server.useItem(angler); // the cast
        EngineAwaits.awaitTicks(server.world(), 60, "the far-frame never window");
        assertFalse(hooked(angler),
                "the flight grounds unhooked — the far frame's x-band never"
                        + " crossed (the boundary's own control)");
    }
}
