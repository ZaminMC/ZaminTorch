package net.zaminmc.torch.server;

import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSessionTest {

    private static ClientLink deadLink() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return false;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    @Test
    void lifecycleGoesThroughValidOrder() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), "Steve", deadLink());
        assertEquals(PlayerState.CONNECTING, session.state());
        session.authenticate();
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("t", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4), Thread.currentThread());
        session.beginJoin(world, new Position(0.5, 5, 0.5));
        assertEquals(PlayerState.JOINING, session.state());
        session.markPlaying();
        assertEquals(PlayerState.PLAYING, session.state());
        session.markDisconnecting();
        session.markDisconnected();
        assertEquals(PlayerState.DISCONNECTED, session.state());
    }

    @Test
    void invalidTransitionsAreRejected() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), "Alex", deadLink());
        // Cannot go straight to playing from connecting.
        assertThrows(IllegalStateException.class, session::markPlaying);
    }

    @Test
    void movementAppliesValidProposals() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), "Kim", deadLink());
        session.applyMovement(new Position(1, 64, 2), new Rotation(90f, -10f), false);
        assertEquals(new Position(1, 64, 2), session.position());
        assertEquals(new Rotation(90f, -10f), session.rotation());
        assertFalse(session.onGround());
    }

    @Test
    void movementRejectsNonFiniteValues() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), "Bo", deadLink());
        assertThrows(IllegalArgumentException.class, () ->
                session.applyMovement(new Position(Double.NaN, 64, 0), Rotation.ZERO, true));
        assertThrows(IllegalArgumentException.class, () ->
                session.applyMovement(Position.ZERO, new Rotation(Float.NEGATIVE_INFINITY, 0f), true));
    }

    @Test
    void disconnectIsIdempotent() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), "Rey", deadLink());
        session.markDisconnected();
        session.markDisconnected(); // must not throw
        assertEquals(PlayerState.DISCONNECTED, session.state());
    }

    @Test
    void registryPreventsDuplicateIdentity() {
        EngineServer server = new EngineServer(EngineConfig.defaults());
        PlayerRegistry registry = server.playerRegistry();
        PlayerSession first = new PlayerSession(UUID.randomUUID(), "Steve", deadLink());
        registry.register(first);
        assertTrue(registry.isNameTaken("Steve"));
        assertEquals(1, registry.size());

        PlayerSession sameName = new PlayerSession(UUID.randomUUID(), "Steve", deadLink());
        assertThrows(IllegalStateException.class, () -> registry.register(sameName));
        // Failed registration must not leave ghost state (§805).
        assertEquals(1, registry.size());
        assertEquals(1, registry.all().size());
    }

    @Test
    void serverBridgeRejectsJoinWhenNotRunning() {
        EngineServer server = new EngineServer(EngineConfig.defaults());
        EngineBridge.JoinResult result =
                server.joinRequest(deadLink(), "Steve", UUID.randomUUID());
        assertTrue(result instanceof EngineBridge.Rejected, "must reject while not RUNNING");
    }

}
