package net.zamin.api;

/**
 * Explicit rotation. Yaw is horizontal rotation in degrees (0 = south, increasing
 * clockwise as Minecraft defines it), pitch is vertical rotation (-90 up, 90 down).
 *
 * <p>A dedicated type prevents undocumented float pairs from flowing through the engine.</p>
 */
public record Rotation(float yaw, float pitch) {

    public static final Rotation ZERO = new Rotation(0.0f, 0.0f);

    public boolean isFinite() {
        return Float.isFinite(yaw) && Float.isFinite(pitch);
    }
}
