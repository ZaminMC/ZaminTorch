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
 * The whitelist registry persisted as the historical {@code whitelist.json}:
 * one JSON array of {@code {uuid, name}} entries — the Paper shape, editable
 * by hand exactly like any Bukkit-family server. Enforcement is the engine's
 * {@code white-list} server.properties flag; this store only holds the roster.
 * Loaded at boot, rewritten on every whitelist mutation.
 */
public final class WhitelistStore {

    /** One whitelist entry (the historical whitelist.json entry shape). */
    public record Entry(UUID uuid, String name) {
    }

    private final Path file;
    private final List<Entry> entries;

    private WhitelistStore(Path file, List<Entry> entries) {
        this.file = file;
        this.entries = entries;
    }

    /** Loads whitelist.json; a missing or unreadable file yields an empty store. */
    public static WhitelistStore load(Path file) {
        if (!Files.exists(file)) {
            return new WhitelistStore(file, new ArrayList<>());
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Entry> parsed = new ArrayList<>();
            java.util.regex.Matcher entry = java.util.regex.Pattern.compile(
                    "\\{[^{}]*\\}").matcher(json);
            while (entry.find()) {
                String body = entry.group();
                String uuid = stringField(body, "uuid");
                String name = stringField(body, "name");
                if (uuid == null || name == null) {
                    continue;
                }
                try {
                    parsed.add(new Entry(UUID.fromString(uuid), name));
                } catch (IllegalArgumentException ignored) {
                    // a corrupt uuid line degrades to skip, never to a boot failure
                }
            }
            return new WhitelistStore(file, parsed);
        } catch (IOException | RuntimeException e) {
            return new WhitelistStore(file, new ArrayList<>());
        }
    }

    /** @return whether the uuid or name carries a whitelist entry. */
    public boolean contains(UUID uuid, String name) {
        for (Entry entry : entries) {
            if (entry.uuid().equals(uuid) || entry.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Adds (or refreshes) a whitelist entry; persists immediately. */
    public void add(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        remove(name);
        entries.add(new Entry(uuid, name));
        save();
    }

    /** Removes by name (case-insensitive). @return whether one existed. */
    public boolean remove(String name) {
        boolean removed = entries.removeIf(e -> e.name().equalsIgnoreCase(name));
        if (removed) {
            save();
        }
        return removed;
    }

    /** @return an unmodifiable view of the entries (the /whitelist list). */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    private void save() {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            json.append("  {\"uuid\": \"").append(e.uuid()).append("\", ")
                    .append("\"name\": \"").append(e.name()).append("\"}")
                    .append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        json.append("]\n");
        try {
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // The roster stays in memory; the next mutation rewrites the file.
        }
    }

    private static String stringField(String body, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }
}
