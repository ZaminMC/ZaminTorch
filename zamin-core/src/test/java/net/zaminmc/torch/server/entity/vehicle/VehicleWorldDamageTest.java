package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileManager;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vehicle damage family's world arms (Slice 77): the projectile's
 * entity scan over the minecart family (the arrow lands on the cart's
 * hull with its damage and the knockback impulse), the BURNING arrow's
 * on-the-spot TNT-cart detonation (takeDamage lines 63-73 — the arrow's
 * own speed feeding the power roll), and the lava walk (Entity.tick's
 * isInLava arm: the flat 4.0 FIRE wound, the death law reading the
 * source — a lava-killed TNT cart detonates, a lava-killed furnace cart
 * still drops its furnace).
 */
class VehicleWorldDamageTest {

    // ------------------------------------------------------------------
    // The projectile arms (the manager's own unit surface).
    // ------------------------------------------------------------------

    /** The recording combat sink (the vehicle arm records its rows). */
    private static final class RecordingCombat implements ProjectileManager.CombatSink {
        final List<Float> vehicleDamages = new ArrayList<>();
        final List<VehicleEntity> vehicleHits = new ArrayList<>();

        @Override
        public boolean mobHit(net.zaminmc.torch.server.entity.MobEntity mob,
                float damage, double kbYaw, int shooterId) {
            return true;
        }

        @Override
        public void playerHit(PlayerSession player, float damage, double kbYaw) {
        }

        @Override
        public void chickenHatch(Position position) {
        }

        @Override
        public boolean vehicleHit(ProjectileEntity projectile, VehicleEntity vehicle,
                float damage, double kbYaw) {
            vehicleDamages.add(damage);
            vehicleHits.add(vehicle);
            return true;
        }
    }

    /** The hit resolver that answers the one rigged vehicle. */
    private static final class RiggedHits implements ProjectileManager.HitResolver {
        VehicleEntity vehicle;

        @Override
        public net.zaminmc.torch.server.entity.MobEntity mobAt(Position point) {
            return null;
        }

        @Override
        public PlayerSession playerAt(Position point) {
            return null;
        }

        @Override
        public VehicleEntity vehicleAt(Position point) {
            return vehicle;
        }
    }

    @Test
    void theArrowLandsOnTheCartsWithItsDamage() {
        RiggedHits hits = new RiggedHits();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, new FxManager(), new Random(7), 3_000);
        MinecartEntity cart = new MinecartEntity(3_100,
                new Position(0.5, 64.0, 0.5), new Random(70L));
        assertFalse(cart.dead(), "the fresh cart is whole");
        hits.vehicle = cart;
        // The full-draw arrow at speed 3: the damage roll (the engine's
        // established speed-scaled formula — ceil(3.0 * 2.0) = 6).
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 64, 0), 0, 0, 3.0);
        manager.tick(java.util.List.of()); // the flight's first pass lands inside the cart
        assertEquals(1, combat.vehicleDamages.size(),
                "the arrow's hit reached the vehicle arm");
        assertEquals(6.0f, combat.vehicleDamages.get(0), 1e-6,
                "the speed-scaled arrow damage (the reference's own roll)");
        assertNull(manager.byId(arrow.entityId()), "the arrow stopped at the cart");
    }

    @Test
    void theBurningArrowDetonatesTheTntCartOnTheSpot() {
        // TntMinecartEntity.takeDamage lines 63-73: the arm runs BEFORE the
        // damage walk — the ARROW's speed squared feeds the power roll
        // (clamped 5), the cart annihilates with no drops.
        final List<float[]> detonations = new ArrayList<>();
        TntMinecartEntity cart = new TntMinecartEntity(3_200,
                new Position(0.5, 64.0, 0.5), new Random(71L));
        cart.setBlastSink((x, y, z, power) -> detonations.add(
                new float[]{(float) x, (float) y, (float) z, power}));
        RiggedHits hits = new RiggedHits();
        hits.vehicle = cart;
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, new RecordingCombat(),
                new FxManager(), new Random(72L), 3_100);
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 64, 0), 0, 0, 3.0);
        arrow.setOnFireFor(100); // the Flame release: the whole flight burns
        manager.tick(java.util.List.of());
        assertEquals(1, detonations.size(), "the burning arrow detonated the cart");
        // The power band: 4.0 + rand * 1.5 * min(sqrt(3.0^2), 5.0).
        double d0 = Math.min(3.0, TntMinecartEntity.BLAST_SPEED_CLAMP);
        float power = detonations.get(0)[3];
        assertTrue(power >= TntMinecartEntity.BLAST_BASE
                        && power <= TntMinecartEntity.BLAST_BASE
                                + TntMinecartEntity.BLAST_SPEED_SCALE * d0 + 1e-6,
                "the arrow-speed power band: " + power);
        assertTrue(cart.dead(), "the cart annihilated");
        assertTrue(cart.annihilated(), "the no-drops mark set");
        assertNull(manager.byId(arrow.entityId()), "the arrow stopped at the cart");
    }

    // ------------------------------------------------------------------
    // The lava walk (the live engine).
    // ------------------------------------------------------------------

    @TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "lavacart",
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theLavaKillsTheCartWithTheFireSourceAndTheDeathLawReadsIt()
            throws Exception {
        EngineServer server = boot();
        try {
            // A lava pool with a furnace cart and a TNT cart standing in
            // it, a plain minecart on dry stone beside (the control), and
            // a stone block near the TNT cart (the fire-death detonation's
            // observable).
            BlockPosition lavaCell = new BlockPosition(170, 40, 170);
            BlockPosition dryStone = new BlockPosition(180, 40, 180);
            BlockPosition chainStone = new BlockPosition(172, 40, 171);
            AtomicReference<VehicleEntity> furnaceCart = new AtomicReference<>();
            AtomicReference<VehicleEntity> tntCart = new AtomicReference<>();
            AtomicReference<VehicleEntity> dryCart = new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(lavaCell,
                        FluidBlocks.sourceOf(FluidBlocks.Kind.LAVA));
                server.world().setBlock(dryStone, BuiltinBlocks.STONE);
                server.world().setBlock(chainStone, BuiltinBlocks.STONE);
                furnaceCart.set(server.vehicles().spawnFurnaceMinecart(
                        new Position(170.5, 40.0, 170.5)));
                tntCart.set(server.vehicles().spawnTntMinecart(
                        new Position(170.9, 40.0, 170.9)));
                dryCart.set(server.vehicles().spawnMinecart(
                        new Position(173.5, 41.0, 173.5)));
            });
            await(() -> furnaceCart.get() != null && tntCart.get() != null
                            && dryCart.get() != null,
                    "the carts spawned");

            // The lava walk: the flat 4.0 FIRE wound kills both carts in
            // the pool (the 4-health body model), the dry control stands.
            await(() -> furnaceCart.get().dead() && tntCart.get().dead(),
                    "the lava killed both pool carts");
            await(() -> !dryCart.get().dead(), "the dry control never burned");
            assertEquals(VehicleEntity.DamageSource.FIRE,
                    furnaceCart.get().lastDamageSource(),
                    "the furnace cart died of the lava's fire");
            assertEquals(VehicleEntity.DamageSource.FIRE,
                    tntCart.get().lastDamageSource(),
                    "the TNT cart died of the lava's fire");

            // The death law over the fire source: the furnace cart's break
            // sweep DROPS the furnace block (only the explosive death skips
            // it) and the TNT cart's fire death DETONATES (the chain stone
            // beside the pool takes the blast).
            await(() -> {
                for (var item : server.itemEntities().all()) {
                    if (item.stack().type().identifier().toString()
                            .equals("minecraft:furnace")) {
                        return true;
                    }
                }
                return false;
            }, "the fire-death furnace cart dropped its furnace block");
            await(() -> server.world().getBlock(chainStone)
                            .equals(server.world().airType()),
                    "the fire-death TNT cart detonated (the chain stone took the blast)");
            // The reference's own shape: a FIRE death DROPS the TNT block
            // (dropItems line 102's gate is !isExplosive() alone — the fire
            // arm detonates, it never withholds the drop) — the blast then
            // consumes the neighborhood (the chain stone took it above).
            boolean tntDropped = false;
            for (var item : server.itemEntities().all()) {
                if (item.stack().type().identifier().toString().equals("minecraft:tnt")) {
                    tntDropped = true;
                }
            }
            assertTrue(tntDropped,
                    "the fire-death TNT cart still dropped its TNT block (the reference's own gate)");
        } finally {
            server.shutdown(() -> { });
        }
    }
}
