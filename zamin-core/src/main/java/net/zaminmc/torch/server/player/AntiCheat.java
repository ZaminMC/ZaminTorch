package net.zaminmc.torch.server.player;

import net.zaminmc.torch.util.Position;

/**
 * The combat-reach math (the Acid/Hawk-adopted shape, re-implemented): the
 * eye point against the victim's expanded AABB. The 1.8 specifics the
 * community checks agree on: the victim hitbox is 0.1 larger per side than
 * its body box (vanilla's own slack), the eye rides 1.62 (1.54 sneaking),
 * and a small lag band keeps honest hits honest.
 */
public final class AntiCheat {

    /** The vanilla 1.8 attack reach in blocks (survival). */
    public static final double SURVIVAL_REACH = 3.0;
    /** The creative reach band (the historical 5.0). */
    public static final double CREATIVE_REACH = 5.0;
    /** The 1.8 hitbox slack (Grim: "vanilla, not uncertainty"). */
    public static final double HITBOX_MARGIN = 0.1;
    /** The ping-lenient band (the lag tolerance the community checks grant;
     * wide enough that honest diagonal hits at the movement cap stay legal). */
    public static final double LAG_TOLERANCE = 0.75;
    /** The kick threshold of the reach ladder (the decay runs against it). */
    public static final int REACH_KICK_VIOLATIONS = 20;

    /** The 1.8 standing eye height (EntityPlayer.getEyeHeight). */
    public static final double EYE_HEIGHT_STANDING = 1.62;
    /** The 1.8 sneaking eye height. */
    public static final double EYE_HEIGHT_SNEAKING = 1.54;

    private AntiCheat() {
    }

    /**
     * @return the distance from the attacker's eye point to the victim's
     * expanded AABB ({@code width x height} at {@code feet}, expanded by the
     * 0.1 vanilla slack), or 0 when the eye is inside the box.
     */
    public static double eyeToBoxDistance(Position eye, Position feet,
                                          double width, double height) {
        double half = width / 2.0 + HITBOX_MARGIN;
        double minX = feet.x() - half;
        double maxX = feet.x() + half;
        double minY = feet.y() - HITBOX_MARGIN;
        double maxY = feet.y() + height + HITBOX_MARGIN;
        double minZ = feet.z() - half;
        double maxZ = feet.z() + half;
        double dx = Math.max(Math.max(minX - eye.x(), 0), eye.x() - maxX);
        double dy = Math.max(Math.max(minY - eye.y(), 0), eye.y() - maxY);
        double dz = Math.max(Math.max(minZ - eye.z(), 0), eye.z() - maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** @return the reach budget for the mode plus the slack and the lag band. */
    public static double maxReach(boolean creative) {
        return (creative ? CREATIVE_REACH : SURVIVAL_REACH)
                + HITBOX_MARGIN + LAG_TOLERANCE;
    }
}
