package net.zaminmc.torch.server.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE COMMAND REFUSAL SPLIT (Wave C1 — CommandRegistry.run lines 44-70
 * 1:1): the unknown command and the permission denial are TWO arms with
 * TWO texts in the reference (commands.generic.notFound vs
 * commands.generic.permission, both red), never the merged refusal the
 * engine carried. The seats pin both texts and the count zero both pay.
 */
class CommandRefusalParityTest {

    /** A capturing sender: the level under test + the delivered lines. */
    private static final class CapturingSender implements CommandSender {
        final int level;
        final List<String> lines = new ArrayList<>();

        CapturingSender(int level) {
            this.level = level;
        }

        @Override
        public String name() {
            return "Captor";
        }

        @Override
        public void sendMessage(String content) {
            lines.add(content);
        }

        @Override
        public int opLevel() {
            return level;
        }

        @Override
        public net.zaminmc.torch.server.player.PlayerSession player() {
            return null; // the console-shaped arm: no body behind the sender
        }
    }

    private static CommandService service() {
        CommandService commands = new CommandService();
        commands.register(new CommandService.Command("needsop",
                "A level-2 test command", 2, (sender, args) -> "ran"));
        commands.register(new CommandService.Command("free",
                "A level-0 test command", (sender, args) -> "ran"));
        return commands;
    }

    @Test
    void theUnknownCommandRefusalCarriesItsOwnText() {
        CommandService commands = service();
        CapturingSender sender = new CapturingSender(4);
        int count = commands.dispatchCounted(sender, "/nonexistent");
        assertEquals(0, count);
        assertEquals(1, sender.lines.size());
        // commands.generic.notFound (red): the 1.8.8 en_US.lang form.
        assertEquals("§cUnknown command. Type \"/help\" for help.", sender.lines.get(0));
    }

    @Test
    void thePermissionRefusalCarriesItsOwnText() {
        CommandService commands = service();
        CapturingSender sender = new CapturingSender(0);
        int count = commands.dispatchCounted(sender, "/needsop");
        assertEquals(0, count);
        assertEquals(1, sender.lines.size());
        // commands.generic.permission (red): the 1.8.8 en_US.lang form
        // verbatim — NEVER the unknown-command text.
        assertTrue(sender.lines.get(0).startsWith("§cI'm sorry, but you do not have "
                + "permission to perform this command."), sender.lines.get(0));
        assertTrue(sender.lines.get(0).endsWith("Please contact the server administrators "
                + "if you believe that this is in error."), sender.lines.get(0));
    }

    @Test
    void theAuthorizedDispatchStillRuns() {
        CommandService commands = service();
        CapturingSender sender = new CapturingSender(2);
        int count = commands.dispatchCounted(sender, "/needsop");
        assertEquals(1, count);
        assertEquals(List.of("ran"), sender.lines);
        // The free command runs at level 0.
        CapturingSender plain = new CapturingSender(0);
        assertEquals(1, commands.dispatchCounted(plain, "/free"));
        assertEquals(List.of("ran"), plain.lines);
    }
}
