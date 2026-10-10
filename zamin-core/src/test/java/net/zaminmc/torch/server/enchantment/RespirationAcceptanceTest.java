package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Respiration slice against the reference (reference/1.8.8
 * enchantment/RespirationEnchantment + entity/living/LivingEntity lines
 * 330-333 updateBreathUnderwater): a level above zero rolls
 * {@code nextInt(level + 1) > 0} per submerged tick — success keeps the
 * tick's air, failure takes the decrement; level 0 drains every tick like
 * the unenchanted body. The Depth Strider read rides the same
 * highest-over-equipment shape (the reference's getDepthStriderLevel):
 * the 1.8 swim physics it feeds run client-side for the local player —
 * the server's read is the surface, the ledger records the boundary.
 */
class RespirationAcceptanceTest {

    /** A forced random: the int queue feeds nextInt in order. */
    private static final class ForcedRandom extends Random {
        private final int[] ints;
        private int i;
        private int consumed;

        ForcedRandom(int[] ints) {
            this.ints = ints;
        }

        @Override
        public int nextInt(int bound) {
            consumed++;
            return ints[i++ % ints.length] % bound;
        }
    }

    /** The test link: a no-op engine-internal connection. */
    private static ClientLink noLink() {
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

    private static PlayerSession session() {
        return new PlayerSession(UUID.randomUUID(), "breather", noLink());
    }

    @Test
    void theUnenchantedBodyDrainsEverySubmergedTick() {
        PlayerSession session = session();
        ForcedRandom untouched = new ForcedRandom(new int[]{5});
        for (int i = 0; i < 300; i++) {
            assertFalse(session.advanceBreath(true, 0, untouched),
                    "no drown tick before the air runs out");
        }
        assertEquals(0, untouched.consumed, "level 0 consumed no rolls");
        // The 300 air ticks are gone; the drown rhythm starts (2/second).
        for (int i = 0; i < 19; i++) {
            assertFalse(session.advanceBreath(true, 0, untouched));
        }
        assertTrue(session.advanceBreath(true, 0, untouched),
                "the twentieth tick past the air fires the 2-damage drown tick");
    }

    @Test
    void theFailureArmDrainsLikeTheUnenchantedBody() {
        PlayerSession session = session();
        // nextInt(4) == 0 every tick: the failure arm takes every decrement.
        ForcedRandom failing = new ForcedRandom(new int[]{0});
        for (int i = 0; i < 300; i++) {
            assertFalse(session.advanceBreath(true, 3, failing));
        }
        for (int i = 0; i < 19; i++) {
            assertFalse(session.advanceBreath(true, 3, failing));
        }
        assertTrue(session.advanceBreath(true, 3, failing),
                "the failure arm exhausts the air on the unenchanted rhythm");
    }

    @Test
    void theKeepArmHoldsTheAirIndefinitely() {
        PlayerSession session = session();
        // nextInt(4) >= 1 every tick: the keep arm — the air never drains.
        ForcedRandom keeping = new ForcedRandom(new int[]{1, 2, 3});
        for (int i = 0; i < 400; i++) {
            assertFalse(session.advanceBreath(true, 3, keeping),
                    "a level-3 body whose rolls all keep the air never drowns");
        }
        assertTrue(keeping.consumed >= 400, "every submerged tick rolled");
    }

    @Test
    void outOfWaterRefillsRegardlessOfTheLevel() {
        PlayerSession session = session();
        session.advanceBreath(true, 0, new ForcedRandom(new int[]{0}));
        session.advanceBreath(true, 0, new ForcedRandom(new int[]{0}));
        assertFalse(session.advanceBreath(false, 3, new ForcedRandom(new int[]{0})),
                "the surface refill consumes no roll");
        // Full air again: 300 fresh submerged ticks before the drain ends.
        ForcedRandom fresh = new ForcedRandom(new int[]{5});
        for (int i = 0; i < 300; i++) {
            assertFalse(session.advanceBreath(true, 0, fresh));
        }
        assertFalse(session.advanceBreath(true, 0, fresh),
                "the refilled body drained 300 fresh ticks before drowning");
    }

    @Test
    void theHelmetLevelReadsHighestOverTheArmorRow() {
        ItemStack helmet = ItemStack.of(BuiltinItems.IRON_HELMET)
                .withEnchantment(Enchantments.RESPIRATION.id, 3);
        ItemStack boots = ItemStack.of(BuiltinItems.IRON_BOOTS)
                .withEnchantment(Enchantments.DEPTH_STRIDER.id, 3);
        ItemStack[] row = new ItemStack[]{helmet, ItemStack.EMPTY, ItemStack.EMPTY, boots};
        assertEquals(3, EnchantmentHelper.highestLevel(
                Enchantments.RESPIRATION.id, row),
                "the respiration read is the highest over the equipment");
        assertEquals(3, EnchantmentHelper.highestLevel(
                Enchantments.DEPTH_STRIDER.id, row),
                "the depth strider read is the highest over the equipment "
                        + "(the 1.8 swim physics it feeds run client-side; "
                        + "the server's read is the recorded surface)");
        assertEquals(0, EnchantmentHelper.highestLevel(
                        Enchantments.RESPIRATION.id,
                        new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY,
                                ItemStack.EMPTY, ItemStack.EMPTY}),
                "the bare body reads zero");
    }
}
