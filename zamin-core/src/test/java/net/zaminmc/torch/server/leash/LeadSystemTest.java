package net.zaminmc.torch.server.leash;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leash system (Slice 11c — the reference port of item/LeadItem +
 * entity/decoration/LeadKnotEntity + MobEntity's leash arms +
 * PathFinderMobEntity.updateLeashStatus): the player's hold (the
 * right-click attach + the consume, the re-click release + the drop), the
 * hostile refusal, the fence tie (no consume — the knot created on the
 * first taker, reused after), the knot's own interact (the tie with a
 * lead, the removal without one — creative releasing without the drop),
 * the holder-gone release, the follow beyond four blocks, the pull
 * beyond six, and the snap at ten with the drop.
 */
class LeadSystemTest {

    // ------------------------------------------------------------------
    // The standalone rig (the system + the fake surfaces)
    // ------------------------------------------------------------------

    private static MobEntity.WorldQuery worldAt() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    /** The drop ledger (the lead's landings). */
    private static final class Drops {
        final List<Position> at = new ArrayList<>();

        boolean anyNear(double x, double z, double radius) {
            for (Position p : at) {
                if (Math.abs(p.x() - x) <= radius && Math.abs(p.z() - z) <= radius) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Rig(MobManager mobs, LeadSystem leads, Drops drops, Wire wire) {
    }

    /** The wire event ledger. */
    private static final class Wire implements LeadSystem.Listener {
        int knotSpawns;
        int knotRemovals;
        int attaches;
        int detaches;

        @Override public void onKnotSpawned(LeashKnot knot) { knotSpawns++; }
        @Override public void onKnotRemoved(LeashKnot knot) { knotRemovals++; }
        @Override public void onMobAttached(MobEntity mob) { attaches++; }
        @Override public void onMobDetached(MobEntity mob) { detaches++; }
    }

    private Rig rig() {
        MobManager mobs = new MobManager(worldAt(), new Random(11), (position, stack) -> { },
                2_000_000, (x, z) -> 4);
        Drops drops = new Drops();
        Wire wire = new Wire();
        LeadSystem leads = new LeadSystem(new LeadSystem.Mobs() {
            @Override
            public List<MobEntity> mobsIn(double minX, double minY, double minZ,
                                            double maxX, double maxY, double maxZ) {
                List<MobEntity> found = new ArrayList<>();
                for (MobEntity mob : mobs.all()) {
                    Position at = mob.position();
                    if (at.x() >= minX - mob.type().width
                            && at.x() <= maxX + mob.type().width
                            && at.z() >= minZ - mob.type().width
                            && at.z() <= maxZ + mob.type().width
                            && at.y() >= minY && at.y() <= maxY + mob.type().height) {
                        found.add(mob);
                    }
                }
                return found;
            }

            @Override
            public void dropLead(Position at) {
                drops.at.add(at);
            }
        }, 5_000_000);
        leads.addListener(wire);
        leads.setSurvivalRead(new World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) {
                return null; // no fence: the survival poll fails
            }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }

            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }

        }, BuiltinBlocks.FENCE.identifier());
        return new Rig(mobs, leads, drops, wire);
    }

    private PlayerSession player(String name, double x, double z, GameMode mode) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.applyMovement(new Position(x, 4.0, z), Rotation.ZERO, true);
        session.setGamemode(mode);
        return session;
    }

    private World stubWorld() {
        return new World() {
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
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }

            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }

        };
    }

    private static void holdLead(PlayerSession player, int count) {
        player.inventory().setSlot(0, ItemStack.of(BuiltinItems.LEAD, count));
        player.inventory().selectHotbarSlot(0);
    }

    // ------------------------------------------------ the player's hold

    @Test
    void theRightClickAttachesTheMobAndConsumesOneLead() {
        Rig rig = rig();
        PlayerSession player = player("Holder", 10.5, 10.5, GameMode.SURVIVAL);
        MobEntity cow = rig.mobs().spawnAt(MobType.COW, new Position(11.5, 4.0, 10.5));
        holdLead(player, 2);
        assertTrue(rig.leads().interactMobWithLead(player, cow),
                "the lead click consumed the interact");
        assertTrue(cow.isLeashed(), "the cow carries the lead");
        assertEquals(player, cow.leashHolderPlayer());
        assertEquals(1, player.inventory().held().count(), "one unit left the stack");
        assertEquals(1, rig.wire().attaches, "the wire attach fired");
        // The re-click releases (the drop in survival).
        assertTrue(rig.leads().interactMobWithLead(player, cow));
        assertFalse(cow.isLeashed());
        assertEquals(1, rig.wire().detaches);
        assertTrue(rig.drops().anyNear(cow.position().x(), cow.position().z(), 2.0),
                "the lead dropped at the mob");
    }

    @Test
    void theHostileRefusesTheLeadAndTheCreativeKeepsIt() {
        Rig rig = rig();
        PlayerSession player = player("Tamer", 10.5, 10.5, GameMode.SURVIVAL);
        MobEntity zombie = rig.mobs().spawnAt(MobType.ZOMBIE, new Position(11.5, 4.0, 10.5));
        holdLead(player, 1);
        assertFalse(rig.leads().interactMobWithLead(player, zombie),
                "the monster family refuses (isTameable's gate)");
        assertFalse(zombie.isLeashed());
        assertEquals(1, player.inventory().held().count(), "no consume on the refusal");
        // The creative hand pays nothing on the attach.
        PlayerSession creative = player("Artist", 10.5, 10.5, GameMode.CREATIVE);
        MobEntity cow = rig.mobs().spawnAt(MobType.COW, new Position(11.5, 4.0, 10.5));
        holdLead(creative, 1);
        assertTrue(rig.leads().interactMobWithLead(creative, cow));
        assertEquals(1, creative.inventory().held().count(), "creative never consumes");
        // The creative release drops nothing.
        assertTrue(rig.leads().interactMobWithLead(creative, cow));
        assertFalse(rig.drops().anyNear(cow.position().x(), cow.position().z(), 4.0),
                "the creative release keeps the lead");
    }

    // ------------------------------------------------ the fence tie

    @Test
    void theFenceTieCreatesTheKnotWithoutConsumingTheLead() {
        Rig rig = rig();
        PlayerSession player = player("Farmer", 10.5, 10.5, GameMode.SURVIVAL);
        MobEntity cow = rig.mobs().spawnAt(MobType.COW, new Position(11.5, 4.0, 10.5));
        holdLead(player, 3);
        rig.leads().interactMobWithLead(player, cow);
        var fence = new net.zaminmc.torch.block.BlockPosition(10, 4, 10);
        int afterAttach = player.inventory().held().count();
        assertTrue(rig.leads().useLeadOnFence(player, fence),
                "the tie found the held mob");
        assertFalse(cow.isLeashed() && cow.leashHolderPlayer() == player,
                "the player no longer holds it");
        assertNotNull(cow.leashHolderKnot(), "the knot holds it now");
        assertEquals(afterAttach, player.inventory().held().count(),
                "the fence tie never consumes (the mob already paid)");
        assertEquals(2, afterAttach, "the attach itself consumed exactly one");
        assertEquals(1, rig.wire().knotSpawns, "the knot's Spawn Object fired");
        // The second tie at the same fence reuses the knot.
        MobEntity cow2 = rig.mobs().spawnAt(MobType.COW, new Position(12.5, 4.0, 10.5));
        rig.leads().interactMobWithLead(player, cow2);
        rig.leads().useLeadOnFence(player, fence);
        assertEquals(1, rig.wire().knotSpawns, "no second knot at the same fence");
        assertEquals(fence, rig.leads().knotAt(fence).fence());
    }

    // ------------------------------------------------ the knot's interact

    @Test
    void theKnotTiesWithALeadAndRemovesWithAnEmptyHand() {
        Rig rig = rig();
        PlayerSession player = player("Clicker", 10.5, 10.5, GameMode.SURVIVAL);
        MobEntity cow = rig.mobs().spawnAt(MobType.COW, new Position(11.5, 4.0, 10.5));
        var fence = new net.zaminmc.torch.block.BlockPosition(10, 4, 10);
        // The empty hand: the knot removes, the tied mob releases with the
        // drop through the holder-gone arm.
        holdLead(player, 1);
        rig.leads().interactMobWithLead(player, cow);
        rig.leads().useLeadOnFence(player, fence);
        LeashKnot knot = rig.leads().knotAt(fence);
        assertNotNull(knot);
        player.inventory().setSlot(0, ItemStack.EMPTY);
        rig.leads().interactKnot(player, knot);
        assertNull(rig.leads().knotAt(fence), "the knot removed");
        assertEquals(1, rig.wire().knotRemovals);
        // The tied mob still holds the gone knot: the tick walk releases it.
        rig.leads().tick(rig.mobs().all(), p -> p.position(), uuid -> java.util.Optional.empty());
        assertFalse(cow.isLeashed(), "the holder-gone arm released the mob");
        assertTrue(rig.drops().anyNear(cow.position().x(), cow.position().z(), 3.0),
                "the lead dropped");
    }

    // ------------------------------------------------ the physics

    @Test
    void theLeashPullsBeyondSixAndSnapsAtTen() {
        Rig rig = rig();
        PlayerSession player = player("Walker", 10.5, 10.5, GameMode.SURVIVAL);
        MobEntity cow = rig.mobs().spawnAt(MobType.COW, new Position(13.5, 4.0, 10.5));
        holdLead(player, 1);
        rig.leads().interactMobWithLead(player, cow);
        double before = cow.position().x();
        // At 3 blocks: the slack — no pull, no follow, no snap.
        rig.leads().tick(List.of(cow), p -> p.position(), uuid -> java.util.Optional.empty());
        assertEquals(before, cow.position().x(), 1e-9, "inside the slack nothing moves");
        assertTrue(cow.isLeashed(), "the slack holds the leash");
        // The holder walks to 8 blocks: the follow band (no snap yet).
        player.applyMovement(new Position(18.5, 4.0, 10.5), Rotation.ZERO, true);
        rig.leads().tick(List.of(cow), p -> p.position(), uuid -> java.util.Optional.empty());
        assertTrue(cow.isLeashed(), "eight blocks only follows — no snap");
        // The holder warps past ten: the snap.
        player.applyMovement(new Position(24.5, 4.0, 10.5), Rotation.ZERO, true);
        rig.leads().tick(List.of(cow), p -> p.position(), uuid -> java.util.Optional.empty());
        assertFalse(cow.isLeashed(), "the leash snapped at ten");
        assertTrue(rig.drops().anyNear(cow.position().x(), cow.position().z(), 4.0),
                "the snapped lead dropped");
    }

    @Test
    void theGoneHolderReleasesWithTheDrop() {
        Rig rig = rig();
        PlayerSession player = player("Leaver", 10.5, 10.5, GameMode.SURVIVAL);
        MobEntity cow = rig.mobs().spawnAt(MobType.COW, new Position(11.5, 4.0, 10.5));
        holdLead(player, 1);
        rig.leads().interactMobWithLead(player, cow);
        // The holder dies: the tick walk's holder-gone arm.
        player.markDead();
        rig.leads().tick(rig.mobs().all(), p -> p.position(), uuid -> java.util.Optional.empty());
        assertFalse(cow.isLeashed(), "a dead holder holds nothing");
        assertTrue(rig.drops().anyNear(cow.position().x(), cow.position().z(), 3.0));
    }

    // ------------------------------------------------ the knot's survival

    @Test
    void theKnotFallsWhenItsFenceIsGone() {
        Rig rig = rig();
        PlayerSession player = player("Breaker", 10.5, 10.5, GameMode.SURVIVAL);
        MobEntity cow = rig.mobs().spawnAt(MobType.COW, new Position(11.5, 4.0, 10.5));
        holdLead(player, 1);
        rig.leads().interactMobWithLead(player, cow);
        var fence = new net.zaminmc.torch.block.BlockPosition(10, 4, 10);
        rig.leads().useLeadOnFence(player, fence);
        // The stub world has no fence: the survival poll fails at tick 100
        // (the reference's own cadence — the knot hangs up to five seconds).
        for (int i = 0; i < 99; i++) {
            rig.leads().tick(rig.mobs().all(), p -> p.position(), uuid -> java.util.Optional.empty());
        }
        assertNotNull(rig.leads().knotAt(fence), "still hanging at 99 ticks");
        rig.leads().tick(rig.mobs().all(), p -> p.position(), uuid -> java.util.Optional.empty());
        assertNull(rig.leads().knotAt(fence), "the 100-tick poll dropped it");
        // The tied mob still holds the gone knot: the release rides the
        // NEXT tick's holder-gone walk (the mobs loop runs before the
        // knots' survival poll inside one tick).
        assertTrue(cow.isLeashed(), "the fall tick left the mob holding");
        rig.leads().tick(rig.mobs().all(), p -> p.position(), uuid -> java.util.Optional.empty());
        assertFalse(cow.isLeashed());
        assertTrue(rig.drops().anyNear(cow.position().x(), cow.position().z(), 4.0));
    }

    // ------------------------------------------------ the live engine (the wiring)

    @Test
    void theEngineTiesTheLeadToTheFence() throws Exception {
        EngineServer server = boot();
        try {
            ClientLink link = new ClientLink() {
                @Override public boolean isActive() { return true; }
                @Override public void kick(String reason) { }
            };
            var result = server.joinRequest(link, "Engineer",
                    UUID.nameUUIDFromBytes("Engineer".getBytes()));
            PlayerSession player = ((EngineBridge.Accepted) result).session();
            server.ticker().submit(player::markPlaying);
            await(() -> player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the test player reached PLAYING");
            server.ticker().submit(() -> {
                var mob = server.mobs().spawnAt(MobType.COW,
                        new Position(1.5, 40.0, 0.5));
                holdLead(player, 1);
                server.leads().interactMobWithLead(player, mob);
                var fence = new net.zaminmc.torch.block.BlockPosition(0, 40, 0);
                server.world().setBlock(fence, net.zaminmc.torch.server.block.BuiltinBlocks.FENCE);
                server.leads().useLeadOnFence(player, fence);
            });
            var fence = new net.zaminmc.torch.block.BlockPosition(0, 40, 0);
            await(() -> knotAtOnTick(server, fence) != null,
                    "the knot tied at the fence");
            // The engine's world has the fence: the knot survives the poll.
            assertTrue(knotAtOnTick(server, fence) != null,
                    "the engine's fence holds the knot");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A knot read completed on the tick thread (the submit-and-wait-to-run
     * pattern: the plain submit-and-poll reads a variable nobody keeps).
     */
    private static LeashKnot knotAtOnTick(EngineServer server,
                                           net.zaminmc.torch.block.BlockPosition fence) {
        AtomicBoolean ran = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<LeashKnot> found =
                new java.util.concurrent.atomic.AtomicReference<>();
        server.ticker().submit(() -> {
            try {
                found.set(server.leads().knotAt(fence));
            } finally {
                ran.set(true);
            }
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return found.get();
    }

    // ------------------------------------------------ the harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "lead" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @TempDir
    Path dataDir;
}
