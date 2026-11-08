package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.leash.LeadSystem;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave LN — the leash's leeched-navigation family (PathFinderMobEntity
 * .updateLeashStatus lines 72-121, read whole from the reference): the
 * arms the engine's leash walk never carried, each verified against the
 * reference's own order.
 *
 * <ul>
 * <li>THE HOLDER-CENTERED VILLAGE PIN (line 76): every leashed tick with
 * a live holder re-centers the village cache ON THE HOLDER at radius
 * five — written BEFORE the distance gates, so it stands even inside the
 * follow slack (the engine's retired "inside the slack" early return
 * skipped it whole).</li>
 * <li>THE SITTING TAMEABLE'S EARLY RETURN (lines 78-84): a seated pet
 * takes no arming, no follow, no pull — the snap alone; the wolf family
 * consults the shared tameable sitting flag.</li>
 * <li>THE LEECHED-NAVIGATION ONE-SHOT (lines 86-93): the first leashed
 * tick parks the swim paths ({@code setCanSwim(false)} — the dragged
 * body paths AROUND the water).</li>
 * <li>THE DETACH'S RECOVERY (lines 112-120): the swim paths return with
 * the reference's own UNCONDITIONAL {@code setCanSwim(true)} — never the
 * parked value: a leashed-then-released CHICKEN (the one kind whose
 * construction never armed swim) ends with its navigation canSwim TRUE,
 * the reference's own quirk — and the village pin resets.</li>
 * <li>THE BAND-2 WANDER (line 19's goToWalkTargetGoal registration + line
 * 87): while the arm stands, the wander's claim (beyond the pin's five,
 * or its navigation-busy shouldContinue) blocks the plain stroll's own
 * start — band 2 precedes the stroll's 7 — and its pick rides the
 * TargetFinder walk's toward-pin half-plane with the in-band gate (the
 * candidates within the pin's five).</li>
 * </ul>
 */
class LeashLeechedNavigationTest {

    /** The stub query: a solid floor below y=4, no players. */
    private static final MobEntity.WorldQuery QUERY = new MobEntity.WorldQuery() {
        @Override public boolean isSolid(double x, double y, double z) {
            return y < 4.0;
        }

        @Override public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    };

    /** The stub world (the LeadSystem's own — no fences, the poll survives). */
    private static final World STUB_WORLD = new World() {
        @Override public String name() { return "stub"; }
        @Override public long timeOfDay() { return 0; }
        @Override public long totalTicks() { return 0; }
        @Override public Position spawnPosition() { return Position.ZERO; }
        @Override public net.zaminmc.torch.block.BlockType getBlock(
                net.zaminmc.torch.block.BlockPosition p) {
            return null;
        }
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
        @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
        @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
        @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
    };

    /** The rig: one mob + the lead system + the holder. */
    private record Rig(MobEntity mob, LeadSystem leads, PlayerSession holder) {
        void tickLeash() {
            leads().tick(List.of(mob()), p -> p.position(),
                    uuid -> java.util.Optional.empty());
        }
    }

    private Rig rig(MobType type, double mobX, double mobZ,
                    double holderX, double holderZ) {
        MobEntity mob = new MobEntity(9, type, new Position(mobX, 4.0, mobZ),
                new java.util.Random(42), QUERY);
        mob.setOnGround(true);
        LeadSystem leads = new LeadSystem(new LeadSystem.Mobs() {
            @Override public List<MobEntity> mobsIn(double minX, double minY, double minZ,
                                                    double maxX, double maxY, double maxZ) {
                return List.of();
            }

            @Override public void dropLead(Position at, int dimension) { }
        }, () -> 5_000_000, STUB_WORLD, "fence", 0);
        PlayerSession holder = player(holderX, holderZ);
        holder.inventory().setSlot(0, ItemStack.of(BuiltinItems.LEAD, 1));
        holder.inventory().selectHotbarSlot(0);
        assertTrue(leads.interactMobWithLead(holder, mob),
                "the lead click consumed the interact");
        return new Rig(mob, leads, holder);
    }

    private PlayerSession player(double x, double z) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes("LeechHolder".getBytes()), "LeechHolder", link);
        session.authenticate();
        session.beginJoin(STUB_WORLD, Position.ZERO);
        session.markPlaying();
        session.applyMovement(new Position(x, 4.0, z), Rotation.ZERO, true);
        session.setGamemode(GameMode.SURVIVAL);
        return session;
    }

    // ------------------------------------------------ the arming + the pin + the swim park

    @Test
    void theFirstLeashedTickArmsThePinAndParksTheSwimInsideTheSlack() {
        // The holder three blocks away — INSIDE the follow slack: the
        // reference writes the pin and arms the leeched navigation BEFORE
        // any distance gate (the engine's retired early return skipped
        // both whole).
        Rig rig = rig(MobType.COW, 13.5, 10.5, 10.5, 10.5);
        assertTrue(rig.mob().leechedNavigationArmed() == false,
                "the arm stands only after a live-holder tick");
        rig.tickLeash();
        assertTrue(rig.mob().leechedNavigationArmed(),
                "the first leashed tick armed the leeched navigation");
        // The pin (line 76): the holder's truncated cell at radius five.
        int[] pin = rig.mob().leashVillagePinForTest();
        assertEquals(10, pin[0], "the pin centers on the holder's truncated x");
        assertEquals(4, pin[1], "the pin centers on the holder's truncated y");
        assertEquals(10, pin[2], "the pin centers on the holder's truncated z");
        assertEquals(5, pin[3], "the pin's radius is the reference's own five");
        // The swim park (lines 88-90): the cow's construction-armed swim
        // stands down while leeched — the dragged body paths around water.
        assertFalse(rig.mob().navigationCanSwim(),
                "the leeched navigation parks the swim paths");
        // The slack itself: no movement, no snap.
        assertEquals(13.5, rig.mob().position().x(), 1e-9,
                "inside the slack the body does not move");
        assertTrue(rig.mob().isLeashed(), "the slack holds the leash");
        // The second tick does not re-arm (the one-shot's own flag).
        rig.tickLeash();
        assertTrue(rig.mob().leechedNavigationArmed());
    }

    // ------------------------------------------------ the sitting tameable's early return

    @Test
    void theSittingTameableTakesNoFollowNoPullAndSnapsAlone() {
        // A seated wolf at seven blocks: past the pull's six — but the
        // reference's early return (lines 78-84) precedes every arm.
        Rig rig = rig(MobType.WOLF, 17.5, 10.5, 10.5, 10.5);
        rig.mob().setWolfSitting(true);
        double vx = rig.mob().velocityX();
        double vz = rig.mob().velocityZ();
        rig.tickLeash();
        assertEquals(vx, rig.mob().velocityX(), 1e-9,
                "the seated wolf takes no pull at seven");
        assertEquals(vz, rig.mob().velocityZ(), 1e-9,
                "the seated wolf takes no pull at seven");
        assertFalse(rig.mob().leechedNavigationArmed(),
                "the early return precedes the arming");
        assertTrue(rig.mob().isLeashed(), "seven blocks holds the seated leash");
        // The seated snap (line 80): past ten, the leash breaks alone.
        rig.holder().applyMovement(new Position(28.5, 4.0, 10.5), Rotation.ZERO, true);
        rig.tickLeash();
        assertFalse(rig.mob().isLeashed(), "the seated leash snapped at eleven");
    }

    // ------------------------------------------------ the detach's recovery (the quirk)

    @Test
    void theDetachReturnsTheSwimUnconditionallyAndResetsThePin() {
        // The chicken — the one kind whose construction never armed swim:
        // the reference's detach (line 116) writes setCanSwim(TRUE)
        // unconditionally, never the parked value, so the
        // leashed-then-released chicken ends with its navigation canSwim
        // ON — the reference's own quirk.
        Rig rig = rig(MobType.CHICKEN, 11.5, 10.5, 10.5, 10.5);
        assertFalse(rig.mob().navigationCanSwim(),
                "the chicken's construction left the swim off");
        rig.tickLeash();
        assertTrue(rig.mob().leechedNavigationArmed());
        assertTrue(rig.mob().villageCacheSet(), "the pin stood while leeched");
        // The release (the re-click's detach path).
        rig.leads().interactMobWithLead(rig.holder(), rig.mob());
        assertFalse(rig.mob().isLeashed(), "the re-click released the chicken");
        assertTrue(rig.mob().navigationCanSwim(),
                "the release returned the swim UNCONDITIONALLY — the reference's own quirk");
        assertFalse(rig.mob().villageCacheSet(), "the release reset the village pin");
        assertFalse(rig.mob().leechedNavigationArmed(),
                "the release retired the leeched-navigation arm");
    }

    // ------------------------------------------------ the band-2 wander

    @Test
    void theWanderClaimsTheStrollLaneBeyondThePinAndPicksTowardIt() {
        // The holder seven blocks east: the cow's cell stands BEYOND the
        // pin's five (WanderThroughVillageGoal.canStart's !isInVillage()
        // gate), so the band-2 wander claims the stroll lane and its pick
        // rides the toward-pin half-plane with the in-band gate.
        Rig rig = rig(MobType.COW, 13.5, 10.5, 20.5, 10.5);
        rig.tickLeash(); // the arm + the pin (the holder's cell, radius 5)
        assertTrue(rig.mob().leashWanderClaimHolds(),
                "beyond the pin the wander's claim holds — the stroll lane is not free");
        // The mind's own walk: the wander starts while the drag window
        // stands open — the pull arm closes a standing gap in a few
        // ticks (the mob drifts inside the pin's five and the claim
        // retires until the holder walks on), so the holder KEEPS
        // WALKING east through the rounds: the vanilla drag itself, the
        // mob trailing at the lead's end where the wander claims. The
        // pick's ten candidates ride the tight pin's five (the in-band
        // gate eats most draws; no interval dice — the goal carries
        // none, every setup pass consults fresh).
        boolean walking = false;
        double holderX = 20.5;
        for (int i = 0; i < 60 && !walking; i++) {
            if (holderX - rig.mob().position().x() < 7.0) {
                holderX += 2.0; // the holder walks on — the drag window stays
                                // open, the gap never reaching the snap's ten
                rig.holder().applyMovement(new Position(holderX, 4.0, 10.5),
                        Rotation.ZERO, true);
            }
            rig.tickLeash(); // the pin re-centers on the walking holder
            rig.mob().tick(false); // the mind consults
            walking = rig.mob().leashWanderWalking();
        }
        assertTrue(walking, "the wander armed within the first mind consults"
                + " [claim=" + rig.mob().leashWanderClaimHolds()
                + " pin=" + java.util.Arrays.toString(rig.mob().leashVillagePinForTest())
                + " at=" + rig.mob().position()
                + " pick=" + rig.mob().leashWanderTargetForTest() + "]");
        Position pick = rig.mob().leashWanderTargetForTest();
        assertNotNull(pick, "the wander's pick stood");
        // The in-band gate (the body inside the pin + 16 band): the
        // candidate must fall INSIDE the pin's five.
        int[] pin = rig.mob().leashVillagePinForTest();
        double pickDx = pick.x() - pin[0];
        double pickDz = pick.z() - pin[2];
        assertTrue(pickDx * pickDx + pickDz * pickDz < 25.0,
                "the pick fell inside the pin's five (the in-band gate)");
        // The toward-pin half-plane (getTargetAwayFromPosition's own
        // filter: startOffset = pin − mob, the passing candidates dot
        // non-negative against it).
        double offsetToPinX = pin[0] - rig.mob().position().x();
        double offsetToPinZ = pin[2] - rig.mob().position().z();
        double pickOffsetX = pick.x() - rig.mob().position().x();
        double pickOffsetZ = pick.z() - rig.mob().position().z();
        assertTrue(pickOffsetX * offsetToPinX + pickOffsetZ * offsetToPinZ >= 0.0,
                "the pick sits in the toward-pin half-plane");
        // The release retires the claim whole.
        rig.leads().interactMobWithLead(rig.holder(), rig.mob());
        assertFalse(rig.mob().leashWanderClaimHolds(),
                "the release retired the wander's claim");
    }
}
