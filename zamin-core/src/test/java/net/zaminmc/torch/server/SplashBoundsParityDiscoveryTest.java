package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-5 — the splash potion's box law (the Phase E boundary arms
 * over the potion family): the reference gathers through
 * {@code getEntitiesOfType(LivingEntity.class, this.getShape().grown(4.0,
 * 2.0, 4.0))} (PotionEntity.onCollision lines 74-77) — each body's OWN
 * box against the query box, STRICT both sides (exactly touching never
 * intersecting), the y band the body's OWN [pos.y, pos.y + height]
 * against [minY, maxY].
 *
 * <p>The pre-fix engine grew the query box around each family's CENTER
 * with inclusive edges — the mob walk hardcoded a 0.6x1.8 hull over the
 * type's own box, and the y band read the FEET POSITION against
 * [minY, maxY + height]: a body standing BELOW the band whose head
 * reaches in (the pit ledge) was never splashed, and a body standing
 * ABOVE the band top was splashed through — both wrong in vanilla's own
 * law.</p>
 *
 * <p>The seat drives the REAL chain whole: a restored splash potion of
 * harming dropped over the impact cell (the deterministic no-gaussian
 * spawn), the flight, the block impact, and the engine's own splash
 * walk — the wound the observable (the magic 6-proximity arm).</p>
 */
class SplashBoundsParityDiscoveryTest {

    /** The splash potion of harming I (metadata 8204 | the splash bit). */
    private static final int HARMING_SPLASH = 24588;

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "splash" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE DISCRIMINATOR: the player's head-reach from the pit
    // below the band
    // ------------------------------------------------------------------

    /**
     * A player standing in a three-deep pit beside the impact cell —
     * feet at 37, the head at 38.8 INSIDE the band whose floor rides
     * the impact [iy-2.125, ...] ≈ [38.0, ...] — takes the splash in
     * vanilla (the own box [37, 38.8] overlapping the query box), the
     * proximity-scaled harming wound landing. The pre-fix consult read
     * the FEET position against [minY - 0.2, ...]: the feet at 37
     * never passed — the head-reach body was NEVER splashed. RED
     * pre-fix.
     */
    @Test
    void theHeadReachPlayerInThePitTakesTheSplash() throws Exception {
        int x = 20, z = 10, fy = 40;
        carveSplashRigWithPit(x, fy, z, true);
        PlayerSession body = joined("PitBody");
        server.teleportPlayer(body, new Position(x + 1.5, fy - 3, z + 0.5));
        EngineAwaits.awaitTicks(server.world(), 2, "the body settles in the pit");
        splashHarmingAt(x, z, fy + 3);
        await(() -> body.health() < 20.0f,
                "the head-reach player in the pit takes the splash (the own "
                        + "box's head inside the band)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the mob's head-reach (the same law over the type's own
    // box)
    // ------------------------------------------------------------------

    /**
     * A cow (a LIVING body — the harming wound lands on it, the undead
     * inversion never confounds: harming HEALS the undead, vanilla's
     * own law) in the west pit, feet at 37, the head at 38.3 inside
     * the band — the harming wound lands over the type's own
     * 0.9x1.3 box. Pre-fix the feet band never passed: RED pre-fix.
     * The roof over the pit kills the daylight-burn consult's own
     * confound (the crush seat's lesson — no undead in this seat, but
     * the roof keeps the rig honest for any future mob swap).
     */
    @Test
    void theHeadReachCowInThePitTakesTheSplash() throws Exception {
        int x = 50, z = 10, fy = 40;
        carveSplashRigWithPit(x, fy, z, false);
        // The west pit's own roof (the undead burn confound — the crush
        // seat's own lesson: the daylight burn consult reads sky access,
        // a burning zombie's health walk would forge the wound
        // assertion; the roof sits above the band, never in the
        // potion's column).
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= -1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    server.world().setBlock(new BlockPosition(x + dx, fy + 2, z + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(), () -> BuiltinBlocks.STONE.equals(
                        server.world().getBlock(new BlockPosition(x - 1, fy + 2, z))),
                40, "the west pit's roof placed");
        // The west pit itself: the floor removed down to fy-4, the pit
        // floor at fy-4 — the zombie's feet at fy-3, the head at fy-1.2.
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= -1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = fy - 1; y >= fy - 4; y--) {
                        server.world().setBlock(new BlockPosition(x + dx, y, z + dz),
                                server.world().airType());
                    }
                    server.world().setBlock(new BlockPosition(x + dx, fy - 4, z + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(), () -> BuiltinBlocks.STONE.equals(
                        server.world().getBlock(new BlockPosition(x - 1, fy - 4, z))),
                40, "the west pit carved");
        AtomicReference<MobEntity> body = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity spawned = server.mobs().spawnAt(
                    net.zaminmc.torch.server.entity.MobType.COW,
                    new Position(x - 1.5, fy - 3, z + 0.5), 0);
            body.set(spawned);
        });
        await(() -> body.get() != null, "the cow spawned in the pit");
        float before = body.get().health();
        splashHarmingAt(x, z, fy + 3);
        await(() -> body.get().health() < before,
                "the head-reach cow in the pit takes the splash (the "
                        + "type's own box's head inside the band)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE NEVER-WINDOW: the body above the band top never
    // hears the splash
    // ------------------------------------------------------------------

    /**
     * A cow standing on the tower beside the impact — feet at 43,
     * the whole box [43, 44.3] ABOVE the band's top [.., iy+2.125] ≈
     * [.., 42.25] — never takes the splash in vanilla (the own box
     * never intersects the query box). The pre-fix y band read the
     * FEET position against {@code at.y <= maxY + 1.8} (the hardcoded
     * player hull over every mob's own box): the feet at 43 passed
     * whole and the wound landed — RED pre-fix (the seat asserts the
     * NEVER window: the health untouched; a LIVING body again — the
     * undead inversion never confounds the wound arm).
     */
    @Test
    void theBodyAboveTheBandTopNeverTakesTheSplash() throws Exception {
        int x = 80, z = 10, fy = 40;
        carveSplashRigWithPit(x, fy, z, false);
        // The tower: one block at fy+2, the cow on top at feet fy+3.
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(x + 2, fy + 2, z), BuiltinBlocks.STONE));
        EngineAwaits.awaitCondition(server.world(), () -> BuiltinBlocks.STONE.equals(
                        server.world().getBlock(new BlockPosition(x + 2, fy + 2, z))),
                40, "the tower placed");
        AtomicReference<MobEntity> body = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity spawned = server.mobs().spawnAt(
                    net.zaminmc.torch.server.entity.MobType.COW,
                    new Position(x + 2.5, fy + 3, z + 0.5), 0);
            body.set(spawned);
        });
        await(() -> body.get() != null, "the cow spawned on the tower");
        float before = body.get().health();
        splashHarmingAt(x, z, fy + 3);
        // The 2.5s never-window: the flight completes well inside it.
        long deadline = System.currentTimeMillis() + 2500;
        while (System.currentTimeMillis() < deadline) {
            assertEquals(before, body.get().health(),
                    "the body above the band top never takes the splash (the "
                            + "own box never intersects the query box)");
            Thread.sleep(50);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * The rig: the impact floor at fy (the potion breaks on its face),
     * clean air above through fy+6 — and, when {@code withPit}, a
     * three-deep pit beside the impact cell (the head-reach bodies
     * stand in it, feet at fy-3, their heads at fy-1.2 inside the band
     * whose floor rides [fy-2.125+0.125, ...]).
     */
    private void carveSplashRigWithPit(int x, int fy, int z, boolean withPit)
            throws InterruptedException {
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.setBlock(new BlockPosition(x + dx, fy - 1, z + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy; y <= fy + 6; y++) {
                        world.setBlock(new BlockPosition(x + dx, y, z + dz),
                                world.airType());
                    }
                }
            }
            if (withPit) {
                // The two pits (east + west of the impact column): the
                // floor removed down to fy-4, the pit floor at fy-4 —
                // the bodies' feet at fy-3, the heads at fy-1.2.
                for (int dx : new int[] {1, 2, -1, -2}) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int y = fy - 1; y >= fy - 4; y--) {
                            world.setBlock(new BlockPosition(x + dx, y, z + dz),
                                    world.airType());
                        }
                        world.setBlock(new BlockPosition(x + dx, fy - 4, z + dz),
                                BuiltinBlocks.STONE);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(world,
                () -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(x, fy - 1, z))),
                40, "the splash rig carved");
    }

    /**
     * The deterministic harming splash: a restored potion (the
     * no-gaussian spawn form) dropped over the impact column from
     * three above the floor — the flight, the block impact and the
     * engine's own splash walk, the real chain whole.
     */
    private void splashHarmingAt(int x, int z, int dropFrom) {
        server.ticker().submit(() -> {
            ProjectileEntity potion = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.POTION,
                    new Position(x + 0.5, dropFrom, z + 0.5),
                    0.0, -0.3, 0.0);
            if (potion != null) {
                potion.setPotionDamage(HARMING_SPLASH);
            }
        });
    }

    private PlayerSession joined(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
