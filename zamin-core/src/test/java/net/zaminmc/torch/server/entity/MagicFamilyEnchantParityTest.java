package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.enchantment.Enchantments;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE EK-2 — THE MAGIC FAMILY'S OWN ENCHANT + EFFECT WALK over the
 * PLAYER victims at the two player-side magic sites (the splash/wither
 * arm and the guardian laser's magic half): the reference's
 * {@code getDamageAfterEffectsAndEnchantments} halves
 * (LivingEntity lines 882-907) that the mob walk landed in Wave EK,
 * now over the player's own armor row. The reference's
 * {@code DamageSource.magic} family carries
 * {@code setBypassesArmor()} — the ARMOR envelope never runs — but is
 * NEVER {@code setUnblockable()}, so:
 * <ul>
 *   <li>the Resistance effect still halves through
 *       {@code (25 - (amplifier+1)*5) / 25} (lines 888-893);</li>
 *   <li>the Protection EPF consult still folds through
 *       {@code (25 - k) / 25} over the armor row with the MAGIC kind
 *       (lines 898-905) — the plain Protection's "all" family alone
 *       contributing (the fire/fall/blast/projectile branches all
 *       refuse, {@code EnchantmentProtection}'s own subtype ladder).</li>
 * </ul>
 * The retired arms' "no protection shave" notes conflated the armor
 * pierce with the enchant consult the reference never skips.
 */
class MagicFamilyEnchantParityTest {

    @TempDir
    Path dataDir;

    private EngineServer activeServer;

    /**
     * SEAT 1 (the control): full plain diamond armor (20 points) — the
     * magic family PIERCES armor whole, and the plain row carries no
     * enchant store: the direct-hit splash of instant damage I pays
     * its full 6.0 ({@code 6 << 0} at proximity 1.0,
     * StatusEffect.affectHealth lines 166-180).
     */
    @Test
    void theSplashHarmingBypassesThePlainArmorRowWhole() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession victim = join(server, "Plain");
            dressIn(victim, 0); // plain diamond, no enchant store
            assertEquals(20, victim.inventory().totalArmorPoints(),
                    "the plain row wears its 20 points");
            float paid = splashDamage(server, victim);
            assertEquals(6.0f, paid, 1e-4f,
                    "the magic family pierces the plain armor row whole (6.0)");
            server.shutdown(null);
        } finally {
            activeServer = null;
        }
    }

    /**
     * SEAT 2 (the fold): the same full row at Protection IV — the "all"
     * family's EPF {@code floor((6 + 16)/3 * 0.75) = 5} per piece, four
     * pieces 20, the half-to-full roll answers k in [10, 20], so the
     * 6.0 lands in {@code 6 * (25-k)/25} = [1.2, 3.6] — never the
     * pierced whole (the retired walk's flat 6.0).
     */
    @Test
    void theProtectionRowFoldsTheMagicFamilyOnThePlayer() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession victim = join(server, "Protected");
            dressIn(victim, 4); // Protection IV on every piece
            float paid = splashDamage(server, victim);
            assertTrue(paid >= 1.2f - 1e-4f && paid <= 3.6f + 1e-4f,
                    "the Protection IV row folds the magic wound into the EPF"
                            + " band [1.2, 3.6] (saw " + paid + ")");
            assertTrue(paid < 6.0f - 0.5f, "the fold is real (saw " + paid + ")");
            server.shutdown(null);
        } finally {
            activeServer = null;
        }
    }

    /**
     * SEAT 3 (the effect halving): Resistance I (amplifier 0, no armor)
     * — {@code 6 * (25 - 5) / 25} = exactly 4.8 (lines 888-893); the
     * magic family is no void, so the halving never refuses.
     */
    @Test
    void theResistanceEffectHalvesThePlayersMagicWound() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession victim = join(server, "Resisting");
            server.ticker().submit(() -> server.potions().add(victim,
                    new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 0)));
            await(() -> server.potions().get(victim, StatusEffects.RESISTANCE) != null,
                    "the resistance installed");
            float paid = splashDamage(server, victim);
            assertEquals(4.8f, paid, 1e-4f,
                    "Resistance I halves the magic wound (6 * 20/25 = 4.8)");
            server.shutdown(null);
        } finally {
            activeServer = null;
        }
    }

    // ------------------------------------------------------------------ harness

    /**
     * Runs a direct-hit splash of instant damage I (metadata 32732,
     * amplifier 0 — {@code 6 << 0} = 6.0 at proximity 1.0) inside the
     * tick lane and reports the health the player paid. The damage
     * lands synchronously on the tick lane, so the before/after read
     * is exact (the WitchAcceptanceTest harness's own shape).
     */
    private float splashDamage(EngineServer server, PlayerSession victim)
            throws InterruptedException {
        float[] paid = new float[] {Float.NEGATIVE_INFINITY};
        Position at = victim.position();
        server.world().execute(() -> {
            float before = victim.health();
            server.potions().splash(at.x(), at.y() + 1.0, at.z(), 32732,
                    victim, null, victim.dimension());
            paid[0] = before - victim.health();
        });
        await(() -> paid[0] != Float.NEGATIVE_INFINITY, "the splash ran");
        return paid[0];
    }

    private void dressIn(PlayerSession victim, int protectionLevel) {
        if (protectionLevel <= 0) {
            victim.inventory().setArmor(0, ItemStack.of(BuiltinItems.DIAMOND_HELMET));
            victim.inventory().setArmor(1, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE));
            victim.inventory().setArmor(2, ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS));
            victim.inventory().setArmor(3, ItemStack.of(BuiltinItems.DIAMOND_BOOTS));
            return;
        }
        victim.inventory().setArmor(0, ItemStack.of(BuiltinItems.DIAMOND_HELMET)
                .withEnchantment(Enchantments.PROTECTION.id, protectionLevel));
        victim.inventory().setArmor(1, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE)
                .withEnchantment(Enchantments.PROTECTION.id, protectionLevel));
        victim.inventory().setArmor(2, ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS)
                .withEnchantment(Enchantments.PROTECTION.id, protectionLevel));
        victim.inventory().setArmor(3, ItemStack.of(BuiltinItems.DIAMOND_BOOTS)
                .withEnchantment(Enchantments.PROTECTION.id, protectionLevel));
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "magicfam", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        activeServer = server;
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, java.util.UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        server.ticker().submit(() -> session.setOpLevel(2));
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        assertNotNull(session);
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description)
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
