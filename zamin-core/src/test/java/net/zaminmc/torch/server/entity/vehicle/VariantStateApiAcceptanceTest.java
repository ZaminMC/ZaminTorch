package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.entity.VariantState;
import net.zaminmc.torch.entity.Vehicle;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The VariantState read view over the public API (Slice 68 — §27.3's
 * complete-exposure wave the sections 27/28 audit named next): every
 * read goes through the {@link Vehicle} handle and the
 * {@link VariantState} views alone — no engine class is named by the
 * read paths (§27.10's "through the public API without depending on
 * internal classes").
 *
 * <p>The harness constructs the carts directly (the engine's own unit
 * surface); the reads under test are strictly the API's. The engine
 * object IS the handle (§27.5) — the same object the world's
 * simulation context ticks.</p>
 */
class VariantStateApiAcceptanceTest {

    /** The flat ground world the carts rest on. */
    private static final VehicleEntity.WorldQuery GROUND_WORLD =
            new VehicleEntity.WorldQuery() {
                @Override public boolean isSolid(double x, double y, double z) {
                    return y < 0.0;
                }
            };

    /** The recording spawn host (the spawner cart's engine surface). */
    private static final class RecordingHost implements SpawnerMinecartEntity.SpawnHost {
        @Override public boolean playerNearby(double x, double y, double z, double range) {
            return true;
        }

        @Override public int nearbyOfKind(
                net.zaminmc.torch.server.entity.MobType type, int cellX, int cellY,
                int cellZ, int range) {
            return 0;
        }

        @Override public boolean spawnAt(
                net.zaminmc.torch.server.entity.MobType type, double x, double y, double z) {
            return false;
        }
    }

    /** The recording command host (the command cart's engine surface). */
    private static final class RecordingCommandHost
            implements CommandBlockMinecartEntity.CommandHost {
        @Override public int run(CommandBlockMinecartEntity cart, String command) {
            return 0;
        }
    }

    @Test
    void theFurnaceCartExposesItsOwnStateThroughTheView() {
        Vehicle handle = new FurnaceMinecartEntity(9001,
                new Position(10.5, 0.35, 10.5), new Random(7L));
        assertTrue(handle.variantState().isPresent(),
                "the furnace cart answers a variant state");
        VariantState.Furnace furnace =
                assertInstanceOf(VariantState.Furnace.class, handle.variantState().orElseThrow());
        assertEquals(0, furnace.fuelTicks(), "a fresh cart carries an empty tank");
        assertFalse(furnace.lit(), "an empty tank is not lit");
        assertEquals(0.0, furnace.pushX(), "no push vector before a feed");
        assertEquals(0.0, furnace.pushZ(), "no push vector before a feed");

        // The feed (the engine's own write arm — the player's coal): the
        // player stands one block west, so the cart aims east.
        ((FurnaceMinecartEntity) handle).feedAndAim(9.5, 10.5, true);
        assertEquals(FurnaceMinecartEntity.COAL_FUEL_TICKS, furnace.fuelTicks(),
                "one coal feed adds the vanilla 3600");
        assertTrue(furnace.lit(), "a fueled tank is lit");
        assertEquals(1.0, furnace.pushX(), "the push vector aims away from the player");
        assertEquals(0.0, furnace.pushZ(), "the z axis carries no aim");

        // One tick of the world (the engine's own clock — the harness
        // side): the tank burns one.
        ((FurnaceMinecartEntity) handle).tick(GROUND_WORLD);
        assertEquals(FurnaceMinecartEntity.COAL_FUEL_TICKS - 1, furnace.fuelTicks(),
                "the tank burns one per tick");
    }

    @Test
    void theTntCartExposesItsFuseThroughTheView() {
        Vehicle handle = new TntMinecartEntity(9002,
                new Position(10.5, 0.35, 10.5), new Random(7L));
        VariantState.Tnt tnt =
                assertInstanceOf(VariantState.Tnt.class, handle.variantState().orElseThrow());
        assertEquals(-1, tnt.fuseTicks(), "a fresh cart is unprimed (the -1 field)");
        assertFalse(tnt.primed(), "an unprimed cart is not primed");

        ((TntMinecartEntity) handle).prime();
        assertEquals(TntMinecartEntity.FUSE_TICKS, tnt.fuseTicks(),
                "the prime arms the vanilla 80");
        assertTrue(tnt.primed(), "a primed cart is primed");
    }

    @Test
    void theSpawnerCartExposesItsLawThroughTheView() {
        Vehicle handle = new SpawnerMinecartEntity(9003,
                new Position(10.5, 0.35, 10.5), new Random(7L), new RecordingHost());
        VariantState.Spawner spawner =
                assertInstanceOf(VariantState.Spawner.class, handle.variantState().orElseThrow());
        assertEquals("minecraft:pig", spawner.spawnType().toString(),
                "the reference's own field default is the pig");
        assertEquals(SpawnerMinecartEntity.INITIAL_DELAY, spawner.delayTicks(),
                "the initial delay is the vanilla 20");
        assertEquals(200, spawner.minSpawnDelay(), "the reroll floor is the vanilla 200");
        assertEquals(800, spawner.maxSpawnDelay(), "the reroll ceiling is the vanilla 800");
        assertEquals(4, spawner.spawnCount(), "the attempts per cycle are the vanilla 4");
        assertEquals(6, spawner.maxNearbyEntities(), "the nearby cap is the vanilla 6");
        assertEquals(16, spawner.requiredPlayerRange(), "the player gate is the vanilla 16");
        assertEquals(4, spawner.spawnRange(), "the scatter range is the vanilla 4");
    }

    @Test
    void theCommandCartExposesItsExecutorThroughTheView() {
        Vehicle handle = new CommandBlockMinecartEntity(9004,
                new Position(10.5, 0.35, 10.5), new Random(7L), new RecordingCommandHost());
        VariantState.Command command =
                assertInstanceOf(VariantState.Command.class, handle.variantState().orElseThrow());
        assertEquals("", command.command(), "a fresh cart carries no command");
        assertEquals(0, command.successCount(), "no successes yet");
        assertTrue(command.trackOutput(), "output tracking defaults on");
        assertNull(command.lastOutput(), "no output recorded yet");

        CommandBlockMinecartEntity engine = (CommandBlockMinecartEntity) handle;
        engine.setCommand("say hi");
        engine.setSuccessCount(3);
        engine.setTrackOutput(false);
        engine.setLastOutput("hi");
        assertEquals("say hi", command.command(), "the command reads through the view");
        assertEquals(3, command.successCount(), "the success count reads through the view");
        assertFalse(command.trackOutput(), "the track flag reads through the view");
        assertEquals("hi", command.lastOutput(), "the last output reads through the view");
    }

    @Test
    void thePlainKindsAnswerNoVariantState() {
        Vehicle plainCart = new MinecartEntity(9005,
                new Position(10.5, 0.35, 10.5), new Random(7L));
        assertTrue(plainCart.variantState().isEmpty(),
                "the plain minecart has no variant state");

        Vehicle chestCart = new ChestMinecartEntity(9006,
                new Position(10.5, 0.35, 10.5), new Random(7L));
        assertTrue(chestCart.variantState().isEmpty(),
                "the chest cart's container rides cargo(), not variantState()");
        assertTrue(chestCart.cargo().isPresent(),
                "the chest cart still answers its cargo");

        Vehicle boat = new BoatEntity(9007,
                new Position(10.5, 0.35, 10.5), new Random(7L));
        assertTrue(boat.variantState().isEmpty(), "the boat has no variant state");
        assertTrue(boat.cargo().isEmpty(), "the boat has no cargo");
    }
}
