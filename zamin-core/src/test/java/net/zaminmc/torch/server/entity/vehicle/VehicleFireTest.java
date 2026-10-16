package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vehicle fire family (Slice 78): the burn clock (Entity.java lines
 * 320-335 — the 1-per-second ON_FIRE cadence over the tick-down), the
 * fire-source contact walk (lines 635-651 — the one-tick grace counter,
 * the 8-second ignition, the wet extinguish with its fizz, the
 * out-of-fire grace re-arm), the lava walk's own 15-second arm
 * (takeLavaDamage lines 358-363 — Slice 77's gap), the flags-byte flip
 * publish (line 347's setFlag(0, ...) — the wire's dirty-entry send), and
 * the ZVD v3 {@code Fire} row (lines 1115/1176 — the raw signed clock
 * across the restart).
 */
class VehicleFireTest {

    /** Dry flat ground at y=4 with a fire source over every cell. */
    private static VehicleEntity.WorldQuery fireWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public boolean inFireSource(double x, double y, double z) {
                return y >= 4.0 && y < 5.0;
            }
        };
    }

    /** Dry flat ground at y=4, no fire anywhere (the dry control). */
    private static VehicleEntity.WorldQuery dryWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }
        };
    }

    /** The wet world: water at the body's band (the extinguish consult). */
    private static VehicleEntity.WorldQuery wetFireWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public boolean inFireSource(double x, double y, double z) {
                return y >= 4.0 && y < 5.0;
            }

            @Override public boolean inWaterOrRain(double x, double y, double z) {
                return y >= 4.0 && y < 5.0;
            }
        };
    }

    /** The lava world: lava at the body's mid-band. */
    private static VehicleEntity.WorldQuery lavaWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public boolean inLava(double x, double y, double z) {
                return y >= 4.0 && y < 5.0;
            }
        };
    }

    /** The recording listener (the flip + fizz arms the wire rides). */
    private static final class Events implements VehicleManager.Listener {
        final List<String> list = new ArrayList<>();

        @Override public void onVehicleSpawned(VehicleEntity vehicle) {
            list.add("spawn");
        }

        @Override public void onVehicleMoved(VehicleEntity vehicle) {
        }

        @Override public void onVehicleHurt(VehicleEntity vehicle) {
            list.add("hurt");
        }

        @Override public void onVehicleMounted(VehicleEntity vehicle, int riderEngineId) {
        }

        @Override public void onVehicleDismounted(VehicleEntity vehicle, int riderEngineId,
                Position riderExit) {
        }

        @Override public void onVehicleBroken(VehicleEntity vehicle) {
            list.add("broken");
        }

        @Override public void onVehicleStateChanged(VehicleEntity vehicle) {
            list.add("state:" + vehicle.onFire());
        }

        @Override public void onVehicleFizz(VehicleEntity vehicle) {
            list.add("fizz");
        }
    }

    private static VehicleManager manager(VehicleEntity.WorldQuery world, Events events) {
        VehicleManager manager = new VehicleManager(world,
                vehicle -> {
                },
                new Random(1), 100);
        manager.addListener(events);
        return manager;
    }

    // ------------------------------------------------------------------
    // The burn clock (Entity.java lines 320-335 — the entity's own law).
    // ------------------------------------------------------------------

    @Test
    void theBurnClockWoundsOncePerSecondAndBurnsOut() {
        MinecartEntity cart = new MinecartEntity(1,
                new Position(0.5, 4.5, 0.5), new Random(1));
        cart.setOnFireFor(8); // the ignition's own duration
        assertEquals(160, cart.onFireTimer(), "the 8-second arm: 160 ticks");
        assertTrue(cart.onFire(), "the body burns");

        // The cadence: the wound is due at every full second boundary
        // (160, 140, 120 ...) and never between — the clock ticks down
        // regardless (the reference's attempted-damage shape).
        int wounds = 0;
        for (int tick = 0; tick < 160; tick++) {
            if (cart.tickOnFire()) {
                wounds++;
            }
        }
        assertEquals(8, wounds, "one ON_FIRE wound per second of burning");
        assertFalse(cart.onFire(), "the burn ran out");
        assertEquals(0, cart.onFireTimer(),
                "the clock bottomed at zero (the grace re-arms only on the next contact walk)");
    }

    @Test
    void theLongerBurnNeverShortens() {
        // setOnFireFor's max arm (Entity.java lines 365-371): a burning
        // body re-armed keeps the longer clock.
        MinecartEntity cart = new MinecartEntity(2,
                new Position(0.5, 4.5, 0.5), new Random(2));
        cart.setOnFireFor(15);
        cart.setOnFireFor(8); // the shorter arm never shortens
        assertEquals(300, cart.onFireTimer(), "the 15-second clock holds");
    }

    // ------------------------------------------------------------------
    // The fire-source contact walk (the manager's own orchestration).
    // ------------------------------------------------------------------

    @Test
    void theFireBlockWoundsAndIgnitesAfterTheGraceTick() {
        // Entity.java lines 636-643: the first tick in a fire source lands
        // the 1.0 IN_FIRE wound and walks the grace (-1 -> 0), the zero
        // crossing arming the 8-second burn — the flags flip publishes.
        Events events = new Events();
        VehicleManager manager = manager(fireWorld(), events);
        VehicleEntity cart = manager.spawnMinecart(new Position(0.5, 4.5, 0.5));
        assertEquals(4.0f, cart.health(), 1e-6, "the fresh cart is whole");

        manager.tick();
        assertEquals(3.0f, cart.health(), 1e-6,
                "the in-fire wound landed (1.0 through the fresh i-frame)");
        assertTrue(cart.onFire(), "the grace zero-crossing ignited the burn");
        assertEquals(160, cart.onFireTimer(),
                "the 8-second ignition (the reference's setOnFireFor(8))");
        assertTrue(events.list.contains("state:true"),
                "the ignite flip published the flags byte");
        assertTrue(events.list.contains("hurt"),
                "the wound published the hurt flash");

        // The standing-in-fire steady state: the burn clock's ON_FIRE
        // cadence and the contact wound share the i-frame window (the
        // engine's one-wound-per-window body model), and the contact's
        // own grace walk keeps counting (the counter climbs while the
        // body stands in the source — the reference's own ++ arm).
        float healthAfter = cart.health();
        for (int i = 0; i < 19; i++) {
            manager.tick();
        }
        assertTrue(cart.onFire(), "still burning while standing in fire");
        assertTrue(cart.health() <= healthAfter,
                "the shared-window wounds never heal");
    }

    @Test
    void theDryBodyAwayFromTheFireReArmsItsGrace() {
        // Entity.java lines 644-646: the out-of-fire body resets its
        // grace every tick (the counter never climbs toward zero away
        // from the sources).
        Events events = new Events();
        VehicleManager manager = manager(dryWorld(), events);
        VehicleEntity cart = manager.spawnMinecart(new Position(0.5, 4.5, 0.5));
        for (int i = 0; i < 30; i++) {
            manager.tick();
        }
        assertFalse(cart.onFire(), "the dry control never ignited");
        assertFalse(cart.dead(), "the dry control never wounded");
        assertEquals(-VehicleEntity.FIRE_SAFE_TICKS, cart.onFireTimer(),
                "the grace held its re-armed value");
    }

    @Test
    void theWetBodyNeverCountsTheGraceAndTheBurnExtinguishes() {
        // Entity.java lines 638 + 648-651: a wet body in a fire source
        // takes the contact wound but never counts the grace; a wet
        // BURNING body drops the burn with the fizz.
        Events events = new Events();
        VehicleManager manager = manager(wetFireWorld(), events);
        VehicleEntity cart = manager.spawnMinecart(new Position(0.5, 4.5, 0.5));
        // A pre-armed burn (the walk-out-then-splash shape): the wet
        // contact extinguishes it on the first tick.
        cart.setOnFireFor(8);
        assertTrue(cart.onFire());
        manager.tick();
        assertFalse(cart.onFire(), "the water dropped the burn");
        assertTrue(events.list.contains("fizz"), "the fizz played");
        assertTrue(events.list.contains("state:false"),
                "the extinguish flip published the flags byte");
        // The wet contact keeps wounding (takeFireDamage has no wet gate)
        // but the grace never crosses zero — no re-ignition.
        assertEquals(3.0f, cart.health(), 1e-6, "the in-fire wound still landed");
        for (int i = 0; i < 30; i++) {
            manager.tick();
        }
        assertFalse(cart.onFire(), "a wet body never ignites");
    }

    @Test
    void theLavaArmsTheFifteenSecondBurn() {
        // takeLavaDamage lines 358-363 — Slice 77 landed the 4.0 wound,
        // Slice 78 closes the walk's own gap: setOnFireFor(15).
        Events events = new Events();
        VehicleManager manager = manager(lavaWorld(), events);
        VehicleEntity cart = manager.spawnMinecart(new Position(0.5, 4.5, 0.5));
        manager.tick();
        assertEquals(300, cart.onFireTimer(),
                "the lava's 15-second burn (the reference's own arm)");
        assertTrue(cart.onFire());
        assertTrue(events.list.contains("state:true"),
                "the lava ignition published the flags byte");
    }

    // ------------------------------------------------------------------
    // The ZVD v3 Fire row (the store round trip + the v2 back-compat).
    // ------------------------------------------------------------------

    @TempDir
    Path dataDir;

    @Test
    void theFireRowSurvivesTheStoreRoundTrip() throws IOException {
        VehicleDataStore store = new VehicleDataStore(dataDir.resolve("vehicles.zvd"));
        List<VehicleManager.VehicleSnapshot> saved = List.of(
                new VehicleManager.VehicleSnapshot("MINECART", 1, 65, 2,
                        90.0f, 4.0f, null, true, -1, null, -1,
                        null, 0, true, null, 123),
                new VehicleManager.VehicleSnapshot("BOAT", 3, 66, 4,
                        180.0f, 2.0f, null, true, -1, null, -1,
                        null, 0, true, null, -1));
        store.save(saved);
        List<VehicleManager.VehicleSnapshot> loaded = store.load();
        assertEquals(2, loaded.size());
        assertEquals(123, loaded.get(0).fire(),
                "the burning cart's clock survived the round trip");
        assertEquals(-1, loaded.get(1).fire(), "the grace-band default held");
    }

    @Test
    void theV2FileLoadsItsFireRowWithTheFreshBodyDefault() throws IOException {
        // A hand-written v2 file (the Slice 66 envelope): the fire row is
        // absent — the load answers the fresh-body grace (-1), exactly
        // like the reference's absent Fire row reading back through the
        // constructor's own default.
        Path file = dataDir.resolve("vehicles.zvd");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(file)))) {
            out.writeByte('Z');
            out.writeByte('V');
            out.writeByte('D');
            out.writeByte(2); // the v2 envelope
            out.writeByte(1); // one cart
            writeV2String(out, "MINECART");
            out.writeDouble(1.0);
            out.writeDouble(65.0);
            out.writeDouble(2.0);
            out.writeFloat(90.0f);
            out.writeFloat(4.0f);
        }
        VehicleDataStore store = new VehicleDataStore(file);
        List<VehicleManager.VehicleSnapshot> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals("MINECART", loaded.get(0).kind());
        assertEquals(-VehicleEntity.FIRE_SAFE_TICKS, loaded.get(0).fire(),
                "the v2 file's fire row reads the fresh-body default");
    }

    /** The store's own varint-string shape (the v2 writer's form). */
    private static void writeV2String(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeByte(bytes.length);
        out.write(bytes);
    }
}
