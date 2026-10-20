package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffects;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.FluidBlocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lava walk for the living family (Slice 93/D2 — Entity.tick lines
 * 337-341, the isInLava arm): takeLavaDamage's pair (the flat 4.0
 * LAVA-source wound at the hurt-i-frame cadence + the fifteen-second arm),
 * the fire-immune exemption, the FIRE_RESISTANCE consult (LivingEntity
 * lines 645-647 — the isFire source gate over the whole fire family), and
 * the shared fluid model's split (the water's fall-store zero vs the
 * lava's *0.5 halving, the water's douse vs the lava's kept burn) — over
 * the mob model and the live engine's player body.
 */
class LavaWalkTest {

    // ------------------------------------------------------------------
    // Level 1 — the mob model
    // ------------------------------------------------------------------

    /**
     * The controllable world: solid below {@code floorY}, water at or below
     * {@code waterY}, lava at or below {@code lavaY} (the two fluid lines
     * independent — the reference's own two-consult shape).
     */
    private static final class StubWorld implements MobEntity.WorldQuery {
        final double floorY;
        final double waterY;
        final double lavaY;

        StubWorld(double floorY, double waterY, double lavaY) {
            this.floorY = floorY;
            this.waterY = waterY;
            this.lavaY = lavaY;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < floorY;
        }

        @Override
        public double supportY(double x, double y, double z) {
            return floorY;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return y <= waterY || y <= lavaY;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return y <= lavaY;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    /** A deep lava column with no floor in reach: the body sinks submerged. */
    private static final double DEEP = -1000.0;

    private static MobEntity cow(StubWorld world) {
        return new MobEntity(7, MobType.COW, new Position(1.5, 64.0, 2.5),
                new Random(11), world);
    }

    @Test
    void theLavaWalkWoundsAndBurns() {
        // The cow submerged in the lava column: the fifteen-second arm holds
        // the burn at 300 while submerged (the max-arm re-armed every tick),
        // and the 4.0 wounds land every 10 ticks — the 10-heart body dies
        // inside three windows.
        MobEntity mob = cow(new StubWorld(DEEP, DEEP, 62.0));
        mob.teleport(new Position(1.5, 60.0, 2.5));
        float health = mob.health();
        for (int i = 0; i < 10; i++) {
            mob.tick(false);
        }
        assertEquals(MobEntity.LAVA_BURN_TICKS, mob.fireTicks());
        assertEquals(health - MobEntity.LAVA_DAMAGE_PER_WOUND, mob.health(), 1e-6f);
        for (int i = 0; i < 10; i++) {
            mob.tick(false);
        }
        assertEquals(health - 2 * MobEntity.LAVA_DAMAGE_PER_WOUND, mob.health(), 1e-6f);
        for (int i = 0; i < 10; i++) {
            mob.tick(false);
        }
        assertTrue(mob.health() <= 0.0f || mob.dead());
    }

    @Test
    void theFireImmuneKindNeverWoundsOrBurns() {
        // takeLavaDamage's own gate: the blaze stands in its element — no
        // ignite, no wound, the burn clock stays cold.
        MobEntity blaze = new MobEntity(7, MobType.BLAZE, new Position(1.5, 60.0, 2.5),
                new Random(11), new StubWorld(DEEP, DEEP, 62.0));
        float health = blaze.health();
        for (int i = 0; i < 30; i++) {
            blaze.tick(false);
        }
        assertEquals(health, blaze.health(), 1e-6f);
        assertEquals(0, blaze.fireTicks());
    }

    @Test
    void theResistantBodyBurnsButNeverWounds() {
        // The reference's own shape: takeLavaDamage arms the burn
        // unconditionally (setOnFireFor runs before takeDamage's gate),
        // the FIRE_RESISTANCE consult (LivingEntity lines 645-647) holds
        // every wound — the lava's 4.0 AND the burn clock's 1.0 residual.
        MobEntity mob = cow(new StubWorld(DEEP, DEEP, 62.0));
        mob.setEffectAmplifierQuery(effectId ->
                effectId == StatusEffects.FIRE_RESISTANCE ? 1 : 0);
        mob.teleport(new Position(1.5, 60.0, 2.5));
        float health = mob.health();
        for (int i = 0; i < 40; i++) {
            mob.tick(false);
        }
        assertEquals(MobEntity.LAVA_BURN_TICKS, mob.fireTicks()); // burning visually
        assertEquals(health, mob.health(), 1e-6f); // never a wound
    }

    @Test
    void theBurnClockHoldsItsWoundsUnderResistance() {
        // The ON_FIRE source is an isFire family member: a resistant body
        // burning OUTSIDE the lava (the plain burn clock) never wounds
        // either — the reference's takeDamage gate over the whole family.
        MobEntity mob = cow(new StubWorld(DEEP, DEEP, DEEP));
        mob.setEffectAmplifierQuery(effectId ->
                effectId == StatusEffects.FIRE_RESISTANCE ? 1 : 0);
        mob.ignite(MobEntity.FIRE_TICKS);
        float health = mob.health();
        for (int i = 0; i < 60; i++) {
            mob.tick(false);
        }
        assertTrue(mob.fireTicks() >= 0);
        assertEquals(health, mob.health(), 1e-6f);
        // The control: an unprotected body wounds at the per-second rhythm.
        MobEntity plain = cow(new StubWorld(DEEP, DEEP, DEEP));
        plain.ignite(MobEntity.FIRE_TICKS);
        float plainHealth = plain.health();
        for (int i = 0; i < 20; i++) {
            plain.tick(false);
        }
        assertEquals(plainHealth - MobEntity.FIRE_DAMAGE_PER_SECOND, plain.health(), 1e-6f);
    }

    @Test
    void theFallStoreHalvesInTheLavaDuringTheDescent() {
        // The shared fluid model's split (Entity.tick line 339): a body
        // DROPPED INTO the lava column from above — the swim goal rides
        // both fluids in the reference too (EntityAISwimming.canStart's
        // isInWater || handleLavaMovement), so a settled body bobs up and
        // never accumulates; the halving's window is the submerged DESCENT
        // (velocityY < 0): the store accumulates the sinking AND halves
        // every tick, converging on ~2x the per-tick drop — positive, but
        // well under the dry fall's accumulation. The water contrast
        // zeroes the store outright; the air contrast accumulates the
        // whole drop.
        MobEntity inLava = cow(new StubWorld(DEEP, DEEP, 64.0));
        inLava.teleport(new Position(1.5, 70.0, 2.5));
        float lavaStore = -1.0f;
        for (int i = 0; i < 60; i++) {
            inLava.tick(false);
            if (inLava.position().y() < 64.0 && inLava.velocityY() < 0.0) {
                lavaStore = inLava.fallDistance(); // mid-descent, submerged
            }
        }
        assertTrue(lavaStore > 0.0f,
                "the lava keeps a positive remainder mid-descent: " + lavaStore);
        assertTrue(lavaStore < 1.5f,
                "the halving eats the accumulated drop: " + lavaStore);
        // The water's full zero (checkWaterCollisions' own family): the
        // same drop into a water column never carries a store (the read
        // sits a block past the surface so the mid-band consult has
        // flipped).
        MobEntity inWater = cow(new StubWorld(DEEP, 64.0, DEEP));
        inWater.teleport(new Position(1.5, 70.0, 2.5));
        for (int i = 0; i < 60; i++) {
            inWater.tick(false);
            if (inWater.position().y() < 63.0 && inWater.velocityY() < 0.0) {
                assertEquals(0.0f, inWater.fallDistance(), 1e-6f);
            }
        }
        // The air's accumulation (the control): the dry fall's store
        // grows past five blocks.
        MobEntity inAir = cow(new StubWorld(DEEP, DEEP, DEEP));
        inAir.teleport(new Position(1.5, 70.0, 2.5));
        for (int i = 0; i < 20; i++) {
            inAir.tick(false);
        }
        assertTrue(inAir.fallDistance() > 5.0f);
    }

    @Test
    void theBurningBodyKeepsItsBurnInTheLava() {
        // The douse is the WATER consult alone (checkWaterCollisions' own
        // family): a body burning inside the lava keeps its burn — the
        // pre-split engine extinguished it because the fluid test covered
        // both kinds.
        MobEntity inLava = cow(new StubWorld(DEEP, DEEP, 62.0));
        inLava.teleport(new Position(1.5, 60.0, 2.5));
        inLava.ignite(MobEntity.FIRE_TICKS);
        for (int i = 0; i < 10; i++) {
            inLava.tick(false);
        }
        assertTrue(inLava.fireTicks() > 0, "the lava never douses the burn");
        assertEquals(MobEntity.LAVA_BURN_TICKS, inLava.fireTicks());
        // The water douse (the control).
        MobEntity inWater = cow(new StubWorld(DEEP, 62.0, DEEP));
        inWater.teleport(new Position(1.5, 60.0, 2.5));
        inWater.ignite(MobEntity.FIRE_TICKS);
        for (int i = 0; i < 2; i++) {
            inWater.tick(false);
        }
        assertEquals(0, inWater.fireTicks());
    }

    // ------------------------------------------------------------------
    // Level 2 — the live engine (the player body)
    // ------------------------------------------------------------------

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "lvt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL,
                true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
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
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, Rotation.ZERO, true);
        return session;
    }

    /** A lava source block at the cell containing the point. */
    private void lavaCell(double x, double y, double z) {
        BlockPosition at = new BlockPosition(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        server.ticker().submit(() -> server.world().setBlock(at,
                FluidBlocks.sourceOf(FluidBlocks.Kind.LAVA)));
    }

    @Test
    void thePlayerTakesTheLavaWoundsAndBurns() throws Exception {
        PlayerSession swimmer = join("Swimmer", new Position(30.5, 4, 30.5));
        float health = swimmer.health();
        lavaCell(30.5, 4, 30.5);
        await(() -> server.world().getBlock(new BlockPosition(30, 4, 30)) != null
                        && FluidBlocks.isLava(server.world()
                                .getBlock(new BlockPosition(30, 4, 30)).identifier()),
                "the lava placed");

        // The fifteen-second arm + the 4.0 wound at the 10-tick cadence:
        // after ~10 engine ticks the burn clock sits at 300 and the first
        // wound landed (20 - 4 = 16 on the unenchanted body).
        await(() -> swimmer.fireTicks() >= MobEntity.LAVA_BURN_TICKS - 20,
                "the lava burn armed");
        await(() -> swimmer.health() <= health - MobEntity.LAVA_DAMAGE_PER_WOUND,
                "the first lava wound");
        assertEquals(health - MobEntity.LAVA_DAMAGE_PER_WOUND, swimmer.health(), 1e-6f);
    }

    @Test
    void thePlayerFallStoreHalvesInTheLava() throws Exception {
        PlayerSession diver = join("Diver", new Position(40.5, 4, 40.5));
        // A deep lava cell below the surface: the diver SINKS into it
        // (feet in the lava, not grounded — a grounded body's store is
        // spent by the landing walk exactly like the reference's
        // updateFallState, so the halving's window is the submerged
        // descent).
        lavaCell(40.5, 3, 40.5);
        await(() -> FluidBlocks.isLava(server.world()
                        .getBlock(new BlockPosition(40, 3, 40)).identifier()),
                "the lava placed");
        diver.applyMovement(new Position(40.5, 3.5, 40.5), Rotation.ZERO, false);

        // Seed the fall store through the movement proposal seam (a 1.0
        // drop), then watch the catch run: the lava HALVES the store every
        // tick — it decays geometrically (1.0 / 2^k) but never zeroes in
        // the observable window — while the water's zero erases it in ONE
        // tick.
        server.ticker().submit(() -> diver.noteFall(4.5, 3.5, false));
        await(() -> diver.fallDistance() > 0.0f
                        && diver.fallDistance() < 0.5f,
                "the first halving (the decayed but surviving store)");

        // The water contrast: the zero runs in the tick walk (the same-tick
        // task phase and the polling thread both miss the transient, and
        // nested submits drain inside the SAME phase), so the read rides
        // the public scheduler's delayed task — two ticks out, after the
        // intermediate walks: the store never survives a tick's walk in
        // the water.
        PlayerSession bather = join("Bather", new Position(50.5, 4, 50.5));
        BlockPosition water = new BlockPosition(50, 3, 50);
        server.ticker().submit(() -> server.world().setBlock(water,
                FluidBlocks.sourceOf(FluidBlocks.Kind.WATER)));
        await(() -> FluidBlocks.kindOf(server.world().getBlock(water).identifier()) != null,
                "the water placed");
        bather.applyMovement(new Position(50.5, 3.5, 50.5), Rotation.ZERO, false);
        java.util.concurrent.atomic.AtomicReference<Float> afterTheWalks =
                new java.util.concurrent.atomic.AtomicReference<>();
        server.scheduler().runLater(2, () ->
                afterTheWalks.set(bather.fallDistance()));
        server.ticker().submit(() -> bather.noteFall(4.5, 3.5, false));
        await(() -> afterTheWalks.get() != null, "the delayed read ran");
        assertEquals(0.0f, afterTheWalks.get(),
                "the water zeroed the store by the next walk");
    }
}
