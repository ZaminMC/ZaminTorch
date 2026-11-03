package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-11 — the follow-owner walk's TELEPORT CONSULT over the Phase E
 * boundary arms: the reference's {@code FollowOwnerGoal.tick} lines 96-111
 * — the 5x5 RING scan (the center 3x3 refused) with the floor read
 * through {@code World.hasSolidTop}'s five arms (the support-consult
 * law, World.java lines 1740-1752: the solid-blocking cubes, the TOP
 * halves of the stairs and slabs, the hoppers, the 7-layer snow — a
 * FENCE floor never holds) and the two body cells through
 * {@code canTeleportTo}'s AIR-or-not-cube law (lines 81-85: {@code
 * block == Blocks.AIR || !block.isCube()} — a FENCE or PANE cell IS
 * teleportable, the reference's own quirk: the pet blinks into the
 * partial shape and the physics resolves).
 *
 * <p>The pre-fix consult sampled the cells' center points for the
 * COLLISION question — a fence floor wrongly held (its 1.5-tall box
 * contains the point) and a fence body cell wrongly refused (the
 * material reads solid), so the pet never landed where vanilla lands
 * it and landed where vanilla refuses. Both arms RED pre-fix; the
 * plain-stone control green by design.</p>
 *
 * <p>The rig: a 2-thick stone platform (the walls of a 3-deep pit hold
 * whole), the pet sealed in the pit — no walkable neighbor, so the
 * pathfinder's own start-node fallback answers null and the reference's
 * {@code moveTo} fails — the owner 14 blocks out on the platform, past
 * both the 10-block start band and the 12-block teleport gate. The
 * tamed wolf, standing, its mind live: the follow claims on the first
 * pass and the re-path clock consults the ring immediately.</p>
 */
class FollowOwnerTeleportParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The platform's surface cells (the bodies stand at y = 40). */
    private static final int FLOOR_Y = 39;

    /** The pit column: the pet's feet at 38, the walls 2-high around. */
    private static final int PIT_X = 24;
    private static final int PIT_Z = 10;

    /** The owner's standing cell (the ring centers on floor(owner) - 2). */
    private static final int OWNER_X = 10;
    private static final int OWNER_Z = 10;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ownteleport" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                200, name + " playing");
        return session;
    }

    /**
     * The platform: stone at y = 38 and 39 for the working area (the
     * 2-thickness is the pit's own wall law — the pet 3 deep sees no
     * step-up), air above through y = 45. The ring's cells: the floors
     * at y = 39, the bodies at y = 40, the heads at y = 41.
     */
    private void buildPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int x = 4; x <= 28; x++) {
                for (int z = 4; z <= 16; z++) {
                    server.world().setBlock(new BlockPosition(x, 38, z),
                            BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, 39, z),
                            BuiltinBlocks.STONE);
                    for (int y = 40; y <= 45; y++) {
                        server.world().setBlock(new BlockPosition(x, y, z),
                                BuiltinBlocks.AIR);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(OWNER_X, 39, OWNER_Z)))
                        && BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(OWNER_X, 38, OWNER_Z))),
                100, "the platform stands");
    }

    /**
     * The pit: the column at (PIT_X, 38..39, PIT_Z) carved to air with
     * the floor pinned to stone at 37 — the pet's feet at y = 38, the
     * platform's own 38/39 cells as its 2-high walls (no step-up: the
     * surface standing cells sit +2 over the pit floor), no walkable
     * neighbor at all, so the pathfinder's start-only fallback answers
     * null and the reference's moveTo fails.
     */
    private void digPit() throws InterruptedException {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(PIT_X, 37, PIT_Z),
                    BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(PIT_X, 38, PIT_Z),
                    BuiltinBlocks.AIR);
            server.world().setBlock(new BlockPosition(PIT_X, 39, PIT_Z),
                    BuiltinBlocks.AIR);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.AIR.equals(server.world().getBlock(
                                new BlockPosition(PIT_X, 39, PIT_Z)))
                        && BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(PIT_X, 37, PIT_Z))),
                100, "the pit carved");
    }

    /**
     * The pet: a tamed wolf in the pit, standing, its mind live — the
     * follow claims on the first pass (the owner 14 blocks out clears
     * the 10-block start band; dx = 14, dy = -2, squared 200 past the
     * 144 teleport gate).
     */
    private MobEntity spawnTamedWolf(PlayerSession owner) throws InterruptedException {
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity wolf = server.mobs().spawnAt(MobType.WOLF,
                    new Position(PIT_X + 0.5, 38.0, PIT_Z + 0.5), 0);
            if (wolf == null) {
                return;
            }
            wolf.tameWolf(owner.name(), owner.uuid().toString());
            wolf.setWolfSitting(false);
            spawned.set(wolf);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the wolf spawned");
        return spawned.get();
    }

    /** The wolf's squared distance to the owner's standing point. */
    private static double distanceSquaredToOwner(MobEntity wolf) {
        double dx = wolf.position().x() - (OWNER_X + 0.5);
        double dy = wolf.position().y() - 40.0;
        double dz = wolf.position().z() - (OWNER_Z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    /** @return whether the wolf still stands at its pit's own level. */
    private static boolean stillInThePit(MobEntity wolf) {
        // The follow's walk keeps pressing the pet into the pit's wall
        // (the look-driven heading with no path), so the pit COLUMN is the
        // honest band — never the exact spawn point.
        return Math.abs(wolf.position().x() - (PIT_X + 0.5)) <= 0.5
                && wolf.position().y() == 38.0
                && Math.abs(wolf.position().z() - (PIT_Z + 0.5)) <= 0.5;
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE CONTROL: the plain stone ring (green by design)
    // ------------------------------------------------------------------

    /**
     * The plain platform: every ring floor a solid-blocking cube, every
     * body and head cell air — the first ring cell (the owner's cell
     * minus 2 on both axes) passes every consult and the pet blinks to
     * its center. Both engines agree; the seat pins the landing's own
     * band (near the owner, on the platform, out of the pit).
     */
    @Test
    void thePlainStoneRingTeleports() throws Exception {
        buildPlatform();
        digPit();
        PlayerSession owner = join("PlainOwner");
        server.teleportPlayer(owner, new Position(OWNER_X + 0.5, 40, OWNER_Z + 0.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> owner.position().y() == 40.0, 200, "the owner standing");
        MobEntity wolf = spawnTamedWolf(owner);

        EngineAwaits.awaitCondition(server.world(),
                () -> !stillInThePit(wolf), 100,
                "the plain stone ring teleports the pet out of the pit");
        assertTrue(distanceSquaredToOwner(wolf) < 36.0,
                "the pet landed within 6 blocks of the owner (the ring's own band), at "
                        + wolf.position());
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE FENCE FLOOR: hasSolidTop's refusal (RED pre-fix)
    // ------------------------------------------------------------------

    /**
     * The 16 ring FLOOR cells carry oak fences: vanilla's {@code
     * World.hasSolidTop} refuses the non-cube whole (the five arms —
     * the fence answers none of them), so NO ring cell passes and the
     * pet NEVER teleports, however long the window. Pre-fix RED: the
     * collision-sample consult read the fence's 1.5-tall box as a floor
     * and the pet blinks onto the fence ring.
     */
    @Test
    void theFenceFloorNeverTeleports() throws Exception {
        buildPlatform();
        digPit();
        // The ring's 16 border floor cells (y = 39) become fences.
        server.ticker().submit(() -> {
            for (int l = 0; l <= 4; l++) {
                for (int i1 = 0; i1 <= 4; i1++) {
                    if (l < 1 || i1 < 1 || l > 3 || i1 > 3) { // the border alone
                        server.world().setBlock(
                                new BlockPosition(OWNER_X - 2 + l, 39, OWNER_Z - 2 + i1),
                                BuiltinBlocks.FENCE);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.FENCE.equals(server.world().getBlock(
                                new BlockPosition(OWNER_X - 2, 39, OWNER_Z - 2))),
                100, "the fence ring placed");
        PlayerSession owner = join("FenceOwner");
        server.teleportPlayer(owner, new Position(OWNER_X + 0.5, 40, OWNER_Z + 0.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> owner.position().y() == 40.0, 200, "the owner standing");
        MobEntity wolf = spawnTamedWolf(owner);

        // The never-window: 150 ticks of re-path cadence (the 10-tick
        // clock consults the ring 15 times over) — the pet never moves.
        EngineAwaits.awaitTicks(server.world(), 150,
                "the fence-floor never window");
        assertTrue(stillInThePit(wolf) && distanceSquaredToOwner(wolf) > 100.0,
                "the fence floor never holds the teleport (hasSolidTop's five arms"
                        + " refuse the non-cube) — the pet stayed in its pit, at "
                        + wolf.position());
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE FENCE CELL: canTeleportTo's not-cube acceptance
    // ------------------------------------------------------------------

    /**
     * The 16 ring BODY cells (y = 40) carry oak fences over the plain
     * stone floors: vanilla's {@code canTeleportTo} answers TRUE for
     * the non-cube ({@code !block.isCube()}), so the first ring cell
     * passes and the pet blinks INTO the fence's cell — the reference's
     * own quirk (the body overlaps the partial shape; the physics
     * resolves). Pre-fix RED: the collision-sample consult refused the
     * fence's solid material at the body cell, no ring cell passed, and
     * the pet never teleported at all.
     */
    @Test
    void theFenceCellIsTeleportable() throws Exception {
        buildPlatform();
        digPit();
        server.ticker().submit(() -> {
            for (int l = 0; l <= 4; l++) {
                for (int i1 = 0; i1 <= 4; i1++) {
                    if (l < 1 || i1 < 1 || l > 3 || i1 > 3) { // the border alone
                        server.world().setBlock(
                                new BlockPosition(OWNER_X - 2 + l, 40, OWNER_Z - 2 + i1),
                                BuiltinBlocks.FENCE);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.FENCE.equals(server.world().getBlock(
                                new BlockPosition(OWNER_X - 2, 40, OWNER_Z - 2))),
                100, "the fence body ring placed");
        PlayerSession owner = join("CellOwner");
        server.teleportPlayer(owner, new Position(OWNER_X + 0.5, 40, OWNER_Z + 0.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> owner.position().y() == 40.0, 200, "the owner standing");
        MobEntity wolf = spawnTamedWolf(owner);

        EngineAwaits.awaitCondition(server.world(),
                () -> !stillInThePit(wolf), 100,
                "the fence body cell accepts the teleport (the not-cube law)");
        assertTrue(distanceSquaredToOwner(wolf) < 36.0,
                "the pet landed in the fence ring's own band (the physics may"
                        + " resolve the overlap sideways), at " + wolf.position());
    }
}
