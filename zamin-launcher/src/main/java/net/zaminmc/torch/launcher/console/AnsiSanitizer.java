package net.zaminmc.torch.launcher.console;

/**
 * The console's formatting rules (architecture §30.3): timestamps, levels,
 * source, message text and full exception details must survive; Minecraft
 * legacy formatting codes (§) get a documented strip-or-translate rule; real
 * ANSI escape sequences are never allowed through as message content.
 *
 * <p>Rules, applied by the terminal and file sinks:</p>
 * <ul>
 *   <li><b>Injection guard</b> — any raw ESC (0x1B) sequence inside a logged
 *       message is removed before the text reaches either sink. Player-typed
 *       chat can carry ESC bytes; the terminal's escape language must never
 *       be driven by untrusted input.</li>
 *   <li><b>Color terminal</b> — legacy § codes are TRANSLATED to SGR codes
 *       (colors 0-9a-f, bold, underline, italic, strikethrough, reset; the
 *       obfuscation code has no SGR form and is dropped), with one trailing
 *       reset so a color never bleeds past its message.</li>
 *   <li><b>Plain terminal / fallback</b> — legacy § codes are STRIPPED (the
 *       message reads clean; no control bytes on a dumb terminal).</li>
 *   <li><b>File sink</b> — legacy § codes stay verbatim (they are literal
 *       content, not ANSI escapes; {@code logs/latest.log} contains zero ANSI
 *       escape sequences by construction).</li>
 * </ul>
 */
public final class AnsiSanitizer {

    private AnsiSanitizer() {
    }

    /** The SGR strings for the legacy color/format codes, indexed by hex digit. */
    private static final String[] LEGACY_SGR = {
            "30", "34", "32", "36", "31", "35", "33", "37",       // 0-7
            "90", "94", "92", "96", "91", "95", "93", "97",       // 8,9,a-f
    };

    /** @return the message with every raw ESC sequence removed (both sinks). */
    public static String stripEscapes(String text) {
        if (text == null || text.indexOf(27) < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != 27) {
                out.append(c);
                continue;
            }
            // Skip the escape sequence body: CSI forms (ESC [ ... final byte
            // 0x40-0x7E) and the two-character OSC-style forms conservatively
            // bounded (ESC ] ... BEL, ESC ( X, ESC X).
            i++;
            if (i >= text.length()) {
                break;
            }
            char next = text.charAt(i);
            if (next == '[') {
                while (i + 1 < text.length()) {
                    char f = text.charAt(++i);
                    if (f >= 0x40 && f <= 0x7E) {
                        break;
                    }
                }
            } else if (next == ']') {
                while (i + 1 < text.length() && text.charAt(i + 1) != 7) {
                    i++;
                }
                i++; // the BEL
            }
            // any other single-character escape: the ESC and its letter die here
        }
        return out.toString();
    }

    /**
     * @return the message with legacy § codes translated to SGR sequences and
     * a trailing reset when any code was translated (color terminals).
     */
    public static String translateLegacy(String text) {
        return rewrite(text, true);
    }

    /** @return the message with legacy § codes removed (plain terminals). */
    public static String stripLegacy(String text) {
        return rewrite(text, false);
    }

    private static String rewrite(String text, boolean translate) {
        if (text == null || text.indexOf('§') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        boolean colored = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '§' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(++i));
            String sgr = sgrOf(code);
            if (sgr != null) {
                colored = true;
                if (translate) {
                    out.append("\u001b[").append(sgr).append('m');
                }
            } else {
                // unknown code letters (incl. the obfuscated 'k'): dropped
                colored = colored || translate && code == 'k';
            }
        }
        if (colored && translate) {
            out.append("\u001b[0m");
        }
        return out.toString();
    }

    /** The SGR payload for a legacy code letter, or null when unmapped. */
    private static String sgrOf(char code) {
        if (code >= '0' && code <= '9') {
            return LEGACY_SGR[code - '0'];
        }
        if (code >= 'a' && code <= 'f') {
            return LEGACY_SGR[10 + (code - 'a')];
        }
        switch (code) {
            case 'l': return "1";   // bold
            case 'm': return "9";   // strikethrough
            case 'n': return "4";   // underline
            case 'o': return "3";   // italic
            case 'r': return "0";   // reset
            default: return null;   // 'k' obfuscated: no SGR form
        }
    }
}
