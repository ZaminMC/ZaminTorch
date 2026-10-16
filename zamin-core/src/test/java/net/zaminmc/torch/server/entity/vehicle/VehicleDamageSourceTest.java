package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vehicle damage-source surface (Slice 74 — the reference's
 * {@code damage(DamageSource, float)} over the minecart family's
 * dropItems arms): the source lands on the body, the death facts freeze
 * at the fatal hit (the reference reads the live velocity inside
 * damage(); the engine's drop walk runs on the next tick), the furnace
 * cart's furnace block and the TNT cart's TNT block both skip an
 * explosive death, the TNT cart's fire/explosive/fast death detonates
 * (the chain reaction), and the explosion's own wound walk reaches the
 * carts with the EXPLOSIVE source.
 */
class VehicleDamageSourceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    /** The recording blast sink. */
    private static final class Blasts implements TntMinecartEntity.BlastSink {
        final List<float[]> detonations = new ArrayList<>();

        @Override
        public void detonate(double x, double y, double z, float power) {
            detonations.add(new float[]{(float) x, (float) y, (float) z, power});
        }
    }

    private static final VehicleEntity.WorldQuery EMPTY_WORLD =
            new VehicleEntity.WorldQuery() {
                @Override public boolean isSolid(double x, double y, double z) {
                    return false;
                }
            };

    @Test
    void theSourceLandsAndTheDeathFactsFreezeAtTheFatalHit() {
        TntMinecartEntity cart = new TntMinecartEntity(9800,
                new Position(0.5, 64.0, 0.5), new Random(80L));
        assertEquals(VehicleEntity.DamageSource.MELEE, cart.lastDamageSource(),
                "a fresh body carries the melee default");
        assertEquals(0.0, cart.deathSpeedSquared(), "no death facts before a fatal hit");

        // A non-fatal hit lands its source; the death facts stay unset.
        cart.hurt(1.0f, VehicleEntity.DamageSource.EXPLOSIVE);
        assertEquals(VehicleEntity.DamageSource.EXPLOSIVE, cart.lastDamageSource(),
                "the source of the last landed hit reads");
        assertEquals(0.0, cart.deathSpeedSquared(), "a surviving hit freezes nothing");
        assertFalse(cart.dead(), "the body survived");

        // The i-frame burns down (the hurt-cooldown rhythm's own ticks).
        for (int i = 0; i < 10; i++) {
            cart.tickHurtCooldown();
        }

        // The fatal hit freezes the speed: dropItems lines 82-84 read the
        // LIVE velocity inside damage() — the engine's drop walk runs on
        // the next tick's break sweep, so the snapshot carries the law (a
        // cart halted between the hit and the sweep must still answer the
        // >= 0.01 gate with its death-time speed).
        cart.setVelocity(0.2, 0.0, 0.0); // speed squared 0.04
        cart.hurt(10.0f, VehicleEntity.DamageSource.MELEE); // the fatal hit
        assertTrue(cart.dead(), "the fatal hit killed the body");
        assertEquals(VehicleEntity.DamageSource.MELEE, cart.lastDamageSource(),
                "the fatal hit's source reads");
        assertEquals(0.04, cart.deathSpeedSquared(), 1e-9,
                "the death speed froze at the fatal hit");
        cart.setVelocity(0.0, 0.0, 0.0); // the sweep-time halt
        assertEquals(0.04, cart.deathSpeedSquared(), 1e-9,
                "the halt after death never rewrites the frozen facts");
    }

    @Test
    void theDeathDetonationRidesTheFrozenSpeedAndTheSourceGates() {
        // The fast melee death (dropItems lines 105-107: d0 >= 0.01):
        // the TNT block DROPPED (the plain-break chain, the law's drop
        // side lives in the engine's drop sink) and the death detonation
        // bursts at the frozen speed's power band.
        Blasts blasts = new Blasts();
        TntMinecartEntity fast = new TntMinecartEntity(9810,
                new Position(1.5, 64.0, 1.5), new Random(81L));
        fast.setBlastSink(blasts);
        fast.setVelocity(0.3, 0.0, 0.0); // speed squared 0.09
        fast.hurt(10.0f, VehicleEntity.DamageSource.MELEE);
        fast.setVelocity(0.0, 0.0, 0.0);
        fast.detonateAtDeath(); // the sweep's own call
        assertEquals(1, blasts.detonations.size(), "the fast death detonated");
        float power = blasts.detonations.get(0)[3];
        double d0 = Math.min(Math.sqrt(0.09), TntMinecartEntity.BLAST_SPEED_CLAMP);
        assertTrue(power >= TntMinecartEntity.BLAST_BASE
                        && power <= TntMinecartEntity.BLAST_BASE
                                + TntMinecartEntity.BLAST_SPEED_SCALE * d0 + 1e-6,
                "the power band 4.0 + rand * 1.5 * the frozen clamped speed: " + power);

        // The halted melee death: no detonation (the reference's own
        // three-gate refusal — not fire, not explosive, not fast — the
        // gate lives INSIDE detonateAtDeath, dropItems' own shape).
        Blasts quiet = new Blasts();
        TntMinecartEntity halted = new TntMinecartEntity(9811,
                new Position(2.5, 64.0, 2.5), new Random(82L));
        halted.setBlastSink(quiet);
        halted.hurt(10.0f, VehicleEntity.DamageSource.MELEE); // at rest
        halted.detonateAtDeath(); // the method's own gate refuses
        assertEquals(0, quiet.detonations.size(), "no detonation fired");

        // The explosive death at rest: the detonation ALWAYS fires (the
        // isExplosive arm) at the rest power 4.0 exactly — d0 = 0.
        Blasts exploded = new Blasts();
        TntMinecartEntity blasted = new TntMinecartEntity(9812,
                new Position(3.5, 64.0, 3.5), new Random(83L));
        blasted.setBlastSink(exploded);
        blasted.hurt(10.0f, VehicleEntity.DamageSource.EXPLOSIVE);
        blasted.detonateAtDeath();
        assertEquals(1, exploded.detonations.size(), "the explosive death detonated");
        assertEquals(TntMinecartEntity.BLAST_BASE, exploded.detonations.get(0)[3], 1e-6,
                "the rest power is exactly 4.0 (d0 = 0)");
    }

    @Test
    void theBlastWoundsTheCartsWithTheExplosiveSourceAndTheDeathLawReadsIt()
            throws Exception {
        EngineServer server = boot();
        try {
            // The topology: a TNT block at the center, a furnace cart and
            // a TNT cart inside the injury radius (power 4 -> radius 8),
            // a plain minecart far outside (the survivor control), and a
            // stone block beside the TNT cart (the chain-detonation's
            // observable — the death blast destroys it).
            BlockPosition tntAt = new BlockPosition(120, 40, 120);
            Position furnaceAt = new Position(121.5, 40.0, 120.5);
            Position tntCartAt = new Position(122.5, 40.0, 122.5);
            Position farCartAt = new Position(140.5, 40.0, 140.5);
            BlockPosition chainStone = new BlockPosition(123, 40, 123);
            AtomicReference<VehicleEntity> furnaceCart = new AtomicReference<>();
            AtomicReference<VehicleEntity> tntCart = new AtomicReference<>();
            AtomicReference<VehicleEntity> farCart = new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(tntAt, BuiltinBlocks.TNT);
                server.world().setBlock(chainStone, BuiltinBlocks.STONE);
                furnaceCart.set(server.vehicles().spawnFurnaceMinecart(furnaceAt));
                tntCart.set(server.vehicles().spawnTntMinecart(tntCartAt));
                farCart.set(server.vehicles().spawnMinecart(farCartAt));
            });
            await(() -> furnaceCart.get() != null && tntCart.get() != null
                            && farCart.get() != null,
                    "the carts spawned");

            // The detonation (the engine's own TNT ignition walk).
            server.ticker().submit(() -> server.tnt().ignite(tntAt, 5));

            // The blast kills both near carts with the EXPLOSIVE source;
            // the break sweep drops ONLY the cart items (the furnace
            // block and the TNT block both skip — dropItems' own
            // !isExplosive() gates); the TNT cart's death detonation
            // fires (the chain) and destroys the stone beside it.
            await(() -> furnaceCart.get().dead() && tntCart.get().dead(),
                    "the blast killed both near carts");
            await(() -> server.vehicles().all().stream().noneMatch(
                            v -> v.entityId() == furnaceCart.get().entityId()
                                    || v.entityId() == tntCart.get().entityId()),
                    "the break sweep removed both bodies");
            await(() -> !farCart.get().dead(), "the far cart survived the blast");
            assertEquals(VehicleEntity.DamageSource.EXPLOSIVE,
                    furnaceCart.get().lastDamageSource(),
                    "the furnace cart died of the blast");
            assertEquals(VehicleEntity.DamageSource.EXPLOSIVE,
                    tntCart.get().lastDamageSource(),
                    "the TNT cart died of the blast");
            // The drops: no furnace block, no TNT block — the blast ate
            // both (the reference's own gates). The cart items dropped.
            boolean furnaceItem = false;
            boolean tntItem = false;
            int minecartItems = 0;
            for (var item : server.itemEntities().all()) {
                String id = item.stack().type().identifier().toString();
                if (id.equals("minecraft:furnace")) {
                    furnaceItem = true;
                } else if (id.equals("minecraft:tnt")) {
                    tntItem = true;
                } else if (id.equals("minecraft:minecart")) {
                    minecartItems++;
                }
            }
            assertFalse(furnaceItem, "the exploded furnace cart dropped NO furnace block");
            assertFalse(tntItem, "the exploded TNT cart dropped NO tnt block");
            assertTrue(minecartItems >= 2,
                    "both broken carts dropped their plain cart items");
            // The chain: the TNT cart's death detonation (the explosive
            // arm) destroyed the stone beside it.
            await(() -> server.world().getBlock(chainStone)
                            .equals(server.world().airType()),
                    "the chain detonation destroyed the stone");
            // The knockback impulse: the surviving far cart... is outside
            // the radius (the impulse's own unit is the frozen-facts
            // test's velocity surface — the engine's body model carries
            // the impulse into the next tick's momentum walk).
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theMeleeDeathDropsTheFurnaceBlock() throws Exception {
        EngineServer server = boot();
        try {
            // The contrast arm (FurnaceMinecartEntity.dropItems lines
            // 63-68): a PLAIN break drops the furnace block under
            // doEntityDrops — only the explosive death skips it.
            Position furnaceAt = new Position(150.5, 40.0, 150.5);
            AtomicReference<VehicleEntity> furnaceCart = new AtomicReference<>();
            server.ticker().submit(() ->
                    furnaceCart.set(server.vehicles().spawnFurnaceMinecart(furnaceAt)));
            await(() -> furnaceCart.get() != null, "the furnace cart spawned");
            server.ticker().submit(() -> server.vehicles().hurt(furnaceCart.get(), 10.0f));
            await(() -> furnaceCart.get().dead(), "the melee hit killed the cart");
            await(() -> server.vehicles().all().stream().noneMatch(
                            v -> v.entityId() == furnaceCart.get().entityId()),
                    "the break sweep removed the body");
            assertEquals(VehicleEntity.DamageSource.MELEE,
                    furnaceCart.get().lastDamageSource(), "the melee source reads");
            await(() -> {
                for (var item : server.itemEntities().all()) {
                    if (item.stack().type().identifier().toString().equals("minecraft:furnace")) {
                        return true;
                    }
                }
                return false;
            }, "the plain break dropped the furnace block");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "vds" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.CREATIVE, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false);
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
}
