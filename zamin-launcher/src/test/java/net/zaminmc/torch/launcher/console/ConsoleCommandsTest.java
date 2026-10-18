package net.zaminmc.torch.launcher.console;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ONE command path (§30.4.1): the built-ins dispatch here, everything
 * else rides the engine's existing console route, and permission/refusal
 * behavior is the target's own — the console adds no registry, no bypass.
 */
class ConsoleCommandsTest {

    /** The recording target: proves the route without booting the engine. */
    private static class RecordingTarget implements ConsoleTarget {
        final List<String> stops = new ArrayList<>();
        int saves;
        int stateReads;
        final List<String> commands = new ArrayList<>();

        @Override public void requestStop() {
            stops.add("stop");
        }

        @Override public void saveAllNow() {
            saves++;
        }

        @Override public String stateLine() {
            stateReads++;
            return "Server state: RUNNING, players=0";
        }

        @Override public void consoleCommand(String raw) {
            commands.add(raw);
        }

        @Override public List<String> commandNames() {
            return List.of("help");
        }
    }

    @Test
    void stopAndShutdownConvergeOnTheIdempotentStopRoute() {
        RecordingTarget target = new RecordingTarget();
        ConsoleCommands dispatcher = new ConsoleCommands(target);
        assertEquals(ConsoleCommands.Result.SHUTDOWN_REQUESTED, dispatcher.dispatch("stop"));
        assertEquals(ConsoleCommands.Result.SHUTDOWN_REQUESTED, dispatcher.dispatch("SHUTDOWN"));
        assertEquals(2, target.stops.size(), "both built-ins reach the same stop route");
        assertTrue(target.commands.isEmpty());
    }

    @Test
    void saveAndStateAreBuiltIns() {
        RecordingTarget target = new RecordingTarget();
        ConsoleCommands dispatcher = new ConsoleCommands(target);
        assertEquals(ConsoleCommands.Result.CONTINUE, dispatcher.dispatch("save"));
        assertEquals(1, target.saves);
        assertEquals(ConsoleCommands.Result.CONTINUE, dispatcher.dispatch("state"));
        assertEquals(1, target.stateReads);
        assertTrue(target.stops.isEmpty());
    }

    @Test
    void everythingElseRidesTheExistingEngineRouteWithCasePreserved() {
        RecordingTarget target = new RecordingTarget();
        ConsoleCommands dispatcher = new ConsoleCommands(target);
        assertEquals(ConsoleCommands.Result.CONTINUE, dispatcher.dispatch("ban Griefer Griefing"));
        assertEquals(List.of("ban Griefer Griefing"), target.commands,
                "the raw line (case intact) reaches the engine route unchanged");
        assertEquals(ConsoleCommands.Result.CONTINUE, dispatcher.dispatch("/say hello"));
        assertEquals("/say hello", target.commands.get(1));
    }

    @Test
    void blankAndBareLinesArePromptRedrawsOnly() {
        RecordingTarget target = new RecordingTarget();
        ConsoleCommands dispatcher = new ConsoleCommands(target);
        assertEquals(ConsoleCommands.Result.CONTINUE, dispatcher.dispatch(""));
        assertEquals(ConsoleCommands.Result.CONTINUE, dispatcher.dispatch("   "));
        assertEquals(ConsoleCommands.Result.CONTINUE, dispatcher.dispatch(null));
        assertTrue(target.commands.isEmpty() && target.stops.isEmpty() && target.saves == 0);
    }

    @Test
    void aBrokenEngineRouteNeverEscapesIntoTheConsole() {
        ConsoleTarget broken = new RecordingTarget() {
            @Override public void consoleCommand(String raw) {
                throw new RuntimeException("engine wedged");
            }
        };
        ConsoleCommands dispatcher = new ConsoleCommands(broken);
        assertEquals(ConsoleCommands.Result.CONTINUE, dispatcher.dispatch("give x"),
                "the dispatcher itself stays alive; the engine's own route reports failures");
    }
}
