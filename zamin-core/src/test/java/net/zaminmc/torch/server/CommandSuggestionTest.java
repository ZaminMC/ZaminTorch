package net.zaminmc.torch.server;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.chat.CommandService;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The per-command ARGUMENT completion's own contract (the reference's
 * getSuggestions arms, the standing demand-named item): the matcher is the
 * reference's suggestMatching (getListOfStringsMatchingLastWord) — the last
 * token's case-insensitive prefix over the candidates, in order, an empty
 * last token matching everything — and the Command record carries the
 * optional suggester alongside the executor without disturbing the
 * pre-completion signatures (the overloads keep every existing registration
 * compiling).
 */
class CommandSuggestionTest {

    private static final CommandSender CONSOLE = new CommandSender() {
        @Override public String name() { return "Server"; }
        @Override public void sendMessage(String message) { }
        @Override public int opLevel() { return 4; }
        @Override public PlayerSession player() { return null; }
    };

    @Test
    void theMatcherIsTheReferenceSuggestMatching() {
        // The prefix matches case-insensitively, in the candidates' own order and case.
        assertEquals(List.of("survival", "spectator"),
                CommandService.matchLastToken(new String[] {"S"},
                        List.of("survival", "creative", "adventure", "spectator")),
                "the last token's case-insensitive prefix, in order");
        // An empty last token matches everything.
        assertEquals(List.of("set", "add", "query"),
                CommandService.matchLastToken(new String[] {""},
                        List.of("set", "add", "query")),
                "the empty last token matches all");
        // A trailing space token is still the empty token.
        assertEquals(List.of("day", "night"),
                CommandService.matchLastToken(new String[] {"set", ""},
                        List.of("day", "night")),
                "the being-typed token after a trailing space matches all");
        // No match answers empty, never null.
        assertEquals(List.of(),
                CommandService.matchLastToken(new String[] {"zzz"},
                        List.of("day", "night")),
                "no match answers empty");
        // The candidates' own case is preserved in the answer.
        assertEquals(List.of("LightningBolt"),
                CommandService.matchLastToken(new String[] {"light"},
                        List.of("LightningBolt", "Creeper")),
                "the candidates' own case rides the answer");
    }

    @Test
    void theRecordCarriesTheOptionalSuggester() {
        CommandService service = new CommandService();
        List<String> modeNames = List.of("survival", "creative", "adventure", "spectator");
        service.register(new CommandService.Command("gamemode", "modes", 2,
                (sender, args) -> "ok",
                (sender, args) -> args.length == 1 ? modeNames : null));
        // The pre-completion signatures still compile and default to no suggester.
        service.register(new CommandService.Command("spawn", "spawn", (sender, args) -> "ok"));
        service.register(new CommandService.Command("weather", "weather", 2,
                (sender, args) -> "ok"));

        assertNull(service.lookup("spawn").suggester(), "the 3-arg overload defaults to null");
        assertNull(service.lookup("weather").suggester(), "the 4-arg overload defaults to null");
        assertEquals(modeNames, service.lookup("gamemode").suggester().apply(CONSOLE, new String[] {"s"}),
                "the suggester receives the tokens after the command name");
        assertNull(service.lookup("gamemode").suggester().apply(CONSOLE, new String[] {"s", "x"}),
                "the arm answers null past its own positions");

        // The suggester never disturbs dispatch.
        assertEquals("ok", service.lookup("gamemode").executor().apply(CONSOLE, new String[] {"survival"}));
        assertThrows(IllegalStateException.class,
                () -> service.register(new CommandService.Command("gamemode", "dupe", 0,
                        (sender, args) -> "ok")),
                "the duplicate guard still applies");
    }
}
