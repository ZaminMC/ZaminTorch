package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.entity.LeashHolderKind;
import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leash family's public API surface (§27.3 — the former NOT AUDITED
 * row): the {@link LivingEntity} handle's leash reads (leashed, the
 * holder kind, the player's uuid, the knot's fence) and the unleash
 * verb — routed through the engine's own LeadSystem detach walk (the
 * wire clear + the lead drop), exercised over the live engine through
 * the reference's own attach paths.
 */
class LeashApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "lsa" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    /** A lead item entity near the position (the drop assertion's probe). */
    private boolean leadDroppedNear(double x, double y, double z) {
        for (var drop : server.itemEntities().all()) {
            var at = drop.position();
            if (drop.stack().type().equals(BuiltinItems.LEAD)
                    && Math.abs(at.x() - x) < 3.0 && Math.abs(at.y() - y) < 3.0
                    && Math.abs(at.z() - z) < 3.0) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theLeashReadsCarryTheHolderArms() throws Exception {
        // Spawn the cow near the holder (the engine's own path).
        AtomicReference<LivingEntity> cow = new AtomicReference<>();
        server.ticker().submit(() -> {
            var mob = server.mobs().spawnAt(
                    MobType.COW, new Position(10.5, 4.0, 10.5));
            cow.set(mob);
        });
        await(() -> cow.get() != null, "the cow spawned");
        LivingEntity body = cow.get();

        // The unleashed state: the reads answer the NONE shape.
        assertFalse(body.leashed(), "the fresh cow carries no lead");
        assertEquals(LeashHolderKind.NONE, body.leashHolderKind(), "the holder kind is none");
        assertTrue(body.leashHolderPlayerUuid().isEmpty(), "no holding player");
        assertTrue(body.leashKnotPosition().isEmpty(), "no holding knot");

        // The reference's own attach path: a lead in hand + the mob's
        // right-click arm (LeadSystem.interactMobWithLead).
        PlayerSession holder = join("Holder", new Position(10.5, 4.0, 10.5));
        server.ticker().submit(() -> {
            holder.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.LEAD, 1));
            holder.inventory().selectHotbarSlot(0);
        });
        await(() -> !holder.inventory().held().isEmpty(), "the holder holds the lead");
        server.ticker().submit(() ->
                server.leads().interactMobWithLead(holder, (net.zaminmc.torch.server.entity.MobEntity) body));
        await(() -> body.leashed(), "the cow carries the lead");

        // The reads answer the PLAYER shape with the holder's uuid.
        assertEquals(LeashHolderKind.PLAYER, body.leashHolderKind(), "the player holds it");
        assertEquals(java.util.Optional.of(holder.uuid()), body.leashHolderPlayerUuid(),
                "the holder's uuid reads back");
        assertTrue(body.leashKnotPosition().isEmpty(), "no knot in the player form");

        // The public unleash with the drop: the lead item lands at the mob.
        server.world().execute(() -> body.unleash(true));
        await(() -> !body.leashed(), "the unleash cleared the leash");
        assertEquals(LeashHolderKind.NONE, body.leashHolderKind(), "the kind is none again");
        assertTrue(body.leashHolderPlayerUuid().isEmpty(), "the holder cleared");
        await(() -> leadDroppedNear(10.5, 4.0, 10.5), "the lead item dropped at the mob");
    }

    @Test
    void theKnotTieAndTheCreativeUnleashRideThePublicSurface() throws Exception {
        AtomicReference<LivingEntity> pig = new AtomicReference<>();
        server.ticker().submit(() -> {
            var mob = server.mobs().spawnAt(
                    MobType.PIG, new Position(20.5, 4.0, 20.5));
            pig.set(mob);
        });
        await(() -> pig.get() != null, "the pig spawned");
        LivingEntity body = pig.get();

        // The fence tie (the reference's use-on-fence arm): the player
        // holds the pig, then ties it to a placed fence.
        PlayerSession farmer = join("Farmer", new Position(20.5, 4.0, 20.5));
        var fence = new net.zaminmc.torch.block.BlockPosition(21, 4, 20);
        server.ticker().submit(() -> {
            server.world().setBlock(fence, BuiltinBlocks.FENCE);
            farmer.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.LEAD, 2));
            farmer.inventory().selectHotbarSlot(0);
        });
        await(() -> !farmer.inventory().held().isEmpty(), "the farmer holds the lead");
        server.ticker().submit(() ->
                server.leads().interactMobWithLead(farmer, (net.zaminmc.torch.server.entity.MobEntity) body));
        await(() -> body.leashed(), "the pig carries the lead");
        server.ticker().submit(() ->
                server.leads().useLeadOnFence(farmer, fence));
        await(() -> body.leashHolderKind() == LeashHolderKind.KNOT,
                "the knot holds the pig now");

        // The reads answer the KNOT shape with the fence's position.
        assertEquals(java.util.Optional.of(fence), body.leashKnotPosition(),
                "the knot's fence reads back");
        assertTrue(body.leashHolderPlayerUuid().isEmpty(), "no player in the knot form");

        // The creative-style unleash (no drop): nothing lands.
        server.world().execute(() -> body.unleash(false));
        await(() -> !body.leashed(), "the unleash cleared the leash");
        assertEquals(LeashHolderKind.NONE, body.leashHolderKind(), "the kind is none again");
        assertTrue(body.leashKnotPosition().isEmpty(), "the knot cleared");
        // Give the item walk a beat: no lead item may land (the no-drop arm).
        Thread.sleep(300);
        assertFalse(leadDroppedNear(20.5, 4.0, 20.5), "the creative release drops nothing");
    }

    @Test
    void theOffLaneUnleashFailsDiagnosably() throws Exception {
        AtomicReference<LivingEntity> sheep = new AtomicReference<>();
        server.ticker().submit(() -> {
            var mob = server.mobs().spawnAt(
                    MobType.SHEEP, new Position(30.5, 4.0, 30.5));
            sheep.set(mob);
        });
        await(() -> sheep.get() != null, "the sheep spawned");
        LivingEntity body = sheep.get();

        // Attach through the engine's own path (a holder's hand).
        PlayerSession holder = join("Shepherd", new Position(30.5, 4.0, 30.5));
        server.ticker().submit(() -> {
            holder.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.LEAD, 1));
            holder.inventory().selectHotbarSlot(0);
        });
        await(() -> !holder.inventory().held().isEmpty(), "the holder holds the lead");
        server.ticker().submit(() ->
                server.leads().interactMobWithLead(holder, (net.zaminmc.torch.server.entity.MobEntity) body));
        await(() -> body.leashed(), "the sheep carries the lead");

        // The off-lane unleash: the §27.6 violation fails diagnosably
        // through the LeadSystem's own assert.
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread stranger = new Thread(() -> {
            try {
                body.unleash(true);
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        stranger.start();
        stranger.join();
        assertInstanceOf(IllegalStateException.class, caught.get(),
                "the off-lane unleash fails diagnosably");

        // The leash survived (the rejected write changed nothing).
        assertTrue(body.leashed(), "the leash survived the rejected write");

        // The public World surface still observes the mob among the
        // entities (§27.5: the same engine object).
        World world = server.world();
        server.world().execute(() -> {
            boolean found = false;
            for (var entity : world.entities()) {
                if (entity == body) {
                    found = true;
                }
            }
            assertTrue(found, "the handle is the world's own entity object");
        });
    }
}
