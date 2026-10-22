package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The particle display arms over the real wire (Slice 30a): the ambient
 * breeding-heart clock — AnimalEntity.mobTick's own {@code addParticle(HEART)}
 * consult, 1:1. One HEART particle (id 34) every 10th love tick at the
 * server-chosen position inside the body's footprint, riding World Particles
 * in the count-0 shape (one particle at the exact position with the offsets
 * as its velocity — the ServerWorld.addParticle form), distinct from the
 * event-18 love burst the feeding itself triggers.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParticleDisplayAcceptanceTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    @Test
    void theAmbientLoveHeartClockEmitsExactPositionParticles() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("HeartWatcher");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // Pen the cow within the feeding reach (walls two high at
            // Chebyshev 4, sealed below and above — the horse pattern).
            var anchor = server.playerRegistry().byName("HeartWatcher").orElseThrow().position();
            int ax = (int) Math.floor(anchor.x());
            int ay = (int) Math.floor(anchor.y());
            int az = (int) Math.floor(anchor.z());
            var wall = net.zaminmc.torch.server.block.BuiltinBlocks.COBBLESTONE;
            server.ticker().submit(() -> {
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != 4) {
                            continue;
                        }
                        for (int dy = -2; dy <= 3; dy++) {
                            server.world().setBlock(
                                    new net.zaminmc.torch.block.BlockPosition(ax + dx, ay + dy, az + dz),
                                    wall);
                        }
                    }
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(ax + 4, ay, az)).equals(wall),
                    "pen built");
            client.sendChat("/spawnmob cow 1");

            // Resolve the commanded cow server-side (the natural population
            // rolls cows too — the commanded one lands within 3 blocks).
            awaitCondition(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == net.zaminmc.torch.server.entity.MobType.COW
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0),
                    "the commanded cow registered near the anchor");
            var cow = server.mobs().all().stream()
                    .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.COW
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0)
                    .findFirst().orElseThrow();
            int cowId = cow.entityId();

            // Wheat in hand, one use: the cow enters love (the feeding arm
            // consumes the stack, the event-18 burst rides Entity Status).
            client.sendChat("/give minecraft:wheat 8");
            EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
            client.sendUseEntity(cowId, Protocol18.USE_ENTITY_INTERACT);
            awaitCondition(() -> server.mobs().byId(cowId).isInLove(),
                    "the wheat fed the cow into love");

            // The first ambient heart lands within 10 love ticks (0.5 s):
            // World Particles with the HEART id in the count-0 shape.
            int[] first = client.readWorldParticles(Protocol18.PARTICLE_HEART, 10_000);
            assertEquals(Protocol18.PARTICLE_HEART, first[0], "the heart id rides the packet");
            assertEquals(0, first[4], "count 0 — the exact-position single-particle shape");

            // The reference's footprint math (AnimalEntity.mobTick lines
            // 44-61): x/z inside width of the body, y inside 0.5..0.5+height.
            // The helper returns 1/32-block ints; compare at that grain.
            var body = server.mobs().byId(cowId);
            double w = body.type().width;
            double h = body.type().height;
            double bx = body.position().x();
            double by = body.position().y();
            double bz = body.position().z();
            double px = first[1] / 32.0;
            double py = first[2] / 32.0;
            double pz = first[3] / 32.0;
            assertTrue(px >= bx - w - 0.06 && px <= bx + w + 0.06,
                    "heart x inside the footprint: " + px + " vs " + bx + " +- " + w);
            assertTrue(pz >= bz - w - 0.06 && pz <= bz + w + 0.06,
                    "heart z inside the footprint: " + pz + " vs " + bz + " +- " + w);
            assertTrue(py >= by + 0.5 - 0.06 && py <= by + 0.5 + h + 0.06,
                    "heart y inside the body band: " + py + " vs " + by + "+0.5.." + (by + 0.5 + h));

            // The clock repeats while the 600-tick window drains: a second
            // heart follows within the next 10 ticks.
            int[] second = client.readWorldParticles(Protocol18.PARTICLE_HEART, 10_000);
            assertEquals(0, second[4], "the repeat also rides count 0");
        }
    }
}
