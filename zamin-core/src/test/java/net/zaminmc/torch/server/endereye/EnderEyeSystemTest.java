package net.zaminmc.torch.server.endereye;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eye family's own law (the reference's EnderEyeEntity 1:1): the
 * target's 12-block clamp with the +8 targetY far form (setTarget lines
 * 63-79), the server-side steering (the 0.0025 closing gain, the
 * near-target 0.8 damp, the vertical 0.015 approach — tick lines
 * 111-133), the 80-tick lifespan end, and the two end forms (the 4-in-5
 * item drop vs the 2003 event). The restored form carries the reference's
 * load quirk whole: the transient target (the origin) and the drops row
 * (a loaded eye never rolls — the 2003 event always ends it).
 */
class EnderEyeSystemTest {

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

    private static final class RecordingSurface implements EnderEyeSystem.Surface {
        Position droppedAt;
        Integer droppedDimension;
        ItemStack droppedStack;
        int throwSounds;
        Integer lastThrowDimension;

        @Override
        public void throwSound(Position at, int dimension, Random random) {
            throwSounds++;
            lastThrowDimension = dimension;
        }

        @Override
        public void dropEyeItem(Position at, int dimension, ItemStack stack) {
            droppedAt = at;
            droppedDimension = dimension;
            droppedStack = stack;
        }
    }

    @Test
    void theFarTargetClampsAtTwelveBlocksWithTheEightLift() {
        RecordingSurface surface = new RecordingSurface();
        EnderEyeSystem system = new EnderEyeSystem(surface, new FixedRoll(1), 1000);
        // A target 100 blocks east: the clamp form lands 12 along the line
        // with the +8 targetY (the reference's setTarget lines 63-79).
        EnderEyeEntity eye = system.launch(new Position(0, 64, 0), 0, 100, 64, 0);
        assertEquals(12.0, eye.target().x(), 1e-9, "the clamp form's X");
        assertEquals(72.0, eye.target().y(), 1e-9, "the +8 targetY lift");
        assertEquals(0.0, eye.target().z(), 1e-9, "the line's Z");
        assertEquals(1, surface.throwSounds, "the throw sound rode the launch");
        assertTrue(eye.dropsItem(), "the roll 1 of 5 is the drop form");
    }

    @Test
    void theNearTargetLandsWhole() {
        EnderEyeSystem system = new EnderEyeSystem(new RecordingSurface(), new FixedRoll(1), 1000);
        EnderEyeEntity eye = system.launch(new Position(0, 64, 0), 0, 5, 70, 3);
        assertEquals(5.0, eye.target().x(), 1e-9, "the near form lands whole");
        assertEquals(70.0, eye.target().y(), 1e-9, "the near targetY");
        assertEquals(3.0, eye.target().z(), 1e-9, "the near Z");
    }

    @Test
    void theFlightSteersTowardTheTargetAndEndsAtEighty() {
        RecordingSurface surface = new RecordingSurface();
        // The roll 3 of 5: the drop form.
        EnderEyeSystem system = new EnderEyeSystem(surface, new FixedRoll(3), 1000);
        EnderEyeEntity eye = system.launch(new Position(0, 64, 0), 0, 100, 64, 0);
        // 80 flight ticks: the steering walks the eye toward the clamped
        // target; the lifespan end is the > 80 gate.
        for (int i = 0; i < 81; i++) {
            system.tick();
        }
        assertTrue(eye.expired(), "the 81st tick crossed the > 80 gate");
        List<EnderEyeEntity> live = system.all();
        assertTrue(live.isEmpty(), "the ended eye left the world");
        // The end's drop: the fresh ENDER_EYE stack at the eye's rest.
        assertEquals(eye.position(), surface.droppedAt, "the drop landed at the eye's rest");
        assertEquals("minecraft:ender_eye", surface.droppedStack.type().identifier().toString());
        assertEquals(1, surface.droppedStack.count());
    }

    @Test
    void theRestoredEyeNeverDropsAndAimsAtTheOrigin() {
        RecordingSurface surface = new RecordingSurface();
        EnderEyeSystem system = new EnderEyeSystem(surface, new FixedRoll(1), 1000);
        // The restored form (the reference's EMPTY readCustomNbt: no rows of
        // its own): the transient target is the origin and the drops row
        // never rolls — the 2003 event always ends it.
        EnderEyeEntity eye = system.launchRestored(new Position(500, 64, 500), 0.5, 0.1, -0.5);
        assertFalse(eye.dropsItem(), "a loaded eye never rolls the drops row");
        assertEquals(0.0, eye.target().x(), 1e-9, "the transient target is the origin");
        assertEquals(0.0, eye.target().y(), 1e-9, "the transient targetY");
        // The 80-tick end: the no-drop form — the drop never happens.
        for (int i = 0; i < 81; i++) {
            system.tick();
        }
        assertTrue(eye.expired(), "the loaded eye's 80 ticks ended it");
        assertTrue(system.all().isEmpty(), "the world carried the end");
        assertNull(surface.droppedAt, "the drop never happened (the 2003 form)");
    }
}
