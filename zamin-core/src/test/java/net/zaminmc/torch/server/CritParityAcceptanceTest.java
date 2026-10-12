package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla critical hit over the live engine (reference/1.8.8
 * PlayerEntity.attack lines 958-1008): the falling attack multiplies the
 * base damage by 1.5 BEFORE the enchantment family joins, the landed crit
 * broadcasts the Animation 4 burst on the target, the enchanted hit
 * broadcasts the Animation 5 burst whether falling or not, and the
 * standing unenchanted swing never bursts.
 */
class CritParityAcceptanceTest {

    @TempDir
    Path dataDir;

    /** The (mobId, animation) bursts the engine published. */
    private final List<long[]> mobBursts = new CopyOnWriteArrayList<>();
    /** The (playerName, animation) bursts the engine published. */
    private final List<String[]> playerBursts = new CopyOnWriteArrayList<>();

    @Test
    void fallingCritOneShotsThePigAndBroadcastsAnimation4() throws Exception {
        EngineServer server = boot();
        PlayerSession hunter = join(server, "Hunter");

        server.chatService().submitChat(hunter, "/spawnmob pig 1");
        MobEntity pig = awaitMob(server, MobType.PIG);
        grant(hunter, ItemStack.of(BuiltinItems.DIAMOND_SWORD));

        // The standing control: the plain blade lands exactly 7 (the pig
        // lives at 3) and no crit burst rides the landed hit.
        standNextTo(server, hunter, pig.position());
        server.attackEntity(hunter, pig.entityId());
        await(() -> pig.health() == 3.0f, "the standing swing lands the plain 7");
        assertFalse(mobBursts.stream().anyMatch(b -> b[0] == pig.entityId()),
                "the grounded swing bursts nothing");

        // The falling swing: lift the body 1.9 above the pig (inside the
        // dy > -2.0 band), then descend in gravity steps exactly as the
        // move-packet loop does — applyMovement accumulates the fall debt.
        liftAbove(server, hunter, pig.position(), 1.9);
        await(() -> !hunter.onGround(), "the body hangs airborne");
        descend(hunter, 0.3);
        assertTrue(hunter.fallDistance() > 0.0f,
                "the descent accumulated the fall debt, saw " + hunter.fallDistance());

        // 7 * 1.5 = 10.5 >= the pig's 10 hp: the crit one-shots where the
        // standing blade needs two.
        server.attackEntity(hunter, pig.entityId());
        await(() -> pig.dead(), "the falling swing crit one-shots the 10-hp pig");
        await(() -> mobBursts.stream().anyMatch(b ->
                        b[0] == pig.entityId() && b[1] == EngineServer.ANIMATION_CRIT),
                "the landed crit burst Animation 4 on the target");
        assertFalse(mobBursts.stream().anyMatch(b ->
                        b[0] == pig.entityId() && b[1] == EngineServer.ANIMATION_MAGIC_CRIT),
                "the unenchanted crit bursts no magic spark");
        server.shutdown(null);
    }

    @Test
    void enchantedHitBurstsAnimation5WithoutFalling() throws Exception {
        EngineServer server = boot();
        PlayerSession hunter = join(server, "Hunter");

        server.chatService().submitChat(hunter, "/spawnmob pig 1");
        MobEntity pig = awaitMob(server, MobType.PIG);
        standNextTo(server, hunter, pig.position());
        grant(hunter, ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                .withEnchantment(Enchantments.SHARPNESS.id, 5));

        // The reference's addEnchantedCritParticles fires on f1 > 0 —
        // falling or not. The grounded Sharpness V swing one-shots
        // (7 + 6.25 = 13.25) and bursts Animation 5 on the target.
        server.attackEntity(hunter, pig.entityId());
        await(() -> pig.dead(), "Sharpness V one-shots the 10-hp pig");
        await(() -> mobBursts.stream().anyMatch(b ->
                        b[0] == pig.entityId() && b[1] == EngineServer.ANIMATION_MAGIC_CRIT),
                "the enchanted hit burst Animation 5 on the target");
        assertFalse(mobBursts.stream().anyMatch(b ->
                        b[0] == pig.entityId() && b[1] == EngineServer.ANIMATION_CRIT),
                "the grounded swing bursts no falling crit");
        server.shutdown(null);
    }

    @Test
    void fallingCritRidesTheVictimWireBodyInPvP() throws Exception {
        EngineServer server = boot();
        PlayerSession attacker = join(server, "Attacker");
        PlayerSession victim = join(server, "Victim");

        // The falling bare fist: 1.0 * 1.5 = 1.5 (the reference's crit
        // multiply rides the fist's 1.0 base). The victim holds 20 - 1.5.
        standNextTo(server, attacker, victim.position());
        liftAbove(server, attacker, victim.position(), 1.9);
        await(() -> !attacker.onGround(), "the body hangs airborne");
        descend(attacker, 0.3);
        server.attackPlayer(attacker, victim);
        await(() -> victim.health() < PlayerSession.MAX_HEALTH, "the falling fist landed");
        assertEquals(18.5f, victim.health(), 0.0001f,
                "the bare fist crit lands exactly 1.5");
        await(() -> playerBursts.stream().anyMatch(b ->
                        b[0].equals("Victim") && b[1].equals(Integer.toString(EngineServer.ANIMATION_CRIT))),
                "the crit burst rode the victim's wire body");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness

    /**
     * Lifts the body above the anchor: the teleport wipes the fall debt
     * (the vanilla teleport rule) and leaves the body airborne.
     */
    private void liftAbove(EngineServer server, PlayerSession player,
                           Position anchor, double height) throws InterruptedException {
        Position lifted = new Position(anchor.x(), anchor.y() + height, anchor.z());
        server.teleportPlayer(player, lifted);
        await(() -> player.position().y() == lifted.y(), "the lift landed");
    }

    /**
     * Descends the body in vanilla-gravity steps (the client's ~0.05/tick
     * ramp, coarse enough for any tick count) — each airborne step folds
     * the displacement into the fall debt through applyMovement, exactly
     * what a real client's move packets produce.
     */
    private void descend(PlayerSession player, double budget) {
        Position at = player.position();
        Rotation rotation = player.rotation();
        double bottom = at.y() - budget;
        double y = at.y();
        double step = 0.05;
        while (y - step >= bottom) {
            y -= step;
            step += 0.05; // the vanilla gravity ramp
            player.applyMovement(new Position(at.x(), y, at.z()), rotation, false);
        }
        // Park mid-air above the anchor band: the last step keeps the body
        // strictly inside the dy > -2.0 reach band.
        player.applyMovement(new Position(at.x(), bottom, at.z()), rotation, false);
    }

    private void standNextTo(EngineServer server, PlayerSession player, Position anchor) {
        server.teleportPlayer(player, new Position(anchor.x(), anchor.y(), anchor.z() + 0.8));
    }

    private MobEntity awaitMob(EngineServer server, MobType type) throws InterruptedException {
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == type),
                type + " present");
        Optional<MobEntity> found = server.mobs().all().stream()
                .filter(m -> m.type() == type).findFirst();
        return found.orElseThrow();
    }

    private void grant(PlayerSession player, ItemStack stack) {
        assertTrue(player.inventory().pickUp(stack).isEmpty(),
                "the test grants the stack into the held hotbar slot");
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "crits", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.addEntityAnimationObserver(new EngineServer.EntityAnimationObserver() {
            @Override
            public void onMobAnimation(MobEntity mob, int animation) {
                mobBursts.add(new long[]{mob.entityId(), animation});
            }

            @Override
            public void onPlayerAnimation(PlayerSession player, int animation) {
                playerBursts.add(new String[]{player.name(), Integer.toString(animation)});
            }
        });
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, name + " playing");
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
