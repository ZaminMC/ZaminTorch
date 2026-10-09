package net.minecraft.client.render.world;

import com.google.common.collect.Lists;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.util.IntegerBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ChunkOcclusionGraph {
    private static final int STEP_X = (int)Math.pow(16.0, 0.0);
    private static final int STEP_Y = (int)Math.pow(16.0, 1.0);
    private static final int STEP_Z = (int)Math.pow(16.0, 2.0);
    private final BitSet closed = new BitSet(4096);
    private static final int[] EDGE_POINTS = new int[1352];
    private int empty = 4096;

    public void close(BlockPos pos) {
        this.closed.set(index(pos), true);
        this.empty--;
    }

    private static int index(BlockPos pos) {
        return index(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    private static int index(int x, int y, int z) {
        return x << 0 | y << 8 | z << 4;
    }

    public OcclusionData resolve() {
        OcclusionData occlusiondata = new OcclusionData();
        if (4096 - this.empty < 256) {
            occlusiondata.fill(true);
        } else if (this.empty == 0) {
            occlusiondata.fill(false);
        } else {
            for (int i : EDGE_POINTS) {
                if (!this.closed.get(i)) {
                    occlusiondata.add(this.getFaces(i));
                }
            }
        }

        return occlusiondata;
    }

    public Set<Direction> getFaces(BlockPos pos) {
        return this.getFaces(index(pos));
    }

    private Set<Direction> getFaces(int index) {
        Set<Direction> set = EnumSet.noneOf(Direction.class);
        Queue<Integer> queue = Lists.newLinkedList();
        queue.add(IntegerBuffer.get(index));
        this.closed.set(index, true);

        while (!queue.isEmpty()) {
            int i = queue.poll();
            this.addEdges(i, set);

            for (Direction direction : Direction.values()) {
                int j = this.offset(i, direction);
                if (j >= 0 && !this.closed.get(j)) {
                    this.closed.set(j, true);
                    queue.add(IntegerBuffer.get(j));
                }
            }
        }

        return set;
    }

    private void addEdges(int index, Set<Direction> faces) {
        int i = index >> 0 & 15;
        if (i == 0) {
            faces.add(Direction.WEST);
        } else if (i == 15) {
            faces.add(Direction.EAST);
        }

        int j = index >> 8 & 15;
        if (j == 0) {
            faces.add(Direction.DOWN);
        } else if (j == 15) {
            faces.add(Direction.UP);
        }

        int k = index >> 4 & 15;
        if (k == 0) {
            faces.add(Direction.NORTH);
        } else if (k == 15) {
            faces.add(Direction.SOUTH);
        }
    }

    private int offset(int index, Direction face) {
        switch (face) {
            case DOWN:
                if ((index >> 8 & 15) == 0) {
                    return -1;
                }

                return index - STEP_Z;
            case UP:
                if ((index >> 8 & 15) == 15) {
                    return -1;
                }

                return index + STEP_Z;
            case NORTH:
                if ((index >> 4 & 15) == 0) {
                    return -1;
                }

                return index - STEP_Y;
            case SOUTH:
                if ((index >> 4 & 15) == 15) {
                    return -1;
                }

                return index + STEP_Y;
            case WEST:
                if ((index >> 0 & 15) == 0) {
                    return -1;
                }

                return index - STEP_X;
            case EAST:
                if ((index >> 0 & 15) == 15) {
                    return -1;
                }

                return index + STEP_X;
            default:
                return -1;
        }
    }

    static {
        int i = 0;
        int j = 15;
        int k = 0;

        for (int l = 0; l < 16; l++) {
            for (int i1 = 0; i1 < 16; i1++) {
                for (int j1 = 0; j1 < 16; j1++) {
                    if (l == 0 || l == 15 || i1 == 0 || i1 == 15 || j1 == 0 || j1 == 15) {
                        EDGE_POINTS[k++] = index(l, i1, j1);
                    }
                }
            }
        }
    }
}
