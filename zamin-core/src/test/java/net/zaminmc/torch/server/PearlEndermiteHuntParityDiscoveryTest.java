package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PEARL'S ENDERMITE HUNT (the 2026-11-08 CI-forensics row, seated):
 * the pearl's 5% passenger hunts the HOVERING thrower — EndermiteEntity's
 * own goal set (MeleeAttackGoal(this, PlayerEntity.class, 1.0, false) at
 * band 2 over ActiveTargetGoal&lt;&gt;(this, PlayerEntity.class, true)) with
 * the melee reach consult (MeleeAttackGoal.attack's d0 = width*2*2 +
 * target.width = 2.2 against the target's box minY) covering the teleport
 * landing's 1.62 gap. The endermite spawns at the thrower's PRE-teleport
 * feet (EnderPearlEntity.onCollision line 52), the block arm lands the
 * thrower hovering at the pearl's own origin (the pre-move from = the eye
 * height), and the first bite charges the ordinary melee damage-taken
 * exhaustion (EntityPlayer.attackEntityFrom's 0.3F). The rig drives the
 * exact shape with the 5% roll controlled away (the endermite spawned
 * through the same surface the sink uses).
 */
class PearlEndermiteHuntParityDiscoveryTest {

    private EngineServer server;
    private Path dataDir;

    @BeforeEach
    void boot() throws Exception {
        dataDir = Files.createTempDirectory("mitehunt");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "mitehunt", "it", 20, 2, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
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

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
    }

    @Test
    void thePearlsPassengerHuntsTheHoveringThrower() throws Exception {
        PlayerSession body = join("Pearlist");
        EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
        double x = body.position().x();
        double y = body.position().y();
        double z = body.position().z();

        // The pearl's passenger at the thrower's PRE-teleport feet — the
        // same surface the sink's 5% roll spawns through (the roll alone
        // controlled away; the hunt itself is the seat's law).
        AtomicReference<MobEntity> mite = new AtomicReference<>();
        server.ticker().submit(() -> mite.set(server.mobs().spawnAt(
                MobType.ENDERMITE, new Position(x, y, z), 0)));
        EngineAwaits.awaitTicks(server.world(), 1, "the passenger spawns");
        assertNotNull(mite.get(), "the endermite spawned at the thrower's feet");

        // The pearl's own landing shape — the sink's carry sequence
        // whole (the phantom-jump-free form): the block arm's position
        // write to the pearl's pre-move origin (the eye height,
        // hovering — the test body carries no client physics; the
        // reference's player is carried by the same setPositionAndUpdate)
        // followed by the delta scratch's reset, exactly the sink's own
        // order.
        server.ticker().submit(() -> {
            body.applyMovement(new Position(x, y + 1.62, z),
                    new Rotation(0.0f, 0.0f), false);
            body.resetBodyDelta();
        });
        EngineAwaits.awaitTicks(server.world(), 1, "the thrower hovers");
        assertEquals(y + 1.62, body.position().y(), 1.0E-9,
                "the thrower hovers at the pearl's origin height");

        // THE HUNT: the endermite's own goal set locks the hovering player
        // (the hunt box's 4-vertical band covering the 1.62 gap, the
        // visibility ray clear through open air) and the melee reach
        // consult (d0 = width*2*2 + target.width = 2.2 against the box
        // minY) bites from BELOW — the health drop and the ordinary
        // melee damage-taken charge (0.3F) the CI forensics read.
        float healthBefore = body.health();
        float exhaustionBefore = body.exhaustion();
        // The first bite's landing tick varies with the selector's setup
        // pass phase (the 3-tick cadence against the spawn tick); the
        // poll waits for the FIRST health drop — the 20-tick swing
        // cooldown keeps the second bite a full second away, so the read
        // that follows sees exactly ONE charge.
        await(() -> body.health() < healthBefore, "the hovering thrower bitten");
        assertEquals(exhaustionBefore + 0.3f, body.exhaustion(), 1.0E-4f,
                "the bite charges the ordinary melee damage-taken exhaustion "
                        + "(EntityPlayer.attackEntityFrom's 0.3F)");
    }
}
