package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.entity.vehicle.BoatEntity;
import net.zaminmc.torch.server.entity.vehicle.MinecartEntity;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PX — THE PUSH FAMILY'S ADJACENT CARRIERS (the re-pin's head row):
 * the reference's gather over {@code world.getEntities} carries THREE
 * families the engine's mob-mob scope never reached — the PLAYERS
 * (living bodies, PlayerEntity.mobTick line 455 running the shared push
 * block through {@code super.mobTick()}) and the CARTS/BOATS (the two
 * isPushable-true non-living families, MinecartEntity line 107 /
 * BoatEntity line 63). Each seat reads the reference's own law:
 *
 * <p>THE PLAYER PAIR rides Entity.push's ordinary mutual law with the
 * engine-side no-wire law: the pushed player's velocity add lands on the
 * SESSION'S MOTION STORE alone (the vanilla wire never carries the push
 * to the pushed player's own client — the player's tracker rides
 * syncVelocity false, EntityMap line 95's three-arg form, and
 * addVelocity never marks damaged).</p>
 *
 * <p>THE CART PAIR rides MinecartEntity.push's WHOLESALE override (lines
 * 649-734): the squared-horizontal-distance form with the 1.0E-4 floor,
 * the 0.1-then-0.5 scale, the cart pushed away FULL and the pushing body
 * pushed away at QUARTER — no rider gates on either side.</p>
 *
 * <p>THE BOAT PAIR rides Entity.push unmodified: the ordinary mutual law
 * with each side's own rider gate (a boat with a passenger never moves).</p>
 */
class PlayerVehiclePushParityDiscoveryTest {

    /** A flat-world stub carrying the seats' own consults. */
    private static final class PushWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return false;
        }
    }

    private static MobManager managerIn(PushWorld world, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        return new MobManager(world, new Random(seed), new NoLoot(),
                996_000, (x, z) -> 8);
    }

    /** The pinned body (NoAI): the mind and the movement stand still, but
     *  the push walk still runs — the seats read the velocity the walk
     *  writes with no goal noise on top. */
    private static MobEntity pinned(MobManager manager, MobType type, Position at) {
        MobEntity mob = manager.spawnAt(type, at);
        assert mob != null : "the spawn never cancels in this seat";
        mob.setNoAi(true);
        return mob;
    }

    /** A grounded playing session at the given position (the arrow-envelope
     *  seat's own construction form). */
    private static PlayerSession player(String name, Position at, GameMode mode) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        session.setGamemode(mode);
        session.assignEngineEntityId(700_000 + name.hashCode() % 1000 & 0xfffff);
        assertEquals(PlayerState.PLAYING, session.state(), name + " playing");
        return session;
    }

    private static World stubWorld() {
        return new World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity>
            entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(
                    net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        };
    }

    // ------------------------------------------------------------------
    // Seat 1 — the mob-player pair separates mutually (both walks)
    // ------------------------------------------------------------------

    /**
     * A pinned cow and a player 0.2 apart: after the MOB'S OWN WALK the
     * player's motion store carries the away impulse and the cow carries
     * its own — and after THE PLAYER'S OWN WALK both double (the vanilla
     * shape: each living body's walk pushes the pair once). The exact
     * magnitudes: half = sqrt(0.2) * 0.05 per walk per side. RED on the
     * pre-fix engine (the mob's gather never carried the player arm —
     * the player's motion store stayed exactly zero however crowded).
     */
    @Test
    void theMobPlayerPairSeparatesMutually() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 81L);
        MobEntity cow = pinned(manager, MobType.COW, new Position(0.5, 8.0, 0.5));
        PlayerSession player = player("Walker",
                new Position(0.7, 8.0, 0.5), GameMode.SURVIVAL);
        List<PlayerSession> players = List.of(player);

        manager.tick(players, 6000L);

        double half = Math.sqrt(0.2) * 0.05;
        // The mob's walk alone: the cow's pusher arm landed whole (its
        // own tick's decay precedes the walk), the player's motion store
        // carries the pushed arm — NEVER a wire (the no-wire law).
        assertEquals(-half, cow.velocityX(), 1e-9,
                "the cow drifts away from the player (the mob's own walk)");
        assertEquals(half, player.motionX(), 1e-9,
                "the player's motion store carries the pushed arm (the"
                        + " pre-fix engine never wrote it)");
        assertEquals(0.0, player.motionZ(), 1e-9,
                "the push is horizontal alone (the axis pair is pure x)");

        // The player's own walk (PlayerEntity.mobTick's super call): the
        // pair pushes a second time — the vanilla doubling.
        manager.tickPlayerPushCollisions(player, players);
        assertEquals(-2.0 * half, cow.velocityX(), 1e-9,
                "the cow's impulse doubled (the player's own walk)");
        assertEquals(2.0 * half, player.motionX(), 1e-9,
                "the player's motion store doubled");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the spectator candidate never joins (the walker quirk)
    // ------------------------------------------------------------------

    /**
     * The gather's NOT_SPECTATOR filter (EntityFilter line 27): a
     * spectating player overlapping the cow never receives the push —
     * the mob's walk skips the candidate whole. The WALKER side carries
     * no spectator gate in the reference (the push block runs for every
     * living body): the spectating player's OWN walk still shoves the
     * cow — the vanilla quirk kept verbatim.
     */
    @Test
    void theSpectatorCandidateNeverJoinsTheGather() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 82L);
        MobEntity cow = pinned(manager, MobType.COW, new Position(0.5, 8.0, 0.5));
        PlayerSession spectator = player("Ghost",
                new Position(0.7, 8.0, 0.5), GameMode.SPECTATOR);
        List<PlayerSession> players = List.of(spectator);

        manager.tick(players, 6000L);

        assertEquals(0.0, spectator.motionX(), 1e-9,
                "the spectating candidate never receives the push");
        assertEquals(0.0, cow.velocityX(), 1e-9,
                "the cow never pushes the spectator back (the filter is"
                        + " the gather's own)");

        // The spectating walker still shoves (the reference's own
        // walker-side shape — no gate).
        manager.tickPlayerPushCollisions(spectator, players);
        double half = Math.sqrt(0.2) * 0.05;
        assertEquals(-half, cow.velocityX(), 1e-9,
                "the spectating walker's own walk still pushes the cow"
                        + " (the vanilla quirk)");
        assertEquals(half, spectator.motionX(), 1e-9,
                "the spectating walker's own motion store carries its arm");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the cart's quarter law (MinecartEntity.push's own form)
    // ------------------------------------------------------------------

    /**
     * A pinned cow and a minecart 0.2 apart: the CART'S OWN LAW (the
     * wholesale override) — the squared-distance form (never the absMax
     * pair), the 0.1-then-0.5 scale, the cart pushed away FULL
     * {@code 0.2/0.2 * 1.0 * 0.1 * 0.5 = 0.05} and the pushing cow
     * pushed away at QUARTER {@code 0.0125}. RED on the pre-fix engine
     * (the gather never carried the vehicle arm — the cart's velocity
     * stayed exactly zero however crowded).
     */
    @Test
    void theCartsQuarterPushLaw() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 83L);
        MobEntity cow = pinned(manager, MobType.COW, new Position(0.5, 8.0, 0.5));
        MinecartEntity cart = new MinecartEntity(997_000,
                new Position(0.7, 8.0, 0.5), new Random(7L));
        List<VehicleEntity> vehicles = List.of(cart);
        manager.setVehicleSource(() -> vehicles);
        List<PlayerSession> none = List.of();

        manager.tick(none, 6000L);

        // MinecartEntity.push lines 668-684: d0 = 0.5 - 0.7 = -0.2;
        // d2 = 0.04 >= 1.0E-4; sqrt -> 0.2; d0 normalized -> -1.0;
        // d3 = 1/0.2 = 5.0 capped at 1.0; the 0.1 scale then the 0.5
        // halving -> -0.05.
        assertEquals(0.05, cart.velocityX(), 1e-9,
                "the cart pushed away FULL (the wholesale override's own"
                        + " scale)");
        assertEquals(-0.0125, cow.velocityX(), 1e-9,
                "the pushing cow pushed away at QUARTER (line 725's"
                        + " d0/4.0)");
        assertEquals(0.0, cart.velocityZ(), 1e-9,
                "the push is horizontal alone");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the boat's mutual law and the passenger gate
    // ------------------------------------------------------------------

    /**
     * A pinned cow and a boat 0.2 apart: Entity.push's ordinary mutual
     * law (the boat overrides nothing) — the absMax form both sides,
     * {@code sqrt(0.2) * 0.05} each. The boat's own rider gate (line
     * 995): a boat with a PASSENGER never moves out from under it — the
     * cow still carries its own arm.
     */
    @Test
    void theBoatsMutualLawAndThePassengerGate() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 84L);
        MobEntity cow = pinned(manager, MobType.COW, new Position(0.5, 8.0, 0.5));
        BoatEntity boat = new BoatEntity(998_000,
                new Position(0.7, 8.0, 0.5), new Random(7L));
        List<VehicleEntity> vehicles = List.of(boat);
        manager.setVehicleSource(() -> vehicles);
        List<PlayerSession> none = List.of();

        manager.tick(none, 6000L);

        double half = Math.sqrt(0.2) * 0.05;
        assertEquals(half, boat.velocityX(), 1e-9,
                "the boat pushed away at the ordinary mutual scale");
        assertEquals(-half, cow.velocityX(), 1e-9,
                "the cow pushed away at the same scale (Entity.push's"
                        + " mutual law)");

        // The passenger gate: an occupied boat never moves.
        BoatEntity ridden = new BoatEntity(998_001,
                new Position(0.7, 8.0, 2.5), new Random(7L));
        assertTrue(ridden.mount(777_777), "the seat takes the passenger");
        MobEntity crowder = pinned(manager, MobType.COW,
                new Position(0.5, 8.0, 2.5));
        List<VehicleEntity> withRider = List.of(ridden);
        manager.setVehicleSource(() -> withRider);
        manager.tick(none, 6000L);
        assertEquals(0.0, ridden.velocityX(), 1e-9,
                "an occupied boat never moves out from under its"
                        + " passenger (line 995)");
        assertEquals(-half, crowder.velocityX(), 1e-9,
                "the pushing cow still carries its own arm (line 998)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the rider never pushes its own mount
    // ------------------------------------------------------------------

    /**
     * The riding-pair refusals: the player riding the pig never pushes
     * it (Entity.push line 970, the player's own walk), and the pig's
     * own walk never pushes its rider (the same law's mirrored arm —
     * pushPlayerBody's refusal). The pair sits still however crowded.
     */
    @Test
    void theRiderNeverPushesItsOwnMount() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 85L);
        MobEntity pig = pinned(manager, MobType.PIG, new Position(0.5, 8.0, 0.5));
        PlayerSession rider = player("Rider",
                new Position(0.5, 8.0, 0.5), GameMode.SURVIVAL);
        List<PlayerSession> players = List.of(rider);
        assertTrue(manager.mountMob(pig, rider.engineEntityId(),
                        "Rider", "00000000-0000-0000-0000-000000000000"),
                "the pig takes the seat");

        manager.tick(players, 6000L);
        manager.tickPlayerPushCollisions(rider, players);

        assertEquals(0.0, pig.velocityX(), 1e-9,
                "the ridden pig never receives its rider's push");
        assertEquals(0.0, rider.motionX(), 1e-9,
                "the rider's motion store never receives the mount's"
                        + " push (the mirrored refusal)");
    }

    // ------------------------------------------------------------------
    // Seat 6 — the walking player's cart quarter and boat refusals
    // ------------------------------------------------------------------

    /**
     * The player's own walk over the vehicles: the CART carries its own
     * law (the player excluded from the auto-mount consult outright —
     * line 661's instanceof gate; the cart pushed away full, the player
     * pushed away at quarter through the motion store), and the rider
     * of either vehicle never pushes the one they ride (the cart's
     * line-656 gate, the boat's line-970 vehicle arm).
     */
    @Test
    void thePlayersCartQuarterAndTheRiderRefusals() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 86L);
        PlayerSession walker = player("Pusher",
                new Position(0.5, 8.0, 0.5), GameMode.SURVIVAL);
        List<PlayerSession> players = List.of(walker);
        MinecartEntity cart = new MinecartEntity(997_001,
                new Position(0.7, 8.0, 0.5), new Random(7L));
        BoatEntity boat = new BoatEntity(998_002,
                new Position(0.5, 8.0, 3.5), new Random(7L));
        List<VehicleEntity> vehicles = List.of(cart, boat);
        manager.setVehicleSource(() -> vehicles);

        manager.tickPlayerPushCollisions(walker, players);

        // The cart's law on the player's walk: the same -0.05/quarter
        // pair as the mob's walk (the pusher's family never changes the
        // cart's own form).
        assertEquals(0.05, cart.velocityX(), 1e-9,
                "the cart pushed away full by the walking player");
        assertEquals(-0.0125, walker.motionX(), 1e-9,
                "the walking player pushed away at quarter through the"
                        + " motion store (the leftward away direction)");
        // The boat's ordinary mutual law.
        assertEquals(0.0, boat.velocityX(), 1e-9,
                "the distant boat never joins this walker's gather");

        // The rider refusals: the seated walker never pushes the vehicle
        // they ride — the cart through its own gate, the boat through
        // Entity.push's vehicle arm.
        MinecartEntity riddenCart = new MinecartEntity(997_002,
                new Position(0.5, 8.0, 6.5), new Random(7L));
        assertTrue(riddenCart.mount(walker.engineEntityId()),
                "the cart takes the walker");
        BoatEntity riddenBoat = new BoatEntity(998_003,
                new Position(0.5, 8.0, 9.5), new Random(7L));
        assertTrue(riddenBoat.mount(walker.engineEntityId()),
                "the boat takes the walker");
        List<VehicleEntity> ridden = List.of(riddenCart, riddenBoat);
        manager.setVehicleSource(() -> ridden);
        walker.applyMovement(new Position(0.5, 8.0, 6.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        manager.tickPlayerPushCollisions(walker, players);
        assertEquals(0.0, riddenCart.velocityX(), 1e-9,
                "the seated walker never pushes the cart they ride (the"
                        + " line-656 gate)");
        assertEquals(0.0, riddenBoat.velocityX(), 1e-9,
                "the seated walker never pushes the boat they ride (the"
                        + " vehicle arm of line 970 — the distant boat's"
                        + " own gather never reached this walker either)");
    }
}
