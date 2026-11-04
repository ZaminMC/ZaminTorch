package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.armorstand.ArmorStand;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-19 — THE PROJECTILE FAMILY'S GROWN-0.3 RAY CONSULT (the
 * entity scan's exact segment form): the reference's ONE {@code
 * getEntities} walk (ArrowEntity.tick lines 187-229, the convention
 * every projectile family rides) — the block ray over the WHOLE step,
 * the entity ray CLIPPED at the block face, every {@code hasCollision}
 * body's OWN box grown 0.3 ({@code getShape().grown(0.3F)}), the
 * {@code Box.clip} slab method, and the CLOSEST intercept by squared
 * distance winning. The pre-fix engine approximated the whole walk
 * with midpoint/endpoint POINT samples against the UNGROWN boxes in a
 * fixed family order — three divergences this seat pins:
 *
 * <ul>
 *   <li><b>THE HULL-ADJACENT GRAZE BAND</b> — a shot passing within
 *       0.3 of a body's hull side (outside the ungrown box, inside the
 *       grown one) LANDS in vanilla, missed whole by the point samples.
 *       The mob side and the stand side both (the PE-18 bounce's own
 *       razor-thin band — the pin's named case).</li>
 *   <li><b>THE CROSS-FAMILY CLOSEST</b> — the reference's ONE walk
 *       picks the closest grown-hull intercept ACROSS every family;
 *       the pre-fix fixed order (mob, player, vehicle, stand) let a
 *       farther mob shadow a nearer stand: a stand stood in front of a
 *       cow, the arrow's endpoint sample landed past the stand inside
 *       the cow — the pre-fix engine wounded the COW. Vanilla's walk
 *       finds the STAND'S grown hull first (and a segment STARTING
 *       inside a grown hull crosses the EXIT face — the reference's
 *       own origin-inside law, Box.clip's exit-point answer): the
 *       stand one-shots, the cow never sees the arrow.</li>
 *   <li><b>THE THIN-WALL CLIP</b> — the reference rayTraces the block
 *       FIRST and clips the entity ray at the hit face ({@code vec3d1
 *       = hitresult.facePos}): a body behind a wall is never struck,
 *       however near the column. The pre-fix samples consulted the
 *       bodies at the endpoint with no block knowledge — a full-speed
 *       arrow crossing a one-block wall with a cow behind it wounded
 *       the COW. Vanilla sticks the arrow IN the wall.</li>
 * </ul>
 *
 * <p>THE RIG: a stone lane (the top face at y=20) running along +Z at
 * x=10, the shots riding the REAL launch wire (the gaussian spread's
 * drift under 0.05 blocks over the lane — every graze offset keeps
 * better than 0.1 of slack against its band edge), a stone back wall
 * at z=18 catching every pre-fix fly-through.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectileRayScanParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The lane's fixed column. */
    private static final int LANE_X = 10;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "rayscan" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * The lane: stone at y=19 (the top face y=20) from z=2 to z=20 at
     * x 8..12, open air above through y=24, and the back wall at z=18
     * (cells z=18, y 20..24) catching every pre-fix fly-through.
     */
    private void buildLane() throws InterruptedException {
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
        });
        BlockPosition laneFloor = new BlockPosition(LANE_X, 19, 10);
        BlockPosition wallTop = new BlockPosition(LANE_X, 24, 18);
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(laneFloor))
                        && BuiltinBlocks.STONE.equals(world.getBlock(wallTop)),
                "the lane and the back wall stand");
    }

    /** Summons the stand at the given lane mark, on the lane's center x. */
    private ArmorStand summonStandAt(double z) throws InterruptedException {
        AtomicReference<ArmorStand> stand = new AtomicReference<>();
        server.ticker().submit(() -> stand.set(server.armorStands().summonAt(
                LANE_X + 0.5, 20.0, z, 0, null)));
        await(() -> stand.get() != null, "the stand summoned on the lane");
        return stand.get();
    }

    /** Spawns the cow at the given lane mark, on the lane's center x. */
    private MobEntity spawnCowAt(double x, double z) throws InterruptedException {
        AtomicReference<MobEntity> cow = new AtomicReference<>();
        server.ticker().submit(() -> cow.set(server.mobs().spawnAt(
                MobType.COW, new Position(x, 20.0, z))));
        await(() -> cow.get() != null, "the cow spawned on the lane");
        return cow.get();
    }

    /**
     * Launches an arrow north along the lane (+Z, yaw 0) at the given
     * x offset and start z, at the bodies' mid-height (y 21).
     */
    private ProjectileEntity launchArrowAt(double x, double startZ, double speed)
            throws InterruptedException {
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1, new Position(x, 21.0, startZ),
                0.0, 0.0, speed, 0)));
        await(() -> launched.get() != null, "the arrow launched along the lane");
        return launched.get();
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE MOB-SIDE GRAZE: the grown band lands the hit the
    // point samples never saw (RED pre-fix: the cow unwounded, the
    // arrow stuck in the back wall)
    // ------------------------------------------------------------------

    /**
     * A cow's hull is 0.9 wide (x &plusmn;0.45); the arrow flies at
     * x + 0.55 — 0.1 OUTSIDE the ungrown box, 0.2 INSIDE the grown one
     * (the grown x band [x-0.75, x+0.75]). Vanilla's clip finds the
     * crossing; the pre-fix midpoint/endpoint samples (both at the same
     * x) never enter the ungrown box. The cow takes the wound, the
     * arrow is consumed at it.
     */
    @Test
    void theMobSideGrazeBandLandsTheWound() throws Exception {
        buildLane();
        // The cow's hull spans x [9.5, 10.4] — the arrow's lane at x
        // 10.5 rides the graze band (10.4, 10.7).
        MobEntity cow = spawnCowAt(9.95, 10.5);
        ProjectileEntity arrow = launchArrowAt(LANE_X + 0.5, 4.5, 2.0);

        await(() -> cow.health() < cow.maxHealth(),
                "the graze-band arrow wounds the cow (the grown hull's clip)");
        await(() -> server.projectiles().byId(arrow.entityId()) == null,
                "the arrow is consumed at the cow (never through to the wall)");
        assertFalse(cow.dead(), "the single graze arrow never kills the cow");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE STAND-SIDE GRAZE (the PE-18 bounce's own band): the
    // grown hull one-shots from OUTSIDE the ungrown hull
    // (RED pre-fix: the stand stands, the arrow sticks in the wall)
    // ------------------------------------------------------------------

    /**
     * The normal stand's hull is 0.5 wide (x &plusmn;0.25); the arrow
     * flies at the stand's x + 0.4 — 0.15 outside the ungrown hull,
     * 0.15 inside the grown one ([x-0.55, x+0.55]). Vanilla's window
     * consult lands the one-shot from the graze band; the pre-fix
     * samples never entered the ungrown hull — the arrow flew through
     * whole and stuck in the back wall.
     */
    @Test
    void theStandSideGrazeBandOneShots() throws Exception {
        buildLane();
        ArmorStand stand = summonStandAt(10.5);
        // The stand's hull spans x [10.25, 10.75] — the arrow's lane at
        // x 10.9 rides the graze band (10.75, 11.05).
        ProjectileEntity arrow = launchArrowAt(LANE_X + 0.9, 4.5, 2.0);

        await(() -> server.armorStands().byId(stand.entityId()) == null,
                "the graze-band arrow one-shots the stand (the grown hull's clip)");
        await(() -> server.projectiles().byId(arrow.entityId()) == null,
                "the arrow is consumed inside the wound walk (line 408)");
        assertFalse(arrow.inGround(),
                "the one-shot's arrow never sticks anywhere (the pre-fix wall stick)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE CROSS-FAMILY CLOSEST: the stand's grown hull wins
    // over the farther cow (RED pre-fix: the endpoint sample inside the
    // cow wounded THE COW, the stand stood whole)
    // ------------------------------------------------------------------

    /**
     * The stand at z=10.1, the cow at z=12.5 behind it. The arrow's
     * third tick flies [8.5, 10.5]: the STAND'S grown hull ([9.55,
     * 10.65]) crosses mid-segment — the closest intercept — and the
     * one-shot consumes the arrow at the stand. The pre-fix samples
     * (9.5, 10.5) both fell outside the stand's UNGROWN hull ([9.85,
     * 10.35]); the FOURTH tick's endpoint (12.5) landed inside the
     * COW's box — the fixed family order wounded the cow.
     */
    @Test
    void theClosestGrownHullWinsAcrossFamilies() throws Exception {
        buildLane();
        ArmorStand stand = summonStandAt(10.1);
        MobEntity cow = spawnCowAt(LANE_X + 0.5, 12.5);
        ProjectileEntity arrow = launchArrowAt(LANE_X + 0.5, 4.5, 2.0);

        await(() -> server.armorStands().byId(stand.entityId()) == null,
                "the stand's grown hull — the closest intercept — takes the one-shot");
        await(() -> server.projectiles().byId(arrow.entityId()) == null,
                "the arrow is consumed at the stand (never reaches the cow)");
        assertTrue(cow.health() >= cow.maxHealth(),
                "the cow behind the stand never sees the arrow (the pre-fix"
                        + " fixed-order scan wounded it — observed health "
                        + cow.health() + " of " + cow.maxHealth() + ")");
        assertFalse(cow.dead(), "the cow lives");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE THIN-WALL CLIP: the entity ray ends at the block
    // face (RED pre-fix: the endpoint sample past the wall wounded the
    // cow behind it)
    // ------------------------------------------------------------------

    /**
     * A one-block stone wall at z=10 (cells y 20..24), a cow at z=11.2
     * behind it. The arrow starts at z=8.8 at speed 2.0: its first tick
     * crosses the wall's face (z=10.0) WHOLE and ends at z~10.78 —
     * inside the cow's ungrown box ([10.75, 11.65]). Vanilla clips the
     * entity ray at the wall face: the cow's grown hull ([10.45,
     * 11.95]) never sees the ray, and the arrow STICKS in the wall
     * (the rest 0.05 before the face). The pre-fix engine's endpoint
     * sample had no block knowledge — the entity consult wounded the
     * cow.
     */
    @Test
    void theBlockFaceClipsTheEntityRayBehindAWall() throws Exception {
        buildLane();
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            for (int x = 8; x <= 12; x++) {
                for (int y = 20; y <= 24; y++) {
                    world.setBlock(new BlockPosition(x, y, 10), BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(
                        new BlockPosition(LANE_X, 21, 10))),
                "the thin wall stands");
        MobEntity cow = spawnCowAt(LANE_X + 0.5, 11.2);
        ProjectileEntity arrow = launchArrowAt(LANE_X + 0.5, 8.8, 2.0);

        await(() -> {
            ProjectileEntity live = server.projectiles().byId(arrow.entityId());
            return live != null && live.inGround();
        }, "the arrow sticks IN the wall (the block ray's own face)");
        ProjectileEntity stuck = server.projectiles().byId(arrow.entityId());
        assertNotNull(stuck, "the stuck arrow stays in the world (its minute-long rest)");
        assertTrue(stuck.position().z() < 10.0,
                "the rest lies BEFORE the wall's face (the 0.05 backoff — observed z "
                        + stuck.position().z() + ")");
        assertTrue(stuck.stickCellZ() == 10,
                "the tracked cell is the wall's own cell (the hitresult cell)");
        assertTrue(cow.health() >= cow.maxHealth(),
                "the cow behind the wall is never struck (the clipped entity ray"
                        + " — observed health " + cow.health() + " of "
                        + cow.maxHealth() + ")");
    }
}
