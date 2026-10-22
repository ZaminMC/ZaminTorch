package net.zaminmc.torch.bukkit;

import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event bridge's third group over the live engine (§28.7's entity
 * adapters — slice 3): the six entity-backed families the slice-2 record
 * demand-named, every one now bridged through the org.bukkit.entity views.
 * The slice-2 test wave's own discipline, family by family: the both-ways
 * cancellation/mutation mapped back onto the engine's own gates, the wrapped
 * views' data lines (the pending spawn facts, the live damage/death body
 * rows), the honest §27.9 shims (the pre-construction refusal probes, the
 * empty drops view, the DEFAULT reason approximation), and the
 * fires-exactly-when-the-engine-runs tripwire in its count form.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(7)
class BukkitEventBridgeGroupThreeTest {

    private static BukkitTestHarness harness() throws Exception {
        BukkitTestHarness harness = BukkitTestHarness.harness();
        harness.ensureProbeEnabled(); // the class's entry state (order-independent)
        return harness;
    }

    @Test
    @Order(1)
    void theCreatureSpawnBridgeCancelsBothWaysAndCarriesThePendingView() throws Exception {
        BukkitTestHarness harness = harness();
        // The wrapped pending view's data lines: the type row through the
        // EntityTypeMap, the position exactly as the engine stated it, the
        // DEFAULT reason (the native event carries no reason row — the
        // recorded §27.9 approximation), the owner-context dispatch.
        var pig = new AtomicReference<net.zaminmc.torch.server.entity.MobEntity>();
        harness.server.ticker().submit(() ->
                pig.set(harness.server.mobs().spawnAt(MobType.PIG, new Position(70, 40, 70))));
        harness.await(() -> pig.get() != null, "the pig spawned through the engine's own surface");
        harness.await(() -> harness.probeLogContains(
                "event:spawn type=PIG reason=DEFAULT at=(70.0,40.0,70.0) primary=true"),
                "the pending view carried the spawn facts on the owner context");
        // The loud-refusal probe: the pending view's id read MUST refuse
        // (the engine's gate fires before the body exists — no id consumed).
        harness.await(() -> harness.probeLogContains("view:spawn-pending-id-refused"),
                "the pending view refused the body read loudly (the §27.9 discipline)");
        // The cancellable-both-ways arm: the legacy cancel maps back onto
        // the engine's own pre-construction gate — no body, no id consumed.
        var sheep = new AtomicReference<net.zaminmc.torch.server.entity.MobEntity>();
        harness.server.ticker().submit(() ->
                sheep.set(harness.server.mobs().spawnAt(MobType.SHEEP, new Position(71, 40, 71))));
        harness.await(() -> harness.probeLogContains("cancel:spawn"),
                "the legacy listener cancelled the sheep spawn");
        harness.await(() -> sheep.get() == null,
                "the cancelled spawn returned null through the engine's own surface");
        boolean noSheep = true;
        for (var mob : harness.server.mobs().all()) {
            if (mob.type() == MobType.SHEEP) {
                Position at = mob.position();
                noSheep = !(Math.abs(at.x() - 71) < 2 && Math.abs(at.z() - 71) < 2);
            }
        }
        assertTrue(noSheep, "no sheep body landed at the cancelled cell");
    }

    @Test
    @Order(2)
    void theMobDamageBridgeCarriesTheLiveViewAndCancelsBothWays() throws Exception {
        BukkitTestHarness harness = harness();
        var pig = new AtomicReference<net.zaminmc.torch.server.entity.MobEntity>();
        harness.server.ticker().submit(() ->
                pig.set(harness.server.mobs().spawnAt(MobType.PIG, new Position(73, 40, 73))));
        harness.await(() -> pig.get() != null, "the pig spawned");
        // The wrapped LIVE view's data lines: the pre-damage health (10.0 —
        // the kind's own ceiling), the max-health row, the display name (the
        // kind's wire name), the deterministic uuid row, all on the owner
        // context.
        harness.server.ticker().submit(() ->
                harness.server.mobs().hurt(pig.get(), 3.0f, 0.0));
        harness.await(() -> harness.probeLogContains(
                "event:mobdamage type=PIG cause=ENTITY_ATTACK amount=3.0"
                        + " health=10.0 maxHealth=10.0 name=Pig uuid=true primary=true"),
                "the live view carried the body's own rows at damage time");
        // The mutable amount BOTH ways: the legacy setDamage(1.0) mapped
        // back onto the engine's own mutable amount.
        harness.await(() -> pig.get().health() == 9.0f,
                "the probe's setDamage(1.0) drove the engine's own amount");
        assertTrue(harness.probeLogContains("modify:mobdamage-amount"),
                "the probe's rewrite arm ran");
        // The cancellable-both-ways arm: the big hit cancelled through the
        // legacy form, nothing lands.
        harness.server.ticker().submit(() ->
                harness.server.mobs().hurt(pig.get(), 20.0f, 0.0));
        harness.await(() -> harness.probeLogContains("cancel:mobdamage"),
                "the legacy listener cancelled the big hit");
        Thread.sleep(300); // the cancelled hit certainly settled
        assertEquals(9.0f, pig.get().health(), 1e-6f,
                "the cancelled damage landed nothing (the mapped-back cancel)");
    }

    @Test
    @Order(3)
    void theMobDeathBridgeObservesWithTheHonestShims() throws Exception {
        BukkitTestHarness harness = harness();
        var cow = new AtomicReference<net.zaminmc.torch.server.entity.MobEntity>();
        harness.server.ticker().submit(() ->
                cow.set(harness.server.mobs().spawnAt(MobType.COW, new Position(75, 40, 75))));
        harness.await(() -> cow.get() != null, "the cow spawned");
        // The native MONITOR observer: the engine's own later handlers see
        // the post-legacy state (the death is already authoritative — the
        // 1.8.8 form is not cancellable, both shapes agree).
        AtomicReference<net.zaminmc.torch.event.entity.MobDeathEvent> nativeSeen =
                new AtomicReference<>();
        EventSubscription monitor = harness.server.events().on(
                net.zaminmc.torch.event.entity.MobDeathEvent.class,
                net.zaminmc.torch.event.EventPriority.MONITOR,
                nativeSeen::set);
        // The cow is the probe's cancel-exempt body — the 1000 hit must
        // land for the death walk to run.
        harness.server.ticker().submit(() ->
                harness.server.mobs().hurt(cow.get(), 1000.0f, 0.0));
        harness.await(() -> cow.get().dead(), "the body died");
        // The MONITOR dispatch lands at-or-after the dead flag inside the
        // engine's own death walk — awaiting the observer (never asserting
        // the instant the flag flips) keeps a loaded runner from losing
        // the sub-tick race the old immediate assertNotNull could.
        harness.await(() -> nativeSeen.get() != null,
                "the native MONITOR observed the death");
        monitor.unsubscribe();
        // The honest shims: the health row reads the dead body's own 0.0,
        // the drops view is the unmodifiable empty shim (the native event
        // fires pre-scatter — no honest drop list exists), the exp row
        // answers the engine-owned 0 (the scatter math is authoritative).
        harness.await(() -> harness.probeLogContains(
                "event:mobdeath type=COW health=0.0 drops=0 exp=0 primary=true"),
                "the death observation carried the live body's row and the honest shims");
        assertTrue(harness.probeLogContains("event:mobdeath type=COW"),
                "the legacy death form dispatched");
    }

    @Test
    @Order(4)
    void theItemSpawnBridgeCancelsBothWaysAndCarriesTheStackView() throws Exception {
        BukkitTestHarness harness = harness();
        // The value view's data lines: the stack's 1.8.8 identity through
        // the bare-path resolver, the count row, the owner-context dispatch.
        harness.server.ticker().submit(() -> harness.server.itemEntities().spawnDropAtBlock(
                new Position(77, 40, 77),
                net.zaminmc.torch.item.ItemStack.of(BuiltinItems.STONE, 1), 10));
        harness.await(() -> anyProbeLine(harness,
                "event:itemspawn item=STONE amount=1 at=(77.", "primary=true"),
                "the pending item view carried the stack facts on the owner context"
                        + " (the pop offset's own x band)");
        // The loud-refusal probe: no body exists to mutate.
        harness.await(() -> harness.probeLogContains("view:item-pending-delay-refused"),
                "the pending item view refused the body mutation loudly");
        // The drop lands through the engine's own surface (the position band
        // of the pop offset — the uncancelled twin).
        harness.await(() -> itemsNear(harness, 77.5, 40.5, 77.5) > 0,
                "the uncancelled stone drop landed at the cell");
        // The cancellable-both-ways arm: the withheld diamond — the drop
        // returns null through the engine's own gate and no body lands.
        var dropped = new AtomicReference<net.zaminmc.torch.server.entity.ItemEntity>();
        harness.server.ticker().submit(() -> dropped.set(
                harness.server.itemEntities().spawnDropAtBlock(
                        new Position(79, 40, 79),
                        net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 1), 10)));
        harness.await(() -> harness.probeLogContains("cancel:itemspawn"),
                "the legacy listener cancelled the diamond drop");
        harness.await(() -> dropped.get() == null,
                "the cancelled drop returned null through the engine's own surface");
        Thread.sleep(300); // any wrongly-landed body would have settled
        assertEquals(0, itemsNear(harness, 79.5, 40.5, 79.5),
                "the cancelled diamond never entered the world");
    }

    @Test
    @Order(5)
    void theProjectileLaunchBridgeCarriesTheShooterAndCancelsBothWays() throws Exception {
        BukkitTestHarness harness = harness();
        // The shooterless shot: -1 resolves no body — "none", never an
        // invented shooter.
        var arrow = new AtomicReference<net.zaminmc.torch.server.entity.projectile.ProjectileEntity>();
        harness.server.ticker().submit(() -> arrow.set(
                harness.server.projectiles().launch(
                        net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW,
                        -1, new Position(81, 40, 81), 0.0, 0.0, 1.5)));
        harness.await(() -> arrow.get() != null, "the arrow launched through the engine's surface");
        harness.await(() -> harness.probeLogContains(
                "event:launch type=ARROW shooter=none at=(81.0,40.0,81.0) primary=true"),
                "the pending projectile view carried the launch facts");
        // The shooter resolution: a joined session's engine id answers the
        // PLAYER view through the adapter's own resolution.
        PlayerSession shooter = harness.join("ShooterProbe");
        var snowball = new AtomicReference<net.zaminmc.torch.server.entity.projectile.ProjectileEntity>();
        harness.server.ticker().submit(() -> snowball.set(
                harness.server.projectiles().launch(
                        net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.SNOWBALL,
                        shooter.engineEntityId(), new Position(83, 40, 83), 0.0, 0.0, 1.5)));
        harness.await(() -> harness.probeLogContains(
                "event:launch type=SNOWBALL shooter=ShooterProbe"),
                "the thrower id resolved onto the player view");
        // The cancellable-both-ways arm: the no-snowball plugin withholds
        // the shot — the launch returns null, no id consumed.
        harness.await(() -> harness.probeLogContains("cancel:launch"),
                "the legacy listener cancelled the snowball launch");
        harness.await(() -> snowball.get() == null,
                "the cancelled launch returned null through the engine's own surface");
        harness.disconnect(shooter);
    }

    @Test
    @Order(6)
    void theVehicleCreateBridgeObservesWithTheLegacyShape() throws Exception {
        BukkitTestHarness harness = harness();
        // The 1.8.8 form's own observation-only shape: the type row through
        // the wire-name table, the position exactly as the engine stated
        // it, the owner-context dispatch — and NO cancel arm (the reference
        // type carries none; the native gate keeps its own verb).
        var boat = new AtomicReference<net.zaminmc.torch.server.entity.vehicle.VehicleEntity>();
        harness.server.ticker().submit(() ->
                boat.set(harness.server.vehicles().spawnBoat(new Position(85, 40, 85))));
        harness.await(() -> boat.get() != null, "the boat spawned through the engine's surface");
        harness.await(() -> harness.probeLogContains(
                "event:vehicle type=BOAT at=(85.0,40.0,85.0) primary=true"),
                "the pending vehicle view carried the spawn facts on the owner context");
        // The wire-name row: the chest minecart is MINECART_CHEST (the
        // legacy spelling, not the engine identifier).
        var chest = new AtomicReference<net.zaminmc.torch.server.entity.vehicle.VehicleEntity>();
        harness.server.ticker().submit(() -> chest.set(
                harness.server.vehicles().spawnChestMinecart(new Position(87, 40, 87))));
        harness.await(() -> chest.get() != null, "the chest minecart spawned");
        harness.await(() -> harness.probeLogContains(
                "event:vehicle type=MINECART_CHEST at=(87.0,40.0,87.0)"),
                "the vehicle table's own wire-name row carried");
        assertFalse(harness.probeLogContains("cancel:vehicle"),
                "the 1.8.8 form carries no cancel arm (the observation never cancels)");
    }

    @Test
    @Order(7)
    void theFamiliesFireExactlyWhenTheEngineRuns() throws Exception {
        BukkitTestHarness harness = harness();
        // The count discipline (the tripwire's numeric form): one engine
        // surface run, exactly ONE legacy dispatch — no double dispatch, no
        // phantom dispatch. The position-pinned needles keep the natural
        // spawn cycle's own lines out of the counts.
        int spawnsBefore = countInProbeLog(harness,
                "event:spawn type=PIG reason=DEFAULT at=(89.0,40.0,89.0)");
        int damageBefore = countInProbeLog(harness,
                "event:mobdamage type=PIG cause=ENTITY_ATTACK amount=2.0");
        var pig = new AtomicReference<net.zaminmc.torch.server.entity.MobEntity>();
        harness.server.ticker().submit(() ->
                pig.set(harness.server.mobs().spawnAt(MobType.PIG, new Position(89, 40, 89))));
        harness.await(() -> pig.get() != null, "the pig spawned");
        harness.await(() -> countInProbeLog(harness,
                "event:spawn type=PIG reason=DEFAULT at=(89.0,40.0,89.0)")
                == spawnsBefore + 1,
                "exactly one spawn dispatch for one engine spawn");
        assertTrue(harness.server.mobs().hurt(pig.get(), 2.0f, 0.0),
                "the hit landed through the engine's own surface");
        harness.await(() -> countInProbeLog(harness,
                "event:mobdamage type=PIG cause=ENTITY_ATTACK amount=2.0") == damageBefore + 1,
                "exactly one damage dispatch for one engine hit");
        // The engine's own verdict stays the engine's own: the hit landed
        // (the probe's 2.0 arm neither cancels nor rewrites).
        assertEquals(pig.get().maxHealth() - 2.0f, pig.get().health(), 1e-6f,
                "the observed hit's verdict stayed the engine's own");
    }

    // ------------------------------------------------------------------ helpers

    /** The live item bodies within the position band (the pop offset's own spread). */
    private static int itemsNear(BukkitTestHarness harness, double x, double y, double z) {
        int count = 0;
        for (var item : harness.server.itemEntities().all()) {
            Position at = item.position();
            if (Math.abs(at.x() - x) < 0.3 && Math.abs(at.y() - y) < 0.3
                    && Math.abs(at.z() - z) < 0.3) {
                count++;
            }
        }
        return count;
    }

    private static int countOf(List<String> lines, String needle) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(needle)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The probe-log count read, safe inside await-suppliers (an IO hiccup
     * reads as the unchanged count — the await's own retry handles it; the
     * harness's probeLogContains law).
     */
    private static int countInProbeLog(BukkitTestHarness harness, String needle) {
        try {
            return countOf(harness.probeLogLines(), needle);
        } catch (java.io.IOException unreadable) {
            return 0;
        }
    }

    /**
     * The line-level containment+tail read (an IO hiccup reads as not-there
     * — the await's own retry handles it): proves one line BOTH carries the
     * needle AND ends with the tail (the owner-context flag's own line).
     */
    private static boolean anyProbeLine(BukkitTestHarness harness, String needle,
            String tail) {
        try {
            for (String line : harness.probeLogLines()) {
                if (line.contains(needle) && line.endsWith(tail)) {
                    return true;
                }
            }
            return false;
        } catch (java.io.IOException unreadable) {
            return false;
        }
    }
}
