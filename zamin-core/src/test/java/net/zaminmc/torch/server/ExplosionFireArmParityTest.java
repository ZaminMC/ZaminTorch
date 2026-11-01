package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockLightTable;
import net.zaminmc.torch.server.block.FireBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blast's fire arm (the nineteenth pass's F-EX4 — the reference's
 * {@code Explosion.damageBlocks} fire walk, lines 191-199): the arm rides
 * the Explosion's own {@code createFire} FIELD (never the method's particle
 * parameter — {@code World.explode} line 1597's six-arg form delegates
 * {@code false} and calls {@code damageBlocks(true)}), runs AFTER the
 * destructive clear (the cells it just cleared are the candidates), and
 * answers THREE gates per damaged cell: the cell holds AIR now, the cell
 * BELOW is an opaque cube ({@code Block.isOpaque} — the construction-time
 * {@code opaqueCube = isSolidRender()}, Block.java line 215), and the
 * Explosion's OWN private {@code new Random()} (line 30 — never the world
 * stream) rolls {@code nextInt(3) == 0}. The only vanilla fire sites: the
 * ghast fireball ({@code FireballEntity.onHit} line 33 — the flag rides
 * mobGriefing for BOTH createFire and destructive) and the wrong-world bed
 * ({@code BedBlock.use} line 78 — power 5, fire always; the bed family's
 * own missing blast arm is the separate F-EX6 row).
 *
 * <p>The engine's walk carried NO fire arm at all — its stand-in was the
 * fireball hit paths' single {@code fireSink.tryPlaceFire} at the impact
 * cell (ungated by mobGriefing, unfloored, one cell at most per hit). These
 * seats pin the restored arm's own laws:</p>
 *
 * <ul>
 *   <li><b>The crater fire</b>: ghast fireballs into a grass plain light
 *       SEVERAL fires — the 1-in-3 roll over every damaged AIR cell with an
 *       opaque floor (the pre-fix stand-in answers at most one per blast,
 *       at the blast's own cleared center cell — the only cell its air gate
 *       can ever accept on the ground path).</li>
 *   <li><b>The struck-body blast</b>: a fireball into a ghast lights the
 *       crater around the struck body — the arm rides the EXPLOSION, not
 *       any single-site stand-in (the pre-fix engine could answer at most
 *       one fire whole).</li>
 *   <li><b>The flag gate</b>: mobGriefing off means NO fire — the arm rides
 *       the flag exactly like the destructive walk.</li>
 *   <li><b>The floor law</b>: fire never sits on glass — the below-cell
 *       consult answers the reference's own isOpaque (glass is translucent;
 *       the crater dug UNDER the glass layer may still light over dirt,
 *       exactly the reference's own shape).</li>
 * </ul>
 *
 * <p>Harness notes: the fire census walks the fire FAMILY (FireBlocks.isFire
 * — the flame's own scheduled tick ages the identifier within seconds, the
 * age-0 constant alone would undercount); the struck-body seats stand a
 * dirt wall behind the ghast so the reference's own 0.4-sigma aim jitter
 * can never carry a shot past every resolution surface (a miss resolves on
 * the wall, and the wall's own dirt crater lights just the same); the
 * floating-flame law is the reference's own (FireBlock.onAdded lines
 * 322-324 remove a flame with neither solid top nor fuel on the spot), so
 * the seats count only floor-sitting flames by construction.</p>
 */
class ExplosionFireArmParityTest {

    @TempDir
    Path dataDir;

    /**
     * SEAT 1 — the crater fire: three ghast fireballs into a grass plain
     * (mobGriefing on) light more fires than the one-per-blast stand-in law
     * could ever answer, and every fire sits on an opaque floor.
     */
    @Test
    void theBlastLightsFireOverTheCrater() throws Exception {
        EngineServer server = boot();
        try {
            // Three separate impact zones, ten blocks apart: the craters
            // never overlap and each blast's fire candidates are its own.
            int first = launchGhastShotDown(server, new Position(12.0, 8.0, 10.0));
            int second = launchGhastShotDown(server, new Position(22.0, 8.0, 16.0));
            int third = launchGhastShotDown(server, new Position(30.0, 8.0, 8.0));
            await(server, () -> server.projectiles().all().stream()
                            .noneMatch(p -> p.entityId() == first || p.entityId() == second
                                    || p.entityId() == third),
                    "all three fireballs resolved");
            // The fire census over the whole test band. The pre-fix engine's
            // stand-in answers AT MOST one fire per blast (the cleared center
            // cell — the only cell the air-gated sink can ever accept on the
            // ground path); three blasts can never reach four.
            List<BlockPosition> fires = firesIn(server, 4, 0, 38, 12, 0, 22);
            assertTrue(fires.size() >= 4,
                    "the 1-in-3 roll over the craters' air cells lights several fires "
                            + "(got " + fires.size() + " — the stand-in law caps at three)");
            // The floor law: every fire sits on an opaque cube (the natural
            // plain answers grass/dirt — both filter 15).
            for (BlockPosition fire : fires) {
                assertTrue(fire.y() > 0, "the fire cell has a below cell");
                assertTrue(BlockLightTable.filter(server.world()
                                .getBlock(fire.offset(0, -1, 0))) == 15,
                        "the fire at " + fire + " sits on an opaque floor");
            }
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 2 — the struck-body blast: a fireball into a ghast lights the
     * crater around the struck body (mobGriefing on). The pre-fix engine's
     * mob-hit stand-in answers at most ONE fire whole; the arm's roll over
     * the blast's own candidates answers several.
     *
     * <p>THE ROLL IS A LOTTERY (run 565's member): the reference's own
     * 1-in-3 per eligible cell means a single blast's fire count is
     * Binomial(eligible, 1/3) — the old single-shot seat's {@code >= 2}
     * assertion rode that tail (one unlucky detonation answered exactly
     * one). The seat now fires FOUR independent blasts, each its own site
     * (own ghast, own wall, own census region), and requires ANY ONE blast
     * to answer several fires — the discrimination is unchanged (the
     * pre-fix stand-in can NEVER answer two in one blast) while the tail
     * compounds to a practical zero.</p>
     */
    @Test
    void theStruckBodyBlastLightsTheCrater() throws Exception {
        EngineServer server = boot();
        try {
            placeDirtWall(server);
            // Four sites, twenty blocks apart — the craters and the census
            // regions never overlap.
            int[] shots = new int[4];
            for (int site = 0; site < 4; site++) {
                double ox = site * 20.0;
                var ghast = server.mobs().spawnAt(MobType.GHAST,
                        new Position(12.5 + ox, 5.0, 12.5));
                assertNotNull(ghast, "the ghast spawned at site " + site);
                // The shot from 1.5 blocks off the box's west face at torso
                // height: the reference's own 0.4-sigma aim jitter cannot
                // carry it past a 4-block box over 1.5 blocks of flight —
                // and a rare miss resolves on the wall behind, whose own
                // dirt crater lights just the same.
                shots[site] = launchGhastShotAt(server,
                        new Position(9.0 + ox, 6.5, 12.5), 1.0, 0.0, 0.0);
            }
            await(server, () -> {
                for (int id : shots) {
                    final int fid = id;
                    if (server.projectiles().all().stream()
                            .anyMatch(p -> p.entityId() == fid)) {
                        return false;
                    }
                }
                return true;
            }, "all four fireballs resolved");
            int bestSite = -1;
            int bestFires = 0;
            for (int site = 0; site < 4; site++) {
                int ox = site * 20;
                List<BlockPosition> fires = firesIn(server,
                        4 + ox, 0, 24 + ox, 12, 4, 22);
                if (fires.size() > bestFires) {
                    bestFires = fires.size();
                    bestSite = site;
                }
                for (BlockPosition fire : fires) {
                    assertTrue(BlockLightTable.filter(server.world()
                                    .getBlock(fire.offset(0, -1, 0))) == 15,
                            "the fire at " + fire + " sits on an opaque floor");
                }
            }
            assertTrue(bestFires >= 2,
                    "some blast at the struck body lights several fires (best site "
                            + bestSite + " answered " + bestFires
                            + " — the stand-in law answers at most one per blast)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 3 — the flag gate: mobGriefing off refuses the fire whole. The
     * blast breaks nothing and lights nothing — the fire rides mobGriefing
     * exactly like the destruction does.
     */
    @Test
    void noFireWhenTheGriefingFlagRefuses() throws Exception {
        EngineServer server = boot();
        try {
            server.ticker().submit(() -> server.gameRules().set("mobGriefing", "false"));
            await(server, () -> "false".equals(server.gameRules().get("mobGriefing")),
                    "mobGriefing off");
            placeDirtWall(server);
            var ghast = server.mobs().spawnAt(MobType.GHAST, new Position(12.5, 5.0, 12.5));
            assertNotNull(ghast, "the ghast spawned");
            // Two shots at the struck-body path — the one path the pre-fix
            // stand-in served without any flag consult.
            int first = launchGhastShotAt(server, new Position(9.0, 6.5, 12.5), 1.0, 0.0, 0.0);
            int second = launchGhastShotAt(server, new Position(9.0, 5.5, 12.5), 1.0, 0.0, 0.0);
            await(server, () -> server.projectiles().all().stream()
                            .noneMatch(p -> p.entityId() == first || p.entityId() == second),
                    "both fireballs resolved");
            List<BlockPosition> fires = firesIn(server, 4, 0, 24, 12, 4, 22);
            assertEquals(0, fires.size(),
                    "no fire anywhere while mobGriefing refuses (got " + fires + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 4 — the floor law: fire never sits on glass. The plain's top
     * layer is swapped for glass over a wide band; the blast digs through
     * the cheap glass into the dirt and may light the crater over the DIRT
     * floors, but no fire may ever answer a glass below-cell (glass is
     * translucent — never an opaque cube).
     */
    @Test
    void theFireNeverSitsOnGlass() throws Exception {
        EngineServer server = boot();
        try {
            // The glass band: the whole top layer (y=4) over the blast area.
            server.ticker().submit(() -> {
                for (int x = 8; x <= 36; x++) {
                    for (int z = 4; z <= 20; z++) {
                        server.world().setBlock(new BlockPosition(x, 4, z), BuiltinBlocks.GLASS);
                    }
                }
            });
            await(server, () -> server.world().getBlock(new BlockPosition(12, 4, 10))
                            .equals(BuiltinBlocks.GLASS),
                    "the glass band placed");
            int first = launchGhastShotDown(server, new Position(14.0, 8.0, 10.0));
            int second = launchGhastShotDown(server, new Position(28.0, 8.0, 16.0));
            await(server, () -> server.projectiles().all().stream()
                            .noneMatch(p -> p.entityId() == first || p.entityId() == second),
                    "both fireballs resolved");
            // The floor consult: every fire's below cell is an opaque cube —
            // glass answers filter 0 and never carries fire. (The crater dug
            // under the glass layer lights over dirt legitimately — the
            // reference's own shape; only the glass-surface band is refused.)
            List<BlockPosition> fires = firesIn(server, 4, 0, 38, 12, 0, 22);
            for (BlockPosition fire : fires) {
                assertTrue(fire.y() > 0, "the fire cell has a below cell");
                assertTrue(BlockLightTable.filter(server.world()
                                .getBlock(fire.offset(0, -1, 0))) == 15,
                        "the fire at " + fire + " sits on an opaque floor "
                                + "(glass is translucent — the floor gate refused it)");
            }
        } finally {
            server.shutdown(() -> { });
        }
    }

    // --- the harness -------------------------------------------------------

    /**
     * The dirt wall behind each site's ghast (x=16+ox, y=4..12, z=6..20):
     * the aim jitter's escape surface. A shot that misses the ghast
     * resolves on the wall — and the wall's own cheap dirt answers the
     * blast with a crater and fire candidates just like the plain, so both
     * resolution paths pin the same law. One wall per struck-body site
     * (seat 2's four sites, twenty blocks apart).
     */
    private void placeDirtWall(EngineServer server) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int site = 0; site < 4; site++) {
                int wx = 16 + site * 20;
                for (int y = 4; y <= 12; y++) {
                    for (int z = 6; z <= 20; z++) {
                        server.world().setBlock(new BlockPosition(wx, y, z), BuiltinBlocks.DIRT);
                    }
                }
            }
        });
        await(server, () -> server.world().getBlock(new BlockPosition(16, 8, 13))
                        .equals(BuiltinBlocks.DIRT)
                        && server.world().getBlock(new BlockPosition(76, 8, 13))
                        .equals(BuiltinBlocks.DIRT),
                "the dirt walls placed");
    }

    /** One ghast fireball straight down (the ground-impact seat's shot). */
    private int launchGhastShotDown(EngineServer server, Position origin)
            throws InterruptedException {
        return launchGhastShotAt(server, origin, 0.0, -1.0, 0.0);
    }

    /** One ghast fireball along an explicit direction. */
    private int launchGhastShotAt(EngineServer server, Position origin,
                                  double dx, double dy, double dz)
            throws InterruptedException {
        AtomicReference<Integer> id = new AtomicReference<>();
        server.ticker().submit(() -> {
            ProjectileEntity shot = server.projectiles().launchGhastFireball(
                    777, origin, dx, dy, dz, 0);
            if (shot != null) {
                id.set(shot.entityId());
            }
        });
        await(server, () -> id.get() != null, "the fireball launched");
        return id.get();
    }

    /**
     * The fire census over one box (read on the tick thread). The census
     * walks the fire FAMILY — the flame's own scheduled tick ages the
     * identifier within seconds, so the age-0 constant alone would
     * undercount a slow read.
     */
    private List<BlockPosition> firesIn(EngineServer server,
                                        int minX, int minY, int maxX,
                                        int maxY, int minZ, int maxZ)
            throws InterruptedException {
        List<BlockPosition> found = new ArrayList<>();
        server.ticker().submit(() -> {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (FireBlocks.isFire(server.world()
                                .getBlock(new BlockPosition(x, y, z)).identifier())) {
                            found.add(new BlockPosition(x, y, z));
                        }
                    }
                }
            }
        });
        await(server, () -> true, "the census read");
        return found;
    }

    private void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "firearm" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
