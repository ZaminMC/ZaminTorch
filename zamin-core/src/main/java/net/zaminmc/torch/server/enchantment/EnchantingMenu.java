package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.WindowClicks;

import java.util.List;
import java.util.Random;

/**
 * The enchanting table's menu state (the historical
 * {@code inventory/menu/EnchantingTableMenu.java}): the two transient slots
 * (the item at max 1, the lapis), the per-player seed, the three slot costs
 * and the three clue values. The slots live in the menu — vanilla keeps them
 * in a per-open {@code SimpleInventory}, not in the block — and the close
 * drops both to the world.
 *
 * <p>Every arm is transcribed from the reference: the bookshelf power scan
 * ({@code onContentsChanged}, the eight-neighborhood walk with the inner
 * air gate and the corner sextuples), the cost ladder over
 * {@link EnchantmentHelper#requiredXpLevel} with the {@code < slot+1 → 0}
 * zeroing, the clue pick ({@code id | level << 8}), the button gates
 * ({@code onButtonClick}: lapis ≥ slot+1, XP ≥ slot+1 and XP ≥ cost, the
 * creative bypasses), the book-to-enchanted-book conversion, and the exact
 * recompute ordering after an enchant (markDirty with the old seed, then the
 * re-seeded recompute).</p>
 */
public final class EnchantingMenu {

    /** The world reads the power scan needs (air + block identity). */
    public interface WorldView {
        BlockType blockAt(BlockPosition position);
    }

    /**
     * The player-side arms the enchant button needs (the historical
     * {@code player.xpLevel}/{@code abilities.creativeMode}/
     * {@code applyEnchantmentCosts}/{@code getEnchantingSeed}).
     */
    public interface Enchanter {
        int xpLevel();

        boolean creative();

        /** Consumes {@code cost} levels (clamping at 0 like the reference) and rerolls the seed. */
        void applyEnchantmentCosts(int cost);

        /** The seed after any reroll ({@code getEnchantingSeed}). */
        int enchantingSeed();
    }

    /** The two menu slots: 0 the item, 1 the lapis (the menu's own inventory). */
    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY};

    private final Random random = new Random();
    private int seed;

    /** The scanned table position — every scan offset rides it. */
    private final BlockPosition origin;

    /**
     * The world the menu reads for the bookshelf scan (the reference menu
     * carries {@code world}); the live recompute arms use it, detached test
     * calls pass their own view or null.
     */
    private WorldView world;

    private final int originY;

    /** The XP level requirement per slot (the reference {@code enchantingCosts}). */
    private final int[] costs = new int[3];

    /** The clue value per slot, {@code id | level << 8}, -1 when none ({@code enchantmentClues}). */
    private final int[] clues = {-1, -1, -1};

    /**
     * Bumped on every observable change (slot contents, costs, clues). The
     * per-tick view diff uses it — the historical
     * {@code detectAndSendChanges}/{@code updateListeners} pairing.
     */
    private long revision;

    public EnchantingMenu(int seed, BlockPosition position) {
        this.seed = seed;
        this.origin = position == null ? new BlockPosition(0, 0, 0) : position;
        this.originY = this.origin.y();
    }

    // --- slot access (clicks, quick-move, close-drop) -----------------------

    public ItemStack item() {
        return slots[0];
    }

    public ItemStack lapis() {
        return slots[1];
    }

    /**
     * Attaches the live world (the engine's open path): the click and button
     * recomputes then scan the real bookshelf neighborhood, exactly like the
     * reference menu's constructor-carried world.
     */
    public void attach(WorldView view) {
        this.world = view;
    }

    /** @return the attached world view, or null when detached (tests). */
    public WorldView attachedWorld() {
        return world;
    }

    // Test hooks: direct slot placement (bypasses the click walk; the state
    // tests recompute explicitly after using them).

    /** Test hook: places a stack directly in the item slot. */
    public void itemSlotSet(ItemStack stack) {
        slots[0] = stack;
    }

    /** Test hook: places a stack directly in the lapis slot. */
    public void lapisSlotSet(ItemStack stack) {
        slots[1] = stack;
    }

    /** @return the dropped stacks when the window closes (the {@code close} walk). */
    public ItemStack[] closeDrop() {
        ItemStack[] dropped = {slots[0], slots[1]};
        slots[0] = ItemStack.EMPTY;
        slots[1] = ItemStack.EMPTY;
        onContentsChanged(null);
        return dropped;
    }

    // --- the reference onContentsChanged ------------------------------------

    /**
     * The contents-driven recompute: the bookshelf scan, the seeded cost
     * ladder, the clue picks. {@code world} may be null — the attached view
     * (the engine's open path) substitutes; with neither, the ladder zeroes
     * (detached test calls). Every mutation bumps the revision.
     */
    public void onContentsChanged(WorldView world) {
        WorldView view = world != null ? world : this.world;
        revision++;
        ItemStack item = slots[0];
        if (view != null && isEnchantable(item)) {
            int power = bookshelfPower(view);
            this.random.setSeed(this.seed);
            for (int slot = 0; slot < 3; slot++) {
                this.costs[slot] = EnchantmentHelper.requiredXpLevel(this.random, slot, power, item);
                this.clues[slot] = -1;
                if (this.costs[slot] < slot + 1) {
                    this.costs[slot] = 0;
                }
            }
            for (int slot = 0; slot < 3; slot++) {
                if (this.costs[slot] > 0) {
                    List<EnchantmentHelper.Offer> list = enchantmentsFor(item, slot, this.costs[slot]);
                    if (list != null && !list.isEmpty()) {
                        EnchantmentHelper.Offer picked = list.get(this.random.nextInt(list.size()));
                        this.clues[slot] = picked.enchantment().id | picked.level() << 8;
                    }
                }
            }
        } else {
            for (int slot = 0; slot < 3; slot++) {
                this.costs[slot] = 0;
                this.clues[slot] = -1;
            }
        }
    }

    /**
     * The bookshelf power scan (the reference's exact geometry): over the
     * eight horizontal neighbors, both cells above the neighbor must be air;
     * then the double-offset column ({@code k*2}/{@code j*2}) counts at both
     * heights, and corners add the four lateral cells. The helper's 15 cap
     * lives in {@link EnchantmentHelper#requiredXpLevel} — the raw scan can
     * exceed it, exactly like the reference.
     */
    private int bookshelfPower(WorldView world) {
        int power = 0;
        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                if ((j != 0 || k != 0)
                        && isAir(world, k, 0, j) && isAir(world, k, 1, j)) {
                    if (isBookshelf(world, k * 2, 0, j * 2)) {
                        power++;
                    }
                    if (isBookshelf(world, k * 2, 1, j * 2)) {
                        power++;
                    }
                    if (k != 0 && j != 0) {
                        if (isBookshelf(world, k * 2, 0, j)) {
                            power++;
                        }
                        if (isBookshelf(world, k * 2, 1, j)) {
                            power++;
                        }
                        if (isBookshelf(world, k, 0, j * 2)) {
                            power++;
                        }
                        if (isBookshelf(world, k, 1, j * 2)) {
                            power++;
                        }
                    }
                }
            }
        }
        return power;
    }

    private boolean isAir(WorldView world, int dx, int dy, int dz) {
        return blockAt(world, dx, dy, dz).identifier()
                .equals(BuiltinBlocks.AIR.identifier());
    }

    private boolean isBookshelf(WorldView world, int dx, int dy, int dz) {
        return blockAt(world, dx, dy, dz).identifier()
                .equals(BuiltinBlocks.BOOKSHELF.identifier());
    }

    /**
     * The bounds-safe read (the vanilla out-of-column rule: air above the
     * build limit, like the engine's other block paths). The scan reaches
     * y+1, which overflows at a table on the build limit.
     */
    private BlockType blockAt(WorldView world, int dx, int dy, int dz) {
        int y = originY + dy;
        if (y < BlockPosition.MIN_Y || y > BlockPosition.MAX_Y) {
            return BuiltinBlocks.AIR;
        }
        return world.blockAt(origin.offset(dx, dy, dz));
    }

    /** The scanned table position. */
    public BlockPosition position() {
        return origin;
    }

    /**
     * The reference {@code isValid}: the block must still be the enchanting
     * table and the player within squared distance 64 of the table's center
     * (the +0.5s) — the window closes the tick this turns false.
     */
    public boolean stillValid(WorldView world, double playerX, double playerY, double playerZ) {
        if (!blockAt(world, 0, 0, 0).identifier()
                .equals(BuiltinBlocks.ENCHANTING_TABLE.identifier())) {
            return false;
        }
        double dx = origin.x() + 0.5 - playerX;
        double dy = origin.y() + 0.5 - playerY;
        double dz = origin.z() + 0.5 - playerZ;
        return dx * dx + dy * dy + dz * dz <= 64.0;
    }

    /**
     * The seven Window Property values in the reference's id order:
     * 0..2 the costs, 3 the seed (masked), 4..6 the clue values.
     */
    public int property(int id) {
        if (id >= 0 && id <= 2) {
            return costs[id];
        }
        if (id == 3) {
            return seed & -16;
        }
        if (id >= 4 && id <= 6) {
            return clues[id - 4];
        }
        throw new IllegalArgumentException("Enchanting property id out of range: " + id);
    }

    /** The seven property ids, pushed on open and after every recompute. */
    public static final int PROPERTY_COUNT = 7;

    /**
     * The historical {@code isEnchantable}: enchantability above zero and
     * not already carrying enchantments (the {@code tag.ench} presence).
     */
    private static boolean isEnchantable(ItemStack item) {
        if (item.isEmpty()) {
            return false;
        }
        if (item.enchantments() != null && !item.enchantments().isEmpty()) {
            return false;
        }
        return Enchantments.enchantabilityOf(item) > 0;
    }

    /** The reference {@code getEnchantments}: re-seeded offers, books drop one. */
    private List<EnchantmentHelper.Offer> enchantmentsFor(ItemStack item, int id, int level) {
        this.random.setSeed(this.seed + id);
        List<EnchantmentHelper.Offer> list = EnchantmentHelper.buildOffers(this.random, item, level);
        if (item.type().identifier().equals(BuiltinItems.BOOK.identifier())
                && list != null && list.size() > 1) {
            list.remove(this.random.nextInt(list.size()));
        }
        return list;
    }

    // --- clicks (the two menu slots through the shared semantics) -----------

    private static final int[] SLOT_MAX = {1, 64};

    /**
     * The lapis slot's {@code isItemAllowed}: the dye-blue rule. The engine's
     * lapis item is the canonical {@code minecraft:lapis} whose damage carries
     * the dye metadata — blue is damage 4 (the wire's 351:4).
     */
    private static boolean isLapis(ItemStack stack) {
        return !stack.isEmpty()
                && stack.type().identifier().equals(BuiltinItems.LAPIS.identifier())
                && stack.damage() == 4;
    }

    /**
     * One click on a menu slot (the reference's PICKUP walk with the slot's
     * own max and the placement gate). Slot 0 accepts anything at max 1;
     * slot 1 only lapis. @return whether the click changed state (a refused
     * click keeps everything — the client reverts its prediction).
     */
    public boolean clickSlot(int menuSlot, int button, WindowClicks.CursorBox cursor) {
        if (menuSlot < 0 || menuSlot > 1) {
            return false;
        }
        ItemStack before = slots[menuSlot];
        ItemStack cursorBefore = cursor.get();
        WindowClicks.SlotFilter filter = menuSlot == 1 ? EnchantingMenu::isLapis : WindowClicks.ANY;
        WindowClicks.click(slots, menuSlot, button, cursor, SLOT_MAX[menuSlot], filter);
        if (!before.equals(slots[menuSlot]) || !cursorBefore.equals(cursor.get())) {
            onContentsChanged(null); // the attached view substitutes when set
            return true;
        }
        return false;
    }

    /**
     * Quick-move out of a menu slot (the reference {@code quickMoveItem} arm
     * for slots 0 and 1: into the player's inventory, merge first).
     *
     * @return the leftover that stays in the slot (empty when fully moved)
     */
    public ItemStack quickMoveToInventory(int menuSlot, java.util.function.Function<ItemStack, ItemStack> insert) {
        if (menuSlot < 0 || menuSlot > 1 || slots[menuSlot].isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack leftover = insert.apply(slots[menuSlot]);
        slots[menuSlot] = leftover;
        onContentsChanged(null);
        return leftover;
    }

    /**
     * Quick-move from the player into the menu (the reference's player arm):
     * lapis goes to slot 1 through {@code moveItem(1, 2, true)} — merge first,
     * then the empty slot, capped at the slot's 64; everything else to slot 0 —
     * one unit, or the whole stack when it is a single named/NBT stack.
     *
     * @return the leftover that stays with the player
     */
    public ItemStack quickMoveFromPlayer(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (isLapis(stack)) {
            int slotMax = Math.min(64, stack.type().maxStackSize());
            if (slots[1].isEmpty()) {
                int take = Math.min(slotMax, stack.count());
                slots[1] = stack.withCount(take);
                onContentsChanged(null);
                return stack.count() - take == 0 ? ItemStack.EMPTY
                        : stack.withCount(stack.count() - take);
            }
            if (!stacksMergeable(slots[1], stack)) {
                return stack; // a non-lapis occupant: the reference's moveItem never lands it
            }
            int capacity = slotMax - slots[1].count();
            int moved = Math.min(capacity, stack.count());
            if (moved > 0) {
                slots[1] = new ItemStack(slots[1].type(), slots[1].count() + moved,
                        slots[1].damage());
                onContentsChanged(null);
            }
            return moved == 0 ? stack
                    : (stack.count() - moved == 0 ? ItemStack.EMPTY
                       : stack.withCount(stack.count() - moved));
        }
        if (!slots[0].isEmpty()) {
            return stack;
        }
        if (stack.count() == 1) {
            slots[0] = stack;
            onContentsChanged(null);
            return ItemStack.EMPTY;
        }
        slots[0] = ItemStack.of(stack.type(), 1).withDamage(stack.damage());
        onContentsChanged(null);
        return stack.withCount(stack.count() - 1);
    }

    private static boolean stacksMergeable(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty()
                && a.type().equals(b.type())
                && a.damage() == b.damage()
                && java.util.Objects.equals(a.displayName(), b.displayName())
                && java.util.Objects.equals(a.enchantments(), b.enchantments());
    }

    // --- the enchant button (the reference onButtonClick) --------------------

    /**
     * The enchant button ({@code id} 0..2): the gates (lapis ≥ slot+1 unless
     * creative; cost > 0; XP ≥ slot+1 and XP ≥ cost unless creative), then
     * the offer application — the level consumption and seed reroll first
     * (the reference applies the cost before the enchant lands), the book's
     * conversion to an enchanted book, each offer onto the stack, the lapis
     * consumption, and the exact recompute ordering (the markDirty recompute
     * with the old seed, then the re-seeded one).
     *
     * @return whether the enchant happened
     */
    public boolean enchantButton(Enchanter player, int id, WorldView world) {
        if (id < 0 || id > 2) {
            return false;
        }
        ItemStack item = slots[0];
        ItemStack lapis = slots[1];
        int total = id + 1;
        if ((lapis.isEmpty() || lapis.count() < total) && !player.creative()) {
            return false;
        }
        if (this.costs[id] > 0
                && !item.isEmpty()
                && (player.xpLevel() >= total && player.xpLevel() >= this.costs[id]
                    || player.creative())) {
            List<EnchantmentHelper.Offer> list = enchantmentsFor(item, id, this.costs[id]);
            if (list == null) {
                return true; // the reference returns true for a null list: the cost was paid
            }
            boolean book = item.type().identifier().equals(BuiltinItems.BOOK.identifier());
            player.applyEnchantmentCosts(total);
            if (book) {
                item = ItemStack.of(BuiltinItems.ENCHANTED_BOOK, item.count())
                        .withDamage(item.damage());
            }
            for (EnchantmentHelper.Offer offer : list) {
                item = item.withEnchantment(offer.enchantment().id, offer.level());
            }
            slots[0] = item;
            if (!player.creative()) {
                int left = lapis.count() - total;
                slots[1] = left <= 0 ? ItemStack.EMPTY : lapis.withCount(left);
            }
            // The reference's recompute ordering: markDirty recomputes with the
            // menu's current (old) seed, then the menu re-reads the player's
            // rerolled seed and recomputes again — the pushed state is the
            // new-seed one.
            onContentsChanged(world);
            this.seed = player.enchantingSeed();
            onContentsChanged(world);
            return true;
        }
        return false;
    }

    // --- the view state (the seven Window Property values) -------------------

    public int cost(int slot) {
        return costs[slot];
    }

    public int clue(int slot) {
        return clues[slot];
    }

    /** The wire's seed property: the reference pushes {@code seed & -16}. */
    public int wireSeed() {
        return seed & -16;
    }

    public long revision() {
        return revision;
    }

    /** Test probe: the menu's current seed (never on the wire raw). */
    public int seed() {
        return seed;
    }
}
