package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * The legacy {@link Inventory} view base over one engine container (§28.7's
 * object-semantics inventory — the slice-4 inventory family): the §27.5
 * handle law — every read resolves to the CURRENT authoritative engine
 * state through the engine's own public surfaces (the block entities' own
 * reads reached through the engine's own manager accessors), never
 * adapter-copied state; NO new engine state anywhere.
 *
 * <p><b>The slot reads:</b> {@link #getItem(int)} answers a fresh legacy
 * VALUE snapshot per call ({@link TorchItemStacks#toLegacy} — the same
 * law the slice-2 furnace family established) and {@code null} for an
 * empty slot — the 1.8.8 slots-hold-null convention. The
 * {@code contains}/{@code all}/{@code first} read family rides the same
 * live per-slot reads.</p>
 *
 * <p><b>The write routing (§27.6):</b> {@link #setItem(int, ItemStack)}
 * translates the legacy stack ({@link TorchItemStacks#toEngine} — a
 * material the engine has not implemented throws the loud refusal, never a
 * wrong item) and routes through the view's own position-scoped mutation
 * path — on the owner's own context (the event handlers') it applies
 * directly, from anywhere else it travels as a nonblocking cross-owner
 * intent applied at a tick boundary. {@code remove}/{@code clear}/
 * {@code setContents} decompose onto the same per-slot writes. The
 * session-owned forms (the player's own inventory, the ender grid) route
 * to the player's own cell — their state is session state, never world
 * state.</p>
 *
 * <p><b>The honest gaps (§27.9 — every one recorded with its reason in
 * this javadoc, none faked):</b></p>
 * <ul>
 *   <li>{@code addItem}/{@code removeItem} — the vanilla pickup/withdraw
 *       MERGE walks are the engine's own internal machinery (the hopper's
 *       insert walk, the inventory's pickup path, the block entities'
 *       quick-move arms); recreating them here would be the parallel
 *       Bukkit world model §27.9 forbids. The native route: the engine's
 *       own give/insert surfaces plus this view's per-slot {@code setItem}.</li>
 *   <li>{@code setMaxStackSize} — the engine exposes no per-container
 *       stack-size override (the per-type limits are the registry's own
 *       rows); {@link #getMaxStackSize()} answers the historical 64.</li>
 *   <li>The block containers' {@code getHolder()} answer is a
 *       position-keyed {@link TorchBlockInventoryHolder} — NOT the legacy
 *       {@code Chest}/{@code Dispenser} block-state views (the block-state
 *       family is the adapter's own demand-named later slice; a plugin
 *       casting the holder fails loudly at the cast, never gets a fake
 *       block state).</li>
 * </ul>
 */
public abstract class TorchInventory implements Inventory {

    /** The adapter this view rides (the §27.6 routing + the translations). */
    protected final net.zaminmc.torch.bukkit.BukkitAdapter adapter;

    /** The engine world the container stands in (null: a session-owned form). */
    protected final World world;

    /** The container's block cell (null: a session-owned form). */
    protected final BlockPosition position;

    private InventoryHolder holder;

    /**
     * @param world     the container's world (null: session-owned state)
     * @param position  the container's block cell (null: session-owned state)
     */
    protected TorchInventory(net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            World world, BlockPosition position) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.world = world;
        this.position = position;
    }

    /** Binds the holder view (the factories wire it after construction). */
    protected void bindHolder(InventoryHolder holder) {
        this.holder = holder;
    }

    // ------------------------------------------------------------------ the per-family engine surface

    /** The container's own slot count (the engine's constant). */
    protected abstract int engineSize();

    /** One slot's live engine read (normalized — never null when in range). */
    protected abstract net.zaminmc.torch.item.ItemStack engineSlot(int index);

    /** One slot's engine write — the ON-OWNER arm only (the routing wrapper calls it). */
    protected abstract void engineSetSlot(int index, net.zaminmc.torch.item.ItemStack stack);

    /** The 1.8.8 inventory type of this container. */
    @Override
    public abstract InventoryType getType();

    /** The family's own title (the engine's window-title family — "Chest" and kin). */
    protected abstract String familyTitle();

    /** The container's custom name, or null when it carries none. */
    protected abstract String customName();

    /**
     * The session-owned forms' write route (the player's own inventory and
     * the ender grid): the player's current cell names the owner. Only
     * called when {@link #position} is null.
     */
    protected abstract void routeOwnedWrite(String operation, Runnable action);

    /**
     * The session-owned forms' viewer answer (the player themselves). Only
     * called when {@link #position} is null.
     */
    protected abstract List<HumanEntity> ownedViewers();

    // ------------------------------------------------------------------ the reads

    @Override
    public int getSize() {
        return engineSize();
    }

    /**
     * The historical container stack limit (1.8.8's own
     * getInventoryStackLimit). The engine governs the actual merges by the
     * per-type limits of its frozen item registry — the container-level
     * override has no engine surface ({@code setMaxStackSize} is the
     * recorded §27.9 refusal).
     */
    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= engineSize()) {
            return null;
        }
        return TorchItemStacks.toLegacy(engineSlot(index),
                inventoryLabel() + ".getItem", adapter.logger());
    }

    @Override
    public ItemStack[] getContents() {
        ItemStack[] contents = new ItemStack[engineSize()];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = getItem(i);
        }
        return contents;
    }

    /** The 1.8.8 name: the custom name when the container carries one, else the family title. */
    @Override
    public String getName() {
        String custom = customName();
        return custom == null || custom.isEmpty() ? familyTitle() : custom;
    }

    /** The title walk is the name walk (the engine's own window-title family). */
    @Override
    public String getTitle() {
        return getName();
    }

    @Override
    public InventoryHolder getHolder() {
        return holder;
    }

    /**
     * The viewers: the sessions whose open container window IS this
     * container — the engine session state is the viewer truth, read
     * through its own public accessors. The session-owned forms answer
     * their own player.
     */
    @Override
    public List<HumanEntity> getViewers() {
        if (position == null) {
            return ownedViewers();
        }
        List<HumanEntity> viewers = new ArrayList<>();
        for (net.zaminmc.torch.entity.Player apiPlayer : adapter.engine().players()) {
            if (apiPlayer instanceof net.zaminmc.torch.server.player.PlayerSession session
                    && position.equals(session.openContainerPosition())) {
                viewers.add(adapter.player(session));
            }
        }
        return viewers;
    }

    // ------------------------------------------------------------------ the read family

    @Override
    public boolean contains(int materialId) {
        return contains(Material.getMaterial(materialId));
    }

    @Override
    public boolean contains(Material material) {
        if (material == null) {
            return false;
        }
        for (int i = 0; i < engineSize(); i++) {
            if (!engineSlot(i).isEmpty() && slotIs(engineSlot(i), material)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(ItemStack stack) {
        return containsAtLeast(stack, stack == null ? 0 : stack.getAmount());
    }

    @Override
    public boolean contains(int materialId, int amount) {
        return contains(Material.getMaterial(materialId), amount);
    }

    @Override
    public boolean contains(Material material, int amount) {
        return containsAtLeast(
                material == null ? null : new ItemStack(material, 1), amount);
    }

    @Override
    public boolean contains(ItemStack stack, int amount) {
        return containsAtLeast(stack, amount);
    }

    @Override
    public boolean containsAtLeast(ItemStack stack, int minimum) {
        if (stack == null || stack.getType() == null || stack.getType() == Material.AIR) {
            return minimum <= 0;
        }
        for (int i = 0; i < engineSize(); i++) {
            net.zaminmc.torch.item.ItemStack slot = engineSlot(i);
            if (!slot.isEmpty() && slotSimilar(slot, stack) && slot.count() >= minimum) {
                return true;
            }
        }
        return false;
    }

    @Override
    public HashMap<Integer, ? extends ItemStack> all(int materialId) {
        return all(Material.getMaterial(materialId));
    }

    @Override
    public HashMap<Integer, ? extends ItemStack> all(Material material) {
        HashMap<Integer, ItemStack> found = new HashMap<>();
        if (material == null) {
            return found;
        }
        for (int i = 0; i < engineSize(); i++) {
            ItemStack view = getItem(i);
            if (view != null && view.getType() == material) {
                found.put(i, view);
            }
        }
        return found;
    }

    @Override
    public HashMap<Integer, ? extends ItemStack> all(ItemStack stack) {
        HashMap<Integer, ItemStack> found = new HashMap<>();
        if (stack == null) {
            return found;
        }
        for (int i = 0; i < engineSize(); i++) {
            net.zaminmc.torch.item.ItemStack slot = engineSlot(i);
            if (!slot.isEmpty() && slotSimilar(slot, stack)) {
                found.put(i, getItem(i));
            }
        }
        return found;
    }

    @Override
    public int first(int materialId) {
        return first(Material.getMaterial(materialId));
    }

    @Override
    public int first(Material material) {
        if (material == null) {
            return -1;
        }
        for (int i = 0; i < engineSize(); i++) {
            if (!engineSlot(i).isEmpty() && slotIs(engineSlot(i), material)) {
                return i;
            }
        }
        return -1;
    }

    /** The first slot similar to the stack with at least its amount (the 1.8.8 "matching stack" read). */
    @Override
    public int first(ItemStack stack) {
        if (stack == null) {
            return -1;
        }
        for (int i = 0; i < engineSize(); i++) {
            net.zaminmc.torch.item.ItemStack slot = engineSlot(i);
            if (!slot.isEmpty() && slotSimilar(slot, stack)
                    && slot.count() >= stack.getAmount()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int firstEmpty() {
        for (int i = 0; i < engineSize(); i++) {
            if (engineSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ the writes (the §27.6 routing)

    @Override
    public void setItem(int index, ItemStack legacy) {
        if (index < 0 || index >= engineSize()) {
            return; // the engine's own §27.3 out-of-range contract: the silent no-op
        }
        net.zaminmc.torch.item.ItemStack engine = TorchItemStacks.toEngine(legacy, adapter);
        routeWrite("bukkit-inventory-setItem", () -> engineSetSlot(index, engine));
    }

    @Override
    public void remove(int materialId) {
        remove(Material.getMaterial(materialId));
    }

    @Override
    public void remove(Material material) {
        if (material == null) {
            return;
        }
        for (int i = 0; i < engineSize(); i++) {
            if (!engineSlot(i).isEmpty() && slotIs(engineSlot(i), material)) {
                setItem(i, null);
            }
        }
    }

    /** Removes every stack matching the given one (the 1.8.8 "same type and amount" read). */
    @Override
    public void remove(ItemStack stack) {
        if (stack == null) {
            return;
        }
        for (int i = 0; i < engineSize(); i++) {
            net.zaminmc.torch.item.ItemStack slot = engineSlot(i);
            if (!slot.isEmpty() && slotSimilar(slot, stack)
                    && slot.count() == stack.getAmount()) {
                setItem(i, null);
            }
        }
    }

    @Override
    public void clear(int index) {
        setItem(index, null);
    }

    @Override
    public void clear() {
        for (int i = 0; i < engineSize(); i++) {
            if (!engineSlot(i).isEmpty()) {
                setItem(i, null);
            }
        }
    }

    @Override
    public void setContents(ItemStack[] contents) throws IllegalArgumentException {
        if (contents == null) {
            return;
        }
        if (contents.length > engineSize()) {
            throw new IllegalArgumentException(
                    "The contents array (" + contents.length + ") exceeds the inventory's own"
                            + " size (" + engineSize() + ") — the 1.8.8 rule");
        }
        for (int i = 0; i < contents.length; i++) {
            setItem(i, contents[i]);
        }
    }

    // ------------------------------------------------------------------ the honest §27.9 refusals

    /**
     * The vanilla pickup merge walk is the engine's own machinery — the
     * adapter refuses to recreate it (§27.9: no parallel Bukkit world
     * model). The native route: the engine's own give/insert surfaces,
     * plus this view's per-slot {@code setItem}.
     */
    @Override
    public HashMap<Integer, ItemStack> addItem(ItemStack... stacks)
            throws IllegalArgumentException {
        throw adapter.unbridged(inventoryLabel() + ".addItem"
                + " (the vanilla pickup merge walk is the engine's own — use the"
                + " per-slot setItem or the engine's own give surface)");
    }

    /** The withdraw twin of the addItem refusal (the same §27.9 record). */
    @Override
    public HashMap<Integer, ItemStack> removeItem(ItemStack... stacks)
            throws IllegalArgumentException {
        throw adapter.unbridged(inventoryLabel() + ".removeItem"
                + " (the vanilla withdraw walk is the engine's own — use the"
                + " per-slot setItem or the engine's own consume surfaces)");
    }

    /** No engine surface for a per-container stack-limit override (the §27.9 record). */
    @Override
    public void setMaxStackSize(int size) {
        throw adapter.unbridged(inventoryLabel()
                + ".setMaxStackSize (the engine's stack limits are the item"
                + " registry's own per-type rows — no container-level override exists)");
    }

    // ------------------------------------------------------------------ the iterators

    @Override
    public ListIterator<ItemStack> iterator() {
        return new SlotIterator(0);
    }

    @Override
    public ListIterator<ItemStack> iterator(int index) {
        if (index < 0 || index > engineSize()) {
            throw new IndexOutOfBoundsException("The iterator index is out of range: " + index);
        }
        return new SlotIterator(index);
    }

    /**
     * The live slot iterator: every arm reads the CURRENT engine slot (the
     * §27.5 handle law); {@code set} routes the per-slot write, {@code
     * remove} clears, {@code add} is refused (the slot count is the
     * container's own).
     */
    private final class SlotIterator implements ListIterator<ItemStack> {
        private int cursor;
        private int last = -1;

        SlotIterator(int start) {
            this.cursor = start;
        }

        @Override
        public boolean hasNext() {
            return cursor < engineSize();
        }

        @Override
        public ItemStack next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            last = cursor++;
            return getItem(last);
        }

        @Override
        public boolean hasPrevious() {
            return cursor > 0;
        }

        @Override
        public ItemStack previous() {
            if (!hasPrevious()) {
                throw new NoSuchElementException();
            }
            last = --cursor;
            return getItem(last);
        }

        @Override
        public int nextIndex() {
            return cursor;
        }

        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        @Override
        public void remove() {
            if (last < 0) {
                throw new IllegalStateException();
            }
            TorchInventory.this.setItem(last, null);
            last = -1;
        }

        @Override
        public void set(ItemStack stack) {
            if (last < 0) {
                throw new IllegalStateException();
            }
            TorchInventory.this.setItem(last, stack);
        }

        @Override
        public void add(ItemStack stack) {
            throw new UnsupportedOperationException("The inventory's slot count is the"
                    + " container's own — the iterator cannot add slots");
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The §27.6 route for one slot-scoped write (the TorchBlock law). */
    protected void routeWrite(String operation, Runnable action) {
        if (world != null && position != null) {
            adapter.mutatePosition(world, position, operation, action);
        } else {
            routeOwnedWrite(operation, action);
        }
    }

    /** The similar read: the type and the damage/dual-role variant (never the count). */
    private static boolean slotSimilar(net.zaminmc.torch.item.ItemStack slot, ItemStack legacy) {
        return slotIs(slot, legacy.getType()) && slot.damage() == legacy.getDurability();
    }

    /** The identity read: the engine slot's identifier path is the legacy material's own lowercase name. */
    private static boolean slotIs(net.zaminmc.torch.item.ItemStack slot, Material material) {
        return slot.type().identifier().value()
                .equals(material.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The diagnostic label (the loud logs' own prefix). */
    protected String inventoryLabel() {
        return getClass().getSimpleName();
    }

    /**
     * The position-keyed holder view: answers {@code getInventory()} with
     * the bound view and carries the container's world + cell as its own
     * identity. NOT the legacy {@code Chest}/{@code Dispenser} block-state
     * views — the block-state family is the adapter's demand-named later
     * slice (§27.9: a plugin casting the holder fails loudly at the cast,
     * never receives a fake block state).
     */
    static final class TorchBlockInventoryHolder implements InventoryHolder {
        private final Inventory inventory;
        private final World world;
        private final BlockPosition position;

        TorchBlockInventoryHolder(Inventory inventory, World world, BlockPosition position) {
            this.inventory = inventory;
            this.world = world;
            this.position = position;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public String toString() {
            return "TorchContainerHolder{world=" + (world == null ? null : world.name())
                    + ", position=" + position + "}";
        }
    }
}
