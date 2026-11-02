package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * The rail connection walk (the thirteenth verifier pass —
 * AbstractRailBlock.RailNode, the reference's lines 143-477, 1:1 over the
 * engine's surfaces): the onAdded force walk that decides every rail's
 * shape from its neighbours, and the reciprocation loop that re-orients
 * the existing rails the placement touches.
 *
 * <p><b>The law, every anchor from the reference:</b></p>
 * <ul>
 *   <li><b>The connection lists</b> (updateConnections, lines 163-206):
 *       each shape's own two neighbour cells — the straights their two
 *       axis cells, the ascents one same-level cell + one UP cell, the
 *       curves their two orthogonal cells.</li>
 *   <li><b>The neighbour search</b> (getNeighborRail, lines 225-241): the
 *       same-level cell first, then UP, then DOWN — rails connect across
 *       one level of slope.</li>
 *   <li><b>The reciprocation prune</b> (removeSoftConnections,
 *       lines 208-217): a connection survives only when its neighbour's
 *       own list carries our cell — and the compare (hasConnection,
 *       lines 247-256) reads X AND Z ALONE, so the UP/DOWN search cells
 *       collapse onto the neighbour's column.</li>
 *   <li><b>The shape selection</b> (updateState, lines 349-453): the
 *       exclusive bands (N||S without E/W → north-south; W||E without
 *       N/S → east-west), the four curve branches the
 *       {@code alwaysStraight} flag gates (the plain rail's family
 *       alone), the null fallback's own bands + the unpowered curve
 *       picks (lines 411-427 — the powered preference at 394-410 is the
 *       redstone seam's own row, the honest tail), and the ASCENDING
 *       upgrades over both straight shapes (lines 431-449 — two ifs, the
 *       south/west read after the north/east, so a both-sided slope
 *       picks the south/west side).</li>
 *   <li><b>The write</b> (lines 455-469): only when the state changed
 *       (or the force arm), and every surviving connection's neighbour
 *       then re-orients back through removeSoftConnections +
 *       canConnectTo + addConnection — the walk that bends an existing
 *       track to meet the new rail.</li>
 * </ul>
 *
 * <p><b>The seats:</b> the placement's onAdded force arm (EngineServer's
 * rail site, the reference's lines 76-83) runs the walk the moment the
 * default north-south state lands; the update bus
 * ({@code BlockUpdateSystem}) carries the support arms alone — the
 * reference's own neighborChanged re-walks only on a redstone signal
 * (RailBlock lines 19-23, the honest tail). The plain rail's POWERED
 * curve preference (lines 394-410) rides the same redstone seam.</p>
 */
public final class RailConnections {

    private RailConnections() {
    }

    // The reference's Shape ids (AbstractRailBlock.Shape, lines 479-489).
    public static final int NORTH_SOUTH = 0;
    public static final int EAST_WEST = 1;
    public static final int ASCENDING_EAST = 2;
    public static final int ASCENDING_WEST = 3;
    public static final int ASCENDING_NORTH = 4;
    public static final int ASCENDING_SOUTH = 5;
    public static final int SOUTH_EAST = 6;
    public static final int SOUTH_WEST = 7;
    public static final int NORTH_WEST = 8;
    public static final int NORTH_EAST = 9;

    /**
     * The walk's own entry (AbstractRailBlock.updateShape, lines 116-118:
     * the force arm at onAdded, the changed-read arm everywhere else):
     * re-reads the cell's shape from its neighbours and writes the
     * selection, the reciprocation loop re-orients the touched rails.
     * Tick-thread context (the placement site's own thread).
     */
    public static void updateShape(EngineWorld world, BlockPosition pos, boolean force) {
        BlockType state = world.getBlock(pos);
        if (!RailBlocks.isRailFamily(state)) {
            return; // the safety read: the cell is not a rail
        }
        new Node(world, pos, RailBlocks.alwaysStraightOf(state)).updateState(force);
    }

    /**
     * The supports consult (AbstractRailBlock.neighborChanged, lines
     * 86-102): the floor below must carry a solid top (the
     * has-solid-top family, canBePlaced's own gate — a bottom slab, a
     * fence, a wall or the thin snow is NOT a rail bed), and each
     * ASCENDING shape's own run cell (the ascent's horizontal
     * direction) must too. The update bus's rail arm and the trio's
     * own onAdded consult (the explicit neighborChanged at line 81)
     * read the same law.
     *
     * <p>F-SP2: the consult reads {@link SupportConsult#hasSolidTop}'s
     * five arms — the reference's {@code World.hasSolidTop} 1:1 — never
     * the translucent fallthrough the fire family's own form carried:
     * GLASS, ICE, TNT, the leaves and the glowstone are NOT rail beds
     * (each translucent, each outside the five arms), the hopper and
     * the TOP-half slabs and stairs ARE.</p>
     *
     * @return whether the rail survives at the cell
     */
    public static boolean survivesAt(EngineWorld world, BlockPosition pos) {
        if (!SupportConsult.hasSolidTop(world.getBlock(pos.offset(0, -1, 0)))) {
            return false;
        }
        int shape = RailBlocks.familyShapeOf(world.getBlock(pos));
        return switch (shape) {
            case ASCENDING_EAST -> SupportConsult.hasSolidTop(world.getBlock(pos.offset(1, 0, 0)));
            case ASCENDING_WEST -> SupportConsult.hasSolidTop(world.getBlock(pos.offset(-1, 0, 0)));
            case ASCENDING_NORTH -> SupportConsult.hasSolidTop(world.getBlock(pos.offset(0, 0, -1)));
            case ASCENDING_SOUTH -> SupportConsult.hasSolidTop(world.getBlock(pos.offset(0, 0, 1)));
            default -> true;
        };
    }

    /** One rail cell's walk state (the reference's RailNode, lines 145-477). */
    private static final class Node {
        private final EngineWorld world;
        private final BlockPosition pos;
        private final boolean alwaysStraight;
        private final List<BlockPosition> connections = new ArrayList<>();

        Node(EngineWorld world, BlockPosition pos, boolean alwaysStraight) {
            this.world = world;
            this.pos = pos;
            this.alwaysStraight = alwaysStraight;
            this.updateConnections(RailBlocks.familyShapeOf(world.getBlock(pos)));
        }

        /** The reference's isRail consult (lines 22-29 — all four families). */
        private boolean isRailAt(BlockPosition at) {
            return WorldSolidity.isRail(world.getBlock(at).identifier());
        }

        /**
         * Each shape's own two neighbour cells (updateConnections,
         * lines 163-206): the ascents read one same-level cell + one UP
         * cell, the curves their two orthogonal cells.
         */
        private void updateConnections(int shape) {
            this.connections.clear();
            BlockPosition north = this.pos.offset(0, 0, -1);
            BlockPosition south = this.pos.offset(0, 0, 1);
            BlockPosition west = this.pos.offset(-1, 0, 0);
            BlockPosition east = this.pos.offset(1, 0, 0);
            switch (shape) {
                case NORTH_SOUTH -> {
                    this.connections.add(north);
                    this.connections.add(south);
                }
                case EAST_WEST -> {
                    this.connections.add(west);
                    this.connections.add(east);
                }
                case ASCENDING_EAST -> {
                    this.connections.add(west);
                    this.connections.add(east.offset(0, 1, 0));
                }
                case ASCENDING_WEST -> {
                    this.connections.add(west.offset(0, 1, 0));
                    this.connections.add(east);
                }
                case ASCENDING_NORTH -> {
                    this.connections.add(north.offset(0, 1, 0));
                    this.connections.add(south);
                }
                case ASCENDING_SOUTH -> {
                    this.connections.add(north);
                    this.connections.add(south.offset(0, 1, 0));
                }
                case SOUTH_EAST -> {
                    this.connections.add(east);
                    this.connections.add(south);
                }
                case SOUTH_WEST -> {
                    this.connections.add(west);
                    this.connections.add(south);
                }
                case NORTH_WEST -> {
                    this.connections.add(west);
                    this.connections.add(north);
                }
                case NORTH_EAST -> {
                    this.connections.add(east);
                    this.connections.add(north);
                }
                default -> {
                }
            }
        }

        /**
         * The neighbour search (getNeighborRail, lines 225-241): the
         * same-level cell first, then UP, then DOWN — rails connect
         * across one level of slope.
         */
        private Node getNeighborRail(BlockPosition at) {
            if (this.isRailAt(at)) {
                return new Node(this.world, at, this.alwaysStraightOf(at));
            }
            if (this.isRailAt(at.offset(0, 1, 0))) {
                return new Node(this.world, at.offset(0, 1, 0), this.alwaysStraightOf(at.offset(0, 1, 0)));
            }
            if (this.isRailAt(at.offset(0, -1, 0))) {
                return new Node(this.world, at.offset(0, -1, 0), this.alwaysStraightOf(at.offset(0, -1, 0)));
            }
            return null;
        }

        /** The neighbour cell's own family flag (the ctor parameter's read). */
        private boolean alwaysStraightOf(BlockPosition at) {
            return RailBlocks.alwaysStraightOf(world.getBlock(at));
        }

        /**
         * The reciprocation prune (removeSoftConnections, lines 208-217):
         * a connection survives only when its neighbour's own list
         * carries our cell — the survivor re-pointed at the neighbour's
         * actual (possibly one up/down) cell.
         */
        private void removeSoftConnections() {
            for (int i = 0; i < this.connections.size(); i++) {
                Node neighbor = this.getNeighborRail(this.connections.get(i));
                if (neighbor != null && neighbor.connectsTo(this)) {
                    this.connections.set(i, neighbor.pos);
                } else {
                    this.connections.remove(i--);
                }
            }
        }

        /** The neighbour's own list must carry our cell (connectsTo, lines 243-245). */
        private boolean connectsTo(Node rail) {
            return this.hasConnection(rail.pos);
        }

        /**
         * The X/Z-ONLY compare (hasConnection, lines 247-256): the UP/DOWN
         * search cells collapse onto the neighbour's column — the load-
         * bearing read the slope's reciprocation rides.
         */
        private boolean hasConnection(BlockPosition at) {
            for (BlockPosition connection : this.connections) {
                if (connection.x() == at.x() && connection.z() == at.z()) {
                    return true;
                }
            }
            return false;
        }

        /** Two connections hold, or the neighbour already points back (canConnectTo, lines 270-272). */
        private boolean canConnectTo(Node rail) {
            return this.connectsTo(rail) || this.connections.size() != 2;
        }

        /**
         * The neighbour's own re-orientation (addConnection, lines 274-337):
         * the connection lands, then the flags over the four axis cells
         * pick the shape — the straight bands (the east-west read after
         * the north-south), the four curve branches the alwaysStraight
         * flag gates, the ASCENDING upgrades over both straight shapes
         * (the south read after the north, the west after the east —
         * two ifs, the reference's own order), the null fallback's
         * north-south.
         */
        private void addConnection(Node rail) {
            this.connections.add(rail.pos);
            BlockPosition north = this.pos.offset(0, 0, -1);
            BlockPosition south = this.pos.offset(0, 0, 1);
            BlockPosition west = this.pos.offset(-1, 0, 0);
            BlockPosition east = this.pos.offset(1, 0, 0);
            boolean toNorth = this.hasConnection(north);
            boolean toSouth = this.hasConnection(south);
            boolean toWest = this.hasConnection(west);
            boolean toEast = this.hasConnection(east);
            Integer shape = null;
            if (toNorth || toSouth) {
                shape = NORTH_SOUTH;
            }
            if (toWest || toEast) {
                shape = EAST_WEST;
            }
            if (!this.alwaysStraight) {
                if (toSouth && toEast && !toNorth && !toWest) {
                    shape = SOUTH_EAST;
                }
                if (toSouth && toWest && !toNorth && !toEast) {
                    shape = SOUTH_WEST;
                }
                if (toNorth && toWest && !toSouth && !toEast) {
                    shape = NORTH_WEST;
                }
                if (toNorth && toEast && !toSouth && !toWest) {
                    shape = NORTH_EAST;
                }
            }
            if (shape != null && shape == NORTH_SOUTH) {
                if (this.isRailAt(north.offset(0, 1, 0))) {
                    shape = ASCENDING_NORTH;
                }
                if (this.isRailAt(south.offset(0, 1, 0))) {
                    shape = ASCENDING_SOUTH;
                }
            }
            if (shape != null && shape == EAST_WEST) {
                if (this.isRailAt(east.offset(0, 1, 0))) {
                    shape = ASCENDING_EAST;
                }
                if (this.isRailAt(west.offset(0, 1, 0))) {
                    shape = ASCENDING_WEST;
                }
            }
            if (shape == null) {
                shape = NORTH_SOUTH;
            }
            this.world.setBlock(this.pos, RailBlocks.withShape(this.world.getBlock(this.pos), shape));
        }

        /**
         * The axis cell's own consult (hasNeighborRail, lines 339-347):
         * the neighbour (same level, one up, one down) pruned, then the
         * connect read — the flag the selection table reads.
         */
        private boolean hasNeighborRail(BlockPosition at) {
            Node neighbor = this.getNeighborRail(at);
            if (neighbor == null) {
                return false;
            }
            neighbor.removeSoftConnections();
            return neighbor.canConnectTo(this);
        }

        /**
         * The shape selection (updateState, lines 349-472): the four
         * axis consults, the exclusive bands, the curve branches, the
         * null fallback with its own bands + the UNPOWERED curve picks
         * (lines 411-427 — the powered preference is the redstone seam's
         * own row, the honest tail), the ASCENDING upgrades, and the
         * changed-read write with the reciprocation loop (lines 455-469).
         */
        void updateState(boolean force) {
            BlockPosition north = this.pos.offset(0, 0, -1);
            BlockPosition south = this.pos.offset(0, 0, 1);
            BlockPosition west = this.pos.offset(-1, 0, 0);
            BlockPosition east = this.pos.offset(1, 0, 0);
            boolean toNorth = this.hasNeighborRail(north);
            boolean toSouth = this.hasNeighborRail(south);
            boolean toWest = this.hasNeighborRail(west);
            boolean toEast = this.hasNeighborRail(east);
            Integer shape = null;
            if ((toNorth || toSouth) && !toWest && !toEast) {
                shape = NORTH_SOUTH;
            }
            if ((toWest || toEast) && !toNorth && !toSouth) {
                shape = EAST_WEST;
            }
            if (!this.alwaysStraight) {
                if (toSouth && toEast && !toNorth && !toWest) {
                    shape = SOUTH_EAST;
                }
                if (toSouth && toWest && !toNorth && !toEast) {
                    shape = SOUTH_WEST;
                }
                if (toNorth && toWest && !toSouth && !toEast) {
                    shape = NORTH_WEST;
                }
                if (toNorth && toEast && !toSouth && !toWest) {
                    shape = NORTH_EAST;
                }
            }
            if (shape == null) {
                if (toNorth || toSouth) {
                    shape = NORTH_SOUTH;
                }
                if (toWest || toEast) {
                    shape = EAST_WEST;
                }
                if (!this.alwaysStraight) {
                    // The unpowered fallback picks (lines 411-427): the
                    // north-west first, the south-east last.
                    if (toNorth && toWest) {
                        shape = NORTH_WEST;
                    }
                    if (toEast && toNorth) {
                        shape = NORTH_EAST;
                    }
                    if (toWest && toSouth) {
                        shape = SOUTH_WEST;
                    }
                    if (toSouth && toEast) {
                        shape = SOUTH_EAST;
                    }
                }
            }
            if (shape != null && shape == NORTH_SOUTH) {
                if (this.isRailAt(north.offset(0, 1, 0))) {
                    shape = ASCENDING_NORTH;
                }
                if (this.isRailAt(south.offset(0, 1, 0))) {
                    shape = ASCENDING_SOUTH;
                }
            }
            if (shape != null && shape == EAST_WEST) {
                if (this.isRailAt(east.offset(0, 1, 0))) {
                    shape = ASCENDING_EAST;
                }
                if (this.isRailAt(west.offset(0, 1, 0))) {
                    shape = ASCENDING_WEST;
                }
            }
            if (shape == null) {
                shape = NORTH_SOUTH;
            }
            this.updateConnections(shape);
            BlockType desired = RailBlocks.withShape(this.world.getBlock(this.pos), shape);
            if (force || !this.world.getBlock(this.pos).equals(desired)) {
                this.world.setBlock(this.pos, desired);
                for (int i = 0; i < this.connections.size(); i++) {
                    Node neighbor = this.getNeighborRail(this.connections.get(i));
                    if (neighbor != null) {
                        neighbor.removeSoftConnections();
                        if (neighbor.canConnectTo(this)) {
                            neighbor.addConnection(this);
                        }
                    }
                }
            }
        }
    }
}
