package net.zamin.engine.world;

import java.util.Optional;

/**
 * The world persistence boundary. Implementations own the on-disk representation;
 * the world model never knows file formats (§13/§284).
 */
public interface WorldStorage {

    /** Persists a consistent snapshot. Blocks until durable. */
    void save(WorldDeltaSnapshot snapshot);

    /** @return the previously saved snapshot, or empty when none exists. */
    Optional<WorldDeltaSnapshot> load();
}
