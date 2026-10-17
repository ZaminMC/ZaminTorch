package net.zaminmc.torch.server.scoreboard;

import java.util.HashMap;
import java.util.Map;

/**
 * The score criteria (ScoreboardCriterion 1:1): the named table the
 * {@code /scoreboard objectives add} walk consults, each with its read-only
 * flag and render type. The colored families (teamkill.&lt;color&gt; /
 * killedByTeam.&lt;color&gt;) register one criterion per chat color, exactly
 * like the reference's static arrays.
 *
 * <p>The health criterion is READ-ONLY (the score is computed from the
 * player's body, never set) and renders HEARTS; every other criterion
 * renders INTEGER and is command-writable. The trigger criterion is
 * command-writable but gated behind {@code players enable} (the
 * /trigger command's own contract — a later slice).</p>
 */
public final class ScoreboardCriterion {

    /** The render type (the objective packet's own string key). */
    public enum RenderType {
        INTEGER("integer"),
        HEARTS("hearts");

        private final String key;

        RenderType(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        /** The reference's byKey: an unknown key degrades to INTEGER. */
        public static RenderType byKey(String key) {
            return "hearts".equals(key) ? HEARTS : INTEGER;
        }
    }

    private final String name;
    private final boolean readOnly;
    private final RenderType renderType;

    private ScoreboardCriterion(String name, boolean readOnly, RenderType renderType) {
        this.name = name;
        this.readOnly = readOnly;
        this.renderType = renderType;
        BY_NAME.put(name, this);
    }

    public String name() {
        return name;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public RenderType renderType() {
        return renderType;
    }

    /** The named registry (the reference's BY_NAME map, ctor-registered). */
    public static final Map<String, ScoreboardCriterion> BY_NAME = new HashMap<>();

    public static final ScoreboardCriterion DUMMY =
            new ScoreboardCriterion("dummy", false, RenderType.INTEGER);
    public static final ScoreboardCriterion TRIGGER =
            new ScoreboardCriterion("trigger", false, RenderType.INTEGER);
    public static final ScoreboardCriterion DEATH_COUNT =
            new ScoreboardCriterion("deathCount", false, RenderType.INTEGER);
    public static final ScoreboardCriterion PLAYER_KILL_COUNT =
            new ScoreboardCriterion("playerKillCount", false, RenderType.INTEGER);
    public static final ScoreboardCriterion TOTAL_KILL_COUNT =
            new ScoreboardCriterion("totalKillCount", false, RenderType.INTEGER);
    public static final ScoreboardCriterion HEALTH =
            new ScoreboardCriterion("health", true, RenderType.HEARTS);

    /** The chat colors (Formatting's own id order) driving the colored families. */
    static final String[] COLORS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"
    };

    /** teamkill.&lt;color&gt; per color (the reference's TEAM_KILL_BY_COLOR array). */
    public static final ScoreboardCriterion[] TEAM_KILL_BY_COLOR = byColorPrefix("teamkill.");
    /** killedByTeam.&lt;color&gt; per color (KILLED_BY_TEAM_BY_COLOR). */
    public static final ScoreboardCriterion[] KILLED_BY_TEAM_BY_COLOR =
            byColorPrefix("killedByTeam.");

    private static ScoreboardCriterion[] byColorPrefix(String prefix) {
        ScoreboardCriterion[] family = new ScoreboardCriterion[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) {
            family[i] = new ScoreboardCriterion(prefix + COLORS[i], false, RenderType.INTEGER);
        }
        return family;
    }
}
