package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

/**
 * An immutable point-in-time view of one entity (architecture §27.6:
 * the any-thread-consumable read surface).
 *
 * <p>Captured on the entity's simulation context; a plugin may hold,
 * compare, and inspect snapshots off-owner freely. A snapshot never
 * changes after capture — it is evidence, not a handle.</p>
 *
 * @param entityId   the engine id at capture time
 * @param type       the kind's canonical identifier
 * @param position   feet-anchored world position
 * @param yaw        horizontal rotation in degrees
 * @param velocityX  x velocity component (blocks per tick)
 * @param velocityY  y velocity component
 * @param velocityZ  z velocity component
 * @param health     remaining health at capture
 * @param maxHealth  the kind's maximum health
 * @param onGround   whether the entity was grounded
 * @param dead       whether the entity was dead or dying at capture
 */
public record EntitySnapshot(int entityId, Identifier type, Position position,
                             float yaw,
                             double velocityX, double velocityY, double velocityZ,
                             float health, float maxHealth,
                             boolean onGround, boolean dead) {

    /** The horizontal speed in blocks per tick. */
    public double horizontalSpeed() {
        return Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
    }
}
