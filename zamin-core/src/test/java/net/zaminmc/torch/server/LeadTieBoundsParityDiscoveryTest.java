package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-5 — the lead tie scan's own box (the Phase E boundary arms
 * over the leash family): the reference ties through
 * {@code getEntitiesOfType(MobEntity.class, new Box(i - 7, j - 7, k -
 * 7, i + 7, j + 7, k + 7))} (LeadItem.attachLead lines 36-41) — the ±7
 * box anchored at the fence block's MIN CORNER, each mob's OWN type
 * box against it, STRICT both sides (exactly touching never
 * intersecting).
 *
 * <p>The pre-fix engine anchored the whole band at the fence block's
 * CENTER (a half-block east/south/up shift) AND grew the query box by
 * the type's FULL width around the mob's center with inclusive edges —
 * the effective band [i+0.5-7-width, i+0.5+7+width] over the center, a
 * half-width-and-more beyond vanilla's own box law — and the y band
 * read the FEET position against [minY, maxY + height], never the
 * mob's OWN [pos.y, pos.y + height] against [minY, maxY]: a mob
 * standing below the band whose head reaches in was never a tie
 * candidate, and a mob past the vanilla east face was tied through.</p>
 */
class LeadTieBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "leash" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE NEVER-ARM: the cow past the vanilla east face never
    // ties (the pre-fix width growth tied it through)
    // ------------------------------------------------------------------

    /**
     * The fence at (10, 40, 10) — the vanilla tie box [3, 17] x [33,
     * 47] x [3, 17] anchored at the block's min corner. A cow (the
     * 0.9x1.3 own box) at center x 17.85 — the own box [17.4, 18.3],
     * its west edge 0.4 PAST the vanilla east face 17 — is NEVER a tie
     * candidate in vanilla (the boxes never intersect). The pre-fix
     * consult read the center against [2.1, 17.9] (the center anchor
     * shifted +0.5, the band grown the full width 0.9): 17.85 passed
     * whole and the cow TIED — RED pre-fix (the seat asserts the
     * player still holds the lead after the fence click).
     */
    @Test
    void theCowPastTheVanillaEastFaceNeverTies() throws Exception {
        int fy = 40;
        carveTiePlatform(fy);
        PlayerSession farmer = joined("Farmer");
        MobEntity cow = spawnCowAt(17.85, fy, 10.5);
        leashTo(farmer, cow, new Position(16.5, fy, 10.5));
        var fence = new BlockPosition(10, fy, 10);
        assertFalse(server.leads().useLeadOnFence(farmer, fence),
                "the cow past the vanilla east face never ties (the own box "
                        + "[17.4, 18.3] never intersects [3, 17])");
        assertTrue(cow.isLeashed() && cow.leashHolderPlayer() == farmer,
                "the player still holds the lead (the tie never landed)");
        assertFalse(server.leads().knotAt(fence) != null,
                "no knot spawned for a tie-less click");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE DISCRIMINATOR: the head-reach cow on the low ledge
    // ties (the pre-fix feet band never saw it)
    // ------------------------------------------------------------------

    /**
     * The same fence's tie box y band [33, 47]: a cow on the floating
     * ledge at y 32 (the feet at 32, the own box [32, 33.3] — the head
     * 0.3 INSIDE the band) IS a tie candidate in vanilla (the own box
     * overlaps the query box). The pre-fix y band read the FEET
     * position against [minY, maxY + height]: the feet at 32 never
     * passed the floor 33 — the head-reach cow was NEVER tied. RED
     * pre-fix.
     */
    @Test
    void theHeadReachCowOnTheLowLedgeTies() throws Exception {
        int fy = 40;
        carveTiePlatform(fy);
        // The low ledge: one block at y 31 (the top face 32), the
        // cow's feet at 32 — inside the tie box's x/z band.
        server.ticker().submit(() -> {
            for (int dx = 9; dx <= 11; dx++) {
                for (int dz = 9; dz <= 11; dz++) {
                    server.world().setBlock(new BlockPosition(dx, 31, dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(), () -> BuiltinBlocks.STONE.equals(
                        server.world().getBlock(new BlockPosition(10, 31, 10))),
                40, "the low ledge placed");
        PlayerSession farmer = joined("LedgeFarmer");
        MobEntity cow = spawnCowAt(10.5, 32, 10.5);
        leashTo(farmer, cow, new Position(12.0, 32, 12.0));
        var fence = new BlockPosition(10, fy, 10);
        assertTrue(server.leads().useLeadOnFence(farmer, fence),
                "the head-reach cow on the low ledge ties (the own box's head "
                        + "inside the y band)");
        assertNotNull(cow.leashHolderKnot(),
                "the knot holds the cow now (the tie landed)");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * The tie platform: the floor blocks at fy-1 (the top face fy)
     * across the whole tie band [3, 17] x [3, 17] (the tie floor), the
     * fence block at (10, fy, 10), and clean air above the floor
     * through fy+6.
     */
    private void carveTiePlatform(int fy) throws InterruptedException {
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            for (int x = 2; x <= 19; x++) {
                for (int z = 2; z <= 18; z++) {
                    world.setBlock(new BlockPosition(x, fy - 1, z),
                            BuiltinBlocks.STONE);
                    for (int y = fy; y <= fy + 6; y++) {
                        world.setBlock(new BlockPosition(x, y, z),
                                world.airType());
                    }
                }
            }
            world.setBlock(new BlockPosition(10, fy, 10), BuiltinBlocks.FENCE);
        });
        EngineAwaits.awaitCondition(world,
                () -> BuiltinBlocks.FENCE.equals(world.getBlock(new BlockPosition(10, fy, 10))),
                40, "the tie platform carved with the fence");
    }

    /** Spawns a cow at the position and awaits its rest. */
    private MobEntity spawnCowAt(double x, int fy, double z)
            throws InterruptedException {
        AtomicReference<MobEntity> cow = new AtomicReference<>();
        server.ticker().submit(() -> cow.set(server.mobs().spawnAt(
                MobType.COW, new Position(x, fy, z), 0)));
        await(() -> cow.get() != null, "the cow spawned at " + x);
        return cow.get();
    }

    /**
     * Holds a lead and clicks the cow — the real hold-and-attach chain
     * (the player teleported beside the cow first, the reach gate's
     * own law).
     */
    private void leashTo(PlayerSession farmer, MobEntity cow, Position beside)
            throws InterruptedException {
        server.teleportPlayer(farmer, beside);
        EngineAwaits.awaitTicks(server.world(), 2, "the farmer settles");
        server.ticker().submit(() -> {
            farmer.inventory().setSlot(0, ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.LEAD, 3));
            farmer.inventory().selectHotbarSlot(0);
            server.leads().interactMobWithLead(farmer, cow);
        });
        await(() -> cow.isLeashed() && cow.leashHolderPlayer() == farmer,
                "the lead attached to the cow");
    }

    private PlayerSession joined(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
