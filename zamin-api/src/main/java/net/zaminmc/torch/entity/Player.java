package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.World;

import java.util.UUID;

/**
 * The authoritative gameplay identity of a connected player.
 *
 * <p>A player is not a connection: the connection belongs to the protocol layer,
 * this object belongs to the engine and outlives transient wire state.</p>
 */
public interface Player {

    UUID uuid();

    String name();

    PlayerState state();

    World world();

    Position position();

    Rotation rotation();

    boolean onGround();
}
