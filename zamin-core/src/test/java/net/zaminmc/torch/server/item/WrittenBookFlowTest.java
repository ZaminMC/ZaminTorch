package net.zaminmc.torch.server.item;

import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.stat.EngineStats;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE WRITTEN-BOOK FLOW (§29's Wave VIII row VIII12 — the reference's
 * {@code BookAndQuillItem}/{@code WrittenBookItem} and the
 * ServerPlayNetworkHandler's MC|BEdit/MC|BSign channels 1:1): the edit
 * channel's pages landing with the reference's own isValid gate, the sign
 * channel's conversion (the SERVER-authoritative author, the payload's
 * title/pages, the item swap), the read gate (the written book's
 * once-only resolve walk with the page normalization, NEVER an edit flow —
 * the structural refusal a signed book embodies for everyone including its
 * own author), the two use arms' item-use stats, the writable book's
 * crafting row (the audit's evidence pin — CraftingManager line 58's own
 * shapeless book + ink sac + feather with the dye's EXACT damage 0), the
 * ItemNbt anvil round trip, and the merge identity.
 */
class WrittenBookFlowTest {

    private final String worldPrefix = "wb" + Math.abs(UUID.randomUUID().hashCode() % 100000);

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    // --- the model-level laws ----------------------------------------------

    /**
     * The two isValid gates (the reference's own statics): a writable book
     * needs the pages row (an over-length page refuses); a written book
     * adds the title at most 32 characters and the author's presence.
     */
    @Test
    void theChannelGatesMatchTheReferenceIsValidLaws() {
        // A writable book without pages: refused (contains("pages", 9)).
        assertFalse(Books.validWritableBook(ItemStack.of(BuiltinItems.WRITABLE_BOOK)),
                "a book with no pages row is not valid");
        // With pages: valid.
        assertTrue(Books.validWritableBook(writable("Page one", "")),
                "the plain pages pass");
        // An over-length page refuses (line 38's 32767 cap).
        assertFalse(Books.validWritableBook(
                        ItemStack.of(BuiltinItems.WRITABLE_BOOK).withBookPages(
                                List.of("x".repeat(ItemStack.MAX_BOOK_PAGE_CHARS + 1)))),
                "the over-length page refuses the whole book");
        // The wrong item family: never valid.
        assertFalse(Books.validWritableBook(ItemStack.of(BuiltinItems.WRITTEN_BOOK)),
                "a written book never passes the writable gate");

        // The written-book gate: pages + title <= 32 + author.
        assertTrue(Books.validWrittenBook(signed("Abasing", "My Book", "\"Page\"")),
                "the full shape passes");
        assertFalse(Books.validWrittenBook(ItemStack.of(BuiltinItems.WRITTEN_BOOK)),
                "an empty written book is not valid");
        assertFalse(Books.validWrittenBook(
                        signed("Abasing", "123456789012345678901234567890123", "\"P\"")),
                "the 33-character title refuses");
        assertTrue(Books.validWrittenBook(
                        signed("Abasing", "12345678901234567890123456789012", "\"P\"")),
                "the 32-character title passes");
        assertFalse(Books.validWrittenBook(
                        signed(null, "My Book", "\"P\"")),
                "the missing author row refuses");
        assertFalse(Books.validWrittenBook(
                        ItemStack.of(BuiltinItems.WRITTEN_BOOK).withBookTitle("T")
                                .withBookAuthor("A")),
                "the missing pages row refuses");
    }

    /**
     * The read gate's resolve walk (resolvePages lines 79-109): the
     * once-only marker, the page normalization (a non-JSON page wraps as
     * the bare JSON string literal — the reference's
     * toJson(new LiteralText(s)) form — an already-JSON page rides
     * verbatim), and the invalid-tag arm (the marker lands, the pages
     * never walk).
     */
    @Test
    void theResolveWalkMatchesTheReferencePages() {
        // A plain-text page and a JSON page: the first wraps, the second
        // rides verbatim.
        ItemStack book = signed("Abasing", "My Book", "Page one", "\"already\"", "{\"text\":\"hi\"}");
        ItemStack resolved = Books.resolvePages(book);
        assertTrue(resolved.bookResolved(), "the marker armed");
        assertEquals(List.of("\"Page one\"", "\"already\"", "{\"text\":\"hi\"}"),
                resolved.bookPages(), "the plain page wrapped, the JSON pages verbatim");

        // The once-only gate: a resolved book never re-walks.
        assertSame(resolved, Books.resolvePages(resolved),
                "the resolved book is a no-op");

        // The invalid tag: the marker still lands, the pages never walk.
        ItemStack invalid = ItemStack.of(BuiltinItems.WRITTEN_BOOK)
                .withBookPages(List.of("Page one")); // no title/author
        ItemStack armed = Books.resolvePages(invalid);
        assertTrue(armed.bookResolved(), "the marker armed on the invalid tag");
        assertEquals(List.of("Page one"), armed.bookPages(),
                "the pages never walked (the isValid gate refused)");

        // The empty hand never resolves; a book & quill only ever enters
        // the walk through a direct call (the engine's use arm routes it
        // to the stat alone) — the method itself, like the reference's
        // resolvePages, carries NO type gate: it arms the marker on any
        // unresolved tag and the isValid consult refuses the pages walk.
        assertSame(ItemStack.EMPTY, Books.resolvePages(ItemStack.EMPTY),
                "the EMPTY stack is a no-op");
        ItemStack quill = writable("Page one");
        ItemStack quillWalked = Books.resolvePages(quill);
        assertTrue(quillWalked != quill && quillWalked.bookResolved(),
                "the method arms the marker on any unresolved tag");
        assertEquals(List.of("Page one"), quillWalked.bookPages(),
                "the isValid consult refuses the quill's page walk");
    }

    /**
     * The wrap arm's own serialization (the reference's bare JsonPrimitive
     * form): the standard JSON escapes, exactly GSON's set.
     */
    @Test
    void theWrapArmQuotesLikeTheReferenceLiteralForm() {
        assertEquals("\"hello\"", Books.quoteJson("hello"));
        assertEquals("\"with \\\"quotes\\\"\"", Books.quoteJson("with \"quotes\""));
        assertEquals("\"back\\\\slash\"", Books.quoteJson("back\\slash"));
        assertEquals("\"line\\nbreak\"", Books.quoteJson("line\nbreak"));
        // The JSON validity consult: the text forms pass, the prose fails.
        assertTrue(Books.isJsonText("\"quoted\""));
        assertTrue(Books.isJsonText("{\"text\":\"hi\"}"));
        assertTrue(Books.isJsonText("[\"a\",\"b\"]"));
        assertTrue(Books.isJsonText("true"));
        assertFalse(Books.isJsonText("Page one"), "the prose fails");
        assertFalse(Books.isJsonText(""), "the empty page fails");
        assertFalse(Books.isJsonText("{\"text\": }"), "the malformed object fails");
        assertFalse(Books.isJsonText("\"unterminated"), "the unterminated string fails");
    }

    /**
     * The ItemNbt anvil round trip (the reference's ItemStack writeNbt /
     * fromNbt shapes): the pages list, the author/title strings, the
     * resolved byte, and the generation int survive the whole walk — an
     * imported vanilla book lands byte-shape whole.
     */
    @Test
    void theAnvilRoundTripCarriesEveryBookRow() {
        ItemStack signed = signed("Abasing", "My Book", "\"Page one\"", "\"Page two\"")
                .withBookResolved(true)
                .withBookGeneration(2);
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> rows =
                new java.util.LinkedHashMap<>();
        net.zaminmc.torch.server.nbt.ItemNbt.write(signed, rows);
        // The tag compound carries the five rows in the reference's shapes.
        var tag = net.zaminmc.torch.server.nbt.NbtOps.getCompound(rows, "tag");
        var pageRows = net.zaminmc.torch.server.nbt.NbtOps.getList(
                tag, "pages", net.zaminmc.torch.server.nbt.Snbt.Tag.Type.STRING);
        assertEquals(2, pageRows.size(), "the pages list rides the tag");
        assertEquals("Abasing",
                net.zaminmc.torch.server.nbt.NbtOps.getString(tag, "author"));
        assertEquals("My Book",
                net.zaminmc.torch.server.nbt.NbtOps.getString(tag, "title"));
        assertTrue(net.zaminmc.torch.server.nbt.NbtOps.getBoolean(tag, "resolved"));
        assertEquals(2, net.zaminmc.torch.server.nbt.NbtOps.getInt(tag, "generation"));
        // The read walk lands the same model arms.
        ItemStack read = net.zaminmc.torch.server.nbt.ItemNbt.read(rows);
        assertNotNull(read);
        assertEquals(signed.bookPages(), read.bookPages());
        assertEquals("Abasing", read.bookAuthor());
        assertEquals("My Book", read.bookTitle());
        assertTrue(read.bookResolved());
        assertEquals(2, read.bookGeneration());
        // A bare writable book round-trips with its pages alone.
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> quillRows =
                new java.util.LinkedHashMap<>();
        net.zaminmc.torch.server.nbt.ItemNbt.write(writable("Draft"), quillRows);
        ItemStack quillRead = net.zaminmc.torch.server.nbt.ItemNbt.read(quillRows);
        assertEquals(List.of("Draft"), quillRead.bookPages());
        assertNull(quillRead.bookAuthor(), "a writable book carries no author row");
        assertNull(quillRead.bookTitle(), "a writable book carries no title row");
        assertFalse(quillRead.bookResolved());
        assertEquals(0, quillRead.bookGeneration());
    }

    /**
     * The merge identity (the reference's areItemStacksEqual whole-tag
     * compare): two books differing in any book row never merge — the
     * pages, the author, the title, the resolved byte, the generation.
     */
    @Test
    void theMergeIdentityConsultsEveryBookRow() {
        ItemStack a = signed("Abasing", "My Book", "\"P\"");
        assertTrue(ItemStack.mergeable(a, signed("Abasing", "My Book", "\"P\"")),
                "identical books merge");
        assertFalse(ItemStack.mergeable(a, signed("Abasing", "My Book", "\"Q\"")),
                "different pages never merge");
        assertFalse(ItemStack.mergeable(a, signed("Other", "My Book", "\"P\"")),
                "different authors never merge");
        assertFalse(ItemStack.mergeable(a, signed("Abasing", "Other", "\"P\"")),
                "different titles never merge");
        assertFalse(ItemStack.mergeable(a, a.withBookResolved(true)),
                "the resolved byte rides the identity");
        assertFalse(ItemStack.mergeable(a, a.withBookGeneration(1)),
                "the generation rides the identity");
    }

    /**
     * The crafting audit's evidence pin (VIII12's crafting sub-row): the
     * writable book's recipe is ALREADY 1:1 with the reference's
     * CraftingManager line 58 — the SHAPELESS book + ink sac (the dye's
     * EXACT damage 0) + feather. The exact-0 gate refuses every other dye
     * (a cocoa bean's damage 3 never crafts a book & quill).
     */
    @Test
    void theWritableBookCraftingRowMatchesTheReference() {
        net.zaminmc.torch.server.crafting.CraftingService matcher =
                net.zaminmc.torch.server.crafting.CraftingService.builtin();
        // The book + ink sac (dye damage 0) + feather crafts the quill —
        // the shapeless row over a 3x3 grid (the matcher's own form).
        ItemStack[] grid = new ItemStack[9];
        java.util.Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = ItemStack.of(BuiltinItems.BOOK);
        grid[1] = ItemStack.of(BuiltinItems.INK_SAC).withDamage(0);
        grid[2] = ItemStack.of(BuiltinItems.FEATHER);
        var matched = matcher.match(grid, 9);
        assertTrue(matched.isPresent(), "the book + ink sac + feather matches");
        assertEquals("minecraft:writable_book",
                matched.get().result().item().toString());
        // The exact-0 dye gate: a cocoa bean (dye damage 3) never crafts.
        ItemStack[] cocoa = new ItemStack[9];
        java.util.Arrays.fill(cocoa, ItemStack.EMPTY);
        cocoa[0] = ItemStack.of(BuiltinItems.BOOK);
        cocoa[1] = ItemStack.of(BuiltinItems.INK_SAC).withDamage(3);
        cocoa[2] = ItemStack.of(BuiltinItems.FEATHER);
        assertTrue(matcher.match(cocoa, 9).isEmpty(),
                "the cocoa bean refuses (the exact damage-0 gate)");
    }

    // --- the live flow over the engine's own arms --------------------------

    /**
     * The MC|BEdit landing (ServerPlayNetworkHandler lines 948-976): the
     * payload's pages land on the held book & quill; the invalid payload
     * and the wrong held item both land nothing (the IOException refusal
     * and the held-item gate).
     */
    @Test
    void theEditChannelLandsThePagesOnTheHeldQuill() throws Exception {
        EngineServer server = boot("wbedit");
        try {
            PlayerSession scribe = join(server, "Scribe");
            server.ticker().submit(() -> scribe.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.WRITABLE_BOOK)));
            await(() -> !scribe.inventory().held().isEmpty(), "the quill held");

            // The channel lands: the payload's pages replace the held
            // book's pages (the reference's addToNbt("pages", ...)).
            server.bookEdit(scribe, writable("First page", "Second page"));
            await(() -> scribe.inventory().held().bookPages() != null
                            && scribe.inventory().held().bookPages().size() == 2,
                    "the pages landed on the held quill");
            assertEquals(List.of("First page", "Second page"),
                    scribe.inventory().held().bookPages());

            // The invalid payload (an over-length page): lands nothing.
            server.bookEdit(scribe, ItemStack.of(BuiltinItems.WRITABLE_BOOK)
                    .withBookPages(List.of("x".repeat(ItemStack.MAX_BOOK_PAGE_CHARS + 1))));
            Thread.sleep(150);
            assertEquals(2, scribe.inventory().held().bookPages().size(),
                    "the invalid payload never lands");

            // The wrong payload item (a written book claiming pages):
            // lands nothing.
            server.bookEdit(scribe, signed("Scribe", "T", "\"P\""));
            Thread.sleep(150);
            assertTrue(scribe.inventory().held().type().equals(BuiltinItems.WRITABLE_BOOK),
                    "the written-book payload never edits a quill");

            // The wrong held item (a written book held): the channel gates
            // on the held WRITABLE_BOOK alone — a signed book, its own
            // author included, NEVER re-opens the edit flow (the read
            // gate's structural refusal).
            server.ticker().submit(() -> scribe.inventory().setSlot(0,
                    signed("Scribe", "Signed", "\"P\"")));
            await(() -> scribe.inventory().held().type().equals(BuiltinItems.WRITTEN_BOOK),
                    "the signed book held");
            server.bookEdit(scribe, writable("hijack"));
            Thread.sleep(150);
            assertEquals(List.of("\"P\""), scribe.inventory().held().bookPages(),
                    "the edit channel refuses a held written book");
        } finally {
            stop(server);
        }
    }

    /**
     * The MC|BSign landing (ServerPlayNetworkHandler lines 977-1008): the
     * signing conversion — the author is the SERVER's own name for the
     * player (never the payload's claim), the title and pages ride the
     * payload, and the held stack swaps to minecraft:written_book. The
     * invalid payload (the missing author row, the over-length title) and
     * the wrong held item both land nothing.
     */
    @Test
    void theSignChannelConvertsTheHeldQuill() throws Exception {
        EngineServer server = boot("wbsign");
        try {
            PlayerSession scribe = join(server, "Scribe");
            server.ticker().submit(() -> scribe.inventory().setSlot(0,
                    writable("Page one", "Page two")));
            await(() -> !scribe.inventory().held().isEmpty(), "the quill held");

            // The sign lands: the client's payload (a written-book stack
            // whose author row the client filled with its OWN name claim).
            server.bookSign(scribe, signed("Mallory", "My Book", "\"Page one\"", "\"Page two\""));
            await(() -> scribe.inventory().held().type().equals(BuiltinItems.WRITTEN_BOOK),
                    "the held stack swapped to the written book");
            ItemStack signed = scribe.inventory().held();
            assertEquals("Scribe", signed.bookAuthor(),
                    "the author is the SERVER's name for the signing player");
            assertEquals("My Book", signed.bookTitle(), "the title rides the payload");
            assertEquals(List.of("\"Page one\"", "\"Page two\""), signed.bookPages());
            assertEquals(1, signed.count(), "the count rides the held stack");
            assertFalse(signed.bookResolved(), "the fresh signature is unresolved");

            // The once-only edit refusal: a held WRITTEN book never
            // converts again (the sign channel gates on the held quill).
            server.bookSign(scribe, signed("Mallory", "Again", "\"P\""));
            Thread.sleep(150);
            assertEquals("My Book", scribe.inventory().held().bookTitle(),
                    "the signed book never re-signs");

            // The empty hand: nothing lands.
            server.ticker().submit(() -> scribe.inventory().setSlot(0, ItemStack.EMPTY));
            await(() -> scribe.inventory().held().isEmpty(), "the hand emptied");
            server.bookSign(scribe, signed("Mallory", "T", "\"P\""));
            Thread.sleep(150);
            assertTrue(scribe.inventory().held().isEmpty(), "the empty hand converts nothing");

            // The invalid payload: the missing author row refuses.
            server.ticker().submit(() -> scribe.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.WRITABLE_BOOK)));
            await(() -> !scribe.inventory().held().isEmpty(), "the quill held again");
            server.bookSign(scribe, ItemStack.of(BuiltinItems.WRITTEN_BOOK)
                    .withBookTitle("T").withBookPages(List.of("\"P\"")));
            Thread.sleep(150);
            assertTrue(scribe.inventory().held().type().equals(BuiltinItems.WRITABLE_BOOK),
                    "the authorless payload never converts");
        } finally {
            stop(server);
        }
    }

    /**
     * The read gate (WrittenBookItem.startUsing lines 68-77): using a
     * written book runs the resolve walk ONCE (the plain page normalizes,
     * the marker arms, the resync fires) and pays the item-use stat; a
     * second use never re-walks. The book & quill's own use pays the stat
     * alone (the client owns its edit screen).
     */
    @Test
    void theReadGateResolvesOnceAndPaysTheStat() throws Exception {
        EngineServer server = boot("wbread");
        try {
            PlayerSession reader = join(server, "Reader");
            // A signed book carrying one plain page (an imported oddity).
            server.ticker().submit(() -> reader.inventory().setSlot(0,
                    signed("Abasing", "My Book", "Page one")));
            await(() -> !reader.inventory().held().isEmpty(), "the book held");

            String statKey = EngineStats.useItemOf(BuiltinItems.WRITTEN_BOOK.identifier());
            server.useItem(reader);
            await(() -> reader.stats().get(statKey) >= 1,
                    "the item-use stat paid");
            await(() -> reader.inventory().held().bookResolved(),
                    "the resolve walk armed the marker");
            assertEquals(List.of("\"Page one\""), reader.inventory().held().bookPages(),
                    "the plain page normalized to the JSON literal form");

            // The second use: no re-walk (the once-only gate), the stat
            // pays again (the reference counts every use).
            int uses = reader.stats().snapshot().get(statKey);
            server.useItem(reader);
            await(() -> reader.stats().snapshot().get(statKey) == uses + 1,
                    "the second use pays the stat again");
            assertEquals(List.of("\"Page one\""), reader.inventory().held().bookPages(),
                    "the resolved pages never re-walk");

            // The book & quill's use: the stat alone.
            server.ticker().submit(() -> reader.inventory().setSlot(0,
                    writable("Draft")));
            await(() -> reader.inventory().held().type().equals(BuiltinItems.WRITABLE_BOOK),
                    "the quill held");
            String quillKey = EngineStats.useItemOf(BuiltinItems.WRITABLE_BOOK.identifier());
            server.useItem(reader);
            await(() -> reader.stats().get(quillKey) >= 1,
                    "the quill's use pays the stat");
            assertEquals(List.of("Draft"), reader.inventory().held().bookPages(),
                    "the quill's use never touches the pages");
        } finally {
            stop(server);
        }
    }

    // --- the fixtures -------------------------------------------------------

    private static ItemStack writable(String... pages) {
        return ItemStack.of(BuiltinItems.WRITABLE_BOOK).withBookPages(List.of(pages));
    }

    private static ItemStack signed(String author, String title, String... pages) {
        return ItemStack.of(BuiltinItems.WRITTEN_BOOK)
                .withBookAuthor(author)
                .withBookTitle(title)
                .withBookPages(List.of(pages));
    }

    private EngineServer boot(String worldName) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, worldName, "it",
                20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static PlayerSession join(EngineServer server, String name) {
        List<String> sink = new ArrayList<>();
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
            @Override public void blockResync(net.zaminmc.torch.block.BlockPosition position,
                    net.zaminmc.torch.block.BlockType type) {
                sink.add(position + "=" + type.identifier());
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static void stop(EngineServer server) {
        server.shutdown(null);
        try {
            long deadline = System.currentTimeMillis() + 8_000;
            while (System.currentTimeMillis() < deadline) {
                if (server.state() == ServerState.STOPPED) {
                    return;
                }
                Thread.sleep(25);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
