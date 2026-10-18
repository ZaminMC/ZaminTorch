package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Locale;

/**
 * The collision-shape table (the collision-shape slice): every partial
 * block's own collision boxes, ported 1:1 from the reference's
 * {@code getCollisionShape} / {@code addCollisions} / {@code setShape}
 * rows. The physics point queries ({@link WorldSolidity#isSolidAt} /
 * {@link WorldSolidity#supportY}) resolve through this table instead of
 * the flat full-cube rule — a body now rests on the cake's 1/2-height
 * slab, steps around the fence's 1.5-tall post, and walks beneath a top
 * slab exactly like the reference.
 *
 * <p>The model is a list of axis-aligned boxes per block type, each a
 * fraction tuple over the cell ({@code 0..1}, the 1.5-tall families
 * carrying {@code maxY} past the ceiling). The connection families — the
 * fences, the walls, the panes and the closed fence gates — resolve their
 * boxes against the four horizontal neighbours through the
 * {@link NeighborProbe}, exactly like the reference's own
 * {@code updateShape} reads.</p>
 *
 * <p>Reference rows ported:</p>
 * <ul>
 * <li>the six wood fences ({@code FenceBlock.addCollisions}): the
 * 0.375-0.625 post 1.5 tall with the connection arms on the connected
 * sides (same-material fence, any fence gate, or a full cube that is not
 * the pumpkin);</li>
 * <li>the wall ({@code WallBlock.getCollisionShape}): the 0.25-0.75 post,
 * the connected sides opening to the cell edge, the straight pair's
 * 0.3125-0.6875 band, the collision {@code maxY} 1.5;</li>
 * <li>the closed fence gates ({@code FenceGateBlock.getCollisionShape}):
 * the 0.375-0.625 panel across the facing axis, 1.5 tall — the open
 * states carry no box at all;</li>
 * <li>the trapdoor pair ({@code TrapdoorBlock.updateShape}): the closed
 * 3/16 floor panel, the closed ceiling panel, and the open vertical panel
 * against the hinge wall per facing;</li>
 * <li>the chest family ({@code ChestBlock}): the 1/16-inset 14/16 box,
 * the double form's edge extending to the joined cell;</li>
 * <li>the thin surfaces: the carpet's 1/16 sheet, the snow layer's 2/16
 * cover, the lily pad's 1/64 film, the pressure plates' 1/32 disc;</li>
 * <li>the partial furniture: the flower pot's 3/8 box, the cake's
 * per-bite sliver, the cactus' 1/16 inset, the farmland's 15/16 field,
 * the enchanting table's 3/4 block, the daylight detector's 3/8 slab,
 * the end portal frame's 13/16 base with the eye's centre post, the bed's
 * 9/16 mattress;</li>
 * <li>the brewing stand's base + post pair, the cauldron's and the
 * hopper's full-height leg columns.</li>
 * </ul>
 */
public final class CollisionShapes {

    private CollisionShapes() {
    }

    /**
     * The neighbour read the connection families resolve against: the
     * block at the given offset from the queried cell. World-facing
     * callers supply their own dimension-aware read; shape queries
     * without a probe answer like an isolated block (no connections).
     */
    @FunctionalInterface
    public interface NeighborProbe {

        /** @return the neighbour type at the offset, null for none. */
        BlockType neighbor(int dx, int dy, int dz);
    }

    /** The no-connection probe: every neighbour reads as air. */
    static final NeighborProbe ISOLATED = (dx, dy, dz) -> null;

    /** One collision box as fractions of the cell (maxY may pass 1.0). */
    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        boolean contains(double fx, double fy, double fz) {
            return fx >= minX && fx < maxX && fy >= minY && fy < maxY && fz >= minZ && fz < maxZ;
        }
    }

    private static final Box FULL_CUBE = new Box(0, 0, 0, 1, 1, 1);

    // --- the public queries ------------------------------------------------

    /**
     * Whether the absolute point lies inside the type's collision shape:
     * any box contains the cell-fractional point. Types without a table
     * row answer the full cube when {@link WorldSolidity#isSolid} says
     * they block a body, and nothing at all otherwise.
     */
    public static boolean contains(BlockType type, double x, double y, double z,
            NeighborProbe probe) {
        if (type == null) {
            return false;
        }
        for (Box box : boxesOf(type, probe)) {
            if (box.contains(frac(x), frac(y), frac(z))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The top surface of the type's collision shape for a body resting at
     * the absolute point: the ceiling of the box the point's column hits.
     * A point already inside a box answers that box's top; a point above
     * every box in its column answers the highest box top at or below it
     * (the body settled onto the shape); no box in the column answers
     * {@link Double#NEGATIVE_INFINITY}.
     */
    public static double top(BlockType type, double x, double y, double z,
            NeighborProbe probe) {
        if (type == null) {
            return Double.NEGATIVE_INFINITY;
        }
        double fx = frac(x);
        double fy = frac(y);
        double fz = frac(z);
        double best = Double.NEGATIVE_INFINITY;
        for (Box box : boxesOf(type, probe)) {
            if (fx >= box.minX && fx < box.maxX && fz >= box.minZ && fz < box.maxZ) {
                if (fy >= box.minY && fy < box.maxY) {
                    return Math.floor(y) + box.maxY;
                }
                if (box.maxY <= fy && box.maxY > best) {
                    best = box.maxY;
                }
            }
        }
        return best == Double.NEGATIVE_INFINITY
                ? Double.NEGATIVE_INFINITY
                : Math.floor(y) + best;
    }

    /** @return whether the type carries the full-cube stand-in shape. */
    static boolean isFullCube(BlockType type) {
        if (!WorldSolidity.isSolid(type)) {
            return false;
        }
        List<Box> boxes = boxesOf(type, ISOLATED);
        return boxes.size() == 1 && boxes.get(0).equals(FULL_CUBE);
    }

    // --- the table ----------------------------------------------------------

    /** @return the type's collision boxes (the isolated form for connection families). */
    private static List<Box> boxesOf(BlockType type, NeighborProbe probe) {
        if (type == null) {
            return List.of();
        }
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return WorldSolidity.isSolid(type) ? List.of(FULL_CUBE) : List.of();
        }
        String value = id.value();

        // The six wood fences + the nether brick fence
        // (FenceBlock.addCollisions): the 1.5-tall post with the
        // connection arms — arm 1 the N/S band (post x, extended z),
        // arm 2 the W/E band (extended x, post z), both 1.5 tall.
        if (isFenceFamily(value)) {
            return fenceBoxes(value, probe);
        }
        // The wall pair (WallBlock.getCollisionShape): one box, the post
        // opening to the cell edge on the connected sides, the straight
        // pair's thin band, the collision 1.5 ceiling.
        if (value.equals("cobblestone_wall") || value.equals("mossy_cobblestone_wall")) {
            return List.of(wallBox(probe));
        }
        // The panes (PaneBlock.addCollisions): the 7/16-9/16 cross with the
        // half arms on one-sided connections, the full bars on both-sided,
        // the full cross isolated.
        if (value.equals("glass_pane") || value.equals("iron_bars")) {
            return paneBoxes(probe);
        }
        // The closed fence gates (FenceGateBlock.getCollisionShape): the
        // 3/8-thick panel across the facing axis, 1.5 tall. The open states
        // carry no box (walk-through, the reference's null).
        if (value.contains("_fence_gate")) {
            if (WorldSolidity.isOpenFenceGate(id)) {
                return List.of();
            }
            boolean axisZ = gateAxisZ(value);
            return List.of(axisZ
                    ? new Box(0, 0, 0.375, 1, 1.5, 0.625)
                    : new Box(0.375, 0, 0, 0.625, 1.5, 1));
        }
        // The trapdoor pair (TrapdoorBlock.updateShape): the closed 3/16
        // floor/ceiling panel, the open vertical panel against the hinge
        // wall per facing.
        if (value.equals("trapdoor") || value.startsWith("trapdoor_")
                || value.equals("iron_trapdoor") || value.startsWith("iron_trapdoor_")) {
            return List.of(trapdoorBox(value));
        }
        // The chest family (ChestBlock / EnderChestBlock): the 1/16-inset
        // 14/16 box, the double form's edge extending to the joined cell.
        if (isChestFamily(value)) {
            return List.of(chestBox(value, probe));
        }
        // The door families (DoorBlock.updateShape 1:1): the closed 3/16
        // panel against the hinge-side edge, the open panel swung against
        // the wall by the hinge — the facing and open bits live on the
        // lower half, the hinge bit on the upper, exactly like the
        // reference's combined-metadata read.
        if (isDoorFamily(value)) {
            return List.of(doorBox(value, probe));
        }
        // The thin surfaces.
        if (value.equals("carpet") || value.startsWith("carpet_")) {
            return List.of(new Box(0, 0, 0, 1, 0.0625, 1));
        }
        if (value.equals("snow_layer")) {
            return List.of(new Box(0, 0, 0, 1, 0.125, 1));
        }
        if (value.equals("waterlily")) {
            return List.of(new Box(0, 0, 0, 1, 0.015625, 1));
        }
        if (isPressurePlate(value)) {
            return List.of(new Box(0.0625, 0, 0.0625, 0.9375, 0.03125, 0.9375));
        }
        // The partial furniture.
        if (value.equals("flower_pot") || value.startsWith("potted_")) {
            return List.of(new Box(0.3125, 0, 0.3125, 0.6875, 0.375, 0.6875));
        }
        if (value.equals("cake") || value.startsWith("cake_bite")) {
            return List.of(cakeBox(value));
        }
        // The skull family (Slice 60 — SkullBlock.updateShape lines 70-87):
        // the floor mount's 0.25-0.75 half-height box, the wall facings
        // inset against their wall; the ceiling mount falls to the floor
        // box (the reference's own missing DOWN case — the switch's
        // default).
        if (value.equals("skull") || value.equals("skull_down")) {
            return List.of(new Box(0.25, 0, 0.25, 0.75, 0.5, 0.75));
        }
        if (value.equals("skull_north")) {
            return List.of(new Box(0.25, 0.25, 0.5, 0.75, 0.75, 1.0));
        }
        if (value.equals("skull_south")) {
            return List.of(new Box(0.25, 0.25, 0.0, 0.75, 0.75, 0.5));
        }
        if (value.equals("skull_west")) {
            return List.of(new Box(0.5, 0.25, 0.25, 1.0, 0.75, 0.75));
        }
        if (value.equals("skull_east")) {
            return List.of(new Box(0.0, 0.25, 0.25, 0.5, 0.75, 0.75));
        }
        if (value.equals("cactus")) {
            return List.of(new Box(0.0625, 0, 0.0625, 0.9375, 1, 0.9375));
        }
        if (value.equals("farmland")) {
            return List.of(new Box(0, 0, 0, 1, 0.9375, 1));
        }
        if (value.equals("enchanting_table")) {
            return List.of(new Box(0, 0, 0, 1, 0.75, 1));
        }
        if (value.equals("daylight_detector") || value.startsWith("daylight_detector_")) {
            return List.of(new Box(0, 0, 0, 1, 0.375, 1));
        }
        if (value.startsWith("end_portal_frame")) {
            return endPortalFrameBoxes(value);
        }
        if (value.equals("bed") || value.startsWith("bed_")) {
            return List.of(new Box(0, 0, 0, 1, 0.5625, 1));
        }
        if (value.equals("brewing_stand") || value.startsWith("brewing_stand_")) {
            // BrewingStandBlock.addCollisions: the 1/8 base plate + the
            // 7/16-9/16 centre post 7/8 tall.
            return List.of(
                    new Box(0, 0, 0, 1, 0.125, 1),
                    new Box(0.4375, 0, 0.4375, 0.5625, 0.875, 0.5625));
        }
        if (value.equals("cauldron") || value.startsWith("cauldron_")) {
            // CauldronBlock.addCollisions: the 5/16 floor + the four
            // full-height 1/8 leg columns.
            return List.of(
                    new Box(0, 0, 0, 1, 0.3125, 1),
                    new Box(0, 0, 0, 0.125, 1, 1),
                    new Box(0, 0, 0, 1, 1, 0.125),
                    new Box(0.875, 0, 0, 1, 1, 1),
                    new Box(0, 0, 0.875, 1, 1, 1));
        }
        if (value.equals("hopper") || value.startsWith("hopper_")) {
            // HopperBlock.addCollisions: the 5/8 funnel + the four
            // full-height 1/8 leg columns (the facing never moves the box).
            return List.of(
                    new Box(0, 0, 0, 1, 0.625, 1),
                    new Box(0, 0, 0, 0.125, 1, 1),
                    new Box(0, 0, 0, 1, 1, 0.125),
                    new Box(0.875, 0, 0, 1, 1, 1),
                    new Box(0, 0, 0.875, 1, 1, 1));
        }
        // The slab and stair halves: the bottom slab and the bottom stair
        // states fill their cell's lower half, the top slab and the
        // UPSIDE-DOWN stair states the upper (StairsBlock.
        // setBaseShapeForCollisions lines 74-81 — the reference's own
        // collision law: the base half box alone, the tall half's body
        // carries no collision in 1.8.8; the inner-corner riser is the
        // connection-dependent refinement). The stone_slab2 pair joins
        // through its own names (the '2' family breaks the suffix
        // conventions — Slice 52).
        if (value.endsWith("_slab_top") || value.equals("stone_slab2_top")
                || value.endsWith("_stairs_east_top")
                || value.endsWith("_stairs_west_top") || value.endsWith("_stairs_south_top")
                || value.endsWith("_stairs_north_top")) {
            return List.of(new Box(0, 0.5, 0, 1, 1, 1));
        }
        if (value.endsWith("_slab") || value.equals("stone_slab2")
                || value.endsWith("_stairs_east")
                || value.endsWith("_stairs_west") || value.endsWith("_stairs_south")
                || value.endsWith("_stairs_north")) {
            return List.of(new Box(0, 0, 0, 1, 0.5, 1));
        }
        // The anvil's own band (Slice 53 — AnvilBlock.updateShape): the X
        // axis facings (W/E) carry the z-thin box, the Z axis facings
        // (S/N) the x-thin box — the full 1.0 height in both, the DAMAGE
        // never moves the box.
        if (net.zaminmc.torch.server.block.AnvilBlocks.isAnvil(type.identifier())) {
            int facing = net.zaminmc.torch.server.block.AnvilBlocks.facingOf(type.identifier());
            return facing == 1 || facing == 3
                    ? List.of(new Box(0, 0, 0.125, 1, 1, 0.875))
                    : List.of(new Box(0.125, 0, 0, 0.875, 1, 1));
        }
        // The end portal (Slice 54 — EndPortalBlock.addCollisions: the
        // empty list — the film never blocks a body, exactly like the
        // reference's own override).
        if (net.zaminmc.torch.server.block.EndPortalBlocks.isEndPortal(type.identifier())) {
            return List.of();
        }
        // The dragon egg (Slice 55 — DragonEggBlock's own setShape): the
        // 1/16-inset obelisk, full height.
        if (net.zaminmc.torch.server.block.DragonEggBlocks.isDragonEgg(type.identifier())) {
            return List.of(new Box(0.0625, 0, 0.0625, 0.9375, 1, 0.9375));
        }
        // Everything else: the full cube when it blocks a body at all.
        return WorldSolidity.isSolid(type) ? List.of(FULL_CUBE) : List.of();
    }

    // --- the fence family ---------------------------------------------------

    /** The fence families: the six woods (Material.WOOD) + the nether brick (Material.STONE). */
    private static boolean isFenceFamily(String value) {
        return value.equals("oak_fence")
                || value.equals("spruce_fence") || value.equals("birch_fence")
                || value.equals("jungle_fence") || value.equals("dark_oak_fence")
                || value.equals("acacia_fence")
                || value.equals("nether_brick_fence");
    }

    /** Whether the fence family is the nether brick's own (Material.STONE). */
    private static boolean isNetherFence(String value) {
        return value.equals("nether_brick_fence");
    }

    /**
     * The fence boxes (FenceBlock.addCollisions 1:1): arm 1 on a north or
     * south connection — the post's x band over the extended z span, 1.5
     * tall; arm 2 on a west or east connection (or none at all — the
     * isolated post) — the extended x span over the post's z band.
     */
    private static List<Box> fenceBoxes(String value, NeighborProbe probe) {
        boolean nether = isNetherFence(value);
        boolean north = connectsFence(nether, probe, 0, -1);
        boolean south = connectsFence(nether, probe, 0, 1);
        boolean west = connectsFence(nether, probe, -1, 0);
        boolean east = connectsFence(nether, probe, 1, 0);
        java.util.ArrayList<Box> boxes = new java.util.ArrayList<>(2);
        if (north || south) {
            boxes.add(new Box(0.375, 0, north ? 0 : 0.375, 0.625, 1.5, south ? 1 : 0.625));
        }
        boxes.add(new Box(west ? 0 : 0.375, 0, 0.375, east ? 1 : 0.625, 1.5, 0.625));
        return boxes;
    }

    /**
     * FenceBlock.shouldConnectTo 1:1 — a same-material fence (every wood
     * fence shares Material.WOOD, the nether brick fence alone carries
     * Material.STONE and never joins the woods), any fence gate, or a full
     * cube that is not the pumpkin (the Material.PUMPKIN pair 86/91 stays
     * excluded, the reference's own quirk).
     */
    private static boolean connectsFence(boolean nether,
            NeighborProbe probe, int dx, int dz) {
        BlockType neighbor = probe.neighbor(dx, 0, dz);
        if (neighbor == null) {
            return false;
        }
        Identifier id = neighbor.identifier();
        if (!id.namespace().equals("minecraft")) {
            return isFullCube(neighbor);
        }
        String value = id.value();
        if (isFenceFamily(value)) {
            return isNetherFence(value) == nether; // the material match
        }
        if (value.contains("_fence_gate")) {
            return true;
        }
        if (value.equals("pumpkin") || value.equals("lit_pumpkin")) {
            return false; // Material.PUMPKIN: solid and cube, never connects
        }
        return isFullCube(neighbor);
    }

    // --- the wall -----------------------------------------------------------

    /**
     * The wall box (WallBlock 1:1): the 0.25-0.75 post opening to the cell
     * edge on the connected sides; the straight pair's 0.3125-0.6875 band
     * across the thin axis; the collision {@code maxY} 1.5 (the outline's
     * own 0.8125 straight height never reaches the collision read).
     */
    private static Box wallBox(NeighborProbe probe) {
        boolean north = connectsWall(probe, 0, -1);
        boolean south = connectsWall(probe, 0, 1);
        boolean west = connectsWall(probe, -1, 0);
        boolean east = connectsWall(probe, 1, 0);
        double minX = 0.25;
        double maxX = 0.75;
        double minZ = 0.25;
        double maxZ = 0.75;
        if (north) {
            minZ = 0;
        }
        if (south) {
            maxZ = 1;
        }
        if (west) {
            minX = 0;
        }
        if (east) {
            maxX = 1;
        }
        if (north && south && !west && !east) {
            minX = 0.3125;
            maxX = 0.6875;
        } else if (west && east && !north && !south) {
            minZ = 0.3125;
            maxZ = 0.6875;
        }
        return new Box(minX, 0, minZ, maxX, 1.5, maxZ);
    }

    /**
     * WallBlock.shouldConnectTo 1:1 — {@code block == this}: the wall's own
     * id family (the normal and the mossy variant are the same block's
     * metadata in the reference, so both connect), any fence gate, or a
     * full cube that is not the pumpkin.
     */
    private static boolean connectsWall(NeighborProbe probe, int dx, int dz) {
        BlockType neighbor = probe.neighbor(dx, 0, dz);
        if (neighbor == null) {
            return false;
        }
        Identifier id = neighbor.identifier();
        if (!id.namespace().equals("minecraft")) {
            return isFullCube(neighbor);
        }
        String value = id.value();
        if (value.equals("cobblestone_wall") || value.equals("mossy_cobblestone_wall")) {
            return true;
        }
        if (value.contains("_fence_gate")) {
            return true;
        }
        if (value.equals("pumpkin") || value.equals("lit_pumpkin")) {
            return false;
        }
        return isFullCube(neighbor);
    }

    // --- the panes ------------------------------------------------------------

    /**
     * The pane boxes (PaneBlock.addCollisions 1:1): the east-west bar (the
     * 7/16-9/16 z band) — the west half arm, the east half arm, or the
     * full bar on both-sided and isolated forms; then the north-south bar
     * the same way (the isolated cross carries both full bars, the
     * reference's own else arms).
     */
    private static List<Box> paneBoxes(NeighborProbe probe) {
        boolean north = connectsPane(probe, 0, -1);
        boolean south = connectsPane(probe, 0, 1);
        boolean west = connectsPane(probe, -1, 0);
        boolean east = connectsPane(probe, 1, 0);
        java.util.ArrayList<Box> boxes = new java.util.ArrayList<>(2);
        if ((!west || !east) && (west || east || north || south)) {
            if (west) {
                boxes.add(new Box(0, 0, 0.4375, 0.5, 1, 0.5625));
            } else if (east) {
                boxes.add(new Box(0.5, 0, 0.4375, 1, 1, 0.5625));
            }
        } else {
            boxes.add(new Box(0, 0, 0.4375, 1, 1, 0.5625));
        }
        if ((!north || !south) && (west || east || north || south)) {
            if (north) {
                boxes.add(new Box(0.4375, 0, 0, 0.5625, 1, 0.5));
            } else if (south) {
                boxes.add(new Box(0.4375, 0, 0.5, 0.5625, 1, 1));
            }
        } else {
            boxes.add(new Box(0.4375, 0, 0, 0.5625, 1, 1));
        }
        return boxes;
    }

    /**
     * PaneBlock.shouldConnectTo 1:1 — an opaque cube, the same pane, the
     * glass pair (plain + stained), or any pane family (the iron bars).
     */
    private static boolean connectsPane(NeighborProbe probe, int dx, int dz) {
        BlockType neighbor = probe.neighbor(dx, 0, dz);
        if (neighbor == null) {
            return false;
        }
        Identifier id = neighbor.identifier();
        if (!id.namespace().equals("minecraft")) {
            return isFullCube(neighbor);
        }
        String value = id.value();
        if (value.equals("glass_pane") || value.equals("iron_bars")) {
            return true;
        }
        if (value.equals("glass") || value.startsWith("stained_glass")) {
            return true;
        }
        return isFullCube(neighbor);
    }

    // --- the smaller families -------------------------------------------------

    /**
     * The gate's axis from the identifier: the reference's facing band
     * keeps the south/north pair on the Z axis and the west/east pair on
     * the X axis (the bare form is the south facing, meta 0).
     */
    private static boolean gateAxisZ(String value) {
        return !(value.endsWith("_west") || value.endsWith("_east"));
    }

    /** The trapdoor box (TrapdoorBlock.updateShape 1:1). */
    private static Box trapdoorBox(String value) {
        boolean open = value.contains("_open");
        boolean top = value.contains("_top");
        if (!open) {
            // The closed panel: the 3/16 floor or the matching ceiling.
            return top
                    ? new Box(0, 0.8125, 0, 1, 1, 1)
                    : new Box(0, 0, 0, 1, 0.1875, 1);
        }
        // The open vertical panel against the hinge wall per facing (the
        // bare form is the north facing, the family's meta-0 state).
        if (value.endsWith("_west")) {
            return new Box(0.8125, 0, 0, 1, 1, 1);
        }
        if (value.endsWith("_east")) {
            return new Box(0, 0, 0, 0.1875, 1, 1);
        }
        if (value.endsWith("_south")) {
            return new Box(0, 0, 0, 1, 1, 0.1875);
        }
        return new Box(0, 0, 0.8125, 1, 1, 1); // north (and the bare form)
    }

    /**
     * The chest family: the plain chest, the trapped chest, and the ender
     * chest's four facing states.
     */
    private static boolean isChestFamily(String value) {
        return value.equals("chest") || value.equals("trapped_chest")
                || value.startsWith("ender_chest");
    }

    /**
     * The chest box (ChestBlock 1:1): the 1/16-inset 14/16 box, the joined
     * edge extending to the cell boundary on the double form's side (the
     * same family neighbour the ChestManager pairs).
     */
    private static Box chestBox(String value, NeighborProbe probe) {
        double minX = 0.0625;
        double maxX = 0.9375;
        double minZ = 0.0625;
        double maxZ = 0.9375;
        if (sameChestFamily(value, probeValue(probe, 0, -1))) {
            minZ = 0;
        }
        if (sameChestFamily(value, probeValue(probe, 0, 1))) {
            maxZ = 1;
        }
        if (sameChestFamily(value, probeValue(probe, -1, 0))) {
            minX = 0;
        }
        if (sameChestFamily(value, probeValue(probe, 1, 0))) {
            maxX = 1;
        }
        return new Box(minX, 0, minZ, maxX, 0.875, maxZ);
    }

    /**
     * Whether the neighbour value belongs to the same chest family as the
     * queried state (ChestManager.isChestBlock's own pair rule: the plain
     * chest joins the plain chest, the trapped the trapped — a mixed pair
     * never forms).
     */
    private static boolean sameChestFamily(String value, String neighbor) {
        if (value.equals("trapped_chest")) {
            return "trapped_chest".equals(neighbor);
        }
        if (value.equals("chest")) {
            return "chest".equals(neighbor);
        }
        return neighbor != null && neighbor.startsWith("ender_chest")
                && value.startsWith("ender_chest");
    }

    /** @return the neighbour's identifier value at the offset, null for none. */
    private static String probeValue(NeighborProbe probe, int dx, int dz) {
        BlockType neighbor = probe.neighbor(dx, 0, dz);
        return neighbor == null ? null : neighbor.identifier().value();
    }

    /**
     * The cake box (CakeBlock.getCollisionShape 1:1): the bite shrinks the
     * eaten side — minX {@code (1 + bites * 2) / 16}, the far side and both
     * z faces keep the 1/16 inset, the height a flat 1/2.
     */
    private static Box cakeBox(String value) {
        int bites = 0;
        if (value.startsWith("cake_bite")) {
            try {
                bites = Integer.parseInt(value.substring("cake_bite".length()));
            } catch (NumberFormatException ignored) {
                bites = 0;
            }
        }
        return new Box((1 + bites * 2) / 16.0, 0, 0.0625, 0.9375, 0.5, 0.9375);
    }

    /**
     * The end portal frame (EndPortalFrameBlock 1:1): the 13/16 base, the
     * eye form adding the 5/16 centre post on top.
     */
    private static List<Box> endPortalFrameBoxes(String value) {
        if (value.contains("_eye_") || value.endsWith("_eye")) {
            return List.of(
                    new Box(0, 0, 0, 1, 0.8125, 1),
                    new Box(0.3125, 0.8125, 0.3125, 0.6875, 1, 0.6875));
        }
        return List.of(new Box(0, 0, 0, 1, 0.8125, 1));
    }

    /** The pressure plates: the stone/wood pair, the weighted pair. */
    private static boolean isPressurePlate(String value) {
        return value.equals("stone_pressure_plate") || value.startsWith("stone_pressure_plate_")
                || value.equals("wooden_pressure_plate") || value.startsWith("wooden_pressure_plate_")
                || value.equals("light_weighted_pressure_plate")
                || value.startsWith("light_weighted_pressure_plate_")
                || value.equals("heavy_weighted_pressure_plate")
                || value.startsWith("heavy_weighted_pressure_plate_");
    }

    // --- the door family --------------------------------------------------

    /**
     * The seven door families (the oak pair plus Slice 34's six): every
     * door identifier carries the {@code _door} marker — the trapdoors
     * spell their own family without the underscore and never match.
     */
    private static boolean isDoorFamily(String value) {
        return value.contains("_door");
    }

    /**
     * The door's own band flags parsed off the identifier: the facing band
     * ({@code _north/_east/_south} suffixes, the bare form band 0), the
     * open band ({@code _open}), the hinge ({@code _right}), and the
     * upper-half marker.
     */
    private static final class DoorState {
        final int band;
        final boolean open;
        final boolean rightHinge;
        final boolean upper;

        DoorState(String value) {
            this.upper = value.contains("_door_upper");
            this.open = value.contains("_open");
            this.rightHinge = value.contains("_right");
            if (value.endsWith("_north")) {
                this.band = 1;
            } else if (value.endsWith("_east")) {
                this.band = 2;
            } else if (value.endsWith("_south")) {
                this.band = 3;
            } else {
                this.band = 0;
            }
        }
    }

    /**
     * The door panel (DoorBlock.updateShape 1:1): the facing band decodes
     * through the reference's own {@code byIdHorizontal(band)
     * .counterClockwiseY()} — band 0 EAST, 1 SOUTH, 2 WEST, 3 NORTH. The
     * closed panel lies against the hinge-side edge; the open panel swings
     * flat against the wall, the side by the hinge bit. The facing and open
     * bits live on the lower half, the hinge bit on the upper — the pair
     * resolves through the probe exactly like the reference's combined
     * metadata read.
     */
    private static Box doorBox(String value, NeighborProbe probe) {
        DoorState own = new DoorState(value);
        int band = own.band;
        boolean open = own.open;
        boolean rightHinge = own.rightHinge;
        if (own.upper) {
            // The upper carries the hinge; the facing and open come from
            // the lower half below (the reference's combined read).
            DoorState lower = doorStateOf(probe.neighbor(0, -1, 0));
            if (lower != null) {
                band = lower.band;
                open = lower.open;
            }
        } else {
            // The lower carries facing and open; the hinge comes from the
            // upper half above.
            DoorState upper = doorStateOf(probe.neighbor(0, 1, 0));
            if (upper != null) {
                rightHinge = upper.rightHinge;
            }
        }
        double f = 0.1875;
        // The facing: band 0 EAST, 1 SOUTH, 2 WEST, 3 NORTH.
        if (!open) {
            return switch (band) {
                case 0 -> new Box(0, 0, 0, f, 1, 1);        // EAST: the west edge
                case 1 -> new Box(0, 0, 0, 1, 1, f);        // SOUTH: the north edge
                case 2 -> new Box(1 - f, 0, 0, 1, 1, 1);    // WEST: the east edge
                default -> new Box(0, 0, 1 - f, 1, 1, 1);   // NORTH: the south edge
            };
        }
        return switch (band) {
            case 0 -> rightHinge                           // EAST + open
                    ? new Box(0, 0, 1 - f, 1, 1, 1)
                    : new Box(0, 0, 0, 1, 1, f);
            case 1 -> rightHinge                           // SOUTH + open
                    ? new Box(0, 0, 0, f, 1, 1)
                    : new Box(1 - f, 0, 0, 1, 1, 1);
            case 2 -> rightHinge                           // WEST + open
                    ? new Box(0, 0, 0, 1, 1, f)
                    : new Box(0, 0, 1 - f, 1, 1, 1);
            default -> rightHinge                          // NORTH + open
                    ? new Box(1 - f, 0, 0, 1, 1, 1)
                    : new Box(0, 0, 0, f, 1, 1);
        };
    }

    /** @return the neighbour's door state flags when it is a door, null otherwise. */
    private static DoorState doorStateOf(BlockType neighbor) {
        if (neighbor == null) {
            return null;
        }
        Identifier id = neighbor.identifier();
        if (!id.namespace().equals("minecraft") || !isDoorFamily(id.value())) {
            return null;
        }
        return new DoorState(id.value());
    }

    /** @return the fractional part of the absolute coordinate. */
    private static double frac(double value) {
        return value - Math.floor(value);
    }
}
