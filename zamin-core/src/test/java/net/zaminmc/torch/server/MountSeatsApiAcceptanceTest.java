package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.World;
import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.entity.Vehicle;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The riding seat rows' public API surface (§27.3 — the §29 VII5/VIII1
 * exposure, the §27.10 capability test): the exotic pairs' seat reads
 * beside the vehicle family's own typed {@code Vehicle.riderEntityId()}
 * — a mount's seated-rider row, a rider's mount row, and the live seat
 * census — exercised over the LIVE engine through the api types alone
 * (every READ goes through {@link Server#seatRows()}, the api worlds,
 * and the api handles; no cast to the engine's internals). The pairs
 * seat through the engine's own import seams — the anvil riding
 * chain's path (the reference's startRiding-at-load,
 * {@code AnvilChunkStorage} line 335): the generic-mount registry's
 * {@code seatImportedRiderOnGenericMount} (the Surfer scene's own
 * handle shape), the mob mounts' {@code seatImportedRider}, and the
 * vehicle seats' {@code VehicleManager.mount}.
 */
class MountSeatsApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The scene's ground cell (flat ground level 4 — the village test's own neighborhood). */
    private static final int SCENE_X = 66;
    private static final int SCENE_Z = 66;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "msa" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        // THE NIGHT SCENE (the api's own time verb — the documented owner
        // path): the undead ignite in the day band (the historical sun
        // burn), and the seated pairs must outlive the burn; midnight keeps
        // the scene deterministic.
        World world = ((Server) server).worlds().iterator().next();
        world.execute(() -> world.setTimeOfDay(18_000));
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * THE GENERIC-MOUNT PAIR (the exotic mount-side shape — a mob
     * seated on a NON-vehicle/mob mount, the anvil scene's Surfer
     * zombie riding a dropped diamond): the seat opens through the
     * import's own seam, and every row reads through the api — the
     * mount's rider row, the rider's mount row, the census row, and
     * the follow the rows answer FOR. A non-seated entity and a
     * never-existing id answer the empty row.
     */
    @Test
    void theExoticGenericPairReadsThroughThePublicSurface() throws Exception {
        Server api = server;
        loadSceneChunk();

        // The scene (the engine's own import seams — the anvil riding
        // chain's path): a zombie and a dropped diamond.
        MobEntity rider = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(SCENE_X + 0.5, 4, SCENE_Z + 0.5));
        ItemEntity mount = server.itemEntities().spawnRestored(
                new Position(SCENE_X + 0.5, 4.05, SCENE_Z + 0.5),
                new ItemStack(BuiltinItems.DIAMOND, 1));
        int riderId = rider.entityId();
        int mountId = mount.entityId();

        // THE NON-SEATED CONTROL: the fresh pair carries no row — the
        // mount answers the empty seat, the rider answers no mount, and
        // a never-existing id answers the empty row both directions.
        assertEquals(-1, api.seatRows().seatedRiderEntityId(mountId),
                "the fresh item mount's seat answers empty");
        assertEquals(-1, api.seatRows().mountEntityId(riderId),
                "the fresh mob rider answers no mount");
        assertEquals(-1, api.seatRows().seatedRiderEntityId(999_999),
                "a never-existing mount id answers the empty row");
        assertEquals(-1, api.seatRows().mountEntityId(999_999),
                "a never-existing rider id answers the empty row");
        assertTrue(api.seatRows().seatRows().stream()
                        .noneMatch(row -> row.riderEntityId() == riderId),
                "the fresh census carries no row for the rider");

        // THE SEAT (the import's own GenericSeatSeam shape: the mount's
        // live position provider + the manager's alive consult + the
        // item family's quarter-height seat band, Entity.java lines
        // 1367-1369's 0.25 * 0.75 = +0.1875).
        assertTrue(server.seatImportedRiderOnGenericMount(mountId,
                        mount::position,
                        () -> server.itemEntities().all().stream()
                                .anyMatch(live -> live.entityId() == mountId),
                        0.1875, riderId),
                "the generic seat opened (the one-rider law: the seat was free)");

        // THE ONE-RIDER LAW: the occupied seat refuses the second rider.
        MobEntity secondRider = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(SCENE_X + 1.5, 4, SCENE_Z + 1.5));
        assertFalse(server.seatImportedRiderOnGenericMount(mountId,
                        mount::position,
                        () -> server.itemEntities().all().stream()
                                .anyMatch(live -> live.entityId() == mountId),
                        0.1875, secondRider.entityId()),
                "the occupied seat refuses the second rider");

        // THE READS — through the api alone (§27.10): the mount's rider
        // row, the rider's mount row, and the census row.
        assertEquals(riderId, api.seatRows().seatedRiderEntityId(mountId),
                "the mount's rider row reads the seated mob (the engine id)");
        assertEquals(mountId, api.seatRows().mountEntityId(riderId),
                "the rider's mount row reads the item mount (the engine id)");
        assertTrue(api.seatRows().seatRows().stream().anyMatch(
                        row -> row.mountEntityId() == mountId
                                && row.riderEntityId() == riderId),
                "the census carries the exotic pair's (mount id, rider id) row");

        // THE ROW ANSWERS FOR LIVE STATE (§27.5): the follow arm the
        // resolver drives anchors the rider at the mount's own cell —
        // read through the api handles (World.entities() is the api
        // view; World.itemEntities() the same).
        World world = api.worlds().iterator().next();
        await(() -> {
            LivingEntity seated = world.entities().stream()
                    .filter(live -> live.entityId() == riderId)
                    .findFirst().orElse(null);
            return seated != null
                    && Math.abs(seated.position().x() - (SCENE_X + 0.5)) < 0.1
                    && Math.abs(seated.position().z() - (SCENE_Z + 0.5)) < 0.1;
        }, "the seated rider follows the item mount's cell (the base Entity's "
                + "follow law behind the seat rows)");
    }

    /**
     * THE MOB-MOUNT PAIR (the exotic pairs' own origin — an imported mob
     * on ANOTHER mob): the seat opens through the import's mob-seat
     * seam, and both rows read through the api, the follow anchoring
     * the rider at the mount's seat band.
     */
    @Test
    void theMobMountPairReadsThroughThePublicSurface() throws Exception {
        Server api = server;
        loadSceneChunk();

        MobEntity mount = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(SCENE_X + 0.5, 4, SCENE_Z + 0.5));
        MobEntity rider = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(SCENE_X + 1.5, 4, SCENE_Z + 1.5));
        int mountId = mount.entityId();
        int riderId = rider.entityId();
        assertEquals(-1, api.seatRows().seatedRiderEntityId(mountId),
                "the fresh mob mount's seat answers empty");

        // The seat (the anvil chain's mob-mount arm — the reference's
        // startRiding-at-load: the plain seat, no temper flow).
        server.mobs().seatImportedRider(mount, riderId);

        // The reads — through the api alone.
        assertEquals(riderId, api.seatRows().seatedRiderEntityId(mountId),
                "the mob mount's rider row reads the seated mob");
        assertEquals(mountId, api.seatRows().mountEntityId(riderId),
                "the mob rider's mount row reads the mob mount");
        assertTrue(api.seatRows().seatRows().stream().anyMatch(
                        row -> row.mountEntityId() == mountId
                                && row.riderEntityId() == riderId),
                "the census carries the mob-mount pair's row");

        // The follow (the mob-on-mob arm): the rider anchors at the
        // mount's cell — both positions read through the api handles.
        World world = api.worlds().iterator().next();
        await(() -> {
            LivingEntity seated = world.entities().stream()
                    .filter(live -> live.entityId() == riderId)
                    .findFirst().orElse(null);
            LivingEntity walking = world.entities().stream()
                    .filter(live -> live.entityId() == mountId)
                    .findFirst().orElse(null);
            return seated != null && walking != null
                    && Math.abs(seated.position().x() - walking.position().x()) < 0.6
                    && Math.abs(seated.position().z() - walking.position().z()) < 0.6;
        }, "the mob rider follows the mob mount's cell (the follow the row answers for)");
    }

    /**
     * THE VEHICLE FAMILY'S ROWS STILL ANSWER (the lane's own guard): the
     * boat-on-minecart pair (VIII1's rider-side scene) seats through
     * the vehicle manager's own mount verb, the EXISTING typed read
     * {@code Vehicle.riderEntityId()} still answers the cart's rider,
     * and the new seat-row consults agree with it — the same seat
     * answered beside the vehicle family's own read.
     */
    @Test
    void theVehicleFamilyRowsStillAnswerBesideTheSeatRows() throws Exception {
        Server api = server;
        loadSceneChunk();

        VehicleEntity cart = server.vehicles().spawnMinecart(
                new Position(SCENE_X + 0.5, 4, SCENE_Z + 0.5));
        VehicleEntity boat = server.vehicles().spawnBoat(
                new Position(SCENE_X + 1.5, 4, SCENE_Z + 1.5));
        int cartId = cart.entityId();
        int boatId = boat.entityId();

        assertTrue(server.vehicles().mount(cart, boatId),
                "the cart's seat opened for the boat rider");
        // THE ONE-RIDER LAW: the occupied seat refuses a real second rider.
        VehicleEntity secondBoat = server.vehicles().spawnBoat(
                new Position(SCENE_X + 2.5, 4, SCENE_Z + 2.5));
        assertFalse(server.vehicles().mount(cart, secondBoat.entityId()),
                "the occupied seat refuses the second rider");

        // The EXISTING typed read (the api handle through World.vehicles()).
        World world = api.worlds().iterator().next();
        Vehicle cartHandle = world.vehicles().stream()
                .filter(live -> live.entityId() == cartId)
                .findFirst().orElseThrow();
        assertEquals(boatId, cartHandle.riderEntityId(),
                "the vehicle family's own typed read still answers the seat");

        // The new consults agree — the same row, read beside the typed read.
        assertEquals(boatId, api.seatRows().seatedRiderEntityId(cartId),
                "the seat-row consult agrees with the typed read");
        assertEquals(cartId, api.seatRows().mountEntityId(boatId),
                "the boat rider's mount row reads the cart (the rider side)");
        assertTrue(api.seatRows().seatRows().stream().anyMatch(
                        row -> row.mountEntityId() == cartId
                                && row.riderEntityId() == boatId),
                "the census carries the vehicle pair's row");
    }

    /**
     * THE LIVE RESOLUTION (§27.5 — the handle law): the rows resolve the
     * live stores fresh — a dismount changes the next answer
     * immediately, never a cached copy. The generic pair's seat opens
     * when the rider departs (the api {@code hurt} verb → the death
     * walk drops the registry row at the corpse's removal); the
     * mob-mount pair's seat opens with the rider's own death arm; the
     * vehicle seat opens with the engine's dismount verb.
     */
    @Test
    void theLiveResolutionDismountFlipsTheAnswer() throws Exception {
        Server api = server;
        loadSceneChunk();

        // The three pairs, seated through the import seams.
        ItemEntity itemMount = server.itemEntities().spawnRestored(
                new Position(SCENE_X + 0.5, 4.05, SCENE_Z + 0.5),
                new ItemStack(BuiltinItems.DIAMOND, 1));
        MobEntity genericRider = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(SCENE_X + 0.5, 4, SCENE_Z + 0.5));
        MobEntity mobMount = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(SCENE_X + 2.5, 4, SCENE_Z + 0.5));
        MobEntity mobRider = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(SCENE_X + 2.5, 4, SCENE_Z + 1.5));
        VehicleEntity cart = server.vehicles().spawnMinecart(
                new Position(SCENE_X + 3.5, 4, SCENE_Z + 0.5));
        VehicleEntity boat = server.vehicles().spawnBoat(
                new Position(SCENE_X + 3.5, 4, SCENE_Z + 1.5));
        int itemMountId = itemMount.entityId();
        int genericRiderId = genericRider.entityId();
        int mobMountId = mobMount.entityId();
        int mobRiderId = mobRider.entityId();
        int cartId = cart.entityId();
        int boatId = boat.entityId();
        assertTrue(server.seatImportedRiderOnGenericMount(itemMountId,
                        itemMount::position,
                        () -> server.itemEntities().all().stream()
                                .anyMatch(live -> live.entityId() == itemMountId),
                        0.1875, genericRiderId),
                "the generic seat opened");
        server.mobs().seatImportedRider(mobMount, mobRiderId);
        assertTrue(server.vehicles().mount(cart, boatId), "the cart seat opened");
        assertEquals(genericRiderId, api.seatRows().seatedRiderEntityId(itemMountId));
        assertEquals(mobRiderId, api.seatRows().seatedRiderEntityId(mobMountId));
        assertEquals(boatId, api.seatRows().seatedRiderEntityId(cartId));

        World world = api.worlds().iterator().next();

        // THE GENERIC PAIR'S DISMOUNT — the rider departs through the
        // api's own verb (LivingEntity.hurt): the death walk carries the
        // corpse out of the manager (the death animation's cadence),
        // and the follow arm drops the registry row (the detach the
        // seat answers for).
        world.entities().stream()
                .filter(live -> live.entityId() == genericRiderId)
                .findFirst().orElseThrow().hurt(1000.0f);
        await(() -> api.seatRows().mountEntityId(genericRiderId) == -1,
                "the departed rider's mount row answers empty (the registry row dropped)");
        assertEquals(-1, api.seatRows().seatedRiderEntityId(itemMountId),
                "the mount's rider row answers the opened seat");
        await(() -> api.seatRows().seatRows().stream()
                        .noneMatch(row -> row.mountEntityId() == itemMountId),
                "the census drops the opened seat's row");

        // THE MOB-MOUNT PAIR'S DISMOUNT — the rider's death opens the
        // mount's seat (the follow arm's own depart law).
        world.entities().stream()
                .filter(live -> live.entityId() == mobRiderId)
                .findFirst().orElseThrow().hurt(1000.0f);
        await(() -> api.seatRows().seatedRiderEntityId(mobMountId) == -1,
                "the dead rider opens the mob mount's seat");
        await(() -> api.seatRows().mountEntityId(mobRiderId) == -1,
                "the dead rider's own mount row answers empty");

        // THE VEHICLE SEAT'S DISMOUNT — the engine's own dismount verb
        // (the mutations are the vehicle family's own, not this lane's):
        // the same-lane state flip reads immediately.
        cart.dismount();
        assertEquals(-1, api.seatRows().seatedRiderEntityId(cartId),
                "the dismount verb opens the seat — the next answer flips (no cached copy)");
        assertEquals(-1, api.seatRows().mountEntityId(boatId),
                "the dismounted rider's mount row flips with it");
        await(() -> api.seatRows().seatRows().stream()
                        .noneMatch(row -> row.mountEntityId() == cartId),
                "the census drops the dismounted vehicle's row");
    }

    /**
     * THE ABSENT-SURFACE CONTRACT (the villages() lane's own shape): the
     * {@code EMPTY} view answers the empty row and the empty census —
     * the unbooted world's answer, a plugin-facing contract pinned
     * without any engine behind it.
     */
    @Test
    void theAbsentSurfaceAnswersEmpty() {
        Vehicle.SeatRows empty = Vehicle.SeatRows.EMPTY;
        assertEquals(-1, empty.seatedRiderEntityId(1), "the absent mount's rider row");
        assertEquals(-1, empty.mountEntityId(1), "the absent rider's mount row");
        assertTrue(empty.seatRows().isEmpty(), "the absent census answers empty");
    }

    private void loadSceneChunk() throws Exception {
        var cell = new net.zaminmc.torch.block.BlockPosition(SCENE_X, 4, SCENE_Z);
        server.requestChunkLoad(cell.chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(cell.chunkPosition()),
                "the scene's chunk loaded");
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
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
}
