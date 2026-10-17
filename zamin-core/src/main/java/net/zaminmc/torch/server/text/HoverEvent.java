package net.zaminmc.torch.server.text;

import java.util.HashMap;
import java.util.Map;

/**
 * The 1.8.8 hover event (HoverEvent.java 1:1) over the engine's text model.
 */
public final class HoverEvent {

    private final HoverEvent.Action action;
    private final Text value;

    public HoverEvent(HoverEvent.Action action, Text value) {
        this.action = action;
        this.value = value;
    }

    public HoverEvent.Action getAction() {
        return this.action;
    }

    public Text getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && this.getClass() == object.getClass()) {
            HoverEvent hoverevent = (HoverEvent) object;
            if (this.action != hoverevent.action) {
                return false;
            }
            return this.value != null ? this.value.equals(hoverevent.value) : hoverevent.value == null;
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
        return "HoverEvent{action=" + this.action + ", value='" + this.value + "'}";
    }

    /** The reference's own action table (all four remote-allowed in 1.8.8). */
    public enum Action {
        SHOW_TEXT("show_text", true),
        SHOW_ACHIEVEMENT("show_achievement", true),
        SHOW_ITEM("show_item", true),
        SHOW_ENTITY("show_entity", true);

        private static final Map<String, HoverEvent.Action> BY_KEY = new HashMap<>();
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

        public static HoverEvent.Action byKey(String key) {
            return BY_KEY.get(key);
        }

        static {
            for (HoverEvent.Action action : values()) {
                BY_KEY.put(action.getKey(), action);
            }
        }
    }
}
