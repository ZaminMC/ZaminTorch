package net.zaminmc.torch.server.text;

import java.util.HashMap;
import java.util.Map;

/**
 * The 1.8.8 click event (ClickEvent.java 1:1) with the reference's own
 * action table — including the two actions a remote source may NOT install
 * (open_file and twitch_user_info).
 */
public final class ClickEvent {

    private final ClickEvent.Action action;
    private final String value;

    public ClickEvent(ClickEvent.Action action, String value) {
        this.action = action;
        this.value = value;
    }

    public ClickEvent.Action getAction() {
        return this.action;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && this.getClass() == object.getClass()) {
            ClickEvent clickevent = (ClickEvent) object;
            if (this.action != clickevent.action) {
                return false;
            }
            return this.value != null ? this.value.equals(clickevent.value) : clickevent.value == null;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int i = this.action.hashCode();
        return 31 * i + (this.value != null ? this.value.hashCode() : 0);
    }

    @Override
    public String toString() {
        return "ClickEvent{action=" + this.action + ", value='" + this.value + "'}";
    }

    /** The reference's own action table (the 1.8.8 six, verbatim flags). */
    public enum Action {
        OPEN_URL("open_url", true),
        OPEN_FILE("open_file", false),
        RUN_COMMAND("run_command", true),
        TWITCH_USER_INFO("twitch_user_info", false),
        SUGGEST_COMMAND("suggest_command", true),
        CHANGE_PAGE("change_page", true);

        private static final Map<String, ClickEvent.Action> BY_KEY = new HashMap<>();
        private final boolean allowFromRemoteSource;
        private final String key;

        Action(String key, boolean allowFromRemoteSource) {
            this.key = key;
            this.allowFromRemoteSource = allowFromRemoteSource;
        }

        public boolean allowFromRemoteSource() {
            return this.allowFromRemoteSource;
        }

        public String getKey() {
            return this.key;
        }

        public static ClickEvent.Action byKey(String key) {
            return BY_KEY.get(key);
        }

        static {
            for (ClickEvent.Action action : values()) {
                BY_KEY.put(action.getKey(), action);
            }
        }
    }
}
