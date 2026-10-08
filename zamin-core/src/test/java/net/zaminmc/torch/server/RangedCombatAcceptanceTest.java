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
