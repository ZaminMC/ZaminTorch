package net.zaminmc.torch.server.nbt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SNBT grammar (Slice 75 — the reference's SnbtParser over the
 * /summon NBT arm, 1:1): the suffix families, the true/false bytes, the
 * all-int array, the quoted strings with escapes, the fall-to-string
 * shape for everything else, and the syntax errors' own refusals.
 */
class SnbtTest {

    @Test
    void thePrimitiveFamiliesTypeExactlyLikeTheReference() {
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{d:1.5d, f:2.5f, b:3b, l:4l, s:5s, i:6, dbl:7.5,"
                        + "t:true, fa:FALSE, neg:-8, plus:+9}");
        assertEquals(Snbt.Tag.Type.DOUBLE, rows.get("d").type());
        assertEquals(1.5, rows.get("d").asDouble(), 1e-9);
        assertEquals(Snbt.Tag.Type.FLOAT, rows.get("f").type());
        assertEquals(2.5f, rows.get("f").asFloat(), 1e-6);
        assertEquals(Snbt.Tag.Type.BYTE, rows.get("b").type());
        assertEquals(3, rows.get("b").asByte());
        assertEquals(Snbt.Tag.Type.LONG, rows.get("l").type());
        assertEquals(Snbt.Tag.Type.SHORT, rows.get("s").type());
        assertEquals(Snbt.Tag.Type.INT, rows.get("i").type());
        assertEquals(6, (int) rows.get("i").asDouble());
        assertEquals(Snbt.Tag.Type.DOUBLE, rows.get("dbl").type());
        // The true/false pair answers BYTES (PrimitiveEntry lines 366-368)
        // — the reference's parseBoolean("FALSE") is still FALSE, the byte 0.
        assertEquals(Snbt.Tag.Type.BYTE, rows.get("t").type());
        assertEquals(1, rows.get("t").asByte());
        assertEquals(0, rows.get("fa").asByte(), "FALSE answers the byte 0");
        assertEquals(-8, (int) rows.get("neg").asDouble());
        assertEquals(9, (int) rows.get("plus").asDouble());
    }

    @Test
    void theStringArmsCarryTheReferenceEscapeWalk() {
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{quoted:\"a name\", escaped:\"say \\\"hi\\\"\","
                        + "bare:plain_word, outOfRange:999b, colonInside:\"x:y\"}");
        assertEquals("a name", rows.get("quoted").asString());
        assertEquals("say \"hi\"", rows.get("escaped").asString());
        // The fall-to-string shape (the NumberFormatException arm): a
        // non-number and an out-of-range number are both STRINGS.
        assertEquals("plain_word", rows.get("bare").asString());
        assertEquals("999b", rows.get("outOfRange").asString());
        assertEquals("x:y", rows.get("colonInside").asString());
        // A quoted KEY (the reference's quoted-key form).
        Map<String, Snbt.Tag> quotedKey = Snbt.parseCompound(
                "{\"quoted key\":1}");
        assertEquals(1, (int) quotedKey.get("quoted key").asDouble());
    }

    @Test
    void theCompoundListAndArrayFormsNest() {
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{nested:{inner:1b, deeper:{leaf:\"x\"}},"
                        + "list:[1,2,3], mixed:[{a:1},{b:2}], empty:[],"
                        + "intArray:[1, 2, 3], notArray:[1, x]}");
        Map<String, Snbt.Tag> nested = rows.get("nested").asCompound();
        assertNotNull(nested);
        assertEquals(Snbt.Tag.Type.BYTE, nested.get("inner").type());
        assertEquals("x", nested.get("deeper").asCompound().get("leaf").asString());
        // The all-int bracket run is an INT ARRAY (the reference's
        // PRIMITIVE_PATTERN split), not a list.
        assertEquals(Snbt.Tag.Type.INT_ARRAY, rows.get("intArray").type());
        assertEquals(3, rows.get("intArray").asIntArray().length);
        assertEquals(2, rows.get("intArray").asIntArray()[1]);
        // The compound list is a LIST of compounds.
        assertEquals(Snbt.Tag.Type.LIST, rows.get("mixed").type());
        assertEquals(2, rows.get("mixed").asList().size());
        assertEquals(Snbt.Tag.Type.COMPOUND, rows.get("mixed").asList().get(1).type());
        assertEquals(1, rows.get("mixed").asList().get(0).asCompound().size());
        assertEquals(0, rows.get("empty").asList().size());
        // A non-int bracket run is a LIST (the reference's own split: only
        // the all-int PRIMITIVE_PATTERN runs take the array arm) whose
        // non-number elements fall to strings.
        assertEquals(Snbt.Tag.Type.LIST, rows.get("notArray").type());
        assertEquals(Snbt.Tag.Type.INT, rows.get("notArray").asList().get(0).type());
        assertEquals(Snbt.Tag.Type.STRING, rows.get("notArray").asList().get(1).type());
    }

    @Test
    void theWhitespaceAndTrailingCommaFormsAreTolerated() {
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "  {  a : 1  ,  b : \"x\" ,  }  ");
        assertEquals(1, (int) rows.get("a").asDouble());
        assertEquals("x", rows.get("b").asString());
        assertEquals(2, rows.size());
        assertEquals(0, Snbt.parseCompound("{}").size());
        assertEquals(0, Snbt.parseCompound("  {  }  ").size());
    }

    @Test
    void theSyntaxErrorsRefuseWithTheReferenceMessages() {
        assertThrows(IllegalArgumentException.class,
                () -> Snbt.parseCompound("no braces"),
                "the missing '{' refuses");
        assertThrows(IllegalArgumentException.class,
                () -> Snbt.parseCompound("{a:1"),
                "the unbalanced compound refuses");
        assertThrows(IllegalArgumentException.class,
                () -> Snbt.parseCompound("{a 1}"),
                "the missing separator refuses");
        assertThrows(IllegalArgumentException.class,
                () -> Snbt.parseCompound("{a:1} {b:2}"),
                "the multiple top tags refuse");
        assertThrows(IllegalArgumentException.class,
                () -> Snbt.parseCompound("{a:\"unterminated}"),
                "the unbalanced quotation refuses");
        assertThrows(IllegalArgumentException.class,
                () -> Snbt.parseCompound("{a:[1,2}"),
                "the unbalanced list refuses");
        assertNull(Snbt.parseCompound("{a:1}").get("b"),
                "an absent row answers null");
    }

    @Test
    void theSummonRowShapesParse() {
        // The exact rows the /summon arm reads.
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{CustomName:\"Herobrine\",CustomNameVisible:1b,Health:25.0f,"
                        + "NoAI:1b,PersistenceRequired:1b,Invulnerable:0b}");
        assertEquals("Herobrine", rows.get("CustomName").asString());
        assertEquals(1, rows.get("CustomNameVisible").asByte());
        assertEquals(25.0f, rows.get("Health").asFloat(), 1e-6);
        assertEquals(1, rows.get("NoAI").asByte());
        assertEquals(1, rows.get("PersistenceRequired").asByte());
        assertEquals(0, rows.get("Invulnerable").asByte());
        // The engine's numeric readers widen any family (a bare int 1 is
        // a truthful NoAI row).
        Map<String, Snbt.Tag> intForms = Snbt.parseCompound(
                "{CustomNameVisible:1,Health:10,NoAI:true}");
        assertTrue(intForms.get("CustomNameVisible").asDouble() != 0.0);
        assertEquals(10.0, intForms.get("Health").asDouble(), 1e-9);
        assertTrue(intForms.get("NoAI").asDouble() != 0.0);
    }
}
