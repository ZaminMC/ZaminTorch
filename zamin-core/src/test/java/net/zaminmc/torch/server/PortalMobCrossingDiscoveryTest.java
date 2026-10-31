package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-006 DISCOVERY SEAT (docs/vanilla-conformance-audit.md,
 * the twenty-fourth pass): the mob's portal crossing — the engine's mobs
 * carry no portal walk at all.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the portal arm is ENTITY-level,
 * not player-level. Every ticked body runs it — the chain
 * {@code MobEntity.baseTick} (line 150-151) → {@code LivingEntity.baseTick}
 * (line 189-191) → {@code Entity.baseTick} (lines 268-330, the portal arm
 * 282-313). A plain mob (not riding, not ridden) in portal cells:
 * {@code PortalBlock.onEntityCollision} (lines 158-162) calls
 * {@code onPortalCollision} (the riding gate passes); the cooldown consult
 * (expired) arms {@code inPortal}; the crossing arm then runs
 * {@code this.vehicle == null && this.portalTime++ >= getMaxNetherPortalTime()}
 * — the BASE Entity's override returns {@code 0} (lines 354-355), so a mob
 * crosses on the FIRST armed tick (contrast the player's 80,
 * PlayerEntity line 300). {@code Entity.changeDimension} (lines 1695-1732)
 * walks the mob across: the world swap, the 8:1 reposition
 * (PlayerManager.changeDimension lines 449-480 — the same scale math as
 * the player), the forcer's placement, the body recreated in the
 * destination world. The cooldown arms at the BASE {@code getPortalCooldown()}
 * = 300 (lines 1457-1459; the player's own override is 10). The NoAI pin
 * does NOT hold the crossing: {@code isNoAi} gates the AI SECTION alone
 * (LivingEntity.tick line 1435's {@code isLocallyControlled()} consult over
 * MobEntity line 872's {@code && !this.isNoAi()}); the portal arm sits in
 * baseTick, before and outside the ai section — a NoAI body still crosses.</p>
 *
 * <p>THE ENGINE'S SHAPE: {@code MobEntity.tick}'s portal row (the
 * portalCooldown decrement) round-trips the NBT field ALONE — its own
 * comment says it outright ("the engine's mobs carry no portal walk of
 * their own (the world's portal envelope), the row round-trips for the
 * NBT surface"). {@code MobManager} has no dimension-change walk at all.
 * A mob standing in portal cells never crosses; the zombie chasing a
 * player through a portal stops at the frame.</p>
 *
 * <p>THE SEAT: a NoAI cow stands INSIDE the overworld frame's interior
 * (its feet on the bottom obsidian row — the deterministic construction:
 * the NoAI pin holds the mind alone, the physics still grounds the body;
 * animals never despawn, and no daylight burn). The nether frame is
 * PRE-BUILT at the crossing's scaled destination (40/8 = 5) so the
 * reference's forcer walk lands the body deterministically inside its
 * cells. THE REFERENCE LAW: the body crosses on the first armed tick —
 * the dimension flips to -1 and the arrival stands inside the pre-built
 * nether frame's interior. This seat is EXPECTED RED on develop until
 * VT-006's correction lands. Do not re-pin it to the engine's current
 * answer; the finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalMobCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "mobcross" + Math.abs(new java.util.Random().nextInt(100000)),
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

    /** The session join (the VT-005 seat's own harness: the stub link). */
    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var accepted = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    /** The 4x5 X-plane frame (the DimensionWalk test's own build shape). */
    private static void buildPortal(EngineWorld world, int x, int y, int z) {
        for (int w = 0; w < 4; w++) {
            for (int h = -1; h < 4; h++) {
                boolean frameCell = w == 0 || w == 3 || h == -1 || h == 3;
                world.setBlock(new BlockPosition(x + w, y + h, z),
                        frameCell ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
            }
        }
    }

    /**
     * VT-006: a mob standing in the portal cells crosses on the first
     * armed tick — the reference's entity-level portal arm (the base
     * threshold 0) — and lands inside the destination frame's interior
     * through the same forcer walk the player rides.
     */
    @Test
    void theMobStandingInThePortalCellsCrosses() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);

        // The overworld portal (the crossing's source): a 4x5 X-plane
        // frame at (40, 20, 40) — the DimensionWalk shape.
        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildPortal(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");

        // The PRE-BUILT nether frame AT THE SCALED DESTINATION (40/8 = 5):
        // the reference's forcer walk (PlayerManager.changeDimension's
        // 8:1 scale + the portal forcer's placement) finds THIS frame, so
        // the arrival lands deterministically inside its cells.
        int netherX = 5;
        int netherZ = 5;
        int netherY = 20;
        for (int cx = (netherX - 4) >> 4; cx <= (netherX + 4) >> 4; cx++) {
            for (int cz = (netherZ - 4) >> 4; cz <= (netherZ + 4) >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!nether.isChunkLoaded(at)) {
                    server.requestChunkLoad(nether.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.isChunkLoaded(new ChunkPosition(netherX >> 4, netherZ >> 4)),
                200, "the nether frame chunk loaded");
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 6; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -2; dy <= 5; dy++) {
                        nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ + dz),
                                nether.airType());
                    }
                }
            }
            buildPortal(nether, netherX, netherY, netherZ);
        });
        await(() -> nether.getBlock(new BlockPosition(netherX + 1, netherY, netherZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the pre-built nether frame stands");

        // The body: a NoAI cow standing INSIDE the frame's interior, its
        // feet on the bottom obsidian row — the deterministic construction
        // (the NoAI pin holds the mind alone; the physics still grounds
        // the body; the animals never despawn; no daylight burn). The
        // reference's own law: the NoAI pin does NOT hold the portal
        // crossing — the arm sits in baseTick, outside the ai section.
        var spawned = new java.util.concurrent.atomic.AtomicReference<net.zaminmc.torch.server.entity.MobEntity>();
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity cow =
                    server.mobs().spawnAt(MobType.COW,
                            new Position(overX + 1.5, overY, overZ + 0.5));
            if (cow != null) {
                cow.setNoAi(true);
            }
            spawned.set(cow);
        });
        await(() -> spawned.get() != null, "the cow spawned in the portal interior");
        net.zaminmc.torch.server.entity.MobEntity cow = spawned.get();

        // The seat's precondition: the body's feet cell is a portal cell.
        BlockPosition feet = new BlockPosition(
                (int) Math.floor(cow.position().x()),
                (int) Math.floor(cow.position().y()),
                (int) Math.floor(cow.position().z()));
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(feet))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(feet)),
                "the seat's precondition: the body stands inside a portal cell ("
                        + feet + ", got " + overworld.getBlock(feet).identifier()
                        + ", position " + cow.position() + ")");
        assertEquals(0, cow.dimension(), "the body is in the overworld at the spawn");

        // THE CROSSING: the reference's law — the body crosses on the
        // FIRST armed tick (the base Entity's threshold 0), landing inside
        // the pre-built nether frame's interior through the same forcer
        // walk the player rides (the 8:1 scale, the nearest-frame
        // placement). THE ENGINE'S SHAPE: no portal walk exists for mobs —
        // the body stands in the cells forever.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> cow.dimension() == -1, 100, "the mob's portal crossing");
        assertTrue(crossed,
                "VT-006: the mob standing in the portal cells crosses on the first "
                        + "armed tick (the reference's entity-level portal arm — the "
                        + "base threshold 0, Entity lines 354-355; the crossing walk "
                        + "Entity.changeDimension lines 1695-1732)");

        // The arrival: the forcer's placement lands the body inside the
        // pre-built nether frame's interior band (the same placement law
        // the player's walk pins — DimensionWalkTest's own shape).
        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(cow.position().x()),
                (int) Math.floor(cow.position().y()),
                (int) Math.floor(cow.position().z()));
        assertTrue(arrival.x() >= netherX && arrival.x() <= netherX + 3
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 3
                        && arrival.z() >= netherZ - 1 && arrival.z() <= netherZ + 1,
                "VT-006: the arrival lands inside the pre-built nether frame's band ("
                        + arrival + ", position " + cow.position() + ")");
        // The body lives in the NETHER's mob list now (the world swap —
        // the reference's removeEntity + the destination's addEntity).
        assertTrue(server.mobs().all().stream().anyMatch(
                        m -> m.entityId() == cow.entityId() && m.dimension() == -1),
                "VT-006: the crossed body carries the nether's dimension stamp");
    }

    /**
     * VT-006's cooldown pin: the arrival standing INSIDE the destination
     * frame's cells never re-fires — the reference's re-arm consult
     * (Entity.onPortalCollision lines 1429-1430: a colliding body whose
     * cooldown is live resets it to the full 300) pins the cooldown while
     * the body stands in the cells, exactly the law that holds the
     * player's own arrival (VT-004's record). A broken re-arm would decay
     * the cooldown one tick at a time and bounce the body back at ~300
     * ticks — the window catches it.
     */
    @Test
    void theArrivalStandingInTheDestinationFrameNeverRefires() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);

        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildPortal(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");

        int netherX = 5;
        int netherZ = 5;
        int netherY = 20;
        for (int cx = (netherX - 4) >> 4; cx <= (netherX + 4) >> 4; cx++) {
            for (int cz = (netherZ - 4) >> 4; cz <= (netherZ + 4) >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!nether.isChunkLoaded(at)) {
                    server.requestChunkLoad(nether.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.isChunkLoaded(new ChunkPosition(netherX >> 4, netherZ >> 4)),
                200, "the nether frame chunk loaded");
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 6; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -2; dy <= 5; dy++) {
                        nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ + dz),
                                nether.airType());
                    }
                }
            }
            buildPortal(nether, netherX, netherY, netherZ);
        });
        await(() -> nether.getBlock(new BlockPosition(netherX + 1, netherY, netherZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the pre-built nether frame stands");

        var spawned = new java.util.concurrent.atomic.AtomicReference<net.zaminmc.torch.server.entity.MobEntity>();
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity cow =
                    server.mobs().spawnAt(MobType.COW,
                            new Position(overX + 1.5, overY, overZ + 0.5));
            if (cow != null) {
                cow.setNoAi(true);
            }
            spawned.set(cow);
        });
        await(() -> spawned.get() != null, "the cow spawned in the portal interior");
        net.zaminmc.torch.server.entity.MobEntity cow = spawned.get();

        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> cow.dimension() == -1, 100, "the mob's portal crossing");
        assertTrue(crossed, "VT-006: the crossing must land before the hold is pinned");

        // The arrival stands INSIDE the pre-built frame's cells (the
        // forcer's placement) — the cooldown consult must hold it there
        // past the full 300-tick cooldown span (a decayed cooldown would
        // bounce the body back to the overworld at the span's end).
        boolean bounced = EngineAwaits.awaitConditionWithin(server.world(),
                () -> cow.dimension() == 0, 350,
                "the arrival's bounce-back (must never land)");
        assertTrue(!bounced,
                "VT-006: the arrival standing in the destination frame never re-fires "
                        + "(the reference's cooldown re-arm, onPortalCollision lines "
                        + "1429-1430 — the 300 store pinned while colliding)");
        assertEquals(-1, cow.dimension(), "the body still lives in the nether");
    }

    /**
     * VT-006's riding-gate pin (VT-005's law at the entity level): the
     * reference's gated collision (PortalBlock.onEntityCollision lines
     * 158-162, vehicle == null && rider == null) never arms a mounted
     * body — a pig carrying a rider stands in the portal cells without
     * crossing. The window sits BELOW the survival rider's own 80-tick
     * stand (the player's walk is VT-005's own lane, PR #94) so this
     * seat pins the MOUNT's arm alone.
     */
    @Test
    void theRiddenMountStandingInThePortalCellsHolds() throws Exception {
        EngineWorld overworld = server.world();
        PlayerSession rider = join("PigRider");

        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildPortal(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");

        // The construction is ordered so the mount NEVER stands free in
        // the cells (an unmounted body crosses on the first armed tick —
        // the first seat's own law): the pig spawns at a NEUTRAL spot,
        // the real rider takes the seat through the manager's own seam
        // (the interact path's call — the VT-005 seat's shape), and only
        // THEN the mounted body moves into the portal interior (the
        // plain teleport — the reference's setPositionAndAngles idiom).
        var spawned = new java.util.concurrent.atomic.AtomicReference<net.zaminmc.torch.server.entity.MobEntity>();
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity pig =
                    server.mobs().spawnAt(MobType.PIG,
                            new Position(overX + 10.5, overY, overZ + 0.5));
            if (pig != null) {
                server.mobs().mountMob(pig, rider.engineEntityId(),
                        rider.name(), rider.uuid().toString());
                pig.teleport(new Position(overX + 1.5, overY, overZ + 0.5));
            }
            spawned.set(pig);
        });
        await(() -> spawned.get() != null, "the mounted pig placed in the portal interior");
        net.zaminmc.torch.server.entity.MobEntity pig = spawned.get();
        await(() -> pig.hasRider(), "the rider holds the pig's seat (the anchor landed)");

        BlockPosition feet = new BlockPosition(
                (int) Math.floor(pig.position().x()),
                (int) Math.floor(pig.position().y()),
                (int) Math.floor(pig.position().z()));
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(feet))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(feet)),
                "the seat's precondition: the mount stands inside a portal cell ("
                        + feet + ", got " + overworld.getBlock(feet).identifier()
                        + ", position " + pig.position() + ")");

        // THE HOLD: the ridden mount never crosses — the gated collision
        // never arms. An ungated arm would cross the body on the FIRST
        // armed tick (the base threshold 0), so the 60-tick window —
        // below the rider's own 80-tick stand — pins the gate cleanly.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> pig.dimension() == -1, 60,
                "the ridden mount's crossing (must never land)");
        assertTrue(!crossed,
                "VT-006/VT-005: the ridden mount standing in the portal cells never "
                        + "crosses (the reference's gated collision, PortalBlock "
                        + ".onEntityCollision lines 158-162 — rider == null)");
        assertEquals(0, pig.dimension(), "the mount stays in the overworld");
        assertTrue(pig.hasRider(), "the seat survives the window (no ghost dismount)");
    }
}
