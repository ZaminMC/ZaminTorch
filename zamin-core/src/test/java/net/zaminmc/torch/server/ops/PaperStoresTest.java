package net.zaminmc.torch.server.ops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Paper-store contracts: bans key on the offline identity and survive a
 * restart (the hand-edited file loads too), the whitelist roster matches by
 * uuid or name, and every mutation persists immediately.
 */
class PaperStoresTest {

    @TempDir
    Path temp;

    @Test
    void bansRoundTripThroughTheFile() throws Exception {
        Path file = temp.resolve("banned-players.json");
        UUID griefer = UUID.nameUUIDFromBytes("OfflinePlayer:Griefer".getBytes());
        BanStore store = BanStore.load(file);
        store.ban(griefer, "Griefer", "Console", "Griefing the spawn");
        assertTrue(Files.exists(file), "the ban persists immediately");

        BanStore reloaded = BanStore.load(file);
        BanStore.Entry entry = reloaded.banOf(griefer, "Griefer");
        assertNotNull(entry, "the ban survives a restart");
        assertEquals("Griefing the spawn", entry.reason());
        assertEquals("Console", entry.source());
        assertTrue(entry.permanent());

        // Either identity key finds the entry (name or uuid).
        assertNotNull(reloaded.banOf(UUID.randomUUID(), "griefer"));
        assertNotNull(reloaded.banOf(griefer, "SomeoneElse"));

        assertTrue(reloaded.pardon("GRIEFER"), "pardon is case-insensitive");
        assertNull(reloaded.banOf(griefer, "Griefer"));
        assertNull(BanStore.load(file).banOf(griefer, "Griefer"), "the pardon persists");
    }

    @Test
    void handEditedPaperBansLoad() throws Exception {
        // The Paper shape: exactly the fields a Bukkit-family admin edits.
        Path file = temp.resolve("banned-players.json");
        Files.writeString(file, """
                [
                  {
                    "uuid": "11111111-2222-3333-4444-555555555555",
                    "name": "RuleBreaker",
                    "created": "2026-10-09 10:00:00 +0000",
                    "source": "Server",
                    "expires": "forever",
                    "reason": "Banned by an operator"
                  }
                ]
                """);
        BanStore.Entry entry = BanStore.load(file).banOf(
                UUID.fromString("11111111-2222-3333-4444-555555555555"), "RuleBreaker");
        assertNotNull(entry);
        assertEquals("RuleBreaker", entry.name());
    }

    @Test
    void whitelistRosterMatchesAndPersists() throws Exception {
        Path file = temp.resolve("whitelist.json");
        UUID builder = UUID.nameUUIDFromBytes("OfflinePlayer:Builder".getBytes());
        WhitelistStore store = WhitelistStore.load(file);
        store.add(builder, "Builder");

        WhitelistStore reloaded = WhitelistStore.load(file);
        assertTrue(reloaded.contains(builder, "Builder"));
        assertTrue(reloaded.contains(UUID.randomUUID(), "BUILDer"), "name matches caseless");
        assertTrue(reloaded.contains(builder, "Renamed"), "uuid matches regardless of name");
        assertFalse(reloaded.contains(UUID.randomUUID(), "Intruder"));

        assertTrue(reloaded.remove("builder"));
        assertFalse(WhitelistStore.load(file).contains(builder, "Builder"));
    }

    @Test
    void missingOrCorruptFilesYieldEmptyStores() throws Exception {
        assertTrue(BanStore.load(temp.resolve("absent.json")).entries().isEmpty());
        assertTrue(WhitelistStore.load(temp.resolve("absent.json")).entries().isEmpty());
        Path junk = temp.resolve("junk.json");
        Files.writeString(junk, "not json at all ]{");
        assertTrue(BanStore.load(junk).entries().isEmpty());
        assertTrue(WhitelistStore.load(junk).entries().isEmpty());
    }
}
