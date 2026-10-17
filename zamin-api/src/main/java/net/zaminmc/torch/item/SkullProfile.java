package net.zaminmc.torch.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One player-head's profile (Wave K — vanilla {@code SkullBlockEntity}'s
 * Owner member, the GameProfile's NBT form): the {@code Name} and string
 * {@code Id} rows plus the {@code Properties} compound's per-key lists of
 * {@code {Value, Signature?}} rows (the skin's {@code textures} property
 * the client renders).
 *
 * <p>Validation mirrors the reference's {@code NbtUtils.readProfile} lines
 * 9-51: the name may be null or empty and the id may be null (a garbage
 * uuid string parses to null, never an exception), but a profile with
 * NEITHER never exists — the read's own gate answers null for an
 * empty-both compound. The properties keep their per-key list order and
 * are deeply immutable; a null-or-empty map means "no properties" (the
 * reference's writeProfile skips the compound then).</p>
 *
 * <p>The engine carries the profile as PARSED DATA, never the online
 * session's own consult: the reference's {@code updateProfile} (the
 * game-profile cache + the session service's texture fill) is an
 * out-of-engine service seam — a profile given to the engine is the
 * profile it keeps, exactly what a offline-mode vanilla server does for
 * an un-cached name.</p>
 *
 * @param name       the profile's name row (null or empty tolerated — the
 *                   string forms arrive non-empty by their own gates)
 * @param id         the profile's id row in its parsed form (null when the
 *                   row is absent or not a valid uuid string — the
 *                   reference's own try/catch)
 * @param properties the properties compound's map (null = absent): each
 *                   key a list of property rows, the order kept
 */
public record SkullProfile(String name, UUID id,
                           Map<String, List<Property>> properties) {

    public SkullProfile {
        boolean hasName = name != null && !name.isEmpty();
        boolean hasId = id != null;
        if (!hasName && !hasId) {
            throw new IllegalArgumentException(
                    "A profile carries a name or an id — an empty-both compound reads null");
        }
        properties = sanitizeProperties(properties);
    }

    /** One property row: the Value string and the optional Signature. */
    public record Property(String value, String signature) {
        public Property {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * @return a name-only profile (the {@code ExtraType} string form and
     *         the {@code SkullOwner} string form both parse to this — a
     *         null id, no properties).
     */
    public static SkullProfile ofName(String name) {
        return new SkullProfile(name, null, null);
    }

    private static Map<String, List<Property>> sanitizeProperties(
            Map<String, List<Property>> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        Map<String, List<Property>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<Property>> row : input.entrySet()) {
            if (row.getKey() == null || row.getValue() == null || row.getValue().isEmpty()) {
                continue; // an empty property list is the compound's own absence
            }
            copied.put(row.getKey(), List.copyOf(row.getValue()));
        }
        return copied.isEmpty() ? null : java.util.Collections.unmodifiableMap(copied);
    }
}
