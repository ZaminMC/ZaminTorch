package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.enchantment.Enchantments;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enchantment effect hooks over the real engine: the damage family
 * rides the mob category (Sharpness one-shots where the plain blade
 * needs two), Fire Aspect pre-sets the burn and re-arms it on a landed
 * hit, the protection step shrinks a melee hit into the vanilla band,
 * and the dig clock reads the held stack's Efficiency.
 */
class EnchantmentEffectAcceptanceTest {

    @TempDir
    Path dataDir;

    @Test
    void sharpnessVBladeOneShotsWhereThePlainBladeNeedsTwo() throws Exception {
        EngineServer server = boot();
        PlayerSession hunter = join(server, "Hunter");

        // The plain diamond sword (7) leaves the 10-hp pig at 3: two hits.
        server.chatService().submitChat(hunter, "/spawnmob pig 1");
        MobEntity pig = awaitMob(server, MobType.PIG);
        standNextTo(server, hunter, pig);
        grant(hunter, ItemStack.of(BuiltinItems.DIAMOND_SWORD));
        server.attackEntity(hunter, pig.entityId());
        await(() -> pig.health() == 3.0f, "the plain blade lands exactly 7");
        server.attackEntity(hunter, pig.entityId());
        await(() -> pig.dead(), "the plain blade needs the second hit");

        // Sharpness V (7 + 6.25 = 13.25) kills on the first swing.
        server.chatService().submitChat(hunter, "/spawnmob pig 1");
        MobEntity pig2 = awaitMob(server, MobType.PIG);
        standNextTo(server, hunter, pig2);
        grant(hunter, ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                .withEnchantment(Enchantments.SHARPNESS.id, 5));
        server.attackEntity(hunter, pig2.entityId());
        await(() -> pig2.dead(), "Sharpness V one-shots the 10-hp pig");
        server.shutdown(null);
    }

    @Test
    void smiteRidesTheUndeadCategoryAndSkipsThePig() throws Exception {
        EngineServer server = boot();
        PlayerSession hunter = join(server, "Hunter");
        grant(hunter, ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                .withEnchantment(Enchantments.SMITE.id, 5));

        // Smite V on an undead body: 7 + 12.5 = 19.5 of the zombie's 20.
        // Night first: the hostile kinds spawn into a dark world.
        server.chatService().submitChat(hunter, "/time set night");
        server.chatService().submitChat(hunter, "/spawnmob zombie 1");
        MobEntity zombie = awaitMob(server, MobType.ZOMBIE);
        standNextTo(server, hunter, zombie);
        server.attackEntity(hunter, zombie.entityId());
        await(() -> zombie.health() == 0.5f, "Smite V lands 19.5 on the undead");
        // The same blade on a pig: the category is UNDEFINED, so 7 exactly.
        server.chatService().submitChat(hunter, "/spawnmob pig 1");
        MobEntity pig = awaitMob(server, MobType.PIG);
        standNextTo(server, hunter, pig);
        server.attackEntity(hunter, pig.entityId());
        await(() -> pig.health() == 3.0f, "Smite V reads as a plain 7 on the pig");
        server.shutdown(null);
    }

    @Test
    void fireAspectLightsTheTargetAndReArmsOnTheLandedHit() throws Exception {
        EngineServer server = boot();
        PlayerSession hunter = join(server, "Hunter");
        grant(hunter, ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                .withEnchantment(Enchantments.FIRE_ASPECT.id, 1));

        server.chatService().submitChat(hunter, "/spawnmob cow 1");
        MobEntity cow = awaitMob(server, MobType.COW);
        standNextTo(server, hunter, cow);
        server.attackEntity(hunter, cow.entityId());
        // The landed hit re-arms the pre-set to level * 4 seconds = 80 ticks
        // — the clock decays from the moment it lands, so the observable is
        // the burn OUTLIVING the pre-set's own 1-second clock (a bare
        // Fire-Aspect pre-set with no re-arm would be out by now). The burn
        // then finishes the 3-hp body (the historical 1-damage-per-second
        // burn rate) — the vanilla fire kill.
        await(() -> cow.burning(), "the pre-set lit the target");
        Thread.sleep(1_600); // past the pre-set's 20-tick expiry
        assertTrue(cow.burning(), "the re-armed 4-second burn outlives the pre-set");
        await(() -> cow.dead(), "the burn finished the 3-hp body");
        server.shutdown(null);
    }

    @Test
    void protectionIVShrinksTheMeleeHitIntoTheVanillaBand() throws Exception {
        EngineServer server = boot();
        PlayerSession attacker = join(server, "Attacker");
        PlayerSession victim = join(server, "Victim");
        assertEquals(PlayerSession.MAX_HEALTH, victim.health(), "the victim starts whole");

        // Full Protection IV diamond vs the 7-damage sword: the armor
        // envelope passes 7 * (1 - 16.5/25) = 2.38; the 20-EPF roll then
        // multiplies by (25 - k)/25 with k in 10..20 -> 0.476..1.428.
        dressIn(victim, 4);
        standNextTo(server, attacker, victim.position());
        grant(attacker, ItemStack.of(BuiltinItems.DIAMOND_SWORD));
        server.attackPlayer(attacker, victim);
        await(() -> victim.health() < PlayerSession.MAX_HEALTH, "the hit landed");
        float enchantedLoss = PlayerSession.MAX_HEALTH - victim.health();
        assertTrue(enchantedLoss > 0.4f && enchantedLoss < 1.5f,
                "the protection roll carried the hit into the vanilla band, saw "
                        + enchantedLoss);

        // The control: the same diamond set unenchanted loses exactly the
        // armor envelope (7 * (1 - 16.5/25) = 2.38), no enchantment step.
        PlayerSession bare = join(server, "Bare");
        dressIn(bare, 0);
        standNextTo(server, attacker, bare.position());
        server.attackPlayer(attacker, bare);
        await(() -> bare.health() < PlayerSession.MAX_HEALTH, "the control hit landed");
        float bareLoss = PlayerSession.MAX_HEALTH - bare.health();
        assertEquals(2.38f, bareLoss, 0.01f, "the unenchanted set takes the bare envelope");
        server.shutdown(null);
    }

    @Test
    void efficiencyVDigReadsTheHeldStack() throws Exception {
        EngineServer server = boot();
        PlayerSession miner = join(server, "Miner");

        // The control: bare hands on stone is 150 ticks (7.5 s) — not done
        // inside the probe window. The dig is then aborted properly so the
        // enchanted dig below starts clean.
        BlockPosition stone = new BlockPosition(2, 5, 2);
        seedBlock(server, miner, stone, BuiltinBlocks.STONE);
        server.blockInteraction().submitMiningStart(miner, stone);
        Thread.sleep(1_000);
        assertTrue(server.world().getBlock(stone).equals(BuiltinBlocks.STONE),
                "bare hands have not broken stone inside the window");
        server.blockInteraction().submitMiningAborted(miner);

        // Efficiency V diamond pick: 8 + 26 = 34 speed -> 1.5 * 30 / 34
        // = 1.32 ticks per progress — after a 300ms hold the accumulated
        // progress is far past the vanilla 0.7 finish accept, so the
        // client-finish packet mines immediately (the live dig breaks on
        // the finish, the vanilla interaction-manager flow).
        grant(miner, ItemStack.of(BuiltinItems.DIAMOND_PICKAXE)
                .withEnchantment(Enchantments.EFFICIENCY.id, 5));
        server.blockInteraction().submitMiningStart(miner, stone);
        Thread.sleep(300);
        server.blockInteraction().submitMiningFinished(miner, stone);
        await(() -> server.world().getBlock(stone).equals(BuiltinBlocks.AIR),
                "the Efficiency V pick broke stone inside the window");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness

    private void grant(PlayerSession player, ItemStack stack) throws InterruptedException {
        assertTrue(player.inventory().pickUp(stack).isEmpty(),
                "the test grants the stack into the held hotbar slot");
    }

    private void dressIn(PlayerSession victim, int protectionLevel) {
        victim.inventory().setArmor(0, ItemStack.of(BuiltinItems.DIAMOND_HELMET)
                .withEnchantment(Enchantments.PROTECTION.id, protectionLevel));
        victim.inventory().setArmor(1, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE)
                .withEnchantment(Enchantments.PROTECTION.id, protectionLevel));
        victim.inventory().setArmor(2, ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS)
                .withEnchantment(Enchantments.PROTECTION.id, protectionLevel));
        victim.inventory().setArmor(3, ItemStack.of(BuiltinItems.DIAMOND_BOOTS)
                .withEnchantment(Enchantments.PROTECTION.id, protectionLevel));
    }

    private void seedBlock(EngineServer server, PlayerSession player, BlockPosition at,
                           net.zaminmc.torch.block.BlockType type) throws InterruptedException {
        server.blockInteraction().submitPlace(player, at.offset(0, -1, 0), 1, type);
        await(() -> server.world().getBlock(at).equals(type), "seeded at " + at);
    }

    private MobEntity awaitMob(EngineServer server, MobType type) throws InterruptedException {
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == type),
                type + " present");
        Optional<MobEntity> found = server.mobs().all().stream()
                .filter(m -> m.type() == type).findFirst();
        return found.orElseThrow();
    }

    private void standNextTo(EngineServer server, PlayerSession player, MobEntity mob) {
        Position at = mob.position();
        server.teleportPlayer(player, new Position(at.x(), at.y(), at.z() + 0.8));
    }

    private void standNextTo(EngineServer server, PlayerSession player, Position target) {
        server.teleportPlayer(player, new Position(target.x(), target.y(), target.z() + 0.8));
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "ench", "it", 20, 4, 20,
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
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
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
