package net.zaminmc.torch.server.item;

import net.zaminmc.torch.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The book family's own laws (§29's Wave VIII row VIII12 — the written-book
 * flow, the reference's {@code BookAndQuillItem} / {@code WrittenBookItem}
 * 1:1): the two channel-gate validators and the read gate's resolve walk.
 *
 * <p><b>The two isValid gates</b> (the reference's own statics, the exact
 * rows): a book &amp; quill's tag is valid when it carries a {@code pages}
 * list of strings with every page at most 32767 characters
 * ({@code BookAndQuillItem.isValid} lines 21-44 — a null tag, a missing or
 * non-list row, or any over-length page refuses the whole book); a written
 * book adds the {@code title} STRING row (at most 32 characters) and the
 * {@code author} STRING row's presence ({@code WrittenBookItem.isValid}
 * lines 25-36). The MC|BEdit and MC|BSign handlers run these over the
 * payload BEFORE any row lands on the held stack — the reference's own
 * {@code throw new IOException("Invalid book tag!")} refusal.</p>
 *
 * <p><b>The resolve walk</b> ({@code WrittenBookItem.resolvePages} lines
 * 79-109 — the read gate's server arm, run when a player USES a written
 * book): a book whose {@code resolved} byte is unset gets the marker armed
 * and — when the tag passes the written-book gate — every page walked
 * through the text-component parse: a page that is not a JSON text form
 * wraps as the bare JSON string literal the reference's
 * {@code Text.Serializer.toJson(new LiteralText(s))} produces (the
 * no-style/no-siblings primitive arm, line 137), a page that already parses
 * rides VERBATIM. The engine carries no text-component model — the named
 * adaptation — so the reference's re-serialization of already-valid JSON
 * (the selector/score resolution pass over parsed components) reduces to
 * the verbatim carry: the vanilla client's own signed pages are already the
 * canonical literal forms, and the wrap arm answers the modded/plain-text
 * page exactly like the reference's catch branch. The walk's answer is the
 * resolved stack; the caller owns the resync.</p>
 */
public final class Books {

    private Books() {
    }

    /**
     * The book &amp; quill's channel gate ({@code BookAndQuillItem.isValid}
     * lines 21-44): the tag must carry a {@code pages} string list with
     * every page at most 32767 characters. A book with no pages row at all
     * is NOT valid (the reference's own {@code !tag.contains("pages", 9)}
     * refusal — an empty-but-present list passes).
     */
    public static boolean validWritableBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !stack.type().equals(BuiltinItems.WRITABLE_BOOK)) {
            return false;
        }
        List<String> pages = stack.bookPages();
        if (pages == null) {
            return false; // the missing pages row refuses (contains("pages", 9))
        }
        for (String page : pages) {
            if (page == null || page.length() > ItemStack.MAX_BOOK_PAGE_CHARS) {
                return false;
            }
        }
        return true;
    }

    /**
     * The written book's channel gate ({@code WrittenBookItem.isValid}
     * lines 25-36): the writable gate's pages row PLUS the {@code title}
     * string at most 32 characters and the {@code author} string row's
     * presence.
     */
    public static boolean validWrittenBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !stack.type().equals(BuiltinItems.WRITTEN_BOOK)) {
            return false;
        }
        if (stack.bookPages() == null) {
            return false;
        }
        for (String page : stack.bookPages()) {
            if (page == null || page.length() > ItemStack.MAX_BOOK_PAGE_CHARS) {
                return false;
            }
        }
        String title = stack.bookTitle();
        if (title == null || title.length() > 32) {
            return false;
        }
        return stack.bookAuthor() != null;
    }

    /**
     * The read gate's resolve walk ({@code resolvePages} lines 79-109): a
     * book whose {@code resolved} byte is unset gets the marker armed and,
     * when the tag passes the written-book gate, every non-JSON page wrapped
     * into the bare JSON string literal form (the reference's
     * catch-branch serialization). Already-JSON pages ride verbatim (the
     * engine's named adaptation — no text-component model to re-serialize
     * through; the vanilla client's own signed pages are already the
     * canonical literal forms, so the observable end state matches the
     * reference for every real book). The walk never runs twice — the
     * marker arms even when the gate refuses the pages (the reference's own
     * order: {@code resolved} lands before the isValid consult).
     *
     * @return the resolved stack (the same instance when the gate refuses)
     */
    public static ItemStack resolvePages(ItemStack book) {
        if (book == null || book.isEmpty() || book.bookResolved()) {
            return book; // the once-only gate (line 82)
        }
        book = book.withBookResolved(true);
        if (!validWrittenBook(book)) {
            return book; // the invalid tag arms the marker but never walks (line 84)
        }
        List<String> resolved = new ArrayList<>(book.bookPages().size());
        for (String page : book.bookPages()) {
            resolved.add(isJsonText(page) ? page : quoteJson(page));
        }
        return book.withBookPages(resolved);
    }

    /**
     * The page's JSON-text validity consult: a page that already parses as
     * a JSON document rides verbatim (the reference's
     * {@code Text.Serializer.fromJson} succeeding — the engine carries no
     * component model, so the parse alone decides, never the component
     * walk). A strict syntax walk: one value — an object, an array, a
     * quoted string, a number, or one of the three literals — then the
     * document ends.
     */
    static boolean isJsonText(String page) {
        if (page == null || page.isEmpty()) {
            return false;
        }
        JsonWalk walk = new JsonWalk(page);
        boolean value = walk.readValue();
        return value && walk.index >= page.length();
    }

    /**
     * The wrap arm's serialization — exactly what the reference's
     * {@code Text.Serializer.toJson(new LiteralText(s))} answers for the
     * plain literal (the serialize method's first arm, line 136-138: no
     * style, no siblings — the bare {@code JsonPrimitive}): the JSON string
     * literal with the standard escapes (GSON's own set — the two quotes,
     * the backslash, and the control rows below 0x20; the single quote and
     * the solidus never escape).
     */
    static String quoteJson(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2);
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    /** One page's cursor for the strict JSON walk. */
    private static final class JsonWalk {
        private final String text;
        private int index;

        JsonWalk(String text) {
            this.text = text;
        }

        boolean readValue() {
            skipWhitespace();
            if (index >= text.length()) {
                return false;
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true");
                case 'f' -> readLiteral("false");
                case 'n' -> readLiteral("null");
                default -> (c == '-' || (c >= '0' && c <= '9')) && readNumber();
            };
        }

        private boolean readObject() {
            index++; // '{'
            skipWhitespace();
            if (index < text.length() && text.charAt(index) == '}') {
                index++;
                return true;
            }
            while (true) {
                skipWhitespace();
                if (index >= text.length() || text.charAt(index) != '"') {
                    return false; // a member's name must be quoted
                }
                if (!readString()) {
                    return false;
                }
                skipWhitespace();
                if (index >= text.length() || text.charAt(index) != ':') {
                    return false;
                }
                index++;
                if (!readValue()) {
                    return false;
                }
                skipWhitespace();
                if (index >= text.length()) {
                    return false;
                }
                char c = text.charAt(index);
                if (c == ',') {
                    index++;
                    continue;
                }
                if (c == '}') {
                    index++;
                    return true;
                }
                return false;
            }
        }

        private boolean readArray() {
            index++; // '['
            skipWhitespace();
            if (index < text.length() && text.charAt(index) == ']') {
                index++;
                return true;
            }
            while (true) {
                if (!readValue()) {
                    return false;
                }
                skipWhitespace();
                if (index >= text.length()) {
                    return false;
                }
                char c = text.charAt(index);
                if (c == ',') {
                    index++;
                    continue;
                }
                if (c == ']') {
                    index++;
                    return true;
                }
                return false;
            }
        }

        private boolean readString() {
            index++; // the opening quote
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == '\\') {
                    index += 2; // the escape pair (the shape alone matters here)
                    continue;
                }
                if (c == '"') {
                    index++;
                    return true;
                }
                index++;
            }
            return false; // unterminated
        }

        private boolean readNumber() {
            if (index < text.length() && text.charAt(index) == '-') {
                index++;
            }
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c >= '0' && c <= '9') {
                    index++;
                } else {
                    break;
                }
            }
            if (index < text.length() && text.charAt(index) == '.') {
                index++;
                while (index < text.length() && text.charAt(index) >= '0'
                        && text.charAt(index) <= '9') {
                    index++;
                }
            }
            if (index < text.length() && (text.charAt(index) == 'e'
                    || text.charAt(index) == 'E')) {
                index++;
                if (index < text.length() && (text.charAt(index) == '+'
                        || text.charAt(index) == '-')) {
                    index++;
                }
                while (index < text.length() && text.charAt(index) >= '0'
                        && text.charAt(index) <= '9') {
                    index++;
                }
            }
            // at least one digit consumed (the caller guaranteed the first)
            return true;
        }

        private boolean readLiteral(String literal) {
            if (text.regionMatches(index, literal, 0, literal.length())) {
                index += literal.length();
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }
    }
}
