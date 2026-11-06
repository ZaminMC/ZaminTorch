package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE TAMEABLE CHASE FAMILY (SitGoal 1:1 — canStart lines 16-31 over the
 * registration at WolfEntity line 58 / OcelotEntity line 44, band 2,
 * {@code setControls(5)}): the sit goal holds MOVE and JUMP, so a seated
 * pet NEVER WALKS — not its standing mob target (the pre-fix engine
 * chased whole), not the stroll (the pre-fix engine strolled the seated
 * pet away), and the provoked wolf's forced player hunt now CHASES (the
 * pre-fix engine landed PE-34's write with no chase body at all — the
 * hit wild wolf stood still and its anger posture cleared on the very
 * next tick, the anger consult blind to the forced hunt). The owner's
 * own wound inside the 12-block band RELEASES the claim (line 30's
 * {@code livingentity.getAttacker() != null} — the classic
 * stand-and-defend window), and the empty-hand verb stands a fighting
 * pet down whole (interactMob lines 327-332's target clear + path drop).
 */
class TameableSitChaseParityDiscoveryTest {

    /** The seated-pet world: a flat plane with a scriptable owner view. */
    private static class PetWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();
        /** The owner's live view (null when the owner left the world). */
        MobEntity.PlayerView ownerView;
        String ownerName = "";
        String ownerUuid = "";

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public MobEntity.PlayerView ownerPlayerView(String name, String uuid) {
            return name.equals(ownerName) ? ownerView : null;
        }
    }

    /** The owner's scriptable view (the sit release's read). */
    private static final class OwnerView implements MobEntity.PlayerView {
        final Position at;
        boolean hurtByMob;

        OwnerView(Position at) {
            this.at = at;
        }

        @Override public Position position() {
            return at;
        }

        @Override public float yaw() {
            return 0;
        }

        @Override public float pitch() {
            return 0;
        }

        @Override public String heldItem() {
            return null;
        }

        @Override public boolean hurtByMobRecently() {
            return hurtByMob;
        }
    }

    private static MobEntity spawn(PetWorld world, int id, MobType type,
            Position at, long seed) {
        MobEntity mob = new MobEntity(id, type, at, new Random(seed), world);
        world.mobs.add(mob);
        return mob;
    }

    // ------------------------------------------------------------------
    // Seat 1 — the sit-park on the standing mob target
    // ------------------------------------------------------------------

    /**
     * A seated tamed wolf with a standing mob target never moves: the
     * write stands (the targetSelector carries no posture gate), the body
     * parks under the MOVE claim. RED on the pre-fix engine (the seated
     * wolf CHASED its target — the classic vanilla law broken).
     */
    @Test
    void theSeatedWolfNeverChasesItsStandingMobTarget() {
        PetWorld world = new PetWorld();
        MobEntity pet = spawn(world, 930, MobType.WOLF,
                new Position(0.5, 8.0, 0.5), 80L);
        MobEntity skeleton = spawn(world, 931, MobType.SKELETON,
                new Position(8.5, 8.0, 0.5), 81L);
        pet.tameWolf("Owner", "owner-1");
        // Wave TS2 — the taming arms the VERB; the POSTURE follows the
        // goal's claim one pass behind (the reference's own shape: the
        // verb at the tame, the seated bit at the selector's next pass).
        pet.setMobAttackTarget(skeleton);

        pet.tick(false); // the spawn settle: the fresh body grounds itself
        pet.tick(false); // the claim pass seats the pet (Wave TS2: the posture follows the verb)
        assertTrue(pet.wolfSitting(), "the taming seats the pet");
        double settledX = pet.position().x();
        for (int i = 0; i < 60; i++) {
            pet.tick(false);
        }
        assertEquals(settledX, pet.position().x(), 1e-9,
                "the seated pet never walks its standing target (SitGoal"
                        + " holds MOVE — the pre-fix engine chased whole)");
        assertEquals(8.0, pet.position().y(), 1e-9);
        assertEquals(skeleton, pet.mobAttackTarget(),
                "the write stands (the targetSelector carries no posture gate)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the provoked wolf's chase + the standing anger
    // ------------------------------------------------------------------

    /**
     * A hit wild wolf turns on its player attacker and CHASES: the forced
     * hunt drives the same melee walk the hostile kinds' player hunt
     * runs, and the anger posture STANDS while the hunt stands
     * (WolfEntity.mobTick lines 178-180's own clear consult reading the
     * live target). RED on the pre-fix engine (no chase body at all —
     * the wolf stood still and the anger cleared on the very next tick).
     */
    @Test
    void theProvokedWolfChasesItsAttackerAndTheAngerStands() {
        PetWorld world = new PetWorld();
        final Position[] attackerAt = { new Position(12.5, 8.0, 0.5) };
        class HuntWorld extends PetWorld {
            @Override
            public Position entityPositionById(int entityId) {
                return attackerAt[0];
            }
        }
        HuntWorld huntWorld = new HuntWorld();
        MobEntity wolf = spawn(huntWorld, 932, MobType.WOLF,
                new Position(0.5, 8.0, 0.5), 82L);

        wolf.setForcedHuntPlayer(9_200);
        wolf.setWolfAngry(true); // setAttackTarget's posture (lines 88-94)
        assertTrue(wolf.wolfAngry());

        double startX = wolf.position().x();
        for (int i = 0; i < 60; i++) {
            wolf.tick(false);
        }
        assertTrue(wolf.position().x() > startX + 2.0,
                "the provoked wolf closes on its attacker (the pre-fix"
                        + " engine landed the write with NO chase body)");
        assertTrue(wolf.wolfAngry(),
                "the anger stands while the forced hunt stands (the"
                        + " pre-fix engine cleared it on the very next tick"
                        + " — the anger consult never saw the forced hunt)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the stroll park
    // ------------------------------------------------------------------

    /**
     * A seated tamed wolf with no target never strolls away — the MOVE
     * claim holds the stroll (band 7 under the sit's band 2). RED on the
     * pre-fix engine (the seated pet strolled off — the seated pet's own
     * classic law broken).
     */
    @Test
    void theSeatedWolfNeverStrollsAway() {
        PetWorld world = new PetWorld();
        MobEntity pet = spawn(world, 933, MobType.WOLF,
                new Position(0.5, 8.0, 0.5), 83L);
        pet.tameWolf("Owner", "owner-1");
        pet.tick(false); // the spawn settle
        pet.tick(false); // the claim pass seats the pet (Wave TS2: the posture follows the verb)
        assertTrue(pet.wolfSitting());

        for (int i = 0; i < 400; i++) {
            pet.tick(false);
        }
        assertEquals(0.5, pet.position().x(), 1e-9,
                "the seated pet stays where it was seated (the pre-fix"
                        + " engine strolled it away)");
        assertEquals(8.0, pet.position().y(), 1e-9);
    }

    // ------------------------------------------------------------------
    // Seat 4 — the sit release (the owner's wound inside the band)
    // ------------------------------------------------------------------

    /**
     * The owner wounded inside the 12-block band RELEASES the claim
     * (SitGoal.canStart line 30's {@code livingentity.getAttacker() !=
     * null}): the seated pet stands into the chase on its standing
     * target; the window's end re-parks it. RED on the pre-fix engine
     * (no release law at all — the seated pet never defended).
     */
    @Test
    void theOwnersWoundInsideTheBandStandsTheSeatedPetIntoTheFight() {
        PetWorld world = new PetWorld();
        OwnerView owner = new OwnerView(new Position(3.5, 8.0, 0.5));
        world.ownerView = owner;
        world.ownerName = "Owner";
        MobEntity pet = spawn(world, 934, MobType.WOLF,
                new Position(0.5, 8.0, 0.5), 84L);
        MobEntity zombie = spawn(world, 935, MobType.ZOMBIE,
                new Position(10.5, 8.0, 0.5), 85L);
        pet.tameWolf("Owner", "owner-1");
        pet.setMobAttackTarget(zombie); // the join write (no posture gate)

        // The owner unwounded: the claim holds, the pet parks.
        assertFalse(owner.hurtByMobRecently());
        pet.tick(false); // the spawn settle: the fresh body grounds itself
        for (int i = 0; i < 40; i++) {
            pet.tick(false);
        }
        double parkedX = pet.position().x();
        assertTrue(parkedX < 1.0,
                "the unwounded owner's seated pet parks (the claim holds)");

        // The owner wounded inside the 12-block band: the claim releases.
        owner.hurtByMob = true;
        for (int i = 0; i < 60; i++) {
            pet.tick(false);
        }
        assertTrue(pet.position().x() > parkedX + 1.0,
                "the owner's wound inside the band stands the seated pet"
                        + " into the chase (the classic stand-and-defend"
                        + " window the pre-fix engine never carried)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the empty-hand verb's stand-down
    // ------------------------------------------------------------------

    /**
     * The owner's empty-hand verb stands a FIGHTING pet down whole
     * (interactMob lines 327-332): the path drops and the attack target
     * clears — both engine seats of the reference's single field. RED on
     * the pre-fix engine (the verb toggled the posture alone; the target
     * stood and the pet fought on seated).
     */
    @Test
    void theEmptyHandVerbStandsTheFightingPetDownWhole() {
        PetWorld world = new PetWorld();
        MobEntity pet = spawn(world, 936, MobType.WOLF,
                new Position(0.5, 8.0, 0.5), 86L);
        MobEntity zombie = spawn(world, 937, MobType.ZOMBIE,
                new Position(10.5, 8.0, 0.5), 87L);
        pet.tameWolf("Owner", "owner-1");
        pet.setMobAttackTarget(zombie);
        pet.setForcedHuntPlayer(9_201);

        pet.standDownByVerb(); // the verb's own body (the interact walk)
        assertNull(pet.mobAttackTarget(),
                "the verb clears the mob target (setAttackTarget(null))");
        assertTrue(pet.forcedHuntPlayerId() < 0,
                "the verb clears the forced player hunt too (the single"
                        + " field's other engine seat)");
        assertFalse(pet.wolfAngry(),
                "the null write stands the anger down (WolfEntity lines"
                        + " 88-94)");
    }
}
