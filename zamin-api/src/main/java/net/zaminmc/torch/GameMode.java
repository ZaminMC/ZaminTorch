package net.zaminmc.torch;

/**
 * The four historical player game modes (org.bukkit.GameMode naming).
 *
 * <ul>
 *   <li>SURVIVAL — time-validated mining, drops, hunger, damage.</li>
 *   <li>CREATIVE — instant interaction, full inventory freedom, flight,
 *       immune to damage.</li>
 *   <li>ADVENTURE — survival interaction without block breaking or placing.</li>
 *   <li>SPECTATOR — no interaction at all, invisible, flight through blocks.</li>
 * </ul>
 */
public enum GameMode {
    SURVIVAL(0),
    CREATIVE(1),
    ADVENTURE(2),
    SPECTATOR(3);

    private final int legacyId;

    GameMode(int legacyId) {
        this.legacyId = legacyId;
    }

    /** The protocol-47 wire value (Join Game / Respawn / Change Game State). */
    public int legacyId() {
        return legacyId;
    }

    /**
     * The protocol-agnostic parse: accepts the historical names plus the
     * numeric ids (the /gamemode shorthand), and the single-letter shorthands
     * of the community command style (c/s/a/sp).
     */
    public static GameMode parse(String raw) {
        return switch (raw.toLowerCase()) {
            case "0", "s", "survival" -> SURVIVAL;
            case "1", "c", "creative" -> CREATIVE;
            case "2", "a", "adventure" -> ADVENTURE;
            case "3", "sp", "spectator" -> SPECTATOR;
            default -> throw new IllegalArgumentException(
                    "Unknown game mode '" + raw + "'");
        };
    }

    /** @return the mode for a protocol id, or null when out of range. */
    public static GameMode byLegacyId(int id) {
        for (GameMode mode : values()) {
            if (mode.legacyId == id) {
                return mode;
            }
        }
        return null;
    }
}
