package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-20 — THE ENTITY SCAN'S CENSUS TAIL (the PE-19 ledger's
 * standing rows, the reference's remaining {@code hasCollision} arms):
 * the PRIMED TNT (PrimedTntEntity line 42's {@code !removed}), the
 * FALLING BLOCKS (FallingBlockEntity line 62's {@code !removed}), the
 * DECORATIONS whole (DecorationEntity line 123's {@code true} — the
 * item frames at their 12x12-pixel hanging box, the paintings at their
 * motive's own span), and the OTHER PROJECTILES (ProjectileEntity line
 * 238's {@code true} — the arrow, the thrown family, the large
 * fireball; SmallFireballEntity line 57 and WitherSkullEntity line 89
 * answer FALSE, never candidates). The pre-fix engine's scan never
 * carried the four families — an arrow flew through a live fuse, a
 * falling anvil, a frame, a painting and another arrow whole, sticking
 * in the back wall behind every one of them.
 *
 * <p>THE PER-FAMILY HIT ARMS the seats pin:</p>
 * <ul>
 *   <li><b>THE ARROW'S BOUNCE OFF A REFUSED BODY</b> — the primed TNT,
 *       the falling block and the struck arrow all answer the wound
 *       with the BASE Entity's takeDamage FALSE (Entity.java lines
 *       741-748, none of the families overrides it): the arrow's own
 *       else-arm (ArrowEntity lines 279-285 — the per-axis *-0.1, the
 *       air clock reset) — the arrow is never consumed, never sticks,
 *       and flies BACK off the body (the pre-fix engine consumed it at
 *       the back wall whole).</li>
 *   <li><b>THE FRAME'S POP</b> — ItemFrameEntity.takeDamage lines 41-58:
 *       the non-explosive source with a display item EJECTS the item
 *       (the frame stays on the wall), the bare frame breaks with the
 *       frame item's drop; TRUE answered — the arrow dies at the frame
 *       with the bowhit. The POTION alone never consults the wound
 *       (PotionEntity.onCollision calls no takeDamage): a splash
 *       through a loaded frame leaves the display whole.</li>
 *   <li><b>THE PAINTING'S BREAK</b> — DecorationEntity.takeDamage lines
 *       129-141 + PaintingEntity.onAttack lines 87-98: the body leaves
 *       the wall, the painting item drops, TRUE — the arrow dies at it.</li>
 * </ul>
 *
 * <p>THE RIG: the PE-19 lane's own shape (a stone lane at y=20 running
 * +Z at x=10, the back wall at z=18) with a WEST WALL column added at
 * x=9 for the hanging bodies (the frame and the painting face east into
 * the lane, their grown hanging boxes' x-band [10-0.3, 10+0.3625]
 * covering the flight line at x=10.19).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectileCensusTailParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The lane's fixed column. */
    private static final int LANE_X = 10;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "censustail" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /**
     * The PE-19 lane plus the west wall column at x=9 (cells y 20..24,
     * the lane's whole length) — the hanging bodies' own support.
     */
    private void buildLaneWithWall() throws InterruptedException {
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            for (int x = 8; x <= 12; x++) {
                for (int z = 2; z <= 20; z++) {
                    world.setBlock(new BlockPosition(x, 19, z), BuiltinBlocks.STONE);
                    for (int y = 20; y <= 24; y++) {
                        world.setBlock(new BlockPosition(x, y, z), BuiltinBlocks.AIR);
                    }
                }
            }
            for (int x = 8; x <= 12; x++) {
                for (int y = 20; y <= 24; y++) {
                    world.setBlock(new BlockPosition(x, y, 18), BuiltinBlocks.STONE);
                }
            }
            for (int z = 2; z <= 18; z++) {
                for (int y = 20; y <= 24; y++) {
                    world.setBlock(new BlockPosition(LANE_X - 1, y, z), BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(
                        new BlockPosition(LANE_X - 1, 21, 12)))
                        && BuiltinBlocks.STONE.equals(world.getBlock(
                                new BlockPosition(LANE_X, 24, 18))),
                "the lane, the back wall and the west wall stand");
    }

    /**
     * Launches an arrow north along the lane (+Z, yaw 0) at the given
     * height and start z — the flight-hugging form the frame band needs
     * (the arrow's gravity drop kept inside the hanging box's grown
     * y-band over the approach).
     */
    private ProjectileEntity launchArrowFrom(double y, double startZ, double speed)
            throws InterruptedException {
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(LANE_X + 0.19, y, startZ), 0.0, 0.0, speed, 0)));
        await(() -> launched.get() != null, "the arrow launched along the lane");
        return launched.get();
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE PRIMED TNT: the refused body's bounce (RED pre-fix:
    // the arrow flew through the live fuse and stuck in the back wall)
    // ------------------------------------------------------------------

    /**
     * A primed TNT standing in the lane at z=10.5 — its own 0.98x0.98
     * hull grown 0.3 spans y [19.7, 21.28], and the arrow's flight at
     * y=21 enters the band from the first tick. Vanilla's walk finds
     * the grown hull, the BASE Entity's takeDamage refuses the wound
     * (FALSE, Entity.java lines 741-748), and the arrow's own else-arm
     * BOUNCES it (ArrowEntity lines 279-285: the per-axis *-0.1): the
     * arrow flies BACK off the fuse — never consumed, never stuck — and
     * the TNT's fuse keeps burning untouched.
     */
    @Test
    void theArrowBouncesOffThePrimedTnt() throws Exception {
        buildLaneWithWall();
        server.ticker().submit(() -> server.tnt().spawnPrimedAt(
                server.world(), LANE_X + 0.5, 20.0, 10.5));
        await(() -> !server.tnt().allPrimed().isEmpty(), "the TNT primed in the lane");
        ProjectileEntity arrow = launchArrowFrom(21.0, 4.5, 2.0);

        await(() -> {
            ProjectileEntity live = server.projectiles().byId(arrow.entityId());
            return live != null && live.velocityZ() < 0.0;
        }, "the arrow BOUNCES off the primed TNT (the refused body's else-arm)");
        ProjectileEntity bounced = server.projectiles().byId(arrow.entityId());
        assertNotNull(bounced, "the bounced arrow stays in the world (never consumed)");
        assertFalse(bounced.inGround(),
                "the bounced arrow never sticks (the pre-fix engine flew it through"
                        + " the fuse to the back wall)");
        assertTrue(!server.tnt().allPrimed().isEmpty(),
                "the TNT's fuse keeps burning (the wound never landed)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE FALLING BLOCK: the same refused-body bounce (RED
    // pre-fix: the arrow flew through the falling sand to the back wall)
    // ------------------------------------------------------------------

    /**
     * A column of sand breaking loose at (x+0.5, 21, 6.5) — the falling
     * body's own 0.98x0.98 hull grown 0.3 spans y [20.7, 22.28] whole,
     * and the arrow's first tick (the segment [4.5, 6.5] at y 20.95)
     * crosses the grown z-face mid-flight: the same BASE-Entity refusal,
     * the same bounce. The sand keeps falling past the deflected arrow
     * and lands on the lane behind it.
     */
    @Test
    void theArrowBouncesOffTheFallingBlock() throws Exception {
        buildLaneWithWall();
        server.ticker().submit(() -> server.fallingEntities().startFall(
                server.world(), new BlockPosition(LANE_X, 21, 6),
                BuiltinBlocks.SAND));
        await(() -> !server.fallingEntities().all().isEmpty(),
                "the sand column breaking loose in the lane");
        ProjectileEntity arrow = launchArrowFrom(21.0, 4.5, 2.0);

        await(() -> {
            ProjectileEntity live = server.projectiles().byId(arrow.entityId());
            return live != null && live.velocityZ() < 0.0;
        }, "the arrow BOUNCES off the falling sand (the refused body's else-arm)");
        ProjectileEntity bounced = server.projectiles().byId(arrow.entityId());
        assertNotNull(bounced, "the bounced arrow stays in the world");
        // The settle: the bounced arrow lands on the lane floor BEFORE
        // the sand's column (never through to the back wall), and the
        // sand column lands whole behind the deflection.
        await(() -> {
            ProjectileEntity live = server.projectiles().byId(arrow.entityId());
            return live != null && live.inGround();
        }, "the bounced arrow settles on the lane floor");
        ProjectileEntity settled = server.projectiles().byId(arrow.entityId());
        assertTrue(settled.stickCellZ() < 6,
                "the deflected arrow settles BEFORE the sand's column (observed z cell "
                        + settled.stickCellZ() + " — the pre-fix engine stuck it in the"
                        + " back wall at cell 18)");
        await(() -> server.fallingEntities().all().isEmpty(),
                "the sand column lands on the lane (its own fall unbroken)");
        assertTrue(BuiltinBlocks.SAND.equals(server.world().getBlock(
                        new BlockPosition(LANE_X, 20, 6))),
                "the landed sand rests where it fell");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE LOADED FRAME: the arrow's one-hit item-eject (RED
    // pre-fix: the arrow flew through the frame, the display stayed)
    // ------------------------------------------------------------------

    /**
     * An item frame hanging on the west wall at (10, 21, 12) facing
     * east, loaded with a diamond — its 12x12-pixel hanging box grown
     * 0.3 spans x [9.7, 10.3625], y [20.825, 22.175], and the arrow's
     * flight at x=10.19 from z=8.5 (y=21.3 at the launch, 21.15 at the
     * crossing) enters the band mid-window. ItemFrameEntity.takeDamage
     * lines 41-58: the non-explosive source with a display item EJECTS
     * the item — the frame STAYS on the wall, the display empties, the
     * diamond drops as an item entity, and the TRUE verdict consumes
     * the arrow at the frame (the bowhit riding).
     */
    @Test
    void theArrowPopsTheLoadedFrame() throws Exception {
        buildLaneWithWall();
        BlockPosition cell = new BlockPosition(LANE_X, 21, 12);
        server.ticker().submit(() -> server.decorations().restoreFrame(
                cell, 3, ItemStack.of(BuiltinItems.DIAMOND, 1), 0));
        await(() -> server.decorations().frameAt(cell) != null,
                "the loaded frame hung on the wall");
        ProjectileEntity arrow = launchArrowFrom(21.3, 8.5, 2.0);

        await(() -> server.projectiles().byId(arrow.entityId()) == null,
                "the arrow is consumed at the frame (the TRUE verdict)");
        var frame = server.decorations().frameAt(cell);
        assertNotNull(frame, "the frame STAYS on the wall (the eject arm alone)");
        assertTrue(frame.display().isEmpty(),
                "the display item is ejected (the pre-fix engine never consulted the"
                        + " frame at all)");
        await(() -> !server.itemEntities().all().isEmpty(),
                "the diamond drops as an item entity (the eject arm's own drop)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE BARE FRAME: the arrow's break (RED pre-fix: the
    // arrow flew through, the frame stood whole)
    // ------------------------------------------------------------------

    /**
     * The same wall cell, the frame BARE: the super arm
     * (DecorationEntity.takeDamage lines 129-141) breaks the frame
     * whole — the body leaves the wall, the frame item drops, the TRUE
     * verdict consumes the arrow.
     */
    @Test
    void theArrowBreaksTheBareFrame() throws Exception {
        buildLaneWithWall();
        BlockPosition cell = new BlockPosition(LANE_X, 21, 12);
        server.ticker().submit(() -> server.decorations().restoreFrame(
                cell, 3, null, 0));
        await(() -> server.decorations().frameAt(cell) != null,
                "the bare frame hung on the wall");
        ProjectileEntity arrow = launchArrowFrom(21.3, 8.5, 2.0);

        await(() -> server.decorations().frameAt(cell) == null,
                "the bare frame BREAKS under the arrow (the super arm)");
        await(() -> server.projectiles().byId(arrow.entityId()) == null,
                "the arrow is consumed at the frame");
        await(() -> !server.itemEntities().all().isEmpty(),
                "the frame item drops (the break arm's own drop)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE PAINTING: the arrow's break drop (RED pre-fix: the
    // arrow flew through, the painting stood whole)
    // ------------------------------------------------------------------

    /**
     * A Kebab painting (16x16 pixels) on the same wall cell: the
     * motive's own hanging span grown 0.3 spans y [20.7, 22.3] — the
     * same flight enters mid-window. DecorationEntity.takeDamage breaks
     * the body whole, PaintingEntity.onAttack drops the painting item,
     * and the TRUE verdict consumes the arrow.
     */
    @Test
    void theArrowBreaksThePainting() throws Exception {
        buildLaneWithWall();
        BlockPosition cell = new BlockPosition(LANE_X, 21, 12);
        server.ticker().submit(() -> server.decorations().restorePainting(
                cell, 3, net.zaminmc.torch.server.decoration.Painting.Motive.KEBAB));
        await(() -> server.decorations().paintingAt(cell) != null,
                "the painting hung on the wall");
        ProjectileEntity arrow = launchArrowFrom(21.3, 8.5, 2.0);

        await(() -> server.decorations().paintingAt(cell) == null,
                "the painting BREAKS under the arrow (the reference's break arm)");
        await(() -> server.projectiles().byId(arrow.entityId()) == null,
                "the arrow is consumed at the painting");
        await(() -> !server.itemEntities().all().isEmpty(),
                "the painting item drops (onAttack's own drop)");
    }

    // ------------------------------------------------------------------
    // Seat 6 — THE SPLASH POTION NEVER POPS THE FRAME (the reference's
    // own quirk: PotionEntity.onCollision calls no takeDamage — RED
    // pre-fix only in the through-flight: the potion flew through the
    // frame and shattered at the back wall)
    // ------------------------------------------------------------------

    /**
     * The loaded frame again, a SPLASH POTION thrown at it: the
     * reference's PotionEntity.onCollision (lines 57-106) never calls
     * takeDamage — the frame's eject ladder never runs, the display
     * STAYS whole — and the potion shatters at the frame's hull (the
     * remove unconditional). The potion's own gravity is lighter
     * (0.03): the same launch geometry crosses the band mid-window.
     */
    @Test
    void theSplashPotionNeverPopsTheFrame() throws Exception {
        buildLaneWithWall();
        BlockPosition cell = new BlockPosition(LANE_X, 21, 12);
        server.ticker().submit(() -> server.decorations().restoreFrame(
                cell, 3, ItemStack.of(BuiltinItems.DIAMOND, 1), 0));
        await(() -> server.decorations().frameAt(cell) != null,
                "the loaded frame hung on the wall");
        AtomicReference<ProjectileEntity> potion = new AtomicReference<>();
        server.ticker().submit(() -> potion.set(server.projectiles().launch(
                ProjectileEntity.Kind.POTION, -1,
                new Position(LANE_X + 0.19, 21.3, 8.5), 0.0, 0.0, 2.0, 0)));

        await(() -> server.projectiles().byId(
                        potion.get() != null ? potion.get().entityId() : -1) == null,
                "the potion shatters at the frame's hull (the unconditional remove)");
        var frame = server.decorations().frameAt(cell);
        assertNotNull(frame, "the frame STAYS on the wall (the splash never breaks it)");
        assertTrue(!frame.display().isEmpty(),
                "the display item STAYS whole (the potion never consults the wound"
                        + " — PotionEntity.onCollision calls no takeDamage)");
        assertTrue(server.itemEntities().all().isEmpty(),
                "no item dropped (the eject ladder never ran)");
    }

    // ------------------------------------------------------------------
    // Seat 7 — THE OTHER PROJECTILE: the arrow's bounce off a STUCK
    // arrow (RED pre-fix: the second arrow crossed the first whole and
    // stuck in the wall beside it)
    // ------------------------------------------------------------------

    /**
     * The projectile family's own census arm: an arrow stuck in the
     * back wall (its rest 0.05 before the face, the grown hull reaching
     * 0.35 back into the lane), and a second arrow fired along the same
     * line. Vanilla's walk finds the STUCK arrow's grown hull first —
     * the BASE Entity's refusal again — and the second arrow BOUNCES
     * off the first, settling on the lane floor BEFORE the wall: the
     * stuck arrow never sees a second stick beside it.
     */
    @Test
    void theArrowBouncesOffTheStuckArrow() throws Exception {
        buildLaneWithWall();
        // The first arrow, fired high enough to reach the BACK WALL
        // before the floor (y=22.5 at the launch, ~21.2 at the wall).
        ProjectileEntity first = launchArrowFrom(22.5, 4.5, 2.0);
        await(() -> {
            ProjectileEntity live = server.projectiles().byId(first.entityId());
            return live != null && live.inGround();
        }, "the first arrow sticks in the back wall");
        ProjectileEntity stuck = server.projectiles().byId(first.entityId());
        assertTrue(stuck.stickCellZ() == 18,
                "the first arrow's own rest is the wall (the rig's own sanity)");

        // The second arrow along the same line: it meets the first
        // arrow's grown hull ~0.35 before the wall face and bounces.
        ProjectileEntity second = launchArrowFrom(22.5, 4.5, 2.0);
        await(() -> {
            ProjectileEntity live = server.projectiles().byId(second.entityId());
            return live != null && live.inGround();
        }, "the second arrow settles (the bounce's own flight)");
        ProjectileEntity settled = server.projectiles().byId(second.entityId());
        assertTrue(settled.stickCellZ() < 18,
                "the second arrow NEVER reaches the wall (the first arrow's grown"
                        + " hull deflected it — observed z cell "
                        + settled.stickCellZ() + ", the pre-fix engine stuck it at 18)");
        assertTrue(settled.stickCellY() == 19,
                "the deflected arrow settles on the lane floor");
        // The first arrow's rest is untouched (the wound never landed
        // on it — the refusal consumes nothing).
        ProjectileEntity stillStuck = server.projectiles().byId(first.entityId());
        assertNotNull(stillStuck, "the first arrow still rests in the wall");
        assertTrue(stillStuck.inGround() && stillStuck.stickCellZ() == 18,
                "the first arrow's own rest never moved (the refused body's law)");
    }

    // ------------------------------------------------------------------
    // Seat 8 — THE PEARL'S REFUSED-BODY TELEPORT (EnderPearlEntity's
    // fall-through — RED pre-fix: the pearl flew through the fuse and
    // teleported the thrower at the back wall instead)
    // ------------------------------------------------------------------

    /**
     * A pearl thrown by the player down the lane at a primed TNT: the
     * pearl's hit on the REFUSED body still teleports the thrower
     * (EnderPearlEntity.onCollision lines 45-70 — the fall-through
     * whatever it struck, the owner alone the pass-through). The
     * teleport lands at the pearl's OWN pre-move origin on its final
     * tick — near the TNT (z&asymp;9-10), never at the back wall (the
     * pre-fix engine's through-flight, z&asymp;16-17).
     */
    @Test
    void thePearlTeleportsOffTheRefusedTnt() throws Exception {
        buildLaneWithWall();
        PlayerSession thrower = joinPlayer("PearlThrower", LANE_X + 0.5, 4.5);
        server.ticker().submit(() -> server.tnt().spawnPrimedAt(
                server.world(), LANE_X + 0.5, 20.0, 10.5));
        await(() -> !server.tnt().allPrimed().isEmpty(), "the TNT primed in the lane");

        AtomicReference<ProjectileEntity> pearl = new AtomicReference<>();
        server.ticker().submit(() -> pearl.set(server.projectiles().launch(
                ProjectileEntity.Kind.ENDER_PEARL, thrower.engineEntityId(),
                new Position(LANE_X + 0.5, 21.0, 4.5), 0.0, 0.0, 1.5, 0)));
        await(() -> pearl.get() != null, "the pearl launched down the lane");
        await(() -> server.projectiles().byId(pearl.get().entityId()) == null,
                "the pearl is consumed at the TNT's grown hull");
        await(() -> thrower.position().z() > 7.0 && thrower.position().z() < 13.0,
                "the thrower teleported to the pearl's origin NEAR THE TNT (the "
                        + "observed z " + thrower.position().z() + " — the pre-fix "
                        + "engine's through-flight landed them at the back wall, "
                        + "z 16+)");
        assertTrue(!server.tnt().allPrimed().isEmpty(),
                "the TNT's fuse keeps burning (the pearl's 0.0 bruise never lands)");
    }

    // ------------------------------------------------------------------
    // Seat 9 — THE EGG'S BLOCK-ARM HATCH (Wave PE-20's parity repair —
    // RED pre-fix: the ground never hatched anything)
    // ------------------------------------------------------------------

    /**
     * The reference's EggEntity.onCollision (lines 33-45) fires the
     * chick roll on ANY collision — the block arm included, the entity
     * consult null under it. The pre-fix engine hatched only on
     * BODIES: an egg thrown at the ground NEVER hatched. Sixty eggs
     * down the lane: the 1-in-8 gate lands better than 5 chicks with
     * probability 1 &minus; (7/8)^60 &asymp; 99.96% — the pre-fix
     * engine's zero is the discriminator.
     */
    @Test
    void theBlockArmHatchesTheChicks() throws Exception {
        buildLaneWithWall();
        server.ticker().submit(() -> {
            for (int i = 0; i < 60; i++) {
                server.projectiles().launch(ProjectileEntity.Kind.EGG, -1,
                        new Position(LANE_X + 0.5, 21.0, 4.5), 0.0, 0.0, 1.5, 0);
            }
        });
        EngineAwaits.awaitCondition(server.world(), () ->
                        server.mobs().all().stream().anyMatch(mob ->
                                mob.type() == net.zaminmc.torch.server.entity.MobType.CHICKEN
                                        && mob.position().z() > 4.0 && mob.position().z() < 18.0),
                600, "at least one egg hatches a chick on the lane floor (the 1-in-8"
                        + " gate over sixty eggs — the pre-fix engine never hatched"
                        + " on the ground)");
    }

    /** Joins a survival player on the lane. */
    private PlayerSession joinPlayer(String name, double x, double z)
            throws InterruptedException {
        var accepted = server.joinRequest(new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, java.util.UUID.randomUUID());
        PlayerSession session =
                ((net.zaminmc.torch.server.net.EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        server.teleportPlayer(session, new Position(x, 20.0, z));
        await(() -> Math.abs(session.position().z() - z) < 0.01,
                name + " standing at the lane's south end");
        return session;
    }
}
