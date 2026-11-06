package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE WOLF'S PLAYER REVENGE + THE TAMEABLE OWNER GATES (Wave PE-34 —
 * RevengeGoal(this, true), WolfEntity line 69, over TrackTargetGoal
 * .isTarget lines 108-115): the PROVOKED PACK — a hit wild wolf turns
 * on its player attacker and the same-kind band joins (the victim's own
 * 1x1 box grown (followRange, 10, followRange) — ±8/±5 for the wolf's
 * 16, never the pre-fix flat 32 sphere); the OWNER refusal (a tamed
 * wolf never avenges on its own owner, the stranger still hunted); the
 * SAME-OWNER refusal (the owner's stray hit between their own pets
 * never starts a fight — the revenge walk AND the owner-join arms both
 * carry it). The pre-fix engine carried no player revenge for wolves
 * at all (a hit wild wolf just stood there) and none of the owner
 * gates.
 */
class WolfRevengeOwnerGatesParityDiscoveryTest {

    /** The flat stub world carrying the pack's own mob list. */
    private static final class PackWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();
        /** The stub player's engine id + position (the forced hunt's read). */
        int playerEntityId = -1;
        Position playerAt;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public Position entityPositionById(int entityId) {
            if (entityId == playerEntityId) {
                return playerAt;
            }
            for (MobEntity mob : mobs) {
                if (mob.entityId() == entityId) {
                    return mob.position();
                }
            }
            return null;
        }
    }

    private static MobEntity spawn(PackWorld world, MobManager manager,
            MobType type, double x, double z) {
        MobEntity mob = manager.spawnAt(type, new Position(x, 8.0, z));
        world.mobs.add(mob);
        return mob;
    }

    // ------------------------------------------------------------------
    // Seat 1 — the provoked pack + the call-others band
    // ------------------------------------------------------------------

    /**
     * A player hits a wild wolf: the victim turns on the player (the
     * forced hunt, the angry posture — setAttackTarget lines 88-94), the
     * kin INSIDE the grown box (6 east, the ±8 band) joins, the kin
     * OUTSIDE it (20 east — inside the pre-fix flat 32 sphere, outside
     * vanilla's box) never does. RED on the pre-fix engine (no player
     * revenge at all).
     */
    @Test
    void theProvokedPackTurnsOnThePlayerInsideTheBand() {
        PackWorld world = new PackWorld();
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        MobManager manager = new MobManager(world, new Random(60L), new NoLoot(),
                990_000, (x, z) -> 8);
        MobEntity victim = spawn(world, manager, MobType.WOLF, 0.5, 0.5);
        MobEntity nearKin = spawn(world, manager, MobType.WOLF, 6.5, 0.5);
        MobEntity farKin = spawn(world, manager, MobType.WOLF, 20.5, 0.5);
        PlayerSession attacker = stubPlayer("Striker", new Position(1.5, 8.0, 0.5), 9_100);
        world.playerEntityId = attacker.engineEntityId();
        world.playerAt = attacker.position();

        assertTrue(manager.hurt(victim, 2.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false,
                null, attacker));
        assertNotNull(victim.forcedHuntPosition(),
                "RevengeGoal(this, true): the hit wild wolf turns on its"
                        + " player attacker (the pre-fix engine carried no"
                        + " player revenge for wolves)");
        assertTrue(victim.wolfAngry(), "setAttackTarget's angry posture");
        assertNotNull(nearKin.forcedHuntPosition(),
                "the kin inside the grown box joins the pack revenge");
        assertTrue(nearKin.wolfAngry(), "the wild kin's angry posture");
        assertNull(farKin.forcedHuntPosition(),
                "the kin 20 east is OUTSIDE the ±8 grown box (the pre-fix"
                        + " flat 32 sphere wrongly reached it)");
        assertFalse(farKin.wolfAngry(), "the outside kin never angers");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the owner refusal
    // ------------------------------------------------------------------

    /**
     * The tamed wolf never avenges on its OWN OWNER (isTarget lines
     * 113-115) — the stranger's hit still lands the hunt.
     */
    @Test
    void theTamedWolfNeverAvenegesOnItsOwnOwner() {
        PackWorld world = new PackWorld();
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        MobManager manager = new MobManager(world, new Random(61L), new NoLoot(),
                991_000, (x, z) -> 8);
        MobEntity pet = spawn(world, manager, MobType.WOLF, 0.5, 0.5);
        pet.tameWolf("Owner", "");
        PlayerSession owner = stubPlayer("Owner", new Position(1.5, 8.0, 0.5), 9_101);
        PlayerSession stranger = stubPlayer("Stranger", new Position(2.5, 8.0, 0.5), 9_102);
        world.playerEntityId = stranger.engineEntityId();
        world.playerAt = stranger.position();

        assertTrue(manager.hurt(pet, 2.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false,
                null, owner));
        assertNull(pet.forcedHuntPosition(),
                "isTarget lines 113-115: the tamed wolf never avenges on"
                        + " its own owner");
        assertFalse(pet.wolfAngry(), "no anger on the owner's hit");

        // The hurt frame (Wave HF — the reference's shared i-frame): the
        // owner's 2.0 stands in the window, so the stranger's probe
        // out-damages it (the excess arm a stronger hit pays in vanilla —
        // the same-damage hit would i-frame away and never avenge).
        assertTrue(manager.hurt(pet, 4.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false,
                null, stranger));
        assertNotNull(pet.forcedHuntPosition(),
                "the stranger's hit lands the revenge (the tame gate is"
                        + " the owner refusal alone)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the same-owner pets (the revenge walk)
    // ------------------------------------------------------------------

    /**
     * The owner's stray hit between their own pets never starts a fight
     * (isTarget lines 108-110 on the revenge walk): wolf A hit by the
     * owner's wolf B never avenges. RED on the pre-fix engine (the
     * revenge landed — the pets fought).
     */
    @Test
    void theSameOwnerPetsNeverAvengeOnEachOther() {
        PackWorld world = new PackWorld();
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        MobManager manager = new MobManager(world, new Random(62L), new NoLoot(),
                992_000, (x, z) -> 8);
        MobEntity petA = spawn(world, manager, MobType.WOLF, 0.5, 0.5);
        MobEntity petB = spawn(world, manager, MobType.WOLF, 2.5, 0.5);
        petA.tameWolf("Owner", "");
        petB.tameWolf("Owner", "");

        assertTrue(manager.hurtByMob(petB, petA, 2.0f));
        assertNull(petA.mobAttackTarget(),
                "isTarget lines 108-110: the same-owner tameable refusal"
                        + " — the owner's pets never fight (the pre-fix"
                        + " engine avenged whole)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the same-owner pets (the owner-join arms)
    // ------------------------------------------------------------------

    /**
     * The owner attacking their OWN pet never drags the pack in
     * (AttackWithOwnerGoal/OwnerHurtGoal route through canTarget — the
     * same-owner refusal on the join target). RED on the pre-fix engine
     * (the join landed).
     */
    @Test
    void theOwnerSOwnPetHitNeverDragsThePackIn() {
        PackWorld world = new PackWorld();
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        MobManager manager = new MobManager(world, new Random(63L), new NoLoot(),
                993_000, (x, z) -> 8);
        MobEntity petA = spawn(world, manager, MobType.WOLF, 0.5, 0.5);
        MobEntity petB = spawn(world, manager, MobType.WOLF, 2.5, 0.5);
        petA.tameWolf("Owner", "");
        petB.tameWolf("Owner", "");
        // The taming seats the pets (SitGoal's own start); the join loop's
        // sitting gate would vacate the seat — the owner's verb stands them.
        petA.setWolfSitting(false);

        // AttackWithOwnerGoal's site: the owner's target is their own petB.
        manager.ownerWolvesJoinFight("Owner", "", petB);
        assertNull(petA.mobAttackTarget(),
                "the owner's own pet as the join target never arms the"
                        + " pack (the same-owner refusal)");
    }

    // ------------------------------------------------------------------
    // The player stub (the CombatTargetTailsTest harness's shape)
    // ------------------------------------------------------------------

    private PlayerSession stubPlayer(String name, Position at, int engineId) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                java.util.UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(new World() {
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
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() {
                return java.util.List.of();
            }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(
                    net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        }, Position.ZERO);
        session.markPlaying();
        session.applyMovement(at, Rotation.ZERO, true);
        session.assignEngineEntityId(engineId);
        return session;
    }
}
