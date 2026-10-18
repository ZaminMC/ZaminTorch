package net.zaminmc.torch.launcher.console;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The formatting rules (§30.3 / 30.4.7): ESC never passes, § codes follow
 * the documented translate-or-strip rule, and the file keeps § verbatim.
 */
class AnsiSanitizerTest {

    @Test
    void rawEscapesNeverPassToAnySink() {
        String injected = "clean\u001b[31mRED\u001b[0mmore\u001b]0;title\u0007tail";
        String clean = AnsiSanitizer.stripEscapes(injected);
        assertFalse(clean.contains("\u001b"), "no ESC byte survives");
        assertTrue(clean.contains("clean"));
        assertTrue(clean.contains("RED"));
        assertTrue(clean.contains("more"));
        assertTrue(clean.contains("tail"));
        assertEquals("cleanREDmoretail", clean);
    }

    @Test
    void plainTextPassesUntouched() {
        assertEquals("hello world", AnsiSanitizer.stripEscapes("hello world"));
        assertEquals("hello world", AnsiSanitizer.stripLegacy("hello world"));
        assertEquals("hello world", AnsiSanitizer.translateLegacy("hello world"));
    }

    @Test
    void legacyColorsTranslateToSgrOnColorTerminals() {
        String translated = AnsiSanitizer.translateLegacy("§4dark-red§r white");
        assertTrue(translated.contains("\u001b[31m"), "the dark red maps to SGR 31");
        assertTrue(translated.contains("\u001b[0m"), "the reset maps to SGR 0");
        assertTrue(translated.endsWith("\u001b[0m"), "a trailing reset stops the bleed");
        // the full palette: 16 colors + the format codes (§c is LIGHT red → 91)
        String all = AnsiSanitizer.translateLegacy("§0§1§2§3§4§5§6§7§8§9§a§b§c§d§e§f§l§m§n§o§r");
        for (String sgr : new String[]{"30", "34", "32", "36", "31", "35", "33", "37",
                "90", "94", "92", "96", "91", "95", "93", "97", "1", "9", "4", "3", "0"}) {
            assertTrue(all.contains("\u001b[" + sgr + "m"), "code for SGR " + sgr + " present");
        }
        assertFalse(all.contains("§"), "no legacy marker survives translation");
        String lightRed = AnsiSanitizer.translateLegacy("§cx");
        assertTrue(lightRed.contains("\u001b[91m"), "§c is the LIGHT red SGR 91");
    }

    @Test
    void legacyColorsStripOnPlainTerminals() {
        assertEquals("red white", AnsiSanitizer.stripLegacy("§cred§r white"));
        String all = AnsiSanitizer.stripLegacy("§0§1§9§a§f§k§l§r text");
        assertEquals(" text", all, "every legacy marker is removed, text intact");
        assertFalse(all.contains("\u001b"), "plain never emits ANSI");
    }

    @Test
    void escapesAreStrippedEvenAroundLegacyCodes() {
        String nasty = "§c\u001b[2Jcleared";
        String plain = AnsiSanitizer.stripLegacy(nasty);
        assertEquals("cleared", plain, "both the legacy code and the ESC die");
        String color = AnsiSanitizer.translateLegacy(nasty);
        assertEquals("\u001b[91mcleared\u001b[0m", color);
    }
}
