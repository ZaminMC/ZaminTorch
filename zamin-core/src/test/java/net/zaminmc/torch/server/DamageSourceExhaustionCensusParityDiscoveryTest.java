package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE DAMAGE-SOURCE EXHAUSTION CENSUS (the 2026-11-08 wave — the law the
 * pearl seat's root cause opened): the reference's {@code DamageSource}
 * carries {@code exhaustion = 0.3F} by default with EXACTLY TWO zeroing
 * setters ({@code setBypassesArmor()} and {@code setUnblockable()},
 * DamageSource lines 122-137) — and the creative immunity reads
 * {@code abilities.invulnerable && !source.isOutOfWorld()}
 * (PlayerEntity.takeDamage line 773): the VOID ALONE pierces an
 * invulnerable body. The census's three finds, each seated:
 * (1) the guardian's spike reflect — {@code DamageSource.thorns(this)}
 * (GuardianEntity.takeDamage line 426) is a PLAIN EntityDamageSource
 * ({@code setThorns() + isMagic()}, neither zeroes) — the wound rides
 * the attacker's armor ladder, charges the ordinary 0.3, and an
 * invulnerable attacker refuses it;
 * (2) the potion-effect family (poison, wither, the harming potion —
 * the MAGIC/WITHER sources) never pierces the creative gate;
 * (3) the guardian beam's magic half (the indirect-magic source) —
 * same gate.
 */
class DamageSourceExhaustionCensusParityDiscoveryTest {

    private EngineServer server;
    private Path dataDir;

    @BeforeEach
    void boot() throws Exception {
        dataDir = Files.createTempDirectory("dmgcensus");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "dmgcensus", "it", 20, 2, 20,
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
    void theGuardiansSpikeWoundChargesTheOrdinaryDamageTaken() throws Exception {
        PlayerSession attacker = join("Puncher");
        EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");

        // A still guardian: the spike verdict's own gate.
        AtomicReference<MobEntity> guardian = new AtomicReference<>();
        server.ticker().submit(() -> guardian.set(server.mobs().spawnAt(
                MobType.GUARDIAN, new Position(attacker.position().x() + 1.0,
                attacker.position().y(), attacker.position().z()), 0)));
        EngineAwaits.awaitTicks(server.world(), 1, "the guardian spawns");
        assertTrue(guardian.get() != null && !guardian.get().dead(),
                "the guardian spawned still");

        // The bare-handed punch (no held item: no crit, no enchant arms —
        // the plain melee family). The spike wounds the ATTACKER through
        // the thorns source's own law.
        float healthBefore = attacker.health();
        float exhaustionBefore = attacker.exhaustion();
        server.attackEntity(attacker, guardian.get().entityId());
        await(() -> attacker.health() < healthBefore, "the spike wounded the attacker");
        // The wound's own shape: the flat 2.0 through the bare body (no
        // armor pieces to shave), and the THORNS source's charge — the
        // ordinary damage-taken 0.3 (EntityPlayer.attackEntityFrom).
        assertEquals(healthBefore - 2.0f, attacker.health(), 1.0E-4f,
                "the still guardian's spike reflect lands the flat 2.0 "
                        + "(GuardianEntity.takeDamage line 426)");
        // TWO charges ride the punch: the swing's own attack exhaustion
        // (PlayerEntity.attack's 0.3) and the thorns source's damage-taken
        // charge (the ordinary 0.3 — setThorns + isMagic, neither zeroes).
        assertEquals(exhaustionBefore + 0.6f, attacker.exhaustion(), 1.0E-4f,
                "the thorns source charges the ordinary damage-taken exhaustion "
                        + "(DamageSource.thorns — setThorns + isMagic, neither zeroes)");
    }

    @Test
    void thePotionEffectsNeverPierceTheCreativeGate() throws Exception {
        PlayerSession body = join("Taster");
        EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
        float survivalHealth = body.health();

        // The creative switch: the engine's own gamemode surface.
        server.ticker().submit(() -> body.setGamemode(
                net.zaminmc.torch.GameMode.CREATIVE));
        EngineAwaits.awaitTicks(server.world(), 1, "the mode lands");
        assertEquals(net.zaminmc.torch.GameMode.CREATIVE, body.gamemode(),
                "the body stands creative");

        // The poison (the reference's StatusEffect line 147: takeDamage(
        // DamageSource.MAGIC, 1.0)): the MAGIC source carries NO
        // out-of-world flag, so the invulnerable body refuses it whole —
        // the health never moves, exactly the reference's takeDamage head
        // (PlayerEntity line 773's !source.isOutOfWorld() gate).
        server.ticker().submit(() -> server.potions().add(body, new StatusEffectInstance(
                StatusEffects.POISON, 40, 0)));
        EngineAwaits.awaitTicks(server.world(), 40, "the poison window");
        assertEquals(survivalHealth, body.health(), 1.0E-4f,
                "the creative body refuses the poison's magic wound "
                        + "(the void alone pierces an invulnerable body)");

        // The wither (StatusEffect line 150: takeDamage(DamageSource
        // .WITHER, 1.0)) — the same refusal, the bypass-armor family's
        // own member.
        server.ticker().submit(() -> server.potions().add(body, new StatusEffectInstance(
                StatusEffects.WITHER, 40, 0)));
        EngineAwaits.awaitTicks(server.world(), 40, "the wither window");
        assertEquals(survivalHealth, body.health(), 1.0E-4f,
                "the creative body refuses the wither's wound too");
    }
}
