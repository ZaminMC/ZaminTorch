package net.zaminmc.torch.server.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The IP ban registry persisted as the historical {@code banned-ips.json}:
 * one JSON object keyed by the banned address, each value carrying the
 * Paper entry fields (uuid/name of the banning moment, created, source,
 * expires, reason) — hand-editable exactly like any Bukkit-family server.
 * Loaded at boot, rewritten on every /ban-ip and /pardon-ip.
 */
public final class IpBanStore {

    /** One IP ban entry (the historical banned-ips.json value shape). */
    public record Entry(String ip, String uuid, String name, String created,
                        String source, String expires, String reason) {
    }

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private IpBanStore(Path file) {
        this.file = file;
    }

    /** Loads banned-ips.json; a missing or unreadable file yields an empty store. */
    public static IpBanStore load(Path file) {
        IpBanStore store = new IpBanStore(file);
        if (!Files.exists(file)) {
            return store;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            // Deliberately dependency-free, the OpStore shape: the object is
            // keyed by the ip string; each value is a flat field set. The
            // tolerant walk pairs "ip": { ... } blocks in file order.
            java.util.regex.Matcher entry = java.util.regex.Pattern.compile(
                    "\"([^\"]+)\"\\s*:\\s*\\{([^{}]*)\\}").matcher(json);
            while (entry.find()) {
                String ip = entry.group(1);
                String body = entry.group(2);
                String uuid = stringField(body, "uuid");
                String name = stringField(body, "name");
                store.entries.put(ip, new Entry(ip,
                        uuid == null ? "unknown" : uuid,
                        name == null ? "unknown" : name,
                        orEmpty(stringField(body, "created")),
                        orDefault(stringField(body, "source"), "Server"),
                        orDefault(stringField(body, "expires"), "forever"),
                        orDefault(stringField(body, "reason"), "Banned by an operator")));
            }
        } catch (IOException | RuntimeException e) {
            store.entries.clear(); // a corrupt file degrades to an empty store
        }
        return store;
    }

    /** @return the active ban for the address, or null when clean. */
    public Entry banOf(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return entries.get(ip);
    }

    /** Bans the address; a re-ban replaces the entry. Persists immediately. */
    public void ban(String ip, String uuid, String name, String source, String reason) {
        Objects.requireNonNull(ip, "ip");
        entries.put(ip, new Entry(ip,
                uuid == null ? "unknown" : uuid,
                name == null ? "unknown" : name,
                now(), source, "forever",
                reason == null || reason.isBlank() ? "Banned by an operator" : reason));
        save();
    }

    /** Lifts the ban on the address. @return whether one existed. */
    public boolean pardon(String ip) {
        boolean removed = entries.remove(ip) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** @return an unmodifiable view of the entries (the /banlist ips output). */
    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    private void save() {
        StringBuilder json = new StringBuilder("{\n");
        List<Entry> all = new ArrayList<>(entries.values());
        for (int i = 0; i < all.size(); i++) {
            Entry e = all.get(i);
            json.append("  \"").append(e.ip()).append("\": {")
                    .append("\"uuid\": \"").append(e.uuid()).append("\", ")
                    .append("\"name\": \"").append(e.name()).append("\", ")
                    .append("\"created\": \"").append(e.created()).append("\", ")
                    .append("\"source\": \"").append(e.source()).append("\", ")
                    .append("\"expires\": \"").append(e.expires()).append("\", ")
                    .append("\"reason\": \"").append(e.reason()).append("\"}")
                    .append(i < all.size() - 1 ? ",\n" : "\n");
        }
        json.append("}\n");
        try {
            Files.createDirectories(file.getParent());
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

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
