package net.zamin.engine.config;

/**
 * The player game mode the server runs (single server-wide mode this slice).
 * The mode changes observable interaction behavior: survival mining is
 * time-validated and yields drops; creative interaction is instant and
 * inventory-free.
 */
public enum GameMode {
    SURVIVAL,
    CREATIVE;

    /** The protocol-agnostic parse used by configuration. */
    public static GameMode parse(String raw) {
        return switch (raw.toLowerCase()) {
            case "survival" -> SURVIVAL;
            case "creative" -> CREATIVE;
            default -> throw new IllegalArgumentException(
                    "Unknown gamemode '" + raw + "' (expected survival or creative)");
        };
    }

    /** Historical wire value for protocol 47 join packets (adapter-owned usage). */
    public int legacyId() {
        return this == CREATIVE ? 1 : 0;
    }
}
