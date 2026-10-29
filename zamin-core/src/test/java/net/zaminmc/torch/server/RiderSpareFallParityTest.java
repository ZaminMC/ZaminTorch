package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.server.entity.vehicle.VehicleManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-RIDER-SPARE (the vehicle landing's rider delegate) — the reference's
 * {@code Entity.takeFallDamage} lines 748-752: the non-living base
 * forwards the landing UP to the rider
 * ({@code if (this.rider != null) this.rider.takeFallDamage(distance,
 * damageMultiplier)}), and {@code LivingEntity.takeFallDamage} line 824's
 * {@code super} call runs the same delegate FIRST for the mounts — so a
 * seated player pays the mount's landing with the SAME multiplier the
 * body's own formula reads: the pad's ZERO spares the rider with the
 * body, the common blocks' 1.0 wounds the rider with the body, and the
 * grief-refused farmland's early return skips both.
 *
 * <p>THE VEHICLE half is the whole fall walk the engine lacked: the
 * store accumulated while airborne (Entity.checkFallDamage lines
 * 728-731), the landing's cell consult (the -0.2 feet cell with the
 * fence fallback), and the payment — the base ALONE, a vehicle has NO
 * formula of its own: the seated player pays, the vehicle pays nothing
 * (its health never moves). The farmland's instanceof LivingEntity gate
 * (FarmlandBlock.onFallenOn lines 57-71) skips vehicles whole.</p>
 */
class RiderSpareFallParityTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The shared stubs (the fall-path family's shape)
    // ------------------------------------------------------------------

    static long key(int x, int y, int z) {
        return ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL);
    }

    static BlockType read(Map<Long, BlockType> cells, int x, int y, int z) {
        BlockType cell = cells.get(key(x, y, z));
        return cell != null ? cell : BuiltinBlocks.AIR;
    }

    /** A flat floor of one material at cell y=63 — the top at 64.0. */
    static void floor(Map<Long, BlockType> cells, String material) {
        BlockType type = switch (material) {
            case "slime_block" -> BuiltinBlocks.SLIME_BLOCK;
            case "farmland" -> BuiltinBlocks.FARMLAND;
            default -> BuiltinBlocks.STONE;
        };
        for (int x = -100; x <= 100; x++) {
            for (int z = -100; z <= 100; z++) {
                cells.put(key(x, 63, z), type);
            }
        }
    }

    /** The recorded delegate: one (rider, distance, multiplier) triple per landing. */
    record Paid(int rider, float distance, float multiplier) {
    }

    // ------------------------------------------------------------------
    // The mount's delegate (the recording shape)
    // ------------------------------------------------------------------

    private static class MountWorld implements MobEntity.WorldQuery {
        final Map<Long, BlockType> cells = new HashMap<>();
        boolean griefing = true;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(cells,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        public Identifier blockIdAt(int x, int y, int z) {
            return read(cells, x, y, z).identifier();
        }

        @Override
        public boolean mobGriefing() {
            return griefing;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return false;
        }

        private final net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                new net.zaminmc.torch.server.physics.EntityCollisionWorld() {
                    @Override
                    public BlockType blockAt(int x, int y, int z) {
                        return read(cells, x, y, z);
                    }

                    @Override
                    public boolean chunkLoaded(int x, int z) {
                        return true;
                    }
                };

        @Override
        public net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    @Test
    void theMountsLandingPaysTheRiderThroughTheDelegate() {
        // Over the common blocks: the delegate forwards the FULL pair —
        // the rider pays 1.0 with the mount (the pre-fix engine paid the
        // mount alone; the seated player never felt the landing).
        ConcurrentLinkedQueue<Paid> paid = new ConcurrentLinkedQueue<>();
        MountWorld stone = new MountWorld();
        floor(stone.cells, "stone");
        MobEntity pig = new MobEntity(9, MobType.PIG,
                new Position(5.5, 70.0, 5.5), new Random(11), stone);
        pig.setRider(77);
        pig.setRiderFall((rider, distance, multiplier) -> paid.add(new Paid(rider, distance, multiplier)));
        for (int i = 0; i < 80 && !pig.onGround(); i++) {
            pig.tick(false);
        }
        assertTrue(pig.onGround(), "the pig landed");
        assertEquals(1, paid.size(), "one delegate per landing");
        Paid stonePaid = paid.poll();
        assertEquals(77, stonePaid.rider());
        assertEquals(1.0f, stonePaid.multiplier(), 1e-9, "the common blocks' 1.0");
        assertTrue(stonePaid.distance() > 5.0f && stonePaid.distance() < 7.0f,
                "the full descent forwarded: " + stonePaid.distance());

        // Over the pad: the ZERO multiplier spares the rider with the
        // mount — the pig's own wound stays zero (the landed law) and
        // the delegate carries the same 0.0.
        ConcurrentLinkedQueue<Paid> padPaid = new ConcurrentLinkedQueue<>();
        MountWorld pad = new MountWorld();
        floor(pad.cells, "slime_block");
        MobEntity pigOnPad = new MobEntity(9, MobType.PIG,
                new Position(5.5, 70.0, 5.5), new Random(11), pad);
        float pigHealthBefore = pigOnPad.health();
        pigOnPad.setRider(77);
        pigOnPad.setRiderFall((rider, distance, multiplier) -> padPaid.add(new Paid(rider, distance, multiplier)));
        for (int i = 0; i < 80 && !pigOnPad.onGround(); i++) {
            pigOnPad.tick(false);
        }
        assertTrue(pigOnPad.onGround(), "the pig landed on the pad");
        assertEquals(1, padPaid.size(), "the delegate still fires over the pad");
        Paid padPair = padPaid.poll();
        assertEquals(0.0f, padPair.multiplier(), 1e-9, "the pad's ZERO forwards — the rider is spared");
        assertEquals(pigHealthBefore, pigOnPad.health(), 1e-9, "the pad's own law holds (the mount unwounded)");

        // The grief-refused farmland: the early return skips the WHOLE
        // call — no delegate, no wound, no trample.
        ConcurrentLinkedQueue<Paid> refused = new ConcurrentLinkedQueue<>();
        MountWorld farm = new MountWorld();
        floor(farm.cells, "farmland");
        farm.griefing = false;
        MobEntity pigOnFarm = new MobEntity(9, MobType.PIG,
                new Position(5.5, 70.0, 5.5), new Random(11), farm);
        pigOnFarm.setRider(77);
        pigOnFarm.setRiderFall((rider, distance, multiplier) -> refused.add(new Paid(rider, distance, multiplier)));
        for (int i = 0; i < 80 && !pigOnFarm.onGround(); i++) {
            pigOnFarm.tick(false);
        }
        assertTrue(pigOnFarm.onGround(), "the pig landed on the farmland");
        assertEquals(0, refused.size(), "the refused grief skips the delegate whole");
    }

    // ------------------------------------------------------------------
    // The vehicle's fall whole (the manager's walk)
    // ------------------------------------------------------------------

    private static class VehicleFloorWorld implements VehicleEntity.WorldQuery {
        final Map<Long, BlockType> cells = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(cells,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        public Identifier blockIdAt(int x, int y, int z) {
            return read(cells, x, y, z).identifier();
        }

        private final net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                new net.zaminmc.torch.server.physics.EntityCollisionWorld() {
                    @Override
                    public BlockType blockAt(int x, int y, int z) {
                        return read(cells, x, y, z);
                    }

                    @Override
                    public boolean chunkLoaded(int x, int z) {
                        return true;
                    }
                };

        @Override
        public net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    @Test
    void theVehiclesFallWholePaysTheRiderAlone() {
        // Over the common blocks: the boat falls, the store accumulates,
        // the landing pays the RIDER at 1.0 — and the vehicle's own health
        // never moves (a vehicle has no fall formula of its own; the
        // pre-fix engine never paid the seated player at all).
        ConcurrentLinkedQueue<Paid> paid = new ConcurrentLinkedQueue<>();
        VehicleFloorWorld stone = new VehicleFloorWorld();
        floor(stone.cells, "stone");
        VehicleManager manager = new VehicleManager(stone,
                vehicle -> { }, new Random(1), 100);
        manager.setRiderFall((rider, distance, multiplier) ->
                paid.add(new Paid(rider, distance, multiplier)));
        VehicleEntity boat = manager.spawnBoat(new Position(5.5, 70.0, 5.5), 0);
        assertTrue(manager.mount(boat, 7), "the seat takes the rider");
        float boatHealthBefore = boat.health();
        for (int i = 0; i < 200 && !boat.onGround(); i++) {
            manager.tick();
        }
        assertTrue(boat.onGround(), "the boat landed");
        assertEquals(0.0f, boat.fallDistance(), 1e-9, "the store spent at the landing");
        assertEquals(1, paid.size(), "one delegate per landing");
        Paid stonePair = paid.poll();
        assertEquals(7, stonePair.rider());
        assertEquals(1.0f, stonePair.multiplier(), 1e-9, "the common blocks' 1.0");
        assertTrue(stonePair.distance() > 5.0f && stonePair.distance() < 7.0f,
                "the accumulated descent: " + stonePair.distance());
        assertEquals(boatHealthBefore, boat.health(), 1e-9,
                "the vehicle pays nothing itself (the non-living base)");

        // Over the pad: the ZERO forwards — the rider is spared with the
        // vehicle.
        ConcurrentLinkedQueue<Paid> padPaid = new ConcurrentLinkedQueue<>();
        VehicleFloorWorld pad = new VehicleFloorWorld();
        floor(pad.cells, "slime_block");
        VehicleManager padManager = new VehicleManager(pad,
                vehicle -> { }, new Random(1), 100);
        padManager.setRiderFall((rider, distance, multiplier) ->
                padPaid.add(new Paid(rider, distance, multiplier)));
        VehicleEntity padBoat = padManager.spawnBoat(new Position(5.5, 70.0, 5.5), 0);
        padManager.mount(padBoat, 7);
        for (int i = 0; i < 200 && !padBoat.onGround(); i++) {
            padManager.tick();
        }
        assertTrue(padBoat.onGround(), "the boat landed on the pad");
        assertEquals(1, padPaid.size());
        assertEquals(0.0f, padPaid.poll().multiplier(), 1e-9, "the pad's ZERO forwards");

        // The farmland: the instanceof LivingEntity gate skips vehicles
        // WHOLE — no payment, the store still spent.
        ConcurrentLinkedQueue<Paid> farmPaid = new ConcurrentLinkedQueue<>();
        VehicleFloorWorld farm = new VehicleFloorWorld();
        floor(farm.cells, "farmland");
        VehicleManager farmManager = new VehicleManager(farm,
                vehicle -> { }, new Random(1), 100);
        farmManager.setRiderFall((rider, distance, multiplier) ->
                farmPaid.add(new Paid(rider, distance, multiplier)));
        VehicleEntity farmBoat = farmManager.spawnBoat(new Position(5.5, 70.0, 5.5), 0);
        farmManager.mount(farmBoat, 7);
        for (int i = 0; i < 200 && !farmBoat.onGround(); i++) {
            farmManager.tick();
        }
        assertTrue(farmBoat.onGround(), "the boat landed on the farmland");
        assertEquals(0, farmPaid.size(), "the farmland skips vehicles whole");
        assertEquals(0.0f, farmBoat.fallDistance(), 1e-9, "the store spent all the same");
    }

    // ------------------------------------------------------------------
    // The seated player's end-to-end payment (the full server)
    // ------------------------------------------------------------------

    @Test
    void theSeatedPlayerPaysTheDelegatedFall() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession rider = join(server, "Rider");
            // A pig at eleven blocks over the plains surface (y=4): the
            // seated player pays the delegated fall — the wound
            // ceil(11 - 3) through the enchant consult and the fallen
            // row at round(11 * 100).
            MobEntity pig = server.mobs().spawnAt(
                    MobType.PIG, new Position(5.5, 15.0, 5.5));
            pig.applySaddle();
            // The engine's own mount flow (the listener sets the session's
            // riding state — the seat's own-landing skip rides it).
            assertTrue(server.mobs().mountMob(pig, rider.engineEntityId(),
                            "Rider", "00000000-0000-0000-0000-000000000000"),
                    "the pig takes the seat");
            float playerBefore = rider.health();
            float pigBefore = pig.health();
            double spawnY = pig.position().y();
            await(() -> pig.onGround(), "the pig landed");
            // The measured descent (whatever the terrain at the landing
            // cell): the delegate's pair and both bodies' wounds read it.
            double descent = spawnY - pig.position().y();
            int expectedWound = (int) Math.ceil(descent - 3.0);
            assertEquals(playerBefore - expectedWound, rider.health(), 1e-9,
                    "the seated player pays the delegated fall (descent " + descent + ")");
            assertEquals(pigBefore - expectedWound, pig.health(), 1e-9,
                    "the mount pays its own formula all the same (descent " + descent + ")");
            assertTrue(rider.stats().get(
                            net.zaminmc.torch.server.stat.EngineStats.CM_FALLEN)
                            >= Math.round(descent * 100.0) - 100,
                    "the fallen row paid the delegated landing: "
                            + rider.stats().get(net.zaminmc.torch.server.stat.EngineStats.CM_FALLEN)
                            + " for descent " + descent);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The harness (the SlimePad family's shape)
    // ------------------------------------------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "riderspare",
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        server.ticker().submit(() -> session.setOpLevel(2));
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timeout: " + description);
            }
            Thread.sleep(25);
        }
    }
}
