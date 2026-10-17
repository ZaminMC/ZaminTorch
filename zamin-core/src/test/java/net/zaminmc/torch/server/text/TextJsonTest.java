package net.zaminmc.torch.server.text;

import net.zaminmc.torch.server.text.Json.JsonFormatException;
import net.zaminmc.torch.server.text.Json.Num;
import net.zaminmc.torch.server.text.Json.Obj;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text component model + the JSON serializer (Text.Serializer and
 * Style.Serializer 1:1 over the engine's dependency-free walk): the
 * bare-literal short form, the style-field inline order, the with-args
 * flattening, the empty-array null quirk, the insertion-vanishing quirk,
 * and the exact color-key lookups.
 */
class TextJsonTest {

    // --- the bare literal -----------------------------------------------------

    @Test
    void bareLiteralRoundTrip() {
        Text text = TextJson.fromJson("\"hi\"");
        assertTrue(text instanceof LiteralText);
        assertEquals("hi", ((LiteralText) text).getRawString());
        assertEquals("\"hi\"", TextJson.toJson(text));
    }

    @Test
    void numberAndBooleanPrimitivesAreStrings() {
        // GSON's getAsString: numbers and booleans answer their literal form
        assertEquals("3", ((LiteralText) TextJson.fromJson("3")).getRawString());
        assertEquals("true", ((LiteralText) TextJson.fromJson("true")).getRawString());
        assertEquals("3.5", ((LiteralText) TextJson.fromJson("3.5")).getRawString());
    }

    // --- the styled component --------------------------------------------------

    @Test
    void styledComponentFieldOrder() {
        // The serializer emits the style fields first, then extra, then the
        // type field — GSON's own insertion order (the reference's addStyle
        // runs before the type dispatch).
        Text text = TextJson.fromJson("{\"text\":\"hi\",\"color\":\"red\",\"bold\":true}");
        assertEquals(Formatting.RED, text.getStyle().getColor());
        assertTrue(text.getStyle().isBold());
        assertEquals("{\"bold\":true,\"color\":\"red\",\"text\":\"hi\"}", TextJson.toJson(text));
    }

    @Test
    void colorKeysAreExact() {
        // The LowercaseEnumTypeAdapterFactory map: the exact lowercase enum
        // name only — "darkblue" (Formatting.byName's condensed form) does
        // NOT resolve through the JSON path.
        Text ok = TextJson.fromJson("{\"text\":\"x\",\"color\":\"dark_blue\"}");
        assertEquals(Formatting.DARK_BLUE, ok.getStyle().getColor());
        Text condensed = TextJson.fromJson("{\"text\":\"x\",\"color\":\"darkblue\"}");
        assertNull(condensed.getStyle().getColor());
        Text upper = TextJson.fromJson("{\"text\":\"x\",\"color\":\"DARK_BLUE\"}");
        assertNull(upper.getStyle().getColor());
    }

    @Test
    void theInsertionVanishingQuirk() {
        // Style.isEmpty() omits the insertion field: an insertion-only style
        // answers empty, so a bare literal carrying one serializes back as the
        // short form and the insertion is dropped — the reference's own walk.
        Text text = TextJson.fromJson("{\"text\":\"x\",\"insertion\":\"abc\"}");
        assertEquals("abc", text.getStyle().getInsertion());
        assertTrue(text.getStyle().isEmpty());
        assertEquals("\"x\"", TextJson.toJson(text));
    }

    @Test
    void clickEventRefusals() {
        // open_file and twitch_user_info are not remote-source allowed: the
        // style keeps them null.
        Text refused = TextJson.fromJson(
                "{\"text\":\"x\",\"clickEvent\":{\"action\":\"open_file\",\"value\":\"/tmp\"}}");
        assertNull(refused.getStyle().getClickEvent());
        Text allowed = TextJson.fromJson(
                "{\"text\":\"x\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://zaminmc.net\"}}");
        assertEquals("https://zaminmc.net", allowed.getStyle().getClickEvent().getValue());
        assertEquals(
                "{\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://zaminmc.net\"},\"text\":\"x\"}",
                TextJson.toJson(allowed));
    }

    @Test
    void hoverEventRoundTrip() {
        Text text = TextJson.fromJson(
                "{\"text\":\"x\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"hovered\"}}}");
        assertEquals(HoverEvent.Action.SHOW_TEXT, text.getStyle().getHoverEvent().getAction());
        assertEquals("hovered", ((LiteralText) text.getStyle().getHoverEvent().getValue()).getRawString());
        // The hover value serializes through the same Text walk: the bare
        // literal short form (the reference's own context.serialize).
        assertEquals(
                "{\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"hovered\"},\"text\":\"x\"}",
                TextJson.toJson(text));
    }

    // --- the sibling walk ------------------------------------------------------

    @Test
    void extraSiblingsAndArrayForm() {
        Text text = TextJson.fromJson("{\"text\":\"a\",\"extra\":[\"b\",{\"text\":\"c\"}]}");
        assertEquals(2, text.getSiblings().size());
        assertEquals("b", ((LiteralText) text.getSiblings().get(0)).getRawString());
        // The array form deserializes head + appends; serialization folds it
        // back to the object form (a non-empty sibling list cancels the bare
        // literal short form — the reference's own gate).
        Text array = TextJson.fromJson("[\"a\",{\"text\":\"c\"}]");
        assertEquals("a", ((LiteralText) array).getRawString());
        assertEquals(1, array.getSiblings().size());
        assertEquals("{\"extra\":[\"c\"],\"text\":\"a\"}", TextJson.toJson(array));
    }

    @Test
    void getStringWalksTheTree() {
        Text text = TextJson.fromJson("{\"text\":\"a\",\"extra\":[\"b\",\"c\"]}");
        assertEquals("abc", text.getString());
    }

    @Test
    void getFormattedStringPrefixesStyleCodes() {
        Text text = TextJson.fromJson("{\"text\":\"a\",\"color\":\"red\",\"extra\":[\"b\"]}");
        String formatted = text.getFormattedString();
        assertEquals("§ca§r", formatted.substring(0, "§ca§r".length()));
    }

    // --- the quirks -------------------------------------------------------------

    @Test
    void emptyArrayAnswersNull() {
        // The reference's loop never assigns: fromJson("[]") answers null
        // (not an exception).
        assertNull(TextJson.fromJson("[]"));
    }

    @Test
    void emptyExtraArrayRefused() {
        JsonFormatException ex = assertThrows(JsonFormatException.class,
                () -> TextJson.fromJson("{\"text\":\"x\",\"extra\":[]}"));
        assertEquals("Unexpected empty array of components", ex.getMessage());
    }

    @Test
    void unknownObjectRefused() {
        JsonFormatException ex = assertThrows(JsonFormatException.class,
                () -> TextJson.fromJson("{\"bold\":true}"));
        assertTrue(ex.getMessage().startsWith("Don't know how to turn"));
    }

    @Test
    void jsonNullRefusedLikeTheSerializerWalk() {
        // The Serializer's own walk throws for a null tree (not a null result)
        // — the reference's final else arm.
        assertThrows(JsonFormatException.class, () -> TextJson.fromJson("null"));
    }

    @Test
    void scoreComponentRoundTrip() {
        Text text = TextJson.fromJson("{\"score\":{\"name\":\"A\",\"objective\":\"points\",\"value\":\"3\"}}");
        assertTrue(text instanceof ScoreText);
        assertEquals("3", ((ScoreText) text).getContent());
        // The serialized value rides getContent() — the fixed value re-emits
        assertEquals(
                "{\"score\":{\"name\":\"A\",\"objective\":\"points\",\"value\":\"3\"}}",
                TextJson.toJson(text));
    }

    @Test
    void scoreWithoutBoardAnswersEmpty() {
        // No installed board: getContent() degrades to the reference's
        // no-server form (the empty string), and the serializer emits it.
        Text text = TextJson.fromJson("{\"score\":{\"name\":\"A\",\"objective\":\"points\"}}");
        assertEquals("", ((ScoreText) text).getContent());
        assertEquals(
                "{\"score\":{\"name\":\"A\",\"objective\":\"points\",\"value\":\"\"}}",
                TextJson.toJson(text));
    }

    @Test
    void translatableWithArgsFlattening() {
        Text text = TextJson.fromJson(
                "{\"translate\":\"multiplayer.player.joined\",\"with\":[\"Abasing\",{\"text\":\"!\"}]}");
        assertTrue(text instanceof TranslatableText);
        Object[] args = ((TranslatableText) text).getArgs();
        assertEquals(2, args.length);
        // Every bare-literal arg flattens to its raw string — including the
        // object form {"text":"!"} (empty style + no siblings there too);
        // only a styled or sibling-carrying arg stays a component.
        assertEquals("Abasing", args[0]);
        assertEquals("!", args[1]);
        assertEquals(
                "{\"translate\":\"multiplayer.player.joined\",\"with\":[\"Abasing\",\"!\"]}",
                TextJson.toJson(text));
        // The no-locale content walk answers the key itself
        assertEquals("multiplayer.player.joined", text.getContent());
    }

    @Test
    void selectorRoundTrip() {
        Text text = TextJson.fromJson("{\"selector\":\"@p\"}");
        assertEquals("@p", ((SelectorText) text).getPattern());
        assertEquals("{\"selector\":\"@p\"}", TextJson.toJson(text));
    }

    // --- the model walks ---------------------------------------------------------

    @Test
    void parentChainedStyleReads() {
        Text parent = TextJson.fromJson("{\"text\":\"a\",\"color\":\"red\"}");
        Text child = new LiteralText("b");
        parent.append(child);
        // The append parents the child's style to the parent's: the child
        // reads the parent's color through the chain.
        assertEquals(Formatting.RED, child.getStyle().getColor());
        // asString: the color code prefix
        assertEquals("§c", child.getStyle().asString());
    }

    @Test
    void iteratorCopiesSiblingElements() {
        Text text = TextJson.fromJson("{\"text\":\"a\",\"extra\":[\"b\"]}");
        Iterator<Text> it = text.iterator();
        assertTrue(it.hasNext());
        Text head = it.next();
        assertSameIdentity(head, text); // the head is yielded RAW
        Text sibling = it.next();
        assertFalse(it.hasNext());
        // The sibling's element is a copy, not the stored component
        assertFalse(sibling == text.getSiblings().get(0));
    }

    @Test
    void formattingTableWalks() {
        assertEquals(Formatting.DARK_BLUE, Formatting.byName("dark_blue"));
        assertEquals(Formatting.DARK_BLUE, Formatting.byName("DARKBLUE")); // the condensed form
        assertNull(Formatting.byName("nope"));
        assertEquals(Formatting.RESET, Formatting.byId(-1));
        assertNull(Formatting.byId(99));
        assertEquals("§l", Formatting.BOLD.toString());
        assertEquals("plain", Formatting.strip("§cplain§r"));
        assertTrue(Formatting.RED.isColor());
        assertFalse(Formatting.BOLD.isColor());
        List<String> colorNames = Formatting.getNames(true, false);
        // The reference's own filter: the sixteen colors AND reset (isColor()
        // is false for RESET, so the colors-only call still carries it).
        assertEquals(17, colorNames.size());
        assertEquals("black", colorNames.get(0));
        assertEquals("reset", colorNames.get(16));
    }

    @Test
    void jsonParseAndWriteBasics() {
        // Duplicate keys: last wins (GSON's JsonObject.add overwrite)
        Obj o = (Obj) Json.parse("{\"a\":1,\"a\":2}");
        assertEquals(1, o.members().size());
        assertEquals(2, ((Json.Num) o.get("a")).getAsInt());
        // Compact writer, escapes
        assertEquals("{\"k\":\"a\\\"b\"}", Json.write(Json.parse("{\"k\" : \"a\\\"b\"  }")));
        assertThrows(JsonFormatException.class, () -> Json.parse("{\"a\":1,}"));
        assertThrows(JsonFormatException.class, () -> Json.parse(""));
        assertThrows(JsonFormatException.class, () -> Json.parse("[1] trailing"));
    }

    // --- helpers -----------------------------------------------------------------

    private static void assertSameIdentity(Object a, Object b) {
        assertTrue(a == b);
    }
}
