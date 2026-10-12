package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;

import java.util.ArrayList;
import java.util.List;

/**
 * The push/pull column resolver (Slice 9e) — the 1:1 port of
 * reference/1.8.8 block/piston/PistonMoveStructureResolver.java: the
 * column walk along the move direction, the 12-block budget, the
 * break-on-DESTROY rule, the slime-block column and neighbor rules
 * (structure ported whole; the engine has no slime blocks yet, so the
 * slime arms read false until that family lands).
 *
 * <p>The lists answer in the reference's own order: {@code getToMove()}
 * lists the blocks to shift (nearest-to-piston first within a column),
 * {@code getToBreak()} the blocks to destroy instead (the DESTROY-material
 * blocks the column runs into, including the start block when it cannot
 * move at all).</p>
 */
final class PistonStructureResolver {

    private final PistonSystem system;
    private final BlockPosition pistonPos;
    private final BlockPosition startPos;
    private final int moveDir;
    private final List<BlockPosition> toMove = new ArrayList<>();
    private final List<BlockPosition> toBreak = new ArrayList<>();

    /**
     * @param extend {@code true} pushes away from the piston (the head's
     *               direction), {@code false} pulls toward it.
     */
    PistonStructureResolver(PistonSystem system, BlockPosition pistonPos, int pistonDir, boolean extend) {
        this.system = system;
        this.pistonPos = pistonPos;
        if (extend) {
            this.moveDir = pistonDir;
            this.startPos = pistonPos.offset(PistonSystem.OFFSETS[pistonDir][0],
                    PistonSystem.OFFSETS[pistonDir][1], PistonSystem.OFFSETS[pistonDir][2]);
        } else {
            this.moveDir = oppositeOf(pistonDir);
            int[] twice = {PistonSystem.OFFSETS[pistonDir][0] * 2,
                    PistonSystem.OFFSETS[pistonDir][1] * 2,
                    PistonSystem.OFFSETS[pistonDir][2] * 2};
            this.startPos = pistonPos.offset(twice[0], twice[1], twice[2]);
        }
    }

    /** The resolver entry (lines 33-58). */
    boolean resolve() {
        toMove.clear();
        toBreak.clear();
        BlockType block = system.world().getBlock(startPos);
        if (!system.canMoveBlock(block, startPos, moveDir, false)) {
            if (system.pistonMoveBehavior(block) != PistonSystem.BEHAVIOR_DESTROY) {
                return false;
            }
            toBreak.add(startPos);
            return true;
        }
        if (!addColumn(startPos)) {
            return false;
        }
        for (int i = 0; i < toMove.size(); i++) {
            BlockPosition pos = toMove.get(i);
            if (system.isSlimeBlock(system.world().getBlock(pos)) && !addNeighborColumns(pos)) {
                return false;
            }
        }
        return true;
    }

    /** The column walk (lines 60-144). */
    private boolean addColumn(BlockPosition pos) {
        BlockType block = system.world().getBlock(pos);
        if (system.isAir(block)) {
            return true;
        }
        if (!system.canMoveBlock(block, pos, moveDir, false)) {
            return true;
        }
        if (pos.equals(pistonPos)) {
            return true;
        }
        if (toMove.contains(pos)) {
            return true;
        }

        int i = 1;
        if (i + toMove.size() > 12) {
            return false;
        }

        while (system.isSlimeBlock(block)) {
            BlockPosition back = pos.offset(
                    PistonSystem.OFFSETS[oppositeOf(moveDir)][0] * i,
                    PistonSystem.OFFSETS[oppositeOf(moveDir)][1] * i,
                    PistonSystem.OFFSETS[oppositeOf(moveDir)][2] * i);
            block = system.world().getBlock(back);
            if (system.isAir(block)
                    || !system.canMoveBlock(block, back, moveDir, false)
                    || back.equals(pistonPos)) {
                break;
            }
            if (++i + toMove.size() > 12) {
                return false;
            }
        }

        int moved = 0;
        for (int j = i - 1; j >= 0; j--) {
            toMove.add(pos.offset(
                    PistonSystem.OFFSETS[oppositeOf(moveDir)][0] * j,
                    PistonSystem.OFFSETS[oppositeOf(moveDir)][1] * j,
                    PistonSystem.OFFSETS[oppositeOf(moveDir)][2] * j));
            moved++;
        }

        int distance = 1;
        while (true) {
            BlockPosition at = pos.offset(
                    PistonSystem.OFFSETS[moveDir][0] * distance,
                    PistonSystem.OFFSETS[moveDir][1] * distance,
                    PistonSystem.OFFSETS[moveDir][2] * distance);
            int existing = toMove.indexOf(at);
            if (existing > -1) {
                insertColumn(moved, existing);
                for (int l = 0; l <= existing + moved; l++) {
                    BlockPosition within = toMove.get(l);
                    if (system.isSlimeBlock(system.world().getBlock(within)) && !addNeighborColumns(within)) {
                        return false;
                    }
                }
                return true;
            }

            block = system.world().getBlock(at);
            if (system.isAir(block)) {
                return true;
            }
            if (!system.canMoveBlock(block, at, moveDir, true) || at.equals(pistonPos)) {
                return false;
            }
            if (system.pistonMoveBehavior(block) == PistonSystem.BEHAVIOR_DESTROY) {
                toBreak.add(at);
                return true;
            }
            if (toMove.size() >= 12) {
                return false;
            }
            toMove.add(at);
            moved++;
            distance++;
        }
    }

    /** The order-preserving column splice (lines 146-157). */
    private void insertColumn(int length, int index) {
        List<BlockPosition> head = new ArrayList<>(toMove.subList(0, index));
        List<BlockPosition> tail = new ArrayList<>(toMove.subList(toMove.size() - length, toMove.size()));
        List<BlockPosition> middle = new ArrayList<>(toMove.subList(index, toMove.size() - length));
        toMove.clear();
        toMove.addAll(head);
        toMove.addAll(tail);
        toMove.addAll(middle);
    }

    /** The slime neighbor-column walk (lines 159-167). */
    private boolean addNeighborColumns(BlockPosition pos) {
        for (int direction = 0; direction < 6; direction++) {
            if (axisOf(direction) != axisOf(moveDir)) {
                BlockPosition neighbor = pos.offset(PistonSystem.OFFSETS[direction][0],
                        PistonSystem.OFFSETS[direction][1], PistonSystem.OFFSETS[direction][2]);
                if (!addColumn(neighbor)) {
                    return false;
                }
            }
        }
        return true;
    }

    List<BlockPosition> getToMove() {
        return toMove;
    }

    List<BlockPosition> getToBreak() {
        return toBreak;
    }

    /** The opposite direction id (the reference's Direction.getOpposite over ids). */
    static int oppositeOf(int direction) {
        return switch (direction) {
            case 0 -> 1; // down <-> up
            case 1 -> 0;
            case 2 -> 3; // north <-> south
            case 3 -> 2;
            case 4 -> 5; // west <-> east
            case 5 -> 4;
            default -> direction;
        };
    }

    /** The axis class of a direction id (0=y, 1=x, 2=z — the reference's Direction.Axis). */
    static int axisOf(int direction) {
        return switch (direction) {
            case 0, 1 -> 0;
            case 2, 3 -> 2;
            default -> 1;
        };
    }
}
