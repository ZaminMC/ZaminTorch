package net.minecraft.scoreboard.team;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;

public abstract class AbstractTeam {
    public boolean isAlliedTo(AbstractTeam team) {
        return team != null && this == team;
    }

    public abstract String getName();

    public abstract String getMemberDisplayName(String member);

    public abstract boolean showFriendlyInvisibles();

    public abstract boolean allowFriendlyFire();

    public abstract AbstractTeam.Visibility getNameTagVisibility();

    public abstract Collection<String> getMembers();

    public abstract AbstractTeam.Visibility getDeathMessageVisibility();

    public enum Visibility {
        ALWAYS("always", 0),
        NEVER("never", 1),
        HIDE_FOR_OTHER_TEAMS("hideForOtherTeams", 2),
        HIDE_FOR_OWN_TEAM("hideForOwnTeam", 3);

        private static Map<String, AbstractTeam.Visibility> BY_KEY = Maps.newHashMap();
        public final String key;
        public final int id;

        public static String[] getKeys() {
            return BY_KEY.keySet().toArray(new String[BY_KEY.size()]);
        }

        public static AbstractTeam.Visibility byKey(String key) {
            return BY_KEY.get(key);
        }

        Visibility(String key, int id) {
            this.key = key;
            this.id = id;
        }

        static {
            for (AbstractTeam.Visibility abstractteam$visibility : values()) {
                BY_KEY.put(abstractteam$visibility.key, abstractteam$visibility);
            }
        }
    }
}
