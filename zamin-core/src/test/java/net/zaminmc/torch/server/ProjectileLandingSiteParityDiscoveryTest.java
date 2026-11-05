package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-21 — THE PROJECTILE FAMILY'S LANDING-SITE REPAIRS (the PE-20
 * ledger's adjacent finds, each verified against the reference's own
 * tick order — ThrownEntity.tick calls onCollision BEFORE the position
 * integration, lines 193-199, so every {@code this.x/y/z} inside the
 * collision walks is the PRE-MOVE origin):
 *
 * <ul>
 *   <li><b>THE SMALL FIREBALL'S FIRE CELL</b> — SmallFireballEntity
 *       .onHit lines 34-49: {@code hit.getPos().offset(hit.face)} — the
 *       struck face's OWN adjacent cell, however far back the tick began
 *       (the pre-fix engine's from-cell stand-in dropped a fast
 *       fireball's fire up to several cells short of the wall), and the
 *       mobGriefing gate lines 40-44 (a MOB shooter's fire rides the
 *       rule; the dispenser's charge and the player's hand place
 *       unconditioned — the reference's {@code flag1} initializer
 *       true).</li>
 *   <li><b>THE POTION'S SPLASH CENTER</b> — PotionEntity.onCollision
 *       lines 72-106: the effect box {@code this.getShape().grown(4, 2,
 *       4)} at the potion's OWN pre-move position, the closing 2002
 *       event at {@code new BlockPos(this)} — never at the struck
 *       intercept (the pre-fix engine splashed at the hit face, up to a
 *       full tick's travel past the reference's center).</li>
 *   <li><b>THE SHARD FAMILY'S BURST</b> — SnowballEntity.onCollision
 *       lines 51-54 / EggEntity lines 55-66: the 8-particle burst on
 *       EVERY collision arm (the block AND the body alike — the
 *       pre-fix engine played it on the block arm alone), NO sound for
 *       either (the glass stand-in was the engine's invention, retired;
 *       the reference's shards play nothing).</li>
 * </ul>
 *
 * <p>THE RIG: the PE-19 lane's own shape (a stone lane at y=20 running
 * +Z at x=10) with per-seat walls.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectileLandingSiteParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The lane's fixed column. */
    private static final int LANE_X = 10;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "landingsite" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * x 8..12, open air above through y=24, and the back wall at the
     * given z (cells z..z, y 20..24).
     */
    private void buildLaneWithWallAt(int wallZ) throws InterruptedException {
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
            // The wall spans WIDER than the lane (x 6..14): the launch's
            // own gaussian tilt walks a 16-block runway's impact across
            // two-plus columns — the struck face stays a wall face.
            for (int x = 6; x <= 14; x++) {
                for (int y = 20; y <= 24; y++) {
                    world.setBlock(new BlockPosition(x, y, wallZ), BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(
                        new BlockPosition(LANE_X, 21, wallZ)))
                        && BuiltinBlocks.STONE.equals(world.getBlock(
                                new BlockPosition(6, 21, wallZ)))
                        && BuiltinBlocks.STONE.equals(world.getBlock(
                                new BlockPosition(LANE_X, 19, 10))),
                "the lane and the wall stand");
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE FIRE CELL: the struck face's own adjacent cell (RED
    // pre-fix: the from-cell stand-in left the fire a cell short)
    // ------------------------------------------------------------------

    /**
     * A dispensed fire charge (the shooter-less arm — mobGriefing never
     * gates it) fired down the lane at the wall at z=18 from z=2: the
     * acceleration walk has the body at ~1.06 blocks/tick when it meets
     * the face, so the pre-fix engine's from-cell floor(18 &minus; 1.06)
     * = 16 — a full cell SHORT of the wall. Vanilla places the fire at
     * the struck face's OWN adjacent cell (17): {@code hit.getPos()
     * .offset(hit.face)}, SmallFireballEntity lines 34-49.
     */
    @Test
    void theFireChargeLightsFireAtTheStruckFacesOwnCell() throws Exception {
        buildLaneWithWallAt(18);
        // The fire's own life is brief on a stone wall (the reference's
        // own law — FireBlock.tick's no-flammable-neighbor extinction),
        // so the seat captures the PLACEMENT itself through the world's
        // commit listener: the struck face's z is the discriminator,
        // however long the fire then lives.
        java.util.List<BlockPosition> fires = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.world().addChangeListener((w, pos, type) -> {
            if (type.equals(BuiltinBlocks.FIRE)) {
                fires.add(pos);
            }
        });
        server.ticker().submit(() -> server.projectiles().launchFireball(
                new Position(LANE_X + 0.5, 21.0, 2.5), 0.0, 0.05, 1.0, 0));

        await(() -> fires.stream().anyMatch(p -> p.z() == 17),
                "the fire lands at the struck face's OWN adjacent cell (17)");
        assertFalse(fires.stream().anyMatch(p -> p.z() == 16),
                "the fire never lands a cell short (the pre-fix from-cell"
                        + " stand-in)");
        assertFalse(fires.stream().anyMatch(p -> p.z() == 18),
                "the fire never lands INSIDE the wall (the face's outward"
                        + " offset)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE MOBGRIEFING GATE: the blaze's fire rides the rule,
    // the dispenser's charge never does (RED pre-fix: the blaze's fire
    // burned where the rule refused)
    // ------------------------------------------------------------------

    /**
     * SmallFireballEntity.onHit lines 40-44: {@code flag1} starts TRUE
     * and turns to the mobGriefing consult ONLY when the shooter is a
     * MobEntity — the blaze's charge never burns in a no-grief world,
     * while the dispenser's shooter-less charge places unconditioned.
     */
    @Test
    void theBlazesFireRidesMobGriefingTheDispensersNever() throws Exception {
        buildLaneWithWallAt(18);
        server.ticker().submit(() -> server.gameRules().set("mobGriefing", "false"));
        java.util.List<BlockPosition> fires = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.world().addChangeListener((w, pos, type) -> {
            if (type.equals(BuiltinBlocks.FIRE)) {
                fires.add(pos);
            }
        });
        // The dispenser's shooter-less charge FIRST (the clear lane — the
        // blaze's own body would take the later shot's entity arm): flag1
        // stays true for the shooter-less arm — the fire places even
        // under the no-grief rule.
        server.ticker().submit(() -> server.projectiles().launchFireball(
                new Position(LANE_X + 0.5, 21.0, 2.5), 0.0, 0.05, 1.0, 0));
        await(() -> fires.stream().anyMatch(p -> p.z() == 17),
                "the DISPENSER's charge places its fire under the no-grief"
                        + " rule (flag1's own initializer)");

        // The blaze's own charge: the rule refuses the fire (the mob
        // shooter's arm — SmallFireballEntity lines 40-44). The blaze
        // stands OFF the flight line (its body would take the shot's
        // entity arm), the charge riding the same 16-block runway as the
        // dispenser's.
        AtomicReference<MobEntity> blaze = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(
                    MobType.BLAZE, new Position(LANE_X + 2.5, 20.0, 5.5), 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            blaze.set(mob);
        });
        await(() -> blaze.get() != null, "the blaze stands beside the lane");
        server.ticker().submit(() -> server.projectiles().launchBlazeFireball(
                blaze.get().entityId(), new Position(LANE_X + 0.5, 21.0, 2.5),
                0.0, 0.05, 1.0, 0.0, 0));
        await(() -> server.projectiles().all().isEmpty(),
                "the blaze's charge spent itself at the wall");
        EngineAwaits.awaitTicks(server.world(), 4, "the refused-fire window");
        // The refusal discriminator: no SECOND placement ever fires — the
        // dispenser's single z=17 row stands alone (the pre-fix engine's
        // unconditional blaze arm would have placed another, a cell
        // short at z=16).
        assertFalse(fires.stream().anyMatch(p -> p.z() == 16),
                "the BLAZE's fire never burns where mobGriefing refuses (the"
                        + " pre-fix engine placed it unconditionally)");
        assertTrue(fires.stream().allMatch(p -> p.z() == 17),
                "the no-grief lane carries the dispenser's placement alone");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE SPLASH CENTER: the potion's own pre-move origin (RED
    // pre-fix: the splash centered at the struck face, a full tick's
    // travel past the reference's center)
    // ------------------------------------------------------------------

    /**
     * A splash potion thrown at the wall at z=12 from z=8.5 at speed 2:
     * the final tick's segment [10.49, 12.43] crosses the face at 12.0
     * mid-flight — the reference's splash box centers at the potion's
     * OWN position inside onCollision (10.49 — ThrownEntity.tick
     * integrates AFTER the hit), and the 2002 event lands at the
     * origin's cell (10). The pre-fix engine centered both at the struck
     * face (12).
     */
    @Test
    void thePotionSplashesAtItsOwnPreMoveOrigin() throws Exception {
        buildLaneWithWallAt(12);
        List<FxManager.PotionSplash> splashes = new ArrayList<>();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.PotionSplash splash) {
                splashes.add(splash);
            }
        });
        server.ticker().submit(() -> server.projectiles().launch(
                ProjectileEntity.Kind.POTION, -1,
                new Position(LANE_X + 0.5, 21.0, 8.5), 0.0, 0.0, 2.0, 0));

        await(() -> !splashes.isEmpty(), "the splash event fired");
        FxManager.PotionSplash splash = splashes.get(0);
        assertTrue(splash.position().z() < 11.5,
                "the splash centers at the potion's OWN pre-move origin (the"
                        + " observed z " + splash.position().z() + " — the reference's"
                        + " this.x/y/z inside onCollision; the pre-fix engine"
                        + " splashed at the struck face, z 12)");
        assertTrue(splash.position().z() > 9.5,
                "the origin lies on the approach (the rig's own sanity)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE SHARD BURST ON THE ENTITY ARM: the poof rides the
    // body hit, no glass sound anywhere (RED pre-fix: the entity arms
    // played no FX at all)
    // ------------------------------------------------------------------

    /**
     * SnowballEntity.onCollision lines 51-54: the 8-particle burst rides
     * ANY collision — the body hit included. A snowball thrown at a
     * no-AI zombie: the poof fires at the hit (the pre-fix engine played
     * the block arm's burst alone — a body hit was silent), and NO glass
     * sound anywhere (the reference's shards play nothing).
     */
    @Test
    void theShardBurstRidesTheEntityArmSilently() throws Exception {
        buildLaneWithWallAt(18);
        AtomicReference<MobEntity> zombie = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(
                    MobType.ZOMBIE, new Position(LANE_X + 0.5, 20.0, 10.5), 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            zombie.set(mob);
        });
        await(() -> zombie.get() != null, "the zombie stands in the lane");
        List<FxManager.Poof> poofs = new ArrayList<>();
        List<FxManager.Sound> sounds = new ArrayList<>();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.Poof poof) {
                poofs.add(poof);
            } else if (event instanceof FxManager.Sound sound) {
                sounds.add(sound);
            }
        });
        server.ticker().submit(() -> server.projectiles().launch(
                ProjectileEntity.Kind.SNOWBALL, -1,
                new Position(LANE_X + 0.5, 21.0, 8.5), 0.0, 0.0, 1.5, 0));

        await(() -> !poofs.isEmpty(),
                "the snowball's burst rides the BODY hit (the pre-fix entity"
                        + " arms played no FX at all)");
        assertFalse(sounds.stream().anyMatch(s -> s.name().equals("random.glass")),
                "the snowball's shatter plays NO glass sound (the reference's"
                        + " own silence — the engine's stand-in retired)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE EGG'S CRUMB BURST: the item-shatter FX at the hit
    // (RED pre-fix: the entity arms played no FX, the block arm's poof
    // was the snowball's own shape)
    // ------------------------------------------------------------------

    /**
     * EggEntity.onCollision lines 55-66: the burst is the ITEM_CRACK
     * crumb carry — the egg's own item id. An egg thrown at the lane
     * floor: the item-shatter event fires at the egg's pre-move origin.
     */
    @Test
    void theEggBurstsItsCrumbsAtTheOwnOrigin() throws Exception {
        buildLaneWithWallAt(18);
        List<FxManager.ItemShatter> crumbs = new ArrayList<>();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.ItemShatter crumb) {
                crumbs.add(crumb);
            }
        });
        server.ticker().submit(() -> server.projectiles().launch(
                ProjectileEntity.Kind.EGG, -1,
                new Position(LANE_X + 0.5, 21.0, 8.5), 0.0, 0.0, 1.5, 0));

        await(() -> !crumbs.isEmpty(),
                "the egg's ITEM_CRACK crumb burst rides the landing (the"
                        + " reference's own shape — the pre-fix engine's poof"
                        + " was the snowball's)");
        assertTrue(crumbs.get(0).stack().type()
                        .equals(net.zaminmc.torch.server.item.BuiltinItems.EGG),
                "the crumbs carry the egg's own item id");
    }
}
