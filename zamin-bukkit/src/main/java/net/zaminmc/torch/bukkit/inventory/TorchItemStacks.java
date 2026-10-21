package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.item.ItemType;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * The ItemStack view (§28.7's object-semantics inventory — the slice-4
 * inventory family): the engine's immutable {@code ItemStack} records
 * translated into the legacy 1.8.8 {@link ItemStack} VALUE snapshots —
 * the same law the slice-2 furnace family established (a fresh value
 * snapshot per read, never a live view; the native events themselves
 * declare their stacks immutable reads, and the engine's stack records
 * carry no mutable surface to mirror anyway).
 *
 * <p><b>The MaterialMap law (slice 2's own finding, carried to items):</b>
 * the engine's lowercase identifier paths ARE the legacy material names —
 * {@code minecraft:diamond} names {@code Material.DIAMOND} — so the
 * engine→legacy translation is the API's own {@link Material#matchMaterial}
 * resolver over the bare path. The legacy→engine translation resolves the
 * material's lowercase name against the engine's own frozen {@code
 * ItemRegistry} (§27.3 — the registry is the source of truth over any
 * mapping table the adapter might invent).</p>
 *
 * <p><b>The honest-gap rule (§27.9):</b> an engine item with no 1.8.8
 * constant (or a legacy material the engine has not implemented) is
 * recorded loudly and answered as unmapped — the caller's no-dispatch /
 * refuse-the-write law, never a wrong item.</p>
 */
public final class TorchItemStacks {

    private TorchItemStacks() {
        // The translation family — no instances.
    }

    /**
     * One engine stack's legacy value view: the bare identifier path
     * against the API's own {@link Material#matchMaterial} resolver, the
     * count and the damage/dual-role variant riding along. A fresh value
     * snapshot (the event's own copy — never a live view). An empty or
     * unmapped stack answers null — the caller's no-dispatch law, never a
     * wrong item.
     *
     * @param what the caller's own diagnostic label (the loud log's prefix)
     */
    public static ItemStack toLegacy(net.zaminmc.torch.item.ItemStack stack,
            String what, Logger logger) {
        if (stack == null || stack.isEmpty() || stack.type() == null) {
            return null;
        }
        String path = stack.type().identifier().value();
        Material material = Material.matchMaterial(path);
        if (material == null) {
            logger.warning(() -> what + ": the engine item minecraft:" + path
                    + " has no 1.8.8 Material — the event was not bridged");
            return null;
        }
        return new ItemStack(material, stack.count(), (short) stack.damage());
    }

    /**
     * One legacy stack's engine translation (the write path — the
     * Inventory views' {@code setItem} arms): the material's lowercase name
     * against the engine's frozen item registry, the amount and the
     * durability riding along. A null or AIR stack answers the engine's
     * EMPTY (the clear); a material the engine has not implemented throws
     * the loud refusal — never a wrong item written.
     */
    public static net.zaminmc.torch.item.ItemStack toEngine(
            ItemStack legacy, BukkitAdapter adapter) {
        if (legacy == null || legacy.getType() == null
                || legacy.getType() == Material.AIR || legacy.getAmount() <= 0) {
            return net.zaminmc.torch.item.ItemStack.EMPTY;
        }
        ItemType type = adapter.engine().items()
                .byKey(legacy.getType().name().toLowerCase(java.util.Locale.ROOT))
                .orElse(null);
        if (type == null) {
            throw new IllegalArgumentException("The 1.8.8 material "
                    + legacy.getType().name() + " is not implemented by the"
                    + " engine — the write is refused (§27.9: never a wrong item)");
        }
        return net.zaminmc.torch.item.ItemStack.of(type, legacy.getAmount())
                .withDamage(legacy.getDurability());
    }

    /**
     * The value-equality read over two legacy views (the divergence checks'
     * own helper — the type, the amount, and the durability/dual-role
     * variant).
     */
    public static boolean viewEquals(ItemStack one, ItemStack two) {
        if (one == two) {
            return true;
        }
        if (one == null || two == null) {
            return false;
        }
        return one.getType() == two.getType()
                && one.getAmount() == two.getAmount()
                && one.getDurability() == two.getDurability();
    }
}
