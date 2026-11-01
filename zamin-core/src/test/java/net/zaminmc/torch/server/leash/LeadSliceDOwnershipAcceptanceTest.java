package net.zaminmc.torch.server.leash;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE LEAD FAMILY'S PER-DIMENSION STORY (FL4-GAP2 Slice D — the transient
 * families' row, the third family): the reference's leash knots live in
 * their own WorldServer — a nether knot is a NETHER body (its survival
 * poll reads the NETHER's fences, its tie scan consults the NETHER's
 * mobs), and the overworld's fence at the SAME coordinates is a
 * DIFFERENT world's cell. The pre-Slice engine kept ONE shared family:
 * the tie scan consulted every dimension's mobs (the NAMED LEAK — a
 * nether fence tied an OVERWORLD mob at the same coordinates), the
 * survival poll read the OVERWORLD's cells (a nether knot died on the
 * overworld's air — or worse, survived on the overworld's fence), the
 * detacher's owner-context assert pinned the overworld's mutation
 * context, and a nether mob's snapped lead dropped an OVERWORLD item.
 *
 * <p>THE SEATS: the nether fence tying only the nether's mobs (the named
 * leak — the overworld cow at the same coordinates keeps its player
 * hold, the overworld family never gains a knot); the survival poll
 * reading the knot's OWN world (a nether knot surviving the overworld's
 * air at the same coordinates, then falling when ITS OWN fence breaks);
 * and the snapped lead's drop stamped with the leashed body's own
 * dimension.</p>
 */
class LeadSliceDOwnershipAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld overworld;
    private EngineWorld nether;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "sliceleads" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        overworld = server.world();
        nether = server.worldFor(-1);
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

    /** Lays a small platform at the given band in BOTH worlds (the same coordinates). */
    private void platformBothWorlds(int cx, int cy, int cz) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    overworld.setBlock(new BlockPosition(cx + dx, cy - 1, cz + dz),
                            BuiltinBlocks.DIRT);
                    nether.setBlock(new BlockPosition(cx + dx, cy - 1, cz + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = cy; y <= cy + 3; y++) {
                        overworld.setBlock(new BlockPosition(cx + dx, y, cz + dz),
                                overworld.airType());
                        nether.setBlock(new BlockPosition(cx + dx, y, cz + dz),
                                nether.airType());
                    }
                }
            }
        });
        await(() -> BuiltinBlocks.DIRT.equals(overworld.getBlock(
                        new BlockPosition(cx, cy - 1, cz)))
                        && BuiltinBlocks.DIRT.equals(nether.getBlock(
                        new BlockPosition(cx, cy - 1, cz))),
                "both worlds' platforms landed");
    }

    private PlayerSession join(String name, Position at) {
        var link = new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    /**
     * THE NAMED LEAK'S SEAT: a nether fence ties only the NETHER's mobs —
     * the nether cow at the cell gains the knot while the OVERWORLD cow
     * at the same coordinates (held by the same player) keeps its player
     * hold, and the overworld family never gains a knot at the fence's
     * cell. The pre-Slice shared scan tied BOTH (the cross-world tie
     * class the trace named).
     */
    @Test
    void theNetherFenceTiesOnlyTheNetherMobs() throws Exception {
        int cx = 64, cy = 100, cz = 64;
        platformBothWorlds(cx, cy, cz);
        BlockPosition fence = new BlockPosition(cx, cy, cz);
        Position cowAt = new Position(cx + 1.5, cy, cz + 0.5);

        // The same fence cell stands in BOTH worlds.
        server.ticker().submit(() -> {
            overworld.setBlock(fence, BuiltinBlocks.FENCE);
            nether.setBlock(fence, BuiltinBlocks.FENCE);
        });
        await(() -> BuiltinBlocks.FENCE.equals(overworld.getBlock(fence))
                        && BuiltinBlocks.FENCE.equals(nether.getBlock(fence)),
                "both worlds' fences landed");

        // The holder, one lead in hand.
        PlayerSession holder = join("Holder", new Position(cx + 1.5, cy, cz + 1.5));
        server.ticker().submit(() -> {
            holder.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.LEAD, 2));
            holder.inventory().selectHotbarSlot(0);
        });
        await(() -> !holder.inventory().held().isEmpty(), "the holder holds the lead");

        // The nether cow (stamped for its own world) and the overworld cow
        // at the SAME coordinates, both held by the same player.
        AtomicReference<MobEntity> netherCow = new AtomicReference<>();
        AtomicReference<MobEntity> overCow = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity netherBody = server.mobs().spawnAt(MobType.COW, cowAt);
            if (netherBody != null) {
                netherBody.setDimension(-1);
                netherCow.set(netherBody);
            }
            overCow.set(server.mobs().spawnAt(MobType.COW, cowAt));
        });
        await(() -> netherCow.get() != null && overCow.get() != null, "both cows spawned");
        server.ticker().submit(() -> {
            server.leads().interactMobWithLead(holder, netherCow.get());
            server.leads().interactMobWithLead(holder, overCow.get());
        });
        await(() -> netherCow.get().isLeashed() && overCow.get().isLeashed(),
                "both cows carry the player's lead");

        // THE TIE: the NETHER family's fence use (the engine routes the
        // player's own world's family here).
        server.ticker().submit(() ->
                server.leadsFor(-1).useLeadOnFence(holder, fence));
        await(() -> netherCow.get().leashHolderKind()
                        == net.zaminmc.torch.entity.LeashHolderKind.KNOT,
                "the nether cow gained the nether knot");

        // The named leak's discrimination: the overworld cow at the same
        // coordinates NEVER tied — its player hold stands.
        assertEquals(net.zaminmc.torch.entity.LeashHolderKind.PLAYER,
                overCow.get().leashHolderKind(),
                "the overworld cow keeps its player hold (the scan gates on"
                        + " the body's own dimension)");
        assertEquals(java.util.Optional.of(holder.uuid()),
                overCow.get().leashHolderPlayerUuid(),
                "the overworld cow's holder is still the player");

        // The registry discrimination: the nether family owns the knot,
        // the overworld family's cell at the same coordinates holds none.
        assertNotNull(server.leadsFor(-1).knotAt(fence),
                "the nether family's knot exists");
        assertNull(server.leadsFor(0).knotAt(fence),
                "the overworld family never gained a knot at the same coordinates");
        assertEquals(-1, server.leadsFor(-1).knotAt(fence).dimension(),
                "the knot carries the nether stamp");
    }

    /**
     * THE SURVIVAL READ'S SEAT: the knot's survival poll reads its OWN
     * world's cells — a nether knot SURVIVES the overworld's air at the
     * same coordinates (the pre-Slice poll read the overworld and killed
     * it), then falls within the 100-tick cadence when ITS OWN fence
     * breaks (the positive control: the poll does read the nether).
     */
    @Test
    void theKnotSurvivalPollReadsItsOwnWorld() throws Exception {
        int cx = 80, cy = 100, cz = 80;
        platformBothWorlds(cx, cy, cz);
        BlockPosition fence = new BlockPosition(cx, cy, cz);

        // The fence stands in the NETHER alone; the overworld's cell at
        // the same coordinates stays AIR.
        server.ticker().submit(() -> nether.setBlock(fence, BuiltinBlocks.FENCE));
        await(() -> BuiltinBlocks.FENCE.equals(nether.getBlock(fence)),
                "the nether fence landed");
        assertTrue(overworld.getBlock(fence).equals(overworld.airType()),
                "the overworld cell at the same coordinates is air");

        // The tie that creates the knot: a nether cow held by a player.
        PlayerSession holder = join("SurvivalHolder", new Position(cx + 1.5, cy, cz + 1.5));
        server.ticker().submit(() -> {
            holder.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.LEAD, 1));
            holder.inventory().selectHotbarSlot(0);
        });
        await(() -> !holder.inventory().held().isEmpty(), "the holder holds the lead");
        AtomicReference<MobEntity> cow = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity body = server.mobs().spawnAt(MobType.COW,
                    new Position(cx + 1.5, cy, cz + 0.5));
            if (body != null) {
                body.setDimension(-1);
                cow.set(body);
            }
        });
        await(() -> cow.get() != null, "the nether cow spawned");
        server.ticker().submit(() ->
                server.leads().interactMobWithLead(holder, cow.get()));
        await(() -> cow.get().isLeashed(), "the cow carries the lead");
        server.ticker().submit(() ->
                server.leadsFor(-1).useLeadOnFence(holder, fence));
        await(() -> server.leadsFor(-1).knotAt(fence) != null, "the nether knot exists");

        // THE DISCRIMINATION: past a FULL survival cadence (100 ticks +
        // margin), the knot still stands — the poll read the NETHER's
        // fence, never the overworld's air at the same coordinates.
        EngineAwaits.awaitTicks(overworld, 160, "a full survival cadence passed");
        assertNotNull(server.leadsFor(-1).knotAt(fence),
                "the nether knot survived the overworld's air at the same"
                        + " coordinates (the poll reads the knot's OWN world)");

        // THE POSITIVE CONTROL: the nether's own fence breaks, and the
        // knot falls within the cadence.
        server.ticker().submit(() -> nether.setBlock(fence, nether.airType()));
        EngineAwaits.awaitCondition(overworld,
                () -> server.leadsFor(-1).knotAt(fence) == null,
                200, "the knot fell when its OWN world's fence broke");
    }

    /**
     * THE DROP STAMP'S SEAT: a nether mob's snapped lead drops a NETHER
     * item — the leash rows live on the mob, and the drop stamps the
     * leashed body's own dimension (the pre-Slice surface dropped an
     * OVERWORLD item at the nether coordinates).
     */
    @Test
    void theSnappedLeashDropsInTheBodysOwnWorld() throws Exception {
        int cx = 96, cy = 100, cz = 96;
        platformBothWorlds(cx, cy, cz);

        // The nether cow held by a player standing beside it.
        PlayerSession holder = join("SnapHolder", new Position(cx + 1.5, cy, cz + 0.5));
        server.ticker().submit(() -> {
            holder.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.LEAD, 1));
            holder.inventory().selectHotbarSlot(0);
        });
        await(() -> !holder.inventory().held().isEmpty(), "the holder holds the lead");
        AtomicReference<MobEntity> cow = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity body = server.mobs().spawnAt(MobType.COW,
                    new Position(cx + 0.5, cy, cz + 0.5));
            if (body != null) {
                body.setDimension(-1);
                cow.set(body);
            }
        });
        await(() -> cow.get() != null, "the nether cow spawned");
        server.ticker().submit(() ->
                server.leads().interactMobWithLead(holder, cow.get()));
        await(() -> cow.get().isLeashed(), "the cow carries the lead");

        // THE SNAP: the holder walks beyond the ten-block threshold —
        // the leash breaks, the lead drops at the mob.
        server.ticker().submit(() -> holder.applyMovement(
                new Position(cx + 0.5 + 12.0, cy, cz + 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitCondition(overworld, () -> !cow.get().isLeashed(),
                100, "the leash snapped at the ten-block threshold");

        // The dropped lead carries the NETHER stamp.
        await(() -> {
            for (ItemEntity drop : server.itemEntities().all()) {
                if (drop.stack().type().equals(BuiltinItems.LEAD)
                        && drop.dimension() == -1
                        && Math.abs(drop.position().x() - (cx + 0.5)) < 3.0
                        && Math.abs(drop.position().z() - (cz + 0.5)) < 3.0) {
                    return true;
                }
            }
            return false;
        }, "the snapped lead dropped as a NETHER item at the mob");
        assertFalse(server.itemEntities().all().stream().anyMatch(drop ->
                        drop.stack().type().equals(BuiltinItems.LEAD)
                                && drop.dimension() == 0
                                && Math.abs(drop.position().x() - (cx + 0.5)) < 3.0
                                && Math.abs(drop.position().z() - (cz + 0.5)) < 3.0),
                "no OVERWORLD lead dropped at the nether coordinates");
    }
}
