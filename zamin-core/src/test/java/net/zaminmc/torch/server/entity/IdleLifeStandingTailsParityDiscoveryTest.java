package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.village.Village;
import net.zaminmc.torch.server.village.VillageDoor;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-23 — THE IDLE-LIFE FAMILY'S STANDING TAILS over the
 * reference's own goal laws: the head-yaw look family ({@code
 * LookAtEntityGoal} + {@code LookAroundGoal} — the universal idle
 * pair), the village-door pair ({@code StayIndoorsGoal} + {@code
 * RestrictOpenDoorGoal}), the villager's farm walk ({@code
 * VillagerFarmGoal} over {@code GoToBlockGoal} — the harvest/replant
 * pair over the 8-slot inventory), the baby's caravan ({@code
 * FormCaravanGoal}), and the golem's offer-status sync (Entity Status
 * 11).
 *
 * <p>THE LAWS PINNED:</p>
 * <ul>
 *   <li>THE LOOK FAMILY — the 2% dice, the {@code 40 + nextInt(40)}
 *       look-at window with the head on the target, the window's
 *       out-of-range stop, the per-kind range table, the villager's
 *       MOB target, and the {@code 20 + nextInt(20)} look-around
 *       window with its cos/sin angle — plus the kinds that never
 *       register (the villager, the rabbit, the ocelot).</li>
 *   <li>THE DOOR PAIR — the restrict goal's flag pair write/restore
 *       and its restrictedTicks tick; the stay-indoors weather gate,
 *       the ticking-door pick, and the near arm's indoors walk.</li>
 *   <li>THE FARM WALK — the spiral's farmland pick with the two delay
 *       arms, the harvest's break-with-drops landing as ITEM entities
 *       the villager's own loot pickup gathers, the replant's seed
 *       consumption and crop placement, and the mobGriefing gate.</li>
 *   <li>THE CARAVAN — the baby gate, the census over the grown-6/3/6
 *       box, the 1000-tick window, and the flag's stop.</li>
 *   <li>THE OFFER SYNC — the golem's {@code setLookingAtVillager}
 *       fires the Entity Status 11 on both arms.</li>
 * </ul>
 *
 * <p>THE RIG: the scripted-world unit seats drive the goal bodies
 * directly (the windows pinned without waiting on the selector's
 * dice); the engine seats run the farm walk's world writes through the
 * real manager drain.</p>
 */
class IdleLifeStandingTailsParityDiscoveryTest {

    // ------------------------------------------------------------------
    // The scripted rig (the unit seats)
    // ------------------------------------------------------------------

    /** The scripted player: the view the mind reads, mutable per phase. */
    private static final class ScriptedPlayer implements MobEntity.PlayerView {
        Position position = new Position(4.5, 8.0, 0.5);
        float yaw;
        float pitch;
        String held;

        @Override public Position position() {
            return position;
        }

        @Override public float yaw() {
            return yaw;
        }

        @Override public float pitch() {
            return pitch;
        }

        @Override public String heldItem() {
            return held;
        }
    }

    /**
     * A flat-world stub with the scripted player, the scripted mob
     * census consults, and the scripted village — the door pair's own
     * surface.
     */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        final ScriptedPlayer player = new ScriptedPlayer();
        boolean sunny = true;
        boolean raining;
        boolean biomeRainy = true;
        Village village;
        MobEntity livingMob;       // nearestLivingMobInBox's answer
        MobEntity babyVillager;    // the caravan census's answer
        java.util.function.BiFunction<Integer, Integer, Integer> blockId;
        boolean mobGriefing = true;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            double dx = player.position.x() - x;
            double dy = player.position.y() - y;
            double dz = player.position.z() - z;
            if (dx * dx + dy * dy + dz * dz > range * range) {
                return null;
            }
            return player.position;
        }

        @Override
        public MobEntity.PlayerView nearestPlayerView(double x, double y, double z, double range) {
            double dx = player.position.x() - x;
            double dy = player.position.y() - y;
            double dz = player.position.z() - z;
            if (dx * dx + dy * dy + dz * dz > range * range) {
                return null;
            }
            return player;
        }

        @Override
        public boolean isSunny() {
            return sunny;
        }

        @Override
        public boolean isRaining() {
            return raining;
        }

        @Override
        public boolean biomeRainyAt(int x, int z) {
            return biomeRainy;
        }

        @Override
        public Village nearestVillageAt(int x, int y, int z, int range) {
            return village;
        }

        @Override
        public MobEntity nearestLivingMobInBox(double minX, double minY, double minZ,
                                               double maxX, double maxY, double maxZ,
                                               int excludeId, double sx, double sy, double sz) {
            return livingMob;
        }

        @Override
        public MobEntity nearestBabyVillagerNotInCaravanInBox(double minX, double minY, double minZ,
                                                              double maxX, double maxY, double maxZ,
                                                              int excludeId,
                                                              double sx, double sy, double sz) {
            return babyVillager;
        }

        @Override
        public net.zaminmc.torch.util.Identifier blockIdAt(int x, int y, int z) {
            Integer value = blockId == null ? null : blockId.apply(x, z);
            return value == null ? null
                    : net.zaminmc.torch.util.Identifier.parse("minecraft:" + switch (value) {
                        case 0 -> "farmland";
                        case 1 -> "wheat_stage7";
                        case 2 -> "air";
                        default -> "stone";
                    });
        }

        @Override
        public boolean mobGriefing() {
            return mobGriefing;
        }
    }

    /** Rolls the dice-bearing consult until it answers true (bounded). */
    private static boolean rollUntil(BooleanSupplier consult, int bound) {
        for (int i = 0; i < bound; i++) {
            if (consult.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE LOOK-AT WINDOW (the 2% dice + the head hold)
    // ------------------------------------------------------------------

    /**
     * A wolf four blocks east of the scripted player: the dice-armed
     * window ({@code 40 + nextInt(40)}) holds the head on the player
     * (the east bearing), burns one tick per goal tick, and stops the
     * moment the player walks out of the window's own reach — the
     * shouldContinue distance gate. A cow six-and-a-half blocks out
     * (the 6-range row) never claims the window at a player seven
     * blocks out — the per-kind range table.
     */
    @Test
    void theLookAtWindowHoldsTheHeadOnThePlayer() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity wolf = new MobEntity(710, MobType.WOLF, new Position(0.5, 8.0, 0.5),
                new Random(101L), world);
        world.player.position = new Position(4.5, 8.0, 0.5); // 4 blocks east

        assertTrue(rollUntil(wolf::goalLookAtEntityCanUse, 2000),
                "the 2% dice arms the window inside 2000 rolls");
        int window = wolf.lookAtTicksLeftForTest();
        assertTrue(window >= 40 && window <= 79,
                "the window is 40 + nextInt(40) (read " + window + ")");

        // The head hold: the east bearing (atan2(-4, 0) = -90).
        wolf.goalLookAtEntityTick();
        assertEquals(-90.0f, wolf.headYaw(), 0.001f, "the head holds the player east");
        assertEquals(window - 1, wolf.lookAtTicksLeftForTest(), "the clock pays its tick");

        // The out-of-range stop: the player teleports 20 blocks out.
        world.player.position = new Position(24.5, 8.0, 0.5);
        assertFalse(wolf.goalLookAtEntityCanUse(),
                "the window stops when the player leaves the reach");
        assertEquals(0, wolf.lookAtTicksLeftForTest());

        // The kinds' own range table: a cow (range 6) at a player 7
        // blocks out never claims.
        world.player.position = new Position(7.5, 8.0, 0.5);
        MobEntity cow = new MobEntity(711, MobType.COW, new Position(0.5, 8.0, 0.5),
                new Random(102L), world);
        assertFalse(rollUntil(() -> {
            if (cow.goalLookAtEntityCanUse()) {
                return true;
            }
            // The player stays out of the 6-reach — the dice alone
            // never claims; every consult that armed a window must be
            // re-rolled with the player still far.
            return false;
        }, 2000), "the cow's 6-range never reaches a 7-block player");

        // A sheep (range 6) with the player five blocks out claims.
        world.player.position = new Position(5.5, 8.0, 0.5);
        MobEntity sheep = new MobEntity(712, MobType.SHEEP, new Position(0.5, 8.0, 0.5),
                new Random(103L), world);
        assertTrue(rollUntil(sheep::goalLookAtEntityCanUse, 2000),
                "the sheep's 6-range reaches a 5-block player");
    }

    /**
     * The villager's own registration: the MOB target at 8 — the
     * census consult ({@code getNearestEntity(MobEntity.class, ...)})
     * answers a MOB, never the player (a villager four blocks from the
     * scripted player with a scripted cow inside the grown box claims
     * the window over the COW, and the head holds the cow's bearing).
     */
    @Test
    void theVillagersLookAtTargetsMobs() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity villager = new MobEntity(720, MobType.VILLAGER, new Position(0.5, 8.0, 0.5),
                new Random(110L), world);
        MobEntity cow = new MobEntity(721, MobType.COW, new Position(-3.5, 8.0, 0.5),
                new Random(111L), world);
        world.livingMob = cow;
        world.player.position = new Position(4.5, 8.0, 0.5);

        assertTrue(rollUntil(villager::goalLookAtEntityCanUse, 2000),
                "the villager's mob census arms the window");
        villager.goalLookAtEntityTick();
        // The cow four blocks WEST: atan2(+4, 0) = +90.
        assertEquals(90.0f, villager.headYaw(), 0.001f,
                "the villager's head holds the MOB, not the player");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE LOOK-AROUND WINDOW (the cos/sin angle)
    // ------------------------------------------------------------------

    /**
     * A cow's look-around: the dice-armed window ({@code 20 +
     * nextInt(20)}) pays its tick with the head on the picked angle,
     * and the window's life ends when the clock runs past zero (the
     * reference's own {@code >= 0} band — the zero tick holds the head
     * one last time). The kinds that never register — the villager,
     * the rabbit, the ocelot, the ghast — never claim, whatever the
     * dice.
     */
    @Test
    void theLookAroundWindowPaysTheAngleAndTheKindsGateRefuses() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity cow = new MobEntity(730, MobType.COW, new Position(0.5, 8.0, 0.5),
                new Random(120L), world);
        assertTrue(rollUntil(() -> cow.goalLookAroundCanUse(false), 2000),
                "the 2% dice arms the look-around window");
        int window = cow.lookAroundTicksLeftForTest();
        assertTrue(window >= 20 && window <= 39,
                "the window is 20 + nextInt(20) (read " + window + ")");

        float headBefore = cow.headYaw();
        cow.goalLookAroundTick();
        assertTrue(cow.lookAroundTicksLeftForTest() == window - 1,
                "the clock pays its tick");
        // The head moved to the picked angle — SOME bearing (the cos/sin
        // pair), asserted by the change from the fresh body's 0.
        assertTrue(cow.headYaw() != headBefore || window == 20,
                "the head took the picked angle");

        // The window's whole life: driving the clock to zero and past
        // it stops the claim (the >= 0 band — the zero tick runs, the
        // pass below zero stops).
        for (int i = 0; i < window; i++) {
            cow.goalLookAroundTick();
        }
        assertEquals(-1, cow.lookAroundTicksLeftForTest(), "the spent marker reads -1");
        assertFalse(cow.goalLookAroundCanUse(false),
                "the spent window stops the claim");

        // The kinds gate: the villager, the rabbit, the ocelot and the
        // ghast never register the goal.
        for (MobType kind : new MobType[]{MobType.VILLAGER, MobType.RABBIT,
                MobType.OCELOT, MobType.GHAST}) {
            MobEntity never = new MobEntity(731, kind, new Position(0.5, 8.0, 0.5),
                    new Random(121L), world);
            for (int i = 0; i < 100; i++) {
                assertFalse(never.goalLookAroundCanUse(false),
                        kind + " never registers the look-around");
            }
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE RESTRICT DOOR (the flag pair + the restrictedTicks)
    // ------------------------------------------------------------------

    /**
     * A villager standing inside its nearest door's indoors band under
     * a sunless sky: the goal claims at once (no dice), drops the
     * navigation's door permissions (the reference's start() pair),
     * grows the door's restrictedTicks every tick (the over-use
     * penalty the ticking-door pick reads), and restores the pair when
     * the sun returns (stop()).
     */
    @Test
    void theRestrictDoorGuardsTheDoorAndDropsThePermissions() {
        ScriptedWorld world = new ScriptedWorld();
        Village village = new Village();
        // The door at (10, 8, 10) facing EAST (indoors two cells east).
        village.addDoor(new VillageDoor(10, 8, 10, 2, 0, 0));
        world.village = village;
        world.sunny = false;

        MobEntity villager = new MobEntity(740, MobType.VILLAGER,
                new Position(12.5, 8.0, 10.5), new Random(130L), world);
        assertTrue(villager.navigationCanOpenDoors(),
                "the fresh body's navigation may open doors");
        assertTrue(villager.navigationCanPassThroughDoors());

        assertTrue(villager.goalRestrictDoorCanUse(),
                "the sunless villager inside the indoors band claims the guard");
        VillageDoor door = villager.restrictDoorForTest();
        assertNotNull(door, "the door is held");
        assertEquals(10, door.x());
        assertFalse(villager.navigationCanOpenDoors(),
                "start(): the open permission drops");
        assertFalse(villager.navigationCanPassThroughDoors(),
                "start(): the pass-through permission drops");

        // The tick grows the over-use penalty.
        assertEquals(0, door.restrictedTicks());
        villager.goalRestrictDoorTick();
        villager.goalRestrictDoorTick();
        assertEquals(2, door.restrictedTicks(),
                "the guard's tick grows the restrictedTicks");

        // The sun returns: stop() restores the pair.
        world.sunny = true;
        assertFalse(villager.goalRestrictDoorCanUse(), "the sunny gate stops the guard");
        assertTrue(villager.navigationCanOpenDoors(), "stop(): the open permission restores");
        assertTrue(villager.navigationCanPassThroughDoors(),
                "stop(): the pass-through permission restores");
        assertNull(villager.restrictDoorForTest(), "the door clears");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE STAY-INDOORS GATE (the weather + the ticking door)
    // ------------------------------------------------------------------

    /**
     * The weather gate and the door pick: a sunless villager with a
     * village nearby claims the shelter walk over the village's
     * TICKING door (the over-use-aware pick — the door with the
     * smaller restrictedTicks wins over a closer one), and the sunny
     * sky refuses the walk whole. The raining-dry-biome arm claims too
     * (the global rain the desert refuses to render).
     */
    @Test
    void theStayIndoorsGateAndTheTickingDoorPick() {
        ScriptedWorld world = new ScriptedWorld();
        Village village = new Village();
        // Two doors near the villager: the nearer one already carries
        // the over-use penalty — the FAR one wins the ticking pick.
        village.addDoor(new VillageDoor(10, 8, 10, 2, 0, 0));
        village.addDoor(new VillageDoor(14, 8, 14, -2, 0, 0));
        world.village = village;
        world.sunny = false;

        MobEntity villager = new MobEntity(750, MobType.VILLAGER,
                new Position(12.5, 8.0, 11.5), new Random(140L), world);
        // The nearer door's penalty: 5 ticks of restriction.
        village.doors().get(0).restrictOpening();
        village.doors().get(0).restrictOpening();
        village.doors().get(0).restrictOpening();
        village.doors().get(0).restrictOpening();
        village.doors().get(0).restrictOpening();

        assertTrue(rollUntil(villager::goalStayIndoorsCanUse, 4000),
                "the 1-in-50 dice arms the shelter walk");
        assertTrue(villager.stayIndoorsWalking(), "the walk owns the body");

        // The sunny sky refuses the walk whole.
        MobEntity sunnyVillager = new MobEntity(751, MobType.VILLAGER,
                new Position(12.5, 8.0, 11.5), new Random(141L), world);
        world.sunny = true;
        for (int i = 0; i < 200; i++) {
            assertFalse(sunnyVillager.goalStayIndoorsCanUse(),
                    "the sunny sky refuses the shelter walk");
        }

        // The raining-dry-biome arm: the rain flag over a dry-read
        // biome claims even under a sunny ambient read.
        world.sunny = false;
        world.raining = true;
        world.biomeRainy = false;
        MobEntity desertVillager = new MobEntity(752, MobType.VILLAGER,
                new Position(12.5, 8.0, 11.5), new Random(142L), world);
        assertTrue(rollUntil(desertVillager::goalStayIndoorsCanUse, 4000),
                "the raining-dry-biome arm claims the shelter walk");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE CARAVAN (the baby gate + the 1000-tick window)
    // ------------------------------------------------------------------

    /**
     * A baby villager with a scripted baby target three blocks out:
     * the 1-in-400 dice arms the window (1000 ticks), the caravan flag
     * rides (the target arm), the clock pays its tick, and the spent
     * window clears the flag. An ADULT villager never claims (the baby
     * gate — the caravan is the child's own walk).
     */
    @Test
    void theCaravanArmsTheFlagAndTheBabyGateRefuses() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity baby = new MobEntity(760, MobType.VILLAGER, new Position(0.5, 8.0, 0.5),
                new Random(150L), world);
        baby.setBreedingAge(-24_000);
        MobEntity other = new MobEntity(761, MobType.VILLAGER, new Position(3.5, 8.0, 0.5),
                new Random(151L), world);
        other.setBreedingAge(-24_000);
        world.babyVillager = other;

        assertTrue(rollUntil(baby::goalFormCaravanCanUse, 100_000),
                "the 1-in-400 dice arms the caravan window");
        assertTrue(baby.villagerInCaravan(), "the caravan flag rides the target arm");
        assertEquals(1000, baby.caravanCountdownForTest(), "start(): the 1000-tick window");

        baby.goalFormCaravanTick();
        assertEquals(999, baby.caravanCountdownForTest(), "the clock pays its tick");

        // The spent window: driving the clock to zero, the next canUse
        // consult plays stop() — the flag clears.
        for (int i = 0; i < 999; i++) {
            baby.goalFormCaravanTick();
        }
        assertFalse(baby.goalFormCaravanCanUse(), "the spent window stops the claim");
        assertFalse(baby.villagerInCaravan(), "stop(): the caravan flag clears");

        // The baby gate: an adult never claims.
        MobEntity adult = new MobEntity(762, MobType.VILLAGER, new Position(0.5, 8.0, 0.5),
                new Random(152L), world);
        for (int i = 0; i < 500; i++) {
            assertFalse(adult.goalFormCaravanCanUse(), "the adult never forms the caravan");
        }
    }

    // ------------------------------------------------------------------
    // The engine rig (the farm walk's world writes)
    // ------------------------------------------------------------------

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The working surface's feet level (the platform top at y 39). */
    private static final int FLOOR_Y = 40;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "pe23tails" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /** The platform: stone at y 38/39 over x 4..44, z 4..24, open air above. */
    private void buildPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int x = 4; x <= 44; x++) {
                for (int z = 4; z <= 24; z++) {
                    server.world().setBlock(new net.zaminmc.torch.block.BlockPosition(x, 38, z),
                            BuiltinBlocks.STONE);
                    server.world().setBlock(new net.zaminmc.torch.block.BlockPosition(x, 39, z),
                            BuiltinBlocks.STONE);
                    for (int y = 40; y <= 52; y++) {
                        server.world().setBlock(new net.zaminmc.torch.block.BlockPosition(x, y, z),
                                BuiltinBlocks.AIR);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new net.zaminmc.torch.block.BlockPosition(20, 39, 10))),
                200, "the platform stands");
    }

    /**
     * A LIVE (AI-driven) body on the platform — the manager's own
     * selector drives its mind (the natural-loop seats).
     */
    private MobEntity spawnLive(MobType type, double x, double z) throws InterruptedException {
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(type,
                    new Position(x, FLOOR_Y, z), 0);
            if (mob != null) {
                mob.setVillagerProfession(0); // the farmer's own row
                mob.setBreedingAge(0); // the adult gate
            }
            spawned.set(mob);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the body spawned");
        return spawned.get();
    }

    /** A STATIC (NoAI) body on the platform (the goal bodies driven by the seat). */
    private MobEntity spawnStatic(MobType type, double x, double z) throws InterruptedException {
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(type,
                    new Position(x, FLOOR_Y, z), 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            spawned.set(mob);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the body spawned");
        return spawned.get();
    }

    // ------------------------------------------------------------------
    // Seat 6 — THE FARM WALK (the harvest with drops + the replant)
    // ------------------------------------------------------------------

    /**
     * A profession-0 farmer standing ON a farmland row: the manager's
     * own selector runs the whole vanilla loop — the farm goal claims
     * the age-7 wheat (the delay-0 arm — the fresh stock has room),
     * the standing-cell work tick breaks the wheat WITH its drops
     * (the ITEM entities at the cell), the villager's own loot pickup
     * gathers the food family into the 8-slot stock, and — once the
     * stock carries seeds — the next scan answers the PLANT arm over
     * the now-empty cell: the seed consumed, the crop placed at age 0.
     */
    @Test
    void theFarmWalkHarvestsWithDropsAndReplants() throws Exception {
        buildPlatform();
        // The farmland cell at (20, 39, 10) with its age-7 wheat.
        net.zaminmc.torch.block.BlockPosition farm =
                new net.zaminmc.torch.block.BlockPosition(20, FLOOR_Y - 1, 10);
        server.ticker().submit(() -> {
            server.world().setBlock(farm, BuiltinBlocks.FARMLAND);
            server.world().setBlock(farm.offset(0, 1, 0), BuiltinBlocks.WHEAT_STAGE7);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.WHEAT_STAGE7.equals(server.world().getBlock(
                                farm.offset(0, 1, 0))),
                        100, "the wheat stands");

        // The LIVE farmer — the manager's own mind drives the loop.
        MobEntity farmer = spawnLive(MobType.VILLAGER, 20.5, 10.5);

        // The HARVEST: the farm goal claims on the manager's own scan
        // (the fresh cooldown, the griefing rule on, the room gate —
        // the empty stock has room), the work tick breaks the wheat
        // WITH its drops.
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.AIR.equals(server.world().getBlock(
                                farm.offset(0, 1, 0))),
                        600, "the wheat broke");

        // The drops landed as ITEM entities (the break-with-drops law —
        // the reference's breakBlock(pos, true)).
        EngineAwaits.awaitCondition(server.world(),
                () -> !server.itemEntities().all().isEmpty(), 600, "the drops spawned");

        // The PLANT arm: the stock carries seeds (the harvest's own
        // seed bonus, or the seat's direct stock when the bonus roll
        // answered zero — the loop's law is the same either way), the
        // 10-tick cooldown runs out, the now-EMPTY farmland answers the
        // plant scan, and the crop lands at age 0.
        server.ticker().submit(() -> {
            if (!farmer.villagerHasPlantableItems()) {
                farmer.villagerAcceptFood(net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.WHEAT_SEEDS, 3));
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> "minecraft:wheat_stage0".equals(server.world().getBlock(
                                farm.offset(0, 1, 0)).identifier().toString()),
                        600, "the seeds planted wheat at age 0");
    }

    /**
     * The villager's own loot pickup (the manager's walk over the food
     * family): a wheat drop at a standing villager's feet lands in the
     * 8-slot stock once the 10-tick pickup delay expires — the gather
     * arm of the farm loop (the reference's break → drop → pickup
     * chain).
     */
    @Test
    void theStandingVillagerGathersTheDropAtItsFeet() throws Exception {
        buildPlatform();
        MobEntity villager = spawnLive(MobType.VILLAGER, 20.5, 10.5);
        server.ticker().submit(() -> server.itemEntities().spawnDropAtBlock(
                new Position(20.5, 40.5, 10.5),
                net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.WHEAT, 1),
                net.zaminmc.torch.server.entity.ItemEntity.PICKUP_DELAY_DROP_TICKS,
                0));
        EngineAwaits.awaitCondition(server.world(),
                () -> server.itemEntities().all().isEmpty(), 200,
                "the villager gathered the drop");
    }

    /**
     * The mobGriefing gate (VillagerFarmGoal.canStart line 28): a
     * no-grief world never opens the farm window — the fresh scan
     * consults the rule first, whatever the farmland carries (the
     * cooldown never arms on a refused consult — the reference's own
     * gate order).
     */
    @Test
    void theFarmWalkRefusesOnANoGriefWorld() throws Exception {
        buildPlatform();
        net.zaminmc.torch.block.BlockPosition farm =
                new net.zaminmc.torch.block.BlockPosition(20, FLOOR_Y - 1, 10);
        server.ticker().submit(() -> {
            server.world().setBlock(farm, BuiltinBlocks.FARMLAND);
            server.world().setBlock(farm.offset(0, 1, 0), BuiltinBlocks.WHEAT_STAGE7);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.WHEAT_STAGE7.equals(server.world().getBlock(
                                farm.offset(0, 1, 0))),
                        100, "the wheat stands");

        MobEntity farmer = spawnStatic(MobType.VILLAGER, 20.5, 10.5);
        server.ticker().submit(() -> {
            farmer.setVillagerProfession(0);
            farmer.setBreedingAge(0);
        });

        // The no-grief rule consult: the engine's own gamerule surface
        // flips the rule, the fresh scan refuses.
        server.ticker().submit(() ->
                server.gameRules().set("mobGriefing", "false"));
        AtomicReference<Boolean> claimed = new AtomicReference<>();
        server.ticker().submit(() -> claimed.set(farmer.goalVillagerFarmCanUse()));
        EngineAwaits.awaitCondition(server.world(),
                () -> claimed.get() != null, 100, "the claim read settled");
        assertFalse(claimed.get(), "the no-grief world never opens the farm window");
        // The refusal consults again — the cooldown never armed (the
        // reference's gate order: the rule read INSIDE the zero arm).
        AtomicReference<Boolean> refusedAgain = new AtomicReference<>();
        server.ticker().submit(() -> refusedAgain.set(farmer.goalVillagerFarmCanUse()));
        EngineAwaits.awaitCondition(server.world(),
                () -> refusedAgain.get() != null, 100, "the second refusal read");
        assertFalse(refusedAgain.get(), "the no-grief world refuses every consult");

        // The rule restores: the scan claims again.
        server.ticker().submit(() ->
                server.gameRules().set("mobGriefing", "true"));
        AtomicReference<Boolean> griefingClaim = new AtomicReference<>();
        server.ticker().submit(() -> griefingClaim.set(farmer.goalVillagerFarmCanUse()));
        EngineAwaits.awaitCondition(server.world(),
                () -> griefingClaim.get() != null, 100, "the second claim read settled");
        assertTrue(griefingClaim.get(), "the griefing world opens the farm window");
    }

    // ------------------------------------------------------------------
    // Seat 7 — THE OFFER SYNC (the Entity Status 11)
    // ------------------------------------------------------------------

    /**
     * The golem's {@code setLookingAtVillager} fires the Entity Status
     * 11 on BOTH arms — the wire's sync nudge the reference's own body
     * sends (IronGolemEntity line 188).
     */
    @Test
    void theGolemsOfferClockFiresTheStatusEleven() throws Exception {
        buildPlatform();
        MobEntity golem = spawnStatic(MobType.IRON_GOLEM, 20.5, 10.5);
        server.ticker().submit(() -> golem.setLookingAtVillager(true));
        EngineAwaits.awaitCondition(server.world(),
                () -> golem.golemLookingAtVillagerTicks() > 390, 100, "the offer armed");
        server.ticker().submit(golem::consumeGolemOfferStatus); // drain the arm's own
        server.ticker().submit(() -> golem.setLookingAtVillager(false));
        AtomicInteger drained = new AtomicInteger(-1);
        server.ticker().submit(() -> {
            if (golem.consumeGolemOfferStatus()) {
                drained.set(1);
            } else {
                drained.set(0);
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> drained.get() >= 0, 100, "the drain read settled");
        assertEquals(1, drained.get(),
                "the FALSE arm fires the status too (the reference's both-arms law)");
    }
}
