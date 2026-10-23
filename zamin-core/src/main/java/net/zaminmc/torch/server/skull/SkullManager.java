package net.zaminmc.torch.server.skull;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The skull store (Slice 60 — SkullBlockEntity 1:1): every placed
 * skull's tile — the TYPE (skeleton 0, wither 1, zombie 2, player 3,
 * creeper 4, dragon 5) and the floor rotation — plus the wither's soul
 * sand summon walk (SkullBlock.trySpawn lines 167-223): the 3x3 pattern
 * (three wither skulls on a soul-sand T) found in any of the four
 * horizontal planes, the whole build vanishing for the boss's birth at
 * the top-center cell.
 *
 * <p>State is simulation-owned (tick thread only). The placement, the
 * break, and the summon all run on the tick lanes through the engine's
 * own call sites.</p>
 */
public final class SkullManager {

    /**
     * One skull's tile (SkullBlockEntity's own fields): the type byte and
     * the rotation byte the wire's 0x35 packet carries, plus the
     * player-head's profile (Wave K — the Owner member, null unless the
     * type is 3 and a profile was given).
     */
    public record SkullTile(BlockPosition position, int type, int rotation,
                            net.zaminmc.torch.item.SkullProfile profile) {

        /** The pre-K three-field shape (the compatibility constructor). */
        public SkullTile(BlockPosition position, int type, int rotation) {
            this(position, type, rotation, null);
        }
    }

    /** The engine's consults at the summon walk. */
    public interface Host {
        /** The block at the cell (the world read). */
        BlockType blockAt(BlockPosition position);

        /** Writes the block (the pattern's clear walk). */
        void setBlock(BlockPosition position, BlockType type);

        /** The air type. */
        BlockType airType();

        /** The current difficulty id (0 peaceful .. 3 hard). */
        int difficulty();

        /**
         * Spawns the wither at the pattern's top-center cell (the engine
         * routes the spawn; the caller arms the window + the body yaw).
         */
        void summonWither(Position at, float bodyYawDegrees);
    }

    /** Events the protocol adapter translates into the 0x35 tile packet. */
    public interface Listener {
        /** A skull landed (or its tile changed): the tile re-publishes. */
        default void onSkullTileChanged(BlockPosition position, SkullTile tile) {
        }

        /** A skull left (the break or the summon's clear walk). */
        default void onSkullRemoved(BlockPosition position) {
        }
    }

    private final Map<BlockPosition, SkullTile> tiles = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Host host;

    public SkullManager(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The tile at the cell, or null. */
    public SkullTile tileAt(BlockPosition position) {
        return tiles.get(position);
    }

    /** The tiles in the chunk (the wire's chunk-send replay). */
    public List<SkullTile> tilesIn(long packedChunk) {
        List<SkullTile> found = new ArrayList<>();
        for (SkullTile tile : tiles.values()) {
            if (chunkOf(tile.position()) == packedChunk) {
                found.add(tile);
            }
        }
        return found;
    }

    private static long chunkOf(BlockPosition position) {
        return position.chunkPosition().packed();
    }

    /**
     * The placement's tile write (SkullItem.useOn lines 55-81): the type
     * from the stack's damage (the player-head profile rides the damage-3
     * consult — Wave K), the floor rotation from the placer's yaw when
     * the mount is UP. The no-profile overload keeps the plain Steve head
     * (the reference's gameprofile = null).
     */
    public void place(BlockPosition position, int type, int rotation) {
        place(position, type, rotation, null);
    }

    /** The placement's tile write with the player-head's profile (the damage-3 arm). */
    public void place(BlockPosition position, int type, int rotation,
            net.zaminmc.torch.item.SkullProfile profile) {
        SkullTile tile = new SkullTile(position, type & 0xFF, rotation & 0xFF, profile);
        tiles.put(position, tile);
        for (Listener listener : listeners) {
            listener.onSkullTileChanged(position, tile);
        }
    }

    /** The break's cleanup (the drop consult reads the tile first). */
    public SkullTile remove(BlockPosition position) {
        SkullTile removed = tiles.remove(position);
        if (removed != null) {
            for (Listener listener : listeners) {
                listener.onSkullRemoved(position);
            }
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // The wither summon (SkullBlock.trySpawn lines 167-223)
    // ------------------------------------------------------------------

    /**
     * The hypothetical-head base-pattern check (SkullBlock.canSpawn's
     * block-side law, lines 159-165): the y and difficulty gates, then
     * the BASE pattern ({@code getBasePattern} lines 246-255 — aisle
     * {@code "   ", "###", "~#~"}: the top row all wildcards — the head
     * is not there yet — a soul-sand row of three under it and the
     * single sand base with air flanks two under). The placed cell may
     * sit at any of the three wildcard top cells; the item-side
     * metadata==1 gate (line 160's first clause) stays at the caller,
     * the item being the caller's own.
     *
     * @return whether a wither skull at {@code head} would sit on a
     *         complete base.
     */
    public boolean canSummonAt(BlockPosition head) {
        if (head.y() < 2 || host.difficulty() == 0) {
            return false; // the y + difficulty gates (lines 161-162)
        }
        // The four horizontal directions (the pattern's width run), the
        // head at any of the three top cells — the same anchor walk the
        // summon's own search runs.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            for (int slot = 0; slot < 3; slot++) {
                int cx = head.x() - slot * dir[0];
                int cz = head.z() - slot * dir[1];
                // The top row is wildcards: only the "###" sand row at
                // y-1 and the "~#~" base row at y-2 must already stand.
                boolean sandRow = true;
                for (int i = -1; i <= 1; i++) {
                    if (!isSoulSand(new BlockPosition(cx + i * dir[0],
                            head.y() - 1, cz + i * dir[1]))) {
                        sandRow = false;
                        break;
                    }
                }
                if (sandRow
                        && isAir(new BlockPosition(cx - dir[0], head.y() - 2, cz - dir[1]))
                        && isSoulSand(new BlockPosition(cx, head.y() - 2, cz))
                        && isAir(new BlockPosition(cx + dir[0], head.y() - 2, cz + dir[1]))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The summon attempt after a wither-skull placement: the gates (type
     * 1, y at least 2, not peaceful — line 168), then the 3x3 pattern —
     * three wither-skull tiles (the predicate reads the TILE type, not
     * the block face) over a soul-sand row, on a soul-sand center with
     * air flanks — searched in all four horizontal planes with the
     * placed skull at any of the three top cells. A match clears the
     * whole build (9 cells) and births the wither at the top-center cell
     * (+0.5, +0.55, +0.5) with the plane's axis yaw.
     *
     * @return true when the summon fired.
     */
    public boolean trySummonWither(BlockPosition placed) {
        SkullTile tile = tiles.get(placed);
        if (tile == null || tile.type() != 1 || placed.y() < 2
                || host.difficulty() == 0) {
            return false; // the gates (line 168)
        }
        // The four horizontal directions (the pattern's width run).
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            // The placed skull sits at one of the three top cells: the
            // slot offsets along the width run.
            for (int slot = 0; slot < 3; slot++) {
                int cx = placed.x() - slot * dir[0];
                int cz = placed.z() - slot * dir[1];
                if (matchesPattern(cx, placed.y(), cz, dir[0], dir[1])) {
                    clearAndSummon(cx, placed.y(), cz, dir[0], dir[1]);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The pattern test at the top-center cell (the aisle "^^^" / "###" /
     * "~#~" — the top row at {@code y}, the sand row below it, the base
     * row at {@code y-2}).
     */
    private boolean matchesPattern(int centerX, int topY, int centerZ, int dx, int dz) {
        // The top row: three wither-skull tiles (the WITHER_SKULL_PREDICATE
        // reads the block AND the tile's type).
        for (int i = -1; i <= 1; i++) {
            BlockPosition at = new BlockPosition(centerX + i * dx, topY, centerZ + i * dz);
            if (!isWitherSkull(at)) {
                return false;
            }
        }
        // The sand row: three soul sand.
        for (int i = -1; i <= 1; i++) {
            if (!isSoulSand(new BlockPosition(centerX + i * dx, topY - 1, centerZ + i * dz))) {
                return false;
            }
        }
        // The base row: air, soul sand, air.
        if (!isAir(new BlockPosition(centerX - dx, topY - 2, centerZ - dz))
                || !isSoulSand(new BlockPosition(centerX, topY - 2, centerZ))
                || !isAir(new BlockPosition(centerX + dx, topY - 2, centerZ + dz))) {
            return false;
        }
        return true;
    }

    private boolean isWitherSkull(BlockPosition at) {
        if (!net.zaminmc.torch.server.block.SkullBlocks.isSkull(
                host.blockAt(at).identifier())) {
            return false;
        }
        SkullTile tile = tiles.get(at);
        return tile != null && tile.type() == 1;
    }

    private boolean isSoulSand(BlockPosition at) {
        return host.blockAt(at).identifier()
                .equals(BuiltinBlocks.SOUL_SAND.identifier());
    }

    private boolean isAir(BlockPosition at) {
        return host.blockAt(at).equals(host.airType());
    }

    /**
     * The clear walk + the birth (lines 172-220): the whole 3x3 build
     * leaves (the skulls' tiles with it), the wither spawns at the
     * top-center cell +0.5/+0.55/+0.5 with the axis yaw (forward along
     * X — the z-directed run — line 191's 0, else 90).
     */
    private void clearAndSummon(int centerX, int topY, int centerZ, int dx, int dz) {
        for (int i = -1; i <= 1; i++) {
            for (int dy = 0; dy >= -2; dy--) {
                BlockPosition at = new BlockPosition(
                        centerX + i * dx, topY + dy, centerZ + i * dz);
                host.setBlock(at, host.airType());
                tiles.remove(at);
                for (Listener listener : listeners) {
                    listener.onSkullRemoved(at);
                }
            }
        }
        // Line 191's axis law: the width run along X reads forward axis X
        // (yaw 0); the Z run reads 90.
        float yaw = dx != 0 ? 0.0f : 90.0f;
        host.summonWither(new Position(centerX + 0.5, topY + 0.55, centerZ + 0.5), yaw);
    }
}
