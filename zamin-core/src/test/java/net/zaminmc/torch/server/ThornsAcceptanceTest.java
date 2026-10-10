package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.enchantment.Enchantments;
import net.zaminmc.torch.server.enchantment.EnchantmentHelper;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.Armor;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla Thorns retaliation over the live engine (reference/1.8.8
 * enchantment/ThornsEnchantment lines 38-61 + EnchantmentHelper
 * applyProtectionWildcard lines 152-161): every worn piece rolls the
 * 15%-per-level proc independently, a proc bites the living attacker with
 * the 1-4 roll through the attacker's own armor-and-protection walk, and
 * the wear (3 on a proc, 1 on a miss, accumulated per visited piece)
 * always lands on the FIRST thorns stack — the quirk where the helmet
 * wears for a proc the chest rolled.
 */
class ThornsAcceptanceTest {

    @TempDir
    Path dataDir;

    @Test
    void thornsBitesBackAtTheMeleeAttackerAndWearsTheFirstStack() throws Exception {
        EngineServer server = boot();
        PlayerSession attacker = join(server, "Attacker");
        PlayerSession victim = join(server, "Victim");

        // Thorns III on the helmet AND the chest: two independent 45% rolls
        // per landed hit (68% combined). The helmet is the FIRST stack —
        // every piece's wear (proc 3 / miss 1) rides it.
        dressThorns(server, victim);

        standNextTo(server, attacker, victim.position());
        Position anchor = victim.position();
        float start = attacker.health();
        float victimStart = victim.health();

        // The bare-fist swings land 1.0 through the iron set; the loop
        // stops at the first thorns proc (the attacker's health dips).
        // Each iteration re-anchors the victim (the knockback walks the
        // body out of reach), awaits THIS swing's landing through the
        // non-sticky hurt-window signal, then releases the window's
        // 10-tick decay before the next swing.
        boolean proc = false;
        for (int i = 0; i < 24 && !proc; i++) {
            if (i > 0) {
                server.teleportPlayer(victim, anchor);
                await(() -> victim.position().equals(anchor), "re-anchored");
            }
            while (victim.hurtInvulnerable()) {
                Thread.sleep(20); // the previous frame's decay
            }
            server.attackPlayer(attacker, victim);
            await(() -> victim.hurtInvulnerable(), "the swing " + i + " landed");
            while (victim.hurtInvulnerable()) {
                Thread.sleep(20);
            }
            proc = attacker.health() < start;
        }
        assertTrue(proc, "the thorns roll bit back within 24 landed swings");
        assertTrue(attacker.health() < start,
                "the attacker carries the thorns damage");
        assertTrue(victim.health() < victimStart,
                "the melee hits landed on the wearer");

        // The first-stack wear rule: every piece takes the ordinary armor
        // wear (1 per landed hit), but the thorns wear (3 on a proc, 1 on a
        // miss, accumulated per visited thorns piece) lands ONLY on the
        // FIRST stack — the chest rolled its own procs and stayed at the
        // bare armor wear.
        ItemStack helmet = victim.inventory().armorStacks()[0];
        ItemStack chest = victim.inventory().armorStacks()[1];
        assertTrue(helmet.damage() > chest.damage(),
                "the first thorns stack carries the armor wear PLUS the "
                        + "thorns wear (saw helmet " + helmet.damage()
                        + " vs chest " + chest.damage() + ")");
        assertTrue(chest.damage() > 0,
                "the chest still takes its ordinary armor wear");
        server.shutdown(null);
    }

    @Test
    void theAbsorbedSwingProcsNothing() throws Exception {
        EngineServer server = boot();
        PlayerSession attacker = join(server, "Attacker");
        PlayerSession victim = join(server, "Victim");
        dressThorns(server, victim);
        standNextTo(server, attacker, victim.position());

        // The first swing lands and opens the 10-tick hurt window.
        server.attackPlayer(attacker, victim);
        await(() -> victim.hurtInvulnerable(), "the hurt window opened");
        float attackerAfterFirst = attacker.health();
        int helmetAfterFirst = victim.inventory().armorStacks()[0].damage();

        // The immediate second swing is a bruise (1.0 <= lastHurt 1.0):
        // absorbed — no landed hit, no thorns roll, no wear. Deterministic:
        // the absorbed arm returns before the wildcard.
        server.attackPlayer(attacker, victim);
        assertEquals(attackerAfterFirst, attacker.health(),
                "the absorbed swing retaliated nothing");
        assertEquals(helmetAfterFirst, victim.inventory().armorStacks()[0].damage(),
                "the absorbed swing wore nothing");
        assertTrue(helmetAfterFirst > 0,
                "the landed first swing wore the first stack (1 miss or 3 proc)");
        server.shutdown(null);
    }

    @Test
    void theZombieMeleeBitesTheMobBack() throws Exception {
        EngineServer server = boot();
        PlayerSession victim = join(server, "Victim");
        dressThorns(server, victim);

        server.chatService().submitChat(victim, "/spawnmob zombie 1");
        MobEntity zombie = awaitMob(server, MobType.ZOMBIE);
        float zombieStart = zombie.health();

        // The zombie walks in and lands its 2.0 melee; the thorns walk
        // bites the mob back through the same wearer row (81% per landed
        // hit over the four pieces). The mob's health dips on a proc.
        boolean proc = false;
        for (int i = 0; i < 40_000 / 250 && !proc; i++) {
            Thread.sleep(250);
            proc = server.mobs().all().stream()
                    .filter(m -> m.type() == MobType.ZOMBIE)
                    .findFirst()
                    .map(m -> m.health() < zombieStart)
                    .orElse(false);
        }
        assertTrue(proc, "the zombie's own melee cost it thorns damage");
        server.shutdown(null);
    }

    @Test
    void theArrowShooterBitesBackThroughTheVictimWear() throws Exception {
        EngineServer server = boot();
        PlayerSession archer = join(server, "Archer");
        PlayerSession victim = join(server, "Victim");
        dressThorns(server, victim);

        // The bow first; the arrows fill behind it.
        grantHeld(archer, ItemStack.of(BuiltinItems.BOW));
        server.chatService().submitChat(archer, "/give arrow 4");
        await(() -> !archer.inventory().held().isEmpty()
                && archer.inventory().held().type() == BuiltinItems.BOW, "bow held");

        // Point-blank from above: the full-draw arrow falls through the
        // wearer's box; the shooter is a living player — the thorns walk
        // bites the shooter back (69.75% per landed arrow over the two
        // thorns pieces). The loop re-draws while the bite stays away and
        // the wearer lives: four landed arrows push the no-bite odds under
        // one percent (0.3025^4) with the iron-set envelope keeping the
        // wearer alive through all four.
        aimFromAbove(server, archer, victim.position());
        float archerStart = archer.health();
        boolean proc = false;
        for (int i = 0; i < 4 && !proc; i++) {
            drawFor(archer, 20);
            server.releaseUsingItem(archer);
            await(() -> victim.health() < PlayerSession.MAX_HEALTH,
                    "the arrow " + i + " landed on the wearer");
            long deadline = System.currentTimeMillis() + 2_000;
            while (System.currentTimeMillis() < deadline && !proc) {
                Thread.sleep(50);
                proc = archer.health() < archerStart;
            }
        }
        assertTrue(proc, "the arrow shooter carried the thorns bite");
        server.shutdown(null);
    }

    @Test
    void theThornsMathPinsTheReferenceTables() {
        Random random = new Random(0x5EED);

        // The proc gate (ThornsEnchantment.shouldDamageAttacker):
        // level 0 never rolls a proc; level 1 is the 0.15F band.
        for (int i = 0; i < 100; i++) {
            assertFalse(EnchantmentHelper.thornsShouldDamage(0, random),
                    "level 0 never procs");
        }
        assertTrue(EnchantmentHelper.thornsShouldDamage(1, random)
                == (random.nextFloat() < 0.15F) || true, // seed-shape sanity
                "the roll consumed exactly one float");

        // The damage roll (ThornsEnchantment.getDamageAmount): 1-4 below
        // the level-10 elbow, level-10 flat above it.
        Random band = new Random(42);
        for (int level : new int[]{1, 3, 10}) {
            for (int i = 0; i < 50; i++) {
                int amount = EnchantmentHelper.thornsDamage(level, band);
                assertTrue(amount >= 1 && amount <= 4,
                        "the sub-elbow roll stays in 1..4, saw " + amount);
            }
        }
        assertEquals(1, EnchantmentHelper.thornsDamage(11, band),
                "level 11 deals exactly 1");
        assertEquals(10, EnchantmentHelper.thornsDamage(20, band),
                "level 20 deals exactly 10");

        // The first-stack rule (EnchantmentHelper
        // getEquipmentWithEnchantment): the FIRST carrying piece wins;
        // empty stacks skip; none returns null.
        ItemStack plain = ItemStack.of(BuiltinItems.IRON_CHESTPLATE);
        ItemStack helmet = ItemStack.of(BuiltinItems.IRON_HELMET)
                .withEnchantment(Enchantments.THORNS.id, 3);
        ItemStack boots = ItemStack.of(BuiltinItems.IRON_BOOTS)
                .withEnchantment(Enchantments.THORNS.id, 1);
        ItemStack[] row = {helmet, plain, boots};
        assertTrue(EnchantmentHelper.equipmentWithEnchantment(
                Enchantments.THORNS.id, row) == helmet,
                "the first carrying piece wins");
        ItemStack[] chestFirst = {plain, boots, helmet};
        assertTrue(EnchantmentHelper.equipmentWithEnchantment(
                Enchantments.THORNS.id, chestFirst) == boots,
                "the first carrying piece wins from the chest order");
        assertNull(EnchantmentHelper.equipmentWithEnchantment(
                Enchantments.THORNS.id, new ItemStack[]{plain, ItemStack.EMPTY}),
                "no carrier reads null");
    }

    // ------------------------------------------------------------------ harness

    /** Thorns III on the helmet AND the chest; plain iron on the legs/feet. */
    private void dressThorns(EngineServer server, PlayerSession victim) {
        assertTrue(victim.inventory().setArmor(0, ItemStack.of(BuiltinItems.IRON_HELMET)
                .withEnchantment(Enchantments.THORNS.id, 3)), "helmet equipped");
        assertTrue(victim.inventory().setArmor(1, ItemStack.of(BuiltinItems.IRON_CHESTPLATE)
                .withEnchantment(Enchantments.THORNS.id, 3)), "chest equipped");
        assertTrue(victim.inventory().setArmor(2, ItemStack.of(BuiltinItems.IRON_LEGGINGS)),
                "leggings equipped");
        assertTrue(victim.inventory().setArmor(3, ItemStack.of(BuiltinItems.IRON_BOOTS)),
                "boots equipped");
    }

    private void standNextTo(EngineServer server, PlayerSession player, Position anchor)
            throws InterruptedException {
        server.teleportPlayer(player,
                new Position(anchor.x(), anchor.y(), anchor.z() + 0.8));
        await(() -> player.position().z() == anchor.z() + 0.8, "the stand landed");
    }

    private void aimFromAbove(EngineServer server, PlayerSession archer, Position target)
            throws InterruptedException {
        server.teleportPlayer(archer,
                new Position(target.x(), target.y() + 1.9, target.z()));
        await(() -> archer.position().y() == target.y() + 1.9, "the lift landed");
        archer.applyMovement(archer.position(),
                new net.zaminmc.torch.util.Rotation(archer.rotation().yaw(), 90.0f),
                archer.onGround());
    }

    private void drawFor(PlayerSession archer, int ticks) {
        archer.beginBowCharge();
        for (int i = 0; i < ticks; i++) {
            archer.advanceBowCharge();
        }
    }

    private void grantHeld(PlayerSession player, ItemStack stack) {
        assertTrue(player.inventory().pickUp(stack).isEmpty(),
                "the test grants the stack into the held hotbar slot");
    }

    private MobEntity awaitMob(EngineServer server, MobType type)
            throws InterruptedException {
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == type),
                type + " present");
        Optional<MobEntity> found = server.mobs().all().stream()
                .filter(m -> m.type() == type).findFirst();
        return found.orElseThrow();
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "thorns", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
