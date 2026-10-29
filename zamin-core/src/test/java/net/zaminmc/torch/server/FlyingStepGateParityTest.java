package net.zaminmc.torch.server;

import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The flying player's step gate (the landed step-glow arms' review —
 * the fifteenth pass's re-adjudication tail):
 *
 * <p>The reference's steps band gates on {@code makesSteps()} (Entity
 * line 715), and the player OVERRIDES it:
 * {@code return !this.abilities.flying} (PlayerEntity lines
 * 1568-1570). A creative body flying at ground contact reports
 * onGround moves, but the band never runs — the flying body never
 * triggers onSteppedOn, whatever its feet touch: a flying player
 * standing on redstone ore NEVER lights it in vanilla. The engine's
 * per-tick standing consult (tickStepOn — the proposal-driven walk's
 * own arm) carried only the onGround/sneak/vehicle gates; the flying
 * bit is the client's own abilities report (the 0x13 packet's flag,
 * PlayerSession.flying).</p>
 */
class FlyingStepGateParityTest {

    @TempDir
    Path dataDir;

    @Test
    void theFlyingPlayerNeverLightsTheOre() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> {
                world.setBlock(new BlockPosition(4, 4, 0),
                        BuiltinBlocks.REDSTONE_ORE);
                world.setBlock(new BlockPosition(12, 4, 0),
                        BuiltinBlocks.REDSTONE_ORE);
            });
            await(() -> world.getBlock(new BlockPosition(4, 4, 0))
                            .equals(BuiltinBlocks.REDSTONE_ORE)
                    && world.getBlock(new BlockPosition(12, 4, 0))
                            .equals(BuiltinBlocks.REDSTONE_ORE),
                    "the two ores seeded");

            // The control first (the default makesSteps TRUE with the
            // flying bit clear): the walking body lights the ore
            // underfoot — the seat's own guard against a vacuous
            // negative.
            PlayerSession walker = join(server, "Walker");
            server.teleportPlayer(walker, new Position(4.5, 5.0, 0.5));
            await(() -> walker.position().x() == 4.5
                    && walker.position().y() == 5.0, "the walker stood");
            server.movementProposal(walker, new Position(4.5, 5.0, 0.5),
                    Rotation.ZERO, true);
            await(() -> world.getBlock(new BlockPosition(4, 4, 0))
                            .equals(BuiltinBlocks.LIT_REDSTONE_ORE),
                    "the walking control lit its ore");

            // The flyer (PlayerEntity.makesSteps = !abilities.flying):
            // a creative body flying at ground contact reports onGround
            // moves, but the band never consults the ore — the plain
            // form stays plain.
            PlayerSession flyer = join(server, "Flyer");
            server.teleportPlayer(flyer, new Position(12.5, 5.0, 0.5));
            await(() -> flyer.position().x() == 12.5
                    && flyer.position().y() == 5.0, "the flyer stood");
            server.ticker().submit(() -> flyer.setFlying(true));
            await(() -> flyer.flying(), "the flying flag set");
            server.movementProposal(flyer, new Position(12.5, 5.0, 0.5),
                    Rotation.ZERO, true);
            Thread.sleep(600);
            assertEquals(BuiltinBlocks.REDSTONE_ORE,
                    world.getBlock(new BlockPosition(12, 4, 0)),
                    "the flying body never lights the ore"
                            + " (makesSteps = !abilities.flying)");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "flygate",
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
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
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timeout: " + description);
            }
            Thread.sleep(25);
        }
    }
}
