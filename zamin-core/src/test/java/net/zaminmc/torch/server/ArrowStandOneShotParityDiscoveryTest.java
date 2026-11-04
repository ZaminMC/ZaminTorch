package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.armorstand.ArmorStand;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
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
 * Wave PE-18 — THE PROJECTILE SCAN'S STAND CENSUS (the compat ledger's
 * "ARROW ONE-SHOT" row, VANILLA_1_8_8_COMPATIBILITY.md's armor-stand
 * family tail): the reference's entity scan walks EVERY {@code
 * hasCollision} body, and {@code ArmorStandEntity.hasCollision} (line
 * 727) answers true for the non-marker stand — so every projectile
 * family consults the stand's hull, and the stand's own {@code
 * takeDamage} (lines 368-433) routes the hit:
 *
 * <ul>
 *   <li><b>THE ONE-SHOT</b> — the "arrow" source name alone lands:
 *       {@code source.getSource().remove()} (line 408) consumes the
 *       arrow INSIDE the wound walk, and the window consult's {@code
 *       !flag} arm (flag true for arrows) breaks the stand in a single
 *       hit: {@code onBrokenByPlayer}'s stand item at the feet cell +
 *       {@code onBroken}'s every worn piece at the head cell, the
 *       breaking particles ahead of both, the bowhit NEVER playing
 *       (the false verdict). The pre-fix engine's scan never consulted
 *       the stands — an arrow flew THROUGH the stand whole.</li>
 *   <li><b>THE REFUSED SHARD</b> — the snowball's "thrown" name is
 *       refused at line 401's early return: the shard still shatters
 *       AT the hull (its own onCollision remove), the stand unharmed.
 *       The pre-fix engine's snowball flew through and shattered at
 *       the back wall.</li>
 *   <li><b>THE INVISIBLE BOUNCE</b> — line 376's {@code invisible}
 *       gate refuses the arrow AHEAD of the arrow arm: the wound never
 *       lands and ArrowEntity.onHit's else arm (lines 279-285) pings
 *       the arrow off ({@code velocity *= -0.1} per axis, the clock
 *       reset). The pre-fix engine's arrow flew through and stuck in
 *       the back wall.</li>
 *   <li><b>THE PEARL'S ENTITY-HIT TELEPORT</b> (the wave's adjacent
 *       find) — EnderPearlEntity.onCollision lines 45-70: the pearl's
 *       hit on ANY body teleports the thrower (the reference's
 *       onCollision falls through to the teleport whatever it struck,
 *       the owner alone the pass-through). The pre-fix engine consumed
 *       the pearl at the body with NO teleport.</li>
 * </ul>
 *
 * <p>THE RIG: a stone lane (the top face at y=20) running along +Z at
 * x=10, the shooter's origin five blocks south of the stand, a stone
 * back wall six blocks past the stand catching every pre-fix
 * fly-through. The flights ride the REAL launch wire (the gaussian
 * 0.0075-spread drift under 0.05 blocks over the lane — the 0.25
 * half-width hull absorbs it center-mass).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStandOneShotParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The lane's fixed column. */
    private static final int LANE_X = 10;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "standoneshot" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * (cells z=18, y 20..24) catching every pre-fix fly-through — the
     * stand rides the lane at z=10.5.
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

    /** Summons the stand at the lane's mark (z=10.5), optionally dressed. */
    private ArmorStand summonStand(boolean helmet) throws InterruptedException {
        AtomicReference<ArmorStand> stand = new AtomicReference<>();
        server.ticker().submit(() -> stand.set(server.armorStands().summonAt(
                LANE_X + 0.5, 20.0, 10.5, 0, null)));
        await(() -> stand.get() != null, "the stand summoned on the lane");
        if (helmet) {
            server.ticker().submit(() -> server.armorStands().equipDispensed(
                    stand.get(), 4, net.zaminmc.torch.item.ItemStack.of(
                            BuiltinItems.IRON_HELMET, 1)));
            await(() -> !stand.get().equipment(4).isEmpty(), "the helmet worn");
        }
        return stand.get();
    }

    /** Summons the INVISIBLE stand through the NBT arm (line 376's gate). */
    private ArmorStand summonInvisibleStand() throws InterruptedException {
        AtomicReference<ArmorStand> stand = new AtomicReference<>();
        server.ticker().submit(() -> stand.set(server.armorStands().summonAt(
                LANE_X + 0.5, 20.0, 10.5, 0,
                net.zaminmc.torch.server.nbt.Snbt.parseCompound("{Invisible:1b}"))));
        await(() -> stand.get() != null, "the invisible stand summoned");
        return stand.get();
    }

    /**
     * Launches a projectile north along the lane (+Z, yaw 0) at the
     * stand's mid-hull height (y 21 — inside [20, 21.975]).
     */
    private ProjectileEntity launchAlongLane(ProjectileEntity.Kind kind, int throwerId)
            throws InterruptedException {
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                kind, throwerId, new Position(LANE_X + 0.5, 21.0, 4.5),
                0.0, 0.0, kind == ProjectileEntity.Kind.ARROW ? 2.0 : 1.5, 0)));
        await(() -> launched.get() != null, "the " + kind + " launched along the lane");
        return launched.get();
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE ONE-SHOT: the arrow breaks the stand, the drops land
    // (RED pre-fix: the arrow flew through whole)
    // ------------------------------------------------------------------

    /**
     * An arrow at the dressed stand: the stand breaks in ONE hit (the
     * compat ledger's arrow one-shot), the arrow is CONSUMED (never
     * stuck, gone from the world), and the drops land — the stand item
     * at the stand's feet cell, the helmet at the head cell
     * (onBrokenByPlayer's own walk). Pre-fix RED: the scan never
     * consulted the stand — the arrow stuck in the back wall, the
     * stand stood whole, nothing dropped.
     */
    @Test
    void theArrowOneShotsTheStandAndTheDropsLand() throws Exception {
        buildLane();
        ArmorStand stand = summonStand(true);
        ProjectileEntity arrow = launchAlongLane(ProjectileEntity.Kind.ARROW, -1);

        await(() -> server.armorStands().byId(stand.entityId()) == null,
                "the stand breaks on the single arrow (the one-shot)");
        await(() -> server.projectiles().byId(arrow.entityId()) == null,
                "the arrow is consumed at the stand (never stuck, never through)");

        // THE DROP LAW — onBrokenByPlayer's walk: the stand item at the
        // feet cell, the helmet at the head cell (the PE-5 anchor laws).
        boolean standItem = false;
        boolean helmet = false;
        for (net.zaminmc.torch.server.entity.ItemEntity item : server.itemEntities().all()) {
            Position at = item.position();
            String id = item.stack().type().identifier().value();
            if (Math.abs(at.x() - (LANE_X + 0.5)) <= 1.0 && Math.abs(at.z() - 10.5) <= 1.0) {
                if (id.equals("armor_stand") && at.y() < 21.0) {
                    standItem = true;
                }
                if (id.equals("iron_helmet") && at.y() >= 21.0) {
                    helmet = true;
                }
            }
        }
        assertTrue(standItem,
                "the broken stand's item drops at the feet cell (onBrokenByPlayer)");
        assertTrue(helmet,
                "the worn helmet drops at the head cell (onBroken's equipment walk)");
        assertFalse(arrow.inGround(),
                "the one-shot's arrow never sticks anywhere (line 408's remove)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE REFUSED SHARD: the snowball shatters AT the hull,
    // the stand unharmed (RED pre-fix: the shard died at the back wall)
    // ------------------------------------------------------------------

    /**
     * A snowball at the stand: the "thrown" source is refused (line
     * 401) — the shard shatters AT the hull, the stand unharmed. The
     * pre-fix engine's snowball flew through the stand and shattered at
     * the back wall six blocks on.
     */
    @Test
    void theSnowballShattersAtTheStandAndTheStandSurvives() throws Exception {
        buildLane();
        ArmorStand stand = summonStand(false);
        ProjectileEntity snowball = launchAlongLane(ProjectileEntity.Kind.SNOWBALL, -1);

        // The last-seen position: sampled while the shard lives.
        AtomicReference<Double> lastZ = new AtomicReference<>(4.5);
        await(() -> {
            ProjectileEntity live = server.projectiles().byId(snowball.entityId());
            if (live != null) {
                lastZ.set(live.position().z());
                return false;
            }
            return true; // the shard left the world
        }, "the snowball shatters");
        double deathZ = lastZ.get();
        assertTrue(deathZ < 11.5,
                "the snowball dies AT the stand's hull (the observed death z "
                        + deathZ + " — the pre-fix flight carried past z 17 to the wall)");
        assertTrue(deathZ > 8.0,
                "the snowball reached the stand's block before dying (the observed "
                        + "death z " + deathZ + ")");
        assertNotNull(server.armorStands().byId(stand.entityId()),
                "the refused wound never broke the stand (the thrown name's early return)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE INVISIBLE BOUNCE: the arrow pings off (RED pre-fix:
    // the arrow flew through and stuck in the back wall)
    // ------------------------------------------------------------------

    /**
     * An arrow at the INVISIBLE stand (the NBT arm): line 376's gate
     * refuses the wound ahead of the arrow branch — the arrow PINGS
     * (onHit lines 279-285: the per-axis *-0.1, the yaw flip, the
     * clock reset) and the hull RECAPTURES it: the reference's consult
     * re-arms every tick while the path clips the stand's shape grown
     * 0.3 (line 203's walk), so the bounced arrow never carries past
     * the stand — it oscillates at the hull with geometric decay,
     * gravity sinks it through the band, and it sticks in the lane's
     * floor just south of the stand's center (the observed resting z
     * ~10.3, y ~19.9). The pre-fix engine's arrow flew through and
     * stuck in the back wall six blocks north.
     */
    @Test
    void theArrowBouncesOffTheInvisibleStand() throws Exception {
        buildLane();
        ArmorStand stand = summonInvisibleStand();
        ProjectileEntity arrow = launchAlongLane(ProjectileEntity.Kind.ARROW, -1);

        // The arrow resolves (the capture's oscillation decays, gravity
        // sinks it through the hull band, the floor catches it) — await
        // the STUCK form (the pre-fix flight also sticks, in the back
        // wall — the z window discriminates the two).
        EngineAwaits.awaitCondition(server.world(), () -> {
            ProjectileEntity live = server.projectiles().byId(arrow.entityId());
            return live != null && live.inGround();
        }, 600, "the bounced arrow settles");
        ProjectileEntity live = server.projectiles().byId(arrow.entityId());
        assertNotNull(live, "the bounced arrow stays in the world (stuck, never consumed)");
        double finalZ = live.position().z();
        assertTrue(finalZ < 11.0,
                "the bounced arrow never passes the stand (the final z " + finalZ
                        + " — the pre-fix flight stuck in the back wall past z 17)");
        assertNotNull(server.armorStands().byId(stand.entityId()),
                "the invisible stand never breaks (line 376's refusal)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE PEARL'S ENTITY-HIT TELEPORT (RED pre-fix: the thrower
    // never moved)
    // ------------------------------------------------------------------

    /**
     * A pearl thrown by the player at a zombie in the lane: the pearl's
     * hit on ANY body teleports the thrower (EnderPearlEntity
     * .onCollision lines 45-70 — the block arm's own law carried to the
     * entity arm). The pre-fix engine consumed the pearl at the body
     * with NO teleport.
     */
    @Test
    void thePearlSTeleportRidesItsEntityHit() throws Exception {
        buildLane();
        PlayerSession thrower = joinPlayer("PearlThrower", LANE_X + 0.5, 4.5);
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> zombie =
                new AtomicReference<>();
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity mob = server.mobs().spawnAt(
                    net.zaminmc.torch.server.entity.MobType.ZOMBIE,
                    new Position(LANE_X + 0.5, 20.0, 10.5), 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            zombie.set(mob);
        });
        await(() -> zombie.get() != null, "the zombie stands in the lane");

        ProjectileEntity pearl = launchAlongLane(ProjectileEntity.Kind.ENDER_PEARL,
                thrower.engineEntityId());
        await(() -> server.projectiles().byId(pearl.entityId()) == null,
                "the pearl resolves at the zombie's hull");
        await(() -> thrower.position().z() > 7.0,
                "the thrower teleported to the pearl's entity-hit position (the "
                        + "observed z " + thrower.position().z() + " — the pre-fix "
                        + "engine never moved them off z 4.5)");
        assertTrue(zombie.get().position().z() > 9.0 && !zombie.get().dead(),
                "the struck zombie stands unharmed (the thrown family's 0.0 bruise)");
    }

    /** Joins a survival player on the lane. */
    private PlayerSession joinPlayer(String name, double x, double z) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, java.util.UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        server.teleportPlayer(session, new Position(x, 20.0, z));
        await(() -> Math.abs(session.position().z() - z) < 0.01,
                name + " standing at the lane's south end");
        return session;
    }
}
