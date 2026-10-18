package net.zaminmc.torch.launcher.console;

import java.util.List;

/**
 * What the console talks to (architecture §30.3 "one command path"): the
 * engine surface the terminal adapter is allowed to touch — lifecycle stop,
 * the save barrier, a state line, the EXISTING console command route, and
 * the command-name snapshot for completion. Nothing here hands the console
 * a live world/player/entity reference (§30.3's ownership boundary), and
 * every method is safe to call from the console thread because the real
 * binding routes mutations through the engine's own submission lane.
 */
public interface ConsoleTarget {

    /** The idempotent stop: console 'stop', Ctrl+C, and signals all land here. */
    void requestStop();

    /** The explicit save barrier. */
    void saveAllNow();

    /** The 'state' status line. */
    String stateLine();

    /** The existing console command route (the dispatcher the chat commands use). */
    void consoleCommand(String raw);

    /**
     * The completion snapshot: an immutable, safely published list of
     * registered command names (the console sees them all — operator level 4).
     */
    List<String> commandNames();
}
