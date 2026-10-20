package net.zaminmc.torch.server.decoration;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.gamerule.GameRules;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * The decoration system (Slice 28a/28b — the reference port of
 * entity/decoration/{DecorationEntity, ItemFrameEntity, PaintingEntity} +
 * item/DecorationItem): the frame and painting registries, the shared
 * survival poll (the 100-tick {@code canSurvive} walk), the placement
 * verb (DecorationItem.useOn's horizontal-face gate + the cell offset +
 * the survive gate + the consume), the frame's interact ladder (the
 * insert + the rotate), the frame's attack ladder (the item-drop arm
 * against the break arm), the painting's attack drop, the frame's
 * comparator consult (the through-the-wall read), and the ZED
 * persistence.
 *
 * <p>The bodies stay owned here; the wire events (the Spawn Object 71,
 * the Spawn Painting, the Set Entity Metadata pair, the Destroy) fan
 * through the listener. The reference's own survival quirk stays: a
 * broken wall leaves the body hanging for up to five seconds before
 * the poll finds it.</p>
 */
public final class DecorationSystem {

    /** The survival poll's cadence (DecorationEntity.tick: every 100 ticks). */
    private static final int VALIDATION_INTERVAL = 100;
    /** The frame's wall inset (DecorationEntity.updateShape's 0.46875). */
    private static final double WALL_INSET = 0.46875;

    /** Events the protocol adapter translates into the wire form. */
    public interface Listener {
        /** A frame spawned (the Spawn Object 71 + the metadata pair). */
        default void onFrameSpawned(ItemFrame frame) { }

        /** A frame removed (the Destroy Entities). */
        default void onFrameRemoved(ItemFrame frame) { }

        /** A frame's displayed stack changed (the Set Entity Metadata slot 8). */
        default void onFrameItemChanged(ItemFrame frame) { }

        /** A frame's rotation changed (the Set Entity Metadata slot 9). */
        default void onFrameRotationChanged(ItemFrame frame) { }

        /** A painting spawned (the Spawn Painting packet). */
        default void onPaintingSpawned(Painting painting) { }

        /** A painting removed (the Destroy Entities). */
        default void onPaintingRemoved(Painting painting) { }
    }

    /** The engine surface the system reads (the world + the drops + the rules). */
    public interface Surface {
        /** The world the bodies hang in. */
        World world();

        /** An item drop at a position (the reference's dropItem at 0.0 upward). */
        void dropItem(Position at, ItemStack stack);

        /** The gamerule read (doEntityDrops gates every drop arm). */
        boolean doEntityDrops();

        /** The comparator wake (World.updateNeighborComparators over the cell). */
        void wakeComparators(BlockPosition cell);
    }

    private final Surface surface;
    private final List<Listener> listeners = new ArrayList<>();
    /** The live frames keyed by their wall cell. */
    private final Map<BlockPosition, ItemFrame> frames = new LinkedHashMap<>();
    /** The live paintings keyed by their wall cell. */
    private final Map<BlockPosition, Painting> paintings = new LinkedHashMap<>();
    private final int firstId;
    private int nextId;
    /** The per-body validation clocks (the reference's own ticksSinceValidation). */
    private final Map<Integer, Integer> validationTicks = new LinkedHashMap<>();
    private final Random random;

    public DecorationSystem(Surface surface, int firstId, Random random) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.firstId = firstId;
        this.nextId = firstId;
        this.random = Objects.requireNonNull(random, "random");
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** The id band the decorations live in. */
    public int firstId() {
        return firstId;
    }

    /** Every live frame (the join view's walk). */
    public List<ItemFrame> allFrames() {
        return new ArrayList<>(frames.values());
    }

    /** Every live painting (the join view's walk). */
    public List<Painting> allPaintings() {
        return new ArrayList<>(paintings.values());
    }

    /** The frame at a cell, or null. */
    public ItemFrame frameAt(BlockPosition cell) {
        return frames.get(cell);
    }

    /** The painting at a cell, or null. */
    public Painting paintingAt(BlockPosition cell) {
        return paintings.get(cell);
    }

    /** The frame with the engine entity id, or null (the interact dispatch). */
    public ItemFrame frameById(int entityId) {
        for (ItemFrame frame : frames.values()) {
            if (frame.entityId() == entityId) {
                return frame;
            }
        }
        return null;
    }

    /** The painting with the engine entity id, or null. */
    public Painting paintingById(int entityId) {
        for (Painting painting : paintings.values()) {
            if (painting.entityId() == entityId) {
                return painting;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The placement (DecorationItem.useOn lines 21-42)
    // ------------------------------------------------------------------

    /**
     * The frame item's placement (DecorationItem.useOn + ItemFrameEntity):
     * the vertical faces refuse outright, the cell lands at the clicked
     * face's offset, the survival gate (the solid wall + the clear box)
     * refuses without the consume, and a surviving frame consumes one
     * unit (the caller's creative convention spares the creative hand).
     *
     * @return whether a frame landed (the useOn's own always-true means
     * the click is eaten either way — the caller decides the consume).
     */
    public boolean placeFrame(BlockPosition clicked, int face, int facingCode) {
        if (face == 0 || face == 1) {
            return false; // DOWN / UP: the reference's own refusal
        }
        int[] off = ItemFrame.FACING_OFFSETS[facingCode & 3];
        BlockPosition cell = clicked.offset(off[0], 0, off[1]);
        ItemFrame frame = new ItemFrame(nextId, cell, facingCode & 3);
        if (!canSurviveFrame(frame)) {
            return false; // refused: the stack intact
        }
        nextId++;
        frames.put(cell, frame);
        validationTicks.put(frame.entityId(), 0);
        for (Listener listener : listeners) {
            listener.onFrameSpawned(frame);
        }
        return true;
    }

    /**
     * The painting item's placement (DecorationItem.useOn +
     * PaintingEntity's ctor lines 21-38): the vertical faces refuse, then
     * the fit walk collects every motive whose body survives the wall and
     * rolls one at random (the empty list leaves the stack intact — the
     * reference's own fallback body never spawns when nothing fits).
     *
     * @return whether a painting landed.
     */
    public boolean placePainting(BlockPosition clicked, int face, int facingCode) {
        if (face == 0 || face == 1) {
            return false; // DOWN / UP
        }
        int[] off = ItemFrame.FACING_OFFSETS[facingCode & 3];
        BlockPosition cell = clicked.offset(off[0], 0, off[1]);
        // The fit roll (the constructor's own walk): every motive that
        // survives collects, one lands at random.
        List<Painting.Motive> fits = new ArrayList<>();
        for (Painting.Motive motive : Painting.Motive.values()) {
            if (canSurviveBody(cell, facingCode & 3, motive.width, motive.height)) {
                fits.add(motive);
            }
        }
        if (fits.isEmpty()) {
            return false; // nothing fits: the stack intact
        }
        Painting.Motive rolled = fits.get(random.nextInt(fits.size()));
        Painting painting = new Painting(nextId, cell, facingCode & 3, rolled);
        nextId++;
        paintings.put(cell, painting);
        validationTicks.put(painting.entityId(), 0);
        for (Listener listener : listeners) {
            listener.onPaintingSpawned(painting);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The restored landings (the Anvil import's exact-cell form)
    // ------------------------------------------------------------------

    /**
     * The frame's restored form (the Anvil import's exact-cell landing —
     * the reference's {@code Entities.create} + {@code readCustomNbt}
     * walk over ItemFrameEntity/DecorationEntity): the anchor cell, the
     * facing, the displayed stack and the rotation land whole from the
     * saved rows. No placement fit roll, no survival gate — the
     * reference's own load lets the per-100-tick validation poll drop a
     * body whose wall is gone, and so does the engine. The displayed
     * stack lands through the same package setter the interact arm uses
     * (the reference's own setDisplayItem(false) — no comparator wake on
     * the load path).
     *
     * <p>The {@code ItemDropChance} row has no per-frame surface (the
     * engine's fixed 1.0 drop chance, the reference field's own
     * initializer) — the import's ledger names it.</p>
     *
     * @return the landed frame, or null when the cell already carries a
     *         frame (the engine keys frames by wall cell — a duplicate
     *         anchor would silently evict the first, so the second
     *         refuses and the import isolates it loudly).
     */
    public ItemFrame restoreFrame(BlockPosition cell, int facing,
                                  ItemStack display, int rotation) {
        Objects.requireNonNull(cell, "cell");
        if (frames.containsKey(cell)) {
            return null; // the occupied-anchor refusal, never a silent evict
        }
        ItemFrame frame = new ItemFrame(nextId, cell, facing);
        if (display != null && !display.isEmpty()) {
            frame.setDisplay(display);
        }
        frame.setRotation(rotation); // the setter's own % 8 wrap
        nextId++;
        frames.put(cell, frame);
        validationTicks.put(frame.entityId(), 0);
        for (Listener listener : listeners) {
            listener.onFrameSpawned(frame);
        }
        return frame;
    }

    /**
     * The painting's restored form (the Anvil import's exact-cell
     * landing — the reference's {@code readCustomNbt} over
     * PaintingEntity/DecorationEntity): the anchor cell, the facing and
     * the motive land whole. No fit roll (the saved variant IS the
     * variant — the reference never re-rolls on load), no survival gate
     * (the validation poll owns the unsupported-wall drop).
     *
     * @return the landed painting, or null when the cell already carries
     *         a painting (the occupied-anchor refusal, as the frame's
     *         own restore arm documents).
     */
    public Painting restorePainting(BlockPosition cell, int facing,
                                    Painting.Motive motive) {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(motive, "motive");
        if (paintings.containsKey(cell)) {
            return null; // the occupied-anchor refusal, never a silent evict
        }
        Painting painting = new Painting(nextId, cell, facing & 3, motive);
        nextId++;
        paintings.put(cell, painting);
        validationTicks.put(painting.entityId(), 0);
        for (Listener listener : listeners) {
            listener.onPaintingSpawned(painting);
        }
        return painting;
    }

    // ------------------------------------------------------------------
    // The survival walk (DecorationEntity.canSurvive lines 93-120)
    // ------------------------------------------------------------------

    /**
     * The frame's own survive gate: the single wall cell + the clear box
     * (the frame's 12x12 quad) + no other decoration overlapping.
     */
    private boolean canSurviveFrame(ItemFrame frame) {
        if (!wallSolid(frame.pos(), frame.facing(), 1, 1)) {
            return false;
        }
        return boxClear(frame.pos(), frame.facing(), 12, 12);
    }

    /**
     * The generic body's survive walk: the wall band (i = width/16, j =
     * height/16 cells walked counter-clockwise from the anchor, the
     * solid-or-diode gate) then the hanging box's own checks (no solid
     * block inside, no other decoration's box inside).
     */
    private boolean canSurviveBody(BlockPosition cell, int facing, int widthPx, int heightPx) {
        int i = Math.max(1, widthPx / 16);
        int j = Math.max(1, heightPx / 16);
        if (!wallSolid(cell, facing, i, j)) {
            return false;
        }
        return boxClear(cell, facing, widthPx, heightPx);
    }

    /**
     * The wall scan (canSurvive lines 98-111): the cells behind the body
     * ({@code pos - facing} walked counter-clockwise by i and up by j)
     * must be solid or a diode (the repeater/comparator mounts count).
     */
    private boolean wallSolid(BlockPosition pos, int facing, int i, int j) {
        int[] back = ItemFrame.FACING_OFFSETS[(facing + 2) & 3];
        int[] cc = ItemFrame.FACING_OFFSETS[(facing + 3) & 3]; // counterClockwiseY
        BlockPosition wall = pos.offset(back[0], 0, back[1]);
        World world = surface.world();
        for (int k = 0; k < i; k++) {
            for (int l = 0; l < j; l++) {
                BlockPosition probe = wall.offset(cc[0] * k, l, cc[1] * k);
                BlockType type = world.getBlock(probe);
                if (!net.zaminmc.torch.server.block.WorldSolidity.isSolid(type)
                        && !isDiode(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The diode gate (canSurvive's own: repeaters and comparators mount art). */
    private static boolean isDiode(BlockType type) {
        return net.zaminmc.torch.server.redstone.RedstoneBlocks.isRepeater(type)
                || net.zaminmc.torch.server.redstone.RedstoneBlocks.isComparator(type);
    }

    /**
     * The box checks (canSurvive lines 93-97 + 113-118): no solid block
     * intersects the hanging box, and no other decoration's box overlaps
     * this one.
     */
    private boolean boxClear(BlockPosition pos, int facing, int widthPx, int heightPx) {
        double[] box = hangingBox(pos, facing, widthPx, heightPx);
        World world = surface.world();
        // The solid scan over the cells the box touches.
        int minX = (int) Math.floor(box[0]);
        int minY = (int) Math.floor(box[1]);
        int minZ = (int) Math.floor(box[2]);
        int maxX = (int) Math.floor(box[3]);
        int maxY = (int) Math.floor(box[4]);
        int maxZ = (int) Math.floor(box[5]);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockType type = world.getBlock(new BlockPosition(x, y, z));
                    if (net.zaminmc.torch.server.block.WorldSolidity.isSolid(type)) {
                        return false;
                    }
                }
            }
        }
        // The decoration overlap scan (the reference's getEntities walk).
        for (ItemFrame frame : frames.values()) {
            if (boxesOverlap(box, hangingBox(frame.pos(), frame.facing(), 12, 12))) {
                return false;
            }
        }
        for (Painting painting : paintings.values()) {
            if (boxesOverlap(box, hangingBox(painting.pos(), painting.facing(),
                    painting.motive().width, painting.motive().height))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The hanging box (DecorationEntity.updateShape lines 42-73): the
     * wall cell's center pulled onto the wall face ({@code -0.46875}
     * along the facing), centered along the wall by the even-pixel rule
     * ({@code length % 32 == 0 ? 0.5 : 0} — the height's own offset lifts
     * the even art half a block up), the half-extents {@code width/32}
     * along the wall (a 16-pixel span covers one block) and
     * {@code 1/32} into the room.
     */
    static double[] hangingBox(BlockPosition pos, int facing, int widthPx, int heightPx) {
        int[] off = ItemFrame.FACING_OFFSETS[facing];
        int[] cc = ItemFrame.FACING_OFFSETS[(facing + 3) & 3];
        double centerAlong = centerOffset(widthPx);
        double up = centerOffset(heightPx);
        double cx = pos.x() + 0.5 - off[0] * WALL_INSET + cc[0] * centerAlong;
        double cy = pos.y() + 0.5 + up;
        double cz = pos.z() + 0.5 - off[1] * WALL_INSET + cc[1] * centerAlong;
        double halfAlong = widthPx / 32.0;
        double halfH = heightPx / 32.0;
        double halfPerp = 1.0 / 32.0;
        // The facing axis carries the 1/32 depth; the other horizontal
        // axis carries the width (the reference's own d6/d8 swap).
        double dx = off[0] != 0 ? halfPerp : halfAlong;
        double dz = off[1] != 0 ? halfPerp : halfAlong;
        return new double[] {cx - dx, cy - halfH, cz - dz,
                cx + dx, cy + halfH, cz + dz};
    }

    /** The even-pixel centering (updateShape's getPositionOffset). */
    private static double centerOffset(int pixels) {
        return pixels % 32 == 0 ? 0.5 : 0.0;
    }

    private static boolean boxesOverlap(double[] a, double[] b) {
        return a[0] < b[3] && b[0] < a[3]
                && a[1] < b[4] && b[1] < a[4]
                && a[2] < b[5] && b[2] < a[5];
    }

    // ------------------------------------------------------------------
    // The frame's interact (ItemFrameEntity.interact lines 180-194)
    // ------------------------------------------------------------------

    /** The interact arm that ran. */
    public enum FrameInteract { NOTHING, INSERTED, ROTATED }

    /**
     * The frame's right-click ladder: a bare frame takes a copy of the
     * held stack (the size-1 copy rule, one unit off the hand — the
     * caller's creative convention), a loaded frame turns one step. Both
     * arms wake the comparators (setDisplayItem's + setItemRotation's own
     * updateNeighborComparators).
     *
     * @return the arm that ran (the caller consumes one unit on INSERTED).
     */
    public FrameInteract interactFrame(ItemFrame frame, ItemStack held) {
        Objects.requireNonNull(frame, "frame");
        if (frame.display().isEmpty()) {
            if (held.isEmpty()) {
                return FrameInteract.NOTHING; // the bare-hand click on a bare frame
            }
            frame.setDisplay(held);
            surface.wakeComparators(frame.pos());
            for (Listener listener : listeners) {
                listener.onFrameItemChanged(frame);
            }
            return FrameInteract.INSERTED;
        }
        frame.setRotation(frame.rotation() + 1);
        surface.wakeComparators(frame.pos());
        for (Listener listener : listeners) {
            listener.onFrameRotationChanged(frame);
        }
        return FrameInteract.ROTATED;
    }

    // ------------------------------------------------------------------
    // The attacks (ItemFrameEntity.takeDamage + onAttack + the painting's)
    // ------------------------------------------------------------------

    /**
     * The frame's attack (takeDamage lines 40-55 + dropItemOrItemFrame
     * lines 79-100): a non-explosive hit on a loaded frame ejects the
     * item (the creative hand removes it silently), anything else breaks
     * the frame off the wall — the frame item drops (the creative hand
     * spares it) and the loaded item follows with it.
     *
     * @return true when the hit landed (the takeDamage's own true).
     */
    public boolean attackFrame(ItemFrame frame, boolean creativeAttacker, boolean explosive) {
        Objects.requireNonNull(frame, "frame");
        if (!explosive && !frame.display().isEmpty()) {
            // The item-eject arm: the frame stays on the wall.
            dropDisplay(frame, creativeAttacker, false);
            frame.clearDisplay();
            surface.wakeComparators(frame.pos());
            for (Listener listener : listeners) {
                listener.onFrameItemChanged(frame);
            }
            return true;
        }
        // The break arm (the base takeDamage): the frame leaves the wall.
        removeFrame(frame);
        if (surface.doEntityDrops() && !creativeAttacker) {
            surface.dropItem(frame.hangingPosition(),
                    ItemStack.of(BuiltinItems.ITEM_FRAME, 1));
            if (!frame.display().isEmpty()) {
                dropDisplay(frame, false, false);
            }
        }
        return true;
    }

    /**
     * The display drop (dropItemOrItemFrame lines 90-98): the item's own
     * copy at the chance roll (the reference's fixed 1.0) — the creative
     * attacker removes it without the drop.
     */
    private void dropDisplay(ItemFrame frame, boolean creative, boolean dropSelf) {
        if (!surface.doEntityDrops()) {
            return;
        }
        if (creative) {
            return; // the creative hand: the item vanishes
        }
        if (dropSelf) {
            surface.dropItem(frame.hangingPosition(),
                    ItemStack.of(BuiltinItems.ITEM_FRAME, 1));
        }
        surface.dropItem(frame.hangingPosition(), frame.display());
    }

    /**
     * The painting's attack (PaintingEntity.onAttack lines 87-98): the
     * body falls and the painting item drops (the creative hand spares
     * it, the doEntityDrops rule gates it).
     */
    public void attackPainting(Painting painting, boolean creativeAttacker) {
        Objects.requireNonNull(painting, "painting");
        removePainting(painting);
        if (surface.doEntityDrops() && !creativeAttacker) {
            surface.dropItem(new Position(painting.pos().x() + 0.5, painting.pos().y() + 0.5,
                    painting.pos().z() + 0.5),
                    ItemStack.of(BuiltinItems.PAINTING, 1));
        }
    }

    /** The removal (the registry drop + the wire destroy). */
    public void removeFrame(ItemFrame frame) {
        frames.remove(frame.pos());
        validationTicks.remove(frame.entityId());
        for (Listener listener : listeners) {
            listener.onFrameRemoved(frame);
        }
    }

    /** The removal (the registry drop + the wire destroy). */
    public void removePainting(Painting painting) {
        paintings.remove(painting.pos());
        validationTicks.remove(painting.entityId());
        for (Listener listener : listeners) {
            listener.onPaintingRemoved(painting);
        }
    }

    // ------------------------------------------------------------------
    // The comparator consult (ComparatorBlock.getInputSignal lines 108-128)
    // ------------------------------------------------------------------

    /**
     * The through-the-wall frame read (ComparatorBlock.getInputSignal
     * lines 118-126): the cell one beyond the comparator's input block,
     * when it holds air and a frame facing the SAME direction the
     * comparator reads from ({@code getHorizontalFacing() == facing}),
     * answers the frame's rotation signal.
     */
    public int frameComparatorRead(BlockPosition cell, int comparatorFacing) {
        World world = surface.world();
        BlockType type = world.getBlock(cell);
        if (!type.identifier().equals(net.zaminmc.torch.util.Identifier.parse("minecraft:air"))) {
            return -1;
        }
        ItemFrame frame = frames.get(cell);
        if (frame == null || frame.facing() != comparatorFacing) {
            return -1;
        }
        return frame.analogSignal();
    }

    // ------------------------------------------------------------------
    // The survival poll (DecorationEntity.tick lines 80-91)
    // ------------------------------------------------------------------

    /**
     * One tick of every body's survival clock: each 100 ticks the
     * canSurvive walk re-runs, and a failed body falls with its own
     * drop (the reference's {@code remove() + onAttack(null)} — the null
     * attacker is not a creative player, the drops always land).
     */
    public void tick() {
        List<ItemFrame> fallenFrames = null;
        for (ItemFrame frame : frames.values()) {
            int ticks = validationTicks.getOrDefault(frame.entityId(), 0) + 1;
            if (ticks < VALIDATION_INTERVAL) {
                validationTicks.put(frame.entityId(), ticks);
                continue;
            }
            validationTicks.put(frame.entityId(), 0);
            if (!canSurviveFrame(frame)) {
                if (fallenFrames == null) {
                    fallenFrames = new ArrayList<>();
                }
                fallenFrames.add(frame);
            }
        }
        if (fallenFrames != null) {
            for (ItemFrame frame : fallenFrames) {
                attackFrame(frame, false, false);
            }
        }
        List<Painting> fallenPaintings = null;
        for (Painting painting : paintings.values()) {
            int ticks = validationTicks.getOrDefault(painting.entityId(), 0) + 1;
            if (ticks < VALIDATION_INTERVAL) {
                validationTicks.put(painting.entityId(), ticks);
                continue;
            }
            validationTicks.put(painting.entityId(), 0);
            if (!canSurviveBody(painting.pos(), painting.facing(),
                    painting.motive().width, painting.motive().height)) {
                if (fallenPaintings == null) {
                    fallenPaintings = new ArrayList<>();
                }
                fallenPaintings.add(painting);
            }
        }
        if (fallenPaintings != null) {
            for (Painting painting : fallenPaintings) {
                attackPainting(painting, false);
            }
        }
    }

    // ------------------------------------------------------------------
    // The persistence (the ZED snapshot form)
    // ------------------------------------------------------------------

    /** One frame's persistable view (writeCustomNbt's own fields). */
    public record FrameRecord(BlockPosition pos, int facing, ItemStack item, int rotation) { }

    /** One painting's persistable view (writeCustomNbt's own fields). */
    public record PaintingRecord(BlockPosition pos, int facing, String motive) { }

    /** The full persistable snapshot (saved at saveAllNow). */
    public record Snapshot(List<FrameRecord> frames, List<PaintingRecord> paintings) { }

    /** The full snapshot (saved at saveAllNow). */
    public Snapshot snapshot() {
        List<FrameRecord> frameRecords = new ArrayList<>();
        for (ItemFrame frame : frames.values()) {
            frameRecords.add(new FrameRecord(frame.pos(), frame.facing(),
                    frame.display(), frame.rotation()));
        }
        List<PaintingRecord> paintingRecords = new ArrayList<>();
        for (Painting painting : paintings.values()) {
            paintingRecords.add(new PaintingRecord(painting.pos(), painting.facing(),
                    painting.motive().name));
        }
        return new Snapshot(frameRecords, paintingRecords);
    }

    /**
     * The restore: the saved bodies land back on their walls (the frames
     * with their stacks and rotations, the paintings with their motives),
     * announced through the listener like a fresh spawn. A body whose
     * wall cannot hold it anymore still lands — the next survival poll
     * sweeps it, the reference's own readCustomNbt behavior.
     */
    public void restore(List<FrameRecord> frameRecords, List<PaintingRecord> paintingRecords) {
        for (FrameRecord record : frameRecords) {
            if (frames.containsKey(record.pos())) {
                continue;
            }
            ItemFrame frame = new ItemFrame(nextId++, record.pos(), record.facing());
            frame.setDisplay(record.item());
            frame.setRotation(record.rotation());
            frames.put(record.pos(), frame);
            validationTicks.put(frame.entityId(), 0);
            for (Listener listener : listeners) {
                listener.onFrameSpawned(frame);
            }
        }
        for (PaintingRecord record : paintingRecords) {
            if (paintings.containsKey(record.pos())) {
                continue;
            }
            Painting.Motive motive = Painting.Motive.byName(record.motive());
            if (motive == null) {
                motive = Painting.Motive.KEBAB; // readCustomNbt's own fallback
            }
            Painting painting = new Painting(nextId++, record.pos(), record.facing(), motive);
            paintings.put(record.pos(), painting);
            validationTicks.put(painting.entityId(), 0);
            for (Listener listener : listeners) {
                listener.onPaintingSpawned(painting);
            }
        }
    }
}
