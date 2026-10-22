package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The mob equipment family's public API surface (§27.3 — the VIII4 row,
 * the former DEMAND-NAMED gap): the {@link LivingEntity} handle's
 * five-slot read/set family over the live engine — exercised through
 * zamin-api alone in the assertion paths, with the engine probe (the
 * SAME object captured at spawn, §27.5) proving the write lands on the
 * authoritative body, never a copy. The write arm routes the one
 * authoritative setEquipment the spawn dice, the anvil import's
 * Equipment rows and the ZVD persistence walk all ride, then the
 * manager's Entity Equipment re-publish; an off-lane write fails
 * diagnosably and changes nothing (§27.6).
 */
class EquipmentApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "eqa" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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

    /** Spawns one mob; captures the SAME object as both the api handle and the engine probe. */
    private void spawn(MobType type, double x, double z,
                       AtomicReference<LivingEntity> handle,
                       AtomicReference<net.zaminmc.torch.server.entity.MobEntity> engine)
            throws InterruptedException {
        server.ticker().submit(() -> {
            var mob = server.mobs().spawnAt(type, new Position(x, 4.0, z));
            engine.set(mob);
            handle.set(mob); // §27.5: the handle IS the engine object
        });
        await(() -> handle.get() != null && engine.get() != null,
                "the " + type + " spawned");
    }

    @Test
    void theFreshBodiesAnswerTheFiveSlotBand() throws Exception {
        // The bare animal: a cow never rolls the equipment dice (the
        // spawn dice are the monster family's own), so every slot of
        // the band answers the canonical empty stack.
        AtomicReference<LivingEntity> handle = new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> engine = new AtomicReference<>();
        spawn(MobType.COW, 10.5, 10.5, handle, engine);
        LivingEntity body = handle.get();

        assertEquals(5, body.equipmentSlotCount(), "the reference's own five-slot array");
        for (int slot = 0; slot < 5; slot++) {
            assertEquals(ItemStack.EMPTY, body.equipmentAt(slot),
                    "the fresh cow's slot " + slot + " is bare");
        }
        ItemStack[] band = body.equipmentStacks();
        assertEquals(5, band.length, "the band copy carries the five slots");
        for (int slot = 0; slot < 5; slot++) {
            assertEquals(ItemStack.EMPTY, band[slot], "the band copy's slot " + slot + " is bare");
        }

        // The archer's own spawn landing (the initialize walk's
        // unconditional bow): the ENGINE's state reads through the api
        // handle — slot 0 is the held stack. (The armor dice also run
        // for the skeleton family, so slots 1-4 stay unasserted here.)
        AtomicReference<LivingEntity> archerHandle = new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> archerEngine = new AtomicReference<>();
        spawn(MobType.SKELETON, 30.5, 30.5, archerHandle, archerEngine);
        LivingEntity archer = archerHandle.get();

        assertEquals(ItemStack.of(BuiltinItems.BOW, 1), archer.equipmentAt(0),
                "the archer's held bow reads through the api handle");
        assertEquals(5, archer.equipmentSlotCount());
        assertSame(archerEngine.get().heldEquipment(), archer.equipmentAt(0),
                "the api read answers the engine object's own stack (§27.5 — the same reference)");
    }

    @Test
    void theWriteArmRoutesTheOneAuthoritativePath() throws Exception {
        AtomicReference<LivingEntity> handle = new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> engine = new AtomicReference<>();
        spawn(MobType.COW, 10.5, 10.5, handle, engine);
        LivingEntity body = handle.get();

        // The publish proof: the manager's own re-send (the Slice 78b
        // Entity Equipment wake) fires for the accepted write.
        PublishProbe probe = new PublishProbe();
        server.mobs().addListener(probe);

        // The armor write, on the owner lane (the §27.6 documented path).
        ItemStack chestplate = ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1);
        server.world().execute(() -> body.setEquipmentAt(3, chestplate));
        await(() -> engine.get() != null && engine.get().equipment(3) == chestplate,
                "the chestplate landed on the live engine object");

        // The §27.5 liveness proof: the api write is THE engine object's
        // state — the same stack reference, no copy, and the engine's own
        // consults (the damage walk's armor sum, the held read) see it.
        assertSame(chestplate, body.equipmentAt(3), "the api read answers the same reference");
        assertEquals(chestplate, engine.get().equipment(3),
                "the engine surface reflects the api write immediately");
        assertEquals(8, engine.get().armorProtectionPoints(),
                "the armor consult (the five-slot quirk's own walk) sums the landed piece");

        // The held write (slot 0 — the reference's own band head).
        ItemStack sword = ItemStack.of(BuiltinItems.IRON_SWORD, 1);
        server.world().execute(() -> body.setEquipmentAt(0, sword));
        await(() -> engine.get() != null && engine.get().heldEquipment() == sword,
                "the sword landed at the held slot");
        assertEquals(sword, body.equipmentAt(0), "the held slot reads back");

        // The band copy after both writes: the sword at [0], the
        // chestplate at [3], the rest bare.
        ItemStack[] band = body.equipmentStacks();
        assertEquals(sword, band[0]);
        assertEquals(chestplate, band[3]);
        assertEquals(ItemStack.EMPTY, band[1]);
        assertEquals(ItemStack.EMPTY, band[2]);
        assertEquals(ItemStack.EMPTY, band[4]);

        // Both accepted writes re-published (the Entity Equipment wake).
        assertTrue(probe.publishes.get() >= 2,
                "every accepted write fired the equipment re-publish (was " + probe.publishes.get() + ")");
    }

    @Test
    void theClearArmRestoresTheBareSlotForm() throws Exception {
        AtomicReference<LivingEntity> handle = new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> engine = new AtomicReference<>();
        spawn(MobType.PIG, 10.5, 10.5, handle, engine);
        LivingEntity body = handle.get();

        ItemStack helmet = ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1);
        server.world().execute(() -> body.setEquipmentAt(4, helmet));
        await(() -> engine.get() != null && engine.get().equipment(4) == helmet,
                "the helmet landed at the head slot");

        // The canonical empty stack clears the slot — and the engine's
        // own bare-slot form (null — the form the NBT rows and the
        // null-check walks ride) is restored, never an EMPTY-stuffed cell.
        server.world().execute(() -> body.setEquipmentAt(4, ItemStack.EMPTY));
        await(() -> engine.get() != null && engine.get().equipment(4) == null,
                "the clear restored the engine's bare-slot form");
        assertEquals(ItemStack.EMPTY, body.equipmentAt(4), "the cleared slot answers empty");
    }

    @Test
    void theForgivingBandGuardsCarryTheNegativeCases() throws Exception {
        AtomicReference<LivingEntity> handle = new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> engine = new AtomicReference<>();
        spawn(MobType.COW, 10.5, 10.5, handle, engine);
        LivingEntity body = handle.get();

        ItemStack sword = ItemStack.of(BuiltinItems.IRON_SWORD, 1);
        server.world().execute(() -> body.setEquipmentAt(0, sword));
        await(() -> engine.get() != null && engine.get().heldEquipment() == sword,
                "the sword landed at the held slot");

        // The out-of-band reads answer the canonical empty stack (the
        // forgiving read — Vehicle.Cargo's own form), never the engine's
        // clamped held-echo.
        assertEquals(ItemStack.EMPTY, body.equipmentAt(-1), "the below-band read answers empty");
        assertEquals(ItemStack.EMPTY, body.equipmentAt(5), "the past-band read answers empty");

        // The out-of-band write is ignored — the api's band guard never
        // lets a stray slot touch the held stack (the engine's own
        // clamped form would have landed it at slot 0).
        server.world().execute(() -> body.setEquipmentAt(7,
                ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1)));
        server.world().execute(() -> body.setEquipmentAt(-3,
                ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1)));
        EngineAwaits.awaitTicks(server.world(), 6, "the lane drains the ignored writes; nothing may land");
        assertSame(sword, body.equipmentAt(0),
                "the out-of-band write never touched the held stack");
        assertEquals(ItemStack.EMPTY, body.equipmentAt(3), "slot 3 stayed bare");
        assertEquals(ItemStack.EMPTY, body.equipmentAt(4), "slot 4 stayed bare");

        // The null write is ignored too (the api never carries null
        // stacks; a null is a plugin bug, not a clear).
        server.world().execute(() -> body.setEquipmentAt(2, null));
        EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
        assertEquals(ItemStack.EMPTY, body.equipmentAt(2), "the null write was ignored");
    }

    @Test
    void theRemovedBodyLeavesThePopulationAndCarriesItsLastState() throws Exception {
        AtomicReference<LivingEntity> handle = new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> engine = new AtomicReference<>();
        spawn(MobType.SHEEP, 10.5, 10.5, handle, engine);
        LivingEntity body = handle.get();

        ItemStack boots = ItemStack.of(BuiltinItems.IRON_BOOTS, 1);
        server.world().execute(() -> body.setEquipmentAt(1, boots));
        await(() -> engine.get() != null && engine.get().equipment(1) == boots,
                "the boots landed at the feet slot");

        // The removal (the public despawn verb): the body leaves the
        // world's population view...
        World world = server.world();
        server.world().execute(() -> body.despawn());
        AtomicReference<Boolean> gone = new AtomicReference<>();
        await(() -> {
            server.world().execute(() -> {
                boolean found = false;
                for (var entity : world.entities()) {
                    if (entity == body) {
                        found = true;
                    }
                }
                gone.set(!found);
            });
            return Boolean.TRUE.equals(gone.get());
        }, "the despawned body left the population view");

        // ...and the handle still resolves to the engine object (§27.5 —
        // never a copy): the removed body answers its LAST state through
        // the same live reference. The documented lifecycle: the removal
        // takes the body out of the world, it does not clone or wipe it.
        assertSame(engine.get(), body, "the handle is still the engine object");
        assertEquals(boots, body.equipmentAt(1),
                "the removed body's handle answers its last state");
        assertEquals(ItemStack.EMPTY, body.equipmentAt(0), "the other slots answer their last state too");
    }

    @Test
    void theOffLaneWriteFailsDiagnosablyAndChangesNothing() throws Exception {
        AtomicReference<LivingEntity> handle = new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> engine = new AtomicReference<>();
        spawn(MobType.COW, 10.5, 10.5, handle, engine);
        LivingEntity body = handle.get();

        // The §27.6 violation: a stranger thread's write fails
        // diagnosably through the wired path's own owner-context assert
        // (the unleash precedent's form) — and nothing lands.
        ItemStack helmet = ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread stranger = new Thread(() -> {
            try {
                body.setEquipmentAt(4, helmet);
            } catch (Throwable t) {
                caught.set(t);
            }
        }, "plugin-thread");
        stranger.start();
        stranger.join();
        assertInstanceOf(IllegalStateException.class, caught.get(),
                "the off-lane equipment write fails diagnosably (was " + caught.get() + ")");
        assertEquals(ItemStack.EMPTY, body.equipmentAt(4),
                "the rejected write changed nothing");
        assertTrue(engine.get().equipment(4) == null,
                "the engine object stayed bare at the rejected slot");
    }

    /**
     * The publish probe (the §27.10 observability arm): a manager
     * listener capturing the equipment re-send alone — every other
     * event a no-op (the interface's own default bodies).
     */
    private static final class PublishProbe
            implements net.zaminmc.torch.server.entity.MobManager.Listener {
        final AtomicInteger publishes = new AtomicInteger();

        @Override public void onMobEquipmentChanged(
                net.zaminmc.torch.server.entity.MobEntity mob) {
            publishes.incrementAndGet();
        }

        @Override public void onMobSpawned(net.zaminmc.torch.server.entity.MobEntity mob) { }
        @Override public void onMobMoved(net.zaminmc.torch.server.entity.MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession rider) { }
        @Override public void onMobHurt(net.zaminmc.torch.server.entity.MobEntity mob) { }
        @Override public void onMobDied(net.zaminmc.torch.server.entity.MobEntity mob) { }
        @Override public void onMobRemoved(net.zaminmc.torch.server.entity.MobEntity mob,
                String reason) { }
        @Override public void onMobAttackedPlayer(net.zaminmc.torch.server.entity.MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession target, float damage) { }
        @Override public void onMobSound(net.zaminmc.torch.server.entity.MobEntity mob,
                String soundName) { }
        @Override public void onMobRangedAttack(net.zaminmc.torch.server.entity.MobEntity mob,
                net.zaminmc.torch.util.Position aimPoint) { }
        @Override public void onMobFuseChanged(net.zaminmc.torch.server.entity.MobEntity mob,
                boolean priming) { }
        @Override public void onMobSheared(net.zaminmc.torch.server.entity.MobEntity mob,
                int woolCount) { }
        @Override public void onMobCoatRegrown(net.zaminmc.torch.server.entity.MobEntity mob) { }
        @Override public void onMobExploded(net.zaminmc.torch.server.entity.MobEntity mob) { }
    }
}
