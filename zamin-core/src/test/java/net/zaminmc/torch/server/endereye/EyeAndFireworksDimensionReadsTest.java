package net.zaminmc.torch.server.endereye;

import net.zaminmc.torch.server.fireworks.FireworksEntity;
import net.zaminmc.torch.server.fireworks.FireworksSystem;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave EYFW — the eye and firework families' dimension reads (the
 * reference's Entity.world over the two standing island families, the
 * TF-9 shape): the launch stamps the body's dimension from the caller's
 * world, every world-touching site reads it back — the eye's throw sound
 * and end-of-flight item drop carry the EYE'S OWN world (the reference's
 * tick lines 160-165: {@code new ItemEntity(this.world, ...)} and {@code
 * this.world.doEvent(2003, ...)}), the rocket's age-0 launch sound
 * carries the ROCKET'S OWN (tick line 94's {@code this.world.playSound})
 * — and the restored (anvil-load) forms keep the overworld 0 (the
 * TF-9 convention: the import files are overworld files).
 */
class EyeAndFireworksDimensionReadsTest {

    /** A fixed-roll random (0 = the 1-in-5 no-drop, 1..4 = the drop form). */
    private static final class FixedRoll extends Random {
        private final int roll;

        FixedRoll(int roll) {
            this.roll = roll;
        }

        @Override
        public int nextInt(int bound) {
            return roll % bound;
        }
    }

    /** The recording eye surface: the throw sound and the drop, both with
     *  their dimension rows. */
    private static final class RecordingEyeSurface implements EnderEyeSystem.Surface {
        final List<Integer> throwDimensions = new ArrayList<>();
        final List<Integer> dropDimensions = new ArrayList<>();

        @Override
        public void throwSound(Position at, int dimension, Random random) {
            throwDimensions.add(dimension);
        }

        @Override
        public void dropEyeItem(Position at, int dimension,
                                net.zaminmc.torch.item.ItemStack stack) {
            dropDimensions.add(dimension);
        }
    }

    /** The recording rocket surface: the age-0 launch sound with its
     *  dimension row. */
    private static final class RecordingRocketSurface implements FireworksSystem.Surface {
        final List<Integer> launchDimensions = new ArrayList<>();

        @Override
        public void launchSound(Position at, int dimension) {
            launchDimensions.add(dimension);
        }
    }

    @Test
    void theEyeCarriesItsThrowersWorldThroughEverySite() {
        RecordingEyeSurface surface = new RecordingEyeSurface();
        // The roll 1 of 5: the drop form — both end sites fire.
        EnderEyeSystem system = new EnderEyeSystem(surface, new FixedRoll(1), 42_000);
        // A NETHER player throws (the reference's EnderEyeItem use arm
        // births the eye in the thrower's world): dimension -1.
        EnderEyeEntity eye = system.launch(new Position(-12.5, 70.0, 3.5), -1,
                200.0, 64.0, 0.0);
        assertEquals(-1, eye.dimension(), "the body's own world row");
        assertEquals(List.of(-1), surface.throwDimensions,
                "the throw sound plays in the eye's own world");
        // The 80-tick flight: the end's drop consults the same row.
        for (int i = 0; i < 81; i++) {
            system.tick();
        }
        assertTrue(system.all().isEmpty(), "the ended eye left the world");
        assertEquals(List.of(-1), surface.dropDimensions,
                "the end's item drop spawns in the eye's own world "
                        + "(the reference's new ItemEntity(this.world, ...))");
    }

    @Test
    void theEyeInEachDimensionAnswersItsOwn() {
        RecordingEyeSurface surface = new RecordingEyeSurface();
        EnderEyeSystem system = new EnderEyeSystem(surface, new FixedRoll(1), 42_500);
        // The End player (dimension 1) and the overworld player (0) —
        // each eye carries its own thrower's world, never a shared row.
        EnderEyeEntity end = system.launch(new Position(0, 64, 0), 1, 40, 64, 0);
        EnderEyeEntity overworld = system.launch(new Position(0, 64, 0), 0, 40, 64, 0);
        assertEquals(1, end.dimension());
        assertEquals(0, overworld.dimension());
        assertEquals(List.of(1, 0), surface.throwDimensions,
                "each throw's own world, in the launch order");
    }

    @Test
    void theRestoredEyeKeepsTheAnvilOverworld() {
        RecordingEyeSurface surface = new RecordingEyeSurface();
        EnderEyeSystem system = new EnderEyeSystem(surface, new FixedRoll(0), 43_000);
        // The anvil load path (the reference's empty readCustomNbt — the
        // base rows alone): the imported eye is an overworld file's row,
        // the TF-9 convention, and the 2003 end (the no-drop roll 0)
        // never consults the surface's drop arm.
        EnderEyeEntity restored = system.launchRestored(
                new Position(10.0, 64.0, 10.0), 0.1, 0.2, 0.3);
        assertEquals(0, restored.dimension(),
                "the restored form keeps the overworld 0");
        for (int i = 0; i < 81; i++) {
            system.tick();
        }
        assertTrue(surface.dropDimensions.isEmpty(),
                "the 1-in-5 restored form ends in the 2003 event, never the drop");
    }

    @Test
    void theRocketCarriesItsPlacersWorldToTheLaunchSound() {
        RecordingRocketSurface surface = new RecordingRocketSurface();
        FireworksSystem system = new FireworksSystem(surface, new Random(71L), 44_000);
        // A NETHER player places the rocket on a block (the reference's
        // FireworksItem.useOn births it in the placer's world): the age-0
        // launch sound reads the body's own world back.
        FireworksEntity rocket = system.launch(new Position(16.5, 64.0, -2.5), -1,
                net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 1));
        assertEquals(-1, rocket.dimension(), "the body's own world row");
        system.tick(); // the age-0 sound tick
        assertEquals(List.of(-1), surface.launchDimensions,
                "the launch sound plays in the rocket's own world "
                        + "(the reference's this.world.playSound)");
    }

    @Test
    void theRestoredRocketKeepsTheAnvilOverworldAndNeverSounds() {
        RecordingRocketSurface surface = new RecordingRocketSurface();
        FireworksSystem system = new FireworksSystem(surface, new Random(73L), 44_500);
        // The anvil load path (the reference's readCustomNbt: the SAVED
        // Life resumes): the imported rocket is an overworld file's row,
        // and the age-0 sound gate never fires for a Life > 0 load — the
        // reference's own age==0 check.
        FireworksEntity restored = system.launchRestored(
                new Position(0, 64, 0), 0.0, 0.05, 0.0, 5, 12,
                net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 1));
        assertEquals(0, restored.dimension(),
                "the restored form keeps the overworld 0");
        system.tick();
        assertTrue(surface.launchDimensions.isEmpty(),
                "a loaded rocket at Life > 0 never sounds (the age-0 gate)");
    }
}
