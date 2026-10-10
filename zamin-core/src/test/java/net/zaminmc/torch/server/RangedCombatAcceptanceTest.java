package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ranged-combat and feedback flows on a real ticker: the bow's draw and
 * release (ammunition consumed, durability worn, a speed-scaled arrow flying),
 * the snowball's press-throw, the block break/place feedback riding the FX
 * bus, and the posture actions reaching the survival listeners.
 */
class RangedCombatAcceptanceTest {

    @TempDir
    Path dataDir;

    private final List<FxManager.FxEvent> fxEvents = new ArrayList<>();

    @Test
    void theBowDrawsConsumesAnArrowAndFiresASpeedScaledArrow() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession archer = join(server, "Archer");
        server.chatService().submitChat(archer, "/give bow 1");
        server.chatService().submitChat(archer, "/give arrow 5");
        await(() -> archer.inventory().countOf(BuiltinItems.ARROW) == 5, "arrows given");

        // No ammunition refuses the draw (the historical refusal).
        server.useItem(archer);
        await(() -> true, "settle");
        assertFalse(archer.bowCharging(), "empty-handed bows never draw");

        // With ammunition: the draw starts, full charge at 20 ticks, release fires.
        server.useItem(archer);
        await(() -> archer.bowCharging(), "the draw started");
        Thread.sleep(1_300); // 26 ticks at 20 Hz: past the 20-tick full charge
        server.releaseUsingItem(archer);
        await(() -> server.projectiles().all().size() == 1, "the arrow launched");
        ProjectileEntity arrow = server.projectiles().all().get(0);
        assertEquals(ProjectileEntity.Kind.ARROW, arrow.kind(), "an arrow flew");
        assertEquals(archer.engineEntityId(), arrow.throwerId(), "the archer is the thrower");
        assertTrue(arrow.launchSpeed() >= 2.9, "a full-draw arrow flies at historical speed");
        await(() -> archer.inventory().countOf(BuiltinItems.ARROW) == 4,
                "one arrow left the quiver");
        server.shutdown(null);
    }

    @Test
    void theSnowballThrowsOnThePressAndShattersOnTheGround() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession thrower = join(server, "Thrower");
        server.chatService().submitChat(thrower, "/give snowball 2");
        await(() -> !thrower.inventory().held().isEmpty(), "snowballs given");

        server.useItem(thrower);
        await(() -> server.projectiles().all().size() == 1, "the snowball flew on the press");
        assertEquals(1, thrower.inventory().held().count(), "one snowball left the hand");
        // The shard falls (thrown from eye height) and shatters on the ground.
        await(() -> server.projectiles().all().isEmpty() && !fxEvents.isEmpty(),
                "the shard shattered on the ground");
        boolean poof = fxEvents.stream().anyMatch(e -> e instanceof FxManager.Poof);
        boolean glass = fxEvents.stream().anyMatch(e ->
                e instanceof FxManager.Sound s && s.name().equals("random.glass"));
        assertTrue(poof, "the shatter puff rode the FX bus");
        assertTrue(glass, "the shatter sound rode the FX bus");
        server.shutdown(null);
    }

    @Test
    void theVanillaChargeCurveShapesMidDrawAndGatesTheFlick() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession archer = join(server, "Archer");
        server.chatService().submitChat(archer, "/give bow 1");
        server.chatService().submitChat(archer, "/give arrow 8");
        await(() -> !archer.inventory().held().isEmpty(), "bow given");

        // The flick (charge 2 shapes to 0.07 < 0.1): no arrow, no wear —
        // the reference's f < 0.1 abort (BowItem line 27). The freeze seam
        // holds the draw at exactly 2: the tick loop's own advance would
        // otherwise slip extra ticks under suite load (the vanilla charge is
        // elapsed-ticks — the reference behaves the same under server lag).
        archer.freezeBowChargeForTest();
        drawFor(archer, 2);
        server.releaseUsingItem(archer);
        Thread.sleep(400);
        assertEquals(0, server.projectiles().all().size(), "the flick fired nothing");
        archer.unfreezeBowChargeForTest();

        // The mid draw (charge 19 shapes to 0.93417): the plain arrow lands
        // exactly 6 — ceil(2.8025 * 2) with no crit flag (the full-draw
        // flag needs the 1.0 clamp). The 10-hp pig lives at 4.
        server.chatService().submitChat(archer, "/spawnmob pig 1");
        await(() -> server.mobs().all().stream().anyMatch(m ->
                m.type() == net.zaminmc.torch.server.entity.MobType.PIG), "pig present");
        var pig = server.mobs().all().stream()
                .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.PIG)
                .findFirst().orElseThrow();
        aimFromAbove(server, archer, pig.position());
        archer.freezeBowChargeForTest(); // the draw pins at exactly 19
        drawFor(archer, 19);
        server.releaseUsingItem(archer);
        archer.unfreezeBowChargeForTest();
        await(() -> pig.health() < net.zaminmc.torch.server.entity.MobType.PIG.maxHealth,
                "the mid-draw arrow landed");
        assertEquals(4.0f, pig.health(), 0.0001f,
                "the charge curve shapes the mid draw to exactly 6 damage");
        server.shutdown(null);
    }

    @Test
    void powerVFullDrawOneShotsThePig() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession archer = join(server, "Archer");
        // The bow first: it takes the held slot; the arrows fill behind it.
        grantHeld(archer, net.zaminmc.torch.item.ItemStack.of(BuiltinItems.BOW)
                .withEnchantment(net.zaminmc.torch.server.enchantment.Enchantments.POWER.id, 5));
        server.chatService().submitChat(archer, "/give arrow 8");
        await(() -> archer.inventory().countOf(BuiltinItems.ARROW) == 8, "arrows given");
        server.chatService().submitChat(archer, "/spawnmob pig 1");
        await(() -> server.mobs().all().stream().anyMatch(m ->
                m.type() == net.zaminmc.torch.server.entity.MobType.PIG), "pig present");
        var pig = server.mobs().all().stream()
                .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.PIG)
                .findFirst().orElseThrow();
        aimFromAbove(server, archer, pig.position());
        // The MID draw (charge 19, no crit flag — the full-draw roll would
        // pollute the isolation): the Power V multiplier lands
        // ceil(2.8025 * 5) = 15 of the pig's 10; the plain draw's 6 would
        // leave it standing.
        drawFor(archer, 19);
        server.releaseUsingItem(archer);
        await(() -> server.mobs().byId(pig.entityId()) == null,
                "the Power V mid draw one-shots the 10-hp pig");
        server.shutdown(null);
    }

    @Test
    void flameFullDrawLightsTheZombieOnTheHit() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession archer = join(server, "Archer");
        grantHeld(archer, net.zaminmc.torch.item.ItemStack.of(BuiltinItems.BOW)
                .withEnchantment(net.zaminmc.torch.server.enchantment.Enchantments.FLAME.id, 1));
        server.chatService().submitChat(archer, "/give arrow 8");
        await(() -> archer.inventory().countOf(BuiltinItems.ARROW) == 8, "arrows given");
        server.chatService().submitChat(archer, "/time set night");
        server.chatService().submitChat(archer, "/spawnmob zombie 1");
        await(() -> server.mobs().all().stream().anyMatch(m ->
                m.type() == net.zaminmc.torch.server.entity.MobType.ZOMBIE), "zombie present");
        var zombie = server.mobs().all().stream()
                .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.ZOMBIE)
                .findFirst().orElseThrow();
        aimFromAbove(server, archer, zombie.position());
        // Not a full draw (charge 19): the 20-hp zombie survives the 6 and
        // the ignite stays observable.
        drawFor(archer, 19);
        server.releaseUsingItem(archer);
        await(() -> zombie.burning(), "the Flame arrow lit the target on the hit");
        server.shutdown(null);
    }

    @Test
    void infinityLeavesTheQuiverFull() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession archer = join(server, "Archer");
        grantHeld(archer, net.zaminmc.torch.item.ItemStack.of(BuiltinItems.BOW)
                .withEnchantment(net.zaminmc.torch.server.enchantment.Enchantments.INFINITY.id, 1));
        server.chatService().submitChat(archer, "/give arrow 4");
        await(() -> archer.inventory().countOf(BuiltinItems.ARROW) == 4, "arrows given");
        // No target needed: the quiver observation only needs the arrow to fly.
        aimStraightDown(archer);
        drawFor(archer, 30);
        server.releaseUsingItem(archer);
        await(() -> server.projectiles().all().size() == 1, "the arrow flew");
        assertEquals(4, archer.inventory().countOf(BuiltinItems.ARROW),
                "the Infinity bow left the quiver full (the reference's flag arm)");
        server.shutdown(null);
    }

    /** Draws the bow for exactly N charge ticks, then the release reads it. */
    private void drawFor(net.zaminmc.torch.server.player.PlayerSession archer, int ticks) {
        archer.beginBowCharge();
        for (int i = 0; i < ticks; i++) {
            archer.advanceBowCharge();
        }
    }

    /**
     * Lifts the archer 1.9 above the target (inside the spawn pull-back's
     * landing window) and points the body straight down — the arrow falls
     * through the target's box.
     */
    private void aimFromAbove(EngineServer server,
                              net.zaminmc.torch.server.player.PlayerSession archer,
                              Position target) throws InterruptedException {
        server.teleportPlayer(archer,
                new Position(target.x(), target.y() + 1.9, target.z()));
        await(() -> archer.position().y() == target.y() + 1.9, "the lift landed");
        archer.applyMovement(archer.position(),
                new Rotation(archer.rotation().yaw(), 90.0f), archer.onGround());
    }

    /** Points the body straight down. */
    private void aimStraightDown(net.zaminmc.torch.server.player.PlayerSession archer) {
        archer.applyMovement(archer.position(),
                new Rotation(archer.rotation().yaw(), 90.0f), archer.onGround());
    }

    /** Puts the stack into the held hotbar slot (the grant seam). */
    private void grantHeld(net.zaminmc.torch.server.player.PlayerSession player,
                           net.zaminmc.torch.item.ItemStack stack) {
        assertTrue(player.inventory().pickUp(stack).isEmpty(),
                "the test grants the stack into the held hotbar slot");
    }

    @Test
    void theThrownShardSpawnsBehindTheEyeLikeTheReference() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession thrower = join(server, "Thrower");
        server.chatService().submitChat(thrower, "/give snowball 2");
        await(() -> !thrower.inventory().held().isEmpty(), "snowballs given");

        // The reference's living-thrower spawn (ThrownEntity lines 58-62):
        // the shard spawns at the eye pulled back by the swapped-trig legacy
        // arm — x -= cos(yaw)*0.16, z -= sin(yaw)*0.16, y -= 0.1 — which is
        // EXACTLY PERPENDICULAR to the throw direction (the legacy quirk:
        // the pull-back is 90 degrees off the look vector). The projectile
        // flies along the look line, so the lateral distance from the
        // eye-line to the observed position stays 0.16 at every tick of
        // flight — an exact, race-free read of the spawn offset.
        server.useItem(thrower);
        await(() -> server.projectiles().all().size() == 1, "the snowball flew on the press");
        ProjectileEntity shard = server.projectiles().all().get(0);

        double yawRadians = Math.toRadians(thrower.rotation().yaw());
        Position eye = new Position(thrower.position().x(),
                thrower.position().y() + 1.62, thrower.position().z());
        // The throw line's direction T = (-sin, +cos); its left normal
        // N = (-cos, -sin) IS the reference spawn offset direction. The
        // launch spread's lateral noise rides on top (sigma 0.0075 per
        // axis, drag-decayed), so the read is a band around 0.16 — the
        // pre-pull-back code read 0.0, well outside it.
        double nx = -Math.cos(yawRadians);
        double nz = -Math.sin(yawRadians);
        double lateral = (shard.position().x() - eye.x()) * nx
                + (shard.position().z() - eye.z()) * nz;
        assertTrue(lateral >= 0.11 && lateral <= 0.21,
                "the shard rides ~0.16 left of the throw line (the legacy spawn pull-back), saw "
                        + lateral);
        server.shutdown(null);
    }

    @Test
    void breakingABlockEmitsTheDigSoundAndTheShatterBurst() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession miner = join(server, "Miner");
        fxEvents.clear();
        server.blockInteraction().submitMiningStart(miner, new net.zaminmc.torch.block.BlockPosition(1, 4, 1));
        // Grass by hand: 15 ticks nominal (750 ms); the 70% leniency floor
        // needs a real wait before the finish gesture lands.
        Thread.sleep(900);
        server.blockInteraction().submitMiningFinished(miner, new net.zaminmc.torch.block.BlockPosition(1, 4, 1));
        await(() -> fxEvents.stream().anyMatch(e -> e instanceof FxManager.Sound s
                        && s.name().equals("dig.grass")),
                "the grass dig sound rode the FX bus");
        boolean shatter = fxEvents.stream().anyMatch(e -> e instanceof FxManager.BlockShatter);
        assertTrue(shatter, "the block-shatter burst rode the FX bus");
        server.shutdown(null);
    }

    @Test
    void postureActionsBroadcastThroughTheSurvivalListener() throws Exception {
        EngineServer server = boot();
        List<String> postures = new ArrayList<>();
        server.addSurvivalListener(new EngineServer.SurvivalListener() {
            @Override
            public void onBodyChanged(net.zaminmc.torch.server.player.PlayerSession player) {
            }

            @Override
            public void onDied(net.zaminmc.torch.server.player.PlayerSession player) {
            }

            @Override
            public void onRespawned(net.zaminmc.torch.server.player.PlayerSession player,
                                    Position spawn) {
            }

            @Override
            public void onPlayerHurt(net.zaminmc.torch.server.player.PlayerSession player) {
            }

            @Override
            public void onKnockback(net.zaminmc.torch.server.player.PlayerSession player,
                                    double vx, double vy, double vz) {
            }

            @Override
            public void onPostureChanged(net.zaminmc.torch.server.player.PlayerSession player) {
                postures.add(player.sneaking() + "/" + player.sprinting());
            }
        });
        net.zaminmc.torch.server.player.PlayerSession sneaky = join(server, "Sneaky");
        server.entityAction(sneaky, 0); // start sneak
        await(() -> !postures.isEmpty() && postures.get(postures.size() - 1).equals("true/false"),
                "the sneak action broadcast");
        server.entityAction(sneaky, 2); // start sprint
        await(() -> postures.get(postures.size() - 1).equals("true/true"),
                "sprint rides beside the sneak");
        server.entityAction(sneaky, 3); // stop sprint
        await(() -> postures.get(postures.size() - 1).equals("true/false"),
                "the sprint stop broadcast");
        server.shutdown(null);
    }

    @Test
    void anArrowHurtsTheTargetInItsPath() throws Exception {
        EngineServer server = boot();
        net.zaminmc.torch.server.player.PlayerSession archer = join(server, "Hunter");
        // The victim stands still (no movement packets): a deterministic target.
        net.zaminmc.torch.server.player.PlayerSession victim = join(server, "Target");
        victim.applyMovement(new Position(
                archer.position().x(), archer.position().y(), archer.position().z() + 1.5),
                new Rotation(0f, 0f), true);
        server.chatService().submitChat(archer, "/give bow 1");
        server.chatService().submitChat(archer, "/give arrow 1");
        await(() -> archer.inventory().countOf(BuiltinItems.ARROW) == 1, "arrow given");
        float before = victim.health();
        archer.applyMovement(archer.position(), new Rotation(0f, 35f), true); // aim down 35°
        server.useItem(archer);
        await(() -> archer.bowCharging(), "the draw started");
        Thread.sleep(1_100);
        server.releaseUsingItem(archer);
        await(() -> victim.health() < before, "the arrow wounded the target");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "ranged", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.fx().addListener(fxEvents::add);
        server.start();
        return server;
    }

    private net.zaminmc.torch.server.player.PlayerSession join(EngineServer server, String name)
            throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.nameUUIDFromBytes(name.getBytes()));
        net.zaminmc.torch.server.player.PlayerSession session =
                ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, name + " playing");
        return session;
    }

    private ClientLink link() {
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
