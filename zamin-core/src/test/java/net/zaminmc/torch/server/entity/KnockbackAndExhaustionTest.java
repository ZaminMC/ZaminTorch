package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla knockback recipe (reference/1.8.8 LivingEntity.applyKnockback)
 * and the exhaustion ledger's hard rules: the half-then-impulse velocity
 * with the 0.4 rise cap (chained hits decay exactly as historical), the
 * 40.0 exhaustion ceiling, and the per-source damage exhaustion charges.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnockbackAndExhaustionTest {

    private FrozenBlockRegistry registry;
    private EngineWorld world;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("test", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(0, 0));
    }

    private MobEntity zombieAt(double x, double y, double z) {
        return new MobEntity(1, MobType.ZOMBIE, new Position(x, y, z), new Random(1),
                new MobEntity.WorldQuery() {
                    @Override public boolean isSolid(double px, double py, double pz) {
                        return py < 4.0;
                    }

                    @Override public Position nearestPlayer(double px, double py, double pz,
                                                            double range) {
                        return null;
                    }
                });
    }

    @Test
    void knockbackFromRestIsThePlainImpulse() {
        MobEntity zombie = zombieAt(2.0, 4.0, 2.0);
        // Yaw 0 faces +Z: the victim in front is pushed further +Z (away).
        zombie.knockbackFrom(0.0);
        assertEquals(0.0, zombie.velocityX(), 1.0E-9, "no sideways component");
        assertEquals(0.4, zombie.velocityY(), 1.0E-9, "the rise from rest");
        assertEquals(0.4, zombie.velocityZ(), 1.0E-9,
                "the away-impulse from the resting zero motion");
    }

    @Test
    void chainedKnockbackHalvesTheResidualMotion() {
        MobEntity zombie = zombieAt(2.0, 4.0, 2.0);
        // The first hit leaves the residual (the away push at yaw 90: -X).
        zombie.knockbackFrom(90.0);
        double firstX = zombie.velocityX();
        double firstY = zombie.velocityY();
        double firstZ = zombie.velocityZ();
        assertEquals(-0.4, firstX, 1.0E-9, "yaw 90 faces -X: the push rides -X");
        assertEquals(0.4, firstY, 1.0E-9);
        // The second hit halves the residual before the impulse (the vanilla
        // chained-hit decay).
        zombie.knockbackFrom(90.0);
        assertEquals(-0.4 / 2.0 - 0.4, zombie.velocityX(), 1.0E-9,
                "the residual halved, then the impulse");
        assertEquals(0.4, zombie.velocityY(), 1.0E-9,
                "the rise caps at 0.4 (0.2 + 0.4 clips to 0.4)");
        assertEquals(firstZ / 2.0, zombie.velocityZ(), 1.0E-9);
    }

    @Test
    void theRiseCapOnlyClipsAbovePointFour() {
        MobEntity zombie = zombieAt(2.0, 4.0, 2.0);
        // A body already falling fast keeps falling (the cap never lifts).
        zombie.setVelocityForTest(-0.0, -2.0, 0.0);
        zombie.knockbackFrom(0.0);
        assertEquals(-2.0 / 2.0 + 0.4, zombie.velocityY(), 1.0E-9,
                "a downward motion survives the recipe (min(-0.6, 0.4) = -0.6)");
    }

    private PlayerSession freshSession() {
        return new PlayerSession(java.util.UUID.randomUUID(), "body", stubLink());
    }

    private static net.zaminmc.torch.server.net.ClientLink stubLink() {
        return new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
    }

    @Test
    void exhaustionCapsAtTheVanillaCeiling() {
        PlayerSession session = freshSession();
        session.addExhaustion(50.0f);
        assertEquals(40.0f, session.exhaustion(), 1.0E-6,
                "the vanilla Math.min(e + amount, 40.0) ceiling");
        session.addExhaustion(0.025f); // a break past the cap stays capped
        assertEquals(40.0f, session.exhaustion(), 1.0E-6);
    }

    @Test
    void thePerMeterExhaustionRatesMatchTheLedger() {
        PlayerSession session = freshSession();
        // The ledger constants the engine charges: walk 0.01/m, sprint
        // 0.1/m, swim 0.015/m, jump 0.2 (0.8 sprinting), break 0.025,
        // attack 0.3, damage 0.3, regen 3.0.
        session.addExhaustion(0.01f * 100 * 0.01f); // one walked meter in cm units
        assertEquals(0.01f, session.exhaustion(), 1.0E-6);
        session.addExhaustion(0.099999994f * 100 * 0.01f); // one sprinted meter
        assertEquals(0.11f, session.exhaustion(), 1.0E-3);
        session.addExhaustion(0.2f); // one jump
        session.addExhaustion(0.025f); // one block broken
        session.addExhaustion(0.3f); // one swing
        session.addExhaustion(3.0f); // one regen heart
        assertEquals(0.01f + 0.1f + 0.2f + 0.025f + 0.3f + 3.0f, session.exhaustion(),
                1.0E-3, "the charges accumulate under the cap");
    }
}
