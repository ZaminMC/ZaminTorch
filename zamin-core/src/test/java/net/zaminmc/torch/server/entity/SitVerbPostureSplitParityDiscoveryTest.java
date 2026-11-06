package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave TS2 — THE SIT POSTURE/VERB SPLIT (SitGoal.enabledWithOwner vs the
 * synced-data sitting bit — the TS wave's honest tail closed): the VERB is
 * the owner's command, the POSTURE the goals' write, and the laws the
 * single-bit shape could not carry: THE WOUNDED PET STANDS (both
 * tameables' takeDamage overrides, WolfEntity line 265 / OcelotEntity
 * line 154 — the verb clears BEFORE the i-frame consult, so even a
 * frame-refused hit stands a sitting pet's command down, the pet rising
 * at the goal's next pass and never re-sitting on its own), THE
 * CHEST-QUIRK (interactMob line 328's
 * {@code setEnabledWithOwner(!isSitting())} — the verb flips to the
 * POSTURE'S OWN OPPOSITE, so a perched cat — posture up, verb already
 * down — computes !true = false and NOTHING changes: the owner can never
 * right-click a perched cat off its perch), and THE ONE-PASS FOLLOW (the
 * posture lands at the goal's claim/stop pass, one tick behind the
 * verb — start()'s setSitting(true) + getNavigation().stop(), stop()'s
 * setSitting(false)).
 */
class SitVerbPostureSplitParityDiscoveryTest {

    /**
     * The flat world with a standing, unhurt owner view beside the pet and
     * an optional chest perch at (2, 7, 0) — the block-sit's own target.
     */
    private static final class SplitWorld implements MobEntity.WorldQuery {
        boolean chestPerch;
        MobEntity.PlayerView ownerView = new MobEntity.PlayerView() {
            @Override public Position position() {
                return new Position(2.5, 8.0, 0.5);
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
        };

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
            return ownerView;
        }

        @Override
        public net.zaminmc.torch.util.Identifier blockIdAt(int x, int y, int z) {
            if (chestPerch && x == 2 && y == 7 && z == 0) {
                return net.zaminmc.torch.util.Identifier.parse("minecraft:chest");
            }
            if (Math.floor(y) < 8.0) {
                return net.zaminmc.torch.util.Identifier.parse("minecraft:stone");
            }
            return net.zaminmc.torch.util.Identifier.parse("minecraft:air");
        }
    }

    private static MobManager managerIn(SplitWorld world, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        return new MobManager(world, new Random(seed), new NoLoot(),
                996_000, (x, z) -> 8);
    }

    /**
     * The seated pet: settled, tamed through the real walk, claimed. The
     * settle runs BEFORE the tame — the reference's own flow (the tame
     * lands on a grounded body, and the selector's band order seats it at
     * the next pass before the block-sit's first consult can race it).
     */
    private static MobEntity seatedPet(MobManager manager, long seed) {
        MobEntity pet = manager.spawnAt(MobType.WOLF,
                new Position(0.5, 8.0, 0.5));
        assert pet != null;
        pet.tick(false); // the settle (the fresh body grounds itself)
        pet.tameWolf("Owner", "owner-1");
        pet.tick(false); // the claim pass seats the posture
        assert pet.wolfSitting() : "the seat's own precondition";
        return pet;
    }

    // ------------------------------------------------------------------
    // Seat 1 — the wounded sitting pet STANDS (and never re-sits)
    // ------------------------------------------------------------------

    /**
     * The wound's verb stand-down (WolfEntity.takeDamage line 265: the
     * verb clears BEFORE the super's i-frame consult — even a hit the
     * frame refuses stands the command down): a hit sitting pet rises at
     * the goal's next pass and stays standing — the verb is down until
     * the owner commands again. RED on the pre-fix engine (the single
     * bit carried no verb: a hit sitting pet REMAINED SEATED whole).
     */
    @Test
    void theWoundedSittingPetStandsAndNeverResits() {
        SplitWorld world = new SplitWorld();
        MobManager manager = managerIn(world, 90L);
        MobEntity pet = seatedPet(manager, 90L);

        // The hit: a full melee wound through the manager's walk.
        assertTrue(manager.hurt(pet, 2.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE));
        assertFalse(pet.sitVerb(),
                "the wound stands the verb down (line 265, before the"
                        + " frame consult)");
        pet.tick(false); // the goal's stop pass
        assertFalse(pet.wolfSitting(),
                "the hit pet rises (the pre-fix engine kept it seated)");

        // The frame-armed pet never re-sits on its own: the verb stays
        // down through the window and past it.
        for (int i = 0; i < 30; i++) {
            pet.tick(false);
        }
        assertFalse(pet.sitVerb());
        assertFalse(pet.wolfSitting(),
                "the pet never re-sits without the owner's command");

        // The owner's command re-seats it (the verb's return).
        pet.ownerSitVerbFlip(); // !posture = !false = true — the sit command
        pet.tick(false); // the claim pass
        assertTrue(pet.wolfSitting(), "the owner's command re-seats the pet");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the CHEST-QUIRK (the verb flips to the POSTURE's opposite)
    // ------------------------------------------------------------------

    /**
     * THE PERCHED CAT (OcelotSitOnBlockGoal's verb write + the posture
     * write): the perch walk stands the verb down and sits the posture
     * up — and the owner's right-click computes
     * {@code setEnabledWithOwner(!isSitting())} = !true = FALSE: nothing
     * changes, the cat stays perched. The pre-fix engine XOR'd the bit
     * directly — the click STOOD the perched cat down, the classic
     * vanilla quirk broken.
     */
    @Test
    void thePerchedCatStaysPerchedOnTheOwnersClick() {
        SplitWorld world = new SplitWorld();
        world.chestPerch = true; // the block-sit's target two cells east
        MobManager manager = managerIn(world, 91L);
        MobEntity cat = manager.spawnAt(MobType.OCELOT,
                new Position(0.5, 8.0, 0.5));
        assert cat != null;
        cat.tick(false); // the settle (the fresh body grounds itself)
        cat.tameOcelot("Owner", "owner-1", 2);
        cat.tick(false); // the claim pass seats the posture (the band order
        // over the block-sit: the sit goal claims before band 6 ever polls)
        assertTrue(cat.wolfSitting());

        // The stand command frees the body for the perch walk (the
        // block-sit's posture gate: a sitting cat never starts).
        cat.ownerSitVerbFlip();
        cat.tick(false);
        assertFalse(cat.wolfSitting());

        // The perch walk: the first consult's ring scan finds the chest,
        // the cat walks there, and the arrival perches it — the verb
        // standing down the whole way (the goal's start + every-tick
        // writes, the reference's own belt-and-braces).
        boolean perched = false;
        for (int i = 0; i < 400 && !perched; i++) {
            cat.tick(false);
            perched = cat.wolfSitting()
                    && cat.position().x() > 2.0; // at the chest's cell
        }
        assertTrue(perched, "the cat reaches the chest and perches on it");
        assertFalse(cat.sitVerb(),
                "the perch walk stands the verb down (start + every tick)");

        // THE OWNER'S CLICK ON THE PERCHED CAT: the verb flips to the
        // POSTURE'S OWN OPPOSITE — !true = false — NOTHING changes. The
        // pre-fix engine XOR'd the bit directly: the click STOOD the
        // perched cat down, the classic vanilla quirk broken.
        cat.ownerSitVerbFlip();
        assertFalse(cat.sitVerb(),
                "the click computes !isSitting() = false: the verb stays"
                        + " down (the chest-quirk)");
        for (int i = 0; i < 20; i++) {
            cat.tick(false);
        }
        assertTrue(cat.wolfSitting(),
                "the perched cat stays perched — the click never stood it"
                        + " down (the pre-fix engine's XOR did)");

        // The control: a NORMALLY-sitted pet (the verb up) DOES stand —
        // the same verb computes !true = false and the claim stops.
        cat.setSitVerb(true);
        cat.tick(false);
        assertTrue(cat.wolfSitting());
        cat.ownerSitVerbFlip(); // !true = false — the stand command
        cat.tick(false);
        assertFalse(cat.wolfSitting(),
                "the control: the owner's verb stands a normally-sitted"
                        + " pet down (the claim stops at the next pass)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the tame's ONE-PASS FOLLOW (the verb at the tame, the
    // posture at the goal's next claim pass)
    // ------------------------------------------------------------------

    /**
     * The tame arms the VERB (line 346's
     * {@code sitGoal.setEnabledWithOwner(true)}) and the POSTURE follows
     * at the goal's claim pass — the reference's own shape (the selector
     * runs on the mob's next tick). The pre-fix engine wrote the bit
     * directly at the tame.
     */
    @Test
    void theTamesVerbPrecedesThePosturesClaim() {
        SplitWorld world = new SplitWorld();
        MobManager manager = managerIn(world, 92L);
        MobEntity pet = manager.spawnAt(MobType.WOLF,
                new Position(0.5, 8.0, 0.5));
        assert pet != null;

        pet.tick(false); // the settle (the fresh body grounds itself)
        pet.tameWolf("Owner", "owner-1");
        assertTrue(pet.sitVerb(), "the tame arms the verb immediately");
        assertFalse(pet.wolfSitting(),
                "the posture follows the claim pass, not the tame (the"
                        + " reference's own one-pass shape)");

        pet.tick(false); // the claim pass
        assertTrue(pet.wolfSitting(), "the posture lands at the claim");
    }
}
