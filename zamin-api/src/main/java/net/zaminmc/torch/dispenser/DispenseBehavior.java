package net.zaminmc.torch.dispenser;

import net.zaminmc.torch.item.ItemStack;

/**
 * One dispense behavior — the unit the dispenser's behavior registry
 * answers with (the public registration contract, §27.3's dispenser
 * family: "behavior registration").
 *
 * <p>A behavior receives the live dispense context (the world, the block,
 * the facing, the picked stack) and answers with the slot's remainder:
 * return {@link ItemStack#EMPTY} when the use consumed the item, or a
 * smaller stack to leave the rest in the slot. The dispense itself
 * consumed nothing — the slot takes whatever this method returns.</p>
 *
 * <p><b>Threading (§27.6):</b> {@code dispense} runs on the simulation
 * owner's context inside the dispense walk — the same lane the built-in
 * behaviors (the projectile launches, the fluid pours) run on. The
 * context's world is the live handle: placing a block through it is
 * exactly what the built-in fire arm does.</p>
 *
 * <p><b>Exception policy:</b> a throwing behavior is logged and the stack
 * stays in the slot (the dispense falls through unresolved) — the
 * simulation never dies.</p>
 */
public interface DispenseBehavior {

    /**
     * Dispenses one use of the stack.
     *
     * @param context the live dispense (world, origin, facing, stack)
     * @return the slot's remainder (empty when consumed)
     */
    ItemStack dispense(DispenseContext context);
}
