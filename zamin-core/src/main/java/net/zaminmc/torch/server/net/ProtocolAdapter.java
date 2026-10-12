package net.zaminmc.torch.server.net;

import net.zaminmc.torch.server.EngineServer;

/**
 * A version protocol adapter. Implementations own the wire for their Minecraft
 * protocol version and connect clients to the engine through {@link EngineBridge}.
 *
 * <p>The engine never sees transport objects; the adapter never implements gameplay
 * policy. Lifecycle: start(server) once, then shutdown() exactly once.</p>
 */
public interface ProtocolAdapter {

    /** Human-readable name for diagnostics, for example {@code "minecraft-1.8.8"}. */
    String protocolName();

    /** Binds and starts accepting clients. Called once, after the engine is RUNNING. */
    void start(EngineServer server) throws Exception;

    /** Stops accepting clients, disconnects active ones, releases network resources. */
    void shutdown();
}
