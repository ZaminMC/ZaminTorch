package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.AnvilBlocks;
import net.zaminmc.torch.server.item.Armor;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.item.ToolMaterial;
import net.zaminmc.torch.server.item.Tools;
import net.zaminmc.torch.server.player.WindowClicks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The anvil's menu state (the historical
 * {@code inventory/menu/AnvilMenu.java}, 1:1): the two transient input
 * slots and the computed output projection, the repair-cost ladder, the
 * combine arms, the rename, and the output take. The slots live in the
 * menu — vanilla keeps them in a per-open {@code SimpleInventory("Repair")},
 * never in the block — and the close drops the two inputs to the world.
 *
 * <p>Every arm is transcribed from the reference: {@link #updateResult()}
 * (lines 116-279: the prior-work RepairCost sum, the material arm's
 * max/4-per-unit walk, the tool+tool 12% durability merge, the enchantment
 * merge with the same-level {@code ++} rule and the conflict penalties, the
 * rarity-weight switch, the strip-on-repair quirk and the rename surcharge,
 * the pure-rename 39 clamp, the too-expensive 40 gate, the output's
 * RepairCost doubling), the output take ({@code onItemRemoved} lines 62-93:
 * the XP payment, the input consume with the material shrink, the 0.12 anvil
 * damage roll walking the DAMAGE band past the break), the close (lines
 * 294-305: the two inputs drop; the output is a server-only projection and
 * is never returned), the walk-away gate ({@code isValid} lines 307-311:
 * the anvil must stand, the player within squared distance 64) and the
 * rename ({@code setItemName} lines 350-362).</p>
 */
public final class AnvilMenu {

    /**
     * The world reads the validity gate needs (the anvil must still stand
     * at the origin cell).
     */
    public interface WorldView {
        BlockType blockAt(BlockPosition position);
    }

    /**
     * The player-side arms the output take needs (the historical
     * {@code player.xpLevel}/{@code abilities.creativeMode}/
     * {@code addXp}).
     */
    public interface Repairer {
        int xpLevel();

        boolean creative();

        /**
         * The reference {@code addXp(int)} on the authoritative points
         * model: {@code cost} levels consume (negative levels), the
         * progress into the level is kept, a level below 0 zeroes the
         * whole bar. No seed reroll — the enchanting table's own
         * {@code applyEnchantmentCosts} rerolls, the anvil's payment
         * never does.
         */
        void addXpLevels(int levels);
    }

    /**
     * The anvil block side of the take (the reference's
     * {@code world.getBlockState/removeBlock/setBlockState/doEvent}
     * consults, the caller's own world): the 0.12 roll walks the DAMAGE
     * band (past 2 the anvil breaks), every take ends in a sound — the
     * break's 1020, every other form's 1021.
     */
    public interface AnvilSite {
        /** The reference {@code blockstate.getBlock() == Blocks.ANVIL} consult. */
        boolean anvilStands();

        /** The standing anvil's current DAMAGE row (0-2). */
        int damageState();

        /** The {@code ++l <= 2} arm: the state advances, doEvent 1021. */
        void progressDamage();

        /** The {@code ++l > 2} arm: the anvil breaks (air), doEvent 1020. */
        void breakAnvil();

        /** The no-damage arms' doEvent 1021 (the plain anvil-use sound). */
        void useSound();
    }

    /**
     * The creative consult {@code updateResult} reads at the 40 gate and
     * the enchantment merge (the reference menu holds the player; the
     * engine's per-open menu holds the flag supplier instead).
     */
    private final java.util.function.BooleanSupplier creativeConsult;

    /** The anvil's own cell — every validity read rides it. */
    private final BlockPosition origin;

    /** The two inputs (0, 1) and the output projection (2). */
    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    /** The window's data id 0 (the pushed repair cost). */
    private int repairCost;

    /** The material units the take consumes out of slot 1 (the reference field). */
    private int minRepairItemCount;

    /**
     * The client-sent rename ({@code setItemName}); null = never sent —
     * the strip-on-repair quirk's own arm.
     */
    private String itemName;

    /**
     * Bumped on every observable change (slots, cost, the output). The
     * per-tick view diff uses it — the historical
     * {@code updateListeners} pairing.
     */
    private long revision;

    /** @param position the anvil's cell (the validity gate's anchor). */
    public AnvilMenu(BlockPosition position, java.util.function.BooleanSupplier creativeConsult) {
        this.origin = position == null ? new BlockPosition(0, 0, 0) : position;
        this.creativeConsult = creativeConsult == null ? () -> false : creativeConsult;
    }

    // --- slot access ---------------------------------------------------------

    /** @return the first input slot (the reference repairInventory slot 0). */
    public ItemStack input() {
        return slots[0];
    }

    /** @return the second input slot (the repair material / the sacrifice). */
    public ItemStack ingredient() {
        return slots[1];
    }

    /** @return the computed output projection (the resultInventory slot). */
    public ItemStack output() {
        return slots[2];
    }

    /** Test hook: places a stack directly in an input slot. */
    public void inputSlotSet(int menuSlot, ItemStack stack) {
        if (menuSlot != 0 && menuSlot != 1) {
            throw new IllegalArgumentException("Anvil input slots are 0 and 1: " + menuSlot);
        }
        slots[menuSlot] = stack == null ? ItemStack.EMPTY : stack;
    }

    /** @return the window's data id 0 (the repair cost the take pays). */
    public int repairCost() {
        return repairCost;
    }

    /**
     * @return the dropped stacks when the window closes (the reference
     * {@code close} walk: the two INPUT slots drop — the output is a
     * server-only projection and is never returned).
     */
    public ItemStack[] closeDrop() {
        ItemStack[] dropped = {slots[0], slots[1]};
        slots[0] = ItemStack.EMPTY;
        slots[1] = ItemStack.EMPTY;
        slots[2] = ItemStack.EMPTY;
        repairCost = 0;
        minRepairItemCount = 0;
        revision++;
        return dropped;
    }

    /** The anvil's own cell. */
    public BlockPosition position() {
        return origin;
    }

    // --- the reference updateResult (lines 116-279) --------------------------

    /**
     * The contents-driven recompute: the combine arms, the cost ladder,
     * the rename, the 40 gate, the output's RepairCost doubling. Pure
     * item logic — the world consults never run here. Every call bumps
     * the revision.
     */
    public void updateResult() {
        revision++;
        ItemStack input = slots[0];
        repairCost = 1;
        int priorCost = 0;   // i2: the RepairCost sum of both inputs
        int workCost = 0;    // l1: the accumulated work charge
        int renameApplied = 0; // j2: the rename flag
        if (input.isEmpty()) {
            slots[2] = ItemStack.EMPTY;
            repairCost = 0;
            return;
        }
        ItemStack result = input;
        ItemStack ingredient = slots[1];
        Map<Integer, Integer> merged = enchantmentsOf(input);
        boolean book = false;
        priorCost += input.repairCost()
                + (ingredient.isEmpty() ? 0 : ingredient.repairCost());
        minRepairItemCount = 0;
        if (!ingredient.isEmpty()) {
            book = isEnchantedBook(ingredient);
            if (isDamageable(input) && isRepairMaterial(input, ingredient)) {
                // The material arm: one unit repairs up to a quarter of the
                // maximum (lines 141-157), each unit charging 1.
                int unit = Math.min(result.damage(), result.type().maxDurability() / 4);
                if (unit <= 0) {
                    slots[2] = ItemStack.EMPTY;
                    repairCost = 0;
                    return;
                }
                int used;
                for (used = 0; unit > 0 && used < ingredient.count(); used++) {
                    result = result.withDamage(result.damage() - unit);
                    workCost++;
                    unit = Math.min(result.damage(), result.type().maxDurability() / 4);
                }
                minRepairItemCount = used;
            } else {
                // The combine arms: two of the same damageable item, or an
                // enchanted book merging onto anything (the flag arm).
                if (!book && (!sameItem(input, ingredient) || !isDamageable(input))) {
                    slots[2] = ItemStack.EMPTY;
                    repairCost = 0;
                    return;
                }
                if (isDamageable(input) && !book) {
                    // The tool+tool durability merge (lines 165-179): the
                    // combined remainders plus 12% of the maximum.
                    int remaining = input.type().maxDurability() - input.damage();
                    int sacrificeRemaining =
                            ingredient.type().maxDurability() - ingredient.damage();
                    int combined = remaining + sacrificeRemaining
                            + input.type().maxDurability() * 12 / 100;
                    int newDamage = input.type().maxDurability() - combined;
                    if (newDamage < 0) {
                        newDamage = 0;
                    }
                    if (newDamage < result.damage()) {
                        result = result.withDamage(newDamage);
                        workCost += 2;
                    }
                }
                // The enchantment merge (lines 181-236): the same-level ++
                // rule, the conflict penalties, the rarity weights, the
                // book's halved weight.
                Map<Integer, Integer> ingredientEnchants = enchantmentsOf(ingredient);
                for (Map.Entry<Integer, Integer> row : ingredientEnchants.entrySet()) {
                    int id = row.getKey();
                    Enchantments.Entry enchantment = Enchantments.byId(id);
                    if (enchantment == null) {
                        continue;
                    }
                    int currentLevel = merged.containsKey(id) ? merged.get(id) : 0;
                    int level = row.getValue();
                    level = currentLevel == level ? ++level : Math.max(level, currentLevel);
                    boolean allowed = enchantment.canEnchant(input, true);
                    if (creativeConsult.getAsBoolean()
                            || isEnchantedBookItem(input.type().identifier())) {
                        allowed = true;
                    }
                    for (int other : merged.keySet()) {
                        if (other != id
                                && !enchantment.isCompatible(Enchantments.byId(other))) {
                            allowed = false;
                            workCost++;
                        }
                    }
                    if (allowed) {
                        if (level > enchantment.maxLevel) {
                            level = enchantment.maxLevel;
                        }
                        merged.put(id, level);
                        int weight = rarityWeight(enchantment.weight);
                        if (book) {
                            weight = Math.max(1, weight / 2);
                        }
                        workCost += weight * level;
                    }
                }
            }
        }

        // The rename arms (lines 240-250): a blank never-sent name strips a
        // custom name on ANY work (the strip-on-repair quirk, +1); a sent
        // name different from the current hover name renames (+1).
        if (isBlank(itemName)) {
            if (input.displayName() != null) {
                renameApplied = 1;
                workCost += renameApplied;
                result = result.withName(null);
            }
        } else if (!itemName.equals(hoverName(input))) {
            renameApplied = 1;
            workCost += renameApplied;
            result = result.withName(itemName);
        }

        repairCost = priorCost + workCost;
        if (workCost <= 0) {
            result = ItemStack.EMPTY;
        }

        // The pure-rename clamp (lines 257-259): a rename alone never hits
        // the too-expensive gate.
        if (renameApplied == workCost && renameApplied > 0 && repairCost >= 40) {
            repairCost = 39;
        }

        if (repairCost >= 40 && !creativeConsult.getAsBoolean()) {
            result = ItemStack.EMPTY;
        }

        if (!result.isEmpty()) {
            // The output's RepairCost doubling (lines 265-274): the higher
            // of the two inputs' rows, doubled, plus one.
            int newCost = result.repairCost();
            if (!ingredient.isEmpty() && newCost < ingredient.repairCost()) {
                newCost = ingredient.repairCost();
            }
            result = result.withRepairCost(newCost * 2 + 1);
            result = result.withEnchantments(merged);
        }

        slots[2] = result;
    }

    /**
     * The rarity-weight switch (lines 207-227): the reference's
     * {@code getType()} band — 1 answers 8, 2 answers 4, 5 answers 2,
     * 10 answers 1, every other weight answers 0.
     */
    private static int rarityWeight(int type) {
        return switch (type) {
            case 1 -> 8;
            case 2 -> 4;
            case 5 -> 2;
            case 10 -> 1;
            default -> 0;
        };
    }

    // --- the rename channel (MC|ItemName -> setItemName, lines 350-362) ------

    /**
     * The client-sent rename lands ({@code setItemName}): the name rides
     * the open output's hover name when one is showing, then the full
     * recompute. A blank name is the strip form.
     */
    public void setItemName(String name) {
        this.itemName = name;
        if (!slots[2].isEmpty()) {
            slots[2] = isBlank(name) ? slots[2].withName(null) : slots[2].withName(name);
        }
        updateResult();
    }

    // --- the output take (the reference canPickUp/onItemRemoved) --------------

    /**
     * The take gate ({@code canPickUp} line 58): the creative bypasses the
     * XP band, the cost must be positive, and an output must be showing.
     */
    public boolean canTakeOutput(Repairer player) {
        return (player.creative() || player.xpLevel() >= repairCost)
                && repairCost > 0 && !slots[2].isEmpty();
    }

    /**
     * The take walk ({@code onItemRemoved} lines 62-93): the XP payment,
     * the first input's full consume, the material arm's shrink of the
     * second (the reference's minRepairItemCount band), the cost reset,
     * and the 0.12 anvil-damage roll walking the DAMAGE band — past 2
     * the anvil breaks (doEvent 1020), every other landing answers the
     * 1021 use sound. The roll never fires for a creative take (the
     * reference's own {@code !creativeMode} gate).
     *
     * @return the taken output stack (the projection as it stood).
     */
    public ItemStack takeOutput(Repairer player, Random playerRandom, AnvilSite site) {
        ItemStack taken = slots[2];
        if (!player.creative()) {
            player.addXpLevels(-repairCost);
        }
        slots[0] = ItemStack.EMPTY;
        if (minRepairItemCount > 0) {
            ItemStack material = slots[1];
            if (!material.isEmpty() && material.count() > minRepairItemCount) {
                slots[1] = material.withCount(material.count() - minRepairItemCount);
            } else {
                slots[1] = ItemStack.EMPTY;
            }
        } else {
            slots[1] = ItemStack.EMPTY;
        }
        repairCost = 0;
        slots[2] = ItemStack.EMPTY;
        if (!player.creative() && site.anvilStands()
                && playerRandom.nextFloat() < 0.12F) {
            if (site.damageState() >= 2) {
                site.breakAnvil();
            } else {
                site.progressDamage();
            }
        } else {
            site.useSound();
        }
        revision++;
        return taken;
    }

    // --- clicks (the two input slots through the shared semantics) -----------

    /**
     * One plain click on an input slot (the reference PICKUP walk over the
     * repair inventory — no per-slot max, the anvil's inputs take full
     * stacks).
     *
     * @return whether the click changed state (a refused click keeps
     * everything — the client reverts its prediction).
     */
    public boolean clickInput(int menuSlot, int button, WindowClicks.CursorBox cursor) {
        if (menuSlot != 0 && menuSlot != 1) {
            return false;
        }
        ItemStack before = slots[menuSlot];
        ItemStack cursorBefore = cursor.get();
        WindowClicks.click(slots, menuSlot, button, cursor);
        if (!before.equals(slots[menuSlot]) || !cursorBefore.equals(cursor.get())) {
            updateResult();
            return true;
        }
        return false;
    }

    /**
     * Quick-move out of an input slot (the reference {@code quickMoveItem}
     * slot 0/1 arm: into the player's inventory, merge first).
     *
     * @return the leftover that stays in the slot (empty when fully moved)
     */
    public ItemStack quickMoveToInventory(int menuSlot,
                                          java.util.function.Function<ItemStack, ItemStack> insert) {
        if ((menuSlot != 0 && menuSlot != 1) || slots[menuSlot].isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack leftover = insert.apply(slots[menuSlot]);
        slots[menuSlot] = leftover == null ? ItemStack.EMPTY : leftover;
        updateResult();
        return slots[menuSlot];
    }

    /**
     * Quick-move from the player into the menu (the reference's player
     * arm: {@code moveItem(stack, 0, 2, false)} — the two inputs alone
     * (the output slot's {@code isItemAllowed} false keeps it out of the
     * range): the merge pass first (an occupied same-item input with
     * room takes the units), then the empty-slot pass (slot 0 first).
     *
     * @return the leftover that stays with the player
     */
    public ItemStack quickMoveFromPlayer(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < 2; slot++) {
            ItemStack occupant = slots[slot];
            if (!occupant.isEmpty() && WindowClicks.stacksMergeable(occupant, stack)) {
                int capacity = Math.min(64, stack.type().maxStackSize()) - occupant.count();
                int moved = Math.min(capacity, stack.count());
                if (moved > 0) {
                    slots[slot] = occupant.withCount(occupant.count() + moved);
                    updateResult();
                    return stack.count() - moved == 0
                            ? ItemStack.EMPTY : stack.withCount(stack.count() - moved);
                }
            }
        }
        for (int slot = 0; slot < 2; slot++) {
            if (slots[slot].isEmpty()) {
                slots[slot] = stack;
                updateResult();
                return ItemStack.EMPTY;
            }
        }
        return stack; // both inputs occupied: the reference's moveItem never lands it
    }

    // --- the walk-away gate (isValid, lines 307-311) ---------------------------

    /**
     * The reference {@code isValid}: the block must still be an anvil (any
     * of the twelve states — the reference reads the family) and the
     * player within squared distance 64 of the anvil's center.
     */
    public boolean stillValid(WorldView world, double playerX, double playerY, double playerZ) {
        if (!AnvilBlocks.isAnvil(world.blockAt(origin).identifier())) {
            return false;
        }
        double dx = origin.x() + 0.5 - playerX;
        double dy = origin.y() + 0.5 - playerY;
        double dz = origin.z() + 0.5 - playerZ;
        return dx * dx + dy * dy + dz * dz <= 64.0;
    }

    // --- the item consults (the reference's item-tier rows) --------------------

    /** The reference {@code ItemStack.isDamageable}: durability-bound and not Unbreakable. */
    private static boolean isDamageable(ItemStack stack) {
        return !stack.isEmpty()
                && stack.type().maxDurability() > 0
                && !stack.unbreakable();
    }

    /** The reference {@code getItem() != getItem()} identity consult. */
    private static boolean sameItem(ItemStack a, ItemStack b) {
        return a.type().identifier().equals(b.type().identifier());
    }

    /** The reference {@code Items.ENCHANTED_BOOK} with stored enchantments (the book flag). */
    private static boolean isEnchantedBook(ItemStack stack) {
        return isEnchantedBookItem(stack.type().identifier())
                && stack.enchantments() != null && !stack.enchantments().isEmpty();
    }

    /** The bare item identity ({@code itemstack.getItem() == Items.ENCHANTED_BOOK}). */
    private static boolean isEnchantedBookItem(net.zaminmc.torch.util.Identifier item) {
        return item.equals(BuiltinItems.ENCHANTED_BOOK.identifier());
    }

    /**
     * The repair-ingredient consult (the reference
     * {@code Item.isRepairable} family rows — the tier tables): tools
     * answer their material's ingredient ({@code Item.Tier.
     * getRepairIngredient}: wood planks, stone cobblestone, iron and
     * gold ingots, diamond), armor answers the {@code ArmorItem.Tier}
     * rows (leather, chainmail-iron, iron, gold, diamond), everything
     * else answers false (the base {@code Item.isRepairable}).
     */
    private static boolean isRepairMaterial(ItemStack tool, ItemStack ingredient) {
        Optional<net.zaminmc.torch.server.item.ToolSpec> spec = Tools.specOf(tool.type());
        if (spec.isPresent()) {
            return ingredient.type().identifier()
                    .equals(repairIngredientOf(spec.orElseThrow().material()));
        }
        Optional<Armor.Spec> armor = Armor.specOf(tool.type());
        if (armor.isPresent()) {
            net.zaminmc.torch.util.Identifier ingredientId = ingredient.type().identifier();
            String value = tool.type().identifier().value();
            if (value.startsWith("minecraft:leather_")) {
                return ingredientId.equals(BuiltinItems.LEATHER.identifier());
            }
            if (value.startsWith("minecraft:chainmail_")) {
                return ingredientId.equals(BuiltinItems.IRON_INGOT.identifier());
            }
            if (value.startsWith("minecraft:iron_")) {
                return ingredientId.equals(BuiltinItems.IRON_INGOT.identifier());
            }
            if (value.startsWith("minecraft:golden_")) {
                return ingredientId.equals(BuiltinItems.GOLD_INGOT.identifier());
            }
            if (value.startsWith("minecraft:diamond_")) {
                return ingredientId.equals(BuiltinItems.DIAMOND.identifier());
            }
            return false;
        }
        return false;
    }

    /** The reference {@code Tier.getRepairIngredient} (Item.java lines 807-817). */
    private static net.zaminmc.torch.util.Identifier repairIngredientOf(ToolMaterial material) {
        return switch (material) {
            case WOOD -> BuiltinItems.PLANKS.identifier();
            case STONE -> BuiltinItems.COBBLESTONE.identifier();
            case GOLD -> BuiltinItems.GOLD_INGOT.identifier();
            case IRON -> BuiltinItems.IRON_INGOT.identifier();
            case DIAMOND -> BuiltinItems.DIAMOND.identifier();
        };
    }

    /** The enchantment rows (the reference {@code getEnchantments}: empty when absent). */
    private static Map<Integer, Integer> enchantmentsOf(ItemStack stack) {
        Map<Integer, Integer> rows = new HashMap<>();
        if (stack.enchantments() != null) {
            rows.putAll(stack.enchantments());
        }
        return rows;
    }

    /** The reference {@code getHoverName}: the custom name, else the item's own. */
    private static String hoverName(ItemStack stack) {
        return stack.displayName() != null ? stack.displayName() : stack.type().displayName();
    }

    /** The reference {@code StringUtils.isBlank}. */
    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    // --- the view state -------------------------------------------------------

    /** The anvil's single Window Property (data id 0: the repair cost). */
    public int property(int id) {
        if (id == 0) {
            return repairCost;
        }
        throw new IllegalArgumentException("Anvil property id out of range: " + id);
    }

    /** The pushed property count (the anvil's data band is the one row). */
    public static final int PROPERTY_COUNT = 1;

    public long revision() {
        return revision;
    }

    /** Test probe: the client-sent name (null when none was sent). */
    public String itemName() {
        return itemName;
    }
}
