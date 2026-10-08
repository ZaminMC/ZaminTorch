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
 * The operator registry persisted as the historical {@code ops.json}: one JSON
 * array of entries with the offline UUID, name, permission level and the
 * bypass flag. Loaded at boot, rewritten on every /op and /deop.
 */
public final class OpStore {

    /** One operator entry (the historical ops.json entry shape). */
    public record Entry(UUID uuid, String name, int level, boolean bypassesPlayerLimit) {
    }

    private final Path file;
    private final List<Entry> entries;

    private OpStore(Path file, List<Entry> entries) {
        this.file = file;
        this.entries = entries;
    }

    /** Loads ops.json; a missing or unreadable file yields an empty store. */
    public static OpStore load(Path file) {
        if (!Files.exists(file)) {
            return new OpStore(file, new ArrayList<>());
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Entry> parsed = new ArrayList<>();
            // Deliberately dependency-free: entries carry four fixed fields, so
            // a tolerant walk beats shipping a JSON parser for one file.
            java.util.regex.Matcher entry = java.util.regex.Pattern.compile(
                    "\\{[^{}]*\\}").matcher(json);
            while (entry.find()) {
                String body = entry.group();
                String uuid = stringField(body, "uuid");
                String name = stringField(body, "name");
                int level = intField(body, "level");
                boolean bypass = booleanField(body, "bypassesPlayerLimit");
                if (uuid != null && name != null) {
                    try {
                        parsed.add(new Entry(UUID.fromString(uuid), name, level, bypass));
                    } catch (IllegalArgumentException ignored) {
                        // a corrupt uuid line degrades to skip, never to a boot failure
                    }
                }
            }
            return new OpStore(file, parsed);
        } catch (IOException | RuntimeException e) {
            return new OpStore(file, new ArrayList<>());
        }
    }

    /** @return the operator level of the uuid (0 = not an operator). */
    public int level(UUID uuid) {
        for (Entry entry : entries) {
            if (entry.uuid().equals(uuid)) {
                return entry.level();
            }
        }
        return 0;
    }

    /** @return the operator entry of a name, case-insensitive, or null. */
    public Entry byName(String name) {
        for (Entry entry : entries) {
            if (entry.name().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    public boolean isOp(UUID uuid) {
        return level(uuid) > 0;
    }

    /** Grants (or re-levels) an operator; persists immediately. */
    public void op(UUID uuid, String name, int level) {
        Objects.requireNonNull(uuid, "uuid");
        remove(name);
        entries.add(new Entry(uuid, name, level, true));
        save();
    }

    /** Revokes by name (case-insensitive); persists immediately. */
    public boolean deop(String name) {
        boolean changed = remove(name);
        if (changed) {
            save();
        }
        return changed;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    private boolean remove(String name) {
        return entries.removeIf(e -> e.name().equalsIgnoreCase(name));
    }

    private void save() {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            json.append("  {\"uuid\": \"").append(e.uuid()).append("\", ")
                    .append("\"name\": \"").append(e.name()).append("\", ")
                    .append("\"level\": ").append(e.level()).append(", ")
                    .append("\"bypassesPlayerLimit\": ").append(e.bypassesPlayerLimit())
                    .append("}");
            json.append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        json.append("]\n");
        try {
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // The registry stays in memory; the next op rewrites the file.
        }
    }

    private static String stringField(String body, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static int intField(String body, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : 4;
    }

    private static boolean booleanField(String body, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(true|false)").matcher(body);
        return m.find() && Boolean.parseBoolean(m.group(1));
    }
}
