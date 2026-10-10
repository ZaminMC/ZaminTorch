package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Identifier;

import java.util.Objects;

/**
 * The portal frame builder (the reference's {@code PortalBlock.PortalBuilder},
 * reference/1.8.8 block/PortalBlock.java lines 271-397, plus the ignition
 * arm {@code create} lines 85-99 and the neighbor-break re-validation lines
 * 101-117): the obsidian frame scan for one axis — sink to the frame's
 * bottom, walk the interior width (2..21) along the axis with every
 * interior cell standing on obsidian, climb the interior height (3..21)
 * with the side cells framed in obsidian and the top row capped — then the
 * build fills the interior with portal blocks carrying the axis.
 *
 * <p>The reference's exact arithmetic is preserved: the bottom sink runs at
 * most 21 steps, {@code findWidth} walks up to 22 cells and demands the
 * terminator obsidian (returning 0 otherwise — an invalid width, so
 * {@code bottomLeft} dies), the interior cells must be replaceable
 * (air, fire or an existing portal), the first cell demands the LEFT
 * neighbor obsidian and the last the RIGHT neighbor, and the top row must
 * be a full obsidian cap. The ignition arm requires
 * {@code foundPortalBlocks == 0} — an already-built frame is never
 * re-ignited (the flint sparks do nothing on a live portal).</p>
 */
public final class PortalFrameBuilder {

    private static final Identifier OBSIDIAN = BuiltinBlocks.OBSIDIAN.identifier();
    private static final Identifier FIRE = BuiltinBlocks.FIRE.identifier();

    /** The axis the builder scans: the reference's Direction.Axis.X / .Z. */
    public enum Axis {
        /** The east-west portal plane (wire metadata 1): left = EAST, right = WEST. */
        X(1, 0),
        /** The north-south portal plane (wire metadata 2): left = NORTH, right = SOUTH. */
        Z(0, -1);

        /** The LEFT walk unit vector (the reference's left direction). */
        final int leftDx;
        final int leftDz;

        Axis(int leftDx, int leftDz) {
            this.leftDx = leftDx;
            this.leftDz = leftDz;
        }

        /** The RIGHT walk is the LEFT negated (the reference's right). */
        int rightDx() {
            return -leftDx;
        }

        int rightDz() {
            return -leftDz;
        }
    }

    private final EngineWorld world;
    private final Axis axis;
    private BlockPosition bottomLeft;
    private int width;
    private int height;
    private int foundPortalBlocks;

    private PortalFrameBuilder(EngineWorld world, Axis axis) {
        this.world = Objects.requireNonNull(world, "world");
        this.axis = axis;
    }

    /**
     * The reference's {@code PortalBlock.create}: the X-axis scan first,
     * then the Z-axis — a valid, not-yet-built frame fills with portal
     * blocks. @return whether the portal built.
     */
    public static boolean createAt(EngineWorld world, BlockPosition pos) {
        PortalFrameBuilder builder = new PortalFrameBuilder(world, Axis.X);
        builder.scanFrom(pos);
        if (builder.isValid() && builder.foundPortalBlocks == 0) {
            builder.build();
            return true;
        }
        builder = new PortalFrameBuilder(world, Axis.Z);
        builder.scanFrom(pos);
        if (builder.isValid() && builder.foundPortalBlocks == 0) {
            builder.build();
            return true;
        }
        return false;
    }

    /**
     * The reference's {@code PortalBlock.neighborChanged}: a portal cell
     * re-validates its own frame — an invalid scan, or a scan finding fewer
     * portal cells than the frame's interior (a frame block broken), kills
     * the cell to air.
     */
    public static boolean survivesNeighborChange(EngineWorld world, BlockPosition pos) {
        PortalFrameBuilder builder = new PortalFrameBuilder(world, Axis.X);
        builder.scanFrom(pos);
        if (builder.isValid()
                && builder.foundPortalBlocks >= builder.width * builder.height) {
            return true;
        }
        builder = new PortalFrameBuilder(world, Axis.Z);
        builder.scanFrom(pos);
        return builder.isValid()
                && builder.foundPortalBlocks >= builder.width * builder.height;
    }

    /** The constructor walk: sink to the bottom, measure the width, climb the height. */
    private void scanFrom(BlockPosition pos) {
        BlockPosition start = pos;
        // The sink: at most 21 steps down while the cell below is replaceable.
        while (pos.y() > start.y() - 21 && pos.y() > 0
                && canBeReplacedByPortal(world.getBlock(pos.offset(0, -1, 0)))) {
            pos = pos.offset(0, -1, 0);
        }
        width = 0;
        height = 0;
        foundPortalBlocks = 0;
        bottomLeft = null;
        int leftCells = findWidth(pos, axis.leftDx, axis.leftDz) - 1;
        if (leftCells >= 0) {
            bottomLeft = pos.offset(axis.leftDx, 0, axis.leftDz, leftCells);
            width = findWidth(bottomLeft, axis.rightDx(), axis.rightDz());
            if (width < 2 || width > 21) {
                bottomLeft = null;
                width = 0;
            }
        }
        if (bottomLeft != null) {
            height = findHeight();
        }
    }

    /**
     * The reference's {@code findWidth}: walks the direction while the cell
     * is replaceable AND stands on obsidian, at most 22 cells; the
     * terminator must be obsidian, otherwise the walk failed (0).
     */
    private int findWidth(BlockPosition pos, int dx, int dz) {
        int i;
        for (i = 0; i < 22; i++) {
            BlockPosition cell = pos.offset(dx, 0, dz, i);
            if (!canBeReplacedByPortal(world.getBlock(cell))
                    || !world.getBlock(cell.offset(0, -1, 0)).identifier().equals(OBSIDIAN)) {
                break;
            }
        }
        BlockPosition terminator = pos.offset(dx, 0, dz, i);
        return world.getBlock(terminator).identifier().equals(OBSIDIAN) ? i : 0;
    }

    /**
     * The reference's {@code findHeight}: climbs up to 21 rows; every row's
     * cells stay replaceable (an existing portal counts), the first cell's
     * LEFT neighbor and the last cell's RIGHT neighbor are obsidian, and
     * the final row is a full obsidian cap. A height outside 3..21 invalidates.
     */
    private int findHeight() {
        label:
        for (height = 0; height < 21; height++) {
            for (int i = 0; i < width; i++) {
                BlockPosition cell = bottomLeft
                        .offset(axis.rightDx(), 0, axis.rightDz(), i)
                        .offset(0, height, 0);
                BlockType block = world.getBlock(cell);
                if (!canBeReplacedByPortal(block)) {
                    break label;
                }
                if (isPortal(block)) {
                    foundPortalBlocks++;
                }
                if (i == 0) {
                    if (!world.getBlock(cell.offset(axis.leftDx, 0, axis.leftDz))
                            .identifier().equals(OBSIDIAN)) {
                        break label;
                    }
                } else if (i == width - 1) {
                    if (!world.getBlock(cell.offset(axis.rightDx(), 0, axis.rightDz()))
                            .identifier().equals(OBSIDIAN)) {
                        break label;
                    }
                }
            }
        }
        // The top-row cap: every interior cell must stand under obsidian.
        for (int j = 0; j < width; j++) {
            if (!world.getBlock(bottomLeft
                    .offset(axis.rightDx(), 0, axis.rightDz(), j)
                    .offset(0, height, 0)).identifier().equals(OBSIDIAN)) {
                height = 0;
                break;
            }
        }
        if (height <= 21 && height >= 3) {
            return height;
        }
        bottomLeft = null;
        width = 0;
        height = 0;
        return 0;
    }

    /** The reference's {@code canBeReplacedByPortal}: air, fire or a portal cell. */
    private boolean canBeReplacedByPortal(BlockType block) {
        return block.equals(world.airType())
                || block.identifier().equals(FIRE)
                || isPortal(block);
    }

    private static boolean isPortal(BlockType block) {
        return block.identifier().equals(BuiltinBlocks.NETHER_PORTAL.identifier())
                || block.identifier().equals(BuiltinBlocks.NETHER_PORTAL_Z.identifier());
    }

    /**
     * The findPortalShape match (the reference's {@code PortalBlock.findPortalShape}
     * over the {@code BlockPattern.Match}, subset to what the portal walk
     * consumes): the top-left-front corner, the FORWARD direction (the axis
     * + which way is positive — the front layer is one step along it), and
     * the frame's width/height. The forward pick is the reference's count
     * walk: the four horizontal axis-directions each count the non-air cells
     * one step along themselves from the frame's rectangle, and the EMPTIER
     * side is the front (the reference's strictly-smaller walk starting at
     * POSITIVE).
     */
    public record PortalShapeMatch(BlockPosition topLeftFront, Axis forwardAxis,
                                   boolean forwardPositive, int width, int height) {
        /** The reference's Direction.of(axisDirection, axis) idHorizontal (S=0, W=1, N=2, E=3). */
        public int forwardHorizontalId() {
            // Axis X: EAST (positive) = 3, WEST (negative) = 1.
            // Axis Z: SOUTH (positive) = 0, NORTH (negative) = 2.
            if (forwardAxis == Axis.X) {
                return forwardPositive ? 3 : 1;
            }
            return forwardPositive ? 0 : 2;
        }
    }

    /**
     * The reference's {@code PortalBlock.findPortalShape}: the X-axis frame
     * scan first, then the Z-axis; an invalid pair falls back to the 1x1x1
     * match at the position facing NORTH (the reference's degenerate Match —
     * a single cell pointing north). The X-portal's base direction is the
     * right vector rotated counter-clockwise (WEST ccwY = SOUTH); the
     * Z-portal's is SOUTH ccwY = EAST.
     */
    public static PortalShapeMatch matchAt(EngineWorld world, BlockPosition pos) {
        PortalFrameBuilder xScan = new PortalFrameBuilder(world, Axis.X);
        xScan.scanFrom(pos);
        if (xScan.isValid()) {
            return shapeMatchOf(world, xScan, Axis.Z);
        }
        PortalFrameBuilder zScan = new PortalFrameBuilder(world, Axis.Z);
        zScan.scanFrom(pos);
        if (zScan.isValid()) {
            return shapeMatchOf(world, zScan, Axis.X);
        }
        return new PortalShapeMatch(pos, Axis.Z, false, 1, 1);
    }

    /** The count walk: the four front-layer counts pick the forward direction. */
    private static PortalShapeMatch shapeMatchOf(EngineWorld world, PortalFrameBuilder builder, Axis forwardAxis) {
        // The base direction: right rotated counter-clockwise (the reference's
        // right.counterClockwiseY()). clockwiseY is N-E-S-W, so ccwY(WEST)=SOUTH
        // (the X-portal) and ccwY(SOUTH)=EAST (the Z-portal).
        boolean basePositive = builder.axis == Axis.X; // X: WEST -> ccwY -> SOUTH (+Z)
        if (builder.axis == Axis.Z) {
            basePositive = true; // Z: SOUTH -> ccwY -> EAST (+X)
        }
        BlockPosition topOfBottomLeft = builder.bottomLeft.offset(0, builder.height - 1, 0);
        int rightDx = builder.axis.rightDx();
        int rightDz = builder.axis.rightDz();

        // The two axis-directions of the forward axis: the frame's rectangle
        // one step along each; the count of non-air cells decides the front.
        int positiveCount = 0;
        int negativeCount = 0;
        for (int i = 0; i < builder.width; i++) {
            for (int j = 0; j < builder.height; j++) {
                BlockPosition cell = topOfBottomLeft
                        .offset(rightDx, j, rightDz, i);
                // The +1 layer along the forward axis (the reference's
                // match.getBlock(i, j, 1) — depth one along forward).
                BlockPosition positiveLayer = cell.offset(
                        forwardAxis == Axis.X ? 1 : 0, 0,
                        forwardAxis == Axis.Z ? 1 : 0);
                if (!world.getBlock(positiveLayer).equals(world.airType())) {
                    positiveCount++;
                }
                BlockPosition negativeLayer = cell.offset(
                        forwardAxis == Axis.X ? -1 : 0, 0,
                        forwardAxis == Axis.Z ? -1 : 0);
                if (!world.getBlock(negativeLayer).equals(world.airType())) {
                    negativeCount++;
                }
            }
        }
        // The reference's pick: start at POSITIVE, switch on strictly smaller
        // (the POSITIVE side wins ties).
        boolean chosenPositive = positiveCount <= negativeCount;

        // The top-left-front corner: the base-side top when the chosen
        // direction matches the base's own axis-direction, otherwise the
        // far-side top (the reference's topLeft selection).
        BlockPosition topLeftFront = (basePositive == chosenPositive)
                ? topOfBottomLeft
                : topOfBottomLeft.offset(rightDx, 0, rightDz, builder.width - 1);
        return new PortalShapeMatch(topLeftFront, forwardAxis, chosenPositive,
                builder.width, builder.height);
    }

    /** The reference's {@code isValid}: a found bottom-left with the bounds held. */
    public boolean isValid() {
        return bottomLeft != null && width >= 2 && width <= 21
                && height >= 3 && height <= 21;
    }

    /** The reference's {@code build}: the interior fills with the axis's portal cells. */
    private void build() {
        BlockType cell = axis == Axis.X
                ? BuiltinBlocks.NETHER_PORTAL
                : BuiltinBlocks.NETHER_PORTAL_Z;
        for (int i = 0; i < width; i++) {
            BlockPosition column = bottomLeft.offset(axis.rightDx(), 0, axis.rightDz(), i);
            for (int j = 0; j < height; j++) {
                world.setBlock(column.offset(0, j, 0), cell);
            }
        }
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }
}
