package net.zaminmc.torch.launcher.console;

import java.util.List;
import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.server.EngineServer;

/**
 * The engine binding of {@link ConsoleTarget}: the launcher's console talks
 * to the live {@link EngineServer} exclusively through these five safe
 * surfaces — the idempotent shutdown (with the network teardown callback),
 * the save barrier, the state line, the existing console command route, and
 * the command-name snapshot. No world/player/entity reference crosses into
 * the console (architecture §30.3).
 */
final public class EngineConsoleTarget implements ConsoleTarget {

    private final EngineServer server;
    private final Runnable networkShutdown;

    public EngineConsoleTarget(EngineServer server, Runnable networkShutdown) {
        this.server = server;
        this.networkShutdown = networkShutdown;
    }

    @Override
    public void requestStop() {
        // Direct path: stop must work even if the tick loop is wedged (the
        // same call the console 'stop' command and the shutdown hook make).
        server.shutdown(networkShutdown);
    }

    @Override
    public void saveAllNow() {
        server.saveAllNow();
    }

    @Override
    public String stateLine() {
        return "Server state: " + server.state() + ", players=" + server.players().size();
    }

    @Override
    public void consoleCommand(String raw) {
        server.consoleCommand(raw);
    }

    @Override
    public List<String> commandNames() {
        try {
            var commands = server.commands();
            return commands == null ? List.of() : commands.commandNames();
        } catch (RuntimeException e) {
            return List.of(); // the engine is mid-boot or shutting down: no names
        }
    }
}
