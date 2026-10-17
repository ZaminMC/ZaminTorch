package net.zaminmc.torch.server.nbt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compound operations (Slice 89 — the reference's NbtCompound/
 * NbtUtils/NbtElement surface over the engine's Tag model): the merge,
 * the matches subset check, the deep equality, the SNBT writer's
 * per-type forms, and the getter family's coercion contracts.
 */
class NbtOpsTest {

    @Test
    void theNumericGettersWidenAnyFamilyLikeTheReference() {
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{i:20, d:2.5d, b:1b, s:70000, text:\"x\", list:[1,2]}");
        // contains(key, 99): any numeric family widens (i is an INT read as
        // short/double/float); a STRING row answers the zero.
        assertEquals(20, NbtOps.getShort(rows, "i"));
        assertEquals(20.0, NbtOps.getDouble(rows, "i"), 1e-9);
        assertEquals(20.0f, NbtOps.getFloat(rows, "i"), 1e-6f);
        assertEquals(1, NbtOps.getByte(rows, "b"));
        assertEquals(0, NbtOps.getShort(rows, "text"));
        assertEquals("", NbtOps.getString(rows, "i"), "an INT row is not a string");
        assertEquals("x", NbtOps.getString(rows, "text"));
        // getBoolean is getByte != 0 (any numeric family).
        assertTrue(NbtOps.getBoolean(rows, "b"));
        assertFalse(NbtOps.getBoolean(rows, "text"));
        // The s:70000 row (an INT parsed out of Short range) truncates on the
        // widening reads exactly like the reference's Number.getShort.
        assertEquals((short) 70000, NbtOps.getShort(rows, "s"));
    }

    @Test
    void getListAnswersEmptyForTheWrongElementFamily() {
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{rot:[0.0f,90.0f], pos:[1.5d,2.5d,3.5d], mixed:[{a:1}]}");
        // Rotation is a list of FLOATs: asked as DOUBLE (the Pos family) the
        // reference answers the empty list.
        assertTrue(NbtOps.getList(rows, "rot", Snbt.Tag.Type.DOUBLE).isEmpty());
        assertEquals(2, NbtOps.getList(rows, "rot", Snbt.Tag.Type.FLOAT).size());
        // Pos asked as FLOAT answers empty; as DOUBLE the three rows.
        assertTrue(NbtOps.getList(rows, "pos", Snbt.Tag.Type.FLOAT).isEmpty());
        List<Snbt.Tag> pos = NbtOps.getList(rows, "pos", Snbt.Tag.Type.DOUBLE);
        assertEquals(1.5, NbtOps.listDouble(pos, 0), 1e-9);
        assertEquals(2.5, NbtOps.listDouble(pos, 1), 1e-9);
        // Out of bounds and wrong family answer 0.0 (NbtList.getDouble).
        assertEquals(0.0, NbtOps.listDouble(pos, 3), 1e-9);
        assertEquals(0.0, NbtOps.listDouble(pos, -1), 1e-9);
        // The float accessor on a DOUBLE element answers 0.0f.
        assertEquals(0.0f, NbtOps.listFloat(pos, 0), 1e-6f);
        // A missing row and a compound-typed row asked as a list: empty.
        assertTrue(NbtOps.getList(rows, "mixed", Snbt.Tag.Type.COMPOUND).size() == 1);
        assertTrue(NbtOps.getList(rows, "absent", Snbt.Tag.Type.COMPOUND).isEmpty());
    }

    @Test
    void theMergeIsTheReferenceRecursiveCompoundMerge() {
        Map<String, Snbt.Tag> into = Snbt.parseCompound(
                "{a:1, nested:{x:1, y:2}, gone:9}");
        Map<String, Snbt.Tag> from = Snbt.parseCompound(
                "{a:5, nested:{y:3, z:4}, fresh:\"new\"}");
        NbtOps.merge(into, from);
        // Non-compound rows replace; compound rows merge recursively; the
        // untouched rows survive.
        assertEquals(5, NbtOps.getInt(into, "a"));
        assertEquals(1, NbtOps.getInt(into.get("nested").asCompound(), "x"));
        assertEquals(3, NbtOps.getInt(into.get("nested").asCompound(), "y"));
        assertEquals(4, NbtOps.getInt(into.get("nested").asCompound(), "z"));
        assertEquals("new", NbtOps.getString(into, "fresh"));
        assertEquals(9, NbtOps.getInt(into, "gone"));
        // The merge deep-copies: mutating the source afterwards changes
        // nothing (the reference's copy() on every put).
        from.get("nested").asCompound().put("y", Snbt.Tag.ofInt(99));
        assertEquals(3, NbtOps.getInt(into.get("nested").asCompound(), "y"));
    }

    @Test
    void theMatchesSubsetCheckFollowsNbtUtils() {
        Map<String, Snbt.Tag> actual = Snbt.parseCompound(
                "{id:\"Zombie\", Pos:[1.0d,2.0d,3.0d],"
                + " nested:{Health:20, NoAI:true}, extra:\"untouched\"}");
        // A row the actual lacks fails the match.
        assertFalse(NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound("{absent:1}")),
                Snbt.Tag.ofCompound(actual), true));
        // Extra rows in the actual pass (the subset check).
        assertTrue(NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound("{id:\"Zombie\"}")),
                Snbt.Tag.ofCompound(actual), true));
        // The wrong value fails; the right nested subset passes.
        assertFalse(NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound("{nested:{Health:21}}")),
                Snbt.Tag.ofCompound(actual), true));
        assertTrue(NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound("{nested:{Health:20}}")),
                Snbt.Tag.ofCompound(actual), true));
        // Type mismatch fails (INT vs DOUBLE families).
        assertFalse(NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound("{Pos:[1,2,3]}")),
                Snbt.Tag.ofCompound(actual), true));
        // The list recursion: each pattern element needs some equal actual
        // element; the empty pattern list matches only an empty actual.
        assertTrue(NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound("{Pos:[3.0d,1.0d]}")),
                Snbt.Tag.ofCompound(actual), true));
        assertFalse(NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound("{Pos:[]}")),
                Snbt.Tag.ofCompound(actual), true));
        assertTrue(NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound("{Pos:[]}")),
                Snbt.Tag.ofCompound(Snbt.parseCompound("{Pos:[]}")), true));
    }

    @Test
    void theDeepEqualityCompoundsUnorderedListsOrdered() {
        Map<String, Snbt.Tag> left = Snbt.parseCompound(
                "{a:1, list:[1d,2d], nested:{x:1, y:2}}");
        Map<String, Snbt.Tag> sameRows = Snbt.parseCompound(
                "{nested:{y:2, x:1}, list:[1d,2d], a:1}");
        Map<String, Snbt.Tag> orderedList = Snbt.parseCompound(
                "{a:1, list:[2d,1d], nested:{x:1, y:2}}");
        Map<String, Snbt.Tag> differentRow = Snbt.parseCompound(
                "{a:1, list:[1d,2d], nested:{x:1, y:3}}");
        assertTrue(NbtOps.equal(left, sameRows), "compounds compare unordered");
        assertFalse(NbtOps.equal(left, orderedList), "lists compare in order");
        assertFalse(NbtOps.equal(left, differentRow));
        assertTrue(NbtOps.equal(left, NbtOps.copy(left)));
    }

    @Test
    void theWriterProducesTheReferenceForms() {
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{b:3b, s:5s, i:6, l:4L, f:2.5f, d:1.5d,"
                + " text:\"say \\\"hi\\\"\", ints:[1,2],"
                + " list:[0.0f,90.0f], nested:{Health:20b}}");
        String written = NbtOps.write(rows);
        // The per-type suffix forms and the list's index-colon shape.
        assertTrue(written.contains("b:3b"), written);
        assertTrue(written.contains("s:5s"), written);
        assertTrue(written.contains("i:6,"), written);
        assertTrue(written.contains("l:4L"), written);
        assertTrue(written.contains("f:2.5f"), written);
        assertTrue(written.contains("d:1.5d"), written);
        assertTrue(written.contains("text:\"say \\\"hi\\\"\""), written);
        assertTrue(written.contains("ints:[1,2,]"), "the int array's trailing comma");
        assertTrue(written.contains("list:[0:0.0f,1:90.0f]"), "the list index-colon form");
        assertTrue(written.contains("nested:{Health:20b}"), written);
        // Round-trip: the written form parses back to the equal compound.
        // (The int-array and list rows are left out: the 1.8.8 writer's own
        // asymmetries — the trailing-comma form [1,2,] does not REPARSE as
        // an int array (the empty last element falls the run to a string),
        // and the list's index-colon form [0:0.0f,1:90.0f] reparses its
        // elements as the strings "0:0.0f" — the reference's own
        // NbtList.toString shape, an output form, never a reparse form.)
        Map<String, Snbt.Tag> roundTripRows = Snbt.parseCompound(
                "{b:3b, s:5s, i:6, l:4L, f:2.5f, d:1.5d,"
                        + " text:\"say \\\"hi\\\"\","
                        + " nested:{Health:20b}}");
        assertTrue(NbtOps.equal(roundTripRows,
                Snbt.parseCompound(NbtOps.write(roundTripRows))),
                "the written form reparses: " + NbtOps.write(roundTripRows));
        // The empty compound's own form.
        assertEquals("{}", NbtOps.write(new java.util.LinkedHashMap<>()));
    }

    @Test
    void theCopyIsDeep() {
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{nested:{x:1}, list:[{a:1b}]}");
        Map<String, Snbt.Tag> copied = NbtOps.copy(rows);
        assertTrue(NbtOps.equal(rows, copied));
        copied.get("nested").asCompound().put("x", Snbt.Tag.ofInt(99));
        assertEquals(1, NbtOps.getInt(rows.get("nested").asCompound(), "x"));
        List<Snbt.Tag> copiedList = copied.get("list").asList();
        copiedList.get(0).asCompound().put("a", Snbt.Tag.ofByte((byte) 0));
        assertEquals(1, NbtOps.getByte(rows.get("list").asList().get(0).asCompound(), "a"));
    }
}
