package net.zaminmc.torch.server.item;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BlockBehaviorTable;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.player.PlayerInventory;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tool-wear parity law (the verifier's fourth pass — the durability
 * wear family, each law read from reference/1.8.8 before the fix):
 *
 * <ul>
 * <li>The takeDamage boundary (ItemStack.takeDamage line 204: {@code
 *     metadata > maxDamage} — takeDamageAndBreak breaks the stack only
 *     when the accumulated wear EXCEEDS the maximum): a stack at exactly
 *     its maximum survives one more use. The pre-fix {@code >=} boundary
 *     at every wear site cost each damageable item exactly ONE use — a
 *     250-durability iron pickaxe answered 250 uses where the reference
 *     answers 251.</li>
 * <li>The per-block wear (the reference's {@code mineBlock} rows): the
 *     SWORD wears TWO per block (SwordItem line 55), every other tool ONE
 *     (ToolItem line 42, ShearsItem line 28). The pre-fix walk wore ONE
 *     for every tool including the sword.</li>
 * <li>The melee wear (the reference's {@code attack} rows): the ToolItem
 *     family wears TWO on a landed hit (ToolItem line 35), the SWORD ONE
 *     (SwordItem line 48), and the shears and hoes NEVER (ShearsItem and
 *     HoeItem extend Item directly — the default {@code attack} returns
 *     false). The pre-fix walk wore ONE for every tool including the
 *     shears.</li>
 * <li>The shears block-wear band (ShearsItem.mineBlock lines 20-29): the
 *     web, the tallgrass block's own pair, the vine, the wool family and
 *     every leaves material — a shears dig of anything else never wears.
 *     The pre-fix walk wore one unit on EVERY block with hardness.</li>
 * </ul>
 */
class ToolWearParityTest {

    private static net.zaminmc.torch.block.BlockType block(String value) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder())
                .freeze().lookup(Identifier.parse(value)).orElseThrow();
    }

    @Test
    void theBoundarySurvivesTheMaximumAndBreaksAtTheExceed() {
        // ItemStack.takeDamage line 204: the break lands at metadata >
        // maxDamage — the stack at exactly 250 survives, the next wear
        // breaks it. The pre-fix >= boundary broke at 250.
        PlayerInventory inventory = new PlayerInventory();
        inventory.setSlot(0, ItemStack.of(BuiltinItems.IRON_PICKAXE, 1).withDamage(249));
        assertTrue(inventory.damageHeld(1, null), "the wear changed the slot");
        assertEquals(250, inventory.held().damage(), "the stack survives AT its maximum");
        assertFalse(inventory.held().isEmpty());
        assertTrue(inventory.damageHeld(1, null), "the breaking wear changes the slot");
        assertTrue(inventory.held().isEmpty(), "the exceed empties the hand");
    }

    @Test
    void anIronPickaxeAnswersTheReferenceOwnsUseCount() {
        // The full walk: a fresh 250-durability pickaxe survives 250 wears
        // and dies on the 251st (the reference's own count — the pre-fix
        // boundary answered 250).
        PlayerInventory inventory = new PlayerInventory();
        inventory.setSlot(0, ItemStack.of(BuiltinItems.IRON_PICKAXE, 1));
        int wears = 0;
        while (!inventory.held().isEmpty() && wears < 1000) {
            inventory.damageHeld(1, null);
            wears++;
        }
        assertEquals(251, wears, "the reference's 251-use count");
    }

    @Test
    void theBlockWearSplitsSwordFromTool() {
        // SwordItem line 55: two per block. ToolItem line 42 and ShearsItem
        // line 28: one. The non-tool: nothing.
        assertEquals(2, Tools.blockWearOf(BuiltinItems.DIAMOND_SWORD));
        assertEquals(1, Tools.blockWearOf(BuiltinItems.IRON_PICKAXE));
        assertEquals(1, Tools.blockWearOf(BuiltinItems.DIAMOND_AXE));
        assertEquals(1, Tools.blockWearOf(BuiltinItems.IRON_SHOVEL));
        assertEquals(1, Tools.blockWearOf(BuiltinItems.SHEARS));
        // HoeItem extends Item directly: the default mineBlock returns
        // false — a hoe dig NEVER wears (the review fix).
        assertEquals(0, Tools.blockWearOf(BuiltinItems.WOODEN_HOE));
        assertEquals(0, Tools.blockWearOf(BuiltinItems.IRON_HOE));
        assertEquals(0, Tools.blockWearOf(BuiltinItems.DIRT));
    }

    @Test
    void theMeleeWearSplitsToolSwordAndTheItemDefaults() {
        // ToolItem line 35: two on a landed hit. SwordItem line 48: one.
        // The shears and the hoes extend Item — the default attack returns
        // false and never wears.
        assertEquals(2, Tools.meleeWearOf(BuiltinItems.IRON_PICKAXE));
        assertEquals(2, Tools.meleeWearOf(BuiltinItems.DIAMOND_AXE));
        assertEquals(2, Tools.meleeWearOf(BuiltinItems.IRON_SHOVEL));
        assertEquals(1, Tools.meleeWearOf(BuiltinItems.DIAMOND_SWORD));
        assertEquals(0, Tools.meleeWearOf(BuiltinItems.SHEARS));
        assertEquals(0, Tools.meleeWearOf(BuiltinItems.WOODEN_HOE));
        assertEquals(0, Tools.meleeWearOf(BuiltinItems.DIRT));
    }

    @Test
    void theShearsWearBandCoversTheReferenceOwnsSix() {
        // ShearsItem.mineBlock lines 20-29: the web, the tallgrass pair,
        // the vine, the wool family, every leaves material — the dead bush
        // and the double plants sit OUTSIDE (the band registers by block).
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:cobweb")));
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:tall_grass")));
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:fern")));
        // The tripwire WIRE sits in the band (the reference's own
        // Blocks.TRIPWIRE member — the review fix); it is behavior-table
        // data only, so the seat constructs the type directly.
        assertTrue(BlockBehaviorTable.inShearsWearBand(new net.zaminmc.torch.server.block
                .EngineBlockType(Identifier.parse("minecraft:tripwire"), "Tripwire")),
                "the reference band's own TRIPWIRE member (the review fix)");
        assertTrue(BlockBehaviorTable.inShearsWearBand(new net.zaminmc.torch.server.block
                .EngineBlockType(Identifier.parse("minecraft:tripwire_attached_disarmed"),
                        "Tripwire")),
                "the state-suffixed wire forms ride the band");
        assertFalse(BlockBehaviorTable.inShearsWearBand(new net.zaminmc.torch.server.block
                .EngineBlockType(Identifier.parse("minecraft:tripwire_hook_south"),
                        "Tripwire Hook")),
                "the HOOK is its own block, outside the band");
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:vine")));
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:wool")));
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:orange_wool")));
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:oak_leaves")));
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:spruce_leaves")));
        // Everything else never wears the shears.
        assertFalse(BlockBehaviorTable.inShearsWearBand(block("minecraft:stone")),
                "a shears dig of stone never wears");
        assertFalse(BlockBehaviorTable.inShearsWearBand(block("minecraft:dirt")));
        assertFalse(BlockBehaviorTable.inShearsWearBand(block("minecraft:dead_bush")),
                "the dead bush sits outside the band");
        assertFalse(BlockBehaviorTable.inShearsWearBand(block("minecraft:double_grass")),
                "the double plants sit outside the band");
    }
}
