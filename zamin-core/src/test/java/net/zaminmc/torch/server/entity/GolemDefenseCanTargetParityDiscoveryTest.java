package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE DEFENSE CONSULT'S canTarget DEPTH (Wave PE-31 —
 * TrackTargetGoal.canTarget lines 123-147 over
 * TrackIronGolemTargetGoal's own {@code super(golem, false, TRUE)}):
 * the goal's arms carry the isTarget gates (canAttack's ghast
 * refusal, the invulnerable player), THE VILLAGE BAND
 * (isValidGoalTarget at the target's own cell — the TRUNCATED
 * {@code (int)(radius * 0.6F)} cached band, IronGolemEntity line 74),
 * and THE PATH CONSULT (checkCanNavigate's shared-cache window) — and
 * the standing writes hold under shouldContinue's own gates (the
 * follow-range band, the team refusal, the invulnerable player). The
 * pre-fix engine read only the team gate: every seat here is RED on
 * it (the outside-band attacker, the razor-thin ring, the walled-off
 * attacker, the ghast, the creative player, the fleeing target).
 */
class GolemDefenseCanTargetParityDiscoveryTest {

    /**
     * The flat stub world (the CombatTargetTailsTest rig's shape) with
     * the WALL arm the unreachable seat needs (an impassable
     * full-height band at a column — the path consult's own refusal
     * geometry) and the mob-aware entity-position read the forced-hunt
     * window consults.
     */
    private static final class DefenseWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();
        final Map<String, String> teams = new HashMap<>();
        /** The impassable wall's column (x) — Integer.MIN_VALUE when no wall stands. */
        int wallX = Integer.MIN_VALUE;
        /** The one player position the nearest scan answers, and her team. */
        Position playerAt;
        String playerTeam;
        /** The stub player's engine id (the forced hunt's position read). */
        int playerEntityId = -1;
        /** The player ids the surface's liveness walk answers for. */
        final List<Integer> playerIds = new ArrayList<>();
        /** The player positions by engine id (the surface's row + hunt reads). */
        final Map<Integer, Position> playerPositions = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            if (Math.floor(y) < 8.0) {
                return true;
            }
            if (wallX != Integer.MIN_VALUE && (int) Math.floor(x) == wallX
                    && Math.floor(y) < 13.0) {
                return true; // the impassable band (a 5-high wall, every z)
            }
            return false;
        }

        @Override
        public List<MobEntity> hostileMobsInBox(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            List<MobEntity> found = new ArrayList<>();
            for (MobEntity mob : mobs) {
                if (mob.dead() || !mob.type().hostile) {
                    continue;
                }
                Position p = mob.position();
                double half = mob.type().width / 2.0;
                if (p.x() + half > minX && p.x() - half < maxX
                        && p.y() + mob.type().height > minY && p.y() < maxY
                        && p.z() + half > minZ && p.z() - half < maxZ) {
                    found.add(mob);
                }
            }
            return found;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return inRange(playerAt, x, y, z, range);
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range,
                                      String excludeTeam) {
            Position found = nearestPlayer(x, y, z, range);
            if (found != null && excludeTeam != null && excludeTeam.equals(playerTeam)) {
                return null;
            }
            return found;
        }

        @Override
        public String teamNameOf(String member) {
            return teams.get(member);
        }

        @Override
        public Position entityPositionById(int entityId) {
            Position player = playerPositions.get(entityId);
            if (player != null) {
                return player;
            }
            for (MobEntity mob : mobs) {
                if (mob.entityId() == entityId) {
                    return mob.position();
                }
            }
            return null;
        }
    }

    /** The village surface (the PE-29 rig's shape): the door map, the liveness + position reads. */
    private static final class DefenseSurface implements net.zaminmc.torch.server.village.VillageSystem.Surface {
        final DefenseWorld world;
        final List<Integer> playerIds;

        DefenseSurface(DefenseWorld world, List<Integer> playerIds) {
            this.world = world;
            this.playerIds = playerIds;
        }

        @Override public boolean isWoodenDoor(int x, int y, int z) { return false; }
        @Override public net.zaminmc.torch.block.Direction doorFacing(int x, int y, int z) {
            return net.zaminmc.torch.block.Direction.SOUTH;
        }
        @Override public boolean hasSkyAccess(int x, int y, int z) { return true; }
        @Override public int countVillagersIn(double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) { return 0; }
        @Override public int countGolemsIn(double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) { return 0; }
        @Override public void spawnGolem(Position at) { }
        @Override public boolean solidAt(int x, int y, int z) { return y < 8; }
        @Override public boolean entityAlive(int engineId) {
            if (playerIds.contains(engineId)) {
                return true; // the production walk resolves players first
            }
            for (MobEntity mob : world.mobs) {
                if (mob.entityId() == engineId) {
                    return !mob.dead();
                }
            }
            return false;
        }
        @Override public double[] entityPosition(int engineId) {
            Position player = world.playerPositions.get(engineId);
            if (player != null) {
                return new double[] {player.x(), player.y(), player.z()};
            }
            for (MobEntity mob : world.mobs) {
                if (mob.entityId() == engineId) {
                    Position p = mob.position();
                    return new double[] {p.x(), p.y(), p.z()};
                }
            }
            return null;
        }
        @Override public double[] playerPosition(String name) { return null; }
        @Override public int engineIdOfPlayer(String name) { return -1; }
    }

    private static Position inRange(Position p, double x, double y, double z, double range) {
        if (p == null) {
            return null;
        }
        double dx = p.x() - x;
        double dy = p.y() - y;
        double dz = p.z() - z;
        return dx * dx + dy * dy + dz * dz > range * range ? null : p;
    }

    /** The seat's own rig: the world + surface + manager + the village at (2,8,2), radius 32 (one door), band 19. */
    private static final class Rig {
        final DefenseWorld world = new DefenseWorld();
        final MobManager manager;
        final net.zaminmc.torch.server.village.VillageSystem system;
        final net.zaminmc.torch.server.village.Village village;

        Rig(long seed) {
            DefenseSurface surface = new DefenseSurface(world, world.playerIds);
            system = new net.zaminmc.torch.server.village.VillageSystem(
                    surface, new Random(seed));
            village = new net.zaminmc.torch.server.village.Village();
            village.addDoor(new net.zaminmc.torch.server.village.VillageDoor(
                    2, 8, 2, net.zaminmc.torch.block.Direction.SOUTH, 0));
            system.villages().add(village);
            class NoLoot implements MobManager.LootSink {
                @Override public void spawnLootDrop(Position position,
                        net.zaminmc.torch.item.ItemStack stack, int dimension) { }
            }
            manager = new MobManager(world, new Random(seed + 1L), new NoLoot(),
                    980_000, (x, z) -> 8);
            manager.setVillageSystem(system);
        }

        MobEntity golemAt(double x, double z) {
            MobEntity golem = manager.spawnAt(MobType.IRON_GOLEM, new Position(x, 8.0, z));
            world.mobs.add(golem);
            return golem;
        }

        MobEntity mobAt(MobType kind, double x, double z) {
            MobEntity mob = manager.spawnAt(kind, new Position(x, 8.0, z));
            world.mobs.add(mob);
            return mob;
        }
    }

    /**
     * SEAT 1 — THE VILLAGE BAND (isValidGoalTarget,
     * PathFinderMobEntity lines 46-48): an attacker whose own cell
     * sits outside the golem's cached {@code (int)(radius * 0.6F)}
     * band NEVER answers the defense consult — a 32-radius village's
     * band is 19, and the zombie 25 east of the golem (its cell 23
     * blocks from the center) stands outside it. The control: the
     * zombie 10 east (its cell 8 from the center) answers. RED on the
     * pre-fix engine (no band consult — both answered).
     */
    @Test
    void theAttackerOutsideTheVillageBandNeverAnswersTheDefense() {
        Rig rig = new Rig(30L);
        MobEntity golem = rig.golemAt(0.5, 0.5);
        MobEntity far = rig.mobAt(MobType.ZOMBIE, 25.5, 0.5);
        rig.village.addOrUpdateAttacker(far.entityId(), false, "ZOMBIE");

        rig.manager.tick(List.of(), 6000L);
        assertNull(golem.mobAttackTarget(),
                "isValidGoalTarget: the attacker outside the cached band never"
                        + " answers (a 32-radius village's band is the int 19)");
        assertNull(golem.forcedHuntPosition(), "no player arm ran");

        // The control: the zombie INSIDE the band answers (its cell 8
        // blocks from the center, the route flat and open).
        MobEntity near = rig.mobAt(MobType.ZOMBIE, 10.5, 0.5);
        rig.village.addOrUpdateAttacker(near.entityId(), false, "ZOMBIE");
        rig.manager.tick(List.of(), 6050L);
        assertSame(near, golem.mobAttackTarget(),
                "the attacker inside the band answers the consult");
    }

    /**
     * SEAT 2 — THE TRUNCATED INT BAND (IronGolemEntity line 74's
     * {@code setVillagePosAndRadius(center, (int)(radius * 0.6F))}):
     * the razor-thin ring. A zombie whose cell sits at EXACTLY the
     * truncated band's edge — squared 361, the 19-cell ring — is
     * refused ({@code 361 < 361} false); the pre-fix engine's plain
     * double band 19.2 would take it ({@code 361 < 368.64}). The
     * control one cell inside (squared 324) answers.
     */
    @Test
    void theCachedBandIsTheTruncatedIntForm() {
        Rig rig = new Rig(31L);
        MobEntity golem = rig.golemAt(0.5, 0.5);
        // The center (2,8,2): the ring cell (21,8,2) — dx 19, squared 361.
        MobEntity ring = rig.mobAt(MobType.ZOMBIE, 21.5, 2.5);
        rig.village.addOrUpdateAttacker(ring.entityId(), false, "ZOMBIE");

        rig.manager.tick(List.of(), 6000L);
        assertNull(golem.mobAttackTarget(),
                "the razor-thin ring at squared 361 is OUTSIDE the int band 19"
                        + " (361 < 361 false) — the double 19.2 band wrongly took it");

        // The control: one cell inside — squared 324 — answers.
        MobEntity inside = rig.mobAt(MobType.ZOMBIE, 20.5, 2.5);
        rig.village.addOrUpdateAttacker(inside.entityId(), false, "ZOMBIE");
        rig.manager.tick(List.of(), 6050L);
        assertSame(inside, golem.mobAttackTarget(),
                "the cell inside the truncated band answers");
    }

    /**
     * SEAT 3 — THE PATH CONSULT (checkCanNavigate, TrackTargetGoal
     * lines 132-144): an attacker inside the band but behind an
     * impassable wall never answers — the shared-cache verdict (the
     * route's end node within squared-2.25 of the target's cell)
     * refuses the consult. The control: the same geometry with no
     * wall answers. RED on the pre-fix engine (no path consult — the
     * walled-off attacker answered).
     */
    @Test
    void theWalledOffAttackerNeverAnswersTheDefense() {
        Rig rig = new Rig(32L);
        rig.world.wallX = 5; // the impassable band between the golem and the attacker
        MobEntity golem = rig.golemAt(0.5, 0.5);
        MobEntity walled = rig.mobAt(MobType.ZOMBIE, 10.5, 0.5);
        rig.village.addOrUpdateAttacker(walled.entityId(), false, "ZOMBIE");

        rig.manager.tick(List.of(), 6000L);
        assertNull(golem.mobAttackTarget(),
                "checkCanNavigate: the attacker behind the impassable wall never"
                        + " answers (no route's end node sits at the target's cell)");
        assertNull(golem.forcedHuntPosition(), "no player arm ran");

        // The wall gone: the same attacker answers once the shared-cache
        // window expires (the refused verdict stands its own 10 + nextInt(5)
        // cooldown — the reference's own quirk, kept verbatim; the seat
        // waits the window out).
        rig.world.wallX = Integer.MIN_VALUE;
        boolean answered = false;
        for (int i = 0; i < 20 && !answered; i++) {
            rig.manager.tick(List.of(), 6050L + i);
            answered = golem.mobAttackTarget() == walled;
        }
        assertTrue(answered,
                "the same attacker behind no wall answers the consult once the"
                        + " navigate window expires");
    }

    /**
     * SEAT 4 — canAttack's GHAST refusal (IronGolemEntity lines
     * 134-136: {@code type != CreeperEntity.class && super.canAttack}
     * over MobEntity line 126's {@code != GhastEntity.class}) and the
     * INVULNERABLE PLAYER (isTarget lines 116-118: {@code !isPlayer &&
     * abilities.invulnerable} — the engine's creative/spectator pair):
     * neither answers the consult. RED on the pre-fix engine (any mob
     * attacker answered; the creative player hunted whole).
     */
    @Test
    void theGhastAndTheInvulnerablePlayerNeverAnswerTheDefense() {
        Rig rig = new Rig(33L);
        MobEntity golem = rig.golemAt(0.5, 0.5);
        MobEntity ghast = rig.mobAt(MobType.GHAST, 10.5, 0.5);
        rig.village.addOrUpdateAttacker(ghast.entityId(), false, "GHAST");

        rig.manager.tick(List.of(), 6000L);
        assertNull(golem.mobAttackTarget(),
                "canAttack refuses the ghast (MobEntity line 126) — the defense"
                        + " consult never answers it");

        // The creative player attacker: the invulnerable refusal (the
        // engine's creative mode is the abilities pair).
        PlayerSession creative = stubPlayer("Creative", new Position(6.5, 8.0, 0.5), 9_001);
        creative.setGamemode(net.zaminmc.torch.GameMode.CREATIVE);
        rig.world.playerIds.add(creative.engineEntityId());
        rig.world.playerPositions.put(creative.engineEntityId(), creative.position());
        rig.village.addOrUpdateAttacker(creative.engineEntityId(), true, null);

        rig.manager.tick(List.of(), 6050L);
        assertNull(golem.forcedHuntPosition(),
                "isTarget's invulnerable refusal: the creative player never"
                        + " answers the consult");
        assertNull(golem.mobAttackTarget(), "the ghast still never answers");
    }

    /**
     * SEAT 5 — THE WINDOW'S OWN GATES (shouldContinue, TrackTargetGoal
     * lines 36-66 — the defense goal (band 1) and the revenge goal
     * (band 2) share the law): a standing write clears when the target
     * flees beyond the FOLLOW RANGE (16) or joins the golem's team.
     * The pre-fix engine held the write until the target's death alone.
     */
    @Test
    void theStandingWriteClearsWhenTheTargetFleesOrTeams() {
        Rig rig = new Rig(34L);
        MobEntity golem = rig.golemAt(0.5, 0.5);
        MobEntity zombie = rig.mobAt(MobType.ZOMBIE, 10.5, 0.5);
        rig.village.addOrUpdateAttacker(zombie.entityId(), false, "ZOMBIE");

        rig.manager.tick(List.of(), 6000L);
        assertSame(zombie, golem.mobAttackTarget(), "the write lands inside the band");

        // THE FLEE: the zombie beyond the follow range (30 blocks) — the
        // window clears (stop()'s own setAttackTarget(null)).
        zombie.teleport(new Position(30.5, 8.0, 0.5));
        rig.manager.tick(List.of(), 6050L);
        assertNull(golem.mobAttackTarget(),
                "shouldContinue's follow-range band: the fleeing target's write"
                        + " clears (the pre-fix engine held it forever)");

        // The re-acquisition: the zombie back inside the band answers again.
        zombie.teleport(new Position(10.5, 8.0, 0.5));
        rig.manager.tick(List.of(), 6100L);
        assertSame(zombie, golem.mobAttackTarget(), "the consult re-acquires");

        // THE TEAM JOIN: the zombie joins the golem's team — the window clears.
        rig.world.teams.put(golem.getUuid().toString(), "red");
        rig.world.teams.put(zombie.getUuid().toString(), "red");
        rig.manager.tick(List.of(), 6150L);
        assertNull(golem.mobAttackTarget(),
                "shouldContinue's same-team refusal clears the standing write");
    }

    /**
     * SEAT 6 — THE PLAYER WINDOW (the forced hunt's own shouldContinue
     * gates): a standing player write clears when the player turns
     * creative or flees beyond the follow range. The pre-fix engine
     * held the write until another write replaced it.
     */
    @Test
    void thePlayerWriteClearsOnCreativeOrTheFlee() {
        Rig rig = new Rig(35L);
        MobEntity golem = rig.golemAt(0.5, 0.5);
        PlayerSession player = stubPlayer("Wounded", new Position(6.5, 8.0, 0.5), 9_002);
        rig.world.playerIds.add(player.engineEntityId());
        rig.world.playerPositions.put(player.engineEntityId(), player.position());
        rig.village.addOrUpdateAttacker(player.engineEntityId(), true, null);
        List<PlayerSession> players = List.of(player);

        rig.manager.tick(players, 6000L);
        assertNotNull(golem.forcedHuntPosition(),
                "the survival player attacker inside the band answers the consult");

        // THE CREATIVE SWITCH: the invulnerable gate clears the write.
        player.setGamemode(net.zaminmc.torch.GameMode.CREATIVE);
        rig.manager.tick(players, 6050L);
        assertNull(golem.forcedHuntPosition(),
                "shouldContinue's invulnerable gate: the creative switch clears"
                        + " the standing player write");

        // Back to survival: the consult re-acquires.
        player.setGamemode(net.zaminmc.torch.GameMode.SURVIVAL);
        rig.manager.tick(players, 6100L);
        assertNotNull(golem.forcedHuntPosition(), "the consult re-acquires");

        // THE FLEE: the player beyond the follow range — the write clears
        // (the session's own position moves, the surface's row read with it).
        Position fled = new Position(40.5, 8.0, 0.5);
        player.applyMovement(fled, Rotation.ZERO, true);
        rig.world.playerPositions.put(player.engineEntityId(), fled);
        rig.manager.tick(players, 6150L);
        assertNull(golem.forcedHuntPosition(),
                "shouldContinue's follow-range band clears the fleeing player's write");
    }

    // ------------------------------------------------------------------
    // The player stub (the CombatTargetTailsTest harness's shape)
    // ------------------------------------------------------------------

    private PlayerSession stubPlayer(String name, Position at, int engineId) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                java.util.UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(new World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() {
                return java.util.List.of();
            }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(
                    net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        }, Position.ZERO);
        session.markPlaying();
        session.applyMovement(at, Rotation.ZERO, true);
        session.assignEngineEntityId(engineId);
        return session;
    }
}
