package net.zaminmc.torch.server.physics;

/**
 * The optional collision-surface capability (the physics-solver slice):
 * the world-facing query interfaces whose families move through the
 * swept-box {@link EntityMoveSolver} extend this — ONE declaration
 * shared by the {@code WorldQuery} and both {@code Ground} families, so
 * a class implementing several of them never meets the diamond.
 *
 * <p>The default answers null: a stub world without block reads keeps
 * its legacy point-query walk. The live server's providers override it
 * with the real dimension-aware surface.</p>
 */
public interface HasCollisionWorld {

    /**
     * The swept-box collision surface, or null when the world provides
     * none (the caller takes its legacy point-query fold).
     */
    default EntityCollisionWorld collisionWorld() {
        return null;
    }
}
