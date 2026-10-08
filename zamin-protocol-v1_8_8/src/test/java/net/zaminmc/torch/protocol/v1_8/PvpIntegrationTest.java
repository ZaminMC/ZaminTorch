package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.entity.Player;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PvP over the wire: two live clients at spawn, the attacker swings with Use
 * Entity (attack) at the victim's OBSERVER-LOCAL entity id, and the victim's
 * client sees the historical sequence — hurt status flash, health re-sync,
 * knockback velocity — while invulnerability frames absorb weaker hits. A
 * lethal swing runs the death-and-respawn path end to end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PvpIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 2_000; // the scripted clients answer only when the test drives it
    }

    private static final long HURT_WINDOW_MS = 700; // i-frames: 10 ticks nominal (500ms)

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            PlayerSession session = server.playerRegistry().byName(name).orElse(null);
            if (session != null) {
                return session;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Player " + name + " never registered");
    }

    @Test
    void meleeRidesTheWireAndKillsThroughInvulnerabilityFrames() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {

            // --- both join at spawn: one block apart, melee reach for sure ---
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            PlayerSession aliceSession = awaitPlayer("Alice");

            bob.sendHandshake(47, 2);
            bob.sendLoginStart("Bob");
            bob.readLoginSuccess();
            bob.readUntilPositionAndLook();
            PlayerSession bobSession = awaitPlayer("Bob");
            awaitCondition(() -> bobSession.state() == PlayerState.PLAYING
                    && aliceSession.state() == PlayerState.PLAYING, "both PLAYING");

            // Each client learns the other's observer-local entity id.
            int aliceSeesBob = alice.readNamedSpawn(5_000)[0];
            bob.readNamedSpawn(5_000); // Bob tracks Alice symmetrically

            // --- the first swing: fist damage 1, Bob drops to 19 ---
            alice.sendUseEntity(aliceSeesBob, Protocol18.USE_ENTITY_ATTACK);
            int[] hurt = bob.readEntityStatus(5_000);
            assertEquals(Protocol18.ENTITY_STATUS_HURT, hurt[1]);
            int[] knockback = bob.readEntityVelocity(5_000);
            assertTrue(Math.abs(knockback[2]) > 1000 || Math.abs(knockback[4]) > 1000,
                    "the knockback rides to the victim (0.4 horizontal)");
            double[] health = bob.readUpdateHealth(5_000);
            assertEquals(19.0, health[0], 0.01, "the first fist hit takes one heart");

            // --- the immediate second swing is absorbed by the hurt window ---
            alice.sendUseEntity(aliceSeesBob, Protocol18.USE_ENTITY_ATTACK);
            Thread.sleep(HURT_WINDOW_MS);
            // --- the third swing lands (out-damaging / past the window): 18 ---
            alice.sendUseEntity(aliceSeesBob, Protocol18.USE_ENTITY_ATTACK);
            bob.readEntityStatus(5_000);
            bob.readEntityVelocity(5_000);
            health = bob.readUpdateHealth(5_000);
            assertEquals(18.0, health[0], 0.01,
                    "the absorbed hit must not double-dip (no 17 here)");

            // --- give Alice a sword for a fast, decisive kill (7 damage) ---
            alice.sendChat("/give diamond_sword 1");
            alice.readWindowItems(5_000); // the authoritative inventory sync

            // Sword damage 7: 18 -> 11 -> 4 -> dead. Space the swings past the
            // hurt window; wire order per hit: status, velocity, health sync.
            for (int swing = 0; swing < 2; swing++) {
                alice.sendUseEntity(aliceSeesBob, Protocol18.USE_ENTITY_ATTACK);
                bob.readEntityStatus(5_000);
                bob.readEntityVelocity(5_000);
                bob.readUpdateHealth(5_000);
                Thread.sleep(HURT_WINDOW_MS);
            }
            alice.sendUseEntity(aliceSeesBob, Protocol18.USE_ENTITY_ATTACK);
            int[] death = bob.readCombatEvent(5_000);
            assertEquals(Protocol18.COMBAT_EVENT_ENTITY_DIED, death[0]);
            assertTrue(bobSession.dead(), "the engine marked the body dead");

            // --- the historical respawn: request, body reset, wire re-anchor ---
            bob.sendClientStatus(0);
            bob.readRespawn(5_000);
            bob.readUpdateHealth(5_000);
            int[] inventory = bob.readWindowItems(5_000);
            assertEquals(Protocol18.INVENTORY_WINDOW_SLOTS, inventory[1]);
            awaitCondition(() -> bobSession.health() == PlayerSession.MAX_HEALTH,
                    "the respawned body is back at full health");
        }
    }
}
