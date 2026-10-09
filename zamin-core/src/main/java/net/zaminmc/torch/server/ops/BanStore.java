package net.zaminmc.torch.server.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The ban registry persisted as the historical {@code banned-players.json}:
 * one JSON array of entries with the offline UUID, name, creation stamp,
 * source, expiry and reason — the Paper shape, so server admins can hand-edit
 * it exactly like any Bukkit-family server. Loaded at boot, rewritten on
 * every /ban and /pardon.
 *
 * <p>Offline-mode note: bans key on the offline UUID (deterministic from the
 * name), so a banned name stays banned across relogins just like the
 * historical offline server. The companion {@code banned-ips.json} stays an
 * empty store this slice — offline connections rotate addresses too freely
 * for an IP ban to mean anything.</p>
 */
public final class BanStore {

    /** One ban entry (the historical banned-players.json entry shape). */
    public record Entry(UUID uuid, String name, String created, String source,
                        String expires, String reason) {
        /** @return whether the entry is a permanent ban (no expiry parsing yet). */
        public boolean permanent() {
            return "forever".equalsIgnoreCase(expires);
        }
    }

    private final Path file;
    private final List<Entry> entries;

    private BanStore(Path file, List<Entry> entries) {
        this.file = file;
        this.entries = entries;
    }

    /** Loads banned-players.json; a missing or unreadable file yields an empty store. */
    public static BanStore load(Path file) {
        if (!Files.exists(file)) {
            return new BanStore(file, new ArrayList<>());
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Entry> parsed = new ArrayList<>();
            // Deliberately dependency-free, the OpStore shape: six fixed
            // string fields per entry, so a tolerant walk beats a parser.
            java.util.regex.Matcher entry = java.util.regex.Pattern.compile(
                    "\\{[^{}]*\\}").matcher(json);
            while (entry.find()) {
                String body = entry.group();
                String uuid = stringField(body, "uuid");
                String name = stringField(body, "name");
                if (uuid == null || name == null) {
                    continue;
                }
                String created = stringField(body, "created");
                String source = stringField(body, "source");
                String expires = stringField(body, "expires");
                String reason = stringField(body, "reason");
                try {
                    parsed.add(new Entry(UUID.fromString(uuid), name,
                            created == null ? "" : created,
                            source == null ? "Server" : source,
                            expires == null ? "forever" : expires,
                            reason == null ? "Banned by an operator" : reason));
                } catch (IllegalArgumentException ignored) {
                    // a corrupt uuid line degrades to skip, never to a boot failure
                }
            }
            return new BanStore(file, parsed);
        } catch (IOException | RuntimeException e) {
            return new BanStore(file, new ArrayList<>());
        }
    }

    /** @return the active ban for the uuid or name, or null when clean. */
    public Entry banOf(UUID uuid, String name) {
        for (Entry entry : entries) {
            if (entry.uuid().equals(uuid)
                    || entry.name().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    /** Bans by uuid+name; a re-ban replaces the entry. Persists immediately. */
    public void ban(UUID uuid, String name, String source, String reason) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        entries.removeIf(e -> e.name().equalsIgnoreCase(name));
        entries.add(new Entry(uuid, name, now(), source, "forever",
                reason == null || reason.isBlank() ? "Banned by an operator" : reason));
        save();
    }

    /** Lifts the ban by name (case-insensitive). @return whether one existed. */
    public boolean pardon(String name) {
        boolean removed = entries.removeIf(e -> e.name().equalsIgnoreCase(name));
        if (removed) {
            save();
        }
        return removed;
    }

    /** @return an unmodifiable view of the entries (the /banlist output). */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    private void save() {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            json.append("  {\"uuid\": \"").append(e.uuid()).append("\", ")
                    .append("\"name\": \"").append(e.name()).append("\", ")
                    .append("\"created\": \"").append(e.created()).append("\", ")
                    .append("\"source\": \"").append(e.source()).append("\", ")
                    .append("\"expires\": \"").append(e.expires()).append("\", ")
                    .append("\"reason\": \"").append(e.reason()).append("\"}")
                    .append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        json.append("]\n");
        try {
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // The registry stays in memory; the next ban rewrites the file.
        }
    }

    /** The historical created stamp: "yyyy-MM-dd HH:mm:ss +0000". */
    private static String now() {
        return java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern(
                        "yyyy-MM-dd HH:mm:ss Z"));
    }

    private static String stringField(String body, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }
}
