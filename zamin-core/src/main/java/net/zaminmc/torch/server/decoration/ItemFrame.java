package net.zaminmc.torch.server.decoration;

import net.zaminmc.torch.item.ItemStack;

/**
 * One item frame body (Slice 28a — the reference port of
 * entity/decoration/ItemFrameEntity): the wall cell it hangs in, the
 * horizontal facing it points away from the wall with, the displayed
 * stack (a copy at size 1 — setDisplayItem's own rule), and the
 * eight-step rotation the right-click walk turns.
 *
 * <p>The frame's drop chance stays the reference's fixed 1.0 (the
 * {@code setDropChance} field never moves off its initializer in the
 * engine — no map decorations exist to care).</p>
 */
public final class ItemFrame {

    /** The reference's fixed drop chance (the field's own initializer). */
    static final float DROP_CHANCE = 1.0f;

    private final int entityId;
    private final net.zaminmc.torch.block.BlockPosition pos;
    /** The facing the frame points away from its wall (0-3, byIdHorizontal). */
    private final int facing;
    /** The world the frame hangs in (FL4-GAP2 Slice D — the per-world family). */
    private final int dimension;
    /** The displayed stack (EMPTY when bare). */
    private ItemStack display;
    /** The rotation step 0-7 (ItemFrameEntity's synced slot 9). */
    private int rotation;

    ItemFrame(int entityId, net.zaminmc.torch.block.BlockPosition pos, int facing, int dimension) {
        this.entityId = entityId;
        this.pos = pos;
        this.facing = facing & 3;
        this.dimension = dimension;
        this.display = ItemStack.EMPTY;
    }

    /** The frame's engine entity id (the Spawn Object 71 wire id). */
    public int entityId() {
        return entityId;
    }

    /** The wall cell the frame hangs in (the reference's own {@code pos}). */
    public net.zaminmc.torch.block.BlockPosition pos() {
        return pos;
    }

    /** The facing code the frame points away from its wall (0-3). */
    public int facing() {
        return facing;
    }

    /** The world the frame hangs in (the observer's own-world gate). */
    public int dimension() {
        return dimension;
    }

    /** The displayed stack (EMPTY when bare). */
    public ItemStack display() {
        return display;
    }

    /** The rotation step 0-7. */
    public int rotation() {
        return rotation;
    }

    /**
     * The setDisplayItem arm (ItemFrameEntity lines 117-133): the stack
     * copies at size 1 — the frame never holds more than one of anything.
     */
    void setDisplay(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            display = ItemStack.EMPTY;
        } else {
            display = ItemStack.of(stack.type(), 1)
                    .withDamage(stack.damage()).withName(stack.displayName());
        }
    }

    /** The clear arm (the takeDamage walk's own setDisplayItem(null)). */
    void clearDisplay() {
        display = ItemStack.EMPTY;
    }

    /**
     * The rotation arm (setItemRotation lines 143-148): the byte-sized
     * wrap {@code rotation % 8}.
     */
    void setRotation(int rotation) {
        this.rotation = Math.floorMod(rotation, 8);
    }

    /**
     * The comparator read (getAnalogSignal lines 196-198): no item answers
     * 0, an item answers {@code rotation % 8 + 1}.
     */
    public int analogSignal() {
        return display.isEmpty() ? 0 : rotation % 8 + 1;
    }

    /**
     * The frame's hanging position (DecorationEntity.updateShape lines
     * 42-73): the wall cell's center pulled back onto the wall face
     * ({@code 0.5 - 0.46875 * facingOffset}), the exact spot the 12x12
     * pixel quad renders at.
     */
    public net.zaminmc.torch.util.Position hangingPosition() {
        int[] off = FACING_OFFSETS[facing];
        double x = pos.x() + 0.5 - off[0] * 0.46875;
        double z = pos.z() + 0.5 - off[1] * 0.46875;
        // The 12-unit-tall frame rides the cell's vertical center.
        return new net.zaminmc.torch.util.Position(x, pos.y() + 0.5, z);
    }

    /**
     * The horizontal facing offsets (byIdHorizontal 0-3: south +Z, west
     * -X, north -Z, east +X — the reference Direction's own table): the
     * cell a body hangs in sits one step INTO the air the facing points
     * to, away from the wall cell. Public for the anvil import's anchor
     * decode (the pre-1.4.2 {@code Direction} form's own offset walk).
     */
    public static final int[][] FACING_OFFSETS = {
            {0, 1},   // south +Z
            {-1, 0},  // west -X
            {0, -1},  // north -Z
            {1, 0},   // east +X
    };
}
