package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TNT cart's destructive flag (F-EX7 — the twenty-first pass's census
 * find): the reference's {@code TntMinecartEntity.explode} (lines 85-92)
 * calls the SIX-ARG {@code world.explode(this, x, y, z, power, TRUE)} — the
 * cart's blast HARDCODES the destructive flag, and mobGriefing never gates
 * it (the famous exception: a TNT cart craters even in a no-grief world —
 * only the fireball and the creeper family ride the rule). The engine's
 * blast sink factory passed {@code gameRules.getBoolean("mobGriefing")} —
 * a no-grief world's cart blast wounded and knocked but never broke a
 * block.
 *
 * <p>The seat: mobGriefing off, a TNT cart on the grass plain, a burning
 * arrow into the cart (TntMinecartEntity.takeDamage lines 63-73's
 * annihilation arm — {@code explode(arrowSpeedSquared)}), and the crater
 * the vanilla law still owes: the ground under the cart breaks.</p>
 */
class TntCartDestructiveFlagParityTest {

    @TempDir
    Path dataDir;

    @Test
    void theCartsBlastBreaksBlocksEvenWhenMobGriefingRefuses() throws Exception {
        EngineServer server = boot();
        try {
            server.ticker().submit(() -> server.gameRules().set("mobGriefing", "false"));
            await(server, () -> "false".equals(server.gameRules().get("mobGriefing")),
                    "mobGriefing off");
            // The cart on the natural plain (grass top at y=4, the cart's
            // wheels at y=5), inside the pregenerated band.
            Position cartAt = new Position(30.5, 5.0, 30.5);
            AtomicReference<VehicleEntity> cart = new AtomicReference<>();
            server.ticker().submit(() ->
                    cart.set(server.vehicles().spawnTntMinecart(cartAt, 0)));
            await(server, () -> cart.get() != null, "the cart spawned");
            // The burning arrow into the cart's mid band, three blocks out
            // (the launch's own 0.0075-sigma spread cannot miss the 0.98
            // box over that flight).
            AtomicReference<Integer> arrowId = new AtomicReference<>();
            server.ticker().submit(() -> {
                ProjectileEntity arrow = server.projectiles().launch(
                        ProjectileEntity.Kind.ARROW, -1,
                        new Position(27.5, 5.35, 30.5), -90.0, 0.0, 3.0, 0);
                if (arrow != null) {
                    arrow.setOnFireFor(100);
                    arrowId.set(arrow.entityId());
                }
            });
            await(server, () -> arrowId.get() != null, "the burning arrow loosed");
            // The annihilation: the cart leaves (no drops, no damage walk —
            // the reference's own arm).
            await(server, () -> server.vehicles().all().stream()
                            .noneMatch(v -> v.entityId() == cart.get().entityId()),
                    "the burning arrow annihilated the cart");
            // THE CRATER (the reference's hardcoded TRUE): the blast broke
            // the ground under the cart even while mobGriefing refuses.
            // The pre-fix engine's flag consult left every grass cell
            // standing — the census over the blast's own neighborhood.
            await(server, () -> craterCells(server, 27, 33, 27, 33) > 0,
                    "the cart's blast broke the ground under it (the hardcoded "
                            + "destructive flag)");
            assertTrue(server.projectiles().all().stream()
                            .noneMatch(p -> p.entityId() == arrowId.get()),
                    "the arrow resolved at the cart");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /** The cleared ground cells (y=4) over one box, read on the tick thread. */
    private int craterCells(EngineServer server, int minX, int maxX, int minZ, int maxZ) {
        // The natural plain's top layer is grass everywhere; a cleared cell
        // is the crater's own signature.
        int cleared = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (server.world().getBlock(new BlockPosition(x, 4, z))
                        .equals(server.world().airType())) {
                    cleared++;
                }
            }
        }
        return cleared;
    }

    private void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tntflag" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
