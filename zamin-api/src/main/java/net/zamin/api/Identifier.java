package net.zamin.api;

import java.util.Objects;

/**
 * A namespaced identifier, the canonical identity for registry content
 * (for example {@code minecraft:stone}).
 *
 * <p>Identifiers are the engine's stable names. Numeric or protocol-specific
 * representations must never replace them inside the engine; those belong to
 * version adapters as translation data.</p>
 *
 * @param namespace the namespace, lowercase alphanumeric with {@code ._-}
 * @param value     the value inside the namespace, lowercase alphanumeric with {@code ._-/}
 */
public record Identifier(String namespace, String value) implements Comparable<Identifier> {

    public Identifier {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(value, "value");
        requirePattern(namespace, "namespace");
        requirePattern(value, "value");
    }

    public static Identifier of(String namespace, String value) {
        return new Identifier(namespace, value);
    }

    /**
     * Parses {@code "namespace:value"}. A missing namespace defaults to {@code minecraft}.
     */
    public static Identifier parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        int sep = raw.indexOf(':');
        if (sep < 0) {
            return new Identifier("minecraft", raw);
        }
        return new Identifier(raw.substring(0, sep), raw.substring(sep + 1));
    }

    private static void requirePattern(String part, String what) {
        if (part.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/';
            if (!ok) {
                throw new IllegalArgumentException(
                        what + " contains illegal character '" + c + "': " + part);
            }
        }
    }

    @Override
    public String toString() {
        return namespace + ":" + value;
    }

    @Override
    public int compareTo(Identifier o) {
        int byNamespace = namespace.compareTo(o.namespace);
        return byNamespace != 0 ? byNamespace : value.compareTo(o.value);
    }
}
