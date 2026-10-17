package net.zaminmc.torch.server.scoreboard;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One team (Team 1:1): the named membership group with the friendly-fire
 * pair, the visibility pair, the prefix/suffix decoration, and the chat
 * color. Every setter fires the update broadcast (action 2) through the
 * owning scoreboard, exactly like the reference's setters.
 */
public final class ScoreboardTeam {

    /** The visibility pair (AbstractTeam.Visibility 1:1). */
    public enum Visibility {
        ALWAYS("always"),
        NEVER("never"),
        HIDE_FOR_OTHER_TEAMS("hideForOtherTeams"),
        HIDE_FOR_OWN_TEAM("hideForOwnTeam");

        private final String key;

        Visibility(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        /** The reference's byKey (null on unknown). */
        public static Visibility byKey(String key) {
            for (Visibility visibility : values()) {
                if (visibility.key.equals(key)) {
                    return visibility;
                }
            }
            return null;
        }

        public static String[] keys() {
            String[] keys = new String[values().length];
            for (Visibility visibility : values()) {
                keys[visibility.ordinal()] = visibility.key;
            }
            return keys;
        }
    }

    private final EngineScoreboard scoreboard;
    private final String name;
    private final Set<String> members = new LinkedHashSet<>();
    private String displayName;
    private String prefix = "";
    private String suffix = "";
    private boolean allowFriendlyFire = true;
    private boolean showFriendlyInvisibles = true;
    private Visibility nameTagVisibility = Visibility.ALWAYS;
    private Visibility deathMessageVisibility = Visibility.ALWAYS;
    /** -1 = RESET (the reference's default). */
    private int colorId = -1;

    ScoreboardTeam(EngineScoreboard scoreboard, String name) {
        this.scoreboard = scoreboard;
        this.name = name;
        this.displayName = name;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        scoreboard.onTeamUpdated(this);
    }

    public Collection<String> members() {
        return members;
    }

    public String prefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
        scoreboard.onTeamUpdated(this);
    }

    public String suffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
        scoreboard.onTeamUpdated(this);
    }

    public String memberDisplayName(String member) {
        return prefix + member + suffix;
    }

    public boolean allowFriendlyFire() {
        return allowFriendlyFire;
    }

    public void setAllowFriendlyFire(boolean allowFriendlyFire) {
        this.allowFriendlyFire = allowFriendlyFire;
        scoreboard.onTeamUpdated(this);
    }

    public boolean showFriendlyInvisibles() {
        return showFriendlyInvisibles;
    }

    public void setShowFriendlyInvisibles(boolean showFriendlyInvisibles) {
        this.showFriendlyInvisibles = showFriendlyInvisibles;
        scoreboard.onTeamUpdated(this);
    }

    public Visibility nameTagVisibility() {
        return nameTagVisibility;
    }

    public void setNameTagVisibility(Visibility visibility) {
        this.nameTagVisibility = visibility;
        scoreboard.onTeamUpdated(this);
    }

    public Visibility deathMessageVisibility() {
        return deathMessageVisibility;
    }

    public void setDeathMessageVisibility(Visibility visibility) {
        this.deathMessageVisibility = visibility;
        scoreboard.onTeamUpdated(this);
    }

    /** The chat color id (-1 = RESET, the reference's default). */
    public int colorId() {
        return colorId;
    }

    public void setColorId(int colorId) {
        this.colorId = colorId;
    }

    /** The friendly flags byte (bit 0 fire, bit 1 see-invisibles). */
    public int packFriendlyFlags() {
        int flags = 0;
        if (allowFriendlyFire()) {
            flags |= 1;
        }
        if (showFriendlyInvisibles()) {
            flags |= 2;
        }
        return flags;
    }

    public void unpackFriendlyFlags(int flags) {
        setAllowFriendlyFire((flags & 1) > 0);
        setShowFriendlyInvisibles((flags & 2) > 0);
    }
}
