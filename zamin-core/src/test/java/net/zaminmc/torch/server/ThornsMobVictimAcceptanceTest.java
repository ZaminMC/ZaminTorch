package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.enchantment.Enchantments;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The thorns wildcard's MOB-victim arm (the README TODO's row 8 — the
 * reference's applyProtectionWildcard over MobEntity's five-slot equipment
 * row, reference/1.8.8 EnchantmentHelper lines 152-161): the PLAYER victim
 * forms landed with the original family; these prove the three MOB-victim
 * sites — the player's landed melee (PlayerEntity.attack line 1016), the
 * living shooter's landed projectile (ArrowEntity line 264 + the fireball
 * trio's damageEntity pair-walk), the mob melee walks (Entity.damageEntity
 * lines 1943-1947 over MonsterEntity.attack 101 / IronGolemEntity 159 /
 * SlimeEntity 202 / WolfEntity 277) — beside the wear law's own rows: the
 * wear always lands on the FIRST thorns stack of the five-slot row (3 on a
 * proc, 1 on a miss, accumulated per visited piece), a broken piece leaves
 * the slot EMPTY, and a plain piece never bites nor wears. The mob row's
 * own order quirk: the row walks [held, feet, legs, CHEST, head], so a
 * dressed chest+helmet pair wears the CHEST — the helmet contributes its
 * proc rolls but never its wear, the same first-stack law the player
 * family proves over its helmet-first row.
 */
class ThornsMobVictimAcceptanceTest {

    @TempDir
    Path dataDir;

    /** The current test's engine — the tick clock the shared await reads. */
    private EngineServer activeServer;

    @Test
    void thePlayersMeleeBitesThroughTheMobWear() throws Exception {
        EngineServer server = boot();
        PlayerSession attacker = join(server, "Attacker");
        server.chatService().submitChat(attacker, "/spawnmob pig 1");
        MobEntity pig = awaitMob(server, MobType.PIG);
        // Thorns III on the chest AND the helmet: two independent 45%
        // rolls per landed swing (69.75% combined) — the loop ends on the
        // first bite. The chest is the row's FIRST carrier (the mob row
        // walks [held, feet, legs, chest, head]).
        pig.setEquipment(3, ItemStack.of(BuiltinItems.IRON_CHESTPLATE)
                .withEnchantment(Enchantments.THORNS.id, 3));
        pig.setEquipment(4, ItemStack.of(BuiltinItems.IRON_HELMET)
                .withEnchantment(Enchantments.THORNS.id, 3));

        float attackerStart = attacker.health();
        boolean proc = false;
        int landed = 0;
        for (int i = 0; i < 24 && !proc && !pig.dead(); i++) {
            // The previous hit's i-frame decay (10 ticks, the reference's
            // 0.5s) — the run #375/#376 conversion: the 600ms wall sleep
            // became its nominal tick equivalent.
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 12,
                    "the i-frame decay before swing " + i);
            // The pig flees after every hit (the panic goal's own ~7
            // blocks a second): the read-teleport-swing sequence runs
            // tight so the drift inside it stays a fraction of the reach.
            Position at = pig.position();
            server.teleportPlayer(attacker,
                    new Position(at.x(), at.y(), at.z() + 0.8));
            await(() -> attacker.position().z() == at.z() + 0.8,
                    "the stand " + i + " landed");
            float pigStart = pig.health();
            server.attackEntity(attacker, pig.entityId());
            await(() -> pig.health() < pigStart, "the swing " + i + " landed");
            landed++;
            proc = attacker.health() < attackerStart;
        }
        assertTrue(proc, "the thorns roll bit the melee attacker through the "
                + "mob's worn row (saw " + landed + " landed swings)");
        assertTrue(pig.equipment(3).damage() > 0,
                "the FIRST thorns stack (the chest) carried the wear (saw "
                        + pig.equipment(3).damage() + " after " + landed
                        + " landed swings)");
        assertEquals(0, pig.equipment(4).damage(),
                "the helmet contributes its proc rolls but never its wear "
                        + "— the first-stack quirk over the mob's row order");
        server.shutdown(null);
    }

    @Test
    void theArchersArrowBitesTheShooterThroughTheMobWear() throws Exception {
        EngineServer server = boot();
        PlayerSession archer = join(server, "Archer");
        server.chatService().submitChat(archer, "/spawnmob zombie 1");
        MobEntity zombie = awaitMob(server, MobType.ZOMBIE);
        // The test's own deterministic-target law (the reference's zombie
        // strolls — WanderAroundGoal's 1-in-120 from the first idle tick,
        // Slice TF-6): the arrows fall through the body's column at the
        // captured anchor, so the stroll goal leaves the selector for the
        // test's life (the §27.3 removal surface — the thorns law is the
        // pin, not the walk).
        zombie.goals().removeBuiltIn("stroll");
        zombie.setEquipment(3, ItemStack.of(BuiltinItems.IRON_CHESTPLATE)
                .withEnchantment(Enchantments.THORNS.id, 3));
        zombie.setEquipment(4, ItemStack.of(BuiltinItems.IRON_HELMET)
                .withEnchantment(Enchantments.THORNS.id, 3));

        grantHeld(archer, ItemStack.of(BuiltinItems.BOW));
        server.chatService().submitChat(archer, "/give " + archer.name() + " arrow 6");
        await(() -> !archer.inventory().held().isEmpty()
                && archer.inventory().held().type() == BuiltinItems.BOW, "bow held");

        // Straight down from +2.9 — outside the zombie's melee vertical
        // band (the only other writer of the archer's health); the arrows
        // fall through the body's column. Two Thorns III carriers: 69.75%
        // per landed arrow; a killing shot still retaliates through the
        // captured row (the row-8 law), and four attempts push the no-bite
        // odds under one percent.
        float archerStart = archer.health();
        boolean proc = false;
        for (int i = 0; i < 4 && !proc && !zombie.dead(); i++) {
            // The zombie aggros the archer after the first hit (the
            // revenge goal — the body walks and jumps at the shooter it
            // cannot reach), so the captured anchor is attempt 0's alone:
            // every attempt re-anchors the column over the body's LIVE
            // position (run 561's attempt 1 shot the empty first column
            // for twenty straight seconds).
            Position at = zombie.position();
            server.teleportPlayer(archer,
                    new Position(at.x(), at.y() + 2.9, at.z()));
            await(() -> archer.position().y() == at.y() + 2.9,
                    "the lift " + i + " landed");
            archer.applyMovement(archer.position(),
                    new net.zaminmc.torch.util.Rotation(archer.rotation().yaw(), 90.0f),
                    archer.onGround());
            float mobStart = zombie.health();
            // The MID draw, pinned at exactly 19 by the freeze (the
            // RangedCombat seats' own law): a full draw's crit rolls 6..10
            // damage, and four of those kill the 20-hp zombie by the third
            // arrow — silently cutting the proc rolls to three (~2.8%
            // structural failure against the seat's own under-one-percent
            // four-roll design). The pinned 19 lands a deterministic 6:
            // three arrows leave the body at 2, all four rolls live.
            archer.freezeBowChargeForTest();
            archer.beginBowCharge();
            for (int t = 0; t < 19; t++) {
                archer.advanceBowCharge();
            }
            server.releaseUsingItem(archer);
            await(() -> !archer.bowCharging(), "the release " + i + " fired");
            archer.unfreezeBowChargeForTest();
            await(() -> zombie.health() < mobStart || zombie.dead(),
                    "the arrow " + i + " landed on the wearer");
            // The bite poll — the EXPECTED-MISS form (the run #375/#376
            // conversion): a no-bite roll (30.25% over the two carriers) is
            // a legitimate outcome the loop retries, so the window may never
            // throw on a miss — only the liveness guard does. 40 ticks = the
            // old 2s window's nominal equivalent.
            proc = net.zaminmc.torch.testing.EngineAwaits.awaitConditionWithin(
                    server.world(), () -> archer.health() < archerStart, 40,
                    "the thorns bite " + i + " reaching the shooter");
        }
        assertTrue(proc, "the arrow shooter carried the thorns bite through "
                + "the mob's worn row");
        assertTrue(zombie.equipment(3).damage() > 0,
                "the FIRST thorns stack (the chest) carried the projectile wear");
        server.shutdown(null);
    }

    @Test
    void theMobMeleeBitesBackThroughTheWornRow() throws Exception {
        EngineServer server = boot();
        PlayerSession summoner = join(server, "Summoner");
        server.chatService().submitChat(summoner, "/spawnmob sheep 1");
        MobEntity sheep = awaitMob(server, MobType.SHEEP);
        sheep.setEquipment(4, ItemStack.of(BuiltinItems.IRON_HELMET)
                .withEnchantment(Enchantments.THORNS.id, 3));

        // The zombie's chase walks it onto the sheep; every landed melee
        // rides the hurtByMob walk — the seam's bite. Nothing else writes
        // the zombie's health (the sheep never fights back).
        server.chatService().submitChat(summoner, "/spawnmob zombie 1");
        MobEntity zombie = awaitSecond(server, MobType.ZOMBIE, sheep);
        zombie.setMobAttackTarget(sheep);

        float zombieStart = zombie.health();
        float sheepStart = sheep.health();
        await(() -> sheep.health() < sheepStart, "the zombie's melee landed");
        // The bite rolls per landed hit (45% per swing over the single
        // carrier); the melee keeps landing until one procs.
        await(() -> zombie.health() < zombieStart, "the thorns roll bit the mob");
        assertTrue(sheep.equipment(4).damage() > 0,
                "the sole carrier (the helmet) carried the mob-melee wear");
        server.shutdown(null);
    }

    @Test
    void theBrokenPieceLeavesTheSlot() throws Exception {
        EngineServer server = boot();
        PlayerSession attacker = join(server, "Attacker");
        server.chatService().submitChat(attacker, "/spawnmob pig 1");
        MobEntity pig = awaitMob(server, MobType.PIG);
        // AT the maximum exactly — the reference's own survival point
        // (ItemStack.takeDamage line 204: the break lands at damage > max,
        // never >=): the next thorns visit (proc 3 or miss 1) EXCEEDS the
        // durability and the piece leaves the slot — the pre-fix >=
        // boundary broke the piece one visit early.
        ItemStack frail = ItemStack.of(BuiltinItems.IRON_HELMET)
                .withEnchantment(Enchantments.THORNS.id, 3)
                .withDamage(BuiltinItems.IRON_HELMET.maxDurability());
        pig.setEquipment(4, frail);

        Position at = pig.position();
        server.teleportPlayer(attacker,
                new Position(at.x(), at.y(), at.z() + 0.8));
        await(() -> attacker.position().z() == at.z() + 0.8, "the stand landed");
        float pigStart = pig.health();
        server.attackEntity(attacker, pig.entityId());
        await(() -> pig.health() < pigStart, "the swing landed");
        assertTrue(pig.equipment(4).isEmpty(),
                "the worn-through piece left the slot EMPTY");
        server.shutdown(null);
    }

    @Test
    void thePlainPieceNeverBitesNorWears() throws Exception {
        EngineServer server = boot();
        PlayerSession attacker = join(server, "Attacker");
        server.chatService().submitChat(attacker, "/spawnmob pig 1");
        MobEntity pig = awaitMob(server, MobType.PIG);
        pig.setEquipment(4, ItemStack.of(BuiltinItems.IRON_HELMET));

        Position at = pig.position();
        server.teleportPlayer(attacker,
                new Position(at.x(), at.y(), at.z() + 0.8));
        await(() -> attacker.position().z() == at.z() + 0.8, "the stand landed");
        float attackerStart = attacker.health();
        float pigStart = pig.health();
        server.attackEntity(attacker, pig.entityId());
        await(() -> pig.health() < pigStart, "the swing landed");
        assertEquals(attackerStart, attacker.health(),
                "a plain piece never bites the attacker");
        assertEquals(0, pig.equipment(4).damage(),
                "a plain piece carries no thorns wear (no other mob-armor "
                        + "wear walk exists)");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness

    private MobEntity awaitMob(EngineServer server, MobType type)
            throws InterruptedException {
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == type),
                type + " present");
        return server.mobs().all().stream()
                .filter(m -> m.type() == type).findFirst().orElseThrow();
    }

    /** The SECOND body of the kind (the pair staging — the first already held). */
    private MobEntity awaitSecond(EngineServer server, MobType type, MobEntity first)
            throws InterruptedException {
        await(() -> server.mobs().all().stream()
                        .filter(m -> m.type() == type && m.entityId() != first.entityId())
                        .anyMatch(m -> true),
                type + " pair present");
        return server.mobs().all().stream()
                .filter(m -> m.type() == type && m.entityId() != first.entityId())
                .findFirst().orElseThrow();
    }

    private void grantHeld(PlayerSession player, ItemStack stack) {
        assertTrue(player.inventory().pickUp(stack).isEmpty(),
                "the test grants the stack into the held hotbar slot");
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "thornsmob", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        activeServer = server;
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        server.ticker().submit(() -> session.setOpLevel(2));
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

    /**
     * The engine-tick await (the run #375/#376 conversion — the census's
     * own rule extended to this class's local helper): the old 20s wall
     * deadline expired by wall time on a loaded runner even while the
     * engine was alive and progressing. The budget is the wall form's
     * nominal tick equivalent (20s = 400 ticks at 20 TPS).
     */
    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        net.zaminmc.torch.testing.EngineAwaits.awaitCondition(
                activeServer.world(), condition, 400, description);
    }
}
