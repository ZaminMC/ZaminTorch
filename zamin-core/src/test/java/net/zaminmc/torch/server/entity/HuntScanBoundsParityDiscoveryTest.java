package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-17 — THE HUNT FAMILY'S OWN LAWS over the reference's target-goal
 * family: the PLAYER hunt's own box form ({@code ActiveTargetGoal
 * .canStart} lines 86-96 + the targetFilter's player folds, lines 54-70 +
 * {@code TrackTargetGoal.isTarget}'s checkVisibility arm, lines 85-120),
 * the PER-KIND band repair (MobEntity.initAttributes line 90's 16.0 base
 * — never the flat 32), the golem's hostile census box form
 * (TrackIronGolemTargetGoal + SnowGolemEntity line 33's registration),
 * and the feud tail (ZombieEntity line 78's golem feud, SlimeEntity 42,
 * EndermanEntity 61's endermite feud).
 *
 * <p>THE LAWS PINNED:</p>
 * <ul>
 *   <li>THE PLAYER FOLDS — the targetFilter's own shrinks: {@code
 *       isSneaking() x0.8} and {@code isInvisible() x0.7 x
 *       getArmorEquippedRatio()} (the occupied armor slots over the four,
 *       floored 0.1 — a naked invisible player's band 35 x 0.07 = 2.45).
 *       The pre-fix scan consulted no posture — a sneaking player at 30
 *       of the zombie's 35 was hunted whole.</li>
 *   <li>THE VISIBILITY GATE — {@code !checkVisibility || canSee}: the
 *       kinds registering the player goal with checkVisibility true (the
 *       zombie's ActiveTargetGoal line 76, the skeleton's 56, the witch's
 *       47, the silverfish's 35, the blaze's 36, the creeper's 40, the
 *       endermite's 34, the killer rabbit's 325, the angry pigman's 222,
 *       the slime and the ghast's MobEntityPlayerTargetGoal line 58, the
 *       spider pair) never START a hunt through a wall. The pre-fix
 *       engine hunted the player through solid stone whole.</li>
 *   <li>THE PER-KIND BAND — the gather grows by the kind's FOLLOW RANGE
 *       attribute: the zombie's own 35 (ZombieEntity line 84), the wolf's
 *       base 16 (MobEntity line 90). A sheep 25 blocks from a wolf is
 *       beyond vanilla's gather whole (the pre-fix flat-32 engine hunted
 *       it); a straddling villager at 35.5 east IS vanilla's find (the
 *       pre-fix flat-32 engine refused it).</li>
 *   <li>THE GOLEM CENSUS — the iron golem's OWN 1.4x2.9 shape grown 32
 *       (the feet-anchored y band [y - 4, y + 6.9], each candidate's own
 *       box STRICT, the creeper excluded, NO visibility consult); the
 *       snow golem's OWN 0.7x1.9 shape grown by the attribute's 16 base
 *       (NEVER the volley's 10 — the scan and the shot are separate
 *       laws), the 1-in-10 cadence, the eye-to-eye ray.</li>
 *   <li>THE FEUD TAIL — the zombie hunts the iron golem when no villager
 *       is in the gather (the villager arm first, ZombieEntity lines
 *       77-78's registration order); the slime family feuds the golem
 *       (SlimeEntity 42); the enderman hunts the endermite
 *       (EndermanEntity 61 — the engine's endermites all pearl-spawned,
 *       isPlayerSpawned true).</li>
 * </ul>
 *
 * <p>THE RIG: a broad stone platform (y 38/39, the bodies at 40), the
 * LIVE hunters PINNED on 1x1 pillars with sky-blocking roofs (the stroll
 * never moves a pinned body, the undead never burn under the roof), the
 * static prey NoAI on the platform. The mob-hunt seats drive the scan
 * synchronously inside one ticker action (400 cadence passes — the
 * deterministic read); the player seats await the hunt state over live
 * ticks; the golem seats await the census's own target read.</p>
 */
class HuntScanBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The pinned hunters' standing cell (the pillar top at y 44). */
    private static final int HUNTER_X = 20;
    private static final int HUNTER_Z = 10;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "huntbounds" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * The platform: stone at y 38/39 over x 4..64, z 4..16, open air
     * above through y 50 — the working surface every arm builds on.
     */
    private void buildPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int x = 4; x <= 64; x++) {
                for (int z = 4; z <= 16; z++) {
                    server.world().setBlock(new BlockPosition(x, 38, z),
                            BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, 39, z),
                            BuiltinBlocks.STONE);
                    for (int y = 40; y <= 50; y++) {
                        server.world().setBlock(new BlockPosition(x, y, z),
                                BuiltinBlocks.AIR);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(HUNTER_X, 39, HUNTER_Z))),
                200, "the platform stands");
    }

    /**
     * Pins a LIVE body: a 4-tall pillar (the top at 43, the feet at 44)
     * whose drop exceeds the pathfinder's 3-block fall band — the stroll
     * never paths down, so the body's own geometry holds for the whole
     * window — and a roof block shading the burning kinds from the
     * daylight clock (the undead survivor's own window). The roof rides
     * ABOVE the body's head whatever the kind's height (the enderman
     * and the golem pair stand 2.9 tall — a flat 46 roof would sit
     * inside their heads and poison every eye-ray consult).
     */
    private MobEntity spawnPinned(MobType type, int x, int z) throws InterruptedException {
        int roofY = (int) Math.ceil(44.0 + type.height) + 1;
        server.ticker().submit(() -> {
            for (int y = 40; y <= 43; y++) {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
            }
            server.world().setBlock(new BlockPosition(x, roofY, z), BuiltinBlocks.STONE);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(x, 43, z)))
                        && BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(x, roofY, z))),
                100, "the pin stands");
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() ->
                spawned.set(server.mobs().spawnAt(type,
                        new Position(x + 0.5, 44.0, z + 0.5), 0)));
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the body spawned");
        return spawned.get();
    }

    /**
     * A STATIC (NoAI) body on the platform — the prey never strolls, the
     * geometry exact. The {@code shadeAt} roof (y 47, above every ray the
     * golem consults) keeps the burning kinds alive through the window.
     */
    private MobEntity spawnStatic(MobType type, double x, double z, boolean shade)
            throws InterruptedException {
        if (shade) {
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(
                            (int) Math.floor(x), 47, (int) Math.floor(z)),
                            BuiltinBlocks.STONE));
            EngineAwaits.awaitCondition(server.world(),
                    () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                    new BlockPosition((int) Math.floor(x), 47,
                                            (int) Math.floor(z)))),
                    100, "the shade stands");
        }
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(type,
                    new Position(x, 40.0, z), 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            spawned.set(mob);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the static " + type + " spawned");
        return spawned.get();
    }

    /** Joins a player and stands them on the platform. */
    private PlayerSession joinPlayer(String name, double x, double z) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                200, name + " playing");
        server.teleportPlayer(session, new Position(x, 40.0, z));
        EngineAwaits.awaitCondition(server.world(),
                () -> session.position().y() == 40.0, 200, name + " standing");
        return session;
    }

    private final AtomicReference<Boolean> scanDone = new AtomicReference<>(false);

    /**
     * The synchronous mob-hunt read (one ticker action): 400 passes of
     * the 1-in-10 cadence — the deterministic answer, never a live-tick
     * await. A null after the full window is the never-answer.
     */
    private MobEntity scanUntilSettled(MobEntity hunter) throws InterruptedException {
        AtomicReference<MobEntity> found = new AtomicReference<>();
        scanDone.set(false);
        server.ticker().submit(() -> {
            for (int i = 0; i < 400; i++) {
                MobEntity answer = hunter.tickMobTargeting();
                if (answer != null) {
                    found.set(answer);
                    return;
                }
            }
            scanDone.set(true);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> found.get() != null || scanDone.get(), 100, "the scan settled");
        return found.get();
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE SNEAKING PLAYER (RED pre-fix) + the walking control
    // ------------------------------------------------------------------

    /**
     * A sneaking player 30 blocks from a pinned zombie (the zombie's
     * own band 35): vanilla's fold shrinks the band to 28 — the hunt
     * never starts over the whole window. The walking control at the
     * same distance starts it (the band's own green). Pre-fix RED: the
     * scan consulted no posture and hunted the sneaking player whole.
     */
    @Test
    void theSneakingPlayerNeverStartsTheHunt() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnPinned(MobType.ZOMBIE, HUNTER_X, HUNTER_Z);
        PlayerSession player = joinPlayer("Sneaker", HUNTER_X + 30.5, HUNTER_Z + 0.5);
        server.ticker().submit(() -> player.setSneaking(true));

        for (int sample = 0; sample < 30; sample++) {
            EngineAwaits.awaitTicks(server.world(), 10,
                    "the sneaking never-window sample " + sample);
            assertFalse(zombie.goalHasTarget(false),
                    "the sneaking player at 30 of the 35 band (the x0.8 fold's"
                            + " own 28) never starts the hunt at sample " + sample);
        }
        // The walking control: the fold lifted, the band's own hunt.
        server.ticker().submit(() -> player.setSneaking(false));
        EngineAwaits.awaitCondition(server.world(),
                () -> zombie.goalHasTarget(false), 100,
                "the walking player at 30 starts the hunt (the band control)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE INVISIBLE PLAYER (RED pre-fix) + the visible control
    // ------------------------------------------------------------------

    /**
     * An invisible naked player 6 blocks from a pinned zombie: the
     * ratio's 0.1 floor folds the 35 band to 2.45 — never started. The
     * visible control player joins only after the never-window (a
     * visible player in the band would arm the shared hunt pin whole).
     * Pre-fix RED: the scan consulted no effect and hunted the invisible
     * player.
     */
    @Test
    void theInvisiblePlayerNeverStartsTheHunt() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnPinned(MobType.ZOMBIE, HUNTER_X, HUNTER_Z);
        PlayerSession ghost = joinPlayer("Ghost", HUNTER_X + 6.5, HUNTER_Z + 0.5);
        server.ticker().submit(() -> server.potions().add(ghost,
                new StatusEffectInstance(StatusEffects.INVISIBILITY, 2000, 0)));

        for (int sample = 0; sample < 30; sample++) {
            EngineAwaits.awaitTicks(server.world(), 10,
                    "the invisible never-window sample " + sample);
            assertFalse(zombie.goalHasTarget(false),
                    "the naked invisible player's band (the x0.7 x 0.1 fold's"
                            + " own 2.45) never holds a player at 6 at sample " + sample);
        }
        // The visible control: a plainly-visible player at 5 hunts.
        joinPlayer("Visible", HUNTER_X + 5.5, HUNTER_Z + 3.5);
        EngineAwaits.awaitCondition(server.world(),
                () -> zombie.goalHasTarget(false), 100,
                "the visible player at 5 starts the hunt (the fold control)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE WALLED PLAYER (RED pre-fix) + the open control
    // ------------------------------------------------------------------

    /**
     * A player 8 blocks from a pinned zombie with a full stone wall
     * between: the checkVisibility family never starts the hunt through
     * stone (the eye-to-target line blocked). The wall lifted, the hunt
     * starts. Pre-fix RED: the engine hunted the player through the
     * wall whole.
     */
    @Test
    void theWalledPlayerNeverStartsTheHunt() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnPinned(MobType.ZOMBIE, HUNTER_X, HUNTER_Z);
        PlayerSession player = joinPlayer("Walled", HUNTER_X + 8.5, HUNTER_Z + 0.5);
        server.ticker().submit(() -> {
            for (int y = 39; y <= 48; y++) {
                for (int z = 4; z <= 16; z++) {
                    server.world().setBlock(new BlockPosition(HUNTER_X + 4, y, z),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(HUNTER_X + 4, 44, HUNTER_Z))),
                100, "the wall stands");

        for (int sample = 0; sample < 30; sample++) {
            EngineAwaits.awaitTicks(server.world(), 10,
                    "the walled never-window sample " + sample);
            assertFalse(zombie.goalHasTarget(false),
                    "the checkVisibility family never starts a hunt through a"
                            + " wall at sample " + sample);
        }
        // The open control: the wall lifted, the hunt starts.
        server.ticker().submit(() -> {
            for (int y = 39; y <= 48; y++) {
                for (int z = 4; z <= 16; z++) {
                    server.world().setBlock(new BlockPosition(HUNTER_X + 4, y, z),
                            BuiltinBlocks.AIR);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> zombie.goalHasTarget(false), 100,
                "the wall lifted, the hunt starts (the visibility control)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE LEDGE: the grown box's vertical band (RED pre-fix)
    // ------------------------------------------------------------------

    /**
     * A villager on a ledge 4 above the pinned zombie (its feet at 48,
     * the zombie's at 44), 33.5 east: its own box [48, 49.8] reaches
     * up into the grown box's vertical band [40, 49.95] (the
     * intersection 1.95) while its CENTER sits 33.74 out — past the
     * pre-fix flat 32 sphere but deep inside the grown face at 55.8.
     * Vanilla hunts; the target locks. Pre-fix RED: the center scan
     * refused 33.74 whole.
     */
    @Test
    void theLedgeVillagerIsHunted() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnPinned(MobType.ZOMBIE, HUNTER_X, HUNTER_Z);
        zombie.setNoAi(true); // the synchronous scan drives the hunt whole
        // The ledge: a floating block at (54, 47, 10), the feet at 48.
        server.ticker().submit(() ->
                server.world().setBlock(new BlockPosition(54, 47, 10),
                        BuiltinBlocks.STONE));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(54, 47, 10))),
                100, "the ledge stands");
        AtomicReference<MobEntity> villager = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(MobType.VILLAGER,
                    new Position(54.5, 48.0, 10.5), 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            villager.set(mob);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> villager.get() != null, 100, "the villager spawned");

        MobEntity found = scanUntilSettled(zombie);
        assertSame(villager.get(), found,
                "the ledge villager's own box (its center 33.74 out, past the"
                        + " pre-fix 32 sphere) is hunted through the vertical band");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE FACE STRADDLE at the grown box's east face (RED
    // pre-fix)
    // ------------------------------------------------------------------

    /**
     * A villager 35.5 east of the pinned zombie at its own level: its
     * own box's near face at 35.2 sits a hair (0.1) inside the grown
     * box's east face at 35.8 — vanilla hunts. Pre-fix RED: the center
     * 35.5 sat past the pre-fix flat 32 sphere and the scan refused it
     * whole.
     */
    @Test
    void theStraddlingVillagerIsHunted() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnPinned(MobType.ZOMBIE, HUNTER_X, HUNTER_Z);
        zombie.setNoAi(true); // the synchronous scan drives the hunt whole
        MobEntity straddler = spawnStatic(MobType.VILLAGER, HUNTER_X + 35.5,
                HUNTER_Z + 0.5, false);
        // The straddle: the body rides its cell's west edge (x 56.0) for
        // the exact 35.5 center — the box's near face at 35.2 grazes the
        // grown face at 35.8 by 0.1.
        server.ticker().submit(() -> straddler.teleport(
                new Position(HUNTER_X + 35.5, 40.0, HUNTER_Z + 0.5)));
        EngineAwaits.awaitTicks(server.world(), 2, "the straddle placement");

        MobEntity found = scanUntilSettled(zombie);
        assertSame(straddler, found,
                "the straddler (its own box's near face 0.1 inside the grown"
                        + " face) is hunted — the pre-fix center scan refused it");
    }

    // ------------------------------------------------------------------
    // Seat 6 — THE FAR CONTROL: beyond the grown face (green by design)
    // ------------------------------------------------------------------

    /**
     * A villager 37.5 east of the pinned zombie: its own box's near
     * face at 37.2 sits past the grown face at 35.8 — never hunted over
     * the whole window, on both engines (the pre-fix 32 sphere refused
     * it too). The gather's own outside control.
     */
    @Test
    void theFarVillagerIsNeverHunted() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnPinned(MobType.ZOMBIE, HUNTER_X, HUNTER_Z);
        zombie.setNoAi(true); // the synchronous scan drives the hunt whole
        MobEntity far = spawnStatic(MobType.VILLAGER, HUNTER_X + 37.5,
                HUNTER_Z + 0.5, false);

        assertNull(scanUntilSettled(zombie),
                "the villager beyond the grown face is never hunted");
        assertTrue(zombie.mobAttackTarget() != far,
                "the far villager never locks the zombie's target");
    }

    // ------------------------------------------------------------------
    // Seat 7 — THE WOLF'S OWN BAND (RED pre-fix)
    // ------------------------------------------------------------------

    /**
     * A pinned untamed wolf and a sheep 25 blocks east: the wolf's own
     * follow range is the base 16 (MobEntity line 90's registration) —
     * the grown face at 36.8 never holds a sheep whose near face sits
     * at 45.2. Vanilla never hunts. Pre-fix RED: the flat 32 sphere
     * hunted the sheep whole.
     */
    @Test
    void theSheepBeyondTheWolfSBandIsNeverHunted() throws Exception {
        buildPlatform();
        MobEntity wolf = spawnPinned(MobType.WOLF, HUNTER_X, HUNTER_Z);
        wolf.setNoAi(true); // the synchronous scan drives the hunt whole
        MobEntity sheep = spawnStatic(MobType.SHEEP, HUNTER_X + 25.5,
                HUNTER_Z + 0.5, false);

        assertNull(scanUntilSettled(wolf),
                "the sheep 25 blocks out (the wolf's own 16 band) is never hunted");
        assertTrue(wolf.mobAttackTarget() != sheep,
                "the sheep never locks the wolf's target");
    }

    // ------------------------------------------------------------------
    // Seat 8 — THE IRON GOLEM'S TARGET LAW (rewritten Wave PE-27)
    // ------------------------------------------------------------------

    /**
     * The 1.8.8 iron golem NEVER hunts on sight (TrackIronGolemTarget
     * Goal alone owns the target, IronGolemEntity line 54 — the village's
     * defense consult): a zombie straddling the old census's very face
     * is never locked; the 32-block hostile census the engine formerly
     * carried is the 1.14 village-siege law, retired.
     */
    @Test
    void theStraddlingZombieNeverLocksTheIronGolem() throws Exception {
        buildPlatform();
        MobEntity golem = spawnPinned(MobType.IRON_GOLEM, HUNTER_X, HUNTER_Z);
        MobEntity straddler = spawnStatic(MobType.ZOMBIE, HUNTER_X + 32.5,
                HUNTER_Z + 0.5, true);
        // The exact straddle: the center at 53.0 (the cell's west edge).
        server.ticker().submit(() -> straddler.teleport(
                new Position(HUNTER_X + 32.5 + 0.5, 40.0, HUNTER_Z + 0.5)));
        EngineAwaits.awaitTicks(server.world(), 2, "the straddle placement");

        EngineAwaits.awaitTicks(server.world(), 20, "the never-hunt window");
        assertNull(golem.golemTarget(),
                "no census runs — the straddling zombie is never locked (the"
                        + " 1.8.8 golem turns only on the village's defense consult)");
    }

    // ------------------------------------------------------------------
    // Seat 9 — THE CREEPER EXCLUSION (rewritten Wave PE-27: the defense
    // consult's own exclusion, never a census)
    // ------------------------------------------------------------------

    /**
     * A creeper 6 east and a zombie 10 east of the iron golem: NEITHER
     * is hunted — no census runs at all (the 1.8.8 golem turns only on
     * the village's defense consult). The creeper exclusion itself now
     * lives in the village's defense consult (the attacker registry's
     * own gate); the seat pins the never-on-sight law beside it.
     */
    @Test
    void theCreeperNeverLocksTheIronGolem() throws Exception {
        buildPlatform();
        MobEntity golem = spawnPinned(MobType.IRON_GOLEM, HUNTER_X, HUNTER_Z);
        MobEntity creeper = spawnStatic(MobType.CREEPER, HUNTER_X + 6.5,
                HUNTER_Z + 0.5, false);
        MobEntity zombie = spawnStatic(MobType.ZOMBIE, HUNTER_X + 10.5,
                HUNTER_Z + 0.5, true);

        EngineAwaits.awaitTicks(server.world(), 20, "the never-hunt window");
        assertNull(golem.golemTarget(),
                "no census runs — neither the creeper nor the zombie behind it"
                        + " is hunted (the defense consult alone owns the target)");
        // The defense arm's write: the mob target's chase owns the walk.
        server.ticker().submit(() -> golem.setMobAttackTarget(zombie));
        EngineAwaits.awaitCondition(server.world(),
                () -> golem.mode() == MobEntity.Mode.CHASE, 100,
                "the defense target's chase owns the walk");
        assertTrue(golem.golemTarget() != creeper,
                "the creeper never rides the golem's target row");
    }

    // ------------------------------------------------------------------
    // Seat 10 — THE SNOW GOLEM'S CENSUS BAND (RED pre-fix)
    // ------------------------------------------------------------------

    /**
     * A zombie 12 blocks east of the snow golem: the ActiveTargetGoal
     * gather reads the kind's own FOLLOW RANGE — the attribute's 16
     * base, never the volley's 10 — so the census FINDS and FACES the
     * body at 12 whole. Pre-fix RED: the engine conflated the scan with
     * the shot band and never saw a hostile past 10.
     */
    @Test
    void theSnowGolemCensusSeesPastTheVolleyBand() throws Exception {
        buildPlatform();
        MobEntity golem = spawnPinned(MobType.SNOW_GOLEM, HUNTER_X, HUNTER_Z);
        MobEntity zombie = spawnStatic(MobType.ZOMBIE, HUNTER_X + 12.5,
                HUNTER_Z + 0.5, true);

        EngineAwaits.awaitCondition(server.world(),
                () -> golem.golemTarget() == zombie, 300,
                "the snow golem's census (the 16-band gather, the 1-in-10"
                        + " cadence) finds the hostile at 12 — past the pre-fix"
                        + " 10-block scan whole");
        assertSame(zombie, golem.golemTarget(),
                "the census's own band is the attribute's 16, never the volley's 10");
    }

    // ------------------------------------------------------------------
    // Seat 11 — THE ZOMBIE'S GOLEM FEUD (RED pre-fix) + the
    // villager-first order (green by design)
    // ------------------------------------------------------------------

    /**
     * A zombie and an iron golem 10 east, no villager in the gather:
     * the zombie hunts the DEFENDER (ZombieEntity line 78's
     * ActiveTargetGoal over IronGolemEntity, checkVisibility true — the
     * open-platform ray clears). Pre-fix RED: the engine's zombie never
     * carried the feud.
     */
    @Test
    void theZombieHuntsTheIronGolemWhenNoVillagerIsNear() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnPinned(MobType.ZOMBIE, HUNTER_X, HUNTER_Z);
        zombie.setNoAi(true); // the synchronous scan drives the hunt whole
        MobEntity golem = spawnStatic(MobType.IRON_GOLEM, HUNTER_X + 10.5,
                HUNTER_Z + 0.5, false);

        MobEntity found = scanUntilSettled(zombie);
        assertSame(golem, found,
                "the zombie's golem feud (line 78) locks the defender when no"
                        + " villager is in the gather");
    }

    /**
     * The villager arm first (ZombieEntity lines 77-78, the same
     * priority 2 in registration order): a villager 30 east and a golem
     * 10 east — the zombie hunts the VILLAGER, never the nearer golem.
     * Green on both engines (the pre-fix villager arm found it too).
     */
    @Test
    void theVillagerWinsTheZombieSGatherOverTheNearerGolem() throws Exception {
        buildPlatform();
        MobEntity zombie = spawnPinned(MobType.ZOMBIE, HUNTER_X, HUNTER_Z);
        zombie.setNoAi(true); // the synchronous scan drives the hunt whole
        MobEntity golem = spawnStatic(MobType.IRON_GOLEM, HUNTER_X + 10.5,
                HUNTER_Z + 0.5, false);
        MobEntity villager = spawnStatic(MobType.VILLAGER, HUNTER_X + 30.5,
                HUNTER_Z + 0.5, false);

        MobEntity found = scanUntilSettled(zombie);
        assertSame(villager, found,
                "the villager arm runs first — the registration order's own law");
        assertTrue(zombie.mobAttackTarget() != golem,
                "the nearer golem never preempts the villager");
    }

    // ------------------------------------------------------------------
    // Seat 12 — THE FEUD TAIL: the slime and the enderman (RED pre-fix)
    // ------------------------------------------------------------------

    /**
     * A slime and an iron golem 8 east: the hop family's own feud
     * (SlimeEntity line 42, MobEntityActiveTargetGoal — no cadence gate,
     * checkVisibility true riding isTarget's fourth arg) locks the
     * defender. Pre-fix RED: the engine's slime never carried the feud.
     */
    @Test
    void theSlimeFeudsTheIronGolem() throws Exception {
        buildPlatform();
        MobEntity slime = spawnPinned(MobType.SLIME, HUNTER_X, HUNTER_Z);
        slime.setNoAi(true); // the synchronous scan drives the hunt whole
        MobEntity golem = spawnStatic(MobType.IRON_GOLEM, HUNTER_X + 8.5,
                HUNTER_Z + 0.5, false);

        MobEntity found = scanUntilSettled(slime);
        assertSame(golem, found,
                "the slime family's golem feud (line 42) locks the defender");
    }

    /**
     * An enderman and an endermite 8 east: the enderman's own feud
     * (EndermanEntity line 61, ActiveTargetGoal over the endermite,
     * chance 10, checkVisibility true, the isPlayerSpawned predicate —
     * the engine's endermites are all pearl-spawned) locks the mite.
     * Pre-fix RED: the engine's enderman never carried the feud.
     */
    @Test
    void theEndermanHuntsTheEndermite() throws Exception {
        buildPlatform();
        MobEntity enderman = spawnPinned(MobType.ENDERMAN, HUNTER_X, HUNTER_Z);
        enderman.setNoAi(true); // the synchronous scan drives the hunt whole
        MobEntity mite = spawnStatic(MobType.ENDERMITE, HUNTER_X + 8.5,
                HUNTER_Z + 0.5, false);

        MobEntity found = scanUntilSettled(enderman);
        assertSame(mite, found,
                "the enderman's endermite feud (line 61) locks the mite");
    }
}
